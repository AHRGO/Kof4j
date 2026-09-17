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
        int nextFree = localIdx + 1;
        // §253 face B: o receiver-box NAO pode ficar na pilha de maquina durante
        // a avaliacao do init (um push impar cruzando os calls do init deixa
        // todo call com rsp%16==8 e o SSE da libc SIGSEGVa no callee). Avalia o
        // init primeiro, derrama o valor num slot de frame, e so entao empilha
        // [box, value] para o putfield. O slot do box ja esta registrado acima
        // (a lambda do init captura por referencia ao slot, nao ao stack).
        if (vdInit != null) {
            nextFree = ExpressionLowerer.emitExpression(driver, vdInit, ops, owner,
                    localIdx + 1, locals);
        } else {
            ops.add(new KofLoadLiteral(initType, 0));
        }
        ops.add(new KofStoreLocal(initType, nextFree));
        locals.add(new IRLocalVariable(nextFree, "$boxinit" + nextFree, initType));
        ops.add(new KofLoadLocal(boxType, localIdx));
        ops.add(new KofLoadLocal(initType, nextFree));
        ops.add(new KofStoreField(boxType, "value", initType));
        return nextFree + (TypeMetrics.isDoubleWidth(initType) ? 2 : 1);
    }
}
