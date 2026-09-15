package dev.kof.compiler.nat;

// FLT001 (NATIVE002, 15/09): Double/Float -> String no runtime riscv64/aarch64 —
// port fiel do RuntimeDtoa x86_64 consumindo a libc pelo link dinamico sob
// demanda (NativeCrossLink). Contrato §180 preservado:
//   - decimal MAIS CURTO que faz round-trip: loop `%.*e` (prec 0..16 double /
//     0..8 float) + strtod; o 1o prec cujos bits batem vence;
//   - limiar cientifico do Java: cientifico sse |v| < 1e-3 ou >= 1e7;
//   - 'E' maiusculo, expoente sem '+' nem zeros, mantissa sempre com '.';
//   - Float imprime a PROPRIA forma curta; NaN/±Inf normalizados.
//
// ABI cross (convencao da lane: FP trafega como BITS CRUS em registrador
// INTEIRO — B31/B40): `kof_double_to_string(a0=bits)`,
// `kof_float_to_string(a0=low32)`. Isso evita registradores FP no contorno e
// reaproveita o tradutor aarch64 existente. `strtod` RETORNA em fa0 (=f10 na
// ABI riscv; d0 na aarch); lemos com `fmv.x.d rd, fa0` e o tradutor normaliza
// `fa0..fa7` -> f0..f7 (-> d0..d7) — correcao latente que este port exigiu.
//
// varargs libc: riscv passa double vararg em registrador INTEIRO (a4; probe
// 15/09 confirmou `%.*e` com os bits em a4 = 3.140000e+00); aarch64 passa em
// d0. Antes de cada `call snprintf` setamos AMBOS: `a4` (riscv) e `f0`
// (=d0 na aarch) via `fmv.d.x f0, a4`. Cada arch consome o seu; o outro e
// inofensivo. Assim uma UNICA slice serve os dois alvos (regra 5).
//
// Alinhamento: os 2 entry points alinham a pilha a 16 ANTES de chamar libc
// (o Kof empilha 8/8). kof_dtoa_format assume sp ja alinhada e mantem (frames
// multiplos de 16). aarch64 herda via tradutor.
//
// Requer libc (snprintf/strtod): um programa que imprime FP linka DINAMICO
// (needsLibc detecta `call snprintf`). Programas sem FP seguem estaticos.
public final class NativeRiscvAsmRtB45 {

    private NativeRiscvAsmRtB45() {}

    static final String RISCV_RUNTIME_ASM_B_45 = """
            .section .rodata
            .Ldtf_fmt_sci:  .asciz "%.*e"
            .Ldtf_fmt_fix:  .asciz "%.*f"
            .Ldtf_str_inf:  .asciz "Infinity"
            .Ldtf_str_ninf: .asciz "-Infinity"
            .Ldtf_str_nan:  .asciz "NaN"

            .section .text
            # kof_dtoa_format(a0=buf1, a1=buf2, a2=prec, a3=value_bits) -> a0=len
            # buf1 = saida de `%.*e` ("d[.ddd]e±XX"); buf2 >= 64 bytes. Reformata
            # ao estilo Java. NAO usa registrador FP (o double so vai como vararg
            # inteiro a4 / d0 no snprintf `%.*f`).
            .globl kof_dtoa_format
            kof_dtoa_format:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   a3, 16(sp)          # value bits
                mv   s0, a0              # buf1
                mv   s1, a1              # buf2
                mv   s2, a2              # prec
                mv   t0, s0
            .Ldtf_finde:
                lbu  t1, 0(t0)
                beqz t1, .Ldtf_raw
                li   t2, 101             # 'e'
                beq  t1, t2, .Ldtf_ate
                addi t0, t0, 1
                j    .Ldtf_finde
            .Ldtf_ate:
                addi t0, t0, 1           # pula 'e'
                li   s3, 0               # exp
                li   s4, 0               # neg
                lbu  t1, 0(t0)
                li   t2, 45              # '-'
                bne  t1, t2, .Ldtf_ate_p
                li   s4, 1
                addi t0, t0, 1
                j    .Ldtf_ated
            .Ldtf_ate_p:
                li   t2, 43              # '+'
                bne  t1, t2, .Ldtf_ated
                addi t0, t0, 1
            .Ldtf_ated:
                lbu  t1, 0(t0)
                li   t2, 48
                bltu t1, t2, .Ldtf_ated_done
                li   t2, 57
                bltu t2, t1, .Ldtf_ated_done
                li   t2, 10
                mul  s3, s3, t2
                addi t1, t1, -48
                add  s3, s3, t1
                addi t0, t0, 1
                j    .Ldtf_ated
            .Ldtf_ated_done:
                beqz s4, .Ldtf_decide
                neg  s3, s3
            .Ldtf_decide:
                li   t2, -3
                blt  s3, t2, .Ldtf_sci
                li   t2, 7
                blt  s3, t2, .Ldtf_plain
                j    .Ldtf_sci
            .Ldtf_plain:
                sub  t3, s2, s3          # frac = prec - exp
                bgez t3, .Ldtf_plain_p
                li   t3, 0
            .Ldtf_plain_p:
                mv   a0, s1
                li   a1, 64
                la   a2, .Ldtf_fmt_fix
                mv   a3, t3
                ld   a4, 16(sp)
                fmv.d.x f0, a4           # vararg double: riscv=a4, aarch=d0
                call snprintf
                mv   t0, s1
                li   s5, 0               # len
                li   t4, 0               # hasdot
            .Ldtf_p_scan:
                add  t1, t0, s5
                lbu  t2, 0(t1)
                beqz t2, .Ldtf_p_end
                li   t3, 46              # '.'
                bne  t2, t3, .Ldtf_p_next
                li   t4, 1
            .Ldtf_p_next:
                addi s5, s5, 1
                j    .Ldtf_p_scan
            .Ldtf_p_end:
                bnez t4, .Ldtf_ret
                add  t1, t0, s5
                li   t2, 46
                sb   t2, 0(t1)
                li   t2, 48
                sb   t2, 1(t1)
                sb   zero, 2(t1)
                addi s5, s5, 2
                j    .Ldtf_ret
            .Ldtf_sci:
                mv   t0, s0              # src
                li   s5, 0               # len
                li   t4, 0               # hasdot
            .Ldtf_s_copy:
                lbu  t1, 0(t0)
                beqz t1, .Ldtf_s_mant_end
                li   t2, 101
                beq  t1, t2, .Ldtf_s_mant_end
                li   t2, 46
                bne  t1, t2, .Ldtf_s_copy1
                li   t4, 1
            .Ldtf_s_copy1:
                add  t2, s1, s5
                sb   t1, 0(t2)
                addi s5, s5, 1
                addi t0, t0, 1
                j    .Ldtf_s_copy
            .Ldtf_s_mant_end:
                bnez t4, .Ldtf_s_E
                add  t2, s1, s5
                li   t1, 46
                sb   t1, 0(t2)
                addi s5, s5, 1
                add  t2, s1, s5
                li   t1, 48
                sb   t1, 0(t2)
                addi s5, s5, 1
            .Ldtf_s_E:
                add  t2, s1, s5
                li   t1, 69              # 'E'
                sb   t1, 0(t2)
                addi s5, s5, 1
                bgez s3, .Ldtf_s_eabs
                add  t2, s1, s5
                li   t1, 45
                sb   t1, 0(t2)
                addi s5, s5, 1
                neg  s3, s3
            .Ldtf_s_eabs:
                mv   t0, sp              # temp (digitos em ordem inversa)
                li   t3, 0               # count
                mv   t1, s3
                li   t4, 10
                bnez t1, .Ldtf_s_ediv
                li   t2, 48
                sb   t2, 0(t0)
                li   t3, 1
                j    .Ldtf_s_erev
            .Ldtf_s_ediv:
                remu t2, t1, t4
                addi t2, t2, 48
                add  t5, t0, t3
                sb   t2, 0(t5)
                addi t3, t3, 1
                divu t1, t1, t4
                bnez t1, .Ldtf_s_ediv
            .Ldtf_s_erev:
                addi t3, t3, -1
            .Ldtf_s_erev1:
                add  t5, t0, t3
                lbu  t1, 0(t5)
                add  t2, s1, s5
                sb   t1, 0(t2)
                addi s5, s5, 1
                addi t3, t3, -1
                bgez t3, .Ldtf_s_erev1
                add  t2, s1, s5
                sb   zero, 0(t2)
                j    .Ldtf_ret
            .Ldtf_raw:
                mv   t0, s0
                li   s5, 0
            .Ldtf_raw_l:
                add  t1, t0, s5
                lbu  t2, 0(t1)
                beqz t2, .Ldtf_raw_e
                add  t3, s1, s5
                sb   t2, 0(t3)
                addi s5, s5, 1
                j    .Ldtf_raw_l
            .Ldtf_raw_e:
                add  t3, s1, s5
                sb   zero, 0(t3)
            .Ldtf_ret:
                mv   a0, s5
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # kof_double_to_string(a0=value_bits) -> a0 = String*
            .globl kof_double_to_string
            kof_double_to_string:
                mv   t3, sp
                addi sp, sp, -256
                andi sp, sp, -16
                sd   t3, 240(sp)         # sp original
                sd   ra, 248(sp)
                sd   s0, 232(sp)
                sd   s1, 224(sp)
                sd   s2, 216(sp)
                sd   s3, 208(sp)
                sd   s4, 200(sp)
                sd   s5, 192(sp)
                mv   s0, a0              # bits
                srli t0, s0, 52
                li   t1, 0x7ff
                and  t0, t0, t1
                beq  t0, t1, .Ld2s_naninf
                li   s1, 0               # prec
            .Ld2s_loop:
                mv   a0, sp
                li   a1, 64
                la   a2, .Ldtf_fmt_sci
                mv   a3, s1
                mv   a4, s0
                fmv.d.x f0, a4
                call snprintf
                mv   a0, sp
                li   a1, 0
                call strtod
                fmv.x.d t0, fa0
                beq  t0, s0, .Ld2s_fmt
                addi s1, s1, 1
                li   t0, 17
                blt  s1, t0, .Ld2s_loop
                li   s1, 16
                mv   a0, sp
                li   a1, 64
                la   a2, .Ldtf_fmt_sci
                mv   a3, s1
                mv   a4, s0
                fmv.d.x f0, a4
                call snprintf
            .Ld2s_fmt:
                mv   a0, sp
                addi a1, sp, 64
                mv   a2, s1
                mv   a3, s0
                call kof_dtoa_format
                mv   a1, a0
                addi a0, sp, 64
                call kof_string_from_literal
                j    .Ld2s_done
            .Ld2s_naninf:
                slli t0, s0, 12
                bnez t0, .Ld2s_nan
                bltz s0, .Ld2s_ninf
                la   a0, .Ldtf_str_inf
                li   a1, 8
                call kof_string_from_literal
                j    .Ld2s_done
            .Ld2s_ninf:
                la   a0, .Ldtf_str_ninf
                li   a1, 9
                call kof_string_from_literal
                j    .Ld2s_done
            .Ld2s_nan:
                la   a0, .Ldtf_str_nan
                li   a1, 3
                call kof_string_from_literal
            .Ld2s_done:
                ld   t3, 240(sp)
                ld   ra, 248(sp)
                ld   s0, 232(sp)
                ld   s1, 224(sp)
                ld   s2, 216(sp)
                ld   s3, 208(sp)
                ld   s4, 200(sp)
                ld   s5, 192(sp)
                mv   sp, t3
                ret

            # kof_float_to_string(a0=low32 bits) -> a0 = String*
            .globl kof_float_to_string
            kof_float_to_string:
                mv   t3, sp
                addi sp, sp, -256
                andi sp, sp, -16
                sd   t3, 240(sp)
                sd   ra, 248(sp)
                sd   s0, 232(sp)
                sd   s1, 224(sp)
                sd   s2, 216(sp)
                sd   s3, 208(sp)
                sd   s4, 200(sp)
                sd   s5, 192(sp)
                slli s0, a0, 32
                srli s0, s0, 32          # bits low32 (zero-extended)
                srli t0, s0, 23
                andi t0, t0, 0xff
                li   t1, 0xff
                beq  t0, t1, .Lf2s_naninf
                li   s1, 0               # prec
            .Lf2s_loop:
                # promove o float a double (f0) e pega os bits para o vararg
                fmv.w.x f0, s0
                fcvt.d.s f0, f0
                fmv.x.d a4, f0
                mv   a0, sp
                li   a1, 64
                la   a2, .Ldtf_fmt_sci
                mv   a3, s1
                call snprintf
                mv   a0, sp
                li   a1, 0
                call strtod
                fcvt.s.d f0, fa0
                fmv.x.w t0, f0
                slli t0, t0, 32
                srli t0, t0, 32
                beq  t0, s0, .Lf2s_fmt
                addi s1, s1, 1
                li   t0, 9
                blt  s1, t0, .Lf2s_loop
                li   s1, 8
                fmv.w.x f0, s0
                fcvt.d.s f0, f0
                fmv.x.d a4, f0
                mv   a0, sp
                li   a1, 64
                la   a2, .Ldtf_fmt_sci
                mv   a3, s1
                call snprintf
            .Lf2s_fmt:
                fmv.w.x f0, s0
                fcvt.d.s f0, f0
                fmv.x.d s2, f0
                mv   a0, sp
                addi a1, sp, 64
                mv   a2, s1
                mv   a3, s2
                call kof_dtoa_format
                mv   a1, a0
                addi a0, sp, 64
                call kof_string_from_literal
                j    .Lf2s_done
            .Lf2s_naninf:
                li   t1, 0x7fffff
                and  t0, s0, t1          # mantissa low23 (32-bit shift truncaria;
                bnez t0, .Lf2s_nan       # s0 << 9 no RV64 manteria os bits do exp)
                srli t0, s0, 31
                bnez t0, .Lf2s_ninf
                la   a0, .Ldtf_str_inf
                li   a1, 8
                call kof_string_from_literal
                j    .Lf2s_done
            .Lf2s_ninf:
                la   a0, .Ldtf_str_ninf
                li   a1, 9
                call kof_string_from_literal
                j    .Lf2s_done
            .Lf2s_nan:
                la   a0, .Ldtf_str_nan
                li   a1, 3
                call kof_string_from_literal
            .Lf2s_done:
                ld   t3, 240(sp)
                ld   ra, 248(sp)
                ld   s0, 232(sp)
                ld   s1, 224(sp)
                ld   s2, 216(sp)
                ld   s3, 208(sp)
                ld   s4, 200(sp)
                ld   s5, 192(sp)
                mv   sp, t3
                ret
            """;
}
