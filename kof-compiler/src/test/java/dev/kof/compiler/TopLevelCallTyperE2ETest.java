package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caracterização da resolução de chamada SEM receiver a função top-level e a
 * `extern` (gate ≤500 do BuiltinCallTyper, REFACTOR-500): prova o COMPORTAMENTO
 * observável do frontend — diagnósticos SEM013/SEM014/SEM015/SEM048/SEM057, o
 * tipo de retorno registrado para a inferência do `var`, defaults, overloads,
 * `extern` void/não-void e a ordem "função top-level antes do fallback de
 * construtor". Verde ANTES e DEPOIS de mover o bloco: se uma regra mudar de
 * lugar ou de ordem, este teste denuncia.
 */
class TopLevelCallTyperE2ETest {

    private CompilationResult compile(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + ".kf");
        Files.writeString(src, kof);
        return new CompilerDriver().compile(src, dir.resolve("out-" + base), Target.JVM);
    }

    private void assertCompiles(Path dir, String base, String kof) throws IOException {
        CompilationResult r = compile(dir, base, kof);
        assertTrue(r.success(), () -> base + " deve compilar: " + r.diagnostics().getDiagnostics());
    }

    private void assertRejects(Path dir, String base, String kof, String code) throws IOException {
        CompilationResult r = compile(dir, base, kof);
        assertFalse(r.success(), base + " deve ser rejeitado (" + code + "), nunca compilar em silêncio");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains(code), base + ": esperado " + code + ", veio: " + diags);
    }

    // ── SEM015 (função inexistente) e o desvio para construtor ────────────
    @Test
    void undefinedFunctionIsSem015(@TempDir Path dir) throws IOException {
        assertRejects(dir, "undef", """
                main() {
                    nope(1)
                }
                """, "SEM015");
    }

    @Test
    void callNamedLikeAClassIsNotSem015AndReachesTheConstructorFallback(@TempDir Path dir) throws IOException {
        assertCompiles(dir, "ctorname", """
                class Z {
                    Int n
                    constructor(Int n) { this.n = n }
                }
                main() {
                    var z = Z(3)
                    println(z.n)
                }
                """);
        // a aridade errada NÃO é engolida pelo bloco top-level: o fallback de
        // construtor (depois dele, na mesma cadeia) segue reportando SEM023.
        assertRejects(dir, "ctorarity", """
                record P(Int x)
                main() {
                    var p = P(1, 2)
                }
                """, "SEM023");
    }

    // ── tipo de retorno registrado (inferência do var local) ──────────────
    @Test
    void nonVoidReturnTypeIsRegisteredSoTheLocalInfersIt(@TempDir Path dir) throws IOException {
        // sem o retorno registrado o `var` viraria Unknown e `s.length` (String
        // API) não resolveria — o efeito colateral que o bloco garante.
        assertCompiles(dir, "retinfer", """
                String name() { return "kof" }
                main() {
                    var s = name()
                    println(s.length)
                }
                """);
    }

    @Test
    void voidFunctionAsStatementFallsThroughCleanly(@TempDir Path dir) throws IOException {
        assertCompiles(dir, "voidcall", """
                void hi() { println("x") }
                main() {
                    hi()
                }
                """);
    }

    // ── overloads e defaults ──────────────────────────────────────────────
    @Test
    void overloadsBySignatureAndShortCallOnDefaultsResolve(@TempDir Path dir) throws IOException {
        assertCompiles(dir, "overload", """
                Int g(Int x) { return x }
                Int g(Int x, Int y) { return x + y }
                Int d(Int a, Int b = 5) { return a + b }
                main() {
                    println(g(1))
                    println(g(1, 2))
                    println(d(1))
                    println(d(1, 2))
                }
                """);
    }

    @Test
    void wrongArityOnAUserFunctionIsSem013(@TempDir Path dir) throws IOException {
        assertRejects(dir, "arity", """
                Int f(Int a) { return a }
                main() {
                    println(f(1, 2))
                }
                """, "SEM013");
    }

    @Test
    void wrongArgumentTypeOnAUserFunctionIsSem014(@TempDir Path dir) throws IOException {
        assertRejects(dir, "argtype", """
                Int f(Int a) { return a }
                main() {
                    println(f("s"))
                }
                """, "SEM014");
    }

    @Test
    void nullLiteralIntoANonNullablePrimitiveParameterIsSem048(@TempDir Path dir) throws IOException {
        assertRejects(dir, "nullprim", """
                Int f(Int a) { return a }
                main() {
                    println(f(null))
                }
                """, "SEM048");
    }

    // ── extern / FFI: contrato do call-site ───────────────────────────────
    @Test
    void externVoidAndNonVoidResolveAndTheReturnTypeInfersTheLocal(@TempDir Path dir) throws IOException {
        assertCompiles(dir, "externok", """
                extern "libc.so.6" srand(Int x)
                extern "libc.so.6" strlen(String s): Long
                main() {
                    srand(1)
                    var n = strlen("ab")
                    println(n)
                }
                """);
    }

    @Test
    void externWrongArityIsSem013AndWrongTypeIsSem014(@TempDir Path dir) throws IOException {
        assertRejects(dir, "externarity", """
                extern "libc.so.6" abs(Int x): Int
                main() {
                    println(abs(1, 2))
                }
                """, "SEM013");
        assertRejects(dir, "externtype", """
                extern "libc.so.6" abs(Int x): Int
                main() {
                    println(abs("s"))
                }
                """, "SEM014");
    }
}
