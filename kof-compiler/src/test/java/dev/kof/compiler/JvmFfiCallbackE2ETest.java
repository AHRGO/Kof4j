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
 * os gaps honestos: JS callback já tem paridade (fatia C3); o que continua não-bindável
 * é um callback que DEVOLVE String (`char*` com ownership não observável no contrato
 * síncrono) → FFI001 na JVM / FFI002 no JS. PARÂMETRO String do callback (char*->String
 * na fronteira do upcall) foi aberto na fatia 3.4-C3.4 e tem paridade byte-a-byte.
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
            typedef int (*is)(const char*);
            int kof_cb_slen(const char* s, is cb) { return cb(s); }
            typedef int (*isi)(const char*,int);
            int kof_cb_slen_seed(const char* s, int seed, isi cb) { return cb(s, seed); }
            typedef long (*isl)(const char*);
            long kof_cb_sl(const char* s, isl cb) { return cb(s); }
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

    // Fonte compartilhada: 4 callbacks escalares (Int/Long/Double/mixto) via .so temp.
    private static final String CALLBACK_KOF = """
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
            """;

    @Test
    void jvmCallbacksComputeAcrossScalarAbis(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, CALLBACK_KOF.formatted(lib));
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM callbacks must bind (3.4-C2): "
                + r.diagnostics().getDiagnostics());
        // Int 42 | Long 42 | Double 6.0 | Int*Double 7.5 (print por tipo, §264)
        assertEquals("42\n42\n6.0\n7.5", runJvm(out));
    }

    @Test
    void jvmAndJsCallbacksMatchByteForByte(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = CALLBACK_KOF.formatted(lib);

        Path jvmSrc = dir.resolve("cb-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), () -> "JVM compile callbacks: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);

        Path jsSrc = dir.resolve("cb-js.kf");
        Files.writeString(jsSrc, kof);
        Path jsOut = dir.resolve("out-js");
        CompilationResult rjs = driver.compile(jsSrc, jsOut, Target.JS);
        assertTrue(rjs.success(), () -> "JS callbacks must bind (3.4-C3.2): "
                + rjs.diagnostics().getDiagnostics());
        String js = runJs(jsOut);

        assertEquals("42\n42\n6.0\n7.5", js, "JS golden callbacks (3.4-C3.3)");
        assertEquals(jvm, js, "JVM==JS callback parity byte-for-byte (3.4-C3.3)");
    }

    @Test
    void jsNonBindableCallbackStaysFfi002(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                extern "libc.so.6" foo(Int a, (Int) -> String cb): Int
                main() { println(foo(1, (i: Int) -> "x")) }
                """);
        // Callback com ARGUMENTO String já é bindável no JS (3.4-C3.4); o NÃO-bindável
        // que sobra é a String no RETORNO do callback (char* ownership não observável).
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JS);
        assertFalse(r.success(), "JS callback não-bindável (String de retorno) deve permanecer gap honesto");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "FFI002".equals(d.code())),
                () -> "esperava FFI002 no JS, veio " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nonBindableCallbackParamStaysFfi001(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                extern "libc.so.6" foo(Int a, (Int) -> String cb): Int
                main() { println(foo(1, (i: Int) -> "x")) }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "callback com RETORNO String não é bindável na JVM");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "FFI001".equals(d.code())),
                () -> "esperava FFI001 (retorno String do callback é não-bindável), veio "
                        + r.diagnostics().getDiagnostics());
    }

    // R3 3.4-C3.4: um callback cujo PARÂMETRO é String (char*->String na fronteira do
    // upcall) é bindável na JVM e no JS host, com paridade byte-a-byte. C entrega o
    // `char*`; o closure Kof vê um String (lê `.length()` ou chama `atol` — prova que o
    // CONTEÚDO, não só o tamanho, atravessa a ponte). String de RETORNO continua gated.
    @Test
    void stringCallbackArgsBindAndMatchJvmJs(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" kof_cb_slen(String s, (String) -> Int cb): Int
                extern "%1$s" kof_cb_slen_seed(String s, Int seed, (String, Int) -> Int cb): Int
                extern "%1$s" kof_cb_sl(String s, (String) -> Long cb): Long
                extern "libc.so.6" atol(String s): Long
                main() {
                    println(kof_cb_slen("hello", (x: String) -> x.length()))
                    println(kof_cb_slen_seed("hell", 100, (x: String, seed: Int) -> seed + x.length()))
                    println(kof_cb_sl("2026", (x: String) -> atol(x)))
                }
                """.formatted(lib);

        Path jvmSrc = dir.resolve("scb-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-scb-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), () -> "JVM String-callback args must bind (3.4-C3.4): "
                + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);

        Path jsSrc = dir.resolve("scb-js.kf");
        Files.writeString(jsSrc, kof);
        Path jsOut = dir.resolve("out-scb-js");
        CompilationResult rjs = driver.compile(jsSrc, jsOut, Target.JS);
        assertTrue(rjs.success(), () -> "JS String-callback args must bind (3.4-C3.4): "
                + rjs.diagnostics().getDiagnostics());
        String js = runJs(jsOut);

        assertEquals("5\n104\n2026", jvm, "JVM golden String-callback: len=5, 100+len=104, atol=2026");
        assertEquals(jvm, js, "JVM==JS String-callback parity byte-for-byte (3.4-C3.4)");
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

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }
}
