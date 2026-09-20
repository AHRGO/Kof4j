package dev.kof.compiler;

import java.util.List;

/**
 * #549/§370 — argumento de `extern` convertido ao tipo do SLOT declarado.
 *
 * <p>O marshaling nativo (SysV/LP64/AAPCS64) lê os bits pela classe do slot e o
 * {@code kof_ffi} da JVM faz cast pelo wrapper do slot: um argumento que só o
 * {@code TypeChecker.isAssignable} do call-site aceitou (`Int→Float`,
 * `Double→Float`, `Int→Double`…) tem de chegar JÁ convertido — a MESMA política de
 * uma chamada comum ({@code CompilerEmission2.emitArgumentsWithFormalTypes}):
 * widening numérico + `Double→Float`; o resto é rejeitado antes, com SEM014.
 */
final class ExternArgumentCoercion {

    private ExternArgumentCoercion() {}

    /**
     * Converte o valor JÁ empilhado de {@code arg} para o primitivo {@code slot}.
     *
     * @return true quando o valor na pilha passou a ter o tipo do slot (o caller
     *         deve boxar/marshalar por ele); false = nada a converter.
     */
    static boolean coerce(CompilerDriver driver, ExpressionNode arg, Type slot,
                          List<KofOperation> ops, List<IRLocalVariable> locals) {
        if (!(slot instanceof Type.PrimitiveType)) return false;
        Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
        if (!(argType instanceof Type.PrimitiveType) || argType.equals(slot)) return false;
        driver.emitWideningIfNeeded(ops, argType, slot);
        return true;
    }
}
