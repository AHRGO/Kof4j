package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §368 — a store de campo deve passar pelo MESMO gate de atributibilidade do
 * local. Antes, o branch {@code FieldAccessExpr} de
 * {@code StatementAnalyzer.analyzeAssignmentStatement} so resolvia o nome
 * (access/final) e NUNCA validava o {@code valueType} contra o tipo declarado
 * do campo: {@code x.n = "s"} em {@code Int n}, {@code x.n = 2.5} em
 * {@code Int n}, {@code x.c = "x"} em {@code Char c}/{@code Char? c}
 * compilavam "clean" e morriam em VerifyError na carga do JVM (e cast error no
 * Native), enquanto Script/JS imprimiam valores divergentes — R6 + regra 5.
 * Os controles positivos provam a regra 1: widening numerico (5 → Double,
 * 2.5 → Float), store de primitivo em {@code Int?} (a caixa do §361),
 * String {@code +=} e char literal continuam compilando byte-identico.
 */
class FieldAssignabilityPhantomE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private boolean compiles(Path dir, String source) throws Exception {
        Path file = dir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        CompilationResult r = driver.compile(file, dir.resolve("out-" + System.nanoTime()), Target.JVM);
        if (!r.success()) {
            String msg = String.valueOf(r.diagnostics().getDiagnostics());
            assertTrue(msg.contains("SEM012"), "rejeicao deve ser SEM012, veio: " + msg);
        }
        return r.success();
    }

    @Test
    void stringLiteralIntoCharFieldIsRejected(@TempDir Path dir) throws Exception {
        assertFalse(compiles(dir, "class C { Char c }\nmain() { var x = C(); x.c = \"x\"; println(x.c) }\n"),
                "String -> Char field deve ser SEM012 (morria em VerifyError na carga)");
    }

    @Test
    void stringLiteralIntoNullableCharFieldIsRejected(@TempDir Path dir) throws Exception {
        assertFalse(compiles(dir, "class C { Char? c }\nmain() { var x = C(); x.c = \"x\"; println(x.c) }\n"),
                "String -> Char? field deve ser SEM012 (face do adendo do §361)");
    }

    @Test
    void doubleIntoIntFieldIsRejected(@TempDir Path dir) throws Exception {
        assertFalse(compiles(dir, "class C { Int n }\nmain() { var x = C(); x.n = 2.5; println(x.n) }\n"),
                "Double -> Int field deve ser SEM012 (narrowing proibido como no local)");
    }

    @Test
    void stringIntoIntFieldIsRejected(@TempDir Path dir) throws Exception {
        assertFalse(compiles(dir, "class C { Int n }\nmain() { var x = C(); x.n = \"s\"; println(x.n) }\n"),
                "String -> Int field deve ser SEM012");
    }

    @Test
    void stringIntoStaticIntFieldIsRejected(@TempDir Path dir) throws Exception {
        assertFalse(compiles(dir, "class C { static Int n }\nmain() { C.n = \"s\"; println(C.n) }\n"),
                "String -> static Int field deve ser SEM012");
    }

    @Test
    void stringIntoInheritedFieldIsRejected(@TempDir Path dir) throws Exception {
        assertFalse(compiles(dir, "class B { Int n }\nclass D extends B { }\nmain() { var x = D(); x.n = \"s\"; println(x.n) }\n"),
                "String -> campo herdado deve ser SEM012 (resolveInHierarchy ja achava o campo)");
    }

    @Test
    void numericWideningIntoFieldsStillCompiles(@TempDir Path dir) throws Exception {
        assertTrue(compiles(dir, "class C { Double d; Float f; Int? n; Char? c; String s }\n"
                + "main() { var x = C(); x.d = 5; x.f = 2.5; x.n = 42; x.c = 'z'; x.s = \"a\"; x.s += \"b\"; println(x.n) }\n"),
                "widening/box/char-literal/String-+= continuam legais (regra 1)");
    }

    @Test
    void constructorAndThisStoresStillCompile(@TempDir Path dir) throws Exception {
        assertTrue(compiles(dir, "class U {\n    String name\n    Int age\n    public constructor(String name, Int age) {\n"
                + "        this.name = name\n        this.age = age\n    }\n"
                + "    String greeting() { return \"Hello \" + name }\n"
                + "}\nmain() { var u = U(\"Mel\", 26); println(u.greeting()) }\n"),
                "construtor com campos declarados (AGENTS.md) nao pode regressir");
    }
}
