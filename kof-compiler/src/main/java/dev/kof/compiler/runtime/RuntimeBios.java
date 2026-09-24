package dev.kof.compiler.runtime;

/**
 * B-5 (D-BAREMETAL-BODIES): corpos da costura {@code kof_plat_*} da face BIOS
 * (x86_64, bare legacy — SeaBIOS/MPC). Mesma ABI SysV dos corpos Linux de
 * {@code RuntimePlat}; o que muda é a fala com a plataforma: no BIOS não há
 * syscalls — o relógio de parede vem do RTC CMOS (portas 0x70/0x71) e a saída
 * de diagnóstico sai em ASCII pelo COM1. Famílias ainda sem corpo (mono/sleep,
 * random, I/O, rede, threads) recusam de forma NOMEADA (R6), nunca stub.
 *
 * <p>Extraído de {@code RuntimePlat} para manter o gate ≤500 (o corpo do RTC é
 * grande); a classe+método continua sendo a granularidade de fatia do podador
 * de {@code RuntimeSlices}.
 */
public final class RuntimeBios {

    private RuntimeBios() {}

    /** Recusa NOMEADA (R6) escolhida por perfil: UEFI fala UTF-16 via
     *  {@code OutputString} (RuntimeUefi); BIOS fala ASCII no COM1 — a forma
     *  UTF-16 no BIOS sairia como lixo com NULs intercalados. */
    static void refuse(StringBuilder sb, String symbols) {
        if (dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            emitRefuse(sb, symbols);
        } else {
            RuntimeUefi.emitUefiRefuse(sb, symbols);
        }
    }

    /** Recusa NOMEADA legível na face BIOS — escreve a mensagem ASCII pela
     *  própria costura de write (COM1) e sai por {@code kof_plat_exit}
     *  ({@code cli;hlt}). Nunca stub silencioso (R6). */
    static void emitRefuse(StringBuilder sb, String symbols) {
        String msg = "KOF BIOS: capacidade nao suportada nesta fatia (B-5): " + symbols + "\\r\\n";
        String tag = symbols.split(", ")[0].replace("kof_plat_", "");
        String lbl = ".Lkof_bios_refuse_" + tag;
        sb.append("        .section .rodata\n")
          .append(lbl).append(": .ascii \"").append(msg).append("\"\n")
          .append(lbl).append("_end:\n")
          .append("        .section .text\n");
        for (String sym : symbols.split(", ")) {
            sb.append("        .globl ").append(sym).append('\n')
              .append("        .type ").append(sym).append(", @function\n")
              .append(sym).append(":\n");
        }
        sb.append("        subq $8, %rsp\n")
          .append("        movl $1, %edi\n")
          .append("        leaq ").append(lbl).append("(%rip), %rsi\n")
          .append("        movq $").append(lbl).append("_end - ").append(lbl).append(", %rdx\n")
          .append("        call kof_plat_write\n")
          .append("        xorl %edi, %edi\n")
          .append("        call kof_plat_exit\n");
    }

    /** Relógio de parede no BIOS a partir do RTC CMOS (portas 0x70/0x71):
     *  aguarda UIP limpar, lê os registradores, converte BCD→binário (bit DM do
     *  status B), trata 12h/24h, resolve o ano (século 0x32, senão infere
     *  19xx/20xx) e devolve {@code ts[0]=tv_sec} (epoch) +
     *  {@code ts[1]=tv_nsec=0} — a ABI que {@code kof_now}→{@code time.now()}
     *  já consome. Mono/sleep ainda sem corpo viram recusa legível. */
    static void emitTime(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .align 4
            .Lkof_dbom: .long 0,0,31,59,90,120,151,181,212,243,273,304,334
            .section .text
            .type kof_plat_cmos_read, @function
            kof_plat_cmos_read:
                movw $0x70, %dx
                outb %al, %dx
                movw $0x71, %dx
                inb %dx, %al
                ret
            .type kof_plat_bcd, @function
            kof_plat_bcd:
                testb $0x04, %r10b
                jz .Lkof_bcd_conv
                movzbl %al, %eax
                ret
            .Lkof_bcd_conv:
                movzbl %al, %eax
                movl %eax, %ecx
                andl $0x0F, %eax
                shrl $4, %ecx
                imull $10, %ecx, %ecx
                addl %ecx, %eax
                ret

            .globl kof_plat_time
            .type kof_plat_time, @function
            kof_plat_time:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $8, %rsp
                movq %rdi, %rbx                 # ts
                movb $0x0B, %al                 # status B (DM/24h)
                call kof_plat_cmos_read
                movb %al, %r10b
            .Lkof_rtc_uip:
                movb $0x0A, %al                 # status A: bit7 = UIP
                call kof_plat_cmos_read
                testb $0x80, %al
                jnz .Lkof_rtc_uip
                movb $0x00, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r12d                # sec
                movb $0x02, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r13d                # min
                movb $0x04, %al                 # hour (12h/PM abaixo)
                call kof_plat_cmos_read
                movzbl %al, %r11d
                xorl %edi, %edi
                testb $0x02, %r10b              # bit1: 1 = 24h
                jnz .Lkof_h_pm_ready
                testl $0x80, %r11d              # bit7 = PM em modo 12h
                jz .Lkof_h_pm_ready
                movl $1, %edi
            .Lkof_h_pm_ready:
                andl $0x7F, %r11d
                movl %r11d, %eax
                call kof_plat_bcd
                testb $0x02, %r10b
                jnz .Lkof_h_done
                cmpl $12, %eax
                jne .Lkof_h_not12
                testl %edi, %edi
                jnz .Lkof_h_twelve
                xorl %eax, %eax
                jmp .Lkof_h_done
            .Lkof_h_twelve:
                movl $12, %eax
                jmp .Lkof_h_done
            .Lkof_h_not12:
                testl %edi, %edi
                jz .Lkof_h_done
                addl $12, %eax
            .Lkof_h_done:
                movl %eax, %r14d                # hour
                movb $0x07, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r15d                # day
                movb $0x08, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r8d                 # month
                movb $0x09, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r11d                # yy (2 digitos)
                movb $0x32, %al                 # seculo (pode faltar)
                call kof_plat_cmos_read
                call kof_plat_bcd
                testl %eax, %eax
                jz .Lkof_year_infer
                imull $100, %eax, %eax
                addl %r11d, %eax
                movl %eax, %r9d
                jmp .Lkof_year_ready
            .Lkof_year_infer:
                movl %r11d, %r9d
                cmpl $70, %r9d
                jb .Lkof_year_20
                addl $1900, %r9d
                jmp .Lkof_year_ready
            .Lkof_year_20:
                addl $2000, %r9d
            .Lkof_year_ready:
                # doy = dbom[month] + (day-1); +1 se (month>2 && bissexto)
                leaq .Lkof_dbom(%rip), %rcx
                movl (%rcx,%r8,4), %eax
                leal -1(%r15), %edx
                addl %edx, %eax
                cmpl $2, %r8d
                jle .Lkof_no_leapadd
                testl $3, %r9d
                jnz .Lkof_no_leapadd
                incl %eax
            .Lkof_no_leapadd:
                # days = 365*(year-1970) + ((year-1)/4 - 492) + doy
                movl %r9d, %ecx
                subl $1970, %ecx
                imull $365, %ecx, %ecx
                addl %eax, %ecx
                movl %r9d, %eax
                decl %eax
                shrl $2, %eax
                subl $492, %eax
                addl %eax, %ecx                 # ecx = dias desde 1970-01-01
                # epoch_s = days*86400 + hour*3600 + min*60 + sec
                movslq %ecx, %rax
                movq $86400, %rdx
                imulq %rdx, %rax
                movslq %r14d, %rdx
                imulq $3600, %rdx, %rdx
                addq %rdx, %rax
                movslq %r13d, %rdx
                imulq $60, %rdx, %rdx
                addq %rdx, %rax
                movslq %r12d, %rdx
                addq %rdx, %rax
                movq %rax, 0(%rbx)              # ts[0] = tv_sec
                movq $0, 8(%rbx)                # ts[1] = tv_nsec
                addq $8, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
        emitRefuse(sb, "kof_plat_time_mono, kof_plat_sleep");
    }
}
