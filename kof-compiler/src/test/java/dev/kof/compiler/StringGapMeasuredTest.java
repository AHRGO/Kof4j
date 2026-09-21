package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Medição comportamental do {@code known-bugs.md §424} (frente de revisão, 21/09
 * — Fatia 11). Não lê a fonte nem a matriz: compila um programa Kof mínimo que
 * usa um dos métodos de {@code String} sem lowering e MEDE o artefato emitido,
 * provando o mecanismo do bug (o "golden" é o emit real, Q3).
 *
 * <p>É um teste de CARACTERIZAÇÃO de bug catalogado (precedente §149): ele fixa
 * o comportamento ATUAL para que a correção (um diagnóstico honesto em vez do
 * link-fail/TypeError — decisão rule-6) precise mudar este arquivo de propósito.
 */
class StringGapMeasuredTest {

    private static final String SRC = """
        main() {
            println("abc".matches("a.*"))
        }
        """;

    private final CompilerDriver driver = new CompilerDriver();

    private String emit(Path tmp, Target target) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC);
        Path out = tmp.resolve("out-" + target.name());
        CompilationResult result = driver.compile(file, out, target);
        assertTrue(result.success(), target + ": compile falhou: "
                + result.diagnostics().getDiagnostics());
        StringBuilder sb = new StringBuilder();
        try (var walk = Files.walk(out)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                if (n.endsWith(".s") || n.endsWith(".js") || n.endsWith(".mjs")
                        || n.endsWith(".ll") || n.endsWith(".c")) {
                    sb.append("\n/* === ").append(p.getFileName()).append(" === */\n");
                    sb.append(Files.readString(p));
                }
            }
        }
        return sb.toString();
    }

    private static String window(String text, String needle, int before, int after) {
        int i = text.indexOf(needle);
        if (i < 0) {
            return "<'" + needle + "' AUSENTE no emit>";
        }
        return text.substring(Math.max(0, i - before), Math.min(text.length(), i + after));
    }

    @Test
    @DisplayName("§424 JS: cai no default -> chamada a membro JS inexistente (matcher)")
    void jsFallsThroughToNonexistentJsMember(@TempDir Path tmp) throws Exception {
        String emit = emit(tmp, Target.JS);
        assertTrue(emit.contains("matches"),
                "§424 JS: esperado o mapeamento direto .matches(...) (membro JS inexistente)."
                        + " Emitido (janela): " + window(emit, "\n", 0, 400));
        assertTrue(!emit.contains("kofStringMatches") && !emit.contains("kof_string_matches"),
                "§424 JS: nao deveria existir helper de matches — o bug e' o default silencioso."
                        + " Janela: " + window(emit, "matches", 120, 120));
    }

    @Test
    @DisplayName("§424 Native: LINK-FAIL medido (ld: undefined reference a java_lang_String_matches)")
    void nativeLinkFailsWithSynthesizedSymbol(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC);
        CompilationResult result =
                driver.compile(file, tmp.resolve("out-NATIVE"), Target.NATIVE);
        assertFalse(result.success(),
                "§424 Native: o compile tem de FALHAR (ld: undefined reference ao simbolo"
                        + " sintetizado) — se passou, o gap foi corrigido e este pin muda");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("java_lang_String_matches"),
                "§424 Native: o link-fail deve nomear java_lang_String_matches. Diag: "
                        + window(diag, "undefined", 0, 140));
    }
}
