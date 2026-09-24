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
 * B-3 (PLAN-BAREMETAL-BOOT) — perfil BIOS (x86_64, boot path legado): o
 * compilador emite um setor de boot de 512 bytes cujo entry {@code _start} roda
 * em modo real 16-bit, imprime via teletype do BIOS ({@code int 0x10,
 * ah=0x0E}) e espelha no COM1 (0x3F8, a captura headless), e para. Aceitação do
 * plano: {@code qemu-system-x86_64 -drive format=raw,file=kof.img} dá boot e o
 * hello aparece; a sabotagem (quebrar a assinatura {@code 0xAA55}) faz o qemu
 * recusar o disco ("not a bootable disk") e nada é impresso.
 *
 * <p>Fatia B-3a: só o BOOT PATH (nada de payload Kof — a carga dos próximos
 * setores é a B-3b). A imagem é flat: o setor começa em 0x7C00 e fecha com a
 * assinatura em 0x1FE.
 */
class BiosBootE2ETest {

    private static final String HELLO = """
            main() {
                println("KO-BIOS PAYLOAD")
            }
            """;

    private static final String MARKER = "KO-BIOS OK";

    private static final String BAD_MARKER = "KO-BIOS LOAD BAD";

    private static final String LM_MARKER = "KO-BIOS LM64 OK";

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
        if (env != null && Files.isRegularFile(Path.of(env, "usr/bin/qemu-system-x86_64"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-ovmf");
        if (Files.isRegularFile(home.resolve("usr/bin/qemu-system-x86_64"))) {
            return home;
        }
        return null;
    }

    private static Path findQemu() {
        if (hasTool("qemu-system-x86_64", "--version")) return Path.of("qemu-system-x86_64");
        Path p = ovmfPrefix();
        return p == null ? null : p.resolve("usr/bin/qemu-system-x86_64");
    }

    private Path build(Path tempDir, String program) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, NativeProfile.BIOS);
        assertTrue(result.success(),
                "compile BIOS inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private static java.util.List<String> qemuCmd(Path qemu, Path img, Path ser) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        Path prefix = ovmfPrefix();
        boolean prefixQemu = prefix != null && qemu.startsWith(prefix);
        if (prefixQemu) {
            cmd.add(prefix.resolve("usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2").toString());
            cmd.add("--library-path");
            cmd.add(prefix.resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        cmd.add(qemu.toString());
        if (prefix != null) {
            // B-3: o firmware do boot legado é o SeaBIOS (debian: share/seabios),
            // NÃO o OVMF (share/qemu só traz os dtbs/roms). Sem o -L certo o qemu
            // morre em "could not load PC BIOS 'bios-256k.bin'" e nada boota.
            Path seabios = prefix.resolve("usr/share/seabios");
            Path datadir = Files.isDirectory(seabios) ? seabios : prefix.resolve("usr/share/qemu");
            cmd.addAll(java.util.List.of("-L", datadir.toString()));
        }
        cmd.addAll(java.util.List.of(
                "-machine", "pc", "-m", "128",
                "-display", "none", "-net", "none", "-no-reboot",
                "-serial", "file:" + ser,
                "-drive", "format=raw,file=" + img + ",if=ide"));
        return cmd;
    }

    private String serialText(Path log) throws IOException {
        return Files.readString(log, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    /** B-3b-3: localiza a magia do header do payload ({@code KOFPAYLD}) na imagem flat. */
    private static int findMagic(byte[] img) {
        for (int i = 0; i + 8 <= img.length; i++) {
            if (img[i] == 'K' && img[i + 1] == 'O' && img[i + 2] == 'F' && img[i + 3] == 'P'
                    && img[i + 4] == 'A' && img[i + 5] == 'Y' && img[i + 6] == 'L' && img[i + 7] == 'D') {
                return i;
            }
        }
        return -1;
    }

    @Test
    void biosArtifactIsBootableMbr(@TempDir Path tempDir) throws IOException {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);
        byte[] b = Files.readAllBytes(img);
        assertTrue(b.length >= 512, "imagem BIOS deveria ter >= 512 bytes, tem " + b.length);
        assertEquals((byte) 0x55, b[510], "assinatura de boot: byte 0x1FE deve ser 0x55");
        assertEquals((byte) 0xAA, b[511], "assinatura de boot: byte 0x1FF deve ser 0xAA");
    }

    @Test
    void biosMbrPrintsUnderQemuAndSabotagedMagicRefuses(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        // (a) boot POSITIVO — o setor imprime o marcador no COM1.
        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains(MARKER)) break;
                }
                Thread.sleep(500);
            }
            assertTrue(text.contains(MARKER),
                    "BIOS nao imprimiu '" + MARKER + "' no serial. Log: "
                            + text.substring(Math.max(0, text.length() - 400)));
        } finally {
            p.destroyForcibly();
        }

        // (b) SABOTAGEM — sem a assinatura 0xAA55 o firmware recusa o disco e
        // NADA é impresso (o nível é real, não decorativo).
        byte[] corrupt = Files.readAllBytes(img);
        corrupt[510] = 0;
        corrupt[511] = 0;
        Path bad = tempDir.resolve("bad.img");
        Files.write(bad, corrupt);
        Path ser2 = tempDir.resolve("ser2.log");
        Process p2 = new ProcessBuilder(qemuCmd(qemu, bad, ser2)).redirectErrorStream(true).start();
        try {
            Thread.sleep(5_000);
        } finally {
            p2.destroyForcibly();
        }
        String text2 = Files.exists(ser2) ? serialText(ser2) : "";
        assertFalse(text2.contains(MARKER),
                "assinatura quebrada NAO pode bootar, mas imprimiu: " + text2);
    }

    @Test
    void biosLoadsPayloadSectorAndNamesCorruptedPayload(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        // (a) carga POSITIVA — o setor lê o payload (LBA 1) e valida a magia.
        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains(MARKER)) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER),
                "payload (LBA 1) nao carregou/magic nao bateu. Log: " + text);

        // (b) payload CORROMPIDO — a magia KOFPAYLD do header deixa de bater e
        // o stage2 imprime a falha NOMEADA no serial (nunca um hang silencioso).
        byte[] corrupt = Files.readAllBytes(img);
        int hdr = findMagic(corrupt);
        assertTrue(hdr >= 0, "header KOFPAYLD ausente na imagem flat");
        corrupt[hdr] = (byte) 'X';
        Path bad = tempDir.resolve("badpay.img");
        Files.write(bad, corrupt);
        Path ser2 = tempDir.resolve("ser2.log");
        Process p2 = new ProcessBuilder(qemuCmd(qemu, bad, ser2)).redirectErrorStream(true).start();
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser2) && serialText(ser2).contains(BAD_MARKER)) break;
                Thread.sleep(500);
            }
        } finally {
            p2.destroyForcibly();
        }
        String text2 = Files.exists(ser2) ? serialText(ser2) : "";
        assertTrue(text2.contains(BAD_MARKER),
                "payload corrompido deveria imprimir '" + BAD_MARKER + "'. Log: " + text2);
        assertFalse(text2.contains(MARKER),
                "payload corrompido NAO pode reportar sucesso: " + text2);
    }

    /** B-3b-3: o PAYLOAD KOF REAL roda bare — o boot carrega os N setores do
     * programa (header KOFPAYLD), entra em long mode, copia o staging para a
     * base 0x100000 e salta para {@code kof_payload_entry}; o println do main
     * sai no COM1 via a costura kof_plat_write (corpo BIOS). Prova: o TEXTO DO
     * PROGRAMA ("KO-BIOS PAYLOAD") aparece no serial DEPOIS dos marcadores do
     * boot. */
    @Test
    void biosRunsKofMainBare(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains("KO-BIOS PAYLOAD")) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER), "boot deveria imprimir '" + MARKER + "': " + text);
        assertTrue(text.contains(LM_MARKER), "boot deveria imprimir '" + LM_MARKER + "': " + text);
        assertTrue(text.contains("KO-BIOS PAYLOAD"),
                "o main Kof nao rodou bare (println ausente no serial). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void biosEntersLongModeAndRuns64BitCode(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains(LM_MARKER)) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER),
                "carga 16-bit nao rodou antes do long mode. Log: " + text);
        assertTrue(text.contains(LM_MARKER),
                "codigo 64-bit nao rodou (long mode nao entrou). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }
}
