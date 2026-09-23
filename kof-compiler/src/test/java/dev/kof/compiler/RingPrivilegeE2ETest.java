package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * B-6.1 (PLAN-BAREMETAL-BOOT) — anéis de privilégio x86_64, fatia 1: o perfil
 * {@code uefi-ring} instala a GDT/IDT/TSS PRÓPRIOS do Kof no {@code _start} do
 * boot path UEFI (B-2), recarrega {@code CS} para o seletor do Kof via
 * {@code lretq}, e prova o handler ring0 com um {@code int3} controlado — só
 * então imprime {@code KO-RING IDT OK} via {@code ConOut}; depois restaura o
 * estado do firmware. Sem superfície Kof (rule 6): é a maquinaria habilitadora,
 * não o domínio CPL1 (B-6.2).
 *
 * <p>Receita de link: idêntica à B-2 (ELF estático → {@code objcopy
 * --target=pei-x86-64 --subsystem=10}); o perfil {@code uefi-ring} herda o
 * boot path e o PE do perfil {@code uefi} (que fica INTOCADO — o teste
 * negativo cobre que a string de anéis não vaza para {@code uefi} puro).
 */
class RingPrivilegeE2ETest {

    private static final String HELLO = """
            main() {
                println("KO-RING MAIN")
            }
            """;

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

    private static Path ovmfPrefix() {
        String env = System.getenv("KOF_OVMF_HOME");
        if (env != null && Files.isRegularFile(Path.of(env, "usr/share/OVMF/OVMF_CODE_4M.fd"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-ovmf");
        if (Files.isRegularFile(home.resolve("usr/share/OVMF/OVMF_CODE_4M.fd"))) {
            return home;
        }
        return null;
    }

    private static Path findOvmfCode() {
        Path p = ovmfPrefix();
        if (p != null) return p.resolve("usr/share/OVMF/OVMF_CODE_4M.fd");
        Path sys = Path.of("/usr/share/OVMF/OVMF_CODE_4M.fd");
        return Files.isRegularFile(sys) ? sys : null;
    }

    private static Path findQemu() {
        if (hasTool("qemu-system-x86_64", "--version")) return Path.of("qemu-system-x86_64");
        Path p = ovmfPrefix();
        if (p != null) return p.resolve("usr/bin/qemu-system-x86_64");
        return null;
    }

    private Path build(Path tempDir, NativeProfile profile) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, HELLO);
        Path outDir = tempDir.resolve("out-" + profile);
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, profile);
        assertEquals(true, result.success(),
                "compile " + profile + " inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private Path makeEsp(Path tempDir, Path peBinary) throws IOException, InterruptedException {
        Path esp = tempDir.resolve("esp.img");
        run(5_000, "mformat", "-i", esp.toString(), "-C", "-T", "16384", "::");
        run(5_000, "mmd", "-i", esp.toString(), "::/EFI");
        run(5_000, "mmd", "-i", esp.toString(), "::/EFI/BOOT");
        run(10_000, "mcopy", "-i", esp.toString(), peBinary.toString(), "::/EFI/BOOT/BOOTX64.EFI");
        return esp;
    }

    private void run(long timeoutMs, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        assertTrue(p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) && p.exitValue() == 0,
                "falhou: " + String.join(" ", cmd));
    }

    /** Boota o PE sob OVMF e devolve o texto do serial (bounded, §418). */
    private String bootOvmf(Path tempDir, Path peBinary, String expected) throws Exception {
        Path code = findOvmfCode();
        Path qemu = findQemu();
        assumeTrue(code != null, "OVMF ausente (KOF_OVMF_HOME ou ~/.local/share/kof-ovmf)");
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("mformat", "-V") || hasTool("mformat", "--help"), "mtools ausente");
        Path esp = makeEsp(tempDir, peBinary);
        Path vars = tempDir.resolve("vars.fd");
        Files.copy(code.resolveSibling("OVMF_VARS_4M.fd"), vars);
        Path ser = tempDir.resolve("ser.log");

        java.util.List<String> cmd = new java.util.ArrayList<>();
        boolean prefixQemu = ovmfPrefix() != null && qemu.startsWith(ovmfPrefix());
        if (prefixQemu) {
            cmd.add(ovmfPrefix().resolve("usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2").toString());
            cmd.add("--library-path");
            cmd.add(ovmfPrefix().resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        cmd.add(qemu.toString());
        if (ovmfPrefix() != null) cmd.addAll(java.util.List.of(
                "-L", ovmfPrefix().resolve("usr/share/qemu").toString()));
        cmd.addAll(java.util.List.of(
                "-machine", "q35", "-m", "256",
                "-display", "none", "-nodefaults", "-net", "none",
                "-serial", "file:" + ser,
                "-drive", "if=pflash,format=raw,readonly=on,file=" + code,
                "-drive", "if=pflash,format=raw,file=" + vars,
                "-drive", "file=" + esp + ",format=raw,media=disk"));

        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 150_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = Files.readString(ser, StandardCharsets.ISO_8859_1).replace("\0", "");
                    if (text.contains(expected)) break;
                }
                Thread.sleep(1_000);
            }
        } finally {
            p.destroyForcibly();
        }
        return text;
    }

    @Test
    void ringProfileBootsAndProvesOwnedIdtUnderOvmf(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, NativeProfile.UEFI_RING);
        String text = bootOvmf(tempDir, bin, "KO-RING IDT OK");
        assertTrue(text.contains("KO-RING IDT OK"),
                "OVMF nao provou a IDT ring0 do Kof. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        // o programa Kof segue rodando normalmente após a restauração.
        assertTrue(text.contains("KO-RING MAIN"),
                "main nao rodou apos o self-test de anéis. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void plainUefiProfileDoesNotEmitRingProof(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, NativeProfile.UEFI);
        String text = bootOvmf(tempDir, bin, "KO-RING MAIN");
        assertTrue(text.contains("KO-RING MAIN"), "UEFI puro deve rodar normalmente");
        assertFalse(text.contains("KO-RING IDT OK"),
                "a maquinaria de anéis NAO pode vazar para o perfil uefi puro (R6)");
    }
}
