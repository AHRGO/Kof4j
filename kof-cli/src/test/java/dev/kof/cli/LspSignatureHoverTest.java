package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void fatiaThreeNamespacesShowSignaturesToo() {
        String t = "main() { val a = strings.slugify(\"x\")\n    val b = net.host(a) }\n";
        String v = LspHover.hoverFor("slugify", t, t.indexOf("slugify") + 3);
        assertTrue(v.contains("slugify(String s) -> String"), "strings.slugify: " + v);
        String v2 = LspHover.hoverFor("host", t, t.indexOf("net.host") + 5);
        assertTrue(v2.contains("host(String url) -> String"), "net.host: " + v2);
    }

    @Test
    void fatiaFourSixNamespacesShowSignaturesToo() {
        String t = "main() { val p = math.pow(2.0, 8.0)\n    log.info(\"go\")\n    val h = crypto.hmacSha256(\"a\", \"b\")\n    val img = Image.open(\"x\") }\n";
        String v = LspHover.hoverFor("pow", t, t.indexOf("pow") + 2);
        assertTrue(v.contains("pow(Double base, Double exp) -> Double"), "math.pow: " + v);
        String v2 = LspHover.hoverFor("info", t, t.indexOf("log.info") + 5);
        assertTrue(v2.contains("info(String msg) -> void"), "log.info: " + v2);
        String v3 = LspHover.hoverFor("hmacSha256", t, t.indexOf("hmacSha256") + 5);
        assertTrue(v3.contains("hmacSha256(String key, String msg) -> String"), "crypto: " + v3);
        String v4 = LspHover.hoverFor("open", t, t.indexOf("Image.open") + 7);
        assertTrue(v4.contains("open(String path) -> ImageData"), "Image.open: " + v4);
    }

    @Test
    void memberWithoutTableKeepsSimpleHoverNoInvention() {
        // 32/32 (fechamento X10, 19/09): todo membro do catalogo tem tabela —
        // o caso "catalogado sem tabela" nao existe mais; a clausula de
        // honestidade (R6: nao inventar assinatura) vale p/ membro FORA do
        // catalogo. json.encode, que era o probe antigo, agora DEVOLVE a
        // forma real travada (encode(value) -> String).
        String t = "main() { val z = zzz.unknown() }\n";
        String v = LspHover.hoverFor("unknown", t, t.indexOf("unknown") + 3);
        assertNull(v, "fora do catalogo = null honesto (R6: nao inventar): " + v);
        String j = "main() { val x = json.encode(\"s\") }\n";
        String hj = LspHover.hoverFor("encode", j, j.indexOf("encode") + 3);
        assertTrue(hj.contains("encode(value) -> String"), "json.encode agora tem tabela: " + hj);
    }
}
