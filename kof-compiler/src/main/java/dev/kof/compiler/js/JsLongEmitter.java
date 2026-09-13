package dev.kof.compiler.js;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;

import java.util.List;

/**
 * Emissão de binários/unários de LONG no backend JS (§81/§167).
 *
 * <p>`Long` no JS é `BigInt`. Os helpers aqui concentram a promoção
 * Number→BigInt (`BigInt()` idempotente), o wrap de 64 bits do JVM
 * (`BigInt.asIntN(64, …)` — BigInt é ilimitado e sem ele `Long.MAX+1`
 * estoura), a máscara do deslocamento (`n & 63`) e o `>>>` (que BigInt não
 * tem: `BigInt.asUintN(64, x) >> n`).
 *
 * <p>Extraído de {@link JsCallEmitter} para manter a classe ≤500 linhas
 * (REFACTOR-500). Comportamento inalterado.
 */
final class JsLongEmitter {

    private JsLongEmitter() {}

    /** §81 (5b): binário de LONG sobre BigInt. DIV: BigInt / já trunca p/
     *  zero (JVM LIDIV; Math.trunc não aceita BigInt). EQ/NE: `==` loose JS
     *  (5n==5 é true — o === cru daria false misturando BigInt/Number, e o
     *  `==` de Kof é de conteúdo). */
    static JsIr.JsExpression longBinaryExpr(KofBinary kb, JsIr.JsExpression left, JsIr.JsExpression right) {
        // §167: o tipo do resultado de um shift é o do operando ESQUERDO
        // (JLS 15.19) — `int << long` dá int, não BigInt. Só aqui o lado
        // esquerdo é rebaixado p/ Number 32-bit; os demais ops usam BigInt
        // nos dois lados (o operandType já é LONG p/ `long & int`).
        if (isShift(kb.op()) && !JsTypeMapper.isLongType(kb.operandType())) {
            JsIr.JsExpression l = int32(left);
            JsIr.JsExpression r = toNumber32(right);
            return new JsIr.JsBinary(l, jsShiftOp(kb.op()), r);
        }
        JsIr.JsExpression l = longOperand(left), r = longOperand(right);
        return switch (kb.op()) {
            // §167: BigInt é ilimitado; o JVM faz wrap de 64 bits. Sem o
            // asIntN(64) o resultado estoura (`Long.MAX+1` dava 2^63 no JS vs
            // Long.MIN no JVM). DIV/MOD também: `Long.MIN / -1` estoura.
            case ADD -> wrap64(new JsIr.JsBinary(l, "+", r));
            case SUB -> wrap64(new JsIr.JsBinary(l, "-", r));
            case MUL -> wrap64(new JsIr.JsBinary(l, "*", r));
            case DIV -> wrap64(new JsIr.JsBinary(l, "/", r));
            case MOD -> wrap64(new JsIr.JsBinary(l, "%", r));
            case EQ -> new JsIr.JsBinary(l, "==", r);
            case NE -> new JsIr.JsBinary(l, "!=", r);
            case LT -> new JsIr.JsBinary(l, "<", r);
            case LE -> new JsIr.JsBinary(l, "<=", r);
            case GT -> new JsIr.JsBinary(l, ">", r);
            case GE -> new JsIr.JsBinary(l, ">=", r);
            case AND -> new JsIr.JsBinary(l, "&", r);
            case OR -> new JsIr.JsBinary(l, "|", r);
            case XOR -> new JsIr.JsBinary(l, "^", r);
            case SHL -> wrap64(shiftLong(l, r, "<<"));
            case SHR -> shiftLong(l, r, ">>");
            case USHR -> wrap64(shiftLongUnsigned(l, r));
            default -> new JsIr.JsBinary(l, "+", r);
        };
    }

    /** §167: reinterpreta o resultado BigInt como long de 64 bits com sinal
     *  (wrap do JVM). */
    static JsIr.JsExpression wrap64(JsIr.JsExpression e) {
        return new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("BigInt"), "asIntN"),
                List.of(new JsIr.JsNumber("64"), e));
    }

    static boolean isShift(KofBinaryOp op) {
        return op == KofBinaryOp.SHL || op == KofBinaryOp.SHR || op == KofBinaryOp.USHR;
    }

    static String jsShiftOp(KofBinaryOp op) {
        return switch (op) {
            case SHL -> "<<";
            case SHR -> ">>";
            default -> ">>>";
        };
    }

    /** §167: BigInt não tem `>>>`; o shift lógico de 64 bits é
     *  `BigInt.asUintN(64, x) >> n` (o `asUintN` zera o sinal e o `>>`
     *  aritmético sobre um valor já não-negativo casa com o JVM). */
    private static JsIr.JsExpression shiftLong(JsIr.JsExpression l, JsIr.JsExpression r, String op) {
        return new JsIr.JsBinary(l, op, shiftCount64(r));
    }

    private static JsIr.JsExpression shiftLongUnsigned(JsIr.JsExpression l, JsIr.JsExpression r) {
        JsIr.JsExpression u = new JsIr.JsCall(
                new JsIr.JsMember(new JsIr.JsIdentifier("BigInt"), "asUintN"),
                List.of(new JsIr.JsNumber("64"), l));
        return new JsIr.JsBinary(u, ">>", shiftCount64(r));
    }

    /** §167: o JVM mascara o deslocamento long em 0x3f (`n & 63`) e o int em
     *  0x1f; BigInt é ilimitado, então `1n << 70n` daria um valor errado.
     *  Normaliza o contador p/ BigInt e aplica a máscara. */
    private static JsIr.JsExpression shiftCount64(JsIr.JsExpression r) {
        JsIr.JsExpression big = longOperand(r);
        return new JsIr.JsBinary(big, "&", new JsIr.JsNumber("63n"));
    }

    /** §167: contador de shift int — `BigInt.asIntN(32, ...)` então Number;
     *  o JS já mascara o operador `<<`/`>>`/`>>>` em 0x1f. */
    static JsIr.JsExpression toNumber32(JsIr.JsExpression e) {
        return new JsIr.JsCall(new JsIr.JsIdentifier("Number"),
                List.of(new JsIr.JsCall(
                        new JsIr.JsMember(new JsIr.JsIdentifier("BigInt"), "asIntN"),
                        List.of(new JsIr.JsNumber("32"), longOperand(e)))));
    }

    static JsIr.JsExpression int32(JsIr.JsExpression e) {
        return new JsIr.JsBinary(e, "|", new JsIr.JsNumber("0"));
    }

    /** §81/§167: envolve o operando com BigInt() — literal Number cru
     *  (promoção Int->Long) OU variável Int-typed (Number) que chega sem
     *  promoção; BigInt é idempotente p/ BigInt puro, então envolver sempre é
     *  seguro e casa a promoção binária do JVM (misturar BigInt/Number lança
     *  `TypeError: Cannot mix BigInt and other types` — era o §167). */
    static JsIr.JsExpression longOperand(JsIr.JsExpression e) {
        if (e instanceof JsIr.JsNumber n && n.text().endsWith("n")) return e;
        return new JsIr.JsCall(new JsIr.JsIdentifier("BigInt"), List.of(e));
    }
}
