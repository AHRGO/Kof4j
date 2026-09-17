package dev.kof.compiler;

/**
 * §267: marcador estrutural de um `if` de NÍVEL-STATEMENT (o caso
 * {@code StatementLowerer.IfStmt}). Emitido como o PRIMEIRO op do lowering do
 * `if`/`if-else`, ANTES da condição, e consumido pelo dispatcher de statements
 * do KofJS ({@code JsControlFlowParser.parseStatements}). No-op nos demais
 * backends. Precedente: {@link KofContinueLabel} (§266) e {@link KofTryEnd}.
 *
 * <p>Por que é preciso: o IR de um `if` de statement é ESTRUTURALMENTE IDÊNTICO
 * ao de uma if-expressão ({@code s = if(c) 3 else 4} — lowering {@code IfExpr}):
 * ambos viram {@code [cond], CJump, Label(true), ramo, Jump(end), Label(false),
 * ramo, Label(end)}. O reconstructor do KofJS tenta a dobra em expressão
 * ({@code tryParseIfExpr}) na mesma posição; quando acerta num `if` de
 * statement cujos ramos são atribuições, a ternária passa a engolir o
 * statement SEGUINTE (o `let` dele sobe p/ antes da ternária → leitura
 * obsoleta, valor errado silencioso). O marcador diz ao parser "isto é um
 * statement: NÃO dobre em expressão, parseie como bloco if/else".
 */
public record KofStatementIf(LabelId branchTrueLabel) implements KofOperation {
}
