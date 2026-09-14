package dev.kof.compiler.nat;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.KofUnaryOp;
import dev.kof.compiler.Type;

/**
 * FASE 3 (REFACTOR-500): emissão x86_64 de aritmética (binária e unária).
 * Extraído verbatim de NativeBackend (emitBinary + emitUnary) — os dois
 * métodos só dependem de NativeTypeKinds (estático), zero estado do backend.
 */
public final class NativeX86Arith {

    private NativeX86Arith() {}

    static void emitBinary(StringBuilder sb, KofBinary kb) {
        Type opTy = kb.operandType();
        if (NativeTypeKinds.isFloatType(opTy)) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movd %ecx, %xmm1\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addss %xmm1, %xmm0\n");
                case SUB -> sb.append("    subss %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulss %xmm1, %xmm0\n");
                case DIV -> sb.append("    divss %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    // NaN => unordered => CF=1 PF=1 => be would be true, clear it
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movd %xmm0, %eax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    movl %eax, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (NativeTypeKinds.isDoubleType(opTy)) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            // §146 (12/09, #101): MOD Double caía no `default` abaixo e
            // devolvia o dividendo. fmod via kof_double_mod (SSE2, sem libm):
            // topo=b, abaixo=a — a pilha x86 entrega b em %rcx e a em %rax,
            // a convenção do helper é rdi=a, rsi=b.
            if (kb.op() == KofBinaryOp.MOD) {
                sb.append("    movq %rax, %rdi\n");
                sb.append("    movq %rcx, %rsi\n");
                sb.append("    call kof_double_mod\n");
                sb.append("    pushq %rax\n");
                return;
            }
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    movq %xmm0, %xmm0\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addsd %xmm1, %xmm0\n");
                case SUB -> sb.append("    subsd %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulsd %xmm1, %xmm0\n");
                case DIV -> sb.append("    divsd %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movq %xmm0, %rax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        sb.append("    popq %rcx\n");
        sb.append("    popq %rax\n");
        boolean int32 = NativeTypeKinds.isInt32Type(opTy);
        String suf = int32 ? "l" : "q";
        String a32 = int32 ? "e" : "r";
        switch (kb.op()) {
            case ADD -> sb.append("    add").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SUB -> sb.append("    sub").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case MUL -> sb.append("    imul").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case DIV -> {
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n");
                }
            }
            case MOD -> {
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n    movl %edx, %eax\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n    movq %rdx, %rax\n");
                }
            }
            case EQ -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    sete %al\n    movzbl %al, %eax\n");
            case NE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setne %al\n    movzbl %al, %eax\n");
            case LT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setl %al\n    movzbl %al, %eax\n");
            case LE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setle %al\n    movzbl %al, %eax\n");
            case GT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setg %al\n    movzbl %al, %eax\n");
            case GE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setge %al\n    movzbl %al, %eax\n");
            case AND -> sb.append("    and").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case OR -> sb.append("    or").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case XOR -> sb.append("    xor").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SHL -> sb.append("    shl").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case SHR -> sb.append("    sar").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case USHR -> sb.append("    shr").append(suf).append(" %cl, %").append(a32).append("ax\n");
        }
        sb.append("    pushq %rax\n");
    }

    static void emitUnary(StringBuilder sb, KofUnary ku) {
        if (ku.operandType() != null && NativeTypeKinds.isFloatType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movl $0x80000000, %ecx\n");
            sb.append("    movd %ecx, %xmm1\n");
            sb.append("    xorps %xmm1, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.operandType() != null && NativeTypeKinds.isDoubleType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movabs $0x8000000000000000, %rcx\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    xorpd %xmm1, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // primitive conversions
        if (ku.op() == KofUnaryOp.I2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %eax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.I2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %eax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %rax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %rax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.F2D) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvtss2sd %xmm0, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.D2F) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    cvtsd2ss %xmm0, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // §181 (13/09): D2I/F2I/D2L/F2L SATURANTES (JLS 5.1.3 — NaN => 0,
        // > MAX => MAX, < MIN => MIN). cvttsd2si cru devolvia "integer
        // indefinite" (INT_MIN) p/ NaN e fora de faixa = divergência do
        // JVM (regra 5). Guard: ucomisd (unordered => jp NaN) + clamp.
        if (ku.op() == KofUnaryOp.D2I) {
            emitSatConv(sb, false, true);
            return;
        }
        if (ku.op() == KofUnaryOp.F2I) {
            emitSatConv(sb, true, true);
            return;
        }
        if (ku.op() == KofUnaryOp.D2L) {
            emitSatConv(sb, false, false);
            return;
        }
        if (ku.op() == KofUnaryOp.F2L) {
            emitSatConv(sb, true, false);
            return;
        }
        if (ku.op() == KofUnaryOp.I2L) {
            sb.append("    popq %rax\n");
            sb.append("    movslq %eax, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        sb.append("    popq %rax\n");
        boolean int32u = NativeTypeKinds.isInt32Type(ku.operandType());
        String suf = int32u ? "l" : "q";
        String reg = int32u ? "%eax" : "%rax";
        if (ku.op() == KofUnaryOp.NEG) {
            sb.append("    neg").append(suf).append(" ").append(reg).append("\n");
        } else if (ku.op() == KofUnaryOp.NOT) {
            sb.append("    cmp").append(suf).append(" $0, ").append(reg).append("\n");
            sb.append("    sete %al\n");
            sb.append("    movzbl %al, %eax\n");
        }

        sb.append("    pushq %rax\n");
    }

    /**
     * §181 — conversão float/double -> Int/Long SATURANTE (JLS 5.1.3).
     * Entrada: valor na pilha (bits crus). NaN => 0; fora de faixa =>
     * saturado no limite; senão truncamento cru (cvttsd2si). O emit entra
     * no NativeMethodEmitter via labels locais da classe (padrão Kof: asm
     * inline, labels únicos por instância não são necessários pois o emit
     * é incondicional/linear — nenhum label colide: usa sufixo contador).
     * isFloat: entrada é Float (32 bits, movd) senão Double (movq).
     * toLong: resultado 64-bit (rax) senão 32-bit (eax).
     */
    private static java.util.concurrent.atomic.AtomicLong SAT_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private static void emitSatConv(StringBuilder sb, boolean isFloat, boolean toInt) {
        String sfx = Long.toString(SAT_SEQ.incrementAndGet());
        // NaN?: ucomisd/x — unordered seta ZF=PF=CF=1; jp pega PF
        sb.append("    popq %rax\n");
        if (isFloat) {
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvttss2si %xmm0, %").append(toInt ? "eax" : "rax").append("\n");
            if (toInt) {
                sb.append("    movd %eax, %xmm1\n");
            } else {
                sb.append("    movd %eax, %xmm1\n");
                sb.append("    cvtss2sd %xmm1, %xmm1\n");
            }
        } else {
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    cvttsd2si %xmm0, %").append(toInt ? "eax" : "rax").append("\n");
            sb.append("    movq %xmm0, %xmm1\n");
        }
        // ordem OBRIGATÓRIA: NaN PRIMEIRO (ucomisd com NaN seta CF=1 E PF=1 —
        // um jc depois do NaN-check iria saturar MIN em vez de dar 0; a
        // 1a tentativa caiu nessa armadilha e `NaN as Long` dava Long.MIN).
        sb.append("    ucomisd %xmm1, %xmm0\n");                 // self: unordered<=>NaN
        sb.append("    jp .Lsat_nan_").append(sfx).append("\n");
        sb.append("    movq $").append(toInt ? "2147483647" : "9223372036854775807")
          .append(", %rdx\n");
        sb.append("    movq %rdx, %xmm2\n");                     // +MAX
        sb.append("    ucomisd %xmm2, %xmm0\n");
        // acima/igual MAX (CF=0) => saturar MAX (JLS: >= MAX_VALUE é MAX)
        sb.append("    jnc .Lsat_hi_").append(sfx).append("\n");
        sb.append("    movq $").append(toInt ? "-2147483648" : "-9223372036854775808")
          .append(", %rdx\n");
        sb.append("    movq %rdx, %xmm2\n");                     // -MIN (bits)
        sb.append("    ucomisd %xmm2, %xmm0\n");
        // abaixo de MIN (CF=1) => saturar MIN
        sb.append("    jc .Lsat_lo_").append(sfx).append("\n");
        sb.append("    jmp .Lsat_done_").append(sfx).append("\n");
        sb.append(".Lsat_hi_").append(sfx).append(":\n");
        sb.append("    movq $").append(toInt ? "2147483647" : "9223372036854775807")
          .append(", %").append(toInt ? "rax" : "rax").append("\n");
        sb.append("    jmp .Lsat_done_").append(sfx).append("\n");
        sb.append(".Lsat_lo_").append(sfx).append(":\n");
        sb.append("    movq $").append(toInt ? "-2147483648" : "-9223372036854775808")
          .append(", %rax\n");
        sb.append("    jmp .Lsat_done_").append(sfx).append("\n");
        sb.append(".Lsat_nan_").append(sfx).append(":\n");
        sb.append("    xorl %eax, %eax\n");
        sb.append(".Lsat_done_").append(sfx).append(":\n");
        if (toInt) {
            // resultado 32-bit já em eax (movq rax p/ eax saturado ok)
            sb.append("    pushq %rax\n");
        } else {
            sb.append("    pushq %rax\n");
        }
    }
}
