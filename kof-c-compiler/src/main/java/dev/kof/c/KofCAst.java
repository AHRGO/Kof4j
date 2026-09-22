package dev.kof.c;

import java.util.List;

public final class KofCAst {
    public record Program(List<VarDecl> globals, List<FuncDecl> funcs) {}

    /** Global variable — 8 bytes in {@code .comm}. */
    public record VarDecl(String name) {}

    /** Function parameter; the subset is {@code int}-only for now. */
    public record Param(String type, String name) {}

    public record FuncDecl(String name, String retType, List<Param> params, List<Stmt> body) {}

    public sealed interface Stmt permits IfStmt, WhileStmt, AsmStmt, ExprStmt, AssignStmt, LocalDeclStmt, ReturnStmt {}

    public record IfStmt(Expr cond, List<Stmt> thenBody) implements Stmt {}
    public record WhileStmt(Expr cond, List<Stmt> body) implements Stmt {}
    public record AsmStmt(int value) implements Stmt {}
    /** Expression statement — a call in practice ({@code f(a, b);}). */
    public record ExprStmt(Expr expr) implements Stmt {}
    public record AssignStmt(boolean deref, String target, Expr value) implements Stmt {}
    /** Local variable declaration ({@code int x;}) — lives on the stack frame. */
    public record LocalDeclStmt(String type, String name) implements Stmt {}
    /** {@code return expr;} — {@code value} is null for a bare {@code return;}. */
    public record ReturnStmt(Expr value) implements Stmt {}

    public sealed interface Expr permits BinaryExpr, UnaryDeref, UnaryAddr, ParenExpr, IdentExpr, IntExpr, CallExpr {}

    public record BinaryExpr(Expr left, String op, Expr right) implements Expr {}
    // Unary cases:
    //  - deref: *(int*)ident
    //  - addr: &ident
    //  - ident / int / paren / call are separate Expr types
    public record UnaryDeref(String ident) implements Expr {}
    public record UnaryAddr(String ident) implements Expr {}
    public record ParenExpr(Expr inner) implements Expr {}
    public record IdentExpr(String name) implements Expr {}
    public record IntExpr(int value) implements Expr {}
    public record CallExpr(String name, List<Expr> args) implements Expr {}
}
