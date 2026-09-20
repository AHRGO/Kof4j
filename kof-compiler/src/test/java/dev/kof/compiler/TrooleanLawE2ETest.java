package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * D-TROOL (19/09, DECISIONS.md d6cf5042) — a LEI tres-estado de {@code Troolean}
 * em {@code &&}/{@code ||}/{@code !}.
 *
 * <p>{@code Bool} voltou a ser dois-valores: {@code Bool?} nao existe mais —
 * quem precisa de verdadeiro/falso/desconhecido escreve {@code Troolean}
 * (SEM095 força a migração). O lowering dobra cada {@code &&}/{@code ||} com
 * lado {@code Troolean} numa cadeia {@code IfExpr} com temporarios
 * {@code $klt} (nao em IR, para o JS dobrar o short-circuit junto), e o
 * tipo do resultado passa a ser {@code Nullable(BOOL)} — Kleene de verdade:
 * {@code true && null = null}, {@code false || null = null}.
 *
 * <p>Os goldens desta matrix vem do ORACULO MEDIDO nos probes
 * {@code /tmp/opencode/trool/{and9,or9}} (T/F/U via {@code show3}) com o jar
 * da unidade landed (kof-cli-0.4.7-beta, 19/09 ~20:05) — IDENTICOS em
 * JVM+JS+Script+Native-x86-64. Aqui se provam JVM+Script+JS (oNative fica com
 * as E2Es nativas existentes, §306).
 */
class TrooleanLawE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String TROOLS = """
            Troolean nb() { return null }
            Troolean fb() { return false }
            Troolean tb() { return true }
            """;

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            // Runner por reflexao: o launcher java mascara VerifyError de
            // "JavaFX runtime ausente" (§149) — nunca aceitar a mensagem.
            Path runnerDir = outDir.resolveSibling(outDir.getFileName() + "-runner");
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            java.nio.file.Path javaHome = java.nio.file.Path.of(System.getProperty("java.home"));
            Process pCompile = new ProcessBuilder(javaHome.resolve("bin").resolve("javac").toString(),
                    "-d", runnerDir.toString(), runnerSrc.toString())
                    .redirectErrorStream(true).start();
            String compileOut = new String(pCompile.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, pCompile.waitFor(), "runner javac: " + compileOut);
            String classpath = String.join(java.io.File.pathSeparator, outDir.toString(), runnerDir.toString());
            Process p = new ProcessBuilder(javaHome.resolve("bin").resolve("java").toString(),
                    "-cp", classpath, "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return assertTarget("JVM", ec, output, expected);
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runScript(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        try {
            KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), tempDir, new String[0]);
            return assertTarget("SCRIPT", r.exitCode(), r.stdout().replace("\r\n", "\n").trim(), expected);
        } catch (KofInterpretException e) {
            fail("SCRIPT frontend error: " + e.getMessage());
            return null;
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws IOException {
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

    private void runAll3(Path tempDir, String source, String expected) throws IOException {
        runJvm(tempDir, source, expected);
        runScript(tempDir, source, expected);
        runJs(tempDir, source, expected);
    }

    private String assertTarget(String target, int ec, String output, String expected) {
        assertEquals(0, ec, target + " exit code, output: " + output);
        assertEquals(expected, output, target + " output");
        return output;
    }

    private CompilationResult compileOnly(Path tempDir, String name, String source) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, source);
        return driver.compile(file, tempDir.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private static String show3(List<String> exprs) {
        StringBuilder sb = new StringBuilder(TROOLS).append("""
                show3(x: Troolean) {
                    if (x == null) { println("U") } else if (x) { println("T") } else { println("F") }
                }
                main() {
                """);
        for (String e : exprs) {
            sb.append("    show3(").append(e).append(")\n");
        }
        return sb.append("}\n").toString();
    }

    // ---- a matriz de Kleene (oraculo medido nos 4 targets) -----------------

    @Test
    void andTableIsKleene(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, show3(List.of(
                "tb() && tb()", "tb() && fb()", "tb() && nb()",
                "fb() && tb()", "fb() && fb()", "fb() && nb()",
                "nb() && tb()", "nb() && fb()", "nb() && nb()")),
                "T\nF\nU\nF\nF\nF\nU\nF\nU");
    }

    @Test
    void orTableIsKleene(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, show3(List.of(
                "tb() || tb()", "tb() || fb()", "tb() || nb()",
                "fb() || tb()", "fb() || fb()", "fb() || nb()",
                "nb() || tb()", "nb() || fb()", "nb() || nb()")),
                "T\nT\nT\nT\nF\nU\nT\nU\nU");
    }

    @Test
    void notTableIsKleene(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, show3(List.of(
                "!tb()", "!fb()", "!nb()")),
                "F\nT\nU");
    }

    @Test
    void logicalResultPrintsNullWhenUnknown(@TempDir Path tempDir) throws IOException {
        // A face impressa do U (medida igual em JVM/Script/JS): println de
        // resultado tres-estado = "null", nao "false" (a materializacao do
        // #486 morreu com o D-TROOL).
        runAll3(tempDir, TROOLS + """
                main() {
                    println(true && fb())
                    println(false || tb())
                    println(true && nb())
                    println(false || nb())
                }
                """, "false\ntrue\nnull\nnull");
    }

    // ----消费者 legitim: narrowing, nunca cegueira ---------------------------

    @Test
    void equalsNullDiscriminatesUnknown(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, TROOLS + """
                main() {
                    println(fb() == null)
                    println(nb() == null)
                    println(tb() == null)
                }
                """, "false\ntrue\nfalse");
    }

    @Test
    void unknownIsFalsyInConditionals(@TempDir Path tempDir) throws IOException {
        // if/while/if-expr leem o slot com identidade Boolean.TRUE:
        // null e FALSE caem no ramo falso (mesma leitura do §306).
        runAll3(tempDir, TROOLS + """
                main() {
                    if (nb()) { println("T") } else { println("F") }
                    val v = if (nb()) "T" else "F"
                    println(v)
                }
                """, "F\nF");
    }

    // ---- o curto-circuito onde a tabela permite -----------------------------

    @Test
    void decisiveSideShortCircuitsEvenWithUnknown(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, TROOLS + """
                Bool rhs() { println("RHS"); return true }
                main() {
                    var a = fb() && rhs()
                    var b = tb() || rhs()
                    println("done")
                }
                """, "done");
    }

    @Test
    void unknownLeftSideMustEvaluateRightSide(@TempDir Path tempDir) throws IOException {
        // Medido (P.kf): null && rhs()=true avalia o RHS mas fica U (Kleene);
        // null || rhs()=true decide T. O RHS roda nos dois.
        runAll3(tempDir, TROOLS + """
                Bool rhs() { println("RHS"); return true }
                main() {
                    var a = nb() && rhs()
                    var b = nb() || rhs()
                    show(a)
                    show(b)
                }
                show(x: Troolean) {
                    if (x == null) { println("U") } else if (x) { println("T") } else { println("F") }
                }
                """, "RHS\nRHS\nU\nT");
    }

    // ---- SEM095: Bool? nao existe mais (as duas grafias) ---------------------

    @Test
    void boolOptionalSyntaxRejectedWithSem095(@TempDir Path tempDir) throws IOException {
        for (String spelling : List.of("Bool?", "Boolean?")) {
            CompilationResult r = compileOnly(tempDir,
                    "Sem095-" + spelling.charAt(0) + ".kf",
                    "main() { var x: " + spelling + " = true }\n");
            assertFalse(r.success(), spelling + " deve ser rejeitado");
            String codes = r.diagnostics().getDiagnostics().toString();
            assertTrue(codes.contains("SEM095"), spelling + " -> " + codes);
            assertTrue(codes.toLowerCase().contains("troolean"), spelling + " -> " + codes);
        }
    }

    @Test
    void boolOptionalReturnAndParamRejectedWithSem095(@TempDir Path tempDir) throws IOException {
        CompilationResult fn = compileOnly(tempDir, "Sem095Fn.kf",
                "Bool? f() { return true }\nmain() { println(f()) }\n");
        assertFalse(fn.success());
        assertTrue(fn.diagnostics().getDiagnostics().toString().contains("SEM095"),
                fn.diagnostics().getDiagnostics().toString());

        CompilationResult par = compileOnly(tempDir, "Sem095Par.kf",
                "void handle(Bool? flag) { }\nmain() { handle(true) }\n");
        assertFalse(par.success());
        assertTrue(par.diagnostics().getDiagnostics().toString().contains("SEM095"),
                par.diagnostics().getDiagnostics().toString());
    }

    @Test
    void plainBoolKeepsTwoValueContract(@TempDir Path tempDir) throws IOException {
        // A face #487 sobrevive INTEIRA para operandos Bool: resultado Bool,
        // atribuivel, retornavel, sem caixa.
        runAll3(tempDir, """
                Bool f() { return false }
                Bool g() { return f() && true }
                main() {
                    Bool x = f() && true
                    println(x)
                    println(g())
                    println(!f())
                }
                """, "false\nfalse\ntrue");
    }
}
