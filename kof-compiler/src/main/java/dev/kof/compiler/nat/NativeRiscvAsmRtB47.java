package dev.kof.compiler.nat;

// DB001 fatia 1 (15/09, dono = 192.168.100.18): runtime kof.db SQLite no
// cross riscv64/aarch64 (aarch64 herda via tradutor). Port do subconjunto
// SQLite do x86 — RuntimeDb2 (resolve/type/connect), RuntimeDb4 (close/
// execute/transaction/bind) e RuntimeDb5 (query). Os caminhos MySQL (Db1/
// Db3/Db6/NativeDbPrepared) ficam de fora: connect só aceita "sqlite:*" e
// devolve 0 (null handle) para qualquer outra URL.
//
// Divergências honestas vs x86 (catalogadas, não silenciosas):
// - kof_db_bind classifica Int×String pela JANELA DO HEAP cross
//   ([_kof_heap, kof_alloc_ptr)) — o x86 usa o limite 0x1000000 do mmap.
//   Um Int cujo valor caia dentro da janela (256KB) seria classificado
//   String; em troca a janela é exata para todo ponteiro vivo.
// - NULL em coluna de query vira o literal JSON `null` (o x86 anexa string
//   VAZIA — make_string len=0 — o que produz JSON inválido; face nunca
//   exercida pelos E2E, que só consultam colunas não-nulas).
// - .Ldb_slots/.Ldb_tx_handle são globais (não TLS como o x86 pós-§129):
//   transaction dentro de spawn cross segue a paridade do EH global do
//   cross (mesma classe do gate OTP001).
//
// O className literal (último arg de kof_db_queryN, empilhado pelo frontend
// — ExpressionDbCallLowerer) chega em a(2+N) e é DESCARTADO por não-uso —
// mesma convenção do x86 (KOF_DB_QUERY_N nunca o lê; stack balanceada).
public final class NativeRiscvAsmRtB47 {

    private NativeRiscvAsmRtB47() {}

    /** kof_db_execute[N](id@a0, sql@a1, b1..bN@a2..) -> rows changed.
     *  Port Db4 KOF_DB_EXEC_N (ramo sqlite; body gerado por aridade —
     *  .macro não sobrevive ao tradutor aarch64). */
    private static String executeN(int n) {
        String fn = n == 0 ? "kof_db_execute" : "kof_db_execute" + n;
        StringBuilder sb = new StringBuilder();
        sb.append("            .globl ").append(fn).append("\n");
        sb.append(fn).append(":\n");
        sb.append("            addi sp, sp, -96\n");
        sb.append(saveS());
        sb.append("            mv   s0, a1\n");                    // sql
        for (int i = 1; i <= n; i++) sb.append("            mv   s").append(i).append(", a").append(i + 1).append("\n");
        sb.append("            call kof_db_resolve\n");           // a0 = id (intacto)
        sb.append("            mv   s5, a0\n");                   // db
        sb.append("            beqz s5, .Lex").append(n).append("_bad\n");
        sb.append("            sd   zero, 0(sp)\n");              // &stmt
        sb.append("            mv   a0, s5\n");
        sb.append("            addi a1, s0, 24\n");
        sb.append("            li   a2, -1\n");
        sb.append("            mv   a3, sp\n");
        sb.append("            li   a4, 0\n");
        sb.append("            call sqlite3_prepare_v2\n");
        for (int i = 1; i <= n; i++) {
            sb.append("            ld   a0, 0(sp)\n");
            sb.append("            li   a1, ").append(i).append("\n");
            sb.append("            mv   a2, s").append(i).append("\n");
            sb.append("            call kof_db_bind\n");
        }
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_step\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_finalize\n");
        sb.append("            mv   a0, s5\n");
        sb.append("            call sqlite3_changes\n");
        sb.append("            j    .Lex").append(n).append("_out\n");
        sb.append("        .Lex").append(n).append("_bad:\n");
        sb.append("            li   a0, 0\n");
        sb.append("        .Lex").append(n).append("_out:\n");
        sb.append(restoreS());
        sb.append("            addi sp, sp, 96\n");
        sb.append("            ret\n\n");
        return sb.toString();
    }

    /** kof_db_query[N](id@a0, sql@a1, b1..bN@a2.., className@a(2+N)) -> List*.
     *  Port Db5 KOF_DB_QUERY_N (ramo sqlite; className nunca lido — cabeçalho). */
    private static String queryN(int n) {
        String fn = n == 0 ? "kof_db_query0" : "kof_db_query" + n;
        StringBuilder sb = new StringBuilder();
        sb.append("            .globl ").append(fn).append("\n");
        sb.append(fn).append(":\n");
        sb.append("            addi sp, sp, -96\n");
        sb.append(saveS());
        sb.append("            mv   s0, a1\n");
        for (int i = 1; i <= n; i++) sb.append("            mv   s").append(i).append(", a").append(i + 1).append("\n");
        sb.append("            call kof_db_resolve\n");
        sb.append("            mv   s5, a0\n");
        sb.append("            beqz s5, .Lq").append(n).append("_bad\n");
        sb.append("            sd   zero, 0(sp)\n");
        sb.append("            mv   a0, s5\n");
        sb.append("            addi a1, s0, 24\n");
        sb.append("            li   a2, -1\n");
        sb.append("            mv   a3, sp\n");
        sb.append("            li   a4, 0\n");
        sb.append("            call sqlite3_prepare_v2\n");
        for (int i = 1; i <= n; i++) {
            sb.append("            ld   a0, 0(sp)\n");
            sb.append("            li   a1, ").append(i).append("\n");
            sb.append("            mv   a2, s").append(i).append("\n");
            sb.append("            call kof_db_bind\n");
        }
        sb.append("            call kof_list_new\n");
        sb.append("            mv   s6, a0\n");
        sb.append("        .Lq").append(n).append("_row:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_step\n");
        sb.append("            li   t0, 100\n");                  // SQLITE_ROW
        sb.append("            bne  a0, t0, .Lq").append(n).append("_done\n");
        sb.append("            call kof_json_builder_new\n");
        sb.append("            mv   s7, a0\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 123\n");                  // '{'
        sb.append("            call kof_json_builder_char\n");
        sb.append("            li   s8, 0\n");                    // col idx
        sb.append("        .Lq").append(n).append("_col:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_column_count\n");
        sb.append("            bge  s8, a0, .Lq").append(n).append("_endrow\n");
        sb.append("            beqz s8, .Lq").append(n).append("_nocomma\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 44\n");                   // ','
        sb.append("            call kof_json_builder_char\n");
        sb.append("        .Lq").append(n).append("_nocomma:\n");
        // nome da coluna: char* -> strlen -> KofStr -> escape JSON -> builder
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_name\n");
        sb.append("            sd   a0, 80(sp)\n");
        sb.append("            call kof_io_strlen\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            ld   a0, 80(sp)\n");
        sb.append("            call kof_io_make_string\n");
        sb.append("            call kof_json_encode_string\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            call kof_json_builder_str\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 58\n");                   // ':'
        sb.append("            call kof_json_builder_char\n");
        // valor pelo tipo da coluna
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_type\n");
        sb.append("            li   t0, 1\n");                    // SQLITE_INTEGER
        sb.append("            beq  a0, t0, .Lq").append(n).append("_int\n");
        sb.append("            li   t0, 3\n");                    // SQLITE_TEXT
        sb.append("            beq  a0, t0, .Lq").append(n).append("_text\n");
        // NULL -> literal `null` (divergência corrigida vs x86 — cabeçalho)
        sb.append("            la   a0, .Ldb_null\n");
        sb.append("            li   a1, 4\n");
        sb.append("            call kof_io_make_string\n");
        sb.append("            j    .Lq").append(n).append("_val\n");
        sb.append("        .Lq").append(n).append("_int:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_int\n");
        sb.append("            call kof_json_encode_int\n");
        sb.append("            j    .Lq").append(n).append("_val\n");
        sb.append("        .Lq").append(n).append("_text:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_text\n");
        sb.append("            sd   a0, 80(sp)\n");
        sb.append("            call kof_io_strlen\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            ld   a0, 80(sp)\n");
        sb.append("            call kof_io_make_string\n");
        sb.append("            call kof_json_encode_string\n");
        sb.append("        .Lq").append(n).append("_val:\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            call kof_json_builder_str\n");
        sb.append("            addi s8, s8, 1\n");
        sb.append("            j    .Lq").append(n).append("_col\n");
        sb.append("        .Lq").append(n).append("_endrow:\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 125\n");                  // '}'
        sb.append("            call kof_json_builder_char\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            call kof_json_builder_result\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            mv   a0, s6\n");
        sb.append("            call kof_list_add\n");
        sb.append("            j    .Lq").append(n).append("_row\n");
        sb.append("        .Lq").append(n).append("_done:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_finalize\n");
        sb.append("            mv   a0, s6\n");
        sb.append("            j    .Lq").append(n).append("_out\n");
        sb.append("        .Lq").append(n).append("_bad:\n");
        sb.append("            li   a0, 0\n");
        sb.append("        .Lq").append(n).append("_out:\n");
        sb.append(restoreS());
        sb.append("            addi sp, sp, 96\n");
        sb.append("            ret\n\n");
        return sb.toString();
    }

    /** Save dos s-regs no frame de 96 (s0..s8 @8..80, ra @88). */
    private static String saveS() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= 8; i++) sb.append("            sd   s").append(i).append(", ").append(8 + i * 8).append("(sp)\n");
        sb.append("            sd   ra, 88(sp)\n");
        return sb.toString();
    }

    private static String restoreS() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= 8; i++) sb.append("            ld   s").append(i).append(", ").append(8 + i * 8).append("(sp)\n");
        sb.append("            ld   ra, 88(sp)\n");
        return sb.toString();
    }

    static final String RISCV_RUNTIME_ASM_B_47;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                .section .rodata
                .Ldb_null: .asciz "null"
                .section .bss
                .align 3
                .Ldb_slots: .zero 512
                .Ldb_types: .zero 64
                .Ldb_count: .quad 0
                # handle (KofString*) da conexão "default" — o que transaction usa.
                .Ldb_default_handle: .quad 0
                # bug 78: handle da conexão COM transação ativa (equivalente do
                # ThreadLocal KOF_DB_TX do JvmConfigRuntime) — bloco transaction
                # aninhado NA MESMA conexão não BEGIN/COMMIT/ROLLBACK.
                .Ldb_tx_handle: .quad 0
                .section .text

                # kof_db_resolve(handle@a0) -> sqlite3* (0 se null). Port Db2:206.
                # Handle = "db<N>" — dígitos em data+2 (offset 26).
                kof_db_resolve:
                    beqz a0, .Lres_null
                    addi t0, a0, 26
                    li   t1, 0
                .Lres_parse:
                    lbu  t2, 0(t0)
                    beqz t2, .Lres_done
                    addi t2, t2, -48
                    li   t3, 10
                    mul  t1, t1, t3
                    add  t1, t1, t2
                    addi t0, t0, 1
                    j    .Lres_parse
                .Lres_done:
                    addi t1, t1, -1
                    la   t0, .Ldb_slots
                    slli t1, t1, 3
                    add  t0, t0, t1
                    ld   a0, 0(t0)
                    ret
                .Lres_null:
                    li   a0, 0
                    ret

                # kof_db_type(handle@a0) -> 1=sqlite (0 se null). Port Db2:229.
                kof_db_type:
                    beqz a0, .Ltyp_null
                    addi t0, a0, 26
                    li   t1, 0
                .Ltyp_parse:
                    lbu  t2, 0(t0)
                    beqz t2, .Ltyp_done
                    addi t2, t2, -48
                    li   t3, 10
                    mul  t1, t1, t3
                    add  t1, t1, t2
                    addi t0, t0, 1
                    j    .Ltyp_parse
                .Ltyp_done:
                    addi t1, t1, -1
                    la   t0, .Ldb_types
                    add  t0, t0, t1
                    lbu  a0, 0(t0)
                    ret
                .Ltyp_null:
                    li   a0, 0
                    ret

                # kof_db_connect(url@a0) — só "sqlite:*" no cross (cabeçalho).
                .globl kof_db_connect
                kof_db_connect:
                    j    .Lconn_go

                # kof_db_connect2(url@a0, user@a1, pass@a2) — credenciais só
                # fazem sentido no MySQL (fora do escopo cross); ignoradas.
                .globl kof_db_connect2
                kof_db_connect2:
                .Lconn_go:
                    addi sp, sp, -128
                    sd   ra, 120(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    sd   s3, 32(sp)
                    sd   s4, 40(sp)
                    # prefixo "sqlite:" em data[0..6] (offsets 24..30)
                    lbu  t0, 24(a0)
                    li   t1, 115
                    bne  t0, t1, .Lconn_bad
                    lbu  t0, 25(a0)
                    li   t1, 113
                    bne  t0, t1, .Lconn_bad
                    lbu  t0, 26(a0)
                    li   t1, 108
                    bne  t0, t1, .Lconn_bad
                    lbu  t0, 27(a0)
                    li   t1, 105
                    bne  t0, t1, .Lconn_bad
                    lbu  t0, 28(a0)
                    li   t1, 116
                    bne  t0, t1, .Lconn_bad
                    lbu  t0, 29(a0)
                    li   t1, 101
                    bne  t0, t1, .Lconn_bad
                    lbu  t0, 30(a0)
                    li   t1, 58
                    bne  t0, t1, .Lconn_bad
                    # sqlite3_open(data+7, &slot@96(sp))
                    sd   zero, 96(sp)
                    addi a0, a0, 31
                    addi a1, sp, 96
                    call sqlite3_open
                    bnez a0, .Lconn_bad
                    ld   s0, 96(sp)
                    # registra o slot: slots[count]=db, types[count]=1, count++
                    la   t0, .Ldb_count
                    ld   t1, 0(t0)
                    li   t2, 63
                    bge  t1, t2, .Lconn_bad
                    la   t2, .Ldb_slots
                    slli t3, t1, 3
                    add  t2, t2, t3
                    sd   s0, 0(t2)
                    la   t2, .Ldb_types
                    add  t2, t2, t1
                    li   t3, 1
                    sb   t3, 0(t2)
                    addi t1, t1, 1
                    la   t2, .Ldb_count
                    sd   t1, 0(t2)
                    # handle "db<N>" — dígitos ao contrário em buffer de 48B @48(sp)
                    addi t2, sp, 95
                    li   t3, 10
                .Lconn_itoa:
                    remu t4, t1, t3
                    addi t4, t4, 48
                    sb   t4, 0(t2)
                    divu t1, t1, t3
                    beqz t1, .Lconn_itoa_done
                    addi t2, t2, -1
                    j    .Lconn_itoa
                .Lconn_itoa_done:
                    mv   s1, t2              # última posição escrita (dígito)
                    addi t2, t2, -1
                    li   t4, 98              # 'b'
                    sb   t4, 0(t2)
                    addi t2, t2, -1
                    li   t4, 100             # 'd'
                    sb   t4, 0(t2)           # t2 = início do handle
                    addi t4, s1, 1
                    sub  t4, t4, t2          # len
                    mv   s2, t2
                    mv   s3, t4
                    # KofString: alloc(25+len), header, copia, NUL
                    addi a0, s3, 25
                    call kof_alloc
                    mv   s4, a0
                    li   t0, 1
                    sw   t0, 0(s4)
                    sw   zero, 4(s4)
                    sd   zero, 8(s4)
                    sw   s3, 16(s4)
                    sw   zero, 20(s4)
                    addi a0, s4, 24
                    mv   a1, s2
                    mv   a2, s3
                    call kof_memcpy
                    addi t0, s4, 24
                    add  t0, t0, s3
                    sb   zero, 0(t0)
                    # conexão atual = default (tx)
                    la   t0, .Ldb_default_handle
                    sd   s4, 0(t0)
                    mv   a0, s4
                    j    .Lconn_out
                .Lconn_bad:
                    li   a0, 0
                .Lconn_out:
                    ld   s4, 40(sp)
                    ld   s3, 32(sp)
                    ld   s2, 24(sp)
                    ld   s1, 16(sp)
                    ld   s0, 8(sp)
                    ld   ra, 120(sp)
                    addi sp, sp, 128
                    ret

                # kof_db_close(id@a0). Port Db4:19 (só o ramo sqlite).
                .globl kof_db_close
                kof_db_close:
                    beqz a0, .Lclose_zero
                    addi sp, sp, -16
                    sd   ra, 8(sp)
                    sd   s0, 0(sp)
                    mv   s0, a0
                    call kof_db_type
                    li   t0, 1
                    bne  a0, t0, .Lclose_done
                    mv   a0, s0
                    call kof_db_resolve
                    beqz a0, .Lclose_done
                    call sqlite3_close
                .Lclose_done:
                    ld   s0, 0(sp)
                    ld   ra, 8(sp)
                    addi sp, sp, 16
                .Lclose_zero:
                    ret

                # kof_db_bind(stmt@a0, idx@a1, val@a2): val na janela do heap
                # [_kof_heap, kof_alloc_ptr) => KofString* (bind_text), senão
                # Int cru (bind_int). Ver cabeçalho (divergência vs x86).
                kof_db_bind:
                    addi sp, sp, -32
                    sd   ra, 24(sp)
                    la   t0, kof_alloc_ptr
                    ld   t0, 0(t0)
                    la   t1, _kof_heap
                    bltu a2, t1, .Lbind_int
                    bgeu a2, t0, .Lbind_int
                    sd   s0, 8(sp)
                    sd   s1, 0(sp)
                    mv   s0, a0
                    mv   s1, a2
                    mv   a0, s0
                    addi a2, s1, 24
                    li   a3, -1
                    li   a4, -1
                    call sqlite3_bind_text
                    ld   s1, 0(sp)
                    ld   s0, 8(sp)
                    j    .Lbind_out
                .Lbind_int:
                    call sqlite3_bind_int
                .Lbind_out:
                    ld   ra, 24(sp)
                    addi sp, sp, 32
                    ret

                # kof_db_transaction(task@a0): BEGIN; lambda; COMMIT (ou
                # ROLLBACK + re-throw). Port Db4:63. Frame de try de 48B com o
                # layout do KofTryStart do emitter (0/8/16/24 = unwinder) e o
                # flag `nested` no slot 32 (mesmo truque do x86 — a lambda
                # pode clobberar registradores; o frame não).
                .globl kof_db_transaction
                kof_db_transaction:
                    addi sp, sp, -112
                    sd   ra, 96(sp)
                    sd   s0, 80(sp)
                    sd   s1, 88(sp)
                    mv   s0, a0              # task (lambda)
                    la   t0, .Ldb_default_handle
                    ld   t0, 0(t0)           # c
                    la   t1, .Ldb_tx_handle
                    ld   t1, 0(t1)
                    li   t3, 0               # nested = 0
                    beqz t1, .Ltx_store_nested
                    bne  t1, t0, .Ltx_store_nested
                    li   t3, 1               # nested = 1
                .Ltx_store_nested:
                    sw   t3, 32(sp)
                    # BEGIN (só se !nested && c != 0) + marca tx ativa
                    bnez t3, .Ltx_begin_done
                    beqz t0, .Ltx_begin_done
                    mv   a0, t0
                    la   a1, .Ldb_begin_str
                    call kof_db_execute
                    la   t2, .Ldb_default_handle
                    ld   t2, 0(t2)
                    la   t3, .Ldb_tx_handle
                    sd   t2, 0(t3)
                .Ltx_begin_done:
                    # try start (mesmo layout de KofTryStart no emitter)
                    la   t0, .Ltx_handler
                    sd   t0, 0(sp)
                    sd   sp, 8(sp)
                    sd   s11, 16(sp)
                    la   t1, kof_exc_chain
                    ld   t2, 0(t1)
                    sd   t2, 24(sp)
                    sd   sp, 0(t1)
                    # invoca a lambda (vtable[0]); a0 = task
                    mv   a0, s0
                    ld   t0, 8(a0)
                    ld   t0, 0(t0)
                    jalr t0
                    # try end / commit (nested NÃO comita — o externo decide)
                    la   t1, kof_exc_chain
                    ld   t2, 24(sp)
                    sd   t2, 0(t1)
                    lw   t3, 32(sp)
                    bnez t3, .Ltx_done
                    la   t0, .Ldb_default_handle
                    ld   t0, 0(t0)
                    beqz t0, .Ltx_done
                    mv   a0, t0
                    la   a1, .Ldb_commit_str
                    call kof_db_execute
                    la   t0, .Ldb_tx_handle
                    sd   zero, 0(t0)
                .Ltx_done:
                    ld   s1, 88(sp)
                    ld   s0, 80(sp)
                    ld   ra, 96(sp)
                    addi sp, sp, 112
                    ret
                .Ltx_handler:
                    # sp = frame base; a0 = exceção (chain já restaurada)
                    mv   s1, a0
                    lw   t3, 32(sp)         # nested
                    bnez t3, .Ltx_rethrow
                    la   t0, .Ldb_default_handle
                    ld   t0, 0(t0)
                    beqz t0, .Ltx_rethrow
                    mv   a0, t0
                    la   a1, .Ldb_rollback_str
                    call kof_db_execute
                    la   t0, .Ldb_tx_handle
                    sd   zero, 0(t0)
                .Ltx_rethrow:
                    mv   a0, s1
                    call kof_throw_string

                # strings Kof para BEGIN/COMMIT/ROLLBACK (lidas por data+24)
                .section .data
                .align 2
                .Ldb_begin_str:
                    .long 1
                    .long 0
                    .quad 0
                    .long 5
                    .long 0
                    .asciz "begin"
                .align 2
                .Ldb_commit_str:
                    .long 1
                    .long 0
                    .quad 0
                    .long 6
                    .long 0
                    .asciz "commit"
                .align 2
                .Ldb_rollback_str:
                    .long 1
                    .long 0
                    .quad 0
                    .long 8
                    .long 0
                    .asciz "rollback"
                .section .text

                """);
        for (int n = 0; n <= 4; n++) sb.append(executeN(n));
        for (int n = 0; n <= 4; n++) sb.append(queryN(n));
        RISCV_RUNTIME_ASM_B_47 = sb.toString();
    }
}