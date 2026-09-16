package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TIER 1.4 (frente 3 do D-DEV-PRIORITY) — resolução TRANSITIVA via Maven
 * (roadmap §"package manager": "generate a temporary pom.xml and use Maven";
 * R9: o resolvedor de grafo Maven já existe fora — delegar, nunca
 * reimplementar). O wiring é testável SEM rede: o seam {@code kof.mvn}
 * aponta p/ um `mvn` fake que escreve o classpath, e {@code maven.repo.local}
 * aponta p/ um repositório fake — o resto (geração do pom, cópia p/ o cache,
 * lock, classpath) é o código de produção real. O E2E com o Maven real roda
 * só quando o binário existe no host (guard honesto).
 */
class DepsTransitiveTest {

    private static final String OLD_MVN = System.getProperty("kof.mvn");
    private static final String OLD_REPO = System.getProperty("maven.repo.local");
    private static final String OLD_HOME = System.getProperty("user.home");

    private static void set(String key, String val) {
        if (val == null) System.clearProperty(key); else System.setProperty(key, val);
    }

    private static void restore() {
        set("kof.mvn", OLD_MVN);
        set("maven.repo.local", OLD_REPO);
        set("user.home", OLD_HOME);
    }

    // --- generatePomXml (puro) ------------------------------------------------

    @Test
    void pomCarriesDeclaredDependencies() {
        String pom = Deps.generatePomXml(List.of("com.example:a:1.0", "org.b:c:2.1"));
        assertTrue(pom.contains("<groupId>com.example</groupId>"), pom);
        assertTrue(pom.contains("<artifactId>a</artifactId>"), pom);
        assertTrue(pom.contains("<version>1.0</version>"), pom);
        assertTrue(pom.contains("<artifactId>c</artifactId>"), pom);
        assertTrue(pom.contains("<packaging>pom</packaging>"), "aggregator-safe");
    }

    @Test
    void pomIgnoresBlankAndInvalidLines() {
        String pom = Deps.generatePomXml(List.of("", "  ", "noColon", "a:b", "ok:dep:9"));
        assertTrue(pom.contains("<artifactId>dep</artifactId>"), pom);
        assertFalse(pom.contains("noColon"), pom);
        assertEquals(1, pom.split("<dependency>", -1).length - 1, "só a linha válida entra");
    }

    @Test
    void pomEscapesXmlSpecials() {
        String pom = Deps.generatePomXml(List.of("g&x:a<b>:1.0"));
        assertTrue(pom.contains("&amp;"), pom);
        assertTrue(pom.contains("&lt;b&gt;"), pom);
    }

    // --- copyToCache (layout M2 → GAV + cópia p/ o cache do kofdeps) ----------

    @Test
    void copyToCacheParsesLayoutAndCopies(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("m2");
        Path jar = root.resolve("com/example/lib/1.2/lib-1.2.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[]{7});
        set("maven.repo.local", root.toString());
        set("user.home", tmp.resolve("home").toString());
        try {
            String gav = Deps.copyToCache(jar);
            assertEquals("com.example:lib:1.2", gav);
            assertTrue(Files.exists(tmp.resolve("home/.kof/deps/com/example/lib/1.2/lib-1.2.jar")),
                    "jar copiado p/ o cache do kofdeps");
        } finally {
            restore();
        }
    }

    @Test
    void copyToCacheRejectsForeignLayout(@TempDir Path tmp) {
        set("maven.repo.local", tmp.resolve("m2").toString());
        try {
            assertNull(Deps.copyToCache(Path.of("/elsewhere/a/b/c/d.jar")));
            assertNull(Deps.copyToCache(tmp.resolve("m2/too/shallow.jar").toAbsolutePath()));
        } catch (IOException e) {
            fail("não deve lançar: " + e);
        } finally {
            restore();
        }
    }

    // --- mvnClosure com `mvn` fake (sem rede) — integração do wiring ----------

    @Test
    void mvnClosureWithFakeMvnProducesGavLock(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("m2");
        Path jarA = root.resolve("com/example/a/1/a-1.jar");
        Path jarB = root.resolve("com/example/b/2/b-2.jar");   // transitiva
        Files.createDirectories(jarA.getParent());
        Files.createDirectories(jarB.getParent());
        Files.write(jarA, new byte[]{1});
        Files.write(jarB, new byte[]{2});

        Path fake = tmp.resolve("fake-mvn");
        String script = """
                #!/bin/sh
                for arg in "$@"; do
                  case "$arg" in -Dmdep.outputFile=*) out="${arg#*=}";; esac
                done
                [ -z "$out" ] && exit 0
                printf '%s:%s\\n' "%s" "%s" > "$out"
                exit 0
                """.formatted(jarA, jarB, jarA, jarB);
        Files.writeString(fake, script);
        fake.toFile().setExecutable(true);

        set("kof.mvn", fake.toString());
        set("maven.repo.local", root.toString());
        set("user.home", tmp.resolve("home").toString());
        try {
            List<String> gavs = Deps.mvnClosure(tmp, List.of("com.example:a:1"));
            assertEquals(List.of("com.example:a:1", "com.example:b:2"), gavs,
                    "fecho = direta + transitiva (a fake-mvn lista as duas)");
            assertTrue(Files.exists(tmp.resolve("home/.kof/deps/com/example/b/2/b-2.jar")),
                    "transitiva copiada p/ o cache");
        } finally {
            restore();
        }
    }

    @Test
    void mvnClosureReportsFailureForFakeThatFails(@TempDir Path tmp) throws IOException {
        Path fake = tmp.resolve("fake-mvn-fail");
        Files.writeString(fake, "#!/bin/sh\necho 'boom' >&2\nexit 1\n");
        fake.toFile().setExecutable(true);
        set("kof.mvn", fake.toString());
        try {
            assertThrows(IOException.class, () -> Deps.mvnClosure(tmp, List.of("g:a:1")));
        } finally {
            restore();
        }
    }

    // --- resolve: kofdeps + lock + classpath (sem rede, fake mvn) -------------

    @Test
    void resolveWritesLockAndClasspathUsesClosure(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("m2");
        Path jarA = root.resolve("com/example/a/1/a-1.jar");
        Path jarB = root.resolve("com/example/b/2/b-2.jar");
        Files.createDirectories(jarA.getParent());
        Files.createDirectories(jarB.getParent());
        Files.write(jarA, new byte[]{1});
        Files.write(jarB, new byte[]{2});
        Path fake = tmp.resolve("fake-mvn");
        Files.writeString(fake, """
                #!/bin/sh
                for arg in "$@"; do
                  case "$arg" in -Dmdep.outputFile=*) out="${arg#*=}";; esac
                done
                [ -z "$out" ] && exit 0
                printf '%s:%s\\n' "%s" "%s" > "$out"
                exit 0
                """.formatted(jarA, jarB, jarA, jarB));
        fake.toFile().setExecutable(true);

        set("kof.mvn", fake.toString());
        set("maven.repo.local", root.toString());
        set("user.home", tmp.resolve("home").toString());
        try {
            // jar direto já no cache do kofdeps → download() vira no-op (sem rede)
            Path kofJar = tmp.resolve("home/.kof/deps/com/example/a/1/a-1.jar");
            Files.createDirectories(kofJar.getParent());
            Files.write(kofJar, new byte[]{1});
            Deps.run(new String[]{"deps", "init", tmp.toString()});
            Deps.run(new String[]{"deps", "add", "com.example:a:1", tmp.toString()});
            int rc = Deps.run(new String[]{"deps", "resolve", tmp.toString()});
            assertEquals(0, rc, "resolve com fake-mvn");
            List<String> lock = Files.readAllLines(tmp.resolve("kofdeps.lock"));
            assertEquals(List.of("com.example:a:1", "com.example:b:2"), lock);
            String cp = Deps.classpath(tmp);
            assertTrue(cp.contains("a-1.jar"), "classpath via lock: " + cp);
            assertTrue(cp.contains("b-2.jar"), "transitiva no classpath: " + cp);
        } finally {
            restore();
        }
    }

    // --- E2E real (Maven real, sem rede: artefatos já em ~/.m2) ---------------
    // jgrapht-core:1.4.0 tem a transitiva org.jheaps:jheaps:0.11 (compile).
    // Roda SÓ quando `mvn` existe no host E os dois artefatos estão no ~/.m2
    // (guard honesto — Q5: skip explícito, nunca verde por acidente).
    @Test
    void realMvnResolvesTransitiveClosure(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mvnOnPath(),
                "`mvn` não está no PATH — E2E real de fechamento Maven pulado (honesto)");
        Path m2 = Path.of(System.getProperty("maven.repo.local",
                System.getProperty("user.home", ".") + "/.m2/repository"));
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.exists(m2.resolve("org/jgrapht/jgrapht-core/1.4.0/jgrapht-core-1.4.0.jar"))
                        && Files.exists(m2.resolve("org/jheaps/jheaps/0.11/jheaps-0.11.jar")),
                "jgrapht-core/jheaps não estão em ~/.m2 — E2E real pulado (sem rede)");

        set("maven.repo.local", m2.toString());
        set("user.home", tmp.resolve("home").toString());
        try {
            Deps.run(new String[]{"deps", "init", tmp.toString()});
            Deps.run(new String[]{"deps", "add", "org.jgrapht:jgrapht-core:1.4.0", tmp.toString()});
            int rc = Deps.run(new String[]{"deps", "resolve", tmp.toString()});
            assertEquals(0, rc, "resolve via Maven real");
            List<String> lock = Files.readAllLines(tmp.resolve("kofdeps.lock"));
            assertTrue(lock.contains("org.jgrapht:jgrapht-core:1.4.0"), "direta no lock: " + lock);
            assertTrue(lock.contains("org.jheaps:jheaps:0.11"),
                    "TRANSITIVA no lock (prova real do fechamento): " + lock);
            String cp = Deps.classpath(tmp);
            assertTrue(cp.contains("jgrapht-core-1.4.0.jar"), cp);
            assertTrue(cp.contains("jheaps-0.11.jar"), "transitiva no classpath: " + cp);
        } finally {
            restore();
        }
    }

    private static boolean mvnOnPath() {
        try {
            return new ProcessBuilder("mvn", "-v").redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void classpathWithoutLockKeepsMvpBehavior(@TempDir Path tmp) throws Exception {
        Deps.run(new String[]{"deps", "init", tmp.toString()});
        Deps.run(new String[]{"deps", "add", "com.example:lib:1.0", tmp.toString()});
        Path jar = Path.of(System.getProperty("user.home"), ".kof", "deps",
                "com", "example", "lib", "1.0", "lib-1.0.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[]{1, 2, 3});
        try {
            String cp = Deps.classpath(tmp);
            assertTrue(cp.contains("lib-1.0.jar"), "sem lock: só diretas (MVP) — " + cp);
            assertFalse(cp.contains("transitive"), "lock ausente não inventa transitiva");
        } finally {
            Files.deleteIfExists(jar);
        }
    }
}
