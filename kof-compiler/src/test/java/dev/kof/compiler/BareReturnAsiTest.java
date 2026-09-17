package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #343 — a bare `return` (early-exit, no value) with no semicolon glued the
 * NEXT line as its expression: `if (x < 0) return\n println(...)` parsed as
 * `return println(...)`, so the body statement vanished and the branch LOOKED
 * inverted (`doWork(5)` printed nothing, `doWork(-1)` printed the value).
 * Same AST on all targets (JVM/Script/JS measured). Fix: newline-ASI in
 * parseReturn — the return value only counts on the SAME line as the
 * keyword; otherwise it is a void return.
 */
class BareReturnAsiTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws Exception {
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        return out;
    }

    @Test
    void bareReturnDoesNotSwallowNextStatement(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                Void doWork(Int x) {
                  if (x < 0) return
                  println("working: " + x)
                }
                main() {
                  doWork(5)
                  doWork(-1)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        // #343 (issue reproducer): "working: 5" prints, "working: -1" must
        // NOT (early return). On the old parser the branch inverted and the
        // output was exactly "working: -1".
        assertEquals("working: 5", runJvm(tempDir.resolve("out")),
                "bare return must stop at the newline, not swallow the println");
    }

    @Test
    void bareReturnWithEqualityCondition(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                Void f(Int x) {
                  if (x == 0) return
                  println("nz: " + x)
                }
                main() {
                  f(5)
                  f(0)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("nz: 5", runJvm(tempDir.resolve("out")),
                "f(0) must early-return; only f(5) prints");
    }

    @Test
    void returnWithValueSameLineStillWorks(@TempDir Path tempDir) throws Exception {
        // the fix must NOT regress `return <expr>` on the same line.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                Int abs(Int x) {
                  if (x < 0) return 0 - x
                  return x
                }
                main() {
                  println(abs(5))
                  println(abs(-3))
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("5\n3", runJvm(tempDir.resolve("out")),
                "return with value on the same line keeps working");
    }
}
