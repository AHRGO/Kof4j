package dev.kof.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * §251 — um tipo DECLARADO (retorno, parâmetro, campo, componente de record,
 * campo de entity) nunca era validado: um nome inexistente compilava em
 * silêncio e o descritor JVM referenciava um tipo que não existe — a classe
 * nem carregava (`NoClassDefFoundError`); para campo/local o valor era
 * simplesmente ignorado. R6 (nunca silencioso).
 *
 * <p>O predicado compartilhado é {@link MemberResolver#declaredTypeUnresolved},
 * que isenta builtins, coleções nuas, {@code kof.ui}/{@code kof.media} (§179),
 * enums, classes/records/interfaces do módulo, tipos externos via
 * {@code --classpath}, imports simples e os TYPE-PARAMS do escopo (classe/record/
 * função genérica — `class Box&lt;T&gt; { T value }`). A face LOCAL
 * ({@code VarDeclStmt}) é fechada no {@code StatementAnalyzer} (§249).
 *
 * <p>DEDICADO (regra 7) para manter os arquivos quentes
 * ({@code SemanticAnalyzer}) longe do limite de 500 linhas.
 */
final class DeclaredTypeChecker {

    private DeclaredTypeChecker() {}

    static void check(SemanticAnalyzer sa) {
        DiagnosticCollector dc = sa.diagnostics();
        if (dc == null || sa.unit() == null) return;
        for (AstNode decl : sa.unit().declarations()) {
            switch (decl) {
                case FunctionDeclarationNode f -> {
                    Set<String> tps = new HashSet<>(f.typeParameters());
                    report(dc, sa, f.returnType(), tps,
                            "return type of function '" + f.name() + "'");
                    for (FormalParameterNode p : f.parameters()) {
                        report(dc, sa, p.type(), tps,
                                "parameter '" + p.name() + "' of function '" + f.name() + "'");
                    }
                }
                case ClassDeclarationNode c -> checkMembers(sa, dc, c.typeParameters(), c.members());
                case RecordDeclarationNode r -> {
                    Set<String> tps = new HashSet<>(r.typeParameters());
                    for (RecordComponentNode comp : r.components()) {
                        report(dc, sa, comp.type(), tps,
                                "component '" + comp.name() + "' of record '" + r.name() + "'");
                    }
                    checkMembers(sa, dc, r.typeParameters(), r.members());
                }
                case InterfaceDeclarationNode i -> checkMembers(sa, dc, i.typeParameters(), i.members());
                case EntityDeclarationNode e -> {
                    for (EntityFieldNode f : e.fields()) {
                        report(dc, sa, f.type(), Set.of(),
                                "field '" + f.name() + "' of entity '" + e.name() + "'");
                    }
                }
                default -> { }
            }
        }
    }

    private static void checkMembers(SemanticAnalyzer sa, DiagnosticCollector dc,
                                     List<String> classTypeParams,
                                     List<? extends AstNode> members) {
        Set<String> tps = new HashSet<>(classTypeParams);
        for (AstNode member : members) {
            switch (member) {
                case FieldDeclarationNode field -> report(dc, sa, field.type(), tps,
                        "field '" + field.name() + "'");
                case MethodDeclarationNode method -> {
                    report(dc, sa, method.returnType(), tps,
                            "return type of method '" + method.name() + "'");
                    for (FormalParameterNode p : method.parameters()) {
                        report(dc, sa, p.type(), tps,
                                "parameter '" + p.name() + "' of method '" + method.name() + "'");
                    }
                }
                case ConstructorDeclarationNode ctor -> {
                    for (FormalParameterNode p : ctor.parameters()) {
                        report(dc, sa, p.type(), tps,
                                "parameter '" + p.name() + "' of constructor '" + ctor.name() + "'");
                    }
                }
                default -> { }
            }
        }
    }

    private static void report(DiagnosticCollector dc, SemanticAnalyzer sa, String declType,
                               Set<String> typeParams, String where) {
        if (declType == null) return;
        if (MemberResolver.declaredTypeUnresolved(sa, declType, typeParams)) {
            dc.error("", 0, 0, 0,
                    "Undefined variable or type: '" + declType.trim() + "' in " + where
                            + " — declare the type or fix the name (R6: undefined declared types"
                            + " must not compile)",
                    "SEM011");
        }
    }
}
