package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * F2b (D-DB-GAPS, 20/09): emissor do resolver {@code kof_orm_ctors} — a
 * ponte className → (vtable, typeId, totalSize) que {@code kof_orm_find}
 * ({@code RuntimeOrm5}) precisa para CONSTRUIR o record no runtime: typeId
 * e vtable são constantes do programa, conhecidas só aqui (o vtable é o
 * rótulo {@code sanitizeName(clazz.name()) + "_vtable"} que o
 * {@code NativeOpHelpers.emitNewObject} já usa para {@code new}).
 *
 * <p>O backend coleta os className das chamadas {@code kof_orm_find} no scan
 * de ops e emite uma entrada por entidade: comparação de comprimento +
 * byte a byte do className ({@code rdi=body, esi=len}) e, no match, retorna
 * {@code rax=vtable*, rdx=typeId, rcx=totalSize}; sem match, {@code rax=0}
 * (o runtime lança ORM001 honesto — não acontece por construção).
 */
final class NativeOrmCtors {

    private NativeOrmCtors() {}

    static void emit(NativeBackend nb, StringBuilder sb, Set<String> classNames) {
        sb.append("            # ---- kof_orm_ctors: className -> (vtab,tid,size) ---\n");
        sb.append("            .globl kof_orm_ctors\n");
        sb.append("            .type kof_orm_ctors, @function\n");
        sb.append("        kof_orm_ctors:\n");
        int n = 0;
        for (String cn : classNames) {
            IRClass clazz = findClass(nb, cn);
            if (clazz == null) continue;
            int typeId = clazz.typeId();
            int totalSize = nb.getLayout(clazz).totalSize();
            String vtab = nb.sanitizeName(clazz.name()) + "_vtable";
            byte[] bytes = cn.getBytes(StandardCharsets.UTF_8);
            sb.append("            cmpl $").append(bytes.length).append(", %esi\n");
            sb.append("            jne .Lormc_n").append(n).append("\n");
            sb.append("            movl %esi, %edx\n");
            sb.append("            leaq .Lormc_b").append(n).append("(%rip), %rsi\n");
            sb.append("            xorl %ecx, %ecx\n");
            sb.append("        .Lormc_c").append(n).append(":\n");
            sb.append("            cmpl %edx, %ecx\n");
            sb.append("            jge .Lormc_ce").append(n).append("\n");
            sb.append("            movzbl (%rdi,%rcx), %r8d\n");
            sb.append("            movzbl (%rsi,%rcx), %r9d\n");
            sb.append("            cmpl %r9d, %r8d\n");
            sb.append("            jne .Lormc_n").append(n).append("\n");
            sb.append("            incl %ecx\n");
            sb.append("            jmp .Lormc_c").append(n).append("\n");
            sb.append("        .Lormc_ce").append(n).append(":\n");
            sb.append("            cmpb $0, (%rdi,%rcx)\n");
            sb.append("            jne .Lormc_n").append(n).append("\n");
            sb.append("            leaq ").append(vtab).append("(%rip), %rax\n");
            sb.append("            movl $").append(typeId).append(", %edx\n");
            sb.append("            movl $").append(totalSize).append(", %ecx\n");
            sb.append("            ret\n");
            sb.append("        .Lormc_n").append(n).append(":\n");
            sb.append("        .Lormc_b").append(n).append(":\n");
            sb.append("            .ascii \"");
            for (byte b : bytes) {
                char c = (char) (b & 0xFF);
                if (c == '"' || c == '\\') sb.append('\\');
                sb.append(c);
            }
            sb.append("\"\n");
            n++;
        }
        sb.append("            xorl %eax, %eax\n");
        sb.append("            ret\n");
    }

    /** Mesma regra de casamento de {@code NativeOpHelpers.emitNewObject}. */
    private static IRClass findClass(NativeBackend nb, String className) {
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                    || className.endsWith("/" + clazz.name())
                    || className.equals(nb.sanitizeName(clazz.name()))) {
                return clazz;
            }
        }
        return null;
    }
}
