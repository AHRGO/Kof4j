package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * kof.ui Window and Label: the window is the webview container and labels
 * bind into it. Rendering happens in the KofJS target (DOM); JVM and Native
 * execute the same program with no-op handles (documented — UI is KofJS).
 */
class WindowE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private String runJvm(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Exit code should be 0");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runNative(Path source, Path outDir) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Exit code should be 0");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private void assertJsRuns(Path tempDir, String name, String program) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile should succeed: " + result.diagnostics().getDiagnostics());
        Path jsFile = tempDir.resolve("js/Default.mjs");
        assertTrue(Files.exists(jsFile), "JS module should exist");
        Path html = tempDir.resolve("js/index.html");
        assertTrue(Files.exists(html), "HTML entry should exist");
        String htmlContent = Files.readString(html);
        assertTrue(htmlContent.contains("kof-root"), "HTML should define the kof-root mount point");
    }

    @Test
    void windowAndLabelProgram(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var w = Window("Minha Janela")
                    var label = Label("Olá, Kof!")
                    w.title = "Kof App"
                    w.bind(label)
                    label.text = "Olá, janela!"
                    println(label.text)
                    println(w.title)
                }
                """;
        Path source = tempDir.resolve("win.kf");
        Files.writeString(source, program);
        runJvm(source, tempDir.resolve("jvm"));
        runNative(source, tempDir.resolve("native"));
        assertJsRuns(tempDir, "win", program);
    }

    @Test
    void windowRendersBoundLabel(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("win3.kf");
        Files.writeString(source, """
                main() {
                    var w = Window("Janela")
                    var label = Label("valor inicial")
                    w.bind(label)
                    label.text = "valor atualizado"
                    w.show()
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile should succeed: " + result.diagnostics().getDiagnostics());
        try {
            var out = new java.io.ByteArrayOutputStream();
            String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                    tempDir.resolve("js/Default.mjs"), out,
                    new java.io.ByteArrayInputStream(new byte[0]), out);
            assertNotNull(html, "The window should serialize to HTML");
            assertTrue(html.contains("valor atualizado"), "Bound label text should appear in the page");
            assertTrue(html.contains("Janela"), "Window title should appear in the page");
        } catch (java.io.IOException e) {
            throw new IOException(e);
        }
    }

    @Test
    void windowBindAndRemove(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var w = Window("App")
                    var a = Label("a")
                    var b = Label("b")
                    w.bind(a)
                    w.bind(b)
                    b.remove()
                    println("done")
                }
                """;
        Path source = tempDir.resolve("win2.kf");
        Files.writeString(source, program);
        String out = runJvm(source, tempDir.resolve("jvm"));
        assertEquals("done", out, "JVM should run the program");
        assertJsRuns(tempDir, "win2", program);
    }

    @Test
    void windowSizeAndClosePersistOnHeadlessDom(@TempDir Path tempDir) throws Exception {
        // #440: w.size(400,400) was a silent no-op — kofUiWindowSetSize/Close declared
        // the handle parameter as `window`, shadowing the global that holds __kofWindows.
        Path source = tempDir.resolve("win440.kf");
        Files.writeString(source, """
                main() {
                    var w = Window("My Window")
                    var label = Label("Hi, Kof!")
                    w.title = "Kof Window Test"
                    w.size(400, 300)
                    w.bind(label)
                    w.show()
                    w.close()
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        Path runtime = tempDir.resolve("js/kof-runtime.mjs");
        assertTrue(Files.exists(runtime), "runtime bundle should exist");
        String emitted = Files.readString(tempDir.resolve("js/Default.mjs"));
        assertTrue(emitted.contains("kofUiWindowSetSize(w, 400, 300)"),
                "lowering must forward size() to the runtime with the handle");
        Path probe = tempDir.resolve("probe.mjs");
        Files.writeString(probe, """
                const m = await import('file://' + process.argv[2]);
                const id = m.kofUiWindowNew('T');
                m.kofUiWindowSetSize(id, 400, 300);
                const el = globalThis.window.__kofWindows[id];
                console.log(el ? el.style.width + 'x' + el.style.height : 'MISSING');
                m.kofUiWindowClose(id);
                console.log('after-close:' + (globalThis.window.__kofWindows[id] === undefined ? 'gone' : 'still'));
                """);
        ProcessBuilder pb = new ProcessBuilder("node", probe.toString(), runtime.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "node probe exit, output:\n" + out);
        assertEquals("400pxx300px", out.lines().findFirst().orElse("<empty>"),
                "size() must persist on the window element (was silent no-op — #440)");
        assertEquals("after-close:gone", out.lines().skip(1).findFirst().orElse("<empty>"),
                "close() must remove the window from the registry");
    }
}
