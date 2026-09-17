package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * switch-EXPRESSÃO Java→Kof do {@code kof translate} (extraído de
 * {@link TranslateStatements} p/ o gate ≤500). A forma Kof é
 * {@code switch (x) { case L -> expr }} (idiom de
 * {@code training/idioms/control-flow.md}).
 */
final class TranslateSwitch {

    private TranslateSwitch() {}

    /** Fornece a expressão de um corpo de case (delegado ao parser host). */
    interface ExprParser {
        String parseExpr();
    }

    /**
     * Consome o switch-expressão a partir do {@code (} (o token {@code switch}
     * já foi consumido por {@code parsePrimary}). Multi-label
     * {@code case 1, 2 ->} expande em cases separados (Kof não aceita lista —
     * PARSE078); a forma colon+{@code yield} vira {@code case L -> expr}.
     * Corpo de case em BLOCO é gap honesto R6 (Kof exige UMA expressão por
     * case, PARSE094).
     */
    static String parse(Parser p, ExprParser expr) {
        p.expect("(");
        String subj = expr.parseExpr();
        p.expect(")");
        p.expect("{");
        StringBuilder sb = new StringBuilder("switch (" + subj + ") {");
        while (!p.at("}")) {
            List<String> labels = new ArrayList<>();
            boolean isDefault;
            if (p.at("case")) {
                p.next();
                labels.add(expr.parseExpr());
                while (p.at(",")) { p.next(); labels.add(expr.parseExpr()); }
                isDefault = false;
            } else if (p.at("default")) {
                p.next();
                isDefault = true;
            } else {
                throw new TranslateException(
                        "expected 'case'/'default' in switch-expression but found '" + p.peek().text + "'");
            }
            if (p.at(T.ARROW)) {
                p.next();
            } else {
                p.expect(":");
            }
            if (p.at("{")) {
                // `case L -> { ... yield v; }` — Kof switch-expr exige UMA
                // expressão por case (PARSE094) → gap honesto R6.
                throw new TranslateException(
                        "switch-expression with a case body in a BLOCK is not supported in Kof "
                        + "(PARSE094: requires one expression per case) — manual review");
            }
            if (p.at("yield")) { p.next(); } // forma colon Java
            String body = expr.parseExpr();
            p.expect(";");
            if (isDefault) {
                sb.append(" default -> ").append(body);
            } else {
                for (String label : labels) {
                    sb.append(" case ").append(label).append(" -> ").append(body);
                }
            }
        }
        p.expect("}");
        sb.append(" }");
        return sb.toString();
    }
}
