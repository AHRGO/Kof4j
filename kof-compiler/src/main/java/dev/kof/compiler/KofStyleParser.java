package dev.kof.compiler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * D-UI-STYLE (UI007): parser of the declarative {@code style} string.
 *
 * <p>{@code Style("background: #ff0000; padding: 8")} is parsed in the
 * compiler (Q4), validated against a typed whitelist (Q3) and normalized to
 * canonical CSS text carried by the lowering. Unknown properties are
 * {@code SEM075}, malformed declarations {@code SEM076}, invalid values for a
 * known property {@code SEM077} — never a silent forward to {@code node.style}
 * (R6).
 *
 * <p>Colors accept hex CSS ({@code #rgb}/{@code #rrggbb}/{@code #rrggbbaa}),
 * the CSS named colors (which include every {@code Palette} name) and the
 * CSS-wide keywords (Q1). Lengths accept a bare integer (meaning {@code px})
 * and the {@code px}/{@code %}/{@code em}/{@code rem} suffixes (Q2).
 */
public final class KofStyleParser {

    private KofStyleParser() {}

    /** A compile-time style problem: the message plus its diagnostic code. */
    record Issue(String message, String code) {}

    /** Parse outcome: the normalized CSS (null when it failed) and the issues. */
    record Result(String normalized, List<Issue> issues) {
        boolean ok() { return issues.isEmpty(); }
    }

    /** How a property's value is validated (the "typed" half of the whitelist). */
    private enum Kind { COLOR, LENGTH, NUMBER, INT, KEYWORD, FREE }

    private record Prop(String canonical, Kind kind) {}

    private static final String COLOR_NAMES = "aliceblue antiquewhite aqua aquamarine azure beige "
            + "bisque black blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse "
            + "chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod "
            + "darkgray darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid "
            + "darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey darkturquoise "
            + "darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite "
            + "forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey "
            + "honeydew hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon "
            + "lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey "
            + "lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey "
            + "lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine "
            + "mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen "
            + "mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy "
            + "oldlace olive olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise "
            + "palevioletred papayawhip peachpuff peru pink plum powderblue purple rebeccapurple red "
            + "rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue "
            + "slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise "
            + "violet wheat white whitesmoke yellow yellowgreen transparent";

    private static final String CSS_WIDE = "inherit initial unset revert revert-layer currentcolor";

    private static final String UNIT_SUFFIXES = "px % em rem ch ex vw vh vmin vmax cm mm in pt pc fr";

    private static final String KEYWORD_PROPS = "display position overflow overflow-x overflow-y "
            + "visibility text-align font-style font-weight flex-direction flex-wrap justify-content "
            + "align-items align-content align-self justify-self text-decoration white-space word-break "
            + "overflow-wrap text-overflow cursor pointer-events user-select object-fit border-style "
            + "border-top-style border-right-style border-bottom-style border-left-style float clear "
            + "list-style-type list-style-position background-repeat box-sizing resize isolation "
            + "mix-blend-mode vertical-align text-transform font-variant grid-auto-flow direction "
            + "unicode-bidi table-layout caption-side empty-cells border-collapse touch-action "
            + "writing-mode background-clip background-origin scroll-behavior appearance";

    private static final String COLOR_PROPS = "color background-color border-color border-top-color "
            + "border-right-color border-bottom-color border-left-color outline-color "
            + "text-decoration-color caret-color accent-color column-rule-color";

    private static final String NUMBER_PROPS = "opacity flex-grow flex-shrink zoom";

    private static final String INT_PROPS = "z-index order";

    private static final String LENGTH_PROPS = "padding padding-top padding-right padding-bottom "
            + "padding-left margin margin-top margin-right margin-bottom margin-left border-width "
            + "border-top-width border-right-width border-bottom-width border-left-width border-radius "
            + "border-top-left-radius border-top-right-radius border-bottom-left-radius "
            + "border-bottom-right-radius width height min-width max-width min-height max-height "
            + "font-size line-height letter-spacing word-spacing gap row-gap column-gap top right "
            + "bottom left outline-width outline-offset text-indent flex-basis inset inline-size "
            + "block-size max-inline-size min-inline-size";

    private static final String FREE_PROPS = "background background-image background-size "
            + "background-position transform transform-origin transition animation box-shadow "
            + "text-shadow filter backdrop-filter aspect-ratio grid-template-columns "
            + "grid-template-rows grid-template-areas grid-column grid-row grid-area content clip-path "
            + "mask font font-family outline border border-top border-right border-bottom border-left "
            + "flex flex-flow place-items place-content place-self will-change list-style "
            + "counter-reset counter-increment object-position";

    private static final Map<String, Prop> PROPS = buildProps();

    private static Map<String, Prop> buildProps() {
        Map<String, Prop> m = new java.util.HashMap<>();
        for (String p : split(COLOR_PROPS)) m.put(p, new Prop(p, Kind.COLOR));
        for (String p : split(LENGTH_PROPS)) m.put(p, new Prop(p, Kind.LENGTH));
        for (String p : split(NUMBER_PROPS)) m.put(p, new Prop(p, Kind.NUMBER));
        for (String p : split(INT_PROPS)) m.put(p, new Prop(p, Kind.INT));
        for (String p : split(KEYWORD_PROPS)) m.put(p, new Prop(p, Kind.KEYWORD));
        for (String p : split(FREE_PROPS)) m.put(p, new Prop(p, Kind.FREE));
        return m;
    }

    private static final Set<String> COLOR_SET = Set.copyOf(split(COLOR_NAMES));

    private static final Set<String> WIDE_SET = Set.copyOf(split(CSS_WIDE));

    private static final Set<String> UNIT_SET = Set.copyOf(split(UNIT_SUFFIXES));

    private static List<String> split(String words) {
        List<String> out = new ArrayList<>();
        for (String w : words.split("\\s+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /**
     * Parses {@code source} and reports every issue to {@code diag}. Returns
     * true when the declarations are valid. Used by the typer (the analyzer
     * path owns the diagnostics; the lowering re-parses for the normalized
     * text without reporting again).
     */
    static boolean report(DiagnosticCollector diag, SourcePosition pos, String source) {
        if (source == null) {
            if (diag != null) {
                diag.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0,
                        pos != null ? pos.column() : 0,
                        pos != null ? pos.length() : 0,
                        "style: the declaration string must be a literal — the 4-Int "
                                + "Style(background, foreground, padding, radius) form takes a computed Color",
                        "SEM076");
            }
            return false;
        }
        Result r = parse(source);
        if (r.ok()) return true;
        if (diag == null) return false;
        for (Issue issue : r.issues()) {
            diag.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0,
                    pos != null ? pos.column() : 0,
                    pos != null ? pos.length() : 0,
                    issue.message(), issue.code());
        }
        return false;
    }

    /** The string value of a string literal argument, or null when not one. */
    static String literalString(ExpressionNode arg) {
        if (arg instanceof LiteralExpr lit && lit.kind() == ConcreteLiteralKind.STRING) {
            return lit.value();
        }
        return null;
    }

    /**
     * Parses and validates {@code source}, returning the normalized CSS text.
     * Never throws: every problem becomes an {@link Issue}.
     */
    static Result parse(String source) {
        List<Issue> issues = new ArrayList<>();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (source == null) {
            return new Result(null, List.of(new Issue(
                    "style: the declaration string must be a literal", "SEM076")));
        }
        for (String raw : source.split(";", -1)) {
            String decl = raw.trim();
            if (decl.isEmpty()) continue;
            int colon = decl.indexOf(':');
            if (colon <= 0 || colon == decl.length() - 1) {
                issues.add(new Issue("style: malformed declaration '" + decl
                        + "' — expected 'property: value'", "SEM076"));
                continue;
            }
            String name = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = collapse(decl.substring(colon + 1));
            Prop prop = PROPS.get(name);
            if (prop == null) {
                issues.add(new Issue("style: unknown property '" + name
                        + "' — not in the kof.ui style whitelist", "SEM075"));
                continue;
            }
            if (value.isEmpty()) {
                issues.add(new Issue("style: property '" + name
                        + "' has an empty value", "SEM077"));
                continue;
            }
            String bad = validate(prop, value);
            if (bad != null) {
                issues.add(new Issue("style: invalid value for '" + name + "': "
                        + bad, "SEM077"));
                continue;
            }
            out.add(prop.canonical() + ": " + normalizeValue(prop, value));
        }
        if (!issues.isEmpty()) return new Result(null, issues);
        return new Result(String.join("; ", out), List.of());
    }

    /**
     * Q2: a bare integer means {@code px} — the emitted CSS carries the unit
     * so the runtime only assigns values. {@code line-height} is exempt (a
     * unitless multiplier is meaningful) and {@code 0} stays unitless.
     */
    private static String normalizeValue(Prop prop, String value) {
        if (prop.kind() != Kind.LENGTH || "line-height".equals(prop.canonical())) return value;
        String[] tokens = value.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (isNumeric(tokens[i]) && !"0".equals(tokens[i])) tokens[i] = tokens[i] + "px";
        }
        return String.join(" ", tokens);
    }

    /** Returns null when the value is valid for the property, else the reason. */
    private static String validate(Prop prop, String value) {
        if (isWide(value)) return null;
        return switch (prop.kind()) {
            case COLOR -> isColor(value) ? null
                    : "'" + value + "' is not a hex color, a CSS color name or a CSS-wide keyword";
            case LENGTH -> lengthsOk(value) ? null
                    : "'" + value + "' is not a length (bare number = px, or px/%/em/rem/…)";
            case NUMBER -> isNumber(value) ? null : "'" + value + "' is not a number";
            case INT -> isInt(value) ? null : "'" + value + "' is not an integer";
            case KEYWORD -> keywordOk(value) ? null
                    : "'" + value + "' is not a CSS keyword";
            case FREE -> null;
        };
    }

    private static boolean isWide(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return WIDE_SET.contains(v);
    }

    private static boolean isColor(String value) {
        if (value.startsWith("#")) return hexOk(value);
        if (value.toLowerCase(Locale.ROOT).startsWith("rgb")
                || value.toLowerCase(Locale.ROOT).startsWith("hsl")) {
            return value.endsWith(")") && value.contains("(");
        }
        return COLOR_SET.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean hexOk(String value) {
        int n = value.length() - 1;
        if (n != 3 && n != 4 && n != 6 && n != 8) return false;
        for (int i = 1; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    private static boolean lengthsOk(String value) {
        String[] tokens = value.split("\\s+");
        if (tokens.length == 0 || tokens.length > 4) return false;
        for (String t : tokens) {
            if (!lengthOk(t)) return false;
        }
        return true;
    }

    private static boolean lengthOk(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        if ("auto".equals(t) || "0".equals(t)) return true;
        if (t.contains("(")) return t.endsWith(")");
        return numericPrefix(t) > 0;
    }

    /** Length of the numeric prefix of {@code t}, or 0 when there is none. */
    private static int numericPrefix(String t) {
        int i = 0;
        if (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) i++;
        int digits = 0;
        while (i < t.length() && Character.isDigit(t.charAt(i))) { i++; digits++; }
        if (i < t.length() && t.charAt(i) == '.') {
            i++;
            while (i < t.length() && Character.isDigit(t.charAt(i))) { i++; digits++; }
        }
        if (digits == 0) return 0;
        if (i == t.length()) return i;
        return UNIT_SET.contains(t.substring(i)) ? i : 0;
    }

    private static boolean isNumber(String value) {
        String t = value.toLowerCase(Locale.ROOT);
        if (t.contains("(")) return t.endsWith(")");
        return isNumeric(t);
    }

    private static boolean isNumeric(String t) {
        if (t.isEmpty()) return false;
        int i = 0;
        if (t.charAt(i) == '+' || t.charAt(i) == '-') i++;
        boolean digit = false;
        while (i < t.length() && Character.isDigit(t.charAt(i))) { i++; digit = true; }
        if (i < t.length() && t.charAt(i) == '.') {
            i++;
            while (i < t.length() && Character.isDigit(t.charAt(i))) { i++; digit = true; }
        }
        return digit && i == t.length();
    }

    private static boolean isInt(String value) {
        String t = value.trim();
        if (t.isEmpty()) return false;
        int i = 0;
        if (t.charAt(i) == '+' || t.charAt(i) == '-') i++;
        if (i >= t.length()) return false;
        while (i < t.length()) {
            if (!Character.isDigit(t.charAt(i))) return false;
            i++;
        }
        return true;
    }

    private static boolean keywordOk(String value) {
        for (String token : value.split("\\s+")) {
            if (token.isEmpty()) return false;
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '#') return false;
            }
        }
        return true;
    }

    private static String collapse(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
