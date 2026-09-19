package dev.kof.cli;

import dev.kof.compiler.StdCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LSP-A (plano universal 8.3, 19/09): {@code textDocument/signatureHelp}
 * consumindo a tabela {@code SIGNATURES} do StdCatalog — a mesma fonte unica
 * do hover, travada em {@code StdCatalogSignaturesTest}. Membro sem tabela
 * nao ganha chute (R6): o servidor responde null e o cliente nao mostra nada.
 */
public final class LspSignatureHelp {

    private LspSignatureHelp() { }

    /**
     * Resultado no formato LSP (signatures/activeSignature/activeParameter)
     * ou {@code null} quando o cursor nao esta dentro de uma chamada de
     * membro stdlib com tabela.
     */
    public static Map<String, Object> helpFor(String text, int offset) {
        if (text == null || text.isEmpty() || offset <= 0 || offset > text.length()) {
            return null;
        }
        int start = 0;
        for (int i = Math.min(offset, text.length()) - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') {
                start = i + 1;
                break;
            }
        }
        List<Integer> opens = new ArrayList<>();
        boolean inStr = false;
        for (int i = start; i < offset; i++) {
            char c = text.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
            } else if (c == '(') {
                opens.add(i);
            } else if (c == ')' && !opens.isEmpty()) {
                opens.remove(opens.size() - 1);
            }
        }
        if (opens.isEmpty()) {
            return null;
        }
        int open = opens.get(opens.size() - 1);
        String[] nsMember = resolveMember(text, open);
        if (nsMember == null) {
            return null;
        }
        List<String> forms = StdCatalog.signaturesOf(nsMember[0], nsMember[1]);
        if (forms.isEmpty()) {
            return null;
        }
        int active = 0;
        int depth = 0;
        boolean str = false;
        for (int i = open + 1; i < offset; i++) {
            char c = text.charAt(i);
            if (str) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    str = false;
                }
                continue;
            }
            if (c == '"') {
                str = true;
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                active++;
            }
        }
        List<Map<String, Object>> sigs = new ArrayList<>();
        int widest = 0;
        for (String form : forms) {
            sigs.add(toSignature(form));
            widest = Math.max(widest, paramLabels(form).size());
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("signatures", sigs);
        res.put("activeSignature", 0L);
        res.put("activeParameter", (long) (widest == 0 ? 0 : Math.min(active, widest - 1)));
        return res;
    }

    /** `ns.member(` no contexto exato do catalogo; membro solto so quando
     *  unico namespace da tabela o contem (ambiguo => null, nunca chute). */
    private static String[] resolveMember(String text, int open) {
        int j = open;
        while (j > 0 && (Character.isLetterOrDigit(text.charAt(j - 1)) || text.charAt(j - 1) == '_')) {
            j--;
        }
        String member = text.substring(j, open);
        if (member.isEmpty()) {
            return null;
        }
        if (j > 0 && text.charAt(j - 1) == '.') {
            int p = j - 1;
            while (p > 0 && (Character.isLetterOrDigit(text.charAt(p - 1)) || text.charAt(p - 1) == '_')) {
                p--;
            }
            String ns = text.substring(p, j - 1);
            return StdCatalog.isNamespace(ns) ? new String[] { ns, member } : null;
        }
        String found = null;
        int hits = 0;
        for (String ns : StdCatalog.namespaces()) {
            if (!StdCatalog.signaturesOf(ns, member).isEmpty()) {
                found = ns;
                hits++;
            }
        }
        return hits == 1 ? new String[] { found, member } : null;
    }

    private static Map<String, Object> toSignature(String form) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (String p : paramLabels(form)) {
            params.add(Map.of("label", p));
        }
        Map<String, Object> sig = new LinkedHashMap<>();
        sig.put("label", form);
        sig.put("parameters", params);
        return sig;
    }

    private static List<String> paramLabels(String form) {
        int a = form.indexOf('(');
        int b = form.lastIndexOf(')');
        List<String> out = new ArrayList<>();
        if (a < 0 || b <= a) {
            return out;
        }
        String inner = form.substring(a + 1, b);
        int depth = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (!inner.isBlank()) {
            out.add(sb.toString().trim());
        }
        return out;
    }
}
