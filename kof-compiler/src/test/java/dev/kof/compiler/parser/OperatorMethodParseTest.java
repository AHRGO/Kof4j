package dev.kof.compiler.parser;

import static org.junit.jupiter.api.Assertions.*;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #379 — a method named {@code operator+} (or any {@code operator<symbol>}) inside
 * a class body drove {@code TypeParser.parseFunctionTypeRef} into infinite mutual
 * recursion: the parameter loop called {@code parseTypeRef}, which returned
 * "Object" WITHOUT consuming the token, so the loop reallocated forever →
 * OutOfMemoryError / hang, never a diagnostic (R6). The loop now guards progress:
 * when a type parse consumes nothing it emits an honest parse error and advances,
 * so the compile fails fast with diagnostics instead of crashing the process.
 */
class OperatorMethodParseTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String OPERATOR_PLUS = """
            class Vec2 {
                Int x
                constructor(x: Int) { this.x = x }
                operator+(other: Vec2): Vec2 {
                    return Vec2(this.x + other.x)
                }
            }
            main() {
                println("hello")
            }
            """;

    @Test
    void operatorMethodFailsWithDiagnosticNotOom(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, OPERATOR_PLUS);
        // Q0: before the progress guard this call OOM'd / hung (never returns).
        // The preemptive timeout turns a hang into a test failure (not a wedge).
        CompilationResult result = assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
                driver.compile(source, tempDir.resolve("out"), Target.JVM),
                "#379: parse must terminate, not hang/OOM");
        assertFalse(result.success(), "operator+ is not valid Kof — must be rejected, not silently compiled");
        List<?> diags = result.diagnostics().getDiagnostics();
        assertFalse(diags.isEmpty(), "R6: rejection carries a diagnostic (never a silent fallback)");
        String all = diags.toString();
        assertFalse(all.contains("COMP002"), "must be a PARSE diagnostic, not an internal compiler error");
    }

    @Test
    void declaredFunctionTypeFieldStillParses(@TempDir Path tempDir) throws Exception {
        // #218 shape that shares the ( ) -> code path must stay legal (no regression).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                class Box {
                    (Int) -> Int transform
                    constructor(t: (Int) -> Int) { transform = t }
                    Int apply(Int v) { return transform(v) }
                }
                main() {
                    var b = Box((n: Int) -> n * 2)
                    println(b.apply(5))
                }
                """);
        CompilationResult result = assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
                driver.compile(source, tempDir.resolve("out"), Target.JVM));
        assertTrue(result.success(), "valid function-type field must compile: " + result.diagnostics().getDiagnostics());
    }
}
