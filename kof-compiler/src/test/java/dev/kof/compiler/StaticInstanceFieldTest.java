package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #345 — a `static` method referencing an instance field by bare name
 * (`return value * 2` inside `static Int getDouble()`) compiled with no
 * diagnostic and the JVM backend emitted `aload_0` in a method that has no
 * `this` slot → VerifyError "Bad local variable type" at load (measured on the
 * tip; the analyzer resolved the identifier against the current class without
 * asking whether the enclosing member was static). The shared semantic pass
 * now rejects it with SEM075 (R6; one gate, four targets — the phantom aload_0
 * never reaches emission). Legal forms keep working: static field referenced
 * from a static method, and instance field referenced from an instance method.
 */
class StaticInstanceFieldTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    @Test
    void staticMethodReadingInstanceFieldIsSem075(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "V", """
                class Foo {
                    Int value = 10
                    static Int getDouble() { return value * 2 }
                }
                main() { println(Foo.getDouble()) }
                """, Target.JVM);
        assertFalse(r.success(), "#345 verbatim: must fail at compile time (old code: VerifyError at load)");
        String d = r.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("SEM075"), "must carry SEM075: " + d);
        assertTrue(d.contains("value"), "message must name the field: " + d);
        assertTrue(d.contains("static"), "message must explain the static context: " + d);
    }

    @Test
    void staticFieldFromStaticMethodStillWorks(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): the legal static→static reference.
        CompilationResult r = compile(tempDir, "K", """
                class Cfg {
                    static Int limit = 10
                    static Int twice() { return limit * 2 }
                }
                main() { println(Cfg.twice()) }
                """, Target.JVM);
        assertTrue(r.success(), "static field in static method stays legal: " + r.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-KJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals("20", out, "static→static path unchanged (golden 20)");
    }

    @Test
    void instanceFieldFromInstanceMethodStillWorks(@TempDir Path tempDir) throws Exception {
        // control: the ordinary instance path (aload_0 EXISTS there).
        CompilationResult r = compile(tempDir, "I", """
                class Foo {
                    Int value = 10
                    Int getDouble() { return value * 2 }
                }
                main() { var f = Foo(); println(f.getDouble()) }
                """, Target.JVM);
        assertTrue(r.success(), "instance method keeps its implicit this: " + r.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-IJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals("20", out, "instance path unchanged (golden 20)");
    }

    @Test
    void rejectionIsSharedAcrossTargets(@TempDir Path tempDir) throws Exception {
        // rule 5: the guard is in the shared semantic pass.
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "U", """
                    class Foo {
                        Int value = 10
                        static Int getDouble() { return value * 2 }
                    }
                    main() { println(Foo.getDouble()) }
                    """, t);
            assertFalse(r.success(), t + ": static→instance field must be rejected");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM075"),
                    t + ": must be SEM075 (universal gate): " + r.diagnostics().getDiagnostics());
        }
    }
}
