package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #228 — if-expression with braces evaluated as expression instead of lambda.
 *
 * Anteriormente, `if (cond) { "yes" } else { "no" }` analisava `{ ... }`
 * como LambdaExpr (corpo de bloco de lambda sem argumentos) e atribuía o
 * objeto lambda `Lambda0@...` em vez de avaliar o ramo da expressão.
 */
class IfExprBracesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void ifExprWithBracesStringsJvm(@TempDir Path tmp) throws Exception {
        // #228 minimal reproduction
        runJvm(tmp, """
                main() {
                    var flag = true
                    var a = if (flag) { "yes" } else { "no" }
                    println(a)
                    var falseFlag = false
                    var b = if (falseFlag) { "yes" } else { "no" }
                    println(b)
                }
                """, "yes\nno");
    }

    @Test
    void ifExprWithBracesIntegersJvm(@TempDir Path tmp) throws Exception {
        // #228 integer variant
        runJvm(tmp, """
                main() {
                    var flag = true
                    var a = if (flag) { 42 } else { 0 }
                    println(a)
                    var falseFlag = false
                    var b = if (falseFlag) { 42 } else { 0 }
                    println(b)
                }
                """, "42\n0");
    }

    @Test
    void ifExprWithMixedBracesAndSemicolonJvm(@TempDir Path tmp) throws Exception {
        // Asymmetric braces and optional semicolon inside braces
        runJvm(tmp, """
                main() {
                    val cond = true
                    val r1 = if (cond) { "first"; } else "second"
                    val r2 = if (!cond) "first" else { "second"; }
                    println(r1)
                    println(r2)
                }
                """, "first\nsecond");
    }

    @Test
    void ifExprWithBracesNative(@TempDir Path tmp) throws Exception {
        // Cross-target: Native x86_64
        runNative(tmp, """
                main() {
                    var flag = true
                    var a = if (flag) { "yes" } else { "no" }
                    println(a)
                    var b = if (flag) { 100 } else { 200 }
                    println(b)
                }
                """, "yes\n100");
    }

    @Test
    void ifExprWithBracesInterpreter(@TempDir Path tmp) throws Exception {
        // Parity: Interpreter
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, """
                main() {
                    var flag = true
                    var a = if (flag) { "ok" } else { "fail" }
                    println(a)
                }
                """);
        KofInterpreter.Result r = driver.interpret(java.util.List.of(file), tmp, new String[0]);
        assertEquals(0, r.exitCode());
        assertEquals("ok", r.stdout().trim());
    }
}
