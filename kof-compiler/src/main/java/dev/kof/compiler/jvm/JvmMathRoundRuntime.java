package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S1b.3, DECISIONS §3).
 * kof.math: roundTo(value, decimals) — half-away-from-zero por escala decimal
 * determinística, sem libm. Extraído de JvmStringMathRuntime (gate ≤500);
 * concatenado em JvmStringRuntime.source() (métodos em classe única — ordem
 * é semanticamente invariante; sem golden byte-a-byte do fragmento).
 */
public final class JvmMathRoundRuntime {

    private JvmMathRoundRuntime() {}

    static String source() {
        return """
                // S1b.3 (DECISIONS §3): roundTo(value, decimals) — half-away-
                // from-zero por escala decimal determinística. SEM libm/BigDecimal:
                // p = 10^m (m=|d|, saturado em 308) por multiplicação REPETIDA
                // (cada passo é 1 op IEEE corretamente arredondada → byte-idêntico
                // nos 5 alvos). d>=0: scaled=v*p, r/p. d<0: scaled=v/p (encolhe),
                // r*p. Overflow de v*p → devolve v (no-op). Contrato ARITMÉTICO
                // (NÃO decimal-string): roundTo(2.675, 2) == 2.68 (o double
                // 2.675*100 arredonda a 267.5).
                public static double kof_math_roundTo(double v, int decimals) {
                    if (Double.isNaN(v) || Double.isInfinite(v)) return v;
                    int m = decimals < 0 ? -decimals : decimals;
                    if (m > 308) m = 308;
                    double p = 1.0;
                    for (int i = 0; i < m; i++) p *= 10.0;
                    if (decimals >= 0) {
                        double scaled = v * p;
                        if (Double.isInfinite(scaled)) return v;
                        return kofMathRoundHalfAway(scaled) / p;
                    }
                    double scaled = v / p;
                    if (Double.isInfinite(scaled)) return v;
                    return kofMathRoundHalfAway(scaled) * p;
                }

                // half-away-from-zero: trunc + correção do resto (|resto|>=0.5
                // → ±1). |x|>=2^52 já é inteiro (retorna x); NaN/Inf idem (exp
                // 0x7ff >= 0x433). Evita o double-rounding do floor(x+0.5)
                // (0.49999999999999994 → 0, não 1). Mesma fórmula no asm.
                private static double kofMathRoundHalfAway(double x) {
                    if (x >= 4503599627370496.0 || x <= -4503599627370496.0) return x;
                    double t = (double) (long) x;
                    double f = x - t;
                    if (f >= 0.5) t += 1.0;
                    else if (f <= -0.5) t -= 1.0;
                    return t;
                }
                """;
    }
}
