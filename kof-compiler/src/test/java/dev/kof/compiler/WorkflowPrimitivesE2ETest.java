package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kof.workflow 2.1.0 RECON (universal plan Stage 2, row 2.1; plan
 * docs/development/future/workflow-plan.md §5).
 *
 * Freezes the §4 primitive table into a test-backed note WITHOUT shipping a
 * surface. The per-target cells were already locked by existing tests and are
 * cited, not duplicated: process run/spawn gaps (`DomainGapCodesTest`
 * processRun/SpawnOnNative/SpawnOnJs), cron `CRON001` + the real
 * `scheduler.at("cron5f", () -> ...)` shape (`KofTimeE2ETest` 1110-1228),
 * ORM on native `ORM001` + JS (`KofOrmE2ETest`), `process.run` JVM+JS parity
 * (`CoreRegressionE2ETest.processRun` F4 runBoth).
 *
 * What this file does lock is the OTHER half of 2.1.0: which of the §2
 * sketch's shapes are real Kof syntax (measured here, not assumed). Outcome
 * already fed back into both plan docs: job functions are typed lambdas,
 * argv is `listOf(...)` / variadic strings — bracket list literals `["x"]`
 * and `{...}` map literals are NOT Kof, and durations have no `1s` literal.
 */
class WorkflowPrimitivesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private void assertJvmJsParity(String source, String... expected) throws Exception {
        Files.writeString(tmp.resolve("W.kf"), source);
        Run jvm = runJvm(tmp.resolve("W.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(tmp.resolve("W.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : expected) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
        }
        assertEquals(jvm.output(), js.output(), "2.1.0 recon: JVM/JS shapes diverge");
    }

    /** The job idiom the plan adopts: typed lambdas that run real `process.run`,
     *  composed through `listOf(...).map(...)` — pure today-Kof, no new syntax. */
    @Test
    void jobLambdaShapesRunOnJvmAndJs() throws Exception {
        assertJvmJsParity("""
            main() {
                var zeroArg = () -> process.run("echo", "dag-ok")
                var oneArg = (ctx: String) -> process.run("echo", ctx)
                println(zeroArg().stdout.trim())
                println(oneArg("built").stdout.trim())
                var jobs = listOf("build", "image")
                println(jobs.map((x: String) -> x + ":done").get(0))
                println(jobs.size())
            }
            """, "dag-ok", "built", "build:done", "2");
    }

    /** The schedule shape: 5-field cron + () -> callback compiles on JVM and JS
     *  (real firing already covered by KofTimeE2ETest; native CRON001 pinned there). */
    @Test
    void cronScheduleShapeCompilesOnJvmAndJs() throws Exception {
        Files.writeString(tmp.resolve("W.kf"), """
            main() {
                var id = scheduler.at("0 3 * * *", () -> println("tick"))
                println(id)
            }
            """);
        CompilationResult jvm = driver.compile(tmp.resolve("W.kf"), tmp.resolve("c-jvm"), Target.JVM);
        assertTrue(jvm.success(), () -> "JVM cron shape must compile: " + diags(jvm));
        CompilationResult js = driver.compile(tmp.resolve("W.kf"), tmp.resolve("c-js"), Target.JS);
        assertTrue(js.success(), () -> "JS cron shape must compile: " + diags(js));
    }

    /** Dead-letter / checkpoint storage shapes: mapOf handles (NOT brace map
     *  literals — pinned negative below) plus orm-style calls are not probed
     *  here; this test locks the list-of-records substrate: records inside
     *  listOf, read back through the chain. */
    @Test
    void recordListSubstrateRuns() throws Exception {
        assertJvmJsParity("""
            main() {
                var failures = listOf(listOf("image", "boom"), listOf("publish", "late"))
                println(failures.size())
                println(failures.get(0).get(1))
            }
            """, "2", "boom");
    }

    /** NEGATIVE pin — the sketch's original form: `["x"]` is NOT a Kof list
     *  literal (measured in the 2.2.2 test loop: "Unexpected token"). The plan
     *  docs must keep `listOf(...)`. */
    @Test
    void bracketListLiteralIsNotKofSyntax() throws Exception {
        Files.writeString(tmp.resolve("N.kf"), """
            main() {
                var a = ["x"]
                println(a.size())
            }
            """);
        CompilationResult r = driver.compile(tmp.resolve("N.kf"), tmp.resolve("n-out"), Target.JVM);
        assertFalse(r.success(), "bracket list must NOT parse");
    }

    /** NEGATIVE pin — `{...}` is NOT a Kof map literal (mapOf is the form);
     *  a stdlib workflow must not advertise brace maps in examples. */
    @Test
    void braceMapLiteralIsNotKofSyntax() throws Exception {
        Files.writeString(tmp.resolve("N.kf"), """
            main() {
                var m = {"k": "v"}
                println(m.get("k"))
            }
            """);
        CompilationResult r = driver.compile(tmp.resolve("N.kf"), tmp.resolve("n-out2"), Target.JVM);
        assertFalse(r.success(), "brace map literal must NOT parse");
    }

    /** NEGATIVE pin — no `1s` duration literal; backoff units are plain
     *  millisecond Ints (measured: `var d = 1s` fails to parse). */
    @Test
    void durationLiteralSuffixIsNotKofSyntax() throws Exception {
        Files.writeString(tmp.resolve("N.kf"), """
            main() {
                var d = 1s
                println(d)
            }
            """);
        CompilationResult r = driver.compile(tmp.resolve("N.kf"), tmp.resolve("n-out3"), Target.JVM);
        assertFalse(r.success(), "duration suffix must NOT parse");
    }
}
