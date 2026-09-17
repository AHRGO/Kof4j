package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #328 — `catch (Int e)` (primitive) used to compile silently and the JVM
 * exception table got `java/lang/Int` (no such class) →
 * NoClassDefFoundError at load. Now rejected at compile time with SEM067.
 * #332 — `catch (Foo e)` with a user class that is NOT a Throwable emitted
 * `java/lang/Foo`; even with the correct internal name the JVM verifier
 * rejects it ("Catch type is not a subclass of Throwable", measured), so the
 * honest place is the compiler: SEM068 for non-throwable user classes, and
 * the backend mapper (#332 fix in JvmBackend.exceptionJvmType) resolves
 * DECLARED classes and import-qualified names instead of blindly
 * prefixing java/lang/. The gate lives in the shared semantic pass — all
 * four targets report it (JVM/JS/Native/Script measured).
 */
class CatchTypeValidationTest {

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
    void catchOnPrimitiveIsRejectedSem067(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                  try {
                    throw "oops"
                  } catch (Int e) {
                    println("int")
                  } catch (String e) {
                    println("string: " + e)
                  }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "primitive catch type must be rejected (#328)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM067"), "must be the primitive-catch code: " + diags);
        assertTrue(diags.contains("Int"), "must name the offending type: " + diags);
    }

    @Test
    void catchOnNonThrowableUserClassIsRejectedSem068(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class Foo {}
                main() {
                  try {
                    throw "err"
                  } catch (Foo e) {
                    println("caught Foo")
                  } catch (String e) {
                    println("string: " + e)
                  }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "non-throwable catch type must be rejected (#332)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM068"), "must be the not-Throwable code: " + diags);
        assertTrue(diags.contains("Foo"), "must name the offending class: " + diags);
    }

    @Test
    void catchOnUnknownSimpleNameIsSem011(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                  try {
                    throw "err"
                  } catch (Klaxon e) {
                    println("x")
                  }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "unknown simple name must not compile");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM011"),
                "unknown name is SEM011 (same invariant as everywhere else)");
    }

    // #332 face (a) — import-qualified simple name: `import java.io.IOException`
    // + `catch (IOException e)` emitted java/lang/IOException (NoClassDefFoundError).
    // The mapper now resolves it through the module imports; the handler is
    // valid bytecode and the program loads (the throw never matches IOException,
    // so the body runs and the catch stays honest-dead, as the JVM dictates).
    @Test
    void importQualifiedCatchTypeResolvesToItsPackage(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                import java.io.IOException
                main() {
                  try {
                    println("hi")
                  } catch (IOException e) {
                    println("io")
                  }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "import-qualified throwable must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("hi", runJvm(tempDir.resolve("out")),
                "must LOAD (old bug: NoClassDefFoundError java/lang/IOException at class init)");
    }

    @Test
    void stringAndJdkThrowableCatchesUnaffected(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                  try {
                    throw "boom"
                  } catch (RuntimeException e) {
                    println("re: " + e)
                  }
                  try {
                    throw "second"
                  } catch (String e) {
                    println("str: " + e)
                  }
                  try {
                    throw "third"
                  } catch (Exception e) {
                    println("ex ok")
                  }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String/JDK throwables stay legal (#163/#241): "
                + result.diagnostics().getDiagnostics());
        runJvm(tempDir.resolve("out"));
    }
}
