package dev.kof.compiler.nat;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.Type;

/**
 * §335 (REFACTOR-500): tags e nomes de caixa do contrato de erasure nativa —
 * puros, compartilhados por x86 e cross (riscv/aarch). Extraído verbatim de
 * {@code NativeX86Calls} (634 ≥ 600 derrubava o gate CI do tip). Rule 7: o nome
 * descreve o conteúdo (tags de caixa), não o irmão. Comportamento idêntico.
 */
public final class NativeBoxTags {

    private NativeBoxTags() {}

    /** §107: tag de elemento/vetor de coleção → argumento do
     *  kof_{list,set,map}_to_string. 0=int/char/short/byte, 1=String, 2=Long,
     *  3=Bool, 4=Double, 5=Float, 6=desconhecido/record/aninhado (→ "?",
     *  face do §104b-ii). SEM056 garante homogeneidade, então UMA tag basta. */
    static int collectionTag(Type t) {
        Type e = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (e instanceof Type.PrimitiveType pt) {
            switch (pt.name()) {
                case "int", "char", "short", "byte": return 0;
                case "long": return 2;
                case "bool": return 3;
                case "float": return 5;
                default: return NativeTypeKinds.isDoubleType(pt) ? 4 : 6;
            }
        }
        if (BuiltinTypes.isString(e)) return 1;
        return 6;
    }

    // §284-map (18/09): VALOR de Map da familia Int/Long e caixa fisica
    // (contrato de escrita no CollectionCallLowerer, igual ao HashMap do
    // JVM) — tag 7 = "caixa numerica" no kof_elem_to_string: despacha por
    // MAGIC+tag via kof_box_to_string; nao-box passa cru (mapas antigos
    // emitidos raw por outras rotas continuam imprimindo certo).
    static int mapValueTag(Type t) {
        Type e = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (e instanceof Type.PrimitiveType pt && unboxFn(pt.name()) != null) return 7;
        return collectionTag(t);
    }

    /** §284: funcao de box por tipo primitivo (tags da tabela de colecao). */
    static String boxFn(String primName) {
        switch (primName) {
            case "int", "char", "short", "byte": return "kof_box_int";
            case "long": return "kof_box_long";
            case "bool", "boolean": return "kof_box_bool";
            case "double": return "kof_box_double";
            case "float": return "kof_box_float";
            default: return null;
        }
    }

    /** §284: funcao de unbox por tipo esperado (v1: inteiros; demais = passthrough). */
    static String unboxFn(String primName) {
        switch (primName) {
            case "int", "char", "short", "byte": return "kof_unbox_int";
            // §284-map: Long aceita caixa Int OU Long (Number.longValue)
            case "long": return "kof_unbox_long";
            default: return null;
        }
    }

    /** §284-map: variante soft (consumidor de `Int?`) — cru passa cru. */
    static String unboxSoftFn(String primName) {
        switch (primName) {
            case "int", "char", "short", "byte": return "kof_unbox_int_soft";
            case "long": return "kof_unbox_long_soft";
            default: return null;
        }
    }

    /** §284-map: receiver de `.equals` que no native e a caixa do slot. */
    static boolean isBoxedNumericReceiver(Type t) {
        Type u = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (!(u instanceof Type.ClassType ct)) return false;
        String n = ct.name();
        return switch (n) {
            case "Integer", "java/lang/Integer", "Long", "java/lang/Long",
                 "Character", "java/lang/Character", "Short", "java/lang/Short",
                 "Byte", "java/lang/Byte" -> true;
            default -> false;
        };
    }
}
