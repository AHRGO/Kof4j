package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de observability2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeObservability2 {

    private RuntimeObservability2() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
                movq %r10, %rsi
                call kof_string_concat
                movq %rax, %r14
                popq %rsi
                ret

            # ── export counters ──
            .Lobs_m_cnt_loop:
                movq .Lkof_obs_counter_len(%rip), %r13
                cmpq %r13, %r12
                jge .Lobs_m_gauges
                leaq .Lkof_obs_counters(%rip), %rbx
                movq %r12, %rax
                shlq $4, %rax
                addq %rax, %rbx
                movq 0(%rbx), %rax
                testq %rax, %rax
                jz .Lobs_m_cnt_next
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_counter(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movl 8(%rbx), %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
            .Lobs_m_cnt_next:
                incq %r12
                jmp .Lobs_m_cnt_loop

            # ── export gauges ──
            .Lobs_m_gauges:
                xorq %r12, %r12
            .Lobs_m_ga_loop:
                movq .Lkof_obs_gauge_len(%rip), %r13
                cmpq %r13, %r12
                jge .Lobs_m_hists
                leaq .Lkof_obs_gauges(%rip), %rbx
                movq %r12, %rax
                shlq $4, %rax
                addq %rax, %rbx
                movq 0(%rbx), %rax
                testq %rax, %rax
                jz .Lobs_m_ga_next
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_gauge(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movl 8(%rbx), %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
            .Lobs_m_ga_next:
                incq %r12
                jmp .Lobs_m_ga_loop

            # ── export histograms ──
            .Lobs_m_hists:
                xorq %r12, %r12
            .Lobs_m_hi_loop:
                movq .Lkof_obs_histogram_len(%rip), %r13
                cmpq %r13, %r12
                jge .Lobs_m_done
                leaq .Lkof_obs_histograms(%rip), %rbx
                movq %r12, %rax
                shlq $5, %rax
                addq %rax, %rbx
                movq 0(%rbx), %rax
                testq %rax, %rax
                jz .Lobs_m_hi_next
                # # TYPE <n>_count counter\n
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_count(%rip), %rdi
                movl $6, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_counter(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                # <n>_count <c>\n
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_count(%rip), %rdi
                movl $6, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 16(%rbx), %rax
                movl %eax, %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                # # TYPE <n>_sum gauge\n
                leaq .Lstr_obs_type_counter(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_sum(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_ws_nl_gauge(%rip), %rdi
                movl $7, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                # <n>_sum <s>\n
                movq 0(%rbx), %rsi
                xorl %r8d, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_suffix_sum(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_space(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                movq 8(%rbx), %rax
                movl %eax, %edi
                call kof_int_to_string
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
                leaq .Lstr_obs_nl(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movl $1, %r8d
                call .Lobs_m_append
            .Lobs_m_hi_next:
                incq %r12
                jmp .Lobs_m_hi_loop

            # ── final: libera temp restante e retorna acc ──
            .Lobs_m_done:
                testq %r15, %r15
                jz .Lobs_m_free_done
                movq %r15, %rdi
                call kof_free
            .Lobs_m_free_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }
}
