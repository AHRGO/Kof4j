package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de `String.format(String, Object...)` — varargs do JDK.
 *
 * <p>#156/#216: o classpath externo casa overloads por name+arity apenas e
 * não enxerga {@code java.lang.String} (o JDK não está nos entries), então a
 * chamada caía no descritor fabricado `(String,String,int)Object` e explodia
 * com {@code NoSuchMethodError} em runtime. Aqui os argumentos extras são
 * empacotados num {@code Object[]} (primitivos boxados) e o descritor REAL
 * {@code (Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;} é emitido.
 * Cobre 0 args extras (`format("Hello World")`).
 *
 * <p>#466 (R10/paridade cross-target): o descritor de 2 args é
 * locale-sensitive — `%.2f` imprime `3,14` em hosts pt_BR e `3.14` em
 * en. O backend sempre emite a forma de 3 args
 * `String.format(Locale.ROOT, String, Object[])`, travando o output
 * determinístico nos alvos que falam com o JDK (JVM/Script). JS já é
 * determinístico (`toFixed`/raiz) e o Native não tem JDK — o oracle é
 * JVM+Locale.ROOT nos dois alvos JVM-like.
 */
final class StringFormatCallLowerer {

    private StringFormatCallLowerer() {}

    /** A chamada é `String.format(...)` com o 1º arg String (ou tipo desconhecido)? */
    static boolean matches(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (mc.arguments().isEmpty()) return false;
        Type first = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        return BuiltinTypes.isString(first) || first == Type.UnknownType.UNKNOWN;
    }

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            String owner, int localIdx, List<IRLocalVariable> locals) {
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        Type objectArray = new Type.ArrayType(object);
        Type locale = new Type.ClassType("java.util", "Locale", List.of());
        List<ExpressionNode> args = mc.arguments();
        // #466: Locale.ROOT primeiro — String.format(String,...) é
        // locale-sensitive (pt_BR → `3,14`); ROOT trava em `3.14` em
        // qualquer host (determinismo R10 + paridade JVM↔Script↔JS).
        ops.add(new KofGetStatic(locale, "ROOT", locale));
        localIdx = ExpressionLowerer.emitExpression(driver, args.get(0), ops, owner, localIdx, locals);
        int extra = args.size() - 1;
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, extra));
        ops.add(new KofNewArray(object));
        for (int i = 0; i < extra; i++) {
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, i));
            ExpressionNode arg = args.get(i + 1);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
            if (argType instanceof Type.PrimitiveType) {
                TypeEmitter.boxPrimitive(ops, argType);
            }
            ops.add(new KofArrayStore(object));
        }
        ops.add(new KofCall(BuiltinTypes.STRING, "format",
                List.of(locale, BuiltinTypes.STRING, objectArray), BuiltinTypes.STRING, KofCallKind.STATIC));
        return localIdx;
    }
}
