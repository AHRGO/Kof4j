package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #155 — multipla interface cujo SEGUNDO metodo retorna primitivo
 * (Boolean) era tipado void no call-site (SEM033/SEM-dependente-de-ordem).
 * O teste existente (CompilerDriverTest.phaseF5_multipleInterfacesJvm) usa
 * DOIS metodos String — nao dispara o bug. Aqui o segundo metodo devolve
 * Boolean/Int (primitivo), que era exatamente o gatilho.
 */
class MultipleInterfaceReturnTypeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void secondInterfacePrimitiveReturnTypeIsNotVoid(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("multiif.kf");
        Files.writeString(src, """
                interface Printable { print(): String }
                interface Saveable  { save(): Boolean }
                class Doc implements Printable, Saveable {
                    print(): String  { return "doc" }
                    save(): Boolean  { return true  }
                }
                main() {
                    var d = new Doc()
                    println(d.print())
                    println(d.save())
                    var s = d.save()
                    println(s)
                }
                """);
        Path out = tempDir.resolve("multiif-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#155 second-method-as-void?): " + r.diagnostics().getDiagnostics());
        assertEquals("doc\ntrue\ntrue", runJvm(out));
    }

    @Test
    void secondInterfaceIntReturnTypeIsNotVoid(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("multiif2.kf");
        Files.writeString(src, """
                interface A { name(): String }
                interface B { count(): Int }
                class C implements A, B {
                    name(): String { return "c" }
                    count(): Int { return 7 }
                }
                main() {
                    var c = new C()
                    println(c.count() + 1)
                    println(c.name() + "!")
                }
                """);
        Path out = tempDir.resolve("multiif2-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("8\nc!", runJvm(out));
    }
}
