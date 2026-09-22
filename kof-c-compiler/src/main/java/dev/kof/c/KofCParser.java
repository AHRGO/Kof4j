package dev.kof.c;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KofCParser {
    /** Register-based argument budget shared by the three ISAs (x86_64 SysV has 6). */
    static final int MAX_ARGS = 6;
    private static final List<String> PRINT_BUILTINS = List.of("print", "print_int", "kof_print");

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
                if (checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.LPAREN)) {
                    funcs.add(parseFunc("int"));
                } else if (checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.SEMI)) {
                    advance(); // int
                    String name = advance().text();
                    expect(KofCTokenType.SEMI);
                    globals.add(new KofCAst.VarDecl(name));
                } else {
                    error("Expected global variable or function declaration");
                    advance();
                }
            } else if (check(KofCTokenType.VOID)) {
                funcs.add(parseFunc("void"));
            } else {
                error("Unexpected token " + peek().text());
                advance();
            }
        }
        KofCAst.Program program = new KofCAst.Program(globals, funcs);
        validateCalls(program);
        return program;
    }

    private KofCAst.FuncDecl parseFunc(String retType) {
        advance(); // type
        String name = expect(KofCTokenType.IDENTIFIER).text();
        expect(KofCTokenType.LPAREN);
        List<KofCAst.Param> params = parseParams();
        expect(KofCTokenType.RPAREN);
        List<KofCAst.Stmt> body = parseBlock();
        return new KofCAst.FuncDecl(name, retType, params, body);
    }

    private List<KofCAst.Param> parseParams() {
        List<KofCAst.Param> params = new ArrayList<>();
        if (check(KofCTokenType.RPAREN)) return params;
        if (check(KofCTokenType.VOID) && checkAt(1, KofCTokenType.RPAREN)) { advance(); return params; }
        while (true) {
            if (!check(KofCTokenType.INT)) { error("Expected parameter type int"); break; }
            advance(); // int
            String pname = expect(KofCTokenType.IDENTIFIER).text();
            params.add(new KofCAst.Param("int", pname));
            if (check(KofCTokenType.COMMA)) { advance(); continue; }
            break;
        }
        if (params.size() > MAX_ARGS) {
            error("Too many parameters (" + params.size() + "); the subset supports at most " + MAX_ARGS);
        }
        return params;
    }

    private List<KofCAst.Stmt> parseBlock() {
        expect(KofCTokenType.LBRACE);
        List<KofCAst.Stmt> body = new ArrayList<>();
        while (!check(KofCTokenType.RBRACE) && !check(KofCTokenType.EOF)) {
            body.add(parseStmt());
        }
        expect(KofCTokenType.RBRACE);
        return body;
    }

    private KofCAst.Stmt parseStmt() {
        if (check(KofCTokenType.IF)) return parseIf();
        if (check(KofCTokenType.WHILE)) return parseWhile();
        if (check(KofCTokenType.ASM)) {
            advance();
            int v = parseIntLiteral();
            expect(KofCTokenType.SEMI);
            return new KofCAst.AsmStmt(v);
        }
        if (check(KofCTokenType.RETURN)) {
            advance();
            if (check(KofCTokenType.SEMI)) { advance(); return new KofCAst.ReturnStmt(null); }
            KofCAst.Expr value = parseExpr();
            expect(KofCTokenType.SEMI);
            return new KofCAst.ReturnStmt(value);
        }
        if (check(KofCTokenType.INT) && checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.SEMI)) {
            advance(); // int
            String name = advance().text();
            expect(KofCTokenType.SEMI);
            return new KofCAst.LocalDeclStmt("int", name);
        }
        // assignment (optionally through a pointer): [*(int*)]? ident = expr ;
        int save = pos;
        boolean deref = false;
        if (isDerefAhead()) { deref = true; consumeDeref(); }
        if (check(KofCTokenType.IDENTIFIER) && checkAt(1, KofCTokenType.EQUAL)) {
            String target = advance().text();
            expect(KofCTokenType.EQUAL);
            KofCAst.Expr expr = parseExpr();
            expect(KofCTokenType.SEMI);
            return new KofCAst.AssignStmt(deref, target, expr);
        }
        if (deref) pos = save; // backtrack: not an assignment
        // expression statement (a call in practice)
        KofCAst.Expr expr = parseExpr();
        expect(KofCTokenType.SEMI);
        return new KofCAst.ExprStmt(expr);
    }

    private KofCAst.IfStmt parseIf() {
        expect(KofCTokenType.IF);
        expect(KofCTokenType.LPAREN);
        KofCAst.Expr cond = parseExpr();
        expect(KofCTokenType.RPAREN);
        List<KofCAst.Stmt> body = parseBlock();
        return new KofCAst.IfStmt(cond, body);
    }

    private KofCAst.WhileStmt parseWhile() {
        expect(KofCTokenType.WHILE);
        expect(KofCTokenType.LPAREN);
        KofCAst.Expr cond = parseExpr();
        expect(KofCTokenType.RPAREN);
        List<KofCAst.Stmt> body = parseBlock();
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
            String name = advance().text();
            if (check(KofCTokenType.LPAREN)) return parseCall(name);
            return new KofCAst.IdentExpr(name);
        }
        if (check(KofCTokenType.INTEGER)) {
            int v = parseIntLiteral();
            return new KofCAst.IntExpr(v);
        }
        error("Unexpected unary " + peek().text());
        advance();
        return new KofCAst.IntExpr(0);
    }

    private KofCAst.CallExpr parseCall(String name) {
        expect(KofCTokenType.LPAREN);
        List<KofCAst.Expr> args = new ArrayList<>();
        if (!check(KofCTokenType.RPAREN)) {
            while (true) {
                args.add(parseExpr());
                if (check(KofCTokenType.COMMA)) { advance(); continue; }
                break;
            }
        }
        expect(KofCTokenType.RPAREN);
        if (args.size() > MAX_ARGS) {
            error("Too many call arguments (" + args.size() + "); the subset supports at most " + MAX_ARGS);
        }
        return new KofCAst.CallExpr(name, args);
    }

    /** Honest diagnostics (R6): unknown call or arity mismatch never becomes a silent wrong binary. */
    private void validateCalls(KofCAst.Program program) {
        Map<String, Integer> arity = new LinkedHashMap<>();
        for (var fn : program.funcs()) arity.put(fn.name(), fn.params().size());
        for (var fn : program.funcs()) {
            for (var st : fn.body()) validateStmt(st, arity);
        }
    }

    private void validateStmt(KofCAst.Stmt stmt, Map<String, Integer> arity) {
        switch (stmt) {
            case KofCAst.IfStmt s -> {
                validateExpr(s.cond(), arity);
                for (var st : s.thenBody()) validateStmt(st, arity);
            }
            case KofCAst.WhileStmt s -> {
                validateExpr(s.cond(), arity);
                for (var st : s.body()) validateStmt(st, arity);
            }
            case KofCAst.ExprStmt s -> validateExpr(s.expr(), arity);
            case KofCAst.AssignStmt s -> validateExpr(s.value(), arity);
            case KofCAst.ReturnStmt s -> { if (s.value() != null) validateExpr(s.value(), arity); }
            case KofCAst.AsmStmt ignored -> { }
            case KofCAst.LocalDeclStmt ignored -> { }
        }
    }

    private void validateExpr(KofCAst.Expr expr, Map<String, Integer> arity) {
        switch (expr) {
            case KofCAst.BinaryExpr e -> { validateExpr(e.left(), arity); validateExpr(e.right(), arity); }
            case KofCAst.ParenExpr e -> validateExpr(e.inner(), arity);
            case KofCAst.CallExpr e -> {
                for (var a : e.args()) validateExpr(a, arity);
                if (PRINT_BUILTINS.contains(e.name())) {
                    if (!e.args().isEmpty()) error("print() takes no arguments in the subset");
                } else if (arity.containsKey(e.name())) {
                    int want = arity.get(e.name());
                    if (want != e.args().size()) {
                        error("function " + e.name() + " expects " + want + " argument(s), got " + e.args().size());
                    }
                } else {
                    error("call to unknown function " + e.name());
                }
            }
            case null, default -> { }
        }
    }

    private boolean isDerefAhead() {
        // pattern: * ( int * )  -> STAR LPAREN INT STAR RPAREN
        if (pos + 4 >= toks.size()) return false;
        if (toks.get(pos).type() != KofCTokenType.STAR) return false;
        if (toks.get(pos + 1).type() != KofCTokenType.LPAREN) return false;
        if (toks.get(pos + 2).type() != KofCTokenType.INT) return false;
        return toks.get(pos + 3).type() == KofCTokenType.STAR
                && toks.get(pos + 4).type() == KofCTokenType.RPAREN;
    }

    private void consumeDeref() {
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
    private boolean checkAt(int off, KofCTokenType t) {
        return pos + off < toks.size() && toks.get(pos + off).type() == t;
    }
    private KofCToken advance() { return toks.get(pos++); }
    private KofCToken expect(KofCTokenType t) {
        if (check(t)) return advance();
        error("Expected " + t + " got " + peek().type() + " (" + peek().text() + ")");
        return new KofCToken(t, "", 0, 0);
    }
    private void error(String msg) {
        KofCToken t = (pos < toks.size()) ? toks.get(pos) : toks.get(toks.size() - 1);
        errors.add("line " + t.line() + ", col " + t.col() + ": " + msg);
    }
}
