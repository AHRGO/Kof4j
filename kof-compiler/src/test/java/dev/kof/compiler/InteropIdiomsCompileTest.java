package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the code in {@code training/idioms/interop.md} section (d): the D6
 * struct/array/out-buffer shapes must compile on the JVM before they are
 * documented, and must stay honest gaps (FFI001/FFI002) on Native/JS.
 * Same discipline as {@link StdlibIdiomsCompileTest} for `stdlib.md`.
 */
class InteropIdiomsCompileTest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Exactly the shapes shown in interop.md (d). */
    private static final String DOC_SHAPES = """
            record Pt(Int x, Int y)
            extern "libshapes.so" mkpt(Int x, Int y): Pt
            extern "libshapes.so" ptlen(Pt p): Int
            extern "libshapes.so" sumn(Int[] xs, Int n): Int
            extern "libshapes.so" fill(Buffer(U8) b, Int n): Int

            main() {
                var xs = new Int[3]
                xs[0] = 1
                var b = buffer.alloc(4)
                fill(b, 4)
                println(b.bytes())
                println(ptlen(Pt(1, 2)))
            }
            """;

    @Test
    void jvmShapeExamplesCompile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("interop.kf");
        Files.writeString(src, DOC_SHAPES);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "interop.md (d) shapes must compile on JVM: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void nativeShapeExamplesStayHonest(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("interopnat.kf");
        Files.writeString(src, DOC_SHAPES);
        CompilationResult r = driver.compile(src, dir.resolve("out-nat"), Target.NATIVE);
        assertFalse(r.success(), "record/array/buffer externs must not bind on Native (R6/R7)");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001 on Native, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void jsShapeExamplesStayHonest(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("interopjs.kf");
        Files.writeString(src, DOC_SHAPES);
        CompilationResult r = driver.compile(src, dir.resolve("out-js"), Target.JS);
        assertFalse(r.success(), "record/array/buffer externs are not bridged on JS yet (R6)");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI002"),
                "expected FFI002 on JS, got: " + r.diagnostics().getDiagnostics());
    }
}
