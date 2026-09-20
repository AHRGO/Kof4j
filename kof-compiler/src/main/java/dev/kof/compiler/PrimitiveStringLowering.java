package dev.kof.compiler;

import java.util.List;

/** Typed Char stringification shared by print and concatenation (#259). */
final class PrimitiveStringLowering {
    private PrimitiveStringLowering() {}

    static void emitChar(CompilerDriver driver, List<KofOperation> ops, Type type) {
        if (driver.target == Target.JS || !(type instanceof Type.NullableType)) {
            valueOf(ops, type);
            return;
        }
        LabelId absent = LabelId.create();
        LabelId present = LabelId.create();
        LabelId end = LabelId.create();
        ops.add(new KofDup());
        ops.add(KofLoadLiteral.ofNull());
        ops.add(new KofConditionalJump(KofComparison.EQ,
                new Type.ClassType("java.lang", "Object", List.of()), absent, present));
        ops.add(new KofLabel(present));
        driver.emitErasureUnbox(ops, Type.PrimitiveType.INT);
        valueOf(ops, Type.PrimitiveType.CHAR);
        ops.add(new KofJump(end));
        ops.add(new KofLabel(absent));
        ops.add(new KofPop());
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "null"));
        ops.add(new KofLabel(end));
    }

    private static void valueOf(List<KofOperation> ops, Type type) {
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf", List.of(type),
                BuiltinTypes.STRING, KofCallKind.STATIC));
    }
}
