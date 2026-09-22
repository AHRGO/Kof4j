package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X6.1 ({@code D-INTEROP-REFLECT}, 21/09/2026) — {@code interop.schema(R)} é um
 * intrínseco de compile-time no namespace {@code interop} (ativado por
 * {@code import kof.interop}). Resolve para uma {@code List<Field>} imutável,
 * com {@code record Field(String name, String type)} fornecido pelo compilador,
 * na ordem de declaração dos componentes.
 *
 * <p>Zero reflexão em runtime: o compilador já conhece a estrutura do record e
 * a dobra acontece no frontend (as mesmas ops do {@code listOf(Field(…))}
 * equivalente) — logo a saída é a mesma nos alvos que executam o frontend
 * (JVM/Script/JS) e o Native apenas compila o mesmo IR.</p>
 */
class InteropSchemaE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path root, List<Path> sources, String expected) throws Exception {
        Path outDir = root.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JVM, root);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals(expected, output, "JVM output");
        return output;
    }

    private void runScript(Path root, List<Path> sources, String expected) {
        KofInterpreter.Result r = driver.interpret(sources, root, new String[0]);
        assertEquals(0, r.exitCode(), "SCRIPT exit code, output: " + r.stdout());
        assertEquals(expected, r.stdout().trim().replace("\r\n", "\n"), "SCRIPT output");
    }

    private void runJs(Path root, List<Path> sources, String expected) throws Exception {
        Path outDir = root.resolve("js-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JS, root);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path entry;
        try (var s = Files.walk(outDir)) {
            entry = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst()
                    .orElseThrow(() -> new java.io.IOException("no Default.mjs in " + outDir));
        }
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
        }
    }

    private static Path write(Path dir, String name, String body) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, body);
        return f;
    }

    @Test
    void schemaOfRecordPrintsFieldsInDeclarationOrder(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record User(String name, Int age)

                main() {
                    for (var f in interop.schema(User)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        String expected = "name:String\nage:Int";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void schemaIsAnImmutableListOfField(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record Point(Int x, Int y)

                main() {
                    List<Field> s = interop.schema(Point)
                    println(s.size)
                    println(s.get(0).name() + "," + s.get(1).name())
                }
                """);
        String expected = "2\nx,y";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void schemaOfSingleFieldRecord(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record Tag(String value)

                main() {
                    for (var f in interop.schema(Tag)) {
                        println(f.name() + "=" + f.type())
                    }
                }
                """);
        String expected = "value=String";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
        runJs(tmp, List.of(f), expected);
    }

    @Test
    void schemaOfGenericRecordUsesDeclaredComponentType(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record Box<T>(T value, Int count)

                main() {
                    for (var f in interop.schema(Box)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        String expected = "value:T\ncount:Int";
        runJvm(tmp, List.of(f), expected);
        runScript(tmp, List.of(f), expected);
    }

    @Test
    void schemaCompilesOnNativeTarget(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                record User(String name, Int age)

                main() {
                    for (var f in interop.schema(User)) {
                        println(f.name() + ":" + f.type())
                    }
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("native"),
                Target.NATIVE, tmp);
        assertTrue(r.success(), "NATIVE compile failed: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaOfNonRecordDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                import kof.interop

                class NotARecord {
                    Int x
                }

                main() {
                    for (var f in interop.schema(NotARecord)) {
                        println(f.name())
                    }
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("bad"),
                Target.JVM, tmp);
        assertFalse(r.success(), "class argument must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())),
                "expected INTEROP001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void schemaWithoutImportDiagnoses(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", """
                record User(String name, Int age)

                main() {
                    for (var f in interop.schema(User)) {
                        println(f.name())
                    }
                }
                """);
        CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("noimp"),
                Target.JVM, tmp);
        assertFalse(r.success(), "interop.schema without import must be rejected");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "INTEROP001".equals(d.code())),
                "expected INTEROP001, got: " + r.diagnostics().getDiagnostics());
    }
}
