package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace shell (shell.*) no emitExpression.
 *
 * Sugar over kof.process: `run` lowers onto the existing kof_process_run
 * binding (JVM+JS real, Native gated PROC001 — same honest gap as process.run);
 * `pipeline` needs live pipes (process.spawn), which today exist only on the
 * JVM, so JS/Native hit PROC001 at compile time exactly like the spawn gate
 * does (never a raw call that would ReferenceError — the §235 lesson).
 * `cmd` is an argv builder, `ok` is pure field/compare IR on the Result.
 */
public final class ExpressionShellCallLowerer {

    private ExpressionShellCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) {
            argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        }
        KofShell.ShellCall call = KofShell.staticCall(mc.methodName(), argTypes);
        if (call == null) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(posFile(mc), posLine(mc), posCol(mc), 0,
                        "Cannot resolve method '" + mc.methodName()
                                + "' on 'shell' (valid: cmd, run, pipeline, ok)",
                        "SEM025");
            }
            return localIdx;
        }
        if (driver.target.isNative()) {
            // every shell call ends in process territory (run/pipeline) or in
            // a Result the Native driver can never produce — same gap as
            // process.run (PROC001), reported once, at compile time, never a
            // silent stub (R6).
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(posFile(mc), posLine(mc), posCol(mc), 0,
                        "shell." + mc.methodName() + ": not supported on the Native"
                                + " driver.target yet (JVM and JS support the shell surface;"
                                + " Native waits for process.run)",
                        "PROC001");
            }
            return localIdx;
        }
        if (driver.target == Target.JS && "pipeline".equals(mc.methodName())) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(posFile(mc), posLine(mc), posCol(mc), 0,
                        "shell.pipeline: live pipes (process.spawn) are supported on the JVM"
                                + " target only; Native and JS are honest PROC001 gaps",
                        "PROC001");
            }
            return localIdx;
        }
        switch (mc.methodName()) {
            case "run" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                if (mc.arguments().size() == 2) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1),
                            ops, owner, localIdx, locals);
                } else {
                    ops.add(new KofCall(KofProcess.STRING_LIST, "kof_list_new", List.of(),
                            KofProcess.STRING_LIST, KofCallKind.FUNCTION));
                }
                ops.add(new KofCall(KofProcess.RESULT, "kof_process_run",
                        List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                        KofProcess.RESULT, KofCallKind.FUNCTION));
            }
            case "cmd" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(KofShell.STRING_LIST, "kof_shell_argv",
                        List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                        KofShell.STRING_LIST, KofCallKind.FUNCTION));
            }
            case "pipeline" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(KofProcess.RESULT, "kof_shell_pipeline",
                        List.of(KofShell.STRING_LIST_LIST), KofProcess.RESULT,
                        KofCallKind.FUNCTION));
            }
            default -> {
                // "ok": shell.ok(result) == result.exitCode == 0 — pure IR, no binding
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                ops.add(new KofLoadField(KofProcess.RESULT, "exitCode",
                        Type.PrimitiveType.INT));
                ops.add(KofLoadLiteral.ofInt(0));
                ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
            }
        }
        return localIdx;
    }

    private static String posFile(MethodCallExpr mc) {
        return mc.position() != null ? mc.position().file() : "";
    }

    private static int posLine(MethodCallExpr mc) {
        return mc.position() != null ? mc.position().line() : 0;
    }

    private static int posCol(MethodCallExpr mc) {
        return mc.position() != null ? mc.position().column() : 0;
    }
}
