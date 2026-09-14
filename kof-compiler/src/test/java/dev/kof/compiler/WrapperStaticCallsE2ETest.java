package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #233 regression (root cause found by lane bugs-and-gaps 14/09).
 *
 * The wrapper-static branch added to {@code ExpressionMethodCallLowerer} matched
 * a builtin receiver ({@code String}/{@code Int}/...) even when the external
 * classpath was present but did NOT contain the class (the common case: plain
 * Kof compilation), and then emitted NOTHING for calls other than
 * {@code isNaN}/{@code isInfinite}/{@code isFinite}. The call was silently
 * dropped (R6), so the enclosing {@code println} emitted its own
 * {@code String.valueOf(Object)} with no value on the stack →
 * ASM COMPUTE_FRAMES crash ({@code NegativeArraySizeException: -1}).
 *
 * Before the fix, {@code String.valueOf(char)} inside {@code println} broke;
 * {@code Double.isNaN} (#233) and the {@code parse*} statics were affected the
 * same way when their wrapper branch did not match.
 */
class WrapperStaticCallsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    private void runBoth(String source, String expected, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(outJvm), name + " JVM output mismatch");
        assertEquals(expected, runJs(outJs), name + " JS output mismatch");
    }

    // Exact repro of the regression: two `String.valueOf(char)` calls inside
    // println. The first crashed ASM; both must return the UTF-8 char.
    @Test
    void stringValueOfCharInsidePrintln(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    println(String.valueOf(104 as Char))
                    println(String.valueOf(72 as Char))
                }
                """, "h\nH", tempDir, "wrap-valueof-char");
    }

    // valueOf of other primitives must keep working (same swallowed branch).
    @Test
    void stringValueOfPrimitivesInsidePrintln(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    println(String.valueOf(42))
                    println(String.valueOf(7L))
                    println(String.valueOf(true))
                }
                """, "42\n7\ntrue", tempDir, "wrap-valueof-primitives");
    }

    // #233 face: Double/Float isNaN/isInfinite (the branch that motivated the
    // code) plus the parse* statics that also flow through the same dispatch.
    //
    // JVM only: the JS backend does NOT lower wrapper statics
    // (`Double.isNaN`/`Int.parseInt` → `ReferenceError: java_lang_Double is
    // not defined`). That gap is PRE-EXISTING (reproduced at 59359935, before
    // the regression) and lives in the JS emitter, not in this dispatch —
    // catalogued as §235. Claiming JS parity here would be a false green.
    @Test
    void wrapperIsAndParseStatics(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("wrap-is-parse.kf");
        Files.writeString(src, """
                main() {
                    var d: Double = 0.0 / 0.0
                    println(Double.isNaN(d))
                    println(Double.isInfinite(d))
                    println(Int.parseInt("42") + 1)
                    println(Long.parseLong("100") * 2L)
                    println(Double.parseDouble("3.5") + 0.5)
                    println(Bool.parseBoolean("true"))
                }
                """);
        Path out = tempDir.resolve("wrap-is-parse-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("true\nfalse\n43\n200\n4.0\ntrue", runJvm(out));
    }
}
