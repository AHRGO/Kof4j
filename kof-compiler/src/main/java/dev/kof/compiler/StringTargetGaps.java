package dev.kof.compiler;

import java.util.Set;

/**
 * §424: five {@code String} methods are accepted by the typer
 * ({@link StringMethodRegistry}) but have no lowering on JS or Native —
 * {@code matches}/{@code replaceAll}/{@code replaceFirst}/{@code toCharArray}/
 * {@code compareToIgnoreCase}. On JS the default emitter produced a direct
 * {@code receiver.<m>(...)} call (a nonexistent {@code String.prototype}
 * member → runtime {@code TypeError}, or the literal-vs-regex divergence of
 * {@code replaceAll}); on Native the unhandled call was mangled into
 * {@code java_lang_String_<m>} and surfaced only as a cryptic {@code ld}
 * undefined-reference. JVM implements all five.
 *
 * <p>§424 chooses the honest backstop (the JS/Native faces are not ported):
 * refuse at compile time with {@code STR003}, never a silent runtime break
 * (R6). A future per-target implementation replaces this gate.
 */
final class StringTargetGaps {

    private StringTargetGaps() {}

    static final String CODE = "STR003";

    /** Accepted by the typer, not lowered on JS/Native. */
    private static final Set<String> INCOMPLETE = Set.of(
            "matches", "replaceAll", "replaceFirst", "toCharArray", "compareToIgnoreCase");

    static boolean isIncompleteMethod(String method) {
        return INCOMPLETE.contains(method);
    }

    static boolean refuses(Target target) {
        return target == Target.JS || target.isNative();
    }

    /**
     * Emits the honest {@code STR003} diagnostic and returns {@code true} when
     * the call must be refused (incomplete method on an unported target).
     * The receiver value, already pushed by the caller, is intentionally left
     * for the aborted compilation (same pattern as the NAT005/NAT006 gates).
     */
    static boolean refuse(CompilerDriver driver, MethodCallExpr mc) {
        if (!isIncompleteMethod(mc.methodName()) || !refuses(driver.target)) {
            return false;
        }
        if (driver.currentDiagnostics != null) {
            SourcePosition pos = mc.position();
            driver.currentDiagnostics.error(
                    pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0,
                    pos != null ? pos.column() : 0, 0,
                    "String." + mc.methodName() + ": not available on the "
                            + driver.target + " driver.target yet (STR003) — the "
                            + "JVM implements this method; the JS/Native face is"
                            + " not ported (use --target jvm)",
                    CODE);
        }
        return true;
    }
}
