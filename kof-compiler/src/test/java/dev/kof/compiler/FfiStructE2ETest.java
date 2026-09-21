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
 * FFI struct ABI (D6-1(A) / slice 3.8b): um `record` Kof de campos escalares
 * atravessa POR VALOR como struct C no target JVM (FFM classifica pela
 * StructLayout derivada do RecordComponent). Prova com um shim C real; struct
 * RETURN e campos não-escalares seguem FFI001 honesto (R6), e Native/JS ficam
 * nos seus gap codes — nunca um binding parcial silencioso.
 */
class FfiStructE2ETest {

    private static final String C_SRC = """
            struct Point { int x; int y; };
            int sumpoint(struct Point p) { return p.x + p.y; }
            double scale(struct Point p, double f) { return (p.x + p.y) * f; }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void structParamByValueJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("point.kf");
        Files.writeString(src, """
                record Point(Int x, Int y)

                extern "%s" sumpoint(Point p): Int
                extern "%s" scale(Point p, Double f): Double

                main() {
                    println(sumpoint(Point(3, 4)))
                    println(scale(Point(2, 3), 2.0))
                }
                """.formatted(so, so));

        Path out = dir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM struct param must bind (3.8b): "
                + r.diagnostics().getDiagnostics());
        assertEquals("7\n10.0", runJvm(out),
                "record-by-value: sumpoint(Point(3,4))=7, scale(Point(2,3),2.0)=10.0");
    }

    @Test
    void structReturnStaysFfi001(@TempDir Path dir) throws IOException {
        // div() da libc devolve `div_t` por valor: struct RETURN ainda não é
        // bindável nesta fatia → FFI001 honesto na declaração (nunca stub).
        Path src = dir.resolve("divret.kf");
        Files.writeString(src, """
                record Div(Int q, Int r)

                extern "libc.so.6" div(Int a, Int b): Div

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-return"), Target.JVM);
        assertFalse(r.success(), "struct return must not silently bind");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structParamStringFieldStaysFfi001(@TempDir Path dir) throws IOException {
        // Campo `String`/`char*` é ponteiro (não-escalar no v1) → FFI001 honesto.
        Path src = dir.resolve("strfield.kf");
        Files.writeString(src, """
                record Named(String name, Int n)

                extern "libc.so.6" abs(Named p): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-strfield"), Target.JVM);
        assertFalse(r.success(), "record with a String field must not bind in v1");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structParamEmptyRecordStaysFfi001(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("empty.kf");
        Files.writeString(src, """
                record E()

                extern "libc.so.6" abs(E p): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-empty"), Target.JVM);
        assertFalse(r.success(), "empty record is not a bindable struct");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structParamNativeStaysFfi001(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("nat.kf");
        Files.writeString(src, """
                record Point(Int x, Int y)

                extern "libc.so.6" sumpoint(Point p): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-native"), Target.NATIVE);
        assertFalse(r.success(), "Native struct ABI is slice 3.7 — must stay unbound");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001 on Native, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structParamJsStaysFfi002(@TempDir Path dir) throws IOException {
        // O runner JS compartilha o bridge escalar, mas ainda não o de struct →
        // FFI002 honesto (R6), nunca um downcall que quebraria em runtime.
        Path src = dir.resolve("js.kf");
        Files.writeString(src, """
                record Point(Int x, Int y)

                extern "libc.so.6" sumpoint(Point p): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-js"), Target.JS);
        assertFalse(r.success(), "JS struct ABI not landed → must stay unbound");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI002"),
                "expected FFI002 on JS, got: " + r.diagnostics().getDiagnostics());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "struct host lib usa um .so nativo (Linux)");
        Path c = dir.resolve("libkofpoint.c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofpoint.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de struct");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de struct: " + out);
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
