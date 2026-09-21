package dev.kof.compiler.jvm;

/** JVM runtime for the {@code Secret} value type (D-SECRETS face 1, Stage 5 /
 *  3.6). {@code KofRuntime$Secret} wraps the raw text; {@code toString()} is the
 *  REDACTED form ({@code Secret(*** )} — never the value, never a prefix) and
 *  {@code equals} is constant-time, so a {@code println} or an interpolation
 *  cannot leak the value and {@code ==} does not short-circuit on content.
 *  {@code reveal()} is the only export. JS/Native never reach this file (honest
 *  gap SECN008 upstream); Native also would need a zeroable buffer (later face). */
public final class JvmSecretRuntime {
    private JvmSecretRuntime() {}

    static String source() {
        return """
                // ── kof.secrets — Secret value type (D-SECRETS face 1, JVM) ──
                public static final class Secret {
                    private final String value;
                    private Secret(String value) { this.value = value == null ? "" : value; }
                    @Override public String toString() { return "Secret(*** )"; }
                    @Override public boolean equals(Object o) {
                        if (this == o) return true;
                        if (!(o instanceof Secret s)) return false;
                        return kof_sec_constant_time_equals(this.value, s.value);
                    }
                }

                public static Secret kof_sec_secret_of(String value) {
                    return new Secret(value);
                }

                public static Secret kof_sec_secret(String name) {
                    return new Secret(kof_sec_secret_get(name));
                }

                public static String kof_sec_secret_reveal(Secret s) {
                    return s == null ? null : s.value;
                }

                public static String kof_sec_secret_redacted(Secret s) {
                    return "***";
                }

                """;
    }
}
