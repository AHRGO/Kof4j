package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-4.1 (PLAN-BAREMETAL-BOOT): E2E do emissor RV32I do MCU sob
 * {@code qemu-system-riscv32 -M virt}. Sem semihosting (o qemu 8.2 não
 * reconhece o {@code ebreak} de semihosting) — a saída é a UART do virt
 * (0x10000000) e o encerramento é o test device (0x100000), exatamente o
 * caminho que o plano B-4 autoriza.
 *
 * <p>Guards honestos (Q5): toolchain binutils riscv64 + qemu-system-riscv32
 * provisionado em {@code ~/.local/share/kof-mcu} (ver
 * {@code scripts/provision-mcu-qemu.sh}). Sem eles o teste é {@code assumeTrue}
 * skip — nunca verde falso.
 */
class NativeMcuE2ETest {

    private static final String HELLO = "main() { println(\"KO-MCU OK\") }";
    private static final String MULTI = "main() { print(\"a\"); println(\"b\"); print(\"c\") }";
    private static final String UNSUPPORTED = "main() { println(42) }";

    @Test
    void mcuRiscv32PrintsOverUart(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        Path bin = build(tempDir, HELLO, true);
        Path ser = boot(tempDir, bin);
        String text = serialText(ser);
        assertTrue(text.contains("KO-MCU OK"),
                "UART deveria conter 'KO-MCU OK', veio: [" + text + "]");
    }

    @Test
    void mcuRiscv32PreservesPrintOrderAndNewlines(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        Path bin = build(tempDir, MULTI, true);
        String text = serialText(boot(tempDir, bin));
        assertEquals("ab\nc", text.strip(),
                "ordem/newlines do print/println alterados");
    }

    @Test
    void mcuArtifactIsElf32Riscv(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasAs(), "binutils riscv64 ausente");
        Path bin = build(tempDir, HELLO, true);
        byte[] b = Files.readAllBytes(bin);
        assertTrue(b.length > 20 && b[0] == 0x7f && b[1] == 'E' && b[2] == 'L' && b[3] == 'F',
                "artefato MCU deveria ser ELF");
        assertEquals(1, b[4] & 0xff, "ELF deve ser 32-bit");
        int machine = (b[18] & 0xff) | ((b[19] & 0xff) << 8);
        assertEquals(243, machine, "e_machine deve ser RISC-V (243)");
    }

    @Test
    void mcuRejectsUnsupportedOpWithDiagnostic(@TempDir Path tempDir) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, UNSUPPORTED);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"),
                Target.NATIVE_RISCV32, NativeProfile.FREESTANDING);
        assertTrue(!result.success(), "print de Int ainda não é suportado no MCU — deve recusar");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("NATIVE002"),
                "recusa deve citar NATIVE002, veio: " + diags);
    }

    private Path build(Path tempDir, String program, boolean expectSuccess) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir,
                Target.NATIVE_RISCV32, NativeProfile.FREESTANDING);
        assertEquals(expectSuccess, result.success(),
                "compile MCU inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private Path boot(Path tempDir, Path bin) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-riscv32 ausente (scripts/provision-mcu-qemu.sh)");
        Path ser = tempDir.resolve("ser.log");
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                qemu.toString(), "-M", "virt", "-bios", "none", "-display", "none",
                "-serial", "file:" + ser, "-kernel", bin.toString()));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Path prefix = mcuPrefix();
        if (prefix != null && qemu.isAbsolute() && qemu.startsWith(prefix)) {
            pb.environment().put("LD_LIBRARY_PATH",
                    prefix.resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        Process p = pb.start();
        try (var in = p.getInputStream()) {
            in.readAllBytes();
        }
        p.waitFor(20, TimeUnit.SECONDS);
        p.destroyForcibly();
        return ser;
    }

    private String serialText(Path log) throws IOException {
        return Files.readString(log, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    private void assumeToolchain() {
        assumeTrue(hasAs(), "binutils riscv64 ausente (riscv64-linux-gnu-as)");
    }

    private static boolean hasAs() {
        return hasTool("riscv64-linux-gnu-as", "--version");
    }

    private static boolean hasTool(String tool, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = new ProcessBuilder(cmd).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Prefixo provisionado sem root (scripts/provision-mcu-qemu.sh). */
    private static Path mcuPrefix() {
        String env = System.getenv("KOF_MCU_HOME");
        if (env != null && Files.isRegularFile(Path.of(env, "usr/bin/qemu-system-riscv32"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-mcu");
        if (Files.isRegularFile(home.resolve("usr/bin/qemu-system-riscv32"))) {
            return home;
        }
        return null;
    }

    private static Path findQemu() {
        if (hasTool("qemu-system-riscv32", "--version")) return Path.of("qemu-system-riscv32");
        Path p = mcuPrefix();
        return p != null ? p.resolve("usr/bin/qemu-system-riscv32") : null;
    }
}
