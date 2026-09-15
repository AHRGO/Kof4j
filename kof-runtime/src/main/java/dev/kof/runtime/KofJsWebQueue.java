package dev.kof.runtime;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * WEB001-T1 (JS): HttpHandler 100% Java que apenas enfileira o exchange.
 *
 * O Context GraalJS é thread-confined: um callback implementado em JS roda na
 * thread do dispatcher do HttpServer e NÃO pode entrar no contexto (a main
 * thread está dentro de kofWebListen). Este handler toca só em Java — a main
 * thread desfile a fila e processa cada request no contexto (event-loop).
 */
public final class KofJsWebQueue implements HttpHandler {

    private final BlockingQueue<HttpExchange> pending;

    public KofJsWebQueue(BlockingQueue<HttpExchange> pending) {
        this.pending = pending;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!pending.offer(exchange)) {
            // fila cheia/bounded: nunca descartar em silencio (R6) — responde 503
            // e libera o exchange (senao o cliente pendura e o dispatcher vaza).
            byte[] msg = "queue full".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, msg.length);
            exchange.getResponseBody().write(msg);
            exchange.close();
        }
    }
}
