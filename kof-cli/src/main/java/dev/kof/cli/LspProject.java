package dev.kof.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Varredura da árvore de arquivos do projeto para o LSP (X10 fatias 4–5,
 * extraída de LspServer no split ≤600 de 18/09): irmãos `.kf` e leitura
 * tolerante a falha. Estrutura-only; comportamento idêntico.
 */
final class LspProject {

    private LspProject() {}

    static Path toPath(String uri) {
        try {
            if (uri == null || !uri.startsWith("file:")) return null;
            return Path.of(java.net.URI.create(uri));
        } catch (Exception e) {
            return null;
        }
    }

    /** Arquivos `.kf` irmãos na árvore do projeto (profundidade ≤6, ordenados). */
    static List<Path> siblings(Path self) {
        if (self.getParent() == null) return List.of();
        try (var stream = Files.walk(self.getParent(), 6)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".kf"))
                    .filter(p -> !p.toAbsolutePath().equals(self.toAbsolutePath()))
                    .sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    static String readOrNull(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
