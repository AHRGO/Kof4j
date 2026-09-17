package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §259 fatia 3 (native): {@code http.circuit(n)} no x86_64. Semântica espelha
 * {@code JvmWebHttpRuntime}: após {@code n} falhas (conexão/timeout/5xx) o
 * circuito ABRE por 30s; enquanto aberto, toda request falha-rápido com
 * {@code throw "kof.http circuit open (fail fast): " + url} SEM tocar o socket
 * (contagem de hits no servidor prova isso); sucesso reseta; {@code circuit(0)}
 * fecha e limpa. Antes: {@code kof_http_circuit_set} era {@code ret} puro
 * (no-op silencioso — R6/Q7) e o fail-fast nunca ocorria (a 2a request chegava
 * ao servidor). Reproduz o cenário de {@code KofHttpResilienceE2ETest}
 * (JVM/JS) no binário nativo.
 */
class KofHttpNativeCircuitE2ETest {

    private final CompilerDriver driver = new CompilerDriver();
    private HttpServer good;
    private ExecutorService pool;
    private final AtomicInteger okHits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (good != null) good.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    private int startGood() throws IOException {
        good = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newFixedThreadPool(2);
        good.setExecutor(pool);
        good.createContext("/ok", ex -> {
            okHits.incrementAndGet();
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        good.start();
        return good.getAddress().getPort();
    }

    private int closedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private String runNative(Path tempDir, String kofSource) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("nat");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native should compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default").resolve("Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        java.util.concurrent.Future<byte[]> reader;
        boolean done;
        try (var ex = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "native-out-reader"); t.setDaemon(true); return t; })) {
            reader = ex.submit(() -> p.getInputStream().readAllBytes());
            done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
            }
        }
        String out = new String(reader.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(done, "native binary did not exit in 30s; out=" + out);
        assertEquals(0, p.exitValue(), "exit code, out=" + out);
        return out;
    }

    @Test
    void nativeCircuitOpensFailFastsThenRecovers(@TempDir Path tempDir) throws Exception {
        int port = startGood();
        int closed = closedPort();
        // retry(0) p/ nao reiniciar o circuito durante a 1a request; circuit(1)
        // = 1 falha ja abre. closedUrl falha (conexao) -> abre; /ok deve
        // fail-fast SEM tocar o servidor (okHits==0 no meio); circuit(0) fecha;
        // /ok entao conecta (okHits==1 so' no recover).
        String out = runNative(tempDir, """
                main() {
                    http.retry(0)
                    http.circuit(1)
                    try {
                        println(http.get("http://127.0.0.1:%d/x"))
                        println("fail=nothrow")
                    } catch (String e) {
                        println("fail=caught")
                    }
                    try {
                        println(http.get("http://127.0.0.1:%d/ok"))
                        println("open=nothrow")
                    } catch (String e) {
                        println("open=" + e)
                    }
                    http.circuit(0)
                    println("recover=" + http.get("http://127.0.0.1:%d/ok"))
                }
                """.formatted(closed, port, port));
        assertTrue(out.contains("fail=caught"), "closed port must throw (opens circuit), got: " + out);
        assertTrue(out.contains("open=kof.http circuit open (fail fast)"),
                "second request must fail-fast with 'circuit open', got: " + out);
        assertFalse(out.contains("open=nothrow"), "circuit open must NOT return body, got: " + out);
        assertTrue(out.contains("recover=ok"), "circuit(0) must recover, got: " + out);
        assertEquals(1, okHits.get(),
                "/ok must be hit EXACTLY once (recovery) — fail-fast must not connect; got hits=" + okHits);
    }
}
