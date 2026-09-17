package dev.kof.compiler;

/**
 * §266: marcador estrutural do label de `continue` de um loop com cláusula
 * update (`for(;;update)` e `for-in`). Emitido IMEDIATAMENTE ANTES do
 * `KofLabel(continueLabel)` pelo lowering (StatementLowerer), é um no-op em
 * todos os backends. O reconstrutor de CFG do KofJS (`JsControlFlowParser`)
 * O usa como fronteira corpo/update em vez da varredura-para-trás ambígua:
 * um `if` sem `else` no corpo do loop emite `Label(endX)` imediatamente antes
 * dos statements posteriores ao `if`, e sem marcador aquele `Label(endX)` era
 * confundido com o continue label — os statements do fim do corpo caíam na
 * cláusula update do `for(;…;…)` fora do escopo dos próprios `let`
 * (`ReferenceError` silencioso; vazio no browser). Precedente de op-marco:
 * {@link KofTryEnd}.
 */
public record KofContinueLabel(LabelId label, LabelId loopStart) implements KofOperation {
}
