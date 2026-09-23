package dev.kof.compiler.runtime;

import dev.kof.compiler.nat.NativeProfile;

/**
 * B-6 (PLAN-BAREMETAL-BOOT), fatia B-6.1: tabelas de descritor PRÓPRIAS do Kof
 * para o perfil {@code uefi-ring}. Sob OVMF o app já entra em long mode/CPL0,
 * mas com a GDT/IDT do firmware (sem descritores ring1). Esta fatia instala a
 * GDT do Kof (null + code/data ring0/ring1 + TSS 64-bit), um TSS com {@code
 * rsp0}, e uma IDT própria; recarrega {@code CS} para o seletor do Kof
 * ({@code 0x08}) via {@code lretq}, {@code lidt} + {@code ltr}. A prova
 * falseável: um {@code int3} controlado cai no handler ring0 do Kof
 * ({@code kof_rings_bp}), que incrementa {@code kof_rings_hits}; o auto-teste só
 * imprime {@code KO-RING IDT OK} se o contador casou — então restaura GDT/IDT/
 * segmentos/flags do firmware e retorna.
 *
 * <p>Sem superfície Kof (rule 6): a maquinaria entra sem sintaxe/API nova; o
 * "domínio ring1" de fato (CPL1) é a fatia B-6.2, que depende de decisão.
 *
 * <p>Interrupções: os descritores trocam por uma janela com GDT própria, então
 * as IRQs ficam desabilitadas ({@code cli}) durante ela e o RFLAGS original é
 * restaurado no fim — nenhum handler do firmware roda com a GDT do Kof (que não
 * tem os seletores dele). A IDT própria tem um gate padrão que trava (bug = hang
 * limitado pelo teste, nunca corrupção silenciosa) e {@code #BP} com o handler.
 */
public final class RuntimeRings {

    private RuntimeRings() {}

    /** Emite a maquinaria de anéis SÓ no perfil {@code uefi-ring} (nos demais o
     *  texto é vazio — a fatia existe, mas não entra). */
    public static void emitRings(StringBuilder sb) {
        if (NativeProfile.active != NativeProfile.UEFI_RING) return;
        sb.append("""
            .section .data
            .align 16
            .globl kof_gdt
            kof_gdt:
                .quad 0
                .quad 0x00AF9A000000FFFF    # 0x08 ring0 code (P=1,DPL=0,type=A,L=1,G=1)
                .quad 0x00CF92000000FFFF    # 0x10 ring0 data
                .quad 0x00AFFA000000FFFF    # 0x18 ring1 code (DPL=1)
                .quad 0x00CFF2000000FFFF    # 0x20 ring1 data (DPL=1)
            kof_gdt_tss:
                .word 0x0067                # limit 15:0
                .word 0                     # base 15:0  (kof_rings_init patcheia)
                .byte 0                     # base 23:16 (patch)
                .byte 0x89                  # P=1,DPL=0,type=1001 (64-bit TSS avail)
                .byte 0x00                  # flags + limit 19:16
                .byte 0                     # base 31:24 (patch)
                .long 0                     # base 63:32 (patch)
                .long 0                     # reservado
            kof_gdt_end:

            .align 16
            kof_tss:                        # TSS 64-bit (0x68 bytes)
                .zero 4                     # reservado
                .quad 0                     # rsp0 (patch: kof_rings_init)
                .quad 0                     # rsp1
                .quad 0                     # rsp2
                .quad 0                     # reservado
                .quad 0
                .quad 0
                .quad 0
                .quad 0
                .quad 0
                .quad 0
                .quad 0
                .zero 2
                .word 0x68                  # iomap base (fim do TSS = sem bitmap)

            .align 16
            kof_rings_idt:
                .zero 4096                  # 256 * 16 (preenchida em runtime)
            kof_rings_fw_gdtr:
                .zero 10                    # GDTR do firmware (sgdt salva)
            kof_rings_fw_idtr:
                .zero 10                    # IDTR do firmware (sidt salva)
            kof_rings_saved:
                .quad 0                     # rflags original
                .quad 0                     # CS original
                .quad 0                     # DS original
                .quad 0                     # ES original
                .quad 0                     # SS original
            .globl kof_rings_hits
            kof_rings_hits:
                .quad 0

            .section .text
            # Gate padrão da IDT própria (vetor não tratado): trava. Só é
            # alcançável por bug — int3/fault esperados têm handler dedicado.
            kof_rings_default:
                cli
                hlt
                jmp kof_rings_default

            # #BP (int3): prova VIVA de que a IDT do Kof está instalada.
            .globl kof_rings_bp
            kof_rings_bp:
                incq kof_rings_hits(%rip)
                iretq

            .globl kof_rings_init
            kof_rings_init:
                pushq %rbx
                pushq %r12
                subq $24, %rsp             # scratch: GDTR/IDTR locais (10B cada)
                # salva estado do firmware
                sgdt kof_rings_fw_gdtr(%rip)
                sidt kof_rings_fw_idtr(%rip)
                pushfq
                popq %rax
                movq %rax, kof_rings_saved(%rip)
                cli                        # nenhum handler do firmware na GDT do Kof
                movw %cs, %ax
                movzwl %ax, %eax
                movq %rax, kof_rings_saved+8(%rip)
                movw %ds, %ax
                movzwl %ax, %eax
                movq %rax, kof_rings_saved+16(%rip)
                movw %es, %ax
                movzwl %ax, %eax
                movq %rax, kof_rings_saved+24(%rip)
                movw %ss, %ax
                movzwl %ax, %eax
                movq %rax, kof_rings_saved+32(%rip)
                # TSS.rsp0 = rsp atual (stack dedicada vem na B-6.2)
                movq %rsp, kof_tss+4(%rip)
                # patcheia a base do descritor de TSS com &kof_tss
                leaq kof_tss(%rip), %rax
                movw %ax, kof_gdt_tss+2(%rip)
                shrq $16, %rax
                movb %al, kof_gdt_tss+4(%rip)
                shrq $8, %rax
                movb %al, kof_gdt_tss+7(%rip)
                shrq $8, %rax
                movl %eax, kof_gdt_tss+8(%rip)
                # GDTR local: limite + &kof_gdt, e lgdt
                movw $(kof_gdt_end - kof_gdt - 1), (%rsp)
                leaq kof_gdt(%rip), %rax
                movq %rax, 2(%rsp)
                lgdt (%rsp)
                # recarrega CS com o seletor do Kof (far return)
                leaq .Lrings_after_cs(%rip), %rax
                pushq $0x08
                pushq %rax
                lretq
            .Lrings_after_cs:
                movw $0x10, %ax
                movw %ax, %ds
                movw %ax, %es
                movw %ax, %ss
                # IDT do Kof: 256 gates -> gate padrão, depois patcheia #BP(3)
                leaq kof_rings_idt(%rip), %r8
                leaq kof_rings_default(%rip), %r9
                xorl %ecx, %ecx
            .Lrings_idt_loop:
                movq %r9, %rax
                movw %ax, 0(%r8)
                movw $0x08, 2(%r8)
                movb $0, 4(%r8)
                movb $0x8E, 5(%r8)
                shrq $16, %rax
                movw %ax, 6(%r8)
                shrq $16, %rax
                movl %eax, 8(%r8)
                movl $0, 12(%r8)
                addq $16, %r8
                incl %ecx
                cmpl $256, %ecx
                jb .Lrings_idt_loop
                leaq kof_rings_idt+48(%rip), %r8
                leaq kof_rings_bp(%rip), %rax
                movw %ax, 0(%r8)
                movw $0x08, 2(%r8)
                movb $0, 4(%r8)
                movb $0x8E, 5(%r8)
                shrq $16, %rax
                movw %ax, 6(%r8)
                shrq $16, %rax
                movl %eax, 8(%r8)
                movl $0, 12(%r8)
                # IDTR local + lidt; carrega o TSS
                movw $4095, (%rsp)
                leaq kof_rings_idt(%rip), %rax
                movq %rax, 2(%rsp)
                lidt (%rsp)
                movw $0x28, %ax
                ltr %ax
                addq $24, %rsp
                popq %r12
                popq %rbx
                ret

            # Prova: dispara int3 e confere que o handler do Kof rodou.
            .globl kof_rings_selftest
            kof_rings_selftest:
                subq $8, %rsp
                movq $0, kof_rings_hits(%rip)
                int3
                cmpq $1, kof_rings_hits(%rip)
                jne .Lrings_fail
                leaq .Lrings_ok(%rip), %rsi
                movq $15, %rdx
                movl $1, %edi
                call kof_plat_write
                addq $8, %rsp
                ret
            .Lrings_fail:
                leaq .Lrings_bad(%rip), %rsi
                movq $16, %rdx
                movl $1, %edi
                call kof_plat_write
                addq $8, %rsp
                ret

            # Restaura GDT/IDT/segmentos/flags do firmware antes de devolver o
            # controle ao carregador (zero efeito colateral para o boot).
            .globl kof_rings_restore
            kof_rings_restore:
                lgdt kof_rings_fw_gdtr(%rip)
                movq kof_rings_saved+8(%rip), %rax
                leaq .Lrings_restored(%rip), %rdx
                pushq %rax
                pushq %rdx
                lretq
            .Lrings_restored:
                movq kof_rings_saved+16(%rip), %rax
                movw %ax, %ds
                movq kof_rings_saved+24(%rip), %rax
                movw %ax, %es
                movq kof_rings_saved+32(%rip), %rax
                movw %ax, %ss
                lidt kof_rings_fw_idtr(%rip)
                pushq kof_rings_saved(%rip)
                popfq
                ret

            .section .rodata
            .Lrings_ok:  .asciz "KO-RING IDT OK\\n"
            .Lrings_bad: .asciz "KO-RING IDT BAD\\n"

            .section .text
            """);
    }
}
