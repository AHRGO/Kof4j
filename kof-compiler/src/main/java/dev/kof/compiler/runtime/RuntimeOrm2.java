package dev.kof.compiler.runtime;

/**
 * Fatia ORM2 do runtime Native (x86-64): {@code kof_orm_create}.
 *
 * <p>Reaproveita os helpers privados de {@link RuntimeOrm1} (emitidos antes
 * deste bloco no MESMO .s): builder {@code .Lorm_bbegin/.Lorm_bp/.Lorm_bh/
 * .Lorm_bfin}, {@code .Lorm_conn} (sqlite handle; mysql/id ruim lanca as
 * strings do host) e {@code .Lorm_exec} (rc→false como o wrapper JDBC).
 * O schema chega como o literal compilado por {@code KofOrm.schemaString}:
 * campos separados por {@code ','}, partes por {@code ':'} —
 * {@code name:dbType[:generated][:unique]}. O mapeamento de tipos espelha
 * {@code kof_orm_sql_type}/{@code kof_orm_pk_ddl} na dialecto sqlite (a
 * unica que o Native serve hoje; mysql e ORM001 honesto no .Lorm_conn).
 *
 * <p>Contrato de pilha (F1c): entrada 16-alinhada + {@code andq} no prologo;
 * todo {@code call} para C sai com {@code rsp % 16 == 0}; epilogio espelha
 * {@code delete_all} (pops na regiao andada, {@code rbp} so no fim).
 * Estado vivo sobre calls de C: {@code rbx}=buf, {@code r12}=cursor de
 * leitura do schema, {@code r13}=fim do schema; o builder possui
 * {@code r14}/{@code r15} (cursor/tamanho) e os helpers {@code .Lorm_b*}
 * nunca tocam em {@code r12/r13}.
 */
public final class RuntimeOrm2 {

    private RuntimeOrm2() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # kof_orm_create(id*, table*, schema*) -> Bool
            #   CREATE TABLE IF NOT EXISTS "t" ("a" T[ UNIQUE], ...)
            #   — espelha o host; generated vira o pk_ddl do sqlite.
            #   slots: 0 id | 8 table | 16 schema | 24 conn | 32 nameStart |
            #   40 nameLen | 48 typeStart | 56 f1 | 64 gen | 72 uniq
            # ---------------------------------------------------------------
                .globl kof_orm_create
                .type kof_orm_create, @function
            kof_orm_create:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $88, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                xorl %eax, %eax
                movq %rax, 48(%rsp)             # typeStart = NULL (sem tipo)
                movl $1, %eax
                movq %rax, 56(%rsp)             # f1
                movq $0, 64(%rsp)               # gen
                movq $0, 72(%rsp)               # uniq
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 24(%rsp)             # conn no slot (r14/r15 viram
                                                #   cursor/len do builder)
                # cap = 66 + tblLen + 40 + 40*campos  — limite: por campo no
                # maximo nome(<=schema)+3 aspas/espaco+VARCHAR(255) UNIQUE(19)
                # + ", " <= 2*schemaLen + 24; folga total = 64 + tbl + 2*sch + 24*8
                movq 8(%rsp), %rax
                movl 16(%rax), %edi             # tblLen
                addl $128, %edi
                movq 16(%rsp), %rax
                movl 16(%rax), %ecx
                leal (%rdi,%rcx,2), %edi        # + 2*schemaLen
                call .Lorm_bbegin               # rbx=buf, r14=cursor, r15d=0
                leaq .Lorm2_cre(%rip), %rsi
                movl $.Lorm2_cre_len, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rdi
                call .Lorm2_qq                  # "table"
                movl $40, %r8d                  # '('
                call .Lorm_bh
                # loop de campos: r12=corpo, r13=fim
                movq 16(%rsp), %rax
                leaq 24(%rax), %r12
                movl 16(%rax), %ecx
                addq %r12, %rcx
                movq %rcx, %r13
            .Lorm2_field:
                cmpq %r13, %r12
                jae .Lorm2_last
                cmpq $0, 56(%rsp)
                jne .Lorm2_nosep
                leaq .Lorm2_comma(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lorm2_nosep:
                movq $0, 56(%rsp)               # f1 = false daqui em diante
                # --- nome: ate ':' ',' ou fim
                movq %r12, 32(%rsp)             # nameStart
            .Lorm2_nscan:
                cmpq %r13, %r12
                jae .Lorm2_nend
                movzbl (%r12), %eax
                cmpl $58, %eax                  # ':'
                je .Lorm2_nend
                cmpl $44, %eax                  # ','
                je .Lorm2_nend
                incq %r12
                jmp .Lorm2_nscan
            .Lorm2_nend:
                movq %r12, %rax
                subq 32(%rsp), %rax
                movq %rax, 40(%rsp)             # nameLen
                movl $34, %r8d                  # '"'
                call .Lorm_bh
                movq 32(%rsp), %rsi
                movq 40(%rsp), %rcx
                call .Lorm_bp
                movl $34, %r8d                  # '"'
                call .Lorm_bh
                movl $32, %r8d                  # ' '
                call .Lorm_bh
                # --- flag reset + tokens ':x'
                movq $0, 64(%rsp)
                movq $0, 72(%rsp)
                movq $0, 48(%rsp)               # typeStart NULL
            .Lorm2_tok:
                cmpq %r13, %r12
                jae .Lorm2_emit
                cmpb $44, (%r12)                # ','
                je .Lorm2_commaField
                cmpb $58, (%r12)                # ':'
                jne .Lorm2_badtok               # nao deveria: consome ate ,
                incq %r12
                movq %r12, %r8                  # ts
            .Lorm2_tscan:
                cmpq %r13, %r12
                jae .Lorm2_tdone
                movzbl (%r12), %eax
                cmpl $58, %eax
                je .Lorm2_tdone
                cmpl $44, %eax
                je .Lorm2_tdone
                incq %r12
                jmp .Lorm2_tscan
            .Lorm2_tdone:
                movq %r12, %rcx
                subq %r8, %rcx                  # len do token
                cmpq $0, 48(%rsp)
                jne .Lorm2_flag                 # ja temos o tipo -> flags
                movq %r8, 48(%rsp)              # typeStart
                movq %rcx, 40(%rsp)             # typeLen
                jmp .Lorm2_tok_next
            .Lorm2_flag:
                movq %r8, %rdi                  # token
                movq %r12, %rsi
                subq %r8, %rsi                  # len (rcx pode estar clobber)
                leaq .Lorm2_gen(%rip), %rdx
                movl $9, %ecx
                call .Lorm2_tis
                testl %eax, %eax
                jz .Lorm2_f2
                movq $1, 64(%rsp)
                jmp .Lorm2_tok_next
            .Lorm2_f2:
                movq %r8, %rdi
                movq %r12, %rsi
                subq %r8, %rsi
                leaq .Lorm2_uq(%rip), %rdx
                movl $6, %ecx
                call .Lorm2_tis
                testl %eax, %eax
                jz .Lorm2_tok_next
                movq $1, 72(%rsp)
            .Lorm2_tok_next:
                cmpq %r13, %r12
                jae .Lorm2_emit
                cmpb $44, (%r12)
                je .Lorm2_commaField
                incq %r12                       # consome ':'
                jmp .Lorm2_tok
            .Lorm2_badtok:
                incq %r12
                jmp .Lorm2_tok_next
            .Lorm2_commaField:
                incq %r12                       # consome ','
            .Lorm2_emit:
                movq 48(%rsp), %r8
                cmpq $0, %r8
                je .Lorm2_emit_vc
                cmpq $0, 64(%rsp)               # generated?
                je .Lorm2_notgen
                leaq .Lorm2_pk(%rip), %rsi
                movl $.Lorm2_pk_len, %ecx
                call .Lorm_bp
                jmp .Lorm2_sep
            .Lorm2_notgen:
                movq 48(%rsp), %rdi
                movq 40(%rsp), %rsi
                leaq .Lorm2_int(%rip), %rdx
                movl $3, %ecx
                call .Lorm2_tis
                testl %eax, %eax
                jz .Lorm2_l2
                leaq .Lorm2_integer(%rip), %rsi
                movl $.Lorm2_integer_len, %ecx
                call .Lorm_bp
                jmp .Lorm2_uniq
            .Lorm2_l2:
                movq 48(%rsp), %rdi
                movq 40(%rsp), %rsi
                leaq .Lorm2_long(%rip), %rdx
                movl $4, %ecx
                call .Lorm2_tis
                testl %eax, %eax
                jz .Lorm2_l3
                leaq .Lorm2_integer(%rip), %rsi
                movl $.Lorm2_integer_len, %ecx
                call .Lorm_bp
                jmp .Lorm2_uniq
            .Lorm2_l3:
                movq 48(%rsp), %rdi
                movq 40(%rsp), %rsi
                leaq .Lorm2_bool(%rip), %rdx
                movl $4, %ecx
                call .Lorm2_tis
                testl %eax, %eax
                jz .Lorm2_l4
                leaq .Lorm2_boolean(%rip), %rsi
                movl $.Lorm2_boolean_len, %ecx
                call .Lorm_bp
                jmp .Lorm2_uniq
            .Lorm2_l4:
                movq 48(%rsp), %rdi
                movq 40(%rsp), %rsi
                leaq .Lorm2_double(%rip), %rdx
                movl $6, %ecx
                call .Lorm2_tis
                testl %eax, %eax
                jz .Lorm2_l5
                leaq .Lorm2_double_sql(%rip), %rsi
                movl $.Lorm2_double_sql_len, %ecx
                call .Lorm_bp
                jmp .Lorm2_uniq
            .Lorm2_l5:
                movq 48(%rsp), %rdi
                movq 40(%rsp), %rsi
                leaq .Lorm2_float(%rip), %rdx
                movl $5, %ecx
                call .Lorm2_tis
                testl %eax, %eax
                jz .Lorm2_emit_vc
                leaq .Lorm2_real(%rip), %rsi
                movl $.Lorm2_real_len, %ecx
                call .Lorm_bp
                jmp .Lorm2_uniq
            .Lorm2_emit_vc:
                leaq .Lorm2_vc(%rip), %rsi
                movl $.Lorm2_vc_len, %ecx
                call .Lorm_bp
            .Lorm2_uniq:
                cmpq $0, 72(%rsp)
                je .Lorm2_sep
                cmpq $0, 64(%rsp)               # generated nunca ganha UNIQUE
                                                #   separado (pk ja e o gen)
                jne .Lorm2_sep
                leaq .Lorm2_unique(%rip), %rsi
                movl $.Lorm2_unique_len, %ecx
                call .Lorm_bp
            .Lorm2_sep:
                jmp .Lorm2_field
            .Lorm2_last:
                movl $41, %r8d                  # ')'
                call .Lorm_bh
                call .Lorm_bfin
                movq 24(%rsp), %rdi
                movq %rbx, %rsi
                call .Lorm_exec
                testl %eax, %eax
                sete %al
                movzbl %al, %eax
                addq $88, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # .Lorm2_qq(rdi=KofString*) -> emite '"' body '"' no builder
            .Lorm2_qq:
                pushq %r12
                pushq %r13
                movq %rdi, %r12
                movl $34, %r8d
                call .Lorm_bh
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                popq %r13
                popq %r12
                ret

            # .Lorm2_tis(rdi=token*, rsi=tokenLen, rdx=lit, ecx=litLen) -> eax 1/0
            .Lorm2_tis:
                cmpl %ecx, %esi
                jne .Lorm2_tsx
                xorl %eax, %eax
            .Lorm2_tsi:
                cmpl %ecx, %eax
                jge .Lorm2_tso
                movzbl (%rdi,%rax), %r10d
                cmpb (%rdx,%rax), %r10b
                jne .Lorm2_tsx
                incl %eax
                jmp .Lorm2_tsi
            .Lorm2_tso:
                movl $1, %eax
                ret
            .Lorm2_tsx:
                xorl %eax, %eax
                ret

            # ---------------------- literais --------------------------------
            .Lorm2_cre:
                .ascii "CREATE TABLE IF NOT EXISTS "
                .set .Lorm2_cre_len, . - .Lorm2_cre
            .Lorm2_comma:
                .ascii ", "
            .Lorm2_pk:
                .ascii "INTEGER PRIMARY KEY AUTOINCREMENT"
                .set .Lorm2_pk_len, . - .Lorm2_pk
            .Lorm2_integer:
                .ascii "INTEGER"
                .set .Lorm2_integer_len, . - .Lorm2_integer
            .Lorm2_boolean:
                .ascii "BOOLEAN"
                .set .Lorm2_boolean_len, . - .Lorm2_boolean
            .Lorm2_double_sql:
                .ascii "DOUBLE"
                .set .Lorm2_double_sql_len, . - .Lorm2_double_sql
            .Lorm2_real:
                .ascii "REAL"
                .set .Lorm2_real_len, . - .Lorm2_real
            .Lorm2_vc:
                .ascii "VARCHAR(255)"
                .set .Lorm2_vc_len, . - .Lorm2_vc
            .Lorm2_unique:
                .ascii " UNIQUE"
                .set .Lorm2_unique_len, . - .Lorm2_unique
            .Lorm2_gen:
                .ascii "generated"
            .Lorm2_uq:
                .ascii "unique"
            .Lorm2_int:
                .ascii "int"
            .Lorm2_long:
                .ascii "long"
            .Lorm2_bool:
                .ascii "bool"
            .Lorm2_double:
                .ascii "double"
            .Lorm2_float:
                .ascii "float"
            """);
    }
}
