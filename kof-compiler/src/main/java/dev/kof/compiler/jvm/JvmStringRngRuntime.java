package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado — X8 fatia 1: kof.rng (PRNG
 * semeável, xorshift128 + splitmix32 no seed). MESMA aritmética do fragmento
 * JS (JsRuntimeUiRng, Math.imul): só xor/shift/adição/multiplicação com wrap
 * int de 32 bits, então JVM e JS produzem bits IDÊNTICOS para a mesma seed —
 * paridade por construção, provada em KofRngTest (mesma sequência nos dois
 * backends).
 *
 * <p>NUNCA para chaves/segredos (isso é SecureRandom, R11); rng é
 * determinismo reprodutível (R10, property-based testing). int(bound) usa
 * módulo (viés minúsculo documentado) — o contrato do rng é paridade
 * determinística, não uniformidade criptográfica.
 */
public final class JvmStringRngRuntime {

    private JvmStringRngRuntime() {}

    static String source() {
        return """

                // ── kof.rng (X8 fatia 1) — xorshift128 + splitmix32, 32-bit ──
                // Estado: 4 ints. Default fixo (0,0,0,1): programa sem seed é
                // determinístico (xorshift128 não aceita estado todo-zero).
                private static int kofRng0 = 0, kofRng1 = 0, kofRng2 = 0, kofRng3 = 1;

                // splitmix32: wrap int 32-bit (JS usa (x + c) | 0 + Math.imul —
                // mesmos bits; paridade provada em KofRngTest).
                private static int kofRngSplitmix32(int x) {
                    int z = x + 0x9e3779b9;
                    z = (z ^ (z >>> 16)) * 0x21f0aaad;
                    z = (z ^ (z >>> 15)) * 0x735a2d97;
                    return z ^ (z >>> 15);
                }

                public static void kof_rng_seed(int seed) {
                    int s = kofRngSplitmix32(seed);
                    kofRng0 = kofRngSplitmix32(s);
                    kofRng1 = kofRngSplitmix32(s + 1);
                    kofRng2 = kofRngSplitmix32(s + 2);
                    kofRng3 = kofRngSplitmix32(s + 3);
                    if ((kofRng0 | kofRng1 | kofRng2 | kofRng3) == 0) {
                        kofRng1 = 1;
                    }
                }

                private static int kofRngNext() {
                    // xorshift128 (Marsaglia 2003)
                    int t = kofRng0 ^ (kofRng0 << 11);
                    kofRng0 = kofRng1;
                    kofRng1 = kofRng2;
                    kofRng2 = kofRng3;
                    int w = kofRng3;
                    kofRng3 = w ^ (w >>> 19) ^ (t ^ (t >>> 8));
                    return kofRng3;
                }

                // [0, bound); bound<=0 => 0 (leniente, paridade random.int).
                // Módulo com viés minúsculo — paridade determinística é o
                // contrato (uniformidade criptográfica é random/security).
                public static int kof_rng_int(int bound) {
                    if (bound <= 0) return 0;
                    return (int) ((kofRngNext() & 0xffffffffL) % bound);
                }

                public static boolean kof_rng_bool() {
                    return (kofRngNext() & 1) == 1;
                }

                // [0,1): 52 bits de mantissa (hi 20 + lo 32) / 2^52; nunca 1.0.
                // Divisão exata em IEEE 754 duplo — idêntica no JS.
                public static double kof_rng_double() {
                    int hi = kofRngNext() >>> 12;
                    int lo = kofRngNext();
                    return (hi * 4294967296.0 + (lo & 0xffffffffL)) / 4503599627370496.0;
                }

                // n chars, cada um uniforme do alfabeto. n<=0 ou alfabeto
                // vazio/null => "" (leniente, paridade random.string).
                public static String kof_rng_string(int n, String alphabet) {
                    if (n <= 0 || alphabet == null || alphabet.isEmpty()) return "";
                    StringBuilder sb = new StringBuilder(n);
                    for (int i = 0; i < n; i++) sb.append(alphabet.charAt(kof_rng_int(alphabet.length())));
                    return sb.toString();
                }
                """;
    }
}
