package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #156/#216 — `String.format(String, Object...)`: the compiler resolved the
 * call as a fixed-arity overload by name+arity only (no ACC_VARARGS), so the
 * JVM emitted e.g. `(String,String,int)Object` and died with
 * `NoSuchMethodError: java.lang.Object java.lang.String.format(...)`.
 * The fix packs the extra arguments into an `Object[]` and emits the real
 * descriptor `(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;`.
 *
 * JVM-only on purpose: the JS backend has no `String.format` lowering at all
 * (pre-existing gap, catalogued as §236 — lane JS). Claiming JS parity here
 * would be false green (Q5).
 */
public class StringFormatVarargsE2ETest {

    @TempDir Path tmp;

    private record Result(boolean success, String output) {}

    private Result runJvm(String code) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), code);
        Path out = Files.createTempDirectory(tmp, "o");
        CompilationResult r = new CompilerDriver().compile(tmp.resolve("S.kf"), out, Target.JVM);
        if (!r.success()) {
            StringBuilder sb = new StringBuilder();
            r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
            return new Result(false, sb.toString());
        }
        var oldOut = System.out;
        var buf = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new java.net.URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Result(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Result(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private void assertJvm(String code, String... expected) throws Exception {
        Result r = runJvm(code);
        assertTrue(r.success(), () -> "compile/run failed: " + r.output());
        for (String e : expected) {
            assertTrue(r.output().contains(e), () -> "expected '" + e + "' in: " + r.output());
        }
    }

    @Test
    void formatTwoArgs() throws Exception {
        assertJvm("""
            main() {
                var s = String.format("Hello %s, age %d", "Alice", 30)
                println(s)
            }
            """, "Hello Alice, age 30");
    }

    @Test
    void formatZeroArgs() throws Exception {
        assertJvm("""
            main() {
                var s = String.format("Hello World")
                println(s)
            }
            """, "Hello World");
    }

    @Test
    void formatIntStringBool() throws Exception {
        assertJvm("""
            main() {
                var s = String.format("int=%d, str=%s, bool=%s", 42, "x", true)
                println(s)
            }
            """, "int=42", "str=x", "bool=true");
    }

    @Test
    void formatThreeArgs() throws Exception {
        assertJvm("""
            main() {
                var s = String.format("%s:%d:%s", "id", 1, "active")
                println(s)
            }
            """, "id:1:active");
    }

    @Test
    void formatInExpression() throws Exception {
        assertJvm("""
            main() {
                println(String.format("Result: %s=%d", "sum", 1 + 2))
            }
            """, "Result: sum=3");
    }

    @Test
    void formatWithDouble() throws Exception {
        assertJvm("""
            main() {
                println(String.format("d=%s", 3.5))
            }
            """, "d=3.5");
    }

    @Test
    void formatRepeatedCallsAreIdempotent() throws Exception {
        assertJvm("""
            main() {
                for (var i in listOf(1, 2)) {
                    println(String.format("run %d", i))
                }
            }
            """, "run 1", "run 2");
    }

    @Test
    void formatWithVariableFormatString() throws Exception {
        assertJvm("""
            main() {
                var fmt = "%s -> %d"
                println(String.format(fmt, "k", 7))
            }
            """, "k -> 7");
    }

    @Test
    void formatWithCharArgument() throws Exception {
        assertJvm("""
            main() {
                println(String.format("c=%c", 'A'))
            }
            """, "c=A");
    }
}