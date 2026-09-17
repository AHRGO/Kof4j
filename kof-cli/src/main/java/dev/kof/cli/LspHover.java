package dev.kof.cli;

import java.util.List;

/**
 * Conteúdo do hover do LSP (textual, mesma família de references/definition):
 * palavras-chave Kof, tipos primitivos e variáveis locais do arquivo aberto.
 * Nenhum parser roda por request.
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
    static String hoverFor(String word, String text) {
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
        return null;
    }
}
