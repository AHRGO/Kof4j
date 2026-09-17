package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #325 — {@code record} com cláusula {@code implements} era rejeitado
 * em cascata (PARSE007): o parse de record coletava `implements` ANTES de
 * consumir os componentes `(Int x, Int y)`, ficava com a lista vazia e
 * deixava a cláusula pendurada na frente do corpo.
 */
class RecordImplementsE2ETest {

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
    void recordWithImplementsCompilesAndDispatches(@TempDir Path tempDir) throws IOException {
        // happy path (repro da issue): `implements` depois dos componentes.
        Path src = tempDir.resolve("main.kf");
        Files.writeString(src, """
                interface Describable {
                    String describe()
                }
                record Point(Int x, Int y) implements Describable {
                    String describe() { return x() + "," + y() }
                }
                main() {
                    var p = new Point(3, 4)
                    println(p.describe())
                }
                """);
        Path out = tempDir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "#325 record implements não compilou: " + r.diagnostics().getDiagnostics());
        assertEquals("3,4", runJvm(out));
    }

    @Test
    void recordWithoutImplementsStillCompiles(@TempDir Path tempDir) throws IOException {
        // regressão guard: a forma SEM implements (o caminho que já andava)
        // continua intacta — incluindo o corpo acessório.
        Path src = tempDir.resolve("plain.kf");
        Files.writeString(src, """
                record Point(Int x, Int y) {
                    Int sum() { return x() + y() }
                }
                main() {
                    println(new Point(2, 5).sum())
                }
                """);
        Path out = tempDir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "record sem implements regrediu: " + r.diagnostics().getDiagnostics());
        assertEquals("7", runJvm(out));
    }

    @Test
    void recordJavaOrderImplementsBeforeComponentsStillCompiles(@TempDir Path tempDir) throws IOException {
        // backward compat (regra 2): `record X implements Y(comps)` compila hoje
        // (o decompiler Fase E emitia essa ordem); o fix #325 passa a aceitar a
        // forma canonical E MANTER a antiga. Sem teste, a ordem-java seria um
        // ramo morto nao provado.
        Path src = tempDir.resolve("javaord.kf");
        Files.writeString(src, """
                interface Named { String name() }
                record NamedR implements Named(String name) { }
                main() {
                    var r = new NamedR("kim")
                    println(r.name())
                }
                """);
        Path out = tempDir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "ordem java (implements antes) regrediu: " + r.diagnostics().getDiagnostics());
        assertEquals("kim", runJvm(out));
    }

    @Test
    void recordWithMultipleImplementsCompiles(@TempDir Path tempDir) throws IOException {
        // edge: clausula multi-interface no meio do header.
        Path src = tempDir.resolve("multi.kf");
        Files.writeString(src, """
                interface A { Int a() }
                interface B { Int b() }
                record R(Int v) implements A, B {
                    Int a() { return v() }
                    Int b() { return v() + 1 }
                }
                main() {
                    var r = new R(7)
                    println(r.a() + r.b())
                }
                """);
        Path out = tempDir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "multi-implements falhou: " + r.diagnostics().getDiagnostics());
        assertEquals("15", runJvm(out));
    }
}
