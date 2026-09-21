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
 * primitivo cru no {@code receive} virava ponteiro (SIGSEGV 139, medido): no
 * nativo a caixa MAGIC do §284 é emitida no SEND (fechamento §374, 21/09) e o
 * println de Unknown despacha por {@code kof_box_to_string} — sem recusa.
 */
final class ChannelWrites {

    private ChannelWrites() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc,
                     List<KofOperation> ops, String owner, int localIdx,
                     List<IRLocalVariable> locals) {
        // §374-cross (21/09): o runtime de canais só existe na face x86_64
        // (NativeX86Calls); riscv64/aarch64 nunca portaram `kof_channel_*`.
        // Diagnóstico honesto NAT005 no lugar do link-fail críptico
        // (`undefined reference`), padrão §352/R6. Resolução = portar os
        // canais no cross (projeto da lane nat).
        if (driver.target.isNative() && driver.target != Target.NATIVE) {
            if (driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "Channel is not supported on the riscv64/aarch64 native targets yet"
                                + " (NAT005) — use the x86_64 native target (--target native)"
                                + " or JVM/JS/Script",
                        "NAT005");
            }
            return localIdx;
        }
        Type elemT = BuiltinTypes.channelElement(recvType);
        if ("send".equals(mc.methodName()) && mc.arguments().size() == 1) {
            ExpressionNode arg = mc.arguments().get(0);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            boolean bare = elemT instanceof Type.UnknownType;
            Type argT = bare ? ExpressionTyper.inferExprType(driver, arg, locals) : elemT;
            // §374 fechado 21/09 (lane nat): canal NU + primitivo no nativo =
            // caixa MAGIC do §284 no SEND (mesma lei do JVM/JS, que boxeiam
            // pelo tipo do ARG) — o println de Unknown já despacha via
            // kof_box_to_string (tags 0/2/3/4/5), então a recusa NAT003 caiu.
            // Canal TIPADO intocado (emissão byte-idêntica).
            if (bare && driver.target.isNative() && argT instanceof Type.PrimitiveType
                    && !ExpressionTyper.boxesOwnBranches(driver, arg, locals)) {
                Type boxed = TypeMetrics.boxedTypeFor(argT);
                ops.add(new KofCall(boxed, "kof_box", List.of(argT), boxed, KofCallKind.FUNCTION));
                ops.add(new KofCall(recvType, "kof_channel_send", List.of(boxed),
                        Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
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
