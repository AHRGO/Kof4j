package dev.kof.compiler.js;

import dev.kof.compiler.KofComparison;
import dev.kof.compiler.Type;

/**
 * Mapeamento de comparação do IR Kof para o IR JS (ratchet §140: extraído de
 * {@link JsControlFlowParser}, que estourava as 500 linhas). Função pura:
 * KofComparison + operandos → JsIr binário, com as duas regras de paridade
 * (truthiness `!!` para Bool, `NE 0` → teste de verdade direto).
 */
final class JsComparisons {

    private JsComparisons() {}

    static JsIr.JsExpression comparisonExpr(KofComparison comp, JsIr.JsExpression left,
                                            JsIr.JsExpression right, Type operandType) {
        if (comp == KofComparison.NE && right instanceof JsIr.JsNumber n && "0".equals(n.text())) {
            // boolean conditions: (cond, 0) CJump(NE) — truthiness in JS
            return left;
        }
        // §93 paridade: Bool no JS pode chegar como 1/0 (stdlib funcs, instanceof)
        // ou true/false (literais). === cru faz 1===true ser false. Normaliza
        // os dois lados com !! para truthiness booleana (JVM/Native usam Z real).
        // Dispara tanto por tipo (operandType bool) quanto por literal (==true/false),
        // porque `if (boolExpr == true)` colapsa operandType p/ INT no lowerer.
        if ((comp == KofComparison.EQ || comp == KofComparison.NE)
                && (JsTypeMapper.isBoolOperand(operandType)
                    || JsTypeMapper.isBoolLiteral(left) || JsTypeMapper.isBoolLiteral(right))) {
            left = new JsIr.JsUnary("!!", left);
            right = new JsIr.JsUnary("!!", right);
        }
        return switch (comp) {
            case EQ -> new JsIr.JsBinary(left, "===", right);
            case NE -> new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
        };
    }
}
