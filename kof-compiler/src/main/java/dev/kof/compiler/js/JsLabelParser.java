package dev.kof.compiler.js;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofOperation;

import dev.kof.compiler.LabelId;

/** Helper de parsing de labels (extraído, ≤500). */
final class JsLabelParser {

    private JsLabelParser() {}

/** True quando `label` marca o início de um loop: algum jump/cond-jump
 *  posterior salta para ele. Lookahead de label (ratchet §140: extraído de
 *  {@link JsControlFlowParser}). §266: a antiga heurística
 *  looksLikeContinueLabel saiu — a fronteira corpo/update do for agora vem do
 *  marcador estrutural KofContinueLabel (emitido no lowering), não de guess. */
static boolean isLoopStart(MethodCtx ctx, int[] pos, LabelId label) {
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj && kj.target().equals(label)) return true;
            if (op instanceof KofConditionalJump cj && cj.trueLabel().equals(label)) return true;
        }
        return false;
    }


}