package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #617 — método desconhecido em tipo builtin de kof.io (File/Path/Directory)
 * era aceito em silêncio: o typer caía para UNKNOWN, o emit não gerava
 * NADA para a chamada e o programa "rodava" — `File("x").mkdir()` imprimia
 * ok sem criar nada (no-op silencioso, R6) e `File("x").mkdir().toString()`
 * dava `ClassFormatError: Illegal class name ""` no JVM. Agora: SEM102
 * limpo no compile (mesma família do SEM028/array #17-#512).
 */
class IoUnknownMethodGuardTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String source, Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return driver.compile(src, tempDir.resolve("out"), Target.JVM);
    }

    @Test
    void fileMkdirFailsWithSem102InsteadOfSilentNoOp(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                File("/tmp/opencode/io_guard_a").mkdir()
                println("stmt ok")
            }
            """, tempDir);
        assertFalse(result.success(), "File.mkdir() must fail to compile (SEM102)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertTrue(diags.contains("mkdir"), "Diagnostic must name the method, was: " + diags);
        assertTrue(diags.contains("createDirectories"), "Diagnostic must point to the real idiom, was: " + diags);
    }

    @Test
    void fileMkdirToStringFailsWithSem102NotClassFormatError(@TempDir Path tempDir) throws IOException {
        // a face do sintoma B: `.toString()` sobre o retorno UNKNOWN chegava
        // ao JVM como `ClassFormatError: Illegal class name ""`.
        CompilationResult result = compile("""
            main() {
                println(File("/tmp/opencode/io_guard_b").mkdir().toString())
            }
            """, tempDir);
        assertFalse(result.success(), "File.mkdir().toString() must fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"), "Must be a compile diagnostic, not a crash: " + diags);
    }

    @Test
    void directoryMkdirAlsoRejectedWithHint(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                Directory("/tmp/opencode/io_guard_c").mkdir()
            }
            """, tempDir);
        assertFalse(result.success());
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "was: " + diags);
    }

    @Test
    void validIoMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        // controle: a tabela viva inteira continua compilando — o guard não
        // pode rejeitar membro legítimo (exists/delete/createDirectories/path).
        CompilationResult result = compile("""
            main() {
                var f = File("/tmp/opencode/io_guard_d.txt")
                println(f.exists())
                var d = Directory("/tmp/opencode/io_guard_d")
                println(d.createDirectories())
                println(f.path)
            }
            """, tempDir);
        assertTrue(result.success(), "Valid io members must compile: "
                + result.diagnostics().getDiagnostics());
    }
}
