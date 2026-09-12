package dev.kof.compiler.js;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventário do runtime JS por UNIDADE DE TOPO (issue #97, T2/S-6).
 *
 * Os blocos do runtime são concatenados pelo {@link JsArtifactWriter}; a
 * fronteira entre eles é acidental (o corte em 17 constantes veio do limite de
 * 64 KiB do pool de constantes do javac, não de fronteira semântica), então
 * poda por bloco não serve: o fecho de um `println` pega 89% dos bytes. A
 * unidade de poda é a declaração de topo — `function`/`class`/`const` na
 * coluna 0 depois de desindentar o bloco.
 *
 * Determinismo: a saída é sempre a ordem do inventário (bloco, unidade),
 * nunca a ordem de descoberta da busca nem de iteração de mapa.
 *
 * Fallback conservador (R6): o que a análise não consegue PROVAR que entende
 * entra inteiro, com motivo registrado — bytes a mais, nunca artefato quebrado.
 */
final class JsRuntimeSlices {

    /** Bloco do runtime, na mesma ordem em que o writer os concatena. */
    record Block(String name, String text, boolean io) {}

    /** Unidade de topo: o que declara, o que referencia, e seu texto literal. */
    record Unit(int block, List<String> provides, Set<String> needs, boolean always, String text) {}

    /** Seleção pronta para escrita, mais as notas de fallback (observabilidade). */
    record Selection(String coreText, String ioText, List<String> notes, int units, int total) {}

    static final List<Block> BLOCKS = List.of(
            new Block("core", JsRuntimeCore.CORE_RUNTIME, false),
            new Block("ui-components", JsRuntimeUiComponents.UI_COMPONENT_RUNTIME, false),
            new Block("ui-widgets", JsRuntimeUiWidgets.UI_WIDGET_RUNTIME, false),
            new Block("ui-forms", JsRuntimeUiForms.UI_FORMS_RUNTIME, false),
            new Block("ui-layout", JsRuntimeUiLayout.UI_LAYOUT_RUNTIME, false),
            new Block("ui-web", JsRuntimeUiWeb.UI_WEB_RUNTIME, false),
            new Block("ui-support", JsRuntimeUiSupport.UI_SUPPORT_RUNTIME, false),
            new Block("security", JsRuntimeUiSecurity.UI_SECURITY_RUNTIME, false),
            new Block("crypto", JsRuntimeUiCrypto.UI_CRYPTO_RUNTIME, false),
            new Block("validation", JsRuntimeUiValidation.UI_VALIDATION_RUNTIME, false),
            new Block("stdlib", JsRuntimeUiStdlib.STDLIB_RUNTIME, false),
            new Block("random", JsRuntimeUiRandom.RANDOM_RUNTIME, false),
            new Block("math-double", JsRuntimeUiMathDouble.MATH_DOUBLE_RUNTIME, false),
            new Block("net", JsRuntimeUiNet.NET_RUNTIME, false),
            new Block("uuid", JsRuntimeUiUuid.UUID_RUNTIME, false),
            new Block("ws", JsRuntimeUiWs.WS_RUNTIME, false),
            new Block("ui-events", JsRuntimeUiEvents.UI_EVENT_RUNTIME, false),
            new Block("io", JsRuntimeIo.IO_RUNTIME, true));

    private static final Pattern DECL = Pattern.compile(
            "^(?:export\\s+)?(?:async\\s+)?(?:function|class|const|let|var)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern INNER_DECL = Pattern.compile(
            "^\\s+(?:export\\s+)?(?:async\\s+)?(?:function|class)\\s+([A-Za-z_$][\\w$]*)",
            Pattern.MULTILINE);
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final Pattern QUOTED = Pattern.compile("[\"']([A-Za-z_$][\\w$]*)[\"']");

    /** Padrões que a análise estática não resolve — presença ⇒ bloco inteiro. */
    private static final List<Map.Entry<Pattern, String>> GUARDS = List.of(
            Map.entry(Pattern.compile("\\beval\\s*\\("), "eval()"),
            Map.entry(Pattern.compile("\\bnew\\s+Function\\s*\\("), "new Function()"),
            Map.entry(Pattern.compile("\\bimport\\s*\\("), "import() dinâmico"),
            Map.entry(Pattern.compile("globalThis\\s*\\["), "globalThis[...]"),
            Map.entry(Pattern.compile("window\\s*\\["), "window[...]"));

    private static List<Unit> units;
    private static Map<String, Integer> owner;
    private static Map<Integer, String> blockFallback;

    private JsRuntimeSlices() {}

    // ── inventário ──────────────────────────────────────────────────

    private static synchronized void build() {
        if (units != null) return;
        List<Unit> inv = new ArrayList<>();
        Map<Integer, String> fallback = new LinkedHashMap<>();
        for (int b = 0; b < BLOCKS.size(); b++) {
            Block block = BLOCKS.get(b);
            List<Unit> blockUnits = chunk(b, block.text());
            String reason = guardReason(block.text(), blockUnits);
            if (reason != null) {
                fallback.put(b, reason);
                blockUnits = List.of(new Unit(b, List.of(), Set.of(), true, block.text()));
            }
            inv.addAll(blockUnits);
        }
        Map<String, Integer> providers = new LinkedHashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            for (String name : inv.get(i).provides()) providers.putIfAbsent(name, i);
        }
        // nome do runtime dentro de literal de string = referência que a
        // análise não enxerga: o bloco que a contém entra inteiro.
        for (int b = 0; b < BLOCKS.size(); b++) {
            if (fallback.containsKey(b)) continue;
            String quoted = quotedRuntimeName(BLOCKS.get(b).text(), providers.keySet());
            if (quoted != null) fallback.put(b, "nome de runtime em literal (\"" + quoted + "\")");
        }
        if (!fallback.isEmpty()) {
            inv = rebuildWithFallbacks(fallback);
            providers = new LinkedHashMap<>();
            for (int i = 0; i < inv.size(); i++) {
                for (String name : inv.get(i).provides()) providers.putIfAbsent(name, i);
            }
        }
        units = List.copyOf(inv);
        owner = Map.copyOf(providers);
        blockFallback = Map.copyOf(fallback);
    }

    private static List<Unit> rebuildWithFallbacks(Map<Integer, String> fallback) {
        List<Unit> inv = new ArrayList<>();
        for (int b = 0; b < BLOCKS.size(); b++) {
            String text = BLOCKS.get(b).text();
            if (fallback.containsKey(b)) {
                inv.add(new Unit(b, List.of(), Set.of(), true, text));
            } else {
                inv.addAll(chunk(b, text));
            }
        }
        return inv;
    }

    /**
     * Fatia o bloco nas declarações de topo; o preâmbulo/wrapper sempre entra.
     *
     * A desindentação vale só para ACHAR as fronteiras (cada text block tem sua
     * base própria) — o texto emitido é o original, byte a byte, porque
     * desindentar mudaria o conteúdo de literais de template (CSS, HTML).
     */
    private static List<Unit> chunk(int block, String text) {
        String[] raw = text.split("\n", -1);
        String[] code = strip(dedent(text)).split("\n", -1);
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < Math.min(raw.length, code.length); i++) {
            if (DECL.matcher(code[i]).find()) starts.add(i);
        }
        List<Unit> out = new ArrayList<>();
        if (starts.isEmpty()) {
            out.add(new Unit(block, List.of(), Set.of(), true, text));
            return out;
        }
        if (starts.get(0) > 0) out.add(unit(block, raw, code, 0, starts.get(0), true));
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : raw.length;
            out.add(unit(block, raw, code, from, to, false));
        }
        return out;
    }

    private static Unit unit(int block, String[] raw, String[] code, int from, int to, boolean always) {
        String text = String.join("\n", List.of(raw).subList(from, Math.min(to, raw.length)));
        String body = String.join("\n", List.of(code).subList(from, Math.min(to, code.length)));
        Set<String> provides = new LinkedHashSet<>();
        if (!always) {
            Matcher head = DECL.matcher(code[from]);
            if (head.find()) provides.add(head.group(1));
        }
        Matcher inner = INNER_DECL.matcher(body);
        while (inner.find()) provides.add(inner.group(1));
        Set<String> needs = new LinkedHashSet<>();
        Matcher ids = IDENT.matcher(body);
        while (ids.find()) needs.add(ids.group());
        needs.removeAll(provides);
        return new Unit(block, List.copyOf(provides), Set.copyOf(needs), always, text);
    }

    /** Motivo pelo qual o bloco NÃO pode ser fatiado com segurança, ou null. */
    private static String guardReason(String text, List<Unit> blockUnits) {
        String code = strip(text);
        for (Map.Entry<Pattern, String> guard : GUARDS) {
            if (guard.getKey().matcher(code).find()) return guard.getValue();
        }
        for (Unit u : blockUnits) {
            String body = strip(u.text());
            if (count(body, '{') != count(body, '}') || count(body, '(') != count(body, ')')) {
                String who = u.provides().isEmpty() ? "preâmbulo" : u.provides().get(0);
                return "unidade não balanceada (" + who + ")";
            }
        }
        return null;
    }

    private static String quotedRuntimeName(String text, Set<String> provided) {
        Matcher m = QUOTED.matcher(stripCommentsOnly(text));
        while (m.find()) {
            if (provided.contains(m.group(1))) return m.group(1);
        }
        return null;
    }

    // ── seleção ─────────────────────────────────────────────────────

    /**
     * Fecho transitivo a partir das sementes (a lista de import do módulo).
     * Sementes desconhecidas são ignoradas aqui e reportadas pelo writer.
     */
    static Selection select(Collection<String> seeds) {
        build();
        boolean[] live = new boolean[units.size()];
        List<Integer> work = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).always()) {
                live[i] = true;
                work.add(i);
            }
        }
        for (String seed : new TreeSet<>(seeds)) {              // ordem estável
            Integer i = owner.get(seed);
            if (i != null && !live[i]) {
                live[i] = true;
                work.add(i);
            }
        }
        for (int w = 0; w < work.size(); w++) {
            for (String need : units.get(work.get(w)).needs()) {
                Integer i = owner.get(need);
                if (i != null && !live[i]) {
                    live[i] = true;
                    work.add(i);
                }
            }
        }
        StringBuilder core = new StringBuilder();
        StringBuilder io = new StringBuilder();
        int kept = 0;
        for (int b = 0; b < BLOCKS.size(); b++) {               // ordem do inventário
            StringBuilder block = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < units.size(); i++) {
                Unit u = units.get(i);
                if (u.block() != b || !live[i]) continue;
                kept++;
                // as unidades são faixas de LINHAS do bloco: a junção leva "\n"
                // entre unidades adjacentes, que é o que reconstrói o texto
                // original byte a byte quando nada é podado.
                if (!first) block.append('\n');
                block.append(u.text());
                first = false;
            }
            if (block.length() > 0 && block.charAt(block.length() - 1) != '\n') block.append('\n');
            (BLOCKS.get(b).io() ? io : core).append(block);
        }
        List<String> notes = new ArrayList<>();
        for (Map.Entry<Integer, String> e : blockFallback.entrySet()) {
            notes.add(BLOCKS.get(e.getKey()).name() + ": " + e.getValue());
        }
        return new Selection(core.toString(), io.toString(), List.copyOf(notes), kept, units.size());
    }

    /** Todos os nomes que o runtime declara (para testes e para o writer). */
    static Set<String> allProvided() {
        build();
        return owner.keySet();
    }

    static List<Unit> inventory() {
        build();
        return units;
    }

    // ── texto ───────────────────────────────────────────────────────

    /** Cada text block tem sua própria indentação base; a coluna 0 é relativa a ela. */
    private static String dedent(String text) {
        int base = Integer.MAX_VALUE;
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) continue;
            int i = 0;
            while (i < line.length() && line.charAt(i) == ' ') i++;
            base = Math.min(base, i);
        }
        if (base == Integer.MAX_VALUE || base == 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            out.append(line.length() >= base && line.substring(0, base).isBlank()
                    ? line.substring(base) : line).append('\n');
        }
        out.setLength(Math.max(0, out.length() - 1));
        return out.toString();
    }

    /**
     * Neutraliza comentários, strings, templates e literais de regex,
     * preservando posição de linha (cada caractere consumido vira espaço ou a
     * própria quebra). Regex exige scanner: `/` é divisão ou início de literal
     * conforme o último token significativo — e um `/[{}]/` mal lido
     * desbalanceia a contagem de chaves e derruba o bloco no fallback.
     */
    private static String strip(String text) {
        char[] out = text.toCharArray();
        int n = out.length;
        char prevSig = ';';
        for (int i = 0; i < n; i++) {
            char c = out[i];
            if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                while (i < n && out[i] != '\n') out[i++] = ' ';
                i--;
            } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                out[i] = out[i + 1] = ' ';
                i += 2;
                while (i < n && !(out[i] == '*' && i + 1 < n && out[i + 1] == '/')) {
                    if (out[i] != '\n') out[i] = ' ';
                    i++;
                }
                if (i + 1 < n) { out[i] = ' '; out[i + 1] = ' '; i++; }
            } else if (c == '"' || c == '\'' || c == '`') {
                i = blankLiteral(out, i, c);
                prevSig = 'x';
            } else if (c == '/' && startsRegex(prevSig)) {
                i = blankRegex(out, i);
                prevSig = 'x';
            } else if (!Character.isWhitespace(c)) {
                prevSig = c;
            }
        }
        return new String(out);
    }

    /** Depois destes tokens um `/` só pode abrir literal de regex. */
    private static boolean startsRegex(char prevSig) {
        return "(,=:[!&|?{};+-*%~^<>".indexOf(prevSig) >= 0;
    }

    private static int blankLiteral(char[] out, int start, char quote) {
        int i = start + 1;
        out[start] = ' ';
        while (i < out.length) {
            char c = out[i];
            if (c == '\\') {
                if (out[i] != '\n') out[i] = ' ';
                if (i + 1 < out.length && out[i + 1] != '\n') out[i + 1] = ' ';
                i += 2;
                continue;
            }
            if (c == quote) { out[i] = ' '; return i; }
            if (c == '\n' && quote != '`') return i - 1;   // string não fecha na linha
            if (c != '\n') out[i] = ' ';
            i++;
        }
        return out.length - 1;
    }

    private static int blankRegex(char[] out, int start) {
        int i = start + 1;
        boolean inClass = false;
        out[start] = ' ';
        while (i < out.length) {
            char c = out[i];
            if (c == '\n') return i - 1;                   // regex não cruza linha
            if (c == '\\') {
                out[i] = ' ';
                if (i + 1 < out.length && out[i + 1] != '\n') out[i + 1] = ' ';
                i += 2;
                continue;
            }
            if (c == '[') inClass = true;
            else if (c == ']') inClass = false;
            else if (c == '/' && !inClass) { out[i] = ' '; return i; }
            out[i] = ' ';
            i++;
        }
        return out.length - 1;
    }

    private static String stripCommentsOnly(String text) {
        Matcher m = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement("\n".repeat(count(m.group(), '\n'))));
        }
        m.appendTail(out);
        return out.toString().replaceAll("//[^\n]*", "");
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
}
