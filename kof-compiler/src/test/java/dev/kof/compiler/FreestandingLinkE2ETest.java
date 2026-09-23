package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-1 (PLAN-BAREMETAL-BOOT) — perfil de link {@code freestanding} no x86_64:
 * sem {@code -dynamic-linker}/-lc, o ELF não tem {@code PT_INTERP} nem
 * {@code DT_NEEDED} e ainda assim imprime o hello (a costura {@code kof_plat_*}
 * do B-0 cobre SO). O controle com o perfil {@code host} (dinâmico) prova que o
 * detector enxerga INTERP/NEEDED — nada de falso-verde. Capacidade libc
 * (concurrency) em freestanding é RECUSADA com diagnóstico NATIVE003.
 */
class FreestandingLinkE2ETest {

    private static final String PLAIN = """
            main() {
                val x = 3
                println("plain " + x)
            }
            """;

    private static final String CONCURRENT = """
            Int compute() { return 42 }
            main() {
                val r = spawn compute()
                println(await r)
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

    private Path build(Path tempDir, String program, NativeProfile profile, boolean expectSuccess)
            throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-" + profile);
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, profile);
        assertEquals(expectSuccess, result.success(),
                "compile " + profile + " inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private String readelf(String flag, Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("readelf", flag, bin.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "readelf nao terminou");
        return out;
    }

    private String runBinary(Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida: " + out);
        return out;
    }

    /** Golden MEDIDO no oracle JVM do MESMO programa (regra: golden real). */
    private String jvmOracle(Path tempDir, String program) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Oracle.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-jvm");
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main", "run")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida jvm: " + out);
        return out;
    }

    @Test
    void helloFreestandingHasNoInterpOrNeededAndRuns(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path bin = build(dir, PLAIN, NativeProfile.FREESTANDING, true);
        String dyn = readelf("--dynamic", bin);
        String ph = readelf("--program-headers", bin);
        assertFalse(ph.contains("INTERP"),
                "B-1: freestanding nao pode ter PT_INTERP:\n" + ph);
        assertFalse(dyn.contains("NEEDED"),
                "B-1: freestanding nao pode ter DT_NEEDED:\n" + dyn);
        assertEquals(jvmOracle(dir, PLAIN), runBinary(bin), "saida freestanding != oracle JVM");
    }

    @Test
    void hostProfileStaysDynamic(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path bin = build(dir, PLAIN, NativeProfile.HOST, true);
        String dyn = readelf("--dynamic", bin);
        String ph = readelf("--program-headers", bin);
        assertTrue(ph.contains("INTERP"),
                "controle: o perfil host DEVE ter PT_INTERP (senao o detector nao enxerga):\n" + ph);
        assertTrue(dyn.contains("NEEDED"),
                "controle: o perfil host DEVE ter DT_NEEDED:\n" + dyn);
    }

    @Test
    void freestandingRefusesLibcCapabilityWithDiagnostic(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        CompilerDriver driver = new CompilerDriver();
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, CONCURRENT);
        Path outDir = dir.resolve("out-bad");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, NativeProfile.FREESTANDING);
        assertFalse(result.success(), "freestanding+concurrency deve ser recusado");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("NATIVE003") && diags.contains("freestanding"),
                "recusa precisa ser honesta e diagnostica (NATIVE003): " + diags);
    }
}
