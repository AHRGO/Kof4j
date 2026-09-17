package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #339/#340/#341 — the JVM class file format itself forbids these shapes
 * (IncompatibleClassChangeError on extending a final class, InstantiationError
 * on new-ing an interface, contradictory modifiers). The compiler accepted
 * them silently and the program died at LOAD — same family as #321/#331/
 * #332/#328: validate at compile time (R6). Diagnostics are additive: code
 * that compiles AND runs today is untouched.
 */
class ClassShapeChecksTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void extendingFinalClassIsRejectedSem070(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                final class Sealed { Int x = 42 }
                class Derived extends Sealed {}
                main() { var d = new Derived(); println(d.x) }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "extends final must be rejected (#339)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM070"), "must be the extends-final code: " + diags);
        assertTrue(diags.contains("Sealed"), "must name the final class: " + diags);
    }

    @Test
    void instantiatingInterfaceIsRejectedSem071(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface Greeter { String greet() }
                main() { var g = new Greeter(); println(g.greet()) }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "new on interface must be rejected (#340)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM071"), "must be the interface-instantiation code: " + diags);
        assertTrue(diags.contains("Greeter"), "must name the interface: " + diags);
    }

    @Test
    void implicitConstructionOfInterfaceAlsoRejected(@TempDir Path tempDir) throws IOException {
        // face BuiltinCallTyper — `Greeter()` sem `new` (construcao implicita)
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface Greeter { String greet() }
                main() { var g = Greeter(); println(g) }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "implicit construction of interface must be rejected (#340)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM071"));
    }

    @Test
    void finalAbstractCombinationIsRejectedSem069(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                final abstract class Oops {}
                main() { println("ok") }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "final+abstract is a contradiction (#341)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM069"), "must be the modifier-conflict code: " + diags);
        assertTrue(diags.contains("Oops"), "must name the class: " + diags);
    }

    @Test
    void legalShapesUnaffected(@TempDir Path tempDir) throws Exception {
        // controls (Q3 negative face): extends non-final, implements +
        // construction of CLASS, abstract-only, final-only — all keep compiling.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                final class Frozen { Int v = 1 }
                abstract class Base { abstract Int go() }
                class Impl extends Base { Int go() { return 5 } }
                interface Shape { Int area() }
                class Sq implements Shape { Int area() { return 4 } }
                main() {
                    var s = Sq()
                    println(s.area() + Impl().go() + Frozen().v)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "legal class shapes must keep compiling: "
                + result.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor());
        assertEquals("10", out, "4+5+1");
    }
}
