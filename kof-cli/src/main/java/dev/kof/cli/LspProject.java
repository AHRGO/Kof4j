package dev.kof.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Varredura da árvore de arquivos do projeto para o LSP (X10 fatias 4–5,
 * extraída de LspServer no split ≤600 de 18/09): irmãos `.kf` e leitura
 * tolerante a falha. Estrutura-only; comportamento idêntico.
 */
final class LspProject {

    private LspProject() {}

    static Path toPath(String uri) {
        try {
            if (uri == null || !uri.startsWith("file:")) return null;
            return Path.of(java.net.URI.create(uri));
        } catch (Exception e) {
            return null;
        }
    }

    /** Arquivos `.kf` irmãos na árvore do projeto (profundidade ≤6, ordenados). */
    static List<Path> siblings(Path self) {
        if (self.getParent() == null) return List.of();
        try (var stream = Files.walk(self.getParent(), 6)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".kf"))
                    .filter(p -> !p.toAbsolutePath().equals(self.toAbsolutePath()))
                    .sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    static String readOrNull(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * workspace/symbol (X10 fatia 6): símbolos dos buffers abertos + arquivos
     * .kf não-abertos da árvore (mesmo walker das fatias 4–5). Filtro
     * case-insensitive por substring; prefixo antes de substring (convenção
     * LSP); desempate por nome e uri.
     */
    @SuppressWarnings("unchecked")
    static java.util.List<Object> workspaceSymbols(
            java.util.Map<String, String> buffers, String query) {
        String q = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        java.util.List<Object> out = new java.util.ArrayList<>();
        java.util.Set<Path> seen = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, String> e : buffers.entrySet()) {
            Path openPath = toPath(e.getKey());
            if (openPath != null) seen.add(openPath.toAbsolutePath());
            collect(out, e.getKey(), e.getValue(), q);
        }
        for (Path open : new java.util.ArrayList<>(seen)) {
            for (Path f : siblings(open)) {
                if (!seen.add(f.toAbsolutePath())) continue;
                String txt = readOrNull(f);
                if (txt == null) continue;
                collect(out, f.toAbsolutePath().toUri().toString(), txt, q);
            }
        }
        out.sort(java.util.Comparator
                .comparingInt((Object o) -> rank((java.util.Map<String, Object>) o, q))
                .thenComparing(o -> String.valueOf(((java.util.Map<String, Object>) o).get("name")))
                .thenComparing(o -> {
                    java.util.Map<String, Object> loc =
                            (java.util.Map<String, Object>) ((java.util.Map<String, Object>) o).get("location");
                    return String.valueOf(loc.get("uri"));
                }));
        return out;
    }

    private static int rank(java.util.Map<String, Object> sym, String q) {
        String name = String.valueOf(sym.get("name")).toLowerCase(java.util.Locale.ROOT);
        return name.startsWith(q) ? 0 : 1;
    }

    private static void collect(java.util.List<Object> out, String uri, String text, String q) {
        for (LspSymbols.DocSymbol s : LspSymbols.documentSymbols(text)) {
            if (!s.name().toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            java.util.Map<String, Object> sym = new java.util.LinkedHashMap<>();
            sym.put("name", s.name());
            sym.put("kind", (long) s.kind());
            java.util.Map<String, Object> loc = new java.util.LinkedHashMap<>();
            loc.put("uri", uri);
            loc.put("range", LspServer.rangeOf(text, s.start(), s.end()));
            sym.put("location", loc);
            out.add(sym);
        }
    }

    /**
     * Linha de declaração do nome (X10 fatia 7 — hover): buffer primeiro
     * (fonte da verdade), depois irmãos `.kf`. Retorna {linha, arquivo} ou
     * null (nunca chute — R6).
     */
    static String[] declarationLine(String uri, String bufferText, String word) {
        if (word == null || word.isEmpty()) return null;
        if (bufferText != null) {
            int[] r = LspSymbols.declarationRange(bufferText, word);
            if (r != null) return new String[]{ lineAt(bufferText, r[0]), nameOf(uri) };
        }
        Path self = toPath(uri);
        if (self == null) return null;
        for (Path f : siblings(self)) {
            String txt = readOrNull(f);
            if (txt == null) continue;
            int[] r = LspSymbols.declarationRange(txt, word);
            if (r != null) return new String[]{ lineAt(txt, r[0]), f.getFileName().toString() };
        }
        return null;
    }

    private static String lineAt(String text, int offset) {
        int ls = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int e = text.indexOf('\n', offset);
        return text.substring(ls, e < 0 ? text.length() : e).strip();
    }

    private static String nameOf(String uri) {
        Path p = toPath(uri);
        return p == null ? "?" : p.getFileName().toString();
    }
}
