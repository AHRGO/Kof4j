package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * LSP-A (linha 8.3 do plano universal): o hover de um membro stdlib em contexto
 * de ponto mostra a assinatura gravada no StdCatalog (fonte unica travada em
 * StdCatalogSignaturesTest); membro SEM tabela continua com a linha simples —
 * nunca inventa forma (R6).
 */
class LspSignatureHoverTest {

    @Test
    void hoverShowsRecordedSignatureForDbAndHttp() {
        String t = "main() { val d = db.connect(\"x\")\n    val r = http.get(\"u\") }\n";
        String v = LspHover.hoverFor("connect", t, t.indexOf("connect") + 3);
        assertTrue(v.contains("connect(String url) -> String"), "db.connect: " + v);
        String v2 = LspHover.hoverFor("get", t, t.indexOf("http.get") + 6);
        assertTrue(v2.contains("get(String url) -> String"), "http.get: " + v2);
        assertTrue(v2.contains("headers..."), "overload de headers faltando: " + v2);
    }

    @Test
    void overloadsAppearTogether() {
        String t = "main() { val q = db.query(\"u\", \"select 1\") }\n";
        String v = LspHover.hoverFor("query", t, t.indexOf("query") + 2);
        assertTrue(v.contains("query(String url, String sql) -> List<String>"), v);
        assertTrue(v.contains("binds[1..4]"), "forma com bind faltando: " + v);
    }

    @Test
    void fatiaTwoNamespacesShowSignaturesToo() {
        String t = "main() { val x = time.sleep(10)\n    val p = process.run(\"ls\") }\n";
        String v = LspHover.hoverFor("sleep", t, t.indexOf("sleep") + 2);
        assertTrue(v.contains("sleep(Int ms) -> void"), "time.sleep: " + v);
        String v2 = LspHover.hoverFor("run", t, t.indexOf("process.run") + 8);
        assertTrue(v2.contains("run(String program, String... args) -> Result"), "process.run: " + v2);
    }

    @Test
    void memberWithoutTableKeepsSimpleHoverNoInvention() {
        String t = "main() { val v = log.info(\"x\") }\n";
        String v = LspHover.hoverFor("info", t, t.indexOf("info") + 1);
        assertTrue(v.contains("member of `kof.log`"), v);
        assertFalse(v.contains("```"), "sem tabela = sem assinatura inventada: " + v);
    }
}
