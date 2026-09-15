package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #233 — Double/Float static predicate methods (isNaN/isInfinite/isFinite).
 *
 * Antes: o typer cegamente retornava String para qualquer método de 1 arg em
 * receiver builtin uppercase (`MethodCallTyper` ramo valueOf) e o emit caía no
 * returnType Unknown → o JVM emitia `Double.isNaN(D)Ljava/lang/String;`
 * (NoSuchMethodError @runtime). A feature agora existe de verdade (Q7) nos
 * 3 alvos com paridade: JVM invokestatic (D)Z / (F)Z; JS Number.isNaN /
 * Number.isFinite / !isFinite&&!NaN (isInfinite).
 *
 * Q3 coberto: NaN (edge), Infinity (edge), valor normal, Float e Double,
 * isFinite, paridade JVM×JS (mesma saída).
 */
class DoubleFloatStaticE2ETest {

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
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    @Test
    void doublePredicatesJvmAndJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("double_pred.kf");
        Files.writeString(src, """
                main() {
                    var d: Double = 0.0 / 0.0
                    println(Double.isNaN(d))
                    println(Double.isInfinite(d))
                    var inf: Double = 1.0 / 0.0
                    println(Double.isInfinite(inf))
                    println(Double.isNaN(inf))
                    var norm: Double = 42.0
                    println(Double.isNaN(norm))
                    println(Double.isInfinite(norm))
                    println(Double.isFinite(norm))
                }
                """);
        Path outJvm = tempDir.resolve("double_pred-jvm");
        Path outJs = tempDir.resolve("double_pred-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        // golden: NaN→isNaN T/isInfinite F; Infinity→isInfinite T/isNaN F;
        // 42.0→ambos F, isFinite T
        String expected = "true\nfalse\ntrue\nfalse\nfalse\nfalse\ntrue";
        assertEquals(expected, runJvm(outJvm), "Double predicates JVM output mismatch");
        assertEquals(expected, runJs(outJs), "Double predicates JS output mismatch");
    }

    @Test
    void floatPredicatesJvmAndJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("float_pred.kf");
        Files.writeString(src, """
                main() {
                    var f: Float = 0.0 / 0.0
                    println(Float.isNaN(f))
                    println(Float.isInfinite(f))
                    var finf: Float = 1.0 / 0.0
                    println(Float.isInfinite(finf))
                    var fnorm: Float = 1.5
                    println(Float.isNaN(fnorm))
                    println(Float.isFinite(fnorm))
                }
                """);
        Path outJvm = tempDir.resolve("float_pred-jvm");
        Path outJs = tempDir.resolve("float_pred-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        String expected = "true\nfalse\ntrue\nfalse\ntrue";
        assertEquals(expected, runJvm(outJvm), "Float predicates JVM output mismatch");
        assertEquals(expected, runJs(outJs), "Float predicates JS output mismatch");
    }
}
