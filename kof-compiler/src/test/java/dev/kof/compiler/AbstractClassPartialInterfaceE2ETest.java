package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #322 — classe ABSTRATA que implementa uma interface pode deixar métodos da
 * interface sem implementação (o contrato é o do Java/JLS 8.4.8.1). O
 * `checkInterfaceImplementation` cobrava SEM043 do abstrato como se fosse
 * concreto (falso-positivo). O conserto DEFE o falso-positivo SEM tornar o
 * erro silencioso: a obrigacao passa a ser TRANSITIVA — a subclasse CONCRETA
 * que nao implementar o metodo da interface herdada via pai abstrato continua
 * rejeitada (SEM043), em vez de morrer em AbstractMethodError mudo (mesma
 * classe da #311, vetada pela regra 6 do freeze / R6).
 */
class AbstractClassPartialInterfaceE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void abstractClassDefersInterfaceMethodAndConcreteSubclassCompiles(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface IO {
                    void read()
                    void write()
                }
                abstract class AbstractIO implements IO {
                    void read() { println("reading") }
                    // write() deliberately deferred to the concrete subclass
                }
                class FileIO extends AbstractIO {
                    void write() { println("writing") }
                }
                main() {
                    var f = FileIO()
                    f.read()
                    f.write()
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(),
                "abstract class may defer interface methods; concrete subclass implements both: "
                        + result.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code");
        assertEquals("reading\nwriting", out,
                "read() runs from AbstractIO, write() from FileIO");
    }

    @Test
    void concreteSubclassStillChargedForInterfaceInheritedViaAbstractParent(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface IO {
                    void read()
                    void write()
                }
                abstract class AbstractIO implements IO {
                    void read() { println("reading") }
                }
                class Broken extends AbstractIO {
                    // write() never implemented anywhere -> AbstractMethodError if silent
                }
                main() {
                    var f = new Broken()
                    f.read()
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(),
                "concrete class must still be charged for the interface method inherited via abstract parent");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM043".equals(d.code())
                                && d.message().contains("Broken")
                                && d.message().contains("write")),
                "must report SEM043 naming the concrete class and the missing method: "
                        + result.diagnostics().getDiagnostics());
    }

    @Test
    void fullyImplementedAbstractClassInterfaceStillCompiles(@TempDir Path tempDir) throws Exception {
        // Controle: o deferral NAO quebra o caminho ja verde (abstrato que ja
        // implementa tudo da interface).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                interface Greeter {
                    String greet()
                }
                abstract class Base implements Greeter {
                    String greet() { return "hi" }
                }
                class Derived extends Base { }
                main() {
                    var d = Derived()
                    println(d.greet())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(),
                "abstract class with full interface impl compiles (and subclass inherits it): "
                        + result.diagnostics().getDiagnostics());
    }
}
