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
        ops.add(new KofDup());
        if (vdInit != null) {
            localIdx = ExpressionLowerer.emitExpression(driver, vdInit, ops, owner, localIdx, locals);
        } else {
            ops.add(new KofLoadLiteral(initType, 0));
        }
        ops.add(new KofStoreField(boxType, "value", initType));
        ops.add(new KofStoreLocal(boxType, localIdx));
        locals.add(new IRLocalVariable(localIdx, vds.name(), boxType));
        return localIdx + 1;
    }
}
