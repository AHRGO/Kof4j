package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X9 fatia 1 ({@code kof deploy --target jvm}): a release empacotada é REAL —
 * o jar roda ({@code java -jar}), o SHA256SUMS confere com o artefato, o
 * RELEASE.md carrega os metadados e o .tar.gz é um tar ustar+gzip legível.
 * Recusas honestas (R6/R7): target não-JVM e --publish (registry = decisão
 * D2) saem com exit 1 + DEP001; flag desconhecida nunca é ignorada.
 */
class CmdDeployTest {

    private static Process startCli(Path workDir, String... cliArgs) throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static Path writeApp(Path dir, String body) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), body);
        return src;
    }

    private record CliResult(int exit, String out) {}

    private static CliResult run(Path workDir, String... cliArgs) throws Exception {
        Process p = startCli(workDir, cliArgs);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(180, TimeUnit.SECONDS);
        return new CliResult(p.exitValue(), out);
    }

    @Test
    void jvmReleaseIsPackagedAndConsistent(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"deploy ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm",
                "--output", "dist", "--name", "servico", "--version", "1.2.3");
        assertEquals(0, r.exit(), "deploy exit, output:\n" + r.out());

        Path releaseDir = dir.resolve("dist/deploy/servico-1.2.3");
        assertTrue(Files.isDirectory(releaseDir), r.out());
        Path jar = releaseDir.resolve("servico-1.2.3.jar");
        assertTrue(Files.isRegularFile(jar), "jar ausente:\n" + r.out());

        // prova real: o artefato empacotado RODA
        Process run = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", jar.toString())
                .directory(dir.toFile()).redirectErrorStream(true).start();
        String runOut = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(60, TimeUnit.SECONDS), "java -jar timeout:\n" + runOut);
        assertEquals(0, run.exitValue(), "java -jar exit:\n" + runOut);
        assertEquals("deploy ok", runOut.trim(), "saída do app empacotado");

        // checksum confere com o artefato
        String sums = Files.readString(releaseDir.resolve("SHA256SUMS"), StandardCharsets.UTF_8);
        String expected = CmdDeploy.sha256Hex(jar) + "  servico-1.2.3.jar";
        assertEquals(expected, sums.trim(), "SHA256SUMS diverge do artefato");

        // RELEASE.md com os metadados
        String release = Files.readString(releaseDir.resolve("RELEASE.md"), StandardCharsets.UTF_8);
        assertTrue(release.contains("servico-1.2.3.jar"), release);
        assertTrue(release.contains("jvm"), release);
        assertTrue(release.contains("Default.Main"), release);
        assertTrue(release.contains("java -jar servico-1.2.3.jar"), release);

        // tar.gz: ustar legível, primeiro entry = jar, 3 entries no total
        Path tgz = dir.resolve("dist/deploy/servico-1.2.3.tar.gz");
        assertTrue(Files.size(tgz) > 512, "tar vazio");
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(tgz))) {
            TarEntry e1 = tarEntry(in);
            assertEquals("servico-1.2.3.jar", e1.name(), "1º entry");
            skipTarPayload(in, e1.size());
            TarEntry rel = tarEntry(in);
            assertEquals("RELEASE.md", rel.name(), "2º entry");
            skipTarPayload(in, rel.size());
            TarEntry sumsEntry = tarEntry(in);
            assertEquals("SHA256SUMS", sumsEntry.name(), "3º entry");
            skipTarPayload(in, sumsEntry.size());
            byte[] eof = new byte[512];
            assertEquals(512, in.readNBytes(eof, 0, 512));
            assertTrue(isZeroBlock(eof), "bloco final do tar deve ser zero");
        }
    }

    // nonJvmTargetIsHonestGap REMOVIDO (18/09): caso obsoleto apos a face
    // ANDROID da X9 fatia 3 + §299 — "--target android" nao e mais um gap
    // universal: com SDK empacota APK (androidWithSdkPackagesApk, verificado
    // no CI ubuntu), sem SDK recusa honesto (androidWithoutSdkIsHonestFailure).
    // Assertar DEP001 com ANDROID_HOME do runner = conflito com o e2e.

    @Test
    void publishIsHonestGapD2(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm",
                "--publish", "example.registry");
        assertEquals(1, r.exit(), "publish falso proibido (R6): " + r.out());
        assertTrue(r.out().contains("DEP001"), "--publish deve recusar com DEP001: " + r.out());
        assertTrue(r.out().contains("D2"), "mensagem deve citar a decisão D2: " + r.out());
    }

    /** X9 fatia 2: face NATIVE — o ELF empacotado RODA e sai mode 0755 no tar. */
    @Test
    void nativeFaceRunsAndMarksExecutable(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"native ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "native",
                "--output", "dist", "--name", "edge", "--version", "2.0.0");
        assertEquals(0, r.exit(), "deploy native, saída:\n" + r.out());

        Path bin = dir.resolve("dist/deploy/edge-2.0.0/edge-2.0.0");
        assertTrue(Files.isRegularFile(bin), "binário ausente:\n" + r.out());

        // prova real: o artefato empacotado RODA
        Process run = new ProcessBuilder(bin.toString())
                .directory(dir.toFile()).redirectErrorStream(true).start();
        String runOut = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(60, TimeUnit.SECONDS), "timeout:\n" + runOut);
        assertEquals(0, run.exitValue(), "exit:\n" + runOut);
        assertEquals("native ok", runOut.trim(), "saída do binário empacotado");

        // checksum confere
        String sums = Files.readString(dir.resolve("dist/deploy/edge-2.0.0/SHA256SUMS"),
                StandardCharsets.UTF_8);
        assertEquals(CmdDeploy.sha256Hex(bin) + "  edge-2.0.0", sums.trim(),
                "SHA256SUMS diverge do ELF");

        // tar: artefato com mode 0755 no header
        try (GZIPInputStream in = new GZIPInputStream(
                Files.newInputStream(dir.resolve("dist/deploy/edge-2.0.0.tar.gz")))) {
            byte[] header = new byte[512];
            assertEquals(512, in.readNBytes(header, 0, 512));
            int mode = tarMode(header);
            assertTrue((mode & 0111) != 0, "artefato native deve ser executável: "
                    + Integer.toOctalString(mode));
        }
    }

    /** X9 fatia 2: face JS — o Default.mjs entra no pacote com checksum. */
    @Test
    void jsFacePackagesEntry(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"js ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "js",
                "--output", "dist", "--name", "webapp", "--version", "0.9.0");
        assertEquals(0, r.exit(), "deploy js, saída:\n" + r.out());

        Path mjs = dir.resolve("dist/deploy/webapp-0.9.0/webapp-0.9.0.mjs");
        assertTrue(Files.isRegularFile(mjs), ".mjs ausente:\n" + r.out());
        String sums = Files.readString(dir.resolve("dist/deploy/webapp-0.9.0/SHA256SUMS"),
                StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(
                sums.startsWith(CmdDeploy.sha256Hex(mjs) + "  webapp-0.9.0.mjs"),
                "linha do entry em SHA256SUMS:\n" + sums);
        // §298: a release tem de ser AUTOCONTIDA — o entry importa o runtime
        // relativo; sem os modulos irmaos o "node webapp-0.9.0.mjs" do
        // RELEASE.md morre em ERR_MODULE_NOT_FOUND (bug medido no tip).
        org.junit.jupiter.api.Assumptions.assumeTrue(hasNode(),
                "node nao disponivel no host (skip honesto, nao verde falso)");
        assertTrue(sums.contains("  kof-runtime.mjs"),
                "runtime deve acompanhar o entry (SHA256SUMS):\n" + sums);
        org.junit.jupiter.api.Assertions.assertTrue(
                Files.isRegularFile(dir.resolve("dist/deploy/webapp-0.9.0/kof-runtime.mjs")),
                "kof-runtime.mjs ausente na release");
        String out = runNode(dir.resolve("dist/deploy/webapp-0.9.0"), "webapp-0.9.0.mjs");
        assertTrue(out.contains("js ok"), "node na release empacotada:\n" + out);
        String release = Files.readString(dir.resolve("dist/deploy/webapp-0.9.0/RELEASE.md"),
                StandardCharsets.UTF_8);
        assertTrue(release.contains("node webapp-0.9.0.mjs"), release);
        assertTrue(release.contains("js"), release);
    }

    /** X9 fatia 3: cross riscv/arm continuam DEP001 honesto. */
    @Test
    void crossTargetsStayHonestGaps(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        for (String t : new String[]{"native.risc", "native.arm"}) {
            CliResult r = run(dir, "deploy", src.toString(), "--target", t);
            assertEquals(1, r.exit(), t + " deve recusar (DEP001): " + r.out());
            assertTrue(r.out().contains("DEP001"), t + " esperava DEP001: " + r.out());
        }
    }

    /** 8.4 fatia 4: multi-target da MESMA fonte — 3 releases + manifest SUCCESS. */
    @Test
    void multiTargetFromOneSource(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"multi ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "all",
                "--output", "dist", "--name", "uni", "--version", "0.1.0");
        assertEquals(0, r.exit(), "multi-deploy exit:\n" + r.out());
        Path dep = dir.resolve("dist").resolve("deploy");
        String manifest = Files.readString(dep.resolve("uni-0.1.0.deploy-manifest.json"));
        for (String t : new String[]{"jvm", "native", "kofjs"}) {
            assertTrue(Files.isDirectory(dep.resolve("uni-0.1.0-" + t)),
                    "release por alvo faltando: " + t + " — out:\n" + r.out());
            assertTrue(Files.isRegularFile(dep.resolve("uni-0.1.0-" + t + ".tar.gz")),
                    "tar.gz por alvo faltando: " + t);
            assertTrue(manifest.contains("\"target\": \"" + t + "\", \"status\": \"SUCCESS\""),
                    t + " deve constar SUCCESS no manifest:\n" + manifest);
        }
        assertTrue(r.out().contains("multi-deploy"), "deve anunciar o manifest:\n" + r.out());
    }

    /** 8.4: alvo que falha não derruba os outros — exit 1 + FAIL com razão honesta. */
    @Test
    void multiTargetPartialFailureIsHonest(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm,native.risc",
                "--output", "dist", "--name", "par", "--version", "1.0");
        assertEquals(1, r.exit(), "com falha o exit é 1:\n" + r.out());
        assertTrue(Files.isDirectory(dir.resolve("dist").resolve("deploy").resolve("par-1.0-jvm")),
                "jvm não pode ser contaminado pela falha do risc:\n" + r.out());
        String manifest = Files.readString(dir.resolve("dist").resolve("deploy")
                .resolve("par-1.0.deploy-manifest.json"));
        assertTrue(manifest.contains("\"target\": \"jvm\", \"status\": \"SUCCESS\""), manifest);
        assertTrue(manifest.contains("\"error\"") && manifest.contains("DEP001"),
                "risc deve entrar FAIL com o gap DEP001:\n" + manifest);
    }

    /** 8.4: lista com repetição deduplica vira single — layout legado sem sufixo preservado. */
    @Test
    void duplicateListDedupesToLegacyLayout(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"legado\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm,jvm",
                "--output", "dist", "--name", "one", "--version", "2.0");
        assertEquals(0, r.exit(), "jvm,jvm dedupeia para um deploy:\n" + r.out());
        assertTrue(Files.isDirectory(dir.resolve("dist").resolve("deploy").resolve("one-2.0")),
                "um alvo usa o nome histórico sem sufixo:\n" + r.out());
        assertFalse(Files.exists(dir.resolve("dist").resolve("deploy")
                .resolve("one-2.0.deploy-manifest.json")), "single não gera manifest de multi");
        assertFalse(r.out().contains("multi-deploy"));
    }

    /** X9 fatia 3: face ANDROID — sem SDK, recusa honesta (não fake-success). */
    @Test
    void androidWithoutSdkIsHonestFailure(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "dev.kof.cli.Main",
                "deploy", src.toString(), "--target", "android",
                "--output", "dist", "--name", "app", "--version", "1.0.0");
        pb.environment().remove("ANDROID_HOME");
        pb.directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertNotEquals(0, p.waitFor(120, TimeUnit.SECONDS),
                "sem ANDROID_HOME o deploy não pode fingir sucesso (R6):\n" + out);
        assertTrue(out.contains("ANDROID_HOME") || out.contains("APK pipeline failed"),
                "mensagem deve apontar a causa:\n" + out);
    }

    /** X9 fatia 3: COM SDK válido, o APK empacotado existe + checksum confere.
     *  Guard honesto: sem build-tools 34.0.0 o bloco dá skip (ambiente). */
    @Test
    void androidWithSdkPackagesApk(@TempDir Path dir) throws Exception {
        String androidHome = System.getenv("ANDROID_HOME");
        Assumptions.assumeTrue(androidHome != null && !androidHome.isBlank(),
                "ANDROID_HOME ausente — face android do deploy é validada no host com SDK");
        Path bt = ApkToolchain.pickBuildTools(Path.of(androidHome));
        Assumptions.assumeTrue(bt != null && ApkToolchain.buildToolsSupportsJava21(bt),
                "android e2e exige build-tools >= 35 (d8 le class major 65); pin 34 falhava no dex"
                        + " — skip honesto, a recusa com diagnostico e coberta por CmdBuildApkToolchainTest");
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "android",
                "--output", "dist", "--name", "app", "--version", "1.0.0");
        assertEquals(0, r.exit(), "deploy android, saída:\n" + r.out());
        Path apk = dir.resolve("dist/deploy/app-1.0.0/app-1.0.0.apk");
        assertTrue(Files.isRegularFile(apk), "APK ausente:\n" + r.out());
        String sums = Files.readString(dir.resolve("dist/deploy/app-1.0.0/SHA256SUMS"),
                StandardCharsets.UTF_8);
        assertEquals(CmdDeploy.sha256Hex(apk) + "  app-1.0.0.apk", sums.trim());
    }

    @Test
    void unknownFlagNeverSilent(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm", "--fat");
        assertEquals(1, r.exit(), "flag estranha deve recusar (R6): " + r.out());
        assertTrue(r.out().contains("unknown or incomplete flag"), r.out());
    }

    // ── §298 node helpers ──

    private static boolean hasNode() {
        try {
            return new ProcessBuilder("node", "--version")
                    .redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String runNode(Path releaseDir, String entry) throws Exception {
        Process p = new ProcessBuilder("node", entry)
                .directory(releaseDir.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "node " + entry + " (release autocontida):\n" + out);
        return out;
    }

    // ── tar helpers ──

    private record TarEntry(String name, long size) {}

    /** Lê o header (nome ustar + size octal); o chamador decide pular o payload. */
    private static TarEntry tarEntry(GZIPInputStream in) throws IOException {
        byte[] header = new byte[512];
        assertEquals(512, in.readNBytes(header, 0, 512), "tar header truncado");
        String magic = new String(header, 257, 6, StandardCharsets.US_ASCII);
        assertTrue(magic.startsWith("ustar"), "magic ustar ausente");
        int end = 0;
        while (end < 100 && header[end] != 0) end++;
        String name = new String(header, 0, end, StandardCharsets.UTF_8);
        long size = 0;
        for (int i = 124; i < 136 && header[i] != 0; i++) {
            char c = (char) header[i];
            if (c == ' ') continue;
            size = size * 8 + (c - '0');
        }
        return new TarEntry(name, size);
    }

    /** Pula payload + padding (512-aligned) do entry cujo header já foi consumido. */
    private static void skipTarPayload(GZIPInputStream in, long size) throws IOException {
        long toSkip = (size + 511) / 512 * 512;
        while (toSkip > 0) {
            long n = in.skip(toSkip);
            if (n <= 0) break;
            toSkip -= n;
        }
    }

    private static int tarMode(byte[] header) {
        int mode = 0;
        for (int i = 100; i < 108 && header[i] != 0; i++) {
            char c = (char) header[i];
            if (c == ' ') continue;
            mode = mode * 8 + (c - '0');
        }
        return mode;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) if (b != 0) return false;
        return true;
    }
}
