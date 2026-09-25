package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace process (process.*) no emitExpression.
 */
public final class ExpressionProcessCallLowerer {

    private ExpressionProcessCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
    if (procCall != null && "kof_process_spawn".equals(procCall.function())) {
        if (driver.target.isNative()) {
            // F10: process.spawn needs live stdin/stdout. Native has no
            // fork/exec descriptors yet — honest PROC001 gap. JVM (incl.
            // Android, which reuses JvmBackend) and JS (KofJsProcessBridge
            // host binding, 19/09) implement the spawn + handle ops with the
            // exact same contract (handle = seq Long, failed spawn = -1).
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        "process.spawn: interactive stdin/stdout is supported on the JVM and JS targets; Native is an honest PROC001 gap",
                        "PROC001");
            }
            return localIdx;
        }
        // F10: process.spawn(program, args...) → monta List<String>
        // e chama kof_process_spawn (stdin/stdout vivos)
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type listType = KofProcess.STRING_LIST;
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        for (int i = 1; i < mc.arguments().size(); i++) {
            ops.add(new KofDup());
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            ops.add(new KofCall(listType, "kof_list_add",
                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                    KofCallKind.INSTANCE));
        }
        ops.add(new KofCall(KofProcess.HANDLE, "kof_process_spawn",
                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                KofProcess.HANDLE, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (procCall != null) {
        // D-FULL-PARITY-050 row 1 slice A: run/exit emit for real ONLY on the
        // x86-64 native target (RuntimeProcess). The cross (riscv64/aarch64)
        // has no kof_process_run slice yet — emitting there would be a raw
        // call ending in an ld undefined-reference (R6) — and spawn stays
        // slice B everywhere (interactive stdin/stdout + handle ops). JVM
        // (incl. Android) and JS (KofJsProcessBridge) carry the full contract.
        boolean cross = driver.target == Target.NATIVE_RISCV64
                || driver.target == Target.NATIVE_AARCH64;
        if (driver.target.isNative() && (cross
                || "kof_process_spawn".equals(procCall.function()))) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        (cross ? "process on the riscv64/aarch64 native targets: "
                                : "process.spawn: interactive stdin/stdout is supported on")
                                + (cross ? "not landed yet (PROC001); x86-64 native has run/exit"
                                : " the JVM and JS targets; Native is an honest PROC001 gap (slice B)"),
                        "PROC001");
            }
            return localIdx;
        }
        // process.run(program, args...) →
        // kof_process_run(program, List<String>) — TODOS os targets
        // (Native x86-64: RuntimeProcess, linha 1 do ledger D-FULL-PARITY-050)
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type listType = KofProcess.STRING_LIST;
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        for (int i = 1; i < mc.arguments().size(); i++) {
            ops.add(new KofDup());
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            ops.add(new KofCall(listType, "kof_list_add",
                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                    KofCallKind.INSTANCE));
        }
        ops.add(new KofCall(KofProcess.RESULT, "kof_process_run",
                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                KofProcess.RESULT, KofCallKind.FUNCTION));
    } else {
        // process.exit(code) — todos os targets
        KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
        if (exitCall != null) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.process", "Process", List.of()),
                    exitCall.function(), exitCall.parameterTypes(), exitCall.returnType(),
                    KofCallKind.FUNCTION));
        } else if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on 'process' (valid: run, spawn, exit)",
                    "SEM025");
        }
    }
        return localIdx;
    }
}