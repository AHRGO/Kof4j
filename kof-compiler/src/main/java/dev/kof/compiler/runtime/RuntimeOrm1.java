package dev.kof.compiler.runtime;

/**
 * kof.orm no Native x86-64 — fatia F1a (D-DB-GAPS DB-1, 20/09):
 * {@code delete_all} SQL-puro sobre o stack kof_db_* existente. SQLite
 * only: MySQL lanca ORM001 honesto em runtime; o cross (riscv/aarch64) e
 * as demais faces seguem ORM001 em compile-time (R6/R7 — nunca silent).
 * Id invalido lanca a MESMA string do host JVM ("unknown db connection: "
 * + id) — paridade por construcao.
 *
 * <p>Convencao dos builders (tudo privado .L): r14 = cursor do corpo,
 * r15d = len acumulado, rbx = KofString* em construcao; os helpers so
 * clobberam rax/rcx/rdx/rsi/rdi/r8-r11. KofString: tag@0=1, len@16,
 * corpo@24. Retornos espelham o host JVM: Bool=(rc==0); SQL error no
 * wrapper JDBC vira -1, logo delete_all = false (nunca excecao).
 */
public final class RuntimeOrm1 {

    private RuntimeOrm1() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # =============== kof.orm F1 helpers (privados .L) ===============

            # .Lorm_bp: append rsi[0..ecx) no builder (r14 cursor, r15d len)
            .Lorm_bp:
                xorl %eax, %eax
            .Lorm_bp_loop:
                cmpl %ecx, %eax
                jge .Lorm_bp_done
                movzbl (%rsi,%rax), %edx
                movb %dl, (%r14)
                incq %r14
                incl %eax
                incl %r15d
                jmp .Lorm_bp_loop
            .Lorm_bp_done:
                ret

            # .Lorm_bh: append 1 byte (r8b) no builder
            .Lorm_bh:
                movb %r8b, (%r14)
                incq %r14
                incl %r15d
                ret

            # .Lorm_bfin: NUL no fim + grava len no header (rbx)
            .Lorm_bfin:
                movb $0, (%r14)
                movl %r15d, 16(%rbx)
                ret

            # .Lorm_bbegin(rdi=cap) -> rbx=KofString* grande o bastante p/ cap
            .Lorm_bbegin:
                leal 25(%rdi), %edi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $0, 20(%rbx)
                leaq 24(%rbx), %r14
                xorl %r15d, %r15d
                movq %rbx, %rax
                ret

            # .Lorm_bad_conn(rdi=id*|NULL): lanca "unknown db connection: "+id
            .Lorm_bad_conn:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %r12
                xorl %r13d, %r13d
                testq %r12, %r12
                jz .Lorm_bc_go
                movl 16(%r12), %r13d
            .Lorm_bc_go:
                movl $23, %edi
                addl %r13d, %edi
                call .Lorm_bbegin
                leaq .Lorm_bc_pre(%rip), %rsi
                movl $23, %ecx
                call .Lorm_bp
                testq %r12, %r12
                jz .Lorm_bc_throw
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                call .Lorm_bp
            .Lorm_bc_throw:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2

            # .Lorm_mysql_pending: ORM001 honesto em runtime (R6)
            .Lorm_mysql_pending:
                leaq .Lorm_mysql_msg(%rip), %rdi
                call kof_throw_string
                ud2

            # .Lorm_conn(rdi=id*) -> rax=handle sqlite; mysql/ruim -> lanca
            .Lorm_conn:
                testq %rdi, %rdi
                jz .Lorm_cn_bad
                pushq %r12
                movq %rdi, %r12
                call kof_db_type
                cmpl $2, %eax
                je .Lorm_cn_my
                cmpl $1, %eax
                jne .Lorm_cn_bad_r
                movq %r12, %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lorm_cn_bad_r
                popq %r12
                ret
            .Lorm_cn_my:
                movq %r12, %rdi
                popq %r12
                call .Lorm_mysql_pending
                ud2
            .Lorm_cn_bad_r:
                movq %r12, %rdi
                popq %r12
            .Lorm_cn_bad:
                call .Lorm_bad_conn
                ud2

            # .Lorm_exec(rdi=db, rsi=sql*) -> eax = 0 ok | -1 SQL error
            .Lorm_exec:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                leaq 24(%r12), %rsi
                xorl %edx, %edx
                xorl %ecx, %ecx
                xorl %r8d, %r8d
                subq $8, %rsp
                movq %rbx, %rdi
                call sqlite3_exec
                addq $8, %rsp
                testl %eax, %eax
                jz .Lorm_ex_ok
                movl $-1, %eax
            .Lorm_ex_ok:
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ---------------------- literais / dados ----------------------
            .Lorm_bc_pre:
                .ascii "unknown db connection: "
            .Lorm_delit:
                .ascii "DELETE FROM \\""
            .Lorm_mysql_msg:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_mysql_len
                .long 0
            .Lorm_mysql_body:
                .ascii "kof.orm on native mysql: not available yet (ORM001)"
                .byte 0
                .set .Lorm_mysql_len, . - .Lorm_mysql_body - 1

            # ---------------------------------------------------------------
            # kof_orm_delete_all(id*, table*, schema*) -> Bool (rax 0/1)
            #   DELETE FROM "table"; rc==0 -> true; SQL error -> false
            #   (espelha o host: kof_db_execute<0 -> false; nunca excecao)
            # ---------------------------------------------------------------
            .globl kof_orm_delete_all
            .type kof_orm_delete_all, @function
            kof_orm_delete_all:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, -8(%rbp)             # id
                movq %rsi, -16(%rbp)            # table
                # cap = 13 + tblLen + 1 + 8 (folga)
                movq -16(%rbp), %rax
                movl 16(%rax), %eax
                addl $22, %eax
                movl %eax, %edi
                call .Lorm_bbegin               # rbx=str, r14=cursor, r15d=0
                movq %rbx, -24(%rbp)
                leaq .Lorm_delit(%rip), %rsi
                movl $13, %ecx
                call .Lorm_bp
                movq -16(%rbp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                call .Lorm_bfin
                # conn depois de qualquer clobber de volatile: id em slot
                movq -8(%rbp), %rdi
                call .Lorm_conn                 # rax = sqlite handle | lanca
                movq %rax, -32(%rbp)
                movq -32(%rbp), %rdi
                movq -24(%rbp), %rsi
                call .Lorm_exec
                testl %eax, %eax
                sete %al
                movzbl %al, %eax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            """);
    }
}
