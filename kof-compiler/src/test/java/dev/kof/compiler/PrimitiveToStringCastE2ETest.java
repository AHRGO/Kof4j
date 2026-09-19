package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #293 — {@code as String} sobre LOCAL primitivo ({@code var s = x as String})
 * caía no ramo §213 de box + {@code CHECKCAST java/lang/String}: o boxed
 * (Integer/Boolean/Double/Character) NÃO é String → {@code ClassCastException}
 * em runtime no JVM (classe carregava, crash silencioso até executar — R6).
 * O oracle do comportamento esperado é JS/Script, que stringificam
 * ({@code 42}, {@code true}) — medido na issue e re-medido no tip.
 *
 * <p>Fix: {@link ExpressionBinaryLowerer} intercepta primitivo→String e desce
 * pela mesma rota do {@code x + ""} ( boxing + {@code String.valueOf} nos 4
 * alvos — concat de primitivo com String já é contrato verde da suíte).
 *
 * <p>Guardas de regressão (Q3): primitivo→Object continua boxando (§213),
 * referência→referência não ganha box (identidade), numérico→numérico segue
 * conversão (nada de String no meio).
 */
class PrimitiveToStringCastE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main");
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

    private static final String CAST_SOURCE = """
            main() {
                var x = 42
                var d = 1.5
                var b = true
                var c = 'A'
                println(x as String)
                println((0 - x) as String)
                println(d as String)
                println(b as String)
                println(c as String)
            }
            """;

    private static final String EXPECTED = "42\n-42\n1.5\ntrue\nA";

    @Test
    void primitiveAsStringsifiesOnJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("pt.kf");
        Files.writeString(src, CAST_SOURCE);
        Path out = tempDir.resolve("pt-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(out), "as String = stringify, não CCE (#293)");
    }

    @Test
    void primitiveAsStringsifiesSameOnScriptAndJs(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("ptp.kf");
        Files.writeString(src, CAST_SOURCE);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "Script");
        Path jsOut = tempDir.resolve("ptp-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder("node", jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals(EXPECTED, jout, "JS");
    }

    @Test
    void varInitWithAsStringsifiesExactIssueRepro(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("pv.kf");
        Files.writeString(src, """
                main() {
                    var x = 42
                    var s = x as String
                    println(s)
                }
                """);
        Path out = tempDir.resolve("pv-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("42", runJvm(out), "repro verbatim da #293 (var s = x as String)");
    }

    @Test
    void referenceCastsAndNumericCastsUnaffected(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ptg.kf");
        Files.writeString(src, """
                main() {
                    var o = 7 as Object
                    var s = "hi" as String
                    var l = 3 as Long
                    println(o)
                    println(s + "!")
                    println(l + 1)
                }
                """);
        Path out = tempDir.resolve("ptg-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("7\nhi!\n4", runJvm(out), "§213/§205/numérico intactos");
    }
}
