package dev.kof.runtime;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * FFI (R3, fatia 3.6-F1): downcall no host do runner GraalJS/node — o runner É uma
 * JVM com {@code java.lang.foreign}, então o mesmo bridge que o target JVM usa pode
 * servir o alvo JS (browser não pode {@code dlopen} → fica {@code FFI002} honesto, R7).
 *
 * <p>Paridade por construção com o runtime JVM gerado por
 * {@code JvmFfiRuntime.kof_ffi}: MESMA codificação de assinatura (chars
 * {@code i/j/f/d/b/S} de parâmetro e {@code v}/{@code i}/{@code j}/{@code f}/{@code d}/
 * {@code b}/{@code S} de retorno), MESMO {@code ValueLayout}, mesmo {@code asSpreader}
 * sobre o {@code Object[]} boxado e mesma leitura de {@code char*} de volta
 * ({@code getString} após {@code reinterpret} com guard de NULL). Uma String
 * retornada é lida do ponteiro nativo; {@code void} devolve {@code null}.
 *
 * <p><b>FATIA F1 — gate AINDA FECHADO.</b> Nada no compilador roteia {@code extern}
 * para este bridge ainda: um {@code extern} no target JS continua emitindo
 * {@code FFI002} em compilação (R6, nunca stub silencioso). Este bridge e o
 * {@code KofJsFfiBridgeTest} provam o downcall ponta-a-ponta no host ANTES de
 * (F2) abrir o {@code isExternBound} do JS + rotear o lowering para um
 * {@code ProxyExecutable} sobre esta classe e (F3) afirmar paridade byte-a-byte
 * JVM↔JS. Zero risco ao backend JS até aqui — nada aqui é alcançável pelo compilador.
 */
public final class KofJsFfiBridge {

    private KofJsFfiBridge() {
    }

    public static Object call(String lib, String name, String sig, Object[] args) {
        Arena arena = Arena.ofConfined();
        try {
            SymbolLookup lookup = lib.isEmpty()
                    ? SymbolLookup.loaderLookup()
                    : SymbolLookup.libraryLookup(lib, arena);
            Linker linker = Linker.nativeLinker();
            char ret = sig.charAt(0);
            MemoryLayout[] pl = new MemoryLayout[args.length];
            Object[] real = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                char c = sig.charAt(i + 1);
                pl[i] = layout(c);
                real[i] = (c == 'S') ? arena.allocateFrom((String) args[i]) : args[i];
            }
            FunctionDescriptor fd = (ret == 'v')
                    ? FunctionDescriptor.ofVoid(pl)
                    : FunctionDescriptor.of(layout(ret), pl);
            MethodHandle handle = linker.downcallHandle(
                    lookup.find(name).orElseThrow(), fd);
            handle = handle.asSpreader(Object[].class, args.length);
            Object r = handle.invoke(real);
            if (ret == 'v') {
                return null;
            }
            if (ret == 'S') {
                MemorySegment seg = (MemorySegment) r;
                if (seg == null || seg.address() == 0L) {
                    return null;
                }
                return seg.reinterpret(Long.MAX_VALUE).getString(0L);
            }
            return r;
        } catch (Throwable t) {
            throw new RuntimeException("kof_ffi: " + lib + "::" + name + " (" + sig + ") failed: "
                    + t.getMessage(), t);
        } finally {
            arena.close();
        }
    }

    public static void callVoid(String lib, String name, String sig, Object[] args) {
        call(lib, name, sig, args);
    }

    static ValueLayout layout(char c) {
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
