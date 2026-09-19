package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.5.3-S2 (D2-A, DECISIONS.md) — o lado PULL do registry: `kof deps` resolve
 * `owner/repo[@ver]` lendo releases no formato exato do `kof deploy --publish`
 * (tar.gz empacotado pelo MESMO `CmdDeploy.writeTarGz`, jar + RELEASE.md +
 * SHA256SUMS conferido). Fake server local — nunca rede real no gate
 * (padrão CmdDeployTest). Faces Q3: happy+classpath, idempotência (2ª resolve
 * não re-baixa), latest→pin, 404=REG001, soma violada=REG002 e nada instalado,
 * pacote sem jar=REG003, sem SHA256SUMS=REG004.
 */
class DepsRegistryTest {

    private static CliResult runWithEnv(Path workDir, Map<String, String> env, String... cliArgs)
            throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("-Duser.home=" + env.get("HOMEOF"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile()).redirectErrorStream(true);
        env.forEach(pb.environment()::put);
        pb.environment().remove("HOMEOF");
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS);
        return new CliResult(p.exitValue(), out);
    }

    private record CliResult(int exit, String out) {}



    /** Release D2-A real: jar valido + RELEASE.md + SHA256SUMS + tar.gz do proprio writer. */
    private static byte[] buildPackage(Path dir, String repo, String version,
                                       boolean withJar, boolean goodSums, boolean withSums)
            throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve(repo + "-" + version + ".jar");
        if (withJar) {
            try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
                jo.putNextEntry(new JarEntry("Hello.txt"));
                jo.write("hello-kof".getBytes(StandardCharsets.UTF_8));
                jo.closeEntry();
            }
        } else {
            Files.writeString(dir.resolve("README.txt"), "sem jar");
        }
        Files.writeString(dir.resolve("RELEASE.md"), "# " + repo + " " + version + "\n");
        if (withSums) {
            String sha = (goodSums && withJar)
                    ? CmdDeploy.sha256Hex(jar)
                    : "0".repeat(64);
            Files.writeString(dir.resolve("SHA256SUMS"),
                    sha + "  " + jar.getFileName() + "\n");
        }
        Path tgz = dir.resolve(repo + "-" + version + ".tar.gz");
        List<Path> files = new java.util.ArrayList<>();
        files.add(withJar ? jar.getFileName() : Path.of("README.txt"));
        files.add(Path.of("RELEASE.md"));
        if (withSums) files.add(Path.of("SHA256SUMS"));
        CmdDeploy.writeTarGz(tgz, dir, files, 0644);
        return Files.readAllBytes(tgz);
    }

    private static HttpServer serveFakeRegistry(Path dir, String repo, String version,
                                                byte[] tgz, boolean found) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String addr = "http://127.0.0.1:" + server.getAddress().getPort();
        String asset = repo + "-" + version + ".tar.gz";
        String json = "{\"tag_name\":\"" + repo + "-" + version + "\",\"assets\":["
                + "{\"name\":\"" + asset + "\",\"download_url\":\"" + addr + "/dl\"}]}";
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            try {
                if (found && (p.startsWith("/repos/acme/" + repo + "/releases/tags/")
                        || p.startsWith("/repos/acme/" + repo + "/releases/latest"))) {
                    byte[] b = json.getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, b.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                } else if (found && p.equals("/dl")) {
                    ex.sendResponseHeaders(200, tgz.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(tgz); }
                } else {
                    ex.sendResponseHeaders(404, -1);
                }
            } catch (Exception e) {
                ex.sendResponseHeaders(500, -1);
            } finally {
                ex.close();
            }
        });
        server.start();
        return server;
    }

    private static Map<String, String> envOf(HttpServer server, Path fakeHome) {
        return Map.of(
                "KOF_REGISTRY_API", "http://127.0.0.1:" + server.getAddress().getPort(),
                "HOMEOF", fakeHome.toString());
    }

    @Test
    void pullResolvesKofReleaseClasspathSeesTheJarAndIsIdempotent(@TempDir Path tmp)
            throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);

            CliResult add = runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            assertEquals(0, add.exit(), "add aceita formato kof:\n" + add.out());
            assertTrue(Files.readString(proj.resolve("kofdeps")).contains("acme/hello@1.2.3"),
                    "linha kof registrada em kofdeps");

            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r.exit(), "resolve feliz:\n" + r.out());
            Path cached = home.resolve(".kof/deps/kof/acme/hello/1.2.3/hello-1.2.3.jar");
            assertTrue(Files.exists(cached), "jar instalado no cache kof:\n" + r.out());
            String cp = r.out().lines()
                    .filter(l -> l.contains("hello-1.2.3.jar") && !l.startsWith("baixado"))
                    .findFirst().orElse(null);
            assertNotNull(cp, "classpath final traz o jar:\n" + r.out());
            assertTrue(cp.startsWith(cached.toString()), "classpath = caminho do cache: " + cp);

            CliResult r2 = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r2.exit(), r2.out());
            assertFalse(r2.out().contains("baixado"), "2ª resolve NAO re-baixa:\n" + r2.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void latestResolvesAndPinsConcreteVersion(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "2.0.0", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "2.0.0", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);
            CliResult a = runWithEnv(proj, env, "deps", "add", "acme/hello");
            assertEquals(0, a.exit(), "add aceita formato kof sem versao:\n" + a.out());
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r.exit(), "latest resolve:\n" + r.out());
            String declared = Files.readString(proj.resolve("kofdeps"));
            assertTrue(declared.contains("acme/hello@2.0.0"),
                    "latest pinou a versao concreta no kofdeps: " + declared);
            assertTrue(Files.exists(home.resolve(".kof/deps/kof/acme/hello/2.0.0/hello-2.0.0.jar")),
                    "jar 2.0.0 instalado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingReleaseIsHonestReg001(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, false);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@9.9.9");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit(), "tag inexistente falha com exit!=0");
            assertTrue(r.out().contains("REG001"), "diagnostico REG001:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tamperedChecksumRefusesInstallReg002(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, false, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit(), "soma violada falha");
            assertTrue(r.out().contains("REG002"), "diagnostico REG002:\n" + r.out());
            assertFalse(Files.exists(home.resolve(".kof/deps/kof/acme/hello/1.2.3/hello-1.2.3.jar")),
                    "nada instalado com checksum quebrado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releaseWithoutJarIsReg003(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", false, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG003"), "diagnostico REG003:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releaseWithoutSumsIsReg004(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, false);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG004"), "integrity obrigatoria (REG004):\n" + r.out());
        } finally {
            server.stop(0);
        }
    }
}
