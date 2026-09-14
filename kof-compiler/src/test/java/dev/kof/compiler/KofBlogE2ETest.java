package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-SPRING F12 (DECISIONS.md, ratificado 13/09): E2E do APP MODEL CANÔNICO —
 * backend + frontend + db + auth + validation num ÚNICO app Kof, sem camadas,
 * sem injeção, sem Spring. É a validação de plataforma: um blog mínimo real
 * (POST /posts com sessão, GET /posts lista do H2, login com passwords.hash,
 * validação de campos, HTML servido pelo próprio app).
 *
 * Padrões copiados dos E2E existentes: servidor real em subprocesso JVM
 * (KofWebWsE2ETest), H2 em memória no classpath do subprocesso (KofDbE2ETest),
 * passwords/sessions reais (KofSecurityTest), validation.* (KofValidationTest).
 */
class KofBlogE2ETest {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }

    private static String findH2Jar() {
        for (String entry : System.getProperty("java.class.path").split(":")) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        return "";
    }

    private static boolean hasH2() {
        return !findH2Jar().isEmpty();
    }

    // ── O app canônico: UM arquivo, todas as capacidades ────────────────
    private static final String BLOG_APP = """
            record Post(String? title, String? body)

            main() {
                var app = web.app()
                var db = db.connect("jdbc:h2:mem:blog;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table posts(id identity, title varchar(200), body clob, author varchar(100))")
                // auth state em closure: hash da senha (passwords real, no boot)
                var PASSWORD_HASH = passwords.hash("correct-horse")

                // frontend servido pelo próprio app (sem pipeline externo)
                app.get("/") {
                    return "<html><body><h1>Kof Blog</h1></body></html>"
                }

                // auth: login com senha hasheada (passwords real) + sessão G9
                app.post("/login") {
                    var creds = json.decode<Post>(body())
                    if (creds.title() == "mel" && passwords.verify(creds.body(), PASSWORD_HASH)) {
                        var sid = security.sessionCreate("mel")
                        return json.encode(mapOf("token", sid))
                    }
                    throw "unauthorized"
                }

                // write path: validação + persistência
                app.post("/posts") {
                    var sid = header("x-session")
                    if (sid == "" || security.sessionGet(sid) == null) {
                        throw "unauthorized"
                    }
                    var post = json.decode<Post>(body())
                    if (!validation.notBlank(post.title()) || !validation.notBlank(post.body())) {
                        throw "invalid"
                    }
                    if (!validation.lengthBetween(post.title(), 1, 200)) {
                        throw "invalid"
                    }
                    db.execute(db, "insert into posts(title, body, author) values (?, ?, ?)",
                            post.title(), post.body(), security.sessionGet(sid))
                    return "{\\"ok\\":true}"
                }

                // read path: lista do banco
                app.get("/posts") {
                    return db.query(db, "select title, body from posts order by id")
                }

                app.listen(PORT)
            }
            """;

    @Test
    void canonicalAppServesFrontendDbAuthValidation(@TempDir Path tempDir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(hasH2(), "H2 ausente — skip honesto");
        int port = freePort();
        Path source = tempDir.resolve("Blog.kf");

        Files.writeString(source, BLOG_APP
                .replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilerDriver driver = new CompilerDriver();
        driver.setExternalClasspath(List.of(testClassesDir()));
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "app canônico deve compilar: "
                + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN,
                "-cp", outDir + ":" + findH2Jar(), "Default.Main");
        pb.redirectErrorStream(true);
        pb.redirectOutput(tempDir.resolve("server.log").toFile());
        serverProcess = pb.start();
        waitListening(port);
        // continua após o probe OK
        assertTrue(serverProcess.isAlive(), "servidor morreu no boot");

        String home = request(port, "GET / HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n");
        assertTrue(home.contains("Kof Blog"), "frontend servido: " + home);

        System.out.println("== STEP: login errado ==");
        // login errado → erro (senha inválida não autentica)
        String badLogin = post(port, "/login", "Content-Type: application/json\r\n",
                "{\"title\":\"mel\",\"body\":\"wrong\"}");
        assertTrue(badLogin.contains("500") || badLogin.contains("unauthorized")
                || badLogin.contains("401"),
                "login errado não pode autenticar: " + badLogin);

        System.out.println("== STEP: login certo ==");
        // login certo → token de sessão
        String login = post(port, "/login", "Content-Type: application/json\r\n",
                "{\"title\":\"mel\",\"body\":\"correct-horse\"}");
        assertTrue(login.contains("token"), "login deve devolver token: " + login);
        String token = extractJsonStringField(login, "token");

        System.out.println("== STEP: write sem sessão ==");
        // write sem sessão → erro
        String noAuth = post(port, "/posts", "",
                "{\"title\":\"t\",\"body\":\"b\"}");
        assertTrue(noAuth.contains("500") || noAuth.contains("unauthorized")
                || noAuth.contains("401"),
                "post sem sessão não pode gravar: " + noAuth);

        System.out.println("== STEP: write inválido ==");
        // write com validação violada (título em branco) → erro
        String invalid = post(port, "/posts", "x-session: " + token + "\r\n",
                "{\"title\":\"\",\"body\":\"conteúdo\"}");
        assertTrue(invalid.contains("500") || invalid.contains("invalid")
                || invalid.contains("400"),
                "validação deve rejeitar título em branco: " + invalid);

        System.out.println("== STEP: write válido ==");
        // write válido → grava
        String created = post(port, "/posts", "x-session: " + token + "\r\n",
                "{\"title\":\"Primeiro post\",\"body\":\"Olá mundo\"}");
        assertTrue(created.contains("\"ok\":true") || created.contains("200"),
                "post válido deve gravar: " + created);

        System.out.println("== STEP: read /posts ==");
        // read path: lista volta do banco
        String list = request(port, "GET /posts HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n");
        assertTrue(list.contains("Primeiro post") && list.contains("Olá mundo"),
                "lista deve conter o post gravado: " + list);
    }

    /** POST com body + Content-Length correto (body() exige). */
    private String post(int port, String path, String headers, String body) throws IOException {
        return request(port, "POST " + path + " HTTP/1.1\r\nHost: x\r\n"
                + headers
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "Connection: close\r\n\r\n" + body);
    }

    /** Extrai o valor string de um campo JSON plano ("token":"..."). */
    private static String extractJsonStringField(String response, String field) {
        for (String line : response.split("\n")) {
            var m = java.util.regex.Pattern
                    .compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(line);
            if (m.find()) return m.group(1);
        }
        throw new AssertionError("campo \"" + field + "\" não encontrado em: " + response);
    }

    // ── helpers (mesmos dos E2E existentes) ─────────────────────────────

    private static Path testClassesDir() throws Exception {
        return Path.of(KofBlogE2ETest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
    }

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private void waitListening(int port) throws IOException {
        int attempt = 0;
        while (attempt < 40) {
            if (!serverProcess.isAlive()) {
                String out;
                try {
                    out = new String(serverProcess.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
                } catch (IOException e) {
                    out = "(sem saída)";
                }
                throw new IOException("server exited early: " + out);
            }
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted waiting for server", ie);
                }
            }
            attempt++;
        }
        throw new IOException("server did not start listening on " + port);
    }

    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
            }
            return response.toString();
        }
    }
}
