package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §CodeQL local-variable-is-never-read #166 — `analyzeFunction` salvava
 * `prevFunction = currentFunctionName` e NUNCA restaurava: o nome do ultimo
 * main/funcao top-level VAZAVA para as declaracoes ANALISADAS DEPOIS dele, e
 * `args` (valido apenas em main — SemExpressionTyper:131) era aceito em
 * metodo de classe depois de um main no mesmo modulo. O fix completa o
 * save/restore (currentFunctionName = prevFunction no fim do corpo).
 */
class CurrentFunctionLeakTest {
    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tmp, String name, String src) throws IOException {
        Path source = tmp.resolve(name);
        Files.writeString(source, src);
        return driver.compile(source, tmp.resolve("out"), Target.JVM);
    }

    @Test
    void argsAfterMainRejectedInClassMethod(@TempDir Path tmp) throws IOException {
        // main() vem ANTES: sem o restore, currentFunctionName segue "main"
        // e show() engole `args` (cravado no CLI antes do fix: "no errors").
        CompilationResult r = compile(tmp, "Leak.kf", """
                main() { println("m") }
                class A {
                    public constructor() { }
                    show() { println(args.length) }
                }
                """);
        assertFalse(r.success(), "args fora de main deve ser SEM011, compilou: "
                + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM011".equals(d.code()) && d.message().contains("args")),
                "esperava SEM011 sobre 'args', foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void argsInsideMainStillAccepted(@TempDir Path tmp) throws IOException {
        // Q3 par do teste anterior: o caminho feliz NAO pode regredir.
        CompilationResult r = compile(tmp, "Ok.kf", """
                main() { println(args.length) }
                class A {
                    public constructor() { }
                    show() { println("ok") }
                }
                """);
        assertTrue(r.success(), "args em main deve compilar: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void argsInFunctionAfterMainRejected(@TempDir Path tmp) throws IOException {
        // Guarda de par (Q3): cada funcao top-level define o PROPRIO nome ao
        // ser analisada — o restore nao pode ter virado "sempre main" nem
        // "nunca main". g() NAO-main ve SEM011 em `args` nos dois casos.
        CompilationResult r = compile(tmp, "Two.kf", """
                main() { println("m") }
                Int g() { return args.length }
                """);
        assertFalse(r.success(), "args em func NAO-main deve ser SEM011, foi: "
                + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM011".equals(d.code()) && d.message().contains("args")),
                "esperava SEM011 sobre 'args', foi: " + r.diagnostics().getDiagnostics());
    }
}
