package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-APP I1 ({@code DECISIONS.md} §D-APP): {@code kof new} — esqueletos por
 * tipo nascendo compiláveis + APP003 honesto (R6: nunca silencioso).
 *
 * <p>Q3: cada tipo gera os arquivos esperados e o manifesto é parseável por
 * {@code KofProjectConfig}; esqueleto backend/full-stack COMPILA de verdade
 * (gateway JVM do driver); bordas: kof.toml existente, tipo inválido, flag
 * desconhecida, dir existente sem conflito.</p>
 */
class CmdNewTest {

    @Test
    void backendSkeletonHasManifestAndApi(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "backend")));
        assertTrue(Files.exists(dir.resolve("kof.toml")), "manifesto deve existir");
        assertTrue(Files.exists(dir.resolve("src/Main.kf")), "backend deve nascer com src/Main.kf");
        String manifest = Files.readString(dir.resolve("kof.toml"));
        assertTrue(manifest.contains("[backend]"), "seção [backend]: " + manifest);
        var cfg = dev.kof.compiler.KofProjectConfig.parse(manifest);
        assertEquals("jvm", cfg.backendTarget(), "target do backend");
        assertTrue(Files.readString(dir.resolve("src/Main.kf")).contains("web.app()"),
                "esqueleto backend usa kof.web (plataforma, não re-implementação)");
    }

    @Test
    void frontendSkeletonHasKofjsTarget(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "frontend")));
        var cfg = dev.kof.compiler.KofProjectConfig.parse(Files.readString(dir.resolve("kof.toml")));
        assertEquals("kofjs", cfg.frontendTarget());
        assertTrue(Files.exists(dir.resolve("src/web/Index.kf")));
    }

    @Test
    void fullStackSkeletonHasBothSectionsAndBackendCompiles(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "full-stack")));
        var cfg = dev.kof.compiler.KofProjectConfig.parse(Files.readString(dir.resolve("kof.toml")));
        assertEquals("jvm", cfg.backendTarget());
        assertEquals("kofjs", cfg.frontendTarget());
        assertTrue(Files.exists(dir.resolve("src/web/Index.kf")));
        assertTrue(Files.exists(dir.resolve("src/static/app.css")));
        // O esqueleto nasce COMPILÁVEL (prova real, não só arquivos no disco).
        var result = new dev.kof.compiler.CompilerDriver()
                .compile(dir.resolve("src/Main.kf"), dir.resolve("target/probe"), dev.kof.compiler.Target.JVM);
        assertTrue(result.success(),
                "esqueleto full-stack deve compilar JVM: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void backendSkeletonCompiles(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "backend")));
        var result = new dev.kof.compiler.CompilerDriver()
                .compile(dir.resolve("src/Main.kf"), dir.resolve("target/probe"), dev.kof.compiler.Target.JVM);
        assertTrue(result.success(),
                "esqueleto backend deve compilar JVM: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void unknownTypeIsHonestGapApp003(@TempDir Path dir) {
        assertNotEquals(0, CmdNew.run(with(dir, "--type", "microservices")));
        assertFalse(Files.exists(dir.resolve("kof.toml")), "falha não deve criar projeto parcial");
    }

    @Test
    void existingManifestIsRefusedApp003(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "mono")));
        String before = Files.readString(dir.resolve("kof.toml"));
        assertNotEquals(0, CmdNew.run(with(dir, "--type", "backend")));
        assertEquals(before, Files.readString(dir.resolve("kof.toml")), "nunca sobrescrever");
    }

    @Test
    void unknownFlagIsRefusedApp003(@TempDir Path dir) {
        assertNotEquals(0, CmdNew.run(with(dir, "--rust")));
        assertFalse(Files.exists(dir.resolve("kof.toml")));
    }

    @Test
    void missingDirIsUsageNotCrash() {
        assertNotEquals(0, CmdNew.run(new String[0]));
    }

    private static String[] with(Path dir, String... rest) {
        String[] out = new String[1 + rest.length];
        out[0] = dir.toString();
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }
}
