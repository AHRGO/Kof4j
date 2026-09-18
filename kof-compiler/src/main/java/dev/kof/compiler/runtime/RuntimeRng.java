package dev.kof.compiler.runtime;

/**
 * Fatia de runtime x86_64 — kof.rng (X8 fatia 2, IMPLEMENTATION-UNIVERSAL-
 * PLATFORM fila X). PRNG semeável (xorshift128 + splitmix32 no seed) com a
 * MESMA matemática de 32 bits dos fragments JVM (JvmStringRngRuntime) e JS
 * (JsRuntimeUiRng): só xor/shift/adição/multiplicação com wrap u32 — mesmos
 * bits por construção nos 3 backends (paridade provada em KofRngTest rodando
 * o MESMO programa no JVM e no NATIVE).
 *
 * <p>Estado global em .data (4 words, default 0,0,0,1 — o mesmo default dos
 * outros backends). cai dentro do intervalo de raízes conservativo
 * (root_start.._end): falso-positivo de root-scan = SOBRE-marcar (mark-sweep
 * não move; nunca under-mark), e o precedente é a data de cache/config que já
 * guarda palavras arbitrárias (hashes) ali há meses.
 *
 * <p>NUNCA para chaves/segredos (isso é random/security, R11); rng é
 * determinismo reprodutível (R10). int(bound) = módulo com viés minúsculo
 * documentado — o contrato é paridade determinística. string: layout ASCII
 * da String nativa (mesma ressalva do S2 — paridade UTF-16 é JVM/JS).
 */
public final class RuntimeRng {

    private RuntimeRng() {}

    public static void emit(StringBuilder sb) {
        sb.append("""

            # ── kof.rng (X8 fatia 2) — xorshift128 + splitmix32, 32-bit ───
            # default (0,0,0,1): programa sem seed é determinístico
            # (xorshift128 não aceita estado todo-zero).
            .section .data
            .balign 4
            .globl kof_rng_state
            kof_rng_state:
                .long 0, 0, 0, 1
            .section .text

            # splitmix32(edi) -> eax (clobber ecx). IMUTÁVEL: mesmos
            # imediatos dos fragments JVM/JS; wrap 32-bit idêntico.
            .Lrng_sm32:
                movl %edi, %eax
                addl $0x9e3779b9, %eax
                movl %eax, %ecx
                shrl $16, %ecx
                xorl %ecx, %eax
                imull $0x21f0aaad, %eax
                movl %eax, %ecx
                shrl $15, %ecx
                xorl %ecx, %eax
                imull $0x735a2d97, %eax
                movl %eax, %ecx
                shrl $15, %ecx
                xorl %ecx, %eax
                ret

            # kof_rng_next() -> eax (u32 cru). Clobber eax/ecx/edx — SEMPRE
            # chamado com edi preservado (int/string dependem dele).
            .Lrng_next:
                movl kof_rng_state(%rip), %eax
                movl %eax, %ecx
                shll $11, %ecx
                xorl %ecx, %eax
                movl kof_rng_state+4(%rip), %edx
                movl %edx, kof_rng_state(%rip)
                movl kof_rng_state+8(%rip), %edx
                movl %edx, kof_rng_state+4(%rip)
                movl kof_rng_state+12(%rip), %edx
                movl %edx, kof_rng_state+8(%rip)
                movl %edx, %ecx
                shrl $19, %ecx
                xorl %ecx, %edx
                movl %eax, %ecx
                shrl $8, %ecx
                xorl %ecx, %eax
                xorl %eax, %edx
                movl %edx, kof_rng_state+12(%rip)
                movl %edx, %eax
                ret

            # kof_rng_seed(edi=seed) -> void
            .globl kof_rng_seed
            .type kof_rng_seed, @function
            kof_rng_seed:
                pushq %rbx
                call .Lrng_sm32
                movl %eax, %ebx
                movl %ebx, %edi
                call .Lrng_sm32
                movl %eax, kof_rng_state(%rip)
                leal 1(%rbx), %edi
                call .Lrng_sm32
                movl %eax, kof_rng_state+4(%rip)
                leal 2(%rbx), %edi
                call .Lrng_sm32
                movl %eax, kof_rng_state+8(%rip)
                leal 3(%rbx), %edi
                call .Lrng_sm32
                movl %eax, kof_rng_state+12(%rip)
                # todo-zero => s1 = 1 (guard dos 3 backends)
                movl kof_rng_state(%rip), %eax
                orl kof_rng_state+4(%rip), %eax
                orl kof_rng_state+8(%rip), %eax
                orl kof_rng_state+12(%rip), %eax
                jnz .Lrng_seed_done
                movl $1, kof_rng_state+4(%rip)
            .Lrng_seed_done:
                popq %rbx
                ret

            # kof_rng_int(edi=bound) -> [0,bound); bound<=0 => 0 (leniente).
            # divl = u32 % bound (movl do next zera rax alto; edx=0) — mesmos
            # bits do (next & 0xffffffffL) % bound do JVM.
            .globl kof_rng_int
            .type kof_rng_int, @function
            kof_rng_int:
                testl %edi, %edi
                jle .Lrng_int_zero
                call .Lrng_next
                movl $0, %edx
                divl %edi
                movl %edx, %eax
                ret
            .Lrng_int_zero:
                xorl %eax, %eax
                ret

            # kof_rng_bool() -> 0/1 em eax
            .globl kof_rng_bool
            .type kof_rng_bool, @function
            kof_rng_bool:
                call .Lrng_next
                andl $1, %eax
                ret

            # kof_rng_double() -> Double em xmm0 (convenção do generic
            # emitter: call + movq %xmm0,%rax + pushq). 52 bits
            # (hi 20 + lo 32) / 2^52; exato em IEEE duplo (soma <= 2^52-1)
            # — idêntico ao JVM/JS.
            .globl kof_rng_double
            .type kof_rng_double, @function
            kof_rng_double:
                pushq %rbx
                call .Lrng_next
                shrl $12, %eax
                movq %rax, %rbx
                call .Lrng_next
                cvtsi2sdq %rax, %xmm0
                cvtsi2sdq %rbx, %xmm1
                movsd .Lrng_two32(%rip), %xmm2
                mulsd %xmm2, %xmm1
                addsd %xmm1, %xmm0
                movsd .Lrng_two52(%rip), %xmm1
                divsd %xmm1, %xmm0
                popq %rbx
                ret

            # kof_rng_string(edi=n, rsi=alphabet) -> String (layout da S2;
            # cada char = alphabet[kof_rng_int(alen)] — ASCII, mesma ressalva
            # de kof_random_string). n<=0 / null / vazio -> "" (leniente).
            .globl kof_rng_string
            .type kof_rng_string, @function
            kof_rng_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl %edi, %ebx              # n
                movq %rsi, %r12              # alphabet
                testl %ebx, %ebx
                jle .Lrng_str_emp
                testq %r12, %r12
                jz .Lrng_str_emp
                movl 16(%r12), %r13d         # alen
                testl %r13d, %r13d
                jle .Lrng_str_emp
                leal 25(%rbx), %edi
                call kof_alloc
                movq %rax, %r14              # novo
                movl $1, (%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %ebx, 16(%r14)
                movl $0, 20(%r14)
                xorl %r15d, %r15d            # i
            .Lrng_str_loop:
                cmpl %ebx, %r15d
                jge .Lrng_str_term
                movl %r13d, %edi
                call kof_rng_int
                movzbl 24(%r12,%rax), %edx
                movb %dl, 24(%r14,%r15)
                incq %r15
                jmp .Lrng_str_loop
            .Lrng_str_term:
                movb $0, 24(%r14,%rbx)
                movq %r14, %rax
                jmp .Lrng_str_done
            .Lrng_str_emp:
                movl $40, %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, (%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl $0, 16(%r14)
                movl $0, 20(%r14)
                movb $0, 24(%r14)
                movq %r14, %rax
            .Lrng_str_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .rodata
            .balign 8
            .Lrng_two32:
                .quad 0x41F0000000000000        # 4294967296.0 = 2^32
            .Lrng_two52:
                .quad 0x4330000000000000        # 4503599627370496.0 = 2^52
            .section .text
            """);
    }
}
