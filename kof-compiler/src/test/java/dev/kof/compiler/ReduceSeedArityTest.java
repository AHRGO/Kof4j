package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #361 (§269(b)) — `list.reduce((a, b) -> a + b)` WITHOUT a seed compiled
 * silently and the JVM backend died inside ASM (NegativeArraySizeException -1
 * in Frame.merge): the runtime signature is (list, seed, fn) with three
 * parameters but the 1-arg call stacked only two. Kof's documented contract
 * ALWAYS passes the seed, in either order (training/idioms/collections.md:
 * "(lambda, init)" and "(init, lambda) is also accepted"). The shared
 * semantic pass now rejects wrong arity with SEM073 (one gate, four targets —
 * same face as SEM072/#336).
 */
class ReduceSeedArityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String program, Target t) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + t), t);
    }

    @Test
    void reduceWithoutSeedIsRejectedSem073(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, """
                main() {
                    var nums = listOf(1, 2, 3, 4, 5)
                    var sum = nums.reduce((acc: Int, n: Int) -> acc + n)
                    println(sum)
                }
                """, Target.JVM);
        assertFalse(r.success(), "#361 verbatim: 1-arg reduce must fail at compile time (old code died in ASM Frame.merge)");
        String d = r.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("SEM073"), "must carry SEM073: " + d);
        assertTrue(d.contains("seed"), "message must name the missing argument: " + d);
    }

    @Test
    void reduceWithTooManyArgsIsRejected(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, """
                main() {
                    var nums = listOf(1, 2)
                    var sum = nums.reduce((a: Int, b: Int) -> a + b, 0, 1)
                    println(sum)
                }
                """, Target.JVM);
        assertFalse(r.success(), "3-arg reduce does not exist either");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM073"));
    }

    @Test
    void wrongArityRejectedOnAllArtifactTargets(@TempDir Path tempDir) throws Exception {
        // rule 5: same diagnostic on every backend — the gate is the shared
        // typer (precedent SEM072/#336, CatchTypeCheck/#332).
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, """
                    main() {
                        var l = listOf(1, 2)
                        l.reduce((a: Int, b: Int) -> a + b)
                    }
                    """, t);
            assertFalse(r.success(), t + ": seedless reduce must be rejected");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM073"),
                    t + ": must be SEM073 (universal gate): " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void bothDocumentedSeedOrdersStillWork(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): the two orders promised by
        // training/idioms/collections.md keep compiling AND running.
        CompilationResult r = compile(tempDir, """
                main() {
                    var nums = listOf(1, 2, 3, 4, 5)
                    var a = nums.reduce((acc: Int, x: Int) -> acc + x, 0)
                    var b = nums.reduce(0, (acc: Int, x: Int) -> acc + x)
                    println(a)
                    println(b)
                }
                """, Target.JVM);
        assertTrue(r.success(), "seeded reduce (both orders) must stay legal: "
                + r.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-JVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals("15\n15", out, "seeded reduce semantics unchanged (golden 15 = 1+2+3+4+5)");
    }
}
