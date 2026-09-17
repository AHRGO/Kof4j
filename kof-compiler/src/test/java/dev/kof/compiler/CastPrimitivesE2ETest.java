package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #205 — `as`-cast para primitivo vindo de tipo de referência
 * (`var o: Object = 5; o as Int`) emitia CHECKCAST na caixa sem unbox:
 * VerifyError "Bad type on operand stack" (1-slot) ou COMP002 crash da
 * ASM COMPUTE_FRAMES (Long/Double, categoria-2).
 */
class CastPrimitivesE2ETest {

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

    private String compileAndRun(String name, String source, Path tempDir) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), name + " JVM compile failed: " + r.diagnostics().getDiagnostics());
        return runJvm(out);
    }

    @Test
    void castObjectToIntUnboxes(@TempDir Path tempDir) throws IOException {
        assertEquals("15", compileAndRun("asint", """
                main() {
                    var obj: Object = 5
                    var n = (obj as Int) * 3
                    println(n)
                }
                """, tempDir), "#205: Int (1 slot) — antes VerifyError");
    }

    @Test
    void castObjectToDoubleAndLongUnboxes(@TempDir Path tempDir) throws IOException {
        assertEquals("4.140000000000001\n200", compileAndRun("as2slot", """
                main() {
                    var d: Object = 3.14
                    println((d as Double) + 1.0)
                    var l: Object = 100L
                    println((l as Long) * 2L)
                }
                """, tempDir), "#205: Long/Double (2 slots) — antes COMP002 (crash da ASM)");
    }

    @Test
    void castObjectToBooleanUnboxes(@TempDir Path tempDir) throws IOException {
        assertEquals("yes", compileAndRun("asbool", """
                main() {
                    var b: Object = true
                    if ((b as Boolean)) { println("yes") } else { println("no") }
                }
                """, tempDir), "#205: Boolean — antes VerifyError no if");
    }

    @Test
    void castObjectToCharUnboxesAsIntegerWidth(@TempDir Path tempDir) throws IOException {
        // Char Kof é guardado BOXED como Integer (§104b-ii) — o unbox tem que
        // pedir intValue()I, não charValue()C (inexistente). O code point do
        // resultado é obtido de forma explícita (`as Int`) — D-PRINT: o
        // println de um Char imprimiria o CARÁTER, não o número.
        assertEquals("98", compileAndRun("aschar", """
                main() {
                    var c: Object = 97
                    println(((c as Char) + 1) as Int)
                }
                """, tempDir), "#205: Char via Integer-box — antes Integer.charValue()C inexistente");
    }

    @Test
    void castToReferenceUnchanged(@TempDir Path tempDir) throws IOException {
        assertEquals("ab!", compileAndRun("asref", """
                main() {
                    var s: Object = "ab"
                    println((s as String) + "!")
                }
                """, tempDir), "cast p/ referência: CHECKCAST puro, sem regresso (back-compat)");
    }

    @Test
    void castPrimitiveFromPrimitiveStillConverts(@TempDir Path tempDir) throws IOException {
        assertEquals("a\n101", compileAndRun("asprim", """
                main() {
                    var x = 97
                    println(x as Char)
                    var y = 100L
                    println((y as Int) + 1)
                }
                """, tempDir), "primitivo→primitivo: ramo numérico (I2x), não tocado pelo fix");
    }
}
