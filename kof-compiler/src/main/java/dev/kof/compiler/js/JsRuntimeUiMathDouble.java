package dev.kof.compiler.js;

/**
 * Runtime JS (STDLIB S1b.1) — escalares Double de kof.math
 * (lerp/percentage/isInteger/isDecimal). Fragmento próprio (gate ≤500:
 * JsRuntimeUiStdlib já estava acima). Ordem de append livre (ESM).
 */
final class JsRuntimeUiMathDouble {

    private JsRuntimeUiMathDouble() {}

    static final String MATH_DOUBLE_RUNTIME = """
            // ── kof.math S1b.1 — Double puros (paridade JVM/Native) ─────
            // Bool = 1/0 (chokepoint §93 cuida do ==true).
            // ── §181 (13/09): casts Double/Float -> Int/Long SATURANTES
            // (JLS 5.1.3 — NaN => 0, > MAX => MAX, < MIN => MIN). O
            // Math.trunc cru dava 3e9 (fora do Int32), NaN e Infinity.
            // Long = BigInt saturado (paridade 64-bit do §81).
            export function kofD2I(v) {
                if (isNaN(v)) return 0;
                const t = Math.trunc(v);
                if (t > 2147483647) return 2147483647;
                if (t < -2147483648) return -2147483648;
                return t;
            }
            export function kofD2L(v) {
                if (isNaN(v)) return 0n;
                const t = Math.trunc(v);
                if (t > 9223372036854775807) return 9223372036854775807n;
                if (t < -9223372036854775808) return -9223372036854775808n;
                return BigInt(t);
            }
            export function kofF2I(v) { return kofD2I(v); }
            export function kofF2L(v) { return kofD2L(v); }
            export function kofMathLerp(a, b, t) { return a + (b - a) * t; }
            export function kofMathPercentage(p, tot) { return p / tot * 100.0; }
            export function kofMathPow(base, exp) { return Math.pow(base, exp); }
            export function kofMathIsInteger(v) {
                return (v === Math.floor(v) && v !== Infinity && v !== -Infinity) ? 1 : 0;
            }
            export function kofMathIsDecimal(v) {
                return !(v === Math.floor(v) && v !== Infinity && v !== -Infinity) ? 1 : 0;
            }
            """;
}
