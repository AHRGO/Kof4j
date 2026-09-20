package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Fase 5 — kof.db: JDBC por interoperabilidade JVM, API idiomática Kof
 * (db.connect/execute/query/query&lt;T&gt;/close + transaction { }).
 *
 * Os testes usam H2 em memória (dependência test-scope); o subprocesso roda
 * com o jar do H2 no classpath.
 */
class KofDbE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir + ":" + h2, "Default.Main");
            pb.redirectErrorStream(true);
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
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException("H2 jar not found on test classpath");
    }

    // DB001 (16/09): o KofJS roda no host GraalJS (KofJsRunner), que vive na
    // MESMA JVM do teste e ve o h2/sqlite do java.class.path pelo DriverManager
    // (o proprio findH2Jar le esse classpath). O delegate `kof_platform.db*`
    // faz o roundtrip JDBC; a saida deve bater com o caminho JVM byte-a-byte.
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

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void executeAndQueryRowsAsJson(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 1, "Mel")
                db.execute(db, "insert into users values (?, ?)", 2, "Kof")
                var rows = db.query(db, "select * from users order by id")
                println(rows.size)
                println(rows.get(0))
                println(rows.get(1))
            }
            """);
        runJvm(source, tempDir.resolve("out"),
                "2\n{\"id\":1,\"name\":\"Mel\"}\n{\"id\":2,\"name\":\"Kof\"}");
    }

    @Test
    void typedQueryBindsRecord(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 7, "Ada")
                var users = db.query<User>(db, "select * from users where id = ?", 7)
                println(users.size)
                println(users.get(0).id)
                println(users.get(0).name)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "1\n7\nAda");
    }

    @Test
    void typedQueryAllRows(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:test3;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (1, 'A')")
                db.execute(db, "insert into users values (2, 'B')")
                var users = db.query<User>(db, "select * from users order by id")
                var total = 0
                for (var u in users) {
                    total = total + u.id
                }
                println(total)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3");
    }

    @Test
    void transactionCommits(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test4;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                transaction {
                    db.execute(db, "insert into t values (1)")
                    db.execute(db, "insert into t values (2)")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"n\":2}");
    }

    @Test
    void androidDbEmitsTheSameBytecodeAsJvm(@TempDir Path tempDir) throws IOException {
        // D-DB-GAPS DB-2 (20/09, §278): "android é JVM" nao e lema — e o
        // bytecode. ANDROID reusa o JvmBackend; o pin abaixo trava a
        // paridade por construcao (mesmo .class nos dois alvos) para kof.db
        // e kof.orm, os namespaces cuja over-gating o DB-2 levantou.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:/tmp/android-parity.db")
                    orm.create<User>(db)
                    println(orm.count<User>(db))
                }
                """);
        CompilationResult jvm = driver.compile(source, tempDir.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM baseline: " + jvm.diagnostics().getDiagnostics());
        CompilationResult android = driver.compile(source, tempDir.resolve("android"), Target.ANDROID);
        assertTrue(android.success(), "android compila kof.db/kof.orm desde DB-2: "
                + android.diagnostics().getDiagnostics());
        byte[] a = Files.readAllBytes(tempDir.resolve("jvm/Default/Main.class"));
        byte[] b = Files.readAllBytes(tempDir.resolve("android/Default/Main.class"));
        assertArrayEquals(a, b, "Main.class ANDROID deve ser identico ao JVM (paridade por construcao)");
    }

    @Test
    void transactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test5;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                try {
                    transaction {
                        db.execute(db, "insert into t values (1)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
                 var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    // GitHub #65 / bug 77 — aninhamento: um bloco transaction interno NESTA
    // mesma conexão NÃO comita (participa da transação externa). Antes o
    // commit interno confirmava as linhas da transação externa e o rollback
    // posterior não as desfazia ({"n":2} — garantia transacional quebrada).
    @Test
    void nestedTransactionDoesNotCommitOuterScope(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var connection = db.connect("jdbc:h2:mem:tx_probe;DB_CLOSE_DELAY=-1")
                db.execute(connection, "create table entries(id int)")
                try {
                    transaction {
                        db.execute(connection, "insert into entries values (1)")
                        transaction {
                            db.execute(connection, "insert into entries values (2)")
                        }
                        throw "abort outer transaction"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(connection, "select count(*) as n from entries")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    // ── DB001 (16/09): os mesmos contratos no KofJS, via delegate
    //    `kof_platform.db*` no host GraalJS — saida byte-parity com JVM ──
    @Test
    void jsExecuteAndQueryRowsAsJson(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:js1;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 1, "Mel")
                db.execute(db, "insert into users values (?, ?)", 2, "Kof")
                var rows = db.query(db, "select * from users order by id")
                println(rows.size)
                println(rows.get(0))
                println(rows.get(1))
            }
            """);
        runJs(source, tempDir.resolve("out"),
                "2\n{\"id\":1,\"name\":\"Mel\"}\n{\"id\":2,\"name\":\"Kof\"}");
    }

    @Test
    void jsTransactionCommits(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:js2;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                transaction {
                    db.execute(db, "insert into t values (1)")
                    db.execute(db, "insert into t values (2)")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJs(source, tempDir.resolve("out"), "{\"n\":2}");
    }

    @Test
    void jsTransactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:js3;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                try {
                    transaction {
                        db.execute(db, "insert into t values (1)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    @Test
    void jsNestedTransactionDoesNotCommitOuterScope(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var connection = db.connect("jdbc:h2:mem:js4;DB_CLOSE_DELAY=-1")
                db.execute(connection, "create table entries(id int)")
                try {
                    transaction {
                        db.execute(connection, "insert into entries values (1)")
                        transaction {
                            db.execute(connection, "insert into entries values (2)")
                        }
                        throw "abort outer transaction"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(connection, "select count(*) as n from entries")
                println(rows.get(0))
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    @Test
    void nativeTransactionCommits(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native transaction requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/tx.db")
                db.execute(db, "create table if not exists t(x int)")
                db.execute(db, "delete from t")
                transaction {
                    db.execute(db, "insert into t values (1)")
                    db.execute(db, "insert into t values (2)")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """.formatted(tempDir));
        runNative(source, tempDir.resolve("out"), "{\"n\":2}");
    }

    @Test
    void nativeTransactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native transaction requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/txr.db")
                db.execute(db, "create table if not exists t(x int)")
                db.execute(db, "delete from t")
                try {
                    transaction {
                        db.execute(db, "insert into t values (1)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """.formatted(tempDir));
        runNative(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    // GitHub #65 / bug 77 espelhado no Native (§78): o bloco transaction
    // interno NESTA mesma conexão NÃO comita — participa da externa. Antes o
    // commit interno efetivava as linhas e o rollback do externo não as
    // desfazia ({"n":2}). Esperado: {"n":0} (paridade com o JVM).
    @Test
    void nativeNestedTransactionDoesNotCommitOuterScope(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native transaction requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/txnest.db")
                db.execute(db, "create table if not exists entries(id int)")
                db.execute(db, "delete from entries")
                try {
                    transaction {
                        db.execute(db, "insert into entries values (1)")
                        transaction {
                            db.execute(db, "insert into entries values (2)")
                        }
                        throw "abort outer transaction"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(db, "select count(*) as n from entries")
                println(rows.get(0))
            }
            """.formatted(tempDir));
        runNative(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    @Test
    void connectWithCredentials(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test6;DB_CLOSE_DELAY=-1", "sa", "")
                db.execute(db, "create table t(x int)")
                db.execute(db, "insert into t values (42)")
                var rows = db.query(db, "select x from t")
                println(rows.get(0))
                db.close(db)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"x\":42}");
    }

    @Test
    void nativeSqliteRoundtrip(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native SQLite requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                db.execute(db, "create table if not exists u(id int, name varchar)")
                db.execute(db, "insert into u values (?, ?)", 7, "Nativa")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """.formatted(tempDir));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Nativa\"}", output, "Native SQLite query output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeMysqlWireProtocol(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        // WIP MySQL wire protocol (handshake + auth switch + COM_QUERY + resultset).
        // Requer um MySQL/MariaDB real (porta configurável via env KOF_MYSQL_PORT,
        // default 13306; credenciais root/kofpass, db test). Pula se indisponível.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MySQL server not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "create table if not exists u(id int, name varchar(50))")
                db.execute(db, "delete from u")
                db.execute(db, "insert into u values (?, ?)", 7, "Nativa")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """.formatted(port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Nativa\"}", output, "Native MySQL query output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeMysqlPreparedBinary(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MySQL server not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "drop table if exists u3")
                db.execute(db, "create table u3(id int, name varchar(120))")
                db.execute(db, "insert into u3 values (?, ?)", 5, "Joe 'Cool'")
                db.execute(db, "insert into u3 values (?, ?)", 6, "x\\\"; drop table u3; --")
                var rows = db.query(db, "select id, name from u3 order by id")
                for (var r in rows) { println(r) }
                var rows2 = db.query(db, "select id, name from u3 where name = ?", "Joe 'Cool'")
                for (var r in rows2) { println(r) }
                db.close(db)
            }
            """.formatted(port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":5,\"name\":\"Joe 'Cool'\"}\n{\"id\":6,\"name\":\"x\\\"; drop table u3; --\"}\n"
                    + "{\"id\":5,\"name\":\"Joe 'Cool'\"}", output, "Native MySQL binary query output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeSupportsSqliteAndJsCompilesTypedQuery(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:/tmp/kof-db-test.db")
            }
            """);
        // Native: kof.db agora compila — SQLite via link direto de
        // libsqlite3 (sem JDBC driver); URLs não-sqlite falham em runtime.
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                nativeResult.diagnostics().getDiagnostics().toString());

        // DB002 (18/09): o gate compile-time CAIU — query tipado compila no JS
        // (bind no guest via __kof_decode_<T>, wire untyped).
        Path jsSource = tempDir.resolve("MainJs.kf");
        Files.writeString(jsSource, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:sqlite:/tmp/x.db")
                var rows = db.query<User>(db, "select * from u")
                println(rows.size)
            }
            """);
        CompilationResult jsResult = driver.compile(jsSource, tempDir.resolve("js-out"), Target.JS);
        assertTrue(jsResult.success(), "DB002: typed query agora compila no JS: "
                + jsResult.diagnostics().getDiagnostics());
        assertFalse(jsResult.diagnostics().getDiagnostics().toString().contains("DB002"),
                jsResult.diagnostics().getDiagnostics().toString());
    }

    @Test
    void jsTypedQueryBindsRecord(@TempDir Path tempDir) throws IOException {
        // DB002 (18/09): paridade byte-a-byte com `typedQueryBindsRecord` da JVM
        // (o mesmo `__kof_decode_<T>` do json.decode faz o bind por linha).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:jsd002a;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 7, "Ada")
                var users = db.query<User>(db, "select * from users where id = ?", 7)
                println(users.size)
                println(users.get(0).id)
                println(users.get(0).name)
            }
            """);
        runJs(source, tempDir.resolve("out"), "1\n7\nAda");
    }

    @Test
    void jsTypedQueryAllRows(@TempDir Path tempDir) throws IOException {
        // DB002 (18/09): paridade com `typedQueryAllRows` da JVM (for-in sobre
        // List<record> + acesso a campo).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:jsd002b;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (1, 'A')")
                db.execute(db, "insert into users values (2, 'B')")
                var users = db.query<User>(db, "select * from users order by id")
                var total = 0
                for (var u in users) {
                    total = total + u.id
                }
                println(total)
            }
            """);
        runJs(source, tempDir.resolve("out"), "3");
    }

    @Test
    void handleReuseAfterCloseDoesNotAliasLiveConnection(@TempDir Path tempDir) throws IOException {
        // issue #60 — `kof_db_register` gerava `"db" + (size() + 1)`: fechar
        // `a` e abrir `c` reutilizava o id de `b` (ainda aberta), sobrescrevia
        // o registro e o UPDATE via `b` escrevia no banco C — silencioso.
        // Fix: contador monotônico (nunca reutilizar handle de conexão ativa).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = db.connect("jdbc:h2:mem:database_a;DB_CLOSE_DELAY=-1")
                var b = db.connect("jdbc:h2:mem:database_b;DB_CLOSE_DELAY=-1")
                db.execute(b, "create table marker(amount int)")
                db.execute(b, "insert into marker values (20)")
                db.close(a)
                var c = db.connect("jdbc:h2:mem:database_c;DB_CLOSE_DELAY=-1")
                db.execute(c, "create table marker(amount int)")
                db.execute(c, "insert into marker values (30)")
                println(b)
                println(c)
                db.execute(b, "update marker set amount = 99")
                var rows = db.query(c, "select amount as n from marker")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("jvm"), "db2\ndb3\n{\"n\":30}");
    }

    @Test
    void crossNativeSqliteRoundtrip(@TempDir Path tempDir) throws IOException {        // DB001 fechado no cross (15/09): o frontend compila db.* e o runtime
        // RtB46/RtB47 liga dinamicamente a libsqlite3 (link-by-use). Mesmo
        // programa do nativeSqliteRoundtrip, riscv64 + aarch64 sob qemu.
        Path source = tempDir.resolve("Cross.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                db.execute(db, "create table if not exists u(id int, name varchar)")
                db.execute(db, "delete from u")
                db.execute(db, "insert into u values (?, ?)", 7, "Nativa")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """.formatted(tempDir));
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            Path out = tempDir.resolve("out-" + t);
            CompilationResult r = driver.compile(source, out, t);
            assertTrue(r.success(), t + " deveria compilar db.*: " + r.diagnostics().getDiagnostics());
            Path binFile = out.resolve("Default/Main");
            assertTrue(Files.exists(binFile), "binário " + t + " deveria existir");
            ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, binFile.toString());
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted running " + t + " binary", e);
            }
            assertEquals(0, ec, t + " exit code, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Nativa\"}", output, t + " SQLite query output");
        }
    }

    /** has() do padrão dos testes cross (command -v). */
    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /** QEMU_LD_PREFIX p/ o loader dinâmico (espelho de NativeRiscv64E2ETest). */
    private static String qemuPrefix(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        String loader = arch.equals("riscv64") ? "ld-linux-riscv64-lp64d.so.1" : "ld-linux-aarch64.so.1";
        if (env != null && !env.isBlank()
                && Files.exists(Path.of(env, "usr", arch + "-linux-gnu", "lib", loader))) {
            return env + "/usr/" + arch + "-linux-gnu";
        }
        for (String root : new String[]{"/tmp/opencode/x", "/"}) {
            Path p = Path.of(root, "usr", arch + "-linux-gnu");
            if (Files.exists(p.resolve("lib").resolve(loader))) {
                return root.equals("/") ? p.toString() : root + "/usr/" + arch + "-linux-gnu";
            }
        }
        return null;
    }

    @Test
    void crossNativeSqliteNowCompiles(@TempDir Path tempDir) throws IOException {
        // DB001 fechado no cross (15/09): o gate caiu — db.* compila nos dois
        // alvos cross. §255 (16/09): compilar p/ cross INCLUI o link contra a
        // libsqlite3 do sysroot (link-by-use); sem ela o ld falha ALTO — que é
        // R6 correto p/ o USUÁRIO, mas um falso-vermelho p/ a SUÍTE numa máquina
        // sem o pacote multiarch. O guard é o mesmo par do irmão
        // crossNativeSqliteRoundtrip: pula honesto quando toolchain/sysroot/
        // sqlite ausentes; na máquina com eles roda como antes (Q0: sem o
        // guard, riscv64-ld "não foi possível localizar -lsqlite3" = red).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:/tmp/x.db")
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            CompilationResult r = new CompilerDriver().compile(source, tempDir.resolve("cross-" + t), t);
            assertTrue(r.success(), t + " db.* agora compila (DB001 fechado): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void jsDbConnectNowCompiles(@TempDir Path tempDir) throws IOException {
        // DB001 fechado no JS (16/09): o gate `KofDb.supportedOn` abriu —
        // connect nao-tipado compila (a ponte `kof_platform.db*` roda na mesma
        // JVM/classpath do caminho JVM). DB002 fechado no JS (18/09): o query
        // tipado tbem compila (bind no guest; veja jsTypedQueryBindsRecord).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:gate;DB_CLOSE_DELAY=-1")
            }
            """);
        CompilationResult r = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertTrue(r.success(), "connect deve compilar no JS apos DB001: "
                + r.diagnostics().getDiagnostics());
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("DB001"),
                r.diagnostics().getDiagnostics().toString());
    }
}