package dev.kof.compiler.nat;

// FASE 3 (REFACTOR-500): fatia 0 de RISCV_RUNTIME_ASM_B — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
public final class NativeRiscvAsmRtB0 {

    private NativeRiscvAsmRtB0() {}

    static  String RISCV_RUNTIME_ASM_B_0 = """
            # kof_string_to_int MOVIDO p/ B30 (bug 79 U3: contrato JDK
            # c/ trim+digito-a-digito+overflow->throw; este corpo silencioso
            # era "abc"=0 / "12a34"=1234 — divergente do JVM).

            # ---- List (typeId@0 super@4 vtable@8 len@16 cap@20 data@24) ----
            .globl kof_list_new
            kof_list_new:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                li   a0, 32
                call kof_alloc
                mv   s0, a0
                li   t0, 100
                sw   t0, 0(s0)
                li   t0, 0
                sw   t0, 4(s0)
                sd   t0, 8(s0)
                sw   t0, 16(s0)
                li   t0, 2
                sw   t0, 20(s0)
                li   a0, 16
                call kof_alloc
                sd   a0, 24(s0)
                mv   a0, s0
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_list_grow
            kof_list_grow:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                lw   t0, 20(s0)
                slli t0, t0, 1
                sw   t0, 20(s0)
                slli a0, t0, 3
                addi a0, a0, 24
                call kof_alloc
                mv   s1, a0
                ld   a1, 24(s0)
                lw   a2, 16(s0)
                slli a2, a2, 3
                call kof_memcpy
                sd   s1, 24(s0)
                mv   a0, s0
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_add
            kof_list_add:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t0, 20(s0)
                lw   t1, 16(s0)
                bge  t1, t0, .Lla_grow
                ld   t2, 24(s0)
                slli t3, t1, 3
                add  t2, t2, t3
                sd   s1, 0(t2)
                addi t1, t1, 1
                sw   t1, 16(s0)
                j    .Lla_done
            .Lla_grow:
                mv   a0, s0
                call kof_list_grow
                lw   t1, 16(s0)
                ld   t2, 24(s0)
                slli t3, t1, 3
                add  t2, t2, t3
                sd   s1, 0(t2)
                addi t1, t1, 1
                sw   t1, 16(s0)
            .Lla_done:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_size
            kof_list_size:
                lw   a0, 16(a0)
                ret

            # kof_list_remove(list, idx) -> item removido (desloca o resto)
            .globl kof_list_remove
            kof_list_remove:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)      # list
                sd   s1, 8(sp)       # idx
                sd   s2, 0(sp)       # item
                mv   s0, a0
                mv   s1, a1
                ld   t0, 24(s0)
                slli t1, s1, 3
                add  t0, t0, t1
                ld   s2, 0(t0)       # item = data[idx]
                lw   t2, 16(s0)      # size
                addi t3, s1, 1       # i = idx+1
            .Llr_loop:
                bge  t3, t2, .Llr_done
                ld   t0, 24(s0)
                slli t4, t3, 3
                add  t4, t0, t4
                ld   t5, 0(t4)       # data[i]
                ld   t0, 24(s0)
                addi t6, t3, -1
                slli t6, t6, 3
                add  t6, t0, t6
                sd   t5, 0(t6)       # data[i-1] = data[i]
                addi t3, t3, 1
                j    .Llr_loop
            .Llr_done:
                addi t2, t2, -1
                sw   t2, 16(s0)      # size--
                mv   a0, s2
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_get
            kof_list_get:
                addi sp, sp, -16
                sd   ra, 8(sp)
                lw   t0, 16(a0)
                bge  a1, t0, .Llg_bounds
                blt  a1, zero, .Llg_bounds
                ld   t1, 24(a0)
                slli t2, a1, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Llg_bounds:
                call kof_bounds_error

            .globl kof_list_set
            kof_list_set:
                addi sp, sp, -16
                sd   ra, 8(sp)
                lw   t0, 16(a0)
                bge  a1, t0, .Lls_bounds
                blt  a1, zero, .Lls_bounds
                ld   t1, 24(a0)
                slli t2, a1, 3
                add  t1, t1, t2
                sd   a2, 0(t1)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lls_bounds:
                call kof_bounds_error

            .globl kof_list_contains
            kof_list_contains:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2            # tag: 0=raw, 1=String, 2=objeto Kof
                li   s3, 0
            .Llc_loop:
                lw   t1, 16(s0)
                bge  s3, t1, .Llc_no
                ld   t2, 24(s0)
                slli t3, s3, 3
                add  t2, t2, t3
                ld   t2, 0(t2)
                li   t4, 1
                beq  s2, t4, .Llc_str
                li   t4, 2
                beq  s2, t4, .Llc_obj
                beq  t2, s1, .Llc_yes
                j    .Llc_next
            .Llc_str:
                beqz t2, .Llc_next
                mv   a0, t2
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Llc_yes
                j    .Llc_next
            .Llc_obj:
                beqz t2, .Llc_next
                mv   a0, t2
                mv   a1, s1
                call kof_obj_equals
                bnez a0, .Llc_yes
            .Llc_next:
                addi s3, s3, 1
                j    .Llc_loop
            .Llc_yes:
                li   a0, 1
                j    .Llc_ret
            .Llc_no:
                li   a0, 0
            .Llc_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            .globl kof_obj_equals
            # §104b-ii (24/09): igualdade por CONTEUDO de referencia Kof
            # (record/classe). a0=a, a1=b -> a0 1/0. a==b (inclui null==null)
            # -> 1; um nulo -> 0; String (type_id==1) -> kof_string_equals;
            # senao despacha o equals virtual por kof_equals_table[type_id]
            # (0 -> 0 = sem equals).
            kof_obj_equals:
                beq  a0, a1, .Lkoe_yes
                beqz a0, .Lkoe_no
                beqz a1, .Lkoe_no
                lw   t0, 0(a0)
                li   t1, 1
                beq  t0, t1, .Lkoe_str
                la   t2, kof_equals_table
                slli t0, t0, 3
                add  t2, t2, t0
                ld   t3, 0(t2)
                beqz t3, .Lkoe_no
                jr   t3
            .Lkoe_str:
                j    kof_string_equals
            .Lkoe_yes:
                li   a0, 1
                ret
            .Lkoe_no:
                li   a0, 0
                ret

            .globl kof_list_is_empty
            kof_list_is_empty:
                lw   t0, 16(a0)
                seqz a0, t0
                ret

            .globl kof_list_clear
            kof_list_clear:
                li   t0, 0
                sw   t0, 16(a0)
                ret

            # ---- kof.log: timestamp `yyyy-MM-dd HH:mm:ss.SSS` (UTC) + rótulo
            # JVM + msg + newline; KOF_LOG_LEVEL filtra; stderr para warn/error,
            # stdout para info/debug. Contrato JVM/x86 (NativeLogE2ETest).
            # Fatias 2a/2b (26/09, D-FULL-PARITY-050 linha 9): nível/fd/rótulo
            # + timestamp civil Hinnant, espelhando RuntimeLog1/2 do x86.
            .globl kof_log_debug
            kof_log_debug:
                li   a1, 0
                j    kof_log_write_lvl
            .globl kof_log_info
            kof_log_info:
                li   a1, 1
                j    kof_log_write_lvl
            .globl kof_log_warn
            kof_log_warn:
                li   a1, 2
                j    kof_log_write_lvl
            .globl kof_log_error
            kof_log_error:
                li   a1, 3
                j    kof_log_write_lvl
            # helper: a0=msg*, a1=level (0..3)
            kof_log_write_lvl:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)      # msg
                sd   s1, 72(sp)      # level
                sd   s2, 64(sp)      # fd
                sd   s3, 56(sp)      # label ptr
                sd   s4, 48(sp)      # label len
                mv   s0, a0
                mv   s1, a1
                # threshold lazy: 0=debug 1=info 2=warn 3=error 4=off
                la   t0, .Llog_threshold
                ld   t1, 0(t0)
                li   t2, -1
                bne  t1, t2, .Llw_have_thresh
                call .Llog_parse_level
                la   t0, .Llog_threshold
                sd   a0, 0(t0)
                mv   t1, a0
            .Llw_have_thresh:
                blt  s1, t1, .Llw_suppressed
                # fd = level >= 2 ? 2(stderr) : 1(stdout)
                li   t0, 2
                bge  s1, t0, .Llw_stderr
                li   s2, 1
                j    .Llw_pick_lbl
            .Llw_stderr:
                li   s2, 2
            .Llw_pick_lbl:
                # escolhe rótulo (s3) + comprimento (s4) — palavras do contrato JVM
                la   s3, .Llog_lbl_info
                li   s4, 4
                beqz s1, .Llw_lbl_debug
                li   t1, 1
                beq  s1, t1, .Llw_ts
                la   s3, .Llog_lbl_warn
                li   t1, 2
                beq  s1, t1, .Llw_ts
                la   s3, .Llog_lbl_error
                li   s4, 5
                j    .Llw_ts
            .Llw_lbl_debug:
                la   s3, .Llog_lbl_debug
                li   s4, 5
            .Llw_ts:
                # timestamp de 23 bytes em sp+0..22 e write(fd, sp, 23)
                mv   a0, sp
                call .Llog_format_ts
                mv   a0, s2
                mv   a1, sp
                li   a2, 23
                call kof_plat_write
                # write(fd, " ", 1)
                mv   a0, s2
                la   a1, .Lstr_space
                li   a2, 1
                call kof_plat_write
                # write(fd, label, s4)
                mv   a0, s2
                mv   a1, s3
                mv   a2, s4
                call kof_plat_write
                # write(fd, " ", 1)
                mv   a0, s2
                la   a1, .Lstr_space
                li   a2, 1
                call kof_plat_write
                # write(fd, msg.data, msg.len)
                beqz s0, .Llw_skip_msg
                lw   a2, 16(s0)
                addi a1, s0, 24
                mv   a0, s2
                call kof_plat_write
            .Llw_skip_msg:
                # newline
                mv   a0, s2
                la   a1, .Lnewline
                li   a2, 1
                call kof_plat_write
            .Llw_suppressed:
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # .Llog_format_ts(a0=destino) — escreve 23 bytes
            # "yyyy-MM-dd HH:mm:ss.SSS" (UTC) a partir de kof_time_now().
            # Conversão civil Hinnant (dias desde epoch -> y/m/d), como o x86.
            # Slots: 40 ms | 36 ss | 32 mi | 28 hh | 24 day | 20 mon | 16 year.
            .Llog_format_ts:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s5, 64(sp)
                mv   s5, a0                  # cursor
                call kof_time_now            # a0 = epoch-ms
                li   t0, 1000
                rem  t1, a0, t0
                sw   t1, 40(sp)              # ms
                div  t1, a0, t0              # epoch sec
                li   t0, 86400
                rem  t2, t1, t0              # sec do dia
                div  t3, t1, t0              # dias
                li   t0, 3600
                div  t4, t2, t0
                sw   t4, 28(sp)              # hh
                rem  t2, t2, t0
                li   t0, 60
                div  t4, t2, t0
                sw   t4, 32(sp)              # mi
                rem  t4, t2, t0
                sw   t4, 36(sp)              # ss
                # z = dias + 719468
                li   t0, 719468
                add  t3, t3, t0
                li   t0, 146097
                div  t4, t3, t0              # era
                rem  t5, t3, t0              # doe
                li   t0, 1460
                div  t6, t5, t0
                li   t0, 36524
                div  a1, t5, t0
                li   t0, 146096
                div  a0, t5, t0
                sub  t6, t5, t6
                add  t6, t6, a1
                sub  t6, t6, a0
                li   t0, 365
                div  t6, t6, t0              # yoe
                li   t0, 400
                mul  a0, t4, t0
                add  a0, a0, t6              # year
                sw   a0, 16(sp)
                # doy = doe - (365*yoe + yoe/4 - yoe/100)
                li   t0, 365
                mul  a1, t6, t0
                srli a2, t6, 2
                add  a1, a1, a2
                li   t0, 100
                div  a2, t6, t0
                sub  a1, a1, a2
                sub  a1, t5, a1              # doy
                # mp = (5*doy + 2)/153
                li   t0, 5
                mul  a2, a1, t0
                addi a2, a2, 2
                li   t0, 153
                div  a3, a2, t0              # mp
                # day = doy - (153*mp+2)/5 + 1
                li   t0, 153
                mul  a2, a3, t0
                addi a2, a2, 2
                li   t0, 5
                div  a2, a2, t0
                sub  a2, a1, a2
                addi a2, a2, 1
                sw   a2, 24(sp)              # day
                # mon = mp + 3 - 12*(mp/10)
                li   t0, 10
                div  a4, a3, t0
                li   t0, 12
                mul  a4, a4, t0
                addi a5, a3, 3
                sub  a5, a5, a4
                sw   a5, 20(sp)              # mon
                li   t0, 2
                bgt  a5, t0, .Lts_yok
                lw   a0, 16(sp)
                addi a0, a0, 1
                sw   a0, 16(sp)
            .Lts_yok:
                # yyyy
                lw   a0, 16(sp)
                li   t0, 1000
                div  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 0(s5)
                rem  a0, a0, t0
                li   t0, 100
                div  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 1(s5)
                rem  a0, a0, t0
                li   t0, 10
                div  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 2(s5)
                rem  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 3(s5)
                li   t0, 45
                sb   t0, 4(s5)               # '-'
                addi s5, s5, 5
                lw   a0, 20(sp)
                call .Lts_put2
                li   t0, 45
                sb   t0, 0(s5)               # '-'
                addi s5, s5, 1
                lw   a0, 24(sp)
                call .Lts_put2
                li   t0, 32
                sb   t0, 0(s5)
                addi s5, s5, 1
                lw   a0, 28(sp)
                call .Lts_put2
                li   t0, 58
                sb   t0, 0(s5)               # ':'
                addi s5, s5, 1
                lw   a0, 32(sp)
                call .Lts_put2
                li   t0, 58
                sb   t0, 0(s5)               # ':'
                addi s5, s5, 1
                lw   a0, 36(sp)
                call .Lts_put2
                li   t0, 46
                sb   t0, 0(s5)               # '.'
                addi s5, s5, 1
                lw   a0, 40(sp)
                call .Lts_put3
                ld   s5, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # .Lts_put2(a0=0..99): escreve 2 dígitos no cursor s5 e avança.
            .Lts_put2:
                li   t0, 10
                div  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 0(s5)
                rem  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 1(s5)
                addi s5, s5, 2
                ret

            # .Lts_put3(a0=0..999): escreve 3 dígitos no cursor s5 e avança.
            .Lts_put3:
                li   t0, 100
                div  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 0(s5)
                rem  a0, a0, t0
                li   t0, 10
                div  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 1(s5)
                rem  t1, a0, t0
                addi t1, t1, 48
                sb   t1, 2(s5)
                addi s5, s5, 3
                ret

            # .Llog_parse_level -> a0 = threshold (0..4); default 1 (info).
            # Lê /proc/self/environ e procura "KOF_LOG_LEVEL=" (valor
            # case-insensitive: DEBUG/Info/WARN/WARNING/ERROR/OFF). Autocontido
            # (sem getenv); espelha o x86 RuntimeLog1.
            # frame: saves em 0..32(sp), buffer de 16 KiB em 64(sp) — offsets
            # pequenos (addi/ld/st riscv são imediatos de 12 bits e estouram
            # com 16 KB). O buffer entra por registrador (sp+64).
            .Llog_parse_level:
                li   t0, 16448
                sub  sp, sp, t0
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                # openat(AT_FDCWD=-100, "/proc/self/environ", O_RDONLY=0, 0)
                li   a0, -100
                la   a1, .Llog_proc_environ
                li   a2, 0
                li   a3, 0
                li   a7, 56
                ecall
                bltz a0, .Lpl_default
                mv   s0, a0                 # fd
                # read(fd, buf=sp+64, 16384)
                mv   a0, s0
                addi a1, sp, 64
                li   a2, 16384
                li   a7, 63
                ecall
                mv   s1, a0                 # n
                # close(fd)
                mv   a0, s0
                li   a7, 57
                ecall
                li   t0, 15
                blt  s1, t0, .Lpl_default
                li   s2, 0                  # i
            .Lpl_scan:
                li   t0, 14
                sub  t0, s1, t0             # n-14
                bge  s2, t0, .Lpl_default
                li   t2, 0                  # j
                la   t3, .Llog_env_name
            .Lpl_cmp:
                li   t0, 14
                bge  t2, t0, .Lpl_found
                add  t4, sp, s2
                add  t4, t4, t2
                addi t4, t4, 64
                lbu  t5, 0(t4)
                add  t6, t3, t2
                lbu  t6, 0(t6)
                bne  t5, t6, .Lpl_advance
                addi t2, t2, 1
                j    .Lpl_cmp
            .Lpl_advance:
                addi s2, s2, 1
                j    .Lpl_scan
            .Lpl_found:
                add  t0, sp, s2
                addi t0, t0, 64
                addi t0, t0, 14             # valor*
                mv   s2, t0
                li   s3, 0                  # len até NUL
            .Lpl_vlen:
                add  t2, s2, s3
                addi t5, sp, 64
                sub  t4, t2, t5
                bge  t4, s1, .Lpl_vdone
                lbu  t2, 0(t2)
                beqz t2, .Lpl_vdone
                addi s3, s3, 1
                j    .Lpl_vlen
            .Lpl_vdone:
                # dispatch pelo comprimento (debug5 info4 warn4 warning7 error5 off3)
                li   t0, 7
                beq  s3, t0, .Lpl_7
                li   t0, 5
                beq  s3, t0, .Lpl_5
                li   t0, 4
                beq  s3, t0, .Lpl_4
                li   t0, 3
                beq  s3, t0, .Lpl_3
                j    .Lpl_default
            .Lpl_7:
                la   a0, .Llog_w_warning
                mv   a1, s2
                mv   a2, s3
                call .Llog_ci_eq
                bnez a0, .Lpl_warn
                j    .Lpl_default
            .Lpl_5:
                la   a0, .Llog_w_debug
                mv   a1, s2
                mv   a2, s3
                call .Llog_ci_eq
                bnez a0, .Lpl_debug
                la   a0, .Llog_w_error
                mv   a1, s2
                mv   a2, s3
                call .Llog_ci_eq
                bnez a0, .Lpl_error
                j    .Lpl_default
            .Lpl_4:
                la   a0, .Llog_w_info
                mv   a1, s2
                mv   a2, s3
                call .Llog_ci_eq
                bnez a0, .Lpl_default
                la   a0, .Llog_w_warn
                mv   a1, s2
                mv   a2, s3
                call .Llog_ci_eq
                bnez a0, .Lpl_warn
                j    .Lpl_default
            .Lpl_3:
                la   a0, .Llog_w_off
                mv   a1, s2
                mv   a2, s3
                call .Llog_ci_eq
                bnez a0, .Lpl_off
                j    .Lpl_default
            .Lpl_debug:
                li   a0, 0
                j    .Lpl_exit
            .Lpl_warn:
                li   a0, 2
                j    .Lpl_exit
            .Lpl_error:
                li   a0, 3
                j    .Lpl_exit
            .Lpl_off:
                li   a0, 4
                j    .Lpl_exit
            .Lpl_default:
                li   a0, 1
            .Lpl_exit:
                ld   s3, 32(sp)
                ld   s2, 24(sp)
                ld   s1, 16(sp)
                ld   s0, 8(sp)
                ld   ra, 0(sp)
                li   t0, 16448
                add  sp, sp, t0
                ret

            # .Llog_ci_eq(a0=candidato lowercase, a1=bytes, a2=len) -> a0=1 se igual
            .Llog_ci_eq:
                li   t0, 0
            .Llog_ci_loop:
                bge  t0, a2, .Llog_ci_yes
                add  t1, a0, t0
                lbu  t2, 0(t1)
                add  t3, a1, t0
                lbu  t4, 0(t3)
                ori  t2, t2, 0x20
                ori  t4, t4, 0x20
                bne  t2, t4, .Llog_ci_no
                addi t0, t0, 1
                j    .Llog_ci_loop
            .Llog_ci_yes:
                li   a0, 1
                ret
            .Llog_ci_no:
                li   a0, 0
                ret

            # ---- kof.config (minimal — retorna default / 0 / false) ----
            .globl kof_config_get
            kof_config_get:
                li   a0, 0
                ret
            .globl kof_config_env
            kof_config_env:
                li   a0, 0
                ret
            .globl kof_config_has
            kof_config_has:
                li   a0, 0
                ret
            .globl kof_config_str
            kof_config_str:
                mv   a0, a1
                ret
            .globl kof_config_int
            kof_config_int:
                mv   a0, a1
                ret
            .globl kof_config_long
            kof_config_long:
                mv   a0, a1
                ret
            .globl kof_config_bool
            kof_config_bool:
                mv   a0, a1
                ret
            .globl kof_config_required
            kof_config_required:
                beqz a0, kof_null_error
                ret

            # ---- kof.time ----
            # kof_time_now() -> epoch-ms (CLOCK_REALTIME). clock_gettime=113
            # (asm-generic, mesma tabela aarch64); paridade com o x86_64 (que
            # usava syscall 96). Antes era stub `li a0,0` → TTL do cache e
            # time.now() quebravam silenciosamente (R6).
            .globl kof_time_now
            kof_time_now:
                addi sp, sp, -32
                sd   ra, 24(sp)
                addi a0, sp, 0              # &timespec {sec@0, nsec@8}
                call kof_plat_time
                ld   t0, 0(sp)              # tv_sec
                ld   t1, 8(sp)              # tv_nsec
                li   t2, 1000
                mul  a0, t0, t2             # sec * 1000
                li   t3, 1000000
                div  t1, t1, t3             # nsec / 1_000_000
                add  a0, a0, t1             # epoch-ms
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .globl kof_time_sleep
            kof_time_sleep:
                # a0 = ms. nanosleep (syscall 101 asm-generic) com timespec
                # {tv_sec=ms/1000, tv_nsec=(ms%1000)*1e6} na stack. Antes era
                # stub `ret` (time.sleep nao dormia no cross — R6).
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                li   t0, 1000
                div  s1, s0, t0            # sec
                rem  t1, s0, t0            # ms%1000
                li   t2, 1000000
                mul  t1, t1, t2            # nsec
                sd   s1, 0(sp)             # timespec.tv_sec
                sd   t1, 8(sp)             # timespec.tv_nsec
                mv   a0, sp                # &ts
                mv   a1, zero              # rem = NULL
                call kof_plat_sleep
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .globl kof_time_interval
            kof_time_interval:
                # TIME001 fechado no cross: mesmo mecanismo do scheduler.every
                # (thread por job, loop com cancel). Aliás (a0=ms, a1=task).
                j    kof_scheduler_every
            .globl kof_time_cancel
            kof_time_cancel:
                j    kof_scheduler_cancel

            # ---- kof.observability (real, minimal para passar KofObservabilityTest) ----
            .globl kof_observability_health
            kof_observability_health:
                la   a0, .Lstr_health
                li   a1, 2
                # tail-call (j): o `call`+`ret` sobrescrevia ra com o ret da
                # própria função → loop infinito. `j` preserva o ra do chamador.
                j    kof_string_from_literal
            .globl kof_observability_readiness
            kof_observability_readiness:
                li   a0, 1
                ret
            .globl kof_observability_liveness
            kof_observability_liveness:
                li   a0, 1
                ret
            .globl kof_observability_counter
            kof_observability_counter:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                la   t0, kof_obs_counter_name
                ld   t1, 0(t0)
                beqz t1, .Loc_counter_new
                mv   a0, t1
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Loc_counter_new
                la   t0, kof_obs_counter_val
                lw   t1, 0(t0)
                addi t1, t1, 1
                sw   t1, 0(t0)
                mv   a0, t1
                j    .Loc_counter_ret
            .Loc_counter_new:
                la   t0, kof_obs_counter_name
                sd   s0, 0(t0)
                la   t0, kof_obs_counter_val
                li   t1, 1
                sw   t1, 0(t0)
                li   a0, 1
            .Loc_counter_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            """;
}
