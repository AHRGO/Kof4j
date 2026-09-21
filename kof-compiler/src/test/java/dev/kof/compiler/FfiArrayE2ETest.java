package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FFI array ABI (D6-2 / slice 3.8b fatia 3): um array primitivo Kof (`new Int[n]`)
 * atravessa como `ptr` C no target JVM, com COPY-IN por chamada (o array Java não
 * é pinado nem visto pelo C). Provado com um shim C real; `String[]` (array de
 * ponteiros) segue FFI001 honesto (R6) e Native/JS ficam nos seus gap codes.
 */
class FfiArrayE2ETest {

    private static final String C_SRC = """
            int sumn(int* xs, int n) { int s = 0; for (int i = 0; i < n; i++) s += xs[i]; return s; }
            double sumd(double* xs, int n) { double s = 0; for (int i = 0; i < n; i++) s += xs[i]; return s; }
            void fill(int* xs, int n) { for (int i = 0; i < n; i++) xs[i] = 99; }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void arrayParamByValueJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("arr.kf");
        Files.writeString(src, """
                extern "%s" sumn(Int[] xs, Int n): Int
                extern "%s" sumd(Double[] xs, Int n): Double

                main() {
                    var xs = new Int[3]
                    xs[0] = 1
                    xs[1] = 2
                    xs[2] = 3
                    println(sumn(xs, 3))
                    var ds = new Double[2]
                    ds[0] = 1.5
                    ds[1] = 2.5
                    println(sumd(ds, 2))
                }
                """.formatted(so, so));

        Path out = dir.resolve("out-jvm-arr");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM array param must bind (3.8b fatia 3): "
                + r.diagnostics().getDiagnostics());
        assertEquals("6\n4.0", runJvm(out),
                "Int[] = ptr + copy-in: sumn=6, sumd=4.0");
    }

    @Test
    void arrayCopyInDoesNotAliasJavaBacking(@TempDir Path dir) throws Exception {
        // O C escreve 99 em todo elemento; se fosse by-ref, o array Java mudaria.
        String so = compileHostLib(dir);
        Path src = dir.resolve("arrcopy.kf");
        Files.writeString(src, """
                extern "%s" sumn(Int[] xs, Int n): Int
                extern "%s" fill(Int[] xs, Int n)

                main() {
                    var xs = new Int[2]
                    xs[0] = 5
                    xs[1] = 6
                    println(sumn(xs, 2))
                    fill(xs, 2)
                    println(sumn(xs, 2))
                    println(xs[0])
                }
                """.formatted(so, so));

        Path out = dir.resolve("out-arr-copy");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM array externs must compile: "
                + r.diagnostics().getDiagnostics());
        assertEquals("11\n11\n5", runJvm(out),
                "copy-in: as escritas do C não voltam para o array Java");
    }

    @Test
    void stringArrayStaysFfi001(@TempDir Path dir) throws IOException {
        // `String[]` é array de ponteiros — fora do v1 (D6-2): FFI001 honesto.
        Path src = dir.resolve("strarr.kf");
        Files.writeString(src, """
                extern "libc.so.6" f(String[] xs): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-strarr"), Target.JVM);
        assertFalse(r.success(), "String[] must not bind in v1");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void arrayParamNativeStaysFfi001(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("arrnat.kf");
        Files.writeString(src, """
                extern "libc.so.6" sumn(Int[] xs, Int n): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-arrnat"), Target.NATIVE);
        assertFalse(r.success(), "Native array ABI is later — must stay unbound");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001 on Native, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void arrayParamJsStaysFfi002(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("arrjs.kf");
        Files.writeString(src, """
                extern "libc.so.6" sumn(Int[] xs, Int n): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-arrjs"), Target.JS);
        assertFalse(r.success(), "JS array bridge not landed → must stay unbound");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI002"),
                "expected FFI002 on JS, got: " + r.diagnostics().getDiagnostics());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "array host lib usa um .so nativo (Linux)");
        Path c = dir.resolve("libkofarr.c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofarr.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de array");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de array: " + out);
        return so.toString();
    }

    private static String firstPresent(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {
                // tenta o próximo candidato
            }
        }
        return null;
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
