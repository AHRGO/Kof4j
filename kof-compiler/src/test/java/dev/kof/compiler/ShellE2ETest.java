package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kof.shell MVP (universal plan Stage 2, row 2.2, slices 2.2.0-2.2.2).
 *
 * The golden contract is the argv-as-list: a command is never concatenated
 * into a string for a shell to re-parse (injection class), so the literal
 * "a b|c && d" must survive intact as ONE argv element. `run` lowers onto the
 * existing kof_process_run and therefore carries JVM+JS byte-for-byte parity
 * (the §239 discipline — same output, not just same code); `cmd` builds the
 * argv list; `ok` is pure field/compare IR on kof.process's Result.
 * `pipeline` chains live pipes: real on JVM AND on the JS host (chain + pump
 * threads in KofJsProcessBridge, 20/09 — byte-parity pinned below); Native
 * keeps the honest compile-time PROC001, like DomainGapCodesTest pins process.
 * `runWith` (2.2.3) adds cwd + additive env on both targets, with honest -1
 * Results for spawn errors — never a hang, never a silent success (R6).
 */
class ShellE2ETest {

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

    /** Both targets must run the same Kof source to the SAME output. */
    private void assertJvmJsParity(String source, String... expectedInOutput) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), source);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(tmp.resolve("S.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : expectedInOutput) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
            assertEquals(jvm.output(), js.output(), "§2.2 JVM/JS parity broken");
        }
    }

    @Test
    void runCapturesStdoutExitAndOk() throws Exception {
        assertJvmJsParity("""
            main() {
                var r = shell.run("echo", listOf("hello", "shell"))
                println(r.stdout.trim())
                println(r.exitCode)
                println(shell.ok(r))
            }
            """, "hello shell", "0", "true");
    }

    @Test
    void runWithNoArgsIsHonest() throws Exception {
        assertJvmJsParity("""
            main() {
                var r = shell.run("pwd")
                println(r.exitCode)
                println(shell.ok(r))
            }
            """, "0", "true");
    }

    @Test
    void argvIsNeverConcatenatedIntoShellString() throws Exception {
        assertJvmJsParity("""
            main() {
                var r = shell.run("echo", listOf("a b|c && d"))
                print("[" + r.stdout.trim() + "]")
            }
            """, "[a b|c && d]");
    }

    @Test
    void cmdBuildsArgvListAndFeedsRun() throws Exception {
        assertJvmJsParity("""
            main() {
                var argv = shell.cmd("echo", listOf("from cmd"))
                println(argv.size())
                println(argv.get(0))
                var r = shell.run(argv.get(0), listOf("from cmd"))
                println(r.stdout.trim())
                println(shell.ok(r))
            }
            """, "2", "echo", "from cmd", "true");
    }

    @Test
    void failingCommandPropagatesExitCodeNotException() throws Exception {
        assertJvmJsParity("""
            main() {
                var r = shell.run("false")
                println(shell.ok(r))
                println(r.exitCode)
            }
            """, "false", "1");
    }

    @Test
    void pipelineChainsStdoutToStdinOnJvm() throws Exception {
        Files.writeString(tmp.resolve("S.kf"), """
            main() {
                var p = shell.pipeline(listOf(listOf("echo", "one two three"), listOf("wc", "-w")))
                println(p.stdout.trim())
                println(p.exitCode)
                println(shell.ok(p))
            }
            """);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM pipeline failed: " + jvm.output());
        assertTrue(jvm.output().contains("3"), "wc -w of 'one two three': " + jvm.output());
        assertTrue(jvm.output().contains("0"), "exit code: " + jvm.output());
    }

    @Test
    void pipelineChainsStdoutToStdinOnJvmAndJs() throws Exception {
        assertJvmJsParity("""
            main() {
                var p = shell.pipeline(listOf(listOf("echo", "one two three"), listOf("wc", "-w")))
                println(p.stdout.trim())
                println(p.exitCode)
                println(shell.ok(p))
            }
            """, "3", "0", "true");
    }

    @Test
    void pipelineThreeStageChainFlowsThroughBothPumps() throws Exception {
        assertJvmJsParity("""
            main() {
                var p = shell.pipeline(listOf(
                    listOf("echo", "a b"),
                    listOf("tr", "a-z", "A-Z"),
                    listOf("wc", "-w")))
                println(p.stdout.trim())
                println(shell.ok(p))
            }
            """, "2", "true");
    }

    @Test
    void runOnNativeIsHonestProc001() throws Exception {
        assertGap(Target.NATIVE, "PROC001", """
            main() {
                var r = shell.run("echo", listOf("hi"))
                println(r.stdout)
            }
            """);
    }

    @Test
    void cmdOnNativeIsHonestProc001() throws Exception {
        assertGap(Target.NATIVE, "PROC001", """
            main() {
                var a = shell.cmd("echo", listOf("hi"))
                println(a.size())
            }
            """);
    }

    @Test
    void pipelineOnNativeIsHonestProc001() throws Exception {
        assertGap(Target.NATIVE, "PROC001", """
            main() {
                var p = shell.pipeline(listOf(listOf("echo", "hi"), listOf("wc", "-l")))
                println(p.stdout)
            }
            """);
    }

    @Test
    void unknownShellMethodIsSem025() throws Exception {
        assertGap(Target.JVM, "SEM025", """
            main() {
                println(shell.frobnicate("x"))
            }
            """);
    }

    /** 2.2.3 — runWith(argv, cwd, env) real on JVM+JS with byte parity:
     *  `pwd` proves cwd, `printenv` proves the ADDITIVE env (child inherits
     *  the parent and the map's keys override — never a silent env wipe). */
    @Test
    void runWithCwdAndEnvParityJvmJs() throws Exception {
        Files.writeString(tmp.resolve("RW.kf"), """
            main() {
                var r = shell.runWith(listOf("pwd"), "%1$s", mapOf("KOF_SHELL23", "on"))
                println(r.exitCode)
                println(r.stdout.trim())
                var e = shell.runWith(shell.cmd("printenv", listOf("KOF_SHELL23")), "", mapOf("KOF_SHELL23", "on"))
                println(e.stdout.trim())
                var p = shell.runWith(shell.cmd("printenv", listOf("PATH")), "", mapOf())
                if (p.stdout.length() > 0) {
                    println("inherited")
                } else {
                    println("wiped")
                }
            }
            """.formatted(tmp.toString()));
        Run jvm = runJvm(tmp.resolve("RW.kf"), tmp.resolve("o-rw-jvm"));
        assertTrue(jvm.ok(), () -> "JVM runWith: " + jvm.output());
        Run js = runJs(tmp.resolve("RW.kf"), tmp.resolve("o-rw-js"));
        assertTrue(js.ok(), () -> "JS runWith: " + js.output());
        assertEquals(jvm.output(), js.output(), "2.2.3 JVM==JS byte parity");
        assertTrue(jvm.output().contains(tmp.getFileName().toString()),
                () -> "cwd missing in: " + jvm.output());
        assertTrue(jvm.output().contains("on"), () -> "env missing in: " + jvm.output());
        assertTrue(jvm.output().contains("inherited"), () -> "env wipe in: " + jvm.output());
    }

    /** 2.2.3 — missing cwd and empty argv are HONEST Results (stderr non-empty,
     *  exit -1) on both targets, never a hang and never a silent success (R6). */
    @Test
    void runWithHonestFailuresJvmJs() throws Exception {
        Files.writeString(tmp.resolve("RWF.kf"), """
            main() {
                var r = shell.runWith(listOf("pwd"), "/nonexistent-kof-2-2-3", mapOf())
                println(r.exitCode)
                var hasErr = "no"
                if (r.stderr.length() > 0) {
                    hasErr = "yes"
                }
                println(hasErr)
                var e = shell.runWith(listOf(), "/tmp", mapOf())
                println(e.exitCode)
                var hasMsg = "no"
                if (e.stderr.contains("empty argv")) {
                    hasMsg = "yes"
                }
                println(hasMsg)
            }
            """);
        Run jvm = runJvm(tmp.resolve("RWF.kf"), tmp.resolve("o-rwf-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        Run js = runJs(tmp.resolve("RWF.kf"), tmp.resolve("o-rwf-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(jvm.output(), js.output(), "failure shapes must also be byte-parity");
        assertTrue(jvm.output().contains("-1"), () -> "expected exit -1: " + jvm.output());
        assertTrue(jvm.output().contains("yes"), () -> "expected honest stderr: " + jvm.output());
    }

    /** 2.2.3 — Native stays an honest PROC001 gap (waits for the native lane's
     *  process.run; no silent stub — the §129 rule). */
    @Test
    void runWithOnNativeIsHonestProc001() throws Exception {
        assertGap(Target.NATIVE, "PROC001", """
            main() {
                var r = shell.runWith(shell.cmd("make", listOf("-j4")), "/src", mapOf("CC", "clang"))
                println(r.stdout)
            }
            """);
    }

    /** 2.2.3 — wrong arity/types on runWith fall through to SEM025 (the
     *  dispatcher never guesses shapes). */
    @Test
    void runWithWrongShapeIsSem025() throws Exception {
        assertGap(Target.JVM, "SEM025", """
            main() {
                var r = shell.runWith(listOf("ls"), "/tmp", "CC=clang")
                println(r.exitCode)
            }
            """);
    }

    private void assertGap(Target target, String code, String source) throws Exception {
        Files.writeString(tmp.resolve("G.kf"), source);
        CompilationResult r = driver.compile(tmp.resolve("G.kf"), tmp.resolve("o-" + target), target);
        assertFalse(r.success(), target + " must refuse the call (" + code + ")");
        Set<String> codes = new LinkedHashSet<>();
        r.diagnostics().getDiagnostics().forEach(d -> codes.add(String.valueOf(d.code())));
        assertTrue(r.diagnostics().getDiagnostics().toString().contains(code)
                        || codes.contains(code),
                () -> "expected " + code + " for " + target + ", got: " + diags(r));
    }
}
