package dev.kof.compiler.nat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S5.1 (db-parity-plan, gaps-db lane, 23/09): as primitivas do wire MySQL
 * cruzado — SHA1 curto ({@code kof_sec_sha1_internal}, port do
 * {@code RuntimeDb1} x86 para a peça B62) + bswap — provadas por harness asm
 * nos DOIS alvos cross, contra o oráculo do JVM ({@code MessageDigest SHA-1}).
 *
 * <p>Por que harness e não E2E: {@code kof.security} recusa no cross por
 * {@code SECN000} (não há superfície Kof), então a prova do primitivo é direta
 * — a mesma fronteira que o S5.1 (handshake/auth) vai consumir.
 *
 * <p>Q0: sem a peça B62 o {@code ld} falha com referência indefinida a
 * {@code kof_sec_sha1_internal} (sabotagem) — prova que o harness exercita a
 * peça nova, não uma homônima.
 */
class NativeRiscvDbWireTest {

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assumeRiscv() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (S5.1)");
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (S5.1)");
    }

    private static final byte[][] MESSAGES = {
            new byte[0],
            "abc".getBytes(StandardCharsets.UTF_8),
            "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8),
    };

    /** Oráculo: cada byte do digest (0..255) em uma linha, os 3 vetores. */
    private static String oracle() throws Exception {
        StringBuilder sb = new StringBuilder();
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        for (byte[] msg : MESSAGES) {
            for (byte b : md.digest(msg)) sb.append(b & 0xff).append('\n');
        }
        return sb.toString().trim();
    }

    /** _start: SHA1 dos 3 vetores + impressão dos 20 bytes de cada digest. */
    private static String harness() {
        return """
                .section .rodata
                .Lhw_abc:
                    .ascii "abc"
                .Lhw_fox:
                    .ascii "The quick brown fox jumps over the lazy dog"
                .section .data
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    mv   a0, sp
                    la   a1, .Lhw_abc
                    li   a2, 0
                    call kof_sec_sha1_internal
                    mv   a0, sp
                    call .Lhw_print20
                    mv   a0, sp
                    la   a1, .Lhw_abc
                    li   a2, 3
                    call kof_sec_sha1_internal
                    mv   a0, sp
                    call .Lhw_print20
                    mv   a0, sp
                    la   a1, .Lhw_fox
                    li   a2, 43
                    call kof_sec_sha1_internal
                    mv   a0, sp
                    call .Lhw_print20
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Lhw_print20:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    mv   s0, a0
                    li   s1, 0
                .Lhw_pr_loop:
                    li   s2, 20
                    bge  s1, s2, .Lhw_pr_done
                    add  t0, s0, s1
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s1, s1, 1
                    j    .Lhw_pr_loop
                .Lhw_pr_done:
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    addi sp, sp, 32
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                """;
    }

    private String runCapture(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            int ec = p.waitFor();
            assertEquals(0, ec, "comando falhou (" + ec + "): " + String.join(" ", cmd) + "\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    private String[] runAllowFail(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            return new String[]{out, String.valueOf(p.waitFor())};
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private String buildRun(String arch, Path tempDir, String name, String asmText) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, asmText);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
        String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
        if (arch.equals("riscv64")) {
            runCapture(as, "-mno-relax", "-o", obj.toString(), asm.toString());
        } else {
            runCapture(as, "-o", obj.toString(), asm.toString());
        }
        // link estático: o harness não usa libc (só o runtime puro).
        runCapture(ld, "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, bin.toString());
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        assertEquals(0, ec, "qemu " + arch + " falhou (" + ec + "): " + out);
        return out;
    }

    @Test
    void sha1MatchesJvmOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = harness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "sha1rv", harness + "\n" + runtime);
        assertEquals(oracle(), out, "SHA1 riscv64 diverge do oráculo JVM");
    }

    @Test
    void sha1MatchesJvmOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = harness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "sha1aa", arm.toString());
        assertEquals(oracle(), out, "SHA1 aarch64 diverge do oráculo JVM");
    }

    @Test
    void withoutSha1PieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = harness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b62 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_62".equals(p.field())) b62 = p.index();
        }
        assertTrue(b62 >= 0, "peça B62 (SHA1 cross) não encontrada no inventário");
        assertTrue(keep.remove(b62), "B62 deveria estar no keep do harness de SHA1");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab.o");
        Path bin = tempDir.resolve("sab");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B62 o link deveria falhar (undefined kof_sec_sha1_internal); saída: " + r[0]);
        assertTrue(r[0].contains("kof_sec_sha1_internal"),
                "a falha deve citar kof_sec_sha1_internal: " + r[0]);
    }
}
