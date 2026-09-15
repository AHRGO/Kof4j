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
}
