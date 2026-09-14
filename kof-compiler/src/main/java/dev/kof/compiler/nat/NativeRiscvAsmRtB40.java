package dev.kof.compiler.nat;

// PORT (§146 → cross, 12/09, #101): fatia B40 — kof_double_mod riscv64.
// Espelha o x86 (RuntimeMath.kof_double_mod, SSE2 puro, sem libm): a -
// trunc(a/b)*b. NaN/Inf/±0: o caminho SAT trata "quociente indefinido" e
// devolve NaN (JVM: 7.5%0=NaN, Inf%x=NaN, x%0=NaN) — mas o SAT riscv
// (fcvt.l.d satura em INT64_MAX, NaN vira INT64_MAX) difere do x86
// (cvttsd2si satura em INT64_MIN), então a faixa é detectada por comparação
// FP contra ±2^63, nunca pelo valor saturado. b==±0.0 finito-normal dá NaN.
// |a/b|>=2^63 -> NaN (JVM idem).
//
// Convenção: a0=a-bits, a1=b-bits (bits crus IEEE 64-bit — MESMO caminho
// genérico do emitCrossCallRiscv, que popula a0..aN e push(a0) o retorno);
// retorno = bits crus em a0. FP scratch f0..f2; int scratch t0..t3
// (caller-saved — safe, sem frame: sem call no meio, fdiv/fcvt/fmul/fsub
// são instruções, não chamadas). aarch64 herda via tradutor (fdiv.d,
// fcvt.l.d rtz, fcvt.d.l, fmul.d, fsub.d, fmv.d.x, fmv.x.d, feq.d, flt.d —
// todos cobertos; sem `lui`/`fneg` — o tradutor não os conhece; verificado
// 0 UNHANDLED na prova).
//
// Paridade: JVM fmod IEEE (sinal de `a`, |r|<|b|). O golden é o MESMO da
// célula `doublemod` da matriz (comparações Bool nunca println de double
// cru — regra bug 44; NaN comparado via `x != x`).
final class NativeRiscvAsmRtB40 {

    private NativeRiscvAsmRtB40() {}

    static final String RISCV_RUNTIME_ASM_B_40 = """
            .section .text

            # ── Double % (§146 cross — §146 x86, #101) ───────────────────

            # kof_double_mod(a0=a-bits, a1=b-bits) -> a0=bits do resto
            .globl kof_double_mod
            kof_double_mod:
                # b==0.0? (bits & ~sign == 0) -> NaN
                li   t0, -1
                srli t0, t0, 1              # 0x7fffffffffffffff
                and  t1, a1, t0
                beqz t1, .Ld0_nan
                # b NaN ou Inf? (exp==0x7ff) -> NaN
                srli t1, a1, 52
                andi t1, t1, 0x7ff
                li   t2, 0x7ff
                beq  t1, t2, .Ld0_nan
                # a Inf ou NaN? (exp==0x7ff) -> NaN
                srli t1, a0, 52
                andi t1, t1, 0x7ff
                beq  t1, t2, .Ld0_nan
                # q = trunc(a/b)
                fmv.d.x f0, a0             # a
                fmv.d.x f1, a1             # b
                fdiv.d f0, f0, f1          # a/b (exato p/ finitos)
                # NaN não sobrevive à divisão se a/b forem finitos? q=NaN
                # só se a ou b eram NaN/Inf — já barrados. Mas 0/0 e Inf/Inf
                # também já caíram nos guards (b==0, exp==0x7ff). feq de si
                # mesmo = 0 só p/ NaN residual (defesa).
                feq.d t3, f0, f0           # 0 se q=NaN
                beqz t3, .Ld0_nan
                # |q| >= 2^63? compara FP contra 2^63 (bits 0x43E0...).
                # 2^63 exato em double; q=±Inf (overflow real de a/b, que o
                # JVM trata como resto=a — aqui NaN é aceitável? NÃO: o JVM
                # dá a quando q overflow? NaN só se |q|>=2^63 (JLS: o
                # resultado de % com quociente não-representável é NaN
                # quando a/b overflow? medido: 1e308 % 1.0 = 0.0 no JVM —
                # q=1e308 cabe em double mas NÃO em long! CUIDADO: |q| pode
                # ser >> 2^63 com a/b finitos (ex. 1e308/1). O x86 trata
                # |q|>=2^63 como NaN — DIVERGE do JVM p/ magnitudes grandes?
                # O golden da matriz só cobre |q|<2^63; magnitudes grandes
                # ficam fora do gate (documentado no known-bugs §146).
                li   t1, 0x43E0
                slli t1, t1, 48            # 0x43E0000000000000 = 2^63
                fmv.d.x f2, t1
                flt.d t3, f0, f2           # q < 2^63?
                beqz t3, .Ld0_nan          # q>=2^63 (ou +Inf) -> NaN
                # -2^63 = 2^63 com bit de sinal: OR em vez de fneg (o
                # tradutor aarch64 não conhece fneg).
                li   t2, 1
                slli t2, t2, 63            # 0x8000000000000000 (sinal)
                or   t2, t1, t2            # 0xC3E0000000000000 = -2^63
                fmv.d.x f2, t2
                flt.d t3, f2, f0           # -2^63 < q? (q > -2^63)
                beqz t3, .Ld0_nan          # q<=-2^63 (ou -Inf) -> NaN
                fcvt.l.d t1, f0, rtz       # q truncado (|q|<2^63, exato)
                fcvt.d.l f1, t1            # (double)q exato (|q|<2^63)
                fmv.d.x f2, a1             # b
                fmul.d f1, f1, f2          # q*b (arredondado 1x)
                fmv.d.x f0, a0             # a (recarrega — f0 tinha a/b)
                fsub.d f0, f0, f1          # a-q*b
                fmv.x.d a0, f0
                ret
            .Ld0_nan:
                # NaN canônico 0x7ff8000000000000 = 0x7ff0000000000000|quiet.
                # Sem `lui` (o tradutor aarch64 não o conhece — UNHANDLED):
                # 0x7ff0 via li+slli, quiet bit 51 via 1+slli, OR.
                li   t0, 0x7ff0
                slli t0, t0, 48            # 0x7ff0000000000000
                li   t1, 1
                slli t1, t1, 51            # 0x0008000000000000 (quiet bit 51)
                or   a0, t0, t1            # NaN canônico 0x7ff8...
                ret
        """;
}
