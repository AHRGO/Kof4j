package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase 10 (docs/ui/architecture.md §2.1 pilar 9) — design-system tokens:
 * {@code Spacing/Radius/Border/Elevation/Typography.<name>} as compile-time
 * Int constants (px — the D-UI-STYLE Q2 convention), folded by the same
 * idiom as {@code Palette}. The fold is shared by all four targets →
 * cross-target parity by construction. Unknown members and method calls on
 * a namespace are SEM078 (R6 — never a silent 0).
 */
class UiTokensE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private CompilationResult compile(Path source, Path outDir, Target target) {
        return new CompilerDriver().compile(source, outDir, target);
    }

    private String runJvm(Path source, Path outDir) throws IOException {
        CompilationResult result = compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit, output: '" + out + "'");
            return out;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private String runNative(Path source, Path outDir) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        CompilationResult result = compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "Native binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Native exit, output: '" + out + "'");
            return out;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static final String TOKENS_PROGRAM = """
        main() {
            println(Spacing.xs + "," + Spacing.sm + "," + Spacing.md + "," + Spacing.lg + "," + Spacing.xl)
            println(Radius.none + "," + Radius.sm + "," + Radius.md + "," + Radius.lg + "," + Radius.full)
            println(Border.hairline + "," + Border.thin + "," + Border.medium + "," + Border.thick)
            println(Elevation.none + "," + Elevation.sm + "," + Elevation.md + "," + Elevation.lg + "," + Elevation.xl)
            println(Typography.xs + "," + Typography.sm + "," + Typography.md + "," + Typography.lg + "," + Typography.xl + "," + Typography.hero)
        }
        """;

    private static final String TOKENS_GOLDEN = """
        4,8,16,24,32
        0,2,4,8,9999
        1,2,4,8
        0,1,2,3,4
        12,14,16,20,24,32""";

    @Test
    void tokenScalesMatchTheGoldenTableOnJvm(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("tokens.kf"), TOKENS_PROGRAM);
        assertEquals(TOKENS_GOLDEN, runJvm(tempDir.resolve("tokens.kf"), tempDir.resolve("jvm")));
    }

    @Test
    void tokenScalesMatchTheGoldenTableOnNative(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("tokens.kf"), TOKENS_PROGRAM);
        assertEquals(TOKENS_GOLDEN, runNative(tempDir.resolve("tokens.kf"), tempDir.resolve("native")));
    }

    @Test
    void tokenScalesMatchTheGoldenTableOnScript(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("tokens.kf"), TOKENS_PROGRAM);
        KofInterpreter.Result r = driver.interpret(List.of(tempDir.resolve("tokens.kf")),
                tempDir, new String[0]);
        assertEquals(0, r.exitCode(), "stderr: " + r.stderr());
        assertEquals(TOKENS_GOLDEN, r.stdout().trim(),
                "the interpreter folds the same constants (shared frontend)");
    }

    @Test
    void tokensReachTheJsDomThroughTheSharedFold(@TempDir Path tempDir) throws IOException {
        // The fold is in the shared frontend, so the JS artifact carries the
        // literal — prove the value lands in the rendered DOM text.
        String program = """
            main() {
                var l = Label("pad-" + Spacing.md + "-rad-" + Radius.lg)
                var w = Window("Tokens")
                w.bind(l)
                w.show()
            }
            """;
        Files.writeString(tempDir.resolve("tokens-js.kf"), program);
        CompilationResult result = compile(tempDir.resolve("tokens-js.kf"),
                tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                tempDir.resolve("js").resolve("Default.mjs"), new ByteArrayOutputStream(),
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        assertNotNull(html, "window HTML should be captured");
        assertTrue(html.contains("pad-16-rad-8"),
                "tokens must fold to the same px values on JS: " + html);
    }

    @Test
    void unknownTokenMemberIsSem076(@TempDir Path tempDir) throws IOException {
        // Q3 edge (R6): an unknown token member must fail the build — never a
        // silent 0 (the existing Palette.nope hole is catalogued separately).
        String program = """
            main() {
                var x = Spacing.huge
                println(x)
            }
            """;
        Files.writeString(tempDir.resolve("bad-token.kf"), program);
        CompilationResult r = compile(tempDir.resolve("bad-token.kf"),
                tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "unknown token member must fail the build");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM078".equals(d.code())),
                "expected SEM078: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void methodCallOnTokenNamespaceIsSem076(@TempDir Path tempDir) throws IOException {
        // Tokens hold constants; a method on the namespace is a translated
        // Java habit — SEM078 with the member list, never a silent no-op.
        String program = """
            main() {
                println(Spacing.of(4))
            }
            """;
        Files.writeString(tempDir.resolve("bad-token-call.kf"), program);
        CompilationResult r = compile(tempDir.resolve("bad-token-call.kf"),
                tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "method call on a token namespace must fail the build");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM078".equals(d.code())),
                "expected SEM078: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void tokensComposeWithStyleAndWidgets(@TempDir Path tempDir) throws IOException {
        // The point of the design system: tokens feed the existing primitives.
        // (Style() itself requires a literal per D-UI-STYLE Q4 — the tokens
        // carry the SAME px values the style declarations use.) setFontSize
        // is the documented JVM/Native no-op (UI001), so we assert the token
        // MATH (deterministic) and only exercise the widget wiring, never
        // read the no-op back.
        String program = """
            main() {
                var l = Label("card")
                l.setFontSize(Typography.lg)
                l.setStyle(Style("padding: 16; border-radius: 4"))
                println(Spacing.md + "," + Radius.md + "," + Typography.lg)
                var w = Window("Compose")
                w.bind(l)
                w.show()
            }
            """;
        Files.writeString(tempDir.resolve("compose.kf"), program);
        assertEquals("16,4,20", runJvm(tempDir.resolve("compose.kf"), tempDir.resolve("jvm")));
        assertEquals("16,4,20", runNative(tempDir.resolve("compose.kf"), tempDir.resolve("native")));
        CompilationResult js = compile(tempDir.resolve("compose.kf"), tempDir.resolve("js2"), Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                tempDir.resolve("js2").resolve("Default.mjs"), new ByteArrayOutputStream(),
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        assertNotNull(html, "window HTML should be captured");
        assertTrue(html.contains("kof-label"), "label rendered: " + html);
    }
}
