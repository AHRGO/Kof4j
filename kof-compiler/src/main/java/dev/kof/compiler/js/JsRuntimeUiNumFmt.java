package dev.kof.compiler.js;

/**
 * Runtime JS (§264) — formatação de Double/Float no contrato do JDK
 * (`Double.toString`/`Float.toString`), igual ao JVM e ao Native (bug 44/§180).
 *
 * O JS nativo (`String(4.0)`) imprime `4`; o contrato Kof em todos os alvos é
 * o do JDK: inteiro com ponto (`4.0`), ponto decimal curto, e notação
 * científica com `E` maiúsculo + expoente sem leading-zero (`1.0E7`, `1.0E-7`).
 * Também o zero assinado (`-0.0`). O gap era SILENCIOSO (R6): `println`,
 * `print`, concatenação e `String.valueOf` de Double/Float divergiam do JVM
 * sem diagnóstico. Este slice fecha o lado display; `d.toString()`/coleções
 * que já passaram por `String.valueOf` caem aqui.
 */
final class JsRuntimeUiNumFmt {

    private JsRuntimeUiNumFmt() {}

    static  String NUM_FMT_RUNTIME = """
            // ── §264: Double/Float → string no formato do JDK ─────────────
            // Regra Double.toString: significante com round-trip mínimo,
            // decimal quando -3 <= exp < 7, senão E±m sem leading-zero.
            // Sempre ao menos um dígito fracionário (4 → "4.0").
            function kofFpToString(v, isFloat) {
                if (v === null || v === undefined) return "null";   // Double? null (paridade JVM "null")
                if (Number.isNaN(v)) return "NaN";
                if (v === Infinity) return "Infinity";
                if (v === -Infinity) return "-Infinity";
                if (v === 0) return Object.is(v, -0) ? "-0.0" : "0.0";
                const d = isFloat ? Math.fround(v) : v;
                const neg = d < 0;
                const a = Math.abs(d);
                // round-trip mínimo: menor precisão que reconverte p/ o mesmo valor
                let sig = null, exp = 0;
                // Float: round-trip na PRECISAO do float (max 9, fround na
                // volta) — senao `1.0f/3.0f` imprimiria a expansao double
                // (mesmo bug do §180 no Native). Double: ate 17.
                const maxP = isFloat ? 9 : 17;
                for (let p = 1; p <= maxP; p++) {
                    const c = a.toExponential(p - 1);   // "s.ffffffe+XX"
                    const back = isFloat ? Math.fround(Number(c)) : Number(c);
                    if (back === a) {
                        const parts = c.split("e");
                        sig = parts[0].replace(".", "").replace(/0+$/, "");
                        exp = parseInt(parts[1], 10);
                        break;
                    }
                }
                if (sig === null) { sig = String(a); exp = 0; }
                const lead = neg ? "-" : "";
                let out;
                if (exp >= -3 && exp < 7) {
                    if (exp >= 0) {
                        if (sig.length > exp + 1) {
                            out = sig.slice(0, exp + 1) + "." + sig.slice(exp + 1);
                        } else {
                            out = sig + "0".repeat(exp + 1 - sig.length) + ".0";
                        }
                    } else {
                        out = "0." + "0".repeat(-exp - 1) + sig;
                    }
                } else {
                    const m = sig.length > 1 ? sig.slice(0, 1) + "." + sig.slice(1) : sig + ".0";
                    out = m + "E" + exp;
                }
                return lead + out;
            }
            export function kofNumFmt(v, isFloat) {
                if (v === null || v === undefined) return "null";
                return kofFpToString(Number(v), isFloat);
            }
            """;
}
