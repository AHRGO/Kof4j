package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Oráculos pós-dominadores (Fase C, DECOMPILER.md passo 3) calculados à mão
 * pela definição: X pdom Y ⟺ todo caminho Y→EXIT passa por X. Os grafos são
 * montados direto nos blocos (a função é pura — o teste prova a definição,
 * não a emissão do recovery).
 */
class DecompilePostDominatorTest {

    private static Map<Integer, Integer> idom(int[]... succIdx) {
        List<BytecodeReader.Block> blocks = new ArrayList<>();
        for (int i = 0; i < succIdx.length; i++) {
            BytecodeReader.Block b = new BytecodeReader.Block(i * 10);
            b.end = i * 10 + 5;
            blocks.add(b);
        }
        for (int i = 0; i < succIdx.length; i++) {
            for (int j : succIdx[i]) {
                blocks.get(i).succ.add(j * 10);
                blocks.get(j).pred.add(i * 10);
            }
        }
        return PostDominator.immediatePostDom(blocks);
    }

    @Test
    void linearChainIdomIsNextAndExitIsLast() {
        Map<Integer, Integer> m = idom(new int[]{1}, new int[]{2}, new int[]{});
        assertEquals(10, m.get(0), "B0 -> B1");
        assertEquals(20, m.get(10), "B1 -> B2");
        assertEquals(PostDominator.EXIT, m.get(20), "terminal -> EXIT");
    }

    @Test
    void ifThenElseJoinPostDominatesBothArms() {
        Map<Integer, Integer> m = idom(new int[]{1, 2}, new int[]{3}, new int[]{3}, new int[]{});
        assertEquals(30, m.get(0), "idom(B0) = join B3");
        assertEquals(30, m.get(10), "idom(then) = B3");
        assertEquals(30, m.get(20), "idom(else) = B3");
        assertEquals(PostDominator.EXIT, m.get(30), "idom(join) = EXIT");
    }

    @Test
    void whileLoopBodyChainConvergesAtExitBlock() {
        // B0 -> B1(header) if (B2 corpo, B4 saida); B2 -> B3; B3 -> B1 (back-edge); B4 -> EXIT
        Map<Integer, Integer> m = idom(
                new int[]{1},
                new int[]{2, 4},
                new int[]{3},
                new int[]{1},
                new int[]{});
        assertEquals(40, m.get(10), "idom(header) = saida do loop (unica porta p/ EXIT)");
        assertEquals(10, m.get(0), "idom(B0) = B1 header (todo caminho B0->EXIT passa por B1; mais proximo)");
        assertEquals(30, m.get(20), "idom(B2 corpo) = B3");
        assertEquals(10, m.get(30), "idom(B3) = B1 — back-edge fecha o loop");
        assertEquals(PostDominator.EXIT, m.get(40), "idom(exit) = EXIT");
    }

    @Test
    void nestedIfElseOuterArmsMeetAtJoinOfWholeIf() {
        Map<Integer, Integer> m = idom(
                new int[]{1, 4},
                new int[]{2, 3},
                new int[]{4},
                new int[]{4},
                new int[]{});
        assertEquals(40, m.get(0), "idom(B0) = B4 (todo caminho p/ EXIT passa por B4)");
        assertEquals(40, m.get(10), "idom(if interno) = B4");
        assertEquals(40, m.get(20), "idom(B2) = B4");
        assertEquals(40, m.get(30), "idom(B3) = B4");
    }

    @Test
    void twoDirectExitsGiveIdomExitForFork() {
        // B0 if (B1 -> EXIT, B2 -> EXIT): nenhum braço pdom B0; EXIT pdom todos.
        Map<Integer, Integer> m = idom(new int[]{1, 2}, new int[]{}, new int[]{});
        assertEquals(PostDominator.EXIT, m.get(0), "idom(B0) = EXIT direto (fork sem join)");
        assertEquals(PostDominator.EXIT, m.get(10), "B1 terminal -> EXIT");
        assertEquals(PostDominator.EXIT, m.get(20), "B2 terminal -> EXIT");
    }
}
