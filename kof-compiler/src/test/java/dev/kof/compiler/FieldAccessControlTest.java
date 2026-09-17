package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #331 (private/protected) + #327 (final) — field access bypassed the
 * compile-time check; the JVM enforced the modifier at runtime with
 * IllegalAccessError (silent compile, R6/Q7 violation).
 *
 * Os metodos ja tinham SEM046 (SG-013); o campo nao tinha porque o
 * SymbolTableBuilder preservava so STATIC no FieldSymbol (flags=0 p/ o
 * resto) — mesma raiz que os metodos tiveram antes do SG-013. Agora a
 * visibilidade e o `final` chegam ao simbolo e os cheques espelham o
 * contrato dos metodos: private so na declarante, protected na declarante/
 * subclasses, escrita final so no <init> da declarante (SEM065).
 */
class FieldAccessControlTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String src) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, src);
        return driver.compile(source, tempDir.resolve("out"), Target.JVM);
    }

    @Test
    void privateFieldReadFromOtherClassIsRejectedWithSEM046(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Foo { private Int secret = 42 }
                class Bar { Int read(Foo f) { return f.secret } }
                main() { println(Bar().read(Foo())) }
                """);
        assertFalse(result.success(),
                "private field read from another class must not compile — runtime IllegalAccessError is silent today");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM046".equals(d.code())
                                && d.message().contains("field 'secret'")
                                && d.message().contains("'Foo'")),
                "must report SEM046 naming the field and owner: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void privateFieldWriteFromOutsideIsRejectedWithSEM046(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Foo { private Int secret = 42 }
                main() { var f = Foo(); f.secret = 7 }
                """);
        assertFalse(result.success(), "face de ESCrita tem o mesmo contrato da leitura");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM046".equals(d.code()) && d.message().contains("secret")),
                "must report SEM046: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void protectedFieldFromNonSubclassIsRejected(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Base { protected Int x = 10 }
                class Stranger { Int peek(Base b) { return b.x } }
                main() { println(Stranger().peek(Base())) }
                """);
        assertFalse(result.success(), "protected so vale na declarante/subclasses (JVM aplica em runtime)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM046".equals(d.code()) && d.message().contains("protected")),
                "must report SEM046 protected: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void protectedFieldViaThisInSubclassStillCompiles(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Base { protected Int x = 10 }
                class Sub extends Base { Int viaThis() { return x } }
                main() { println(Sub().viaThis()) }
                """);
        assertTrue(result.success(),
                "subclasses access protected via this/nu identifier — must keep compiling: "
                        + result.diagnostics().getDiagnostics());
    }

    @Test
    void finalFieldWriteOutsideConstructorIsRejectedWithSEM065(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Config { final Int timeout = 30 }
                main() { var c = Config(); c.timeout = 60; println(c.timeout) }
                """);
        assertFalse(result.success(),
                "putfield of a final field outside <init> = IllegalAccessError at runtime, silent today");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM065".equals(d.code())
                                && d.message().contains("final field 'timeout'")),
                "must report SEM065 naming the field: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void finalFieldAssignedInOwnConstructorStillCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Config {
                    final Int timeout
                    public constructor(Int t) { this.timeout = t }
                    Int get() { return timeout }
                }
                main() { println(Config(30).get()) }
                """);
        assertTrue(result.success(),
                "this.x = in the declaring ctor is THE legal write site (JVMS 4.4): "
                        + result.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code");
        assertEquals("30", out, "ctor final init works end-to-end");
    }

    @Test
    void publicFieldsAndRecordComponentsUnaffected(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                enum Color { Red, Green }
                record P(Int x, Int y)
                class Wr { Int count = 0
                    public constructor(Int v) { this.count = v } }
                main() {
                    var w = Wr(9)
                    w.count = 10
                    println(Color.Red)
                    println(P(1,2).x())
                    println(w.count)
                }
                """);
        assertTrue(result.success(),
                "public field write, enum constant, record accessor — the checks must not fire: "
                        + result.diagnostics().getDiagnostics());
    }
}
