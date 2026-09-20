.file 1 "Main.kf"
.section .data
.globl kof_heap_root_start
kof_heap_root_start:
.quad 0
.Lstr_0: .asciz "hello"
.Lnewline: .asciz "\n"
.Lkof_str_true: .asciz "true"
.Lkof_str_false: .asciz "false"
.balign 8
kof_super_table:
    .long 0, 0
kof_static_java_lang_System_out: .quad 0
.balign 8
.globl Default_Main_vtable
.type Default_Main_vtable, @object
Default_Main_vtable:
    .quad Default_Main_process_O
    .quad Default_Main_main
    .quad 0

.section .text
            .section .data
            .quad 0
            .section .text
.globl kof_print
.type kof_print, @function
kof_print:
    pushq %rbx
    movq %rdi, %rbx
    xorq %rdx, %rdx
.Lkof_print_len:
    cmpb $0, (%rbx,%rdx)
    je .Lkof_print_do
    incq %rdx
    jmp .Lkof_print_len
.Lkof_print_do:
    movq $1, %rax
    movq $1, %rdi
    movq %rbx, %rsi
    syscall
    popq %rbx
    ret
.globl kof_println
.type kof_println, @function
kof_println:
    # §284: dispatch de box de erasure — MAGIC em [0] e o valor e
    # um BOX [magic][tag][value] (RuntimeErasureBox); o print segue
    # o golden JVM por tag. Sem MAGIC (ponteiro de objeto real ou
    # string) o caminho antigo roda INALTERADO (zero regressao).
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkof_println_gen
    pushq %rbx
    movq %rdi, %rbx
    movq 8(%rbx), %rax
    cmpl $0, %eax
    je .Lkp_box_int
    cmpl $2, %eax
    je .Lkp_box_long              # long: kof_int_to_string trunca em 32-bit
    cmpl $3, %eax
    je .Lkp_box_bool
    cmpl $4, %eax
    je .Lkp_box_dbl
    cmpl $5, %eax
    je .Lkp_box_flt
    jmp .Lkp_box_gen              # tag desconhecido -> caminho antigo
.Lkp_box_int:
    movq 16(%rbx), %rdi
    call kof_int_to_string
    movq %rax, %rdi
    call kof_println_string
    jmp .Lkp_box_end
.Lkp_box_long:
    movq 16(%rbx), %rdi
    call kof_long_to_string
    movq %rax, %rdi
    call kof_println_string
    jmp .Lkp_box_end
.Lkp_box_bool:
    movq 16(%rbx), %rdi
    call kof_bool_to_string
    movq %rax, %rdi
    call kof_println_string
    jmp .Lkp_box_end
.Lkp_box_dbl:
    movq 16(%rbx), %rdi
    movq %rdi, %xmm0
    call kof_print_double
    jmp .Lkp_box_nl
.Lkp_box_flt:
    movq 16(%rbx), %rdi
    movd %edi, %xmm0
    cvtss2sd %xmm0, %xmm0
    call kof_print_double
    jmp .Lkp_box_nl
.Lkp_box_gen:
    movq %rbx, %rdi
    call kof_print
    jmp .Lkp_box_nl
.Lkp_box_nl:
    leaq .Lnewline(%rip), %rdi
    call kof_print
.Lkp_box_end:
    popq %rbx
    ret
.Lkof_println_gen:
    call kof_print
    pushq %rbx
    leaq .Lnewline(%rip), %rdi
    call kof_print
    popq %rbx
    ret
# §284: box de erasure — alocar 24B [magic][tag][value]
.text
.globl kof_box_int
.type kof_box_int, @function
kof_box_int:
    pushq %rbx
    movq %rdi, %rbx
    movl $24, %edi
    call kof_alloc
    movabsq $0x4B4F46425F425801, %rcx
    movq %rcx, (%rax)
    movl $0, 8(%rax)
    movq %rbx, 16(%rax)
    popq %rbx
    ret
.globl kof_box_long
.type kof_box_long, @function
kof_box_long:
    pushq %rbx
    movq %rdi, %rbx
    movl $24, %edi
    call kof_alloc
    movabsq $0x4B4F46425F425801, %rcx
    movq %rcx, (%rax)
    movl $2, 8(%rax)
    movq %rbx, 16(%rax)
    popq %rbx
    ret
.globl kof_box_bool
.type kof_box_bool, @function
kof_box_bool:
    pushq %rbx
    movq %rdi, %rbx
    movl $24, %edi
    call kof_alloc
    movabsq $0x4B4F46425F425801, %rcx
    movq %rcx, (%rax)
    movl $3, 8(%rax)
    movq %rbx, 16(%rax)
    popq %rbx
    ret
.globl kof_box_double
.type kof_box_double, @function
kof_box_double:
    pushq %rbx
    movq %rdi, %rbx
    movl $24, %edi
    call kof_alloc
    movabsq $0x4B4F46425F425801, %rcx
    movq %rcx, (%rax)
    movl $4, 8(%rax)
    movq %rbx, 16(%rax)
    popq %rbx
    ret
.globl kof_box_float
.type kof_box_float, @function
# valor: bits IEEE-754 (32) — metade alta 0 por construção do
# lowering (`movd`/`movl` zero-extend)
kof_box_float:
    pushq %rbx
    movl %edi, %ebx
    movl $24, %edi
    call kof_alloc
    movabsq $0x4B4F46425F425801, %rcx
    movq %rcx, (%rax)
    movl $5, 8(%rax)
    movq %rbx, 16(%rax)
    popq %rbx
    ret
.globl kof_unbox_int
.type kof_unbox_int, @function
# le um box de inteiro (tag 0 = Int/Char/Short/Byte — mesma largura
# 64-bit dos slots int nativos). Regra JVM (medida 18/09): `o as Int`
# sobre Long/Double/Boolean/String = ClassCastException — NUNCA
# valor inventado nem passthrough cru. Qualquer outra forma (tag
# != 0, sem MAGIC = referencia real) e diagnostico honesto (R6).
kof_unbox_int:
    testq %rdi, %rdi                  # null -> diagnostico CCE
    jz .Lkui_bad                      # (JVM: NPE/CCE; nunca *(0))
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkui_bad
    movq %rdi, %rcx
    movq 8(%rdi), %rax
    testq %rax, %rax
    jnz .Lkui_bad
    movq 16(%rcx), %rax
    ret
.Lkui_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
# §284-map (18/09): kof_unbox_long — paridade Number.longValue():
# aceita caixa Int (tag 0, o valor e um qword com signo) e caixa
# Long (tag 2); qualquer outra forma e o mesmo diagnostico (R6).
.globl kof_unbox_long
.type kof_unbox_long, @function
kof_unbox_long:
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkul_bad
    movq %rdi, %rcx
    movq 8(%rdi), %rax
    cmpq $2, %rax
    je .Lkul_ok
    testq %rax, %rax
    jnz .Lkul_bad
.Lkul_ok:
    movq 16(%rcx), %rax
    ret
.Lkul_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
# §284-map (18/09): unbox SOFT para consumidores de `Int?`
# (arithmetic/relacional/println/pos-call do get). No native o
# valor pode chegar de 3 formas: caixa do slot (ler +16), CRU
# de variavel local ja-desembalada (JSR-45-esque — passar cru)
# ou null de get ausente (diagnostico igual ao estrito). O
# estrito NAO passa cru por contrato (`as Int` de String-box e
# CCE); quem precisa da flexibilidade e o consumidor nullable.
.globl kof_unbox_int_soft
.type kof_unbox_int_soft, @function
kof_unbox_int_soft:
    testq %rdi, %rdi
    jz .Lkuis_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkuis_raw
    cmpq $0, 8(%rdi)
    jne .Lkuis_bad
    movq 16(%rdi), %rax
    ret
.Lkuis_raw:
    movq %rdi, %rax
    ret
.Lkuis_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
.globl kof_unbox_long_soft
.type kof_unbox_long_soft, @function
kof_unbox_long_soft:
    testq %rdi, %rdi
    jz .Lkuls_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkuls_raw
    movq 8(%rdi), %rax
    cmpq $2, %rax
    je .Lkuls_ok
    testq %rax, %rax
    jnz .Lkuls_bad
.Lkuls_ok:
    movq 16(%rdi), %rax
    ret
.Lkuls_raw:
    movq %rdi, %rax
    ret
.Lkuls_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
.globl kof_unbox_bool
.type kof_unbox_bool, @function
kof_unbox_bool:
    testq %rdi, %rdi
    jz .Lkub_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkub_bad
    cmpq $3, 8(%rdi)
    jne .Lkub_bad
    movq 16(%rdi), %rax
    ret
.Lkub_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
.globl kof_unbox_bool_soft
.type kof_unbox_bool_soft, @function
kof_unbox_bool_soft:
    testq %rdi, %rdi
    jz .Lkubs_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkubs_raw
    cmpq $3, 8(%rdi)
    jne .Lkubs_bad
    movq 16(%rdi), %rax
    ret
.Lkubs_raw:
    movq %rdi, %rax
    ret
.Lkubs_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
.globl kof_unbox_double
.type kof_unbox_double, @function
kof_unbox_double:
    testq %rdi, %rdi
    jz .Lkud_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkud_bad
    cmpq $4, 8(%rdi)
    jne .Lkud_bad
    movq 16(%rdi), %rax
    ret
.Lkud_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
.globl kof_unbox_double_soft
.type kof_unbox_double_soft, @function
kof_unbox_double_soft:
    testq %rdi, %rdi
    jz .Lkuds_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkuds_raw
    cmpq $4, 8(%rdi)
    jne .Lkuds_bad
    movq 16(%rdi), %rax
    ret
.Lkuds_raw:
    movq %rdi, %rax
    ret
.Lkuds_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
.globl kof_unbox_float
.type kof_unbox_float, @function
kof_unbox_float:
    testq %rdi, %rdi
    jz .Lkuf_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkuf_bad
    cmpq $5, 8(%rdi)
    jne .Lkuf_bad
    movq 16(%rdi), %rax
    ret
.Lkuf_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
.globl kof_unbox_float_soft
.type kof_unbox_float_soft, @function
kof_unbox_float_soft:
    testq %rdi, %rdi
    jz .Lkufs_bad
    movabsq $0x4B4F46425F425801, %rax
    cmpq %rax, (%rdi)
    jne .Lkufs_raw
    cmpq $5, 8(%rdi)
    jne .Lkufs_bad
    movq 16(%rdi), %rax
    ret
.Lkufs_raw:
    movq %rdi, %rax
    ret
.Lkufs_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
# §284-map (18/09): kof_box_equals(rdi=L, rsi=R) -> rax 0/1.
# Igualdade de `T?` no native com slot FISICAMENTE boxed.
# Caixa vs caixa = VALOR; null vs null = 1; null vs presente = 0.
.globl kof_box_equals
.type kof_box_equals, @function
kof_box_equals:
    testq %rdi, %rdi
    jz .Lke_lnull
    testq %rsi, %rsi
    jz .Lke_false                   # L!=null, R=null -> 0 (false)
    jmp .Lke_go
.Lke_lnull:
    testq %rsi, %rsi
    jz .Lke_tnull                   # null == null -> 1 (true)
    xorl %eax, %eax                 # L=null, R!=null -> 0 (false)
    ret
.Lke_tnull:
    movl $1, %eax
    ret
.Lke_false:
    xorl %eax, %eax
    ret
.Lke_go:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    movabsq $0x4B4F46425F425801, %rbx
    # lado L: r12=tag (-1 = nao-caixa), r13=valor|ponteiro
    movq (%rdi), %rax
    cmpq %rbx, %rax
    jne .Lke_lraw
    movq 8(%rdi), %r12
    movq 16(%rdi), %r13
    jmp .Lke_ldone
.Lke_lraw:
    movq $-1, %r12
    movq %rdi, %r13
.Lke_ldone:
    movq (%rsi), %rax
    cmpq %rbx, %rax
    jne .Lke_rraw
    movq 8(%rsi), %r14
    movq 16(%rsi), %rsi
    jmp .Lke_rdone
.Lke_rraw:
    movq $-1, %r14
.Lke_rdone:
    xorl %eax, %eax
    cmpq %r14, %r12
    jne .Lke_end
    cmpq %rsi, %r13
    jne .Lke_end
    movl $1, %eax
.Lke_end:
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret
.Lke_bad:
    leaq .Lkui_msgint(%rip), %rdi
    call kof_throw_string
# §284: kof_box_to_string (rdi = box|[0]=magic -> rax = ptr string)
# O despacho do valueOf(Object) nativo cai aqui quando o static
# type nao tem vtable (Object/Nullable(Object)): sem isto o box
# cru caia em println_string (SIGSEGV). Nao-box passa cru (mesmo
# ponteiro) — o que ja era string/objeto nao muda de mao.
.globl kof_box_to_string
.type kof_box_to_string, @function
kof_box_to_string:
    testq %rdi, %rdi
    jz .Lkbs_null                   # null -> "null" (paridade JVM
    movabsq $0x4B4F46425F425801, %rax        #  String.valueOf(null)); a
    cmpq %rax, (%rdi)               #  sonda leu *(0) sem isto
    jne .Lkbs_pass
    pushq %rbx
    movq %rdi, %rbx
    movq 8(%rbx), %rax
    cmpl $0, %eax
    je .Lkbs_int
    cmpl $2, %eax
    je .Lkbs_long              # long: kof_int_to_string trunca em 32-bit
    cmpl $3, %eax
    je .Lkbs_bool
    cmpl $4, %eax
    je .Lkbs_dbl
    cmpl $5, %eax
    je .Lkbs_flt
    jmp .Lkbs_pass_end
.Lkbs_int:
    movq 16(%rbx), %rdi
    call kof_int_to_string
    jmp .Lkbs_end
.Lkbs_long:
    movq 16(%rbx), %rdi
    call kof_long_to_string
    jmp .Lkbs_end
.Lkbs_bool:
    movq 16(%rbx), %rdi
    call kof_bool_to_string
    jmp .Lkbs_end
.Lkbs_dbl:
    movq 16(%rbx), %rdi
    movq %rdi, %xmm0
    call kof_double_to_string
    jmp .Lkbs_end
.Lkbs_flt:
    movq 16(%rbx), %rdi
    movd %edi, %xmm0
    call kof_float_to_string
.Lkbs_end:
    popq %rbx
    ret
.Lkbs_pass_end:
    popq %rbx
.Lkbs_pass:
    movq %rdi, %rax
    ret
.Lkbs_null:
    leaq .Lkbs_nullstr(%rip), %rax
    ret
.section .rodata
.Lkbs_nullstr:
    .int 1
    .int 0
    .int 0
    .int 0
    .int 4
    .int 0
    .ascii "null"
.Lkui_msgint:
    .int 1
    .int 0
    .int 0
    .int 0
    .int 82
    .int 0
    .ascii "type error: class java.lang.Integer cannot be cast to the boxed value (Kof native)"
    .byte 0
.text
.section .text
.globl kof_print_float
.type kof_print_float, @function
kof_print_float:
    pushq %rbp
    movq %rsp, %rbp
    call kof_float_to_string
    movq %rax, %rdi
    call kof_print_string
    popq %rbp
    ret
.globl kof_print_double
.type kof_print_double, @function
kof_print_double:
    pushq %rbp
    movq %rsp, %rbp
    call kof_double_to_string
    movq %rax, %rdi
    call kof_print_string
    popq %rbp
    ret
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
.globl kof_int_to_string
.type kof_int_to_string, @function
kof_int_to_string:
    pushq %rbx
    pushq %r12
    pushq %r13
    movl %edi, %eax
    movq $0, %r12
    testl %eax, %eax
    jns .Lkof_int_to_str_pos
    movq $1, %r12
    negl %eax
.Lkof_int_to_str_pos:
    movl %eax, %r13d
    movq $0, %rbx
    movl $10, %ecx
.Lkof_int_to_str_count:
    xorl %edx, %edx
    divl %ecx
    incq %rbx
    testl %eax, %eax
    jnz .Lkof_int_to_str_count
    testq %r12, %r12
    jz .Lkof_int_to_str_count_done
    incq %rbx
.Lkof_int_to_str_count_done:
    leaq 25(%rbx), %rdi
    call kof_alloc
    pushq %rax
    leaq 23(%rax), %rsi
    addq %rbx, %rsi
    movl %r13d, %eax
    movl $10, %ecx
.Lkof_int_to_str_loop:
    xorl %edx, %edx
    divl %ecx
    addb $48, %dl
    movb %dl, (%rsi)
    decq %rsi
    testl %eax, %eax
    jnz .Lkof_int_to_str_loop
    testq %r12, %r12
    jz .Lkof_int_to_str_negdone
    movb $45, (%rsi)
.Lkof_int_to_str_negdone:
    testq %r12, %r12
    jnz .Lkof_int_to_str_ready
    incq %rsi
.Lkof_int_to_str_ready:
    popq %r13
    movl $1, 0(%r13)
    movl $0, 4(%r13)
    movq $0, 8(%r13)
    movl %ebx, 16(%r13)
    movl $0, 20(%r13)
    movq %r13, %rax
    popq %r13
    popq %r12
    popq %rbx
    ret
.globl kof_long_to_string
.type kof_long_to_string, @function
kof_long_to_string:
    pushq %rbx
    pushq %r12
    pushq %r13
    movq %rdi, %rax
    movq $0, %r12
    testq %rax, %rax
    jns .Lkof_long_to_str_pos
    movq $1, %r12
    negq %rax
.Lkof_long_to_str_pos:
    movq %rax, %r13
    movq $0, %rbx
    movq $10, %rcx
.Lkof_long_to_str_count:
    xorq %rdx, %rdx
    divq %rcx
    incq %rbx
    testq %rax, %rax
    jnz .Lkof_long_to_str_count
    testq %r12, %r12
    jz .Lkof_long_to_str_count_done
    incq %rbx
.Lkof_long_to_str_count_done:
    leaq 25(%rbx), %rdi
    call kof_alloc
    pushq %rax
    leaq 23(%rax), %rsi
    addq %rbx, %rsi
    movq %r13, %rax
    movq $10, %rcx
.Lkof_long_to_str_loop:
    xorq %rdx, %rdx
    divq %rcx
    addb $48, %dl
    movb %dl, (%rsi)
    decq %rsi
    testq %rax, %rax
    jnz .Lkof_long_to_str_loop
    testq %r12, %r12
    jz .Lkof_long_to_str_negdone
    movb $45, (%rsi)
.Lkof_long_to_str_negdone:
    testq %r12, %r12
    jnz .Lkof_long_to_str_ready
    incq %rsi
.Lkof_long_to_str_ready:
    popq %r13
    movl $1, 0(%r13)
    movl $0, 4(%r13)
    movq $0, 8(%r13)
    movl %ebx, 16(%r13)
    movl $0, 20(%r13)
    movq %r13, %rax
    popq %r13
    popq %r12
    popq %rbx
    ret
.globl kof_bool_to_string
.type kof_bool_to_string, @function
kof_bool_to_string:
    testl %edi, %edi
    jz .Lkof_bool_to_str_false
    leaq .Lkof_str_true(%rip), %rdi
    movl $4, %esi
    jmp .Lkof_bool_to_str_make
.Lkof_bool_to_str_false:
    leaq .Lkof_str_false(%rip), %rdi
    movl $5, %esi
.Lkof_bool_to_str_make:
    jmp kof_string_from_literal
.globl kof_list_new
.type kof_list_new, @function
kof_list_new:
    pushq %rbx
    movq $64, %rdi
    call kof_alloc
    movq %rax, %rbx
    movl $100, 0(%rbx)
    movl $0, 4(%rbx)
    movq $0, 8(%rbx)
    movl $0, 16(%rbx)
    movl $2, 20(%rbx)
    movq $16, %rdi
    call kof_alloc
    movq %rax, 24(%rbx)
    movq %rbx, %rax
    popq %rbx
    ret

.globl kof_list_grow
.type kof_list_grow, @function
kof_list_grow:
    pushq %rbx
    pushq %r12
    pushq %r13
    movq %rdi, %rbx
    movl 20(%rbx), %r12d
    movl %r12d, %r13d
    shll $1, %r13d
    movl %r13d, 20(%rbx)
    movslq %r13d, %rdi
    shlq $3, %rdi
    addq $24, %rdi
    call kof_alloc
    movq %rax, %rcx
    movq 24(%rbx), %rsi
    movl 16(%rbx), %r13d
    movslq %r13d, %r13
    xorq %rdx, %rdx
.Lkof_list_grow_copy:
    cmpq %r13, %rdx
    jge .Lkof_list_grow_done
    movq (%rsi,%rdx,8), %rax
    movq %rax, (%rcx,%rdx,8)
    incq %rdx
    jmp .Lkof_list_grow_copy
.Lkof_list_grow_done:
    movq %rcx, 24(%rbx)
    movq %rbx, %rax
    popq %r13
    popq %r12
    popq %rbx
    ret

.globl kof_list_add
.type kof_list_add, @function
kof_list_add:
    pushq %rbx
    pushq %r12
    movq %rdi, %rbx
    movq %rsi, %r12
    movl 16(%rbx), %eax
    cmpl 20(%rbx), %eax
    jl .Lkof_list_add_ok
    movq %rbx, %rdi
    call kof_list_grow
.Lkof_list_add_ok:
    movl 16(%rbx), %eax
    movslq %eax, %rcx
    movq 24(%rbx), %rdx
    movq %r12, (%rdx,%rcx,8)
    addl $1, 16(%rbx)
    popq %r12
    popq %rbx
    ret

.globl kof_list_get
.type kof_list_get, @function
kof_list_get:
    pushq %rbx
    movq %rdi, %rbx
    movl 16(%rbx), %eax
    cmpl %eax, %esi
    jge .Lkof_list_get_bounds
    testl %esi, %esi
    jl .Lkof_list_get_bounds
    movslq %esi, %rcx
    movq 24(%rbx), %rax
    movq (%rax,%rcx,8), %rax
    popq %rbx
    ret
.Lkof_list_get_bounds:
    movl %esi, %edi
    movl 16(%rbx), %esi
    call kof_bounds_error

.globl kof_list_set
.type kof_list_set, @function
kof_list_set:
    pushq %rbx
    movq %rdi, %rbx
    movl 16(%rbx), %eax
    cmpl %eax, %esi
    jge .Lkof_list_set_bounds
    testl %esi, %esi
    jl .Lkof_list_set_bounds
    movslq %esi, %rcx
    movq 24(%rbx), %rax
    movq %rdx, (%rax,%rcx,8)
    popq %rbx
    ret
.Lkof_list_set_bounds:
    movl %esi, %edi
    movl 16(%rbx), %esi
    call kof_bounds_error

.globl kof_list_size
.type kof_list_size, @function
kof_list_size:
    movslq 16(%rdi), %rax
    ret

.globl kof_list_contains
.type kof_list_contains, @function
kof_list_contains:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    movq %rdi, %rbx
    movq %rsi, %r12
    movl %edx, %r13d
    movl 16(%rbx), %r14d
    xorl %r15d, %r15d
.Lkof_list_contains_loop:
    cmpl %r14d, %r15d
    jge .Lkof_list_contains_no
    movq 24(%rbx), %rax
    movq (%rax,%r15,8), %rax
    cmpl $1, %r13d
    je .Lkof_list_contains_str
    cmpq %r12, %rax
    je .Lkof_list_contains_yes
    jmp .Lkof_list_contains_next
.Lkof_list_contains_str:
    movq %rax, %rdi
    movq %r12, %rsi
    call kof_string_equals
    testl %eax, %eax
    jnz .Lkof_list_contains_yes
.Lkof_list_contains_next:
    incl %r15d
    jmp .Lkof_list_contains_loop
.Lkof_list_contains_yes:
    movl $1, %eax
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret
.Lkof_list_contains_no:
    xorl %eax, %eax
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret

.globl kof_list_contains_tag
.type kof_list_contains_tag, @function
kof_list_contains_tag:
    jmp kof_list_contains

.globl kof_list_is_empty
.type kof_list_is_empty, @function
kof_list_is_empty:
    cmpl $0, 16(%rdi)
    sete %al
    movzbl %al, %eax
    ret

.globl kof_list_remove
.type kof_list_remove, @function
kof_list_remove:
    pushq %rbx
    pushq %r12
    pushq %r13
    movq %rdi, %rbx
    movl 16(%rbx), %eax
    cmpl %eax, %esi
    jge .Lkof_list_remove_bounds
    testl %esi, %esi
    jl .Lkof_list_remove_bounds
    movslq %esi, %rcx
    movq 24(%rbx), %rax
    movq (%rax,%rcx,8), %r12
.Lkof_list_remove_shift:
    movl 16(%rbx), %eax
    decl %eax
    cmpl %eax, %ecx
    jge .Lkof_list_remove_done
    movq 24(%rbx), %rax
    movq 8(%rax,%rcx,8), %rdx
    movq 24(%rbx), %rax
    movq %rdx, (%rax,%rcx,8)
    incq %rcx
    jmp .Lkof_list_remove_shift
.Lkof_list_remove_done:
    movl 16(%rbx), %eax
    decl %eax
    movl %eax, 16(%rbx)
    movq %r12, %rax
    popq %r13
    popq %r12
    popq %rbx
    ret
.Lkof_list_remove_bounds:
    movl %esi, %edi
    movl 16(%rbx), %esi
    call kof_bounds_error

.globl kof_list_clear
.type kof_list_clear, @function
kof_list_clear:
    movl $0, 16(%rdi)
    ret

.globl kof_list_map
.type kof_list_map, @function
kof_list_map:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    movq %rdi, %r12
    movq %rsi, %r13
    call kof_list_new
    movq %rax, %r14
    xorl %r15d, %r15d
.Lkof_list_map_loop:
    movl 16(%r12), %eax
    cmpl %eax, %r15d
    jge .Lkof_list_map_done
    movq 24(%r12), %rax
    movslq %r15d, %rcx
    movq (%rax,%rcx,8), %rsi
    movq %r13, %rdi
    movq 8(%rdi), %rax
    movq (%rax), %rax
    call *%rax
    movq %rax, %rsi
    movq %r14, %rdi
    call kof_list_add
    incl %r15d
    jmp .Lkof_list_map_loop
.Lkof_list_map_done:
    movq %r14, %rax
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret

.globl kof_list_filter
.type kof_list_filter, @function
kof_list_filter:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    movq %rdi, %r12
    movq %rsi, %r13
    call kof_list_new
    movq %rax, %r14
    xorl %r15d, %r15d
.Lkof_list_filter_loop:
    movl 16(%r12), %eax
    cmpl %eax, %r15d
    jge .Lkof_list_filter_done
    movq 24(%r12), %rax
    movslq %r15d, %rcx
    movq (%rax,%rcx,8), %rsi
    movq %r13, %rdi
    movq 8(%rdi), %rax
    movq (%rax), %rax
    call *%rax
    testq %rax, %rax
    jz .Lkof_list_filter_skip
    movq 24(%r12), %rax
    movslq %r15d, %rcx
    movq (%rax,%rcx,8), %rsi
    movq %r14, %rdi
    call kof_list_add
.Lkof_list_filter_skip:
    incl %r15d
    jmp .Lkof_list_filter_loop
.Lkof_list_filter_done:
    movq %r14, %rax
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret

.globl kof_list_reduce
.type kof_list_reduce, @function
kof_list_reduce:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    movq %rdi, %r12
    movq %rsi, %r13
    movq %rdx, %r14
    xorl %r15d, %r15d
.Lkof_list_reduce_loop:
    movl 16(%r12), %eax
    cmpl %eax, %r15d
    jge .Lkof_list_reduce_done
    movq 24(%r12), %rax
    movslq %r15d, %rcx
    movq (%rax,%rcx,8), %rdx
    movq %r13, %rsi
    movq %r14, %rdi
    movq 8(%rdi), %rax
    movq (%rax), %rax
    call *%rax
    movq %rax, %r13
    incl %r15d
    jmp .Lkof_list_reduce_loop
.Lkof_list_reduce_done:
    movq %r13, %rax
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret
.section .bss
.balign 8
kof_alloc_lock: .space 40          # pthread_mutex_t (zero-init = default)
kof_free_head: .quad 0
.globl kof_gc_head
.balign 8
kof_gc_head: .quad 0
.balign 8
kof_heap_low: .quad 0
.balign 8
kof_heap_high: .quad 0
.balign 8
kof_main_tid: .quad 0              # tid do main thread p/ o GC (conservador lê a stack)
kof_main_stack_bottom: .quad 0     # rsp do _start: topo da pilha main; o mark varre rsp..ate_isto (G-6b)
.section .data
.Lstr_alloc_fail: .asciz "Runtime error: out of memory"
.section .rodata
.Lkof_alloc_dbg: .ascii "."
.section .text
.globl kof_alloc
.type kof_alloc, @function
kof_alloc:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $24, %rsp
    movq %rdi, (%rsp)                # tamanho solicitado
    leaq kof_alloc_lock(%rip), %rsi  # &lock
    xorl %eax, %eax                  # esperado 0
.Lkof_alloc_lock_try:
    movl $1, %edx
    lock cmpxchg %edx, (%rsi)        # 0->1 atomically?
    testl %eax, %eax
    jz .Lkof_alloc_locked
    # ocupado: futex wait
    movl $1, %edx                    # val=1
    xorq %r10, %r10
    xorq %r8, %r8
    xorq %r9, %r9
    movq $202, %rax                  # SYS_futex WAIT
    syscall
    jmp .Lkof_alloc_lock_try
.Lkof_alloc_locked:
    movq $0, 8(%rsp)                 # flag: GC ainda nao tentou
    movq (%rsp), %r12
    addq $7, %r12
    andq $~7, %r12
    addq $32, %r12
    movq kof_free_head(%rip), %r13
    xorq %r14, %r14
    movq $1048576, %r11
.Lkof_alloc_search:
    testq %r13, %r13
    je .Lkof_alloc_maybe_gc
    decq %r11
    je .Lkof_alloc_mmap
    movq 0(%r13), %r15
    cmpq %r12, %r15
    jb .Lkof_alloc_next
    cmpq $0, %r14
    je .Lkof_alloc_found_head
    movq 8(%r13), %r15
    movq %r15, 8(%r14)
    jmp .Lkof_alloc_found
.Lkof_alloc_found_head:
    movq 8(%r13), %rax
    movq %rax, kof_free_head(%rip)
.Lkof_alloc_found:
    movb $0, 24(%r13)
    movq %r13, %rax
    addq $32, %rax
    incq .Lkof_alloc_count(%rip)
    addq %r12, .Lkof_alloc_bytes(%rip)
    movq %rax, (%rsp)                # preserva retorno
    leaq kof_alloc_lock(%rip), %rdi
    movl $0, (%rdi)
    movl $1, %esi                    # FUTEX_WAKE, 1 waiter
    movq $202, %rax
    xorl %edx, %edx
    xorq %r10, %r10
    syscall
    movq (%rsp), %rax
    addq $24, %rsp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret
.Lkof_alloc_next:
    movq %r13, %r14
    movq 8(%r13), %r13
    jmp .Lkof_alloc_search
.Lkof_alloc_maybe_gc:
    # G-6(a) (native-multiarch, §260): free-list exausta -> UMA
    # passada de collect_now antes do mmap (flag 8(%rsp), ja
    # zerada no prologo). Antes o trigger era INSOND (temporario
    # vivo em caller-saved invisivel ao mark -> sweep liberava
    # bloco vivo -> SIGSEGV 139 medido em KofStringParse/
    # supervisor). Agora kof_gc_collect_now derrama os 15 GPRs
    # (blanket spill) e o cursor de busca aqui e NULL (falhou) —
    # nada vivo em registrador nosso alem dos salvos. Gate
    # kof_spawn_count==0 (contador CUMULATIVO — apos qualquer
    # spawn o auto-collect fica desligado: pilhas de worker nao
    # sao varridas; face "scan de stack de worker" catalogada,
    # nunca silenciosa). Sem gate/flag o hang antigo (status.md)
    # voltava: collect reentrante com cursor vivo.
    cmpq $0, 8(%rsp)
    jne .Lkof_alloc_mmap
    cmpq $0, kof_spawn_count(%rip)
    jne .Lkof_alloc_mmap
    movq $1, 8(%rsp)
    call kof_gc_collect_now
    movq kof_free_head(%rip), %r13
    xorq %r14, %r14
    movq $1048576, %r11
    jmp .Lkof_alloc_search
.Lkof_alloc_maybe_gc_skip:
    jmp .Lkof_alloc_mmap
.Lkof_alloc_mmap:
    movq $0, %rdi
    movq %r12, %rsi
    movq $3, %rdx
    movq $0x22, %r10
    movq $-1, %r8
    movq $0, %r9
    movq $9, %rax
    syscall
    testq %rax, %rax
    js .Lkof_alloc_fail
    movq %r12, 0(%rax)
    movq $0, 8(%rax)
    movq kof_gc_head(%rip), %rcx
    movq %rcx, 16(%rax)
    movb $0, 24(%rax)
    movq %rax, kof_gc_head(%rip)
    movq kof_heap_low(%rip), %rcx
    testq %rcx, %rcx
    je .Lheap_set_low
    cmpq %rcx, %rax
    jae .Lheap_low_ok
.Lheap_set_low:
    movq %rax, kof_heap_low(%rip)
.Lheap_low_ok:
    movq kof_heap_high(%rip), %rcx
    movq %rax, %rdx
    addq %r12, %rdx
    cmpq %rdx, %rcx
    jae .Lheap_high_ok
    movq %rdx, kof_heap_high(%rip)
.Lheap_high_ok:
    addq $32, %rax
    incq .Lkof_alloc_count(%rip)
    addq %r12, .Lkof_alloc_bytes(%rip)
    movq %rax, (%rsp)                # preserva retorno
    leaq kof_alloc_lock(%rip), %rdi
    movl $0, (%rdi)
    movl $1, %esi                    # FUTEX_WAKE, 1 waiter
    movq $202, %rax
    xorl %edx, %edx
    xorq %r10, %r10
    syscall
    movq (%rsp), %rax
    addq $24, %rsp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret
.Lkof_alloc_fail:
    leaq kof_alloc_lock(%rip), %rdi
    movl $0, (%rdi)
    movl $1, %esi
    movq $202, %rax
    xorl %edx, %edx
    xorq %r10, %r10
    syscall
    leaq .Lstr_alloc_fail(%rip), %rdi
    call kof_panic
.section .data
.Lgc_tick: .quad 0
.section .text
.globl kof_gc_mark
.type kof_gc_mark, @function
kof_gc_mark:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    pushq %rbp
    movq %rsp, %r12
    # G-6b (16/09, causa (1) medida no §260): varre a pilha INTEIRA
    # da thread main (rsp_do_coletor .. kof_main_stack_bottom), nao
    # apenas o frame corrente [rsp..rbp]. Com o scan restrito, uma
    # String viva no frame de main enquanto um helper aloca era
    # INVISIVEL ao mark -> sweep liberava vivo (keep corrompido,
    # supervisor SIGSEGV 139 — ambos medidos com gdb 16/09). Lixo
    # stale de frames ja retornados eh conservador-safe: marca a
    # mais = leak de vida, nunca menos = corrupcao; o try_mark ja
    # filtra pelo intervalo de heap. Cap 64MB = guard contra r13
    # corrompido; fallback sp..sp+4096 mantido (harness asm sem
    # _start gravado, mesmo caminho de antes).
    movq kof_main_stack_bottom(%rip), %r13
    testq %r13, %r13
    je .Lgc_mark_stack_fallback
    cmpq %r13, %r12
    jae .Lgc_mark_stack_fallback
    movq %r13, %rax
    subq %r12, %rax
    cmpq $67108864, %rax
    ja .Lgc_mark_stack_fallback
    jmp .Lgc_mark_stack
.Lgc_mark_stack_fallback:
    leaq 4096(%r12), %r13
.Lgc_mark_stack:
    cmpq %r13, %r12
    jge .Lgc_mark_stack_done
    movq (%r12), %rdi
    call kof_gc_mark_transitive
    addq $8, %r12
    jmp .Lgc_mark_stack
.Lgc_mark_stack_done:
    # raizes estaticas: varre .data+.bss ate _end -- cache/mq/config
    # vivem em .data, NAO bss. #113: kof_heap_root_start agora e
    # emitido na ABERTURA do .data do PROGRAMA (NativeBackend.emit),
    # nao no preamble do runtime — estaticos/strings do usuario
    # apontando p/ heap eram raizes ABAIXO do inicio e ficavam
    # invisiveis ao mark. O topo continua _end (fim do .bss):
    # kof_heap_root_end explicito so entra JUNTO do --gc-sections
    # x86 (S-5 #97) — hoje encolheria o intervalo e under-marcaria.
    leaq kof_heap_root_start(%rip), %r12
    leaq _end(%rip), %r13
.Lgc_mark_bss:
    cmpq %r13, %r12
    jge .Lgc_mark_bss_done
    movq (%r12), %rdi
    call kof_gc_mark_transitive
    addq $8, %r12
    jmp .Lgc_mark_bss
.Lgc_mark_bss_done:
.Lgc_mark_heap_done:
    popq %rbp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret

.globl kof_gc_try_mark
.type kof_gc_try_mark, @function
kof_gc_try_mark:
    pushq %rbx
    pushq %r12
    pushq %r10
    movq %rdi, %r12
    cmpq $0x1000, %r12
    jb .Ltry_done_pop
    testq $7, %r12
    jne .Ltry_done_pop
    movq kof_heap_low(%rip), %rbx
    testq %rbx, %rbx
    je .Ltry_heap_ok
    cmpq %rbx, %r12
    jb .Ltry_done_pop
    movq kof_heap_high(%rip), %rbx
    cmpq %rbx, %r12
    jae .Ltry_done_pop
.Ltry_heap_ok:
    movq kof_gc_head(%rip), %rbx
    movq $10000, %r10
.Ltry_loop:
    testq %rbx, %rbx
    je .Ltry_done_pop
    decq %r10
    je .Ltry_done_pop
    leaq 32(%rbx), %rax
    cmpq %rax, %r12
    je .Ltry_found
    movq 0(%rbx), %rcx
    subq $32, %rcx
    leaq 32(%rbx), %rdx
    cmpq %rdx, %r12
    jb .Ltry_next
    addq %rcx, %rdx
    cmpq %rdx, %r12
    jae .Ltry_next
.Ltry_found:
    cmpb $0, 24(%rbx)
    jne .Ltry_done
    movb $1, 24(%rbx)
    jmp .Ltry_done
.Ltry_next:
    movq 16(%rbx), %rbx
    jmp .Ltry_loop
.Ltry_done:
    popq %r10
    popq %r12
    popq %rbx
    ret
.Ltry_done_pop:
    popq %r10
    popq %r12
    popq %rbx
    ret

.globl kof_gc_mark_transitive
.type kof_gc_mark_transitive, @function
kof_gc_mark_transitive:
    pushq %rbx
    pushq %rbp
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    movq %rdi, %r12
    cmpq $0x1000, %r12
    jb .Lmtrans_ret
    testq $7, %r12
    jne .Lmtrans_ret
    movq kof_heap_low(%rip), %rbx
    testq %rbx, %rbx
    je .Lmtrans_heap_ok
    cmpq %rbx, %r12
    jb .Lmtrans_ret
    movq kof_heap_high(%rip), %rbx
    cmpq %rbx, %r12
    jae .Lmtrans_ret
.Lmtrans_heap_ok:
    movq kof_gc_head(%rip), %rbx
    movq $10000, %r10
.Lmtrans_loop:
    testq %rbx, %rbx
    je .Lmtrans_ret
    decq %r10
    je .Lmtrans_ret
    leaq 32(%rbx), %rax
    cmpq %rax, %r12
    je .Lmtrans_found
    movq 0(%rbx), %rcx
    subq $32, %rcx
    leaq 32(%rbx), %rdx
    cmpq %rdx, %r12
    jb .Lmtrans_next
    addq %rcx, %rdx
    cmpq %rdx, %r12
    jae .Lmtrans_next
.Lmtrans_found:
    cmpb $0, 24(%rbx)
    jne .Lmtrans_ret
    movb $1, 24(%rbx)
    movq 0(%rbx), %rcx
    testq %rcx, %rcx
    je .Lmtrans_ret
    leaq 32(%rbx), %r13
    leaq 32(%rbx), %r14
    addq %rcx, %r14
.Lmtrans_fields:
    cmpq %r14, %r13
    jae .Lmtrans_ret
    movq (%r13), %rdi
    testq %rdi, %rdi
    je .Lmtrans_field_next
    pushq %r13
    pushq %r14
    call kof_gc_mark_transitive
    popq %r14
    popq %r13
    jmp .Lmtrans_field_next
.Lmtrans_field_next:
    addq $8, %r13
    jmp .Lmtrans_fields
.Lmtrans_next:
    movq 16(%rbx), %rbx
    jmp .Lmtrans_loop
.Lmtrans_ret:
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbp
    popq %rbx
    ret

.globl kof_gc_sweep
.type kof_gc_sweep, @function
kof_gc_sweep:
    # Percorre a GC list; para cada bloco:
    #   byte flags @24: bit0=mark, bit1=in-free-list
    #   mark==1       -> limpa mark (sobreviveu ao ciclo)
    #   mark==0, !free-> insere na free list (morto), seta bit1
    pushq %rbx
    pushq %r12
    movq kof_gc_head(%rip), %rbx
.Lgc_sweep_loop:
    testq %rbx, %rbx
    je .Lgc_sweep_done
    movzbl 24(%rbx), %eax
    testb $1, %al
    jz .Lgc_sweep_free_it
    # sobreviveu: limpa mark
    andb $~1, %al
    movb %al, 24(%rbx)
    jmp .Lgc_sweep_next
.Lgc_sweep_free_it:
    testb $2, %al
    jnz .Lgc_sweep_next         # ja esta na free list
    # insere na free list
    movq 0(%rbx), %r12          # size (total)
    movq kof_free_head(%rip), %rax
    movq %rax, 8(%rbx)          # next_free = cabeca antiga
    movq %rbx, kof_free_head(%rip)
    orb $2, 24(%rbx)            # marca como liberado
    incq .Lkof_free_count(%rip)
    addq %r12, .Lkof_free_bytes(%rip)
.Lgc_sweep_next:
    movq 16(%rbx), %rbx         # proximo na gc list
    jmp .Lgc_sweep_loop
.Lgc_sweep_done:
    popq %r12
    popq %rbx
    ret

# collect sem o tick-guard: usado por kof_alloc quando a free
# list esta esgotada (evita mmap quando ha lixo coletavel).
# Empilha os callee-saved para que o mark conservador tambem
# enxergue ponteiros vivos em %rbx/%r12-%r15/%rbp do caller
# (o mark so varre a stack -- registrador puro era coletado:
# ex.: 2.º alloc do kof_spawn_handle_new perdia o handle em %rbx).
# G-6(a) (2.1.x native-multiarch, §260 causa-2): o DERRAME passa a
# ser GERAL — os 9 caller-saved (rax,rcx,rdx,rsi,rdi,r8-r11) tambem
# viram raizes visiveis na pilha durante o mark. Era exatamente o
# buraco medido: temporarios vivos do backend no call-site do
# kof_alloc (parse/toFloat, bloco do handle no spawn path) em
# registrador -> mark nao via -> sweep liberava bloco VIVO -> 139.
# Custo: 9 push/pop por coletar (nao por alloc). Um inteiro que
# por acaso imita endereco de bloco so SUPERRETEN (try_mark confere
# alinhamento, faixa do heap e pertencimento a gc-list) — nunca
# corrompe.
.globl kof_gc_collect_now
.type kof_gc_collect_now, @function
kof_gc_collect_now:
    pushq %rax
    pushq %rcx
    pushq %rdx
    pushq %rsi
    pushq %rdi
    pushq %r8
    pushq %r9
    pushq %r10
    pushq %r11
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    pushq %rbp
    call kof_gc_mark
    call kof_gc_sweep
    popq %rbp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    popq %r11
    popq %r10
    popq %r9
    popq %r8
    popq %rdi
    popq %rsi
    popq %rdx
    popq %rcx
    popq %rax
    ret

.globl kof_gc_collect
.type kof_gc_collect, @function
kof_gc_collect:
    movq .Lgc_tick(%rip), %rax
    andq $4095, %rax
    jne .Lgc_collect_skip
    pushq %rbx
    call kof_gc_mark
    call kof_gc_sweep
    popq %rbx
.Lgc_collect_skip:
    incq .Lgc_tick(%rip)
    ret
.globl kof_gc_tick
.type kof_gc_tick, @function
kof_gc_tick:
    movq .Lgc_tick(%rip), %rax
    ret
.section .bss
.balign 8
kof_spawn_handles: .quad 0          # cabeca da lista (no: [next, handle])
kof_spawn_count: .quad 0
# §117 (8a): 256 entries de 16B [tid(8), flag(8)], chave = TID REAL
# (pthread_self) + probe linear — substitui a tabela por hash
# truncado (2 TIDs vivos no mesmo slot = cancel perdido/alheio).
.balign 16
kof_cancel_slots: .space 4096
.section .text
.globl kof_spawn_trampoline
.type kof_spawn_trampoline, @function
kof_spawn_trampoline:
    # rdi = bloco {task, handle}
    # 2 pushes + subq 48 -> site do call rsp ≡ 0 (mesmo padrão do
    # pthread_create em kof_spawn_handle_new). Frame de handler de
    # 48B: [0]=handler, [8]=rsp, [16]=rbp, [24]=chain anterior,
    # [32]=handle — o catch lê DAQUI (o task clobbera callee-saved).
    pushq %rbx
    pushq %r12
    subq $48, %rsp                  # §129: nó de handler do worker
    movq %rdi, %rbx
    # §117 (8a): registra (TID real, flag=0) na tabela de slots
    # com CHAVE = pthread_self (probe linear) — zero colisão: a
    # tabela antiga indexava por hash truncado e 2 TIDs vivos
    # podiam cair no mesmo slot (cancel vazava/apagava alheio).
    call pthread_self               # rax = TID
    movq %rax, %rdi
    call kof_cancel_slot_insert     # rax = entry ptr (flag=0)
    movq 8(%rbx), %r12              # handle
    movq %rax, 32(%r12)             # handle->cancelEntry (trampoline SEMPRE tem handle)
    movq %rax, 40(%rsp)             # §286: cópia da entry NO FRAME — o delete
                                    # lê daqui; handle->cancelEntry pode apontar
                                    # p/ slot ALHEIO se o handle for reciclado
                                    # entre done=1 e o nosso delete
    movq %r12, 32(%rsp)             # frame->handle (o catch lê daqui)
    # §286: aplica cancel pendente marcado por kof_cancel ANTES deste
    # registro (worker recém-spawnado ainda não registrado sob carga).
    # Dekker com kof_cancel: store(cancelEntry) → fence → load(pending).
    mfence
    movq 48(%r12), %rdx
    testq %rdx, %rdx
    jz .Lkof_spawn_no_pend
    testq %rax, %rax                # tabela cheia → nada a marcar
    jz .Lkof_spawn_no_pend
    movq $1, 8(%rax)                # flag = 1 (pedido antigo vale agora)
.Lkof_spawn_no_pend:
    # §129 (DECISIONS §2, opção B): handler POR WORKER. O chain é
    # TLS (RuntimeGc), então um `throw` sem try no worker longjmpa
    # AQUI (não no try da main) e marca o handle como excepcional;
    # await/selectAny do consumidor relança a causa.
    leaq .Lkof_spawn_catch(%rip), %rax
    movq %rax, 0(%rsp)
    movq %rsp, 8(%rsp)
    movq %rbp, 16(%rsp)
    movq %fs:kof_exc_chain@tpoff, %rax
    movq %rax, 24(%rsp)
    movq %rsp, %fs:kof_exc_chain@tpoff
    movq 0(%rbx), %rdi              # task
    movq 8(%rdi), %rax              # task vtable
    movq (%rax), %rax               # vtable[0] = invoke
    call *%rax
    # término normal: desinstala o handler e publica o resultado
    movq 24(%rsp), %rcx
    movq %rcx, %fs:kof_exc_chain@tpoff
    movq 40(%rsp), %rbx             # §286: entry do FRAME antes de desmontar
    addq $48, %rsp
    testq %r12, %r12
    jz .Lkof_spawn_thr_done
    movq %rax, 16(%r12)             # handle->result
    movl $1, 4(%r12)                # handle->done = 1
    jmp .Lkof_spawn_thr_done
.Lkof_spawn_catch:
    # kof_throw_string já desempilhou o chain e restaurou rsp/rbp
    # ao frame base; %rsi = mensagem. O handle vem do FRAME
    # (32(%rsp)) — NÃO de %r12, que o task pode ter clobberado.
    movq 32(%rsp), %r12
    movq %rsi, 40(%r12)             # handle->exc
    movl $1, 4(%r12)                # handle->done = 1
    movq 40(%rsp), %rbx             # §286: entry do FRAME antes de desmontar
    addq $48, %rsp
.Lkof_spawn_thr_done:
    # §117/§286: remove a entry deste TID (tid=0) — slot volta a vazio
    # sem tocar em worker alheio. §286: a entry vem do FRAME do próprio
    # worker (%rbx), não do handle: entre `done=1` publicado e este
    # delete o main pode RECICLAR o handle p/ outro worker, e ler
    # handle->cancelEntry apagaria o slot ALHEIO (stale flag=1 →
    # `cancelled()` vazando 999 p/ o worker novo sob carga).
    testq %rbx, %rbx
    jz .Lkof_spawn_thr_nocl
    movq $0, (%rbx)
.Lkof_spawn_thr_nocl:
    xorl %eax, %eax
    popq %r12
    popq %rbx
    ret

# ---- tabela de cancel por TID real (§117, 8a) ----
# 256 entries de 16B: [tid(8), flag(8)]. Probe linear a partir do
# hash phi do TID — chaves REAIS (pthread_self), colisão resolve
# pelo probe; tid=0 = vazio. Handle: 32=cancelEntry (novo).
.globl kof_cancel_slot_insert
.type kof_cancel_slot_insert, @function
kof_cancel_slot_insert:
    # rdi = TID -> rax = entry (tid=rdi, flag=0) ou 0 (tabela cheia)
    pushq %rbx
    pushq %r12
    movq %rdi, %rbx                 # tid
    movabs $0x9E3779B97F4A7C15, %r10
    mulq %rbx                       # rdx = high 64
    shrq $56, %rdx                  # slot base
    xorl %r12d, %r12d               # i = 0
.Lkcsi_loop:
    cmpl $256, %r12d
    jge .Lkcsi_full
    movl %r12d, %eax
    addl %edx, %eax
    andl $255, %eax
    imull $16, %eax, %eax
    leaq kof_cancel_slots(%rip), %r10
    addq %rax, %r10                 # entry
    cmpq $0, (%r10)
    je .Lkcsi_claim
    cmpq %rbx, (%r10)
    je .Lkcsi_reuse
    incl %r12d
    jmp .Lkcsi_loop
.Lkcsi_claim:
    movq %rbx, (%r10)
.Lkcsi_reuse:
    movq $0, 8(%r10)                # flag = 0
    movq %r10, %rax
    popq %r12
    popq %rbx
    ret
.Lkcsi_full:
    xorl %eax, %eax
    popq %r12
    popq %rbx
    ret

.globl kof_cancel_slot_find
.type kof_cancel_slot_find, @function
kof_cancel_slot_find:
    # rdi = TID -> rax = entry (se registrada e viva) ou 0
    pushq %rbx
    pushq %r12
    movq %rdi, %rbx
    movabs $0x9E3779B97F4A7C15, %r10
    mulq %rbx
    shrq $56, %rdx
    xorl %r12d, %r12d
.Lkcsf_loop:
    cmpl $256, %r12d
    jge .Lkcsf_none
    movl %r12d, %eax
    addl %edx, %eax
    andl $255, %eax
    imull $16, %eax, %eax
    leaq kof_cancel_slots(%rip), %r10
    addq %rax, %r10
    cmpq %rbx, (%r10)
    je .Lkcsf_hit
    incl %r12d
    jmp .Lkcsf_loop
.Lkcsf_hit:
    movq %r10, %rax
    popq %r12
    popq %rbx
    ret
.Lkcsf_none:
    xorl %eax, %eax
    popq %r12
    popq %rbx
    ret

.globl kof_spawn_handle_new
.type kof_spawn_handle_new, @function
kof_spawn_handle_new:
    # rdi = task, esi = wants_result -> handle
    # entry ≡8; 4 push -> ≡8; subq 24 -> ≡8-24? 16k+8-32-24 = 16k-48 ≡ 0 no call ✓
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    subq $24, %rsp
    movq %rdi, %r13
    movl %esi, %r14d
    # G-6(a)/§260: fecha o gate do auto-collect ANTES de qualquer
    # alloc do caminho de spawn. O contador e CUMULATIVO e o unico
    # leitor e o gatilho em .Lkof_alloc_maybe_gc (RuntimeMemory).
    # Se o incq ficar so no .Lkof_spawn_ok (pos-pthread_create),
    # o worker pode chamar kof_alloc nesse intervalo com count
    # ainda 0: o collect-dispara varre sem a stack do worker (face
    # catalogada NUNCA-silencioso) e libera o result-box vivo do
    # proprio worker -- medido: spawnWorkerThrowIsolated* nativo
    # perdia s1=42 -> s1=0 (19/09). Com o incq na entrada, spawn
    # em andamento ou passado nunca ve auto-collect.
    incq kof_spawn_count(%rip)
    movl $56, %edi                  # §117: 32=cancelEntry, 40=exc; §286: 48=pending
    call kof_alloc
    movq %rax, %rbx                 # handle
    movl $2, 0(%rbx)
    movl $0, 4(%rbx)
    movq $0, 8(%rbx)
    movq $0, 16(%rbx)
    movq $0, 24(%rbx)
    movq $0, 32(%rbx)               # cancelEntry = 0
    movq $0, 40(%rbx)
    movq $0, 48(%rbx)               # §286: pending = 0
    # bloco do trampolim
    movl $16, %edi
    call kof_alloc
    movq %r13, 0(%rax)              # task
    movq %rbx, 8(%rax)              # handle
    # GC: o trampolim só é referenciado pelo arg do pthread_create;
    # quando o worker executa, nada na stack/bss do main aponta pra
    # ele → o mark-sweep varreria como morto e o free corromperia o
    # worker. Ancora no handle (24) -- handles ficam na lista global
    # (bss) até o join, então o bloco continua visível ao GC.
    movq %rax, 24(%rbx)
    leaq 8(%rbx), %rdi              # &handle->thread
    xorl %esi, %esi                 # attr = NULL
    leaq kof_spawn_trampoline(%rip), %rdx
    movq %rax, %rcx                 # arg = bloco
    # pthread_create é um C call: a ABI SysV exige rsp ≡ 0 (mod 16)
    # NO SITE DO CALL. O caller (main) pode chegar desalinhado quando
    # um println/print precede o spawn (a convenção args-by-stack via
    # push empilha um slot a mais) -- sem alinhar, a glibc segfaulta
    # em pthread_attr_copy escrevendo no frame. Alinha na hora,
    # preservando r15 (callee-saved, livre aqui) e o frame de rsp:
    pushq %r15                      # [A-8]=r15c ; rsp=A-8
    movq %rsp, %r15                 # r15=A-8
    andq $-16, %rsp                 # rsp=B (B%16==0)
    call pthread_create
    subq %rsp, %r15                 # r15=(A-8)-B = delta
    addq %r15, %rsp                 # rsp=B+delta=A-8
    popq %r15                       # r15c ; rsp=A (frame restaurado)
    testl %eax, %eax
    jz .Lkof_spawn_ok
    # falha no pthread: roda inline (degradacao segura). Passa o
    # BLOCO {task,handle} (handle->block em 24), não a task: o
    # trampolim lê 8(%rdi) como handle.
    movq 24(%rbx), %rdi
    call kof_spawn_trampoline
    movl $1, 4(%rbx)
    jmp .Lkof_spawn_next
.Lkof_spawn_ok:
    # adiciona o handle na lista global p/ join implicito
    movl $16, %edi
    call kof_alloc
    leaq kof_spawn_handles(%rip), %rcx
    movq (%rcx), %rdx               # head atual
    movq %rbx, 8(%rax)              # no->handle
    movq %rdx, 0(%rax)              # no->next
    movq %rax, (%rcx)
    # G-6(a): incq movido p/ entrada de kof_spawn_handle_new —
    # aqui era tarde (janela de alloc do worker com gate aberto).
.Lkof_spawn_next:
    addq $24, %rsp
    movq %rbx, %rax                 # retorna handle
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret

.globl kof_spawn
.type kof_spawn, @function
kof_spawn:
    # rdi = task (stmt) -> handle REGISTRADO: o fim do main chama
    # kof_spawn_join_all e aguarda TODAS as tasks -- tarefa spawnada
    # nunca fica órfã (senão o processo sai antes do worker rodar).
    movl $1, %esi
    jmp kof_spawn_result

.globl kof_spawn_result
.type kof_spawn_result, @function
kof_spawn_result:
    # rdi = task -> handle registrado (await/join depois)
    movl $1, %esi
    jmp kof_spawn_handle_new

.globl kof_await
.type kof_await, @function
kof_await:
    # rdi = handle -> valor (join da thread)
    testq %rdi, %rdi
    jz .Lkof_await_null
    cmpl $2, 0(%rdi)
    jne .Lkof_await_null
    cmpq $0, 8(%rdi)
    je .Lkof_await_val
    pushq %rdi                      # rsp: ≡8 -> ≡0 no call (ABI)
    movq 8(%rdi), %rdi              # pthread_join(tid, NULL)
    xorl %esi, %esi
    call pthread_join
    popq %rdi                       # restaura handle base
    movq $0, 8(%rdi)                # §129: join feito -> zera o TID
                                    # (join_all no fim do main não
                                    # re-join — double join é UB e
                                    # segfaulta com TCB reciclado)
.Lkof_await_val:
    # §129: task que falhou publica a causa no handle (40); o
    # await RELANÇA no thread do consumidor (paridade JVM).
    movq 40(%rdi), %rcx
    testq %rcx, %rcx
    jnz .Lkof_await_throw
    movq 16(%rdi), %rax
    ret
.Lkof_await_throw:
    movq %rcx, %rdi
    jmp kof_throw_string
.Lkof_await_null:
    xorl %eax, %eax
    ret

.globl kof_spawn_join_all
.type kof_spawn_join_all, @function
kof_spawn_join_all:
    # join implicito: percorre a lista e aguarda todas as tasks
    pushq %rbx
    pushq %r12
    subq $8, %rsp
    movq kof_spawn_handles(%rip), %rbx
.Lkof_join_loop:
    testq %rbx, %rbx
    jz .Lkof_join_done
    movq 8(%rbx), %r12              # handle
    cmpq $0, 8(%r12)
    je .Lkof_join_next
    movq 8(%r12), %rdi              # tid
    xorl %esi, %esi                 # retval = NULL
    call pthread_join
.Lkof_join_next:
    movq 0(%rbx), %rbx              # next
    jmp .Lkof_join_loop
.Lkof_join_done:
    addq $8, %rsp
    popq %r12
    popq %rbx
    ret

# kof_await_timeout(handle, timeoutMs): valor se a task terminar no prazo;
# senão lança (kof_throw_string -> try/catch do usuário) ou panic.
# Polling 1ms (o handle já existe; sem join para não bloquear demais).
.Lstr_await_timeout: .asciz "awaitTimeout: estourou o tempo limite"
.globl kof_await_timeout
.type kof_await_timeout, @function
kof_await_timeout:
    # rdi = handle, esi = timeoutMs (>=0)
    # entry rsp≡8; 2 push -> rsp≡0? nao: 16k+8-8-8 = 16k-8 ≡ 8 no call ✓
    pushq %rbx
    pushq %r12
    testq %rdi, %rdi
    jz .Lkat_zero
    cmpl $2, 0(%rdi)
    jne .Lkat_zero
    movq %rdi, %rbx
    movl %esi, %r12d                    # iterações restantes (~1ms cada)
.Lkat_poll:
    cmpl $1, 4(%rbx)                    # done?
    je .Lkat_result
    testl %r12d, %r12d
    jle .Lkat_timeout
    movl $1000, %edi
    call usleep
    decl %r12d
    jmp .Lkat_poll
.Lkat_result:
    # §129: falha do worker relança no consumidor
    movq 40(%rbx), %rcx
    testq %rcx, %rcx
    jnz .Lkat_throw
    movq 16(%rbx), %rax
    popq %r12
    popq %rbx
    ret
.Lkat_throw:
    movq %rcx, %rdi
    jmp kof_throw_string
.Lkat_timeout:
    leaq .Lstr_await_timeout(%rip), %rdi
    call kof_throw_string               # longjmp p/ o try; panic se não houver
.Lkat_zero:
    xorl %eax, %eax
    popq %r12
    popq %rbx
    ret

# CONC001 (residual): done/poll não-bloqueantes sobre o handle.
# Handle: 0=tag(2), 4=done, 8=pthread_t, 16=result. x86 TSO
# garante visibilidade do store do worker p/ um load simples.
.globl kof_done
.type kof_done, @function
kof_done:
    # rdi = handle -> 1 se a tarefa terminou, 0 caso contrário.
    # movzbl: zero-estende p/ rax de 64 bits (bool limpo)
    testq %rdi, %rdi
    jz .Lkof_done_zero
    cmpl $2, 0(%rdi)
    jne .Lkof_done_zero
    movzbl 4(%rdi), %eax
    ret
.Lkof_done_zero:
    xorl %eax, %eax
    ret

.globl kof_poll
.type kof_poll, @function
kof_poll:
    # rdi = handle -> valor se pronto, 0 se ainda não (não bloqueia)
    testq %rdi, %rdi
    jz .Lkof_poll_zero
    cmpl $2, 0(%rdi)
    jne .Lkof_poll_zero
    cmpl $1, 4(%rdi)
    jne .Lkof_poll_zero
    movq 16(%rdi), %rax
    ret
.Lkof_poll_zero:
    xorl %eax, %eax
    ret

# cancel(handle): marca `canceled` NO PRÓPRIO handle (§117, 8a) —
# cooperativo; o worker vê via TLS. Sem calls → alinhamento irrelevante.
.globl kof_cancel
.type kof_cancel, @function
kof_cancel:
    # rdi = handle -> 1 se marcou, 0 se handle nulo/inválido
    testq %rdi, %rdi
    jz .Lkof_cancel_no
    cmpl $2, 0(%rdi)
    jne .Lkof_cancel_no
    movq 8(%rdi), %rsi              # TID (pthread_create grava)
    testq %rsi, %rsi
    jz .Lkof_cancel_no              # nunca disparou
    pushq %rbx                      # §286: handle vivo p/ o caminho pending
    movq %rdi, %rbx
    movq %rsi, %rdi
    call kof_cancel_slot_find
    testq %rax, %rax
    jnz .Lkof_cancel_hit
    # §286: handle criado mas a trampoline AINDA NÃO registrou a entry
    # (janela de agendamento sob carga → o cancel se perdia e o
    # assert(cancel) do §117 falava). Marca pending no handle e
    # re-checa a entry — Dekker com o `mfence` da trampoline: ou o
    # cancel pega a entry recém-criada, ou o start aplica o pending.
    movq $1, 48(%rbx)               # pending = 1
    mfence
    movq 8(%rbx), %rdi
    call kof_cancel_slot_find
    testq %rax, %rax
    jz .Lkof_cancel_yes
.Lkof_cancel_hit:
    movq $1, 8(%rax)                # flag = 1
.Lkof_cancel_yes:
    movl $1, %eax
    popq %rbx
    ret
.Lkof_cancel_no:
    xorl %eax, %eax
    ret

# cancelled(): a flag do TID ATUAL foi marcada?
# 1 call (pthread_self) -> rsp≡8 na entrada, ok.
.globl kof_cancelled
.type kof_cancelled, @function
kof_cancelled:
    # §117: flag da entry do TID ATUAL (chave real, sem colisão).
    # 0 fora de worker (paridade: interpretador sempre 0).
    pushq %rbx
    pushq %r12
    subq $8, %rsp                   # 2 push + subq 8 -> rsp≡8 no call
    call pthread_self
    movq %rax, %rdi
    call kof_cancel_slot_find
    testq %rax, %rax
    jz .Lkof_cancelled_no
    movzbl 8(%rax), %eax
    addq $8, %rsp
    popq %r12
    popq %rbx
    ret
.Lkof_cancelled_no:
    xorl %eax, %eax
    addq $8, %rsp
    popq %r12
    popq %rbx
    ret

# selectAny(list): valor do primeiro handle pronto; senão
# aguarda (polling 1ms) até um terminar -- paridade JVM anyOf.
# frame: 3 push -> mesmos offsets de callee-saved do frame
# antigo. §252: size em r14
# (callee-saved); na slot -8(%rsp) o `call usleep` gravava o
# próprio endereço de retorno e o re-scan lia lixo como size.
.globl kof_select_any
.type kof_select_any, @function
kof_select_any:
    pushq %rbx                      # list
    pushq %r12                      # index
    pushq %r14                      # size (mesma prof. do frame)
    movq %rdi, %rbx
    testq %rbx, %rbx
    jz .Lkof_sel_no
    call kof_list_size              # rsp≡8
    testq %rax, %rax
    jz .Lkof_sel_no
    movq %rax, %r14                 # size
    xorl %r12d, %r12d
.Lkof_sel_scan:
    cmpq %r14, %r12                 # r12 - r14 = index - size
    jge .Lkof_sel_wait              # index >= size -> aguarda e re-escaneia
    movq %rbx, %rdi
    movl %r12d, %esi
    call kof_list_get               # rsp≡8
    testq %rax, %rax
    jz .Lkof_sel_next
    cmpl $2, 0(%rax)
    jne .Lkof_sel_next
    cmpl $1, 4(%rax)
    jne .Lkof_sel_next
    mfence                          # visibilidade do done/result escrito pelo worker
    movq 40(%rax), %rcx             # §129: handle excepcional?
    testq %rcx, %rcx
    jnz .Lkof_sel_rethrow
    movq 16(%rax), %rax             # pronto: devolve resultado
    popq %r14
    popq %r12
    popq %rbx
    ret
.Lkof_sel_rethrow:
    movq %rcx, %rdi
    jmp kof_throw_string
.Lkof_sel_next:
    incq %r12
    jmp .Lkof_sel_scan
.Lkof_sel_wait:
    movl $1000, %edi                # usleep(1ms)
    call usleep                     # rsp≡8
    xorl %r12d, %r12d               # RE-SCAN: reset index (senão loopa p/ sempre)
    jmp .Lkof_sel_scan
.Lkof_sel_no:
    xorl %eax, %eax
    popq %r14
    popq %r12
    popq %rbx
    ret
.section .tbss,"awT",@nobits
.balign 8
kof_exc_chain: .quad 0
.section .text
.globl kof_panic
.type kof_panic, @function
kof_panic:
    call kof_println
    movq $60, %rax
    movq $1, %rdi
    syscall
.globl kof_throw_string
.type kof_throw_string, @function
kof_throw_string:
    movq %rdi, %rsi
    movq %fs:kof_exc_chain@tpoff, %rax
    testq %rax, %rax
    jz .Lkof_throw_panic
    movq 8(%rax), %rsp
    movq 16(%rax), %rbp
    movq 24(%rax), %rcx
    movq %rcx, %fs:kof_exc_chain@tpoff
    movq 0(%rax), %rcx
    testq %rcx, %rcx
    jz .Lkof_throw_panic
    jmp *%rcx
.Lkof_throw_panic:
    movq %rsi, %rdi
    call kof_println_string
    movq $60, %rax
    movq $1, %rdi
    syscall
.Lstr_bounds_err: .asciz "Runtime error: array index out of bounds"
.globl kof_bounds_error
.type kof_bounds_error, @function
kof_bounds_error:
    leaq .Lstr_bounds_err(%rip), %rdi
    call kof_panic
.globl kof_memcpy
.type kof_memcpy, @function
kof_memcpy:
    xorq %rcx, %rcx
.Lkof_memcpy_loop:
    cmpl %ecx, %edx
    jle .Lkof_memcpy_done
    movb (%rsi,%rcx), %al
    movb %al, (%rdi,%rcx)
    incq %rcx
    jmp .Lkof_memcpy_loop
.Lkof_memcpy_done:
    ret
.globl kof_string_from_literal
.type kof_string_from_literal, @function
kof_string_from_literal:
    pushq %rbx
    pushq %r12
    pushq %r13
    movq %rdi, %rbx
    movl %esi, %r12d
    leal 25(%r12), %edi
    call kof_alloc
    movq %rax, %r13
    movl $1, 0(%r13)
    movl $0, 4(%r13)
    movq $0, 8(%r13)
    movl %r12d, 16(%r13)
    movl $0, 20(%r13)
    movq %r13, %rdi
    addq $24, %rdi
    movq %rbx, %rsi
    movl %r12d, %edx
    call kof_memcpy
    movb $0, 24(%r13,%r12)
    movq %r13, %rax
    popq %r13
    popq %r12
    popq %rbx
    ret
.section .rodata
.Lkof_null_str: .asciz "null"
.section .text
.globl kof_string_concat
.type kof_string_concat, @function
kof_string_concat:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    movq %rdi, %rbx
    movq %rsi, %r12
    xorl %r13d, %r13d
    xorl %r15d, %r15d
    testq %rbx, %rbx
    jnz .Lkof_concat_rbx_len
    movl $4, %r13d
    movl $1, %r15d
    jmp .Lkof_concat_r12_len
.Lkof_concat_rbx_len:
    movl 16(%rbx), %r13d
.Lkof_concat_r12_len:
    testq %r12, %r12
    jnz .Lkof_concat_r12_len2
    addl $4, %r13d
    orl $2, %r15d
    jmp .Lkof_concat_alloc
.Lkof_concat_r12_len2:
    addl 16(%r12), %r13d
.Lkof_concat_alloc:
    leal 25(%r13), %edi
    call kof_alloc
    movq %rax, %r14
    movl $1, 0(%r14)
    movl $0, 4(%r14)
    movq $0, 8(%r14)
    movl %r13d, 16(%r14)
    movl $0, 20(%r14)
    movq %r14, %rdi
    addq $24, %rdi
    testl $1, %r15d
    jnz .Lkof_concat_copy_null_rbx
    leaq 24(%rbx), %rsi
    movl 16(%rbx), %edx
    call kof_memcpy
    movl 16(%rbx), %eax
    jmp .Lkof_concat_after_rbx
.Lkof_concat_copy_null_rbx:
    leaq .Lkof_null_str(%rip), %rsi
    movl $4, %edx
    call kof_memcpy
    movl $4, %eax
.Lkof_concat_after_rbx:
    movq %r14, %rdi
    addq $24, %rdi
    addq %rax, %rdi
    testl $2, %r15d
    jnz .Lkof_concat_copy_null_r12
    testq %r12, %r12
    jz .Lkof_concat_done
    leaq 24(%r12), %rsi
    movl 16(%r12), %edx
    call kof_memcpy
    jmp .Lkof_concat_done
.Lkof_concat_copy_null_r12:
    leaq .Lkof_null_str(%rip), %rsi
    movl $4, %edx
    call kof_memcpy
.Lkof_concat_done:
    movb $0, 24(%r14,%r13)
    movq %r14, %rax
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret
.globl kof_string_equals
.type kof_string_equals, @function
kof_string_equals:
    # null-safe: comparar String com null compara ponteiros
    testq %rdi, %rdi
    jz .Lkof_streq_nulla
    testq %rsi, %rsi
    jnz .Lkof_streq_body
    xorl %eax, %eax          # a != null, b == null
    ret
.Lkof_streq_nulla:
    testq %rsi, %rsi
    jnz .Lkof_streq_nullb
    movl $1, %eax            # ambas nulas
    ret
.Lkof_streq_nullb:
    xorl %eax, %eax          # a == null, b != null
    ret
.Lkof_streq_body:
    pushq %rbx
    pushq %r12
    pushq %r13
    movq %rdi, %rbx
    movq %rsi, %r12
    movl 16(%rbx), %r13d
    cmpl %r13d, 16(%r12)
    jne .Lkof_strequals_no
    xorq %rcx, %rcx
.Lkof_strequals_loop:
    cmpl %r13d, %ecx
    jge .Lkof_strequals_yes
    movzbl 24(%rbx,%rcx), %eax
    cmpb %al, 24(%r12,%rcx)
    jne .Lkof_strequals_no
    incq %rcx
    jmp .Lkof_strequals_loop
.Lkof_strequals_yes:
    movl $1, %eax
    popq %r13
    popq %r12
    popq %rbx
    ret
.Lkof_strequals_no:
    xorl %eax, %eax
    popq %r13
    popq %r12
    popq %rbx
    ret
.globl kof_print_string
.type kof_print_string, @function
kof_print_string:
    testq %rdi, %rdi
    jnz .Lkof_print_string_ok
    leaq .Lkof_null_str(%rip), %rsi
    movq $4, %rdx
    movq $1, %rax
    movq $1, %rdi
    syscall
    ret
.Lkof_print_string_ok:
    movq %rdi, %rsi
    addq $24, %rsi
    movl 16(%rdi), %edx
    movq $1, %rax
    movq $1, %rdi
    syscall
    ret
.globl kof_println_string
.type kof_println_string, @function
kof_println_string:
    pushq %rbx
    movq %rdi, %rbx
    movq %rbx, %rdi
    call kof_print_string
    leaq .Lnewline(%rip), %rdi
    call kof_print
    popq %rbx
    ret
.globl kof_array_alloc
.type kof_array_alloc, @function
kof_array_alloc:
    pushq %rbx
    pushq %r12
    movl %edi, %ebx
    movl %esi, %r12d
    movq %rbx, %rax
    imulq %r12, %rax
    addq $24, %rax
    movq %rax, %rdi
    call kof_alloc
    movq %rax, %rcx
    movl $2, 0(%rcx)
    movl $0, 4(%rcx)
    movq $0, 8(%rcx)
    movl %ebx, 16(%rcx)
    movl %r12d, 20(%rcx)
    movq %rcx, %rax
    popq %r12
    popq %rbx
    ret
.section .data
.Lkof_alloc_count: .quad 0
.Lkof_free_count: .quad 0
.Lkof_alloc_bytes: .quad 0
.Lkof_free_bytes: .quad 0
.Lkof_memstats_lbl_alloc: .asciz "allocs: "
.Lkof_memstats_lbl_free: .asciz "frees: "
.Lkof_memstats_lbl_live: .asciz "live bytes: "
.Lkof_memstats_nl: .asciz "\n"
.section .text
.globl kof_memstats
.type kof_memstats, @function
kof_memstats:
    pushq %rbx
    leaq .Lkof_memstats_lbl_alloc(%rip), %rdi
    call kof_print
    movq .Lkof_alloc_count(%rip), %rdi
    call kof_long_to_string
    movq %rax, %rdi
    call kof_print_string
    leaq .Lkof_memstats_nl(%rip), %rdi
    call kof_print
    leaq .Lkof_memstats_lbl_free(%rip), %rdi
    call kof_print
    movq .Lkof_free_count(%rip), %rdi
    call kof_long_to_string
    movq %rax, %rdi
    call kof_print_string
    leaq .Lkof_memstats_nl(%rip), %rdi
    call kof_print
    leaq .Lkof_memstats_lbl_live(%rip), %rdi
    call kof_print
    movq .Lkof_alloc_bytes(%rip), %rbx
    subq .Lkof_free_bytes(%rip), %rbx
    movq %rbx, %rdi
    call kof_long_to_string
    movq %rax, %rdi
    call kof_print_string
    leaq .Lkof_memstats_nl(%rip), %rdi
    call kof_print
    popq %rbx
    ret
            .section .text
.globl kof_init_object
.type kof_init_object, @function
kof_init_object:
    movl %esi, 0(%rdi)
    movl $0, 4(%rdi)
    movq %rdx, 8(%rdi)
    ret
# ================= Web Runtime (WEB002) =================
# Layout das entradas de rotas:
#   .Lweb_routes[i] = { method_ptr(8), path_ptr(8), handler_ptr(8), pad(8) }
# method/path vivem no heap (kof strings); o handler é o
# objeto Lambda* Kof (ainda não invocado aqui).

.section .data
.Lweb_nroutes:     .quad 0
.Lweb_routes:      .space 16384   # 512 rotas de 32B
# resposta fixos
.Lweb_h1:  .asciz "HTTP/1.1 "
.Lweb_ok:  .asciz "200 OK\r\n"
.Lweb_nf:  .asciz "404 Not Found\r\n"
.Lweb_hct: .asciz "Content-Type: text/plain\r\n"
.Lweb_hcc: .asciz "Connection: close\r\n"
.Lweb_hnl: .asciz "Content-Length: "
.Lweb_body_ok: .asciz "route-match"
.Lweb_crlfx2: .asciz "\r\n\r\n"

.section .bss
.Lweb_reqbuf:     .space 16384
.Lweb_skb:        .space 8192
.Lweb_last_body:  .space 8192     # body extraído da última request
.Lweb_last_blen:  .quad 0
.Lweb_last_path:  .space 512

.section .text

# ------------------------------------------------------------------
# strlen c-string: rdi → rax
# ------------------------------------------------------------------
kof_web_strlen:
    xorq %rax, %rax
.Lwsloop:
    cmpb $0, (%rdi,%rax)
    je .Lwsdone
    incq %rax
    jmp .Lwsloop
.Lwsdone:
    ret

# ------------------------------------------------------------------
# int→cstr: rdi=dst, rsi=val → rax=novo_cursor
# (mínimo 4 dígitos significativos, sem sinal)
# ------------------------------------------------------------------
kof_web_i32str:
    pushq %rbx
    pushq %r12
    pushq %r13
    movq %rdi, %rbx
    movq %rsi, %r12
    # como int32 non-negative
    movl %r12d, %eax
    # 10k divisor
    movl $10000, %ecx
    # gera dígitos por divisão
    xorl %edx, %edx
    divl %ecx                # eax=high, edx=low
    movl %eax, %r13d         # saída hi
    # primeiro (hi)
    movl %r13d, %eax
    addl $'0', %eax
    movb %al, (%rbx)
    incq %rbx
    # segundo (lo)
    movl %edx, %eax
    addl $'0', %eax
    movb %al, (%rbx)
    incq %rbx
    # terminar
    movq %rbx, %rax
    popq %r13
    popq %r12
    popq %rbx
    ret

# ------------------------------------------------------------------
# app_new sentinel — único "objeto" real
# ------------------------------------------------------------------
.globl kof_web_app_new
.type kof_web_app_new, @function
kof_web_app_new:
    movq $1, %rax
    ret

# ------------------------------------------------------------------
# kof_web_body(): body da última request (vazio se não há)
# ------------------------------------------------------------------
.globl kof_web_body
.type kof_web_body, @function
kof_web_body:
    leaq .Lweb_last_body(%rip), %rdi
    movq .Lweb_last_blen(%rip), %rsi
    call kof_string_from_literal
    ret

# ------------------------------------------------------------------
# kof_web_set_body_ctx(rdi=ptr, rsi=len) — chamado pelo handle_client
# ------------------------------------------------------------------
.globl kof_web_set_body_ctx
.type kof_web_set_body_ctx, @function
kof_web_set_body_ctx:
    cmpq $8191, %rsi
    jle .Lsbc_ok
    movl $8191, %esi
.Lsbc_ok:
    movq %rsi, .Lweb_last_blen(%rip)
    leaq .Lweb_last_body(%rip), %rdx
    xorq %rcx, %rcx
.Lsbc_cp:
    cmpq %rsi, %rcx
    jae .Lsbc_done
    movb (%rdi,%rcx), %al
    movb %al, (%rdx,%rcx)
    incq %rcx
    jmp .Lsbc_cp
.Lsbc_done:
    movb $0, (%rdx,%rcx)
    ret

# ------------------------------------------------------------------
# route(app_ign rdi, method_string rsi, path_string rdx, handler rcx)
# Registra 1 slot
# ------------------------------------------------------------------
.globl kof_web_route
.type kof_web_route, @function
kof_web_route:
    movq .Lweb_nroutes(%rip), %r8
    imulq $32, %r8, %r9
    leaq .Lweb_routes(%rip), %r10
    addq %r9, %r10
    movq %rsi, 0(%r10)
    movq %rdx, 8(%r10)
    movq %rcx, 16(%r10)
    movq $0, 24(%r10)
    incq %r8
    movq %r8, .Lweb_nroutes(%rip)
    ret

# ------------------------------------------------------------------
# my_strlen vs Kof-String (Kof Expands tipo String em (char*, len))
# Calcula uma string C em rdi e devolve rax=ptr; len em rdx.
# ------------------------------------------------------------------
# Não sei — uso o que existe: kof_string_to_cstring? Não sei se existe.
# Build-to-order: apenas retorno o body nochamlot.

# ------------------------------------------------------------------
# listen(row_ptr rdi, port_int rsi)
# Estratégia: criar socket direto; só sem raw syscalls.
# ------------------------------------------------------------------
.globl kof_web_listen
.type kof_web_listen, @function
kof_web_listen:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    movq %rsi, %r14                  # port (rbx = align16 lixo)
    movl $2, %edi
    movl $1, %esi
    xorl %edx, %edx
    movl $41, %eax                   # SYS_socket
    syscall
    testq %rax, %rax
    js .Lwl_fail
    movq %rax, %rbx                  # server_fd
    # bind
    subq $16, %rsp
    movw $2, (%rsp)
    movq %r14, %rax
    movzx %ax, %eax
    xchgb %al, %ah                   # htons
    movw %ax, 2(%rsp)
    movl $0, 4(%rsp)                 # 0.0.0.0
    movq $0, 8(%rsp)
    movq %rbx, %rdi
    movq %rsp, %rsi
    movl $16, %edx
    movl $49, %eax                   # SYS_bind
    syscall
    testq %rax, %rax
    js .Lwl_bf
    addq $16, %rsp
    # listen
    movq %rbx, %rdi
    movl $64, %esi
    movl $50, %eax                   # SYS_listen
    syscall
# ---- accept loop ----
.Lwl_accept:
    movq %rbx, %rdi
    xorl %esi, %esi
    xorl %edx, %edx
    xorl %r10d, %r10d
    movl $43, %eax                   # SYS_accept
    syscall
    testq %rax, %rax
    js .Lwl_accept
    movq %rax, %r12                  # client_fd
    movq %r12, %rdi                  # passa para handle_client
    call kof_web_handle_client
    movq %r12, %rdi
    movl $3, %eax                    # SYS_close
    syscall
    jmp .Lwl_accept
.Lwl_bf:
    addq $16, %rsp
.Lwl_fail:
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret

# ------------------------------------------------------------------
# handle_client(rdi = client_fd)
# Fluxo: lê request → parseia method+path → pesquisa em routes
# → responde (200 "route-match" se achou, 404 senão).
# ------------------------------------------------------------------
.globl kof_web_handle_client
.type kof_web_handle_client, @function
kof_web_handle_client:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    movq %rdi, %rbx                  # fd

    # read
    movq %rbx, %rdi
    leaq .Lweb_reqbuf(%rip), %rsi
    movl $16384, %edx
    xorl %eax, %eax                  # SYS_read
    syscall
    testq %rax, %rax
    jle .Lwh_404
    movq %rax, %r15                  # len total

    # ------- T4: detecta body (apos CRLF CRLF) -------
    leaq .Lweb_reqbuf(%rip), %r8     # cursor
    leaq (%r8,%r15), %r9             # end
    leaq .Lweb_reqbuf(%rip), %rsi    # scan todas: 


.Lwh_bodyseek:
    cmpq %r9, %rsi
    jae .Lwh_nobody
    movb (%rsi), %al
    cmpb $13, %al
    jne .Lwh_seeknext
    movb 1(%rsi), %al
    cmpb $10, %al
    jne .Lwh_seeknext
    movb 2(%rsi), %al
    cmpb $13, %al
    jne .Lwh_seeknext
    movb 3(%rsi), %al
    cmpb $10, %al
    jne .Lwh_seeknext
    # achou body separator: body comeca em rsi+4
    leaq 4(%rsi), %rdi               # body ptr
    movq %r9, %rsi                   # end
    subq %rdi, %rsi                  # body len
    call kof_web_set_body_ctx
    jmp .Lwh_parsedone
.Lwh_seeknext:
    incq %rsi
    jmp .Lwh_bodyseek
.Lwh_nobody:
    leaq .Lweb_reqbuf(%rip), %rdi
    xorq %rsi, %rsi
    call kof_web_set_body_ctx
.Lwh_parsedone:

    # ------- parse METHOD -------
    leaq .Lweb_reqbuf(%rip), %r8     # cursor
    movq %r8, %r9                    # method_start
.Lwh_m:
    movb (%r8), %al
    cmpb $32, %al                    # ' '
    je .Lwh_m_done
    incq %r8
    jmp .Lwh_m
.Lwh_m_done:
    movq %r8, %r10                   # method_end
    incq %r8                         # skip space
    # ------- parse PATH -------
    movq %r8, %r11                   # path_start
.Lwh_p:
    movb (%r8), %al
    cmpb $32, %al
    je .Lwh_p_done
    cmpb $'\r', %al
    je .Lwh_p_done
    incq %r8
    jmp .Lwh_p
.Lwh_p_done:
    movq %r8, %r12                   # path_end

    # r9=method_start r10=method_end r11=path_start r12=path_end

    # ------- lookup -------
    movq .Lweb_nroutes(%rip), %r13   # count
    leaq .Lweb_routes(%rip), %r14    # base
    xorq %rcx, %rcx                  # idx
.Lwh_loop:
    cmpq %r13, %rcx
    jae .Lwh_404
    # r14 + rcx*32
    movq %rcx, %rax
    imulq $32, %rax, %rax
    leaq (%r14,%rax), %r8            # route[i] (entry)
    # kof method
    movq 0(%r8), %rdi                # method ptr (kof string)
    # method string é (len14 %rdi, chars @ 24(%rdi))
    movl 16(%rdi), %eax              # kof len
    movq %r10, %rdx                  # method_end
    subq %r9, %rdx                   # method_req_len
    cmpl %eax, %edx
    jne .Lwh_next
    # compara chars
    leaq 24(%rdi), %rsi              # src chars
    leaq 0(%r9), %rdi                # req method chars
    # loop
    xorl %eax, %eax
.Lwh_cm:
    cmpl %edx, %eax
    jae .Lwh_cmdone
    movb (%rsi,%rax), %r8b
    cmpb (%rdi,%rax), %r8b
    jne .Lwh_next
    incl %eax
    jmp .Lwh_cm
.Lwh_cmdone:
    # Se matchou method, compara path
    movq 8(%r8), %rdi                # path kof ptr  — mas r8 mudou... rei_load_from rcx
    movq %rcx, %rax
    imulq $32, %rax, %rax
    leaq (%r14,%rax), %r8
    movq 8(%r8), %rdi                # path ptr
    movl 16(%rdi), %eax              # kof path len
    movq %r12, %rdx                  # path_end
    subq %r11, %rdx                  # path_req_len
    cmpl %eax, %edx
    jne .Lwh_next
    leaq 24(%rdi), %rsi
    movq %r11, %rdi                  # req path start
    xorl %eax, %eax
.Lwh_cp:
    cmpl %edx, %eax
    jae .Lwh_cpdone
    movb (%rsi,%rax), %r8b
    cmpb (%rdi,%rax), %r8b
    jne .Lwh_next
    incl %eax
    jmp .Lwh_cp
.Lwh_cpdone:
    # ------- MATCH: invoca handler via trampolim -------
    # handler obj está em 16(%r14,%rax-slot-atual) — reaponto:
    movq %rcx, %rax
    imulq $32, %rax, %rax
    leaq .Lweb_routes(%rip), %r11
    addq %rax, %r11
    movq 16(%r11), %rdi              # handler object
    testq %rdi, %rdi
    jz .Lwh_hello
    movq 8(%rdi), %rax               # vtable
    movq (%rax), %rax                # invoke
    # ABI SysV: call *%rax — precisa rsp alinhado; estamos após 5 pushes+1
    subq $8, %rsp
    call *%rax
    addq $8, %rsp
    # rax = KofString (body)
    testq %rax, %rax
    jz .Lwh_hello                    # null → hello
    movq %rbx, %rdi
    movq %rax, %rsi                  # body ptr (String)
    call kof_web_write_body_response
    jmp .Lwh_done

# ------- 404 -------
.Lwh_next:
    incq %rcx
    jmp .Lwh_loop

# ------- MATCH fallback: responde "hello" se handler null -------
.Lwh_hello:
    movq %rbx, %rdi
    leaq .Lweb_body_hello_lit(%rip), %rsi
    call kof_web_write_body_response
    jmp .Lwh_done

# ---------------------------------------
# write_body_response: rdi=client_fd, rsi=KofString(body)
# Escreve header 200 + Content-Length + CRLF + body
# ---------------------------------------
kof_web_write_body_response:
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    movq %rdi, %rbx                  # fd
    movq %rsi, %r12                  # body String
    leaq .Lweb_skb(%rip), %r13
    # cabecalho 200 OK (sem literais CRLF aqui no asm-comment)
    leaq .Lweb_h1(%rip), %rsi
    movq %r13, %rdi
    call kof_web_append_cstr
    movq %rax, %r13
    leaq .Lweb_ok(%rip), %rsi
    movq %r13, %rdi
    call kof_web_append_cstr
    movq %rax, %r13
    leaq .Lweb_hct(%rip), %rsi
    movq %r13, %rdi
    call kof_web_append_cstr
    movq %rax, %r13
    leaq .Lweb_hnl(%rip), %rsi
    movq %r13, %rdi
    call kof_web_append_cstr
    movq %rax, %r13
    # Content-Length value (kof_int_to_string)
    movl 16(%r12), %edi              # body len (Int32)
    call kof_int_to_string
    # rax=String; append
    movl 16(%rax), %edx              # len deste String
    leaq 24(%rax), %rsi              # chars
.Lwb_resp_cl:
    movb (%rsi), %cl
    movb %cl, (%r13)
    incq %r13
    incq %rsi
    decl %edx
    jnz .Lwb_resp_cl
    # CRLF CRLF
    movb $13, (%r13); incq %r13
    movb $10, (%r13); incq %r13
    movb $13, (%r13); incq %r13
    movb $10, (%r13); incq %r13
    # Body bytes (len da KofString)
    movl 16(%r12), %r14d             # len
    leaq 24(%r12), %rsi              # chars
    xorq %rdx, %rdx
.Lwb_body:
    cmpl %edx, %r14d
    jle .Lwb_body_done
    movb (%rsi,%rdx), %cl
    movb %cl, (%r13)
    incq %r13
    incq %rdx
    jmp .Lwb_body
.Lwb_body_done:
    # write(f, skb, cursor - skb)
    leaq .Lweb_skb(%rip), %rsi
    movq %r13, %rdx
    subq %rsi, %rdx
    movq %rbx, %rdi
    movl $1, %eax                    # SYS_write
    syscall
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret


# ------- 404 -------
.Lwh_404:
    movq %rbx, %rdi
    call kof_web_send_404
.Lwh_done:
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    ret

# ------------------------------------------------------------------
# helpers send (rdi = client_fd)
# ------------------------------------------------------------------
# grava CRLF e retorna cursor+2 (além de rdi)
_kof_web_crlf:
    movb $13, (%rdi)
    incq %rdi
    movb $10, (%rdi)
    incq %rdi
    ret

# copia cstr (rsi) para dst (rdi) byte a byte; retorna fim do dst
kof_web_append_cstr:
.Lwa:
    movzbl (%rsi), %eax
    testb %al, %al
    jz .Lwadone
    movb %al, (%rdi)
    incq %rdi
    incq %rsi
    jmp .Lwa
.Lwadone:
    movq %rdi, %rax
    ret

# 200 route-match: escreve resposta com "route-match"
kof_web_send_match:
    pushq %rbx
    movq %rdi, %rbx
    leaq .Lweb_skb(%rip), %rdi
    leaq .Lweb_h1(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_ok(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_hct(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_hcc(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_hnl(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    # Content-Length: 11
    movb $'1', (%rdi)
    incq %rdi
    movb $'1', (%rdi)
    incq %rdi
    call _kof_web_crlf
    call _kof_web_crlf      # header-end LF+LF
    # body
    leaq .Lweb_body_ok(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    # write(f, skb, cursor - skb)
    leaq .Lweb_skb(%rip), %rsi
    movq %rdi, %rdx
    subq %rsi, %rdx
    movq %rbx, %rdi
    movl $1, %eax                    # SYS_write
    syscall
    popq %rbx
    ret

# 404 Not Found (corpo curto)
kof_web_send_404:
    pushq %rbx
    movq %rdi, %rbx
    leaq .Lweb_skb(%rip), %rdi
    leaq .Lweb_h1(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_nf(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_hcc(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_hnl(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    # Content-Length: 9
    movb $'9', (%rdi)
    incq %rdi
    call _kof_web_crlf
    call _kof_web_crlf
    # body "Not Found"
    leaq .Lweb_nfbody(%rip), %rsi
    call kof_web_append_cstr
    movq %rax, %rdi
    leaq .Lweb_skb(%rip), %rsi
    movq %rdi, %rdx
    subq %rsi, %rdx
    movq %rbx, %rdi
    movl $1, %eax
    syscall
    popq %rbx
    ret

.section .data
.Lweb_nfbody: .asciz "Not Found"
.Lweb_body_hello_lit: .asciz "hello"
.section .text

.globl Default_Main_process_O
.type Default_Main_process_O, @function
Default_Main_process_O:
    pushq %rbp
    movq %rsp, %rbp
    subq $16, %rsp
    movq %rdi, -8(%rbp)
    .loc 1 2 0
    movq -8(%rbp), %rax
    pushq %rax
    .loc 1 2 0
    call toString
    pushq %rax
    .loc 1 2 0
    popq %rax
    movq %rbp, %rsp
    popq %rbp
    ret
.Lfe_Default_Main_process_O:

.globl Default_Main_main
.type Default_Main_main, @function
Default_Main_main:
    pushq %rbp
    movq %rsp, %rbp
    subq $16, %rsp
    .loc 1 6 0
    leaq kof_static_java_lang_System_out(%rip), %rax
    movq 0(%rax), %rax
    pushq %rax
    .loc 1 6 0
    movq $42, %rax
    pushq %rax
    .loc 1 6 0
    popq %rdi
    call kof_box_int
    pushq %rax
    .loc 1 6 0
    popq %rdi
    call Default_Main_process_O
    pushq %rax
    .loc 1 6 0
    .loc 1 6 0
    popq %rdi
    call kof_println_string
    addq $8, %rsp
    .loc 1 7 0
    leaq kof_static_java_lang_System_out(%rip), %rax
    movq 0(%rax), %rax
    pushq %rax
    .loc 1 7 0
    leaq .Lstr_0(%rip), %rdi
    movl $5, %esi
    call kof_string_from_literal
    pushq %rax
    .loc 1 7 0
    popq %rdi
    call Default_Main_process_O
    pushq %rax
    .loc 1 7 0
    .loc 1 7 0
    popq %rdi
    call kof_println_string
    addq $8, %rsp
    movq %rbp, %rsp
    popq %rbp
    ret
.Lfe_Default_Main_main:

.globl _start
_start:
    movq %rsp, kof_main_stack_bottom(%rip)
    movq $186, %rax
    syscall
    movq %rax, kof_main_tid(%rip)
    xorl %edi, %edi
    movl $8, %esi
    call kof_array_alloc
    movq %rax, %rdi
    call Default_Main_main
    movq $231, %rax
    xorq %rdi, %rdi
    syscall

# --- DWARF Kof: DW_TAG_subprogram por funcao (frente 4) ---
    .section .debug_abbrev
.Lkof_abbrev:
    .byte 1
    .byte 0x11
    .byte 1
    .byte 0x03, 0x08
    .byte 0x13, 0x0b
    .byte 0x10, 0x06
    .byte 0x11, 0x01
    .byte 0x12, 0x06
    .byte 0, 0
    .byte 2
    .byte 0x2e
    .byte 1
    .byte 0x11, 0x01
    .byte 0x12, 0x06
    .byte 0x03, 0x08
    .byte 0x3a, 0x0b
    .byte 0x3b, 0x0f
    .byte 0x40, 0x18
    .byte 0x49, 0x13
    .byte 0, 0
    .byte 3
    .byte 0x05
    .byte 0
    .byte 0x03, 0x08
    .byte 0x02, 0x18
    .byte 0x49, 0x13
    .byte 0, 0
    .byte 4
    .byte 0x34
    .byte 0
    .byte 0x03, 0x08
    .byte 0x02, 0x18
    .byte 0x49, 0x13
    .byte 0, 0
    .byte 5
    .byte 0x24
    .byte 0
    .byte 0x0b, 0x0b
    .byte 0x3e, 0x0b
    .byte 0x03, 0x08
    .byte 0, 0
    .byte 6
    .byte 0x24
    .byte 0
    .byte 0x0b, 0x0b
    .byte 0x03, 0x08
    .byte 0, 0
    .byte 0

    .section .debug_info
.Lkof_info:
    .4byte .Lkof_info_end - .Lkof_info - 4
    .2byte 4
    .4byte .Lkof_abbrev
    .byte 8
    .byte 1
    .asciz "Main.kf"
    .byte 0x0c
    .4byte 0
    .quad Default_Main_process_O
    .4byte .Lfe_Default_Main_main - Default_Main_process_O
    .byte 2
    .quad Default_Main_process_O
    .4byte .Lfe_Default_Main_process_O - Default_Main_process_O
    .asciz "process"
    .byte 1
    .uleb128 2
    .byte 1
    .byte 0x56
    .4byte .Lbty1 - .Lkof_info
    .byte 3
    .asciz "item"
    .byte 2
    .byte 0x91, 0x78
    .4byte .Lbty1 - .Lkof_info
    .byte 0
    .byte 2
    .quad Default_Main_main
    .4byte .Lfe_Default_Main_main - Default_Main_main
    .asciz "main"
    .byte 1
    .uleb128 6
    .byte 1
    .byte 0x56
    .4byte .Lbty2 - .Lkof_info
    .byte 0
.Lbty1:
    .byte 5
    .byte 8
    .byte 7
    .asciz "Opaque"
.Lbty2:
    .byte 6
    .byte 0
    .asciz "Void"
    .byte 0
.Lkof_info_end:
