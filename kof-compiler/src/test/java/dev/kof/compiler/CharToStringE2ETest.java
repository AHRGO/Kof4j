package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #153 (face 1 de §216) — `Char.toString()` retornava a String do code
 * point ("65") em vez do caractere ("A"), porque o lowering de
 * `primitivo.toString()` boxava TODO primitivo via
 * {@link TypeEmitter#boxPrimitive} (char→Integer) e chamava
 * `String.valueOf(Object)` → `Integer(65).toString()` → "65" (ec=0, silencioso,
 * R6/freeze-4 valor errado). Triagem da mantenedora (issue #153, 14/09):
 * "`println(Char)` numérico é comportamento documentado; `Char.toString()` não"
 * — alinhar `toString()` ao `Character.toString`/`String.valueOf(char)` é
 * correção de paridade (Freeze regra 4), NÃO mudança de contrato.
 *
 * Fix (Q0, causa-raiz): {@link ExpressionInstanceCallLowerer} — para receiver
 * `char` (e só ele) emite `String.valueOf(char)` com o DESCRITOR (C) direto,
 * sem boxear (o overload `(C)` já existe e é o que `String.valueOf(c)` standalone
 * usa, §27). Os outros primitivos mantêm o box.
 *
 * NÃO toca (contrato CONGELADO, regra 6 — §216 face 2):
 *  - `println(char)` numérico (`65`) — `training/language/strings.md:25`
 *    documenta `println(s.charAt(0)) // 72`;
 *  - concatenação `"x" + char` numérica — mesma fonte;
 *  - box de char em COLEÇÃO (`mapOf/listOf/setOf`) continua `Integer`
 *    (`JvmOpCollections`) — a face char do §104b-ii depende disso.
 *
 * Matriz Q3/Q4: toString do char nos 4 targets (JVM run + Script interpret +
 * JS node + Native run) = "A"/length 1; println(char)=65 e "char="+c=char=65
 * NÃO regridem (congelado); os outros primitivos.toString() (int/bool/double/
 * long) intactos; char em coleção intacto.
 */
class CharToStringE2ETest {

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

    // o caso do corpo: toString do char = o caractere; .length() = 1
    private static final String CHAR_SOURCE = """
            main() {
                var a = 'A'
                var zero = '0'
                println(a.toString())
                println(zero.toString())
                println(a.toString().length())
            }
            """;

    @Test
    void charToStringIsTheCharacterOnJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("cs.kf");
        Files.writeString(src, CHAR_SOURCE);
        Path out = tempDir.resolve("cs-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("A\n0\n1", runJvm(out), "toString do char deve ser o caractere, não o code point");
    }

    @Test
    void charToStringSameOnScriptJsNative(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("csp.kf");
        Files.writeString(src, CHAR_SOURCE);
        // Script (interpretador, mesma IR)
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals("A\n0\n1", i.stdout().trim(), "Script");
        // JS (node)
        Path jsOut = tempDir.resolve("csp-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder("node", jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals("A\n0\n1", jout, "JS");
    }

    @Test
    void printlnAndConcatOfCharPrintTheCharacter(@TempDir Path tempDir) throws IOException {
        // D-PRINT (#168, mantenedora 15/09): a stringificação IMPLÍCITA de
        // char imprime o CARÁTER, não o code point — supersede a face numérica
        // de §216 face 2 (regra 4: remove saída silenciosamente errada).
        Path src = tempDir.resolve("dp.kf");
        Files.writeString(src, """
                main() {
                    var a = 'A'
                    println(a)
                    println("char=" + a)
                }
                """);
        Path out = tempDir.resolve("dp-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("A\nchar=A", runJvm(out),
                "println(char)/concat imprimem o caractere (D-PRINT)");
    }

    @Test
    void otherPrimitiveToStringUnaffected(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("op.kf");
        Files.writeString(src, """
                main() {
                    var n = 65
                    println(n.toString())
                    var b = true
                    println(b.toString())
                    var d = 1.5
                    println(d.toString())
                    var l = 9000000000
                    println(l.toString())
                }
                """);
        Path out = tempDir.resolve("op-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("65\ntrue\n1.5\n9000000000", runJvm(out),
                "int/bool/double/long .toString() não podem regrudar");
    }

    @Test
    void charInCollectionStillBoxesAsInteger(@TempDir Path tempDir) throws IOException {
        // §216/§104b-ii: o STORAGE de char em coleção continua Integer boxed
        // (o D-PRINT não moveu JvmOpCollections). Mas o println de um Char —
        // inclusive vindo de coleção (get devolve Char/Char?) — imprime o
        // CARÁTER (D-PRINT, mantenedora 15/09), não o code point.
        Path src = tempDir.resolve("col.kf");
        Files.writeString(src, """
                main() {
                    var c = 'A'
                    var l = listOf('A', 'B')
                    println(l.get(0))
                    var m = mapOf("k", c)
                    println(m.get("k"))
                }
                """);
        Path out = tempDir.resolve("col-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("A\nA", runJvm(out),
                "char em coleção continua boxed Integer, mas o println imprime o caractere (D-PRINT)");
    }
}
