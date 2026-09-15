package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #141 — `var h = spawn { expr }; await h` perdia o valor: o typer do
 * spawn dava Handle<Void> para corpo-bloco de UMA expressão e o `await`
 * morria em SEM033 falso-positivo ("atribuição recebeu valor void") — antes
 * (0.3.x) era null em runtime; mudou de face, nunca de raiz: a conversão de
 * corpo-expressão-única em retorno existe na EMISSÃO (CompilerLambdaClass
 * converte ExpressionStmt único em ReturnStmt quando o tipo não é void) mas
 * NENHUM dos 3 typers do caminho spawn a espelhava (BuiltinCallTyper —
 * análise semântica, MethodCallTyper — lowering, StatementLowerer — pin do
 * Handle no local).
 * Matriz Q3: String literal, chamada de função Int (corpo da issue exato),
 * `return` explícito (não-regressão), corpo-void (println — o Handle deve
 * CONTINUAR void, fire-and-forget intocado), paridade JVM×JS.
 */
class SpawnAwaitBlockE2ETest {

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

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    private void both(String name, String source, String expected, Path tempDir) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rj = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rj.success(), name + " JVM compile failed: " + rj.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), name + " JS compile failed: " + rjs.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(outJvm), name + " JVM");
        assertEquals(expected, runJs(outJs), name + " JS");
    }

    // #141 corpo exato: spawn { work() } — bloco com ÚNICA expressão que é
    // chamada de função Int. Antes: SEM033 no `await` (Handle<Void>).
    @Test
    void spawnBlockWithCallAwaitsValue(@TempDir Path tempDir) throws IOException {
        both("s141", """
                Int work() {
                    var x = 41
                    return x + 1
                }
                main() {
                    val r = spawn { work() }
                    val v = await r
                    println(v)
                }
                """, "42", tempDir);
    }

    // #141 variante String do RELATO ORIGINAL (0.3.x dava null em runtime):
    // agora compila e o await devolve o valor do bloco.
    @Test
    void spawnBlockWithStringLiteral(@TempDir Path tempDir) throws IOException {
        both("s141c", """
                main() {
                    var h = spawn { "ok" }
                    var r = await h
                    if (r == null) {
                        println("FAIL: await returned null")
                    } else {
                        println("OK: " + r)
                    }
                }
                """, "OK: ok", tempDir);
    }

    // não-regressão: return EXPLÍCITO no bloco (bug 46) continua.
    @Test
    void spawnBlockWithExplicitReturnStillWorks(@TempDir Path tempDir) throws IOException {
        both("s141a", """
                main() {
                    var h = spawn { return "ok" }
                    var r = await h
                    println(r)
                }
                """, "ok", tempDir);
    }

    // não-regressão: corpo-void (println) — Handle VOID, await sem crash,
    // fire-and-forget intocado (spawn statement).
    @Test
    void spawnVoidBlockStaysVoid(@TempDir Path tempDir) throws IOException {
        both("s141v", """
                main() {
                    var h = spawn { println("bg") }
                    await h
                    println("done")
                }
                """, "bg\ndone", tempDir);
    }

    // borda: bloco com MAIS de uma statement SEM return — o corpo continua
    // void (closures.md §1: bloco exige return explícito); só a ÚNICA
    // expressão é implícita. await de Handle<Void> não crasha. Ordem
    // travada por await sequencial (spawn é concorrente).
    @Test
    void spawnStatementWithLastExprRuns(@TempDir Path tempDir) throws IOException {
        both("s141m", """
                main() {
                    var h1 = spawn { println("a"); println("b") }
                    await h1
                    var h2 = spawn { println("c") }
                    await h2
                    println("z")
                }
                """, "a\nb\nc\nz", tempDir);
    }
}
