package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #326 — method override with incompatible return type accepted silently.
 *
 * O override com retorno NAO-covariante (nem igual, nem subtipo) virava dois
 * metodos com descritores diferentes na mesma hierarquia: a JVM nao ve
 * override, a chamada via referencia do pai despacha ao PAI (saida errada,
 * silenciosa). Retorno covariante e legal (bridge do #248) — o SEM059 so pega
 * o incompativel.
 */
class OverrideReturnCompatibilityTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void incompatibleOverrideReturnTypeIsRejectedWithSEM059(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class Base { Int compute() { return 1 } }
                class Child extends Base { String compute() { return "oops" } }
                main() {
                    Base b = Child()
                    println(b.compute())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(),
                "String return cannot override Int return — silent wrong dispatch is a bug");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM059".equals(d.code())
                                && d.message().contains("compute")
                                && d.message().contains("Base")),
                "must report SEM059 naming the method and the parent: "
                        + result.diagnostics().getDiagnostics());
    }

    @Test
    void covariantOverrideReturnStillCompilesAndDispatchesViaBridge(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class Animal { Object speak() { return "generic" } }
                class Dog extends Animal { String speak() { return "woof" } }
                main() {
                    Animal a = Dog()
                    println(a.speak())
                    Dog d = Dog()
                    println(d.speak())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(),
                "covariant (String <: Object) override is legal — bridge must dispatch: "
                        + result.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code");
        assertEquals("woof\nwoof", out, "virtual dispatch via Animal ref hits the Dog override (bridge, #248)");
    }

    @Test
    void sameReturnOverrideUnaffected(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class Base { Int value() { return 1 } }
                class Child extends Base { Int value() { return 2 } }
                main() {
                    Base b = Child()
                    println(b.value())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(),
                "identical return type is plain override: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void overloadNotOverrideUnaffected(@TempDir Path tempDir) throws Exception {
        // mesma classe, MESMO nome, ARIDADE diferente = sobrecarga (§131), nao
        // override — o check casa por assinatura completa (params), nao so nome.
        // Child so declara compute(Int) (retorno String, incompativel com o
        // compute() Int do Base); chamar apenas compute(5) prova que a
        // aridade-diferente NAO dispara SEM059. (Chamar o compute() herdado
        // esbarraria no sombra-membro pre-existente, familia #291 — fora do
        // escopo deste teste.)
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class Base { Int compute() { return 1 } }
                class Child extends Base { String compute(Int x) { return "x" + x } }
                main() {
                    var c = Child()
                    println(c.compute(5))
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(),
                "different arity is an overload, not an override — must not fire SEM059: "
                        + result.diagnostics().getDiagnostics());
        assertFalse(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM059".equals(d.code())),
                "SEM059 must not fire on an overload");
    }
}
