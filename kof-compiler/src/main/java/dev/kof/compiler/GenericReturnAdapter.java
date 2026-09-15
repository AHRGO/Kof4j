package dev.kof.compiler;

import java.util.List;

/**
 * Ajuste do valor devolvido por uma chamada cujo retorno declarado é uma
 * variável de tipo {@code T} (apagada a {@code Object} no descritor JVM).
 *
 * <p>Quando o tipo EFETIVO do retorno é conhecido pela substituição
 * ({@code Box<String>} → {@code T=String}), o call-site precisa adaptar o
 * valor que saiu como {@code Object}:
 * <ul>
 *   <li>primitivo ({@code T=Int}) → unbox ({@code kof_unbox});</li>
 *   <li>referência concreta ({@code T=String}) → {@code checkcast} (#161);
 *       sem ele o {@code invokevirtual} seguinte recebe {@code Object} na
 *       pilha → {@code VerifyError: Bad type on operand stack}.</li>
 * </ul>
 * JS/Native ignoram {@code KofCheckCast} (no-op) — o ajuste é inofensivo lá.
 */
final class GenericReturnAdapter {

    private GenericReturnAdapter() {}

    static void emit(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            List<IRLocalVariable> locals, Type declaredReturn) {
        if (!(declaredReturn instanceof Type.TypeVariable)) return;
        Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
        if (TypeMetrics.isPrimitiveType(effective)) {
            driver.emitErasureUnbox(ops, effective);
            return;
        }
        Type ref = effective instanceof Type.NullableType nt ? nt.inner() : effective;
        boolean concrete = ref instanceof Type.ClassType || ref instanceof Type.ArrayType;
        if (concrete && !(ref instanceof Type.ClassType ct && "Object".equals(ct.name()))) {
            ops.add(new KofCheckCast(ref));
        }
    }
}
