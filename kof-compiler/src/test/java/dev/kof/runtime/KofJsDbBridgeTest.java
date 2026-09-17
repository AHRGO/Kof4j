package dev.kof.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fatia A do DB001 — o bridge JDBC do host GraalJS, exercitado SEM o engine
 * (os primitivos String/Object; o caminho Value e o mesmo). Prova a
 * paridade de semantica com `JvmConfigRuntime`: query = List<String> json,
 * transaction aninhada (bug 77), row_to_json com escape.
 */
class KofJsDbBridgeTest {

    @Test
    void connectExecuteQueryRoundtrip() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:a;DB_CLOSE_DELAY=-1");
        assertTrue(id.startsWith("db"), "id sequencial: " + id);
        KofJsDbBridge.execute(id, "create table t(id int primary key, name varchar(32))");
        assertEquals(1, KofJsDbBridge.execute(id, "insert into t values(?, ?)", 7, "Nativa"));
        List<String> rows = KofJsDbBridge.query(id, "select id, name from t where id = ?", "", 7);
        assertEquals(1, rows.size());
        assertEquals("{\"id\":7,\"name\":\"Nativa\"}", rows.get(0));
        KofJsDbBridge.close(id);
    }

    @Test
    void typedQueryFailsLoudNeverSilent() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:b;DB_CLOSE_DELAY=-1");
        KofJsDbBridge.execute(id, "create table u(id int)");
        KofJsDbBridge.execute(id, "insert into u values(1)");
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> KofJsDbBridge.query(id, "select id from u", "User"));
        assertTrue(ex.getMessage().contains("DB002"), ex.getMessage());
        KofJsDbBridge.close(id);
    }

    @Test
    void transactionCommitsAndRollsBack() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:c;DB_CLOSE_DELAY=-1");
        KofJsDbBridge.execute(id, "create table t(n int)");
        KofJsDbBridge.transaction(() -> exec(id, "insert into t values(1)"));
        assertEquals(1, scalar(id, "select count(*) from t"), "commitou");
        assertThrows(RuntimeException.class, () -> KofJsDbBridge.transaction(() -> {
            exec(id, "insert into t values(2)");
            throw new RuntimeException("boom");
        }));
        assertEquals(1, scalar(id, "select count(*) from t"), "rollback do 2");
        KofJsDbBridge.close(id);
    }

    @Test
    void nestedTransactionDoesNotCommitInner() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:d;DB_CLOSE_DELAY=-1");
        KofJsDbBridge.execute(id, "create table t(n int)");
        // bloco interno NAO decide; o externo rollbacka tudo (bug 77).
        assertThrows(RuntimeException.class, () -> KofJsDbBridge.transaction(() -> {
            exec(id, "insert into t values(1)");
            try {
                KofJsDbBridge.transaction(() -> exec(id, "insert into t values(2)"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("aborta externo");
        }));
        assertEquals(0, scalar(id, "select count(*) from t"), "aninhado nao comitou sozinho");
        KofJsDbBridge.close(id);
    }

    @Test
    void rowToJsonEscapesParityJvm() {
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("k", "a\"b\\c\nd");
        assertEquals("{\"k\":\"a\\\"b\\\\c\\nd\"}", KofJsDbBridge.rowToJson(row));
    }

    private static void exec(String id, String sql) {
        try {
            KofJsDbBridge.execute(id, sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int scalar(String id, String sql) throws Exception {
        return Integer.parseInt(KofJsDbBridge.query(id, sql, "").get(0).replaceAll("[^0-9]", ""));
    }
}
