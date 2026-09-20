package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BUG "enum com package gerado no package errado" — o enum era emitido na
 * RAIZ do output enquanto o caller referenciava pkg/... →
 * NoClassDefFoundError no load (mensagem JavaFX por cima, §149). A causa
 * (8 sítios assumindo pacote vazio na cadeia de resolução de enum) foi
 * corrigida por §308/D-ENUM207 (#445, c6e0b7db). Este arquivo PINA o
 * contrato de COLOCAÇÃO física dos artefatos (o EnumCrossFileE2ETest cobre
 * o comportamento semântico):
 *
 *  - enum declarado em arquivo COM package → .class sob o diretório do
 *    package, NUNCA na raiz;
 *  - package profundo (foo.bar.baz);
 *  - import explícito / wildcard cross-package;
 *  - mesmo package, multiplos arquivos, SEM import (forma do CLI);
 *  - duas enums em dois packages no mesmo modulo;
 *  - enum SEM package continua na raiz (guarda contra sobreaplicacao);
 *  - paridade no alvo script.
 *
 * Obs. operacional: o sintoma reportado com `bin/kof` no tip vinha do
 * `lib/kof.jar` OBSTO (build 17:39, anterior ao landing de §308) — o jar
 * reconstruido do tip emite o pacote correto. rebuild:
 * `mvn -o -pl kof-cli -am package -DskipTests && cp
 * kof-cli/target/kof-cli-*.jar lib/kof.jar`.
 */
class EnumPackagePlacementE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path root, String rel, String src) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent() == null ? root : f.getParent());
        Files.writeString(f, src);
        return f;
    }

    private Path compileJvm(Path root, List<Path> sources, String tag) {
        Path outDir = root.resolve("out-" + tag);
        CompilationResult result = driver.compileSources(sources, outDir, Target.JVM, root);
        assertTrue(result.success(), tag + " compile failed: " + result.diagnostics().getDiagnostics());
        return outDir;
    }

    private void runJvm(Path outDir, String tag, String expected) throws Exception {
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail(tag + " JVM run hung (>30s)");
        }
        assertEquals(0, p.exitValue(), tag + " JVM exit, output: " + output);
        assertEquals(expected, output, tag + " JVM output");
    }

    /**
     * O bug do package errado emitia a classe na RAIZ do output enquanto o
     * caller referenciava pkg/... — pinar que NÃO existe cópia solta na raiz.
     * (A cópia correta sob pkg/ é assertada em cada teste.)
     */
    private void assertPlaced(Path outDir, String cls) throws Exception {
        String simple = cls.substring(cls.lastIndexOf('/') + 1);
        assertFalse(Files.exists(outDir.resolve(simple + ".class")),
                "classe empacotada emitida na RAIZ (o bug do package errado): " + simple);
    }

    @Test
    void packagedEnumFileAsDirectSourceEmitsUnderItsPackage(@TempDir Path tmp) throws Exception {
        Path enumFile = write(tmp, "foo/bar/Status.kf", """
                package foo.bar

                enum Status { ACTIVE, INACTIVE }

                main() {
                    println(Status.ACTIVE)
                }
                """);
        Path out = compileJvm(tmp, List.of(enumFile), "placement");
        assertTrue(Files.exists(out.resolve("foo/bar/Status.class")),
                "foo/bar/Status.class ausente (enum perdeu o package na emissao)");
        assertPlaced(out, "foo/bar/Status");
        runJvm(out, "placement", "ACTIVE");
    }

    @Test
    void deepPackageEnumPlacedAndUsable(@TempDir Path tmp) throws Exception {
        Path enumFile = write(tmp, "foo/bar/baz/Color.kf", """
                package foo.bar.baz

                enum Color { RED, GREEN, BLUE }

                main() {
                    println(Color.BLUE)
                    println(Color.valueOf("GREEN"))
                }
                """);
        Path out = compileJvm(tmp, List.of(enumFile), "deep");
        assertTrue(Files.exists(out.resolve("foo/bar/baz/Color.class")),
                "package profundo: foo/bar/baz/Color.class ausente");
        assertPlaced(out, "foo/bar/baz/Color");
        runJvm(out, "deep", "BLUE\nGREEN");
    }

    @Test
    void explicitImportedEnumPlacedInOwnerPackage(@TempDir Path tmp) throws Exception {
        Path enumFile = write(tmp, "foo/bar/Status.kf", """
                package foo.bar

                enum Status { ACTIVE, INACTIVE }
                """);
        Path main = write(tmp, "Main.kf", """
                import foo.bar.Status

                main() {
                    println(Status.valueOf("ACTIVE"))
                    println(Status.ACTIVE == Status.valueOf("ACTIVE"))
                }
                """);
        Path out = compileJvm(tmp, List.of(main, enumFile), "ximport");
        assertTrue(Files.exists(out.resolve("foo/bar/Status.class")), "ximport: package do dono");
        assertPlaced(out, "foo/bar/Status");
        runJvm(out, "ximport", "ACTIVE\ntrue");
    }

    @Test
    void samePackageMultiFileNoImportPlacesEnumCorrectly(@TempDir Path tmp) throws Exception {
        Path enumFile = write(tmp, "foo/bar/Status.kf", """
                package foo.bar

                enum Status { ACTIVE, INACTIVE }
                """);
        Path user = write(tmp, "foo/bar/User.kf", """
                package foo.bar

                class User {
                    String name
                    Status state
                    describe() {
                        println(name + ":" + state)
                    }
                }
                """);
        Path main = write(tmp, "Main.kf", """
                import foo.bar

                main() {
                    var u = User()
                    u.name = "mel"
                    u.state = Status.INACTIVE
                    u.describe()
                }
                """);
        // só o main entra na lista: `import foo.bar` expande os irmãos — o
        // mesmo caminho do CLI da issue (listar os dois registraria de novo).
        Path out = compileJvm(tmp, List.of(main), "samepkg");
        assertTrue(Files.exists(out.resolve("foo/bar/Status.class")), "samepkg: Status no package");
        assertTrue(Files.exists(out.resolve("foo/bar/User.class")), "samepkg: User no package");
        assertPlaced(out, "foo/bar/Status");
        runJvm(out, "samepkg", "mel:INACTIVE");
    }

    @Test
    void twoEnumsInDifferentPackagesBothPlaced(@TempDir Path tmp) throws Exception {
        Path alpha = write(tmp, "alpha/Colour.kf", """
                package alpha

                enum Colour { RED, BLUE }
                """);
        Path beta = write(tmp, "beta/Tone.kf", """
                package beta

                enum Tone { LOUD, QUIET }
                """);
        Path main = write(tmp, "Main.kf", """
                import alpha.Colour
                import beta.Tone

                main() {
                    println(Colour.RED)
                    println(Tone.QUIET)
                }
                """);
        Path out = compileJvm(tmp, List.of(main, alpha, beta), "two");
        assertTrue(Files.exists(out.resolve("alpha/Colour.class")), "two: alpha/Colour.class");
        assertTrue(Files.exists(out.resolve("beta/Tone.class")), "two: beta/Tone.class");
        assertPlaced(out, "alpha/Colour");
        assertPlaced(out, "beta/Tone");
        runJvm(out, "two", "RED\nQUIET");
    }

    @Test
    void rootPackageEnumStillEmitsAtRoot(@TempDir Path tmp) throws Exception {
        Path enumFile = write(tmp, "Main.kf", """
                enum Color { RED, GREEN }

                main() {
                    println(Color.RED)
                }
                """);
        Path out = compileJvm(tmp, List.of(enumFile), "root");
        assertTrue(Files.exists(out.resolve("Color.class")),
                "enum SEM package deve continuar na raiz (nao sobreaplicar §308)");
        runJvm(out, "root", "RED");
    }

    @Test
    void deepPackageEnumParityScriptTarget(@TempDir Path tmp) throws Exception {
        Path enumFile = write(tmp, "foo/bar/baz/Color.kf", """
                package foo.bar.baz

                enum Color { RED, GREEN, BLUE }

                main() {
                    println(Color.BLUE)
                }
                """);
        KofInterpreter.Result r = driver.interpret(List.of(enumFile), tmp, new String[0]);
        assertEquals(0, r.exitCode(), "deep SCRIPT exit, output: " + r.stdout());
        assertEquals("BLUE", r.stdout().trim().replace("\r\n", "\n"), "deep SCRIPT output");
    }
}
