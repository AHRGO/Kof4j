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
 * D-APP I3 ({@code DECISIONS.md} §D-APP, Q6): flag {@code --fat} opcional —
 * empacota classes do app + runtime {@code dev.kof.runtime} + dependências num
 * único {@code kof-app.jar} executável com {@code Main-Class}. Default (sem a
 * flag) permanece classpath explícito, sem jar.
 *
 * <p>Prova real: o jar gerado roda com {@code java -jar} e produz a saída do
 * programa; target não-JVM recusa honesto (R6).</p>
 */
class CmdBuildFatTest {

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Process startCli(Path workDir, String... cliArgs) throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static Path writeApp(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), "main() { println(\"fat ok\") }\n");
        return src;
    }

    @Test
    void fatProducesRunnableJar(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Process p = startCli(dir, "build", src.toString(), "--target", "jvm",
                "--output", "dist", "--fat");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "build timeout\n" + out);
        assertEquals(0, p.exitValue(), "build --fat rc\n" + out);

        Path jar = dir.resolve("dist/kof-app.jar");
        assertTrue(Files.exists(jar), "fat jar deve existir: " + out);

        // O jar embute o runtime e é auto-executável.
        Process run = new ProcessBuilder(javaBin(), "-jar", jar.toString())
                .directory(dir.toFile()).redirectErrorStream(true).start();
        String runOut = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(60, TimeUnit.SECONDS), "run timeout\n" + runOut);
        assertEquals(0, run.exitValue(), "java -jar rc\n" + runOut);
        assertTrue(runOut.contains("fat ok"), "saída do programa no jar: " + runOut);
    }

    @Test
    void fatBundlesDependenciesAndPrefersAppEntries(@TempDir Path dir) throws Exception {
        Path classes = dir.resolve("classes");
        Files.createDirectories(classes.resolve("Default"));
        Files.write(classes.resolve("Default/Main.class"), new byte[]{1});
        Path dep = dir.resolve("dep.jar");
        try (var jos = new java.util.jar.JarOutputStream(Files.newOutputStream(dep))) {
            jos.putNextEntry(new java.util.jar.JarEntry("lib/Util.class"));
            jos.write(new byte[]{2});
            jos.closeEntry();
            jos.putNextEntry(new java.util.jar.JarEntry("Default/Main.class"));
            jos.write(new byte[]{9});
            jos.closeEntry();
            jos.putNextEntry(new java.util.jar.JarEntry("META-INF/MANIFEST.MF"));
            jos.write(new byte[]{0});
            jos.closeEntry();
        }
        Path jar = CmdBuild.buildFatJar(classes, java.util.List.of(dep));
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            assertNotNull(zip.getEntry("lib/Util.class"), "dep deve entrar no jar");
            var appEntry = zip.getEntry("Default/Main.class");
            assertNotNull(appEntry);
            try (var in = zip.getInputStream(appEntry)) {
                assertEquals(1, in.read(), "entrada do app tem precedência sobre a dep");
            }
            assertNotNull(zip.getEntry("META-INF/MANIFEST.MF"));
        }
        try (var jf = new java.util.jar.JarFile(jar.toFile())) {
            assertEquals("Default.Main",
                    jf.getManifest().getMainAttributes().getValue("Main-Class"));
        }
    }

    @Test
    void defaultDoesNotProduceJar(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Process p = startCli(dir, "build", src.toString(), "--target", "jvm",
                "--output", "dist");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "build timeout\n" + out);
        assertEquals(0, p.exitValue(), "build rc\n" + out);
        assertFalse(Files.exists(dir.resolve("dist/kof-app.jar")),
                "sem --fat não deve gerar jar (classpath explícito): " + out);
    }

    @Test
    void fatWithNonJvmTargetIsHonestError(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir);
        Process p = startCli(dir, "build", src.toString(), "--target", "native", "--fat");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "build timeout\n" + out);
        assertNotEquals(0, p.exitValue(), "--fat fora do JVM deve recusar (R6): " + out);
        assertTrue(out.contains("--fat") && out.contains("jvm"),
                "diagnóstico honesto esperado: " + out);
    }
}
