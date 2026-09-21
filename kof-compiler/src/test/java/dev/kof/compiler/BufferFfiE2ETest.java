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
 * FFI out-buffer (D6-3 / D-R3-BUFFER, slice B2): the nominal {@code Buffer(U8)}
 * crosses an {@code extern} as an INOUT pointer — copy-in / call / copy-back on
 * the JVM (D-FFI-STRUCT). Proven with a real C shim; Native/JS keep honest gaps.
 */
class BufferFfiE2ETest {

    private static final String C_SRC = """
            int bump(unsigned char* buf, int n) {
                int s = 0;
                for (int i = 0; i < n; i++) { buf[i] = buf[i] + 10; s += buf[i]; }
                return s;
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void bufferInoutCopyInCopyBackJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("bufinout.kf");
        Files.writeString(src, """
                extern "%s" bump(Buffer(U8) buf, Int n): Int

                main() {
                    var b = buffer.alloc(2)
                    println(bump(b, 2))
                    println(b.bytes())
                    println(bump(b, 2))
                    println(b.bytes())
                }
                """.formatted(so));

        Path out = dir.resolve("out-jvm-bufinout");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM Buffer(U8) extern param must bind (B2): "
                + r.diagnostics().getDiagnostics());
        assertEquals("20\n[10, 10]\n40\n[20, 20]", runJvm(out),
                "copy-in reads the buffer, copy-back writes it: 10s accumulate on the 2nd call");
    }

    @Test
    void bareBufferSpellingBindsJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("bufbare.kf");
        Files.writeString(src, """
                extern "%s" bump(Buffer buf, Int n): Int

                main() {
                    var b = buffer.alloc(1)
                    println(bump(b, 1))
                }
                """.formatted(so));

        Path out = dir.resolve("out-jvm-bufbare");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "bare `Buffer` is accepted as Buffer(U8): "
                + r.diagnostics().getDiagnostics());
        assertEquals("10", runJvm(out));
    }

    @Test
    void bufferParamNativeStaysFfi001(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("bufnat.kf");
        Files.writeString(src, """
                extern "libc.so.6" f(Buffer(U8) buf, Int n): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-buf-fnat"), Target.NATIVE);
        assertFalse(r.success(), "Native buffer ABI is later — must stay unbound");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001 on Native, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void bufferParamJsStaysFfi002(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("bufjs.kf");
        Files.writeString(src, """
                extern "libc.so.6" f(Buffer(U8) buf, Int n): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-buf-fjs"), Target.JS);
        assertFalse(r.success(), "JS buffer bridge not landed → must stay unbound");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI002"),
                "expected FFI002 on JS, got: " + r.diagnostics().getDiagnostics());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "buffer host lib usa um .so nativo (Linux)");
        Path c = dir.resolve("libkofbuf.c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofbuf.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de buffer");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de buffer: " + out);
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
