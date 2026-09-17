package dev.kof.runtime;

import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * kof.db no host GraalJS (DB001) — mesma semântica dos gerados por
 * {@code JvmConfigRuntime.source()} no alvo JVM (connect/close/execute/
 * query untyped/transaction com aninhamento), sobre JDBC do classpath do
 * runner (o mesmo contrato do caminho JVM: quem traz o driver é o
 * classpath — sqlite/h2 chegam via {@code -cp} dos testes e do app).
 *
 * O runtime JS ({@code kof-runtime-io.mjs}) delega {@code kofDb*} para os
 * métodos deste objeto publicados em {@code kof_platform}
 * ({@link KofJsRunner}); a classe fica no kof-runtime porque é o host que
 * executa, não o compilador. Métodos com {@link Value} aceitam o lado JS
 * direto (Value→Java nas fronteiras); os primitivos String/Object são para
 * teste sem GraalJS.
 */
public final class KofJsDbBridge {

    private KofJsDbBridge() {
    }

    private static final Map<String, java.sql.Connection> CONNECTIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static volatile String DEFAULT_ID;
    private static final ThreadLocal<java.sql.Connection> TX = new ThreadLocal<>();

    public static String connect(String url) throws Exception {
        return register(java.sql.DriverManager.getConnection(url));
    }

    public static String connect2(String url, String user, String pass) throws Exception {
        return register(java.sql.DriverManager.getConnection(url, user, pass));
    }

    private static String register(java.sql.Connection c) {
        String id = "db" + SEQ.incrementAndGet();
        CONNECTIONS.put(id, c);
        DEFAULT_ID = id;
        return id;
    }

    public static int close(String id) {
        java.sql.Connection c = CONNECTIONS.remove(id == null || id.isEmpty() ? DEFAULT_ID : id);
        if (c == null) {
            return -1;
        }
        try {
            c.close();
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    public static int execute(String id, String sql, Object... args) throws Exception {
        try (java.sql.PreparedStatement ps = conn(id).prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    /** Value[] do guest JS (execute/query variadics). */
    public static int executeV(String id, String sql, Value... args) throws Exception {
        Object[] plain = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            plain[i] = fromGuest(args[i]);
        }
        return execute(id, sql, plain);
    }

    /** Rows como JSON strings (contrato {@code List<String>} do frontend). */
    public static List<String> query(String id, String sql, String className, Object... args)
            throws Exception {
        List<String> rows = new ArrayList<>();
        try (java.sql.PreparedStatement ps = conn(id).prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                java.sql.ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(md.getColumnLabel(i).toLowerCase(), value(rs.getObject(i)));
                    }
                    if (className != null && !className.isEmpty()) {
                        // O programa JS nao emite .class no classpath do host
                        // (Target.JS nao produz bytecode JVM) — o caminho
                        // tipado e gate DB002 no compile; em runtime jamais
                        // silenciar: erro claro, nunca linha vazia.
                        throw new UnsupportedOperationException(
                                "db.query<T> is not supported on the JS target (DB002)");
                    }
                    rows.add(rowToJson(row));
                }
            }
        }
        return rows;
    }

    public static List<String> queryV(String id, String sql, String className, Value... args)
            throws Exception {
        Object[] plain = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            plain[i] = fromGuest(args[i]);
        }
        return query(id, sql, className, plain);
    }

    /** Mesma regra de aninhamento do JVM (bug 77): bloco interno NAO
     *  comita nem rollbacka — quem decide e o bloco externo. */
    public static void transaction(Runnable task) throws Exception {
        java.sql.Connection c = conn(DEFAULT_ID);
        boolean nested = c.equals(TX.get());
        boolean prevAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        if (!nested) {
            TX.set(c);
        }
        try {
            task.run();
            if (!nested) {
                c.commit();
            }
        } catch (Exception | Error e) {
            if (!nested) {
                try {
                    c.rollback();
                } catch (Exception ignored) {
                }
            }
            throw e;
        } finally {
            if (!nested) {
                TX.remove();
            }
            c.setAutoCommit(prevAuto);
        }
    }

    /** Bridge do Value: o guest passa Int/Long/Double/Bool/String; o shape
     *  do argumento e o mesmo do `setObject` no JVM. */
    static Object fromGuest(Value v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isString()) {
            return v.asString();
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.fitsInLong()) {
            return v.asLong();
        }
        if (v.fitsInDouble()) {
            return v.asDouble();
        }
        return v.as(Object.class);
    }

    /** Bridge do Value p/ objeto: o guest passa o record via `toJSON()` (membros
     *  = nomes de campo crus); o ORM lê os valores pela ordem do schema. */
    static Map<String, Object> valueMap(Value obj) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (obj == null || obj.isNull()) {
            return m;
        }
        for (String key : obj.getMemberKeys()) {
            m.put(key, fromGuest(obj.getMember(key)));
        }
        return m;
    }

    /** Normaliza tipos JDBC p/ JSON natural (CLOB→String, BLOB→base64) —
     *  paridade com o `kof_db_value` do JVM. */
    private static Object value(Object v) throws Exception {
        if (v instanceof java.sql.Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        if (v instanceof java.sql.Blob blob) {
            return java.util.Base64.getEncoder().encodeToString(blob.getBytes(1, (int) blob.length()));
        }
        return v;
    }

    static java.sql.Connection conn(String id) throws Exception {
        String key = (id == null || id.isEmpty()) ? DEFAULT_ID : id;
        java.sql.Connection c = key == null ? null : CONNECTIONS.get(key);
        if (c == null) {
            throw new IllegalStateException("no db connection: " + id);
        }
        return c;
    }

    /** Serializacao JSON do linha — mesmo shape do `kof_db_row_to_json` JVM
     *  (chaves na ordem de leitura, strings escapadas, null literal). */
    static String rowToJson(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(encodeString(e.getKey())).append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String s) {
                sb.append(encodeString(s));
            } else if (v instanceof Number n) {
                sb.append(n);
            } else if (v instanceof Boolean b) {
                sb.append(b);
            } else {
                sb.append(encodeString(String.valueOf(v)));
            }
        }
        return sb.append('}').toString();
    }

    static String encodeString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        sb.append("0".repeat(4 - hex.length()));
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
