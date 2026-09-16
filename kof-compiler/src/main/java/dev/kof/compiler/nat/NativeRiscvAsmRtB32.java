package dev.kof.compiler.nat;

// FASE (STDLIB S1b/MATH001): fatia 32 de RISCV_RUNTIME_ASM_B — kof.math Double.
// Port do x86 RuntimeMath (S1b sqrt + S1b.1 lerp/percentage/isInteger/
// isDecimal) p/ riscv64; aarch64 herda via translateRiscvToAarch64 (fsqrt.d
// -> fsqrtd adicionado ao tradutor neste commit).
//
// Convenção: args em a0/a1/a2 (bits crus IEEE 64-bit — MESMO caminho genérico
// do NativeRiscvCrossOps.emitCrossCallRiscv, que popula a0..aN e push(a0) o
// retorno); retorno = bits crus em a0 (o caller faz pushRiscv a0). FP scratch
// f0..f2; int scratch t0..t3 (caller-saved — safe, sem frame). Sem .rodata de
// texto (percentage reusa o padrão B27: .quad em .rodata + la/fld).
//
// Paridade byte-level com o x86 (RuntimeMath) e o JVM (JvmStringMathRuntime):
//   sqrt(v)        = IEEE sqrt (NaN p/ v<0 — Math.sqrt paridade)
//   lerp(a,b,t)    = a + (b-a)*t  (mesma ordem de sub/mul/add)
//   percentage(p,t)= p/t*100.0    (div-then-mul; 100.0 = .Lmth_pct100)
//   isInteger(v)   = exp==0x7ff(NaN/Inf)->false; exp>=0x433(|v|>=2^52 finito)
//                    ->true; senão feq(v, trunc32(v))
//   isDecimal(v)   = !isInteger(v)
public final class NativeRiscvAsmRtB32 {

    static  String RISCV_RUNTIME_ASM_B_32 = """

            .section .text

            # ── kof.math Double (STDLIB S1b + S1b.1 — MATH001) ─────────

            # kof_math_sqrt(a0=v) -> f0=bits, a0=bits
            .globl kof_math_sqrt
            kof_math_sqrt:
                fmv.d.x f0, a0
                fsqrt.d f0, f0
                fmv.x.d a0, f0
                ret

            # kof_math_lerp(a0=a, a1=b, a2=t) -> a0=bits de a+(b-a)*t
            .globl kof_math_lerp
            kof_math_lerp:
                fmv.d.x f0, a0             # a
                fmv.d.x f1, a1             # b
                fmv.d.x f2, a2             # t
                fsub.d f1, f1, f0          # b-a
                fmul.d f1, f1, f2          # (b-a)*t
                fadd.d f0, f0, f1          # a+...
                fmv.x.d a0, f0
                ret

            # kof_math_percentage(a0=part, a1=total) -> a0=bits de part/total*100
            .globl kof_math_percentage
            kof_math_percentage:
                fmv.d.x f0, a0             # part
                fmv.d.x f1, a1             # total
                fdiv.d f0, f0, f1          # part/total
                la   t0, .Lmth_pct100
                fld  f1, 0(t0)
                fmul.d f0, f0, f1          # *100.0
                fmv.x.d a0, f0
                ret

            # kof_math_isInteger(a0=v) -> a0=1/0
            # exp bits [63:52]: 0x7ff = NaN/Inf -> 0; >=0x433 (|v|>=2^52,
            # finito) -> 1; senão trunc p/ int64 e de volta a double, feq.
            # (FCVT.L.D 64-bit — igual o cvttsd2si %rdx do x86; FCVT.W.D
            # saturaria em 2^31 e quebraria inteiros como 1e10.)
            .globl kof_math_isInteger
            kof_math_isInteger:
                mv   t0, a0
                srli t1, t0, 52
                andi t1, t1, 0x7ff
                li   t2, 0x7ff
                beq  t1, t2, .Lmth_ii_false
                li   t2, 0x433
                bgeu t1, t2, .Lmth_ii_true
                fmv.d.x f0, t0
                fcvt.l.d t0, f0, rtz
                fcvt.d.l f1, t0
                feq.d t0, f0, f1
                beqz t0, .Lmth_ii_false
            .Lmth_ii_true:
                li   a0, 1
                ret
            .Lmth_ii_false:
                li   a0, 0
                ret

            # kof_math_isDecimal(a0=v) -> a0=1/0 (negação do isInteger —
            # seqz p/ 0/1; xori nao existe no tradutor aarch).
            # LIÇÃO isWeekend/B14: wrapper que faz `call` DEVE salvar ra
            # (jalr do call sobrescreve ra — o ret do wrapper voltaria ao
            # próprio corpo = loop infinito; provado no trace qemu 11/09).
            .globl kof_math_isDecimal
            kof_math_isDecimal:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_math_isInteger
                ld   ra, 8(sp)
                addi sp, sp, 16
                seqz a0, a0
                ret

            # S1b.3 (DECISIONS §3): kof_math_roundTo(a0=v bits, a1=decimals)
            # -> a0=bits. Half-away-from-zero por escala decimal determinística
            # (sem libm). p = 10^m (m=|d|, satura 308) por fmul.d REPETIDO —
            # byte-idêntico ao x86/JVM/JS. d>=0: scaled=v*p, r/p. d<0:
            # scaled=v/p, r*p. Overflow de v*p -> devolve v. Inline (sem
            # wrapper/call → não precisa salvar ra).
            .globl kof_math_roundTo
            kof_math_roundTo:
                sext.w a1, a1                    # normaliza decimals p/ 64-bit
                # v NaN/Inf (exp==0x7ff) -> devolve v
                srli t0, a0, 52
                andi t0, t0, 0x7ff
                li   t1, 0x7ff
                beq  t0, t1, .Lmth_rt_ret
                # m = min(|d|, 308)
                mv   t2, a1
                bgez t2, .Lmth_rt_abs
                neg  t2, t2
            .Lmth_rt_abs:
                li   t1, 308
                ble  t2, t1, .Lmth_rt_m_ok
                li   t2, 308
            .Lmth_rt_m_ok:
                # p = 10^m
                la   t0, .Lmth_one
                fld  f1, 0(t0)
                la   t3, .Lmth_ten
                fld  f2, 0(t3)
                beqz t2, .Lmth_rt_p_done
            .Lmth_rt_p_loop:
                fmul.d f1, f1, f2
                addi t2, t2, -1
                bnez t2, .Lmth_rt_p_loop
            .Lmth_rt_p_done:
                fmv.d.x f0, a0
                bltz a1, .Lmth_rt_negd
                fmul.d f0, f0, f1                # scaled = v*p
                j    .Lmth_rt_scale
            .Lmth_rt_negd:
                fdiv.d f0, f0, f1                # scaled = v/p
            .Lmth_rt_scale:
                fmv.x.d t0, f0
                srli t1, t0, 52
                andi t1, t1, 0x7ff
                li   t2, 0x7ff
                beq  t1, t2, .Lmth_rt_ret        # overflow -> v
                li   t2, 0x433
                bgeu t1, t2, .Lmth_rt_apply      # |scaled|>=2^52 -> r=scaled
                # r = half-away-from-zero(scaled)
                fcvt.l.d t3, f0, rtz             # t = trunc
                fcvt.d.l f3, t3                  # (double)t
                fsub.d f4, f0, f3                # frac = scaled - t
                la   t0, .Lmth_half
                fld  f5, 0(t0)
                flt.d t1, f4, f5                 # frac < 0.5 ?
                beqz t1, .Lmth_rt_up             # !(frac<0.5) -> frac>=0.5
                la   t0, .Lmth_nhalf
                fld  f5, 0(t0)
                flt.d t1, f5, f4                 # -0.5 < frac ?
                bnez t1, .Lmth_rt_r_done         # frac in (-0.5,0.5) -> r=t
                addi t3, t3, -1                  # frac <= -0.5 -> t-1
                j    .Lmth_rt_r_done
            .Lmth_rt_up:
                addi t3, t3, 1
            .Lmth_rt_r_done:
                fcvt.d.l f0, t3
            .Lmth_rt_apply:
                bltz a1, .Lmth_rt_mulback
                fdiv.d f0, f0, f1                # r/p
                j    .Lmth_rt_fin
            .Lmth_rt_mulback:
                fmul.d f0, f0, f1                # r*p
            .Lmth_rt_fin:
                fmv.x.d a0, f0
                ret
            .Lmth_rt_ret:
                ret

            .section .rodata
            .align 3
            .Lmth_pct100:
                .quad 0x4059000000000000
            .Lmth_one:
                .quad 0x3ff0000000000000
            .Lmth_ten:
                .quad 0x4024000000000000
            .Lmth_half:
                .quad 0x3fe0000000000000
            .Lmth_nhalf:
                .quad 0xbfe0000000000000
            .section .text
        """;
}
