package dev.kof.runtime;

import org.graalvm.polyglot.Value;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * R3 (3.6 + 3.4-C3): marshalling do lado do host JS entre o array de args do runner
 * GraalJS e o downcall FFM em {@link KofJsFfiBridge}. Vive separado do {@code KofJsRunner}
 * (que já é grande) — o {@code ProxyExecutable} só delega. A mesma ABI escalar do target
 * JVM ({@code kof_ffi}), com o_acréscimo de callbacks/upcalls: um parâmetro de tipo-função
 * vira um ponteiro de função C real via {@code Linker.upcallStub}.
 *
 * <p>Callback (3.4-C3): um valor de função Kof compilado para JS NÃO é uma arrow nativa —
 * é um objeto {@code Lambda…} com um método {@code invoke}; a ponte do stub chama
 * {@code fn.getMember("invoke").execute(...)}. Contrato síncrono/não-escapante (o stub vive
 * na {@code Arena.ofConfined()} da chamada, fechada assim que o downcall retorna).
 */
final class KofJsFfiMarshal {

    private KofJsFfiMarshal() {
    }

    /** Downcall com retorno: abre a arena confined dos stubs e chama o bridge. */
    static Object ffi(String lib, String name, String sig, Value jsArgs) {
        try (Arena stubArena = Arena.ofConfined()) {
            return KofJsFfiBridge.call(lib, name, sig, args(sig, jsArgs, stubArena));
        }
    }

    /** Downcall void (statement): idem, descarta o retorno. */
    static void ffiVoid(String lib, String name, String sig, Value jsArgs) {
        try (Arena stubArena = Arena.ofConfined()) {
            KofJsFfiBridge.callVoid(lib, name, sig, args(sig, jsArgs, stubArena));
        }
    }

    private static Object[] args(String sig, Value jsArgs, Arena stubArena) {
        int n = countParams(sig);   // tokens de 1º nível (callback "(..)" conta 1)
        Object[] real = new Object[n];
        int cur = 1;
        for (int i = 0; i < n; i++) {
            char c = sig.charAt(cur);
            Value v = (jsArgs != null && jsArgs.hasArrayElements() && i < jsArgs.getArraySize())
                    ? jsArgs.getArrayElement(i) : null;
            if (c == '(') {
                int j = cur + 1;
                int depth = 1;
                StringBuilder inner = new StringBuilder();
                while (depth > 0) {
                    char x = sig.charAt(j);
                    if (x == '(') { depth++; inner.append(x); }
                    else if (x == ')') { depth--; if (depth > 0) inner.append(x); }
                    else inner.append(x);
                    j++;
                }
                cur = j;
                try {
                    real[i] = jsCallbackStub(v, inner.toString(), stubArena);
                } catch (Throwable t) {
                    throw new RuntimeException("ffi: callback upcallStub failed: " + t, t);
                }
            } else {
                cur++;
                if (v == null || v.isNull()) {
                    real[i] = null;
                } else {
                    real[i] = switch (c) {
                        case 'i' -> v.asInt();
                        case 'j' -> v.asLong();
                        case 'f' -> v.asFloat();
                        case 'd' -> v.asDouble();
                        case 'b' -> v.asBoolean();
                        default -> v.asString();   // 'S'
                    };
                }
            }
        }
        return real;
    }

    /** Nº de tokens de parâmetro de 1º nível (um callback `(..)` conta como 1). */
    private static int countParams(String sig) {
        int n = 0, cur = 1;
        while (cur < sig.length()) {
            char c = sig.charAt(cur);
            if (c == '(') {
                n++;
                int j = cur + 1, depth = 1;
                while (depth > 0) { char x = sig.charAt(j); if (x == '(') depth++; else if (x == ')') depth--; j++; }
                cur = j;
            } else {
                n++;
                cur++;
            }
        }
        return n;
    }

    /**
     * Callback/upcall JS (R3, 3.4-C3): transforma o objeto de função Kof num ponteiro de
     * função C via `Linker.upcallStub`. A ponte chama `fn.getMember("invoke").execute(...)`
     * reentrante na mesma thread (medido na C3.1, `KofJsFfiCallbackBridgeTest`); o stub vive
     * na arena confined da chamada → contrato síncrono/não-escapante. `inner` =
     * "r"+chars dos params do callback (só primitivos i/j/f/d/b, retorno pode ser v).
     */
    private static MemorySegment jsCallbackStub(Value fn, String inner, Arena arena)
            throws Throwable {
        char rb = inner.charAt(0);
        int arity = inner.length() - 1;
        MemoryLayout[] il = new MemoryLayout[arity];
        Class<?>[] prim = new Class<?>[arity];
        for (int k = 0; k < arity; k++) {
            char c = inner.charAt(k + 1);
            il[k] = ffiLayout(c);
            prim[k] = ffiCarrier(c);
        }
        Class<?> ret = rb == 'v' ? void.class : ffiCarrier(rb);
        // asVarargsCollector (não asSpreader): o alvo varargs `executeJsX(Value, Object...)`
        // recebe os primitivos já com boxing do asType. Mesma forma usada no pin C3.1.
        MethodHandle h = MethodHandles.lookup()
                .findStatic(KofJsFfiMarshal.class, "executeJs" + Character.toUpperCase(rb),
                        MethodType.methodType(ret, Value.class, Object[].class))
                .bindTo(fn)
                .asVarargsCollector(Object[].class)
                .asType(MethodType.methodType(ret, prim));
        FunctionDescriptor cb = (rb == 'v')
                ? FunctionDescriptor.ofVoid(il)
                : FunctionDescriptor.of(ffiLayout(rb), il);
        return Linker.nativeLinker().upcallStub(h, cb, arena);
    }

    static int executeJsI(Value fn, Object... a) { return fn.getMember("invoke").execute(a).asInt(); }
    static long executeJsJ(Value fn, Object... a) { return fn.getMember("invoke").execute(a).asLong(); }
    static float executeJsF(Value fn, Object... a) { return fn.getMember("invoke").execute(a).asFloat(); }
    static double executeJsD(Value fn, Object... a) { return fn.getMember("invoke").execute(a).asDouble(); }
    static boolean executeJsB(Value fn, Object... a) { return fn.getMember("invoke").execute(a).asBoolean(); }
    static void executeJsV(Value fn, Object... a) { fn.getMember("invoke").execute(a); }

    static Class<?> ffiCarrier(char c) {
        return switch (c) {
            case 'i' -> int.class;
            case 'j' -> long.class;
            case 'f' -> float.class;
            case 'd' -> double.class;
            case 'b' -> boolean.class;
            default -> throw new IllegalArgumentException("bad ffi carrier char: " + c);
        };
    }

    static MemoryLayout ffiLayout(char c) {
        return switch (c) {
            case 'i' -> ValueLayout.JAVA_INT;
            case 'j' -> ValueLayout.JAVA_LONG;
            case 'f' -> ValueLayout.JAVA_FLOAT;
            case 'd' -> ValueLayout.JAVA_DOUBLE;
            case 'b' -> ValueLayout.JAVA_BOOLEAN;
            case 'S' -> ValueLayout.ADDRESS;
            default -> throw new IllegalArgumentException("bad ffi layout char: " + c);
        };
    }
}
