package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end coverage for the pure-Kof PDF library in {@code libs/pdf}. */
class PdfLibraryE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void createsPdfWithAccentsAndUserDefinedGrid() throws Exception {
        Path pdf = tmp.resolve("relatorio.pdf");
        runKof("""
            import pdf.PdfDocument
            import pdf.style.PdfColor
            import pdf.style.GridStyle
            import pdf.style.TextAlign
            import pdf.style.TextStyle

            main() {
                var document = PdfDocument()
                document.title("Relatório de usuários € “Kof” —", 40, 802)
                    .fontColor(PdfColor("#FF0000"))
                document.text("São Paulo", 40, 760)
                var pessoas = listOf(
                    Pessoa("João", 28, "São Paulo", "Professor")
                )
                document.grid(4)
                    .widths(listOf(140, 50, 170, 155))
                    .style(GridStyle()
                        .rowHeight(30)
                        .headerStyle(TextStyle().fontSize(10).align(TextAlign.center)))
                    .header(listOf("Nome", "Idade", "Cidade", "Profissão"))
                    .rows(pessoas.map((pessoa: Pessoa) -> listOf(
                        pessoa.nome,
                        pessoa.idade.toString(),
                        pessoa.cidade,
                        pessoa.profissao
                    )))
                document.save("%s")
            }

            class Pessoa {
                String nome
                Int idade
                String cidade
                String profissao

                constructor(String nome, Int idade, String cidade, String profissao) {
                    this.nome = nome
                    this.idade = idade
                    this.cidade = cidade
                    this.profissao = profissao
                }
            }
            """.formatted(pdf.toString().replace('\\', '/')));

        assertTrue(Files.isRegularFile(pdf), "save() must create the requested PDF");
        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.startsWith("%PDF-1.4\n"));
        assertTrue(content.endsWith("%%EOF\n"));
        assertTrue(content.contains("/Encoding /WinAnsiEncoding"));
        assertTrue(content.contains("Relat\\363rio de usu\\341rios"));
        assertTrue(content.contains("\\200 \\223Kof\\224 \\227"));
        assertTrue(content.contains("S\\343o Paulo"));
        assertTrue(content.contains("Profiss\\343o"));
        assertTrue(content.contains("Jo\\343o"));
        assertTrue(content.contains("1 0 0 rg\nBT /F1 20 Tf 40 802 Td"));
        assertTrue(content.contains("BT /F1 10 Tf 98 717 Td (Nome)"));

        // Four columns and custom widths are reflected in the generated layout.
        assertTrue(content.contains("40 702 140 30 re"));
        assertTrue(content.contains("180 702 50 30 re"));
        assertTrue(content.contains("230 702 170 30 re"));
        assertTrue(content.contains("400 702 155 30 re"));

        int startXref = content.lastIndexOf("startxref\n");
        int offsetStart = startXref + "startxref\n".length();
        int offsetEnd = content.indexOf('\n', offsetStart);
        int declaredXrefOffset = Integer.parseInt(content.substring(offsetStart, offsetEnd));
        assertEquals(content.indexOf("xref\n"), declaredXrefOffset,
                "startxref must point to the xref table");
    }

    @Test
    void createsSingleColumnGrid() throws Exception {
        Path pdf = tmp.resolve("uma-coluna.pdf");
        runKof("""
            import pdf.PdfDocument

            main() {
                var document = PdfDocument()
                document.grid(1)
                    .header(listOf("Nome"))
                    .row(listOf("Davi"))
                document.save("%s")
            }
            """.formatted(pdf.toString().replace('\\', '/')));

        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("40 778 515 24 re"));
        assertTrue(content.contains("40 754 515 24 re"));
        assertTrue(content.contains("(Nome) Tj"));
        assertTrue(content.contains("(Davi) Tj"));
    }

    @Test
    void rejectsInvalidGridAndStyleConfigurations() throws Exception {
        runKof("""
            import pdf.PdfDocument
            import pdf.style.GridStyle
            import pdf.style.PdfColor
            import pdf.style.TextStyle

            main() {
                var rejected = 0

                try { PdfDocument().grid(0) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfDocument().grid(2).header(listOf("Nome")) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfDocument().grid(2).widths(listOf(100)) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfDocument().grid(2).widths(listOf(100, -1)) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfColor("XYZ") }
                catch (String error) { rejected = rejected + 1 }

                try {
                    var tooWide = PdfDocument()
                    tooWide.grid(2).widths(listOf(400, 200))
                    tooWide.save("ignorado-largura.pdf")
                } catch (String error) { rejected = rejected + 1 }

                try {
                    var tooShort = PdfDocument()
                    tooShort.grid(1)
                        .style(GridStyle()
                            .rowHeight(10)
                            .headerStyle(TextStyle().fontSize(10)))
                        .header(listOf("Nome"))
                    tooShort.save("ignorado-altura.pdf")
                } catch (String error) { rejected = rejected + 1 }

                if (rejected != 7) {
                    throw "Nem todas as configurações inválidas foram rejeitadas"
                }
            }
            """);
    }

    private void runKof(String code) throws Exception {
        Path installRoot = tmp.resolve("kof-install");
        copyLibrary(installRoot.resolve("lib/kof-libs"));
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = Files.createTempDirectory(tmp, "pdf-out-");
        String previousInstallDir = System.getProperty("kof.install.dir");
        CompilationResult result;
        System.setProperty("kof.install.dir", installRoot.toString());
        try {
            result = driver.compile(source, out, Target.JVM);
        } finally {
            if (previousInstallDir == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previousInstallDir);
        }
        assertTrue(result.success(), () -> result.diagnostics().getDiagnostics().toString());

        try (var loader = new URLClassLoader(
                new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, loader)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        }
    }

    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("pdf")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/pdf");
        if (Files.isRegularFile(fromRepository.resolve("PdfDocument.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/pdf").normalize();
        if (Files.isRegularFile(fromModule.resolve("PdfDocument.kf"))) return fromModule;

        throw new IllegalStateException("libs/pdf not found from " + workingDirectory);
    }
}
