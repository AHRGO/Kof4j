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
 * D-UI-STYLE (UI007): declarative {@code Style("<declarations>")}.
 *
 * <p>Compile-time parse (Q4), typed whitelist (Q3), hex/name colors (Q1),
 * px/%/em/rem units (Q2) and {@code setStyle} on every DOM widget (Q5). The
 * style is real on KofJS and a documented no-op on JVM/Native (UI001).
 */
class UiStyleCssE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private CompilationResult compile(Path source, Path outDir, Target target) {
        return new CompilerDriver().compile(source, outDir, target);
    }

    private void write(Path dir, String name, String program) throws IOException {
        Files.writeString(dir.resolve(name + ".kf"), program);
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

    private String runJsHtml(Path source, Path outDir) throws IOException {
        CompilationResult result = compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                outDir.resolve("Default.mjs"), new ByteArrayOutputStream(),
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        assertNotNull(html, "window HTML should be captured");
        return html;
    }

    @Test
    void declarativeStyleLinksOnJvmAndNative(@TempDir Path tempDir) throws IOException {
        // Q1/Q2 happy path: hex color, bare Int (=px), % unit.
        write(tempDir, "style", """
            main() {
                var s = Style("background: #ff0000; padding: 8; border-radius: 4")
                var v = View(s)
                var w = Window("StyleTest")
                w.bind(v)
                w.show()
            }
            """);
        assertEquals("", runJvm(tempDir.resolve("style.kf"), tempDir.resolve("jvm")));
        assertEquals("", runNative(tempDir.resolve("style.kf"), tempDir.resolve("native")));
    }

    @Test
    void declarativeStyleIsRealInTheJsDom(@TempDir Path tempDir) throws IOException {
        write(tempDir, "style-js", """
            main() {
                var s = Style("background: #ff0000; padding: 8; border-radius: 4")
                var v = View(s)
                var w = Window("StyleTest")
                w.bind(v)
                w.show()
            }
            """);
        // The JS target must compile and render; the inline-style truth lives
        // in the real-DOM proof (KofJsBrowserE2ETest.declarativeStyleRenders-
        // InRealBrowserDom) — the HTML export does not serialize node.style.
        String html = runJsHtml(tempDir.resolve("style-js.kf"), tempDir.resolve("out"));
        assertTrue(html.contains("kof-view"), "view rendered: " + html);
    }

    @Test
    void cssColorNamesAndUnitsAreAccepted(@TempDir Path tempDir) throws IOException {
        // Q1 (CSS/Palette name) + Q2 (%/em/rem + bare Int) in one program.
        write(tempDir, "names", """
            main() {
                var s = Style("color: white; background-color: rebeccapurple; width: 50%; margin: 2em; font-size: 1.5rem")
                var v = View(s)
                var w = Window("Names")
                w.bind(v)
                w.show()
            }
            """);
        assertEquals("", runJvm(tempDir.resolve("names.kf"), tempDir.resolve("jvm")));
        assertEquals("", runNative(tempDir.resolve("names.kf"), tempDir.resolve("native")));
    }

    @Test
    void setStyleAppliesOnEveryDomWidget(@TempDir Path tempDir) throws IOException {
        // Q5: setStyle(style) on a Label (not only View).
        write(tempDir, "widget", """
            main() {
                var l = Label("card")
                l.setStyle(Style("background: #00ff00; padding: 4"))
                var col = Column(listOf(l))
                var w = Window("Widget")
                w.bind(col)
                w.show()
            }
            """);
        assertEquals("", runJvm(tempDir.resolve("widget.kf"), tempDir.resolve("jvm")));
        assertEquals("", runNative(tempDir.resolve("widget.kf"), tempDir.resolve("native")));
        String html = runJsHtml(tempDir.resolve("widget.kf"), tempDir.resolve("out"));
        assertTrue(html.contains("kof-label"), "label rendered: " + html);
    }

    @Test
    void unknownPropertyIsSem073(@TempDir Path tempDir) throws IOException {
        write(tempDir, "bad-prop", """
            main() {
                var s = Style("colr: #ff0000")
                var v = View(s)
            }
            """);
        CompilationResult r = compile(tempDir.resolve("bad-prop.kf"), tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "unknown property must fail the build");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM076".equals(d.code())),
                "expected SEM076: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void malformedDeclarationIsSem074(@TempDir Path tempDir) throws IOException {
        write(tempDir, "bad-decl", """
            main() {
                var s = Style("background")
                var v = View(s)
            }
            """);
        CompilationResult r = compile(tempDir.resolve("bad-decl.kf"), tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "malformed declaration must fail the build");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM077".equals(d.code())),
                "expected SEM077: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void invalidValueIsSem075(@TempDir Path tempDir) throws IOException {
        write(tempDir, "bad-value", """
            main() {
                var s = Style("padding: banana")
                var v = View(s)
            }
            """);
        CompilationResult r = compile(tempDir.resolve("bad-value.kf"), tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "invalid value must fail the build");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM078".equals(d.code())),
                "expected SEM078: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nonLiteralArgumentIsSem074(@TempDir Path tempDir) throws IOException {
        // Q4: the parse happens in the compiler, so the argument must be a
        // literal — a runtime string cannot be validated (R6, never silent).
        write(tempDir, "non-literal", """
            main() {
                var css = "#ff0000"
                var s = Style(css)
                var v = View(s)
            }
            """);
        CompilationResult r = compile(tempDir.resolve("non-literal.kf"), tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "non-literal argument must fail the build");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM077".equals(d.code())),
                "expected SEM077: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void declarativeStyleRunsOnScriptWithTheUi002Warning(@TempDir Path tempDir) throws IOException {
        // UI001/UI002 parity: kof.ui does not render on Script — it is a
        // documented no-op with a single warning, never a silent fallback (R6).
        write(tempDir, "script", """
            main() {
                var s = Style("background: #ff0000; padding: 8")
                var v = View(s)
                println("ok")
            }
            """);
        KofInterpreter.Result r = driver.interpret(List.of(tempDir.resolve("script.kf")),
                tempDir, new String[0]);
        assertEquals(0, r.exitCode(), "stderr: " + r.stderr());
        assertTrue(r.stdout().contains("ok"), "stdout: " + r.stdout());
        assertTrue(r.stderr().contains("UI002"), "expected the UI002 no-op warning: " + r.stderr());
    }

    @Test
    void fourIntFormIsUntouched(@TempDir Path tempDir) throws IOException {
        // Freeze rule 2: the existing Style(4 Ints) keeps its exact semantics.
        write(tempDir, "four-ints", """
            main() {
                var s = Style(Palette.black, Palette.white, 16, 8)
                var v = View(s)
                var w = Window("Legacy")
                w.bind(v)
                w.show()
            }
            """);
        assertEquals("", runJvm(tempDir.resolve("four-ints.kf"), tempDir.resolve("jvm")));
        assertEquals("", runNative(tempDir.resolve("four-ints.kf"), tempDir.resolve("native")));
        String html = runJsHtml(tempDir.resolve("four-ints.kf"), tempDir.resolve("out"));
        assertTrue(html.contains("kof-view"), "4-Int style still renders the view: " + html);
    }
}