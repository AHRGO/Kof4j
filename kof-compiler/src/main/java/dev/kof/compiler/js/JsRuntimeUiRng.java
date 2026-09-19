package dev.kof.compiler.js;

/**
 * Runtime JS do kof.rng (X8 fatia 1 — PRNG semeável, xorshift128 +
 * splitmix32). MESMA aritmética do fragmento JVM (JvmStringRngRuntime):
 * só xor/shift/add/mul com wrap int32 (| 0 e Math.imul), então JVM e JS
 * produzem bits IDÊNTICOS para a mesma seed — paridade por construção,
 * provada em KofRngTest (mesma sequência impressa nos dois backends).
 *
 * <p>NUNCA para chaves/segredos (isso é kof_platform/crypto, R11); rng é
 * determinismo reprodutível (R10, property-based testing).
 */
public final class JsRuntimeUiRng {

    private JsRuntimeUiRng() {}

    static String RNG_RUNTIME = """

            // ── kof.rng (X8 fatia 1) — xorshift128 + splitmix32, 32-bit ──
            // Estado default fixo (0,0,0,1): programa sem seed é determinístico.
            let kofRngS0 = 0, kofRngS1 = 0, kofRngS2 = 0, kofRngS3 = 1;
            function kofRngSplitmix32(x) {
                let z = (x + 0x9e3779b9) | 0;
                z = Math.imul(z ^ (z >>> 16), 0x21f0aaad) | 0;
                z = Math.imul(z ^ (z >>> 15), 0x735a2d97) | 0;
                return (z ^ (z >>> 15)) | 0;
            }
            export function kofRngSeed(seed) {
                const s = kofRngSplitmix32(seed | 0);
                kofRngS0 = kofRngSplitmix32(s);
                kofRngS1 = kofRngSplitmix32((s + 1) | 0);
                kofRngS2 = kofRngSplitmix32((s + 2) | 0);
                kofRngS3 = kofRngSplitmix32((s + 3) | 0);
                if (((kofRngS0 | kofRngS1) | (kofRngS2 | kofRngS3)) === 0) kofRngS1 = 1;
            }
            function kofRngNext() {
                let t = (kofRngS0 ^ (kofRngS0 << 11)) | 0;
                kofRngS0 = kofRngS1;
                kofRngS1 = kofRngS2;
                kofRngS2 = kofRngS3;
                const w = kofRngS3;
                kofRngS3 = ((w ^ (w >>> 19) ^ (t ^ (t >>> 8)))) | 0;
                return kofRngS3;
            }
            // [0, bound); bound<=0 => 0 (leniente, paridade random.int). Módulo
            // (u32 % bound) — mesma matemática do JVM (paridade determinística).
            export function kofRngInt(bound) {
                if (bound <= 0) return 0;
                return (kofRngNext() >>> 0) % bound;
            }
            export function kofRngBool() {
                return (kofRngNext() & 1) === 1;
            }
            // [0,1): 52 bits (hi 20 + lo 32) / 2^52; nunca 1.0. Exata em IEEE
            // 754 duplo — idêntica ao JVM (JvmStringRngRuntime).
            export function kofRngDouble() {
                const hi = kofRngNext() >>> 12;
                const lo = kofRngNext() >>> 0;
                return (hi * 4294967296.0 + lo) / 4503599627370496.0;
            }
            export function kofRngString(n, alphabet) {
                if (n <= 0 || alphabet == null || alphabet.length === 0) return "";
                let out = "";
                for (let i = 0; i < n; i++) out += alphabet.charAt(kofRngInt(alphabet.length));
                return out;
            }
            """;
}
