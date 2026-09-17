package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #362 (§269 R6 family) — `n.abs()` on a primitive compiled silently and the
 * JVM backend emitted a KofCall with owner "" (Methodref "" in the constant
 * pool): the class only died at LOAD time (ClassFormatError, hidden behind
 * "main not found" by the launcher); JS threw `n.abs is not function` at
 * runtime. Primitives have no methods in Kof beyond the documented whitelist
 * (toString + the §89 conversions + §218 formatters); comparison is `a == b`,
 * math is a top-level function (math.abs(x)). The shared semantic pass now
 * rejects any other call on a primitive with SEM074 (one gate, all targets —
 * precedent SEM028/array, SEM050/field, SEM072-073/#336-#361).
 */
class PrimitiveMethodCallGuardTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    @Test
    void primitiveAbsCallIsRejectedSem074(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "Abs", """
                main() {
                    var n = -42
                    println(n.abs())
                }
                """, Target.JVM);
        assertFalse(r.success(), "#362 verbatim: method on primitive must fail at compile time (old code compiled and died at class load)");
        String d = r.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("SEM074"), "must carry SEM074: " + d);
        assertTrue(d.contains("math.abs"), "message must point to the Kof idiom: " + d);
    }

    @Test
    void objectMethodsOnPrimitiveAreRejected(@TempDir Path tempDir) throws Exception {
        // Q3 edges: the java.lang.Object methods the emitter does NOT box for
        // (equals/hashCode crashed; toString is the documented whitelist).
        CompilationResult r = compile(tempDir, "Eq", """
                main() {
                    var n = 42
                    println(n.equals(42))
                }
                """, Target.JVM);
        assertFalse(r.success(), "n.equals() is translated Java (== is the Kof idiom) and never worked on JVM anyway");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM074"));
        CompilationResult h = compile(tempDir, "Hc", """
                main() {
                    var n = 42
                    println(n.hashCode())
                }
                """, Target.JVM);
        assertFalse(h.success(), "n.hashCode() crashed at load too");
        assertTrue(h.diagnostics().getDiagnostics().toString().contains("SEM074"));
    }

    @Test
    void unlistedConversionToCharIsRejected(@TempDir Path tempDir) throws Exception {
        // §89 whitelist is toInt/toLong/toFloat/toDouble only — `n.toChar()`
        // was outside the branch and crashed the same way.
        CompilationResult r = compile(tempDir, "Tc", """
                main() {
                    var n = 65
                    println(n.toChar())
                }
                """, Target.JVM);
        assertFalse(r.success(), "toChar() is not a §89 conversion: `n as Char` is the idiom");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM074"));
    }

    @Test
    void rejectionIsUniversalAcrossTargets(@TempDir Path tempDir) throws Exception {
        // rule 5: same diagnostic on every backend — the gate is the shared
        // semantic pass (precedent SEM072/#336, SEM073/#361).
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "U", """
                    main() {
                        var b = true
                        b.flip()
                    }
                    """, t);
            assertFalse(r.success(), t + ": method on Bool must be rejected");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM074"),
                    t + ": must be SEM074 (universal gate): " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void whitelistedPrimitiveCallsStillWork(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): the documented surface compiles AND runs —
        // toString (§216), toInt (§89), toHexString (§218), goldens measured
        // on the pre-change tip jar.
        CompilationResult r = compile(tempDir, "Ok", """
                main() {
                    var n = 42
                    println(n.toString())
                    var d = 3.9
                    println(d.toInt())
                    var l = 255
                    println(l.toHexString())
                }
                """, Target.JVM);
        assertTrue(r.success(), "whitelist must stay legal: " + r.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-OkJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals("42\n3\nff", out, "whitelisted primitive calls unchanged");
    }
}
