package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issues #193/#198 — lambda guardada em contentor generico
 * (`new List<() -> Int>()`) perdia o tipo no get(0): SEM015 ao chamar a
 * variavel (#193) e Methodref vazio (ClassFormatError) na chamada
 * encadeada `get(0)()` (#198).
 *
 * Raiz (2 camadas):
 *  1. parser: o loop de type-args de `new T<...>()` descartava `(`/`)`/`->`
 *     (so aceitava IDENTIFIER/primitivo) — "() -> Int" virava "Int";
 *  2. SemExpressionTyper NewExpr: devolvia BuiltinTypes.LIST SEM os
 *     type-arguments (o ExpressionTyper do emit sempre aplicou) — divergencia
 *     semantico/emit fazia get(0) inferir Unknown -> o owner do call virava
 *     "".
 */
class LambdaInGenericContainerE2ETest {

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

    private String both(String name, String source, String expected, Path tempDir) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rj = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rj.success(), name + " JVM compile failed: " + rj.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), name + " JS compile failed: " + rjs.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(outJvm), name + " JVM");
        assertEquals(expected, runJs(outJs), name + " JS");
        return expected;
    }

    // #193 (repro exato): var f = fns.get(0); f() — antes SEM015.
    @Test
    void lambdaRetrievedIntoVariableIsCallable(@TempDir Path tempDir) throws IOException {
        both("f193", """
                main() {
                    var fns = new List<() -> Int>()
                    fns.add(() -> 42)
                    var f = fns.get(0)
                    println(f())
                }
                """, "42", tempDir);
    }

    // #198 (repro exato): fns.get(0)() encadeado — antes ClassFormatError
    // (Methodref com classe/metodo vazios).
    @Test
    void chainedCallOnGetResult(@TempDir Path tempDir) throws IOException {
        both("f198", """
                main() {
                    var fns = new List<() -> Int>()
                    fns.add(() -> 42)
                    println(fns.get(0)())
                }
                """, "42", tempDir);
    }

    // #198 variante com captura (o 2o repro da issue).
    @Test
    void chainedCallOnCapturedLambda(@TempDir Path tempDir) throws IOException {
        both("f198c", """
                main() {
                    var fns = new List<() -> Int>()
                    var x = 5
                    fns.add(() -> x)
                    println(fns.get(0)())
                }
                """, "5", tempDir);
    }

    // #193 variante parametrizada: List<(Int) -> Int>.get(0)(5).
    @Test
    void parametrizedLambdaTypeInContainer(@TempDir Path tempDir) throws IOException {
        both("f193p", """
                main() {
                    var fns = new List<(Int) -> Int>()
                    fns.add((x: Int) -> x * 2)
                    var g = fns.get(0)
                    println(g(5))
                }
                """, "10", tempDir);
    }

    // Back-compat: value-type em container continua (x + 1) e Map.get narrowing.
    @Test
    void valueTypeContainerUnchanged(@TempDir Path tempDir) throws IOException {
        both("fbasic", """
                main() {
                    var lst = new List<Int>()
                    lst.add(10)
                    var x = lst.get(0)
                    println(x + 1)
                    var m = new Map<String, Int>()
                    m.put("a", 3)
                    var v = m.get("a")
                    if (v != null) { println(v) } else { println(0) }
                }
                """, "11\n3", tempDir);
    }
}
