package dev.kof.c;

import java.util.List;

/**
 * Emissor x86_64 (AT&T/gas com {@code .intel_syntax noprefix}) do subconjunto
 * C — alvo histórico, freestanding ({@code as --64} + {@code ld}). Preserva a
 * estrutura original: globais de 8 bytes, acumulador {@code rax}, temporários
 * na pilha e {@code _start} que chama {@code main} e sai por syscall.
 */
final class KofCEmitterX86 extends KofCEmitterBase {

    KofCEmitterX86(KofCAst.Program prog) { super(prog); }

    @Override
    protected void emitDataSection(List<KofCAst.VarDecl> globals) {
        sb.append("    .intel_syntax noprefix\n");
        if (!globals.isEmpty()) {
            sb.append("    .bss\n");
            for (var g : globals) {
                sb.append("    .globl ").append(g.name()).append("\n");
                sb.append("    .comm ").append(g.name()).append(",8,8\n");
            }
        }
    }

    @Override
    protected void emitStart() {
        sb.append("    .globl _start\n");
        sb.append("_start:\n");
        sb.append("    call main\n");
        sb.append("    mov rax, 60\n"); // exit
        sb.append("    xor rdi, rdi\n");
        sb.append("    syscall\n");
    }

    @Override
    protected void emitPrintHelpers() {
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
        if (!hasGlobal("print_arg")) {
            sb.append("    .bss\n");
            sb.append("    .globl print_arg\n");
            sb.append("    .comm print_arg,8,8\n");
            sb.append("    .text\n");
        }
    }

    @Override
    protected void emitFuncPrologue() {
        sb.append("    push rbp\n");
        sb.append("    mov rbp, rsp\n");
    }

    @Override
    protected void emitFuncEpilogue() {
        sb.append("    pop rbp\n");
        sb.append("    ret\n");
    }

    @Override
    protected void emitLoadImm(int v) {
        sb.append("    mov rax, ").append(v).append("\n");
    }

    @Override
    protected void emitLoadGlobal(String name) {
        sb.append("    mov rax, qword ptr [rip + ").append(name).append("]\n");
    }

    @Override
    protected void emitStoreGlobal(String name) {
        sb.append("    mov qword ptr [rip + ").append(name).append("], rax\n");
    }

    @Override
    protected void emitAddrOfGlobal(String name) {
        sb.append("    lea rax, [rip + ").append(name).append("]\n");
    }

    @Override
    protected void emitLoadThrough(String name) {
        sb.append("    mov rax, qword ptr [rip + ").append(name).append("]\n");
        sb.append("    mov rax, qword ptr [rax]\n");
    }

    @Override
    protected void emitDerefStore(String target) {
        sb.append("    mov rcx, rax\n");
        sb.append("    mov rax, qword ptr [rip + ").append(target).append("]\n");
        sb.append("    mov qword ptr [rax], rcx\n");
    }

    @Override
    protected void emitPushAcc() {
        sb.append("    push rax\n");
    }

    @Override
    protected void emitMoveAccToRight() {
        sb.append("    mov rcx, rax\n"); // right in rcx
    }

    @Override
    protected void emitPopLeftToAcc() {
        sb.append("    pop rax\n"); // left in rax
    }

    @Override
    protected void emitBinaryOp(String op) {
        // rax = left, rcx = right -> rax = left op right
        switch (op) {
            case "+" -> sb.append("    add rax, rcx\n");
            case "-" -> sb.append("    sub rax, rcx\n");
            case "&" -> sb.append("    and rax, rcx\n");
            case "|" -> sb.append("    or rax, rcx\n");
            case "^" -> sb.append("    xor rax, rcx\n");
            case "<<" -> sb.append("    shl rax, cl\n");
            case ">>" -> sb.append("    sar rax, cl\n");
            case "==" -> { sb.append("    cmp rax, rcx\n"); sb.append("    sete al\n"); sb.append("    movzx rax, al\n"); }
            case "!=" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setne al\n"); sb.append("    movzx rax, al\n"); }
            case "<" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setl al\n"); sb.append("    movzx rax, al\n"); }
            case ">" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setg al\n"); sb.append("    movzx rax, al\n"); }
            case "<=" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setle al\n"); sb.append("    movzx rax, al\n"); }
            case ">=" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setge al\n"); sb.append("    movzx rax, al\n"); }
            default -> sb.append("    ; unknown op ").append(op).append("\n");
        }
    }

    @Override
    protected void emitBranchIfZero(String label) {
        sb.append("    cmp rax, 0\n");
        sb.append("    je ").append(label).append("\n");
    }

    @Override
    protected void emitJump(String label) {
        sb.append("    jmp ").append(label).append("\n");
    }

    @Override
    protected void emitCall(String name) {
        sb.append("    call ").append(name).append("\n");
    }
}
