package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 row 2 (shell.*) — CROSS riscv64/aarch64 runtime piece:
 * {@code kof_shell_argv(program@a0, args@a1|null) -> List [program, args...]}
 * (the JVM oracle is a prepend, {@code JvmRuntimeCore.kof_shell_argv} — no
 * splitting). Pure list surgery ({@code kof_list_new/add/get}), zero libc.
 * {@code shell.run} reuses {@code kof_process_run} (row 1 slice C);
 * {@code pipeline}/{@code runWith} stay PROC001 (the lowerer gates them).
 */
public final class NativeRiscvAsmShell {

    private NativeRiscvAsmShell() {
    }

    public static final String RISCV_RUNTIME_ASM_SHELL = """
            # ── kof.shell (D-FULL-PARITY-050 row 2) — cross riscv64/aarch64 ──
            .section .text
            .globl kof_shell_argv
            .type kof_shell_argv, @function
            kof_shell_argv:
                # a0 = program (KofString), a1 = args (KofList|null)
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0                # program
                mv   s1, a1                # args (pode ser null)
                call kof_list_new
                mv   s2, a0                # argv
                mv   a0, s2
                mv   a1, s0
                call kof_list_add          # argv[0] = program
                li   s3, 0                 # i = 0
            .Lkof_shargv_loop:
                beqz s1, .Lkof_shargv_done
                lw   t0, 16(s1)            # args.size
                bge  s3, t0, .Lkof_shargv_done
                mv   a0, s1
                mv   a1, s3
                call kof_list_get          # a0 = item
                mv   a1, a0
                mv   a0, s2
                call kof_list_add
                addi s3, s3, 1
                j    .Lkof_shargv_loop
            .Lkof_shargv_done:
                mv   a0, s2
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret
            """;
}
