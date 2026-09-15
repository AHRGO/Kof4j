package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #264 — dois membros de classe com a MESMA assinatura JVM (mesmo nome +
 * mesmos tipos de parametro + mesmo retorno, APAGADOS a descritor) eram
 * ACEITOS em silencio e emitidos como dois metodos com par (name, descriptor)
 * identico no mesmo .class: JVMS §4.6 proibe, a classe NAO carrega
 * (ClassFormatError: Duplicate method name) — nunca houve diagnostico (R6).
 *
 * Casos medidos no tip antes do fix (jar fresco + launcher por reflexao):
 * - static go(Int):Int + instance go(Int):Int  → ClassFormatError "(I)I"
 * - instance + instance go(Int):Int            → ClassFormatError "(I)I"
 * - static + static go(Int):Int                → ClassFormatError "(I)I"
 * - construtores P(Int) x P(Int)               → ClassFormatError "<init>(I)V"
 * - record com go(Int) x go(Int)               → ClassFormatError "(I)I"
 * - interface I.run(Int) x run(Int)            → ClassFormatError "(I)I"
 * - go(List<Int>) x go(List<String>)           → ClassFormatError (mesmo
 *   apagamento — javac tambem rejeita: "same erasure")
 *
 * Fix espelha o SEM047 de funcao top-level (SG-011B, ja ratificado): DUPLICATA
 * EXATA = erro SEM061 na declaracao; sobrecarga por assinatura DIFERENTE
 * continua permitida (§131). Staticness NAO faz parte do descritor JVM —
 * static+instance com mesmos params colidem. Retorno faz (diferente retorno =
 * diferente descritor = passa, medido em m2: Int × String). Int? × Int =
 * descritores diferentes (boxed) e passa.
 *
 * Matriz Q3: os 6 + 1 casos acima (erro) + 3 nao-regressoes (overload legitimo
 * por tipo, por aridade, retorno diferente) + chamada com argumento (rejeicao
 * na declaracao, nao no call-site).
 */
class DuplicateMethodSignatureE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String file, String code) throws IOException {
        Path src = tempDir.resolve(file);
        Files.writeString(src, code);
        return driver.compile(src, tempDir.resolve("out-" + file), Target.JVM);
    }

    private void assertSem061(CompilationResult r, String shown) {
        assertFalse(r.success(), "duplicata exata nao pode compilar (geraria ClassFormatError): "
                + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM061".equals(d.code()) && d.message().contains(shown)),
                "esperava SEM060/61 sobre '" + shown + "', foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void staticPlusInstanceSameSignatureRejected(@TempDir Path tempDir) throws IOException {
        // o caso EXATO da issue #264
        assertSem061(compile(tempDir, "w.kf", """
                class W {
                    static Int go(Int x) { return x * 2 }
                    Int go(Int x) { return x * 3 }
                }
                main() {
                    println(W.go(5))
                }
                """), "go(Int)");
    }

    @Test
    void instancePlusInstanceSameSignatureRejected(@TempDir Path tempDir) throws IOException {
        assertSem061(compile(tempDir, "a.kf", """
                class C {
                    Int go(Int x) { return x }
                    Int go(Int x) { return x + 1 }
                }
                main() { println(1) }
                """), "go(Int)");
    }

    @Test
    void staticPlusStaticSameSignatureRejected(@TempDir Path tempDir) throws IOException {
        assertSem061(compile(tempDir, "b.kf", """
                class C {
                    static Int go(Int x) { return x }
                    static Int go(Int x) { return x + 1 }
                }
                main() { println(1) }
                """), "go(Int)");
    }

    @Test
    void duplicateConstructorRejected(@TempDir Path tempDir) throws IOException {
        assertSem061(compile(tempDir, "c.kf", """
                class P {
                    Int v
                    P(Int a) { v = a }
                    P(Int b) { v = b + 1 }
                }
                main() { println(1) }
                """), "constructor P(Int)");
    }

    @Test
    void duplicateInRecordRejected(@TempDir Path tempDir) throws IOException {
        assertSem061(compile(tempDir, "d.kf", """
                record R(Int a) {
                    Int go(Int x) { return x }
                    Int go(Int x) { return x + 1 }
                }
                main() { println(1) }
                """), "go(Int)");
    }

    @Test
    void duplicateInInterfaceRejected(@TempDir Path tempDir) throws IOException {
        assertSem061(compile(tempDir, "e.kf", """
                interface I {
                    Int run(Int x)
                    Int run(Int y)
                }
                main() { println(1) }
                """), "run(Int)");
    }

    @Test
    void sameErasureGenericsRejected(@TempDir Path tempDir) throws IOException {
        // go(List<Int>) × go(List<String>): mesmo apagamento → mesmo descritor
        // (java.util.ArrayList) → ClassFormatError; javac rejeita igual ("same erasure").
        assertSem061(compile(tempDir, "f.kf", """
                class C {
                    Int go(List<Int> x) { return 1 }
                    Int go(List<String> x) { return 2 }
                }
                main() { println(1) }
                """), "go(List<String>)");
    }

    @Test
    void legitimateOverloadsStillCompile(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("g.kf");
        Files.writeString(src, """
                class O {
                    Int go(Int x) { return x }
                    Int go(String x) { return 2 }
                    Int go(Int a, Int b) { return a + b }
                    String go2(Int x) { return "s" }
                    Int go2(Int x, Int y) { return x }
                }
                main() {
                    var o = new O()
                    println(o.go(1))
                    println(o.go("a"))
                    println(o.go(1, 2))
                    println(o.go2(3))
                    println(o.go2(3, 4))
                }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("outg"), Target.JVM);
        assertTrue(r.success(), "sobrecarga legitima (§131: tipos/aridade diferentes) nao pode virar erro: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void sameParamsDifferentReturnStillCompiles(@TempDir Path tempDir) throws IOException {
        // retorno faz parte do descritor — go(Int):Int × go(Int):String sao
        // descritores DIFERENTES (I)I × (I)String. Medido no tip ANTES do fix:
        // compila e carrega (sai "s" — o 2º define() vence); NAO e ClassFormatError.
        Path src = tempDir.resolve("h.kf");
        Files.writeString(src, """
                class C {
                    Int go(Int x) { return x }
                    String go(Int x) { return "s" }
                }
                main() { println(new C().go(1)) }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("outh"), Target.JVM);
        assertTrue(r.success(), "mesmo nome/params com retorno DIFERENTE tem descritores diferentes — nao e o bug da #264: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void nullableVsPrimitiveParamNotDuplicate(@TempDir Path tempDir) throws IOException {
        // go(Int) × go(Int?): descritores (I) × (Ljava/lang/Integer;) — diferentes
        // (Int? e boxed no JVM desde c0cf805e). Nao podem colidir na chave.
        Path src = tempDir.resolve("i.kf");
        Files.writeString(src, """
                class C {
                    Int go(Int x) { return 1 }
                    Int go(Int? y) { return 2 }
                }
                main() { println(1) }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("outi"), Target.JVM);
        assertTrue(r.success(), "Int × Int? tem descritores diferentes — nao e duplicata: "
                + r.diagnostics().getDiagnostics());
    }
}
