package dev.kof.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3 fatia 3.6-F1: prova HOST-LEVEL do bridge FFI do runner JS. Exercita o MESMO
 * downcall que o target JVM roda ({@code JvmFfiRuntime.kof_ffi}) diretamente no host,
 * contra libc/libm reais, para que a fiação de paridade JS (F2/F3) descanse sobre um
 * caminho já provado. Os casos espelham 1:1 os e2es do {@code FfiE2ETest} (mesmas
 * libs, assinaturas e saídas) → paridade por construção.
 *
 * <p>O gate do compilador para JS segue FECHADO (extern no JS → {@code FFI002}); nada
 * aqui é alcançável pelo compilador ainda — é só a ponte no host, provada isolada.
 */
class KofJsFfiBridgeTest {

    @Test
    void absIntToInt() {
        assertEquals(5, KofJsFfiBridge.call("libc.so.6", "abs", "ii", new Object[] { -5 }));
    }

    @Test
    void atoiStringToInt() {
        assertEquals(42, KofJsFfiBridge.call("libc.so.6", "atoi", "iS", new Object[] { "42" }));
    }

    @Test
    void sqrtDoubleToDouble() {
        assertEquals(3.0, (double) KofJsFfiBridge.call("libm.so.6", "sqrt", "dd", new Object[] { 9.0 }), 1e-9);
    }

    @Test
    void powTwoDoubleArgs() {
        assertEquals(1024.0,
                (double) KofJsFfiBridge.call("libm.so.6", "pow", "ddd", new Object[] { 2.0, 10.0 }), 1e-9);
    }

    @Test
    void atolStringToLong() {
        assertEquals(1234567890123L,
                KofJsFfiBridge.call("libc.so.6", "atol", "jS", new Object[] { "1234567890123" }));
    }

    @Test
    void strstrTwoStringsToString() {
        assertEquals("world", KofJsFfiBridge.call("libc.so.6", "strstr", "SSS",
                new Object[] { "hello world", "wor" }));
    }

    @Test
    void strstrMissingNeedleIsNullNotGarbage() {
        assertNull(KofJsFfiBridge.call("libc.so.6", "strstr", "SSS",
                new Object[] { "hello", "zzz" }), "char* NULL returns null, not a bogus read");
    }

    @Test
    void srandVoidReturnsNullAndCallVoidDoesNotThrow() {
        assertNull(KofJsFfiBridge.call("libc.so.6", "srand", "vi", new Object[] { 42 }));
        KofJsFfiBridge.callVoid("libc.so.6", "srand", "vi", new Object[] { 42 });
    }
}
