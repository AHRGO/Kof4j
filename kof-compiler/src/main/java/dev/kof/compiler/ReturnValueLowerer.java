package dev.kof.compiler;

import java.util.List;

/**
 * Coerção do valor de `return <expr>` para o tipo declarado — extraído de
 * {@link StatementLowerer} (REFACTOR-500, §140) porque a mesma lógica se
 * repetia no caminho normal e no caminho try/finally (store no slot antes
 * do jump p/ o epílogo).
 */
final class ReturnValueLowerer {

    private ReturnValueLowerer() {}

    /**
     * Emite {@code ret.value()} já coagido para {@code returnType}: null
     * real p/ {@code return null} em Nullable(primitivo) (D-NULL-INTENT,
     * supersede §125 opção A — pula toda a maquinaria de widening/box
     * numérico, que pressupõe um primitivo concreto e tentaria "desempacotar"
     * um null); senão fold de ramos null (join heterogêneo, §125-ext),
     * widening, erasure-box (#169) e o box de Nullable(primitivo) que segue
     * o tipo ALVO (nunca a expressão de origem — bug atômico do boxer, ver
     * histórico N1). Deixa o valor coagido no topo da pilha; o chamador
     * decide {@code KofReturn} ou {@code KofStoreLocal} (retorno direto vs
     * dentro de try/finally).
     */
    static int emitCoerced(CompilerDriver driver, ReturnStmt ret, Type returnType,
                            List<KofOperation> ops, String owner, int localIdx,
                            List<IRLocalVariable> locals) {
        if (CompilerComparisons.isNullablePrimNullReturn(ret, returnType)) {
            return ExpressionLowerer.emitExpression(driver, ret.value(), ops, owner, localIdx, locals);
        }
        // D-NULL-INTENT (#278): o fold §125-ext (ramo null -> default) só
        // continua vivo no Native (fase 2 do rollout, DECISIONS.md — Native
        // mantém a representação antiga de Nullable(primitivo)). JVM/Script/
        // JS deixam o ramo null real e reusam a maquinaria genérica de join
        // heterogêneo (#57/§70, ifBranchTypes/boxesOwnBranches) — ela já
        // boxa o ramo primitivo p/ o SEU boxed e mantém aconst_null no outro,
        // exatamente a representação Absent|Present(T) que o contrato exige.
        ExpressionNode rv = driver.target.isNative()
                ? CompilerComparisons.foldNullablePrimBranches(ret.value(), returnType)
                : ret.value();
        localIdx = ExpressionLowerer.emitExpression(driver, rv, ops, owner, localIdx, locals);
        Type rvType = ExpressionTyper.inferExprType(driver, rv, locals);
        driver.emitWideningIfNeeded(ops, rvType, returnType);
        // Issue #169: retorno de primitivo de função tipo Object
        if (driver.erasesToReference(returnType)
                && TypeMetrics.isPrimitiveType(rvType)
                && !ExpressionTyper.boxesOwnBranches(driver, rv, locals)) {
            driver.emitErasureBox(ops, rvType);
        }
        // D-NULL-INTENT: destino Nullable(primitivo) recebendo um primitivo
        // CRU (rvType é PrimitiveType, não já Nullable) — box segue o tipo
        // ALVO. Um rvType já Nullable(mesmo inner) (ex.: `return outraOpt`,
        // `return m.get(k)`) já chega boxed/null — não reboxa. `rv` que já
        // se auto-boxou por dentro (if/switch heterogêneo, boxesOwnBranches)
        // também não reboxa — o "then" primitivo já virou Integer/Boolean
        // reais lá dentro; um segundo valueOf(referência) é VerifyError.
        if (returnType instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType
                && rvType instanceof Type.PrimitiveType pt && !Type.isVoid(pt)
                && !ExpressionTyper.boxesOwnBranches(driver, rv, locals)) {
            TypeEmitter.boxPrimitive(ops, returnType);
        }
        return localIdx;
    }
}
