package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — SSE no host GraalJS (WEB001 residual, 16/09).
 * Concatenado ao bloco "ui-web" (mesmo escopo de módulo de JsRuntimeUiWeb,
 * padrão crypto+chacha): usa kofWebUtf8Bytes/kofWebApps do bloco mestre.
 */
public final class JsRuntimeUiWebSse {
    private JsRuntimeUiWebSse() {
    }

    // §176: método (não literal constante) — evita inlining nos consumidores.
    static String uiWebSseRuntime() {
        return """
            // ── WEB001 SSE no host GraalJS (16/09) — HANDLER-SCOPED ──
            // O pump JS é single-thread cooperativo (KofJsWebQueue): um
            // exchange por vez na main-thread. Logo os eventos são escritos
            // DURANTE o corpo do handler e o stream fecha no retorno dele —
            // push assíncrono pós-return (o modelo ThreadLocal do JVM, onde
            // um timer/outro handler envia a qualquer momento) e múltiplos
            // clientes simultâneos seguem WEB003 residual no JS. R6: se o
            // guest chamar sse() depois do handler voltar, erro EXPLÍCITO —
            // nunca silêncio.
            let kofWebSseConn = null;

            function kofWebSseMakeConn(exchange) {
                const headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "text/event-stream");
                headers.set("Cache-Control", "no-cache");
                headers.set("Connection", "keep-alive");
                headers.set("X-Accel-Buffering", "no");
                exchange.sendResponseHeaders(200, 0);
                // javadoc HttpExchange: 0 = chunked (sem Content-Length, stream
                // vive até close); -1 = SEM corpo (o servidor engole os writes)
                // — foi o bug do primeiro dia; 404 path do pump usa 0 idem.
                const os = exchange.getResponseBody();
                const conn = {
                    __open: true,
                    __os: os,
                    send: function (data) {
                        kofWebSseWriteData(conn, data);
                    },
                    event: function (name, data) {
                        if (!conn.__open) return;
                        kofWebSseWriteFrame(conn, "event: " + name + "\\n");
                        kofWebSseWriteData(conn, data);
                    },
                    close: function () {
                        if (!conn.__open) return;
                        conn.__open = false;
                        try { os.flush(); } catch (e) { }
                        try { os.close(); } catch (e) { }
                    },
                    isOpen: function () {
                        return conn.__open;
                    }
                };
                return conn;
            }

            function kofWebSseWriteFrame(conn, frame) {
                if (!conn.__open) return;
                try {
                    const bytes = kofWebUtf8Bytes(frame);
                    conn.__os.write(bytes);
                    conn.__os.flush();
                } catch (e) {
                    // client fechou o socket (IOException no write/flush):
                    // marca fechado, nunca crasha o pump (idem JVM writeFrame).
                    conn.__open = false;
                }
            }

            function kofWebSseWriteData(conn, data) {
                if (!conn.__open) return;
                const text = String(data);
                const lines = text.split("\\n");
                for (let i = 0; i < lines.length; i++) {
                    kofWebSseWriteFrame(conn, "data: " + lines[i] + "\\n");
                }
                kofWebSseWriteFrame(conn, "\\n");
            }

            export function kofWebSseRoute(appId, method, path, handler) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                // idem JVM: route.kind SSE, method decorativo (o matching não
                // filtra método p/ SSE); o handler recebe a conexão no invoke.
                app.handlers.set("GET:" + path, { __sse: true, handler: handler });
                return 0;
            }

            export function kofWebSseSend(text) {
                // sse(text) — context-fn do handler corrente (idem JVM
                // KOF_SSE_SENDER; single-thread: variável de módulo basta).
                if (kofWebSseConn !== null && kofWebSseConn.isOpen()) {
                    kofWebSseConn.send(text);
                }
                return text;
            }

            """;
    }
}
