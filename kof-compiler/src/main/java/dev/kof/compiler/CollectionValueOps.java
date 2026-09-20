package dev.kof.compiler;

import java.util.List;

/**
 * #386 (fatia 2) — extras do containsValue no lowering compartilhado: o box
 * do argumento (família Int/Long no nativo — o slot de Map é fisicamente
 * caixa para ela, §284) e o tag de comparação por valor
 * ({@link CollectionMethodGates#valueCmpTag}), incluindo a rejeição NAT002
 * do mapa de valor Object no nativo (slot de Double cru é indistinguível de
 * caixa sem dereferência — §352). Vive fora do CollectionCallLowerer pelo
 * gate de 500 linhas e pela responsabilidade própria; o backend JVM faz POP
 * do tag (java.util usa equals), Script/JS ignoram, nativo usa no scan.
 */
final class CollectionValueOps {

    private CollectionValueOps() {}

    /**
     * Emite [box(arg0)?][tag] e devolve true quando o programa foi rejeitado
     * (NAT002) — o lowerer aborta o KofCall nesse caso, nunca emite a call
     * sem o arg extra.
     */
    static boolean emitContainsValueExtras(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, List<IRLocalVariable> locals,
            List<Type> argTypes, Type valueType) {
        Type arg0 = argTypes.isEmpty() ? null : argTypes.get(0);
        // #386 — containsValue: a sonda nativa exige caixa nos dois lados
        // quando a família é Int/Long (slot boxed × arg cru = deref de
        // inteiro como ponteiro); os tags 0/3 cuidam dos demais casos.
        if (!argTypes.isEmpty() && driver.target.isNative() && driver.needsErasureBoxing()
                && CollectionCallLowerer.mapBoxablePrim(arg0)
                && !ExpressionTyper.boxesOwnBranches(driver, mc.arguments().get(0), locals)) {
            CompilerEmissionHelpers.emitErasureBox(driver, ops, arg0);
        }
        int cvTag = CollectionMethodGates.valueCmpTag(valueType, arg0);
        if (cvTag < 0) {
            if (driver.target.isNative() && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "Map.containsValue on an Object-valued map is not supported"
                                + " on the native target yet (NAT002) — pin a concrete"
                                + " value type or use JVM/JS/Script", "NAT002");
                return true;
            }
            cvTag = 0;
        }
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, cvTag));
        return false;
    }
}
