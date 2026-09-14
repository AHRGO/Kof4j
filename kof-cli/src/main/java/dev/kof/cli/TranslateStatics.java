package dev.kof.cli;

import java.util.HashSet;
import java.util.Set;

/**
 * Suporte a campos estáticos no {@code kof translate}.
 *
 * <p>Java `static` fields existem, mas o translator promove métodos `static`
 * (inclusive `main`) a funções top-level Kof (o idioma Kof). Uma referência
 * nua a um campo estático dentro de uma função hoisted fica fora de escopo
 * (`Undefined variable or type` — SEM011). A correção idiomática é qualificar
 * a referência como `Classe.campo` (Kof suporta campo estático de classe —
 * verificado 13/09). Este helper faz a varredura dos nomes e a qualificação
 * de forma segura (não toca literais de string/char nem identificadores já
 * qualificados por `.`).
 */
final class TranslateStatics {

    private TranslateStatics() {}

    /**
     * Pré-varredura do corpo de uma classe: coleta os nomes de campos
     * `static` (não métodos). `start` = posição logo após o `{` da classe.
     * Pula o `{`...`}` casado; considera apenas membros no nível 1.
     */
    static Set<String> scanFieldNames(Parser p, int start) {
        Set<String> names = new HashSet<>();
        int depth = 1;
        int i = start;
        var toks = p.toks;
        while (i < toks.size() && depth > 0) {
            String t = toks.get(i).text;
            if (t.equals("{")) { depth++; i++; continue; }
            if (t.equals("}")) { depth--; i++; continue; }
            if (depth == 1 && t.equals("static")) {
                int k = i + 1;
                // pula `final`/`transient`/`volatile` e o tipo (com generics/[])
                while (k < toks.size() && isFieldModifier(toks.get(k).text)) k++;
                k = skipType(toks, k);
                if (k < toks.size() && toks.get(k).type == T.IDENT) {
                    String nm = toks.get(k).text;
                    int after = k + 1;
                    if (after < toks.size()
                            && (toks.get(after).text.equals("=") || toks.get(after).text.equals(";")
                                || toks.get(after).text.equals("[") || toks.get(after).text.equals(","))) {
                        names.add(nm);
                    }
                }
            }
            i++;
        }
        return names;
    }

    private static boolean isFieldModifier(String s) {
        return switch (s) {
            case "final", "transient", "volatile" -> true;
            default -> false;
        };
    }

    /** Avança sobre um tipo (qualificado, genérico, array) a partir de `i`. */
    private static int skipType(java.util.List<Tok> toks, int i) {
        if (i >= toks.size()) return i;
        i++; // base do tipo (int/String/T)
        while (i + 1 < toks.size() && toks.get(i).text.equals(".")
                && toks.get(i + 1).type == T.IDENT) {
            i += 2;
        }
        if (i < toks.size() && toks.get(i).text.equals("<")) {
            int depth = 0;
            while (i < toks.size()) {
                String t = toks.get(i).text;
                if (t.equals("<")) depth++;
                else if (t.equals(">")) { depth--; if (depth == 0) { i++; break; } }
                i++;
            }
        }
        while (i + 1 < toks.size() && toks.get(i).text.equals("[")
                && toks.get(i + 1).text.equals("]")) {
            i += 2;
        }
        return i;
    }

    /**
     * Qualifica referências nuas a campos estáticos em um statement já
     * emitido: `X` → `Owner.X`. Não toca conteúdo de string/char literals,
     * identificadores já precedidos por `.` (ex.: `outro.X`), nem nomes que
     * são parâmetros/locais do método (`shadowed`).
     */
    static String qualify(String stmt, String owner, Set<String> fields, Set<String> shadowed) {
        if (fields.isEmpty() || stmt == null || stmt.isEmpty()) return stmt;
        StringBuilder sb = new StringBuilder(stmt.length() + 16);
        int i = 0;
        int n = stmt.length();
        char prev = 0;
        while (i < n) {
            char c = stmt.charAt(i);
            if (c == '"' || c == '\'') {
                char q = c;
                sb.append(c); i++;
                while (i < n) {
                    char d = stmt.charAt(i);
                    sb.append(d);
                    if (d == '\\' && i + 1 < n) { sb.append(stmt.charAt(i + 1)); i += 2; continue; }
                    i++;
                    if (d == q) break;
                }
                prev = q;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                while (j < n && Character.isJavaIdentifierPart(stmt.charAt(j))) j++;
                String word = stmt.substring(i, j);
                boolean qualified = prev == '.';
                if (!qualified && fields.contains(word) && !shadowed.contains(word)) {
                    sb.append(owner).append('.').append(word);
                } else {
                    sb.append(word);
                }
                prev = stmt.charAt(j - 1);
                i = j;
                continue;
            }
            sb.append(c);
            prev = c;
            i++;
        }
        return sb.toString();
    }
}
