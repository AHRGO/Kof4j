package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #323 — constructor argument type mismatch accepted silently.
 *
 * A resolucao do construtor era so por ARIDADE; o tipo dos argumentos nunca
 * era conferido. `new A("x")` / `A("x")` num construtor `(Int)` compilava e o
 * emit inventava o descritor a partir dos ARGS → <init>(Ljava/lang/String;)V
 * fantasma → VerifyError mudo no load (R6: nunca silencioso). Agora: SEM014
 * na construcao, overload-aware — um irmao de mesma aridade que casa passa
 * (o emit tambem passa nele), e aridade sem candidato continua SEM023.
 */
class ConstructorArgTypeTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String src) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, src);
        return driver.compile(source, tempDir.resolve("out"), Target.JVM);
    }

    @Test
    void newWithWrongArgTypeIsRejectedWithSEM014(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class A { constructor(Int n) { println(n) } }
                main() { var x = new A("wrong") }
                """);
        assertFalse(result.success(),
                "String arg into (Int) ctor must not compile — phantom <init>(String)V is a VerifyError");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM014".equals(d.code())
                                && d.message().contains("Argument 1")
                                && d.message().contains("'A'")),
                "must report SEM014 naming the class: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void implicitConstructionWithWrongArgTypeIsRejectedWithSEM014(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class A { constructor(Int n) { println(n) } }
                main() { A("wrong") }
                """);
        assertFalse(result.success(),
                "face implicita A(\"x\") tem de dar o mesmo SEM014 que new A(\"x\")");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM014".equals(d.code())
                                && d.message().contains("'A'")),
                "must report SEM014: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void sameArityOverloadAcceptsEachArgType(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class B {
                    constructor(Int n) { println("int " + n) }
                    constructor(String s) { println("str " + s) }
                }
                main() {
                    var x = B(5)
                    var y = B("hi")
                    var z = new B(7)
                }
                """);
        assertTrue(result.success(),
                "ctor(Int)+ctor(String): cada chamada casa um irmao — check nao pode dar falso-positivo: "
                        + result.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code");
        assertEquals("int 5\nstr hi\nint 7", out, "overload resolution by arg type (implicit + new faces)");
    }

    @Test
    void correctSingleCtorStillCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class A { constructor(Int n) { println("n=" + n) } }
                main() { var x = new A(42); A(7) }
                """);
        assertTrue(result.success(),
                "happy path (arg matches) must be untouched: " + result.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code");
        assertEquals("n=42\nn=7", out, "both faces with the right arg type still construct");
    }

    @Test
    void wrongArityStillReportsSEM023(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class A { constructor(Int n) { println(n) } }
                main() { var x = new A(1, 2) }
                """);
        assertFalse(result.success(), "arity mismatch must stay a compile error");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM023".equals(d.code())
                                && d.message().contains("2 argument(s)")),
                "arity: SEM023, not SEM014 — the type check must not steal it: "
                        + result.diagnostics().getDiagnostics());
    }

    @Test
    void nullableArgIntoNullableCtorParamIsAccepted(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Box {
                    constructor(String? s) { if (s == null) { println("null") } else { println("got " + s) } }
                }
                readOne(): String? { if (false) { return null } else { return "x" } }
                main() {
                    var a = readOne()
                    Box(a)
                    Box("hi")
                }
                """);
        assertTrue(result.success(),
                "String? arg into String? ctor param (and String into String?) must pass: "
                        + result.diagnostics().getDiagnostics());
    }
}
