package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #266 (c) — emenda da mantenedora 15/09 (DECISIONS §7): o `null` literal
 * NUNCA e fabricavel em posicao alguma (SG-005/SEM048 — null safety e por
 * narrowing, null so chega de API que devolve `T?`). Num PARAMETRO
 * NAO-nullable PRIMITIVO (`f(Int n)` chamado `f(null)`) o call-site empurrava
 * `aconst_null` contra slot `int` e a classe so morria no load:
 * `VerifyError: Type null ... not assignable to integer` — R6: ZERO diagnostico
 * no compile. Agora e SEM048 honesto no ponto do `null` (espelha a atribuicao
 * `x = null` do StatementAnalyzer e o `return null` §125).
 *
 * NAO-REGRESSAO (o outro lado da emenda): um `T?` (NullableType) param NUNCA cai
 * no guard — passar null a ele e LEGITIMO (a frente boxed dos 4 targets e a fila
 * §241/#266/#259, nao este guard). Por isso os casos de `Int?`/`String?` aqui
 * Assertam apenas "sem SEM048" (esses programas ainda esbarram em OUTROS bugs
 * preexistentes de boxing, que a emenda reabriu como fila — nao e deste unit).
 *
 * Faces cobertas (todas pelo checkNullArgs): metodo de instancia, construtor,
 * funcao top-level (BuiltinCallTyper:451). Edge: Long primitivo tambem dispara.
 */
class NullArgPrimitiveParamE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String file, String code) throws IOException {
        Path src = tempDir.resolve(file);
        Files.writeString(src, code);
        return driver.compile(src, tempDir.resolve("out-" + file), Target.JVM);
    }

    private void assertSem048(CompilationResult r, String shown) {
        assertFalse(r.success(), "null em param primitivo NAO-nullable nao pode compilar"
                + " (geraria VerifyError no load): " + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM048".equals(d.code()) && d.message().contains(shown)),
                "esperava SEM048 sobre '" + shown + "', foi: " + r.diagnostics().getDiagnostics());
    }

    private void assertNoSem048(CompilationResult r, String label) {
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .noneMatch(d -> "SEM048".equals(d.code())),
                label + " nao deve disparar SEM048 (param e nullable T?): "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void nullToInstancePrimitiveParamRejected(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "M1.kf",
                "class S {\n    Int len(Int n) { return n }\n}\n"
                        + "main() { println(new S().len(null)) }\n");
        assertSem048(r, "argument 1 of 'len()");
    }

    @Test
    void nullToConstructorPrimitiveParamRejected(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "M2.kf",
                "class P {\n    constructor(Int a) { }\n}\nmain() { new P(null) }\n");
        assertSem048(r, "of 'P()");
    }

    @Test
    void nullToTopLevelPrimitiveParamRejected(@TempDir Path tempDir) throws IOException {
        // a chamada top-level NAO passa por resolvedMethods — coberta pelo
        // check direto em BuiltinCallTyper (a frente que o pass pos-resolucao
        // sozinho deixaria viva).
        CompilationResult r = compile(tempDir, "M3.kf",
                "Int len(Int n) { return n }\nmain() { println(len(null)) }\n");
        assertSem048(r, "argument 1 of 'len()");
    }

    @Test
    void nullToLongPrimitiveParamRejected(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "M4.kf",
                "class S {\n    Long id(Long n) { return n }\n}\n"
                        + "main() { println(new S().id(null)) }\n");
        assertSem048(r, "long?");
    }

    @Test
    void nullToNullableIntParamNotRejected(@TempDir Path tempDir) throws IOException {
        // o caso EXATO #266: `handle(Boolean? flag)` com `handle(null)` e
        // legitimo — o guard nao pode engoli-lo (isso seria desfazer a emenda).
        CompilationResult r = compile(tempDir, "M5.kf",
                "class Handler {\n    void handle(Boolean? flag) {\n"
                        + "        if (flag == null) { println(\"null\") }\n    }\n}\n"
                        + "main() { new Handler().handle(null) }\n");
        assertNoSem048(r, "Boolean? param + null");
    }

    @Test
    void nullToNullableStringParamNotRejected(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "M6.kf",
                "class S {\n    Int id(String? s) { if (s == null) { return -1 } return 1 }\n}\n"
                        + "main() { println(new S().id(null)) }\n");
        assertNoSem048(r, "String? param + null");
    }

    @Test
    void realValueToPrimitiveParamStillCompiles(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(tempDir, "M7.kf",
                "class S {\n    Int len(Int n) { return n }\n}\n"
                        + "main() { println(new S().len(5)) }\n");
        assertTrue(r.success(), "valor real em param primitivo deve compilar: "
                + r.diagnostics().getDiagnostics());
    }
}
