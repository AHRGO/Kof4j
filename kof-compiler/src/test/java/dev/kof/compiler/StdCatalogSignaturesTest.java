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
    void timeTableBindsAgainstRealDispatcher() {
        assertNotNull(KofTime.staticCall("sleep", List.of(I)), "sleep(Int)");
        assertNull(KofTime.staticCall("sleep", List.of()), "sleep sem argumento");
        assertNotNull(KofTime.staticCall("now", List.of()), "now()");
        assertNull(KofTime.staticCall("now", List.of(I)), "now nao aceita args");
        assertNotNull(KofTime.staticCall("interval", List.of(I, O)), "interval(Int, cb)");
        assertNull(KofTime.staticCall("interval", List.of(I)), "interval sem cb");
        assertNotNull(KofTime.staticCall("daysBetween", List.of(I, I, I, I, I, I)), "daysBetween(6)");
        assertNull(KofTime.staticCall("daysBetween", List.of(I, I, I, I, I)), "daysBetween(5)");
        assertNotNull(KofTime.staticCall("hoursBetween", List.of(I, I, I, I, I, I, I, I)), "hoursBetween(8)");
        assertNull(KofTime.staticCall("hoursBetween", List.of(I, I, I, I, I, I, I)), "hoursBetween(7)");
        assertNull(KofTime.staticCall("isLeapYear", List.of(S)), "isLeapYear(String) NAO binda (gate de tipo)");
        assertNull(KofTime.staticCall("addDays", List.of(S, S)), "addDays(String,String) NAO binda");
        assertNotNull(KofTime.staticCall("addDays", List.of(S, I)), "addDays(String,Int)");
        assertNotNull(KofTime.staticCall("tzOffsetSeconds", List.of()), "tzOffsetSeconds()");
        for (String m : KofTime.functions())
            assertFalse(StdCatalog.signaturesOf("time", m).isEmpty(), "tabela sem time." + m);
    }

    @Test
    void cacheProcessShellTablesBindAgainstRealDispatchers() {
        assertNotNull(KofCache.staticCall("get", List.of(S)), "cache.get");
        assertNull(KofCache.staticCall("get", List.of()), "cache.get/0");
        assertNotNull(KofCache.staticCall("set", List.of(S, S)), "cache.set/2");
        assertNotNull(KofCache.staticCall("set", List.of(S, S, I)), "cache.set/3 (ttl)");
        assertNull(KofCache.staticCall("set", List.of(S, S, I, I)), "cache.set/4 NAO binda");
        assertNotNull(KofCache.staticCall("clear", List.of()), "cache.clear");
        assertNull(KofCache.staticCall("clear", List.of(S)), "cache.clear/1 NAO binda");
        for (String m : KofCache.functions())
            assertFalse(StdCatalog.signaturesOf("cache", m).isEmpty(), "tabela sem cache." + m);

        assertNotNull(KofProcess.entryCall("run", List.of(S)), "process.run/1");
        assertNotNull(KofProcess.entryCall("run", List.of(S, S, S)), "process.run variadico");
        assertNotNull(KofProcess.entryCall("spawn", List.of(S)), "process.spawn/1");
        assertNull(KofProcess.runCall(List.of()), "run sem programa");
        assertNotNull(KofProcess.exitCall(List.of(I)), "process.exit(Int)");
        assertNull(KofProcess.exitCall(List.of(S)), "exit(String) NAO binda");
        for (String m : KofProcess.functions())
            assertFalse(StdCatalog.signaturesOf("process", m).isEmpty(), "tabela sem process." + m);

        assertNotNull(KofShell.staticCall("cmd", List.of(S, KofProcess.STRING_LIST)), "shell.cmd");
        assertNull(KofShell.staticCall("cmd", List.of(S)), "shell.cmd/1 NAO binda");
        assertNotNull(KofShell.staticCall("run", List.of(S)), "shell.run/1");
        assertNotNull(KofShell.staticCall("run", List.of(S, KofProcess.STRING_LIST)), "shell.run/2");
        assertNull(KofShell.staticCall("run", List.of()), "shell.run/0");
        assertNotNull(KofShell.staticCall("ok", List.of(KofProcess.RESULT)), "shell.ok(Result)");
        assertNull(KofShell.staticCall("ok", List.of(S)), "ok(String) NAO binda");
        for (String m : KofShell.functions())
            assertFalse(StdCatalog.signaturesOf("shell", m).isEmpty(), "tabela sem shell." + m);
    }

    @Test
    void ghostSignaturesAndUnknownNamespaceAreEmpty() {
        assertTrue(StdCatalog.signaturesOf("db", "ghost").isEmpty());
        assertTrue(StdCatalog.signaturesOf("nope", "get").isEmpty());
    }

    @Test
    void untabledNamespacesStayHonestEmpty() {
        // fatias 1-2 = db/http/time/cache/process/shell; inventar forma p/ os
        // demais e proibido (R6)
        assertTrue(StdCatalog.signaturesOf("log", "info").isEmpty());
        assertTrue(StdCatalog.signaturesOf("net", "lookup").isEmpty());
        assertTrue(StdCatalog.signaturesOf("nope", "get").isEmpty());
    }
}
