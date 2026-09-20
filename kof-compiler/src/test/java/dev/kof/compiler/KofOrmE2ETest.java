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
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("localhost", 3306),
                "MariaDB not reachable (start it: docker run -d -p 3306:3306 -e MARIADB_ROOT_PASSWORD=kof mariadb:11)");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:mariadb://localhost:3306/kof_test_" + System.nanoTime() + "?user=root&password=kof&allowMultiQueries=true&createDatabaseIfNotExist=true\")\n"
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
    void nativeReportsOrm001JsSupported(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("sqlite:/tmp/orm-test.db")
                    orm.save(db, User(1, "Mel", "m@kof.dev", 30))
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertFalse(nativeResult.success());
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("ORM001"),
                "Native should report ORM001: " + nativeResult.diagnostics().getDiagnostics());

        // ORM001 (18/09): JS agora suportado via KofJsOrmBridge — compila limpo.
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertTrue(jsResult.success(),
                "JS should now compile orm.* (ORM001 closed): " + jsResult.diagnostics().getDiagnostics());
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
    void sqlFacesRestantesNativeAindaOrm001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("sqlite:/tmp/f1a-gate.db")
                    orm.save(db, User(1, "Mel", "m@kof.dev", 30))
                }
                """);
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(r.success(), "save (row-object) ainda e ORM001 no Native ate F2");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("ORM001"),
                "gate honesto (nunca silent): " + r.diagnostics().getDiagnostics());
    }
}