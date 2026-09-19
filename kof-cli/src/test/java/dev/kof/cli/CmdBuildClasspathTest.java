package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #441 (Windows): {@code kof build --classpath "C:\first.jar;C:\second.jar"}
 * era splitado por {@code [:;]} — o {@code :} da unidade quebrava cada entrada
 * em fragmentos ("C", "\first.jar"...) e o ExternalClasspath emitia
 * {@code CP002 classpath entry not found: C} falso. O split agora usa o
 * separador real da plataforma ({@code File.pathSeparatorChar}); o host do
 * teste é Linux, então a semântica Windows é provada com o separador injetado.
 * Zero regressão no Unix: {@code :} continua splitando, e a tolerância
 * histórica ao {@code ;} é mantida no modo Unix.
 */
class CmdBuildClasspathTest {

    @Test
    void windowsSeparatorKeepsDrivePrefixes() {
        String cp = "C:\\Users\\juann\\.gradle\\caches\\modules-2\\skiko-awt-0.144.0.jar;"
                + "C:\\Users\\juann\\.gradle\\caches\\modules-2\\kotlin-stdlib.jar";
        List<String> parts = CmdBuild.splitClasspathEntries(cp, ';');
        assertEquals(2, parts.size(), "windows: ';' e NUNCA ':' separam:\n" + parts);
        assertTrue(parts.get(0).startsWith("C:\\"), "drive prefix intacto:\n" + parts);
        assertTrue(parts.get(1).startsWith("C:\\"), "drive prefix intacto:\n" + parts);
        assertFalse(parts.contains("C"), "sem fragmento-fantasma 'C' (CP002 falso do #441):\n" + parts);
    }

    @Test
    void windowsSingleEntryKeepsColonWhole() {
        List<String> parts = CmdBuild.splitClasspathEntries("C:\\libs\\only.jar", ';');
        assertEquals(List.of("C:\\libs\\only.jar"), parts, "windows: um unico C:\\... nao se parte");
    }

    @Test
    void unixSeparatorSplitsColonAndKeepsLegacySemicolon() {
        assertEquals(List.of("/a/first.jar", "/b/second.jar"),
                CmdBuild.splitClasspathEntries("/a/first.jar:/b/second.jar", ':'),
                "unix: ':' separa (comportamento historico)");
        assertEquals(List.of("/a/first.jar", "/b/second.jar"),
                CmdBuild.splitClasspathEntries("/a/first.jar;/b/second.jar", ':'),
                "unix: ';' continua aceito (zero regressao para quem passava os dois)");
        assertEquals(3, CmdBuild.splitClasspathEntries("/a.jar;b.jar:/c.jar", ':').size(),
                "unix: mixtos seguem os dois separadores de hoje");
    }

    @Test
    void blankAndEmptyProduceNoEntries() {
        assertTrue(CmdBuild.splitClasspathEntries(null, ':').isEmpty(), "null = vazio");
        assertTrue(CmdBuild.splitClasspathEntries("   ", ';').isEmpty(), "blank = vazio");
        assertTrue(CmdBuild.splitClasspathEntries(";;;", ';').isEmpty(), "so separadores = vazio");
    }

    @Test
    void productionDefaultsToPlatformSeparator() {
        String cp = "/x/first.jar:/y/second.jar";
        assertEquals(CmdBuild.splitClasspathEntries(cp, File.pathSeparatorChar),
                CmdBuild.splitClasspathEntries(cp),
                "o call site do build usa File.pathSeparatorChar — mesmo resultado da injecao");
    }
}
