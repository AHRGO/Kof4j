package dev.kof.compiler.js;

import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.LabelId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JsTryParser — reconhece a região try/catch/finally do IR e produz
 * JsIr.JsTry (REFACTOR-500 ratchet §140: extraído de
 * {@link JsControlFlowParser}, mesma responsabilidade separável de
 * JsIfThrowElse/JsLabelParser). DD-01/bug45/bug49 preservados verbatim; o
 * split do §266 só moveu o arquivo (zero mudança de comportamento).
 */
final class JsTryParser {

    private final JsControlFlowParser flow;
    private final JsMethodParser p;

    JsTryParser(JsControlFlowParser flow, JsMethodParser p) {
        this.flow = flow;
        this.p = p;
    }

JsIr.JsStatement parse(MethodCtx ctx, int[] pos) {
        KofTryStart ts = (KofTryStart) ctx.ops.get(pos[0]);
                pos[0]++;
        // DD-01 (bug 45): lookahead do label return-finally — é o alvo do
        // KofJump que aparece logo após o store #retVal no corpo do try.
        ctx.currentReturnFinallyLabel = null;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofStoreLocal sl
                    && "#retVal".equals(ctx.rawLocalNames.get(sl.index()))) {
                for (int j = i + 1; j < ctx.ops.size() && j <= i + 3; j++) {
                    if (ctx.ops.get(j) instanceof KofJump kj) { ctx.currentReturnFinallyLabel = kj.target(); break; }
                    if (ctx.ops.get(j) instanceof KofLabel) break;
                }
                break;
            }
        }
        List<JsIr.JsStatement> tryBody = flow.parseStatements(ctx, pos, Set.of(ts.endLabel()), new ArrayList<>());
        // DD-01: com return no corpo (sem catch), o body para no primeiro
        // region-exit (jump p/ returnFinally) e sobra o jump do fluxo normal
        // p/ o finally — consumir jumps soltos até o endLabel/TryEnd/CatchStart.
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel tryEnd
                && tryEnd.label().equals(ts.endLabel())) {
            // The end label may already have been consumed as a region exit
            // (e.g. when the try body ends with throw: the trailing jump is
            // unreachable and the optimizer drops it).
            pos[0]++;
        }
        List<JsIr.JsCatchClause> catches = new ArrayList<>();
        boolean hasFinally = false;
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofCatchStart cs) {
            if ("Throwable".equals(cs.exceptionType())) {
                // catch-all + rethrow emulates finally; JS finally is native.
                hasFinally = true;
                pos[0]++;
                if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump) {
                    pos[0]++;
                }
                break;
            }
            pos[0]++;
            String param = p.expr.localName(ctx, cs.localIndex());
            List<JsIr.JsStatement> catchBody = flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
            catches.add(new JsIr.JsCatchClause(param, catchBody));
        }
        if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofTryEnd)) {
            throw new IllegalStateException("KofJS: try expected KofTryEnd at " + pos[0]
                    + " of " + ctx.ops.size() + ": " + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "eof"));
        }
        pos[0]++;
        List<JsIr.JsStatement> finallyBody = List.of();
        // o label do finally é novo da região do try — nunca do loop. Só existe
        // quando o try tem finally (catch-all "Throwable"): um KofLabel que não
        // é o fim de um finally pertence ao try ANINHADO/outer (bug 49).
        if (hasFinally && pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel finallyStart
                && !ctx.isLoopLabel(finallyStart.label())) {
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            finallyBody = flow.parseStatements(ctx, pos, Set.of(), exits);
            // skip the rethrow machinery: Label(rethrow) ... Label(done)
            // DD-01: se há return-finally (return no corpo), o epílogo vem
            // ANTES do Label(done) — parar o skip nele e parsear o epílogo.
            LabelId done = exits.isEmpty() ? null : exits.get(exits.size() - 1);
            LabelId rf = ctx.currentReturnFinallyLabel;
            if (rf != null) {
                while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofLabel kl
                        && kl.label().equals(rf))) {
                    pos[0]++;
                }
            } else if (done != null) {
                while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofLabel kl
                        && kl.label().equals(done))) {
                    pos[0]++;
                }
                if (pos[0] < ctx.ops.size()) pos[0]++;
            } else if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
                // no-finally: the trailing empty label (done) ends the try
                pos[0]++;
            }
        } else if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel doneLabel
                && !ctx.isLoopLabel(doneLabel.label())
                && !ctx.isTryEndLabel(doneLabel.label())) {
            // sem finally: o label done (trailing) encerra o try — consome,
            // mas NÃO se for o endLabel de um try aninhado/outer (bug 49).
            pos[0]++;
        }
        if (!finallyBody.isEmpty() && finallyBody.get(finallyBody.size() - 1) instanceof JsIr.JsThrow) {
            // bug 45: o rethrow Kof (`throw _excTmp`) que abre o caminho de
            // exceção do finally é REDUNDANTE no try/finally nativo do JS (que
            // relança automaticamente). Pior: quando o try `return`s, o throw
            // de `_excTmp` (undefined) aborta o retorno → `undefined`. Removê-lo
            // faz o finally rodar e o retorno prevalecer (Java-correct).
            finallyBody = finallyBody.subList(0, finallyBody.size() - 1);
        }
        // DD-01 (bug 45): após o caminho de rethrow vem Label(returnFinally)
        // + finallyBody + (load #retVal + return). No JS o try/finally NATIVO
        // já executa o corpo do finally no caminho do return — o epílogo IR
        // repetiria o corpo (duplicado). Consumimos os ops e descartamos o
        // corpo, preservando SÓ o return final do valor.
        List<JsIr.JsStatement> returnFinally = new ArrayList<>();
        if (hasFinally && pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel rf
                && rf.label().equals(ctx.currentReturnFinallyLabel)) {
            pos[0]++;
            List<JsIr.JsStatement> epilogue = flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
            boolean returning = false;
            for (JsIr.JsStatement st : epilogue) {
                if (st instanceof JsIr.JsReturn jr) { returnFinally.add(jr); returning = true; }
            }
            if (!returning) returnFinally.addAll(epilogue);
            // o Label(done) do try encerra o epílogo — consumir (não é loop:
            // nenhum jump posterior aponta p/ ele depois do epílogo parseado)
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel dl
                    && !ctx.isLoopLabel(dl.label())) {
                pos[0]++;
            }
        }
        return new JsIr.JsTry(tryBody, catches, finallyBody, returnFinally);
    }

}
