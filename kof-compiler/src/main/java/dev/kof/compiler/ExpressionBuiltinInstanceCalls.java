package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Chamadas de instância em receivers BUILTIN por domínio (enum name / kof.web /
 * kof.media / kof.io) — extraído verbatim do ExpressionInstanceCallLowerer
 * (§140 split-5, regra ≤500). Cada bloco termina em return localIdx: quem casa,
 * consome a chamada inteira; o caller decide se continua (Io devolve localIdx
 * quando nenhum método casa — o fluxo do caller segue).
 */
final class ExpressionBuiltinInstanceCalls {

    private ExpressionBuiltinInstanceCalls() {}

    /** enum .name(): o valor do enum JÁ é o nome (String em runtime) — identidade. */
    static boolean isEnumIdentityName(CompilerDriver driver, Type recvType, MethodCallExpr mc) {
        return CompilerTypes.isEnumType(recvType, driver.currentUnit)
                && ("name".equals(mc.methodName()) || "toString".equals(mc.methodName())) && mc.arguments().isEmpty();
    }

    static int lowerWeb(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        List<Type> webArgTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
        if (webCall != null) {
            boolean nativeWebT1 = (driver.target == Target.NATIVE
                    || driver.target == Target.NATIVE_RISCV64
                    || driver.target == Target.NATIVE_AARCH64)
                    && (webCall.function().equals("kof_web_listen")
                        || webCall.function().equals("kof_web_route"));
            // WEB001-T1 JS (13/09): routes HTTP + listen liberados no JS — o
            // runtime JsRuntimeUiWeb emite kofWebAppNew/Route/Listen (server
            // GraalJS HttpServer real); sse/ws/TLS seguem WEB003/004/002.
            boolean jsWebT1 = driver.target == Target.JS
                    && (webCall.function().equals("kof_web_listen")
                        || webCall.function().equals("kof_web_route")
                        || webCall.function().equals("kof_web_app_new"));
            if (driver.target != Target.JVM && driver.target != Target.ANDROID
                    && !nativeWebT1 && !jsWebT1) {
                String webCode = KofWeb.gapCode(webCall.function());
                String webMsg = switch (webCode) {
                    case "WEB002" -> "web TLS: not available on the " + driver.target
                            + " driver.target yet (WEB002)";
                    case "WEB003" -> "web SSE: not available on the " + driver.target
                            + " driver.target yet (WEB003)";
                    case "WEB004" -> "web WebSocket: not available on the " + driver.target
                            + " driver.target yet (WEB004)";
                    case "WEB006" -> "web security middleware: not available on the "
                            + driver.target + " driver.target yet (WEB006)";
                    default -> "web: not available on the " + driver.target
                            + " driver.target yet (WEB001)";
                };
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                            mc.position() != null ? mc.position().line() : 0,
                            mc.position() != null ? mc.position().column() : 0,
                            0, webMsg, webCode);
                }
                return localIdx;
            }
            List<Type> webParams = new ArrayList<>();
            webParams.add(BuiltinTypes.STRING);
            if (KofWeb.isRouteMethod(mc.methodName()) && !"ws".equals(mc.methodName())) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.methodName().toUpperCase()));
                webParams.add(BuiltinTypes.STRING);
            }
            for (ExpressionNode arg : mc.arguments()) {
                webParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(KofWeb.APP, webCall.function(), webParams,
                    webCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }

    static int lowerMedia(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                          String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        KofMedia.MediaCall mediaCall =
                KofMedia.handleMethod(recvType, mc.methodName(), mc.arguments().size());
        if (mediaCall != null) {
            List<Type> mediaParams = new ArrayList<>();
            mediaParams.add(Type.PrimitiveType.INT);      // handle (receiver)
            for (ExpressionNode arg : mc.arguments()) {
                mediaParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    mediaCall.function(), mediaParams,
                    mediaCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }

    static int lowerIo(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                       String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        if (KofIo.isIdentityMethod(mc.methodName())) {
            return localIdx;
        }
        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (ioCall != null) {
            // receiver File/Path/Directory é apagado pra String
            // path em runtime (empilhado acima); os METHOD args
            // alinham com ioCall.parameterTypes() — a conversão
            // formal (int literal → long slot no readRange)
            // evita o frame bug I/J no visitMaxs
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ioCall.parameterTypes(),
                    ops, owner, localIdx, locals);
            List<Type> ioParams = new ArrayList<>();
            ioParams.add(BuiltinTypes.STRING);
            ioParams.addAll(ioCall.parameterTypes());
            ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                    ioCall.function(), ioParams, ioCall.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
        return localIdx;
    }
}
