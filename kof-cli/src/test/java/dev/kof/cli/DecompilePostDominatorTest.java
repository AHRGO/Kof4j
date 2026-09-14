package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    // ── oracle independente sobre CFGs REAIS (brute-force caminhos simples) ──
    // A passada rapida (bitset) deve concordar com a DEFINICAO de caminho:
    // X pdom estrito de b ⟺ todo caminho simples b→terminal passa por X;
    // idom(b) = o pdom estrito POST-DOMINADO por todos os outros (topo da
    // cadeia); sem pdom estrito real → EXIT. Caminho simples basta: todo
    // caminho com ciclo tem um atalho sem ciclo (lembrete teoria dos grafos).

    @Test
    void fastPassAgreesWithPathDefinitionOnRealCorpusCfGs() throws Exception {
        // surefire roda com CWD = diretorio do modulo; sobe ate achar o repo
        Path root = null;
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path cand = p.resolve("kof-compiler").resolve("target").resolve("classes");
            if (Files.isDirectory(cand)) { root = cand; break; }
        }
        assumeTrue(root != null, "corpus ausente (mvn -o compile -pl kof-compiler -am antes)");
        List<Path> classes;
        try (var s = Files.walk(root)) {
            classes = s.filter(p -> p.toString().endsWith(".class")).limit(300).toList();
        }
        int checked = 0, methods = 0;
        for (Path c : classes) {
            dev.kof.compiler.parser.ClassFileParser.ClassFile ir;
            try (var in = Files.newInputStream(c)) {
                ir = dev.kof.compiler.parser.ClassFileParser.parse(in);
            } catch (Exception e) { continue; }
            for (var m : ir.methods) {
                if (m.code == null || "<clinit>".equals(m.name)) continue;
                methods++;
                var insns = BytecodeReader.decode(m.code.bytecode);
                var blocks = BytecodeReader.cfg(insns, new int[0]);
                if (blocks.isEmpty() || blocks.size() > 40) continue; // limita DFS
                var fast = PostDominator.immediatePostDom(blocks);
                var path = pathOracle(blocks);
                for (var b : blocks) {
                    checked++;
                    assertEquals(path.get(b.start), fast.get(b.start),
                            "idom(" + b.start + ") diverge em " + c.getFileName() + "." + m.name);
                }
            }
        }
        assumeTrue(checked > 200, "corpus minado de CFGs (esperado centenas)");
    }

    private static Map<Integer, Integer> pathOracle(List<BytecodeReader.Block> blocks) {
        Map<Integer, Integer> idx = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) idx.put(blocks.get(i).start, i);
        Map<Integer, Integer> out = new HashMap<>();
        for (BytecodeReader.Block b : blocks) {
            // conjuntos de pdoms estritos reais sobre caminhos simples b→terminal
            Set<Integer> strict = new HashSet<>();
            for (BytecodeReader.Block o : blocks) if (o != b) strict.add(o.start);
            // filtra: o ∈ strict ⟺ o está em TODO caminho simples b→terminal
            strict.removeIf(o -> existsPathAvoiding(b, o, blocks, idx));
            if (strict.isEmpty()) { out.put(b.start, PostDominator.EXIT); continue; }
            // idom = o x ∈ strict tal que todo y ∈ strict\{x} está em todo caminho x→terminal
            int idom = -2;
            for (int x : strict) {
                BytecodeReader.Block xb = blocks.get(idx.get(x));
                final int xFinal = x;
                boolean top = true;
                for (int y : strict) {
                    if (y == xFinal) continue;
                    if (existsPathAvoiding(xb, y, blocks, idx)) { top = false; break; }
                }
                if (top) { idom = x; break; }
            }
            out.put(b.start, idom);
        }
        return out;
    }

    /** true ⟺ existe caminho simples de `from` a um terminal que NÃO passa por `avoid`. */
    private static boolean existsPathAvoiding(BytecodeReader.Block from, int avoid,
                                              List<BytecodeReader.Block> blocks,
                                              Map<Integer, Integer> idx) {
        if (from.start == avoid) return false;
        Deque<Object[]> stack = new ArrayDeque<>();
        Set<Integer> onPath = new HashSet<>();
        stack.push(new Object[]{from, onPath});
        while (!stack.isEmpty()) {
            Object[] fr = stack.pop();
            BytecodeReader.Block cur = (BytecodeReader.Block) fr[0];
            @SuppressWarnings("unchecked") Set<Integer> path = (Set<Integer>) fr[1];
            if (cur.succ.isEmpty()) return true; // chegou a terminal sem pisar avoid
            for (int s : cur.succ) {
                if (s == avoid) continue;
                Integer si = idx.get(s);
                if (si == null) continue;
                if (path.contains(s)) continue; // ciclo: ignora
                BytecodeReader.Block sb = blocks.get(si);
                Set<Integer> np = new HashSet<>(path);
                np.add(s);
                stack.push(new Object[]{sb, np});
            }
        }
        return false;
    }
}
