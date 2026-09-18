package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class ExceptionsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private static final String CATCH_SOURCE = """
            main() {
                try {
                    throw "boom"
                    println("unreachable")
                } catch (String e) {
                    println("caught: " + e)
                }
                println("done")
            }
            """;

    @Test
    void catchCatchesThrow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CATCH_SOURCE);
        runJvm(source, tempDir.resolve("out"), "caught: boom\ndone");
    }

    @Test
    void nativeCatchCatchesThrow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CATCH_SOURCE);
        runNative(source, tempDir.resolve("out"), "caught: boom\ndone");
    }

    @Test
    void finallyRunsOnNormalPath(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    println("body")
                } finally {
                    println("finally")
                }
                println("end")
            }
            """);
        runJvm(source, tempDir.resolve("out"), "body\nfinally\nend");
    }

    @Test
    void finallyRunsWithCaughtException(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "x"
                } catch (String e) {
                    println("caught")
                } finally {
                    println("finally")
                }
                println("end")
            }
            """);
        runJvm(source, tempDir.resolve("out"), "caught\nfinally\nend");
    }

    @Test
    void nestedFinallyPropagatesToOuterCatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "deep"
                    } finally {
                        println("inner finally")
                    }
                } catch (String e) {
                    println("outer caught: " + e)
                }
                println("end")
            }
            """);
        runJvm(source, tempDir.resolve("out"), "inner finally\nouter caught: deep\nend");
    }

    @Test
    void nativeNestedFinallyPropagatesToOuterCatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "deep"
                    } finally {
                        println("inner finally")
                    }
                } catch (String e) {
                    println("outer caught: " + e)
                }
                println("end")
            }
            """);
        runNative(source, tempDir.resolve("out"), "inner finally\nouter caught: deep\nend");
    }

    @Test
    void exceptionAcrossFrames(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            void inner() {
                throw "from-inner"
            }
            String outer() {
                try {
                    inner()
                    return "no"
                } catch (String e) {
                    return "got: " + e
                }
            }
            main() {
                try {
                    try {
                        throw "deep"
                    } finally {
                        println("inner finally")
                    }
                } catch (String e) {
                    println("outer caught: " + e)
                }
                try {
                    println("normal path")
                } finally {
                    println("finally normal")
                }
                println(outer())
                println("end")
            }
            """);
        String expected = "inner finally\nouter caught: deep\nnormal path\nfinally normal\ngot: from-inner\nend";
        runJvm(source, tempDir.resolve("out-jvm"), expected);
        runNative(source, tempDir.resolve("out-native"), expected);
    }

    @Test
    void multipleCatches(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "first"
                } catch (String e) {
                    println("c1: " + e)
                } catch (Exception e2) {
                    println("c2")
                }
                try {
                    throw "second"
                } catch (String e) {
                    println("c3: " + e)
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "c1: first\nc3: second");
    }

    @Test
    void uncaughtInInnerTryReachesOuter(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "up"
                    } catch (String e) {
                        println("inner caught")
                    }
                } catch (String e) {
                    println("outer caught")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "inner caught");
    }

    // Issue #163 — typing `catch (RuntimeException e)` (a java.lang type
    // written by its simple name) left the catch local unqualified:
    // ClassType("", "RuntimeException"). Calling `e.getMessage()` then emitted
    // `LRuntimeException;` in the constant pool default package and loading
    // the class failed with NoClassDefFoundError: RuntimeException. The catch
    // type is now qualified to java.lang (StatementAnalyzer + StatementLowerer).
    @Test
    void typedCatchExceptionMethodCall(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (RuntimeException e) {
                    println(e.getMessage())
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "boom");
    }

    // Issue #211 — same root as #163: the caught-variable type of ANY
    // java.lang throwable written by its simple name (Exception, Throwable,
    // ...) stayed unqualified, so e.getMessage() emitted
    // `invokevirtual Exception.getMessage` (default package) -> COMP002 /
    // NoClassDefFoundError. The catch header is now qualified to java.lang
    // for every throwable in CompilerTypes.JAVA_LANG_THROWABLES.
    @Test
    void typedCatchExceptionAndThrowable(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (Exception e) {
                    println(e.getMessage())
                }
                try {
                    throw "deep"
                } catch (Throwable t) {
                    println(t.getMessage())
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "boom\ndeep");
    }

    // ── ICE do JS (18/09): `catch (Throwable e)` derrubava o JsTryParser
    // (colisão com a catch-all sintética que emula `finally`). Cobertura
    // control-flow apenas — o valor de um Throwable é interop JVM (D-NOT-JAVA),
    // não há representação byte-idêntica no JS. ──

    private String runJs(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compilation should succeed (no ICE): "
                + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, ec, "JS exit code should be 0, output: '" + output + "'");
        assertEquals(expected, output, "Unexpected JS output");
        return output;
    }

    @Test
    void jsCatchThrowableRunsBody(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (Throwable e) {
                    println("caught")
                }
                println("done")
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\ndone");
    }

    @Test
    void jsCatchThrowableWithFinally(@TempDir Path tempDir) throws IOException {
        // O caso de maior risco: um catch(Throwable) do USUÁRIO e a catch-all
        // sintética "#excTmp" do finally coexistem no mesmo try — o
        // discriminador precisa separá-los.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (Throwable e) {
                    println("caught")
                } finally {
                    println("finally")
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\nfinally");
    }
}