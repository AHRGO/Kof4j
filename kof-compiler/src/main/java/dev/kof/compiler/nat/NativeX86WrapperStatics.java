package dev.kof.compiler.nat;

import dev.kof.compiler.KofCall;

/**
 * §235 native face (x86_64): wrapper statics (`Int.parseInt`, `Double.isNaN`,
 * `Double.isInfinite`, `Double.isFinite`, `Bool.parseBoolean`, …) had no JDK
 * class in the native runtime, so the generic call emitted
 * `java_lang_Integer_parseInt` and the LINK failed (COMP001 — R6-honest but not
 * rule-5 parity). This dispatches them before the generic call, mirroring the
 * JS fix of the same section:
 *
 * <ul>
 *   <li>{@code parse*} → the existing {@code kof_string_to_*} helpers (already
 *       carry the JDK contract: trim, sign, overflow throws).</li>
 *   <li>{@code parseBoolean} → {@code kof_string_to_bool} (new, JVM contract
 *       {@code s != null && s.equalsIgnoreCase("true")} — no trim).</li>
 *   <li>{@code isNaN/isInfinite/isFinite} → inline branchless IEEE bit tests
 *       (no runtime symbol needed).</li>
 * </ul>
 */
final class NativeX86WrapperStatics {

    private NativeX86WrapperStatics() {}

    static boolean emit(StringBuilder sb, KofCall kc) {
        switch (NativeOpHelpers.wrapperStatic(kc)) {
            case PARSE_INT -> { popRdi(sb); sb.append("    call kof_string_to_int\n"); pushRax(sb); }
            case PARSE_LONG -> { popRdi(sb); sb.append("    call kof_string_to_long\n"); pushRax(sb); }
            case PARSE_DOUBLE -> {
                popRdi(sb);
                sb.append("    call kof_string_to_double\n");
                sb.append("    movq %xmm0, %rax\n");
                pushRax(sb);
            }
            case PARSE_FLOAT -> {
                popRdi(sb);
                sb.append("    call kof_string_to_float\n");
                sb.append("    movd %xmm0, %eax\n");
                pushRax(sb);
            }
            case PARSE_BOOL -> { popRdi(sb); sb.append("    call kof_string_to_bool\n"); pushRax(sb); }
            case IS_NAN_D -> {
                popRax(sb);
                sb.append("    movq %rax, %xmm0\n");
                sb.append("    ucomisd %xmm0, %xmm0\n");
                setBool(sb, "setp");
            }
            case IS_NAN_F -> {
                popRax(sb);
                sb.append("    movd %eax, %xmm0\n");
                sb.append("    ucomiss %xmm0, %xmm0\n");
                setBool(sb, "setp");
            }
            case IS_INF_D -> {
                popRax(sb);
                sb.append("    movabsq $0x7FFFFFFFFFFFFFFF, %rcx\n");
                sb.append("    andq %rcx, %rax\n");
                sb.append("    movabsq $0x7FF0000000000000, %rcx\n");
                sb.append("    cmpq %rcx, %rax\n");
                setBool(sb, "sete");
            }
            case IS_FIN_D -> {
                popRax(sb);
                sb.append("    movq %rax, %rcx\n");
                sb.append("    movabsq $0x7FF0000000000000, %rdx\n");
                sb.append("    andq %rdx, %rcx\n");
                sb.append("    cmpq %rdx, %rcx\n");
                setBool(sb, "setne");
            }
            case IS_INF_F -> {
                popRax(sb);
                sb.append("    andl $0x7FFFFFFF, %eax\n");
                sb.append("    cmpl $0x7F800000, %eax\n");
                setBool(sb, "sete");
            }
            case IS_FIN_F -> {
                popRax(sb);
                sb.append("    movl %eax, %ecx\n");
                sb.append("    andl $0x7F800000, %ecx\n");
                sb.append("    cmpl $0x7F800000, %ecx\n");
                setBool(sb, "setne");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void popRdi(StringBuilder sb) { sb.append("    popq %rdi\n"); }

    private static void popRax(StringBuilder sb) { sb.append("    popq %rax\n"); }

    private static void pushRax(StringBuilder sb) { sb.append("    pushq %rax\n"); }

    private static void setBool(StringBuilder sb, String setcc) {
        sb.append("    ").append(setcc).append(" %al\n");
        sb.append("    movzbl %al, %eax\n");
        sb.append("    pushq %rax\n");
    }
}
