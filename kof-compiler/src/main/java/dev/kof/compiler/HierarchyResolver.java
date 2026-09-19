package dev.kof.compiler;

/**
 * Resolução de hierarquia de classes via SemanticAnalyzer (helpers do
 * CompilerDriver). Puro — recebe o analyzer por parâmetro.
 */
public final class HierarchyResolver {

    private HierarchyResolver() {}

    static SymbolTable.Symbol resolveFromSemantic(String name, SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return null;
        for (var entry : semanticAnalyzer.allClasses().entrySet()) {
            SymbolTable.ClassSymbol cs = entry.getValue();
            SymbolTable.Symbol s = cs.members().resolve(name);
            if (s != null) return s;
        }
        return null;
    }

    static SymbolTable.Symbol resolveFieldInHierarchy(String className, String fieldName,
                                                      SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return null;
        return MemberResolver.resolveFieldInHierarchy(semanticAnalyzer, className, fieldName);
    }

    static String findSuperClass(String internalName, SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return null;
        String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
        SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simpleName);
        if (cs == null) return null;
        String superName = cs.superClass();
        if (superName == null || superName.isEmpty() || "Object".equals(superName)) return null;
        if (!superName.contains("/")) {
            SymbolTable.ClassSymbol superCs = semanticAnalyzer.getClass(superName);
            if (superCs != null) return superCs.internalName();
        }
        return superName;
    }

    /**
     * True quando a cadeia de superclasses a partir de internalName é
     * inteiramente conhecida pelo SemanticAnalyzer (nenhuma classe externa
     * no caminho). Só nesse caso "método não resolvido" prova inexistência.
     */
    static boolean hierarchyFullyKnown(String internalName, SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer == null) return false;
        String cur = internalName;
        int hops = 0;
        while (cur != null && !"java/lang/Object".equals(cur) && hops++ < 32) {
            String simple = cur.substring(cur.lastIndexOf('/') + 1);
            SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simple);
            if (cs == null) return false;
            String sup = cs.superClass();
            if (sup == null || sup.isEmpty() || "Object".equals(sup)) return true;
            if (sup.contains(".")) {
                cur = sup.replace('.', '/');
            } else {
                SymbolTable.ClassSymbol supCs = semanticAnalyzer.getClass(sup);
                cur = supCs != null ? supCs.internalName() : sup;
            }
        }
        return true;
    }

    static String superSimpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    /**
     * Calcula o tipo comum mais específico (LCA) entre dois tipos para if-expression / branches.
     */
    static Type commonSupertype(SemanticAnalyzer sa, Type t1, Type t2) {
        if (t1 == null || t1 instanceof Type.UnknownType) return t2;
        if (t2 == null || t2 instanceof Type.UnknownType) return t1;
        if (t1.equals(t2)) return t1;
        if (t1 instanceof Type.ClassType ct1 && t2 instanceof Type.ClassType ct2) {
            if (sa != null) {
                if (TypeChecker.isAssignable(sa, ct1, ct2)) return ct2;
                if (TypeChecker.isAssignable(sa, ct2, ct1)) return ct1;
                // #360: o ancestral comum pode ser uma INTERFACE — a cadeia
                // antiga só subia SUPERCLASSES de ct1 e perdia o caso
                // Dog/Cat->Animal (interfaces). Fecha o fecho (supers +
                // interfaces, BFS = do mais específico) de cada lado e devolve
                // o primeiro ancestral que o OUTRO lado também satisfaz.
                Type a = firstCommonAncestor(sa, ct1, ct2);
                if (a != null) return a;
                Type b = firstCommonAncestor(sa, ct2, ct1);
                if (b != null) return b;
            }
        }
        return new Type.ClassType("java.lang", "Object", java.util.List.of());
    }

    /** #360 — BFS pelo fecho de `base` (sem o próprio base); primeiro nó ao
     *  qual `other` é atribuível vence. Null = sem ancestral comum nomeado. */
    private static Type firstCommonAncestor(SemanticAnalyzer sa,
            Type.ClassType base, Type.ClassType other) {
        String from = base.name();
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        visited.add(from);
        SymbolTable.ClassSymbol start = sa.getClass(from);
        if (start == null) return null;
        if (start.superClass() != null && !"Object".equals(start.superClass())) {
            queue.add(stripGenerics(start.superClass()));
        }
        for (String iface : start.interfaces()) queue.add(stripGenerics(iface));
        int hops = 0;
        while (!queue.isEmpty() && hops++ < 64) {
            String cur = queue.poll();
            if (cur.isEmpty() || !visited.add(cur)) continue;
            Type curType = ancestorType(sa, cur);
            if (TypeChecker.isAssignable(sa, other, curType)) return curType;
            SymbolTable.ClassSymbol cs = sa.getClass(cur);
            if (cs != null) {
                if (cs.superClass() != null && !"Object".equals(cs.superClass())) {
                    queue.add(stripGenerics(cs.superClass()));
                }
                for (String iface : cs.interfaces()) queue.add(stripGenerics(iface));
            }
        }
        return null;
    }

    private static Type ancestorType(SemanticAnalyzer sa, String simpleName) {
        SymbolTable.ClassSymbol cs = sa.getClass(simpleName);
        return cs != null ? cs.type() : new Type.ClassType("", simpleName, java.util.List.of());
    }

    private static String stripGenerics(String declared) {
        int lt = declared.indexOf('<');
        return lt < 0 ? declared : declared.substring(0, lt).trim();
    }

    /**
     * #360 — widening só quando existe um ancestral comum REAL (interface ou
     * superclasse nomeada); sem ele (Dog+String) devolve o elemento atual —
     * mantém o first-wins de hoje, r1: nada que roda hoje deixa de rodar.
     */
    static Type widenToCommonSupertype(SemanticAnalyzer sa, Type.ClassType elem, Type.ClassType next) {
        if (elem.equals(next)) return elem;
        if (sa == null) return elem;
        Type c = commonSupertype(sa, elem, next);
        if (c instanceof Type.ClassType cc
                && !("java.lang".equals(cc.packageName()) && "Object".equals(cc.name()))) {
            return cc;
        }
        return elem;
    }
}