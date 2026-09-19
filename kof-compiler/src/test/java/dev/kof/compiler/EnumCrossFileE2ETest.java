package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #445 — enum DECLARADO EM ARQUIVO IMPORTADO (pacote real != "").
 * O Commit do D-ENUM207 tornou enum uma INSTÂNCIA de classe, mas ~8 sítios
 * de resolução ainda assumem pacote vazio: a classe sai emitida na RAIZ
 * enquanto o caller referencia pkg/... → NoClassDefFoundError no load com a
 * mensagem JavaFX por cima (§149). Este contrato exige o pacote real em TODO
 * o caminho: declaração, acesso, ==, switch, values()/valueOf(), parâmetro.
 */
class EnumCrossFileE2ETest {
    private final CompilerDriver driver = new CompilerDriver();

    private static final String PKG_ENUM = """
            package pkg

            enum ModoOperacao { SIMULAR, COPIAR, MOVER }
            """;

    private List<Path> project(Path root, String mainBody) throws java.io.IOException {
        Files.createDirectories(root.resolve("pkg"));
        Path main = root.resolve("Main.kf");
        Files.writeString(main, "import pkg\n\nmain() {\n" + mainBody + "}\n");
        Files.writeString(root.resolve("pkg/Modo.kf"), PKG_ENUM);
        // só o main: `import pkg` expande pkg/Modo.kf (mesmo caminho do CLI
        // da issue). Listar os dois registraria a classe de novo.
        return List.of(main);
    }

    private String runJvmMulti(Path root, List<Path> sources, String expected) throws java.io.IOException {
        Path outDir = root.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JVM, root);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private void runScriptMulti(Path root, List<Path> sources, String expected) {
        KofInterpreter.Result r = driver.interpret(sources, root, new String[0]);
        assertEquals(0, r.exitCode(), "SCRIPT exit code, output: " + r.stdout());
        assertEquals(expected, r.stdout().trim().replace("\r\n", "\n"), "SCRIPT output");
    }

    private void runJsMulti(Path root, List<Path> sources, String expected) throws java.io.IOException {
        Path outDir = root.resolve("js-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JS, root);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        throw new java.io.IOException("no Default.mjs in " + dir);
    }

    @Test
    void verbatimImportedConstantPrintsOnAllTargets(@TempDir Path tmp) throws Exception {
        List<Path> sources = project(tmp, "    println(ModoOperacao.SIMULAR)\n");
        runJvmMulti(tmp, sources, "SIMULAR");
        runScriptMulti(tmp, sources, "SIMULAR");
        runJsMulti(tmp, sources, "SIMULAR");
    }

    @Test
    void importedClassFileLandsInPackageDirectoryNotRoot(@TempDir Path tmp) throws Exception {
        List<Path> sources = project(tmp, "    println(ModoOperacao.MOVER)\n");
        Path outDir = tmp.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JVM, tmp);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        assertTrue(Files.exists(outDir.resolve("pkg/ModoOperacao.class")),
                "classe do enum deve ser emitida em pkg/ (o descriptor do caller aponta p/ pkg/)");
        assertFalse(Files.exists(outDir.resolve("ModoOperacao.class")),
                "não pode sobrar cópia na raiz com o nome sem pacote");
    }

    @Test
    void importedConstantsEqualityAndIdentity(@TempDir Path tmp) throws Exception {
        List<Path> sources = project(tmp, """
                println(ModoOperacao.SIMULAR == ModoOperacao.SIMULAR)
                println(ModoOperacao.SIMULAR == ModoOperacao.COPIAR)
                println(ModoOperacao.SIMULAR != ModoOperacao.MOVER)
                """);
        runJvmMulti(tmp, sources, "true\nfalse\ntrue");
        runScriptMulti(tmp, sources, "true\nfalse\ntrue");
        runJsMulti(tmp, sources, "true\nfalse\ntrue");
    }

    @Test
    void importedValuesValueOfAndMembers(@TempDir Path tmp) throws Exception {
        List<Path> sources = project(tmp, """
            val vs = ModoOperacao.values()
            println(vs.size())
            println(vs.get(0) == ModoOperacao.SIMULAR)
            println(ModoOperacao.valueOf("MOVER") == ModoOperacao.MOVER)
            println(ModoOperacao.COPIAR.name())
            println(ModoOperacao.COPIAR.ordinal())
            """);
        String expected = "3\ntrue\ntrue\nCOPIAR\n1";
        runJvmMulti(tmp, sources, expected);
        runScriptMulti(tmp, sources, expected);
        runJsMulti(tmp, sources, expected);
    }

    @Test
    void switchAndParamOverImportedEnum(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("pkg"));
        Files.writeString(tmp.resolve("pkg/Modo.kf"), PKG_ENUM + """

            String rotulo(ModoOperacao m) {
                var r = "?"
                switch (m) {
                    case ModoOperacao.SIMULAR: { r = "sim" }
                    case ModoOperacao.COPIAR: { r = "cop" }
                    case ModoOperacao.MOVER: { r = "mov" }
                }
                return r
            }
            """);
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, """
                import pkg

                main() {
                    println(rotulo(ModoOperacao.COPIAR))
                    var m = ModoOperacao.MOVER
                    switch (m) {
                        case ModoOperacao.MOVER: { println("movido") }
                        default: { println("outro") }
                    }
                }
                """);
        // APENAS o main em sources: `import pkg` expande pkg/Modo.kf (mesmo
        // caminho do `kof run src/Main.kf` da issue) — passar os dois de novo
        // registraria o arquivo duas vezes (SEM047 falso, artefato de harness).
        List<Path> sources = List.of(main);
        runJvmMulti(tmp, sources, "cop\nmovido");
        runScriptMulti(tmp, sources, "cop\nmovido");
        runJsMulti(tmp, sources, "cop\nmovido");
    }

    @Test
    void sameFileEnumStillWorksControl(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("Solo.kf");
        Files.writeString(f, """
                enum Cor { VERMELHO, AZUL }
                main() {
                    println(Cor.AZUL)
                    println(Cor.AZUL == Cor.VERMELHO)
                    println(Cor.values().size())
                }
                """);
        runJvmMulti(tmp, List.of(f), "AZUL\nfalse\n2");
    }

    @Test
    void importedEnumComparedToStringStillSem062(@TempDir Path tmp) throws Exception {
        List<Path> sources = project(tmp, "    println(ModoOperacao.SIMULAR == \"SIMULAR\")\n");
        CompilationResult result = driver.compileSources(sources, tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "enum x String é SEM062 (D-ENUM207) também p/ enum importado");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM062".equals(d.code())),
                "SEM062 esperado, veio: " + result.diagnostics().getDiagnostics());
    }
}
