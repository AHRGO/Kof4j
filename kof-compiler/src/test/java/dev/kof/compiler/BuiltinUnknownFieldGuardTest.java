package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §491 — the FIELD sibling of #617/§490. The pseudo-types with a dedicated typer
 * branch ({@code Buffer}, {@code Secret}, {@code KeyHandle} from
 * kof.buffer/kof.security and {@code File}/{@code Path}/{@code Directory} from
 * kof.io) have NO property form: their accessors are methods. An unknown field
 * access compiled clean and fell through to the generic {@code ClassType} path,
 * which emitted {@code getfield <receiver>.<name>} against a class that does not
 * exist in the runtime ({@code kof/Buffer}, {@code kof/Secret},
 * {@code kof/io/File}) — a {@code NoClassDefFoundError} at class load (hidden
 * behind the JavaFX launcher message) that no diagnostic warned about.
 *
 * <p>This class pins the contract: an unknown field on those builtins is a clean
 * {@code SEM102} at compile time, and the valid property faces elsewhere
 * ({@code String.length}/{@code name}/{@code path}, {@code List.size},
 * {@code Map.size}, array {@code length}, record/class fields) keep compiling.
 */
class BuiltinUnknownFieldGuardTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String source, Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return driver.compile(src, tempDir.resolve("out"), Target.JVM);
    }

    private void assertSem102Field(CompilationResult result, String field, String type) {
        assertFalse(result.success(), type + "." + field + " must fail to compile (SEM102)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertTrue(diags.contains(type), "Diagnostic must name the type, was: " + diags);
        assertTrue(diags.contains(field), "Diagnostic must name the field, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    // ---- diagnosis: the invalid-bytecode faces become SEM102 ----

    @Test
    void unknownBufferFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var b = buffer.alloc(8)
                println(b.bogus)
            }
            """, tempDir), "bogus", "Buffer");
    }

    @Test
    void unknownSecretFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var s = secrets.of("x")
                println(s.bogus)
            }
            """, tempDir), "bogus", "Secret");
    }

    @Test
    void unknownKeyHandleFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var k = secrets.keyFromHex("00")
                println(k.bogus)
            }
            """, tempDir), "bogus", "KeyHandle");
    }

    @Test
    void unknownFileFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var f = File("x")
                println(f.bogus)
            }
            """, tempDir), "bogus", "File");
    }

    @Test
    void unknownPathFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var p = Path("x")
                println(p.bogus)
            }
            """, tempDir), "bogus", "Path");
    }

    @Test
    void unknownDirectoryFieldIsSem102(@TempDir Path tempDir) throws IOException {
        assertSem102Field(compile("""
            main() {
                var d = Directory("x")
                println(d.bogus)
            }
            """, tempDir), "bogus", "Directory");
    }

    @Test
    void plausibleButInvalidPathFieldIsSem102(@TempDir Path tempDir) throws IOException {
        // `path` is a METHOD (`File("x").path()`); the field form used to emit
        // `getfield kof/io/File.path` against a nonexistent class.
        assertSem102Field(compile("""
            main() {
                var f = File("x")
                println(f.path)
            }
            """, tempDir), "path", "File");
    }

    // ---- control: the real property faces still compile ----

    @Test
    void validPropertyFacesStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            record Point(Int x, Int y)
            main() {
                var s = "hello"
                println(s.length)
                println(s.name)
                println(s.path)
                var l = listOf(1, 2)
                println(l.size)
                var m = mapOf("a", 1)
                println(m.size)
                var a = new Int[3]
                println(a.length)
                var p = Point(1, 2)
                println(p.x)
            }
            """, tempDir);
        assertTrue(result.success(), "Valid property faces must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void ioAccessorsAsMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                var f = File("x")
                println(f.path())
                println(f.size())
                var p = Path("x")
                println(p.path())
            }
            """, tempDir);
        assertTrue(result.success(), "The io accessor METHODS must compile: "
                + result.diagnostics().getDiagnostics());
    }
}
