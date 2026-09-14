package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #229 — switch-EXPRESSION só era aceito pelo parser em `var r = switch`.
 * A forma simples (`x = switch`) ja tem prova no #200
 * (switchExpressionAsRhsOfAssignment); esta classe cobre as DUAS formas que
 * o relatorio do #229 apontou como PARSE041 e que nao estao no teste do #200:
 * reatribuicao para var ja declarada e a forma COMPOSTA `total += switch`.
 */
class SwitchRhsAssignmentE2ETest {

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
    void switchAsRhsOfPlainAndCompoundAssignment(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("switchrhs.kf");
        Files.writeString(src, """
                main() {
                    var n = 0
                    var msg = switch (n) { case 0 -> "zero" default -> "other" }
                    n = 1
                    msg = switch (n) { case 0 -> "zero" default -> "one" }
                    println(msg)
                    var total = 0
                    var x = 1
                    total += switch (x) { case 1 -> 10 default -> 0 }
                    total -= switch (x) { case 1 -> 3 default -> 0 }
                    println(total)
                }
                """);
        Path out = tempDir.resolve("switchrhs-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (PARSE041 #229?): " + r.diagnostics().getDiagnostics());
        assertEquals("one\n7", runJvm(out));
    }
}
