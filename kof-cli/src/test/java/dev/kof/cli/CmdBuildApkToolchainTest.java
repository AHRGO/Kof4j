package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CI red de 18/09 (ubuntu com SDK): o pipeline APK tinha build-tools PINADO em
 * 34.0.0 e um keytool de caminho relativo — duas causas de falha reais que o
 * skip-local escondia. Este teste prova as correcoes SEM SDK: selecao de
 * build-tools por versao e leitura do class-major do jar (guarda do d8).
 */
class CmdBuildApkToolchainTest {

    private static void touchExec(Path p) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "#!/bin/sh\nexit 0\n");
        p.toFile().setExecutable(true);
    }

    @Test
    void pickBuildToolsChoosesHighestUsable(@TempDir Path home) throws Exception {
        Path btRoot = home.resolve("build-tools");
        for (String v : new String[]{"34.0.0", "9.0.0", "35.0.0", "34.0.2"}) {
            touchExec(btRoot.resolve(v).resolve("aapt2"));
            touchExec(btRoot.resolve(v).resolve("d8"));
            touchExec(btRoot.resolve(v).resolve("zipalign"));
            touchExec(btRoot.resolve(v).resolve("apksigner"));
        }
        // incompleto: sem apksigner -> NAO e candidato
        touchExec(btRoot.resolve("36.0.0").resolve("aapt2"));
        touchExec(btRoot.resolve("36.0.0").resolve("d8"));

        assertEquals("35.0.0", ApkToolchain.pickBuildTools(home).getFileName().toString(),
                "maior versao COMPLETA vence (9 < 34 lexicamente, 36 incompleta)");
    }

    @Test
    void pickBuildToolsSemNadaUsavelRetornaNull(@TempDir Path home) throws Exception {
        assertNull(ApkToolchain.pickBuildTools(home));
        touchExec(home.resolve("build-tools/20.0.0/aapt2"));
        assertNull(ApkToolchain.pickBuildTools(home), "ferramentas faltando = null honesto");
    }

    @Test
    void compareToolVersionsNumericNaoLexico() {
        assertTrue(ApkToolchain.compareToolVersions("34.0.0", "9.0.0") > 0, "34 > 9 (nao '3'<'9')");
        assertTrue(ApkToolchain.compareToolVersions("34.0.2", "34.0.0") > 0);
        assertEquals(0, ApkToolchain.compareToolVersions("35.0.0", "35.0.0"));
    }

    @Test
    void java21GateRefusesOldD8ForMajor65(@TempDir Path home) throws Exception {
        Path jar = home.resolve("kof-app.jar");
        writeJarWithClassMajor(jar, 65);
        assertEquals(65, ApkToolchain.classMajorOf(jar), "major lido do header (bytes 6-7)");
        Path bt34 = home.resolve("build-tools/34.0.0");
        Files.createDirectories(bt34);
        assertFalse(ApkToolchain.buildToolsSupportsJava21(bt34), "34.x nao le major 65");
        assertFalse(ApkToolchain.buildToolsSupportsJava21(home.resolve("build-tools/9.0.0")));
        assertTrue(ApkToolchain.buildToolsSupportsJava21(home.resolve("build-tools/35.0.0")));
        writeJarWithClassMajor(jar, 61);
        assertEquals(61, ApkToolchain.classMajorOf(jar));
    }

    private static void writeJarWithClassMajor(Path jar, int major) throws IOException {
        byte[] header = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0,
                (byte) (major >> 8), (byte) major};
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            z.putNextEntry(new ZipEntry("Default/Main.class"));
            z.write(header);
            z.closeEntry();
        }
    }
}
