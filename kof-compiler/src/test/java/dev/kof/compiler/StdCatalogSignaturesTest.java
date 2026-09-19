package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * LSP-A (19/09): trava COMPORTAMENTAL da tabela de assinaturas do
 * {@link StdCatalog} — cada forma gravada e re-executada contra o dispatcher
 * REAL (KofDb.staticCall / KofHttp.staticCall): aridade gravada binda; a
 * aridade proibida pela fonte (bind 5 do db, zero-arg/sem-body do http) nao
 * binda. A tabela nao e comentario: divergir do dispatcher quebra aqui
 * (mesma filosofia de trava do StdCatalogTest, no nivel da assinatura).
 */
class StdCatalogSignaturesTest {

    private static final Type S = BuiltinTypes.STRING;
    private static final Type I = Type.PrimitiveType.INT;
    private static final Type O = Type.UnknownType.UNKNOWN;

    @Test
    void dbTableBindsAgainstRealDispatcher() {
        assertNotNull(KofDb.staticCall("connect", List.of(S), true), "connect(1)");
        assertNull(KofDb.staticCall("connect", List.of(S, S), true), "connect(2) NAO binda");
        assertNotNull(KofDb.staticCall("connect", List.of(S, S, S), true), "connect(3)");
        assertNotNull(KofDb.staticCall("close", List.of(S), true), "close");
        assertNull(KofDb.staticCall("close", List.of(S, S), true), "close(2)");
        assertNotNull(KofDb.staticCall("transaction", List.of(O), true), "transaction(cb)");
        assertNotNull(KofDb.staticCall("query", List.of(S, S), true), "query sem bind");
        assertNotNull(KofDb.staticCall("query", List.of(S, S, O, O, O, O), true), "query bind 4");
        assertNull(KofDb.staticCall("query", List.of(S, S, O, O, O, O, O), true),
                "query bind 5 excede MAX_BIND");
        assertNotNull(KofDb.staticCall("execute", List.of(S, S), true), "execute sem bind");
        assertNotNull(KofDb.staticCall("execute", List.of(S, S, O), true), "execute bind 1");
        assertNull(KofDb.staticCall("execute", List.of(S), true), "execute 1 arg NAO binda");
        for (String m : List.of("connect", "query", "execute", "close", "transaction")) {
            assertFalse(StdCatalog.signaturesOf("db", m).isEmpty(), "tabela sem db." + m);
            assertTrue(KofDb.functions().contains(m), "catalog db sem " + m);
        }
        assertTrue(StdCatalog.signaturesOf("db", "ghost").isEmpty(), "assinatura fantasma");
    }

    @Test
    void httpTableBindsAgainstRealDispatcher() {
        for (String v : List.of("get", "delete", "options")) {
            assertNotNull(KofHttp.staticCall(v, List.of(S)), v);
            assertNotNull(KofHttp.staticCall(v, List.of(S, S)), v + " headers");
            assertNull(KofHttp.staticCall(v, List.of()), v + " zero-arg NAO binda");
        }
        for (String v : List.of("post", "put", "patch")) {
            assertNotNull(KofHttp.staticCall(v, List.of(S, S)), v);
            assertNotNull(KofHttp.staticCall(v, List.of(S, S, S)), v + " headers");
            assertNull(KofHttp.staticCall(v, List.of(S)), v + " sem body NAO binda");
        }
        assertNotNull(KofHttp.staticCall("status", List.of(S)), "status");
        assertNull(KofHttp.staticCall("status", List.of()), "status sem url");
        for (String c : List.of("timeout", "retry", "circuit")) {
            assertNotNull(KofHttp.staticCall(c, List.of(I)), c);
            assertNull(KofHttp.staticCall(c, List.of()), c + " sem argumento");
        }
        for (String m : KofHttp.functions()) {
            assertFalse(StdCatalog.signaturesOf("http", m).isEmpty(), "tabela sem http." + m);
        }
    }

    @Test
    void untabledNamespacesStayHonestEmpty() {
        // fatia 1 = db+http; inventar forma p/ os demais e proibido (R6)
        assertTrue(StdCatalog.signaturesOf("time", "sleep").isEmpty());
        assertTrue(StdCatalog.signaturesOf("cache", "get").isEmpty());
        assertTrue(StdCatalog.signaturesOf("nope", "get").isEmpty());
    }
}
