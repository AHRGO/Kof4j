package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FFI (R3, fatia 3.4-C2): o gate de callback da JVM está ABERTO — um {@code extern}
 * com parâmetro de tipo-função é baixado para {@code kof_ffi} com token {@code
 * C(<desc>)} e o runtime chama {@code Linker.upcallStub} sobre o valor de função Kof
 * (medido: interface sintética especializada {@code int invoke(int,int)}). Este teste
 * roda o mesmo fonte .kf na JVM e afirma o resultado computado ponta-a-ponta, e trava
 * os gaps honestos: JS callback continua {@code FFI002} (paridade = fatia C3) e um
 * callback com parâmetro não-escalar ({@code String}) continua {@code FFI001} na JVM.
 */
class JvmFfiCallbackE2ETest {

    private static final String C_SRC = """
            typedef int (*ii)(int,int);
            int kof_cb_add(int a, int b, ii cb) { return cb(a,b); }
            typedef long (*ll)(long,long);
            long kof_cb_addl(long a, long b, ll cb) { return cb(a,b); }
            typedef double (*ddd)(double,double);
            double kof_cb_addd(double a, double b, ddd cb) { return cb(a,b); }
            typedef double (*id_d)(int,double);
            double kof_cb_mixed(int a, double b, id_d cb) { return cb(a,b); }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    /** Compila o .so do host de callback no temp dir; skip honesto sem Linux/toolchain. */
    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "callback host usa um .so nativo (Linux)");
        Path src = dir.resolve("libkofcb.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofcb.so");
        String cc = null;
        for (String cand : new String[] {"/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc"}) {
            try {
                Process p = new ProcessBuilder(cand, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) { cc = cand; break; }
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de callback");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou: " + out);
        return so.toString();
    }

    @Test
    void jvmCallbacksComputeAcrossScalarAbis(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                extern "%1$s" kof_cb_add(Int a, Int b, (Int, Int) -> Int cb): Int
                extern "%1$s" kof_cb_addl(Long a, Long b, (Long, Long) -> Long cb): Long
                extern "%1$s" kof_cb_addd(Double a, Double b, (Double, Double) -> Double cb): Double
                extern "%1$s" kof_cb_mixed(Int a, Double b, (Int, Double) -> Double cb): Double
                extern "libc.so.6" atol(String s): Long

                main() {
                    println(kof_cb_add(20, 22, (x: Int, y: Int) -> x + y))
                    println(kof_cb_addl(atol("20"), atol("22"), (a: Long, b: Long) -> a + b))
                    println(kof_cb_addd(2.0, 3.0, (x: Double, y: Double) -> x * y))
                    println(kof_cb_mixed(3, 2.5, (i: Int, d: Double) -> i * d))
                }
                """.formatted(lib));
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM callbacks must bind (3.4-C2): "
                + r.diagnostics().getDiagnostics());
        // Int 42 | Long 42 | Double 6.0 | Int*Double 7.5 (print por tipo, §264)
        assertEquals("42\n42\n6.0\n7.5", runJvm(out));
    }

    @Test
    void jsCallbackStaysHonestFfi002(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                extern "libc.so.6" foo(Int a, Int b, (Int, Int) -> Int cb): Int
                main() { println(foo(1, 2, (x: Int, y: Int) -> x + y)) }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JS);
        assertFalse(r.success(), "JS callback deve permanecer gap honesto (paridade = C3)");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "FFI002".equals(d.code())),
                () -> "esperava FFI002 no JS, veio " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nonBindableCallbackParamStaysFfi001(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                extern "libc.so.6" foo(Int a, (String) -> Int cb): Int
                main() { println(foo(1, (s: String) -> 0)) }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "callback com parâmetro String não é bindável na C2");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "FFI001".equals(d.code())),
                () -> "esperava FFI001 (ABI não-bindável do callback), veio "
                        + r.diagnostics().getDiagnostics());
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
