package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WEB001-T1 (13/09): o servidor web Kof roda no target JS (GraalJS hostless).
 *
 * Prova E2E real: compila um programa Kof com rotas ({@code app.get/post +
 * app.listen(port)}) para Target.JS, executa via {@link dev.kof.runtime.KofJsRunner}
 * (thread daemon — listen é bloqueante, idem JVM) e exercita as rotas por
 * sockets HTTP reais.
 *
 * Bugs de runtime cobertos aqui (regressão):
 * <ul>
 *   <li>Context GraalJS é thread-confined — handler HTTP implementado em JS
 *       morre silenciosamente na thread do dispatcher; o dispatcher agora é
 *       Java puro ({@code KofJsWebQueue}) e o request é processado na main
 *       thread (event-loop em kofWebListen).</li>
 *   <li>O RETORNO do handler é o body 200 (idem JVM JvmRuntimeWebDispatch);
 *       antes era descartado (empty reply).</li>
 *   <li>Rota com {@code :param}: HttpServer não tem matching de params — o
 *       createContext registra o PREFIXO estático e o match real acontece no
 *       dispatch (chave "METHOD:path" não pode ser quebrada em ":" dentro do
 *       path, ex.: "GET:/u/:id").</li>
 *   <li>UTF-8: encoder puro JS ({@code kofWebUtf8Bytes}) — interop de
 *       INSTÂNCIA Java host (String.getBytes) não expõe métodos nesta build;
 *       statics e Java.to funcionam.</li>
 * </ul>
 */
class KofWebJsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String WEB_APP = """
            main() {
                var app = web.app()
                app.get("/hello") {
                    return "Hello from Kof"
                }
                app.get("/users/:id") {
                    return "user " + param("id") + " q=" + query("name")
                }
                app.get("/me") {
                    return method() + " " + path()
                }
                app.post("/echo") {
                    return "got:" + body()
                }
                app.listen(PORT)
            }
            """;

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }

    /** HTTP/1.0 request mínimo — lê a resposta inteira e fecha. */
    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private static String bodyOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(idx + 4) : rawResponse;
    }

    @Test
    void jsWebServesRoutes(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, WEB_APP.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS web deve suceder: "
                + result.diagnostics().getDiagnostics());

        // listen é bloqueante (idem JVM) — roda o runner em thread daemon.
        Thread serverThread = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.InputStream.nullInputStream(),
                        new java.io.ByteArrayOutputStream());
            } catch (Exception ignored) {
                // o socket fechar no fim do teste interrompe o accept/poll
            }
        }, "kof-web-js-e2e");
        serverThread.setDaemon(true);
        serverThread.start();

        // espera o server abrir a porta
        int attempt = 0;
        boolean listening = false;
        while (attempt < 50) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                listening = true;
                break;
            } catch (IOException e) {
                Thread.sleep(100);
            }
            attempt++;
        }
        assertTrue(listening, "server JS não abriu a porta " + port);

        String hello = request(port, "GET /hello HTTP/1.0\r\nHost: x\r\n\r\n");
        assertTrue(hello.startsWith("HTTP/1.1 200") || hello.startsWith("HTTP/1.0 200"), hello);
        assertEquals("Hello from Kof", bodyOf(hello).trim(), "rota exata: " + hello);

        String param = request(port, "GET /users/42?name=mel HTTP/1.0\r\nHost: x\r\n\r\n");
        assertTrue(param.startsWith("HTTP/1.1 200") || param.startsWith("HTTP/1.0 200"), param);
        assertEquals("user 42 q=mel", bodyOf(param).trim(), "rota :param + query: " + param);

        String me = request(port, "GET /me HTTP/1.0\r\nHost: x\r\n\r\n");
        assertEquals("GET /me", bodyOf(me).trim(), "method()+path(): " + me);

        String echo = request(port, "POST /echo HTTP/1.0\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello");
        assertTrue(echo.startsWith("HTTP/1.1 200") || echo.startsWith("HTTP/1.0 200"), echo);
        assertEquals("got:hello", bodyOf(echo).trim(), "POST body: " + echo);
    }
}
