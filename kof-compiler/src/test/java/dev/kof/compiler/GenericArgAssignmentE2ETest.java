package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §270 / #401 (D-POLL-19, DECISIONS.md 18/09) — {@code List<Int>} era
 * atribuído a {@code List<String>} sem diagnóstico: {@code isAssignable}
 * só comparava o RAW ({@code kof.List} vs {@code kof.List}) e os type-args
 * nunca eram consultados. O {@code get(0)} devolvia Integer num slot
 * tipado String → {@code ClassCastException} em runtime (corrupção
 * silenciosa de tipo — R6). Agora a atribuição é REJEITADA em compile
 * time (SEM012) quando os dois lados têm args CONCRETOS e o mesmo raw.
 *
 * <p>Invariantes conservadoras (não podem regredir): inferência
 * ({@code listOf()}/{@code listOf(1)} p/ {@code List<Int>}), raw de um lado
 * ({@code List x = nums}), alvo {@code Object}, classe→interface genérica
 * (#400 {@code IntToString -> Converter<Int,String>}) e args UNKNOWN.
 */
class GenericArgAssignmentE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String source) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        return driver.compile(src, tempDir.resolve(name + "-out"), Target.JVM);
    }

    private void assertAccepted(Path tempDir, String name, String source) throws IOException {
        CompilationResult r = compile(tempDir, name, source);
        assertTrue(r.success(), name + " deve compilar: " + r.diagnostics().getDiagnostics());
    }

    private void assertRejected(Path tempDir, String name, String source) throws IOException {
        CompilationResult r = compile(tempDir, name, source);
        assertFalse(r.success(), name + " deve ser REJEITADO em compile time (#401)");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d ->
                        d.code() != null && (d.code().equals("SEM012") || d.code().equals("SEM021"))
                                && (d.message().contains("List") || d.message().contains("Map"))),
                name + " precisa de SEM0xx citando os tipos: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void verbatimIssue401ListIntToRejectedOnString(@TempDir Path tempDir) throws IOException {
        assertRejected(tempDir, "g401p", """
                main() {
                    var nums = listOf(1, 2, 3)
                    var strs: List<String> = nums
                    println(strs.get(0))
                }
                """);
    }

    @Test
    void rejectionAppliesToPlainAssignmentToo(@TempDir Path tempDir) throws IOException {
        assertRejected(tempDir, "g401a", """
                main() {
                    var nums = listOf(1, 2, 3)
                    var strs: List<String> = listOf("a")
                    strs = nums
                    println(strs.size)
                }
                """);
    }

    @Test
    void nestedArgsRejected(@TempDir Path tempDir) throws IOException {
        assertRejected(tempDir, "g401n", """
                main() {
                    var a: Map<String, List<Int>> = mapOf("k", listOf(1))
                    var b: Map<String, List<String>> = a
                    println(b.size)
                }
                """);
    }

    @Test
    void sameArgsStillAccepted(@TempDir Path tempDir) throws IOException {
        assertAccepted(tempDir, "g401s", """
                main() {
                    var nums = listOf(1, 2, 3)
                    var ints: List<Int> = nums
                    println(ints.size)
                }
                """);
    }

    @Test
    void inferenceAndRawAndObjectStayLenient(@TempDir Path tempDir) throws IOException {
        assertAccepted(tempDir, "g401i", """
                main() {
                    var e: List<String> = listOf()
                    println(e.size)
                    var nums = listOf(1, 2)
                    var raw: List = nums
                    println(raw.size)
                    var o: Object = nums
                    println(o)
                }
                """);
    }

    @Test
    void unknownArgSideStaysLenient(@TempDir Path tempDir) throws IOException {
        // `guess` nunca e lido — o lado UNKNOWN do type-arg fica permissivo
        assertAccepted(tempDir, "g401u", """
                main() {
                    var guess: List<Int> = listOf()
                    println(guess.isEmpty())
                    var raw: List = listOf(1)
                    println(raw.size)
                }
                """);
    }

    @Test
    void concreteBothSidesDifferentEvenNumericRejected(@TempDir Path tempDir) throws IOException {
        // mapa honesto: mapOf("k", 1) INFERE Map<String, Int> (nada de
        // unknown) — Map<String,Int> -> Map<String,Long> e concreto vs
        // concreto: rejeitado pelo MESMO rule (nao e um lado unknown)
        assertRejected(tempDir, "g401m", """
                main() {
                    var pair = mapOf("k", 1)
                    var guess: Map<String, Long> = pair
                    println(guess.size)
                }
                """);
    }

    @Test
    void classToGenericInterfaceStillAccepted(@TempDir Path tempDir) throws IOException {
        assertAccepted(tempDir, "g400c", """
                interface Converter<A, B> {
                    convert(input: A): B
                }
                class IntToString implements Converter<Int, String> {
                    convert(input: Int): String { return input.toString() }
                }
                main() {
                    var c: Converter<Int, String> = IntToString()
                    println(c.convert(7))
                }
                """);
    }
}
