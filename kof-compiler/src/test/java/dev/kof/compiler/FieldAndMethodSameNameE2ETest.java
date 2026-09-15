package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #219 — field access b.field resolves to method call when a method has the same name.
 *
 * `b.size` (sem parênteses) deve emitir `getfield Box.size:I` e retornar o valor do campo (7).
 * `b.size()` deve chamar o método e retornar 99.
 */
class FieldAndMethodSameNameE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

@SuppressWarnings("ProcessBuilderCommandInjection")
    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            String javaCmd = System.getProperty("java.home") + "/bin/java";
            Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void fieldAndMethodSameNameJvm(@TempDir Path tmp) throws Exception {
        // #219 minimal reproduction
        runJvm(tmp, """
                class Box {
                    Int size = 7
                    Int size() { return 99 }
                }
                main() {
                    var b = new Box()
                    println(b.size)
                    println(b.size())
                }
                """, "7\n99");
    }

    @Test
    void fieldMutationAndMethodSameNameJvm(@TempDir Path tmp) throws Exception {
        // Write to field and verify read vs method
        runJvm(tmp, """
                class Box {
                    Int count = 10
                    Int count() { return 42 }
                }
                main() {
                    var b = new Box()
                    b.count = 25
                    println(b.count)
                    println(b.count())
                }
                """, "25\n42");
    }

    @Test
    void fieldAndMethodSameNameInsideClassMethodsJvm(@TempDir Path tmp) throws Exception {
        // Access within instance methods of the same class
        runJvm(tmp, """
                class Box {
                    Int size = 7
                    Int size() { return 99 }
                    Int getField() { return this.size }
                    Int callMethod() { return this.size() }
                }
                main() {
                    var b = new Box()
                    println(b.getField())
                    println(b.callMethod())
                }
                """, "7\n99");
    }

    @Test
    void fieldAndMethodSameNameNative(@TempDir Path tmp) throws Exception {
        // Cross-target: Native x86_64
        runNative(tmp, """
                class Box {
                    Int size = 7
                    Int size() { return 99 }
                }
                main() {
                    var b = new Box()
                    println(b.size)
                    println(b.size())
                }
                """, "7\n99");
    }

    @Test
    void fieldAndMethodSameNameInterpreter(@TempDir Path tmp) throws Exception {
        // Parity: Interpreter
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, """
                class Box {
                    Int size = 7
                    Int size() { return 99 }
                }
                main() {
                    var b = new Box()
                    println(b.size)
                    println(b.size())
                }
                """);
        KofInterpreter.Result r = driver.interpret(java.util.List.of(file), tmp, new String[0]);
        assertEquals(0, r.exitCode());
        assertEquals("7\n99", r.stdout().trim());
    }
}
