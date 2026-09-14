package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #204 — block lambda sem return inferido como () -> UnknownType era
 * rejeitado em parametro () -> void (SEM014). Fix veio junto com #180
 * (SemExpressionTyper, efda67b2) — #180 testou (Int) -> void com parametro;
 * esta prova cobre o caso exato de #204: no-arg `() -> void`.
 */
class LambdaVoidInferenceE2ETest {

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

    @Test
    void noArgBlockLambdaAcceptedWhereVoidFunctionExpected(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("voidlambda.kf");
        Files.writeString(src, """
                void invoke(() -> void cb) { cb() }
                main() {
                    invoke(() -> { println("hi") })
                    invoke(() -> { var lst = listOf(0); lst.add(1) })
                    invoke(() -> { println("two statements") })
                }
                """);
        Path out = tempDir.resolve("voidlambda-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (SEM014 #204?): " + r.diagnostics().getDiagnostics());
        assertEquals("hi\ntwo statements", runJvm(out));
    }

    @Test
    void blockLambdaStoredInVoidFunctionVariable(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("voidvar.kf");
        Files.writeString(src, """
                main() {
                    var g: () -> void = () -> { println("stored") }
                    g()
                }
                """);
        Path out = tempDir.resolve("voidvar-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("stored", runJvm(out));
    }
}
