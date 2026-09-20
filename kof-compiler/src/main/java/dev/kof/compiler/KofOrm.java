package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch table for the Kof ORM ({@code orm}).
 *
 * <p>The compiler knows every entity schema in compile-time (fields, types,
 * constraints declared with {@code entity}) — no reflection to discover the
 * schema, never annotations. {@code orm} lowers to {@code kof_orm_*} runtime
 * functions that speak SQL via {@code kof.db}.
 *
 * <pre>{@code
 * entity User {
 *     id: Long generated
 *     name: String
 *     email: String unique
 *     age: Int
 * }
 *
 * var db = db.connect("jdbc:h2:mem:test")
 * orm.create<User>(db)                 // DDL a partir do schema
 * orm.save(db, User(0, "Mel", "m@kof.dev", 30))
 * var u = orm.find<User>(db, 1)
 * var all = orm.all<User>(db)
 * orm.delete<User>(db, 1)
 * }</pre>
 *
 * <p>JVM: JDBC (via kof.db). Native e JS reportam {@code ORM001} em
 * compile-time.
 */
public final class KofOrm {

    private KofOrm() {
    }

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;

    static boolean isOrmNamespace(String name) {
        return "orm".equals(name);
    }

    /** JVM/ANDROID: JDBC via kof.db (ANDROID fecha 20/09, D-DB-GAPS DB-2 —
     *  mesmo JvmBackend, paridade por construção). JS: KofJsOrmBridge (18/09,
     *  ORM001). Native: SQL-puro das fatias F1 (delete_all/count/migrate/
     *  create); o resto e cross seguem ORM001. */
    static boolean supportedOn(@SuppressWarnings("unused") Target target) {
        return target == Target.JVM || target == Target.ANDROID || target == Target.JS;
    }

    /** D-DB-GAPS DB-1 (20/09): faces SQL-puro do Native x86-64, uma por fatia.
     *  F1a = {@code delete_all}, F1b = {@code count}, F1c = {@code migrate}
     *  (asm em {@code RuntimeOrm1}), F1d = {@code create} (parser de schema +
     *  DDL em {@code RuntimeOrm2}); o row-object (save/find/all/where) entra depois. MySQL (runtime) e o
     *  cross riscv/aarch64 (compile-time) seguem {@code ORM001} honesto
     *  (R7, R6 — nunca silent). */
    private static final java.util.Set<String> NATIVE_F1 = java.util.Set.of(
            "kof_orm_delete_all", "kof_orm_count", "kof_orm_migrate", "kof_orm_create");

    static boolean fnSupportedOn(Target target, String fn) {
        if (supportedOn(target)) return true;
        return target == Target.NATIVE && NATIVE_F1.contains(fn);
    }

    static String gapCode() {
        return "ORM001";
    }

    record OrmCall(String function, Type returnType, List<Type> parameterTypes,
                   String entityName, boolean typed) {
    }

/** X10 fatia 3: nomes aceitos pelo dispatch real (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case-literals da fonte abaixo. */
    static List<String> functions() { return List.of("create", "save", "find", "all", "delete", "count", "deleteAll", "where", "saveAll", "page", "migrate"); }

    /** {@code orm.<method>(...) } — resolve o runtime function. */
    static OrmCall staticCall(String name, List<Type> argTypes, boolean typed, String entityName) {
        return switch (name) {
            case "create" -> argTypes.size() == 1
                    ? new OrmCall("kof_orm_create", BOOL, List.of(STR), entityName, false)
                    : null;
            case "save" -> argTypes.size() == 2
                    ? new OrmCall("kof_orm_save", OBJ, List.of(STR, OBJ), entityName, false)
                    : null;
            case "find" -> typed && argTypes.size() == 2
                    ? new OrmCall("kof_orm_find", OBJ, List.of(STR, OBJ), entityName, true)
                    : null;
            case "all" -> typed && argTypes.size() == 1
                    ? new OrmCall("kof_orm_all", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR), entityName, true)
                    : null;
            case "delete" -> typed && argTypes.size() == 2
                    ? new OrmCall("kof_orm_delete", BOOL, List.of(STR, OBJ), entityName, true)
                    : null;
            case "count" -> typed && argTypes.size() == 1
                    ? new OrmCall("kof_orm_count", Type.PrimitiveType.LONG, List.of(STR), entityName, true)
                    : typed && argTypes.size() == 3
                    ? new OrmCall("kof_orm_count_where", Type.PrimitiveType.LONG, List.of(STR, STR, OBJ),
                    entityName, true)
                    : null;
            case "deleteAll" -> typed && argTypes.size() == 1
                    ? new OrmCall("kof_orm_delete_all", BOOL, List.of(STR), entityName, true)
                    : null;
            case "where" -> typed && argTypes.size() == 3
                    ? new OrmCall("kof_orm_where", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR, STR, OBJ), entityName, true)
                    : typed && argTypes.size() == 4
                    ? new OrmCall("kof_orm_where_op", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR, STR, STR, OBJ), entityName, true)
                    : null;
            case "saveAll" -> typed && argTypes.size() == 2
                    ? new OrmCall("kof_orm_save_all", BOOL, List.of(STR, new Type.ClassType("kof", "List", List.of(STR))),
                    entityName, false)
                    : null;
            case "page" -> typed && argTypes.size() == 3
                    ? new OrmCall("kof_orm_page", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR, OBJ, OBJ), entityName, true)
                    : null;
            case "migrate" -> argTypes.size() == 3
                    ? new OrmCall("kof_orm_migrate", BOOL, List.of(STR, STR, STR), entityName, false)
                    : null;
            default -> null;
        };
    }

    /** Serializa o schema da entidade para o runtime:
     *  {@code name:type:constraints,...} (ex.: {@code id:long:generated,
     *  name:string,email:string:unique,age:int}). */
    static String schemaString(List<EntityFieldNode> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            EntityFieldNode f = fields.get(i);
            sb.append(f.name()).append(':').append(dbType(f.type()));
            if (f.generated()) sb.append(":generated");
            if (f.unique()) sb.append(":unique");
        }
        return sb.toString();
    }

    private static String dbType(String kofType) {
        return switch (kofType) {
            case "Int" -> "int";
            case "Long" -> "long";
            case "String" -> "string";
            case "Bool" -> "bool";
            case "Double" -> "double";
            case "Float" -> "float";
            default -> "string";
        };
    }

    /** Nome da tabela: o nome da entidade em minúsculas (User → user). */
    static String tableName(String entityName) {
        return entityName.toLowerCase();
    }
}