package dev.kof.compiler.runtime;

/**
 * B-2 (PLAN-BAREMETAL-BOOT): corpos da costura {@code kof_plat_*} para o
 * perfil UEFI (x86_64). Mesma ABI dos corpos Linux de {@code RuntimePlat}
 * (SysV nos chamadores do runtime) — o que muda é a FALA com o firmware:
 * UEFI x86_64 é MS x64 (RCX/RDX/R8/R9 + 32B de shadow space), e os serviços
 * vêm de tabelas ({@code SystemTable->ConOut->OutputString},
 * {@code BootServices->Exit}), não de syscalls.
 *
 * <p>Layout de tabelas (UEFI 2.x, fixo): {@code ST+64 = ConOut},
 * {@code ST+96 = BootServices}, {@code ConOut+8 = OutputString},
 * {@code gBS+64 = AllocatePool}, {@code gBS+208 = Exit}. Os helpers
 * {@code kof_efi_call3}/{@code kof_efi_call4} traduzem SysV→MS à mão
 * (RCX/RDX/R8 + shadow + alinhamento 16) — o código Kof emitido continua
 * SysV e só conversa com a costura.
 *
 * <p>Globals em {@code .data} (valor 0 explícito) — o PE não carrega
 * {@code .bss} de forma confiável via objcopy; medição B-2: com
 * {@code -j .bss} o objcopy cria seção uninit, mas os globals da costura
 * ficam em .data por simplicidade de auditoria.
 */
public final class RuntimeUefi {

    private RuntimeUefi() {}

    /** Globals + helpers MS-ABI + dummy .reloc (o loader EDK2 exige um
     *  diretório de relocações NÃO-VAZIO: um bloco padding de 10 bytes —
     *  PageRVA=0, BlockSize=10, uma entrada ABSOLUTE — mede o mínimo). */
    public static void emitUefiHelpers(StringBuilder sb) {
        sb.append("""
            .section .data
            .globl kof_efi_st
            kof_efi_st:   .quad 0
            .globl kof_efi_ih
            kof_efi_ih:   .quad 0

            .section .text
            # SysV->MS x64: rdi=fn, rsi=a1, rdx=a2 -> fn(a1,a2) com shadow 32B
            .globl kof_efi_call3
            .type kof_efi_call3, @function
            kof_efi_call3:
                movq %rsi, %rcx
                subq $40, %rsp
                call *%rdi
                addq $40, %rsp
                ret

            # SysV->MS x64: rdi=fn, rsi=a1, rdx=a2, rcx=a3 (a4=0) -> fn(a1,a2,a3,0)
            .globl kof_efi_call4
            .type kof_efi_call4, @function
            kof_efi_call4:
                movq %rcx, %r8
                xorl %r9d, %r9d
                movq %rsi, %rcx
                subq $40, %rsp
                call *%rdi
                addq $40, %rsp
                ret

            # Entrada MS x64: RCX=ImageHandle, RDX=SystemTable. Guarda os dois
            # e segue a maquinaria SysV padrao (emitStart emite o resto).
            .globl kof_efi_save_args
            .type kof_efi_save_args, @function
            kof_efi_save_args:
                movq %rdx, kof_efi_st(%rip)
                movq %rcx, kof_efi_ih(%rip)
                ret

            .section .reloc, "aR"   # R = SHF_GNU_RETAIN: sobrevive ao --gc-sections
            .long 0, 10
            .short 0
            """);
    }

    /** {@code kof_plat_write} UEFI — MESMO contrato do corpo Linux
     *  (write-like): {@code rdi=fd (ignorado), rsi=buf, rdx=len}. Converte
     *  UTF-8→UTF-16LE ({@code '\n'} → CR LF) num buffer de 1 KiB na pilha e
     *  imprime via {@code ConOut->OutputString}. ASCII puro é o caso feliz;
     *  bytes ≥0x80 viram o byte replicado (latin-1-ish) — o B-2 fatia 3
     *  refina para UTF-8 completo. */
    public static void emitUefiWrite(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_write
            .type kof_plat_write, @function
            kof_plat_write:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $1056, %rsp
                movq %rsi, %r12        # buf
                movq %rdx, %r13        # len restante
                leaq 16(%rsp), %r14    # buffer UTF-16 (1024B) — NUL sempre dentro
            .Luefi_chunk:
                xorl %r15d, %r15d      # chars no buffer
            .Luefi_loop:
                testq %r13, %r13
                jz .Luefi_flush
                cmpq $510, %r15
                jae .Luefi_flush
                movzbl (%r12), %edi
                cmpl $10, %edi         # newline -> CR LF
                je .Luefi_lf
                movl %edi, %eax
                movw %ax, (%r14,%r15,2)
                incq %r15
                incq %r12
                decq %r13
                jmp .Luefi_loop
            .Luefi_lf:
                movw $13, (%r14,%r15,2)
                movw $10, 2(%r14,%r15,2)
                addq $2, %r15
                incq %r12
                decq %r13
            .Luefi_flush:
                testq %r15, %r15
                jz .Luefi_done
                movw $0, (%r14,%r15,2) # NUL terminador
                movq kof_efi_st(%rip), %rax
                movq 64(%rax), %r11    # ConOut
                movq 8(%r11), %rax     # OutputString
                movq %r11, %rcx        # MS a1 = This
                movq %r14, %rdx        # MS a2 = string UTF-16
                call *%rax             # rsp%16 == 0 aqui (prologo: 5 pushes + 1056)
                jmp .Luefi_chunk
            .Luefi_done:
                addq $1056, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_plat_writev
            .type kof_plat_writev, @function
            kof_plat_writev:
                # MESMO contrato do Linux: rdi=fd (ignorado), rsi=iovec, rdx=count
                testq %rdx, %rdx
                jz .Luefi_wv_done
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rsi, %rbx
                movq %rdx, %r13
            .Luefi_wv_loop:
                movq (%rbx), %rsi      # base
                movq 8(%rbx), %rdx     # len
                movl $1, %edi          # fd (contrato write-like)
                call kof_plat_write
                addq $16, %rbx
                decq %r13
                jnz .Luefi_wv_loop
                popq %r13
                popq %r12
                popq %rbx
            .Luefi_wv_done:
                ret

            .globl kof_plat_exit
            .type kof_plat_exit, @function
            kof_plat_exit:
                jmp kof_plat_exit_group

            .globl kof_plat_exit_group
            .type kof_plat_exit_group, @function
            kof_plat_exit_group:
                # Fim canônico de app UEFI: RETORNAR o status ao StartImage
                # (gBS->StartImage devolve o valor de RAX da entry; Exit() é
                # só para saída antecipada com dados — medição B-2: chamar
                # Exit aqui crashava o DXE com #UD).
                movl %edi, %eax        # EFI_STATUS = exit code
                ret
            """);
    }

    /** UEFI não tem TID — thread única do firmware. */
    public static void emitUefiThreadId(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_thread_id
            .type kof_plat_thread_id, @function
            kof_plat_thread_id:
                movq $1, %rax
                ret
            """);
    }

    /** UEFI single-threaded: o lock do alocador (futex) nunca tem
     *  contensão — no-op com retorno 0 é semântica correta. */
    public static void emitUefiSyncNoop(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_sync
            .type kof_plat_sync, @function
            kof_plat_sync:
                xorl %eax, %eax
                ret
            """);
    }

    /** Costura de CRESCIMENTO de heap no UEFI: {@code AllocatePool}
     *  (gBS+64) — {@code AllocatePool(EfiLoaderData=2, size, &out)}; falha
     *  devolve -1 (a semântica mmap que o alocador testa com {@code js}). */
    public static void emitUefiHeapGrow(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_heap_grow
            .type kof_plat_heap_grow, @function
            kof_plat_heap_grow:
                # rdi=tamanho -> rax=ptr | -1
                subq $24, %rsp          # slot de 8B + alinhamento (entry rsp%16==8)
                leaq (%rsp), %r10       # &out
                movq %rdi, %rdx         # MS a2 = size
                movq kof_efi_st(%rip), %rax
                movq 96(%rax), %rax     # BootServices
                movq 64(%rax), %rdi     # fn = AllocatePool
                movl $2, %esi           # MS a1 = EfiLoaderData
                movq %r10, %rcx         # MS a3 = &out (call4 leva p/ R8)
                call kof_efi_call4
                testq %rax, %rax
                jnz .Lkof_heap_grow_fail
                movq (%rsp), %rax
                addq $24, %rsp
                ret
            .Lkof_heap_grow_fail:
                movq $-1, %rax
                addq $24, %rsp
                ret
            """);
    }

    /** Recusa NOMEADA (R6) para famílias de costura sem corpo UEFI nesta
     *  fatia (time/random/sync/threads/io/net): imprime o diagnóstico pela
     *  própria costura de write e sai via {@code BootServices->Exit} —
     *  nunca um corpo syscall-Linux que não existe no firmware. */
    public static void emitUefiRefuse(StringBuilder sb, String symbols) {
        String msg = "KOF UEFI: capacidade nao suportada nesta fatia (B-2): " + symbols + "\r\n";
        String tag = symbols.split(", ")[0].replace("kof_plat_", "");
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < msg.length(); i++) {
            if (i > 0) words.append(',');
            char c = msg.charAt(i);
            words.append(c == '\'' ? "'\\''" : "'" + c + "'");
        }
        String lbl = ".Lkof_uefi_refuse_" + tag;
        sb.append("        .section .rodata\n")
          .append(lbl).append(": .word ").append(words).append('\n')
          .append(lbl).append("_end:\n")
          .append("        .section .text\n");
        for (String sym : symbols.split(", ")) {
            sb.append("        .globl ").append(sym).append('\n')
              .append("        .type ").append(sym).append(", @function\n")
              .append(sym).append(":\n");
        }
        sb.append("        subq $8, %rsp          # alinha (entry rsp%16==8)\n")
          .append("        movl $1, %edi\n")
          .append("        leaq ").append(lbl).append("(%rip), %rsi\n")
          .append("        movq $").append(lbl).append("_end - ").append(lbl).append(", %rdx\n")
          .append("        call kof_plat_write\n")
          .append("        xorl %edi, %edi\n")
          .append("        call kof_plat_exit_group\n");
    }
}
