package dev.kof.compiler.jvm;

/**
 * FFI (R3, TIER 2.1.4/2.1.6): downcall JVM-first via FFM
 * ({@code java.lang.foreign}) para o target JVM. Mantido fora de
 * {@code JvmRuntime} para a regra de ≤500 linhas/classe.
 */
final class JvmFfiRuntime {

    private JvmFfiRuntime() {}

    static String source() {
        // O source gerado é compilado IN-PROCESS (ToolProvider) pelo MESMO JDK
        // que roda o compilador — e o gate de preview em JvmRuntime usa a mesma
        // condição. JDK 21 (FFM preview): Arena.allocateUtf8String(String);
        // JDK 22+ (FFM final, JEP 454): Arena.allocateFrom(String).
        String alloc = Runtime.version().feature() < 22 ? "allocateUtf8String" : "allocateFrom";
        return FORMATTED.formatted(alloc);
    }

    private static final String FORMATTED = """
                public static int kof_ffi_i(String lib, String name, int a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.global();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_INT,
                                        java.lang.foreign.ValueLayout.JAVA_INT));
                        return (int) handle.invoke(a);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_i: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static int kof_ffi_si(String lib, String name, String a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_INT,
                                        java.lang.foreign.ValueLayout.ADDRESS));
                        java.lang.foreign.MemorySegment seg = arena.%1$s(a);
                        return (int) handle.invoke(seg);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_si: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static double kof_ffi_dd(String lib, String name, double a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.global();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_DOUBLE,
                                        java.lang.foreign.ValueLayout.JAVA_DOUBLE));
                        return (double) handle.invoke(a);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_dd: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static Object kof_ffi(String lib, String name, String sig, Object[] args) {
                    java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                    try {
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        char ret = sig.charAt(0);
                        java.lang.foreign.MemoryLayout[] pl =
                                new java.lang.foreign.MemoryLayout[args.length];
                        Object[] real = new Object[args.length];
                        for (int i = 0; i < args.length; i++) {
                            char c = sig.charAt(i + 1);
                            pl[i] = kof_ffi_layout(c);
                            real[i] = (c == 'S') ? arena.%1$s((String) args[i]) : args[i];
                        }
                        java.lang.foreign.FunctionDescriptor fd = (ret == 'v')
                                ? java.lang.foreign.FunctionDescriptor.ofVoid(pl)
                                : java.lang.foreign.FunctionDescriptor.of(kof_ffi_layout(ret), pl);
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(), fd);
                        handle = handle.asSpreader(Object[].class, args.length);
                        Object r = handle.invoke(real);
                        if (ret == 'v') return null;
                        if (ret == 'S') {
                            java.lang.foreign.MemorySegment seg = (java.lang.foreign.MemorySegment) r;
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

                public static void kof_ffi_void(String lib, String name, String sig, Object[] args) {
                    kof_ffi(lib, name, sig, args);
                }

                static java.lang.foreign.ValueLayout kof_ffi_layout(char c) {
                    return switch (c) {
                        case 'i' -> java.lang.foreign.ValueLayout.JAVA_INT;
                        case 'j' -> java.lang.foreign.ValueLayout.JAVA_LONG;
                        case 'f' -> java.lang.foreign.ValueLayout.JAVA_FLOAT;
                        case 'd' -> java.lang.foreign.ValueLayout.JAVA_DOUBLE;
                        case 'b' -> java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
                        case 'S' -> java.lang.foreign.ValueLayout.ADDRESS;
                        default -> throw new IllegalArgumentException("bad ffi layout char: " + c);
                    };
                }

    """;
}