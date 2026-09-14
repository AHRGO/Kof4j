package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.*;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §CodeQL deref-null (CompilerImports:67/121): a expansão de imports de
 * diretório reportava os erros de parse do arquivo importado SEM o guard
 * {@code currentDiagnostics != null} que TODO os outros ramos da mesma função
 * usam (PKG003/PKG004) — um driver sem collector caía em NPE em vez de
 * silenciar. Prova da regressão: o caminho com collector null.
 */
class CompilerImportsNullDiagnosticsTest {

    @Test
    void dirImportWithParseErrorsDoesNotNpeWhenDiagnosticsIsNull(@TempDir Path moduleRoot)
            throws IOException {
        Path pkgDir = moduleRoot.resolve("pkg");
        Files.createDirectories(pkgDir);
        // .kf malformado: silencia os tokens -> o parser do arquivo importado
        // tem ERROS (silent.hasErrors() true), o ramo do report sem guard.
        Files.writeString(pkgDir.resolve("Bad.kf"), "@@@ ??? !!!");

        DiagnosticCollector mainDiags = new DiagnosticCollector();
        Lexer lx = new Lexer("import pkg\nmain() { }", "Main.kf", mainDiags);
        CompilationUnitNode unit = new Parser(lx.tokenize(), mainDiags, "Main.kf").parse();
        Map<AstNode, String> declPkgs = new HashMap<>();

        // O bug: com currentDiagnostics == null, o report do erro do Bad.kf
        // dava NullPointerException. Com o guard, a expansão apenas ignora o
        // arquivo inválido e retorna a unidade.
        assertDoesNotThrow(() -> CompilerImports.expandKofImports(
                unit, moduleRoot, null, declPkgs, null),
                "expansão com diagnostics null não pode lançar NPE (bug §CodeQL deref-null)");
    }

    @Test
    void dirImportWithParseErrorsStillReportsWhenDiagnosticsPresent(@TempDir Path moduleRoot)
            throws IOException {
        Path pkgDir = moduleRoot.resolve("pkg");
        Files.createDirectories(pkgDir);
        Files.writeString(pkgDir.resolve("Bad.kf"), "@@@ ??? !!!");

        DiagnosticCollector mainDiags = new DiagnosticCollector();
        Lexer lx = new Lexer("import pkg\nmain() { }", "Main.kf", mainDiags);
        CompilationUnitNode unit = new Parser(lx.tokenize(), mainDiags, "Main.kf").parse();
        DiagnosticCollector collector = new DiagnosticCollector();

        CompilerImports.expandKofImports(unit, moduleRoot, collector, new HashMap<>(), null);
        assertTrue(collector.hasErrors(),
                "com collector presente, o erro de parse do importado DEVE ser reportado (R6 — nada silencioso)");
    }
}
