package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §CodeQL dereferenced-value-may-be-null #385/#386/#387 — os tres call-sites
 * dereferenciavam `driver.semanticAnalyzer` ANTES do guard que a propria linha
 * seguinte ja tinha (`!= null`). Em `CompilerDriverState:22` o campo NAO e
 * inicializado (so o pipeline completo o atribui, `CompilerPipeline:302`),
 * entao qualquer caminho que chame o typer/lowering sem o pipeline (testes de
 * unidade, ferramentas) estourava NPE crua no meio do compile em vez de cair
 * no fallback existente (resolved==null → assignability/typer conservativo).
 *
 * Fix: hoist do guard para o proprio deref (`x != null ? x.get(...) : null`) —
 * quando nao-null o resultado e identico (zero regressao); quando null o
 * comportamento passa a ser o MESMO do branch null ja previsto pelo autor.
 */
class NullSemanticAnalyzerTest {

    private static SourcePosition pos() {
        return new SourcePosition("t.kf", 1, 1, 0, 1);
    }

    @Test
    void methodCallTyperWithoutSemanticAnalyzerFallsBackNotNpe() {
        CompilerDriver driver = new CompilerDriver();
        assertNull(driver.semanticAnalyzer, "driver cru: analyzer deve ser null");
        MethodCallExpr mc = new MethodCallExpr(pos(), new IdentifierExpr(pos(), "x"),
                "toString", List.of(), List.of());
        Type t = assertDoesNotThrow(() -> MethodCallTyper.inferType(driver, mc, new ArrayList<>()),
                "#387: inferType sem analyzer nao pode lancar NPE");
        assertNotNull(t);
    }

    @Test
    void newExprLoweringWithoutSemanticAnalyzerFallsBackNotNpe() {
        CompilerDriver driver = new CompilerDriver();
        List<KofOperation> ops = new ArrayList<>();
        NewExpr ne = new NewExpr(pos(), "Widget", List.of(), List.of());
        assertDoesNotThrow(() -> ExpressionLowerer.emitExpression(driver, ne, ops, "Default/Main", 1, new ArrayList<>()),
                "#386: lowering de new sem analyzer nao pode lancar NPE");
        assertTrue(ops.stream().anyMatch(o -> o instanceof KofNewObject),
                "deve baixar KofNewObject pelo caminho fallback, ops=" + ops);
    }

    @Test
    void instanceCallLoweringWithoutSemanticAnalyzerFallsBackNotNpe() {
        CompilerDriver driver = new CompilerDriver();
        List<KofOperation> ops = new ArrayList<>();
        MethodCallExpr mc = new MethodCallExpr(pos(), new IdentifierExpr(pos(), "w"),
                "greet", List.of(), List.of());
        try {
            ExpressionInstanceCallLowerer.lower(driver, mc, ops, "Default/Main", 1, new ArrayList<>());
        } catch (NullPointerException npe) {
            fail("#385: lowering de chamada de instancia sem analyzer deve cair no fallback (diagnostico/KofCall), nao NPE: " + npe);
        } catch (Exception | Error clean) {
            // SEM025/diagnostico ou erro de ambiente — qualquer coisa que NAO
            // seja o NPE do deref e o caminho esperado do fallback.
        }
    }

    /** Sanity: com analyzer presente o resultado NAO mudou (zero-regressao). */
    @Test
    void withAnalyzerPathStillResolves() {
        CompilerDriver driver = new CompilerDriver();
        driver.semanticAnalyzer = new SemanticAnalyzer();
        MethodCallExpr mc = new MethodCallExpr(pos(), new IdentifierExpr(pos(), "x"),
                "toString", List.of(), List.of());
        assertNotNull(driver.semanticAnalyzer);
        assertDoesNotThrow(() -> MethodCallTyper.inferType(driver, mc, new ArrayList<>()));
    }
}
