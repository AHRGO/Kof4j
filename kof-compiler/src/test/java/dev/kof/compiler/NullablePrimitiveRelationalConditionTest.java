package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-NULL-INTENT (#278/#438) — face RELACIONAL sobre primitivo nulável numa
 * CONDIÇÃO (if/while), buraco achado ao fechar o §279. O #278 trocou a
 * representação de {@code Int?} por box de referência no JVM e consertou o
 * caminho de VALOR da comparação ({@code ExpressionBinaryLowerer.isNumericComparison}
 * desempacota antes do {@code DCMP}/{@code IADD}), mas o caminho de
 * CONDICA0 ({@code CompilerComparisons.emitComparisonShortcut}, usado por if/while/
 * print-if para emitir {@code if_icmp*} direto) NÃO desempacotava o operando
 * {@code Nullable(primitivo)} já-boxed. Resultado medido: {@code if (v > 0)} com
 * {@code v} sendo um {@code Int?} compila LIMPO mas morre no LOAD da classe com
 * {@code VerifyError: Bad type on operand stack … 'java/lang/Integer' … if_icmpgt}
 * (stack {Integer, integer}). O valor de {@code v} só é lido quando o ramo é vivo,
 * mas o verifier checa o bytecode inteiro na carga → todo o programa falha.
 *
 * <p>Estes testes RODAM o JVM (o verifier é o que pega — só compilar não prova,
 * Q0) e exigem paridade byte-a-byte com o JS. Precondition Q0: antes do desempacote
 * no atalho, o caso {@link #nullableRelationalInElseIfChain} dá VerifyError na
 * carga (todo o programa morre mesmo quando o {@code v > 0} nunca roda).
 */
class NullablePrimitiveRelationalConditionTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
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

    private void assertBoth(String program, String expected, Path tempDir, String name)
            throws IOException {
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
    void nullableRelationalInElseIfChain(@TempDir Path tempDir) throws IOException {
        // o exato verbatim do §279 (aqui so o ramo `v > 0` estava morto para
        // probe(null); com 5/0/-3 o relacional REALMENTE roda). Precondition Q0:
        // sem o desempacote no atalho, VerifyError no LOAD mesmo para probe(null).
        assertBoth("""
                String probe(Int? v) {
                    if (v == null) { return "n" } else if (v > 0) { return "e" } else { return "z" }
                }
                main() {
                    println(probe(5))
                    println(probe(0))
                    println(probe(-3))
                    println(probe(null))
                }
                """, "e\nz\nz\nn", tempDir, "nullRelElseIf");
    }

    @Test
    void nullableRelationalInPlainIfConditions(@TempDir Path tempDir) throws IOException {
        assertBoth("""
                String sign(Int? v) {
                    if (v > 0) { return "p" }
                    if (v < 0) { return "m" }
                    return "z"
                }
                main() {
                    println(sign(7))
                    println(sign(-2))
                    println(sign(0))
                }
                """, "p\nm\nz", tempDir, "nullRelPlainIf");
    }

    @Test
    void nullableRelationalWithMixedWidthLong(@TempDir Path tempDir) throws IOException {
        // `Int?` vs literal Long: desempacota o Integer p/ int, alarga p/ long,
        // depois lcmp/if_icmpge. Precondition Q0: if_icmpge sobre Integer → VerifyError.
        assertBoth("""
                String f(Int? v) {
                    if (v >= 100000L) { return "big" }
                    return "small"
                }
                main() {
                    println(f(200000))
                    println(f(5))
                }
                """, "big\nsmall", tempDir, "nullRelLong");
    }
}
