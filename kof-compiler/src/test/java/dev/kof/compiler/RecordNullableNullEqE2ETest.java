package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressão do §262 — record `T?` comparado com `null` (`== null` / `!= null`)
 * dava `NullPointerException` no JVM: o `==` de record virava
 * `left.equals(right)` (igualdade de conteúdo, bug 11/188) mesmo contra o
 * literal `null`, sem guarda no receiver.
 *
 * Contrato: `== null` é sempre comparação de REFERÊNCIA (if_acmp*), nunca
 * chamada de método. A igualdade de conteúdo (record × record) continua
 * valendo quando NENHUM dos lados é o literal `null`.
 */
class RecordNullableNullEqE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String RECORD_MISS = """
        record Point(Int x, Int y)
        Point? find(Bool yes) {
            if (yes) return Point(7, 8)
            return null
        }
        main() {
            var miss = find(false)
            if (miss == null) { println("miss-null") } else { println("miss-not-null") }
            var hit = find(true)
            if (hit != null) { println("hit") } else { println("hit-null") }
            println("done")
        }
        """;

    private static final String RECORD_MAP_MISS = """
        record Point(Int x, Int y)
        main() {
            var m = mapOf("k", Point(7, 8))
            var maybe = m.get("z")
            if (maybe == null) { println("missing") } else { println("found") }
            println("done")
        }
        """;

    private static final String RECORD_CONTENT_EQUALITY = """
        record Point(Int x, Int y)
        main() {
            var a = Point(1, 2)
            var b = Point(1, 2)
            var c = Point(3, 4)
            println(a == b)
            println(a == c)
            println(a != b)
            println("done")
        }
        """;

    private static final String EXACT_262 = """
        record Point(Int x, Int y)
        main() {
            var maybe: Point? = mapOf("k", Point(7, 8)).get("z")
            if (maybe == null) { println("is-null") } else { println("not-null") }
            var hit: Point? = mapOf("k", Point(7, 8)).get("k")
            if (hit != null) { println("hit") }
            println("done")
        }
        """;

    /**
     * §262 FACE (b): DOIS lados nulos, nenhum literal `null`. Antes: receiver
     * null chamava `equals` → NPE (JVM) / TypeError (JS) / SIGSEGV (Native);
     * e `Point? == Point?` de conteúdo caía no caminho de referência (false
     * silencioso). Agora é Objects.equals nos 4 alvos.
     */
    private static final String NULLABLE_RECORD_PAIR = """
        record Point(Int x, Int y)
        Point? mk(Bool yes) {
            if (yes) return Point(1, 2)
            return null
        }
        main() {
            var m = mapOf("k", Point(7, 8))
            var miss: Point? = m.get("z")
            var hit: Point? = m.get("k")
            println(miss == hit)
            println(miss != hit)
            println(hit == miss)
            var n1: Point? = m.get("z")
            var n2: Point? = m.get("z")
            println(n1 == n2)
            println(n1 != n2)
            var a: Point? = Point(1, 2)
            var b: Point? = Point(1, 2)
            println(a == b)
            if (miss == hit) { println("iguais") } else { println("diferentes") }
            if (a == b) { println("iguais") } else { println("diferentes") }
            println(mk(true) == mk(true))
            println(mk(false) == mk(false))
            var i = 0
            while (i < 1) {
                if (mk(false) == miss) { println("loop-cond-ok") }
                i = i + 1
            }
            println(a == b == (miss != hit))
            println("done")
        }
        """;

    @Test
    void exactRepro262Jvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, EXACT_262, "is-null\nhit\ndone");
    }

    @Test
    void recordNullableEqualsNullJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, RECORD_MISS, "miss-null\nhit\ndone");
    }

    @Test
    void recordMapMissEqualsNullJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, RECORD_MAP_MISS, "missing\ndone");
    }

    @Test
    void recordContentEqualityJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, RECORD_CONTENT_EQUALITY, "true\nfalse\nfalse\ndone");
    }

    @Test
    void recordNullableEqualsNullScript(@TempDir Path tmp) throws Exception {
        runScript(tmp, RECORD_MISS, "miss-null\nhit\ndone");
    }

    @Test
    void recordNullableEqualsNullJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, RECORD_MISS, "miss-null\nhit\ndone");
    }

    // ---- §262 face (b): nullableRecord == nullableRecord (Objects.equals) ----
    // `false,true,false,` (miss vs hit), `true,false,` (n1 vs n2 ambos null),
    // `true` (a==b conteúdo), depois os ifs: miss==hit → "diferentes",
    // a==b → "iguais", done.

    @Test
    void nullableRecordPairJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, NULLABLE_RECORD_PAIR, "false\ntrue\nfalse\ntrue\nfalse\ntrue\ndiferentes\niguais\ntrue\ntrue\nloop-cond-ok\ntrue\ndone");
    }

    @Test
    void nullableRecordPairJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, NULLABLE_RECORD_PAIR, "false\ntrue\nfalse\ntrue\nfalse\ntrue\ndiferentes\niguais\ntrue\ntrue\nloop-cond-ok\ntrue\ndone");
    }

    @Test
    void nullableRecordPairScript(@TempDir Path tmp) throws Exception {
        runScript(tmp, NULLABLE_RECORD_PAIR, "false\ntrue\nfalse\ntrue\nfalse\ntrue\ndiferentes\niguais\ntrue\ntrue\nloop-cond-ok\ntrue\ndone");
    }

    @Test
    void nullableRecordPairNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, NULLABLE_RECORD_PAIR, "false\ntrue\nfalse\ntrue\nfalse\ntrue\ndiferentes\niguais\ntrue\ntrue\nloop-cond-ok\ntrue\ndone");
    }

    // face (a) tambem coberta no native (aqui o literal null caia no ramo de
    // referencia if_acmp, que o nat ja suportava; prova que o guard do face b
    // nao o regressou) — reuse o EXACT_262.
    @Test
    void exactRepro262Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, EXACT_262, "is-null\nhit\ndone");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
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
            return assertTarget("JVM", ec, output, expected);
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runScript(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        try {
            KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), tempDir, new String[0]);
            return assertTarget("SCRIPT", r.exitCode(), r.stdout().trim(), expected);
        } catch (KofInterpretException e) {
            fail("SCRIPT frontend error: " + e.getMessage());
            return null;
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("js-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (java.io.InputStream) new java.io.ByteArrayInputStream(new byte[0]), out);
        return assertTarget("JS", ec, out.toString().replace("\r\n", "\n").trim(), expected);
    }

    private String assertTarget(String target, int ec, String output, String expected) {
        assertEquals(0, ec, target + " exit code, output: " + output);
        assertEquals(expected, output, target + " output");
        return output;
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("nat-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return assertTarget("NATIVE", ec, output, expected);
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }
}
