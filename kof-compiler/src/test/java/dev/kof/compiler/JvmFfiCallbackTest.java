package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FFI (R3, fatia 3.4-C1): pin do MECANISMO de callback/upcall no nível do host, com
 * as MESMAS idéias que o {@code kof_ffi} gerado usará na C2 — {@code Linker.upcallStub}
 * + ponte de closure {@code .asType} (boxing/unboxing automáticos) + rooting por
 * {@code Arena} + arg {@code ADDRESS} via {@code asSpreader}. Contra um {@code .so}
 * temp compilado por gcc/cc (skip honesto sem toolchain).
 *
 * <p><b>GATE AINDA FECHADO (C1).</b> Nada no compilador roteia um {@code extern} de
 * callback ainda — um {@code extern} com parâmetro de tipo-função continua {@code FFI002}
 * em compilação (R6, nunca stub silencioso). Este teste trava o contrato medido do
 * design §R3-3.4 (só síncrono/não-escapante, ABI escalar) para as fatias C2/C3 não
 * regressionarem. Zero mudança de produção, zero risco ao backend.
 */
class JvmFfiCallbackTest {

    /** Um valor de função estilo Kof: objeto com {@code Object invoke(Object,Object)}. */
    private static Object addClosure() {
        return new Object() {
            public Object invoke(Object x, Object y) {
                return (Integer) x + (Integer) y;
            }
        };
    }

    private static final String C_SRC = """
            typedef int (*ii)(int,int);
            int kof_cb_add(int a, int b, ii cb) { return cb(a,b); }
            int kof_cb_sum_to(int n, int base, ii cb) {
                int s = 0;
                for (int i = 0; i < n; i++) { s += cb(i, base); }
                return s;
            }
            typedef long (*ll)(long,long);
            long kof_cb_addl(long a, long b, ll cb) { return cb(a,b); }
            """;

    /** Compila o .so do host de callback; devolve null se não houver toolchain/Linux. */
    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(isLinux(), "callback host lib usa um .so nativo (Linux)");
        Path src = dir.resolve("libkofcb.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofcb.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de callback");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de callback: " + out);
        return so.toString();
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static String firstPresent(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return c;
                }
            } catch (Exception ignored) {
                // tenta o próximo candidato
            }
        }
        return null;
    }

    // --- espelho fiel das idéias do kof_ffi gerado (C2) ----------------------------

    /** Mesma codificação char→layout de {@code JvmFfiRuntime.kof_ffi_layout}. */
    private static ValueLayout layout(char c) {
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

    /**
     * Ponte de callback igual à que o {@code kof_ffi} fará: o objeto de função Kof
     * ({@code invoke(Object...) -> Object}) vira um MethodHandle de carrier unboxed
     * pelo único {@code .asType} (boxing/unboxing automáticos) e um stub ADDRESS.
     */
    private static MemorySegment callbackStub(Linker linker, Arena arena, Object closure,
            String innerSig) throws Throwable {
        int arity = innerSig.length() - 1; // ret char + param chars
        Class<?>[] objParams = new Class<?>[arity];
        for (int i = 0; i < arity; i++) {
            objParams[i] = Object.class;
        }
        MethodHandle invoke = MethodHandles.lookup()
                .findVirtual(closure.getClass(), "invoke",
                        MethodType.methodType(Object.class, objParams))
                .bindTo(closure);
        Class<?>[] carriers = new Class<?>[arity];
        for (int i = 0; i < arity; i++) {
            carriers[i] = carrierOf(innerSig.charAt(i + 1));
        }
        MethodHandle unboxed = invoke.asType(
                MethodType.methodType(carrierOf(innerSig.charAt(0)), carriers));
        MemoryLayout[] il = new MemoryLayout[arity];
        for (int i = 0; i < arity; i++) {
            il[i] = layout(innerSig.charAt(i + 1));
        }
        FunctionDescriptor cbDesc = FunctionDescriptor.of(layout(innerSig.charAt(0)), il);
        return linker.upcallStub(unboxed, cbDesc, arena);
    }

    private static Class<?> carrierOf(char c) {
        return switch (c) {
            case 'i' -> int.class;
            case 'j' -> long.class;
            case 'f' -> float.class;
            case 'd' -> double.class;
            case 'b' -> boolean.class;
            default -> Object.class;
        };
    }

    @Test
    void kofStyleClosureBridgedIntoFfmUpcallRoundTrips(@TempDir Path dir) throws Throwable {
        String libPath = compileHostLib(dir);
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib = SymbolLookup.libraryLookup(libPath, Arena.global());
        Object closure = addClosure();

        // (1) downcall com spreader (fiel ao kof_ffi): each(Int,Int,CB) -> CB(a,b) = a+b
        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment stub = callbackStub(linker, callArena, closure, "iii");
            FunctionDescriptor fd = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
            MethodHandle h = linker.downcallHandle(lib.find("kof_cb_add").orElseThrow(), fd);
            h = h.asSpreader(Object[].class, 3);
            Object r = h.invoke(new Object[] {6, 7, stub});
            assertEquals(13, ((Integer) r).intValue(), "callback síncrono via ADDRESS spreader");
        }

        // (2) mesma ponte exercida por C em loop (rooting do stub durante a chamada)
        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment stub = callbackStub(linker, callArena, closure, "iii");
            FunctionDescriptor fd = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
            MethodHandle h = linker.downcallHandle(lib.find("kof_cb_sum_to").orElseThrow(), fd);
            int s = (int) h.invoke(4, 10, stub);
            assertEquals(46, s, "cb(i,10) chamado 4x por C: 10+11+12+13");
        }

        // (3) carrier Long no callback (j) — mesma ponte callbackStub, outro layout/ABI
        Object longSum = new Object() {
            public Object invoke(Object x, Object y) {
                return (Long) x + (Long) y;
            }
        };
        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment stub = callbackStub(linker, callArena, longSum, "jjj");
            FunctionDescriptor fd = FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
            MethodHandle h = linker.downcallHandle(lib.find("kof_cb_addl").orElseThrow(), fd);
            long r = (long) h.invoke(20L, 22L, stub);
            assertEquals(42L, r, "callback com carrier Long via .asType (kof_cb_addl)");
        }
    }
}
