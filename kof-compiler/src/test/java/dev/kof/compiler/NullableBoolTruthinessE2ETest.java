package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * §306 — faces LEITORAS de {@code Nullable(Bool)} deixadas pelo Commit B
 * (#278/#438, fechado no cluster escritor §295(b)).
 *
 * <p><b>Face (a) — JVM truthiness.</b> O slot {@code Bool?} é fisicamente
 * boxed ({@code java/lang/Boolean}) desde o Commit B, mas os sítios de
 * truthiness ({@code if}/{@code while}/{@code if-expr}/{@code ?:}) emitiam
 * {@code LOAD; INT 0; IF_ICMPNE} — {@code if_icmpne} sobre referência =
 * {@code VerifyError: Bad type on operand stack} no LOAD da classe (compila
 * limpo, morre ao carregar — o launcher JVM mascara de "JavaFX", §149).
 * O fix espelha o padrão de leitura do §295(a)/retorno: comparação de
 * identidade com {@code Boolean.TRUE} (null/FALSE → falso — mesma semântica
 * falsy de {@code null} no JS e do {@code 0}/{@code null} no interpretador),
 * auto-portada ao JVM ({@code needsErasureBoxing}); Script/JS/Native não
 * veem a mudança.
 *
 * <p><b>Face (b) — representação no Script.</b> {@code println} de um local
 * {@code Bool?} imprimia {@code 1}/{@code 0} (JVM/JS imprimem {@code true}/
 * {@code false}) — divergência cross-target (freeze rule 5).
 */
class NullableBoolTruthinessE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            // Runner por reflexão: evita o launcher `java` mascarar
            // VerifyError como "JavaFX runtime ausente" (§149).
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

    // ---- face (a): truthiness de Bool? em if/while/if-expr/ternário -------

    @Test
    void ifStmtOverNullableBoolTrue(@TempDir Path tempDir) throws IOException {
        // Verbatim do repro catalogado em §306(a).
        runAll3(tempDir, """
                main() {
                    Bool? b = true
                    if (b) { println("Y") } else { println("N") }
                }
                """, "Y");
    }

    @Test
    void ifStmtOverNullableBoolFalse(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Bool? b = false
                    if (b) { println("Y") } else { println("N") }
                }
                """, "N");
    }

    @Test
    void mapGetBoolPresentAndMissing(@TempDir Path tempDir) throws IOException {
        // Face reachable SEM o fix do escritor (bug 87): o valor chega como
        // referência; chave ausente = null → falsy (mesma semântica JS).
        runAll3(tempDir, """
                main() {
                    var m = mapOf("flag", true)
                    var hit: Bool? = m.get("flag")
                    var miss: Bool? = m.get("nope")
                    if (hit) { println("HIT") } else { println("NOHIT") }
                    if (miss) { println("MISS") } else { println("NO") }
                }
                """, "HIT\nNO");
    }

    @Test
    void ifExprOverNullableBool(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Bool? b = true
                    var s = if (b) "A" else "B"
                    println(s)
                }
                """, "A");
    }

    @Test
    void whileLoopOverNullableBool(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Bool? go = true
                    var n = 0
                    while (go) {
                        n = n + 1
                        if (n >= 3) { go = false }
                    }
                    println(n)
                }
                """, "3");
    }

    // ---- face (b): mesma representação impressa nos 3 targets -------------

    @Test
    void printNullableBoolLocalMatchesTargets(@TempDir Path tempDir) throws IOException {
        // §306(a)+(b): mesma representação impressa nos 3 alvos. A face (b)
        // fechou junto com o fix do box: o kof_box do interpretador coerz
        // Number→Boolean (alinhado com o slot boxed da JVM e o coerceFor do
        // get) — antes o println do slot Nullable(Bool) imprimia o Integer
        // cru do ofBool ("1/0") enquanto JVM/JS imprimiam "true/false".
        runAll3(tempDir, """
                main() {
                    Bool? b = true
                    println(b)
                    b = false
                    println(b)
                }
                """, "true\nfalse");
    }

    // ---- controles: Bool plano e narrowing não mudam de comportamento -----

    @Test
    void plainBoolTruthinessUnchanged(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Bool b = true
                    if (b) { println("P") } else { println("Q") }
                    println(b)
                }
                """, "P\ntrue");
    }

    @Test
    void nullableBoolNarrowingStillWorks(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = mapOf("flag", true)
                    var b: Bool? = m.get("nope")
                    if (b != null) { println("X") } else { println("Z") }
                }
                """, "Z");
    }

    // ---------------------------------------------------------------------
    // #462 — a fronteira CONDICAO -> VALOR. O §306 fechou a truthiness nos
    // sitios de CONDICAO (if/while/if-expr); aqui `&&`/`||` aparecem em
    // POSICAO DE VALOR (`println(x && y)`, `Bool b = ...`, `return ...`).
    //
    // Duas causas: (A) o `ExpressionTyper` do lowering nao tinha regra para
    // `&&`/`||` e herdava o tipo do operando ESQUERDO (`Bool?`), enquanto o
    // `TypeChecker` semantico ja diz `Bool` — o consumidor entao acreditava
    // num `Bool?` boxed e recebia um int primitivo; (B) so o LHS passava pelo
    // `nullableBoolTruthinessRewrite`, entao um RHS `Bool?` chegava ao join
    // como referencia enquanto o outro arco deixava int.
    // ---------------------------------------------------------------------

    /** T1 — reproducer verbatim da issue (value position, LHS `Bool?`). */
    @Test
    void logicalValuePositionWithNullableLhs(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Bool? fb() { return false }
                Bool? nb() { return null }
                Bool? tb() { return true }
                main() {
                    println(fb() && true)
                    println(tb() && true)
                    println(nb() || true)
                    println(fb() || false)
                }
                """, "false\ntrue\ntrue\nfalse");
    }

    /**
     * T2 — RHS tambem nullable (prova a causa B: RHS canonicalizado).
     * JVM + Script; a face JS vive no teste abaixo, por ser um gap ANTERIOR
     * e de outro subsistema (o JS nao passa por este bloco de IR).
     */
    @Test
    void logicalValuePositionWithNullableRhs(@TempDir Path tempDir) throws IOException {
        String src = """
                Bool? fb() { return false }
                Bool? nb() { return null }
                Bool? tb() { return true }
                main() {
                    println(true && fb())
                    println(false || tb())
                    println(true && nb())
                    println(false || nb())
                }
                """;
        String golden = "false\ntrue\nfalse\nfalse";
        runJvm(tempDir, src, golden);
        runScript(tempDir, src, golden);
    }

    /**
     * T2 face JS — comportamento ATUAL do backend JS, que NAO passa pelo bloco
     * de IR do short-circuit (`ExpressionBinaryLowerer`: `&& driver.target !=
     * Target.JS`). O JS emite `&&`/`||` crus, e a semantica do JavaScript e
     * devolver o OPERANDO (`true && null` → `null`), nao um `Bool` canonico.
     *
     * <p>Pre-existente e alheio a esta correcao: no caso `true && nb()` o
     * operando ESQUERDO ja era `Bool`, entao a regra nova do `ExpressionTyper`
     * nao muda nada aqui — o `null` vem do backend JS. Fica pinado para nao
     * mudar em silencio; a face esta na issue #486 e no ledger (§338).
     */
    @Test
    void logicalValuePositionWithNullableRhsJsGap(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                Bool? fb() { return false }
                Bool? nb() { return null }
                Bool? tb() { return true }
                main() {
                    println(true && fb())
                    println(false || tb())
                    println(true && nb())
                    println(false || nb())
                }
                """, "false\ntrue\nnull\nnull");
    }

    /** T3 — consumidor diferente de print (impede fix oportunista no println). */
    @Test
    void logicalResultAssignedToBoolLocal(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Bool? fb() { return false }
                main() {
                    Bool x = fb() && true
                    println(x)
                }
                """, "false");
    }

    /** T4 — retorno tipado `Bool`. */
    @Test
    void logicalResultReturnedAsBool(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Bool? fb() { return false }
                Bool g() { return fb() && true }
                main() { println(g()) }
                """, "false");
    }

    /** T5 — short-circuit preservado: o RHS nao pode ser avaliado. */
    @Test
    void logicalShortCircuitStillLazy(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Bool? rhs() {
                    println("RHS")
                    return true
                }
                main() {
                    println(false && rhs())
                    println(true || rhs())
                }
                """, "false\ntrue");
    }

    /** T6 — controle: `Bool` puro nao muda. */
    @Test
    void plainBoolLogicalValuePositionUnchanged(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    println(false && true)
                    println(true || false)
                }
                """, "false\ntrue");
    }
}
