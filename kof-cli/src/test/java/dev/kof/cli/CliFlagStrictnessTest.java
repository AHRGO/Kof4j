package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R6: comandos sem pass-through de args ao programa nao podem ignorar uma
 * flag desconhecida em silencio. `kof init` chegava a tratar `--flag` como
 * nome de diretorio (criava `--bogus/`); `kof info` ignorava.
 */
class CliFlagStrictnessTest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "timeout\n" + out);
        return new Cli(p.exitValue(), out);
    }

    @Test
    void initRejectsFlagAndDoesNotCreateJunkDir(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "init", "--bogus-xyz");
        assertNotEquals(0, r.exit(), "init --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("desconhecida"), r.out());
        assertFalse(Files.exists(dir.resolve("--bogus-xyz")),
                "nao pode criar diretorio com nome de flag");
    }

    @Test
    void initStillCreatesProject(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("proj");
        Cli r = cli(dir, "init", target.toString());
        assertEquals(0, r.exit(), r.out());
        assertTrue(Files.exists(target.resolve("main.kf")), r.out());
        assertTrue(Files.exists(target.resolve("tests/smoke.kf")), r.out());
    }

    @Test
    void infoRejectsUnknownFlag(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "info", "--bogus");
        assertNotEquals(0, r.exit(), "info --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("desconhecida"), r.out());
    }

    @Test
    void infoJsonStillWorks(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "info", "--json");
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("\"kof\":\""), r.out());
    }

    @Test
    void versionRejectsUnknownFlag(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "version", "--bogus");
        assertNotEquals(0, r.exit(), "version --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("desconhecida"), r.out());
    }

    @Test
    void versionPlainWorks(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "version");
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("kof "), r.out());
    }

    @Test
    void buildRejectsUnknownFlag(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "build", src.toString(), "--output", "dist", "--bogus");
        assertNotEquals(0, r.exit(), "build --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--bogus"), r.out());
    }

    @Test
    void buildRejectsFlagMissingItsValue(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "build", src.toString(), "--output", "dist", "--min-sdk");
        assertNotEquals(0, r.exit(), "build --min-sdk sem valor deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--min-sdk"), r.out());
    }

    @Test
    void buildStillBuildsPlainSource(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "build", src.toString(), "--output", "dist");
        assertEquals(0, r.exit(), "build simples nao regride:\n" + r.out());
        assertTrue(Files.exists(dir.resolve("dist/Default/Main.class")), r.out());
    }

    @Test
    void testRejectsUnknownFlag(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "test", src.toString(), "--bogus");
        assertNotEquals(0, r.exit(), "test --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--bogus"), r.out());
    }

    @Test
    void testRejectsTargetWithoutValue(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "test", src.toString(), "--target");
        assertNotEquals(0, r.exit(), "test --target sem valor deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--target"), r.out());
    }

    @Test
    void testStillRunsPlainSource(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "test", src.toString());
        assertEquals(0, r.exit(), "test simples nao regride:\n" + r.out());
        assertTrue(r.out().contains("1 passed, 0 failed"), r.out());
    }

    @Test
    void buildRejectsNonexistentSource(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "build", dir.resolve("does-not-exist").toString());
        assertNotEquals(0, r.exit(), "build de dir inexistente deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("not found"), r.out());
    }

    @Test
    void buildRejectsFlagInSourcePosition(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "build", "--bogus");
        assertNotEquals(0, r.exit(), "build com flag na posicao da fonte deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--bogus"), r.out());
    }

    @Test
    void buildAcceptsSingleSourceFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "build", "Main.kf", "--output", "out");
        assertEquals(0, r.exit(), "build <file.kf> documentado nao pode ser no-op (R6):\n" + r.out());
        assertTrue(Files.exists(dir.resolve("out/Default/Main.class")), r.out());
    }

    @Test
    void lspRejectsUnknownFlag(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "lsp", "--bogus");
        assertNotEquals(0, r.exit(), "lsp --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--bogus"), r.out());
    }

    @Test
    void configGenRejectsUnknownFlag(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "config", "gen", "Main.kf", "--bogus");
        assertNotEquals(0, r.exit(), "config gen --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--bogus"), r.out());
    }

    @Test
    void configGenStillWorks(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "config", "gen", "Main.kf");
        assertEquals(0, r.exit(), "config gen simples nao regride:\n" + r.out());
    }

    @Test
    void benchRejectsUnknownFlagInsteadOfTreatingItAsPath(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "bench", "--verbos");
        assertNotEquals(0, r.exit(), "bench --flag deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("unknown flag") && r.out().contains("--verbos"),
                "diagnostico honesto esperado (nao 'no benchmarks found'):\n" + r.out());
    }

    @Test
    void benchWithoutBenchmarksStillReportsHonestly(@TempDir Path dir) throws Exception {
        Cli r = cli(dir, "bench");
        assertNotEquals(0, r.exit(), "sem benchmarks: exit 1 preservado:\n" + r.out());
        assertTrue(r.out().contains("no benchmarks found"), r.out());
    }
}
