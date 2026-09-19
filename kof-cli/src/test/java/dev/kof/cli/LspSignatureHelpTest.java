package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * LSP-A (plano universal 8.3, 19/09): o motor de signatureHelp le a MESMA
 * tabela do hover; fora de chamada com tabela devolve null — nunca chute (R6).
 */
class LspSignatureHelpTest {

    @Test
    void insideTabledCallReturnsFormsAndActiveArg() {
        String t = "main() { val d = db.connect(\"x\", y";
        Map<String, Object> r = LspSignatureHelp.helpFor(t, t.length());
        assertNotNull(r, "db.connect( com tabela nao pode dar null");
        assertEquals(1L, r.get("activeParameter"), "cursor no 2o argumento");
        List<?> sigs = (List<?>) r.get("signatures");
        assertEquals(2, sigs.size(), "db.connect tem 2 formas gravadas");
        String label = String.valueOf(((Map<?, ?>) sigs.get(0)).get("label"));
        assertTrue(label.contains("connect(String url) -> String"), label);
        List<?> params = (List<?>) ((Map<?, ?>) sigs.get(0)).get("parameters");
        assertEquals("String url", ((Map<?, ?>) params.get(0)).get("label"));
    }

    @Test
    void justAfterOpenParenIsActiveZero() {
        String t = "main() { val s = time.sleep(";
        Map<String, Object> r = LspSignatureHelp.helpFor(t, t.length());
        assertNotNull(r);
        assertEquals(0L, r.get("activeParameter"), r.toString());
    }

    @Test
    void untabledMemberStaysNull() {
        String t = "main() { val j = json.encode(";
        assertNull(LspSignatureHelp.helpFor(t, t.length()),
                "json nao tem tabela (dispatch por tipo no lowerer) — null honesto");
        String u = "main() { val z = zzz.unknown(";
        assertNull(LspSignatureHelp.helpFor(u, u.length()), "fora do catalogo => null");
    }

    @Test
    void overloadsComeAllTogether() {
        String t = "main() { val j = jwt.create(t, k";
        Map<String, Object> r = LspSignatureHelp.helpFor(t, t.length());
        assertNotNull(r, "jwt.create(2|3) tem tabela");
        assertTrue(((List<?>) r.get("signatures")).size() >= 2,
                "overloads create(2) e create(3): " + r.get("signatures"));
        assertEquals(1L, r.get("activeParameter"));
    }

    @Test
    void nestedParensAndStringsDoNotInflateArgIndex() {
        String t = "main() { security.cookieSet(f(x), y";
        Map<String, Object> r = LspSignatureHelp.helpFor(t, t.length());
        assertNotNull(r);
        assertEquals(1L, r.get("activeParameter"), "parenteses aninhados contam como 1 argumento");
        String u = "main() { val d = db.connect(\"a,b\", z";
        Map<String, Object> ru = LspSignatureHelp.helpFor(u, u.length());
        assertNotNull(ru);
        assertEquals(1L, ru.get("activeParameter"), "virgula dentro de string nao e separador");
    }

    @Test
    void closedCallAndAmbiguousBareMemberAreNull() {
        String t = "main() { val d = db.connect(\"x\")";
        assertNull(LspSignatureHelp.helpFor(t, t.length()), "chamada fechada => sem ajuda");
        String u = "main() { val v = get(";
        assertNull(LspSignatureHelp.helpFor(u, u.length()),
                "membro solto ambiguo (http.get/config.get/...) => null, nunca sorteio");
        String w = "main() { val h = hmacSha256(";
        assertNotNull(LspSignatureHelp.helpFor(w, w.length()),
                "membro solto UNICO no catalogo resolve (crypto)");
    }

    @Test
    void activeParameterClampsToWidestOverload() {
        String t = "main() { val d = db.connect(\"a\", \"b\", \"c\", extra";
        Map<String, Object> r = LspSignatureHelp.helpFor(t, t.length());
        assertNotNull(r);
        int act = (Integer) ((Number) r.get("activeParameter")).intValue();
        assertTrue(act >= 2 && act <= 2, "max 3 params -> clamp 2: " + act);
    }
}
