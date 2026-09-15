package dev.kof.compiler;

/**
 * Validação/homogeneidade de ESCRITAS de coleção (List.add/set, Map.put,
 * Set.add, literais listOf/mapOf/setOf) — §126 (opção ii) + §121/§143/§144.
 * Separado de CollectionCallLowerer p/ o gate ≤500: aqui mora a REJEIÇÃO
 * estática (SEM056) do que quebra de verdade; a CONVERSÃO do widening
 * abençoado fica em CompilerEmissionHelpers.coerceStoreWiden.
 */
public final class CollectionWrites {

    private CollectionWrites() {}

    /**
     * §126 (opção ii — decisão da mantenedora 11/09): um add/put cujo valor é
     * do TIPO ERRADO para o elemento/chave PINADA do container polui o heap —
     * uma futura varredura com tag String (elem String) chama kof_string_equals
     * sobre o candidato não-String e o trata como ponteiro → SIGSEGV no Native
     * (H3/H4; JVM tolera com HashMap heterogêneo). A correção é ESTÁTICA:
     * rejeitar em compile-time (SEM056), família SEM055/§122 — "Kof estático".
     *
     * Rejeita APENAS a família que quebra de verdade: (a) um lado String e o
     * outro NÃO (é isto que vira tag=1 sobre um inteiro); (b) §144 —
     * narrowing numérico (arg mais LARGO que o slot: Long→Int, Double→Int),
     * medido quebrado nos 3 (JVM VerifyError, Native TRUNCA em silêncio —
     * 5000000000→705052704 —, Script preserva). Casos apenas de "miss"
     * (non-String-pinned recebe String → raw cmpq, nunca deref → false/null
     * como o JVM), widening numérico (Int em Long — ABENÇOADO §126, vira
     * conversão no emit, não rejeição) e Unknown/TypeVariable (SG-008: pode
     * casar em runtime) PASSAM — não rejeitar o que não é perigoso. int↔bool
     * e char↔int NÃO entram (G2–G5 medidos consistentes nos 3).
     */
    public static boolean pollutesPinned(Type pinned, Type arg) {
        if (pinned == null || arg == null) return false;
        Type p = pinned instanceof Type.NullableType pn ? pn.inner() : pinned;
        Type a = arg instanceof Type.NullableType an ? an.inner() : arg;
        if (p instanceof Type.UnknownType || a instanceof Type.UnknownType) return false;
        if (p instanceof Type.TypeVariable || a instanceof Type.TypeVariable) return false;
        if (BuiltinTypes.isString(p) != BuiltinTypes.isString(a)) return true;
        if (p instanceof Type.PrimitiveType pp && a instanceof Type.PrimitiveType ap
                && isNumericFamily(pp) && isNumericFamily(ap)
                && TypeMetrics.primWidth(ap) > TypeMetrics.primWidth(pp)) {
            return true;
        }
        return false;
    }

    /** int/long/float/double (sem bool/char — famílias de width que casam). */
    private static boolean isNumericFamily(Type.PrimitiveType pt) {
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    /**
     * §126: tag de comparação do Native (1 = kof_string_equals, 0 = raw
     * cmpq). O equals de String só é SEGURO quando ambos os lados são
     * String conhecidos: o lado desconhecido pode ser um Int cru que o
     * kof_string_equals trataria como ponteiro → SIGSEGV (A1/E1/ST1).
     * Qualquer outro par cai no raw cmpq, que nunca deref e devolve o
     * miss silencioso (false/null) — exatamente o que o JVM faz com
     * tipos incompatíveis no HashMap/HashSet/ArrayList reais.
     */
    public static int stringTag(CompilationUnitNode unit, Type elemType,
                                java.util.List<Type> argTypes, int argIdx) {
        Type at = argIdx < argTypes.size() ? argTypes.get(argIdx) : null;
        if (at instanceof Type.NullableType nt) at = nt.inner();
        Type et = elemType instanceof Type.NullableType ent ? ent.inner() : elemType;
        boolean etKnown = et != null && !(et instanceof Type.UnknownType);
        boolean atKnown = at != null && !(at instanceof Type.UnknownType);
        if (etKnown && atKnown) {
            return isStringLike(et, unit) && isStringLike(at, unit) ? 1 : 0;
        }
        if (etKnown) return isStringLike(et, unit) ? 1 : 0;
        if (atKnown) return isStringLike(at, unit) ? 1 : 0;
        return 1;
    }

    /**
     * §150 / D-ENUM207: String é o único tipo comparado por CONTEÚDO no
     * Native (kof_string_equals). Uma constante de enum agora é uma INSTÂNCIA
     * real (singleton de {@code <clinit>}) — o raw {@code cmpq} por PONTEIRO
     * acerta (as constantes são o mesmo objeto em toda a execução), como o
     * JVM faz com identity-equals. Marcar enum como string-like fazia o
     * Native chamar {@code kof_string_equals} sobre o ponteiro do objeto →
     * SIGSEGV (exit 135).
     */
    private static boolean isStringLike(Type t, CompilationUnitNode unit) {
        return BuiltinTypes.isString(t);
    }

    public static String typeNameFor(Type t) {
        if (t instanceof Type.NullableType nt) return typeNameFor(nt.inner()) + "?";
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int" -> "Int"; case "long" -> "Long"; case "double" -> "Double";
                case "float" -> "Float"; case "bool" -> "Bool"; case "char" -> "Char";
                case "byte" -> "Byte"; case "short" -> "Short"; default -> pt.name();
            };
        }
        if (t instanceof Type.ClassType ct) return ct.name();
        if (t instanceof Type.ArrayType a) return typeNameFor(a.componentType()) + "[]";
        return String.valueOf(t);
    }
}
