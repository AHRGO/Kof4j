package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §211 / issue #207 / D-ENUM207 — enum identity.
 *
 * <p>Today an enum constant compiles to {@code ldc "N"} (a String) and NO
 * {@code Dir.class} is emitted, so {@code Dir.N.getClass()} returns
 * {@code java.lang.String} and {@code Dir.N == "N"} is {@code true}. The
 * maintainer ratified the semantic change (D-ENUM207): an enum value is an
 * enum instance, not a String.
 *
 * <p>This test encodes the contract. The first face is the shared-frontend
 * rejection of {@code enum == String} (a type error, uniform across the 4
 * targets — no half-landing, lesson §241); the identity faces follow the JVM
 * slice.
 */
class EnumIdentityE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String source, Target target) throws Exception {
        Path file = dir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        return driver.compile(file, dir.resolve("out-" + System.nanoTime()), target);
    }

    // ---- face 1: enum == String is a type error (D-ENUM207) ----

    @Test
    void enumComparedToStringIsRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                enum Dir { N, S }
                main() {
                    var d: Dir = Dir.N
                    println(d == "N")
                }
                """, Target.JVM);
        assertFalse(r.success(), "comparing an enum to a String must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d0 -> "SEM062".equals(d0.code())),
                "expected SEM062 (enum vs String comparison), got: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void stringComparedToEnumIsRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                enum Dir { N, S }
                main() {
                    var d: Dir = Dir.N
                    println("N" == d)
                }
                """, Target.JVM);
        assertFalse(r.success(), "comparing a String to an enum must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d0 -> "SEM062".equals(d0.code())),
                "expected SEM062, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void enumComparedToEnumStillCompiles(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                enum Dir { N, S }
                main() {
                    var d: Dir = Dir.N
                    println(d == Dir.N)
                    println(d != Dir.S)
                }
                """, Target.JVM);
        assertTrue(r.success(), "enum == enum must keep compiling: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void enumComparedToNullableEnumCompiles(@TempDir Path tmp) throws Exception {
        // valueOf can return null: `Dir? d = Dir.valueOf(s); d == Dir.N`
        CompilationResult r = compile(tmp, """
                enum Dir { N, S }
                main() {
                    var d: Dir? = Dir.valueOf("N")
                    if (d != null) {
                        println(d == Dir.N)
                    }
                }
                """, Target.JVM);
        assertTrue(r.success(), "enum vs nullable enum must keep compiling: "
                + r.diagnostics().getDiagnostics());
    }

    // ---- faces 2..n: enum is a real instance (slice 2, D-ENUM207) ----

    @Test
    void enumHasRealIdentityOnJvm(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("out-" + System.nanoTime());
        CompilationResult r = compileInto(tmp, """
                enum Dir { N, S, E }
                main() {
                    println(Dir.N.getClass())
                    println(Dir.N == Dir.N)
                    println(Dir.N == Dir.S)
                    var d: Dir = Dir.S
                    println(d instanceof Dir)
                    println(Dir.N.name())
                    println(Dir.S.ordinal())
                    println(Dir.N.compareTo(Dir.S))
                    val vs = Dir.values()
                    println(vs.size())
                    println(vs.get(0) == Dir.N)
                    println(Dir.valueOf("S") == Dir.S)
                    println(Dir.valueOf("nope") == null)
                }
                """, out);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        // a real enum class is emitted (was: no Dir.class at all)
        assertTrue(Files.exists(out.resolve("Dir.class")),
                "the enum class Dir.class must be emitted");
        String output = runJvm(out);
        assertEquals("""
                class Dir
                true
                false
                true
                N
                1
                -1
                3
                true
                true
                true""", output);
    }

    @Test
    void enumConstantCompilesToGetstatic(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("out-" + System.nanoTime());
        CompilationResult r = compileInto(tmp, """
                enum Dir { N, S }
                main() {
                    println(Dir.N)
                }
                """, out);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        // javap proof: `Dir.N` is a getstatic of the enum class, not `ldc "N"`.
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/javap",
                "-c", "-p", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String disasm = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(disasm.contains("getstatic") && disasm.contains("Dir.N:LDir;"),
                "expected `getstatic Dir.N:LDir;`, got:\n" + disasm);
        assertFalse(disasm.contains("String N"),
                "the old `ldc // String N` must be gone:\n" + disasm);
    }

    private CompilationResult compileInto(Path dir, String source, Path out) throws Exception {
        Path file = dir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        return driver.compile(file, out, Target.JVM);
    }

    private String runJvm(Path out) throws Exception {
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        return output;
    }
}
