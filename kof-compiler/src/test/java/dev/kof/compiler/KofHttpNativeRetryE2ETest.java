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
 * §259 fatia 2 (native): {@code http.retry(n)} no x86_64. Semântica espelha
 * {@code JvmWebHttpRuntime.kof_http_request}: N+1 tentativas quando a
 * conexão falha OU o status HTTP é {@code >= 500}; esgotadas as tentativas,
 * {@code throw}. Antes: {@code kof_http_retry_set} era {@code ret} puro
 * (no-op silencioso — R6/Q7) e o core retornava o body de um 5xx sem
 * throwar (divergência de paridade com JVM = bug pela regra 4 do freeze).
 */
class KofHttpNativeRetryE2ETest {

    private final CompilerDriver driver = new CompilerDriver();
    private HttpServer server;
    private ExecutorService pool;
    private final AtomicInteger flakyHits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    private int startFlaky(int failuresBeforeOk) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newFixedThreadPool(2);
        server.setExecutor(pool);
        server.createContext("/flaky", ex -> {
            int n = flakyHits.incrementAndGet();
            byte[] body = ("ok-" + n).getBytes(StandardCharsets.UTF_8);
            if (n <= failuresBeforeOk) {
                ex.sendResponseHeaders(500, body.length);
            } else {
                ex.sendResponseHeaders(200, body.length);
            }
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        return server.getAddress().getPort();
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
        return out;
    }

    @Test
    void nativeRetrySucceedsAfterFlaky5xx(@TempDir Path tempDir) throws Exception {
        int port = startFlaky(2); // 500, 500, depois 200
        String out = runNative(tempDir, """
                main() {
                    http.retry(2)
                    println(http.get("http://127.0.0.1:%d/flaky"))
                }
                """.formatted(port));
        assertTrue(out.contains("ok-3"),
                "retry(2) should reach the 3rd attempt (200 ok-3), got: " + out);
        assertEquals(3, flakyHits.get(), "flaky server should have been hit exactly 3 times, got: " + flakyHits);
    }

    @Test
    void nativePersistent5xxThrowsAfterRetries(@TempDir Path tempDir) throws Exception {
        int port = startFlaky(100); // sempre 500
        String out = runNative(tempDir, """
                main() {
                    http.retry(1)
                    try {
                        println(http.get("http://127.0.0.1:%d/flaky"))
                        println("NO_THROW")
                    } catch (String e) {
                        println("e5=" + e)
                    }
                }
                """.formatted(port));
        assertTrue(out.contains("e5=HTTP 500 from"),
                "persistent 5xx must throw 'HTTP 500 from <url>' after retries (JVM parity), got: " + out);
        assertFalse(out.contains("NO_THROW"), "5xx must not return the body silently, got: " + out);
        assertEquals(2, flakyHits.get(), "retry(1) = 2 attempts, got: " + flakyHits);
    }

    @Test
    void nativeRetryZeroFivexxThrowsImmediately(@TempDir Path tempDir) throws Exception {
        int port = startFlaky(100); // sempre 500
        String out = runNative(tempDir, """
                main() {
                    try {
                        println(http.get("http://127.0.0.1:%d/flaky"))
                        println("NO_THROW")
                    } catch (String e) {
                        println("e5=" + e)
                    }
                }
                """.formatted(port));
        assertTrue(out.contains("e5=HTTP 500 from"),
                "default retry(0): single attempt, 5xx throws (JVM parity), got: " + out);
        assertEquals(1, flakyHits.get(), "retry(0) = exactly 1 attempt, got: " + flakyHits);
    }
}
