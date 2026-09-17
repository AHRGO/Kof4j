package dev.kof.compiler;

import java.util.List;

/**
 * `var` de nome capturado-mutável (closure): o storage vira uma caixa
 * sintética (`BoxClass.value`) compartilhada entre o closure e o escopo
 * pai. Extraído de StatementLowerer (mesma semântica, 1:1) para manter o
 * lowering de statements no gate ≤500.
 */
public final class CapturedVarBox {

    private CapturedVarBox() {}

    static int emit(CompilerDriver driver, VarDeclStmt vds, ExpressionNode vdInit,
                    List<KofOperation> ops, String owner, int localIdx,
                    List<IRLocalVariable> locals) {
        Type initType = vdInit == null ? Type.PrimitiveType.INT
                : ExpressionTyper.inferExprType(driver, vdInit, locals);
        String boxName = driver.boxFactory.createBoxClass(initType, driver.syntheticClasses, driver.lambdaCounter);
        Type boxType = new Type.ClassType("", boxName, List.of());
        ops.add(new KofNewObject(boxType, List.of()));
        ops.add(new KofDup());
        ops.add(new KofCall(boxType, "<init>", List.of(),
                Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        // §253 face A (d): o box é definido no slot ANTES de avaliar o init.
        // `var id = time.interval(…, () -> cancel(id))` — a lambda criada
        // dentro do init captura o slot `id` por REF (findLocalVar resolve
        // o IRLocalVariable box-typed já registrado). Ordem antiga (store
        // depois do init) deixava o capture sem slot → leitura do valor
        // default no tick (id null/0 → cancel(0) silencioso). Invariantes
        // mantidas: (i) init não lê `id` de forma síncrona (o job só roda
        // no agendamento); (ii) para captures não-self o slot ainda não
        // existia no capture path (decl anterior) — comportamento idêntico.
        ops.add(new KofStoreLocal(boxType, localIdx));
        locals.add(new IRLocalVariable(localIdx, vds.name(), boxType));
        ops.add(new KofLoadLocal(boxType, localIdx));
        int nextFree = localIdx + 1;
        if (vdInit != null) {
            nextFree = ExpressionLowerer.emitExpression(driver, vdInit, ops, owner,
                    localIdx + 1, locals);
        } else {
            ops.add(new KofLoadLiteral(initType, 0));
        }
        ops.add(new KofStoreField(boxType, "value", initType));
        return nextFree;
    }
}
