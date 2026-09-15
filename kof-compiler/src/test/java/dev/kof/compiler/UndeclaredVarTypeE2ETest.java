package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §249 — dois identificadores adjacentes no início de um statement
 * (`s length`, `Foo x`) eram aceitos em silêncio como uma declaração-tipo
 * invisível cujo TIPO não resolvia para nada: o programa compilava, o
 * statement virava lixo e as chamadas seguintes podiam ser engolidas pelo
 * parser (nested function espúria). O tipo explícito de {@code VarDeclStmt}
 * que não resolve para NENHUM tipo conhecido agora é SEM011, alinhado ao
 * caso {@code IdentifierExpr} de {@code SemExpressionTyper}.
 *
 * <p>Formas legítimas com tipo declarado continuam válidas: builtin
 * (`Int x`), coleção nua (`List xs = listOf(...)`), classe/record/enum do
 * módulo, classe externa via --classpath, tipo UI/media (§179) e type-param
 * de função genérica.
 */
class UndeclaredVarTypeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String source) throws Exception {
        Path file = dir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        return driver.compile(file, dir.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private boolean hasSem011(CompilationResult r, String typeName) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM011".equals(d.code()) && d.message().contains(typeName));
    }

    // ---- §249 faces: statement com dois identificadores adjacentes ----

    @Test
    void bogusDeclarationWithUnknownTypeIsRejected(@TempDir Path tmp) throws Exception {
        // `Foo x` — Foo não é tipo conhecido: antes SEM321 provava que compilava
        CompilationResult r = compile(tmp, """
                main() {
                    Foo x
                    println("ok")
                }
                """);
        assertFalse(r.success(), "Unknown declared type must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"),
                "Must report SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void validTypedDeclStillAcceptedNoInit(@TempDir Path tmp) throws Exception {
        // `Int x` SEM inicializador é forma legítima — não pode virar SEM011.
        CompilationResult r = compile(tmp, """
                main() {
                    Int x
                    println("ok")
                }
                """);
        assertTrue(r.success(), "Builtin typed decl without init must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void memberAccessOnVariableNoLongerMangledIntoDecl(@TempDir Path tmp) throws Exception {
        // Antes: `s length` era lido como decl invisível, `one`/`two` e o
        // próximo statement podiam ser engolidos. Agora é SEM011 no tipo `s`.
        CompilationResult r = compile(tmp, """
                main() {
                    var s = "a"
                    s length
                    println("one")
                    println("two")
                }
                """);
        assertFalse(r.success(), "mangled decl must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "s"),
                "Must report SEM011 naming the bogus type s: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void twoAdjacentIdentifiersMultiMangled(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                main() {
                    var s = "a"
                    s length junk
                    println("one")
                }
                """);
        assertFalse(r.success(), "multi mangled decl must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "s"),
                "Must report SEM011 on s: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "junk"),
                "Must report SEM011 on junk: " + r.diagnostics().getDiagnostics());
    }

    // ---- forms legítimas que NÃO podem ser rejeitadas (Q4/Q3) ----

    @Test
    void moduleClassTypeStillAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class Foo { Int v }
                main() {
                    Foo x
                    println("ok")
                }
                """);
        assertTrue(r.success(), "Module class as declared type must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void bareCollectionTypeAccepted(@TempDir Path tmp) throws Exception {
        // `List xs = listOf(...)` usa o nome simples sem `<`.
        CompilationResult r = compile(tmp, """
                main() {
                    List xs = listOf(1, 2)
                    println(xs.size)
                }
                """);
        assertTrue(r.success(), "Bare List declared type must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void functionTypeParameterAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                Int len<T>(T[] arr) { return arr.length }
                main() {
                    var a = new Int[2]
                    println(len(a))
                }
                """);
        assertTrue(r.success(), "Generic type-param as declared type must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void uiDeclaredTypeAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                main() {
                    Label l
                    println("ok")
                }
                """);
        // `var l` sem inicializador é a forma idiomatica; declarar Label nu pode
        // não compilar em todos os targets — aceitamos só confirmar que NÃO é
        // rejeitado por SEM011 de tipo não resolvido.
        assertFalse(hasSem011(r, "Label"),
                "Label is a declared UI type, must not be SEM011: " + r.diagnostics().getDiagnostics());
    }
}
