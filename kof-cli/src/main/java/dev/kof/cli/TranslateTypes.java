package dev.kof.cli;

/**
 * Helpers estáticos de tipo/modificador Java→Kof do {@code kof translate}
 * (extraídos de {@link TranslateExpr} p/ o gate ≤500).
 */
final class TranslateTypes {

    private TranslateTypes() {}

    static boolean isModifier(String s) {
        return switch (s) {
            case "public", "private", "protected", "static", "final",
                 "abstract", "synchronized", "native", "transient", "volatile",
                 "default" -> true;
            default -> false;
        };
    }

    static boolean isTypekeyword(String s) {
        return switch (s) {
            case "int", "long", "float", "double", "boolean", "char", "byte",
                 "short", "void", "String" -> true;
            default -> false;
        };
    }

    static boolean isPrimitiveOrType(String s) {
        return isTypekeyword(s) || (!isKeyword(s) && Character.isUpperCase(s.charAt(0)));
    }

    static boolean isKeyword(String s) {
        return TranslateLexer.KEYWORDS.contains(s);
    }

    static String kofType(String javaType) {
        return switch (javaType) {
            case "int", "Integer" -> "Int";
            case "long", "Long" -> "Long";
            case "float", "Float" -> "Float";
            case "double", "Double" -> "Double";
            case "boolean", "Boolean" -> "Bool";
            case "char", "Character" -> "Char";
            case "byte", "Byte" -> "Byte";
            case "short", "Short" -> "Short";
            case "void" -> "void";
            default -> javaType;
        };
    }
}
