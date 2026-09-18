package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #386 slice 1/3 — `Map.getOrDefault(K, V): V`. The issue whitelist was
 * put/get/remove/containsKey/contains/size/clear/isEmpty/keys/values and
 * getOrDefault died with SEM025 even though the shape is the single most
 * requested map idiom (absence with a caller-provided default). Lowered to
 * kof_map_get_or_default across the shared IR: JVM delegates to
 * java.util.Map.getOrDefault (INVOKEINTERFACE), script to the host map, JS
 * to kofMapGetOrDefault over the kofMapKeyIdx canonical key, native to a
 * kof_map_find + values-slot read with the DEFAULT on the miss path (not
 * 0 — that is the whole point of the method). A default that pollutes the
 * pinned value type is rejected (same §126 wall as put's value).
 */
class MapGetOrDefaultTest {

    private static final String PROGRAM = """
            main() {
                val m: Map<String, Int> = mapOf()
                m.put("a", 1)
                println(m.getOrDefault("a", 7))
                println(m.getOrDefault("b", 0))
                val s: Map<String, String> = mapOf()
                println(s.getOrDefault("k", "fb"))
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    @Test
    void getOrDefaultRunsOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "V", PROGRAM, Target.JVM);
        assertTrue(r.success(), "#386 verbatim must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out-VJVM").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run must exit 0, got:\n" + out);
        assertEquals("1\n0\nfb", out, "hit / primitive-miss default / reference-miss default");
    }

    @Test
    void getOrDefaultRunsOnJs(@TempDir Path tempDir) throws Exception {
        // Script parity lives in kof-script KofScriptStdlibParityTest (the
        // interpreter runner is not on kof-compiler's classpath by design);
        // here JS runs through the in-JVM KofJsRunner like KofJsE2ETest.
        Path src = tempDir.resolve("J.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals("1\n0\nfb", txt, "js output");
    }

    @Test
    void getOrDefaultRunsOnNative(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run exit, output:\n" + out);
        assertEquals("1\n0\nfb", out, "native output");
    }

    @Test
    void defaultPollutingValuePinnedTypeIsRejected(@TempDir Path tempDir) throws Exception {
        // §126 wall mirrored from put: Long default into a Map<String,Int>
        // would poison the slot and die at unbox — SEM056 at compile time.
        CompilationResult r = compile(tempDir, "G", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    m.put("a", 1)
                    println(m.getOrDefault("b", 5L))
                }
                """, Target.JVM);
        assertFalse(r.success(), "polluting default must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM056")),
                "SEM056 expected: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void remainingTrioStillFailsHonestly(@TempDir Path tempDir) throws Exception {
        // #386 slices 2-3 pending: containsValue/putIfAbsent must keep the
        // loud SEM025 (R6 — never a silent fallback). This test flips in the
        // commits that implement them.
        CompilationResult r = compile(tempDir, "R", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    println(m.containsValue(1))
                }
                """, Target.JVM);
        assertFalse(r.success(), "containsValue must still be rejected until slice 2");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM025")),
                "SEM025 expected: " + r.diagnostics().getDiagnostics());
    }
}
