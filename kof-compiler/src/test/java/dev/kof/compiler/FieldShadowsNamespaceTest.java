package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #403 — a field named `log` made EVERY method body of the class emit as a
 * bare `return` (VerifyError: Operand stack underflow at load; silent compile,
 * R6). Root: the emit pass re-dispatched `log.<anything>` to the kof.log
 * namespace lowerer without the field-shadow guard the semantic pass already
 * applies (SemExpressionTyper resolves the bare identifier `log` to the field
 * before the namespace exemption) — and the namespace lowerer silently emits
 * NOTHING for a method it does not map (`add`, `size`...), leaving the enclosing
 * method with an empty body. The same latent hole existed on the sibling
 * hijacks (json/db/orm/process, which do not even check locals). The fix adds
 * `shadowsFieldOfCurrentClass` to the identifier-namespace hijacks in
 * ExpressionMethodCallLowerer — a field of the current class wins (precedent
 * §179/§243: declared type wins the builtin alias).
 */
class FieldShadowsNamespaceTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path out) throws Exception {
        String java = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(java, "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "load/run must succeed (empty method body IS the bug: " + o + ")");
        return o;
    }

    @Test
    void fieldNamedLogDoesNotHijackMethodBodies(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                class Collector {
                    List<String> log
                    constructor() { log = listOf() }
                    emit(event: String) { log.add(event) }
                    count(): Int { return log.size() }
                }
                main() {
                    val c: Collector = Collector()
                    c.emit("a")
                    println(c.count())
                }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("1", runJvm(tempDir.resolve("out")),
                "field `log` used as a List — old code emitted empty bodies (#403)");
    }

    @Test
    void siblingNamespaceHijacksAlsoRespectFieldShadow(@TempDir Path tempDir) throws Exception {
        // json/db/process fields: the same hijack chain, none guarded locals
        // before the fix; a String field named `json` must stay a field.
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                class Box {
                    String json
                    constructor() { json = "{}" }
                    show(): String { return json }
                }
                main() { var b = Box(); println(b.show()) }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("{}", runJvm(tempDir.resolve("out")), "field `json` not hijacked to the json namespace");
    }

    @Test
    void realLogNamespaceStillRoutesWhenNoFieldShadow(@TempDir Path tempDir) throws Exception {
        // control (backward compat, freeze rule 2): without a `log` field the
        // kof.log namespace dispatch still lowers log.debug/info to kof_log_*.
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    log.info("hello-log")
                    println("done")
                }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "log.info() (no shadowing field) must still compile: " + r.diagnostics().getDiagnostics());
        String out = runJvm(tempDir.resolve("out"));
        assertTrue(out.contains("hello-log") && out.contains("done"),
                "kof.log namespace must still work with no field named log: " + out);
    }
}
