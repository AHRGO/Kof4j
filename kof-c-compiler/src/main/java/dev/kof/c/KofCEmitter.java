package dev.kof.c;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Native-only emitter: C subset -> x86_64 GAS assembly (AT&T) -> ELF64.
 * Globals are 8-byte (.comm) to hold 64-bit values/pointers.
 * All values are 64-bit (movq). Conditions are 0 == false.
 */
public final class KofCEmitter {
    private final KofCAst.Program prog;
    private final StringBuilder sb = new StringBuilder();
    private final AtomicInteger labelSeq = new AtomicInteger(0);

    public KofCEmitter(KofCAst.Program prog) { this.prog = prog; }

    public String emit() {
        sb.append("    .intel_syntax noprefix\n"); // use Intel for readability but gas supports both
        // Alternative: keep AT&T; we use Intel + .intel_syntax
        // Globals
        if (!prog.globals().isEmpty()) {
            sb.append("    .bss\n");
            for (var g : prog.globals()) {
                sb.append("    .globl ").append(g.name()).append("\n");
                sb.append("    .comm ").append(g.name()).append(",8,8\n");
            }
        }
        sb.append("    .text\n");
        sb.append("    .globl _start\n");
        sb.append("_start:\n");
        sb.append("    call main\n");
        sb.append("    mov rax, 60\n"); // exit
        sb.append("    xor rdi, rdi\n");
        sb.append("    syscall\n");

        // helpers for print (optional builtin)
        emitPrintHelper();

        for (var fn : prog.funcs()) {
            emitFunc(fn);
        }
        // undefined calls? Provide stub for print_int etc.
        return sb.toString();
    }

    private void emitPrintHelper() {
        sb.append("kof_print_int:\n");
        sb.append("    push rbp\n");
        sb.append("    mov rbp, rsp\n");
        sb.append("    sub rsp, 32\n");
        sb.append("    mov rax, rdi\n");
        sb.append("    lea rsi, [rbp-32]\n");
        sb.append("    add rsi, 31\n");
        sb.append("    mov byte ptr [rsi], 10\n");
        sb.append("    mov rcx, 1\n");
        sb.append("    cmp rax, 0\n");
        sb.append("    jne .Lprint_loop\n");
        sb.append("    dec rsi\n");
        sb.append("    mov byte ptr [rsi], 48\n");
        sb.append("    inc rcx\n");
        sb.append("    jmp .Lprint_write\n");
        sb.append(".Lprint_loop:\n");
        sb.append("    test rax, rax\n");
        sb.append("    je .Lprint_write\n");
        sb.append("    xor rdx, rdx\n");
        sb.append("    mov rbx, 10\n");
        sb.append("    div rbx\n");
        sb.append("    add dl, 48\n");
        sb.append("    dec rsi\n");
        sb.append("    mov byte ptr [rsi], dl\n");
        sb.append("    inc rcx\n");
        sb.append("    jmp .Lprint_loop\n");
        sb.append(".Lprint_write:\n");
        sb.append("    mov rdx, rcx\n");
        sb.append("    mov rax, 1\n");
        sb.append("    mov rdi, 1\n");
        sb.append("    syscall\n");
        sb.append("    leave\n");
        sb.append("    ret\n");
        sb.append("kof_print:\n");
        sb.append("    mov rdi, qword ptr [rip + print_arg]\n");
        sb.append("    jmp kof_print_int\n");
        boolean hasPrintArg = prog.globals().stream().anyMatch(g -> g.name().equals("print_arg"));
        if (!hasPrintArg) {
            sb.append("    .bss\n");
            sb.append("    .globl print_arg\n");
            sb.append("    .comm print_arg,8,8\n");
            sb.append("    .text\n");
        }
    }

    private void emitFunc(KofCAst.FuncDecl fn) {
        sb.append(fn.name()).append(":\n");
        sb.append("    push rbp\n");
        sb.append("    mov rbp, rsp\n");
        for (var stmt : fn.body()) emitStmt(stmt);
        sb.append("    pop rbp\n");
        sb.append("    ret\n");
    }

    private void emitStmt(KofCAst.Stmt stmt) {
        if (stmt instanceof KofCAst.IfStmt s) {
            String end = freshLabel("if_end");
            emitExpr(s.cond());
            sb.append("    cmp rax, 0\n");
            sb.append("    je ").append(end).append("\n");
            for (var st : s.thenBody()) emitStmt(st);
            sb.append(end).append(":\n");
        } else if (stmt instanceof KofCAst.WhileStmt s) {
            String start = freshLabel("while_start");
            String end = freshLabel("while_end");
            sb.append(start).append(":\n");
            emitExpr(s.cond());
            sb.append("    cmp rax, 0\n");
            sb.append("    je ").append(end).append("\n");
            for (var st : s.body()) emitStmt(st);
            sb.append("    jmp ").append(start).append("\n");
            sb.append(end).append(":\n");
        } else if (stmt instanceof KofCAst.AsmStmt s) {
            // emit raw byte
            sb.append("    .byte ").append(s.value()).append("\n");
        } else if (stmt instanceof KofCAst.CallStmt s) {
            // builtin print handling
            if (s.name().equals("print") || s.name().equals("print_int") || s.name().equals("kof_print")) {
                sb.append("    call kof_print\n");
            } else if (s.name().equals("kof_print_int")) {
                // expects rdi already set? we call via global
                sb.append("    call kof_print_int\n");
            } else {
                sb.append("    call ").append(s.name()).append("\n");
            }
        } else if (stmt instanceof KofCAst.AssignStmt s) {
            // evaluate RHS to rax
            emitExpr(s.value());
            // save rax to rcx
            sb.append("    mov rcx, rax\n");
            if (s.deref()) {
                // *(int*)target = rcx ; target holds address
                // load target's value (address) to rax
                sb.append("    mov rax, qword ptr [rip + ").append(s.target()).append("]\n");
                sb.append("    mov qword ptr [rax], rcx\n");
            } else {
                sb.append("    mov qword ptr [rip + ").append(s.target()).append("], rcx\n");
            }
        }
    }

    private void emitExpr(KofCAst.Expr expr) {
        switch (expr) {
            case KofCAst.IntExpr e -> {
                sb.append("    mov rax, ").append(e.value()).append("\n");
            }
            case KofCAst.IdentExpr e -> {
                sb.append("    mov rax, qword ptr [rip + ").append(e.name()).append("]\n");
            }
            case KofCAst.UnaryAddr e -> {
                sb.append("    lea rax, [rip + ").append(e.ident()).append("]\n");
            }
            case KofCAst.UnaryDeref e -> {
                sb.append("    mov rax, qword ptr [rip + ").append(e.ident()).append("]\n");
                sb.append("    mov rax, qword ptr [rax]\n");
            }
            case KofCAst.ParenExpr e -> {
                emitExpr(e.inner());
            }
            case KofCAst.BinaryExpr e -> {
                // left -> push, right -> rax, pop left to rcx, compute rcx op rax -> rax
                emitExpr(e.left());
                sb.append("    push rax\n");
                emitExpr(e.right());
                sb.append("    mov rcx, rax\n"); // right in rcx
                sb.append("    pop rax\n"); // left in rax
                // now rax = left, rcx = right, compute rax op rcx -> rax
                switch (e.op()) {
                    case "+" -> sb.append("    add rax, rcx\n");
                    case "-" -> sb.append("    sub rax, rcx\n");
                    case "&" -> sb.append("    and rax, rcx\n");
                    case "|" -> sb.append("    or rax, rcx\n");
                    case "^" -> sb.append("    xor rax, rcx\n");
                    case "<<" -> sb.append("    mov rcx, rcx\n    shl rax, cl\n");
                    case ">>" -> sb.append("    mov rcx, rcx\n    sar rax, cl\n");
                    case "==" -> {
                        sb.append("    cmp rax, rcx\n");
                        sb.append("    sete al\n");
                        sb.append("    movzx rax, al\n");
                    }
                    case "!=" -> {
                        sb.append("    cmp rax, rcx\n");
                        sb.append("    setne al\n");
                        sb.append("    movzx rax, al\n");
                    }
                    case "<" -> {
                        sb.append("    cmp rax, rcx\n");
                        sb.append("    setl al\n");
                        sb.append("    movzx rax, al\n");
                    }
                    case ">" -> {
                        sb.append("    cmp rax, rcx\n");
                        sb.append("    setg al\n");
                        sb.append("    movzx rax, al\n");
                    }
                    case "<=" -> {
                        sb.append("    cmp rax, rcx\n");
                        sb.append("    setle al\n");
                        sb.append("    movzx rax, al\n");
                    }
                    case ">=" -> {
                        sb.append("    cmp rax, rcx\n");
                        sb.append("    setge al\n");
                        sb.append("    movzx rax, al\n");
                    }
                    default -> sb.append("    ; unknown op ").append(e.op()).append("\n");
                }
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else)
        }
    }

    private String freshLabel(String base) {
        return ".L" + base + "_" + labelSeq.getAndIncrement();
    }
}
