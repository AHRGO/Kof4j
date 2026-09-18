package dev.kof.compiler;

import java.util.List;

/**
 * FFI (R3): descrição compacta da assinatura `extern` e mapeamento tipo→layout
 * FFM. chars: i=Int j=Long f=Float d=Double b=Boolean S=String(char*) v=void(retorno).
 * Mantido fora de {@code CompilerPipeline} para a regra de ≤500 linhas/classe.
 */
final class FfiSignature {

    private FfiSignature() {}

    static Character paramChar(String t) {
        if (CompilerPipeline.isIntType(t)) return 'i';
        if (CompilerPipeline.isStringType(t)) return 'S';
        if (CompilerPipeline.isDoubleType(t)) return 'd';
        if (isLongFFI(t)) return 'j';
        if (isFloatFFI(t)) return 'f';
        if (isBoolFFI(t)) return 'b';
        return null;
    }

    static Character returnChar(String t) {
        if (isVoidFFI(t)) return 'v';
        return paramChar(t);
    }

    static boolean isVoidFFI(String t) {
        return t == null || t.isEmpty() || "void".equals(t) || "Void".equals(t);
    }

    static String signature(ExternalFunctionNode ext) {
        StringBuilder sb = new StringBuilder();
        sb.append(returnChar(ext.returnType()));
        for (var p : ext.parameters()) sb.append(paramChar(p.type()));
        return sb.toString();
    }

    static Type returnType(String r) {
        if (isVoidFFI(r)) return Type.PrimitiveType.VOID;
        if (CompilerPipeline.isDoubleType(r)) return Type.PrimitiveType.DOUBLE;
        if (isLongFFI(r)) return Type.PrimitiveType.LONG;
        if (isFloatFFI(r)) return Type.PrimitiveType.FLOAT;
        if (isBoolFFI(r)) return Type.PrimitiveType.BOOL;
        if (CompilerPipeline.isStringType(r)) return BuiltinTypes.STRING;
        return Type.PrimitiveType.INT;
    }

    static boolean isLongFFI(String t) { return "long".equals(t) || "Long".equals(t); }
    static boolean isFloatFFI(String t) { return "float".equals(t) || "Float".equals(t); }
    static boolean isBoolFFI(String t) {
        return "bool".equals(t) || "boolean".equals(t) || "Boolean".equals(t);
    }
}
