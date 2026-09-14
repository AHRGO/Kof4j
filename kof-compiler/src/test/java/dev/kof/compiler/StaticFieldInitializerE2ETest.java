package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #221 — campo `static` com inicializador de EXPRESSAO (10*10, 5+3,
 * "foo"+"bar") ficava no default da JVM (0/null): nenhum <clinit> era
 * sintetizado nem as expressoes constantes eram dobradas. Mesmo familia do
 * #133 (<clinit> para inicializador nao-constante), mas aqui o corpo da
 * issue usa arimetica/concat LITERAL — coberto pelo dobramento em
 * compile-time + <clinit> do #133. Esta classe prova as 4 celulas do
 * reprodutor exato (MAX/SUM/TAG = expressao; LIT = literal).
 */
class StaticFieldInitializerE2ETest {

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
    void staticExpressionInitializersEvaluateJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("staticinit.kf");
        Files.writeString(src, """
                class Config {
                    static Int MAX = 10 * 10
                    static Int SUM = 5 + 3
                    static String TAG = "foo" + "bar"
                    static Int LIT = 100
                }
                main() {
                    println(Config.MAX)
                    println(Config.SUM)
                    println(Config.TAG)
                    println(Config.LIT)
                }
                """);
        Path out = tempDir.resolve("staticinit-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("100\n8\nfoobar\n100", runJvm(out));
    }
}
