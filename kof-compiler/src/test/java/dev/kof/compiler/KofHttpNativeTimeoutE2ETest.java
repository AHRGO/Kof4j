package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §259 (native): {@code kof.http} timeout/retry/circuit eram SILENT no-ops no
 * alvo NATIVE (asm emitia {@code ret} puro). Esta classe fecha a FATIA 1 —
 * timeout REAL no x86_64: connect nao-bloqueante + poll com deadline +
 * getsockopt SO_ERROR + SO_RCVTIMEO/SO_SNDTIMEO, jogando {@code throw
 * "kof.http: timeout"} quando o socket nao responde a tempo (mesma superficie
 * de erro observavel do JVM). O servidor "blackhole" abaixo ACEITA a conexao
 * mas NUNCA responde — sem o timeout o binario travaria no read ate o
 * wall-clock do proprio teste (que falha em vez de pendurar).
 */
class KofHttpNativeTimeoutE2ETest {

    private final CompilerDriver driver = new CompilerDriver();
    private HttpServer good;
    private ExecutorService pool;
    private ServerSocket blackhole;
    private volatile boolean bhRunning = true;
    private final java.util.List<Socket> bhHeld = new java.util.ArrayList<>();

    @AfterEach
    void stop() throws IOException {
        bhRunning = false;
        if (blackhole != null) blackhole.close();
        synchronized (bhHeld) {
            for (Socket s : bhHeld) { try { s.close(); } catch (IOException ignored) {} }
        }
        if (good != null) good.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    /** Porta que aceita conexoes e nunca escreve nada (deadline de leitura). */
    private int startBlackhole() throws IOException {
        blackhole = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            while (bhRunning) {
                try {
                    Socket s = blackhole.accept();
                    synchronized (bhHeld) { bhHeld.add(s); } // mantem aberto, nao responde
                } catch (IOException e) {
                    return;
                }
            }
        }, "blackhole-accept");
        t.setDaemon(true);
        t.start();
        return blackhole.getLocalPort();
    }

    private int startGood() throws IOException {
        good = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newFixedThreadPool(2);
        good.setExecutor(pool);
        good.createContext("/hello", ex -> {
            byte[] body = "Hello from Kof".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        good.start();
        return good.getAddress().getPort();
    }

    private String runNative(Path tempDir, String kofSource) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("nat");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native should compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default").resolve("Main");
        assertTrue(Files.exists(bin), "Native binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        java.util.concurrent.Future<byte[]> reader;
        boolean done;
        try (var ex = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "native-out-reader"); t.setDaemon(true); return t; })) {
            reader = ex.submit(() -> p.getInputStream().readAllBytes());
            done = p.waitFor(20, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
            }
        }
        String out = new String(reader.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(done, "Native binary did not exit within 20s (timeout knob not enforced); out=" + out);
        assertEquals(0, p.exitValue(), "exit code, out=" + out);
        return out;
    }

    @Test
    void nativeTimeoutFiresWhenServerNeverReplies(@TempDir Path tempDir) throws Exception {
        int bh = startBlackhole();
        String out = runNative(tempDir, """
                main() {
                    http.timeout(1)
                    try {
                        println(http.get("http://127.0.0.1:%d/x"))
                        println("NO_TIMEOUT")
                    } catch (String e) {
                        println("tmo=" + e)
                    }
                }
                """.formatted(bh));
        assertTrue(out.contains("tmo=kof.http: timeout"),
                "timeout should throw 'kof.http: timeout' when server never replies, got: " + out);
        assertFalse(out.contains("NO_TIMEOUT"), "get() must not return on a silent server, got: " + out);
    }

    @Test
    void nativeRequestStillSucceedsWithTimeoutSet(@TempDir Path tempDir) throws Exception {
        int port = startGood();
        startBlackhole();
        // bom servidor responde antes do deadline -> nao deve throwar
        String out = runNative(tempDir, """
                main() {
                    http.timeout(5)
                    println(http.get("http://127.0.0.1:%d/hello"))
                }
                """.formatted(port));
        assertEquals("Hello from Kof", out, "normal request with timeout(5) must still succeed");
    }

    @Test
    void nativeTimeoutFailsFastOnClosedPortWithTimeout(@TempDir Path tempDir) throws Exception {
        // porta fechada: connect deve falhar RAPIDO (nao esperar o timeout) —
        // exercita o caminho getsockopt(SO_ERROR) != 0 -> erro de conexao.
        int dead;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            dead = s.getLocalPort();
        }
        String out = runNative(tempDir, """
                main() {
                    http.timeout(5)
                    try {
                        println(http.get("http://127.0.0.1:%d/x"))
                        println("CONNECTED")
                    } catch (String e) {
                        println("err=" + e)
                    }
                }
                """.formatted(dead));
        assertTrue(out.contains("err=kof.http: connect falhou"),
                "closed port must fail fast as connect error, not hang, got: " + out);
        assertFalse(out.contains("CONNECTED"), "must not connect to a closed port, got: " + out);
    }
}
