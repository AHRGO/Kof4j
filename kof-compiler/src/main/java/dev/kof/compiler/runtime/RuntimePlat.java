package dev.kof.compiler.runtime;

/**
 * B-0 (D-BAREMETAL-BOOT): costura de plataforma {@code kof_plat_*} — face
 * x86_64. Mesma ABI da face riscv64 ({@code NativeRiscvAsmRt0}): a
 * implementação Linux é syscall direto; o perfil bare-metal (B-1+) troca só
 * o corpo, sem tocar os chamadores.
 *
 * <p>Superfície desta fatia (núcleo single-thread): {@code kof_plat_write},
 * {@code kof_plat_writev}, {@code kof_plat_exit} e
 * {@code kof_plat_exit_group}. As fatias seguintes acrescentam
 * tempo/sono/entropia/tid e depois spawn/futex e sockets.
 */
public final class RuntimePlat {

    private RuntimePlat() {}

    public static void emitPlatSeam(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_write
            .type kof_plat_write, @function
            kof_plat_write:
                movq $1, %rax
                syscall
                ret

            .globl kof_plat_writev
            .type kof_plat_writev, @function
            kof_plat_writev:
                movq $20, %rax
                syscall
                ret

            .globl kof_plat_exit
            .type kof_plat_exit, @function
            kof_plat_exit:
                movq $60, %rax
                syscall

            .globl kof_plat_exit_group
            .type kof_plat_exit_group, @function
            kof_plat_exit_group:
                movq $231, %rax
                syscall
            """);
    }
}
