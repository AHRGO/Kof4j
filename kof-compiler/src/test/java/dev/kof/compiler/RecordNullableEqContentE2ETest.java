package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressao do 262 face (b) — `nullableRecord == nullableRecord` (sem literal
 * `null`) dava NPE: o `==` de record virava `receiver.equals(arg)` (igualdade
 * de conteudo, bug 11/188) sem guarda no receiver, quebrando nos 4 alvos.
 *
 * Contrato (Objects.equals, regra 5): null == null -> true; um lado null ->
 * false; ambos presentes -> igualdade de conteudo. Nenhum alvo pode
 * desreferenciar um receiver nulo.
 */
class RecordNullableEqContentE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String MISS_VS_HIT = """
        record Point(Int x, Int y)
        main() {
            var m = mapOf("k", Point(7, 8))
            var miss: Point? = m.get("z")
            var hit: Point? = m.get("k")
            println(miss == hit)
            println("done")
        }
        """;

    private static final String BOTH_NULL = """
        record Point(Int x, Int y)
        main() {
            var m = mapOf("k", Point(7, 8))
            var a: Point? = m.get("z")
            var b: Point? = m.get("w")
            println(a == b)
            println(a != b)
            println("done")
        }
        """;

    private static final String CONTENT_STILL = """
        record Point(Int x, Int y)
        main() {
            var m = mapOf("a", Point(1, 2), "b", Point(1, 2), "c", Point(9, 9))
            var x: Point? = m.get("a")
            var y: Point? = m.get("b")
            var z: Point? = m.get("c")
            println(x == y)
            println(x == z)
            println("done")
        }
        """;

    @Test
    void missVsHitJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, MISS_VS_HIT, "false\ndone");
    }

    @Test
    void bothNullJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, BOTH_NULL, "true\nfalse\ndone");
    }

    @Test
    void contentStillJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, CONTENT_STILL, "true\nfalse\ndone");
    }

    @Test
    void missVsHitScript(@TempDir Path tmp) throws Exception {
        runScript(tmp, MISS_VS_HIT, "false\ndone");
    }

    @Test
    void missVsHitJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, MISS_VS_HIT, "false\ndone");
    }

    @Test
    void bothNullJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, BOTH_NULL, "true\nfalse\ndone");
    }

    @Test
    void bothNullScript(@TempDir Path tmp) throws Exception {
        runScript(tmp, BOTH_NULL, "true\nfalse\ndone");
    }

    @Test
    void bothNullNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, BOTH_NULL, "true\nfalse\ndone");
    }

    @Test
    void contentStillNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, CONTENT_STILL, "true\nfalse\ndone");
    }

    @Test
    void missVsHitNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, MISS_VS_HIT, "false\ndone");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
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
            return assertTarget("JVM", ec, output, expected);
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runScript(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        try {
            KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), tempDir, new String[0]);
            return assertTarget("SCRIPT", r.exitCode(), r.stdout().trim(), expected);
        } catch (KofInterpretException e) {
            fail("SCRIPT frontend error: " + e.getMessage());
            return null;
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("js-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (java.io.InputStream) new java.io.ByteArrayInputStream(new byte[0]), out);
        return assertTarget("JS", ec, out.toString().replace("\r\n", "\n").trim(), expected);
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("nat-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "NATIVE compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary missing");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return assertTarget("NATIVE", ec, output, expected);
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String assertTarget(String target, int ec, String output, String expected) {
        assertEquals(0, ec, target + " exit code, output: " + output);
        assertEquals(expected, output, target + " output");
        return output;
    }
}
