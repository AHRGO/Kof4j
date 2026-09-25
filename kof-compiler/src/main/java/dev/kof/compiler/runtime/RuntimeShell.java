package dev.kof.compiler.runtime;

/**
 * D-FULL-PARITY-050 row 2 (shell.*) slice A — x86-64 runtime piece:
 * {@code kof_shell_argv(program, args)} = {@code [program, args...]} (the
 * JVM oracle is a prepend, JvmRuntimeCore.kof_shell_argv — no splitting).
 * Pure list surgery, zero libc — usable even in freestanding.
 * {@code kof_shell_pipeline}/{@code kof_shell_runwith} stay PROC001
 * (slice B) — the lowerer gates them honestly.
 */
public final class RuntimeShell {

    private RuntimeShell() {
    }

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.shell (D-FULL-PARITY-050 row 2 slice A) ────────────────
            .globl kof_shell_argv
            .type kof_shell_argv, @function
            kof_shell_argv:
                # (rdi=program KofString, rsi=args List|null) -> List [program, args...]
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # program
                movq %rsi, %r12             # args (pode ser null)
                call kof_list_new
                movq %rax, %r13             # novo argv
                movq %r13, %rdi
                movq %rbx, %rsi
                call kof_list_add           # argv[0] = program
                xorl %r14d, %r14d           # i = 0
            .Lkof_shell_argv_loop:
                testq %r12, %r12
                jz .Lkof_shell_argv_done
                movl 16(%r12), %eax         # args.size
                cmpl %eax, %r14d
                jge .Lkof_shell_argv_done
                movq %r12, %rdi
                movl %r14d, %esi
                call kof_list_get
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_list_add
                incl %r14d
                jmp .Lkof_shell_argv_loop
            .Lkof_shell_argv_done:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
