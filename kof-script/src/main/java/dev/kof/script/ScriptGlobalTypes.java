package dev.kof.script;

import java.util.Map;

/**
 * Inferência de tipos dos `var`/`val` de topo do KofScript sem anotação —
 * os campos estáticos de `KofScriptGlobals` precisam de um tipo declarado
 * (Kof não tem campo sem tipo). Separado de {@link KofScript} (gate 500):
 * esta classe conhece apenas literais, construtores e a superfície de
 * coleção/string do corpus; o wrapper usa {@link #infer} no primeiro passo
 * e {@link #chain} quando o init referencia outro global.
 */
final class ScriptGlobalTypes {

    private ScriptGlobalTypes() { }

    static String infer(String init) {
        String t = init.strip();
        // string literal
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) return "String";
        if ("true".equals(t) || "false".equals(t)) return "Bool";
        if (t.matches("-?\\d+")) return "Int";
        if (t.matches("-?\\d*\\.\\d+([eE][+-]?\\d+)?")) return "Double";
        if (t.startsWith("0x") || t.startsWith("0X")) return "Int";
        // coleções literais: `listOf(...)`/`setOf(...)`/`mapOf(k, v, ...)` —
        // fallback antigo (Int/String) mistipava o campo estático e o
        // interpretador quebrava em runtime (bug da varredura 18/09).
        String coll = collectionTypeOf(t);
        if (coll != null) return coll;
        // construtor/classe: `User("Mel", 26)` → User (name antes do `(`)
        int paren = t.indexOf('(');
        if (paren > 0 && t.substring(0, paren).matches("[A-Z]\\w*")) return t.substring(0, paren);
        // heuristic: contains quotes => String concatenation
        if (t.contains("\"") || t.contains("'")) return "String";
        // fallback to Int (most common) — explicit type in source is preferred
        return "Int";
    }

    /**
     * Tipo de um global sem anotação cujo init referencia OUTRO global:
     * cópia pura (`var d = items`) ou call única de API de coleção/string
     * conhecida. `null` = sem regra (mantém inferência própria). Cadeias
     * profundas (`a.map(...).filter(...)`) ficam limitadas — documento no
     * commit; forma idiomática continua anotar o tipo explicitamente.
     */
    static String chain(String init, java.util.Map<String,String> resolved) {
        String t = init.strip();
        if (t.matches("\\w+") && resolved.containsKey(t)) return resolved.get(t);
        var m = java.util.regex.Pattern.compile("^(\\w+)\\.(\\w+)\\(.*\\)$").matcher(t);
        if (!m.matches()) return null;
        String recv = resolved.get(m.group(1));
        if (recv == null) return null;
        String method = m.group(2);
        boolean isList = recv.startsWith("List<") || "List".equals(recv);
        boolean isSet = recv.startsWith("Set<") || "Set".equals(recv);
        boolean isMap = recv.startsWith("Map<");
        if (isList || isSet) {
            if (isList && ("map".equals(method) || "filter".equals(method) || "take".equals(method) || "drop".equals(method))) return recv;
            if ("contains".equals(method) || "isEmpty".equals(method)) return "Bool";
            if (isList && "size".equals(method)) return "Int";
        }
        if (isMap) {
            String[] kv = mapKV(recv);
            if ("get".equals(method) && kv != null) return kv[1];
            if ("keys".equals(method) && kv != null) return "List<" + kv[0] + ">";
            if ("values".equals(method) && kv != null) return "List<" + kv[1] + ">";
            if ("containsKey".equals(method) || "isEmpty".equals(method)) return "Bool";
        }
        if ("String".equals(recv)) {
            if ("toUpperCase".equals(method) || "toLowerCase".equals(method) || "trim".equals(method) || "substring".equals(method) || "replace".equals(method)) return "String";
            if ("contains".equals(method) || "startsWith".equals(method) || "endsWith".equals(method) || "isEmpty".equals(method)) return "Bool";
            if ("length".equals(method) || "indexOf".equals(method)) return "Int";
        }
        return null;
    }

    private static String[] mapKV(String mapType) {
        int lt = mapType.indexOf('<'), gt = mapType.lastIndexOf('>');
        if (lt < 0 || gt < lt) return null;
        String[] kv = mapType.substring(lt + 1, gt).split(",");
        if (kv.length != 2) return null;
        return new String[] { kv[0].strip(), kv[1].strip() };
    }

    /** `listOf(1,2)`/`setOf("a")`/`mapOf("a",1)` → tipo element-typed; null se não-coleção. */
    private static String collectionTypeOf(String t) {
        java.util.List<String> args = null;
        String kind = null;
        for (String[] f : new String[][] { {"listOf(", "List"}, {"setOf(", "Set"}, {"mapOf(", "Map"} }) {
            if (t.startsWith(f[0]) && t.endsWith(")")) {
                kind = f[1];
                args = splitTopLevel(t.substring(f[0].length(), t.length() - 1));
                break;
            }
        }
        if (kind == null) return null;
        if ("Map".equals(kind)) {
            String k = args.isEmpty() ? "String" : elemType(args.get(0));
            String v = args.size() < 2 ? "Int" : elemType(args.get(1));
            return "Map<" + k + "," + v + ">";
        }
        String e = null;
        for (String a : args) {
            String et = elemType(a);
            if (e == null) e = et;
            else if (!e.equals(et)) { e = "String"; break; } // homogêneo não inferível
        }
        return kind + "<" + (e == null ? "Int" : e) + ">";
    }

    private static String elemType(String lit) {
        String a = lit.strip();
        if (a.matches("-?\\d+")) return "Int";
        if (a.matches("-?\\d*\\.\\d+")) return "Double";
        if ("true".equals(a) || "false".equals(a)) return "Bool";
        if ((a.startsWith("\"") && a.endsWith("\"")) || (a.startsWith("'") && a.endsWith("'"))) return "String";
        return a.contains("\"") || a.contains("'") ? "String" : "Int";
    }

    private static java.util.List<String> splitTopLevel(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        char q = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inStr) {
                cur.append(ch);
                if (ch == q) inStr = false;
                continue;
            }
            if (ch == '"' || ch == '\'') { inStr = true; q = ch; cur.append(ch); continue; }
            if (ch == '(' || ch == '[') depth++;
            if (ch == ')' || ch == ']') depth--;
            if (ch == ',' && depth == 0) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(ch);
        }
        if (!cur.toString().strip().isEmpty()) out.add(cur.toString());
        return out;
    }
}
