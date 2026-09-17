package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof-android Fase 4: {@code --min-sdk}/{@code --target-sdk} no
 * {@code kof build --target android}. O doc do alvo promete override por
 * flag explicita (nunca arquivo magico); estes testes cravam o caminho CLI
 * completo (parsing -> CompilerDriver -> AndroidProjectWriter) e as recusas
 * honestas (R6).
 */
class CmdBuildAndroidSdkTest {

    private record Cli(int exit, String out) {}

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Cli cli(Path workDir, String... cliArgs) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "timeout\n" + out);
        return new Cli(p.exitValue(), out);
    }

    private static Path writeApp(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        return src;
    }

    @Test
    void minAndTargetSdkReachManifestAndPom(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Cli r = cli(dir, "build", src.toString(), "--target", "android",
                "--output", "dist", "--min-sdk", "21", "--target-sdk", "35");
        assertEquals(0, r.exit(), "build android rc\n" + r.out());

        String manifest = Files.readString(dir.resolve("dist/src/main/AndroidManifest.xml"));
        assertTrue(manifest.contains("minSdkVersion=\"21\""), "manifest minSdk:\n" + manifest);
        assertTrue(manifest.contains("targetSdkVersion=\"35\""), "manifest targetSdk:\n" + manifest);

        String pom = Files.readString(dir.resolve("dist/pom.xml"));
        assertTrue(pom.contains("platforms/android-35/android.jar"), "pom plataforma:\n" + pom);
        assertTrue(pom.contains("<arg value=\"--min-api\"/><arg value=\"21\"/>"),
                "pom d8 min-api:\n" + pom);
    }

    @Test
    void defaultsRemain24And34(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Cli r = cli(dir, "build", src.toString(), "--target", "android", "--output", "dist");
        assertEquals(0, r.exit(), "build android rc\n" + r.out());
        String manifest = Files.readString(dir.resolve("dist/src/main/AndroidManifest.xml"));
        assertTrue(manifest.contains("minSdkVersion=\"24\"")
                        && manifest.contains("targetSdkVersion=\"34\""),
                "defaults 24/34 preservados:\n" + manifest);
    }

    @Test
    void invalidSdkIsHonestError(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Cli r = cli(dir, "build", src.toString(), "--target", "android", "--min-sdk", "abc");
        assertNotEquals(0, r.exit(), "sdk invalido deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("--min-sdk") && r.out().contains("inválido"),
                "diagnostico honesto esperado:\n" + r.out());
    }

    @Test
    void sdkFlagsOutsideAndroidAreHonestError(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Cli r = cli(dir, "build", src.toString(), "--target", "jvm", "--target-sdk", "35");
        assertNotEquals(0, r.exit(), "flag fora do android deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("android"), "diagnostico honesto esperado:\n" + r.out());
    }

    @Test
    void minGreaterThanTargetIsHonestError(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Cli r = cli(dir, "build", src.toString(), "--target", "android",
                "--min-sdk", "35", "--target-sdk", "21");
        assertNotEquals(0, r.exit(), "min>target deve recusar (R6):\n" + r.out());
        assertTrue(r.out().contains("não pode ser maior"), "diagnostico honesto esperado:\n" + r.out());
    }
}
