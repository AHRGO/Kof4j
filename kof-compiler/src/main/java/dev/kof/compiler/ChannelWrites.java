package dev.kof.compiler;

import java.util.List;

/**
 * Lowering dos métodos de canal builtin (send/receive) — extraído do
 * CollectionCallLowerer (gate ≤500, regra 7: nome = responsabilidade).
 *
 * <p>§374/#553 (face residual do canal): canal BARE ({@code channel()} sem
 * type-args) tem elemT {@code Unknown}; o {@code parameterTypes} do KofCall
 * carregava o MESMO elemT, então o fallback box-by-ARG de
 * {@code JvmOpCollections} ({@code kof_channel_send}) lia um Unknown e não
 * boxava — {@code int} cru no {@code LinkedBlockingQueue.put(Object)} =
 * VerifyError no JVM (§149). Lei única: canal nua = arg na caixa pelo TIPO
 * DO ARGUMENTO (mesma lei do bug 35/§374 para List/Set/Map). Canal TIPADA
 * mantém emissão byte-idêntica (freeze regra 1): {@code parameterTypes}
 * continua {@code List.of(elemT)}. No Native a fila é de objetos e o
 * primitivo cru no {@code receive} virava ponteiro (SIGSEGV 139, medido):
 * recusa honesta {@code NAT003} (padrão §352), resolução = lane nat (§374).
 */
final class ChannelWrites {

    private ChannelWrites() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc,
                     List<KofOperation> ops, String owner, int localIdx,
                     List<IRLocalVariable> locals) {
        Type elemT = BuiltinTypes.channelElement(recvType);
        if ("send".equals(mc.methodName()) && mc.arguments().size() == 1) {
            ExpressionNode arg = mc.arguments().get(0);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            boolean bare = elemT instanceof Type.UnknownType;
            Type argT = bare ? ExpressionTyper.inferExprType(driver, arg, locals) : elemT;
            if (bare && driver.target.isNative() && argT instanceof Type.PrimitiveType
                    && !ExpressionTyper.boxesOwnBranches(driver, arg, locals)) {
                // NAT003 (padrão honesto §352/NAT001-NAT002): a fila nativa é
                // de OBJETOS — o inteiro cru que kof_channel_send empurrasse
                // virava ponteiro no receive (SIGSEGV 139, medido), e a caixa
                // do §284 imprimia lixo no dispatch dinâmico do println de
                // Unknown (sem header de tipo — resolução = lane nat, §374).
                if (driver.currentDiagnostics != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "send of a primitive on a bare Channel is not supported on"
                                    + " the native target yet (NAT003) — pin the element"
                                    + " type (channel<Int>()) or use JVM/JS/Script",
                            "NAT003");
                }
                return localIdx;
            }
            ops.add(new KofCall(recvType, "kof_channel_send", List.of(argT),
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
        // diagnostics null: o original CAIA no caminho genérico (não era
        // tratado) — sentinel -1 preserva o fallthrough exato.
        return -1;
    }
}
