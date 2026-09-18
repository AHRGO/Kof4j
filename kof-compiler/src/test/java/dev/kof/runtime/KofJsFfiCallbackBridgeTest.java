package dev.kof.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R3 fatia 3.4-C3.1: pin HOST-LEVEL da paridade JS de callback/upcall. Prova o ponto
 * que realmente importa e NÃO é óbvio: um upcall FFM disparado a partir de um downcall
 * nativo que foi ele mesmo iniciado pelo motor GraalJS (JS → {@link ProxyExecutable}
 * → downcall C → {@code Linker.upcallStub} → de volta {@code Value.execute}) funciona
 * de forma REENTRANTE na MESMA thread — sem crash, sem deadlock. É o cenário exato do
 * runner KofJS real, onde todo o programa roda dentro de {@code context.eval(...)}.
 *
 * <p>Isto espelha o pin da JVM ({@code JvmFfiCallbackTest}, C1) do lado JS. O gate do
 * compilador para callback no JS segue FECHADO ({@code isExternBound} do JS aceita só
 * escalar → {@code FFI002}); nada aqui é alcançável pelo compilador ainda — é só a
 * prova do mecanismo que a fatia C3.2 (abrir o gate + {@code KofJsFfiBridge.upcall} +
 * marshalling do {@code Value} no {@code KofJsRunner}) e C3.3 (E2E de paridade byte-a-
 * byte + degrade honesto no browser) vão ligar. Zero mudança de produção, zero risco.
 */
class KofJsFfiCallbackBridgeTest {

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

    /** Chamada de volta: o upcall roda o JS `fn` (reentrante na thread do eval). */
    static int jsCbInt(Value fn, int x, int y) {
        return fn.execute(x, y).asInt();
    }

    static long jsCbLong(Value fn, long x, long y) {
        return fn.execute(x, y).asLong();
    }

    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "callback host usa um .so nativo (Linux)");
        Path src = dir.resolve("libkofcb.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofcb.so");
        String cc = null;
        for (String cand : new String[] {"/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc"}) {
            try {
                Process p = new ProcessBuilder(cand, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) { cc = cand; break; }
            } catch (Exception ignored) { /* próximo candidato */ }
        }
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de callback");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de callback: " + out);
        return so.toString();
    }

    @Test
    void reentrantFfmUpcallIntoGraalJsValue(@TempDir Path dir) throws Throwable {
        String libPath = compileHostLib(dir);
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib = SymbolLookup.libraryLookup(libPath, Arena.global());

        // (i) callback síncrono (int,int)->int, disparado de dentro do eval JS
        ProxyExecutable probeIi = (Value[] args) -> ffiProbe(args, lib, linker,
                "kof_cb_add",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                MethodType.methodType(int.class, Value.class, int.class, int.class),
                ValueLayout.JAVA_INT);
        // (ii) carrier Long (long,long)->long
        ProxyExecutable probeJj = (Value[] args) -> ffiProbe(args, lib, linker,
                "kof_cb_addl",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
                MethodType.methodType(long.class, Value.class, long.class, long.class),
                ValueLayout.JAVA_LONG);
        // (iii) C chama o stub num loop (rooting durante a chamada) — (int,int)->int
        ProxyExecutable probeLoop = (Value[] args) -> ffiProbe(args, lib, linker,
                "kof_cb_sum_to",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                MethodType.methodType(int.class, Value.class, int.class, int.class),
                ValueLayout.JAVA_INT);

        try (Context ctx = Context.newBuilder("js").allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false").build()) {
            ctx.getBindings("js").putMember("probeIi", probeIi);
            ctx.getBindings("js").putMember("probeJj", probeJj);
            ctx.getBindings("js").putMember("probeLoop", probeLoop);

            // args[2]/[1] são a função JS; o stub chama de volta REENTRANTMENTE
            assertEquals(42, ctx.eval("js", "probeIi(20, 22, (x, y) => x + y)").asInt(),
                    "FFM upcall -> GraalJS execute reentrante, mesma thread");
            assertEquals(42L, ctx.eval("js", "probeJj(20, 22, (x, y) => x + y)").asLong(),
                    "callback com carrier Long reentrante");
            assertEquals(46, ctx.eval("js", "probeLoop(4, 10, (i, base) => i + base)").asInt(),
                    "C chama o stub 4x num loop (rooting durante a chamada): 10+11+12+13");
        }
    }

    /**
     * O mecanismo exato que C3.2 vai embutir no {@code KofJsFfiBridge}: monta um
     * {@code upcallStub} sobre o {@code Value} de função JS (via um MethodHandle estático
     * que chama {@code fn.execute(...)}), então faz o downcall C passando o stub como
     * {@code ADDRESS}. Args: [0]=a [1]=b [2]=fn.
     */
    private static Object ffiProbe(Value[] args, SymbolLookup lib, Linker linker, String name,
            FunctionDescriptor cbParams, MethodType jsBridgeType, ValueLayout carrier) {
        Value fn = args[2];
        long a = carrier == ValueLayout.JAVA_LONG ? args[0].asLong() : args[0].asLong();
        long b = carrier == ValueLayout.JAVA_LONG ? args[1].asLong() : args[1].asLong();
        try (Arena ar = Arena.ofConfined()) {
            MethodHandle bridge;
            try {
                bridge = MethodHandles.lookup().findStatic(KofJsFfiCallbackBridgeTest.class,
                        carrier == ValueLayout.JAVA_LONG ? "jsCbLong" : "jsCbInt", jsBridgeType)
                        .bindTo(fn);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            MemorySegment stub = linker.upcallStub(bridge, cbParams, ar);
            MethodHandle call = linker.downcallHandle(lib.find(name).orElseThrow(),
                    FunctionDescriptor.of(carrier, carrier, carrier, ValueLayout.ADDRESS));
            if (carrier == ValueLayout.JAVA_LONG) {
                try { return (long) call.invoke(a, b, stub); }
                catch (Throwable t) { throw new RuntimeException(t); }
            }
            try { return (int) call.invoke((int) a, (int) b, stub); }
            catch (Throwable t) { throw new RuntimeException(t); }
        }
    }
}
