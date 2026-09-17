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
}
