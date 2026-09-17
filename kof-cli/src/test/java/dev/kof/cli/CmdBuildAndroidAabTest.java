package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof-android: {@code --aab} (App Bundle) ainda nao e produzido (requer
 * bundletool, fora do build-tools). O flag nao pode ser ignorado em silencio
 * devolvendo um APK — o alvo recusa honesto (R6).
 */
class CmdBuildAndroidAabTest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "timeout\n" + out);
        return new Cli(p.exitValue(), out);
    }

    private static Path writeApp(Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        return src;
    }

    @Test
    void aabOnAndroidIsHonestGap(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Cli r = cli(dir, "build", src.toString(), "--target", "android",
                "--output", "dist", "--aab");
        assertNotEquals(0, r.exit(), "--aab sem bundletool deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--aab") && r.out().contains("bundletool"),
                "diagnostico honesto esperado:\n" + r.out());
        // o projeto continua sendo gerado (o usuário pode usar --apk / bundletool)
        assertTrue(Files.exists(dir.resolve("dist/pom.xml")), "projeto gerado mesmo com --aab");
    }

    @Test
    void aabOutsideAndroidIsHonestError(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Cli r = cli(dir, "build", src.toString(), "--target", "jvm", "--aab");
        assertNotEquals(0, r.exit(), "--aab fora do android deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("android"), "diagnostico honesto esperado:\n" + r.out());
    }
}
