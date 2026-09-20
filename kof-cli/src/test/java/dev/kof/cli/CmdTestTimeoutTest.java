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
 * X8-A / §G6 "timeouts": o runner do `kof test` nao pode hangar num programa
 * que nunca termina. `--timeout <sec>` mata o processo do harness e reporta
 * FAIL honesto (R6); sem a flag o comportamento historico esta preservado.
 */
class CmdTestTimeoutTest {

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
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o proprio CLI nao pode hangar\n" + out);
        return new Cli(p.exitValue(), out);
    }

    @Test
    void hangingProgramFailsWithTimeoutInsteadOfHangingForever(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"),
                "main() {\n    var i = 0\n    while (true) { i = i + 1 }\n}\n");
        long t0 = System.currentTimeMillis();
        Cli r = cli(dir, "test", src.toString(), "--timeout", "3");
        long ms = System.currentTimeMillis() - t0;
        assertEquals(1, r.exit(), "loop infinito deve FAIL com timeout:\n" + r.out());
        assertTrue(r.out().contains("timeout after 3s"), "diagnostico de timeout:\n" + r.out());
        assertTrue(r.out().contains("0 passed, 1 failed"), "contagem final:\n" + r.out());
        assertTrue(ms < 120_000, "tem de RETORNAR (nao hangar); ms=" + ms);
    }

    @Test
    void fastProgramPassesUnderTheTimeout(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, "test", src.toString(), "--timeout=60");
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("1 passed, 0 failed"), r.out());
    }

    @Test
    void timeoutFlagRejectsGarbageValues(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli bad = cli(dir, "test", src.toString(), "--timeout", "abc");
        assertEquals(1, bad.exit(), "--timeout abc recusado (R6):\n" + bad.out());
        Cli zero = cli(dir, "test", src.toString(), "--timeout", "0");
        assertEquals(1, zero.exit(), "--timeout 0 nao tem sentido, recusar:\n" + zero.out());
        Cli dash = cli(dir, "test", src.toString(), "--timeout");
        assertEquals(1, dash.exit(), "--timeout sem valor recusado (flag estrita):\n" + dash.out());
    }
}
