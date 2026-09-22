package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.interop} (X6, {@code D-INTEROP-REFLECT}, 21/09/2026).
 *
 * <p>O host é escrito EM KOF ({@code dev/kof/interop-host.kf} no resource) e
 * injetado FLAT no {@code import kof.interop} EXPLÍCITO — mesmo mecanismo do
 * {@code kof.supervisor}/{@code kof.workflow}. Ele fornece o record
 * {@code Field(String name, String type)} usado pelo intrínseco de
 * compile-time {@code interop.schema(R)}.</p>
 *
 * <p>{@code interop.schema(R)} NÃO usa reflexão em runtime: o compilador já
 * conhece os componentes de {@code R}, então a chamada é dobrada no lowerer
 * para as mesmas operações que o {@code listOf(Field("n","t"), …)} equivalente
 * emitiria — sem código por backend, logo a saída é idêntica nos 4 alvos (não
 * há gap {@code REF001}). Só na fronteira de interop; nunca fundação da
 * linguagem.</p>
 */
final class CompilerInterop {

    static final String HOST_IMPORT = "kof.interop";
    static final String FIELD = "Field";

    private CompilerInterop() {}

    /** Tipo do record {@code Field} (host injetado flat, pacote vazio). */
    static Type fieldType() {
        return new Type.ClassType("", FIELD, List.of());
    }

    /** {@code List<Field>} — tipo de retorno do intrínseco {@code interop.schema(R)}. */
    static Type schemaType() {
        return new Type.ClassType("kof", "List", List.of(fieldType()));
    }

    /**
     * O host foi injetado? Reconhece o record {@code Field(name, type)}
     * fornecido pelo compilador pela forma (não pelo nome solto, para não
     * confundir com um {@code Field} do usuário).
     */
    static boolean hostPresent(CompilerDriver driver) {
        List<RecordComponentNode> c = recordComponents(driver, FIELD);
        return c != null && c.size() == 2
                && "name".equals(c.get(0).name()) && "type".equals(c.get(1).name());
    }

    /** O identificador é o namespace {@code interop} (isento de SEM011). */
    static boolean isInteropNamespace(String name) {
        return "interop".equals(name);
    }

    /** {@code interop.schema(R)} — intrinsic de compile-time (1 argumento). */
    static boolean isSchemaCall(MethodCallExpr mc) {
        return mc.receiver() instanceof IdentifierExpr rid
                && "interop".equals(rid.name())
                && "schema".equals(mc.methodName())
                && mc.arguments().size() == 1;
    }

    /**
     * Componentes (nome, tipo) de um {@code record} declarado na unidade
     * corrente — {@code null} se não houver record com esse nome (o chamador
     * diagnostica, nunca silencia: R6).
     */
    static List<RecordComponentNode> recordComponents(CompilerDriver driver, String name) {
        for (AstNode d : driver.currentUnit.declarations()) {
            if (d instanceof RecordDeclarationNode r && r.name().equals(name)) {
                return r.components();
            }
        }
        return null;
    }

    /**
     * Dobra {@code interop.schema(R)} para as mesmas ops do
     * {@code listOf(Field("n","t"), …)} equivalente — o compilador já conhece
     * os componentes de {@code R}, então não há reflexão em runtime nem código
     * por backend. Devolve o {@code localIdx} atualizado.
     */
    static int lowerSchema(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                           String owner, int localIdx, List<IRLocalVariable> locals) {
        if (!hostPresent(driver)) {
            error(driver, mc, "interop.schema requires `import kof.interop` (INTEROP001)");
            return localIdx;
        }
        ExpressionNode schemaArg = mc.arguments().get(0);
        if (!(schemaArg instanceof IdentifierExpr recId)) {
            error(driver, mc, "interop.schema expects a record type name as its argument (INTEROP001)");
            return localIdx;
        }
        List<RecordComponentNode> comps = recordComponents(driver, recId.name());
        if (comps == null) {
            error(driver, mc, "interop.schema: '" + recId.name()
                    + "' is not a record declared in this module (INTEROP001)");
            return localIdx;
        }
        List<ExpressionNode> fieldCalls = new ArrayList<>();
        for (RecordComponentNode c : comps) {
            fieldCalls.add(new MethodCallExpr(mc.position(), null, FIELD, List.of(),
                    List.of(new LiteralExpr(mc.position(), ConcreteLiteralKind.STRING, c.name()),
                            new LiteralExpr(mc.position(), ConcreteLiteralKind.STRING, c.type()))));
        }
        ExpressionNode listCall = new MethodCallExpr(mc.position(), null, "listOf",
                List.of(), fieldCalls);
        return ExpressionLowerer.emitExpression(driver, listCall, ops, owner, localIdx, locals);
    }

    private static void error(CompilerDriver driver, MethodCallExpr mc, String message) {
        if (driver.currentDiagnostics == null) return;
        SourcePosition pos = mc.position();
        driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0, message, "INTEROP001");
    }

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuário definiu o próprio Field: não injeta (o nome colidindo é sinal,
        // não silêncio — mesmo critério do supervisor).
        boolean collision = unit.declarations().stream()
                .anyMatch(d -> d instanceof TypeDeclarationNode t && FIELD.equals(t.name()));
        if (collision) return unit;
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/interop-host.kf")) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "interop host resource /dev/kof/interop-host.kf missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "interop-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "interop-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "interop host did not parse", "PKG003");
                return null;
            }
            List<String> imports = new ArrayList<>();
            for (String imp : unit.imports()) {
                String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
                if (HOST_IMPORT.equals(base)) continue; // virtual — resolvido aqui
                if (!imports.contains(imp)) imports.add(imp);
            }
            List<AstNode> decls = new ArrayList<>(unit.declarations());
            for (AstNode d : hostUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
            return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "interop host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
