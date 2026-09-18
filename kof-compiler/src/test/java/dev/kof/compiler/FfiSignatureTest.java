package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3: locks the compiler-side scalar type -> FFM layout char -> Kof {@link Type}
 * mapping for the FULL claimed scalar ABI set {Int, Long, Float, Double, Boolean,
 * String} + void return. The runtime downcall path is type-generic (one
 * asSpreader over the {@code Object[]} boxed args + per-char {@code ValueLayout}),
 * and it is proven end-to-end in {@link FfiE2ETest} for Int/Long/Double/String/
 * void; this unit test closes the mapping claim for Float/Boolean, which the ABI
 * maps correctly but which libc offers no clean, deterministic call site for from
 * Kof source (no float/long literals; C bool-returning predicates are rare).
 */
class FfiSignatureTest {

    @Test
    void paramCharCoversAllScalars() {
        assertEquals('i', FfiSignature.paramChar("Int"));
        assertEquals('i', FfiSignature.paramChar("int"));
        assertEquals('j', FfiSignature.paramChar("Long"));
        assertEquals('j', FfiSignature.paramChar("long"));
        assertEquals('f', FfiSignature.paramChar("Float"));
        assertEquals('f', FfiSignature.paramChar("float"));
        assertEquals('d', FfiSignature.paramChar("Double"));
        assertEquals('b', FfiSignature.paramChar("Boolean"));
        assertEquals('b', FfiSignature.paramChar("bool"));
        assertEquals('S', FfiSignature.paramChar("String"));
    }

    @Test
    void paramCharRejectsUnmappable() {
        assertNull(FfiSignature.paramChar("Int[]"), "array param is R3 3.8 (struct/array ABI), not scalar");
        assertNull(FfiSignature.paramChar("MyStruct"));
        assertNull(FfiSignature.paramChar("void"), "void is a return-only marker");
    }

    @Test
    void returnCharAddsVoid() {
        assertEquals('v', FfiSignature.returnChar("void"));
        assertEquals('v', FfiSignature.returnChar(""));
        assertEquals('v', FfiSignature.returnChar(null));
        assertEquals('j', FfiSignature.returnChar("Long"));
        assertEquals('f', FfiSignature.returnChar("Float"));
        assertEquals('b', FfiSignature.returnChar("Boolean"));
        assertEquals('S', FfiSignature.returnChar("String"));
    }

    @Test
    void returnTypeMapsToKofPrimitives() {
        assertEquals(Type.PrimitiveType.INT, FfiSignature.returnType("Int"));
        assertEquals(Type.PrimitiveType.LONG, FfiSignature.returnType("Long"));
        assertEquals(Type.PrimitiveType.FLOAT, FfiSignature.returnType("Float"));
        assertEquals(Type.PrimitiveType.DOUBLE, FfiSignature.returnType("Double"));
        assertEquals(Type.PrimitiveType.BOOL, FfiSignature.returnType("Boolean"));
        assertEquals(Type.PrimitiveType.VOID, FfiSignature.returnType("void"));
        assertSame(BuiltinTypes.STRING, FfiSignature.returnType("String"));
    }

    // R3 3.4-C3.4: callback ARGUMENTS accept String (char*->String at the upcall
    // boundary); a String RETURN stays non-bindable (cbReturnChar rejects 'S').
    @Test
    void callbackDescriptorBindsScalarAndStringArgsButNotStringReturn() {
        assertEquals("iii", FfiSignature.callbackDescriptor("(Int, Int) -> Int"));
        assertEquals("iS", FfiSignature.callbackDescriptor("(String) -> Int"));
        assertEquals("iSi", FfiSignature.callbackDescriptor("(String, Int) -> Int"));
        assertEquals("jS", FfiSignature.callbackDescriptor("(String) -> Long"));
        assertEquals("vd", FfiSignature.callbackDescriptor("(Double) -> void"));
        assertNull(FfiSignature.callbackDescriptor("(Int) -> String"),
                "String RETURN from a callback is not bindable (char* ownership)");
        assertNull(FfiSignature.callbackDescriptor("(MyStruct) -> Int"),
                "non-scalar callback arg stays non-bindable");
        assertNull(FfiSignature.callbackDescriptor("(Int[]) -> Int"),
                "array callback arg is R3 3.8, not bindable");
    }
}
