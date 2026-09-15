package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #258 — chamada de método de INSTÂNCIA qualificado pelo nome da classe
 * (`Calc.addToBase(x)` dentro de método de instância de `Calc`) era roteada
 * sempre como STATIC no dispatcher (`ExpressionMethodCallLowerer`): emitia
 * `invokestatic` sem `this` → `IncompatibleClassChangeError` em runtime.
 *
 * Fix (IR compartilhada, regra 5): método de instância referenciado por nome
 * de classe vale só dentro de método de instância da MESMA classe (equivale a
 * `this.m(x)`); fora disso é SEM060 honesto (R6), nunca invokestatic silencioso.
 *
 * Matriz Q3: exato da issue (chamada classificada dupla) nos 4 targets
 * (JVM/Script/JS/Native = mesma IR); static-by-class-name inalterado
 * (invokestatic continua); instância→estático da mesma classe inalterado;
 * cross-classe a partir de contexto estático → SEM060; mesmo-caso no alvo
 * interpretado (Script) e no runtime do JS.
 */
class ClassNameInstanceCallE2ETest {

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

    private static final String REPRO = """
            class Calc {
                Int base
                Int addToBase(Int n) { return base + n }
                Int triple(Int n) {
                    return Calc.addToBase(Calc.addToBase(n))
                }
            }
            main() {
                var c = new Calc()
                c.base = 10
                println(c.addToBase(5))
                println(c.triple(5))
            }
            """;

    @Test
    void classQualifiedInstanceCallDispatchesOnThisJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("q258.kf");
        Files.writeString(src, REPRO);
        Path out = tempDir.resolve("q258-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("15\n25", runJvm(out), "JVM target: esperado 15|25 (invokevirtual sobre this)");
    }

    @Test
    void classQualifiedInstanceCallParityScript(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("q258.kf");
        Files.writeString(src, REPRO);
        KofInterpreter.Result r = driver.interpret(List.of(src), src.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), "Script exit/stderr: " + r.stdout() + " " + r.stderr());
        assertEquals("15\n25", r.stdout().trim(), "Script (mesma IR compartilhada)");
    }

    @Test
    void classQualifiedInstanceCallParityJs(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("q258.kf");
        Files.writeString(src, REPRO);
        Path out = tempDir.resolve("q258-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile failed: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("node", out.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JS exit code, output: " + output);
        assertEquals("15\n25", output, "JS target");
    }

    @Test
    void classQualifiedInstanceCallParityNative(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("q258.kf");
        Files.writeString(src, REPRO);
        Path out = tempDir.resolve("q258-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "Native compile failed: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "Native exit code, output: " + output);
        assertEquals("15\n25", output, "Native x86 target");
    }

    @Test
    void staticViaClassNameStaysInvokestatic(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("q258s.kf");
        Files.writeString(src, """
                class Calc {
                    Int base
                    static Int twice(Int n) { return n * 2 }
                    Int apply(Int n) { return Calc.twice(n) + 1 }
                }
                main() {
                    var c = new Calc()
                    println(c.apply(5))
                    println(Calc.twice(7))
                }
                """);
        Path out = tempDir.resolve("q258s-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "static-by-class-name não pode mudar: " + r.diagnostics().getDiagnostics());
        assertEquals("11\n14", runJvm(out));
    }

    @Test
    void crossClassInstanceViaClassNameRejectedSem060(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("q258x.kf");
        Files.writeString(src, """
                class A {
                    static Int useB() { return B.miss() }
                }
                class B {
                    Int miss() { return 2 }
                }
                main() { }
                """);
        Path out = tempDir.resolve("q258x-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertFalse(r.success(), "instância de outra classe a partir de contexto estático não pode ser invokestatic");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM060".equals(d.code())
                        && d.message().contains("B.miss")),
                "esperava SEM060 apontando B.miss, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void sameClassInstanceCallFromTopLevelRejectedSem060(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("q258t.kf");
        Files.writeString(src, """
                class Calc {
                    Int base
                    Int addToBase(Int n) { return base + n }
                }
                main() {
                    println(Calc.addToBase(5))
                }
                """);
        Path out = tempDir.resolve("q258t-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertFalse(r.success(), "Calc.addToBase(5) no top-level não tem this — erro honesto, não runtime");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM060".equals(d.code())),
                "esperava SEM060, foi: " + r.diagnostics().getDiagnostics());
    }
}
