package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #313 — `throw new ExceptionType("...")` with a JDK throwable emitted the
 * BARE simple name in the `new`/`invokespecial` instructions
 * (`new Exception` → NoClassDefFoundError at load), while the exception
 * TABLE was correctly qualified (`java/lang/Exception`) because it goes
 * through a different path (`CompilerTypes.exceptionType`, #163/#241).
 * Same for `class E extends RuntimeException` without an import (the super
 * came out `RuntimeException` bare → the class itself failed to load).
 *
 * Root fix in the shared resolver `CompilerTypes.toType` (java.lang
 * throwables always carry their package) + the extends-super resolution in
 * `SymbolTableBuilder`. Additive: the java.lang throwable name set is
 * precise (no primitive, no user class, no `Object`/`String`); the full
 * suite is the oracle.
 */
class ThrowQualifiedTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws Exception {
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code (NoClassDefFoundError here IS the bug)");
        return out;
    }

    @Test
    void throwNewJdkExceptionLoadsAndIsCaught(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    try {
                        throw new Exception("oops")
                    } catch (Exception e) {
                        println("caught: " + e.getMessage())
                    }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("caught: oops", runJvm(tempDir.resolve("out")),
                "throw new Exception must emit java/lang/Exception (old: bare 'Exception' → NoClassDefFoundError)");
    }

    @Test
    void throwNewIllegalArgumentQualifiedAndCaught(@TempDir Path tempDir) throws Exception {
        // second throwable family member — not a one-off special case.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    try {
                        throw new IllegalArgumentException("bad arg")
                    } catch (IllegalArgumentException e) {
                        println("iae: " + e.getMessage())
                    } catch (String e) {
                        println("string path")
                    }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("iae: bad arg", runJvm(tempDir.resolve("out")),
                "IllegalArgumentException must also resolve to java/lang/");
    }

    @Test
    void classExtendsThrowableWithoutImportLoads(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class MyEx extends RuntimeException {
                    public constructor() { }
                }
                main() { println("ok") }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        // The load used to die here: `class MyEx extends RuntimeException`
        // (no import) emitted the super as bare `RuntimeException`.
        assertEquals("ok", runJvm(tempDir.resolve("out")),
                "extends RuntimeException (no import) must emit java/lang/RuntimeException as super");
    }

    @Test
    void stringThrowStillWorksAndUserClassNotHijacked(@TempDir Path tempDir) throws Exception {
        // control: the canonical Kof path (throw String) is unaffected, and
        // the qualifier is scoped to the java.lang throwable NAME SET, not
        // every simple name (a user class stays in its own package).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class MyErr extends Exception {
                    public constructor() { }
                }
                main() {
                    try {
                        throw "plain string"
                    } catch (String s) {
                        println("str: " + s)
                    }
                    var m = new MyErr()
                    println("constructed")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        String out = runJvm(tempDir.resolve("out"));
        assertEquals("str: plain string\nconstructed", out,
                "String throw (Kof canonical) + a user class extending a JDK throwable both still work");
    }
}
