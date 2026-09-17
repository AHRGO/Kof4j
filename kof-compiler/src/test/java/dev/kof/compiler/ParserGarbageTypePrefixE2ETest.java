package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressao do 263 — o parser aceita QUALQUER identificador antes de uma
 * declaracao ANOTADA e o DESCARTA em silencio (R6): `let x: Int = 5` compila
 * e imprime `5` como se fosse `var`. Sem anotacao ele ja falha honesto
 * (SEM011, tipo desconhecido `let`); o buraco so aparece com `: Type`, porque
 * `parseVarDecl` sobrescreve o "tipo" (o prefixo lixo) pelo tipo da anotacao.
 *
 * Contrato: a forma anotada `nome: Type = ...` so vale depois de `var`/`val`.
 * No caminho type-first (`Type nome = ...`) um `:` logo apos o nome significa
 * que o prefixo NUNCA foi um tipo real — tem de virar diagnostico (PARSE095),
 * nao descarte silencioso. Cobre os tres alvos por ser level de parser.
 */
class ParserGarbageTypePrefixE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tmp, String src) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, src);
        return driver.compile(file, tmp.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private static String main(String body) {
        return "main() {\n    " + body + "\n    println(\"end\")\n}\n";
    }

    @Test
    void rejectsLetWithAnnotation(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, main("let x: Int = 5"));
        assertFalse(r.success(), "let x: Int must NOT compile silently (R6)");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("PARSE095"),
                "must report PARSE095 on the discarded prefix, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void rejectsArbitraryBogusPrefix(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, main("Klaxon x: Int = 5"));
        assertFalse(r.success(), "Klaxon x: Int must NOT compile silently");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("PARSE095"),
                "must report PARSE095, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void rejectsStringBogusPrefix(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, main("Banana q: String = \"z\""));
        assertFalse(r.success(), "Banana q: String must NOT compile silently");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("PARSE095"),
                "must report PARSE095, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void validVarAnnotatedStillCompiles(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, main("var x: Int = 5"));
        assertTrue(r.success(), "var x: Int = 5 is the valid annotated form: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void validValAnnotatedStillCompiles(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, main("val v: Int = 5"));
        assertTrue(r.success(), "val v: Int = 5 is valid: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void validTypeFirstNoColonStillCompiles(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, main("Int n = 5"));
        assertTrue(r.success(), "type-first `Int n = 5` (no colon) is valid: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void toleratesIdentityTypeFirstColon(@TempDir Path tmp) throws Exception {
        // prefix == annotation discards NOTHING (identity overwrite) — not the
        // reported bug; the cataloguer's fix direction is "prefix must MATCH the
        // annotation", so Int x: Int stays valid.
        CompilationResult r = compile(tmp, main("Int x: Int = 5"));
        assertTrue(r.success(), "Int x: Int (identity) is not the discard hole: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void rejectsTypeFirstColonMismatch(@TempDir Path tmp) throws Exception {
        // legit-looking prefix that DIFFERS from the annotation is silently
        // discarded (would overwrite Int with String) — the R6 hole.
        CompilationResult r = compile(tmp, main("Int x: String = \"z\""));
        assertFalse(r.success(), "Int x: String discards the Int prefix silently");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("PARSE095"),
                "must report PARSE095, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void rejectsLetWithAnnotationOnJsToo(@TempDir Path tmp) throws Exception {
        // parser-level fix: every target inherits the same diagnostic (rule 5)
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, main("let x: Int = 5"));
        CompilationResult r = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), Target.JS);
        assertFalse(r.success(), "JS target must also reject `let x: Int`");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("PARSE095"),
                "JS must report PARSE095, got: " + r.diagnostics().getDiagnostics());
    }
}
