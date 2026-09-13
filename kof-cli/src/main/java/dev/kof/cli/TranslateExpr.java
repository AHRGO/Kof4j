package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser de expressões Java→Kof do {@code kof translate} (extraído p/ gate
 * <=500). {@link Translate.Emitter} estende esta classe e herda {@code p}
 * + os métodos de expressão/tipo.
 */
class TranslateExpr {

    final Parser p;

    TranslateExpr(Parser p) { this.p = p; }

        String parseExpr() {
            return parseTernary();
        }

        String parseTernary() {
            String cond = parseAssignment();
            if (p.at("?")) {
                p.next();
                String a = parseTernary();
                p.expect(":");
                String b = parseTernary();
                return "if (" + cond + ") " + a + " else " + b;
            }
            return cond;
        }

        String parseAssignment() {
            String lhs = parseOr();
            T t = p.peek().type;
            if (t == T.EQ) { p.next(); return lhs + " = " + parseAssignment(); }
            if (t == T.PLUSEQ) { p.next(); return lhs + " += " + parseAssignment(); }
            if (t == T.MINUSEQ) { p.next(); return lhs + " -= " + parseAssignment(); }
            if (t == T.STAREQ) { p.next(); return lhs + " *= " + parseAssignment(); }
            if (t == T.SLASHEQ) { p.next(); return lhs + " /= " + parseAssignment(); }
            if (t == T.PERCENTEQ) { p.next(); return lhs + " %= " + parseAssignment(); }
            return lhs;
        }

        String parseOr() {
            String e = parseAnd();
            while (p.at(T.OROR)) { p.next(); e = e + " || " + parseAnd(); }
            return e;
        }

        String parseAnd() {
            String e = parseBitOr();
            while (p.at(T.ANDAND)) { p.next(); e = e + " && " + parseBitOr(); }
            return e;
        }

        String parseBitOr() {
            String e = parseBitAnd();
            while (p.at(T.PIPE) || p.at(T.CARET)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseBitAnd();
            }
            return e;
        }

        String parseBitAnd() {
            String e = parseEquality();
            while (p.at(T.AMP)) { p.next(); e = e + " & " + parseEquality(); }
            return e;
        }

        String parseEquality() {
            String e = parseRel();
            while (p.at(T.EQEQ) || p.at(T.NE)) {
                if (p.at(T.EQEQ)) { p.next(); e = e + " == " + parseRel(); }
                else { p.next(); e = e + " != " + parseRel(); }
            }
            return e;
        }

        String parseRel() {
            String e = parseShift();
            while (p.at(T.LT) || p.at(T.LE) || p.at(T.GT) || p.at(T.GE) || p.at("instanceof")) {
                if (p.at("instanceof")) {
                    // `o instanceof String` → `o instanceof String` (Kof tem
                    // instanceof nativo, training/language/overview.md).
                    p.next();
                    e = e + " instanceof " + parseType();
                } else {
                    String op = p.next().text;
                    e = e + " " + op + " " + parseShift();
                }
            }
            return e;
        }

        String parseShift() {
            String e = parseAdd();
            while (true) {
                if (p.at(T.LT) && p.peek(1).type == T.LT) {
                    p.next(); p.next();
                    e = e + " << " + parseAdd();
                } else if (p.at(T.GT) && p.peek(1).type == T.GT && p.peek(2).type == T.GT) {
                    p.next(); p.next(); p.next();
                    e = e + " >>> " + parseAdd();
                } else if (p.at(T.GT) && p.peek(1).type == T.GT) {
                    p.next(); p.next();
                    e = e + " >> " + parseAdd();
                } else {
                    break;
                }
            }
            return e;
        }

        String parseAdd() {
            String e = parseMul();
            while (p.at(T.PLUS) || p.at(T.MINUS)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseMul();
            }
            return e;
        }

        String parseMul() {
            String e = parseUnary();
            while (p.at(T.STAR) || p.at(T.SLASH) || p.at(T.PERCENT)) {
                String op = p.next().text;
                e = e + " " + op + " " + parseUnary();
            }
            return e;
        }

        String parseUnary() {
            if (p.at(T.NOT)) { p.next(); return "!" + parseUnary(); }
            if (p.at(T.MINUS)) { p.next(); return "-" + parseUnary(); }
            if (p.at(T.PLUS)) { p.next(); return "+" + parseUnary(); }
            return parsePostfix();
        }

        String parsePostfix() {
            String e = parsePrimary();
            while (true) {
                if (p.at(".")) {
                    p.next();
                    String field = p.next().text;
                    if (p.at("(")) {
                        // method call on receiver
                        e = translateCall(e, field);
                    } else {
                        e = e + "." + field;
                    }
                } else if (p.at("[")) {
                    p.next();
                    String idx = parseExpr();
                    p.expect("]");
                    e = e + "[" + idx + "]";
                } else if (p.at("(")) {
                    // bare call: foo(args) — sem receiver. Sem este ramo,
                    // `boom("x");` caía em parseExprOrDecl → "expected ';'
                    // but found '('" (descoberto via try/catch 13/09: o corpo
                    // do try quase sempre chama métodos).
                    String args = parseCallArgs();
                    e = e + "(" + args + ")";
                } else if (p.at(T.INC)) { p.next(); e += "++"; }
                else if (p.at(T.DEC)) { p.next(); e += "--"; }
                else break;
            }
            return e;
        }

        String translateCall(String receiver, String method) {
            if (receiver.equals("System.out") && method.equals("println")) {
                String args = parseCallArgs();
                return "println(" + args + ")";
            }
            if (receiver.equals("System.out") && method.equals("print")) {
                String args = parseCallArgs();
                return "print(" + args + ")";
            }
            if (method.equals("equals")) {
                String arg = parseSingleArg();
                return receiver + " == " + arg;
            }
            if (method.equals("length")) {
                p.expect("("); p.expect(")");
                return receiver + ".length";
            }
            String args = parseCallArgs();
            return receiver + "." + method + "(" + args + ")";
        }

        String parseSingleArg() {
            p.expect("(");
            String e = parseExpr();
            p.expect(")");
            return e;
        }

        String parseCallArgs() {
            p.expect("(");
            List<String> args = new ArrayList<>();
            if (!p.at(")")) {
                args.add(parseExpr());
                while (p.at(",")) { p.next(); args.add(parseExpr()); }
            }
            p.expect(")");
            return String.join(", ", args);
        }

        String parsePrimary() {
            Tok t = p.next();
            return switch (t.type) {
                case INT, FLOAT -> t.text;
                case STR -> "\"" + t.text + "\"";
                case CHAR -> "'" + t.text + "'";
                case IDENT -> switch (t.text) {
                    case "true" -> "true";
                    case "false" -> "false";
                    case "null" -> "null";
                    case "new" -> parseNew();
                    case "this" -> "this";
                    case "throw" -> "throw " + parseExpr();
                    default -> t.text;
                };
                case P -> {
                    if (t.text.equals("(")) {
                        if (isLambdaAhead()) {
                            yield parseLambda();
                        }
                        if (isCastAhead()) {
                            yield parseCast();
                        }
                        String e = parseExpr();
                        p.expect(")");
                        // PRESERVAR os parênteses: descartá-los muda a
                        // semântica (`(1+2)*3` → `1+2*3` = 7, não 9) — bug
                        // latente de correção achado 13/09 (R6/Q0: compilável
                        // + semântica errada é o pior bug). Kof aceita
                        // parênteses redundantes.
                        yield "(" + e + ")";
                    }
                    yield t.text;
                }
                default -> t.text;
            };
        }

        boolean isLambdaAhead() {
            int depth = 1; // já estamos dentro do '('
            for (int i = p.pos; i + 1 < p.toks.size(); i++) {
                String s = p.toks.get(i).text;
                if (s.equals("(")) depth++;
                else if (s.equals(")")) {
                    depth--;
                    if (depth == 0) return p.toks.get(i + 1).type == T.ARROW;
                }
            }
            return false;
        }

        String parseLambda() {
            List<String> params = new ArrayList<>();  // '(' já consumido pelo parsePrimary
            if (!p.at(")")) {
                // (Type name, ...) — tipado; (name) — não-tipado (fallback)
                boolean typed = isPrimitiveOrType(p.peek().text) && p.peek(1).type == T.IDENT;
                if (typed) {
                    String ty = kofType(p.next().text);
                    String nm = p.next().text;
                    params.add(nm + ": " + ty);
                    while (p.at(",")) { p.next(); String t2 = kofType(p.next().text); String n2 = p.next().text; params.add(n2 + ": " + t2); }
                } else {
                    String nm = p.next().text;
                    params.add(nm);
                    while (p.at(",")) { p.next(); params.add(p.next().text); }
                }
            }
            p.expect(")");
            p.expect("->");
            String body = parseExpr();
            return "(" + String.join(", ", params) + ") -> " + body;
        }

        /** Lookahead: `(Type) expr` — cast Java. Kof usa `expr as Type`. */
        boolean isCastAhead() {
            // já consumimos '('; olha o próximo token
            String first = p.peek().text;
            if (!isPrimitiveOrType(first) && !first.equals("int") && !first.equals("boolean")
                    && !first.equals("char") && !first.equals("long") && !first.equals("double")) {
                return false;
            }
            int i = p.pos + 1;
            // genéricos: (List<String>) x
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
            return i < p.toks.size() && p.toks.get(i).text.equals(")");
        }

        /** `(String) o` → `o as String` (cast de conversão Kof). */
        String parseCast() {
            String type = parseType();
            p.expect(")");
            String operand = parseUnary();
            return operand + " as " + type;
        }

        String parseNew() {
            String typeName = p.next().text;
            // Nome qualificado `new java.util.ArrayList<...>()`: o translator
            // ignora imports e não resolve FQN. Mapear coleções Java →
            // stdlib Kof (`ArrayList`→`listOf`/`List`, `HashMap`→`Map`) é
            // decisão de design (regra 6) → revisão manual (R6), nunca parse
            // error confuso nem Kof inválido.
            if (p.at(".")) {
                throw new TranslateException(
                        "tipo qualificado (`new pacote.Classe(...)`) não é resolvido pelo "
                        + "translator (imports ignorados) — revisão manual");
            }
            // `new Box<Integer>(...)` — Kof infere o tipo na chamada
            // (`Box(5)`); os argumentos de tipo Java são descartados.
            if (p.at("<")) {
                int depth = 0;
                do {
                    if (p.at("<")) depth++;
                    else if (p.at(">")) depth--;
                    p.next();
                } while (depth > 0 && !p.at(T.EOF));
            }
            if (p.at("[")) {
                // array creation: `new int[n]` → `new Int[n]`.
                // Bug latente (achado 13/09): o código consumia `[` E o
                // primeiro token da dimensão antes do parseExpr, então
                // `new int[3]` saía `new Int[]]` (PARSE041 no Kof gerado).
                p.next(); // [
                if (p.at("]")) {
                    // `new int[]{...}` — array initializer sem equivalente
                    // direto em Kof (revisão manual, R6).
                    throw new TranslateException(
                            "array initializer `new T[]{...}` não tem equivalente direto em Kof "
                            + "(use `new Int[n]` + atribuições) — revisão manual");
                }
                String size = parseExpr();
                p.expect("]");
                return "new " + kofType(typeName) + "[" + size + "]";
            }
            String args = parseCallArgs();
            if (p.at("{")) {
                // Classe anônima Java (`new Runnable() { ... }`) — Kof não
                // tem classes anônimas (só lambdas p/ interface funcional).
                // Converter exige inferir a interface funcional — decisão de
                // design (regra 6) → revisão manual (R6).
                throw new TranslateException(
                        "classe anônima (`new X() { ... }`) não tem equivalente direto em Kof "
                        + "(use lambda p/ interface funcional) — revisão manual");
            }
            if (typeName.equals("RuntimeException") || typeName.equals("IllegalStateException")
                    || typeName.equals("IllegalArgumentException") || typeName.equals("Exception")) {
                // Exceções Java → String Kof (idiom errors.md: `throw "msg"`).
                // `new RuntimeException("boom " + k)` → `"boom " + k`.
                // Sem args → string vazia (throw exige String, SEM026).
                return args.isEmpty() ? "\"\"" : args;
            }
            return kofType(typeName) + "(" + args + ")";
        }

        // ── types ───────────────────────────────────────────────────────────

        String parseType() {
            String base = p.next().text;
            // Tipo qualificado `java.util.Map` → `Map` (stripa o pacote; o
            // translator ignora imports e Kof referencia tipos pelo nome
            // simples). `Map`/`List`/`Set` são builtins Kof.
            while (p.at(".") && p.peek(1).type == T.IDENT) {
                p.next();
                base = p.next().text;
            }
            StringBuilder sb = new StringBuilder(kofType(base));
            // generic args <...>
            if (p.at("<")) {
                p.next();
                String inner = parseType();
                while (p.at(",")) { p.next(); inner += ", " + parseType(); }
                p.expect(">");
                sb.append("<").append(inner).append(">");
            }
            while (p.at("[")) { p.next(); p.next(); sb.append("[]"); }
            return sb.toString();
        }

        List<String> parseParams() {
            p.expect("(");
            List<String> params = new ArrayList<>();
            if (!p.at(")")) {
                params.add(parseParam());
                while (p.at(",")) { p.next(); params.add(parseParam()); }
            }
            p.expect(")");
            return params;
        }

        private String parseParam() {
            String ty = parseType();
            if (p.at(".") && p.peek(1).text.equals(".") && p.peek(2).text.equals(".")) {
                // Java varargs `T...` não tem equivalente em função Kof
                // (só builtins setOf/listOf são variádicos). Revisão manual
                // (R6: nunca silencioso).
                throw new TranslateException(
                        "varargs (`T...`) não tem equivalente direto em Kof "
                        + "(use `List<T>` ou `T[]`) — revisão manual");
            }
            String nm = p.next().text;
            return ty + " " + nm;
        }

        String paramList(List<String> params) {
            return String.join(", ", params);
        }

        // ── static helpers ──────────────────────────────────────────────────

        static boolean isModifier(String s) {
            return switch (s) {
                case "public", "private", "protected", "static", "final",
                     "abstract", "synchronized", "native", "transient", "volatile",
                     "default" -> true;
                default -> false;
            };
        }

        static boolean isTypekeyword(String s) {
            return switch (s) {
                case "int", "long", "float", "double", "boolean", "char", "byte",
                     "short", "void", "String" -> true;
                default -> false;
            };
        }

        static boolean isPrimitiveOrType(String s) {
            return isTypekeyword(s) || (!isKeyword(s) && Character.isUpperCase(s.charAt(0)));
        }

        static boolean isKeyword(String s) {
            return TranslateLexer.KEYWORDS.contains(s);
        }

        static String kofType(String javaType) {
            return switch (javaType) {
                case "int", "Integer" -> "Int";
                case "long", "Long" -> "Long";
                case "float", "Float" -> "Float";
                case "double", "Double" -> "Double";
                case "boolean", "Boolean" -> "Bool";
                case "char", "Character" -> "Char";
                case "byte", "Byte" -> "Byte";
                case "short", "Short" -> "Short";
                case "void" -> "void";
                default -> javaType;
            };
        }
}
