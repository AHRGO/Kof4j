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
 * #278 (D-NULL-INTENT, atomic) — corpus de regressão do contrato
 * {@code Nullable(primitivo) = Absent | Present(T)} (I1-I8, ver
 * KOF_278_D_NULL_INTENT_SOLUCAO_2026-09-18.md). Casos anti-falso-verde
 * exigidos pelo documento (§8.3): cache de {@code Integer} não pode virar
 * identidade de wrapper (I6), zero/false não são ausência (I2/I5), e
 * Map.get/put/remove não podem inventar valor (I7).
 *
 * <p><b>Q0 MEDIDO (18/09, tip {@code c39a4406} de beta-0.4.0, mvn -pl
 * kof-compiler -am test -Dtest=NullablePrimitiveContractE2ETest) — 4/8
 * vermelho no JVM:</b>
 * <ul>
 *   <li>{@code returnNullPreservesRealNull}: esperado {@code true\nnull},
 *       medido {@code false\n0} — confirma o fold {@code null→0} ainda vivo
 *       no retorno (I2/I5), já catalogado no DOING.md (sweep 18/09,
 *       "no tip o fold unificou p/ 0 nos 4 alvos").</li>
 *   <li>{@code mapGetMissingKeyIsNullDistinctFromPresentZero}: esperado
 *       {@code true\nfalse\n0}, medido {@code false\nfalse\n0} — a chave
 *       ausente não distingue de {@code Present(0)} (I7).</li>
 *   <li>{@code mapPutOnNewKeyReturnsNullNotZero}/
 *       {@code mapRemoveOnAbsentKeyReturnsNullNotZero}: mesmo padrão —
 *       {@code put}/{@code remove} sem entrada anterior não retornam
 *       {@code null} (I7; raiz confirmada em código:
 *       {@code MemberCallTyper.java:232-233} tipa os dois como {@code V},
 *       não {@code V?}, ao contrário de {@code get} na linha 230, que já
 *       tem o SG-008/bug-87 correto).</li>
 * </ul>
 * <p>Os outros 4 ({@code returnZeroStaysDistinctFromNull},
 * {@code returnFalseStaysDistinctFromNull}, {@code integerCacheDoesNotFakeEquality},
 * {@code twoGenuineNullsAreEqualToEachOther}) já passam hoje, mas por
 * acidente: sem boxing real ainda implementado, {@code Int?} de retorno é só
 * um {@code int} primitivo na pilha — não há wrapper para colidir no cache
 * do {@code Integer} nem para descolar identidade de valor. Ficam como rede
 * de regressão para quando o boxing de verdade entrar (Commit B): se algum
 * deles ficar RED depois disso, é sinal de identidade de wrapper vazando
 * pro `==`, não do contrato sendo cumprido.
 *
 * <p>Alvos cobertos aqui: JVM/Script/JS (fase 1 da fila do D-NULL-INTENT em
 * DECISIONS.md — Native é fase 2, separada). {@code runAll3} para no
 * primeiro alvo que falhar (fail-fast), então o Q0 acima é só JVM; Script/JS
 * dos 4 casos vermelhos ficam pendentes de medição até o fix do JVM não
 * quebrar mais cedo na cadeia.
 */
class NullablePrimitiveContractE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            // Runner por reflexão: evita o launcher `java` mascarar
            // VerifyError como "JavaFX runtime ausente" (mesmo padrão de
            // NullablePrimitiveE2ETest/GenericOperandConcatE2ETest).
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
            Process pCompile = new ProcessBuilder("javac", "-d", runnerDir.toString(), runnerSrc.toString())
                    .redirectErrorStream(true).start();
            String compileOut = new String(pCompile.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, pCompile.waitFor(), "runner javac: " + compileOut);
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                    outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
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

    // ---- I2/I5: zero/false/0.0 não são ausência --------------------------

    @Test
    void returnNullPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? f() { return null }
                main() {
                    println(f() == null)
                    println(f())
                }
                """, "true\nnull");
    }

    @Test
    void returnZeroStaysDistinctFromNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? zero() { return 0 }
                main() {
                    println(zero() == null)
                    println(zero())
                }
                """, "false\n0");
    }

    @Test
    void returnFalseStaysDistinctFromNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Boolean? no() { return false }
                main() {
                    println(no() == null)
                    println(no())
                }
                """, "false\nfalse");
    }

    // ---- I6: igualdade lifted, NUNCA identidade de wrapper ----------------
    // Caso obrigatório do §5/I6 e §8.3 do documento: 10000 escapa do cache de
    // Integer (-128..127) do JDK — se o `==` comparar REFERÊNCIA do wrapper
    // em vez de desempacotar e comparar valor, dois retornos de 10000 (dois
    // objetos Integer DIFERENTES) dão `false`, quando o contrato exige `true`.

    @Test
    void integerCacheDoesNotFakeEquality(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? a() { return 10000 }
                Int? b() { return 10000 }
                main() {
                    println(a() == b())
                }
                """, "true");
    }

    @Test
    void twoGenuineNullsAreEqualToEachOther(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? ni() { return null }
                main() {
                    var a = ni()
                    var b = ni()
                    println(a == b)
                }
                """, "true");
    }

    // ---- I7: Map.get/put/remove não inventam valor -------------------------
    // MemberCallTyper.java:232-233 tipa put()/remove() como V (não V?) hoje —
    // contradiz Java Map (retorna o valor anterior/removido OU null) e o I7.

    @Test
    void mapGetMissingKeyIsNullDistinctFromPresentZero(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("zero", 0)
                    println(m.get("missing") == null)
                    println(m.get("zero") == null)
                    println(m.get("zero"))
                }
                """, "true\nfalse\n0");
    }

    @Test
    void mapPutOnNewKeyReturnsNullNotZero(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = new Map<String, Int>()
                    println(m.put("k", 1) == null)
                    println(m.put("k", 2))
                }
                """, "true\n1");
    }

    @Test
    void mapRemoveOnAbsentKeyReturnsNullNotZero(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("k", 7)
                    println(m.remove("missing") == null)
                    println(m.remove("k"))
                }
                """, "true\n7");
    }

    // ---- I2/I5 estendido: ramo null de if/switch/var explícito não é default ----
    // §125-ext (opção A) foldava CADA ramo null de if/switch p/ o default do
    // primitivo antes do fix — sobrevivia mesmo depois do Commit A/B tratarem
    // o `return null` DIRETO, porque o fold interceptava ANTES do join
    // heterogêneo (#57/§70) rodar. `foldNullablePrimBranches` agora só
    // dispara no Native (fase 2); JVM/Script/JS reusam o boxing in-branch já
    // testado (ExpressionLowerer#IfExpr, boxesOwnBranches).

    @Test
    void ifExprNullBranchPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? en(Int x) = if (x > 0) x else null
                main() {
                    println(en(7))
                    println(en(-7))
                }
                """, "7\nnull");
    }

    @Test
    void switchExprNullBranchPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? sw(Int x) = switch (x) { case 1 -> 10 default -> null }
                main() {
                    println(sw(1))
                    println(sw(2))
                }
                """, "10\nnull");
    }

    @Test
    void explicitTypedLocalNullBranchPreservesRealNull(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                main() {
                    Int? v = if (false) 9 else null
                    println(v)
                    Bool? bn = if (false) true else null
                    println(bn)
                }
                """, "null\nnull");
    }

    // ---- I2/I5 estendido: concatenação de String com Nullable(primitivo) null ----
    // `emitOperandToString` usava `TypeMetrics.isPrimitiveType` (desembrulha
    // Nullable) p/ decidir se precisa boxPrimitive — um `Int?` GENUÍNO
    // (Commit B: já chega boxed/aconst_null) caía no mesmo ramo que um
    // primitivo CRU e levava um segundo `Integer.valueOf(int)` sobre uma
    // REFERÊNCIA (VerifyError JVM; NPE silenciosa no interpretador).

    @Test
    void stringConcatWithNullNullablePrimitive(@TempDir Path tempDir) throws IOException {
        runAll3(tempDir, """
                Int? ni() { return null }
                main() {
                    println("a" + ni())
                    println(ni() + "b")
                }
                """, "anull\nnullb");
    }

}
