package dev.kof.compiler;

import java.util.List;

/**
 * §218/#148 — formatadores numéricos de receiver PRIMITIVO
 * (`n.toHexString()`/`n.toBinaryString()`): não são métodos de instância em
 * Kof nem no JDK (`Integer.toHexString` é ESTÁTICO). Antes caíam no fallback
 * de método-resolvido ausente com owner "" no Methodref
 * (`invokevirtual "".toHexString:()Object`) → `ClassFormatError: Illegal
 * class name ""` (a classe nunca carrega). O roteamento vai para o estático
 * JDK real (`java/lang/Integer` ou `java/lang/Long`, retorno String,
 * argumento o próprio primitivo) — mesma superfície do alias de conversão
 * `toInt`/§89; o receiver empilhado vira o argumento da chamada estática.
 * Os formatadores só existem para Int/Long; Byte/Short/Char/Float/Double/Bool
 * recebem SEM052 honesto apontando o idiom real — nunca owner vazio (R6).
 */
final class PrimitiveNumericFormatters {

    private PrimitiveNumericFormatters() {}

    /** `true` se `methodName` é um formatador numérico sem args (§218). */
    static boolean isFormatter(String methodName, int argumentCount) {
        return argumentCount == 0
                && ("toHexString".equals(methodName) || "toBinaryString".equals(methodName));
    }

    /**
     * Emite a chamada para o estático JDK real e retorna `true`; sem suporte
     * (não-Int/Long) emite SEM052 + valor neutro e retorna `false` (o caller
     * encerra o caminho do mesmo jeito).
     */
    static boolean emit(CompilerDriver driver, MethodCallExpr mc, Type recvType,
                        List<KofOperation> ops) {
        String prim = Type.canonicalPrimitiveName(((Type.PrimitiveType) recvType).name());
        boolean isInt = "int".equals(prim);
        boolean isLong = "long".equals(prim);
        if (isInt || isLong) {
            ops.add(new KofCall(new Type.ClassType("java.lang", isInt ? "Integer" : "Long", List.of()),
                    mc.methodName(), List.of(recvType), BuiltinTypes.STRING, KofCallKind.STATIC));
            return true;
        }
        if (driver.currentDiagnostics != null) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "Kof has no \"" + mc.methodName() + "\" method on "
                            + TypeMetrics.primitiveName(recvType) + "; use the stdlib "
                            + "function on an Int/Long, e.g.: n.toLong()." + mc.methodName() + "()",
                    "SEM052");
        }
        ops.add(new KofPop());
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ""));
        return false;
    }
}
