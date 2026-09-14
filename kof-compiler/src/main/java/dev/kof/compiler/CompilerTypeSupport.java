package dev.kof.compiler;

import java.util.List;

/**
 * Helpers de tipo e layout: json/fp support, nomes internos, access flags.
 */
public final class CompilerTypeSupport {

    private CompilerTypeSupport() {}

    static Type listOfElementType(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (!mc.arguments().isEmpty()) {
            Type first = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
            if (first instanceof Type.FunctionType ft) {
                // §156: lista heterogênea de lambdas com a MESMA assinatura —
                // o tipo do elemento carregava o className da PRIMEIRA lambda
                // concreta (Lambda0) e o `kof_list_get` fazia checkcast p/ ela
                // (CCE quando o elemento era Lambda1). Todas as lambdas da
                // assinatura implementam a MESMA interface SAM sintética — o
                // elemento desce sem className (dispatch por interface, bug 8).
                // Só unifica quando TODOS os args são FunctionType da mesma
                // assinatura (params+retorno); senão mantém o primeiro (SEM056
                // barra a poluição heterogênea de verdade no literal).
                if (sameLambdaSignature(driver, mc, locals, ft)) {
                    return new Type.FunctionType(ft.parameterTypes(), ft.returnType());
                }
            }
            return first;
        }
        if (!mc.typeArguments().isEmpty()) {
            return CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
        }
        return Type.UnknownType.UNKNOWN;
    }

    private static boolean sameLambdaSignature(CompilerDriver driver, MethodCallExpr mc,
            List<IRLocalVariable> locals, Type.FunctionType first) {
        for (int i = 1; i < mc.arguments().size(); i++) {
            Type t = ExpressionTyper.inferExprType(driver, mc.arguments().get(i), locals);
            if (!(t instanceof Type.FunctionType ft)) return false;
            if (!ft.parameterTypes().equals(first.parameterTypes())) return false;
            if (!ft.returnType().equals(first.returnType())) return false;
        }
        return true;
    }

    static boolean ctorCompatible(CompilerDriver driver, Type formal, Type arg) {
        if (formal == null || arg == null) return true;
        if (Type.isUnknown(formal) || Type.isUnknown(arg)) return true;
        if (formal.equals(arg)) return true;
        if (formal instanceof Type.PrimitiveType fp && arg instanceof Type.PrimitiveType ap) {
            return TypeMetrics.primWidth(ap) <= TypeMetrics.primWidth(fp);
        }
        if (formal instanceof Type.ClassType fc && arg instanceof Type.ClassType ac
                && driver.semanticAnalyzer != null) {
            java.util.Set<String> visited = new java.util.HashSet<>();
            java.util.Queue<String> queue = new java.util.LinkedList<>();
            queue.add(ac.name());
            visited.add(ac.name());
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(fc.name())) return true;
                SymbolTable.ClassSymbol cur = driver.semanticAnalyzer.getClass(current);
                if (cur == null) continue;
                if (cur.superClass() != null && !cur.superClass().equals("java/lang/Object")
                        && visited.add(cur.superClass())) queue.add(cur.superClass());
                for (String i : cur.interfaces()) {
                    if (visited.add(i)) queue.add(i);
                }
            }
        }
        return true;
    }

    static boolean erasesToReference(Type t) {
        return t instanceof Type.TypeVariable || t instanceof Type.ClassType
                || t instanceof Type.ArrayType || t instanceof Type.UnknownType;
    }

    static boolean syntheticExists(CompilerDriver driver, String name) {
        for (IRClass c : driver.syntheticClasses) {
            if (c.name().equals(name)) return true;
        }
        return false;
    }

    static boolean fpSupportedOnNative(CompilerDriver driver, Type type, @SuppressWarnings("unused") SourcePosition pos) {
        // Native float/double now supported via XMM (was FLT001) — KofJS always was
        return true;
    }

    static boolean jsonSupported(CompilerDriver driver, Type type, boolean isDecode) {
        Type check = BuiltinTypes.isList(type) ? CompilerTypeSupport.listElementType(driver,type) : type;
        if (check instanceof Type.PrimitiveType pt && ("float".equals(pt.name()) || "double".equals(pt.name()))) {
            // JSN001 fechado: encode/decode float/double no Native
            // (kof_json_encode_double + kof_string_to_double, FP XMM).
            return true;
        }
        if (isDecode && type instanceof Type.ArrayType) {
            // JSN003 fechado: int/long/bool/string[] tem decoders nativos.
            // JSN001: float/double[] também decodifica no Native.
            return true;
        }
        if (check instanceof Type.ClassType && driver.target.isNative() && !BuiltinTypes.isList(type)
                && !BuiltinTypes.isString(type)) {
            // JSN002 fechado para classes cujos campos sao todos suportados
            // pelo walker nativo (primitivos, string e objetos aninhados).
            String cn = check instanceof Type.ClassType ct
                    ? (ct.packageName().isEmpty() ? ct.name()
                      : ct.packageName() + "." + ct.name())
                    : "";
            if (!driver.nativeObjJsonFieldsOk(cn, new java.util.HashSet<>(), null)) {
                return false;
            }
        }
        return true;
    }

    static boolean fieldOk(CompilerDriver driver, String typeName, String className, java.util.Set<String> visiting) {
        Type t = CompilerTypes.toType(typeName, driver.currentUnit);
        if (t instanceof Type.PrimitiveType) return true;
        if (BuiltinTypes.isString(t)) return true;
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error("", 0, 0, 0,
                    "json: class " + className + " has field of type " + typeName
                            + " not supported by the Native JSON encoder yet"
                            + " (use int, long, bool or string fields; nested objects coming soon)",
                    "JSN002");
        }
        return false;
    }

    static Type listElementType(CompilerDriver driver, Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    static String toInternalName(String packageName, String simpleName) {
        if (simpleName.contains("/")) return simpleName;
        if (simpleName.contains(".")) return simpleName.replace('.', '/');
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    static int computeAccess(List<String> modifiers) {
        int access = 0;
        boolean hasVisibility = false;
        for (String mod : modifiers) {
            access |= switch (mod) {
                case "public" -> AccessFlags.PUBLIC;
                case "private" -> AccessFlags.PRIVATE;
                case "protected" -> AccessFlags.PROTECTED;
                case "static" -> AccessFlags.STATIC;
                case "final" -> AccessFlags.FINAL;
                case "abstract" -> AccessFlags.ABSTRACT;
                default -> 0;
            };
            if ("public".equals(mod) || "private".equals(mod) || "protected".equals(mod)) {
                hasVisibility = true;
            }
        }
        if (!hasVisibility) access |= AccessFlags.PUBLIC;
        return access;
    }

    static int parseIntLiteral(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            // no suffix stripping: hex digits may end in a..f
            return (int) Long.parseUnsignedLong(value.substring(2), 16);
        }
        return Integer.parseInt(CompilerTypeSupport.stripSuffix(value));
    }

    static long parseLongLiteral(String value) {
        String stripped = CompilerTypeSupport.stripSuffix(value);
        if (stripped.startsWith("0x") || stripped.startsWith("0X")) {
            return Long.parseUnsignedLong(stripped.substring(2), 16);
        }
        return Long.parseLong(stripped);
    }

    static String stripSuffix(String value) {
        if (value.endsWith("l") || value.endsWith("L") ||
            value.endsWith("f") || value.endsWith("F") ||
            value.endsWith("d") || value.endsWith("D")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}