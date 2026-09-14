package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #241 — catch com nome QUALIFICADO (java.lang.RuntimeException)
 * gerava "java/lang/java.lang.RuntimeException" na exception table →
 * ClassFormatError ao carregar a classe. Raiz: exceptionJvmType() em
 * JvmBackend concatenava "java/lang/" sem olhar se o nome ja tinha ponto
 * (diferente do `new`, que passa pelo qualify/toInternalName).
 * Matriz Q3: nome qualificado exato da issue + java.lang.Exception
 * (borda: nome pontuado NAO primitivo-boxed) + simples RuntimeException
 * (nao-regressao) + catch String (Kof-canonical) + multi-branch
 * qualificado+simples no mesmo programa.
 */
class QualifiedCatchE2ETest {

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
    void qualifiedExceptionNameInCatchCompilesAndRuns(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("qualcatch.kf");
        Files.writeString(src, """
                main() {
                    try {
                        throw new java.lang.RuntimeException("boom")
                    } catch (java.lang.RuntimeException e) {
                        println("caught: " + e.getMessage())
                    }
                    try {
                        throw new java.lang.RuntimeException("x")
                    } catch (java.lang.Exception e) {
                        println("qual: " + e.getMessage())
                    }
                }
                """);
        Path out = tempDir.resolve("qualcatch-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("caught: boom\nqual: x", runJvm(out));
    }

    @Test
    void simpleAndStringCatchStillWork(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("mixcatch.kf");
        Files.writeString(src, """
                main() {
                    try {
                        throw new java.lang.RuntimeException("r")
                    } catch (Exception e) {
                        println("any: " + e.getMessage())
                    }
                    try {
                        throw "plain"
                    } catch (String e) {
                        println("str: " + e)
                    }
                }
                """);
        Path out = tempDir.resolve("mixcatch-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("any: r\nstr: plain", runJvm(out));
    }
}
