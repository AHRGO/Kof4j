package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #333 — {@code void} com {@code return <valor>} compilava silencioso e
 * quebrava em runtime com {@code NoSuchMethodError}: a reinferencia "bug 26"
 * ({@link SemanticAnalyzer}) remudava o descriptor do metodo para {@code ()I}
 * enquanto o call site ja tipava {@code ()V}. VOID EXPLICITO nao e candidado
 * a inferencia — o diagnostico correto e SEM093 em tempo de compilacao (R6:
 * nunca crash de link "de graca").
 *
 * <p>Inferencia preservada (contrato §130/bug 26): metodo SEM tipo declarado
 * que termina em {@code return <expr>} continua a inferir o tipo; {@code
 * return} pelado em void e legal (saida antecipada).
 */
class VoidReturnValueE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tmp, String name, String source) throws IOException {
        Path src = tmp.resolve(name + ".kf");
        Files.writeString(src, source);
        return driver.compile(src, tmp.resolve(name + "-out"), Target.JVM);
    }

    @Test
    void voidWithReturnValueRejectedWithSem093(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "v1", """
                void doThing() { return 42 }
                main() {
                    doThing()
                }
                """);
        assertFalse(r.success(), "void + return valor deve falhar (#333), nao NoSuchMethodError");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM093") && d.message().contains("void")),
                "SEM093 esperado em: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void voidReturnDeepInBodyAlsoRejected(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "v2", """
                void pick(Int x) {
                    if (x > 0) {
                        return x
                    }
                }
                main() {
                    pick(1)
                }
                """);
        assertFalse(r.success(), "return com valor em void aninhado e o mesmo crime");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> d.code().equals("SEM093")),
                "SEM093: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void constructorWithValueReturnRejected(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "v3", """
                class B {
                    Int v
                    constructor(Int a) { v = a; return a }
                }
                main() {
                    var b = B(1)
                    println(b.v)
                }
                """);
        assertFalse(r.success(), "constructor nao devolve valor");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> d.code().equals("SEM093")),
                "SEM093: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void bareReturnInVoidStaysLegal(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "v4", """
                void g(Int x) {
                    if (x > 0) {
                        return
                    }
                    println("passou")
                }
                main() {
                    g(1)
                    g(-1)
                }
                """);
        assertTrue(r.success(), "return pelado = saida antecipada, sempre foi legal: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void undeclaredMethodStillInfersReturnType(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "v5", """
                class S {
                    Int total
                    constructor() { total = 0 }
                    add(Int v) {
                        total = total + v
                        return total
                    }
                }
                main() {
                    var s = S()
                    s.add(3)
                    println(s.add(4))
                }
                """);
        assertTrue(r.success(), "inferencia bug-26 (SEM tipo declarado) preservada (§130): "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void untypedTopLevelWithReturnRejected(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "v7", """
                f() { return 5 }
                main() {
                    f()
                }
                """);
        assertFalse(r.success(), "link crash silencioso (#333 face no-type): FunctionLowering emite ()I, call site ()V");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> d.code().equals("SEM093")),
                "SEM093: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void declaredReturnTypeMismatchStillSem010(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "v6", """
                Int f() { return "x" }
                main() {
                    println(f())
                }
                """);
        assertFalse(r.success());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM010")),
                "SEM010 intocado: " + r.diagnostics().getDiagnostics());
    }
}
