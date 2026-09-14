package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofEnumTest {
    private final CompilerDriver driver = new CompilerDriver();

    private static final String ENUM_HEAD = """
            enum Color { Red, Green, Blue }

            main() {
                val c = Color.Red
                assert(c == Color.Red)
                assert(!(c == Color.Blue))
                assert(c != Color.Green)
                println(c.name())
                println(Color.Green)
//MORE""";

    @Test
    void enumJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, ENUM_HEAD.replace("//MORE", """
                val vs = Color.values()
                assert(vs.size() == 3)
                assert(vs.get(0) == Color.Red)
                assert(vs.get(2) == Color.Blue)
                assert(Color.valueOf("Green") == Color.Green)
                println("ok")
                }"""), "Red\nGreen\nok");
    }

    @Test
    void enumJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, ENUM_HEAD.replace("//MORE", """
                val vs = Color.values()
                println(vs.size())
                println(Color.valueOf("Blue") == Color.Blue)
                println(Color.valueOf("nope") == null)
                println("done")
                }"""), "Red\nGreen\n3\ntrue\ntrue\ndone");
    }

    @Test
    void enumNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, ENUM_HEAD.replace("//MORE", "\n}"), "Red\nGreen");
    }

    @Test
    void enumForInValuesMethodCallsJvm(@TempDir Path tmp) throws Exception {
        // Issue #165 — for-in over EnumType.values() method calls (name() / toString())
        runJvm(tmp, """
                enum Color { RED, GREEN, BLUE }
                main() {
                    for (var c in Color.values()) {
                        println(c.name())
                        println(c.toString())
                    }
                }
                """, "RED\nRED\nGREEN\nGREEN\nBLUE\nBLUE");
    }

    @Test
    void enumTypeSafetyJvm(@TempDir Path tmp) throws Exception {
        // constante desconhecida deve falhar (SEM/field não resolvido)
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, "enum Color { Red }\nmain() { val c = Color.Nope }");
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Unknown constant should not compile");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
