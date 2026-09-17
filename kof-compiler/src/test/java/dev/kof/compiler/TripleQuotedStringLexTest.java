package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.*;

import dev.kof.compiler.parser.Lexer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * #364 / §269(a) — {@code """..."""} folded to an EMPTY STRING silently: the
 * program compiled and ran printing nothing (R6/Q7 — a construct the language
 * does not have must never compile). Kof has no raw/multiline literal
 * ({@code docs/language-reference/lexical-structure.md} §4.1). The lexer now
 * reports LEX008 and skips the block so exactly ONE diagnostic lands.
 */
class TripleQuotedStringLexTest {

    private List<Diagnostic> lex(String src) {
        DiagnosticCollector diags = new DiagnosticCollector();
        new Lexer(src, "Main.kf", diags).tokenize();
        return diags.getDiagnostics();
    }

    @Test
    void tripleQuotedStringIsRejectedNotSilent() {
        List<Diagnostic> ds = lex("main() {\n  println(\"\"\"Hello\nWorld\"\"\")\n}\n");
        assertTrue(ds.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR
                        && "LEX008".equals(d.code())),
                "#364: `\"\"\"` MUST fail with LEX008, never fold to empty (actual: " + ds + ")");
    }

    @Test
    void exactlyOneDiagnosticPerTripleBlock() {
        List<Diagnostic> ds = lex("main() {\n  val s = \"\"\"abc\"\"\"\n}\n");
        long n = ds.stream().filter(d -> "LEX008".equals(d.code())).count();
        assertEquals(1, n, "one block = one LEX008, no cascade (Q3: cascade would bury the real error): " + ds);
        assertEquals(2, ds.stream().filter(d -> "LEX008".equals(d.code())).findFirst().orElseThrow().line(),
                "diagnostic points at the opening triple quotes");
    }

    @Test
    void normalStringsUnaffected() {
        // control: a quote-adjacent regular string is STILL a regular string —
        // `""` empty + adjacent "x" tokenizes as before (backwards compat).
        List<Diagnostic> ds = lex("main() { println(\"\"); println(\"a\") }");
        assertTrue(ds.isEmpty(), "regular strings must not gain diagnostics (backward compat): " + ds);
    }

    @Test
    void unterminatedTripleAlsoRejects() {
        List<Diagnostic> ds = lex("main() { println(\"\"\"no end");
        assertTrue(ds.stream().anyMatch(d -> "LEX008".equals(d.code())),
                "unterminated `\"\"\"` also fails fast (never eats EOF silently): " + ds);
    }
}
