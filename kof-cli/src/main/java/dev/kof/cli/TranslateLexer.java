package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/** Lexer + tokens + parser-base do {@code kof translate} (extraído p/ gate <=500). */
    enum T {
        IDENT, INT, FLOAT, STR, CHAR, P, // { } ( ) [ ] ; , . 
        EQ, EQEQ, NE, LT, LE, GT, GE, PLUS, MINUS, STAR, SLASH, PERCENT,
        ANDAND, OROR, NOT, PLUSEQ, MINUSEQ, STAREQ, SLASHEQ, PERCENTEQ,
        INC, DEC, ARROW, PIPE, AMP, CARET, AT, EOF
    }

    final class Tok {
        final T type;
        final String text;
        Tok(T type, String text) { this.type = type; this.text = text; }
    }

    final class TranslateLexer {
    static final java.util.Set<String> KEYWORDS = java.util.Set.of(
            "public", "private", "protected", "static", "final", "abstract",
            "class", "interface", "extends", "implements", "return", "if",
            "else", "while", "for", "new", "package", "import", "null",
            "true", "false", "throw", "try", "catch", "finally", "void",
            "boolean", "byte", "short", "int", "long", "float", "double",
            "char", "String", "this", "super", "switch", "case", "break",
            "default", "do");

        static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '"') {
                // Text block Java `""" ... """` — Kof has no text block
                // (só string com `\n`) → gap honesto R6 (antes: o lexer lia
                // `""` vazio e reabria, gerando parse error confuso).
                if (i + 2 < n && s.charAt(i + 1) == '"' && s.charAt(i + 2) == '"') {
                    throw new TranslateException(
                            "text block (`\"\"\"`) has no direct equivalent in Kof "
                            + "(use a string with `\\n`) — manual review");
                }
                int j = i + 1;
                StringBuilder sb = new StringBuilder();
                while (j < n && s.charAt(j) != '"') {
                    if (s.charAt(j) == '\\' && j + 1 < n) {
                        Esc esc = decodeEscape(s, j);
                        sb.append(esc.ch());
                        j += esc.len();
                    } else {
                        sb.append(s.charAt(j)); j++;
                    }
                }
                out.add(new Tok(T.STR, sb.toString()));
                i = j + 1;
                continue;
            }
            if (c == '\'') {
                // Char literal: simples, com escape (barra-n etc.), unicode
                // (barra-u + 4 hex) e octal. Antes so o simples era lido.
                // Antes só `'x'` simples era reconhecido; `'\n'` caía no
                // `else` e o literal inteiro era descartado (bug latente Q4
                // 13/09 — o `'` de abertura sumia e o resto virava lixo).
                if (i + 1 < n && s.charAt(i + 1) == '\\' && i + 2 < n) {
                    Esc esc = decodeEscape(s, i + 1);
                    int end = i + 1 + esc.len();
                    if (end < n && s.charAt(end) == '\'') {
                        out.add(new Tok(T.CHAR, String.valueOf(esc.ch())));
                        i = end + 1;
                        continue;
                    }
                }
                if (i + 2 < n && s.charAt(i + 2) == '\'') {
                    out.add(new Tok(T.CHAR, String.valueOf(s.charAt(i + 1))));
                    i += 3;
                    continue;
                }
                i++;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = scanNumber(s, i);
                // Kof NÃO aceita separador `_` (PARSE043) — remove (mesmo valor).
                String text = s.substring(i, j).replace("_", "");
                boolean isFloat = text.indexOf('.') >= 0 || text.indexOf('e') >= 0
                        || text.indexOf('E') >= 0
                        || text.endsWith("f") || text.endsWith("F")
                        || text.endsWith("d") || text.endsWith("D");
                out.add(new Tok(isFloat ? T.FLOAT : T.INT, text));
                i = j;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                String w = s.substring(i, j);
                if (KEYWORDS.contains(w)) {
                    out.add(new Tok(T.IDENT, w)); // keyword kept as IDENT text
                } else {
                    out.add(new Tok(T.IDENT, w));
                }
                i = j;
                continue;
            }
            switch (c) {
                case '{' -> out.add(new Tok(T.P, "{"));
                case '}' -> out.add(new Tok(T.P, "}"));
                case '(' -> out.add(new Tok(T.P, "("));
                case ')' -> out.add(new Tok(T.P, ")"));
                case '[' -> out.add(new Tok(T.P, "["));
                case ']' -> out.add(new Tok(T.P, "]"));
                case ';' -> out.add(new Tok(T.P, ";"));
                case ',' -> out.add(new Tok(T.P, ","));
                case ':' -> out.add(new Tok(T.P, ":"));
                case '?' -> out.add(new Tok(T.P, "?"));
                case '.' -> {
                    // Literal iniciado por ponto (`.5`) — válido em Java, Kof
                    // exige o zero à esquerda (`.5` é PARSE041). Normaliza p/
                    // `0.5` (mesmo valor) em vez de erro de parse confuso.
                    if (i + 1 < n && Character.isDigit(s.charAt(i + 1))) {
                        int j = scanNumber(s, i + 1);
                        out.add(new Tok(T.FLOAT, ("0" + s.substring(i, j)).replace("_", "")));
                        i = j;
                        continue;
                    }
                    out.add(new Tok(T.P, "."));
                }
                case '=' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.EQEQ, "==")); i += 2; continue; } out.add(new Tok(T.EQ, "=")); }
                case '!' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.NE, "!=")); i += 2; continue; } out.add(new Tok(T.NOT, "!")); }
                case '<' -> { if (i + 2 < n && s.charAt(i + 1) == '<' && s.charAt(i + 2) == '=') { out.add(new Tok(T.LT, "<")); out.add(new Tok(T.LT, "<")); out.add(new Tok(T.EQ, "=")); i += 3; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.LE, "<=")); i += 2; continue; } out.add(new Tok(T.LT, "<")); }
                case '>' -> { if (i + 3 < n && s.charAt(i + 1) == '>' && s.charAt(i + 2) == '>' && s.charAt(i + 3) == '=') { out.add(new Tok(T.GT, ">")); out.add(new Tok(T.GT, ">")); out.add(new Tok(T.GT, ">")); out.add(new Tok(T.EQ, "=")); i += 4; continue; } if (i + 2 < n && s.charAt(i + 1) == '>' && s.charAt(i + 2) == '=') { out.add(new Tok(T.GT, ">")); out.add(new Tok(T.GT, ">")); out.add(new Tok(T.EQ, "=")); i += 3; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.GE, ">=")); i += 2; continue; } out.add(new Tok(T.GT, ">")); }
                case '+' -> { if (i + 1 < n && s.charAt(i + 1) == '+') { out.add(new Tok(T.INC, "++")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.PLUSEQ, "+=")); i += 2; continue; } out.add(new Tok(T.PLUS, "+")); }
                case '-' -> { if (i + 1 < n && s.charAt(i + 1) == '-') { out.add(new Tok(T.DEC, "--")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.MINUSEQ, "-=")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '>') { out.add(new Tok(T.ARROW, "->")); i += 2; continue; } out.add(new Tok(T.MINUS, "-")); }
                case '*' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.STAREQ, "*=")); i += 2; continue; } out.add(new Tok(T.STAR, "*")); }
                case '/' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.SLASHEQ, "/=")); i += 2; continue; } out.add(new Tok(T.SLASH, "/")); }
                case '%' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.PERCENTEQ, "%=")); i += 2; continue; } out.add(new Tok(T.PERCENT, "%")); }
                case '&' -> { if (i + 1 < n && s.charAt(i + 1) == '&') { out.add(new Tok(T.ANDAND, "&&")); i += 2; continue; } out.add(new Tok(T.AMP, "&")); }
                case '|' -> { if (i + 1 < n && s.charAt(i + 1) == '|') { out.add(new Tok(T.OROR, "||")); i += 2; continue; } out.add(new Tok(T.PIPE, "|")); }
                case '^' -> out.add(new Tok(T.CARET, "^"));
                case '@' -> out.add(new Tok(T.AT, "@"));
                case '~' -> out.add(new Tok(T.P, "~"));
                default -> throw new TranslateException(
                        "caractere inesperado `" + s.charAt(i) + "` is not supported by the translator "
                        + "(it used to be dropped in SILENCE, generating invalid Kof) — manual review");
            }
            i = Math.min(i + 1, n); // guarded advance for simple single-char cases
        }
        out.add(new Tok(T.EOF, ""));
        return out;
    }

    /**
     * Consome um literal numérico Java (mesma forma que o Kof aceita):
     * decimal/hex/bin/octal, `_` separador, ponto, expoente `e/E`, sufixos
     * `l/L/f/F/d/D` e hex-float `0x1.8p3`. Antes o scanner parava em
     * dígitos+ponto, então `10L`/`1.5e3`/`1.5f`/`0x1F`/`1_000` viravam
     * tokens separados (`L`, `e3`, `f`, …) = erro de parse (bug latente Q4).
     * O texto é preservado como veio — Kof aceita as mesmas formas.
     */
    static int scanNumber(String s, int start) {
        int n = s.length();
        int j = start;
        if (s.charAt(j) == '0' && j + 1 < n && (s.charAt(j + 1) == 'x' || s.charAt(j + 1) == 'X')) {
            j += 2;
            while (j < n && (isHex(s.charAt(j)) || s.charAt(j) == '_')) j++;
            if (j < n && s.charAt(j) == '.') {
                j++;
                while (j < n && (isHex(s.charAt(j)) || s.charAt(j) == '_')) j++;
            }
            if (j < n && (s.charAt(j) == 'p' || s.charAt(j) == 'P')) {
                j++;
                if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
            }
        } else if (s.charAt(j) == '0' && j + 1 < n && (s.charAt(j + 1) == 'b' || s.charAt(j + 1) == 'B')) {
            j += 2;
            while (j < n && (s.charAt(j) == '0' || s.charAt(j) == '1' || s.charAt(j) == '_')) j++;
        } else {
            while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
            if (j < n && s.charAt(j) == '.') {
                j++;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
            }
            if (j < n && (s.charAt(j) == 'e' || s.charAt(j) == 'E')) {
                j++;
                if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
            }
        }
        // sufixo de tipo (l/L/f/F/d/D); hex aceita l/L
        if (j < n && "lLfFdD".indexOf(s.charAt(j)) >= 0) j++;
        return j;
    }

    private static boolean isHex(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * Re-escapa o valor JÁ decodificado do literal Java para uma forma que o
     * Kof interpreta igual. O lexer decodifica `\n`/`\t`/`\"`/`\\` para chars
     * reais; emitir cru quebrava: `\"` virava `"` (fecha a string = PARSE043)
     * e `\\` virava `\` (Kof engole a barra: `"path\x"` → `pathx` ≠ Java
     * `path\x`) — bug latente Q4 13/09. Escapa só o essencial; os demais
     * chars vão crus (Kof aceita raw).
     */
    /** Escape Java decodificado: o char real + quantos chars de fonte consumiu. */
    private record Esc(char ch, int len) {}

    /**
     * Decodifica um escape Java a partir de {@code s.charAt(j) == '\\'}:
     * barra-n, barra-t, barra-r, barra-b, barra-f, aspas, apostrofo, barra,
     * unicode (barra-u + 4 hex) e octal. Os escapes sem forma direta em Kof
     * viram o char de controle e o {@link #escapeKofString} os reemite como
     * unicode (Kof suporta) — antes eram dropados/errados silenciosamente.
     */
    private static Esc decodeEscape(String s, int j) {
        char e = s.charAt(j + 1);
        switch (e) {
            case 'n': return new Esc('\n', 2);
            case 't': return new Esc('\t', 2);
            case 'r': return new Esc('\r', 2);
            case 'b': return new Esc('\b', 2);
            case 'f': return new Esc('\f', 2);
            case '"': return new Esc('"', 2);
            case '\'': return new Esc('\'', 2);
            case '\\': return new Esc('\\', 2);
            case 'u': {
                int hex = 0, k = 0;
                while (k < 4 && j + 2 + k < s.length()) {
                    int d = Character.digit(s.charAt(j + 2 + k), 16);
                    if (d < 0) break;
                    hex = hex * 16 + d; k++;
                }
                return new Esc((char) hex, 2 + k);
            }
            default:
                if (e >= '0' && e <= '7') {
                    int oct = 0, k = 0;
                    while (k < 3 && j + 1 + k < s.length()) {
                        char oc = s.charAt(j + 1 + k);
                        if (oc < '0' || oc > '7') break;
                        oct = oct * 8 + (oc - '0'); k++;
                    }
                    return new Esc((char) oct, 1 + k);
                }
                return new Esc(e, 2);
        }
    }

    static String escapeKofString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> {
                    // Controles sem escape Kof dedicado (`\b`, `\f`, outros)
                    // → unicode (Kof suporta); antes iam CRUS.
                    if (c < 0x20 || c == 0x7F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    static String escapeKofChar(String s) {
        if (s.length() != 1) return s;
        char c = s.charAt(0);
        return switch (c) {
            case '\\' -> "\\\\";
            case '\'' -> "\\'";
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\r' -> "\\r";
            default -> (c < 0x20 || c == 0x7F)
                    ? String.format("\\u%04x", (int) c)
                    : s;
        };
    }

    // ── Parser + Emitter range helpers ────────────────────────────────────

    }
class TranslateException extends RuntimeException {
        TranslateException(String m) { super(m); }
    }

    class Parser {
        final List<Tok> toks;
        int pos;
        Parser(List<Tok> toks) { this.toks = toks; }
        Tok peek() { return toks.get(pos); }
        Tok peek(int ahead) { int i = Math.min(pos + ahead, toks.size() - 1); return toks.get(i); }
        Tok next() {
            Tok t = toks.get(pos);
            if (pos < toks.size() - 1) pos++;
            return t;
        }
        boolean at(String text) { return peek().text.equals(text); }
        boolean at(T t) { return peek().type == t; }
        Tok expect(String text) {
            if (!at(text)) throw new TranslateException("expected '" + text + "' but found '" + peek().text + "'");
            return next();
        }
        Tok expectPunct() { return next(); }
    }
