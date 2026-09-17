package dev.kof.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Fatia A do ORM001 — o bridge ORM do host GraalJS, exercitado SEM o engine e
 * SEM o compilador (os primitivos Map/String; o caminho {@code Value} de
 * {@code save} é o mesmo, via {@code KofJsDbBridge.valueMap}). Prova a
 * paridade de semântica com {@code JvmOrmRuntime}: DDL por schema serializado,
 * save = INSERT-com-PK-gerada / UPDATE / INSERT-upsert, find/all/where = JSON
 * rows, count, page/migrate — os MESMOS resultados que o {@code KofOrmE2ETest}
 * da JVM reporta byte-a-byte.
 */
class KofJsOrmBridgeTest {

    // schema do ENTITY_SRC do KofOrmE2ETest: id:long:generated, name, email:unique, age:int
    private static final String USER_SCHEMA =
            "id:long:generated,name:string,email:string:unique,age:int";

    private static Map<String, Object> user(Object id, String name, String email, int age) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("email", email);
        m.put("age", age);
        return m;
    }

    @Test
    void createSaveFindAllDeleteParity() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:ormjs1;DB_CLOSE_DELAY=-1");
        assertTrue(KofJsOrmBridge.create(id, "user", USER_SCHEMA), "create table");
        String saved = KofJsOrmBridge.saveMap(id, user(0L, "Mel", "mel@kof.dev", 30), "user", USER_SCHEMA);
        assertEquals("{\"id\":1,\"name\":\"Mel\",\"email\":\"mel@kof.dev\",\"age\":30}", saved,
                "PK gerada preenchida (JVM: mel.id → 1)");
        String found = KofJsOrmBridge.find(id, 1L, "user", USER_SCHEMA);
        assertEquals(saved, found, "find devolve a mesma linha (JVM: u.name → Mel)");
        assertEquals(1, KofJsOrmBridge.all(id, "user", USER_SCHEMA).size(), "all.size → 1");
        assertEquals(1L, KofJsOrmBridge.count(id, "user", USER_SCHEMA), "count → 1");
        assertTrue(KofJsOrmBridge.delete(id, 1L, "user", USER_SCHEMA), "delete");
        assertEquals(0L, KofJsOrmBridge.count(id, "user", USER_SCHEMA), "count pós-delete → 0");
        assertNull(KofJsOrmBridge.find(id, 1L, "user", USER_SCHEMA), "find pós-delete → null");
        KofJsDbBridge.close(id);
    }

    @Test
    void saveUpdatesExistingRowParity() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:ormjs2;DB_CLOSE_DELAY=-1");
        KofJsOrmBridge.create(id, "user", USER_SCHEMA);
        String mel = KofJsOrmBridge.saveMap(id, user(0L, "Mel", "mel@kof.dev", 30), "user", USER_SCHEMA);
        assertTrue(mel.startsWith("{\"id\":1,"), mel);
        String up = KofJsOrmBridge.saveMap(id, user(1L, "Melissa", "mel@kof.dev", 31), "user", USER_SCHEMA);
        assertEquals("{\"id\":1,\"name\":\"Melissa\",\"email\":\"mel@kof.dev\",\"age\":31}", up,
                "UPDATE por PK existente (JVM: Melissa 31)");
        assertEquals(1, KofJsOrmBridge.all(id, "user", USER_SCHEMA).size(), "UPDATE não duplicou");
        KofJsDbBridge.close(id);
    }

    @Test
    void whereAndWhereOpAndCountWhereParity() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:ormjs3;DB_CLOSE_DELAY=-1");
        KofJsOrmBridge.create(id, "user", USER_SCHEMA);
        KofJsOrmBridge.saveMap(id, user(0L, "Mel", "a@kof.dev", 30), "user", USER_SCHEMA);
        KofJsOrmBridge.saveMap(id, user(0L, "Ana", "b@kof.dev", 40), "user", USER_SCHEMA);
        List<String> eq = KofJsOrmBridge.where(id, "name", "Mel", "user", USER_SCHEMA);
        assertEquals(1, eq.size());
        assertTrue(eq.get(0).contains("\"name\":\"Mel\""), eq.get(0));
        assertEquals(1L, KofJsOrmBridge.countWhere(id, "name", "Ana", "user", USER_SCHEMA), "count_where");
        List<String> gt = KofJsOrmBridge.whereOp(id, "age", ">", 35, "user", USER_SCHEMA);
        assertEquals(1, gt.size());
        assertTrue(gt.get(0).contains("\"name\":\"Ana\""), gt.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> KofJsOrmBridge.whereOp(id, "age", "DROP", 1, "user", USER_SCHEMA),
                "operador fora da whitelist nunca vira SQL");
        KofJsDbBridge.close(id);
    }

    @Test
    void pageDeleteAllAndMigrateParity() throws Exception {
        String id = KofJsDbBridge.connect("jdbc:h2:mem:ormjs4;DB_CLOSE_DELAY=-1");
        KofJsOrmBridge.create(id, "user", USER_SCHEMA);
        for (int i = 1; i <= 5; i++) {
            KofJsOrmBridge.saveMap(id, user(0L, "u" + i, "e" + i + "@kof.dev", i), "user", USER_SCHEMA);
        }
        assertEquals(5L, KofJsOrmBridge.count(id, "user", USER_SCHEMA), "5 antes da paginação");
        List<String> win = KofJsOrmBridge.page(id, 2, 1, "user", USER_SCHEMA);
        assertEquals(2, win.size(), "LIMIT 2 OFFSET 1");
        assertTrue(win.get(0).contains("\"name\":\"u2\""), "offset pula u1: " + win.get(0));
        assertTrue(KofJsOrmBridge.saveAll(id, List.of(
                user(0L, "x", "x@kof.dev", 1), user(0L, "y", "y@kof.dev", 2)), "user", USER_SCHEMA));
        assertEquals(7L, KofJsOrmBridge.count(id, "user", USER_SCHEMA), "save_all somou 2");
        assertTrue(KofJsOrmBridge.deleteAll(id, "user", USER_SCHEMA), "delete_all");
        assertEquals(0L, KofJsOrmBridge.count(id, "user", USER_SCHEMA), "0 pós-delete_all");
        // migrate roda uma única vez (tabela kof_migrations)
        assertTrue(KofJsOrmBridge.migrate(id, "m1", "create table t(n int)"), "1ª migrate");
        assertTrue(KofJsOrmBridge.migrate(id, "m1", "create table t(n int)"), "2ª migrate = no-op true");
        assertEquals(1L, KofJsDbBridge.query(id, "select count(*) as c from \"kof_migrations\"", "").size());
        KofJsDbBridge.close(id);
    }

    @Test
    void entityWithoutGeneratedUsesFirstFieldAsPk() throws Exception {
        // schema sem `generated` → pkIndex 0 (JVM: entityWithoutGeneratedUsesFirstFieldAsPk)
        String skuSchema = "sku:string,price:double";
        String id = KofJsDbBridge.connect("jdbc:h2:mem:ormjs5;DB_CLOSE_DELAY=-1");
        assertTrue(KofJsOrmBridge.create(id, "product", skuSchema));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("sku", "A1");
        p.put("price", 19.99);
        String saved = KofJsOrmBridge.saveMap(id, p, "product", skuSchema);
        assertTrue(saved.contains("\"price\":19.99"), saved);
        String found = KofJsOrmBridge.find(id, "A1", "product", skuSchema);
        assertEquals(saved, found, "find por PK não-numérica (sku)");
        KofJsDbBridge.close(id);
    }

    @Test
    void uniqueConstraintIsNotSilent() throws Exception {
        // email UNIQUE — o segundo INSERT com mesmo email deve PROPAGAR o erro
        // JDBC (nunca voltar linha vazia / true silencioso). R6.
        String id = KofJsDbBridge.connect("jdbc:h2:mem:ormjs6;DB_CLOSE_DELAY=-1");
        KofJsOrmBridge.create(id, "user", USER_SCHEMA);
        KofJsOrmBridge.saveMap(id, user(0L, "Mel", "dup@kof.dev", 30), "user", USER_SCHEMA);
        assertThrows(java.sql.SQLException.class, () -> KofJsOrmBridge.saveMap(id,
                user(0L, "Outro", "dup@kof.dev", 31), "user", USER_SCHEMA),
                "violação de UNIQUE lança (JVM: 'rejected' via catch no programa)");
        KofJsDbBridge.close(id);
    }
}
