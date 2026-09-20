package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #466 — {@code String.format} com especificadores de ponto flutuante
 * ({@code %.2f}, {@code %e}, {@code %g}, {@code %+.2f}) era
 * locale-sensitive: a JVM backend baixava o descritor de 2 argumentos
 * {@code String.format(String, Object[])}, que usa o locale padrão do host
 * — num host {@code pt_BR} o output era {@code 3,14} (JS do runner Graal
 * idem, pela ponte host). O lowering agora sempre emite a forma de 3
 * argumentos {@code String.format(Locale.ROOT, String, Object[])} (R10:
 * determinístico em qualquer host; paridade JVM↔Script↔JS).
 *
 * <p>Q3: {@code %.2f}/{@code %e}/{@code %g}/{@code %+.2f}/{@code %5.2f}
 * (os do corpus da issue) + controle sem specificador de locale
 * ({@code %d}/{@code %s}); face crítica = JVM executada em CHILD com
 * {@code -Duser.language=pt -Duser.country=BR} (golden medido no oracle
 * JDK {@code String.format(Locale.ROOT, ...)} — nunca de memória);
 * Script face no processo (ponte do interpreter agora resolve o overload
 * de 3 args); JS face via runner Graal (ponte do host com ROOT — paridade
 * byte-a-byte §239 preservada e fortalecida).
 */
class StringFormatLocaleE2ETest {

    private static final String SRC = """
        main() {
            println(String.format("%.2f", 3.14))
            println(String.format("%d", 42))
            println(String.format("%s %d %.1f", "n", 5, 2.5))
            println(String.format("%e", 1234.5678))
            println(String.format("%g", 0.00042))
            println(String.format("%+.2f", 3.14))
            println(String.format("%5.2f|%s", 9.99, "ab"))
        }
        """;

    /** Golden = oracle JDK real (String.format(Locale.ROOT, ...) sob pt_BR-default). */
    private static final String EXPECTED = String.join("\n",
            "3.14", "42", "n 5 2.5", "1.234568e+03", "0.000420000", "+3.14", " 9.99|ab");

    @TempDir Path tmp;

    @Test
    void jvmChildUnderPtBrLocaleStillFormatsWithRootDot() throws Exception {
        Files.writeString(tmp.resolve("S.kf"), SRC);
        Path out = tmp.resolve("classes");
        CompilationResult r = new CompilerDriver().compile(tmp.resolve("S.kf"), out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-Duser.language=pt", "-Duser.country=BR",
                "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM child exit, output: " + output);
        assertEquals(EXPECTED, output,
                "#466: host pt_BR nao pode trocar o separador decimal (Locale.ROOT travado)");
    }

    @Test
    void scriptFaceUsesRoot() throws Exception {
        Files.writeString(tmp.resolve("S2.kf"), SRC);
        KofInterpreter.Result i = new CompilerDriver()
                .interpret(java.util.List.of(tmp.resolve("S2.kf")), tmp, new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "#466 Script face");
    }

    @Test
    void jsFaceViaGraalRunnerMatchesJvm() throws Exception {
        Files.writeString(tmp.resolve("S3.kf"), SRC);
        Path out = tmp.resolve("js");
        CompilationResult r = new CompilerDriver().compile(tmp.resolve("S3.kf"), out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int rc = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        String output = buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, rc, "JS runner exit, output: " + output);
        assertEquals(EXPECTED, output, "#466 JS face (ponte do host com ROOT)");
    }
}
