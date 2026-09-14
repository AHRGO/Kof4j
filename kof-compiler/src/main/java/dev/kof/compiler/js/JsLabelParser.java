package dev.kof.compiler.js;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryStart;

import dev.kof.compiler.LabelId;

/** Helper de parsing de labels (extraído, ≤500). */
final class JsLabelParser {

    private JsLabelParser() {}

/** True quando `label` marca o início de um loop: algum jump/cond-jump
 *  posterior salta para ele. Lookahead de label — mesma família de
 *  {@link #looksLikeContinueLabel} (ratchet §140: extraído de
 *  {@link JsControlFlowParser}). */
static boolean isLoopStart(MethodCtx ctx, int[] pos, LabelId label) {
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj && kj.target().equals(label)) return true;
            if (op instanceof KofConditionalJump cj && cj.trueLabel().equals(label)) return true;
        }
        return false;
    }

static boolean looksLikeContinueLabel(MethodCtx ctx, int[] pos, LabelId label) {
        LoopCtx loop = ctx.currentLoop();
        if (loop == null || label.equals(loop.start) || label.equals(loop.end)) return false;
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj) {
                return kj.target().equals(loop.start);
            }
            if (op instanceof KofLabel || op instanceof KofConditionalJump
                    || op instanceof KofTryStart || op instanceof KofCatchStart
                    || op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow) {
                return false;
            }
        }
        return false;
    }

}