package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #334 — `List.map()` typed the result with the SOURCE element type in the
 * SEMANTIC pass (`MemberCallTyper`), while the emit already honored the
 * lambda's return (#149). The wrong cached type made `strs.get(0)` emit a
 * checkcast to the SOURCE type over the REAL value → silent
 * ClassCastException (the compile was clean). The semantic now mirrors the
 * emit: map → List<lambda-return>, reduce → lambda-return, filter → source
 * (correct). Cross-target parity is asserted (JVM/JS share the typer).
 */
class MapReturnElementTypeTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws Exception {
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code (a CheckCastException here IS the bug)");
        return out;
    }

    @Test
    void mapToIntToStringKeepsTheLambdaReturnTypeEndToEnd(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var ints = listOf(1, 2, 3)
                    var strs = ints.map((x: Int) -> "val=" + x)
                    println(strs.get(0))
                    println(strs.size)
                    var s = strs.get(0) + "!"
                    println(s)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile clean: " + result.diagnostics().getDiagnostics());
        // Q0: on the old typer `strs.get(0)` carried the SOURCE element type
        // (Int) and threw ClassCastException at runtime (exit != 0, caught in
        // runJvm). Golden = real measured output.
        assertEquals("val=1\n3\nval=1!", runJvm(tempDir.resolve("out")),
                "mapped element is a String — no checkcast to the source type");
    }

    @Test
    void mapSameTypeAndFilterAndReduceUnaffected(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var nums = listOf(1, 2, 3, 4)
                    var doubled = nums.map((x: Int) -> x * 2)
                    println(doubled.get(0))
                    var evens = nums.filter((x: Int) -> x % 2 == 0)
                    println(evens.size)
                    println(evens.get(0))
                    var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
                    println(total)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile clean: " + result.diagnostics().getDiagnostics());
        assertEquals("2\n2\n2\n10", runJvm(tempDir.resolve("out")),
                "same-type map, filter (keeps source type) and reduce (lambda return) all correct");
    }

    @Test
    void mapResultIsIndexableLikeTheSourceType(@TempDir Path tempDir) throws Exception {
        // the checkcast on the RESULT's element type — `[]` over the mapped
        // list must use the lambda's return, not the source (the #149 emit
        // already did; the semantic cache must agree so no wrong box/unbox).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var ints = listOf(1, 2)
                    var strs = ints.map((x: Int) -> "v" + x)
                    println(strs[0])
                    String direct = strs[0]
                    println(direct + "-ok")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must compile clean: " + result.diagnostics().getDiagnostics());
        assertEquals("v1\nv1-ok", runJvm(tempDir.resolve("out")),
                "indexing a mapped-to-String list yields the String (assignable to String)");
    }
}
