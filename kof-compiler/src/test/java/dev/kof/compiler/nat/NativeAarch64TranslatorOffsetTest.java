package dev.kof.compiler.nat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * §CodeQL uncaught-number-format-exception #244-246 — os tres ramos de
 * memoria do tradutor riscv->aarch64 (`ld/lw/... off(rs)`, `sd/sw`, `fld`)
 * usavam `Integer.parseInt` sobre `(-?\d+)` capturado pelo regex: um offset
 * maior que int estoura NFE crua no meio da compilacao Native. O regex NAO
 * limita digitos — `ld a0, 99999999999(t0)` casa e derrubava o build.
 * Contrato corrigido: nao-encodable (overflow) = passthrough da linha,
 * identico ao no-match existente.
 */
class NativeAarch64TranslatorOffsetTest {

    @Test
    void inRangeOffsetTranslates() {
        List<String> out = NativeAarch64Translator.translateRiscvToAarch64("    ld a0, 8(t0)");
        String joined = String.join("\n", out);
        assertNotEquals("    ld a0, 8(t0)", joined, "offset valido deve traduzir");
        assertTrue(joined.contains("ldr"), "ld vira ldr: " + joined);
        assertTrue(joined.contains("#8"), "offset preservado: " + joined);
    }

    @Test
    void intOverflowOffsetPassesThroughWithoutCrash() {
        // 99999999999 > Integer.MAX_VALUE — antes: NumberFormatException crua.
        assertDoesNotThrow(() ->
                NativeAarch64Translator.translateRiscvToAarch64("    ld a0, 99999999999(t0)"));
        assertEquals(List.of("    ld a0, 99999999999(t0)"),
                NativeAarch64Translator.translateRiscvToAarch64("    ld a0, 99999999999(t0)"),
                "overflow = passthrough igual no-match (nao engolir a linha)");
    }

    @Test
    void storeAndFloatBranchesAlsoGuardOverflow() {
        for (String line : List.of(
                "    sd a0, 99999999999999(t0)",
                "    fld f1, -99999999999(t1)")) {
            List<String> out = assertDoesNotThrow(
                    () -> NativeAarch64Translator.translateRiscvToAarch64(line),
                    "ramo store/float deve sobreviver a offset gigante: " + line);
            assertEquals(List.of(line), out, "passthrough: " + line);
        }
    }

    @Test
    void negativeInRangeOffsetStillTranslates() {
        List<String> out = NativeAarch64Translator.translateRiscvToAarch64("    lw a1, -16(sp)");
        String joined = String.join("\n", out);
        assertNotEquals("    lw a1, -16(sp)", joined);
        assertTrue(joined.contains("-16"), "offset negativo valido preservado: " + joined);
    }
}
