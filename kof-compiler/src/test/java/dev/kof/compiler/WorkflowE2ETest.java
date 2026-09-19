package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kof.workflow 2.1.2 MVP golden (universal plan Stage 2, row 2.1; plan
 * docs/development/workflow-plan.md §5 2.1.2 — Q2 minimal surface: job, dag,
 * after, run, Report ONLY; retry/checkpoint/deadLetter are the 2.1.3 bundle).
 *
 * The host is pure Kof (`/dev/kof/workflow-host.kf`) injected flat by
 * `CompilerWorkflow` on `import kof.workflow` (DD-OTP-01 option A, same
 * mechanism as `kof.supervisor` — hence `job(...)`/`dag(...)`, not the
 * `workflow.` prefix the §2 sketch drew; the sign-off note says so). Nothing
 * here crosses a runtime boundary — no threads, no process — so the SAME
 * source executes byte-identical on JVM and JS, and compiles on Script and
 * Native (compile-pinned below; the honest PROC001/CRON001/ORM001 gaps of §4
 * live in the job BODIES the user writes, not in this layer).
 */
class WorkflowE2ETest {

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

    /** JVM+JS byte-parity (rule 5) + exact stdout golden. */
    private void assertJvmJsParity(String source, String... expected) throws Exception {
        Files.writeString(tmp.resolve("W.kf"), source);
        Run jvm = runJvm(tmp.resolve("W.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(tmp.resolve("W.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        assertEquals(jvm.output(), js.output(), "kof.workflow MVP: JVM/JS diverge");
        for (String e : expected) {
            assertTrue(jvm.output().contains(e), () -> "expected '" + e + "' in: " + jvm.output());
        }
    }

    /** The MVP headline: linear dependency chain runs in topological order
     *  regardless of input order, Report.summary/`allOk` agree on both faces. */
    @Test
    void linearDagRunsInOrderReportsOk() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var build = job("build", () -> true)
                var image = job("image", () -> true).after(build)
                var rep = dag(listOf(image, build)).run()
                println(rep.summary())
                println(rep.allOk())
            }
            """, "ok=build,image failed= skipped=", "true");
    }

    /** Failure cascades: failed job poisons its transitive dependents,
     *  independent branches still run (diamond with a side branch). */
    @Test
    void failureSkipsTransitiveDependentsOnly() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var build = job("build", () -> false)
                var image = job("image", () -> true).after(build)
                var ship = job("ship", () -> true).after(image)
                var lint = job("lint", () -> true)
                var rep = dag(listOf(build, image, ship, lint)).run()
                println(rep.summary())
                println(rep.allOk())
            }
            """, "ok=lint failed=build skipped=image,ship", "false");
    }

    /** A throwing body is caught, recorded as failed with the reason, and
     *  cascades like a false (errors face). NOTE: the body keeps an
     *  unreachable-looking `return false` because a block lambda whose only
     *  exit is `throw` types as Void ("expected 'function' but got
     *  'function'") — typer limitation, cataloged with the unit. */
    @Test
    void throwingBodyRecordedWithReason() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var boom = job("boom", () -> { if (true) { throw "kaboom" } return false })
                var after = job("after", () -> true).after(boom)
                var rep = dag(listOf(boom, after)).run()
                println(rep.summary())
                println(rep.errors.get(0))
            }
            """, "ok= failed=boom skipped=after", "boom: kaboom");
    }

    /** Cycle is rejected at RUN time with an actionable message (§2 invariant
     *  — never a silent hang), propagating as a plain Kof string throw. */
    @Test
    void cycleRejectedAtRunTimeWithMessage() throws Exception {
        Files.writeString(tmp.resolve("C.kf"), """
            import kof.workflow
            main() {
                var a = job("a", () -> true)
                var b = job("b", () -> true).after(a)
                var c = job("c", () -> true).after(b)
                a.after(c)
                dag(listOf(a, b, c)).run()
            }
            """);
        Run jvm = runJvm(tmp.resolve("C.kf"), tmp.resolve("c-jvm"));
        assertTrue(jvm.output().contains("ciclo detectado entre: a,b,c"),
                () -> "JVM cycle message wrong: " + jvm.output());
        Run js = runJs(tmp.resolve("C.kf"), tmp.resolve("c-js"));
        assertTrue(js.output().contains("ciclo detectado entre: a,b,c"),
                () -> "JS cycle message wrong: " + js.output());
    }

    /** Construction-time guards: dup name / null body / empty dag — clear
     *  errors, not UB. */
    @Test
    void dagGuardsThrowAtConstruction() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var x = job("x", () -> true)
                var y = job("y", () -> true)
                try { dag(listOf(x, y, x)).run() } catch (String e) { println(e) }
                try { job("nulo", null) } catch (String e) { println(e) }
                try { dag(listOf()) } catch (String e) { println(e) }
                try { x.after(x) } catch (String e) { println(e) }
            }
            """, "nome de job duplicado 'x'", "corpo nulo", "dag() vazia",
                "dependência de si mesmo");
    }

    /** Real bodies: a workflow whose jobs do measurable work — closures
     *  accumulate, chains compose — proving the layer is composition, not a
     *  toy (string building through dependent jobs, Report verifies order). */
    @Test
    void realBodiesComposeThroughDeps() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var acc = listOf("start")
                var build = job("build", () -> { acc.add("built"); return true })
                var image = job("image", () -> { acc.add("imaged"); return true }).after(build)
                var rep = dag(listOf(image, build)).run()
                println(rep.summary())
                println(acc.get(2))
            }
            """, "ok=build,image failed= skipped=", "imaged");
    }

    /** Rule-5 source portability: the same injected host compiles on Native
     *  (no runtime boundary in this layer — bodies decide). Script shares the
     *  exact same merge/injection pipeline (CompilerPipeline.interpret), so
     *  compilation is proven there by construction. */
    @Test
    void hostCompilesOnNative() throws Exception {
        Files.writeString(tmp.resolve("P.kf"), """
            import kof.workflow
            main() {
                var rep = dag(listOf(job("a", () -> true))).run()
                println(rep.summary())
            }
            """);
        CompilationResult nativeRes = driver.compile(tmp.resolve("P.kf"), tmp.resolve("p-native"), Target.NATIVE);
        assertTrue(nativeRes.success(), () -> "Native must compile the host: " + diags(nativeRes));
    }
}
