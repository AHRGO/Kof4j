package dev.kof.c;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Espinha do codegen: percorre a AST do subconjunto e delega cada instrução ao
 * alvo. As ISAs load-store (riscv64/aarch64) e o x86_64 compartilham o MESMO
 * fluxo (globais, if/while, binárias, deref/&), então o esqueleto vive aqui e
 * cada {@link KofCEmitter} implementa só os ganchos de instrução.
 *
 * <p>Convenção do modelo de valores: toda expressão deixa o resultado no
 * acumulador do alvo (rax/x0/a0); a pilha guarda temporários de forma
 * balanceada. Globais são sempre 8 bytes ({@code .comm}), como no x86.
 */
abstract class KofCEmitterBase implements KofCEmitter {
    protected final KofCAst.Program prog;
    protected final StringBuilder sb = new StringBuilder();
    private final AtomicInteger labelSeq = new AtomicInteger(0);

    protected KofCEmitterBase(KofCAst.Program prog) { this.prog = prog; }

    @Override
    public String emit() {
        sb.setLength(0);
        emitDataSection(prog.globals());
        sb.append("    .text\n");
        emitStart();
        emitPrintHelpers();
        for (var fn : prog.funcs()) {
            sb.append(fn.name()).append(":\n");
            emitFuncPrologue();
            for (var st : fn.body()) emitStmt(st);
            emitFuncEpilogue();
        }
        return sb.toString();
    }

    protected void emitStmt(KofCAst.Stmt stmt) {
        if (stmt instanceof KofCAst.IfStmt s) {
            String end = label("if_end");
            emitExpr(s.cond());
            emitBranchIfZero(end);
            for (var st : s.thenBody()) emitStmt(st);
            sb.append(end).append(":\n");
        } else if (stmt instanceof KofCAst.WhileStmt s) {
            String start = label("while_start");
            String end = label("while_end");
            sb.append(start).append(":\n");
            emitExpr(s.cond());
            emitBranchIfZero(end);
            for (var st : s.body()) emitStmt(st);
            emitJump(start);
            sb.append(end).append(":\n");
        } else if (stmt instanceof KofCAst.AsmStmt s) {
            sb.append("    .byte ").append(s.value()).append("\n");
        } else if (stmt instanceof KofCAst.CallStmt s) {
            if (s.name().equals("print") || s.name().equals("print_int") || s.name().equals("kof_print")) {
                emitCall("kof_print");
            } else {
                emitCall(s.name());
            }
        } else if (stmt instanceof KofCAst.AssignStmt s) {
            emitExpr(s.value());
            if (s.deref()) emitDerefStore(s.target());
            else emitStoreGlobal(s.target());
        }
    }

    protected void emitExpr(KofCAst.Expr expr) {
        switch (expr) {
            case KofCAst.IntExpr e -> emitLoadImm(e.value());
            case KofCAst.IdentExpr e -> emitLoadGlobal(e.name());
            case KofCAst.UnaryAddr e -> emitAddrOfGlobal(e.ident());
            case KofCAst.UnaryDeref e -> emitLoadThrough(e.ident());
            case KofCAst.ParenExpr e -> emitExpr(e.inner());
            case KofCAst.BinaryExpr e -> {
                // esquerda -> acc -> pilha; direita -> acc -> reg da direita;
                // pilha -> acc (esquerda); op acc, direita
                emitExpr(e.left());
                emitPushAcc();
                emitExpr(e.right());
                emitMoveAccToRight();
                emitPopLeftToAcc();
                emitBinaryOp(e.op());
            }
            case null, default -> { }
        }
    }

    protected String label(String base) {
        return ".L" + base + "_" + labelSeq.getAndIncrement();
    }

    protected boolean hasGlobal(String name) {
        return prog.globals().stream().anyMatch(g -> g.name().equals(name));
    }

    // ---- ganchos de alvo -------------------------------------------------

    protected abstract void emitDataSection(List<KofCAst.VarDecl> globals);

    protected abstract void emitStart();

    protected abstract void emitPrintHelpers();

    protected abstract void emitFuncPrologue();

    protected abstract void emitFuncEpilogue();

    protected abstract void emitLoadImm(int v);

    protected abstract void emitLoadGlobal(String name);

    protected abstract void emitStoreGlobal(String name);

    protected abstract void emitAddrOfGlobal(String name);

    protected abstract void emitLoadThrough(String name);

    protected abstract void emitDerefStore(String target);

    protected abstract void emitPushAcc();

    protected abstract void emitMoveAccToRight();

    protected abstract void emitPopLeftToAcc();

    protected abstract void emitBinaryOp(String op);

    protected abstract void emitBranchIfZero(String label);

    protected abstract void emitJump(String label);

    protected abstract void emitCall(String name);
}
