package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering of the ssh namespace (ssh.*) in emitExpression.
 *
 * <p>Sugar over kof.process: {@code cmd}/{@code run} lower onto the runtime
 * bindings {@code kof_ssh_argv}/{@code kof_ssh_run} (JVM+JS real; Native gated
 * PROC001 — the same honest gap as {@code process.run}/{@code shell.run}),
 * {@code ok} is pure field/compare IR on the shared Result. Never a raw call that
 * would ReferenceError (the §235 lesson).
 */
public final class ExpressionSshCallLowerer {

    private ExpressionSshCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) {
            argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        }
        KofSsh.SshCall call = KofSsh.staticCall(mc.methodName(), argTypes);
        if (call == null) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(posFile(mc), posLine(mc), posCol(mc), 0,
                        "Cannot resolve method '" + mc.methodName()
                                + "' on 'ssh' (valid: " + String.join(", ", KofSsh.functions()) + ")",
                        "SEM025");
            }
            return localIdx;
        }
        if (driver.target.isNative() && driver.target != Target.NATIVE) {
            // Row 3 slice A: the x86-64 native target now emits (kof_ssh_argv /
            // kof_ssh_run → kof_process_run, RuntimeSsh). The cross targets (and
            // the freestanding MCU) still have no ssh runtime — same honest
            // PROC001 as process.run before row 1 slice C, reported once at
            // compile time, never a silent ld undefined (R6).
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(posFile(mc), posLine(mc), posCol(mc), 0,
                        "ssh." + mc.methodName() + ": not supported on the "
                                + driver.target + " native target yet (JVM, JS and"
                                + " Native x86-64 support kof.ssh)",
                        "PROC001");
            }
            return localIdx;
        }
        switch (mc.methodName()) {
            case "cmd" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(KofProcess.STRING_LIST, "kof_ssh_argv",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        KofProcess.STRING_LIST, KofCallKind.FUNCTION));
            }
            case "run" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(KofProcess.RESULT, "kof_ssh_run",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        KofProcess.RESULT, KofCallKind.FUNCTION));
            }
            default -> {
                // "ok": ssh.ok(result) == result.exitCode == 0 — pure IR, no binding
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
