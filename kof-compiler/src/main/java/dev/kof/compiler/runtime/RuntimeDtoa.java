package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * §180 (DECISIONS §6, 14/09): Double/Float -> String no runtime nativo x86_64
 * com o MESMO contrato do JDK Double.toString/Float.toString:
 *
 *  - decimal MAIS CURTO que faz round-trip: loop limitado `%.*e` (prec 0..16
 *    no double, 0..8 no float) + strtod; o 1o prec que reconstroi os bits
 *    vence (mesma aproximacao deterministica que a decisao ratificou);
 *  - limiar cientifico do Java: notacao cientifica sse |v| < 1e-3 ou >= 1e7
 *    (glibc %g usa outro limiar — por isso o reformat e explicito);
 *  - 'E' maiusculo, expoente sem '+' nem zeros a esquerda, mantissa SEMPRE com
 *    parte fracionaria ("1.0E7", "1.0E-5");
 *  - Float imprime a PROPRIA forma curta (nao a expansao double);
 *  - NaN/Infinity normalizados (glibc escreve nan/inf).
 *
 * Substitui o antigo `snprintf("%.16g")` (que truncava o shortest round-trip,
 * divergia o limiar e imprimia Float como double — §180 faces a/b/c).
 *
 * Requer libc (snprintf/strtod): x86_64 only; riscv/aarch continuam FLT001.
 * NOTA: o stack do runtime Kof nao garante 16-byte alignment no call site —
 * as funcoes abaixo alinham explicitamente antes de chamar snprintf/strtod
 * (movaps de glibc exige 16B), como fazia o antigo kof_print_double.
 */
public final class RuntimeDtoa {

    private RuntimeDtoa() {}

    public static void emitDtoa(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .Lfmt_sci: .asciz "%.*e"
            .Lfmt_fix: .asciz "%.*f"
            .Lstr_inf: .asciz "Infinity"
            .Lstr_ninf: .asciz "-Infinity"
            .Lstr_nan: .asciz "NaN"
            .Lstr_null: .asciz "null"
            .section .text

            # ---- kof_dtoa_format ----
            # rdi = buf1 ("d[.ddd]e±XX" — saida do loop %.*e)
            # rsi = buf2 (saida, >= 64 bytes)
            # edx = prec (digitos fracionarios da mantissa)
            # xmm0 = valor (double; no float ja vem alargado p/ %.f)
            # retorna eax = comprimento de buf2 (sem NUL).
            .globl kof_dtoa_format
            .type kof_dtoa_format, @function
            kof_dtoa_format:
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $200, %rsp
                andq $-16, %rsp
                movq %rdi, %rbx             # rbx = buf1
                movq %rsi, %r14             # r14 = buf2
                movl %edx, %r12d            # r12 = prec
                movsd %xmm0, -48(%rbp)      # valor
                movq %rbx, %rdi
            .Ldtf_finde:
                movzbl (%rdi), %eax
                testb %al, %al
                jz .Ldtf_raw                # sem 'e' (nao deve ocorrer) -> copia
                cmpb $101, %al              # 'e'
                je .Ldtf_ate
                incq %rdi
                jmp .Ldtf_finde
            .Ldtf_ate:
                incq %rdi                   # pula 'e'
                xorl %r13d, %r13d           # exp
                xorl %r15d, %r15d           # neg
                movzbl (%rdi), %eax
                cmpb $45, %al               # '-'
                jne .Ldtf_ate_p
                movl $1, %r15d
                incq %rdi
                jmp .Ldtf_ated
            .Ldtf_ate_p:
                cmpb $43, %al               # '+'
                jne .Ldtf_ated
                incq %rdi
            .Ldtf_ated:
                movzbl (%rdi), %eax
                cmpb $48, %al
                jb .Ldtf_ated_done
                cmpb $57, %al
                ja .Ldtf_ated_done
                imull $10, %r13d, %r13d
                subl $48, %eax
                addl %eax, %r13d
                incq %rdi
                jmp .Ldtf_ated
            .Ldtf_ated_done:
                testl %r15d, %r15d
                jz .Ldtf_decide
                negl %r13d
            .Ldtf_decide:
                cmpl $-3, %r13d             # -3 <= e < 7 -> fixo; senao cientifico
                jl .Ldtf_sci
                cmpl $7, %r13d
                jl .Ldtf_plain
                jmp .Ldtf_sci
            .Ldtf_plain:
                movl %r12d, %ecx            # frac = prec - e
                subl %r13d, %ecx
                testl %ecx, %ecx
                jns .Ldtf_plain_p
                xorl %ecx, %ecx
            .Ldtf_plain_p:
                movq %r14, %rdi
                movq $64, %rsi
                leaq .Lfmt_fix(%rip), %rdx
                movsd -48(%rbp), %xmm0
                movl $1, %eax
                call snprintf
                movq %r14, %rbx
                xorl %r15d, %r15d           # len
                xorl %r12d, %r12d           # hasdot
            .Ldtf_p_scan:
                movzbl (%rbx,%r15), %eax
                testb %al, %al
                jz .Ldtf_p_end
                cmpb $46, %al               # '.'
                jne .Ldtf_p_next
                movl $1, %r12d
            .Ldtf_p_next:
                incq %r15
                jmp .Ldtf_p_scan
            .Ldtf_p_end:
                testl %r12d, %r12d
                jnz .Ldtf_ret
                movw $12334, (%rbx,%r15)    # ".0" (0x302E LE)
                movb $0, 2(%rbx,%r15)
                addq $2, %r15
                jmp .Ldtf_ret
            .Ldtf_sci:
                movq %rbx, %rdi             # src = buf1
                movq %r14, %rsi             # dst = buf2
                xorl %r15d, %r15d           # dst len
                xorl %r12d, %r12d           # hasdot
            .Ldtf_s_copy:
                movzbl (%rdi), %eax
                testb %al, %al
                jz .Ldtf_s_mant_end
                cmpb $101, %al              # 'e'
                je .Ldtf_s_mant_end
                cmpb $46, %al               # '.'
                jne .Ldtf_s_copy1
                movl $1, %r12d
            .Ldtf_s_copy1:
                movb %al, (%rsi,%r15)
                incq %r15
                incq %rdi
                jmp .Ldtf_s_copy
            .Ldtf_s_mant_end:
                testl %r12d, %r12d
                jnz .Ldtf_s_E
                movb $46, (%rsi,%r15)       # ".0"
                incq %r15
                movb $48, (%rsi,%r15)
                incq %r15
            .Ldtf_s_E:
                movb $69, (%rsi,%r15)       # 'E'
                incq %r15
                testl %r13d, %r13d
                jns .Ldtf_s_eabs
                movb $45, (%rsi,%r15)       # '-'
                incq %r15
                negl %r13d
            .Ldtf_s_eabs:
                leaq -96(%rbp), %rdi        # temp (digitos em ordem inversa)
                movl %r13d, %eax
                xorl %r11d, %r11d
                movl $10, %r9d
                testl %eax, %eax
                jnz .Ldtf_s_ediv
                movb $48, (%rdi)
                movl $1, %r11d
                jmp .Ldtf_s_erev
            .Ldtf_s_ediv:
                xorl %edx, %edx
                divl %r9d
                addb $48, %dl
                movb %dl, (%rdi,%r11)
                incq %r11
                testl %eax, %eax
                jnz .Ldtf_s_ediv
            .Ldtf_s_erev:
                decq %r11
            .Ldtf_s_erev1:
                movzbl (%rdi,%r11), %eax
                movb %al, (%rsi,%r15)
                incq %r15
                decq %r11
                jns .Ldtf_s_erev1
                movb $0, (%rsi,%r15)
                jmp .Ldtf_ret
            .Ldtf_raw:
                movq %rbx, %rdi
                xorl %r15d, %r15d
            .Ldtf_raw_l:
                movzbl (%rdi,%r15), %eax
                testb %al, %al
                jz .Ldtf_raw_e
                movb %al, (%r14,%r15)
                incq %r15
                jmp .Ldtf_raw_l
            .Ldtf_raw_e:
                movb $0, (%r14,%r15)
            .Ldtf_ret:
                movl %r15d, %eax
                leaq -40(%rbp), %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret

            # ---- kof_double_to_string(xmm0: double) -> rax: String* ----
            .globl kof_double_to_string
            .type kof_double_to_string, @function
            kof_double_to_string:
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $200, %rsp
                andq $-16, %rsp
                movsd %xmm0, -48(%rbp)      # valor
                movq %xmm0, %rax
                movq %rax, %rcx
                shrq $52, %rcx
                andl $0x7ff, %ecx
                cmpl $0x7ff, %ecx
                je .Ld2s_naninf
                xorl %r12d, %r12d           # prec = 0
            .Ld2s_loop:
                leaq -112(%rbp), %rdi       # buf1
                movq $64, %rsi
                leaq .Lfmt_sci(%rip), %rdx
                movl %r12d, %ecx
                movsd -48(%rbp), %xmm0
                movl $1, %eax
                call snprintf
                leaq -112(%rbp), %rdi
                xorl %esi, %esi
                call strtod
                movq %xmm0, %rax
                cmpq -48(%rbp), %rax
                je .Ld2s_fmt
                incl %r12d
                cmpl $17, %r12d
                jl .Ld2s_loop
                movl $16, %r12d
                leaq -112(%rbp), %rdi
                movq $64, %rsi
                leaq .Lfmt_sci(%rip), %rdx
                movl %r12d, %ecx
                movsd -48(%rbp), %xmm0
                movl $1, %eax
                call snprintf
            .Ld2s_fmt:
                leaq -112(%rbp), %rdi
                leaq -176(%rbp), %rsi
                movl %r12d, %edx
                movsd -48(%rbp), %xmm0
                call kof_dtoa_format
                leaq -176(%rbp), %rdi
                movl %eax, %esi
                call kof_string_from_literal
                jmp .Ld2s_done
            .Ld2s_naninf:
                movq -48(%rbp), %rax
                movq %rax, %rcx
                shlq $12, %rcx              # mantissa != 0 -> NaN
                jnz .Ld2s_nan
                testq %rax, %rax
                js .Ld2s_ninf
                leaq .Lstr_inf(%rip), %rdi
                movl $8, %esi
                call kof_string_from_literal
                jmp .Ld2s_done
            .Ld2s_ninf:
                leaq .Lstr_ninf(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                jmp .Ld2s_done
            .Ld2s_nan:
                leaq .Lstr_nan(%rip), %rdi
                movl $3, %esi
                call kof_string_from_literal
            .Ld2s_done:
                leaq -40(%rbp), %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret

            # ---- kof_float_to_string(xmm0: float) -> rax: String* ----
            .globl kof_float_to_string
            .type kof_float_to_string, @function
            kof_float_to_string:
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $200, %rsp
                andq $-16, %rsp
                movss %xmm0, -48(%rbp)      # valor (float, 4 bytes)
                movd %xmm0, %eax
                movl %eax, %ecx
                shrl $23, %ecx
                andl $0xff, %ecx
                cmpl $0xff, %ecx
                je .Lf2s_naninf
                xorl %r12d, %r12d           # prec = 0
            .Lf2s_loop:
                leaq -112(%rbp), %rdi
                movq $64, %rsi
                leaq .Lfmt_sci(%rip), %rdx
                movl %r12d, %ecx
                movss -48(%rbp), %xmm0
                cvtss2sd %xmm0, %xmm0
                movl $1, %eax
                call snprintf
                leaq -112(%rbp), %rdi
                xorl %esi, %esi
                call strtod
                cvtsd2ss %xmm0, %xmm0
                movd %xmm0, %eax
                cmpl -48(%rbp), %eax
                je .Lf2s_fmt
                incl %r12d
                cmpl $9, %r12d
                jl .Lf2s_loop
                movl $8, %r12d
                leaq -112(%rbp), %rdi
                movq $64, %rsi
                leaq .Lfmt_sci(%rip), %rdx
                movl %r12d, %ecx
                movss -48(%rbp), %xmm0
                cvtss2sd %xmm0, %xmm0
                movl $1, %eax
                call snprintf
            .Lf2s_fmt:
                leaq -112(%rbp), %rdi
                leaq -176(%rbp), %rsi
                movl %r12d, %edx
                movss -48(%rbp), %xmm0
                cvtss2sd %xmm0, %xmm0
                call kof_dtoa_format
                leaq -176(%rbp), %rdi
                movl %eax, %esi
                call kof_string_from_literal
                jmp .Lf2s_done
            .Lf2s_naninf:
                movd %xmm0, %eax
                movl %eax, %ecx
                shll $9, %ecx               # mantissa != 0 -> NaN
                jnz .Lf2s_nan
                testl %eax, %eax
                js .Lf2s_ninf
                leaq .Lstr_inf(%rip), %rdi
                movl $8, %esi
                call kof_string_from_literal
                jmp .Lf2s_done
            .Lf2s_ninf:
                leaq .Lstr_ninf(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                jmp .Lf2s_done
            .Lf2s_nan:
                leaq .Lstr_nan(%rip), %rdi
                movl $3, %esi
                call kof_string_from_literal
            .Lf2s_done:
                leaq -40(%rbp), %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }
}
