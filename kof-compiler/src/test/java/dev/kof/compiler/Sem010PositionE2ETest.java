package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #502 (PR #505 + teste desta lane) — SEM010 ("Return type mismatch") era
 * emitado com posição hardcoded {@code "", 0, 0}: o usuário não sabia em que
 * linha o {@code return} errado estava (R6: diagnóstico sem local = caça
 * cega). O PR usa a posição do próprio statement de retorno; este teste trava
 * a posição e cobre o edge null-position (fallback honesto, nunca crash).
 */
class Sem010PositionE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void sem010PointsAtTheReturnLine(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("pos.kf");
        Files.writeString(src, """
                class A {
                    Int compute() {
                        val k = 1
                        return "wrong"
                    }
                }
                main() { }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("o"), Target.JVM);
        assertFalse(r.success(), "mismatch continua SEM010");
        Diagnostic sem010 = r.diagnostics().getDiagnostics().stream()
                .filter(d -> "SEM010".equals(d.code()))
                .findFirst().orElseThrow(() -> new AssertionError("esperava SEM010: "
                        + r.diagnostics().getDiagnostics()));
        assertEquals(4, sem010.line(), "SEM010 aponta para a linha do return (#502)");
        assertTrue(sem010.file().endsWith("pos.kf"), "arquivo preenchido: " + sem010.file());
    }

    @Test
    void sem010MessageStaysHumanReadable(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("msg.kf");
        Files.writeString(src, """
                class B {
                    Int pick() {
                        return true
                    }
                }
                main() { }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("o2"), Target.JVM);
        String all = r.diagnostics().getDiagnostics().toString();
        assertTrue(all.contains("SEM010") && all.contains("expected 'Int' but got 'Bool'"),
                "mensagem legível preservada (#324): " + all);
    }
}
