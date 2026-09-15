package dev.kof.compiler;

import java.util.List;

/**
 * Resolução de membros e hierarquia extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Puro — recebe o estado necessário por parâmetro.
 */
public final class MemberResolver {

    private MemberResolver() {}

    /** BFS pela hierarquia (super + interfaces) em busca de um membro. */
    static SymbolTable.Symbol resolveInHierarchy(SemanticAnalyzer sa, String className, String memberName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = sa.getClass(current);
            if (cs == null) continue;
            SymbolTable.Symbol s = cs.members().resolve(memberName);
            if (s != null) return s;
            if (cs.superClass() != null && !"Object".equals(cs.superClass()) && !visited.contains(cs.superClass())) {
                visited.add(cs.superClass());
                queue.add(cs.superClass());
            }
            for (String iface : cs.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
        }
        return null;
    }

    /** BFS pela hierarquia buscando campo com prioridade sobre métodos de mesmo nome. */
    static SymbolTable.Symbol resolveFieldInHierarchy(SemanticAnalyzer sa, String className, String fieldName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = sa.getClass(current);
            if (cs == null) continue;
            SymbolTable.FieldSymbol fs = cs.members().resolveField(fieldName);
            if (fs != null) return fs;
            if (cs.superClass() != null && !"Object".equals(cs.superClass()) && !visited.contains(cs.superClass())) {
                visited.add(cs.superClass());
                queue.add(cs.superClass());
            }
            for (String iface : cs.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
        }
        return resolveInHierarchy(sa, className, fieldName);
    }

    static boolean isObjectMethod(String name, int argCount) {
        return switch (name) {
            case "hashCode", "toString", "getClass" -> argCount == 0;
            case "equals" -> argCount == 1;
            default -> false;
        };
    }

    static boolean isBuiltinTypeName(String name) {
        return switch (name) {
            case "String", "string", "Object", "Int", "int", "Long", "long",
                    "Bool", "bool", "boolean", "Boolean", "Char", "char",
                    "Byte", "byte", "Short", "short", "Float", "float",
                    "Double", "double", "void", "Void" -> true;
            default -> false;
        };
    }

    /**
     * Nome simples declarado em import vira tipo qualificado
     * ("import android.webkit.WebView" → ClassType("android.webkit","WebView")).
     * Sem isso, tipos de classes externas saem sem pacote e o descritor
     * JVM quebra.
     */
    static Type qualifyViaImports(CompilationUnitNode unit, String name) {
        return qualifyViaImports(unit, name, null);
    }

    /**
     * §134 residual: com um `ExternalClasspath` (--classpath/--deps), o
     * WILDCARD `import a.b.*` passa a qualificar o nome simples quando a
     * classe `a/b/<name>` REALMENTE existe num entry carregado. Sem o cp
     * (targets Native/JS — `externalClasspath == null`) o comportamento é
     * o antigo: wildcard nunca qualifica, cai em SEM011/PKG006. Aditivo:
     * só casa o que existe; nome inexistente segue o fluxo de erro normal.
     */
    static Type qualifyViaImports(CompilationUnitNode unit, String name,
                                  ExternalClasspath external) {
        if (name == null || name.contains(".") || name.contains("<") || name.endsWith("[]")) return null;
        if (unit == null) return null;
        for (String imp : unit.imports()) {
            if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                String pkg = imp.substring(0, imp.lastIndexOf('.'));
                return new Type.ClassType(pkg, name, List.of());
            }
        }
        if (external != null) {
            for (String imp : unit.imports()) {
                if (imp.endsWith(".*")) {
                    String pkg = imp.substring(0, imp.length() - 2);
                    if (external.knows(pkg.replace('.', '/') + "/" + name)) {
                        return new Type.ClassType(pkg, name, List.of());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Nomes qualificados ("android.os.Bundle") precisam do pacote separado
     * do nome simples — senão o descritor JVM sai com pontos
     * (Landroid.os.Bundle;) e a classe não carrega.
     */
    static Type qualifiedType(Type type) {
        if (type instanceof Type.ClassType ct && !ct.name().contains("<")
                && ct.packageName().isEmpty()) {
            int lastDot = ct.name().lastIndexOf('.');
            if (lastDot > 0) {
                return new Type.ClassType(ct.name().substring(0, lastDot),
                        ct.name().substring(lastDot + 1), ct.typeArguments());
            }
        }
        return type;
    }

    /** Resolve um nome de tipo no escopo (type param → import → qualificado). */
    static Type resolveType(SemanticAnalyzer sa, String name, SymbolTable scope) {
        // SG-012: param de lambda sem anotação chega como null — Unknown
        // (a inferência contextual decide; nunca Object silencioso)
        if (name == null) return Type.UnknownType.UNKNOWN;
        SymbolTable.Symbol sym = scope != null ? scope.resolve(name) : null;
        if (sym instanceof SymbolTable.TypeParameterSymbol) return sym.type();
        Type viaImports = qualifyViaImports(sa.unit(), name, sa.externalTypes());
        if (viaImports != null) return viaImports;
        // qualifyDeep: recursa nos type-arguments — `List<NodeUI>` com
        // `import com.dev.NodeUI` precisa do pacote no ARG (senão o receiver
        // do `.get()` fica ClassType("","NodeUI") e o checkcast sai sem pacote
        // → NoClassDefFoundError). Idempotente; não toca builtin/enum/nome local.
        // §179: qualifyDeep mapeia o builtin kof.ui/kof.media quando nada mais
        // resolve o nome (preservando shadowing por import/classe do módulo).
        return CompilerTypes.qualifyDeep(qualifiedType(Type.of(name)), sa.unit(), sa);
    }

    /**
     * §249: o nome simples declarado NÃO resolve para nenhum tipo conhecido?
     * Um tipo explícito de `VarDeclStmt` (`Foo x`, `s length`) caía em
     * {@code Type.of(name)} = {@code ClassType("", name)} sem diagnóstico algum
     * — o programa compilava e o statement virava lixo em silêncio (R6).
     * Espelha as isenções de SEM011 em {@code SemExpressionTyper} (case
     * IdentifierExpr): builtin, tipo UI/media declarado, coleção nua, classe/
     * record/enum do módulo, classe externa (--classpath), type-param do escopo
     * e import simples.
     * Conservador: só casa NOME SIMPLES (sem `.`/`<`/`?`/`[]`/`(`) — as formas
     * compostas (`List<Int>`, `Int[]`, `String?`, `(Int) -> Int`) são resolvidas
     * por {@code Type.of} e nunca caem aqui.
     */
    static boolean isUnresolvedSimpleType(SemanticAnalyzer sa, String name, SymbolTable scope) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains(".") || name.contains("<") || name.contains("?")
                || name.contains("[") || name.contains("(")) return false;
        if (isBuiltinTypeName(name)) return false;
        // `List xs = listOf(...)`: o Type.of só mapeia a forma parametrizada.
        if (BuiltinTypes.baseTypeName(name) != null) return false;
        // UI/media (`Label`, `ImageData`) — §179.
        if (CompilerTypes.builtinDeclaredType(name) != null) return false;
        if (BuiltinTypes.isEnumName(name)) return false;
        if (scope != null && scope.resolve(name) instanceof SymbolTable.TypeParameterSymbol) return false;
        if (sa.allClasses().containsKey(name)) return false;
        if (CompilerTypes.unitDeclaresType(sa.unit(), name)) return false;
        if (qualifyViaImports(sa.unit(), name, sa.externalTypes()) != null) return false;
        return true;
    }

    /** Constantes de um enum declarado na unit (vazio se não for enum). */
    static List<String> enumConstantsOf(CompilationUnitNode unit, String name) {
        if (name == null || unit == null) return List.of();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en && en.name().equals(name)) {
                return en.constants();
            }
        }
        return List.of();
    }

    /** Nome da constante de enum referenciada por uma expressão, ou null. */
    static String enumConstantOfExpr(CompilationUnitNode unit, ExpressionNode e) {
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid) {
            return enumConstantsOf(unit, rid.name()).contains(fa.fieldName()) ? fa.fieldName() : null;
        }
        if (e instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.STRING) {
            return l.value();
        }
        if (e instanceof IdentifierExpr ie) {
            // não-qualificado: Red quando algum enum declara Red
            if (unit != null) {
                for (AstNode d : unit.declarations()) {
                    if (d instanceof EnumDeclarationNode en && en.constants().contains(ie.name())) {
                        return ie.name();
                    }
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Nome do enum que declara a constante referenciada por {@code e}, ou null.
     * Complementa {@link #enumConstantOfExpr}: aquele devolve só a constante,
     * este o TIPO do enum — necessário para o tipo de `Color.RED` como
     * expressão (o exhaustiveness de switch-expr sobre enum depende dele).
     */
    static String enumNameOfConstant(CompilationUnitNode unit, ExpressionNode e) {
        if (unit == null) return null;
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid) {
            for (AstNode d : unit.declarations()) {
                if (d instanceof EnumDeclarationNode en && en.name().equals(rid.name())
                        && en.constants().contains(fa.fieldName())) {
                    return en.name();
                }
            }
        }
        return null;
    }

    static boolean isBooleanType(Type t) {
        if (t == Type.PrimitiveType.BOOL) return true;
        if (t instanceof Type.ClassType ct) {
            String n = ct.name();
            String p = ct.packageName();
            return ("Boolean".equals(n) || "Bool".equals(n)) && ("java.lang".equals(p) || p.isEmpty());
        }
        return false;
    }

    static boolean isBooleanExhaustive(List<SwitchExprCase> cases) {
        boolean hasTrue = false;
        boolean hasFalse = false;
        for (SwitchExprCase sc : cases) {
            if (sc.value() instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.BOOLEAN) {
                if ("true".equals(l.value())) hasTrue = true;
                if ("false".equals(l.value())) hasFalse = true;
            }
        }
        return hasTrue && hasFalse;
    }

    static void checkSwitchExprExhaustiveness(SemanticAnalyzer sa, SwitchExpr se, Type subjectType) {
        if (isBooleanType(subjectType)) {
            if (!isBooleanExhaustive(se.cases())) {
                sa.reportError(se, "switch expressão sobre Boolean não cobre todos os valores (true e false)", "SEM032");
            }
            return;
        }
        if (subjectType instanceof Type.ClassType sct && sct.packageName().isEmpty() && sa.unit() != null) {
            java.util.Set<String> covered = new java.util.HashSet<>();
            for (SwitchExprCase sc : se.cases()) {
                String cn = enumConstantOfExpr(sa.unit(), sc.value());
                if (cn != null) covered.add(cn);
            }
            List<String> constants = enumConstantsOf(sa.unit(), sct.name());
            List<String> missing = constants.stream().filter(c -> !covered.contains(c)).toList();
            if (!missing.isEmpty()) {
                sa.reportError(se, "switch expressão sobre '" + sct.name()
                        + "' não cobre: " + String.join(", ", missing)
                        + " (adicione default ou os casos faltantes)", "SEM032");
            }
        } else {
            sa.reportError(se, "switch expressão exige 'default' (ou exaustividade de enum)", "SEM032");
        }
    }

    /**
     * SG-015 (#256): classe concreta que estende classe abstrata deve implementar
     * todos os métodos abstratos herdados da cadeia de superclasses.
     */
    static void checkAbstractClassImplementation(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        if (sa.diagnostics() == null || cls.modifiers().contains("abstract")) return;
        String curSuper = cls.superClass();
        java.util.Set<String> checkedMethods = new java.util.HashSet<>();
        while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
            String simpleSuper = curSuper.contains("/") ? curSuper.substring(curSuper.lastIndexOf("/") + 1) : curSuper;
            if (simpleSuper.contains("<")) simpleSuper = simpleSuper.substring(0, simpleSuper.indexOf("<"));
            SymbolTable.ClassSymbol superCs = sa.allClasses().get(simpleSuper);
            if (superCs == null) break;
            for (java.util.Map.Entry<String, SymbolTable.Symbol> e : superCs.members().localSymbols().entrySet()) {
                if (!(e.getValue() instanceof SymbolTable.MethodSymbol am)) continue;
                if ((am.accessFlags() & AccessFlags.ABSTRACT) == 0) continue;
                // §242: a chave é a ASSINATURA (nome + tipos de parâmetro), não
                // a aridade — duas sobrecargas de mesma aridade mas tipos
                // diferentes são abstratos DISTINTOS. Sem isto, o dedup
                // colapsava-as e um `Int run(Int)` podia "satisfazer"
                // `abstract Int run(String)` (AbstractMethodError em runtime).
                String methodKey = am.name() + TopLevelOverload.sigTag(am.parameterTypes());
                if (!checkedMethods.add(methodKey)) continue;
                SymbolTable.Symbol local = resolveInHierarchy(sa, cls.name(), am.name());
                boolean implemented = false;
                if (local instanceof SymbolTable.MethodSymbol lm) {
                    implemented = satisfiesAbstract(lm, am);
                } else if (local instanceof SymbolTable.MethodSet set) {
                    for (SymbolTable.MethodSymbol lm : set.methods()) {
                        if (satisfiesAbstract(lm, am)) {
                            implemented = true;
                            break;
                        }
                    }
                }
                if (!implemented) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "class '" + cls.name() + "' does not implement abstract method '"
                                    + am.name() + "()' from superclass '" + superCs.name() + "'",
                            "SEM043");
                }
            }
            curSuper = superCs.superClass();
        }
    }

    /**
     * §242: um método concreto satisfaz um abstrato quando NÃO é abstrato e a
     * assinatura casa — mesma aridade E mesmos tipos de parâmetro (o que a JVM
     * exige para o override). Antes o check comparava só a ARIDADE: um
     * `Int run(Int)` "satisfazia" `abstract Int run(String)` e o
     * `AbstractMethodError` voltava em runtime.
     */
    private static boolean satisfiesAbstract(SymbolTable.MethodSymbol concrete,
                                             SymbolTable.MethodSymbol abstractMethod) {
        if ((concrete.accessFlags() & AccessFlags.ABSTRACT) != 0) return false;
        if (concrete.parameterTypes().size() != abstractMethod.parameterTypes().size()) return false;
        return TopLevelOverload.sigTag(concrete.parameterTypes())
                .equals(TopLevelOverload.sigTag(abstractMethod.parameterTypes()));
    }
}
