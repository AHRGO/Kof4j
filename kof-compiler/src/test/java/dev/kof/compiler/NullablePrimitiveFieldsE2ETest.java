package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #252 — Nullable primitive fields (Int?, Boolean?, Double?, Long?) use unboxed JVM descriptor.
 *
 * Ao declarar campos primitivos anuláveis, o backend JVM deve emitir descritores de
 * referência empacotada (Ljava/lang/Integer;, Ljava/lang/Boolean;, Ljava/lang/Double;, Ljava/lang/Long;)
 * para que os campos sejam inicializados com null por padrão e possam representar null.
 */
class NullablePrimitiveFieldsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
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
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void nullableIntFieldUninitializedIsNullJvm(@TempDir Path tmp) throws Exception {
        // #252 minimal reproduction for Int?
        runJvm(tmp, """
                class Box {
                    Int? value
                }
                main() {
                    var b = new Box()
                    println(b.value == null)
                    println(b.value)
                }
                """, "true\nnull");
    }

    @Test
    void nullableBooleanFieldUninitializedIsNullJvm(@TempDir Path tmp) throws Exception {
        // #252 for Boolean?
        runJvm(tmp, """
                class Box {
                    Bool? flag
                }
                main() {
                    var b = new Box()
                    println(b.flag == null)
                    println(b.flag)
                }
                """, "true\nnull");
    }

    @Test
    void nullableDoubleFieldUninitializedIsNullJvm(@TempDir Path tmp) throws Exception {
        // #252 for Double?
        runJvm(tmp, """
                class Box {
                    Double? rate
                }
                main() {
                    var b = new Box()
                    println(b.rate == null)
                    println(b.rate)
                }
                """, "true\nnull");
    }

    @Test
    void nullablePrimitiveFieldAssignedValueJvm(@TempDir Path tmp) throws Exception {
        // Assigning primitive to nullable primitive field boxes it
        runJvm(tmp, """
                class Box {
                    Int? value
                    Bool? flag
                }
                main() {
                    var b = new Box()
                    b.value = 42
                    b.flag = true
                    println(b.value == null)
                    println(b.value)
                    println(b.flag == null)
                    println(b.flag)
                }
                """, "false\n42\nfalse\ntrue");
    }
}
