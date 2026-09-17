package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #397 (§269(c)) — a local named {@code args} inside {@code main()} was
 * shadowed by the implicit main-args intercept: the EMIT (ExpressionLowerer/
 * ExpressionTyper) intercepted {@code args} by NAME before scanning declared
 * locals, while the SEM pass (SemExpressionTyper) resolved locals first — the
 * two halves disagreed, and reads of the user's variable loaded the implicit
 * String[] (slot 0): {@code var args: List<String> = listOf("a","b");
 * args.size()} printed 0 and {@code args.get(0)} threw AIOOBE;
 * {@code var args = 42; println(args)} printed {@code [Ljava.lang.String;@…}.
 * Fix: the declared local WINS (same §179/field-vs-namespace-precedent as
 * #403). The implicit {@code args} (with no local of that name) is unchanged.
 */
class MainArgsShadowTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String program) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "program must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit code (VerifyError/crash = the bug): " + out);
        return out;
    }

    @Test
    void listLocalNamedArgsWinsOverImplicitArgs(@TempDir Path tempDir) throws Exception {
        // dossier minimal repro — before the fix: "0" + ArrayIndexOutOfBounds
        String out = runJvm(tempDir, """
                main() {
                    var args: List<String> = listOf("a", "b")
                    println(args.size())
                    println(args.get(0))
                }
                """);
        assertEquals("2\na", out, "#397: declared local args must shadow the implicit main args");
    }

    @Test
    void intLocalNamedArgsWinsOverImplicitArgs(@TempDir Path tempDir) throws Exception {
        String out = runJvm(tempDir, """
                main() {
                    var args = 42
                    println(args)
                }
                """);
        assertEquals("42", out, "#397: args Int local was printing the String[] identity (silent wrong)");
    }

    @Test
    void implicitListArgsParameterStillWorks(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): main(args: List<String>) keeps the prologue
        // path — declared param IS the implicit list, no user shadow.
        String out = runJvm(tempDir, """
                main(args: List<String>) {
                    println(args.size())
                }
                """);
        assertEquals("0", out, "implicit args parameter unchanged (empty argv)");
    }

    @Test
    void localArgsInOtherFunctionUnaffected(@TempDir Path tempDir) throws Exception {
        String out = runJvm(tempDir, """
                show(): Void {
                    var args = 7
                    println(args)
                }
                main() {
                    show()
                }
                """);
        assertEquals("7", out, "non-main locals named args were never intercepted");
    }
}
