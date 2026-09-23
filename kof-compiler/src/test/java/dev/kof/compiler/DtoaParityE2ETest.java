package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-1c (§448 / PLAN-BAREMETAL-BOOT): o dtoa freestanding (Schubfach portado do
 * JDK) tem de render o {@code double} byte-a-byte igual ao oráculo JVM — inclusive
 * nos subnormais, onde o antigo loop {@code snprintf}+{@code strtod} divergia
 * ({@code 5.0E-324} vs {@code 4.9E-324}). O golden é MEDIDO no oráculo JVM do
 * MESMO programa, nunca de memória.
 */
class DtoaParityE2ETest {

    /** Bordas: subnormal mínimo, subnormais, fronteiras de notação, negativos. */
    private static final String CORPUS = """
            main() {
                println(5E-324)
                println(1E-323)
                println(4.9E-324)
                println(2.2250738585072014E-308)
                println(1.7976931348623157E308)
                println(0.1)
                println(1.0)
                println(100.0)
                println(10000000.0)
                println(0.0001)
                println(-123.456)
                println(3.141592653589793)
                println(0.0)
            }
            """;

    private static boolean hasTool(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "--version").start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String jvmOracle(Path dir, String program) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = dir.resolve("Oracle.kf");
        Files.writeString(src, program);
        Path outDir = dir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, outDir, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main", "run")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida jvm: " + out);
        return out;
    }

    @Test
    void freestandingDoublePrintMatchesJvmOracle(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        CompilerDriver driver = new CompilerDriver();
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, CORPUS);
        Path outDir = dir.resolve("out");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE, NativeProfile.FREESTANDING);
        assertTrue(r.success(), "compile freestanding: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");

        Process rp = new ProcessBuilder("readelf", "--dyn-syms", bin.toString())
                .redirectErrorStream(true).start();
        String dyn = new String(rp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(rp.waitFor(30, TimeUnit.SECONDS), "readelf nao terminou");
        assertFalse(dyn.contains("snprintf"), "dtoa libc vivo (snprintf):\n" + dyn);
        assertFalse(dyn.contains("strtod"), "dtoa libc vivo (strtod):\n" + dyn);

        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida nativa: " + out);
        assertEquals(jvmOracle(dir, CORPUS), out, "dtoa nativo != oráculo JVM");
    }
}
