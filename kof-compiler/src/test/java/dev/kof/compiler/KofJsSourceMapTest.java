package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source map V3 do KofJS (debugger/VS Code) — o backend gera um
 * {@code Default.mjs.map} real (mappings VLQ não vazios) em nível de linha:
 * cada cabeçalho de função gerado aponta para uma linha da fonte Kof
 * (via {@code KofDebugInfo}, mesma fonte das line tables do JVM). Antes era
 * um stub com {@code "mappings":""}.
 */
class KofJsSourceMapTest {

    private static final String VLQ_B64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void jsSourceMapMapsFunctionsToKofLines(@TempDir Path tempDir) throws Exception {
        String src = """
                main() {
                    helper()
                    println("ok")
                }

                Int helper() {
                    return 42
                }
                """;
        Path file = tempDir.resolve("Main.kf");
        Files.writeString(file, src);
        Path outDir = tempDir.resolve("out-js");
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());

        String js = Files.readString(outDir.resolve("Default.mjs"));
        assertTrue(js.contains("//# sourceMappingURL=Default.mjs.map"),
                "sourceMappingURL comment ausente");

        String mapJson = Files.readString(outDir.resolve("Default.mjs.map"));
        int ver = jsonInt(mapJson, "version");
        assertEquals(3, ver, "source map V3");
        assertTrue(mapJson.contains("\"sources\":[\"Default.kf\"]"), "sources aponta p/ Default.kf");
        String mappings = jsonString(mapJson, "mappings");
        assertFalse(mappings.isEmpty(), "mappings não podem estar vazias (antes era stub)");

        // decodifica os mappings VLQ → mapa linhaGerada(1-based) → linhaKof(1-based)
        Map<Integer, Integer> byGen = decode(mappings);
        assertFalse(byGen.isEmpty(), "mappings sem nenhum segmento decodificável");

        // localiza as linhas geradas dos cabeçalhos de função
        int mainLine = lineOf(js, "function main(");
        int helperLine = lineOf(js, "function helper(");
        assertTrue(mainLine > 0 && helperLine > 0, "cabeçalhos de função não encontrados no .mjs");

        // main: corpo Kof linhas 1..4; helper: linhas 6..8
        Integer mainKof = byGen.get(mainLine);
        Integer helperKof = byGen.get(helperLine);
        assertNotNull(mainKof, "main não tem mapeamento na linha " + mainLine);
        assertNotNull(helperKof, "helper não tem mapeamento na linha " + helperLine);
        assertTrue(mainKof >= 1 && mainKof <= 4,
                "main deve mapear p/ uma linha do corpo Kof (1..4), got " + mainKof);
        assertTrue(helperKof >= 6 && helperKof <= 8,
                "helper deve mapear p/ uma linha do corpo Kof (6..8), got " + helperKof);
    }

    /** Linha (1-based) de {@code needle} no texto, ou 0. */
    private static int lineOf(String text, String needle) {
        int at = text.indexOf(needle);
        if (at < 0) return 0;
        int line = 1;
        for (int i = 0; i < at; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    /**
     * Decodifica as mappings V3 (formato padrão) emitidas pelo Kof, em nível
     * de linha: cada entrada (separada por {@code ';'}) é uma linha gerada;
     * um segmento {@code [genCol,srcIdx,srcLine,srcCol]} acumulado dá a linha
     * Kof (1-based) para a linha gerada correspondente.
     */
    private static Map<Integer, Integer> decode(String mappings) {
        Map<Integer, Integer> byGen = new LinkedHashMap<>();
        int genLine = 1;
        int prevSrcLine0 = 0;
        for (String entry : mappings.split(";", -1)) {
            if (!entry.isEmpty()) {
                int p = 0;
                int[] g = vlqAt(entry, p); p = g[1];   // genCol (zera p/ linha)
                int[] s = vlqAt(entry, p); p = s[1];   // srcIdx (0)
                int[] l = vlqAt(entry, p); p = l[1];   // delta srcLine (0-based)
                prevSrcLine0 += l[0];
                byGen.put(genLine, prevSrcLine0 + 1);  // linha Kof 1-based
            }
            genLine++;
        }
        return byGen;
    }

    /** Lê um número VLQ em {@code s,from} → {valor, novaPosição}. */
    private static int[] vlqAt(String s, int from) {
        int result = 0, shift = 0, i = from;
        while (i < s.length()) {
            char c = s.charAt(i++);
            int digit = VLQ_B64.indexOf(c);
            int cont = digit & 32;
            digit &= 31;
            result |= (digit << shift);
            shift += 5;
            if (cont == 0) break;
        }
        int value = (result & 1) != 0 ? -((result >> 1) + 1) : (result >> 1);
        return new int[]{value, i};
    }

    /** Extrai um int JSON simples: "key":N. */
    private static int jsonInt(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        assertTrue(i >= 0, "chave '" + key + "' ausente no JSON");
        i = json.indexOf(':', i);
        int start = i;
        while (start < json.length() && !Character.isDigit(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        // JSON generated by test — valid integer guaranteed
        @SuppressWarnings("NumberFormatException")
        int parsed = Integer.parseInt(json.substring(start, end));
        return parsed;
    }

    /** Extrai uma string JSON simples: "key":"...". */
    private static String jsonString(String json, String key) {
        int i = json.indexOf("\"" + key + "\":\"");
        assertTrue(i >= 0, "chave '" + key + "' ausente ou não é string");
        int start = i + key.length() + 4;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
