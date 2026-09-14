package dev.kof.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pós-dominadores imediatos (Fase C — DECOMPILER.md, passo 3). Fórmula de
 * conjuntos (dual do algoritmo de Cooper–Harvey–Kennedy p/ dominadores, a
 * formulação padrão dos compiladores — princípio D-ENGINEERING: não
 * reinventar): pdom(b) = {b} ∪ ⋂{pdom(s) : s ∈ succ(b)}, com bloco terminal
 * pdom = {b, EXIT}. EXIT = sentinela {@value #EXIT} (offsets ≥ 0): o
 * "depois do último return". Converge por monotonia (interseção só encolhe
 * do universo; terminal planta {b,EXIT} e tudo que só alcança terminal
 * herda). Determinístico, sem dependência de ordem de iteração.
 */
final class PostDominator {

    static final int EXIT = -1;

    private PostDominator() {}

    /** start(bloco) → pós-dominador imediato (start, ou {@link #EXIT}). */
    static Map<Integer, Integer> immediatePostDom(List<BytecodeReader.Block> blocks) {
        int n = blocks.size();
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(blocks.get(i).start, i);

        java.util.BitSet universal = new java.util.BitSet(n + 1);
        universal.set(0, n + 1);

        List<java.util.BitSet> pdom = new ArrayList<>();
        for (int i = 0; i < n; i++) pdom.add((java.util.BitSet) universal.clone());

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                BytecodeReader.Block b = blocks.get(i);
                java.util.BitSet acc = new java.util.BitSet(n + 1);
                acc.set(i);
                if (b.succ.isEmpty()) {
                    acc.set(n); // terminal → pdom = {b, EXIT}
                } else {
                    java.util.BitSet inter = (java.util.BitSet) universal.clone();
                    for (int s : b.succ) {
                        Integer si = index.get(s);
                        if (si == null) continue; // aresta fora do método
                        inter.and(pdom.get(si));
                    }
                    acc.or(inter); // {b} ∪ ⋂ pdom(suc)
                }
                if (!acc.equals(pdom.get(i))) {
                    pdom.set(i, acc);
                    changed = true;
                }
            }
        }

        Map<Integer, Integer> out = new HashMap<>();
        for (int i = 0; i < n; i++) {
            java.util.BitSet strict = (java.util.BitSet) pdom.get(i).clone();
            strict.clear(i);
            boolean found = false;
            int bestIdx = 0;
            int bestSize = -1;
            for (int x = strict.nextSetBit(0); x >= 0; x = strict.nextSetBit(x + 1)) {
                int sz;
                if (x == n) {
                    sz = 0; // EXIT: sempre o mais distante (subset trivial)
                } else {
                    sz = pdom.get(x).cardinality();
                }
                if (sz > bestSize) {
                    bestSize = sz;
                    bestIdx = (x == n) ? EXIT : blocks.get(x).start;
                    found = true;
                }
            }
            out.put(blocks.get(i).start, found ? bestIdx : null);
        }
        return out;
    }
}
