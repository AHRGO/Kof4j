package dev.kof.cli;

import java.util.List;

/**
 * Conteúdo do hover do LSP (textual, mesma família de references/definition):
 * palavras-chave Kof, tipos primitivos, variáveis locais e DOMÍNIO da stdlib
 * (namespace — membros reais do StdCatalog; membro soló no contexto exato
 * {@code ns.}). Nenhum parser roda por request.
 */
final class LspHover {

    private LspHover() {}

    static final List<String[]> KEYWORDS = List.of(
            new String[]{"var", "mutable variable"}, new String[]{"val", "immutable value"},
            new String[]{"spawn", "roda tarefa em virtual thread"},
            new String[]{"await", "aguarda Handle<T> e devolve T"},
            new String[]{"enum", "conjunto fechado de constantes"},
            new String[]{"record", "immutable structure with components"},
            new String[]{"class", "classe"}, new String[]{"interface", "contrato"},
            new String[]{"switch", "selection (exhaustive over enum → SEM031)"},
            new String[]{"listOf", "cria List<T>"}, new String[]{"mapOf", "cria Map<K,V>"},
            new String[]{"setOf", "cria Set<T>"},
            new String[]{"println", "prints a line to stdout"});

    static final List<String> BUILTIN_TYPES = List.of(
            "Int", "Long", "Bool", "String", "Float", "Double");

    /** markdown do hover para {@code word}, ou null se não tem. */
    static String hoverFor(String word, String text, int caret) {
        for (String[] k : KEYWORDS) {
            if (k[0].equals(word)) return "**" + k[0] + "** — " + k[1];
        }
        if (BUILTIN_TYPES.contains(word)) return "**" + word + "** — tipo primitivo Kof";
        for (String ln : text.split("\n")) {
            String t = ln.strip();
            if (t.startsWith("var ") || t.startsWith("val ")) {
                String rest = t.substring(4).strip();
                if (rest.startsWith(word)) {
                    int after = rest.indexOf(word) + word.length();
                    if (after < rest.length() && ":= \t".indexOf(rest.charAt(after)) >= 0) {
                        return "**" + word + "** — local variable\n```kf\n" + ln.strip() + "\n```";
                    }
                }
            }
        }
        return hoverStd(word, text, caret);
    }

    /**
     * 8.3 (linha do plano universal): hover POR DOMÍNIO — namespace da stdlib
     * e membro em contexto {@code ns.} exato, ambos vindos do StdCatalog (a
     * mesma fonte do completion; nenhum catálogo paralelo). Fora do contexto
     * {@code .} um membro solto NÃO vira hover — sem contexto não há como
     * saber se é o membro ou um usuário com o mesmo nome (nunca chute, R6).
     */
    private static String hoverStd(String word, String text, int caret) {
        if (dev.kof.compiler.StdCatalog.isNamespace(word)) {
            var ms = dev.kof.compiler.StdCatalog.membersOf(word);
            StringBuilder b = new StringBuilder("**" + word + "** \u2014 namespace `kof." + word
                    + "` (" + ms.size() + " members)");
            if (!ms.isEmpty()) {
                b.append("\n```\n");
                for (int i = 0; i < Math.min(8, ms.size()); i++) b.append(ms.get(i)).append("\n");
                if (ms.size() > 8) b.append("\u2026\n");
                b.append("```");
            }
            return b.toString();
        }
        int st = Math.max(0, Math.min(caret, text.length()));
        while (st > 0 && (Character.isLetterOrDigit(text.charAt(st - 1)) || text.charAt(st - 1) == '_')) st--;
        if (st > 0 && text.charAt(st - 1) == '.') {
            int ns0 = st - 1;
            while (ns0 > 0 && (Character.isLetterOrDigit(text.charAt(ns0 - 1)) || text.charAt(ns0 - 1) == '_')) ns0--;
            String ns = text.substring(ns0, st - 1);
            if (dev.kof.compiler.StdCatalog.isNamespace(ns)
                    && dev.kof.compiler.StdCatalog.membersOf(ns).contains(word)) {
                var sig = dev.kof.compiler.StdCatalog.signaturesOf(ns, word);
                if (sig.isEmpty()) return "**" + word + "** \u2014 member of `kof." + ns + "`";
                StringBuilder mb = new StringBuilder("**" + word + "** \u2014 member of `kof." + ns + "`");
                mb.append("\n```\n");
                for (String sg : sig) mb.append(sg).append("\n");
                mb.append("```");
                return mb.toString();
            }
        }
        return null;
    }
}
