package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Statements Java→Kof do {@code kof translate} (extraído de {@link Translate}
 * p/ o gate ≤500). {@link Translate.Emitter} estende esta classe e herda
 * {@code p} + os métodos de expressão/tipo ({@link TranslateExpr}) + os
 * statements daqui.
 */
class TranslateStatements extends TranslateExpr {

    TranslateStatements(Parser p) { super(p); }

    @Override
    protected List<String> parseStatementBlock() {
        return parseBlock();
    }

    @Override
    protected String parseSwitchExprHook() {
        return TranslateSwitch.parse(p, this::parseExpr);
    }

    // ── statements ─────────────────────────────────────────────────────

    protected List<String> parseBlock() {
        p.expect("{");
        List<String> stmts = new ArrayList<>();
        while (!p.at("}")) {
            stmts.add(parseStatement());
        }
        p.expect("}");
        return stmts;
    }

    protected void skipBlock() {
        p.expect("{");
        int depth = 1;
        while (depth > 0) {
            if (p.at("{")) depth++;
            if (p.at("}")) depth--;
            p.next();
        }
    }

    protected String parseStatement() {
        if (p.at(";")) {
            // Instrução vazia Java (`;`) — sem efeito; Kof não tem, então
            // descarta (antes: `expected ';' but found 'return'` confuso).
            p.next();
            return "";
        }
        if (p.at("class") || p.at("interface") || p.at("enum") || p.at("record")) {
            // Classe LOCAL (`void f() { class B { ... } }`) — Kof não tem
            // tipos aninhados/locais (SEM042) → gap honesto R6 (antes:
            // `expected ';' but found 'B'` confuso).
            throw new TranslateException(
                    "classe/tipo LOCAL (`class`/`interface`/`enum`/`record` dentro de método) não "
                    + "tem equivalente em Kof (SEM042: sem tipos aninhados) — revisão manual");
        }
        if (p.at("{")) {
            List<String> body = parseBlock();
            StringBuilder sb = new StringBuilder("{ ");
            for (String s : body) if (!s.isEmpty()) sb.append(s).append(' ');
            return sb.append('}').toString().trim();
        }
        if (p.at("this") && p.peek(1).text.equals("(")) {
            // Delegação de construtor Java `this(...)` — Kof não tem (probe:
            // `variable 'this' is not a function` = SEM015). Sem equivalente
            // direto (duplicar o corpo ou usar um `init` privado) → gap
            // honesto R6 (bug latente Q4 13/09).
            throw new TranslateException(
                    "delegação de construtor `this(...)` não tem equivalente direto em Kof "
                    + "(SEM015) — duplique o corpo ou extraia um método privado — revisão manual");
        }
        if (p.peek().type == T.IDENT && p.peek(1).text.equals(":")) {
            // Labeled statement Java (`outer: for (...)`) — Kof não tem
            // labels (verificado 13/09: `outer:` é PARSE041). Revisão manual
            // (R6): o desugar (flag booleana + condição) muda o fluxo.
            throw new TranslateException(
                    "labeled statement (`label:`) não tem equivalente direto em Kof "
                    + "(sem labels; use uma flag) — revisão manual");
        }
        if (p.at("return")) {
            p.next();
            if (p.at(";")) { p.next(); return "return"; }
            String e = parseExpr();
            p.expect(";");
            return "return " + e;
        }
        if (p.at("if")) {
            p.next();
            p.expect("(");
            String cond = parseExpr();
            p.expect(")");
            String thenBranch = parseStatement();
            String out = "if (" + cond + ") { " + thenBranch + " }";
            if (p.at("else")) {
                p.next();
                String elseBranch = parseStatement();
                out += " else { " + elseBranch + " }";
            }
            return out;
        }
        if (p.at("while")) {
            p.next();
            p.expect("(");
            String cond = parseExpr();
            p.expect(")");
            String body = parseStatement();
            return "while (" + cond + ") { " + body + " }";
        }
        if (p.at("do")) {
            // do-while Java → do-while Kof (idiom 1:1, training/idioms/control-flow.md).
            // `do` é keyword do TranslateLexer (KEYWORDS) mas nenhum statement a consumia —
            // caía em parseExprOrDecl → "expected ';' but found '{'".
            // parseStatement embrulha bloco em "{ ... }" — aqui o corpo precisa
            // do CONTEÚDO cru (o "do { ... }" já abre as chaves), senão sai
            // "do { { ... } }".
            p.next();
            String body;
            if (p.at("{")) {
                List<String> stmts = parseBlock();
                body = String.join(" ", stmts);
            } else {
                body = parseStatement();
            }
            p.expect("while");
            p.expect("(");
            String cond = parseExpr();
            p.expect(")");
            p.expect(";");
            return "do { " + body + " } while (" + cond + ")";
        }
        if (p.at("for")) {
            return parseFor();
        }
        if (p.at("switch")) {
            return parseSwitch();
        }
        if (p.at("try")) {
            return parseTry();
        }
        if (p.at("throw")) {
            p.next();
            String e = parseExpr();
            p.expect(";");
            return "throw " + e;
        }
        if (p.at("assert")) {
            // `assert cond;` / `assert cond : msg;` Java → `assert(cond)`
            // / `assert(cond, "msg")` Kof (primitive de teste, ver
            // AssertE2ETest — é função, não keyword).
            p.next();
            String cond = parseExpr();
            if (p.at(":")) {
                p.next();
                String msg = parseExpr();
                p.expect(";");
                return "assert(" + cond + ", " + msg + ")";
            }
            p.expect(";");
            return "assert(" + cond + ")";
        }
        // local variable declaration or expression statement.
        return parseExprOrDecl();
    }

    private String parseFor() {
        p.next();
        p.expect("(");
        if (forHasColon()) {
            // enhanced for: [Type] ident ':' expr
            parseType();
            String varName = p.next().text;
            p.expect(":");
            String coll = parseExpr();
            p.expect(")");
            String body = parseStatement();
            return "for (var " + varName + " in " + coll + ") { " + body + " }";
        }
        // Kof `for` C-style NÃO aceita init/incr com vírgula (`i=0, j=3` /
        // `i++, j--` — verificado 13/09: PARSE041). Detectar a vírgula no
        // cabeçalho ANTES do parse (senão `parseExpr` quebra) e devolver gap
        // honesto: desugar p/ while muda o fluxo (continue pula o incr).
        if (forHeaderHasComma()) {
            throw new TranslateException(
                    "`for` com init/incr múltiplos (`i=0, j=3` / `i++, j--`) não tem "
                    + "equivalente direto em Kof (for não aceita vírgula) — revisão manual");
        }
        String init = "";
        if (!p.at(";")) init = parseForInit();
        p.expect(";");
        String cond = "";
        if (!p.at(";")) cond = parseExpr();
        p.expect(";");
        String incr = "";
        if (!p.at(")")) incr = parseExpr();
        p.expect(")");
        String body = parseStatement();
        return "for (" + init + "; " + cond + "; " + incr + ") { " + body + " }";
    }

    /** Vírgula no nível do cabeçalho do `for` (init ou incr), fora de `[...]`/`(...)`. */
    private boolean forHeaderHasComma() {
        int depth = 0;
        for (int i = p.pos; i < p.toks.size(); i++) {
            String t = p.toks.get(i).text;
            if (t.equals("(") || t.equals("[") || t.equals("{")) depth++;
            else if (t.equals(")") || t.equals("]") || t.equals("}")) { if (depth == 0) return false; depth--; }
            else if (depth == 0 && t.equals(",")) return true;
            else if (depth == 0 && t.equals(";")) continue;
        }
        return false;
    }

    private boolean forHasColon() {
        int depth = 0;
        for (int i = p.pos; i < p.toks.size(); i++) {
            String t = p.toks.get(i).text;
            if (t.equals("(")) depth++;
            else if (t.equals(")")) { if (depth == 0) return false; depth--; }
            else if (depth == 0 && t.equals(":")) return true;
            else if (depth == 0 && t.equals(";")) return false;
        }
        return false;
    }

    private String parseForInit() {
        if (TranslateTypes.isPrimitiveOrType(p.peek().text) && p.peek(1).type == T.IDENT) {
            p.next(); // type
            String name = p.next().text;
            String expr = "";
            if (p.at("=")) { p.next(); expr = parseExpr(); }
            return "var " + name + (expr.isEmpty() ? "" : " = " + expr);
        }
        return parseExpr();
    }

    private String parseSwitch() {
        // switch Java-statement → switch Kof-statement (idiom 1:1,
        // training/idioms/control-flow.md: `case N:` + sem fallthrough).
        // `switch`/`case`/`break`/`default` são keywords do TranslateLexer
        // mas nenhum statement as consumia — caía em parseExprOrDecl →
        // "expected ';' but found '('".
        // Mapeamento: `break` é OPCIONAL em Kof (auto-termina, sem
        // fallthrough — verificado 02/09) → dropar; labels múltiplos
        // `case 1, 2:` → cases separados; corpo de case = statements até
        // o próximo `case`/`default`/`}` (consumindo `break;` e `:`).
        p.next(); // switch
        p.expect("(");
        String subj = parseExpr();
        p.expect(")");
        p.expect("{");
        StringBuilder sb = new StringBuilder("switch (" + subj + ") {");
        while (!p.at("}")) {
            if (p.at("case")) {
                p.next();
                // labels: expr [, expr]*
                List<String> labels = new ArrayList<>();
                labels.add(parseExpr());
                while (p.at(",")) { p.next(); labels.add(parseExpr()); }
                if (p.at(T.ARROW)) {
                    // Java 14+ arrow-switch `case 1 -> stmt` → `case 1:` Kof
                    // (Kof-statement usa `:`; `->` é a forma expressão SYN001).
                    p.next();
                } else {
                    p.expect(":");
                }
                for (String label : labels) {
                    sb.append(" case ").append(label).append(":");
                }
                // corpo: statements até case/default/}; `break;` → drop.
                boolean any = false;
                while (!p.at("case") && !p.at("default") && !p.at("}")) {
                    if (p.at("break")) { p.next(); p.expect(";"); continue; }
                    sb.append(' ').append(parseStatement());
                    any = true;
                }
                if (!any) sb.append(" {}");
            } else if (p.at("default")) {
                p.next();
                if (p.at(T.ARROW)) p.next(); else p.expect(":");
                sb.append(" default:");
                boolean any = false;
                while (!p.at("case") && !p.at("default") && !p.at("}")) {
                    if (p.at("break")) { p.next(); p.expect(";"); continue; }
                    sb.append(' ').append(parseStatement());
                    any = true;
                }
                if (!any) sb.append(" {}");
            } else {
                throw new TranslateException("expected 'case'/'default' but found '" + p.peek().text + "'");
            }
        }
        p.expect("}");
        sb.append(" }");
        return sb.toString();
    }

    private String parseTry() {
        // try/catch/finally Java → try/catch(String)/finally Kof
        // (training/idioms/errors.md: exceções são Strings).
        // `try`/`catch`/`finally`/`throw` são keywords do TranslateLexer
        // mas nenhum statement as consumia — caía em parseExprOrDecl →
        // "expected ';' but found '{'" (try) / "found 'new'" (throw).
        // `catch (Type name)` → `catch (String name)` — Kof só tem
        // exceção-String (SEM026 rejeita throw não-String em compile-time).
        // Multi-catch Java `catch (A | B e)` → catch único (Kof não tem
        // união de tipos em catch). Bloco vazio → `{}`.
        p.next(); // try
        if (p.at("(")) {
            // `try (R r = ...) { }` — Kof não tem try-with-resources nem
            // AutoCloseable (RAII é plano futuro). Desugar mecânico p/
            // `try/finally` exige `if (r != null) r.close()` + o tipo Java
            // pode nem existir em Kof → revisão manual (R6: nunca silencioso).
            throw new TranslateException(
                    "try-with-resources (`try (R r = ...)`) não tem equivalente direto em Kof "
                    + "(sem AutoCloseable; use `try/finally` + `r.close()`) — revisão manual");
        }
        List<String> tryBody = parseBlock();
        String tryStr = tryBody.isEmpty() ? "{}"
                : "{ " + String.join(" ", tryBody) + " }";
        StringBuilder sb = new StringBuilder("try " + tryStr);
        while (p.at("catch")) {
            p.next();
            p.expect("(");
            // [final] Type [| Type]* name
            if (p.at("final")) p.next();
            p.next(); // type (descartado — Kof: String)
            // union `catch (A | B e)` → catch único (Kof não tem união
            // de tipos em catch; `|` agora é T.PIPE no lexer).
            while (p.at(T.PIPE)) { p.next(); p.next(); } // '|' TipoExtra
            String varName = p.next().text;
            p.expect(")");
            List<String> catchBody = parseBlock();
            String catchStr = catchBody.isEmpty() ? "{}"
                    : "{ " + String.join(" ", catchBody) + " }";
            sb.append(" catch (String ").append(varName).append(") ").append(catchStr);
        }
        if (p.at("finally")) {
            p.next();
            List<String> finBody = parseBlock();
            String finStr = finBody.isEmpty() ? "{}"
                    : "{ " + String.join(" ", finBody) + " }";
            sb.append(" finally ").append(finStr);
        }
        return sb.toString();
    }

    private String parseExprOrDecl() {
        int save = p.pos;
        // Detect "Type name [= expr];" / "Type[] name ..." / "Type<...> name ..."
        if (isLocalDeclAhead()) {
            // modificador local `final int y = 2` — Kof não tem `final` em
            // local (vars são mutáveis; sem `val` local) → descarta o
            // modificador e traduz a declaração (bug latente Q4 13/09).
            while (p.at("final")) p.next();
            p.next(); // type base
            // tipo qualificado `java.util.List` — o tipo simples é o último
            // segmento (`List`); o pacote é descartado (Kof referencia pelo
            // nome simples; a decl local vira `var`).
            while (p.at(".") && p.peek(1).type == T.IDENT) {
                p.next();
                p.next();
            }
            int dims = 0;
            if (p.at("<")) {  // generics: List<String> xs
                int depth = 0;
                do {
                    if (p.at("<")) depth++;
                    else if (p.at(">")) depth--;
                    p.next();
                } while (depth > 0 && !p.at(T.EOF));
            }
            while (p.at("[")) { p.next(); p.expect("]"); dims++; }  // Type[] name
            String name = p.next().text;
            while (p.at("[")) { p.next(); p.expect("]"); dims++; }  // Type name[]
            if (p.at("=")) {
                p.next();
                if (p.at("{")) {
                    // Array initializer `int[] xs = {1,2,3}` não tem literal
                    // equivalente em Kof (não existe `{...}` — arrays são
                    // `new Int[n]` + atribuição, ou `listOf` p/ List).
                    // Revisão manual (R6: nunca silencioso).
                    throw new TranslateException(
                            "array initializer `{...}` não tem equivalente direto em Kof "
                            + "(use `new Int[n]` + atribuições ou `listOf(...)`) — revisão manual");
                }
                String e = parseExpr();
                List<String> decls = new ArrayList<>();
                decls.add("var " + name + " = " + e);
                // multi-declaração Java `int x = 1, y = 2;` → statements
                // Kof separados por espaço dentro do bloco (não há `,` em
                // decl Kof). `int x = 1, y = f()` também.
                while (p.at(",")) {
                    p.next();
                    String n2 = p.next().text;
                    if (p.at("=") ) { p.next(); n2 = "var " + n2 + " = " + parseExpr(); }
                    else { n2 = "var " + n2; }
                    decls.add(n2);
                }
                p.expect(";");
                return String.join(" ", decls);
            }
            // multi-declaração sem init: `int x, y;`
            List<String> decls = new ArrayList<>();
            decls.add("var " + name);
            while (p.at(",")) {
                p.next();
                decls.add("var " + p.next().text);
            }
            p.expect(";");
            return String.join(" ", decls);
        }
        p.pos = save;
        String e = parseExpr();
        p.expect(";");
        return e;
    }

    /** Lookahead: "Type[<...>][[]...] name ..." — declaração local. */
    private boolean isLocalDeclAhead() {
        // Java `var x = 1` — `var` é o mesmo nome reservado do Kof; a
        // declaração traduz como ela mesma (`var x = 1`).
        // Modificador local `final` é descartado (Kof não tem final local).
        int base = p.pos;
        while (base < p.toks.size() && p.toks.get(base).text.equals("final")) base++;
        if (p.toks.get(base).text.equals("var")) {
            return base + 1 < p.toks.size() && p.toks.get(base + 1).type == T.IDENT;
        }
        if (!TranslateTypes.isPrimitiveOrType(p.toks.get(base).text)) {
            // tipo qualificado `java.util.List<...> name` — o primeiro
            // segmento é pacote minúsculo; reconhece a cadeia e exige que o
            // último segmento seja um tipo (maiúsculo).
            int j = base;
            boolean chain = false;
            while (j + 1 < p.toks.size() && p.toks.get(j + 1).text.equals(".")
                    && p.toks.get(j + 2).type == T.IDENT) {
                j += 2;
                chain = true;
            }
            if (!chain || !TranslateTypes.isPrimitiveOrType(p.toks.get(j).text)) return false;
            int k = j + 1;
            if (k < p.toks.size() && p.toks.get(k).text.equals("<")) {
                int depth = 0;
                while (k < p.toks.size()) {
                    String t = p.toks.get(k).text;
                    if (t.equals("<")) depth++;
                    else if (t.equals(">")) { depth--; if (depth == 0) { k++; break; } }
                    k++;
                }
            }
            while (k + 1 < p.toks.size() && p.toks.get(k).text.equals("[")
                    && p.toks.get(k + 1).text.equals("]")) {
                k += 2;
            }
            return k < p.toks.size() && p.toks.get(k).type == T.IDENT;
        }
        int i = base + 1;
        // tipo qualificado `java.util.List<...>` — stripa o pacote no parse
        // (o tipo simples é o último segmento); reconhece a cadeia aqui.
        while (i + 1 < p.toks.size() && p.toks.get(i).text.equals(".")
                && p.toks.get(i + 1).type == T.IDENT) {
            i += 2;
        }
        if (i < p.toks.size() && p.toks.get(i).text.equals("<")) {
            int depth = 0;
            while (i < p.toks.size()) {
                String t = p.toks.get(i).text;
                if (t.equals("<")) depth++;
                else if (t.equals(">")) { depth--; if (depth == 0) { i++; break; } }
                i++;
            }
        }
        while (i + 1 < p.toks.size() && p.toks.get(i).text.equals("[")
                && p.toks.get(i + 1).text.equals("]")) {
            i += 2;
        }
        return i < p.toks.size() && p.toks.get(i).type == T.IDENT;
    }
}
