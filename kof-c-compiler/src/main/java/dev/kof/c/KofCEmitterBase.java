package dev.kof.c;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Espinha do codegen: percorre a AST do subconjunto e delega cada instrução ao
 * alvo. As ISAs load-store (riscv64/aarch64) e o x86_64 compartilham o MESMO
 * fluxo (globais, if/while, binárias, deref/&, funções com parâmetros/retorno/
 * locais e chamadas), então o esqueleto vive aqui e cada {@link KofCEmitter}
 * implementa só os ganchos de instrução.
 *
 * <p>Modelo de valores: toda expressão deixa o resultado no acumulador do alvo
 * (rax/x0/a0) — que também é o registrador de retorno da ABI C. A pilha guarda
 * temporários de forma balanceada. Cada função tem um frame: um par
 * frame/retorno salvo e {@code frameSlots} slots de 8 bytes para parâmetros e
 * locais. Globais são sempre 8 bytes ({@code .comm}), como no x86.
 */
abstract class KofCEmitterBase implements KofCEmitter {
    protected final KofCAst.Program prog;
    protected final StringBuilder sb = new StringBuilder();
    private final AtomicInteger labelSeq = new AtomicInteger(0);

    /** Localização de uma variável: slot do frame ou global. */
    protected record Storage(boolean local, int slot, String name) {
        static Storage local(int slot) { return new Storage(true, slot, null); }
        static Storage global(String name) { return new Storage(false, -1, name); }
    }

    private Map<String, Integer> localSlots = Map.of();
    private Map<String, String> varTypes = Map.of();
    private final Map<String, KofCAst.StructDecl> structs = new LinkedHashMap<>();
    private String funcEndLabel = "";

    protected KofCEmitterBase(KofCAst.Program prog) {
        this.prog = prog;
        for (var s : prog.structs()) structs.put(s.name(), s);
    }

    @Override
    public String emit() {
        sb.setLength(0);
        emitDataSection(prog.globals());
        sb.append("    .text\n");
        emitStart();
        emitPrintHelpers();
        for (var fn : prog.funcs()) emitFunc(fn);
        return sb.toString();
    }

    private void emitFunc(KofCAst.FuncDecl fn) {
        localSlots = new LinkedHashMap<>();
        varTypes = new LinkedHashMap<>();
        for (var g : prog.globals()) varTypes.putIfAbsent(g.name(), g.type());
        int slots = 0;
        for (var p : fn.params()) { localSlots.put(p.name(), slots++); varTypes.put(p.name(), p.type()); }
        collectLocals(fn.body());
        sb.append(fn.name()).append(":\n");
        emitFuncPrologue(localSlots.size());
        for (int i = 0; i < fn.params().size(); i++) {
            emitStoreParam(i, localSlots.get(fn.params().get(i).name()));
        }
        funcEndLabel = label("ret");
        for (var st : fn.body()) emitStmt(st);
        sb.append(funcEndLabel).append(":\n");
        emitFuncEpilogue(localSlots.size());
    }

    /** Pré-varre o corpo para dimensionar o frame (declarações em blocos contam). */
    private void collectLocals(List<KofCAst.Stmt> body) {
        for (var st : body) {
            if (st instanceof KofCAst.LocalDeclStmt s) {
                localSlots.computeIfAbsent(s.name(), k -> localSlots.size());
                varTypes.put(s.name(), s.type());
            } else if (st instanceof KofCAst.IfStmt s) {
                collectLocals(s.thenBody());
            } else if (st instanceof KofCAst.WhileStmt s) {
                collectLocals(s.body());
            }
        }
    }

    /** Byte offset of {@code field} inside {@code type} ({@code struct X}); C int = 4 B. */
    protected int fieldOffset(String type, String field) {
        if (type == null || !type.startsWith("struct ")) return 0;
        KofCAst.StructDecl decl = structs.get(type.substring("struct ".length()));
        if (decl == null) return 0;
        for (int i = 0; i < decl.fields().size(); i++) {
            if (decl.fields().get(i).name().equals(field)) return 4 * i;
        }
        return 0;
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
        } else if (stmt instanceof KofCAst.ExprStmt s) {
            emitExpr(s.expr());
        } else if (stmt instanceof KofCAst.LocalDeclStmt) {
            // o slot já foi reservado na pré-varredura; nada a emitir
        } else if (stmt instanceof KofCAst.ReturnStmt s) {
            if (s.value() != null) emitExpr(s.value());
            emitJump(funcEndLabel);
        } else if (stmt instanceof KofCAst.AssignStmt s) {
            emitExpr(s.value());
            Storage target = resolve(s.target());
            if (s.deref()) emitDerefStoreStorage(target);
            else emitStoreStorage(target);
        } else if (stmt instanceof KofCAst.FieldAssignStmt s) {
            emitExpr(s.value());
            emitStoreField(resolve(s.target()), fieldOffset(varTypes.get(s.target()), s.field()));
        }
    }

    protected void emitExpr(KofCAst.Expr expr) {
        switch (expr) {
            case KofCAst.IntExpr e -> emitLoadImm(e.value());
            case KofCAst.IdentExpr e -> emitLoadStorage(resolve(e.name()));
            case KofCAst.UnaryAddr e -> emitAddrOfStorage(resolve(e.ident()));
            case KofCAst.UnaryDeref e -> emitLoadThroughStorage(resolve(e.ident()));
            case KofCAst.FieldExpr e -> emitLoadField(resolve(e.base()), fieldOffset(varTypes.get(e.base()), e.field()));
            case KofCAst.ParenExpr e -> emitExpr(e.inner());
            case KofCAst.CallExpr e -> emitCallExpr(e);
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

    private void emitCallExpr(KofCAst.CallExpr call) {
        if (call.name().equals("print") || call.name().equals("print_int") || call.name().equals("kof_print")) {
            emitCall("kof_print");
            return;
        }
        List<KofCAst.Expr> args = call.args();
        for (var a : args) {
            emitExpr(a);
            emitPushAcc();
        }
        for (int i = args.size() - 1; i >= 0; i--) emitPopArg(i);
        emitCall(call.name());
    }

    protected Storage resolve(String name) {
        Integer slot = localSlots.get(name);
        return slot != null ? Storage.local(slot) : Storage.global(name);
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

    protected abstract void emitFuncPrologue(int frameSlots);

    protected abstract void emitFuncEpilogue(int frameSlots);

    /** Salva o registrador do argumento {@code argIndex} no slot de parâmetro. */
    protected abstract void emitStoreParam(int argIndex, int slot);

    protected abstract void emitLoadImm(int v);

    protected abstract void emitLoadStorage(Storage storage);

    protected abstract void emitStoreStorage(Storage storage);

    /** Loads a 32-bit C {@code int} struct field at {@code byteOffset}, sign-extended. */
    protected abstract void emitLoadField(Storage storage, int byteOffset);

    /** Stores the accumulator as a 32-bit C {@code int} struct field at {@code byteOffset}. */
    protected abstract void emitStoreField(Storage storage, int byteOffset);

    protected abstract void emitAddrOfStorage(Storage storage);

    protected abstract void emitLoadThroughStorage(Storage storage);

    protected abstract void emitDerefStoreStorage(Storage storage);

    protected abstract void emitPushAcc();

    protected abstract void emitMoveAccToRight();

    protected abstract void emitPopLeftToAcc();

    /** Desempilha o topo para o registrador do argumento {@code argIndex}. */
    protected abstract void emitPopArg(int argIndex);

    protected abstract void emitBinaryOp(String op);

    protected abstract void emitBranchIfZero(String label);

    protected abstract void emitJump(String label);

    protected abstract void emitCall(String name);
}
