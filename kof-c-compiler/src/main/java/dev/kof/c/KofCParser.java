package dev.kof.c;

import java.util.ArrayList;
import java.util.List;

public final class KofCParser {
    private final List<KofCToken> toks;
    private int pos = 0;
    private final List<String> errors = new ArrayList<>();

    public KofCParser(List<KofCToken> toks) { this.toks = toks; }

    public boolean hasErrors() { return !errors.isEmpty(); }
    public List<String> errors() { return List.copyOf(errors); }

    public KofCAst.Program parseProgram() {
        List<KofCAst.VarDecl> globals = new ArrayList<>();
        List<KofCAst.FuncDecl> funcs = new ArrayList<>();
        while (!check(KofCTokenType.EOF)) {
            if (check(KofCTokenType.INT)) {
                // var_decl: int ident ;
                // lookahead: int ident ;
                if (pos + 2 < toks.size() && toks.get(pos+1).type() == KofCTokenType.IDENTIFIER && toks.get(pos+2).type() == KofCTokenType.SEMI) {
                    advance(); // int
                    String name = advance().text();
                    expect(KofCTokenType.SEMI);
                    globals.add(new KofCAst.VarDecl(name));
                } else {
                    error("Expected var decl");
                    advance();
                }
            } else if (check(KofCTokenType.VOID)) {
                funcs.add(parseFunc());
            } else {
                error("Unexpected token " + peek().text());
                advance();
            }
        }
        return new KofCAst.Program(globals, funcs);
    }

    private KofCAst.FuncDecl parseFunc() {
        expect(KofCTokenType.VOID);
        String name = expect(KofCTokenType.IDENTIFIER).text();
        expect(KofCTokenType.LPAREN);
        expect(KofCTokenType.RPAREN);
        expect(KofCTokenType.LBRACE);
        List<KofCAst.Stmt> body = new ArrayList<>();
        while (!check(KofCTokenType.RBRACE) && !check(KofCTokenType.EOF)) {
            body.add(parseStmt());
        }
        expect(KofCTokenType.RBRACE);
        return new KofCAst.FuncDecl(name, body);
    }

    private KofCAst.Stmt parseStmt() {
        if (check(KofCTokenType.IF)) {
            return parseIf();
        }
        if (check(KofCTokenType.WHILE)) {
            return parseWhile();
        }
        if (check(KofCTokenType.ASM)) {
            advance();
            int v = parseIntLiteral();
            expect(KofCTokenType.SEMI);
            return new KofCAst.AsmStmt(v);
        }
        // lookahead for assign: deref? ident = expr ;
        // deref is *(int*)  -> tokens: STAR LPAREN INT RPAREN STAR ? Actually *(int*) is STAR LPAREN INT STAR RPAREN ? Wait we lex * as STAR, ( as LPAREN, int as INT, ) as RPAREN, * as STAR
        // Sequence for *(int*): STAR LPAREN INT STAR RPAREN
        // But our lexer tokenizes * separately. So check for that pattern.
        int save = pos;
        boolean deref = false;
        if (isDerefAhead()) {
            deref = true;
            consumeDeref();
        }
        if (check(KofCTokenType.IDENTIFIER) && pos + 1 < toks.size() && toks.get(pos+1).type() == KofCTokenType.EQUAL) {
            String target = advance().text();
            expect(KofCTokenType.EQUAL);
            KofCAst.Expr expr = parseExpr();
            expect(KofCTokenType.SEMI);
            return new KofCAst.AssignStmt(deref, target, expr);
        }
        // reset and try call: ident () ;
        if (deref) pos = save; // backtrack if not assign
        if (check(KofCTokenType.IDENTIFIER) && pos + 2 < toks.size()
                && toks.get(pos+1).type() == KofCTokenType.LPAREN
                && toks.get(pos+2).type() == KofCTokenType.RPAREN) {
            String name = advance().text();
            advance(); // (
            advance(); // )
            expect(KofCTokenType.SEMI);
            return new KofCAst.CallStmt(name);
        }
        error("Unexpected statement at " + peek().text());
        // skip to ;
        while (!check(KofCTokenType.SEMI) && !check(KofCTokenType.RBRACE) && !check(KofCTokenType.EOF)) advance();
        if (check(KofCTokenType.SEMI)) advance();
        return new KofCAst.CallStmt("error");
    }

    private KofCAst.IfStmt parseIf() {
        expect(KofCTokenType.IF);
        expect(KofCTokenType.LPAREN);
        KofCAst.Expr cond = parseExpr();
        expect(KofCTokenType.RPAREN);
        expect(KofCTokenType.LBRACE);
        List<KofCAst.Stmt> body = new ArrayList<>();
        while (!check(KofCTokenType.RBRACE) && !check(KofCTokenType.EOF)) body.add(parseStmt());
        expect(KofCTokenType.RBRACE);
        return new KofCAst.IfStmt(cond, body);
    }

    private KofCAst.WhileStmt parseWhile() {
        expect(KofCTokenType.WHILE);
        expect(KofCTokenType.LPAREN);
        KofCAst.Expr cond = parseExpr();
        expect(KofCTokenType.RPAREN);
        expect(KofCTokenType.LBRACE);
        List<KofCAst.Stmt> body = new ArrayList<>();
        while (!check(KofCTokenType.RBRACE) && !check(KofCTokenType.EOF)) body.add(parseStmt());
        expect(KofCTokenType.RBRACE);
        return new KofCAst.WhileStmt(cond, body);
    }

    // expr = unary (op unary)?
    private KofCAst.Expr parseExpr() {
        KofCAst.Expr left = parseUnary();
        String op = parseOp();
        if (op != null) {
            KofCAst.Expr right = parseUnary();
            left = new KofCAst.BinaryExpr(left, op, right);
        }
        return left;
    }

    private String parseOp() {
        KofCToken t = peek();
        return switch (t.type()) {
            case PLUS -> { advance(); yield "+"; }
            case MINUS -> { advance(); yield "-"; }
            case AMP -> { advance(); yield "&"; }
            case PIPE -> { advance(); yield "|"; }
            case CARET -> { advance(); yield "^"; }
            case LESS_LESS -> { advance(); yield "<<"; }
            case GREATER_GREATER -> { advance(); yield ">>"; }
            case EQUAL_EQUAL -> { advance(); yield "=="; }
            case BANG_EQUAL -> { advance(); yield "!="; }
            case LESS -> { advance(); yield "<"; }
            case GREATER -> { advance(); yield ">"; }
            case LESS_EQUAL -> { advance(); yield "<="; }
            case GREATER_EQUAL -> { advance(); yield ">="; }
            default -> null;
        };
    }

    private KofCAst.Expr parseUnary() {
        if (isDerefAhead()) {
            consumeDeref();
            String ident = expect(KofCTokenType.IDENTIFIER).text();
            return new KofCAst.UnaryDeref(ident);
        }
        if (check(KofCTokenType.AMP)) {
            advance();
            String ident = expect(KofCTokenType.IDENTIFIER).text();
            return new KofCAst.UnaryAddr(ident);
        }
        if (check(KofCTokenType.LPAREN)) {
            advance();
            KofCAst.Expr inner = parseExpr();
            expect(KofCTokenType.RPAREN);
            return new KofCAst.ParenExpr(inner);
        }
        if (check(KofCTokenType.IDENTIFIER)) {
            return new KofCAst.IdentExpr(advance().text());
        }
        if (check(KofCTokenType.INTEGER)) {
            int v = parseIntLiteral();
            return new KofCAst.IntExpr(v);
        }
        error("Unexpected unary " + peek().text());
        advance();
        return new KofCAst.IntExpr(0);
    }

    private boolean isDerefAhead() {
        // pattern: * ( int * )  -> STAR LPAREN INT STAR RPAREN
        // sometimes *(int*) with no space for second *: still same tokens
        // Check: STAR LPAREN INT
        if (pos + 3 >= toks.size()) return false;
        if (toks.get(pos).type() != KofCTokenType.STAR) return false;
        if (toks.get(pos+1).type() != KofCTokenType.LPAREN) return false;
        if (toks.get(pos+2).type() != KofCTokenType.INT) return false;
        // need STAR before RPAREN: could be INT STAR RPAREN or INT RPAREN STAR ?
        // canonical *(int*) : tokens: STAR LPAREN INT STAR RPAREN
        // Some code may have *(int *) with space: same.
        // We'll check for STAR at pos+3 or pos+4
        if (toks.get(pos+3).type() == KofCTokenType.STAR && pos+4 < toks.size() && toks.get(pos+4).type() == KofCTokenType.RPAREN) return true;
        if (toks.get(pos+3).type() == KofCTokenType.RPAREN) {
            // maybe *(int) without second star? but grammar says *(int*) so require star, treat as not deref if missing
            return false;
        }
        return false;
    }

    private void consumeDeref() {
        // consume STAR LPAREN INT STAR RPAREN
        expect(KofCTokenType.STAR);
        expect(KofCTokenType.LPAREN);
        expect(KofCTokenType.INT);
        expect(KofCTokenType.STAR);
        expect(KofCTokenType.RPAREN);
    }

    private int parseIntLiteral() {
        String txt = expect(KofCTokenType.INTEGER).text();
        try {
            if (txt.startsWith("0x") || txt.startsWith("0X")) return (int) Long.parseLong(txt.substring(2), 16);
            return Integer.parseInt(txt);
        } catch (NumberFormatException e) { return 0; }
    }

    // helpers
    private KofCToken peek() { return toks.get(pos); }
    private boolean check(KofCTokenType t) { return peek().type() == t; }
    private KofCToken advance() { return toks.get(pos++); }
    private KofCToken expect(KofCTokenType t) {
        if (check(t)) return advance();
        error("Expected " + t + " got " + peek().type() + " (" + peek().text() + ")");
        return new KofCToken(t, "", 0, 0);
    }
    private void expect(KofCTokenType t, String msg) { if (!check(t)) error(msg); else advance(); }
    private void error(String msg) {
        KofCToken t = (pos < toks.size()) ? toks.get(pos) : toks.get(toks.size() - 1);
        errors.add("line " + t.line() + ", col " + t.col() + ": " + msg);
    }
}
