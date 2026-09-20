package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Binários do JDK em caminho ABSOLUTO, sem concatenação de string.
 *
 * Os E2E que disparam `javac`/`java` como subprocesso montavam o comando de
 * duas formas que o CodeQL marca (justamente): `System.getProperty("java.home")
 * + "/bin/java"` (java/concatenated-command-line) e `"java"`/`"javac"` soltos
 * (java/relative-path-command — depende do PATH do shell, não determinístico).
 * Este helper resolve os dois: `Path.of(javaHome, "bin", "java")` não é
 * concatenação de string e é caminho absoluto. #555.
 */
final class TestJdk {

    private TestJdk() {}

    static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    static String javacBin() {
        return Path.of(System.getProperty("java.home"), "bin", "javac").toString();
    }

    /** Variante não-lançante de {@link #onPath(String)} para call-sites sem
     *  {@code throws} (ex.: inside ProcessBuilder). Mesmo contrato de miss:
     *  devolve o nome cru e deixa o processo falhar com a mensagem natural (R6). */
    static String which(String name) {
        try {
            return onPath(name);
        } catch (java.io.IOException e) {
            return name;
        }
    }

    /** Resolve um executável do PATH de forma absoluta (ex.: "sh", "node"). */
    static String onPath(String name) throws IOException {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (dir.isEmpty()) continue;
                Path cand = Path.of(dir, name);
                if (Files.isExecutable(cand)) return cand.toString();
            }
        }
        return name; // deixa o ProcessBuilder falhar com a mensagem natural (R6)
    }
}
