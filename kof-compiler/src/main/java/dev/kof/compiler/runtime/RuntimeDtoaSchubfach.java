package dev.kof.compiler.runtime;

import java.math.BigInteger;

/**
 * B-1c (PLAN-BAREMETAL-BOOT §B-1c): constant tables of the JDK
 * {@code DoubleToDecimal} (Schubfach) algorithm, needed by the libc-free dtoa.
 *
 * <p>The JDK renders {@code Double} with Schubfach (Giulietti,
 * "The Schubfach way to render doubles"), whose output is NOT the
 * mathematically shortest ({@code Double.toString(0x1)} = {@code 4.9E-324}
 * although {@code 5e-324} round-trips). To match the oracle byte-for-byte the
 * runtime must reproduce the algorithm exactly, so the constants below are
 * GENERATED from the same closed forms the JDK {@code MathUtils} uses, not
 * transcribed by hand.
 *
 * <p>{@code g} (the {@code K_MIN..K_MAX} split table): for each {@code k},
 * {@code 10^-k = beta 2^r} with {@code 2^125 <= beta < 2^126}; {@code g =
 * floor(beta) + 1}, split into {@code g1 = g >> 63} and {@code g0 = g &
 * (2^63-1)}. Validated: the full 1234-value table hashes exactly to the JDK
 * {@code MathUtils.g} array (see {@code SchubfachTableGeneratorTest}).
 */
public final class RuntimeDtoaSchubfach {

    /** JDK {@code DoubleToDecimal.K_MIN}/{@code K_MAX} (and {@code MathUtils}). */
    public static final int K_MIN = -324;
    public static final int K_MAX = 292;

    /** C_2 = floor(log2(10) * 2^Q_2) (JDK {@code MathUtils}). */
    private static final int Q_2 = 38;
    private static final long C_2 = 913_124_641_741L;

    /** The first powers of 10, 10^0..10^17 (JDK {@code MathUtils.pow10}). */
    private static final long[] POW10 = {
        1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L,
        100_000_000L, 1_000_000_000L, 10_000_000_000L, 100_000_000_000L,
        1_000_000_000_000L, 10_000_000_000_000L, 100_000_000_000_000L,
        1_000_000_000_000_000L, 10_000_000_000_000_000L, 100_000_000_000_000_000L,
    };

    private RuntimeDtoaSchubfach() {}

    static int flog2pow10(int e) {
        return (int) ((e * C_2) >> Q_2);
    }

    /** {@code floor(10^-k / 2^(flog2pow10(-k)-125)) + 1}, as the JDK. */
    static long[] g1g0(int k) {
        int e = -k;
        int s = 125 - flog2pow10(e);
        BigInteger num;
        BigInteger den;
        if (e >= 0) {
            num = BigInteger.TEN.pow(e);
            den = BigInteger.ONE;
        } else {
            num = BigInteger.ONE;
            den = BigInteger.TEN.pow(-e);
        }
        if (s >= 0) {
            num = num.shiftLeft(s);
        } else {
            den = den.shiftLeft(-s);
        }
        BigInteger g = num.divide(den).add(BigInteger.ONE);
        long g1 = g.shiftRight(63).longValue();
        long g0 = g.and(BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE)).longValue();
        return new long[]{g1, g0};
    }

    /** The interleaved {@code g1,g0} table for {@code k} in {@code K_MIN..K_MAX}. */
    public static long[] gTable() {
        long[] t = new long[(K_MAX - K_MIN + 1) * 2];
        for (int k = K_MIN; k <= K_MAX; k++) {
            long[] p = g1g0(k);
            t[(k - K_MIN) * 2] = p[0];
            t[(k - K_MIN) * 2 + 1] = p[1];
        }
        return t;
    }

    /** Emits {@code .Lschub_g} and {@code .Lschub_pow10} into {@code .rodata}. */
    public static void emitTables(StringBuilder sb) {
        sb.append("            .section .rodata\n");
        sb.append("            .align 8\n");
        sb.append(".Lschub_g:\n");
        long[] g = gTable();
        for (int i = 0; i < g.length; i += 2) {
            sb.append("            .quad 0x").append(Long.toHexString(g[i]))
                    .append(", 0x").append(Long.toHexString(g[i + 1])).append('\n');
        }
        sb.append(".Lschub_pow10:\n");
        for (int i = 0; i < POW10.length; i++) {
            sb.append("            .quad ").append(POW10[i]).append('\n');
        }
    }

    /**
     * Emits the libc-free Schubfach dtoa: {@code kof_double_to_string(xmm0) ->
     * rax: String*}, reproducing the JDK {@code DoubleToDecimal} algorithm
     * (tables, {@code rop}, {@code toDecimal}, {@code toChars}) byte-for-byte.
     * Fixes §448 (Native {@code 5.0E-324} vs JVM {@code 4.9E-324}).
     */
    public static void emitCore(StringBuilder sb) {
        sb.append("            .section .rodata\n");
        sb.append(".Lschub_inf: .asciz \"Infinity\"\n");
        sb.append(".Lschub_ninf: .asciz \"-Infinity\"\n");
        sb.append(".Lschub_nan: .asciz \"NaN\"\n");
        sb.append(".Lschub_zero: .asciz \"0.0\"\n");
        sb.append(".Lschub_nzero: .asciz \"-0.0\"\n");
        emitTables(sb);
        sb.append(CORE);
    }

    private static final String CORE = """
.section .text

# ---- kof_schub_flog10pow2(edi) -> eax : floor(log10(2^e)) ----
.globl kof_schub_flog10pow2
kof_schub_flog10pow2:
    movslq %edi, %rax
    movabsq $0x9A209A84FB, %rcx
    imulq %rcx, %rax
    sarq $41, %rax
    ret

# ---- kof_schub_flog10threequarters(edi) -> eax ----
.globl kof_schub_flog10threequarters
kof_schub_flog10threequarters:
    movslq %edi, %rax
    movabsq $0x9A209A84FB, %rcx
    imulq %rcx, %rax
    movabsq $0x3FF7F85779, %rcx
    subq %rcx, %rax
    sarq $41, %rax
    ret

# ---- kof_schub_flog2pow10(edi) -> eax ----
.globl kof_schub_flog2pow10
kof_schub_flog2pow10:
    movslq %edi, %rax
    movabsq $0xD49A784BCD, %rcx
    imulq %rcx, %rax
    sarq $38, %rax
    ret

# ---- kof_schub_g1(edi=k) -> rax ----
.globl kof_schub_g1
kof_schub_g1:
    addl $324, %edi
    movslq %edi, %rcx
    shlq $1, %rcx
    leaq .Lschub_g(%rip), %rax
    movq (%rax,%rcx,8), %rax
    ret

# ---- kof_schub_g0(edi=k) -> rax ----
.globl kof_schub_g0
kof_schub_g0:
    addl $324, %edi
    movslq %edi, %rcx
    shlq $1, %rcx
    incq %rcx
    leaq .Lschub_g(%rip), %rax
    movq (%rax,%rcx,8), %rax
    ret

# ---- kof_schub_pow10(edi=e em 0..17) -> rax ----
.globl kof_schub_pow10
kof_schub_pow10:
    movslq %edi, %rdi
    leaq .Lschub_pow10(%rip), %rax
    movq (%rax,%rdi,8), %rax
    ret

# ---- kof_schub_rop(rdi=g1, rsi=g0, rdx=cp) -> rax ----
.globl kof_schub_rop
kof_schub_rop:
    movq %rdx, %rcx
    movq %rsi, %rax
    mulq %rcx
    movq %rdx, %r8
    movq %rdi, %rax
    mulq %rcx
    movq %rax, %r9
    movq %rdx, %r10
    shrq $1, %r9
    addq %r8, %r9
    movq %r9, %rax
    shrq $63, %rax
    addq %r10, %rax
    movq %r9, %rdx
    movabsq $0x7FFFFFFFFFFFFFFF, %rcx
    andq %rcx, %rdx
    addq %rcx, %rdx
    shrq $63, %rdx
    orq %rdx, %rax
    ret

# ---- kof_schub_to_decimal(edi=q, rsi=c, edx=dk) -> rax=f, ecx=e ----
.globl kof_schub_to_decimal
kof_schub_to_decimal:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $168, %rsp
    movl %edi, %ebx
    movq %rsi, %r12
    movl %edx, %r13d
    movl %r12d, %r14d
    andl $1, %r14d
    movabsq $0x10000000000000, %rax
    cmpq %rax, %r12
    jne .Ltd_reg
    cmpl $-1074, %ebx
    je .Ltd_reg
    movq %r12, %rax
    shlq $2, %rax
    movq %rax, -48(%rbp)
    leaq -1(%rax), %rax
    movq %rax, -64(%rbp)
    movl %ebx, %edi
    call kof_schub_flog10threequarters
    jmp .Ltd_kdone
.Ltd_reg:
    movq %r12, %rax
    shlq $2, %rax
    movq %rax, -48(%rbp)
    leaq -2(%rax), %rax
    movq %rax, -64(%rbp)
    movl %ebx, %edi
    call kof_schub_flog10pow2
.Ltd_kdone:
    movl %eax, -120(%rbp)
    movq -48(%rbp), %rax
    addq $2, %rax
    movq %rax, -56(%rbp)
    movl -120(%rbp), %edi
    negl %edi
    call kof_schub_flog2pow10
    addl %ebx, %eax
    addl $2, %eax
    movl %eax, -72(%rbp)
    movl -120(%rbp), %edi
    call kof_schub_g1
    movq %rax, -80(%rbp)
    movl -120(%rbp), %edi
    call kof_schub_g0
    movq %rax, -88(%rbp)
    movq -80(%rbp), %rdi
    movq -88(%rbp), %rsi
    movq -48(%rbp), %rdx
    movl -72(%rbp), %ecx
    shlq %cl, %rdx
    call kof_schub_rop
    movq %rax, -96(%rbp)
    movq -80(%rbp), %rdi
    movq -88(%rbp), %rsi
    movq -64(%rbp), %rdx
    movl -72(%rbp), %ecx
    shlq %cl, %rdx
    call kof_schub_rop
    movq %rax, -104(%rbp)
    movq -80(%rbp), %rdi
    movq -88(%rbp), %rsi
    movq -56(%rbp), %rdx
    movl -72(%rbp), %ecx
    shlq %cl, %rdx
    call kof_schub_rop
    movq %rax, -112(%rbp)
    movq -96(%rbp), %rax
    shrq $2, %rax
    movq %rax, -128(%rbp)
    cmpq $100, %rax
    jl .Ltd_cmp
    movq -128(%rbp), %rax
    movabsq $0x19999999999999A0, %rcx
    mulq %rcx
    imulq $10, %rdx, %rdx
    movq %rdx, -136(%rbp)
    leaq 10(%rdx), %rax
    movq %rax, -144(%rbp)
    movq -104(%rbp), %rax
    addq %r14, %rax
    movq -136(%rbp), %rdx
    shlq $2, %rdx
    cmpq %rdx, %rax
    setbe %r8b
    movq -144(%rbp), %rax
    shlq $2, %rax
    addq %r14, %rax
    movq -112(%rbp), %rdx
    cmpq %rdx, %rax
    setbe %r9b
    cmpb %r9b, %r8b
    je .Ltd_cmp
    testb %r8b, %r8b
    jz .Ltd_use_tp
    movq -136(%rbp), %rdi
    jmp .Ltd_retd_k
.Ltd_use_tp:
    movq -144(%rbp), %rdi
.Ltd_retd_k:
    movl -120(%rbp), %esi
    jmp .Ltd_ret
.Ltd_cmp:
    movq -128(%rbp), %rax
    leaq 1(%rax), %rax
    movq %rax, -144(%rbp)
    movq -104(%rbp), %rcx
    addq %r14, %rcx
    movq -128(%rbp), %rdx
    shlq $2, %rdx
    cmpq %rdx, %rcx
    setbe %r8b
    movq -144(%rbp), %rcx
    shlq $2, %rcx
    addq %r14, %rcx
    movq -112(%rbp), %rdx
    cmpq %rdx, %rcx
    setbe %r9b
    cmpb %r9b, %r8b
    je .Ltd_final
    testb %r8b, %r8b
    jz .Ltd_use_t
    movq -128(%rbp), %rdi
    jmp .Ltd_retd_kdk
.Ltd_use_t:
    movq -144(%rbp), %rdi
.Ltd_retd_kdk:
    movl -120(%rbp), %esi
    addl %r13d, %esi
    jmp .Ltd_ret
.Ltd_final:
    movq -128(%rbp), %rax
    movq -144(%rbp), %rcx
    addq %rcx, %rax
    shlq $1, %rax
    movq -96(%rbp), %rdx
    subq %rax, %rdx
    js .Ltd_pick_s
    jnz .Ltd_pick_t
    testb $1, -128(%rbp)
    jz .Ltd_pick_s
.Ltd_pick_t:
    movq -144(%rbp), %rdi
    jmp .Ltd_retd_kdk2
.Ltd_pick_s:
    movq -128(%rbp), %rdi
.Ltd_retd_kdk2:
    movl -120(%rbp), %esi
    addl %r13d, %esi
.Ltd_ret:
    movq %rdi, %rax
    movl %esi, %ecx
    leaq -40(%rbp), %rsp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    popq %rbp
    ret

# ---- kof_schub_format(rdi=f>0, esi=e, edx=sign) -> rax: String* ----
.globl kof_schub_format
kof_schub_format:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $104, %rsp
    movq %rdi, %rbx
    movl %esi, %r12d
    movl %edx, %r13d
    bsrq %rbx, %rax
    movl $63, %edi
    subl %eax, %edi
    movl $64, %eax
    subl %edi, %eax
    movl %eax, %edi
    call kof_schub_flog10pow2
    movl %eax, %esi
    movl %esi, %edi
    call kof_schub_pow10
    cmpq %rax, %rbx
    jb .Lschubf_len_ok
    incl %esi
.Lschubf_len_ok:
    movl $17, %edi
    subl %esi, %edi
    call kof_schub_pow10
    imulq %rax, %rbx
    addl %esi, %r12d
    leaq -48(%rbp), %r10
    movq %rbx, %rax
    movl $17, %ecx
    movl $10, %r8d
.Lschubf_dig:
    xorl %edx, %edx
    divq %r8
    addb $48, %dl
    decq %r10
    movb %dl, (%r10)
    decl %ecx
    jnz .Lschubf_dig
    leaq -112(%rbp), %r14
    xorl %r15d, %r15d
    testl %r13d, %r13d
    jz .Lschubf_nosign
    movb $45, (%r14)
    incq %r15
.Lschubf_nosign:
    cmpl $0, %r12d
    jle .Lschubf_le0
    cmpl $7, %r12d
    jg .Lschubf_sci
    movl %r12d, %ecx
.Lschubf_p1:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_p1
    movb $46, (%r14,%r15)
    incq %r15
    movl $17, %ecx
    subl %r12d, %ecx
.Lschubf_p2:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_p2
    jmp .Lschubf_strip
.Lschubf_le0:
    cmpl $-3, %r12d
    jle .Lschubf_sci
    movb $48, (%r14,%r15)
    incq %r15
    movb $46, (%r14,%r15)
    incq %r15
    movl %r12d, %ecx
    negl %ecx
    testl %ecx, %ecx
    jz .Lschubf_zskip
.Lschubf_z0:
    movb $48, (%r14,%r15)
    incq %r15
    decl %ecx
    jnz .Lschubf_z0
.Lschubf_zskip:
    movl $17, %ecx
.Lschubf_z1:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_z1
    jmp .Lschubf_strip
.Lschubf_sci:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    movb $46, (%r14,%r15)
    incq %r15
    movl $16, %ecx
.Lschubf_s1:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_s1
.Lschubf_strip:
    testq %r15, %r15
    jz .Lschubf_strip_done
    movzbl -1(%r14,%r15), %eax
    cmpb $48, %al
    jne .Lschubf_strip_done
    cmpq $1, %r15
    jle .Lschubf_strip_dec
    movzbl -2(%r14,%r15), %eax
    cmpb $46, %al
    je .Lschubf_strip_done
.Lschubf_strip_dec:
    decq %r15
    jmp .Lschubf_strip
.Lschubf_strip_done:
    cmpl $7, %r12d
    jg .Lschubf_exp
    cmpl $0, %r12d
    jg .Lschubf_emit
    cmpl $-3, %r12d
    jg .Lschubf_emit
.Lschubf_exp:
    movb $69, (%r14,%r15)
    incq %r15
    movl %r12d, %eax
    decl %eax
    testl %eax, %eax
    jns .Lschubf_exp_p
    movb $45, (%r14,%r15)
    incq %r15
    negl %eax
.Lschubf_exp_p:
    leaq -128(%rbp), %rdi
    xorl %r9d, %r9d
    movl $10, %esi
    testl %eax, %eax
    jnz .Lschubf_ediv
    movb $48, (%rdi)
    movl $1, %r9d
    jmp .Lschubf_erev
.Lschubf_ediv:
    xorl %edx, %edx
    divl %esi
    addb $48, %dl
    movb %dl, (%rdi,%r9)
    incq %r9
    testl %eax, %eax
    jnz .Lschubf_ediv
.Lschubf_erev:
    decq %r9
.Lschubf_erev1:
    movzbl (%rdi,%r9), %eax
    movb %al, (%r14,%r15)
    incq %r15
    decq %r9
    jns .Lschubf_erev1
.Lschubf_emit:
    movq %r14, %rdi
    movl %r15d, %esi
    call kof_string_from_literal
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
    subq $56, %rsp
    movq %xmm0, %rax
    movq %rax, %rbx
    movq %rbx, %r13
    shrq $63, %r13
    movq %rbx, %rcx
    shrq $52, %rcx
    andl $0x7ff, %ecx
    cmpl $0x7ff, %ecx
    je .Ld2s_naninf
    testl %ecx, %ecx
    jz .Ld2s_sub
    movl $1075, %r12d
    subl %ecx, %r12d
    movq %rbx, %r14
    movabsq $0xFFFFFFFFFFFFF, %rax
    andq %rax, %r14
    movabsq $0x10000000000000, %rax
    orq %rax, %r14
    testl %r12d, %r12d
    jle .Ld2s_dec
    cmpl $53, %r12d
    jge .Ld2s_dec
    movl %r12d, %ecx
    movq %r14, %rax
    shrq %cl, %rax
    movq %rax, %rdx
    shlq %cl, %rdx
    cmpq %r14, %rdx
    jne .Ld2s_dec
    movq %rax, %rdi
    xorl %esi, %esi
    movl %r13d, %edx
    call kof_schub_format
    jmp .Ld2s_done
.Ld2s_dec:
    movl %r12d, %edi
    negl %edi
    movq %r14, %rsi
    xorl %edx, %edx
    call kof_schub_to_decimal
    movq %rax, %rdi
    movl %ecx, %esi
    movl %r13d, %edx
    call kof_schub_format
    jmp .Ld2s_done
.Ld2s_sub:
    movq %rbx, %rax
    movabsq $0xFFFFFFFFFFFFF, %rcx
    andq %rcx, %rax
    testq %rax, %rax
    jz .Ld2s_zero
    cmpq $3, %rax
    jae .Ld2s_sub_c
    imulq $10, %rax, %rsi
    movl $-1074, %edi
    movl $-1, %edx
    call kof_schub_to_decimal
    jmp .Ld2s_after
.Ld2s_sub_c:
    movq %rax, %rsi
    movl $-1074, %edi
    xorl %edx, %edx
    call kof_schub_to_decimal
.Ld2s_after:
    movq %rax, %rdi
    movl %ecx, %esi
    movl %r13d, %edx
    call kof_schub_format
    jmp .Ld2s_done
.Ld2s_zero:
    testl %r13d, %r13d
    jz .Ld2s_zero_p
    leaq .Lschub_nzero(%rip), %rdi
    movl $4, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_zero_p:
    leaq .Lschub_zero(%rip), %rdi
    movl $3, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_naninf:
    movq %rbx, %rax
    shlq $12, %rax
    jnz .Ld2s_nan
    testq %rbx, %rbx
    js .Ld2s_ninf
    leaq .Lschub_inf(%rip), %rdi
    movl $8, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_ninf:
    leaq .Lschub_ninf(%rip), %rdi
    movl $9, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_nan:
    leaq .Lschub_nan(%rip), %rdi
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

""";
}
