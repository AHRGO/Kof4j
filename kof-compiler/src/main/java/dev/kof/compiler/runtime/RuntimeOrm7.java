package dev.kof.compiler.runtime;

/**
 * F2c2 (D-DB-GAPS, 21/09): faces {@code where} da leitura row-object no
 * runtime Native x86-64 - {@code kof_orm_where} (igualdade, 6 args) e
 * {@code kof_orm_where_op} (op do usuario, 7 args com className na stack)
 * compartilham UM corpo: whitelist do operador identica ao host (medida:
 * throw exato {@code ORM operator not allowed: <op>}), SQL
 * {@code SELECT * FROM "t" WHERE "f" <op> ?} com bind do value pelo mesmo
 * classificador do key no {@code RuntimeOrm5} (box 284 / KofString do
 * coerce do call-site / null) e o loop de campos do {@code RuntimeOrm6}
 * (397 incluso) acumulado em {@code kof_list_new}/{@code kof_list_add};
 * vazio = lista VAZIA (nunca null), como o host.
 *
 * <p>Contrato de pilha (F1c): prologo com {@code andq}, frame 168, todo
 * {@code call} de C sai com rsp = 0; stmt em {@code r12}, SQL em
 * {@code rbx} (padrao Orm4/Orm5/Orm6).
 */
public final class RuntimeOrm7 {

    private RuntimeOrm7() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # F2c2 (D-DB-GAPS, 21/09): faces `where` da leitura row-object no
            # Native x86-64 - UMA semantica em dois globls:
            #   kof_orm_where(id,field,value,table,schema,className)   -> `=`
            #   kof_orm_where_op(id,field,op,value,table,schema,className)
            #     (arg7=className na STACK: 8(%rsp) na entry, lido antes do
            #      andq; S7f do caller ja empilha arg7 no topo - SysV)
            # corpo compartilhado (.Lorm7_body): whitelist do op (mesmas
            # strings do host: > < >= <= != LIKE ; "==" -> "="; senao throw
            # "ORM operator not allowed: " + op - medido), SQL
            # `SELECT * FROM "t" WHERE "f" <op> ?` com bind do value (mesmo
            # classificador do key no Orm5: box 284 / KofString (coerce do
            # call-site, medido) / null), e o MESMO loop de campos do
            # RuntimeOrm6 (397 incluso) acumulado em kof_list_new/kof_list_add.
            # slots do frame: 0 id | 8 field | 16 value | 24 op | 32 table |
            #   40 schema | 48 className | 56 conn | 64 ftab | 72 nFields |
            #   80 stmt | 88 list | 96 dst | 104 nCols | 112 i | 120 entry |
            #   128 nameLen (ate o SQL: opPtr) | 136 colj (ate o SQL: opLen) |
            #   144 vtab | 152 typeId | 160 totalSize
            # ---------------------------------------------------------------
                .globl kof_orm_where
                .type kof_orm_where, @function
            kof_orm_where:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq $0, 24(%rsp)
                movq %rcx, 32(%rsp)
                movq %r8, 40(%rsp)
                movq %r9, 48(%rsp)
                jmp .Lorm7_body
                .globl kof_orm_where_op
                .type kof_orm_where_op, @function
            kof_orm_where_op:
                movq 8(%rsp), %r10           # className (arg7) ANTES do andq
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rcx, 16(%rsp)
                movq %rdx, 24(%rsp)
                movq %r8, 32(%rsp)
                movq %r9, 40(%rsp)
                movq %r10, 48(%rsp)
            .Lorm7_body:
            # F2d4b: mysql -> restaura o frame e tail-chama .Lorm_where_my
            # (o dispatch mora aqui: as DUAS faces passam pelo corpo)
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm7_my_dispatch
            # ---- whitelist do op (host: antes do SQL; "==" -> "=") ---------
                movq 24(%rsp), %rcx
                testq %rcx, %rcx
                jz .Lorm7_opdef
                movl 16(%rcx), %eax
                cmpl $1, %eax
                jne .Lorm7_op2
                movzbl 24(%rcx), %edx
                cmpl $62, %edx
                je .Lorm7_opok
                cmpl $60, %edx
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op2:
                cmpl $2, %eax
                jne .Lorm7_op4
                movzbl 24(%rcx), %edx
                movzbl 25(%rcx), %r8d
                cmpl $61, %edx
                je .Lorm7_opeq
                cmpl $62, %edx
                jne .Lorm7_op2b
                cmpl $61, %r8d
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op2b:
                cmpl $60, %edx
                jne .Lorm7_op2c
                cmpl $61, %r8d
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op2c:
                cmpl $33, %edx
                jne .Lorm7_opbad
                cmpl $61, %r8d
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op4:
                cmpl $4, %eax
                jne .Lorm7_opbad
                movzbl 24(%rcx), %edx
                movzbl 25(%rcx), %r8d
                movzbl 26(%rcx), %r9d
                movzbl 27(%rcx), %r11d
                cmpl $76, %edx
                jne .Lorm7_opbad
                cmpl $73, %r8d
                jne .Lorm7_opbad
                cmpl $75, %r9d
                jne .Lorm7_opbad
                cmpl $69, %r11d
                jne .Lorm7_opbad
            .Lorm7_opok:
                movl 16(%rcx), %eax
                movl %eax, 136(%rsp)
                leaq 24(%rcx), %rax
                movq %rax, 128(%rsp)
                jmp .Lorm7_bodystart
            .Lorm7_opeq:
            .Lorm7_opdef:
                leaq .Lorm7_eqop(%rip), %rax
                movq %rax, 128(%rsp)
                movl $1, 136(%rsp)
            .Lorm7_bodystart:
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 56(%rsp)
                movq 40(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 64(%rsp)          # ftab
                movq %rcx, 72(%rsp)          # nFields
                movq 48(%rsp), %rax
                leaq 24(%rax), %rdi
                movl 16(%rax), %esi
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm7_noface
                movq %rax, 144(%rsp)         # vtab
                movq %rdx, 152(%rsp)         # typeId
                movq %rcx, 160(%rsp)         # totalSize
                call kof_list_new
                movq %rax, 88(%rsp)          # lista destino
            # ---- SQL: SELECT * FROM "t" WHERE "f" <op> ? --------------------
                movq 32(%rsp), %rax
                movl 16(%rax), %edx          # tblLen
                movq 8(%rsp), %rax
                addl 16(%rax), %edx          # + fieldLen
                addl 136(%rsp), %edx         # + opLen
                addl $45, %edx
                movl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm7_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movq 32(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm7_q(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm7_sp(%rip), %rsi
                movl $1, %ecx
                call .Lorm_bp
                movq 128(%rsp), %rsi
                movl 136(%rsp), %ecx
                call .Lorm_bp
                leaq .Lorm7_qm(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
                call .Lorm_bfin
            # ---- prepare + bind value (param 1) ------------------------------
                movq 56(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 80(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 80(%rsp), %r12
                testq %r12, %r12
                jz .Lorm7_prep_fail
                movq %r12, %rdi
                movl $1, %esi
                movq 16(%rsp), %r13
                testq %r13, %r13
                jz .Lorm7_bnull
                movq (%r13), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lorm7_strchk
                movl 8(%r13), %eax
                cmpl $0, %eax
                je .Lorm7_bint
                cmpl $1, %eax
                je .Lorm7_bquad
                cmpl $2, %eax
                je .Lorm7_bquad
                cmpl $4, %eax
                je .Lorm7_bdbl
                cmpl $5, %eax
                je .Lorm7_bflt
                jmp .Lorm7_bnull
            .Lorm7_bint:
                movl 16(%r13), %eax
                movslq %eax, %rdx
                jmp .Lorm7_bq
            .Lorm7_bquad:
                movq 16(%r13), %rdx
            .Lorm7_bq:
                call sqlite3_bind_int64
                jmp .Lorm7_bstep
            .Lorm7_bdbl:
                movq 16(%r13), %rax
                movq %rax, %xmm0
                call sqlite3_bind_double
                jmp .Lorm7_bstep
            .Lorm7_bflt:
                movl 16(%r13), %eax
                movd %eax, %xmm0
                cvtss2sd %xmm0, %xmm0
                call sqlite3_bind_double
                jmp .Lorm7_bstep
            .Lorm7_strchk:
                cmpl $1, (%r13)
                jne .Lorm7_bnull
                cmpl $0, 4(%r13)
                jne .Lorm7_bnull
                movq 8(%r13), %rax
                testq %rax, %rax
                jnz .Lorm7_bnull
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                jmp .Lorm7_bstep
            .Lorm7_bnull:
                call sqlite3_bind_null
            .Lorm7_bstep:
        # ---- loop de linhas --------------------------------------------
            .Lorm7_row:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Lorm7_end
                movq 160(%rsp), %rdi
                call kof_alloc
                movq %rax, 96(%rsp)          # dst (record novo por linha)
                movq 96(%rsp), %rdi
                movl 152(%rsp), %esi
                movq 144(%rsp), %rdx
                call kof_init_object
            # ---- loop de campos: casar coluna por NOME, ler por typeCode ---
                movq %r12, %rdi
                call sqlite3_column_count
                movl %eax, 104(%rsp)
                movq $0, 112(%rsp)
            .Lorm7_fld:
                movq 112(%rsp), %rax
                cmpq 72(%rsp), %rax
                jge .Lorm7_rowdone
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 64(%rsp), %rax
                movq %rax, 120(%rsp)          # entry
                movl 8(%rax), %eax
                movl %eax, 128(%rsp)         # nameLen
                movq $0, 136(%rsp)           # colj
            .Lorm7_col:
                movl 136(%rsp), %eax
                cmpl 104(%rsp), %eax
                jge .Lorm7_nomatch
                movq %r12, %rdi
                movl 136(%rsp), %esi
                call sqlite3_column_name
                movq %rax, %r13              # colname (C string)
                movq 120(%rsp), %rsi
                movq 0(%rsi), %rsi           # field name
                movl 128(%rsp), %edx
                xorl %ecx, %ecx
            .Lorm7_cmp:
                cmpl %edx, %ecx
                jge .Lorm7_cmp_end
                movzbl (%r13,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                cmpl %r9d, %r8d
                jne .Lorm7_nextcol
                incl %ecx
                jmp .Lorm7_cmp
            .Lorm7_cmp_end:
                cmpb $0, (%r13,%rcx)
                jne .Lorm7_nextcol
                jmp .Lorm7_read              # casou: colj atual
            .Lorm7_nextcol:
                incq 136(%rsp)
                jmp .Lorm7_col
            .Lorm7_read:
                movq 120(%rsp), %rax
                movl 12(%rax), %ecx          # typeCode
                movq 96(%rsp), %rdx
                movq 112(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                addq %rdx, %rax              # slot do campo i
                movq %rax, %r14              # slot (callee-saved)
                movq 136(%rsp), %r15         # colj casada
                cmpl $2, %ecx
                je .Lorm7_rstr
                cmpl $3, %ecx
                je .Lorm7_rbool
                cmpl $4, %ecx
                je .Lorm7_rdbl
                cmpl $5, %ecx
                je .Lorm7_rflt
                cmpl $1, %ecx
                je .Lorm7_rlng
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int
                movslq %eax, %rax
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rlng:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rdbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rflt:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rstr:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax                # SQLITE_NULL -> null
                jne .Lorm7_rstr1
                movq $0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rstr1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                movq %rax, %r13              # ptr
                movq %r13, %rdi
                call kof_io_strlen
                movq %rax, %rsi
                movq %r13, %rdi
                call kof_io_make_string
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rbool:
                # §397 (mesma paridade do binder do host, via Orm5): NULL->
                # false, INTEGER/FLOAT->numero!=0, TEXT->"true" literal.
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax
                je .Lorm7_rboolnull
                cmpl $1, %eax
                je .Lorm7_rboolint
                cmpl $2, %eax
                je .Lorm7_rbooldbl
                jmp .Lorm7_rbool1            # TEXT/BLOB: literal "true"
            .Lorm7_rboolnull:
                movq $0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rboolint:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rbooldbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2si %xmm0, %rax         # truncate = intValue() do binder
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rbool1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                testq %rax, %rax
                jnz .Lorm7_rbool2
                movq $0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rbool2:
                movzbl (%rax), %r13d
                orl $32, %r13d
                cmpl $116, %r13d             # 't'
                jne .Lorm7_rboolf
                movzbl 1(%rax), %r13d
                orl $32, %r13d
                cmpl $114, %r13d             # 'r'
                jne .Lorm7_rboolf
                movzbl 2(%rax), %r13d
                orl $32, %r13d
                cmpl $117, %r13d             # 'u'
                jne .Lorm7_rboolf
                movzbl 3(%rax), %r13d
                orl $32, %r13d
                cmpl $101, %r13d             # 'e'
                jne .Lorm7_rboolf
                movq $1, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rboolf:
                movq $0, (%r14)              # texto != "true" -> false
            .Lorm7_fldnext:
                incq 112(%rsp)
                jmp .Lorm7_fld
            .Lorm7_rowdone:
                movq 88(%rsp), %rdi
                movq 96(%rsp), %rsi
                call kof_list_add
                jmp .Lorm7_row
            .Lorm7_end:
                movq %r12, %rdi
                call sqlite3_finalize
                movq 88(%rsp), %rax
                jmp .Lorm7_ret
            # ---- fail: throw "sqlite: " + errmsg ----------------------------
            .Lorm7_prep_fail:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lorm7_pre(%rip), %rsi
                movl $8, %ecx
                call .Lorm_bp
                movq 56(%rsp), %rdi
                call sqlite3_errmsg
                testq %rax, %rax
                jz .Lorm7_pfin
                movq %rax, %rsi
                call .Lorm_pcstr
            .Lorm7_pfin:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm7_nomatch:
                movl $256, %edi
                call .Lorm_bbegin
                leaq .Lorm7_nc(%rip), %rsi
                movl $18, %ecx
                call .Lorm_bp
                movq 120(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm7_noface:
                leaq .Lorm7_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm7_ret:
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # F2d4b: mysql -> restaura o frame e tail-chama .Lorm_where_my
            # (className vai em r10 — ABI interna; a asm do mysql vive no
            # RuntimeOrmMysqlWhere, emitido junto no mesmo .s).
            .Lorm7_my_dispatch:
                movq 48(%rsp), %r10
                movq 0(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                movq 32(%rsp), %r8
                movq 40(%rsp), %r9
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_where_my

                        .Lorm7_opbad:
                # GC-safe: A e B empilhados ANTES do alloc interno do concat
                # (scan conservativo veem-nos do rsp ate kof_main_stack_bottom;
                #  pushes movem os slots do frame em +16 -> op e 40(%rsp)).
                pushq 24(%rsp)                   # B = op no topo (stack-safe)
                leaq .Lorm7_bad(%rip), %rdi
                movl $26, %esi
                call kof_string_from_literal     # A = rax
                pushq %rax                       # A na stack (stack-safe)
                movq %rax, %rdi
                movq 40(%rsp), %rsi              # op (slot 24 deslocado +16)
                call kof_string_concat
                popq %rcx                        # desempilha A (rax = A + B
                popq %rcx                        #   ja nao serve: rdi e clobber)
                movq %rax, %rdi
                call kof_throw_string
                ud2

            .Lorm7_s1:
                .ascii "SELECT * FROM "
            .Lorm7_q:
                .ascii " WHERE "
            .Lorm7_sp:
                .ascii " "
            .Lorm7_qm:
                .ascii " ?"
            .Lorm7_eqop:
                .ascii "="
                .byte 0
            .Lorm7_bad:
                .ascii "ORM operator not allowed: "
            .Lorm7_pre:
                .ascii "sqlite: "
            .Lorm7_nc:
                .ascii "sqlite: no column "
            .Lorm7_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm7_face_len
                .long 0
            .Lorm7_face_body:
                .ascii "orm.where: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm7_face_len, . - .Lorm7_face_body - 1
        """);
    }
}
