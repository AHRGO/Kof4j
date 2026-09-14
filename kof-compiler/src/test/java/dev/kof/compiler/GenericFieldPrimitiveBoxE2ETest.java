package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issues #243/#220 — atribuir primitivo a campo de tipo T (apagado a
 * Object) sem boxing: o putfield saía com `int` cru na pilha sobre
 * 'java/lang/Object' → VerifyError "Bad type on operand stack" (e o
 * mesmo programa quebrava de um jeito por alvo). Raiz compartilhada:
 * o store de campo precisa do MESMO emitErasureBox do store de
 * coleção/parâmetro (§143/§126). Matriz Q3: Int/Double/Boolean/String
 * no MESMO campo T (String = controle: referência sempre funcionou) +
 * paridade JVM×JS + get de volta lido como objeto.
 */
class GenericFieldPrimitiveBoxE2ETest {

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

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    @Test
    void primitiveAssignedToGenericFieldIsBoxedJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("genfield.kf");
        Files.writeString(src, """
                class Wrapper<T> {
                    T value
                }
                main() {
                    var w = new Wrapper<Int>()
                    w.value = 42
                    println(w.value)
                }
                """);
        Path out = tempDir.resolve("genfield-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("42", runJvm(out));
    }

    @Test
    void allPrimitiveKindsAndReferenceIntoGenericField(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("genfield4.kf");
        Files.writeString(src, """
                class Holder<T> {
                    T value
                    show(): String { return "" + value }
                }
                main() {
                    var a = new Holder<Int>()
                    a.value = 7
                    var d = new Holder<Double>()
                    d.value = 1.5
                    var b = new Holder<Bool>()
                    b.value = true
                    var s = new Holder<String>()
                    s.value = "hi"
                    println(a.show())
                    println(d.show())
                    println(b.show())
                    println(s.show())
                }
                """);
        Path outJvm = tempDir.resolve("genfield4-jvm");
        Path outJs = tempDir.resolve("genfield4-js");
        CompilationResult rj = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rj.success(), "JVM compile failed: " + rj.diagnostics().getDiagnostics());
        CompilationResult rs = driver.compile(src, outJs, Target.JS);
        assertTrue(rs.success(), "JS compile failed: " + rs.diagnostics().getDiagnostics());
        String expected = "7\n1.5\ntrue\nhi";
        assertEquals(expected, runJvm(outJvm), "JVM");
        assertEquals(expected, runJs(outJs), "JS");
    }
}
