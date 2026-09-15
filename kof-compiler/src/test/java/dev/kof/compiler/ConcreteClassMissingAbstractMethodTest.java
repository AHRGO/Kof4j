package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #256 — Concrete class missing abstract method implementations compiles without error.
 *
 * Uma classe concreta que estende uma classe abstrata sem implementar todos os
 * métodos abstratos deve ser rejeitada em tempo de compilação com erro SEM043,
 * em vez de falhar em tempo de execução com AbstractMethodError.
 */
class ConcreteClassMissingAbstractMethodTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void missingAbstractMethodImplementationIsRejectedWithSEM043(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                abstract class Shape {
                    abstract Int area()
                    abstract Int perimeter()
                }
                class Rect extends Shape {
                    Int w
                    Int h
                    Int area() { return w * h }
                    // perimeter() is NOT implemented
                }
                main() {
                    var r = new Rect()
                    println(r.area())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Compilation must fail when abstract method is not implemented");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM043".equals(d.code()) && d.message().contains("perimeter")),
                "Must report SEM043 naming the missing abstract method: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void completeAbstractMethodImplementationCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                abstract class Shape {
                    abstract Int area()
                    abstract Int perimeter()
                }
                class Rect extends Shape {
                    Int w
                    Int h
                    Int area() { return w * h }
                    Int perimeter() { return 2 * (w + h) }
                }
                main() {
                    var r = new Rect()
                    r.w = 3
                    r.h = 4
                    println(r.area())
                    println(r.perimeter())
                }
                """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation must succeed when all abstract methods are implemented: "
                + result.diagnostics().getDiagnostics());

        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code");
        assertEquals("12\n14", output, "JVM output");
    }

    @Test
    void abstractSubclassCanOmitImplementation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                abstract class Shape {
                    abstract Int area()
                }
                abstract class Polygon extends Shape {
                    // Allowed to not implement area() because Polygon is abstract
                }
                class Rect extends Polygon {
                    Int area() { return 42 }
                }
                main() {
                    var r = new Rect()
                    println(r.area())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Abstract subclass may omit implementation: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void concreteSubclassOfAbstractSubclassMissingImplementationIsRejected(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                abstract class Shape {
                    abstract Int area()
                }
                abstract class Polygon extends Shape {
                }
                class Rect extends Polygon {
                    // Rect is concrete and does not implement area() from Shape
                }
                main() {
                    var r = new Rect()
                    println(r)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Concrete subclass must implement transitive abstract methods");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM043".equals(d.code()) && d.message().contains("area")),
                "Must report SEM043 naming the missing transitive abstract method: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void wrongSignatureDoesNotSatisfyAbstractMethod(@TempDir Path tempDir) throws IOException {
        // §242 residual: the match must be by SIGNATURE, not by arity. A
        // same-arity overload with a different parameter type does NOT implement
        // the abstract — before this it compiled clean and blew up at runtime
        // with AbstractMethodError.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                abstract class Svc {
                    abstract Int run(String cmd)
                }
                class Impl extends Svc {
                    Int run(Int code) { return code }
                }
                main() { }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Same-arity wrong-type overload must NOT satisfy abstract 'run(String)'");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM043".equals(d.code())),
                "Must report SEM043 for the unimplemented 'run(String)': " + result.diagnostics().getDiagnostics());
    }

    @Test
    void correctSignatureAmongSameArityOverloadsSatisfiesAbstractMethod(@TempDir Path tempDir) throws IOException {
        // The positive edge of §242: the concrete class declares several
        // same-arity overloads, one of which matches by type — the abstract is
        // satisfied.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                abstract class Svc {
                    abstract Int run(String cmd)
                }
                class Impl extends Svc {
                    Int run(Int code) { return code }
                    Int run(String cmd) { return cmd.length }
                }
                main() {
                    var s = new Impl()
                    println(s.run("abcd"))
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "A matching signature among same-arity overloads must satisfy the abstract: "
                + result.diagnostics().getDiagnostics());
    }
}
