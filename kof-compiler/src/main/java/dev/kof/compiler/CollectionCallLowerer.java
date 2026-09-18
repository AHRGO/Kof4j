package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de métodos de coleção (List/Channel/Map/Set) no emitExpression.
 * Retorna -1 se nenhum método de coleção foi reconhecido (cai no genérico).
 */
public final class CollectionCallLowerer {

    private CollectionCallLowerer() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    if (BuiltinTypes.isList(recvType)
            && ("map".equals(mc.methodName()) || "filter".equals(mc.methodName())
                || "reduce".equals(mc.methodName()))) {
        String hoFn = "kof_list_" + mc.methodName();
        // receiver já empilhado acima (3396) — não duplicar
        Type lambdaT = Type.UnknownType.UNKNOWN;
        // reduce: init antes; lambda por último
        for (ExpressionNode arg : mc.arguments()) {
            if (!(arg instanceof LambdaExpr)) {
                Type argT = ExpressionTyper.inferExprType(driver, arg, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular)
                if (TypeMetrics.isPrimitiveType(argT) && driver.target == Target.JVM
                        && !ExpressionTyper.boxesOwnBranches(driver, arg, locals)) {
                    Type boxed = TypeMetrics.boxedTypeFor(argT);
                    ops.add(new KofCall(boxed, "kof_box", List.of(argT), boxed, KofCallKind.FUNCTION));
                }
            }
        }
        for (ExpressionNode arg : mc.arguments()) {
            if (arg instanceof LambdaExpr lam) {
                lambdaT = ExpressionTyper.inferExprType(driver, lam, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, lam, ops, owner, localIdx, locals);
            }
        }
        List<Type> callParams = new ArrayList<>();
        callParams.add(new Type.ClassType("java.util", "ArrayList", List.of()));
        if ("reduce".equals(mc.methodName())) callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        Type ret;
        if ("filter".equals(mc.methodName())) ret = recvType;
        else if ("map".equals(mc.methodName())) {
            Type elem = (lambdaT instanceof Type.FunctionType ft && !(ft.returnType() instanceof Type.UnknownType)) ? ft.returnType() : Type.UnknownType.UNKNOWN;
            ret = new Type.ClassType("kof", "List", List.of(elem));
        } else {
            ret = (lambdaT instanceof Type.FunctionType ft) ? ft.returnType() : Type.UnknownType.UNKNOWN;
        }
        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()), hoFn, callParams, ret,
                KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofProcess.isHandle(recvType)) {
        // F10: h.write/readLine/exitCode/kill/alive — o handle
        // empilhado entra como 1º parâmetro do call estático
        KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(),
                mc.arguments().stream().map(a -> ExpressionTyper.inferExprType(driver, a, locals)).toList());
        if (hm != null) {
            List<Type> params = new ArrayList<>();
            params.add(KofProcess.HANDLE);
            for (int pi = 1; pi < hm.parameterTypes().size(); pi++) {
                params.add(hm.parameterTypes().get(pi));
            }
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    hm.function(), params, hm.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
    }
    if (BuiltinTypes.isChannel(recvType)) {
        // Canais tipados: c.send(v) enfileira; c.receive() retira.
        // O receiver (Channel) está empilhado; o elemento vai
        // após — o backend faz a ordem (send: chan,elem; receive: chan).
        Type elemT = BuiltinTypes.channelElement(recvType);
        if ("send".equals(mc.methodName()) && mc.arguments().size() == 1) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, "kof_channel_send", List.of(elemT),
                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
            return localIdx;
        }
        if ("receive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            ops.add(new KofCall(recvType, "kof_channel_receive", List.of(),
                    elemT, KofCallKind.INSTANCE));
            return localIdx;
        }
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Channel' (valid: send, receive)",
                    "SEM025");
            return localIdx;
        }
    }
    if (BuiltinTypes.isList(recvType)) {
        String listFn = switch (mc.methodName()) {
            case "add", "push", "append" -> "kof_list_add";
            case "get" -> "kof_list_get";
            case "set" -> "kof_list_set";
            case "size", "length", "count" -> "kof_list_size";
            case "contains" -> "kof_list_contains";
            case "isEmpty" -> "kof_list_is_empty";
            case "remove" -> "kof_list_remove";
            case "clear" -> "kof_list_clear";
            default -> null;
        };
        // R6: método desconhecido em List não pode ser silencioso (bug Set.first)
        if (listFn == null && driver.currentDiagnostics != null
                && !"toArray".equals(mc.methodName()) && !"sublist".equals(mc.methodName())
                && !"subSet".equals(mc.methodName()) && !"map".equals(mc.methodName())
                && !"filter".equals(mc.methodName()) && !"reduce".equals(mc.methodName())) {
            String m = mc.methodName();
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + m + "' on type 'List' (valid: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce)",
                    "SEM025");
            return localIdx;
        }
        if (listFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type elemType = driver.listElementType(recvType);
            // §122 (opção B, família SEM051/052/053/054): o índice de
            // get/set/remove é Int (learn/12: remove(0) devolve o elemento);
            // String/record/array no índice era ACEITO em silêncio e quebrava
            // feio: JVM VerifyError "not assignable to integer" na carga da
            // classe, Native usa o PONTEIRO como índice (array index out of
            // bounds — RM/RM5/IX/IX2 probes 11/09). Unknown/Nullable NÃO
            // flagados (SG-008: pode chegar Int em runtime); numéricos passam
            // (Int é o contrato; o verifier cuida do resto).
            if (("kof_list_get".equals(listFn) || "kof_list_set".equals(listFn)
                    || "kof_list_remove".equals(listFn))
                    && !argTypes.isEmpty() && driver.currentDiagnostics != null) {
                Type idxT = argTypes.get(0);
                if (isReferenceIndexType(idxT)) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "List." + mc.methodName() + " takes an Int INDEX; " + CollectionWrites.typeNameFor(idxT)
                                    + " is not an index (to search by value use contains)",
                            "SEM055");
                    return localIdx;
                }
            }
                // listOf() with no type argument produces
            // List<Unknown>; the first add() pins the element
            // type on the local so later get() calls are
            // typed (records, classes) instead of Object.
            if ("kof_list_add".equals(listFn) || "kof_list_set".equals(listFn)) {
                // §126 (ii): add/set de tipo ≠ elemType PINADA polui o heap —
                // o JVM já VerifyError no get/unbox, o Native SIGSEGV no
                // scan com tag String. Rejeitar em compile-time (SEM056).
                // Só quando elemType já é concreto (o add que PINA um
                // List<Unknown> não é poluição — é a definição do tipo).
                // set: o VALOR é o arg 1 (o índice já foi checado em SEM055).
                int valIdx = "kof_list_set".equals(listFn) ? 1 : 0;
                if (argTypes.size() > valIdx && CollectionWrites.pollutesPinned(elemType, argTypes.get(valIdx))
                        && driver.currentDiagnostics != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "List." + mc.methodName() + ": element " + CollectionWrites.typeNameFor(argTypes.get(valIdx))
                                    + " does not match the list element type (" + CollectionWrites.typeNameFor(elemType)
                                    + ") — Kof collections are homogeneous",
                            "SEM056");
                    return localIdx;
                }
            }
            if ("kof_list_add".equals(listFn)
                    && Type.UnknownType.UNKNOWN.equals(elemType)
                    && !argTypes.isEmpty()
                    && !(argTypes.get(0) instanceof Type.UnknownType)
                    && mc.receiver() instanceof IdentifierExpr rid) {
                for (int li = 0; li < locals.size(); li++) {
                    IRLocalVariable lv = locals.get(li);
                    if (lv.name().equals(rid.name())) {
                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                new Type.ClassType("kof", "List", List.of(argTypes.get(0)))));
                        break;
                    }
                }
            }
            // §121/§126 (B1): o widening numérico ABENÇOADO pelo §126
            // ("Int em Long passa") precisa da CONVERSÃO no IR antes do
            // store — sem ela o JVM boxeia pelo tipo PINADO (Long.valueOf(J))
            // sobre um int cru na pilha (arg empurrado width-1) → VerifyError
            // "integer not assignable to long_2nd" (B1a), e o unbox do get dá
            // CCE. Mesmo mecanismo do array-store §121 (emitWideningIfNeeded,
            // que SÓ promove I2L/I2F/I2D/L2F/L2D/D2F — nunca trunca). Só o
            // VALOR armazenado (add arg0, set arg1); índice (set arg0) e as
            // buscas contains/get ficam raw (família §126 miss, intocada).
            // §121/§126 (B1): widening numérico abençoado no VALOR (add arg0,
            // set arg1) precisa da conversão IR antes do store — sem ela o
            // JVM boxeia pelo tipo PINADO sobre arg cru (VerifyError/CCE).
            // Índice (set arg0) e buscas ficam raw (família §126 miss).
            int storeValIdx = "kof_list_set".equals(listFn) ? 1 : 0;
            localIdx = CompilerEmissionHelpers.emitArgsCoercingValue(driver, mc, ops, owner,
                    localIdx, locals, argTypes, elemType,
                    ("kof_list_add".equals(listFn) || "kof_list_set".equals(listFn)) ? storeValIdx : -1);
            Type retType = switch (listFn) {
                case "kof_list_add", "kof_list_set", "kof_list_clear" -> Type.PrimitiveType.VOID;
                case "kof_list_contains", "kof_list_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_list_remove" -> elemType;
                default -> elemType;
            };
            if ("kof_list_contains".equals(listFn)) {

                // §126: equals de String só quando AMBOS elemType e arg são
                // String conhecidos; senão raw cmpq (nunca deref → miss seguro
                // = false do JVM). Int-arg em String-list era SIGSEGV (E1).
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                        CollectionWrites.stringTag(elemType, argTypes, 0)));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            ops.add(new KofCall(recvType, listFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    if (BuiltinTypes.isMap(recvType)) {

        String mapFn = switch (mc.methodName()) {
            case "put" -> "kof_map_put";
            case "get" -> "kof_map_get";
            case "getOrDefault" -> "kof_map_get_or_default";
            case "remove" -> "kof_map_remove";
            case "containsKey", "contains" -> "kof_map_contains";
            case "size", "length", "count" -> "kof_map_size";
            case "clear" -> "kof_map_clear";
            case "isEmpty" -> "kof_map_is_empty";
            case "keys" -> "kof_map_keys";
            case "values" -> "kof_map_values";
            default -> null;
        };
        if (mapFn == null && driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Map' (valid: put/get/getOrDefault/remove/containsKey/contains/size/clear/isEmpty/keys/values)",
                    "SEM025");
            return localIdx;
        }
        if (mapFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type keyType = Type.UnknownType.UNKNOWN;
            Type valueType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                keyType = ct.typeArguments().get(0);
                valueType = ct.typeArguments().get(1);
            }
            // mapOf() nasce Map<Unknown,Unknown>: o primeiro put()
            // pina os tipos no local para que get()/remove() tenham
            // tipo concreto (comparações e unboxing corretos)
            if ("kof_map_put".equals(mapFn)
                    && keyType instanceof Type.UnknownType
                    && argTypes.size() == 2
                    && !(argTypes.get(0) instanceof Type.UnknownType)
                    && mc.receiver() instanceof IdentifierExpr rid) {
                for (int li = 0; li < locals.size(); li++) {
                    IRLocalVariable lv = locals.get(li);
                    if (lv.name().equals(rid.name())) {
                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                new Type.ClassType("kof", "Map", List.of(argTypes.get(0), argTypes.get(1)))));
                        break;
                    }
                }
                // #103 caso 3: o pin acima muda o TIPO DO LOCAL, mas este
                // lowering continua usando o valueType/keyType lidos do
                // receiver ANTES do pin (Unknown). Sem alinhá-los, o
                // retType do KofCall sai Unknown e emitPrevValueUnbox é no-op
                // (put deixa 1 Object na pilha) — mas o typer da statement
                // (SemMethodCallTyper, que roda o mesmo pin) já vê Map<K,Long>
                // e descarta com POP2. 1 slot empilhado × POP2 = underflow do
                // frame (VerifyError / "frame crash"). Alinhar ao pinado casa
                // o unbox (Object→long, 2 slots) com o descarte.
                keyType = argTypes.get(0);
                valueType = argTypes.get(1);
            }
            // §126 (ii): put CHAVE ou VALOR de tipo ≠ pinado polui o mapa.
            // Chave errada = scan tag=1 sobre Int cru → SIGSEGV no Native
            // (A2/H4); valor errado = ClassCastException no JVM no get/unbox.
            // Rejeição cobre os dois lados (decisão "put heterogêneo").
            if (("kof_map_put".equals(mapFn) || "kof_map_get_or_default".equals(mapFn))
                    && driver.currentDiagnostics != null) {
                String badSlot = null; Type badType = null, slotType = null;
                int valIdx = 1;
                if (argTypes.size() >= 1 && CollectionWrites.pollutesPinned(keyType, argTypes.get(0))) {
                    badSlot = "chave"; badType = argTypes.get(0); slotType = keyType;
                } else if (argTypes.size() > valIdx && CollectionWrites.pollutesPinned(valueType, argTypes.get(valIdx))) {
                    badSlot = "valor"; badType = argTypes.get(valIdx); slotType = valueType;
                }
                if (badSlot != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "Map.put: " + badSlot + " " + CollectionWrites.typeNameFor(badType)
                                    + " does not match the map type (" + CollectionWrites.typeNameFor(slotType)
                                    + ") — Kof collections are homogeneous",
                            "SEM056");
                    return localIdx;
                }
            }
            Type retType = switch (mapFn) {
                case "kof_map_put", "kof_map_remove", "kof_map_get_or_default" -> valueType;
                // get() devolve V? (SG-008/bug 87): ausência é null comparável
                // (`x == null`), nunca NPE por unbox. O unbox acontece no
                // USE (aritmética), guiado pelo tipo do slot.
                case "kof_map_get" -> new Type.NullableType(valueType);
                case "kof_map_contains", "kof_map_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_map_size" -> Type.PrimitiveType.INT;
                case "kof_map_clear" -> Type.PrimitiveType.VOID;
                case "kof_map_keys", "kof_map_values" -> new Type.ClassType("kof", "List", List.of(mapFn.equals("kof_map_keys") ? keyType : valueType));
                default -> Type.UnknownType.UNKNOWN;
            };
            // §121/§126 (B1): widening no VALOR do put (arg1); chave (arg0)
            // fica raw (família §126 miss: Long-key em String-map → null do
            // get, nunca crash). O box JVM é guiado por parameterTypes —
            // emitArgsCoercingValue ajusta argTypes ao converter.
            localIdx = CompilerEmissionHelpers.emitArgsCoercingValue(driver, mc, ops, owner,
                    localIdx, locals, argTypes, valueType,
                    "kof_map_put".equals(mapFn) ? 1 : -1);
            ops.add(new KofCall(recvType, mapFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    if (BuiltinTypes.isSet(recvType)) {

        String setFn = switch (mc.methodName()) {
            case "add" -> "kof_set_add";
            case "contains" -> "kof_set_contains";
            case "remove" -> "kof_set_remove";
            // add/contains/remove recebem tag de tipo (1=string)
            case "size", "length", "count" -> "kof_set_size";
            case "clear" -> "kof_set_clear";
            case "isEmpty" -> "kof_set_is_empty";
            default -> null;
        };
        if (setFn == null && driver.currentDiagnostics != null
                && !"toArray".equals(mc.methodName()) && !"subSet".equals(mc.methodName()) && !"sublist".equals(mc.methodName())) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on type 'Set' (valid: add/contains/remove/size/clear/isEmpty)",
                    "SEM025");
            return localIdx;
        }
        if (setFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type elemType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
            // §126 (ii): Set.add com tipo ≠ elemType pinada = heap poluído
            // (o scan tag=1 chama kof_string_equals sobre Int cru → SIGSEGV
            // no Native — ST1/H3). JVM tolera; a linguagem NÃO (homogênea).
            if ("kof_set_add".equals(setFn) && !argTypes.isEmpty()
                    && CollectionWrites.pollutesPinned(elemType, argTypes.get(0))
                    && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0,
                        pos != null ? pos.column() : 0, 0,
                        "Set.add: element " + CollectionWrites.typeNameFor(argTypes.get(0))
                                + " does not match the set element type (" + CollectionWrites.typeNameFor(elemType)
                                + ") — Kof collections are homogeneous",
                        "SEM056");
                return localIdx;
            }
            Type retType = switch (setFn) {
                case "kof_set_add", "kof_set_remove" -> Type.PrimitiveType.BOOL;
                case "kof_set_contains", "kof_set_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_set_size" -> Type.PrimitiveType.INT;
                case "kof_set_clear" -> Type.PrimitiveType.VOID;
                default -> Type.UnknownType.UNKNOWN;
            };
            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            if (driver.target.isNative()
                    && ("kof_set_add".equals(setFn) || "kof_set_contains".equals(setFn)
                        || "kof_set_remove".equals(setFn))) {
                // tag de tipo só no Native (HashSet usa equals no JVM)
                // §126: conjunção elem×arg — senão raw cmpq, que nunca deref
                // (Int-arg em String-set era SIGSEGV: ST1/ST2).
                int tag = CollectionWrites.stringTag(elemType, argTypes, 0);
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            ops.add(new KofCall(recvType, setFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    // bug 16: `toArray()` não é suportado (nem documentado) e
    // caía no caminho genérico → bytecode inválido (JVM) /
    // undefined reference (Native). Diagnóstico limpo em vez de
    // saída quebrada.
    if ("toArray".equals(mc.methodName())
            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
            && driver.currentDiagnostics != null) {
        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0,
                "method '" + mc.methodName() + "' is not supported on collections;"
                        + " use a loop with new T[n] to materialize an array",
                "SEM029");
    }
    // bug 16 (cauda): `sublist()`/`subSet()` retornam COLEÇÃO —
    // o backend não sabe materializar o retorno de coleção e
    // emitia bytecode inválido (JVM) / undefined reference
    // (Native). Mesmo tratamento do toArray: diagnóstico limpo.
    if (("sublist".equals(mc.methodName()) || "subSet".equals(mc.methodName()))
            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
            && driver.currentDiagnostics != null) {
        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0,
                "method '" + mc.methodName() + "' is not supported on collections"
                        + " (collection return is not materializable);"
                        + " copy the elements with a loop",
                "SEM034");
    }
    for (ExpressionNode arg : mc.arguments()) {
        ExpressionTyper.inferExprType(driver, arg, locals);
    }
        return -1;
    }

    /** §122: tipos que NUNCA são um índice válido p/ get/set/remove de List. */
    private static boolean isReferenceIndexType(Type t) {
        if (t == null || Type.UnknownType.UNKNOWN.equals(t)) return false;
        if (t instanceof Type.NullableType nt) return isReferenceIndexType(nt.inner());
        if (TypeMetrics.isPrimitiveType(t)) return false;
        return t instanceof Type.ClassType || t instanceof Type.ArrayType
                || t instanceof Type.TypeVariable;
    }

}