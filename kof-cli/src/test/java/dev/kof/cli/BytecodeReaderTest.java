package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Robustez do {@link BytecodeReader#decode} contra bytecode truncado ou
 * corrompido (CodeQL java/index-out-of-bounds: o `skipVariable` lia
 * tableswitch/lookupswitch sem checar o fim do array).
 *
 * <p>Contrato: truncado/corrompido PARA no fim ({@code code.length}), nunca
 * lança, nunca trava. Input válido decodifica byte-idêntico ao de antes.
 */
class BytecodeReaderTest {

    private static byte[] tableSwitch(int dflt, int low, int high, int... jumps) {
        // op em pc=0 → pad 3 → default(4) + low(4) + high(4) + jumps(4 cada).
        byte[] code = new byte[1 + 3 + 12 + jumps.length * 4];
        code[0] = (byte) 0xaa;
        putInt(code, 4, dflt);
        putInt(code, 8, low);
        putInt(code, 12, high);
        for (int i = 0; i < jumps.length; i++) {
            putInt(code, 16 + i * 4, jumps[i]);
        }
        return code;
    }

    private static byte[] lookupSwitch(int dflt, int[] pairs) {
        // pares match/offset intercalados.
        byte[] code = new byte[1 + 3 + 8 + pairs.length * 4];
        code[0] = (byte) 0xab;
        putInt(code, 4, dflt);
        putInt(code, 8, pairs.length / 2);
        for (int i = 0; i < pairs.length; i++) {
            putInt(code, 12 + i * 4, pairs[i]);
        }
        return code;
    }

    private static void putInt(byte[] code, int at, int v) {
        code[at] = (byte) (v >>> 24);
        code[at + 1] = (byte) (v >>> 16);
        code[at + 2] = (byte) (v >>> 8);
        code[at + 3] = (byte) v;
    }

    @Test
    void validTableSwitchDecodesOneOpaqueInsn() {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(tableSwitch(0, 1, 2, 10, 20));
        assertEquals(1, insns.size(), "tableswitch válido = 1 insn opaco");
        assertEquals(0xaa, insns.get(0).opcode());
        assertEquals(0, insns.get(0).offset());
    }

    @Test
    void validLookupSwitchDecodesOneOpaqueInsn() {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(lookupSwitch(0, new int[]{1, 10}));
        assertEquals(1, insns.size(), "lookupswitch válido = 1 insn opaco");
        assertEquals(0xab, insns.get(0).opcode());
    }

    @Test
    void truncatedTableSwitchStopsWithoutThrowing() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // Só opcode + pad parcial: sem default/low/high.
            List<BytecodeReader.Insn> insns = BytecodeReader.decode(new byte[]{(byte) 0xaa, 0, 0});
            assertEquals(1, insns.size(), "truncado para no fim, nunca lança");
        });
    }

    @Test
    void truncatedLookupSwitchStopsWithoutThrowing() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // Opcode sozinho: sem pad, sem default, sem npairs.
            List<BytecodeReader.Insn> insns = BytecodeReader.decode(new byte[]{(byte) 0xab});
            assertEquals(1, insns.size(), "truncado para no fim, nunca lança");
        });
    }

    @Test
    void lookupSwitchWithNegativePairsStopsWithoutLooping() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // npairs = -1: o código antigo fazia `pc += -8` (voltava o pc —
            // risco de loop infinito no decode). Agora para no fim.
            byte[] code = lookupSwitch(0, new int[]{});
            putInt(code, 8, -1);
            List<BytecodeReader.Insn> insns = BytecodeReader.decode(code);
            assertEquals(1, insns.size(), "npairs negativo para, nunca volta o pc");
        });
    }

    @Test
    void tableSwitchWithInvertedRangeStopsCleanly() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // high < low: 0 jumps, mesmo pc final do código antigo.
            List<BytecodeReader.Insn> insns = BytecodeReader.decode(tableSwitch(0, 5, 2));
            assertEquals(1, insns.size(), "intervalo invertido = 0 jumps, sem throw");
        });
    }

    @Test
    void truncatedJumpTableStopsAtEnd() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // Header completo mas jumps cortados no meio (high-low+1=4, só 1 presente).
            byte[] full = tableSwitch(0, 1, 4, 10, 20, 30, 40);
            byte[] cut = new byte[full.length - 8];
            System.arraycopy(full, 0, cut, 0, cut.length);
            List<BytecodeReader.Insn> insns = BytecodeReader.decode(cut);
            assertEquals(1, insns.size(), "jumps truncados param no fim, nunca lançam");
        });
    }
}
