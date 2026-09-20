package dev.kof.compiler.nat;

import dev.kof.compiler.KofCall;

/**
 * §235 native face (riscv64; aarch64 inherits via the translator): wrapper
 * statics (`Int.parseInt`, `Double.isNaN`, …) had no JDK class in the native
 * runtime — the generic call emitted `java_lang_Integer_parseInt` and the LINK
 * failed (COMP001). Mirrors {@link NativeX86WrapperStatics}:
 *
 * <ul>
 *   <li>{@code parse*} → the existing {@code kof_string_to_*} helpers (double
 *       and float return the raw bits in {@code a0}).</li>
 *   <li>{@code parseBoolean} → {@code kof_string_to_bool} (new, B30).</li>
 *   <li>{@code isNaN/isInfinite/isFinite} → inline branchless IEEE bit tests
 *       ({@code and}/{@code xor}/{@code seqz}/{@code snez}) — no runtime symbol
 *       and no per-site labels (translator-safe, rule 5).</li>
 * </ul>
 */
final class NativeRiscvWrapperStatics {

    private NativeRiscvWrapperStatics() {}

    static boolean emit(StringBuilder sb, KofCall kc) {
        switch (NativeOpHelpers.wrapperStatic(kc)) {
            case PARSE_INT -> parse(sb, "kof_string_to_int");
            case PARSE_LONG -> parse(sb, "kof_string_to_long");
            case PARSE_DOUBLE -> parse(sb, "kof_string_to_double");
            case PARSE_FLOAT -> parse(sb, "kof_string_to_float");
            case PARSE_BOOL -> parse(sb, "kof_string_to_bool");
            case IS_NAN_D -> {
                pop(sb);
                sb.append("    li t0, 0x7FF0000000000000\n");
                sb.append("    and t1, a0, t0\n");
                sb.append("    xor t1, t1, t0\n");
                sb.append("    seqz t1, t1\n");
                sb.append("    li t2, 0x000FFFFFFFFFFFFF\n");
                sb.append("    and t2, a0, t2\n");
                sb.append("    snez t2, t2\n");
                sb.append("    and a0, t1, t2\n");
                push(sb);
            }
            case IS_NAN_F -> {
                pop(sb);
                sb.append("    li t0, 0x7F800000\n");
                sb.append("    and t1, a0, t0\n");
                sb.append("    xor t1, t1, t0\n");
                sb.append("    seqz t1, t1\n");
                sb.append("    li t2, 0x007FFFFF\n");
                sb.append("    and t2, a0, t2\n");
                sb.append("    snez t2, t2\n");
                sb.append("    and a0, t1, t2\n");
                push(sb);
            }
            case IS_INF_D -> {
                pop(sb);
                sb.append("    li t0, 0x7FFFFFFFFFFFFFFF\n");
                sb.append("    and t1, a0, t0\n");
                sb.append("    li t0, 0x7FF0000000000000\n");
                sb.append("    xor t1, t1, t0\n");
                sb.append("    seqz a0, t1\n");
                push(sb);
            }
            case IS_INF_F -> {
                pop(sb);
                sb.append("    li t0, 0x7FFFFFFF\n");
                sb.append("    and t1, a0, t0\n");
                sb.append("    li t0, 0x7F800000\n");
                sb.append("    xor t1, t1, t0\n");
                sb.append("    seqz a0, t1\n");
                push(sb);
            }
            case IS_FIN_D -> {
                pop(sb);
                sb.append("    li t0, 0x7FF0000000000000\n");
                sb.append("    and t1, a0, t0\n");
                sb.append("    xor t1, t1, t0\n");
                sb.append("    snez a0, t1\n");
                push(sb);
            }
            case IS_FIN_F -> {
                pop(sb);
                sb.append("    li t0, 0x7F800000\n");
                sb.append("    and t1, a0, t0\n");
                sb.append("    xor t1, t1, t0\n");
                sb.append("    snez a0, t1\n");
                push(sb);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void parse(StringBuilder sb, String fn) {
        pop(sb);
        sb.append("    call ").append(fn).append("\n");
        push(sb);
    }

    private static void pop(StringBuilder sb) { sb.append("    pop a0\n"); }

    private static void push(StringBuilder sb) {
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd a0, 0(sp)\n");
    }
}
