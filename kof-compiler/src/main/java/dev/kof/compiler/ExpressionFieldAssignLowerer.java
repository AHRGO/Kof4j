package dev.kof.compiler;

import java.util.List;

/**
 * Lowering do AssignmentExpr com alvo FieldAccessExpr (x = field / field = v).
 * Extraído de ExpressionAssignmentLowerer (mesma semântica, 1:1, §253 face B
 * spill em slot de frame) para manter o lowering de atribuição no gate ≤500.
 */
public final class ExpressionFieldAssignLowerer {

    private ExpressionFieldAssignLowerer() {}

static int lowerField(CompilerDriver driver, AssignmentExpr ae, FieldAccessExpr fa,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    if (fa.receiver() instanceof IdentifierExpr rid && driver.semanticAnalyzer != null
            && driver.semanticAnalyzer.getClass(rid.name()) != null) {
        // Static field store: Class.field = value.
        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(rid.name());
        SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(cs.name(), fa.fieldName(), driver.semanticAnalyzer);
        if (fs instanceof SymbolTable.FieldSymbol fld) {
            String sfaOp = ae.operator();
            boolean sfaCompound = ExpressionAssignmentLowerer.isCompoundOp(sfaOp);
            // compound em campo ESTÁTICO qualificado (`Counter.total += 5`,
            // GitHub #64): getstatic antes do emit — sem receiver na pilha
            // (estático não consome this), a ordem simples do caminho por
            // nome simples basta.
            if (sfaCompound) {
                ops.add(new KofGetStatic(cs.type(), fa.fieldName(), fld.type()));
            }
            Type sfaValueType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
            boolean sfaConcat = sfaCompound && "+=".equals(sfaOp)
                    && (Type.isString(fld.type()) || Type.isString(sfaValueType));
            if (sfaConcat) {
                if (!Type.isString(fld.type()) && TypeMetrics.isPrimitiveType(fld.type())) {
                    TypeEmitter.boxPrimitive(ops, fld.type());
                }
                if (!Type.isString(fld.type())) {
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(driver.target.isNative() && !Type.isString(fld.type())
                                    && !(fld.type() instanceof Type.PrimitiveType)
                                    ? fld.type() : Type.UnknownType.UNKNOWN),
                            BuiltinTypes.STRING, KofCallKind.STATIC));
                }
            }
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            if (sfaConcat) {
                if (!Type.isString(sfaValueType) && TypeMetrics.isPrimitiveType(sfaValueType)) {
                    TypeEmitter.boxPrimitive(ops, sfaValueType);
                }
                if (!Type.isString(sfaValueType)) {
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(driver.target.isNative() && !Type.isString(sfaValueType)
                                    && !(sfaValueType instanceof Type.PrimitiveType)
                                    ? sfaValueType : Type.UnknownType.UNKNOWN),
                            BuiltinTypes.STRING, KofCallKind.STATIC));
                }
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            } else if (sfaCompound) {
                // RHS primitivo ≠ campo (ex.: Double *= int): widening p/ o
                // tipo do campo — o KofBinary usa fld.type() p/ o opcode e o
                // literal int na pilha de um DMUL daria frame inválido. Shift
                // (`<<=`) exige contagem int (L2I) e resultado no tipo do alvo.
                ExpressionAssignmentLowerer.emitCompoundRhsConv(driver, ops, sfaOp, fld.type(), sfaValueType);
                ops.add(new KofBinary(ExpressionAssignmentLowerer.compoundBinaryOp(sfaOp), fld.type()));
            } else if ("=".equals(sfaOp)) {
                if (TypeMetrics.isPrimitiveType(sfaValueType) && TypeMetrics.isPrimitiveType(fld.type())) {
                    driver.emitWideningIfNeeded(ops, sfaValueType, fld.type());
                } else if (driver.erasesToReference(fld.type())
                        && TypeMetrics.isPrimitiveType(sfaValueType)
                        && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
                    // Issue #181: campo estático Object recebendo primitivo (Holder.item = 99)
                    driver.emitErasureBox(ops, sfaValueType);
                }
            }
            ops.add(new KofPutStatic(cs.type(), fa.fieldName(), sfaConcat ? BuiltinTypes.STRING : fld.type()));
            return localIdx;
        }
    }
    Type faRecvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
    if (KofUi.isWindow(faRecvType) && "title".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_window_set_title", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "text".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "fontSize".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_font_size", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "bold".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_bold", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.BOOL),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "color".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_color", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isWindow(faRecvType) && "theme".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_window_set_theme", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isButton(faRecvType) && "text".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_button_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isInput(faRecvType) && "text".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_input_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isComponent(faRecvType) && "state".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_component_state_set", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    Type recvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
    // §246/#269: o emit-path não conhece o narrowing (que vive no escopo
    // semântico) — um receiver já validado como não-nulo chega aqui ainda
    // como `NullableType`. Desembrulhar espelha o READ (ExpressionLowerer);
    // sem isto o campo saía com owner `?` e tipo `Object` → `putfield`
    // inválido (VerifyError). O acesso NÃO-narrowed nunca chega aqui: o
    // StatementAnalyzer já o rejeita com SEM049.
    if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
    Type fieldType = Type.UnknownType.UNKNOWN;
    boolean isStaticField = false;
    if (recvType instanceof Type.ClassType ct) {
        SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
        if (fs instanceof SymbolTable.FieldSymbol fldSym) {
            fieldType = fldSym.type();
            isStaticField = (fldSym.accessFlags() & AccessFlags.STATIC) != 0;
        } else if (fs != null) {
            fieldType = fs.type();
        } else if (!ct.packageName().isEmpty()
                && driver.externalClasspath.knows(ct.internalName())) {
            String desc = driver.externalClasspath.resolveFieldType(
                    ct.internalName(), fa.fieldName());
            if (desc != null) fieldType = ExternalClasspath.typeFromDescriptor(desc);
        }
    }
    // §357/#295 (rio da erasure): slot `T[]` (apagado para Object[] no JVM)
    // recebendo array de PRIMITIVO (`new Int[10]` = `[I`) — o verifier
    // rejeita `[I` → `[Ljava/lang/Object;` (JVMS 4.10.1: int[] NÃO é subtipo
    // de Object[]; javac idem). Com a erasure correta do descritor (o bug
    // filed) a falha deixou de ser crash de load e passou a ser decisão de
    // tipo: SEM098 no ALVO JVM (gate de alvo, precedente SEM092 só em
    // NATIVE*; Script/JS/Native têm array dinâmico e continuam verdes —
    // R7/R6, nunca VerifyError escondido).
    if (driver.target == dev.kof.compiler.Target.JVM && !driver.interpreting
            && driver.currentDiagnostics != null) {
        Type faPreValueType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
        if (TypeParams.primitiveArrayIntoErasedRefArray(fieldType, faPreValueType)) {
            var gpos = fa.position();
            driver.currentDiagnostics.error(gpos != null ? gpos.file() : "",
                    gpos != null ? gpos.line() : 0, gpos != null ? gpos.column() : 0, 0,
                    "cannot store a primitive array (Int[]) into '" + fa.fieldName()
                            + "' of erased reference-array type T[] (erases to Object[] on the"
                            + " JVM — int[] is not a subtype of Object[]). Use List<Int> (the Kof"
                            + " idiom for a growable sequence of primitives) or a reference-typed"
                            + " array slot",
                    "SEM098");
            return localIdx;
        }
    }
    int faRecvSlot = -1;
    if (!isStaticField) {
        // §253 face B (campo de instância): o receiver NAO pode ficar na pilha
        // de maquina durante o RHS — um push impar cruzando os calls do RHS
        // deixa todo call com rsp%16==8 e o SSE da libc SIGSEGVa no callee
        // (mesmo mecanismo do box capturado; a glibc e vitima, a pilha e o
        // bug). Derrama o receiver num slot de frame; a ordem de efeitos fica
        // preservada (receiver ainda avalia antes do RHS em todos backends).
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        ops.add(new KofStoreLocal(recvType, localIdx));
        locals.add(new IRLocalVariable(localIdx, "#fldrecv" + localIdx, recvType));
        faRecvSlot = localIdx;
        localIdx = localIdx + (TypeMetrics.isDoubleWidth(recvType) ? 2 : 1);
    }
    String faOp = ae.operator();
    int faCurSlot = -1;
    int faValSlot = -1;
    if (ExpressionAssignmentLowerer.isCompoundOp(faOp)) {
        if (isStaticField) {
            ops.add(new KofGetStatic(recvType, fa.fieldName(), fieldType));
        } else {
            ops.add(new KofLoadLocal(recvType, faRecvSlot));
            ops.add(new KofLoadField(recvType, fa.fieldName(), fieldType));
        }
        // `cur` atravessaria o RHS na pilha de maquina (1 push impar) — derrama.
        ops.add(new KofStoreLocal(fieldType, localIdx));
        locals.add(new IRLocalVariable(localIdx, "#fldcur" + localIdx, fieldType));
        faCurSlot = localIdx;
        localIdx = localIdx + (TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1);
    }
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
    boolean faCompound = ExpressionAssignmentLowerer.isCompoundOp(faOp);
    Type faValType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
    if (faCompound) {
        // §103.2 (#103): widening do valor p/ o tipo do campo (h.value = n,
        // Int→Long); no shift (`<<=`) a contagem é int (L2I) — regra do §167.
        ExpressionAssignmentLowerer.emitCompoundRhsConv(driver, ops, faOp, fieldType, faValType);
        // o binario quer [cur, rhs]; derrama o rhs e recolhe na ordem certa
        // (commutatividade NAO pode ser assumida — sub/div/shift).
        ops.add(new KofStoreLocal(fieldType, localIdx));
        locals.add(new IRLocalVariable(localIdx, "#fldrhs" + localIdx, fieldType));
        faValSlot = localIdx;
        localIdx = localIdx + (TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1);
        ops.add(new KofLoadLocal(fieldType, faCurSlot));
        ops.add(new KofLoadLocal(fieldType, faValSlot));
        ops.add(new KofBinary(ExpressionAssignmentLowerer.compoundBinaryOp(faOp), fieldType));
    } else if ("=".equals(faOp)) {
        if (TypeMetrics.isPrimitiveType(fieldType)) {
            driver.emitWideningIfNeeded(ops, faValType, fieldType);
        } else if (driver.erasesToReference(fieldType)
                && TypeMetrics.isPrimitiveType(faValType)
                && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
            // Issue #181: atribuição de primitivo a campo tipo Object (h.field = 99)
            driver.emitErasureBox(ops, faValType);
        }
    }
    if (isStaticField) {
        ops.add(new KofPutStatic(recvType, fa.fieldName(), fieldType));
    } else {
        // pilha aqui so carrega [value]; o par final [receiver, value] nasce
        // depois do ultimo call do RHS e e consumido em sequencia pelo store.
        ops.add(new KofStoreLocal(fieldType, localIdx));
        locals.add(new IRLocalVariable(localIdx, "#fldval" + localIdx, fieldType));
        ops.add(new KofLoadLocal(recvType, faRecvSlot));
        ops.add(new KofLoadLocal(fieldType, localIdx));
        localIdx = localIdx + (TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1);
        ops.add(new KofStoreField(recvType, fa.fieldName(), fieldType));
    }
    return localIdx;
}
}
