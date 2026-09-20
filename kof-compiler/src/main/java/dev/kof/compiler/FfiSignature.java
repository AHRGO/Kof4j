package dev.kof.compiler;

import java.util.List;

/**
 * FFI (R3): descrição compacta da assinatura `extern` e mapeamento tipo→layout
 * FFM. chars: i=Int j=Long f=Float d=Double b=Boolean S=String(char*) v=void(retorno).
 * Mantido fora de {@code CompilerPipeline} para a regra de ≤500 linhas/classe.
 */
public final class FfiSignature {

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
        for (var p : ext.parameters()) {
            Character c = paramChar(p.type());
            if (c != null) {
                sb.append(c);
            } else {
                // callback (R3, 3.4): token aninhado "(<retchar><paramchars>)"
                sb.append('(').append(callbackDescriptor(p.type())).append(')');
            }
        }
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

    /** #431 (Native): char do layout FFI a partir do Type ja baixado na IR
     *  (o KofCall nativo carrega os tipos declarados do `extern`). null = fora
     *  do conjunto escalar (callback/array/struct — nunca alcançável no call
     *  site nativo, o gate FFI001 filtra antes). */
    public static Character charOfType(Type t) {
        if (t == null) return null;
        if (Type.isVoid(t)) return 'v';
        if (t instanceof Type.PrimitiveType pt) {
            switch (pt.name()) {
                case "int": return 'i';
                case "long": return 'j';
                case "float": return 'f';
                case "double": return 'd';
                case "bool": case "boolean": return 'b';
                default: return null;
            }
        }
        if (t instanceof Type.NullableType nt) return charOfType(nt.inner());
        if (BuiltinTypes.isString(t)) return 'S';
        return null;
    }

    /** Type do parâmetro `extern` (o caminho nativo empurra o valor cru na
     *  pilha de operandos com este tipo; espelha returnType). */
    public static Type paramType(String t) {
        if (CompilerPipeline.isIntType(t)) return Type.PrimitiveType.INT;
        if (isLongFFI(t)) return Type.PrimitiveType.LONG;
        if (isFloatFFI(t)) return Type.PrimitiveType.FLOAT;
        if (isBoolFFI(t)) return Type.PrimitiveType.BOOL;
        if (CompilerPipeline.isStringType(t)) return BuiltinTypes.STRING;
        if (CompilerPipeline.isDoubleType(t)) return Type.PrimitiveType.DOUBLE;
        return null;
    }

    static boolean isLongFFI(String t) { return "long".equals(t) || "Long".equals(t); }
    static boolean isFloatFFI(String t) { return "float".equals(t) || "Float".equals(t); }
    static boolean isBoolFFI(String t) {
        return "bool".equals(t) || "boolean".equals(t) || "Boolean".equals(t) || "Bool".equals(t);
    }

    // ---- callbacks / upcalls (R3, fatia 3.4): token C(<ret><params>) ----------------
    // Parâmetros bindáveis: os escalares {Int, Long, Float, Double, Boolean} E String
    // (um `char*` que entra no callback: o runtime faz o bridge ADDRESS->String na
    // fronteira do upcall, espelhando o downcall `getString`; 3.4-C3.4). O RETORNO do
    // callback continua primitivo-ou-void: devolver `String` exigiria entregar ao C um
    // `char*` cujo dono da memória não é observável no contrato síncrono → fica fora do
    // conjunto bindável e o gate mantém FFI001/FFI002 honestos (R6). void/pointer/struct/
    // função-aninhada como parâmetro continuam null (não-bindável).
    // Ex.: "(String, Int) -> Int" -> descritor "iSi"; "(Int) -> String" -> null (retorno S).

    static Character cbParamChar(String t) {
        // 'S' é bindável como ARGUMENTO (char*->String no upcall); só o não-escalar
        // (função/struct/pointer) — paramChar==null — cai fora do conjunto.
        return paramChar(t);
    }

    static Character cbReturnChar(String t) {
        if (isVoidFFI(t)) return 'v';
        Character c = paramChar(t);
        if (c == null || c.charValue() == 'S') return null;   // String RETURN: não-bindável
        return c;
    }

    static boolean isFunctionType(String t) {
        return t != null && t.startsWith("(") && t.contains(" -> ");
    }

    /** Descritor de callback ("r" + chars dos params) ou null se não for bindável. */
    static String callbackDescriptor(String t) {
        if (!isFunctionType(t)) return null;
        int close = matchParen(t, 0);
        if (close < 0) return null;
        String rest = t.substring(close + 1).trim();
        if (!rest.startsWith("->")) return null;
        Character rc = cbReturnChar(rest.substring(2).trim());
        if (rc == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(rc.charValue());
        String paramsStr = t.substring(1, close).trim();
        if (!paramsStr.isEmpty()) {
            for (String p : splitTopLevel(paramsStr)) {
                Character pc = cbParamChar(p.trim());
                if (pc == null) return null;
                sb.append(pc.charValue());
            }
        }
        return sb.toString();
    }

    private static int matchParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static java.util.List<String> splitTopLevel(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '<') depth++;
            else if (c == ')' || c == '>') depth--;
            else if (c == ',' && depth == 0) { out.add(s.substring(start, i)); start = i + 1; }
        }
        out.add(s.substring(start));
        return out;
    }
}
