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

    private static final byte[] SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PASS = "password".getBytes(StandardCharsets.US_ASCII);

    /** Oráculo: scramble (20 bytes) + os 3 casos de lenenc (valor, offset). */
    private static String wireOracle() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] s1 = md.digest(PASS);
        byte[] s2 = md.digest(s1);
        byte[] combo = new byte[SEED.length + s2.length];
        System.arraycopy(SEED, 0, combo, 0, SEED.length);
        System.arraycopy(s2, 0, combo, SEED.length, s2.length);
        byte[] s3 = md.digest(combo);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) sb.append((s1[i] ^ s3[i]) & 0xff).append('\n');
        // offsets absolutos no buffer .Ldw_lenenc (caso 2 no byte 1, caso 3 no byte 4)
        sb.append("16\n1\n4660\n4\n1193046\n8");
        return sb.toString().trim();
    }

    /**
     * _start: scramble do par (seed, pass) + impressão dos 20 bytes; depois os
     * 3 casos de lenenc (valor, offset de retorno) sobre buscas em offsets
     * distintos do mesmo buffer.
     */
    private static String wireHarness() {
        return """
                .section .rodata
                .Ldw_seed:
                    .ascii "12345678901234567890"
                .Ldw_lenenc:
                    .byte 0x10, 0xFC, 0x34, 0x12, 0xFD, 0x56, 0x34, 0x12
                .section .data
                .align 3
                .Ldw_pass:
                    .zero 16
                    .word 8
                    .zero 4
                    .ascii "password"
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    mv   a0, sp
                    la   a1, .Ldw_seed
                    li   a2, 20
                    la   a3, .Ldw_pass
                    call kof_db_mysql_scramble
                    mv   a0, sp
                    call .Ldw_print20
                    la   s0, .Ldw_lenenc
                    # caso 1: 1 byte (0x10), offset base+0
                    mv   a0, s0
                    call kof_db_mysql_lenenc
                    mv   s1, a1
                    call kof_println_int
                    sub  a0, s1, s0
                    call kof_println_int
                    # caso 2: 0xFC + 2 bytes LE, offset base+1
                    addi a0, s0, 1
                    call kof_db_mysql_lenenc
                    mv   s1, a1
                    call kof_println_int
                    sub  a0, s1, s0
                    call kof_println_int
                    # caso 3: 0xFD + 3 bytes LE, offset base+4
                    addi a0, s0, 4
                    call kof_db_mysql_lenenc
                    mv   s1, a1
                    call kof_println_int
                    sub  a0, s1, s0
                    call kof_println_int
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Ldw_print20:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    mv   s0, a0
                    li   s1, 0
                .Ldw_pr_loop:
                    li   s2, 20
                    bge  s1, s2, .Ldw_pr_done
                    add  t0, s0, s1
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s1, s1, 1
                    j    .Ldw_pr_loop
                .Ldw_pr_done:
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

    private static final String GREETING_SEED = "abcdefghABCDEFGH1234";

    /** Oráculo: status 1 + os 20 bytes do seed; depois status 0 no pacote ruim. */
    private static String greetingOracle() {
        StringBuilder sb = new StringBuilder("1");
        for (byte b : GREETING_SEED.getBytes(StandardCharsets.US_ASCII)) sb.append('\n').append(b & 0xff);
        sb.append("\n0");
        return sb.toString();
    }

    /** _start: parse do greeting sintético (seed 20B) + pacote com protocolo ruim. */
    private static String greetingHarness() {
        return """
                .section .rodata
                .Lgr_pkt:
                    .byte 0x4A, 0x00, 0x00, 0x00
                    .byte 0x0A
                    .ascii "5.5.5-10.3.39-MariaDB"
                    .byte 0
                    .byte 0x2A, 0x00, 0x00, 0x00
                    .ascii "abcdefgh"
                    .byte 0
                    .byte 0x00, 0x00
                    .byte 0x21
                    .byte 0x02, 0x00
                    .byte 0x00, 0x00
                    .byte 21
                    .zero 10
                    .ascii "ABCDEFGH1234"
                    .byte 0
                .Lgr_bad:
                    .byte 0x05, 0x00, 0x00, 0x00
                    .byte 0x0B
                    .zero 8
                .section .data
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -32
                    mv   s0, sp
                    li   t0, 0
                .Lgr_zero:
                    li   t1, 20
                    bge  t0, t1, .Lgr_zero_done
                    add  t2, s0, t0
                    sb   zero, 0(t2)
                    addi t0, t0, 1
                    j    .Lgr_zero
                .Lgr_zero_done:
                    la   a0, .Lgr_pkt
                    mv   a1, s0
                    call kof_db_mysql_parse_greeting
                    call kof_println_int
                    mv   a0, s0
                    call .Lgr_print20
                    la   a0, .Lgr_bad
                    mv   a1, s0
                    call kof_db_mysql_parse_greeting
                    call kof_println_int
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Lgr_print20:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    mv   s0, a0
                    li   s1, 0
                .Lgr_pr_loop:
                    li   s2, 20
                    bge  s1, s2, .Lgr_pr_done
                    add  t0, s0, s1
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s1, s1, 1
                    j    .Lgr_pr_loop
                .Lgr_pr_done:
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

    @Test
    void greetingParseMatchesOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = greetingHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "greet_rv", harness + "\n" + runtime);
        assertEquals(greetingOracle(), out, "parse do greeting riscv64 diverge do oráculo");
    }

    @Test
    void greetingParseMatchesOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = greetingHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "greet_aa", arm.toString());
        assertEquals(greetingOracle(), out, "parse do greeting aarch64 diverge do oráculo");
    }

    @Test
    void withoutGreetingPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = greetingHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b64 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_64".equals(p.field())) b64 = p.index();
        }
        assertTrue(b64 >= 0, "peça B64 (parse do greeting) não encontrada no inventário");
        assertTrue(keep.remove(b64), "B64 deveria estar no keep do harness de greeting");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_greet.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_greet.o");
        Path bin = tempDir.resolve("sab_greet");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B64 o link deveria falhar (undefined kof_db_mysql_parse_greeting); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_parse_greeting"),
                "a falha deve citar kof_db_mysql_parse_greeting: " + r[0]);
    }

    @Test
    void scrambleAndLenencMatchOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = wireHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "wire_rv", harness + "\n" + runtime);
        assertEquals(wireOracle(), out, "scramble/lenenc riscv64 diverge do oráculo");
    }

    @Test
    void scrambleAndLenencMatchOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = wireHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "wire_aa", arm.toString());
        assertEquals(wireOracle(), out, "scramble/lenenc aarch64 diverge do oráculo");
    }

    @Test
    void withoutAuthPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = wireHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b63 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_63".equals(p.field())) b63 = p.index();
        }
        assertTrue(b63 >= 0, "peça B63 (auth cross) não encontrada no inventário");
        assertTrue(keep.remove(b63), "B63 deveria estar no keep do harness de auth");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_auth.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_auth.o");
        Path bin = tempDir.resolve("sab_auth");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B63 o link deveria falhar (undefined kof_db_mysql_scramble); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_scramble"),
                "a falha deve citar kof_db_mysql_scramble: " + r[0]);
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
