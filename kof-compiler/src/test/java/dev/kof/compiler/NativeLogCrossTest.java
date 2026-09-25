package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-FULL-PARITY-050 (row 9, fatia 2a) — {@code kof.log} nos cross-arch
 * (riscv64/aarch64): o interpretador de {@code KOF_LOG_LEVEL} e o rótulo do
 * contrato JVM/x86 ({@code INFO}/{@code DEBUG}/...). Mesmo estilo do
 * {@link NativeLogE2ETest} (x86): default info, debug só sob KOF_LOG_LEVEL=debug,
 * error suprime info, off suprime tudo, warn/error vão para stderr, e o parse
 * do nível é case-insensitive. O timestamp ({@code yyyy-MM-dd HH:mm:ss.SSS} UTC)
 * é a fatia 2b — ainda NÃO coberto aqui (o golden não o exige).
 */
class NativeLogCrossTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static final String LOG_PROGRAM = """
            main() {
                log.debug("detail message")
                log.info("hello from kof")
                log.warn("careful")
                log.error("boom")
            }
            """;

    /** Retorna {stdout, stderr} do binário cross rodando sob qemu. */
    private String[] run(Path tempDir, String source, String archFlag, String qemuArch, String level)
            throws IOException {
        Path src = tempDir.resolve("Log-" + archFlag + ".kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out-" + archFlag);
        CompilationResult result = driver.compile(src, outDir, Target.valueOf(archFlag));
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(qemuArch, bin);
        pb.redirectErrorStream(false);
        if (level != null) pb.environment().put("KOF_LOG_LEVEL", level);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        assertEquals(0, ec, "exit code, stderr: " + stderr);
        return new String[]{stdout, stderr};
    }

    private void assertBothArches(Path tempDir, String source, String level,
                                  java.util.function.BiConsumer<String[], String> checks) throws IOException {
        String[] riscv = run(tempDir, source, "NATIVE_RISCV64", "riscv64", level);
        checks.accept(riscv, "riscv64");
        String[] arm = run(tempDir, source, "NATIVE_AARCH64", "aarch64", level);
        checks.accept(arm, "aarch64");
    }

    @Test
    void defaultLevelIsInfoBothArches(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain + qemu ausente — pulando (NATIVE002)");
        assertBothArches(tempDir, LOG_PROGRAM, null, (out, arch) -> {
            assertTrue(out[0].endsWith("INFO hello from kof"), arch + " stdout: " + out[0]);
            assertFalse(out[0].contains("detail message"), arch + " debug must be off: " + out[0]);
            assertTrue(out[1].contains("WARN careful"), arch + " stderr: " + out[1]);
            assertTrue(out[1].contains("ERROR boom"), arch + " stderr: " + out[1]);
        });
    }

    @Test
    void debugLevelShowsDebugBothArches(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain + qemu ausente — pulando (NATIVE002)");
        assertBothArches(tempDir, LOG_PROGRAM, "debug", (out, arch) ->
                assertTrue(out[0].contains("DEBUG detail message"), arch + " stdout: " + out[0]));
    }

    @Test
    void errorLevelSuppressesInfoBothArches(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain + qemu ausente — pulando (NATIVE002)");
        assertBothArches(tempDir, LOG_PROGRAM, "error", (out, arch) -> {
            assertFalse(out[0].contains("hello from kof"), arch + " stdout: " + out[0]);
            assertTrue(out[1].contains("ERROR boom"), arch + " stderr: " + out[1]);
        });
    }

    @Test
    void offSuppressesEverythingBothArches(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain + qemu ausente — pulando (NATIVE002)");
        assertBothArches(tempDir, LOG_PROGRAM, "off", (out, arch) -> {
            assertEquals("", out[0], arch + " stdout: " + out[0]);
            assertEquals("", out[1], arch + " stderr: " + out[1]);
        });
    }

    @Test
    void warnGoesToStderrBothArches(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain + qemu ausente — pulando (NATIVE002)");
        assertBothArches(tempDir, """
                main() {
                    log.warn("to stderr")
                }
                """, null, (out, arch) -> {
            assertEquals("", out[0], arch + " stdout: " + out[0]);
            assertTrue(out[1].contains("WARN to stderr"), arch + " stderr: " + out[1]);
        });
    }

    @Test
    void uppercaseEnvLevelAcceptedBothArches(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain + qemu ausente — pulando (NATIVE002)");
        assertBothArches(tempDir, LOG_PROGRAM, "DEBUG", (out, arch) ->
                assertTrue(out[0].contains("DEBUG detail message"), arch + " stdout: " + out[0]));
    }
}
