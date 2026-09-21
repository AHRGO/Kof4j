package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.orm — o ORM da própria linguagem: {@code entity} define o schema em
 * compile-time (o compilador conhece campos, tipos e constraints — nunca
 * reflection para descobrir schema) e {@code orm} fala SQL via kof.db.
 *
 * Fluxo: entity → record gerado + schema registrado; orm.create/save/find/
 * all/delete/count sobre JDBC (H2 nos testes).
 */
class KofOrmE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ENTITY_SRC = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        return runJvmWithExtra(source, outDir, null, expected);
    }

    private String runJvmWithExtra(Path source, Path outDir, String extraJar, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        String cp = outDir + java.io.File.pathSeparator + h2
                + (extraJar != null ? java.io.File.pathSeparator + extraJar : "");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED",
                    "-cp", cp, "Default.Main");
            // stderr separado: drivers (Mongo/SQLite) podem logar avisos de
            // inicialização no stderr — o stdout é o output do programa Kof
            pb.redirectError(java.io.File.createTempFile("kof-orm-stderr", ".txt"));
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private static String findH2Jar() {
        return findDriverJar("h2", "H2");
    }

    private static String findDriverJar(String marker, String label) {
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains(marker) && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException(label + " jar not found on test classpath");
    }

    @Test
    void createSaveFindAllDelete(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm1;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    println(mel.id)
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    var all = orm.all<User>(db)
                    println(all.size)
                    println(orm.count<User>(db))
                    orm.delete<User>(db, mel.id)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "1\nMel\n1\n1\n0");
    }

    @Test
    void saveUpdatesExistingRow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm2;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    mel = orm.save(db, User(mel.id, "Melissa", "mel@kof.dev", 31))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name + " " + u.age)
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "Melissa 31");
    }

    @Test
    void uniqueConstraintRejected(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm3;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "same@kof.dev", 30))
                    try {
                        orm.save(db, User(0, "Kof", "same@kof.dev", 1))
                        println("no-error")
                    } catch (Throwable e) {
                        println("rejected")
                    }
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "rejected");
    }

    @Test
    void entityWithoutGeneratedUsesFirstFieldAsPk(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                entity Product {
                    code: String unique
                    price: Double
                }
                main() {
                    var db = db.connect("jdbc:h2:mem:orm4;DB_CLOSE_DELAY=-1")
                    orm.create<Product>(db)
                    orm.save(db, Product("P1", 19.99))
                    var p = orm.find<Product>(db, "P1")
                    println(p.price)
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "19.99");
    }

    @Test
    void whereFiltersByField(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm6;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    var adultos = orm.where<User>(db, \"age\", 30)\n"
                + "                    println(adultos.size)\n"
                + "                    println(adultos.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "1\nMel");
    }

    @Test
    void whereWithOperator(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm8;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.save(db, User(0, \"Leo\", \"leo@kof.dev\", 40))\n"
                + "                    var adultos = orm.where<User>(db, \"age\", \">\", 25)\n"
                + "                    println(adultos.size)\n"
                + "                    var jovens = orm.where<User>(db, \"age\", \"<=\", 25)\n"
                + "                    println(jovens.size)\n"
                + "                    var ana = orm.where<User>(db, \"name\", \"LIKE\", \"A%\")\n"
                + "                    println(ana.size)\n"
                + "                    println(ana.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1\n1\nAna");
    }

    @Test
    void whereUnknownColumnIsCompileError(@TempDir Path tempDir) throws IOException {
        // P3-10: coluna que não existe na entidade → falha em compile-time (ORM003)
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm12;DB_CLOSE_DELAY=-1\")\n"
                + "                    var r = orm.where<User>(db, \"idade\", 30)\n"
                + "                }\n");
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "coluna inexistente deve falhar na compilação");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "ORM003".equals(d.code())),
                "gap ORM003 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void queryDslFiltersOrdersAndLimits(@TempDir Path tempDir) throws IOException {
        // ORM001 (nível 3): Query DSL tipada — User.query(db) { where ...; orderBy ...; limit N }
        // O compilador baixa para db.query (SQL preparada em compile-time; valores como binds).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl1;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.save(db, User(0, \"Leo\", \"leo@kof.dev\", 40))\n"
                + "                    var adultos = User.query(db) {\n"
                + "                        where age > 25\n"
                + "                        orderBy name asc\n"
                + "                    }\n"
                + "                    println(adultos.size)\n"
                + "                    println(adultos.get(0).name)\n"
                + "                    println(adultos.get(1).name)\n"
                + "                    var limitado = User.query(db) {\n"
                + "                        where age >= 25\n"
                + "                        limit 1\n"
                + "                    }\n"
                + "                    println(limitado.size)\n"
                + "                    db.close(db)\n"
                + "                }\n");
        runJvm(source, tempDir.resolve("out"), "2\nLeo\nMel\n1");
    }

    @Test
    void queryDslMultipleWhereAnds(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl2;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.save(db, User(0, \"Leo\", \"leo@kof.dev\", 40))\n"
                + "                    var faixa = User.query(db) {\n"
                + "                        where age >= 25\n"
                + "                        where age < 40\n"
                + "                        orderBy name asc\n"
                + "                    }\n"
                + "                    println(faixa.size)\n"
                + "                    println(faixa.get(0).name)\n"
                + "                    println(faixa.get(1).name)\n"
                + "                    db.close(db)\n"
                + "                }\n");
        // age >= 25 AND age < 40 → age 25 (Ana) + age 30 (Mel); idade 40 fora
        runJvm(source, tempDir.resolve("out"), "2\nAna\nMel");
    }

    @Test
    void queryDslUnknownColumnIsCompileError(@TempDir Path tempDir) throws IOException {
        // ORM003: coluna inexistente no where do DSL → falha em compile-time
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl3;DB_CLOSE_DELAY=-1\")\n"
                + "                    var r = User.query(db) {\n"
                + "                        where idade > 10\n"
                + "                    }\n"
                + "                }\n");
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "coluna inexistente no where do DSL deve falhar na compilação");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "ORM003".equals(d.code())),
                "gap ORM003 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void queryDslWhereMustBeComparisonIsCompileError(@TempDir Path tempDir) throws IOException {
        // ORM004: where sem comparação (só uma coluna) → falha em compile-time
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl4;DB_CLOSE_DELAY=-1\")\n"
                + "                    var r = User.query(db) {\n"
                + "                        where age\n"
                + "                    }\n"
                + "                }\n");
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "where sem comparação deve falhar na compilação");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "ORM004".equals(d.code())),
                "gap ORM004 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void whereDynamicColumnIsAllowed(@TempDir Path tempDir) throws IOException {
        // P3-10: coluna dinâmica (não-literal) não é validada em compile-time
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm13;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    var col = \"age\"\n"
                + "                    var r = orm.where<User>(db, col, 30)\n"
                + "                    println(r.size)\n"
                + "                    db.close(db)\n"
                + "                }\n");
        runJvm(source, tempDir.resolve("out"), "1");
    }

    @Test
    void saveAllBatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm9;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    var l = new List<User>()\n"
                + "                    l.add(User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    l.add(User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.saveAll<User>(db, l)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    var mel = orm.where<User>(db, \"email\", \"mel@kof.dev\")\n"
                + "                    println(mel.size)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1");
    }

    @Test
    void pagePagination(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm10;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    var l = new List<User>()\n"
                + "                    l.add(User(0, \"A\", \"a@kof.dev\", 20))\n"
                + "                    l.add(User(0, \"B\", \"b@kof.dev\", 21))\n"
                + "                    l.add(User(0, \"C\", \"c@kof.dev\", 22))\n"
                + "                    orm.saveAll<User>(db, l)\n"
                + "                    var p1 = orm.page<User>(db, 2, 0)\n"
                + "                    println(p1.size)\n"
                + "                    var p2 = orm.page<User>(db, 2, 2)\n"
                + "                    println(p2.size)\n"
                + "                    println(p2.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1\nC");
    }

    @Test
    void countWhereAndDeleteAll(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm11;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    println(orm.count<User>(db, \"age\", 30))\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    orm.deleteAll<User>(db)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "1\n2\n0");
    }

    @Test
    void migrateAppliesOnce(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm7;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.migrate(db, \"add_city\", \"ALTER TABLE \\\"user\\\" ADD COLUMN city VARCHAR(255)\")\n"
                + "                    orm.migrate(db, \"add_city\", \"ALTER TABLE \\\"user\\\" ADD COLUMN city VARCHAR(255)\")\n"
                + "                    orm.migrate(db, \"v2\", \"ALTER TABLE \\\"user\\\" ADD COLUMN country VARCHAR(255)\")\n"
                + "                    println(\"ok\")\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "ok");
    }

    @Test
    void sqliteDialectViaJdbc(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:sqlite:%s")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """.formatted(tempDir.resolve("orm.db")));
        runJvmWithExtra(source, tempDir.resolve("out"), findDriverJar("sqlite-jdbc", "SQLite"),
                "Mel\n1");
    }

    @Test
    void mongoCrud(@TempDir Path tempDir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mongoAvailable(),
                "MongoDB not reachable on localhost:27017 (start it: docker run -d -p 27017:27017 mongo:7)");
        int port = 27017;
        {
            Path source = tempDir.resolve("Main.kf");
            Files.writeString(source, """
                entity User {
                    id: Long
                    name: String
                    email: String unique
                    age: Int
                }
                main() {
                    var db = db.connect("mongodb://localhost:%d/kof_test_%d")
                    orm.create<User>(db)
                    orm.save(db, User(1, "Mel", "mel@kof.dev", 30))
                    var u = orm.find<User>(db, 1)
                    println(u.name)
                    var adultos = orm.where<User>(db, "age", 30)
                    println(adultos.size)
                    println(orm.count<User>(db))
                    var l = new List<User>()
                    l.add(User(2, "Ana", "ana@kof.dev", 25))
                    l.add(User(3, "Leo", "leo@kof.dev", 40))
                    orm.saveAll<User>(db, l)
                    println(orm.count<User>(db))
                    var jovens = orm.where<User>(db, "age", "<=", 25)
                    println(jovens.size)
                    var pg = orm.page<User>(db, 2, 1)
                    println(pg.size)
                    var leo = orm.where<User>(db, "name", "LIKE", "L%%")
                    println(leo.size)
                    println(orm.count<User>(db, "age", 25))
                    orm.deleteAll<User>(db)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """.formatted(port, System.nanoTime()));
            runJvmWithExtra(source, tempDir.resolve("out"), mongoClasspath(), "Mel\n1\n1\n3\n1\n2\n1\n1\n0");
        }
    }

    private static boolean mongoAvailable() {
        try (java.net.Socket s = new java.net.Socket("localhost", 27017)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tcpOpen(String host, int port) {
        try (java.net.Socket s = new java.net.Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void mariadbCrud(@TempDir Path tempDir) throws Exception {
        int dbPort;
        try { dbPort = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "3306")); }
        catch (NumberFormatException e) { dbPort = 3306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", dbPort),
                "MariaDB not reachable on 127.0.0.1:" + dbPort + " (docker run -d -p 3306:3306 -e MARIADB_ROOT_PASSWORD=kof mariadb:11)");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + dbPort + "/kof_test_" + System.nanoTime() + "?user=root&password=kof&allowMultiQueries=true&createDatabaseIfNotExist=true\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.saveAll<User>(db, new List<User>())\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    var mel = orm.find<User>(db, 1)\n"
                + "                    println(mel.name)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    var velhos = orm.where<User>(db, \"age\", \">\", 26)\n"
                + "                    println(velhos.size)\n"
                + "                    orm.deleteAll<User>(db)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvmWithExtra(source, tempDir.resolve("out"), findDriverJar("mariadb", "MariaDB"), "Mel\n2\n1\n0");
    }

    @Test
    void deleteAllMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d1 (D-DB-GAPS DB-3): orm.deleteAll sobre o wire MySQL no Native
        // x86-64 — espelho do host (kof_orm_q backtick + kof_db_execute >= 0).
        // JVM dirige a MESMA semantica via JDBC; a prova e byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    println(orm.deleteAll<User>(db))
                    println(orm.deleteAll<User>(db))
                    var rows = db.query(db, "select count(*) as c from `user`")
                    for (var r in rows) {
                        println(r)
                    }
                    db.close(db)
                }
                """;
        String expected = "true\ntrue\n{\"c\":0}";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.deleteAll no mysql (F2d1): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (deleteAll mysql; idempotente + count 0)");
    }

    @Test
    void countMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d2 (D-DB-GAPS DB-3): orm.count sobre o wire MySQL no Native x86-64
        // — COM_QUERY + leitura do resultset (kof_db_mysql_next/lenenc) com
        // parse bounded; host via JDBC; a prova e byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 41)
                    println(orm.count<User>(db))
                    db.execute(db, "delete from `user` where id = ?", 2)
                    println(orm.count<User>(db))
                    orm.deleteAll<User>(db)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """;
        String expected = "3\n2\n0";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.count no mysql (F2d2): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (count mysql; 3 -> 2 apos delete -> 0 apos deleteAll)");
    }

    @Test
    void postgresCrud(@TempDir Path tempDir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("localhost", 5432),
                "PostgreSQL not reachable (start it: docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=kof postgres)");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
+ "                    var db = db.connect(\"jdbc:postgresql://localhost:5432/kof_test?user=postgres&password=kof\")\n"
+ "                    db.execute(db, \"DROP TABLE IF EXISTS \\\"user\\\"\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.saveAll<User>(db, new List<User>())\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    var mel = orm.find<User>(db, 1)\n"
                + "                    println(mel.name)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    var velhos = orm.where<User>(db, \"age\", \">\", 26)\n"
                + "                    println(velhos.size)\n"
                + "                    orm.deleteAll<User>(db)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvmWithExtra(source, tempDir.resolve("out"), findDriverJar("postgresql", "PostgreSQL"), "Mel\n2\n1\n0");
    }

    private static String mongoClasspath() {
        StringBuilder cp = new StringBuilder();
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if ((entry.contains("mongodb") || entry.contains("bson") || entry.contains("slf4j"))
                    && entry.endsWith(".jar")) {
                if (cp.length() > 0) cp.append(java.io.File.pathSeparator);
                cp.append(entry);
            }
        }
        return cp.toString();
    }

    @Test
    void saveCompilesOnNativeAndJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("sqlite:/tmp/orm-test.db")
                    orm.save(db, User(1, "Mel", "m@kof.dev", 30))
                }
                """);
        // F2a (20/09): kof_orm_save REAL no Native x86-64 — compila limpo.
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                "Native should now compile orm.save (F2a): " + nativeResult.diagnostics().getDiagnostics());

        // ORM001 (18/09): JS suportado via KofJsOrmBridge — compila limpo.
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertTrue(jsResult.success(),
                "JS should now compile orm.* (ORM001 closed): " + jsResult.diagnostics().getDiagnostics());
    }

    // ── D-DB-GAPS F2a (20/09): kof_orm_save REAL no Native x86-64 ──

    @Test
    void findPreservesSavedBoolTrueRegression397(@TempDir Path tempDir) throws Exception {
        // §397: gravar==ler no Bool (JVM le INTEGER !=0 apos o fix do binder;
        // a asm de leitura do find (F2b) seguiu o oracle pre-fix e le "true"
        // so do texto -- este teste trava a PARIDADE CROSS com o host
        // corrigido: save(true) -> find().ok == true nos dois targets).
        String kf = """
            entity Flag {
                id: Long generated
                ok: Bool
            }
            main() {
                var db = db.connect("%s")
                db.execute(db, "create table if not exists flag (id INTEGER PRIMARY KEY AUTOINCREMENT, ok INTEGER)")
                var a = orm.save(db, Flag(0, true))
                var b = orm.save(db, Flag(0, false))
                var fa = orm.find<Flag>(db, a.id)
                if (fa != null) { println(fa.ok) }
                var fb = orm.find<Flag>(db, b.id)
                if (fb != null) { println(fb.ok) }
                var fm = orm.find<Flag>(db, 999L)
                if (fm == null) { println("miss=null") }
            }
            """;
        String expected = "true\nfalse\nmiss=null";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource,
                kf.formatted("jdbc:sqlite:" + tempDir.resolve("jvm-flag397.db")));
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource,
                kf.formatted("sqlite:" + tempDir.resolve("nat-flag397.db")));
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar orm.find (F2b): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        String out = new String(pr.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = pr.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "§397 cross-target: save(true)->find().ok=true tambem no Native (INTEGER !=0)");
    }

    @Test
    void saveNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    var u1 = orm.save(db, User(0, "O'Mel", "m@kof.dev", 30))
                    println(u1.id)
                    println(u1.name)
                    var u2 = orm.save(db, User(1, "Mel-2", "m@kof.dev", 30))
                    println(u2.id)
                    println(u2.name)
                    println(orm.count<User>(db))
                    var u3 = orm.save(db, User(9, "Ana", "a@kof.dev", 25))
                    println(u3.id)
                    println(orm.count<User>(db))
                    println(orm.count<User>(db, "age", 25))
                    var rows = db.query(db, "select id, name from user order by id")
                    for (var r in rows) {
                        println(r)
                    }
                }
                """;
        String expected = "1\nO'Mel\n1\nMel-2\n1\n9\n2\n1\n{\"id\":1,\"name\":\"Mel-2\"}\n{\"id\":9,\"name\":\"Ana\"}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmsave.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativesave.db") + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.save (F2a): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (save; 3 paths: INSERT-gen, UPDATE hit com VALOR ALTERADO writeback-verified, UPDATE miss -> INSERT-all)");
    }

    @Test
    void unknownEntityReportsOrm002(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm5;DB_CLOSE_DELAY=-1")
                    orm.create<Ghost>(db)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success());
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("ORM002"),
                "Should report ORM002: " + result.diagnostics().getDiagnostics());
    }

    // ── ORM001 (18/09): kof.orm no JS ── paridade byte-a-byte com o caminho
    // JVM acima, agora via KofJsOrmBridge (mesmo SQL) + bind de record no guest
    // (JsRuntimeOps __kof_decode_<T>). Cobrem as 3 formas de retorno: record
    // único (save/find), List<record> (all/where/where_op/page) e primitivo
    // (count/count_where/delete/delete_all/create/migrate/saveAll).

    private String runJs(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compilation should succeed: "
                + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, ec, "JS exit code should be 0, output: '" + output + "'");
        assertEquals(expected, output, "Unexpected JS output");
        return output;
    }

    @Test
    void jsCreateSaveFindAllDelete(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm1;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    println(mel.id)
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    var all = orm.all<User>(db)
                    println(all.size)
                    println(orm.count<User>(db))
                    orm.delete<User>(db, mel.id)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "1\nMel\n1\n1\n0");
    }

    @Test
    void jsSaveUpdatesExistingRow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm2;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    mel = orm.save(db, User(mel.id, "Melissa", "mel@kof.dev", 31))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name + " " + u.age)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "Melissa 31");
    }

    @Test
    void jsUniqueConstraintRejected(@TempDir Path tempDir) throws IOException {
        // R6: a violacao de `unique` nao pode ser silenciosa. Na sessao anterior
        // este E2E estava BARRADO pelo ICE `catch(Throwable)` no JS (compilar
        // quebrava); com o fix do JsTryParser ele passa a ser cobertura real da
        // propagacao de erro da ponte KofJsOrmBridge -> catch no guest.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm3;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "same@kof.dev", 30))
                    try {
                        orm.save(db, User(0, "Kof", "same@kof.dev", 1))
                        println("no-error")
                    } catch (Throwable e) {
                        println("rejected")
                    }
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "rejected");
    }

    @Test
    void jsEntityWithoutGeneratedUsesFirstFieldAsPk(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                entity Product {
                    code: String unique
                    price: Double
                }
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm4;DB_CLOSE_DELAY=-1")
                    orm.create<Product>(db)
                    orm.save(db, Product("P1", 19.99))
                    var p = orm.find<Product>(db, "P1")
                    println(p.price)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "19.99");
    }

    @Test
    void jsWhereFiltersByField(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm6;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    var adultos = orm.where<User>(db, "age", 30)
                    println(adultos.size)
                    println(adultos.get(0).name)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "1\nMel");
    }

    @Test
    void jsWhereWithOperator(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm8;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    orm.save(db, User(0, "Leo", "leo@kof.dev", 40))
                    var adultos = orm.where<User>(db, "age", ">", 25)
                    println(adultos.size)
                    var jovens = orm.where<User>(db, "age", "<=", 25)
                    println(jovens.size)
                    var ana = orm.where<User>(db, "name", "LIKE", "A%")
                    println(ana.size)
                    println(ana.get(0).name)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\n1\n1\nAna");
    }

    @Test
    void jsSaveAllBatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm9;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var l = new List<User>()
                    l.add(User(0, "Mel", "mel@kof.dev", 30))
                    l.add(User(0, "Ana", "ana@kof.dev", 25))
                    orm.saveAll<User>(db, l)
                    println(orm.count<User>(db))
                    var mel = orm.where<User>(db, "email", "mel@kof.dev")
                    println(mel.size)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\n1");
    }

    @Test
    void jsPagePagination(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm10;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var l = new List<User>()
                    l.add(User(0, "A", "a@kof.dev", 20))
                    l.add(User(0, "B", "b@kof.dev", 21))
                    l.add(User(0, "C", "c@kof.dev", 22))
                    orm.saveAll<User>(db, l)
                    var p1 = orm.page<User>(db, 2, 0)
                    println(p1.size)
                    var p2 = orm.page<User>(db, 2, 2)
                    println(p2.size)
                    println(p2.get(0).name)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\n1\nC");
    }

    @Test
    void jsCountWhereAndDeleteAll(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm11;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    println(orm.count<User>(db, "age", 30))
                    println(orm.count<User>(db))
                    orm.deleteAll<User>(db)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "1\n2\n0");
    }

    @Test
    void jsQueryDslFiltersOrdersAndLimits(@TempDir Path tempDir) throws IOException {
        // Query DSL tipada (nível 3) baixa para kof_db_queryN (mesmo caminho de
        // db.query<T>, DB002) — a supportedOn JS da ORM001 habilita o front-end
        // `User.query(db){ ... }` no mesmo passo. Byte-paridade com o JVM.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm12;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    orm.save(db, User(0, "Leo", "leo@kof.dev", 40))
                    var adultos = User.query(db) {
                        where age > 25
                        orderBy name asc
                    }
                    println(adultos.size)
                    println(adultos.get(0).name)
                    println(adultos.get(1).name)
                    var limitado = User.query(db) {
                        where age >= 25
                        limit 1
                    }
                    println(limitado.size)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\nLeo\nMel\n1");
    }

    // ── D-DB-GAPS F1a (20/09): kof_orm_delete_all REAL no Native x86-64 ──

    @Test
    void deleteAllNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    println(orm.deleteAll<User>(db))
                    var rows = db.query(db, "select count(*) as n from user")
                    for (var r in rows) {
                        println(r)
                    }
                }
                """;
        String expected = "true\n{\"n\":0}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvma.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativea.db") + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.deleteAll: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (delete_all)");
    }

    @Test
    void deleteAllUnknownConnectionThrowsJvmMessageOnBothTargets(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    try {
                        println(orm.deleteAll<User>("db2"))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        String expected = "unknown db connection: db2";
        runJvm(source, tempDir.resolve("jvm-out"), expected);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar (usesOrm liga sqlite): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "id invalido deve lancar a MESMA string do host (R6, paridade)");
    }

    @Test
    void countNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    db.execute(db, "insert into user (name, email, age) values ('Kof', 'k@kof.dev', 1)")
                    println(orm.count<User>(db))
                    println(orm.deleteAll<User>(db))
                    println(orm.count<User>(db))
                }
                """;
        String expected = "2\ntrue\n0";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmb.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativeb.db") + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.count: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (count; 3 calls em sequencia testam a preservacao de registrantes da chamada)");
    }

    @Test
    void countUnknownConnectionThrowsJvmMessageOnNative(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    try {
                        println(orm.count<User>("db2"))
                    } catch (String e) {
                        println(e)
                    }
                    println("after-throw")
                }
                """);
        String expected = "unknown db connection: db2\nafter-throw";
        runJvm(source, tempDir.resolve("jvm-out"), expected);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "throw + execucao continua (stack de excecao nativo)");
    }

    @Test
    void createNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    println(orm.create<User>(db))
                    println(orm.create<User>(db))
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    println(orm.count<User>(db))
                    println(db.query(db, "select email from user").get(0))
                    println(db.query(db, "select sql from sqlite_master where type='table' and name='user'").get(0))
                }
                """;
        // o DDL gravado no sqlite_master e o TEXTO que cada engine enviou —
        // iguala-lo prova AUTOINCREMENT/UNIQUE/VARCHAR byte a byte (bug das
        // flags comidas pelo badtok, medido 20/09).
        String expected = "true\ntrue\n1\n{\"email\":\"m@kof.dev\"}\n{\"sql\":\"CREATE TABLE \\\"user\\\" (\\\"id\\\" INTEGER PRIMARY KEY AUTOINCREMENT, \\\"name\\\" VARCHAR(255), \\\"email\\\" VARCHAR(255) UNIQUE, \\\"age\\\" INTEGER)\"}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmb.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativeb.db") + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.create: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (DDL real: UNIQUE/varchar/pk"
                + " medidos pelo SELECT do email); CREATE IF NOT EXISTS duas vezes = true");
    }

    @Test
    void migrateNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String ddl = "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)";
        String body = """
                    println(orm.migrate(db, "001-user", "DDL1"))
                    println(orm.migrate(db, "001-user", "DDL1"))
                    println(orm.migrate(db, "002-t2", "create table if not exists t2 (id INTEGER)"))
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    println(db.query(db, "select count(*) as n from kof_migrations").get(0))
                    println(orm.count<User>(db))
                }
                """.replace("DDL1", ddl);
        String expected = "true\ntrue\ntrue\n{\"n\":2}\n1";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmb.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativeb.db") + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.migrate: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (migrate idempotente; "
                + "applied_at nao sai no golden - valor de relogio nao e observavel pela API, como no host)");
    }

    @Test
    void migrateUnknownConnectionThrowsJvmMessageOnNative(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    try {
                        println(orm.migrate("db2", "001", "create table t(x int)"))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        runJvmWithExtra(source, tempDir.resolve("jvm-out"), null,
                "unknown db connection: db2");
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals("unknown db connection: db2", out);
    }

    @Test
    void countWhereNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F3a: count com UM bind — SQL identico ao host (FROM "t" WHERE "f"
        // = ?), valor via box de erasure §284. Q3: string, int, ausente,
        // injecao (o bind nunca concatena) e negativo (sign-extension do
        // movslq vs Integer do JDBC).
        String body = """
                    println(orm.create<User>(db))
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    db.execute(db, "insert into user (name, email, age) values ('Ana', 'a@kof.dev', 41)")
                    println(orm.count<User>(db, "name", "Mel"))
                    println(orm.count<User>(db, "age", 30))
                    println(orm.count<User>(db, "email", "nope@x.io"))
                    println(orm.count<User>(db, "name", "x' OR 1=1 --"))
                    println(orm.count<User>(db, "age", -7))
                }
                """;
        String expected = "true\n1\n1\n0\n0\n0";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmb.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativeb.db") + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar count com bind: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "count_where Native deve igualar o JVM");
    }

    @Test
    void findNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    var u1 = orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    var f1 = orm.find<User>(db, u1.id)
                    println(f1.id)
                    println(f1.name)
                    println(f1.age)
                    var g = orm.find<User>(db, 999)
                    if (g == null) {
                        println("null")
                    } else {
                        println("hit")
                    }
                    var f2 = orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    var f3 = orm.find<User>(db, f2.id)
                    println(f3.name + "/" + f3.age)
                    db.execute(db, "update user set name = 'Melissa' where id = " + u1.id)
                    var f4 = orm.find<User>(db, u1.id)
                    println(f4.name)
                    var rows = db.query(db, "select count(*) as n from user")
                    for (var r in rows) {
                        println(r)
                    }
                }
                """;
        String expected = "1\nMel\n30\nnull\nAna/25\nMelissa\n{\"n\":2}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmfind.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativefind.db") + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.find (F2b): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (find: hit, miss=null, 2a linha, update lido de volta)");
    }

    @Test
    void allNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c: kof_orm_all no Native — 2 linhas por campo, lista vazia depois
        // do delete (host devolve List vazia, nunca null), oracle MEDIDO no
        // JVM (2/Mel/Ana/0/empty=0) antes de escrever a asm.
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    var all = orm.all<User>(db)
                    println(all.size())
                    for (var u in all) {
                        println(u.name)
                    }
                    db.execute(db, "delete from user")
                    var all2 = orm.all<User>(db)
                    println(all2.size())
                    var none = orm.all<User>(db)
                    if (none.size() == 0) { println("empty=0") }
                }
                """;
        String expected = "2\nMel\nAna\n0\nempty=0";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmall.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativeall.db") + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar orm.all (F2c): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (all: 2 linhas, ordem de insercao, lista vazia=0)");
    }

    @Test
    void whereNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c2: kof_orm_where/where_op no Native - oracle MEDIDO no JVM
        // (WhereJvm.kf): igualdade, vazio!=null, >, LIKE, ==, throw exato
        // "ORM operator not allowed: <op>" e chamada repetida sem leak.
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    orm.save(db, User(0, "Bia", "b@kof.dev", 40))
                    var w1 = orm.where<User>(db, "age", 30)
                    println(w1.size())
                    for (var u in w1) { println(u.name) }
                    var w2 = orm.where<User>(db, "age", 99)
                    println(w2.size())
                    var w3 = orm.where<User>(db, "age", ">", 25)
                    println(w3.size())
                    for (var u in w3) { println(u.name) }
                    var w4 = orm.where<User>(db, "name", "LIKE", "A%")
                    println(w4.size())
                    var w5 = orm.where<User>(db, "age", "==", 25)
                    println(w5.size())
                    try {
                        orm.where<User>(db, "age", "DROP TABLE user", 1)
                        println("no-throw")
                    } catch (String e) {
                        println("throw:[" + e + "]")
                    }
                    var w6 = orm.where<User>(db, "age", 30)
                    println(w6.size())
                }
                """;
        String expected = "1\nMel\n0\n2\nMel\nBia\n1\n1\nthrow:[ORM operator not allowed: DROP TABLE user]\n1";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmwhere.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativewhere.db") + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar orm.where/where_op (F2c2): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (where: =/>/LIKE/==, throw exato, vazio=0)");
    }

    @Test
    void pageDeleteSaveAllNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c3: kof_orm_page/kof_orm_delete/kof_orm_save_all no Native - oracle
        // MEDIDO no JVM (C3Jvm.kf): saveAll em batch, page com LIMIT/OFFSET
        // (bind 1/2), pagina vazio por offset, delete hit E miss (true sempre,
        // como execute1 >= 0 do host), leitura de volta por where/count.
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    var batch = listOf(User(0, "Mel", "m@kof.dev", 30), User(0, "Ana", "a@kof.dev", 25))
                    println(orm.saveAll<User>(db, batch))
                    println(orm.count<User>(db))
                    var p1 = orm.page<User>(db, 1, 0)
                    println(p1.size())
                    for (var u in p1) { println(u.name) }
                    var p2 = orm.page<User>(db, 2, 1)
                    println(p2.size())
                    for (var u in p2) { println(u.name) }
                    var p3 = orm.page<User>(db, 0, 0)
                    println(p3.size())
                    var p4 = orm.page<User>(db, 10, 99)
                    println(p4.size())
                    var w = orm.where<User>(db, "name", "Mel")
                    println(orm.delete<User>(db, w.get(0).id))
                    println(orm.count<User>(db))
                    var w2 = orm.where<User>(db, "name", "Mel")
                    println(w2.size())
                    println(orm.delete<User>(db, 999))
                    println(orm.count<User>(db))
                    var all = orm.all<User>(db)
                    for (var u in all) { println(u.name) }
                }
                """;
        String expected = "true\n2\n1\nMel\n1\nAna\n0\n0\ntrue\n1\n0\ntrue\n1\nAna";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmc3.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativec3.db") + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar page/delete/saveAll (F2c3): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (F2c3: saveAll batch, page LIMIT/OFFSET, delete hit/miss=true)");
    }

    @Test
    void pageEdgesDeleteMissSaveAllEmptyNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c3 edges (Q3): saveAll de lista VAZIA (true, count 0), pagina com
        // offset para alem do fim (vazia), page(1,1) = segunda linha, delete
        // repetido do MESMO id: hit=true/count-1, miss=true/count intacto
        // (execute1 >= 0 do host). Oracle MEDIDO no JVM (C3bJvm.kf).
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    println(orm.saveAll<User>(db, listOf()))
                    println(orm.count<User>(db))
                    println(orm.saveAll<User>(db, listOf(User(0, "Mel", "m@kof.dev", 30), User(0, "Ana", "a@kof.dev", 25))))
                    println(orm.count<User>(db))
                    var p = orm.page<User>(db, 5, 0)
                    println(p.size())
                    for (var u in p) { println(u.name) }
                    var pf = orm.page<User>(db, 5, 2)
                    println(pf.size())
                    var p1 = orm.page<User>(db, 1, 1)
                    println(p1.size())
                    println(p1.get(0).name)
                    var w = orm.where<User>(db, "name", "Mel")
                    println(orm.delete<User>(db, w.get(0).id))
                    println(orm.count<User>(db))
                    println(orm.delete<User>(db, w.get(0).id))
                    println(orm.count<User>(db))
                }
                """;
        String expected = "true\n0\ntrue\n2\n2\nMel\nAna\n0\n1\nAna\ntrue\n1\ntrue\n1";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + tempDir.resolve("jvmc3b.db") + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + tempDir.resolve("nativec3b.db") + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar edges F2c3: "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (F2c3 edges: batch vazio, offset alem, miss=true)");
    }

    @Test
    void rowObjectFechadoNoX86CrossAindaOrm001(@TempDir Path tempDir) throws IOException {
        // F2c3 FECHOU o row-object no x86-64 (os dois testes acima provam por
        // execucao). O pin honesto migrou para o que ainda e ORM001 em ORM:
        // cross riscv64/aarch64 (compile-time) - o gate da frente recusa a
        // face REAL, nunca silent (R6/R7). MySQL (runtime) segue pending no
        // backend via .Lorm_conn (coberto pelo pin existente de dialect).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("sqlite:/tmp/f2c3-pin.db")
                    var p = orm.page<User>(db, 1, 0)
                    var ok = orm.delete<User>(db, 1)
                    println(orm.saveAll<User>(db, listOf(User(0, "Mel", "m@kof.dev", 30))))
                }
                """);
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.NATIVE_RISCV64);
        assertFalse(r.success(), "row-object REAL so no x86-64; riscv64 ainda ORM001 ate a frente cross");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("ORM001"),
                "gate honesto (nunca silent): " + r.diagnostics().getDiagnostics());
    }
}