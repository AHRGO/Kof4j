package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de AssignmentExpr (case do emitExpression).
 */
public final class ExpressionAssignmentLowerer {

    private ExpressionAssignmentLowerer() {}

    static int lower(CompilerDriver driver, AssignmentExpr ae, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
if (ae.target() instanceof IdentifierExpr ie && !owner.isEmpty()) {
    boolean isLocal = false;
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(ie.name())) { isLocal = true; break; }
    }
    if (!isLocal) {
        String className = owner.substring(owner.lastIndexOf('/') + 1);
        SymbolTable.Symbol fieldSym = driver.semanticAnalyzer != null
                ? HierarchyResolver.resolveFieldInHierarchy(className, ie.name(), driver.semanticAnalyzer) : null;
        if (fieldSym != null
                && (fieldSym instanceof SymbolTable.FieldSymbol
                || (fieldSym instanceof SymbolTable.MethodSymbol ms
                        && ms.parameterTypes().isEmpty()))) {
            Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
            // campo ESTÁTICO por nome simples (ex.: `count = count + 1` em
            // bump()): GETSTATIC/PUTSTATIC — sem this (LoadLocal(0) quebraria
            // método estático: aload_0 sem receiver).
            if (fieldSym instanceof SymbolTable.FieldSymbol fsStatic
                    && (fsStatic.accessFlags() & AccessFlags.STATIC) != 0) {
                String sop = ae.operator();
                boolean compound = isCompoundOp(sop);
                if (compound) {
                    ops.add(new KofGetStatic(ownerType, ie.name(), fsStatic.type()));
                }
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                if (compound) {
                    emitCompoundRhsConv(driver, ops, sop, fsStatic.type(),
                            ExpressionTyper.inferExprType(driver, ae.value(), locals));
                    ops.add(new KofBinary(compoundBinaryOp(sop), fsStatic.type()));
                }
                ops.add(new KofPutStatic(ownerType, ie.name(), fsStatic.type()));
                return localIdx;
            }
            ops.add(new KofLoadLocal(ownerType, 0));
            String op = ae.operator();
            if (isCompoundOp(op)) {
                // compound em CAMPO de instância: o getfield consome o `this`
                // e o putfield precisa dele de novo — duplica antes (bug 40:
                // stack underflow no putfield, `n += 1` em método de instância).
                ops.add(new KofDup());
                ops.add(new KofLoadField(ownerType, ie.name(), fieldSym.type()));
            }
            boolean compoundAsgn = isCompoundOp(op);
            // #194 — `s += t` em campo String de INSTÂNCIA: o caminho de
            // campo de instância não tinha o tratamento de concatenação que
            // o de campo estático já tinha (linhas ~98-126), então caía em
            // KofBinary(ADD, String) → `iadd` → VerifyError. Espelha o
            // caminho estático: box do primitivo + valueOf + kof_string_concat.
            Type instValType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
            boolean instConcat = compoundAsgn && "+=".equals(op)
                    && (Type.isString(fieldSym.type()) || Type.isString(instValType));
            if (instConcat) {
                if (!Type.isString(fieldSym.type()) && TypeMetrics.isPrimitiveType(fieldSym.type())) {
                    TypeEmitter.boxPrimitive(ops, fieldSym.type());
                }
                if (!Type.isString(fieldSym.type())) {
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(driver.target.isNative() && !Type.isString(fieldSym.type())
                                    && !(fieldSym.type() instanceof Type.PrimitiveType)
                                    ? fieldSym.type() : Type.UnknownType.UNKNOWN),
                            BuiltinTypes.STRING, KofCallKind.STATIC));
                }
            }
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            // §103.2 (#103): widening do valor p/ o tipo do CAMPO — Int→Long
            // putfield sem I2L → VerifyError (this.value = n, campo Long,
            // param Int). O caminho estático (~158) e o de array-store já
            // faziam; o de campo de instância não fazia NEM simples NEM
            // composto (RHS Int num LADD também quebra o frame).
            if (instConcat) {
                if (!Type.isString(instValType) && TypeMetrics.isPrimitiveType(instValType)) {
                    TypeEmitter.boxPrimitive(ops, instValType);
                }
                if (!Type.isString(instValType)) {
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(driver.target.isNative() && !Type.isString(instValType)
                                    && !(instValType instanceof Type.PrimitiveType)
                                    ? instValType : Type.UnknownType.UNKNOWN),
                            BuiltinTypes.STRING, KofCallKind.STATIC));
                }
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            } else if (TypeMetrics.isPrimitiveType(fieldSym.type()) && compoundAsgn) {
                emitCompoundRhsConv(driver, ops, op, fieldSym.type(), instValType);
            } else if ("=".equals(op)) {
                if (TypeMetrics.isPrimitiveType(instValType)
                        && TypeMetrics.isPrimitiveType(fieldSym.type())) {
                    driver.emitWideningIfNeeded(ops, instValType, fieldSym.type());
                } else if (driver.erasesToReference(fieldSym.type())
                        && TypeMetrics.isPrimitiveType(instValType)
                        && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
                    // Issue #181: campo de instância declarado Object recebendo primitivo (this.item = 99)
                    driver.emitErasureBox(ops, instValType);
                }
            }
            if (compoundAsgn && !instConcat) {
                ops.add(new KofBinary(compoundBinaryOp(op), fieldSym.type()));
            }
            ops.add(new KofStoreField(ownerType, ie.name(),
                    instConcat ? BuiltinTypes.STRING : fieldSym.type()));
            return localIdx;
        }
    }
}
if (ae.target() instanceof FieldAccessExpr fa) {
    if (fa.receiver() instanceof IdentifierExpr rid && driver.semanticAnalyzer != null
            && driver.semanticAnalyzer.getClass(rid.name()) != null) {
        // Static field store: Class.field = value.
        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(rid.name());
        SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(cs.name(), fa.fieldName(), driver.semanticAnalyzer);
        if (fs instanceof SymbolTable.FieldSymbol fld) {
            String sfaOp = ae.operator();
            boolean sfaCompound = isCompoundOp(sfaOp);
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
                emitCompoundRhsConv(driver, ops, sfaOp, fld.type(), sfaValueType);
                ops.add(new KofBinary(compoundBinaryOp(sfaOp), fld.type()));
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
    if (!isStaticField) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
    }
    String faOp = ae.operator();
    if (isCompoundOp(faOp)) {
        if (isStaticField) {
            ops.add(new KofGetStatic(recvType, fa.fieldName(), fieldType));
        } else {
            ops.add(new KofDup());
            ops.add(new KofLoadField(recvType, fa.fieldName(), fieldType));
        }
    }
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
    boolean faCompound = isCompoundOp(faOp);
    Type faValType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
    if (faCompound) {
        // §103.2 (#103): widening do valor p/ o tipo do campo (h.value = n,
        // Int→Long); no shift (`<<=`) a contagem é int (L2I) — regra do §167.
        emitCompoundRhsConv(driver, ops, faOp, fieldType, faValType);
        ops.add(new KofBinary(compoundBinaryOp(faOp), fieldType));
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
        ops.add(new KofStoreField(recvType, fa.fieldName(), fieldType));
    }
    return localIdx;
}
if (ae.target() instanceof ArrayAccessExpr aa) {
    localIdx = ExpressionLowerer.emitExpression(driver, aa.receiver(), ops, owner, localIdx, locals);
    localIdx = ExpressionLowerer.emitExpression(driver, aa.index(), ops, owner, localIdx, locals);
    Type aaRecvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
    Type aaElemType = Type.arrayElementType(aaRecvType);
    String aaOp = ae.operator();
    boolean aaCompound = isCompoundOp(aaOp);
    if (aaCompound) {
        // compound em ELEMENTO de array (`values[0] += 5`, GitHub #64): o
        // stack do aaload é [receiver, index] — duplica os 2 e carrega o
        // valor atual; o store consome um par + o resultado.
        ops.add(new KofDup2());
        ops.add(new KofArrayLoad(aaElemType));
    }
    Type aaValueType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
    boolean aaStringConcat = aaCompound && "+=".equals(aaOp)
            && (Type.isString(aaElemType) || Type.isString(aaValueType));
    if (aaStringConcat && !Type.isString(aaElemType) && TypeMetrics.isPrimitiveType(aaElemType)) {
        TypeEmitter.boxPrimitive(ops, aaElemType);
    }
    if (aaStringConcat && !Type.isString(aaElemType)) {
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(driver.target.isNative() && !Type.isString(aaElemType)
                        && !(aaElemType instanceof Type.PrimitiveType)
                        ? aaElemType : Type.UnknownType.UNKNOWN),
                BuiltinTypes.STRING, KofCallKind.STATIC));
    }
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
    if (aaStringConcat) {
        if (!Type.isString(aaValueType) && TypeMetrics.isPrimitiveType(aaValueType)) {
            TypeEmitter.boxPrimitive(ops, aaValueType);
        }
        if (!Type.isString(aaValueType)) {
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(driver.target.isNative() && !Type.isString(aaValueType)
                            && !(aaValueType instanceof Type.PrimitiveType)
                            ? aaValueType : Type.UnknownType.UNKNOWN),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        }
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
    } else if (aaCompound) {
        // RHS primitivo ≠ elemento (ex.: Int[] += int ok, Long[] += int
        // precisa widening) — o KofBinary usa aaElemType p/ o opcode. Shift
        // (`<<=`) exige contagem int (L2I) — regra do §167.
        emitCompoundRhsConv(driver, ops, aaOp, aaElemType, aaValueType);
        ops.add(new KofBinary(compoundBinaryOp(aaOp), aaElemType));
    }
    if (!aaStringConcat && !aaCompound) {
        // §121: valor primitivo ≠ slot (ex.: Int em Long[]) → converter no IR
        // (I2L/L2I) ANTES do store, senão o emit gera lastore/aastore com tipo
        // errado e o verifier rejeita (frame crash COMP002 em
        // `new Long[4]; c[1] = 9`). O bloco existia como comentário-vazio
        // (prometia a conversão, nunca a emitiu) — mesma linha que o caminho
        // compound logo acima já aplica. NO compound NÃO duplicar: lá o
        // widening do RHS já foi feito antes do KofBinary.
        if (TypeMetrics.isPrimitiveType(aaValueType)
                && TypeMetrics.isPrimitiveType(aaElemType)) {
            driver.emitWideningIfNeeded(ops, aaValueType, aaElemType);
        }
    }
    ops.add(new KofArrayStore(aaStringConcat ? BuiltinTypes.STRING : aaElemType));
    return localIdx;
}
if (ae.target() instanceof IdentifierExpr ieBox) {
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(ieBox.name()) && driver.boxFactory.isBoxType(locals.get(i).type())) {
            IRLocalVariable boxLv = locals.get(i);
            String op = ae.operator();
            Type valType = driver.boxFactory.boxValueType(boxLv.type());
            // #192 — compound (`+=`/`-=`/`*=`) e concat em variável capturada:
            // o `getfield value` consome a referência do box e o `putfield`
            // precisa dela de novo — duplicar antes (o caminho de campo de
            // instância já fazia; aqui faltava → VerifyError "Operand stack
            // underflow" no putfield do invoke() da lambda).
            if ("+=".equals(op) && BuiltinTypes.isString(valType)) {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                ops.add(new KofDup());
                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                        KofCallKind.STATIC));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
            } else if (isCompoundOp(op)) {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                ops.add(new KofDup());
                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                emitCompoundRhsConv(driver, ops, op, valType,
                        ExpressionTyper.inferExprType(driver, ae.value(), locals));
                ops.add(new KofBinary(compoundBinaryOp(op), valType));
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
            } else {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), valType);
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
            }
            return localIdx;
        }
    }
}
// composto sobre local: LHS empurrado ANTES do RHS (a ordem do
// binário é lhs op rhs). O caminho antigo empurrava o RHS na
// linha compartilhada e o LHS depois → `a -= 2` virava `2 - 10`
// (bugs 2 e 3: resultado errado + stack extra no concat de s+=).
if (ae.target() instanceof IdentifierExpr cie) {
    IRLocalVariable targetLocal = null;
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(cie.name())) { targetLocal = locals.get(i); break; }
    }
    if (targetLocal != null) {
        String op = ae.operator();
        if ("+=".equals(op) && BuiltinTypes.isString(targetLocal.type())) {
            ops.add(new KofLoadLocal(targetLocal.type(), targetLocal.index()));
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                    KofCallKind.STATIC));
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                    KofCallKind.STATIC));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            ops.add(new KofStoreLocal(targetLocal.type(), targetLocal.index()));
            return localIdx;
        } else if (isCompoundOp(op)) {
            ops.add(new KofLoadLocal(targetLocal.type(), targetLocal.index()));
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            // conversão ANTES do binário (shift: contagem int via L2I).
            emitCompoundRhsConv(driver, ops, op, targetLocal.type(),
                    ExpressionTyper.inferExprType(driver, ae.value(), locals));
            ops.add(new KofBinary(compoundBinaryOp(op), targetLocal.type()));
            ops.add(new KofStoreLocal(targetLocal.type(), targetLocal.index()));
            return localIdx;
        }
    }
}
// atribuição simples: empurra o RHS e guarda no slot do local
localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
if (ae.target() instanceof IdentifierExpr sie) {
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(sie.name())) {
            driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), locals.get(i).type());
            // bug 15: `Object o; o = 7` — box primitivo p/ referência
            // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular)
            if (driver.erasesToReference(locals.get(i).type())
                    && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, ae.value(), locals))
                    && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
                driver.emitErasureBox(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals));
            }
            ops.add(new KofStoreLocal(locals.get(i).type(), locals.get(i).index()));
            return localIdx;
        }
    }
}
ops.add(new KofStoreLocal(Type.UnknownType.UNKNOWN, localIdx));
return localIdx;
    }

    private static KofBinaryOp compoundBinaryOp(String op) {
        return switch (op) {
            case "+=" -> KofBinaryOp.ADD;
            case "-=" -> KofBinaryOp.SUB;
            case "*=" -> KofBinaryOp.MUL;
            case "/=" -> KofBinaryOp.DIV;
            case "%=" -> KofBinaryOp.MOD;
            case "&=" -> KofBinaryOp.AND;
            case "|=" -> KofBinaryOp.OR;
            case "^=" -> KofBinaryOp.XOR;
            case "<<=" -> KofBinaryOp.SHL;
            case ">>=" -> KofBinaryOp.SHR;
            case ">>>=" -> KofBinaryOp.USHR;
            default -> KofBinaryOp.ADD;
        };
    }

    /**
     * Operador de atribuição composta reconhecido pelo lowering. O parser
     * aceita `<<=`, `>>=`, `>>>=` (Lexer/ExpressionParser), mas o lowerer não
     * os tratava como compostos → caíam no ramo de atribuição SIMPLES e só o
     * RHS era gravado (`x = 6; x <<= 2` virava `x = 2`, não 24) — bug de
     * correção silencioso nos 4 targets (achado 13/09 na caça Q4 do
     * translator, que emite `<<=`).
     */
    static boolean isCompoundOp(String op) {
        return switch (op) {
            case "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
                 "<<=", ">>=", ">>>=" -> true;
            default -> false;
        };
    }

    /** Operador de atribuição composta de shift (`<<=`, `>>=`, `>>>=`). */
    static boolean isShiftAssignOp(String op) {
        return "<<=".equals(op) || ">>=".equals(op) || ">>>=".equals(op);
    }

    /**
     * Conversão do RHS já empilhado para o composto. No shift o JVM usa
     * `lshl`/`ishl` com contagem SEMPRE int (`(long,int)`/`(int,int)`) e o
     * resultado tem o tipo PROMOVIDO do operando esquerdo (JLS 15.19) — o RHS
     * long precisa de L2I, nunca de widening p/ o tipo do alvo (§167 no
     * caminho binário; aqui no composto). Nos demais compostos, widening do
     * RHS p/ o tipo do alvo (ex.: campo Long `+=` Int).
     */
    static void emitCompoundRhsConv(CompilerDriver driver, List<KofOperation> ops,
                                    String op, Type targetType, Type valueType) {
        if (isShiftAssignOp(op)) {
            driver.emitPrimNarrow(ops, valueType, Type.PrimitiveType.INT);
        } else if (TypeMetrics.isPrimitiveType(valueType)
                && TypeMetrics.isPrimitiveType(targetType)) {
            driver.emitWideningIfNeeded(ops, valueType, targetType);
        }
    }
}