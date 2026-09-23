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

            .globl kof_plat_time
            .type kof_plat_time, @function
            kof_plat_time:
                movq %rdi, %rsi          # ts
                xorq %rdi, %rdi          # CLOCK_REALTIME
                movq $228, %rax
                syscall
                ret

            .globl kof_plat_time_mono
            .type kof_plat_time_mono, @function
            kof_plat_time_mono:
                movq %rdi, %rsi          # ts
                movq $1, %rdi            # CLOCK_MONOTONIC
                movq $228, %rax
                syscall
                ret

            .globl kof_plat_sleep
            .type kof_plat_sleep, @function
            kof_plat_sleep:
                movq $35, %rax           # nanosleep(req, rem)
                syscall
                ret

            .globl kof_plat_random
            .type kof_plat_random, @function
            kof_plat_random:
                movq $318, %rax          # getrandom(buf, len, 0)
                xorq %rdx, %rdx
                syscall
                ret

            .globl kof_plat_thread_id
            .type kof_plat_thread_id, @function
            kof_plat_thread_id:
                movq $186, %rax          # gettid
                syscall
                ret

            .globl kof_plat_sync
            .type kof_plat_sync, @function
            kof_plat_sync:
                movq $202, %rax          # futex(uaddr, op, val, timeout)
                syscall
                ret
            """);
    }
}
