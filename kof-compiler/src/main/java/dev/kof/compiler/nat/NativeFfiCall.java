package dev.kof.compiler.nat;

import dev.kof.compiler.FfiSignature;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;

/**
 * #431 (Native FFI): call-site do `extern` no alvo nativo — marshaling SysV
 * (x86-64) direto para uma shared object ligada no link do binário
 * (`call sym@PLT`, o caminho de saída PROVO de §61 e o mesmo mecanismo do
 * consumidor SQLite/DB001). Nenhum dlopen em runtime; nenhum wrapper à mão.
 *
 * <p>Contrato do KofCall (montado em {@code ExpressionMethodCallLowerer}):
 * owner {@code kof.ffi}, methodName {@code "lib::simbolo"}, parameterTypes =
 * os tipos ESCALARES declarados do extern, returnType = tipo do extern. Os
 * argumentos já estão na pilha de operandos (empilhados esquerda→direita,
 * 8 bytes por slot — a mesma convenção dos calls internos {@code kof_*}).
 *
 * <p>Classes SysV: int-class = {Int, Long, Bool, String(char*)} →
 * rdi,rsi,rdx,rcx,r8,r9, depois pilha; float-class = {Float, Double} →
 * xmm0..7, depois pilha. String: o objeto Kof nativo carrega UTF-8
 * NUL-terminado no offset 24 (layout {@code kof_string_from_literal}) — vai
 * {@code obj+24} direto (NULL Kof → NULL C, nunca segfault silencioso).
 * Retorno String = cópia na fronteira p/ um objeto Kof novo (o buffer C NUNCA
 * é free'd — mesmo contrato do downcall JVM {@code strstr}); void não empilha.
 */
final class NativeFfiCall {

    private NativeFfiCall() {}

    static boolean isExternCall(KofCall kc) {
        return kc.kind() == KofCallKind.FUNCTION
                && kc.ownerType() instanceof Type.ClassType ct
                && "ffi".equals(ct.name()) && "kof".equals(ct.packageName())
                && kc.methodName().indexOf("::") >= 0;
    }

    static String libOf(KofCall kc) {
        return kc.methodName().substring(0, kc.methodName().indexOf("::"));
    }

    static String symbolOf(KofCall kc) {
        return kc.methodName().substring(kc.methodName().indexOf("::") + 2);
    }

    /** true se o retorno exige o helper de cópia char*→objeto String. */
    static boolean returnsCstr(KofCall kc) {
        Character c = FfiSignature.charOfType(kc.returnType());
        return c != null && c.charValue() == 'S';
    }

    private static boolean isFloatClass(char c) { return c == 'f' || c == 'd'; }

    // ── x86-64 (SysV) ────────────────────────────────────────────────
    static void emitX86(NativeBackend nb, StringBuilder sb, KofCall kc) {
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        int n = kc.parameterTypes().size();
        char[] cls = new char[n];
        for (int i = 0; i < n; i++) cls[i] = FfiSignature.charOfType(kc.parameterTypes().get(i));
        char ret = FfiSignature.charOfType(kc.returnType()).charValue();
        // ordinais POR CLASSE na ordem formal (arg0 → reg0 da sua classe)
        int[] ord = new int[n];
        int nInt = 0, nFlt = 0;
        for (int i = 0; i < n; i++) {
            if (isFloatClass(cls[i])) { ord[i] = nFlt++; } else { ord[i] = nInt++; }
        }
        int spill = (nInt > 6 ? nInt - 6 : 0) + (nFlt > 8 ? nFlt - 8 : 0);
        int seq = nb.inlineSeq++;
        // 1) desempilha direita→esquerda (o topo é o último arg) nos destinos
        for (int i = n - 1; i >= 0; i--) {
            char c = cls[i];
            if (isFloatClass(c)) {
                sb.append("    popq %r11\n");
                if (ord[i] < 8) {
                    sb.append(c == 'f' ? "    movd %r11d, %xmm" : "    movq %r11, %xmm")
                      .append(ord[i]).append("\n");
                } else {
                    sb.append("    movq %r11, -").append(256 + i * 8).append("(%rbp)\n");
                }
            } else if (ord[i] < 6) {
                sb.append("    popq ").append(intRegs[ord[i]]).append("\n");
                if (c == 'S') {
                    // char* = payload UTF-8 do objeto (offset 24); NULL → NULL
                    String lbl = ".Lffi_s" + seq + "_" + i;
                    sb.append("    testq ").append(intRegs[ord[i]]).append(", ").append(intRegs[ord[i]]).append("\n");
                    sb.append("    je ").append(lbl).append("\n");
                    sb.append("    leaq 24(").append(intRegs[ord[i]]).append("), ").append(intRegs[ord[i]]).append("\n");
                    sb.append(lbl).append(":\n");
                }
            } else {
                sb.append("    popq %r11\n");
                if (c == 'S') {
                    String lbl = ".Lffi_s" + seq + "_" + i;
                    sb.append("    testq %r11, %r11\n");
                    sb.append("    je ").append(lbl).append("\n");
                    sb.append("    leaq 24(%r11), %r11\n");
                    sb.append(lbl).append(":\n");
                }
                sb.append("    movq %r11, -").append(256 + i * 8).append("(%rbp)\n");
            }
        }
        // 2) pilha SysV: salva o topo da pilha de operandos, alinha 16,
        //    compensa a paridade do spill e empilha os derramados — o formal
        //    mais à DIREITA primeiro p/ o 1º derramado ficar em 0(%rsp).
        sb.append("    movq %rsp, %rbx\n");
        sb.append("    andq $-16, %rsp\n");
        if (spill % 2 != 0) sb.append("    subq $8, %rsp\n");
        for (int i = n - 1; i >= 0; i--) {
            if (isFloatClass(cls[i]) ? ord[i] >= 8 : ord[i] >= 6) {
                sb.append("    pushq -").append(256 + i * 8).append("(%rbp)\n");
            }
        }
        // 3) o call direto (PLT → ld.so resolve no exec; sem dlopen — §61)
        sb.append("    call ").append(symbolOf(kc)).append("@PLT\n");
        sb.append("    movq %rbx, %rsp\n");
        // 4) retorno → slot de 8 bytes (void: nada)
        switch (ret) {
            case 'v': return;
            case 'i': sb.append("    movslq %eax, %rax\n"); break;
            case 'j': break;
            case 'f': sb.append("    movd %xmm0, %eax\n"); break;
            case 'd': sb.append("    movq %xmm0, %rax\n"); break;
            case 'b': sb.append("    movzbl %al, %eax\n"); break;
            case 'S':
                // C devolve o char* em %rax; o helper recebe p/ %rdi (conv
                // dos helpers kof_* de 1 arg — mesmo movimento que o call faz).
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call kof_ffi_from_cstr\n");
                break;
            default: return;
        }
        sb.append("    pushq %rax\n");
    }

    // ── riscv64 / aarch64 (LP64 duploat + AAPCS64) ───────────────────
    // O aarch64 é tradução linha-a-linha deste texto (NativeAarch64Translator
    // cobre ld/sd/mv/li/addi/and/j/beqz/call→bl/sext.w/fmv.*/lbu/ret) — um
    // shim serve as duas archs. Modelo do backend cross: `sp` É a pilha de
    // operandos; bloco de args [E, E+8n) (direita em 0(sp)); nada é popado —
    // os registradores saem por OFFSET de t0 e o sp é movido UMA vez para o
    // bloco derramado + consumo. O ponto de restauração (E+8n) mora numa
    // pilha privada ALINHADA entregue à C: a C escreve só abaixo do sp que
    // recebe e lê só [0(sp), 8·ns(sp)) — a slot salva em 8·ns+8 fica intacta.
    static void emitRiscv(NativeBackend nb, StringBuilder sb, KofCall kc) {
        String[] intRegs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        int n = kc.parameterTypes().size();
        char[] cls = new char[n];
        for (int i = 0; i < n; i++) cls[i] = FfiSignature.charOfType(kc.parameterTypes().get(i));
        char ret = FfiSignature.charOfType(kc.returnType()).charValue();
        // ordinais POR CLASSE na ordem formal (arg0 → reg0 da sua classe)
        int[] ord = new int[n];
        int nInt = 0, nFlt = 0;
        for (int i = 0; i < n; i++) {
            if (isFloatClass(cls[i])) { ord[i] = nFlt++; } else { ord[i] = nInt++; }
        }
        int ns = (nInt > 8 ? nInt - 8 : 0) + (nFlt > 8 ? nFlt - 8 : 0);
        int seq = nb.inlineSeq++;
        // 1) t0 = topo E; args de registro por offset SEM popar (o bloco fica
        //    intacto p/ os derramados; String: payload no offset 24, NULL→NULL)
        sb.append("    mv t0, sp\n");
        for (int i = 0; i < n; i++) {
            char c = cls[i];
            boolean floatC = isFloatClass(c);
            if (floatC ? ord[i] >= 8 : ord[i] >= 8) continue; // derramado: passo 3
            String dst = floatC ? "t2" : intRegs[ord[i]];
            sb.append("    ld ").append(dst).append(", ").append(8 * (n - 1 - i)).append("(t0)\n");
            if (c == 'S') {
                String lbl = ".Lffis" + seq + "_" + i;
                sb.append("    beqz ").append(dst).append(", ").append(lbl).append("\n");
                sb.append("    addi ").append(dst).append(", ").append(dst).append(", 24\n");
                sb.append(lbl).append(":\n");
            }
            // fa0..fa7 (NÃO f0..f7!): a ABI C riscv64 põe args FP nos apelidos
            // fa* = registradores FÍSICOS f10-f17 (ft0/f0 é só o RETORNO) —
            // medido 19/09: glibc riscv64 `exp` lê fa0; f0 passava despercebido.
            // O tradutor aarch normaliza fa0..7→f0..7→d0..d7 (AAPCS64 ✓) — o
            // MESMO texto serve as duas archs.
            if (floatC) {
                sb.append(c == 'f' ? "    fmv.w.x fa" : "    fmv.d.x fa")
                  .append(ord[i]).append(", t2\n");
            }
        }
        // 2) consome o bloco inteiro + reserva o área derramada + alinha 16.
        //    Immediato = 8n − 8ns − 16 ≥ 48 p/ n ≥ 1 (ns ≤ n−8 por classe).
        // Alinhamento via t3 (nunca `and sp,sp,..` direto: no aarch64 `and`
        // rejeita sp como Rn — o tradutor receberia instrução inválida).
        sb.append("    addi t3, t0, ").append(8 * n - 8 * ns - 16).append("\n");
        sb.append("    li t1, -16\n");
        sb.append("    and t3, t3, t1\n");
        sb.append("    mv sp, t3\n");
        // 3) derramados na ordem formal (arg0 → 0(sp) — o 1º stack-arg da C);
        //    valor cru (8 bytes) passa direto p/ o slot da C, float incluso.
        int k = 0;
        for (int i = 0; i < n; i++) {
            char c = cls[i];
            boolean floatC = isFloatClass(c);
            if (!(floatC ? ord[i] >= 8 : ord[i] >= 8)) continue;
            sb.append("    ld t2, ").append(8 * (n - 1 - i)).append("(t0)\n");
            if (c == 'S') {
                String lbl = ".Lffis" + seq + "_" + i;
                sb.append("    beqz t2, ").append(lbl).append("\n");
                sb.append("    addi t2, t2, 24\n");
                sb.append(lbl).append(":\n");
            }
            sb.append("    sd t2, ").append(8 * k++).append("(sp)\n");
        }
        // 4) ponto de restauração (E+8n) salvo ACIMA dos args da C: a callee
        //    toca só [< sp, +8ns); a chamada devolve sp = A (ABI) — o slot é
        //    lido com sp ainda em A.
        sb.append("    addi t2, t0, ").append(8 * n).append("\n");
        sb.append("    sd t2, ").append(8 * ns + 8).append("(sp)\n");
        // 5) call direto (PLT gerado pelo ld; §61: resolve no exec sem dlopen)
        sb.append("    call ").append(symbolOf(kc)).append("\n");
        sb.append("    ld sp, ").append(8 * ns + 8).append("(sp)\n");
        switch (ret) {
            case 'v': return;
            case 'i': sb.append("    sext.w a0, a0\n"); break; // canonicaliza o Int 32-bit
            case 'j': break;
            // RETORNO FP em fa0 (NÃO ft0!): medido 19/09 — o glibc riscv64
            // deste sysroot devolve double/float em fa0=f10 (a cadeia do
            // `exp` termina em fa0; o strtod do próprio runtime cross já lê
            // fa0 — testes verdes). No aarch64 o tradutor mapeia fa0→f0→d0,
            // que É o registro de retorno AAPCS64 — um texto, duas archs.
            case 'f': sb.append("    fmv.x.w a0, fa0\n"); break;
            case 'd': sb.append("    fmv.x.d a0, fa0\n"); break;
            case 'b': break; // C _Bool: 0/1 em a0 (zext pela ABI) — como o Kof guarda
            case 'S':
                sb.append("    call kof_ffi_from_cstr\n");
                break;
            default: return;
        }
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd a0, 0(sp)\n");
    }

    /** Helper char*→String (cópia UTF-8 crua na fronteira — o buffer C nunca é
     *  liberado; NULL → 0 = null Kof). Definido UMA vez por programa quando um
     *  extern retorna String; o PRÓPRIO call-site o referencia (a poda de
     *  runtime é por texto do programa — o helper vive no texto do programa). */
    static void emitX86CstrHelper(StringBuilder sb) {
        sb.append("""
                kof_ffi_from_cstr:
                    testq %rdi, %rdi
                    je .Lffc_null
                    pushq %r12
                    pushq %r13
                    pushq %r14
                    movq %rdi, %r12
                    xorq %rcx, %rcx
                .Lffc_scan:
                    cmpb $0, (%r12,%rcx)
                    je .Lffc_got
                    incq %rcx
                    jmp .Lffc_scan
                .Lffc_got:
                    movq %rcx, %r13
                    leal 25(%r13), %edi
                    call kof_alloc
                    movq %rax, %r14
                    movl $1, 0(%r14)
                    movl $0, 4(%r14)
                    movq $0, 8(%r14)
                    movl %r13d, 16(%r14)
                    movl $0, 20(%r14)
                    leaq 24(%r14), %rdi
                    movq %r12, %rsi
                    movl %r13d, %edx
                    call kof_memcpy
                    movb $0, 24(%r14,%r13)
                    movq %r14, %rax
                    popq %r14
                    popq %r13
                    popq %r12
                    ret
                .Lffc_null:
                    xorl %eax, %eax
                    ret
                """);
    }

    /** Helper char*→String no cross: strlen + kof_string_from_literal (copia
     *  UTF-8 + NUL-termina — o buffer C nunca e free'd; NULL → 0 = null Kof).
     *  Mesma forma x86; mnemonicos todos cobertos pelo tradutor aarch. */
    static void emitRiscvCstrHelper(StringBuilder sb) {
        sb.append("""
                kof_ffi_from_cstr:
                    beqz a0, .Lffc_null
                    addi sp, sp, -16
                    sd ra, 8(sp)
                    sd a0, 0(sp)
                    mv a1, a0
                    li a2, 0
                .Lffc_scan:
                    lbu a3, 0(a1)
                    beqz a3, .Lffc_got
                    addi a1, a1, 1
                    addi a2, a2, 1
                    j .Lffc_scan
                .Lffc_got:
                    ld a0, 0(sp)
                    mv a1, a2
                    call kof_string_from_literal
                    ld ra, 8(sp)
                    addi sp, sp, 16
                    ret
                .Lffc_null:
                    li a0, 0
                    ret
                """);
    }
}
