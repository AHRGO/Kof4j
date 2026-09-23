package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;

/**
 * B-3 (PLAN-BAREMETAL-BOOT): emissão do entry LEGACY BIOS — o setor de boot de
 * 512 bytes em modo real 16-bit que carrega o setor do payload, valida a magia e
 * entra em long mode (x86_64). Extraído de {@link NativeMethodEmitter} (gate
 * ≤500/≤600 linhas); a responsabilidade aqui é só o assembly do setor de boot.
 */
final class NativeBiosBootEmitter {

    private NativeBiosBootEmitter() {}

    /** Emite o setor de boot BIOS; no-op se a classe não tem {@code main} (entry). */
    static void emit(StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        sb.append("\n.section .text.boot,\"ax\"\n");
        sb.append(".globl _start\n");
        sb.append(".code16\n");
        sb.append("_start:\n");
        sb.append("    cli\n");
        sb.append("    xorw %ax, %ax\n");
        sb.append("    movw %ax, %ds\n");
        sb.append("    movw %ax, %es\n");
        sb.append("    movw %ax, %ss\n");
        sb.append("    movw $0x7C00, %sp\n");
        sb.append("    movb %dl, kof_bios_drive\n");   // drive de boot (BIOS entrega em DL)
        // B-3b-1: lê o setor do payload (LBA 1) via EDD int 0x13 ah=0x42
        sb.append("    movb $0x42, %ah\n");
        sb.append("    movb kof_bios_drive, %dl\n");
        sb.append("    movw $kof_bios_dap, %si\n");
        sb.append("    int $0x13\n");
        sb.append("    jc kof_bios_load_bad\n");       // carry = falha NOMEADA, nunca hang
        sb.append("    movl $0x50464F4B, %eax\n");     // magia "KOFP"
        sb.append("    cmpl %eax, 0x8000\n");
        sb.append("    jne kof_bios_load_bad\n");
        sb.append("    movl $0x444C5941, %eax\n");     // magia "AYLD"
        sb.append("    cmpl %eax, 0x8004\n");
        sb.append("    jne kof_bios_load_bad\n");
        sb.append("    movw $kof_bios_ok, %si\n");
        sb.append("    call kof_bios_print\n");
        sb.append("    jmp kof_bios_enter_lm\n");     // B-3b-2
        sb.append("kof_bios_load_bad:\n");
        sb.append("    movw $kof_bios_bad, %si\n");
        sb.append("    call kof_bios_print\n");
        sb.append("    jmp kof_bios_halt\n");
        // --- rotina de print 16-bit (retorna) ---
        sb.append("kof_bios_print:\n");
        sb.append("    lodsb\n");
        sb.append("    testb %al, %al\n");
        sb.append("    jz kof_bios_print_ret\n");
        sb.append("    movb %al, %bl\n");
        sb.append("    movb $0x0E, %ah\n");
        sb.append("    int $0x10\n");              // teletype do BIOS
        sb.append("    movw $0x3FD, %dx\n");       // LSR do COM1
        sb.append("kof_bios_txe:\n");
        sb.append("    inb %dx, %al\n");
        sb.append("    testb $0x20, %al\n");       // THR vazio
        sb.append("    jz kof_bios_txe\n");
        sb.append("    movw $0x3F8, %dx\n");
        sb.append("    movb %bl, %al\n");
        sb.append("    outb %al, %dx\n");          // espelho no serial (captura qemu)
        sb.append("    jmp kof_bios_print\n");
        sb.append("kof_bios_print_ret:\n");
        sb.append("    ret\n");
        sb.append("kof_bios_halt:\n");
        sb.append("    cli\n");
        sb.append("kof_bios_loop:\n");
        sb.append("    hlt\n");
        sb.append("    jmp kof_bios_loop\n");
        // --- B-3b-2: A20 + GDT flat + transição para long mode (x86_64) ---
        sb.append("kof_bios_enter_lm:\n");
        sb.append("    movw $0x2401, %ax\n");       // A20 (fast gate)
        sb.append("    int $0x15\n");
        sb.append("    cli\n");
        sb.append("    lgdt kof_bios_gdt_desc\n");
        sb.append("    movl %cr0, %eax\n");
        sb.append("    orl $1, %eax\n");            // CR0.PE
        sb.append("    movl %eax, %cr0\n");
        sb.append("    .byte 0x66, 0xEA\n");        // far jump -> code32 (0x08)
        sb.append("    .long kof_bios_pm32\n");
        sb.append("    .word 0x08\n");
        sb.append("kof_bios_gdt_desc:\n");
        sb.append("    .word 0x1F\n");              // 4 entradas * 8 - 1
        sb.append("    .long kof_bios_gdt\n");
        sb.append("    .align 8\n");
        sb.append("kof_bios_gdt:\n");
        sb.append("    .quad 0x0000000000000000\n");
        sb.append("    .quad 0x00CF9A000000FFFF\n"); // 0x08 code32
        sb.append("    .quad 0x00CF92000000FFFF\n"); // 0x10 data
        sb.append("    .quad 0x00AF9A000000FFFF\n"); // 0x18 code64 (L=1)
        sb.append(".code32\n");
        sb.append("kof_bios_pm32:\n");
        sb.append("    movw $0x10, %ax\n");
        sb.append("    movw %ax, %ds\n");
        sb.append("    movw %ax, %es\n");
        sb.append("    movw %ax, %ss\n");
        sb.append("    movw %ax, %fs\n");
        sb.append("    movw %ax, %gs\n");
        // tabelas de paginação: identidade do 1º MiB (2 MiB page)
        sb.append("    movl $0xA003, 0x9000\n");    // PML4[0] -> PDPT|P|RW
        sb.append("    movl $0, 0x9004\n");
        sb.append("    movl $0xB003, 0xA000\n");    // PDPT[0] -> PD|P|RW
        sb.append("    movl $0, 0xA004\n");
        sb.append("    movl $0x83, 0xB000\n");      // PD[0] = 0|P|RW|PS (2 MiB)
        sb.append("    movl $0, 0xB004\n");
        sb.append("    movl $0x9000, %eax\n");
        sb.append("    movl %eax, %cr3\n");
        sb.append("    movl %cr4, %eax\n");
        sb.append("    orl $0x20, %eax\n");         // CR4.PAE
        sb.append("    movl %eax, %cr4\n");
        sb.append("    movl $0xC0000080, %ecx\n");  // EFER
        sb.append("    rdmsr\n");
        sb.append("    orl $0x100, %eax\n");        // EFER.LME
        sb.append("    wrmsr\n");
        sb.append("    movl %cr0, %eax\n");
        sb.append("    orl $0x80000000, %eax\n");   // CR0.PG
        sb.append("    movl %eax, %cr0\n");
        sb.append("    .byte 0xEA\n");              // far jump -> code64 (0x18)
        sb.append("    .long kof_bios_lm64\n");
        sb.append("    .word 0x18\n");
        // --- B-3b-2: prova viva rodando código 64-bit ---
        sb.append(".code64\n");
        sb.append("kof_bios_lm64:\n");
        sb.append("    movw $0x10, %ax\n");
        sb.append("    movw %ax, %ds\n");
        sb.append("    movw %ax, %es\n");
        sb.append("    movw %ax, %ss\n");
        sb.append("    movl $kof_bios_lm64_msg, %esi\n");
        sb.append("kof_bios_lm64_loop:\n");
        sb.append("    lodsb\n");
        sb.append("    testb %al, %al\n");
        sb.append("    jz kof_bios_lm64_halt\n");
        sb.append("    movb %al, %bl\n");
        sb.append("kof_bios_lm64_txe:\n");
        sb.append("    movw $0x3FD, %dx\n");
        sb.append("    inb %dx, %al\n");
        sb.append("    testb $0x20, %al\n");
        sb.append("    jz kof_bios_lm64_txe\n");
        sb.append("    movw $0x3F8, %dx\n");
        sb.append("    movb %bl, %al\n");
        sb.append("    outb %al, %dx\n");
        sb.append("    jmp kof_bios_lm64_loop\n");
        sb.append("kof_bios_lm64_halt:\n");
        sb.append("    cli\n");
        sb.append("kof_bios_lm64_h:\n");
        sb.append("    hlt\n");
        sb.append("    jmp kof_bios_lm64_h\n");
        sb.append("kof_bios_dap:\n");              // Disk Address Packet (EDD)
        sb.append("    .byte 0x10, 0\n");
        sb.append("    .word 1\n");                // 1 setor
        sb.append("    .word 0x8000\n");           // offset do buffer de staging
        sb.append("    .word 0\n");                // segmento
        sb.append("    .quad 1\n");                // LBA 1
        sb.append("kof_bios_drive:\n");
        sb.append("    .byte 0\n");
        sb.append("kof_bios_ok:\n");
        sb.append("    .asciz \"KO-BIOS OK\\r\\n\"\n");
        sb.append("kof_bios_bad:\n");
        sb.append("    .asciz \"KO-BIOS LOAD BAD\\r\\n\"\n");
        sb.append("kof_bios_lm64_msg:\n");
        sb.append("    .asciz \"KO-BIOS LM64 OK\\r\\n\"\n");
        sb.append("    .org 510, 0\n");            // preenche até a assinatura
        sb.append("    .word 0xAA55\n");
        // setor do payload em LBA 1: magia + padding até 512 B (imagem válida = 2 setores)
        sb.append(".section .payload,\"a\"\n");
        sb.append(".ascii \"KOFPAYLD\"\n");
        sb.append("    .space 504, 0\n");
    }
}
