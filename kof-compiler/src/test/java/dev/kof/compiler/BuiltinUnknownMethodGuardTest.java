package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #490 — the `kof.io` guard of #617 was not the only pseudo-type family with a
 * silent fall-through. `Buffer(U8)` (`kof.buffer`) and `Secret`/`KeyHandle`
 * (`kof.security`) have dedicated typer branches: a method absent from their
 * live table fell through to the generic resolution, which kept no contract —
 * the emit returned the RECEIVER (silent no-op: `s.bogus()` printed the Secret)
 * or leaked an UNKNOWN whose empty class name blew up at load
 * (`ClassFormatError: Illegal class name ""`). Both faces are R6 violations:
 * exactly the #617 mechanism, one family over.
 *
 * <p>This class pins the contract: an unknown method (or a known method with
 * the WRONG arity) on those builtins is a clean `SEM102` at compile time, and
 * the live tables keep compiling.
 */
class BuiltinUnknownMethodGuardTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String source, Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return driver.compile(src, tempDir.resolve("out"), Target.JVM);
    }

    private void assertSem102(CompilationResult result, String method, String type) {
        assertFalse(result.success(), type + "." + method + "() must fail to compile (SEM102)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertTrue(diags.contains(method), "Diagnostic must name the method, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    // ---- diagnosis: the silent/ClassFormatError faces become SEM102 ----

    @Test
    void unknownBufferMethodFailsWithSem102(@TempDir Path tempDir) throws IOException {
        // symptom A: `buffer.alloc(8).bogus()` printed `Buffer[8]` (receiver).
        assertSem102(compile("""
            main() {
                var b = buffer.alloc(8)
                println(b.bogus())
            }
            """, tempDir), "bogus", "Buffer");
    }

    @Test
    void unknownSecretMethodFailsWithSem102(@TempDir Path tempDir) throws IOException {
        // symptom A: `secrets.of("x").bogus()` printed `Secret(*** )` (receiver).
        assertSem102(compile("""
            main() {
                var s = secrets.of("x")
                println(s.bogus())
            }
            """, tempDir), "bogus", "Secret");
    }

    @Test
    void unknownSecretMethodToStringFailsWithSem102NotClassFormatError(@TempDir Path tempDir) throws IOException {
        // symptom B: `.bogus(1,2)` compiled and the JVM aborted at load with
        // `ClassFormatError: Illegal class name ""`.
        assertSem102(compile("""
            main() {
                var s = secrets.of("x")
                println(s.bogus(1, 2))
            }
            """, tempDir), "bogus", "Secret");
    }

    @Test
    void unknownKeyHandleMethodFailsWithSem102(@TempDir Path tempDir) throws IOException {
        assertSem102(compile("""
            main() {
                var k = secrets.keyFromHex("00")
                println(k.bogus())
            }
            """, tempDir), "bogus", "KeyHandle");
    }

    @Test
    void knownMethodWithWrongArityIsSem102(@TempDir Path tempDir) throws IOException {
        // `reveal()` takes zero args; `s.reveal(1)` was accepted silently and
        // returned the receiver. Wrong arity on a pseudo-type is now diagnosed.
        assertSem102(compile("""
            main() {
                var s = secrets.of("x")
                println(s.reveal(1))
            }
            """, tempDir), "reveal", "Secret");
    }

    // ---- control: the live tables still compile ----

    @Test
    void validBufferAndSecretMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                var b = buffer.alloc(4)
                println(b.bytes())
                var s = secrets.of("hunter2")
                println(s.reveal())
                println(s.redacted())
                var k = secrets.keyFromHex("00")
                println(k.rotate())
            }
            """, tempDir);
        assertTrue(result.success(), "Valid Buffer/Secret/KeyHandle members must compile: "
                + result.diagnostics().getDiagnostics());
    }
}
