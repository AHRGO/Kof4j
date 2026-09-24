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
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
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
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
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
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                """;
    }

    private static final byte[] AUTH_SCRAMBLE = new byte[20];
    static {
        for (int i = 0; i < 20; i++) AUTH_SCRAMBLE[i] = (byte) (0x10 + i);
    }

    /** Payload do handshake response, espelhando o layout do RuntimeDb3 x86. */
    private static byte[] authPayload(int passLen) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(0x0B);
        o.write(0x82);
        o.write(0x08);
        o.write(0x00);
        o.write(0x00);
        o.write(0x00);
        o.write(0x00);
        o.write(0x01);
        o.write(0x21);
        for (int i = 0; i < 23; i++) o.write(0);
        for (byte b : "root".getBytes(StandardCharsets.US_ASCII)) o.write(b);
        o.write(0);
        if (passLen > 0) {
            o.write(20);
            o.writeBytes(AUTH_SCRAMBLE);
        } else {
            o.write(0);
        }
        for (byte b : "kof".getBytes(StandardCharsets.US_ASCII)) o.write(b);
        o.write(0);
        for (byte b : "mysql_native_password".getBytes(StandardCharsets.US_ASCII)) o.write(b);
        o.write(0);
        return o.toByteArray();
    }

    private static String authOracle() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int passLen : new int[]{20, 0}) {
            byte[] p = authPayload(passLen);
            if (!first) sb.append('\n');
            first = false;
            sb.append(p.length);
            for (byte b : p) sb.append('\n').append(b & 0xff);
        }
        return sb.toString();
    }

    private static String authHarness() {
        return """
                .section .rodata
                .Law_scramble:
                    .byte 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17
                    .byte 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
                    .byte 0x20, 0x21, 0x22, 0x23
                .section .data
                .align 3
                .Law_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Law_db:
                    .zero 16
                    .word 3
                    .zero 4
                    .ascii "kof"
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -528
                    mv   s0, sp
                    # caso passLen=20
                    mv   a0, s0
                    la   a1, .Law_scramble
                    la   a2, .Law_user
                    la   a3, .Law_db
                    li   a4, 20
                    call kof_db_mysql_build_auth_response
                    mv   s1, a0
                    call kof_println_int
                    li   s2, 0
                .Law_pr1:
                    bge  s2, s1, .Law_pr1_done
                    add  t0, s0, s2
                    addi t0, t0, 4
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s2, s2, 1
                    j    .Law_pr1
                .Law_pr1_done:
                    # caso passLen=0
                    mv   a0, s0
                    la   a1, .Law_scramble
                    la   a2, .Law_user
                    la   a3, .Law_db
                    li   a4, 0
                    call kof_db_mysql_build_auth_response
                    mv   s1, a0
                    call kof_println_int
                    li   s2, 0
                .Law_pr2:
                    bge  s2, s1, .Law_pr2_done
                    add  t0, s0, s2
                    addi t0, t0, 4
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s2, s2, 1
                    j    .Law_pr2
                .Law_pr2_done:
                    li   a0, 0
                    li   a7, 93
                    ecall
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                """;
    }

    @Test
    void authResponseMatchesOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = authHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "auth_rv", harness + "\n" + runtime);
        assertEquals(authOracle(), out, "auth response riscv64 diverge do oráculo");
    }

    @Test
    void authResponseMatchesOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = authHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "auth_aa", arm.toString());
        assertEquals(authOracle(), out, "auth response aarch64 diverge do oráculo");
    }

    @Test
    void withoutAuthResponsePieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = authHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b65 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_65".equals(p.field())) b65 = p.index();
        }
        assertTrue(b65 >= 0, "peça B65 (auth response) não encontrada no inventário");
        assertTrue(keep.remove(b65), "B65 deveria estar no keep do harness de auth response");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_authr.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_authr.o");
        Path bin = tempDir.resolve("sab_authr");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B65 o link deveria falhar (undefined kof_db_mysql_build_auth_response); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_build_auth_response"),
                "a falha deve citar kof_db_mysql_build_auth_response: " + r[0]);
    }

    private static int mysqlPort() {
        return Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306"));
    }

    /** Pula se o MariaDB real não estiver acessível (mesma porta de KofDbE2ETest). */
    private void assumeMaria() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", mysqlPort()), 500);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "MariaDB em 127.0.0.1:" + mysqlPort() + " ausente — pulando (S5.1 real)");
        }
    }

    /** _start: handshake real com credenciais corretas (0) e senha errada (-1). */
    private static String mysqlHandshakeHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        return """
                .section .data
                .align 3
                .Lmh_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lmh_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lmh_baddb:
                    .zero 16
                    .word 18
                    .zero 4
                    .ascii "kof_no_such_db_xyz"
                .align 3
                .Lmh_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lmh_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    # conexao 1: credenciais corretas
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lmh_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lmh_user
                    la   a2, .Lmh_pass
                    la   a3, .Lmh_db
                    call kof_db_mysql_handshake
                    call kof_println_int
                    mv   a0, s0
                    call kof_plat_close
                    # conexao 2: banco inexistente -> ERR (1049) do servidor
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lmh_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lmh_user
                    la   a2, .Lmh_pass
                    la   a3, .Lmh_baddb
                    call kof_db_mysql_handshake
                    call kof_println_int
                    mv   a0, s0
                    call kof_plat_close
                    li   a0, 0
                    li   a7, 93
                    ecall
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                """.replace("PORT_HI", hi).replace("PORT_LO", lo);
    }

    @Test
    void handshakeAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = mysqlHandshakeHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "hs_rv", harness + "\n" + runtime);
        assertEquals("0\n-1", out, "handshake riscv64: creds ok devem dar 0 e banco inexistente -1");
    }

    @Test
    void handshakeAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = mysqlHandshakeHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "hs_aa", arm.toString());
        assertEquals("0\n-1", out, "handshake aarch64: creds ok devem dar 0 e banco inexistente -1");
    }

    @Test
    void withoutHandshakePieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlHandshakeHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b66 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_66".equals(p.field())) b66 = p.index();
        }
        assertTrue(b66 >= 0, "peça B66 (handshake) não encontrada no inventário");
        assertTrue(keep.remove(b66), "B66 deveria estar no keep do harness de handshake");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_hs.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_hs.o");
        Path bin = tempDir.resolve("sab_hs");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B66 o link deveria falhar (undefined kof_db_mysql_handshake); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_handshake"),
                "a falha deve citar kof_db_mysql_handshake: " + r[0]);
    }

    /**
     * S5.2 (db-parity-plan, gaps-db lane, 23/09): envia COM_QUERY texto e le o
     * 1o pacote de resposta para classificar — {@code >=1}=column count de um
     * resultset, {@code 0x00}=OK, {@code 0xFF}=ERR. Cada comando usa conexao
     * propria (SELECT gera varios pacotes; uma conexao por comando evita ler
     * sobras do resultset anterior, que e o escopo do B67 — so a 1a resposta).
     */
    private static String mysqlCommandHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        return """
                .section .data
                .align 3
                .Lmq_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lmq_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lmq_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lmq_sel:
                    .zero 16
                    .word 8
                    .zero 4
                    .ascii "SELECT 1"
                .align 3
                .Lmq_set:
                    .zero 16
                    .word 12
                    .zero 4
                    .ascii "SET @kof_x=1"
                .align 3
                .Lmq_bad:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "SELEC 1"
                .align 3
                .Lmq_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    li   t6, 4224
                    sub  sp, sp, t6
                    addi s2, sp, 128          # buffer de response (4096)
                    la   a0, .Lmq_sel
                    call .Lmq_one
                    call kof_println_int
                    la   a0, .Lmq_set
                    call .Lmq_one
                    call kof_println_int
                    la   a0, .Lmq_bad
                    call .Lmq_one
                    call kof_println_int
                    li   a0, 0
                    li   a7, 93
                    ecall
                # a0 = sql KofString -> a0 = byte de classificacao | -1
                .Lmq_one:
                    addi sp, sp, -64
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s3, 24(sp)
                    mv   s1, a0
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lmq_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lmq_user
                    la   a2, .Lmq_pass
                    la   a3, .Lmq_db
                    call kof_db_mysql_handshake
                    bnez a0, .Lmq_one_fail
                    mv   a0, s0
                    mv   a1, s1
                    mv   a2, s2
                    li   a3, 4096
                    call kof_db_mysql_command
                    blt  a0, zero, .Lmq_one_fail
                    lbu  s3, 0(a1)
                    j    .Lmq_one_close
                .Lmq_one_fail:
                    li   s3, -1
                .Lmq_one_close:
                    mv   a0, s0
                    call kof_plat_close
                    mv   a0, s3
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s3, 24(sp)
                    addi sp, sp, 64
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                """.replace("PORT_HI", hi).replace("PORT_LO", lo);
    }

    @Test
    void commandClassifiesResponseAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = mysqlCommandHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "cmd_rv", harness + "\n" + runtime);
        assertEquals("1\n0\n255", out, "COM_QUERY riscv64: SELECT=1, SET=0, SQL ruim=255");
    }

    @Test
    void commandClassifiesResponseAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = mysqlCommandHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "cmd_aa", arm.toString());
        assertEquals("1\n0\n255", out, "COM_QUERY aarch64: SELECT=1, SET=0, SQL ruim=255");
    }

    @Test
    void withoutCommandPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlCommandHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b67 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_67".equals(p.field())) b67 = p.index();
        }
        assertTrue(b67 >= 0, "peça B67 (COM_QUERY) não encontrada no inventário");
        assertTrue(keep.remove(b67), "B67 deveria estar no keep do harness de COM_QUERY");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_cmd.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_cmd.o");
        Path bin = tempDir.resolve("sab_cmd");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B67 o link deveria falhar (undefined kof_db_mysql_command); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_command"),
                "a falha deve citar kof_db_mysql_command: " + r[0]);
    }

    /**
     * S5.2 (db-parity-plan, gaps-db lane, 23/09): parseia o cabecalho do
     * resultset texto — ncols + a PRIMEIRA linha (payload cru, celulas lenenc).
     * Prova contra o MariaDB real: `SELECT 1` → 1 coluna, linha [0x01,'1'];
     * `SELECT 1,'ab'` → 2 colunas, linha [0x01,'1',0x02,'a','b'].
     */
    private static String mysqlResultsetHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        return """
                .section .data
                .align 3
                .Lqt_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lqt_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lqt_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lqt_sel1:
                    .zero 16
                    .word 8
                    .zero 4
                    .ascii "SELECT 1"
                .align 3
                .Lqt_sel2:
                    .zero 16
                    .word 14
                    .zero 4
                    .ascii "SELECT 1,'ab'"
                .align 3
                .Lqt_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    la   a0, .Lqt_sel1
                    call .Lqt_one
                    la   a0, .Lqt_sel2
                    call .Lqt_one
                    li   a0, 0
                    li   a7, 93
                    ecall
                # a0 = sql -> imprime ncols, rowlen e os bytes da 1a linha
                .Lqt_one:
                    addi sp, sp, -96
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    sd   s3, 32(sp)
                    sd   s4, 40(sp)
                    sd   s5, 48(sp)
                    mv   s1, a0
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lqt_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lqt_user
                    la   a2, .Lqt_pass
                    la   a3, .Lqt_db
                    call kof_db_mysql_handshake
                    bnez a0, .Lqt_fail
                    mv   a0, s0
                    mv   a1, s1
                    call kof_db_mysql_query_text
                    blt  a0, zero, .Lqt_fail
                    mv   s2, a0
                    mv   s3, a1
                    mv   s4, a2
                    mv   a0, s2
                    call kof_println_int
                    mv   a0, s4
                    call kof_println_int
                    li   s5, 0
                .Lqt_bytes:
                    bge  s5, s4, .Lqt_close
                    add  t0, s3, s5
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s5, s5, 1
                    j    .Lqt_bytes
                .Lqt_fail:
                    li   a0, -1
                    call kof_println_int
                .Lqt_close:
                    mv   a0, s0
                    call kof_plat_close
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    ld   s3, 32(sp)
                    ld   s4, 40(sp)
                    ld   s5, 48(sp)
                    addi sp, sp, 96
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                """.replace("PORT_HI", hi).replace("PORT_LO", lo);
    }

    @Test
    void resultsetHeaderAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = mysqlResultsetHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "rs_rv", harness + "\n" + runtime);
        assertEquals("1\n2\n1\n49\n2\n5\n1\n49\n2\n97\n98", out,
                "resultset riscv64: SELECT 1 -> 1 col/[01 31]; SELECT 1,'ab' -> 2 col/[01 31 02 61 62]");
    }

    @Test
    void resultsetHeaderAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = mysqlResultsetHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "rs_aa", arm.toString());
        assertEquals("1\n2\n1\n49\n2\n5\n1\n49\n2\n97\n98", out,
                "resultset aarch64: SELECT 1 -> 1 col/[01 31]; SELECT 1,'ab' -> 2 col/[01 31 02 61 62]");
    }

    @Test
    void withoutReaderPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlResultsetHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b68 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_68".equals(p.field())) b68 = p.index();
        }
        assertTrue(b68 >= 0, "peça B68 (reader de pacotes) não encontrada no inventário");
        assertTrue(keep.remove(b68), "B68 deveria estar no keep do harness de resultset");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_rd.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_rd.o");
        Path bin = tempDir.resolve("sab_rd");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B68 o link deveria falhar (undefined kof_db_mysql_next); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_next"),
                "a falha deve citar kof_db_mysql_next: " + r[0]);
    }

    @Test
    void withoutResultsetsPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlResultsetHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b69 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_69".equals(p.field())) b69 = p.index();
        }
        assertTrue(b69 >= 0, "peça B69 (resultset texto) não encontrada no inventário");
        assertTrue(keep.remove(b69), "B69 deveria estar no keep do harness de resultset");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_rs.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_rs.o");
        Path bin = tempDir.resolve("sab_rs");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B69 o link deveria falhar (undefined kof_db_mysql_query_text); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_query_text"),
                "a falha deve citar kof_db_mysql_query_text: " + r[0]);
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
