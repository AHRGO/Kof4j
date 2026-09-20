package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #473/#474 — switch sobre Long/Double/Float: o "EQ não-SUB" de 2238bd0a
 * cobriu a face pattern/expression e deixou o ramo NUMÉRICO do STATEMENT
 * com {@code SUB}+{@code if_icmpeq} → {@code VerifyError: Type long_2nd not
 * assignable to integer} no JVM (medido; o launcher JavaFX mascarava — regra
 * do launcher reflexivo) e {@code IllegalStateException: unexpected op} no
 * parser JS. A face b (esta lane) troca o ramo largo/FP do stmt pelo mesmo
 * {@code KofBinary(EQ, tipo)} da face pattern (LCMP/DCMP+IFEQ no backend;
 * NaN nunca casa, como o switch do Java) e ensina o {@code JsSwitchParser} a
 * pular o EQ numérico (=== do JS casa a mesma semântica). Golden = saída
 * MEDIDA no JVM/Script/JS/Native, não chute.
 */
class SwitchLongDoubleSupportE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SOURCE = """
            main() {
                val big: Long = 4000000000
                var hits = 0
                switch (big) {
                    case 4000000000L: hits += 1
                    default: hits += 100
                }
                println(hits)
                val miss = 7L
                println(switch (miss) {
                    case 4000000000L -> "far"
                    case 7L -> "seven"
                    default -> "none"
                })
                val neg = -0.5
                println(switch (neg) {
                    case -0.5 -> "neg-hit"
                    default -> "neg-miss"
                })
            }
            """;

    private static final String EXPECTED = "1\nseven\nneg-hit";

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(),
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

    @Test
    void switchOnLongAndDoubleWorksOnJvmStmtAndExprForms(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("sl.kf");
        Files.writeString(src, SOURCE);
        Path out = tempDir.resolve("sl-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(out), "switch Long/Double stmt+expr (#473/#474)");
    }

    @Test
    void switchOnLongAndDoubleSameOnScriptAndJs(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("sl2.kf");
        Files.writeString(src, SOURCE);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "Script");
        Path jsOut = tempDir.resolve("sl2-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder(TestJdk.which("node"), jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals(EXPECTED, jout, "JS (=== casa NaN-nunca/-0.0 igual Java)");
    }

    @Test
    void switchOnLongAndDoubleNativeMatchesJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("sl3.kf");
        Files.writeString(src, SOURCE);
        Path out = tempDir.resolve("sl3-native");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "ELF produzido");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "native exit, output: " + output);
            assertEquals(EXPECTED, output, "Native = mesmo golden do JVM");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }
}
