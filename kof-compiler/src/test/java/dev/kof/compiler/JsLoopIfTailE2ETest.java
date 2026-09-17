package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §266 (KofJS, R6): um loop cujo corpo tem `if` SEGUIDO de statements
 * miscompilava SÓ no JS — o reconstrutor de CFG varria para trás do back-edge
 * procurando o continue label, parava no `Label(end)` do if sem `else` e
 * MARCAVA-O como continue: os statements posteriores ao `if` caíam na cláusula
 * update do `for(;…;…)` fora do escopo dos próprios `let` → `ReferenceError`
 * headless (e SILENCIOSO em `Component.view` — o catch do kofUiRender monta a
 * UI VAZIA). A correção é estrutural: o lowering (StatementLowerer, ForStmt +
 * ForInStmt) emite `KofContinueLabel(continue, startLabel)` rotulado com o dono;
 * `JsControlFlowParser` consome o marcador como fronteira corpo/update e o
 * heuristic guess post-hoc (looksLikeContinueLabel) saiu. Estes testes cobrem
 * as formas do registro Q1: while/for × cauda do corpo, if/else aninhado,
 * continue/break, for aninhado em while, for-in, for(;;), while(true).
 * Paridade JVM == JS com valores exatos.
 */
class JsLoopIfTailE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        String s = out.toString().trim();
        assertEquals(0, exitCode, "JS exit code, output: " + s);
        return s;
    }

    private void assertBoth(String program, String expected, Path tempDir, String name) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        Files.createDirectories(outJvm);
        Files.createDirectories(outJs);
        CompilationResult rjvm = driver.compile(source, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(source, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        String jvm = runJvm(outJvm);
        String js = runJs(outJs);
        assertEquals(expected, jvm, "JVM output");
        assertEquals(jvm, js, "stdout parity JVM vs JS");
    }

    @Test
    void whileIfTailParity(@TempDir Path tempDir) throws IOException {
        // o repro exato do registro: if sem else + `var r = v + flag` depois
        assertBoth("""
                main() {
                    var out = new List<Int>()
                    var p = 0
                    while (p < 3) {
                        var v = p + 100
                        var flag = 0
                        if (p == 1) { flag = 1 }
                        var r = v + flag
                        out.add(r)
                        p = p + 1
                    }
                    println(out.get(0) + "," + out.get(1) + "," + out.get(2))
                }
                """, "100,102,102", tempDir, "whileIfTail");
    }

    @Test
    void forIfTailParity(@TempDir Path tempDir) throws IOException {
        assertBoth("""
                main() {
                    var b = new List<Int>()
                    for (var i = 0; i < 3; i = i + 1) {
                        var w = i * 10
                        var mark = 0
                        if (i == 2) { mark = 5 }
                        var z = w + mark
                        b.add(z)
                    }
                    println(b.get(0) + "," + b.get(1) + "," + b.get(2))
                }
                """, "0,10,25", tempDir, "forIfTail");
    }

    @Test
    void continueRealWithTailParity(@TempDir Path tempDir) throws IOException {
        // `continue` REAL + if+trailing depois — o marcador é o dono da
        // fronteira; o continue do usuário é um Jump(continueLabel) normal.
        assertBoth("""
                main() {
                    var c = new List<Int>()
                    for (var k = 0; k < 5; k = k + 1) {
                        if (k == 2) { continue }
                        var cv = k + 1000
                        if (k == 3) { cv = cv + 1 }
                        c.add(cv)
                    }
                    println(c.size + " " + c.get(0) + "," + c.get(1) + "," + c.get(2) + "," + c.get(3))
                }
                """, "4 1000,1001,1004,1004", tempDir, "continueTail");
    }

    @Test
    void breakWithIfTailParity(@TempDir Path tempDir) throws IOException {
        assertBoth("""
                main() {
                    var d = new List<Int>()
                    var q = 0
                    while (q < 10) {
                        var dv = q
                        if (dv == 3) { q = q + 1; continue }
                        if (dv == 4) { break }
                        var tail = dv * 2
                        d.add(tail)
                        q = q + 1
                    }
                    println(d.size + " last=" + d.get(d.size - 1))
                }
                """, "3 last=4", tempDir, "breakTail");
    }

    @Test
    void nestedForInWhileParity(@TempDir Path tempDir) throws IOException {
        // marcador de loop INTERNO não pode responder pela varredura do externo
        assertBoth("""
                main() {
                    var e = new List<Int>()
                    var n = 0
                    while (n < 2) {
                        var sum = 0
                        for (var m = 0; m < 3; m = m + 1) {
                            sum = sum + m
                        }
                        var ev = n * 100 + sum
                        e.add(ev)
                        n = n + 1
                    }
                    println(e.get(0) + "," + e.get(1))
                }
                """, "3,103", tempDir, "nestedLoops");
    }

    @Test
    void forInWithIfTailParity(@TempDir Path tempDir) throws IOException {
        assertBoth("""
                main() {
                    var items = new List<String>()
                    items.add("x")
                    items.add("y")
                    items.add("z")
                    var f = new List<Int>()
                    var fidx = 0
                    for (var it in items) {
                        var fv = fidx
                        if (it == "y") { fv = fv + 10 }
                        f.add(fv)
                        fidx = fidx + 1
                    }
                    println(f.get(0) + "," + f.get(1) + "," + f.get(2))
                }
                """, "0,11,2", tempDir, "forInIfTail");
    }

    @Test
    void whileTrueBreakWithIfTailParity(@TempDir Path tempDir) throws IOException {
        // rota parseTrueLoop (condição literal true) com if+trailing no corpo
        assertBoth("""
                main() {
                    var h = new List<Int>()
                    var u = 0
                    while (true) {
                        var hv = u * 7
                        if (hv > 20) { break }
                        var ht = hv + u
                        h.add(ht)
                        u = u + 1
                    }
                    println(h.size + " last=" + h.get(h.size - 1))
                }
                """, "3 last=16", tempDir, "trueLoopTail");
    }
}
