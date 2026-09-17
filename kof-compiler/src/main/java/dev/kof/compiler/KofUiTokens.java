package dev.kof.compiler;

/**
 * KofUiTokens — design-system tokens (Fase 10, docs/ui/architecture.md
 * §2.1 pilar 9): `Spacing`/`Radius`/`Border`/`Elevation`/`Typography` are
 * compile-time constant namespaces, folded by the same idiom as `Palette`
 * (FieldAccessExpr on an identifier receiver → {@code KofLoadLiteral}).
 *
 * Values are bare Ints in px (the D-UI-STYLE Q2 convention: a plain Int is
 * pixels). The scales follow the 8px grid (Material/Tailwind consensus):
 *
 *   Spacing.xs=4  sm=8  md=16  lg=24  xl=32
 *   Radius.none=0 sm=2  md=4  lg=8  full=9999
 *   Border.hairline=1 thin=2 medium=4 thick=8
 *   Elevation.none=0 sm=1 md=2 lg=3 xl=4
 *   Typography.xs=12 sm=14 md=16 lg=20 xl=24 hero=32
 *
 * Unknown members are a compile-time diagnostic (SEM076) — R6, no silent
 * 0 (unlike the existing `Palette.nope` hole, catalogued separately).
 * Method calls on a token namespace are SEM076 too: tokens are constants.
 */
public final class KofUiTokens {

    private KofUiTokens() {
    }

    static final String[] NAMESPACES = {"Spacing", "Radius", "Border", "Elevation", "Typography"};

    static boolean isTokenNamespace(String name) {
        for (String n : NAMESPACES) {
            if (n.equals(name)) return true;
        }
        return false;
    }

    /** Token value in px (Int), or null if the member does not exist. */
    static Integer tokenValue(String namespace, String member) {
        if (member == null) return null;
        return switch (namespace) {
            case "Spacing" -> switch (member) {
                case "xs" -> 4;
                case "sm" -> 8;
                case "md" -> 16;
                case "lg" -> 24;
                case "xl" -> 32;
                default -> null;
            };
            case "Radius" -> switch (member) {
                case "none" -> 0;
                case "sm" -> 2;
                case "md" -> 4;
                case "lg" -> 8;
                case "full" -> 9999;
                default -> null;
            };
            case "Border" -> switch (member) {
                case "hairline" -> 1;
                case "thin" -> 2;
                case "medium" -> 4;
                case "thick" -> 8;
                default -> null;
            };
            case "Elevation" -> switch (member) {
                case "none" -> 0;
                case "sm" -> 1;
                case "md" -> 2;
                case "lg" -> 3;
                case "xl" -> 4;
                default -> null;
            };
            case "Typography" -> switch (member) {
                case "xs" -> 12;
                case "sm" -> 14;
                case "md" -> 16;
                case "lg" -> 20;
                case "xl" -> 24;
                case "hero" -> 32;
                default -> null;
            };
            default -> null;
        };
    }

    static String memberList(String namespace) {
        return switch (namespace) {
            case "Spacing" -> "xs, sm, md, lg, xl";
            case "Radius" -> "none, sm, md, lg, full";
            case "Border" -> "hairline, thin, medium, thick";
            case "Elevation" -> "none, sm, md, lg, xl";
            case "Typography" -> "xs, sm, md, lg, xl, hero";
            default -> "";
        };
    }

    static String unknownMemberMessage(String namespace, String member) {
        return "unknown token '" + namespace + "." + member + "' — "
                + namespace + " has: " + memberList(namespace);
    }
}
