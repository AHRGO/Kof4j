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
                // Sobe a cadeia de superclasses de ct1 procurando ancestral comum a ct2
                String cur = ct1.name();
                int hops = 0;
                while (cur != null && !"Object".equals(cur) && hops++ < 32) {
                    SymbolTable.ClassSymbol cs = sa.getClass(cur);
                    if (cs == null) break;
                    String sup = cs.superClass();
                    if (sup == null || sup.isEmpty() || "Object".equals(sup)) break;
                    Type supType = new Type.ClassType("", sup, java.util.List.of());
                    if (TypeChecker.isAssignable(sa, ct2, supType)) return supType;
                    cur = sup;
                }
            }
            return new Type.ClassType("java.lang", "Object", java.util.List.of());
        }
        return new Type.ClassType("java.lang", "Object", java.util.List.of());
    }
}