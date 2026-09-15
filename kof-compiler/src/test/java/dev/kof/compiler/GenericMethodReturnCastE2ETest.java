package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #161 — chamada de método de instância num valor retornado de um
 * método genérico `T` de uma classe instanciada com tipo concreto
 * (`Box<String>`) não emitia `checkcast` no call-site: o `invokevirtual`
 * seguinte recebia `Object` na pilha → JVM VerifyError "Bad type on
 * operand stack".
 *
 * Raiz compartilhada com o ramo primitivo já existente em
 * `ExpressionInstanceCallLowerer` (que só emitia o unbox): faltava o
 * checkcast do tipo EFETIVO quando ele é uma referência concreta.
 * Matriz Q3: leitura direta (`s.length()`), anotação explícita
 * (`var s: String`), encadeamento (`b.get().toUpperCase()`), tipo genérico
 * aninhado e paridade JVM×JS.
 */
class GenericMethodReturnCastE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /**
     * Invoca por reflexão (não pelo launcher `java -cp ... Default.Main`):
     * o launcher do JavaFX engole o VerifyError real atrás de "os componentes
     * de runtime do JavaFX não foram encontrados" (regra do AGENTS.md). A
     * reflexão expõe a causa raiz, tornando um vermelho diagnosticável.
     */
    private String runJvm(Path outDir) throws IOException {
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new java.net.URL[]{outDir.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return buf.toString().replace("\r\n", "\n").trim();
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("JVM threw: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IOException("reflect failed", e);
        } finally {
            System.setOut(oldOut);
        }
    }

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    private static final String BOX = """
            class Box<T> {
                T item = null
                set(T v) { this.item = v }
                get(): T { return this.item }
            }
            """;

    @Test
    void genericReturnStringMethodCallJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("genret.kf");
        Files.writeString(src, BOX + """
                main() {
                    var b = new Box<String>()
                    b.set("hello")
                    var s = b.get()
                    println(s.length())
                }
                """);
        Path out = tempDir.resolve("genret-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("5", runJvm(out));
    }

    @Test
    void genericReturnExplicitAnnotationAndChainedJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("genret2.kf");
        Files.writeString(src, BOX + """
                main() {
                    var b = new Box<String>()
                    b.set("hello")
                    var s: String = b.get()
                    println(s.length())
                    println(b.get().toUpperCase())
                }
                """);
        Path out = tempDir.resolve("genret2-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("5\nHELLO", runJvm(out));
    }

    @Test
    void genericReturnParityJvmJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("genret3.kf");
        Files.writeString(src, BOX + """
                main() {
                    var b = new Box<String>()
                    b.set("kof")
                    println(b.get().length())
                    println(b.get().toUpperCase())
                    var i = new Box<Int>()
                    i.set(41)
                    println(i.get() + 1)
                }
                """);
        Path outJvm = tempDir.resolve("genret3-jvm");
        Path outJs = tempDir.resolve("genret3-js");
        CompilationResult rj = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rj.success(), "JVM compile failed: " + rj.diagnostics().getDiagnostics());
        CompilationResult rs = driver.compile(src, outJs, Target.JS);
        assertTrue(rs.success(), "JS compile failed: " + rs.diagnostics().getDiagnostics());
        String expected = "3\nKOF\n42";
        assertEquals(expected, runJvm(outJvm), "JVM");
        assertEquals(expected, runJs(outJs), "JS");
    }

    @Test
    void genericReturnInConditionJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("genret4.kf");
        Files.writeString(src, BOX + """
                main() {
                    var b = new Box<String>()
                    b.set("yes")
                    if (b.get().length() == 3) {
                        println("ok")
                    }
                }
                """);
        Path out = tempDir.resolve("genret4-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("ok", runJvm(out));
    }
}
