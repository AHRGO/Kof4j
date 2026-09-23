package dev.kof.compiler.nat;

// S5.1 (db-parity-plan, gaps-db lane, 23/09): fecha o handshake/auth do MySQL
// no cross — le o greeting, extrai o seed, calcula o scramble, monta e envia o
// handshake response e le o OK/ERR. Port do fluxo de RuntimeDb3 (x86), agora
// sobre as pecas B62 (SHA1), B63 (scramble/lenenc), B64 (greeting) e B65
// (auth response) + a HAL de socket (kof_plat_net_*). A prova e o round-trip
// real contra o MariaDB sob qemu.
//
// Contrato riscv:
//   kof_db_mysql_handshake(a0=fd, a1=user KofString, a2=pass KofString,
//                          a3=db KofString) -> a0 = 0 ok | -1 falha
public final class NativeRiscvAsmRtB66 {

    private NativeRiscvAsmRtB66() {}

    static String RISCV_RUNTIME_ASM_B_66 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_handshake(a0=fd,a1=user,a2=pass,a3=db) -> 0 ok|-1
            # Frame 8368: ra+s0..s8 (0..79) | greeting 4096 (128..4223) |
            #             auth 4096 (4224..8319) | seed20 (8320..8339) |
            #             scramble20 (8340..8359)
            # ---------------------------------------------------------------
            .globl kof_db_mysql_handshake
            .type kof_db_mysql_handshake, @function
            kof_db_mysql_handshake:
                li   t6, 8368
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                sd   s6, 56(sp)
                sd   s7, 64(sp)
                sd   s8, 72(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   s3, a3
                # le o greeting
                mv   a0, s0
                addi a1, sp, 128
                li   a2, 4096
                call kof_plat_read
                blez a0, .L66_fail
                # extrai o seed (20 bytes)
                addi a0, sp, 128
                li   t0, 8320
                add  a1, sp, t0
                call kof_db_mysql_parse_greeting
                beqz a0, .L66_fail
                # passLen
                li   s4, 0
                beqz s2, .L66_nopass
                lw   s4, 16(s2)
            .L66_nopass:
                beqz s4, .L66_skip_scr
                li   t0, 8340
                add  a0, sp, t0
                li   t0, 8320
                add  a1, sp, t0
                li   a2, 20
                mv   a3, s2
                call kof_db_mysql_scramble
            .L66_skip_scr:
                # monta o handshake response
                li   t0, 4224
                add  a0, sp, t0
                li   t0, 8340
                add  a1, sp, t0
                mv   a2, s1
                mv   a3, s3
                mv   a4, s4
                call kof_db_mysql_build_auth_response
                mv   s5, a0
                # envia
                mv   a0, s0
                li   t0, 4224
                add  a1, sp, t0
                addi a2, s5, 4
                call kof_plat_write
                # le a resposta (OK/ERR)
                mv   a0, s0
                addi a1, sp, 128
                li   a2, 4096
                call kof_plat_read
                blez a0, .L66_fail
                lbu  t1, 132(sp)
                beqz t1, .L66_ok
                j    .L66_fail
            .L66_ok:
                li   a0, 0
                j    .L66_ret
            .L66_fail:
                li   a0, -1
            .L66_ret:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                ld   s5, 48(sp)
                ld   s6, 56(sp)
                ld   s7, 64(sp)
                ld   s8, 72(sp)
                li   t6, 8368
                add  sp, sp, t6
                ret
            .section .text
            """;
}
