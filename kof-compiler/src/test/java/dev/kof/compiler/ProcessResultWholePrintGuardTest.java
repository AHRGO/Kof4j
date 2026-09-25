package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-FULL-PARITY-050 linha 1 (fatia A {@code process.run} no Native x86-64):
 * imprimir/concatenar o record {@code Result} INTEIRO no Native é um gap
 * honesto {@code PROC001} no compile-time.
 *
 * <p>Medido no tip que pousou a fatia A: {@code println(r)} compilava LIMPO e
 * o binário morria com SIGSEGV (exit 139) sem imprimir nada (valueOf lia o
 * header de um objeto opaco sem vtable/typeId). O guard
 * ({@code ProcessResultPrintGuard}) transforma isso no diagnóstico honesto;
 * no JVM o conteúdo continua imprimível (§367). A superfície suportada no
 * Native é o acesso a {@code .stdout}/{@code .stderr}/{@code .exitCode}.
 */
class ProcessResultWholePrintGuardTest {

    private static final String PRINT_WHOLE = """
            main() {
                var r = process.run("echo", "x")
                println(r)
            }
            """;

    private static final String CONCAT_WHOLE = """
            main() {
                var r = process.run("echo", "x")
                println("res: " + r)
            }
            """;

    private CompilationResult compile(Path dir, String program, Target target) throws Exception {
        Files.createDirectories(dir);
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, program);
        return new CompilerDriver().compile(source, dir.resolve("out"), target);
    }

    @Test
    void wholeResultPrintIsHonestProc001OnNative(@TempDir Path dir) throws Exception {
        String[] programs = {PRINT_WHOLE, CONCAT_WHOLE};
        for (int i = 0; i < programs.length; i++) {
            CompilationResult result = compile(dir.resolve("n" + i), programs[i], Target.NATIVE);
            assertFalse(result.success(),
                    "record inteiro no Native deve recusar em compile-time: " + programs[i]);
            String diags = result.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("PROC001"),
                    "diagnostico honesto PROC001 esperado, veio: " + diags);
        }
    }

    @Test
    void wholeResultPrintStillWorksOnJvm(@TempDir Path dir) throws Exception {
        assumeTrue(hasJava(), "java ausente");
        String[] programs = {PRINT_WHOLE, CONCAT_WHOLE};
        for (int i = 0; i < programs.length; i++) {
            CompilationResult result = compile(dir.resolve("j" + i), programs[i], Target.JVM);
            assertTrue(result.success(), "JVM deve compilar: "
                    + result.diagnostics().getDiagnostics());
            Process p = new ProcessBuilder("java", "-cp", dir.resolve("j" + i).resolve("out").toString(),
                    "Default.Main").redirectErrorStream(false).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit " + ec);
            assertTrue(out.contains("ProcessResult["),
                    "JVM mantem o toString de conteudo (§367), veio: [" + out + "]");
        }
    }

    private static boolean hasJava() {
        try {
            return new ProcessBuilder("java", "-version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
