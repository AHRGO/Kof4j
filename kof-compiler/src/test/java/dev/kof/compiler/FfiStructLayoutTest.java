package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3.7 fatia 1: classificação/bindability do struct nativo x86-64, sem toolchain C
 * (o E2E com shim real é {@code FfiStructE2ETest}). Trava o caminho de
 * REGISTRADORES (SysV) e os gaps honestos: MEMORY (> 16 B), SSE com mais de um
 * campo (empacotamento não emitido) e estouro de registradores.
 */
class FfiStructLayoutTest {

    private static Type struct(char... chars) {
        return FfiStructLayout.structTypeOfChars(new String(chars));
    }

    @Test
    void registerPathStructsAreBindable() {
        assertTrue(FfiStructLayout.x86Bindable(List.of(struct('i', 'i'))),
                "Point(Int,Int): 1 eightbyte INTEGER");
        assertTrue(FfiStructLayout.x86Bindable(List.of(struct('i', 'f'))),
                "Int+Float no MESMO eightbyte → INTEGER (int domina)");
        assertTrue(FfiStructLayout.x86Bindable(List.of(struct('j', 'd'))),
                "Time(Long,Double): INTEGER + SSE");
        assertTrue(FfiStructLayout.x86Bindable(List.of(
                        Type.PrimitiveType.INT, struct('i', 'i'), Type.PrimitiveType.DOUBLE)),
                "escalar + struct + escalar na ordem formal");
    }

    @Test
    void memoryPathAndExhaustionStayUnbound() {
        assertFalse(FfiStructLayout.x86Bindable(List.of(struct('i', 'i', 'i', 'i', 'i'))),
                "Big3(5×Int) = 20 B → SysV MEMORY (FFI001 honesto)");
        assertFalse(FfiStructLayout.x86Bindable(List.of(struct('f', 'f'))),
                "dois Float no MESMO eightbyte SSE não é emitido (FFI001 honesto)");
        assertFalse(FfiStructLayout.x86Bindable(List.of(
                        Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                        Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                        struct('i', 'i'))),
                "6 int regs já consumidos → struct iria à memória (FFI001 honesto)");
    }

    @Test
    void abiForMapsTargets() {
        assertEquals(AbiLayout.Abi.SYSV_X86_64, FfiStructLayout.abiFor(Target.NATIVE));
        assertEquals(AbiLayout.Abi.RISCV64, FfiStructLayout.abiFor(Target.NATIVE_RISCV64));
        assertEquals(AbiLayout.Abi.AAPCS64, FfiStructLayout.abiFor(Target.NATIVE_AARCH64));
    }
}
