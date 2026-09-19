package dev.kof.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * §132/#83-JS — host-side mini event loop for embedded GraalJS, extracted from
 * {@code KofJsRunner} to keep each class inside the REFACTOR-500 budget. The guest
 * has no event loop of its own on a single JS thread, so the host drives cooperative
 * async {@code time.sleep}: it steps the sleeper queue, drains released microtasks,
 * and real-sleeps toward the earliest deadline. {@code KofJsRunner} calls
 * {@link #drainActiveTasks(Context)} after evaluating the module (both run and
 * runCaptureHtml). Behavior is identical to the inline version it replaces.
 */
final class KofJsAsyncPump {
    private KofJsAsyncPump() {
    }

    static boolean activeTasks(Context context) {
        Value a = context.getBindings("js").getMember("kofActiveTasks");
        return a != null && a.isNumber() && a.asInt() > 0;
    }

    public static void drainActiveTasks(Context context) {
        // §132/#83-JS: cooperative async sleep. The guest exposes __kofSleepStep
        // (GraalJS path: resolves due sleeper promises + fires interval jobs and
        // returns the epoch-ms deadline of the earliest pending sleep, or -1).
        // This host loop is a minimal event loop: step -> eval(void 0) to drain the
        // microtasks the step released -> if work remains, Thread.sleep toward the
        // next deadline (real clock; time.now()/Date.now() unchanged). It does what
        // guest JS on a single thread cannot: sleep AND advance concurrent tasks.
        Value step = context.getBindings("js").getMember("__kofSleepStep");
        if (step == null || step.isNull() || !step.canExecute()) {
            Value active = context.getBindings("js").getMember("kofActiveTasks");
            while (active != null && active.isNumber() && active.asInt() > 0) {
                context.eval(Source.newBuilder("js", "void 0;", "kof-pump.js").buildLiteral());
                active = context.getBindings("js").getMember("kofActiveTasks");
            }
            return;
        }
        Source pump = Source.newBuilder("js", "void 0;", "kof-pump.js").buildLiteral();
        int guard = 0;
        while (guard++ < 2_000_000) {
            context.eval(pump);                          // run continuations a prior resolve queued
            long next = step.execute().asLong();         // fire timers + resolve due sleepers
            boolean active = activeTasks(context);
            if (next < 0 && !active) {
                // A resolve above may have resumed an awaiting task that registered a
                // NEW sleep (main's 2nd `time.sleep`), or a microtask chain still to
                // run — drain once more and re-check before declaring quiescence.
                context.eval(pump);
                long nextB = step.execute().asLong();
                if (nextB < 0 && !activeTasks(context)) break;
                next = nextB;
            }
            if (next >= 0) {
                long wait = next - System.currentTimeMillis();
                if (wait > 0) {
                    try {
                        Thread.sleep(Math.min(wait, 5));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
