package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * FFI struct/array ABI — slice 3.7 (native asm), shared by the gate
 * ({@code CompilerPipeline.nativeExternBound}) and the emitters
 * ({@code nat.NativeFfiCall}). Consumes {@link AbiLayout} (3.8a) — the layout
 * engine is the single source of truth for offsets/classes.
 *
 * <p>A native struct parameter travels as a {@link Type.ClassType} tagged
 * {@code kof.ffi.struct} whose type arguments are the field types (primitive
 * only, no {@code String}/pointer — {@code FfiSignature.structFieldChars} is the
 * gate). The emitter reads the type arguments; no {@code CompilerDriver} is
 * needed at codegen time.
 *
 * <p>This slice binds the **register path** only (x86-64 SysV first): a struct
 * that would go to memory (SysV MEMORY, AAPCS64 BYREF, riscv BYREF, or that does
 * not fit the remaining registers) stays an honest {@code FFI001} — never a
 * partial/silent binding (R6).
 */
public final class FfiStructLayout {

    private FfiStructLayout() {}

    static final String PKG = "kof.ffi";
    static final String NAME = "struct";

    public static boolean isStructType(Type t) {
        return t instanceof Type.ClassType ct
                && PKG.equals(ct.packageName()) && NAME.equals(ct.name());
    }

    public static Type structType(List<Type> fieldTypes) {
        return new Type.ClassType(PKG, NAME, fieldTypes);
    }

    static Type primitiveOf(char ch) {
        return switch (ch) {
            case 'j' -> Type.PrimitiveType.LONG;
            case 'f' -> Type.PrimitiveType.FLOAT;
            case 'd' -> Type.PrimitiveType.DOUBLE;
            case 'b' -> Type.PrimitiveType.BOOL;
            default -> Type.PrimitiveType.INT;
        };
    }

    /** Struct Type from the field chars of {@code FfiSignature.structFieldChars}. */
    static Type structTypeOfChars(String chars) {
        List<Type> ts = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) ts.add(primitiveOf(chars.charAt(i)));
        return structType(ts);
    }

    public static AbiLayout.Abi abiFor(Target t) {
        return switch (t) {
            case NATIVE_RISCV64 -> AbiLayout.Abi.RISCV64;
            case NATIVE_AARCH64 -> AbiLayout.Abi.AAPCS64;
            default -> AbiLayout.Abi.SYSV_X86_64;
        };
    }

    public static AbiLayout.Scalar scalarOf(Type t) {
        Character ch = FfiSignature.charOfType(t);
        if (ch == null) throw new IllegalArgumentException("non-scalar struct field: " + t);
        return switch (ch) {
            case 'j' -> AbiLayout.Scalar.LONG;
            case 'f' -> AbiLayout.Scalar.FLOAT;
            case 'd' -> AbiLayout.Scalar.DOUBLE;
            case 'b' -> AbiLayout.Scalar.BOOL;
            default -> AbiLayout.Scalar.INT;
        };
    }

    public static AbiLayout.Layout layout(AbiLayout.Abi abi, Type structType) {
        List<AbiLayout.Field> fs = new ArrayList<>();
        List<Type> ts = ((Type.ClassType) structType).typeArguments();
        for (int i = 0; i < ts.size(); i++) {
            fs.add(new AbiLayout.Field("f" + i, scalarOf(ts.get(i))));
        }
        return AbiLayout.of(abi, fs);
    }

    /** Field with its Kof 8-byte slot index and its C offset (from AbiLayout). */
    public record FieldInfo(int kofSlot, int cOffset, AbiLayout.Scalar scalar) {}

    public static List<FieldInfo> fields(Type structType) {
        AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, structType);
        List<Type> ts = ((Type.ClassType) structType).typeArguments();
        List<FieldInfo> out = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            out.add(new FieldInfo(i, l.offsets()[i], scalarOf(ts.get(i))));
        }
        return out;
    }

    // ── x86-64 SysV bindability (register path only) ─────────────────────

    /** A struct whose SSE eightbyte carries more than one field needs packing
     *  we do not emit yet — honest gap. */
    private static boolean sseEightbytesAreSingleField(Type structType) {
        List<FieldInfo> fs = fields(structType);
        AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, structType);
        for (int e = 0; e < l.classes().size(); e++) {
            if (l.classes().get(e) != AbiLayout.ArgClass.SSE) continue;
            int lo = e * 8;
            int count = 0;
            for (FieldInfo f : fs) {
                if (f.cOffset() < lo + 8 && f.cOffset() + f.scalar().size > lo) count++;
            }
            if (count != 1) return false;
        }
        return true;
    }

    /** True when the whole parameter list is bindable on x86-64 (scalars may
     *  spill; structs must fit entirely in registers and use single-field SSE
     *  eightbytes). Simulates SysV register counting in formal order. */
    public static boolean x86Bindable(List<Type> paramTypes) {
        int nInt = 0, nFlt = 0;
        for (Type t : paramTypes) {
            if (isStructType(t)) {
                if (!sseEightbytesAreSingleField(t)) return false;
                AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, t);
                if (l.byMemory()) return false;
                for (AbiLayout.ArgClass c : l.classes()) {
                    if (c == AbiLayout.ArgClass.SSE) {
                        if (nFlt >= 8) return false;
                        nFlt++;
                    } else {
                        if (nInt >= 6) return false;
                        nInt++;
                    }
                }
            } else {
                Character ch = FfiSignature.charOfType(t);
                if (ch == null) return false;
                if (ch == 'f' || ch == 'd') nFlt++; else nInt++;
            }
        }
        return true;
    }

    // ── x86-64 emission ──────────────────────────────────────────────────

    /**
     * Builds eightbyte {@code e} of the struct (base register holds the Kof
     * record object pointer) into {@code dst} — an integer register for
     * INTEGER/SSE-upgraded classes, or an xmm register for SSE.
     *
     * <p>Kof field slots are 8 bytes wide ({@code FieldLayout.sizeOf} = 8) in
     * declaration order starting at object offset 16; the C field is written at
     * its natural size. Integer eightbytes are assembled with shift/or (a
     * struct field never crosses an eightbyte boundary under natural
     * alignment).
     */
    public static void emitX86Eightbyte(StringBuilder sb, Type structType, int e,
                                        String base, String dst, boolean sse) {
        List<FieldInfo> fs = fields(structType);
        AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, structType);
        int lo = e * 8;
        if (sse) {
            for (FieldInfo f : fs) {
                if (f.cOffset() < lo + 8 && f.cOffset() + f.scalar().size > lo) {
                    int off = 16 + 8 * f.kofSlot();
                    if (f.scalar().size == 8) {
                        sb.append("    movq ").append(off).append("(").append(base).append("), %rax\n");
                        sb.append("    movq %rax, ").append(dst).append("\n");
                    } else {
                        sb.append("    movd ").append(off).append("(").append(base).append("), ").append(dst).append("\n");
                    }
                    return;
                }
            }
            return;
        }
        sb.append("    xorq %rax, %rax\n");
        for (FieldInfo f : fs) {
            if (f.cOffset() >= lo + 8 || f.cOffset() + f.scalar().size <= lo) continue;
            int off = 16 + 8 * f.kofSlot();
            int shift = (f.cOffset() - lo) * 8;
            switch (f.scalar().size) {
                case 1 -> sb.append("    movzbl ").append(off).append("(").append(base).append("), %r11d\n");
                case 2 -> sb.append("    movzwl ").append(off).append("(").append(base).append("), %r11d\n");
                case 4 -> sb.append("    movl ").append(off).append("(").append(base).append("), %r11d\n");
                default -> sb.append("    movq ").append(off).append("(").append(base).append("), %r11\n");
            }
            if (shift > 0) sb.append("    shlq $").append(shift).append(", %r11\n");
            sb.append("    orq %r11, %rax\n");
        }
        sb.append("    movq %rax, ").append(dst).append("\n");
    }
}
