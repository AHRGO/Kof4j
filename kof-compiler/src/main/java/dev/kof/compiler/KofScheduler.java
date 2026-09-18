package dev.kof.compiler;

import java.util.List;

public final class KofScheduler {
    private KofScheduler() {}
    static final Type SCHEDULER = new Type.ClassType("kof.scheduler", "Scheduler", List.of());
    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isSchedulerNamespace(String name) { return "scheduler".equals(name) || "every".equals(name) || "at".equals(name); }
    static boolean isSchedulerMethod(String name) {
        return switch (name) {
            case "every", "at", "cancel" -> true;
            default -> false;
        };
    }
    record SchedulerCall(String function, Type returnType, List<Type> parameterTypes) {}
    static boolean supportedOn(String function, Target target) {
        // SCHED001 FEITO no cross (05/09): kof_scheduler_every/cancel no
        // runtime riscv64/aarch64 (thread por job via clone 220 + nanosleep
        // 101 + spinlock amoswap.w — mesmo mecanismo do spawn).
        // CRON001: `at(cron)` passou a ter parser cron real no JVM/JS (17/09);
        // o Native não tem o parser em asm — recusa honesta em compile-time
        // (R6/R7), nunca o stub silencioso de 60s que ignorava a expressão.
        if ("kof_scheduler_at".equals(function) && target.isNative()) return false;
        return target == Target.JVM || target == Target.ANDROID
                || target == Target.JS || target.isNative();
    }

    /** Diagnostic code for target gaps (R6). */
    static String gapCode(String function) {
        return "kof_scheduler_at".equals(function) ? "CRON001" : "SCHED001";
    }
    static SchedulerCall staticCall(String name, List<Type> argTypes) {
        return switch (name) {
            case "every" -> argTypes.size() == 2 && argTypes.get(0) instanceof Type.PrimitiveType
                    ? new SchedulerCall("kof_scheduler_every", STR, List.of(INT, OBJ)) : null;
            case "at" -> argTypes.size() == 2
                    ? new SchedulerCall("kof_scheduler_at", STR, List.of(STR, OBJ)) : null;
            case "cancel" -> argTypes.size() == 1
                    ? new SchedulerCall("kof_scheduler_cancel", VOID, List.of(STR)) : null;
            default -> null;
        };
    }
}
