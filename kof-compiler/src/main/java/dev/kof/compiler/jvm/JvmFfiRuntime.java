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
                        int cur = 1;
                        for (int i = 0; i < args.length; i++) {
                            char c = sig.charAt(cur);
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
                                pl[i] = java.lang.foreign.ValueLayout.ADDRESS;
                                real[i] = kof_ffi_upcall(linker, arena, args[i], inner.toString());
                            } else {
                                cur++;
                                pl[i] = kof_ffi_layout(c);
                                real[i] = (c == 'S') ? arena.%1$s((String) args[i]) : args[i];
                            }
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

                // Callback/upcall (R3, 3.4): um valor de função Kof (objeto que
                // implementa a interface sintética `invoke(...)`) vira ponteiro de
                // função C. A interface do Kof é ESPECIALIZADA (ex. int invoke(int,int)),
                // então o `unreflect` já dá um MethodHandle de carrier unboxed; o
                // `.asType(mt)` é o bridge (no-op quando o tipo bate). O stub vive na
                // arena da chamada → só callback SÍNCRONO/não-escapante (medido em C1).
                static java.lang.foreign.MemorySegment kof_ffi_upcall(
                        java.lang.foreign.Linker linker, java.lang.foreign.Arena arena,
                        Object closure, String inner) throws Throwable {
                    char rb = inner.charAt(0);
                    int arity = inner.length() - 1;
                    java.lang.foreign.MemoryLayout[] il =
                            new java.lang.foreign.MemoryLayout[arity];
                    // carrier NATIVO do stub (ADDRESS -> MemorySegment) e carrier do
                    // invoke Kof (String -> java.lang.String). Só divergem nos 'S'.
                    Class<?>[] up = new Class<?>[arity];
                    Class<?>[] cb = new Class<?>[arity];
                    int[] strPos = new int[arity];
                    int ns = 0;
                    for (int k = 0; k < arity; k++) {
                        char c = inner.charAt(k + 1);
                        il[k] = kof_ffi_layout(c);
                        if (c == 'S') {
                            up[k] = java.lang.foreign.MemorySegment.class;
                            cb[k] = String.class;
                            strPos[ns++] = k;
                        } else {
                            up[k] = kof_ffi_carrier(c);
                            cb[k] = kof_ffi_carrier(c);
                        }
                    }
                    Class<?> rret = kof_ffi_carrier(rb);
                    java.lang.invoke.MethodType cbMt =
                            java.lang.invoke.MethodType.methodType(rret, cb);
                    java.lang.invoke.MethodType upMt =
                            java.lang.invoke.MethodType.methodType(rret, up);
                    java.lang.reflect.Method m = null;
                    for (java.lang.reflect.Method cand : closure.getClass().getMethods()) {
                        if (cand.getName().equals("invoke")
                                && cand.getParameterCount() == arity) {
                            m = cand;
                            break;
                        }
                    }
                    if (m == null) {
                        throw new RuntimeException("kof_ffi: callback "
                                + closure.getClass().getName()
                                + " has no arity-" + arity + " invoke()");
                    }
                    java.lang.invoke.MethodHandles.Lookup lookup =
                            java.lang.invoke.MethodHandles.lookup();
                    java.lang.invoke.MethodHandle mh =
                            lookup.unreflect(m).bindTo(closure).asType(cbMt);
                    if (ns > 0) {
                        // fronteira ADDRESS->String do upcall: o C entrega um `char*`;
                        // o invoke Kof deve ver um String (reinterpret+getString, como
                        // no downcall). Sem filtro p/ callback só-primitivo (no-op).
                        java.lang.invoke.MethodHandle cstr = lookup.findStatic(
                                lookup.lookupClass(), "kof_ffi_cstr",
                                java.lang.invoke.MethodType.methodType(String.class,
                                        java.lang.foreign.MemorySegment.class));
                        for (int p = 0; p < ns; p++) {
                            mh = java.lang.invoke.MethodHandles.filterArguments(mh, strPos[p], cstr);
                        }
                    }
                    mh = mh.asType(upMt);
                    java.lang.foreign.FunctionDescriptor fd = (rb == 'v')
                            ? java.lang.foreign.FunctionDescriptor.ofVoid(il)
                            : java.lang.foreign.FunctionDescriptor.of(kof_ffi_layout(rb), il);
                    return linker.upcallStub(mh, fd, arena);
                }

                // char* (ADDRESS) entregue por C no callback -> String UTF-8; NULL vira
                // null (nunca um segfault silencioso). Espelha a leitura do downcall.
                public static String kof_ffi_cstr(java.lang.foreign.MemorySegment seg) {
                    if (seg == null || seg.address() == 0L) return null;
                    return seg.reinterpret(java.lang.Long.MAX_VALUE).getString(0L);
                }

                static Class<?> kof_ffi_carrier(char c) {
                    return switch (c) {
                        case 'i' -> int.class;
                        case 'j' -> long.class;
                        case 'f' -> float.class;
                        case 'd' -> double.class;
                        case 'b' -> boolean.class;
                        case 'v' -> void.class;
                        default -> throw new IllegalArgumentException("bad ffi carrier char: " + c);
                    };
                }

    """;
}