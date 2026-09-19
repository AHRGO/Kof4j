package dev.kof.compiler.runtime;

/**
 * #386 (fatia 2) — lookups de VALOR no map nativo x86_64: containsValue
 * (scan com tag do valor: 0=cmpq raw, 1=String via kof_string_equals,
 * 2=caixa MAGIC via kof_box_equals, 3=miss garantido — o tag é calculado
 * na semântica compartilhada por gates.valueCmpTag; Object vira NAT002 no
 * compile, nunca deref cega) e putIfAbsent (find → hit: devolve o slot sem
 * tocar; miss: kof_map_put já devolve 0 = null de `V?`). Reusa os
 * invariantes de §123 (tag de chave no slot 40 — escrito pelo caller, igual
 * ao put) e §284 (slot de valor boxed para Int/Long) — zero invariante novo.
 */
public final class RuntimeMapLookups {

    private RuntimeMapLookups() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .text
            # kof_map_contains_value(rdi=map, rsi=val, rdx=tag) -> 0/1 (#386)
            .globl kof_map_contains_value
            .type kof_map_contains_value, @function
            kof_map_contains_value:
                cmpl $3, %edx
                je .LKMCV_no
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx             # map
                movq %rsi, %r12             # val
                movl %edx, %r13d            # tag
                xorl %r14d, %r14d           # i
            .LKMCV_loop:
                cmpl 16(%rbx), %r14d
                jge .LKMCV_nopop
                movq 32(%rbx), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %r15    # slot
                cmpl $1, %r13d
                je .LKMCV_str
                cmpl $2, %r13d
                je .LKMCV_box
                cmpq %r12, %r15
                je .LKMCV_yes
                jmp .LKMCV_next
            .LKMCV_str:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .LKMCV_yes
                jmp .LKMCV_next
            .LKMCV_box:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_box_equals
                testl %eax, %eax
                jnz .LKMCV_yes
                jmp .LKMCV_next
            .LKMCV_next:
                incl %r14d
                jmp .LKMCV_loop
            .LKMCV_yes:
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMCV_nopop:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMCV_no:
                xorl %eax, %eax
                ret

            # kof_map_put_if_absent(rdi=map, rsi=key, rdx=val) -> anterior | 0
            # (= null de V? — o mesmo sentinela do put/get §284). A tag de
            # chave no slot 40 é escrita pelo caller (família kof_map_put).
            .globl kof_map_put_if_absent
            .type kof_map_put_if_absent, @function
            kof_map_put_if_absent:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rdx, %r13
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_map_find
                cmpq $-1, %rax
                je .LKMPIA_ins
                movslq %eax, %rcx
                movq 32(%rbx), %rdx
                movq (%rdx,%rcx,8), %rax    # anterior (sem sobrescrever)
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMPIA_ins:
                movq %rbx, %rdi
                movq %r12, %rsi
                movq %r13, %rdx
                call kof_map_put            # insere; retorno já é 0
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
