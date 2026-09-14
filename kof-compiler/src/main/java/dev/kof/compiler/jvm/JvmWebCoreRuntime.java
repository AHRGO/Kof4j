package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.web (native web stack: WebApp/router/middleware/static/serve) - parte 1/2 de JvmWebRuntime. Concatenacao preserva byte-a-byte.
 */
public final class JvmWebCoreRuntime {

    private JvmWebCoreRuntime() {}

    static String source() {
        return """
                // ── kof.web — native web stack ────────────────────

                private static final java.util.concurrent.ConcurrentHashMap<String, WebApp> KOF_WEB_APPS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_WEB_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();
                private static final ThreadLocal<WebRequest> KOF_WEB_REQUEST = new ThreadLocal<>();
                private static final ThreadLocal<Integer> KOF_WEB_STATUS = new ThreadLocal<>();
                private static final ThreadLocal<java.util.Map<String, String>> KOF_WEB_HEADERS =
                        ThreadLocal.withInitial(java.util.HashMap::new);
                // D-SEC C18: headers de resposta do middleware de security
                // (CSP/HSTS/CORS/Set-Cookie) — separados de KOF_WEB_HEADERS
                // porque o dispatch limpa estes ANTES de invocar a rota; estes
                // sobrevivem até o build da resposta.
                private static final ThreadLocal<java.util.Map<String, String>> KOF_SEC_RESPONSE_HEADERS =
                        ThreadLocal.withInitial(java.util.LinkedHashMap::new);
                public static final java.util.concurrent.atomic.AtomicLong SSE_CONNECTIONS_ACTIVE =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong WS_CONNECTIONS_ACTIVE =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong SSE_EVENTS_SENT =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong WS_MESSAGES_RECEIVED =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong WS_MESSAGES_SENT =
                        new java.util.concurrent.atomic.AtomicLong();

                public static String kof_web_status(int code, String body) {
                    KOF_WEB_STATUS.set(code);
                    return body;
                }

                public static String kof_web_header_set(String name, String value) {
                    KOF_WEB_HEADERS.get().put(name, value);
                    return value;
                }

                private static String kof_web_status_text(int code) {
                    return switch (code) {
                        case 200 -> "OK";
                        case 201 -> "Created";
                        case 202 -> "Accepted";
                        case 204 -> "No Content";
                        case 301 -> "Moved Permanently";
                        case 302 -> "Found";
                        case 304 -> "Not Modified";
                        case 400 -> "Bad Request";
                        case 401 -> "Unauthorized";
                        case 403 -> "Forbidden";
                        case 404 -> "Not Found";
                        case 409 -> "Conflict";
                        case 422 -> "Unprocessable Entity";
                        case 429 -> "Too Many Requests";
                        case 500 -> "Internal Server Error";
                        case 502 -> "Bad Gateway";
                        case 503 -> "Service Unavailable";
                        default -> "OK";
                    };
                }

                public static String wsAccept(String secWebSocketKey) {
                    try {
                        String concat = secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
                        byte[] hash = sha1.digest(concat.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        return java.util.Base64.getEncoder().encodeToString(hash);
                    } catch (Exception e) {
                        throw new RuntimeException("SHA-1 unavailable on JVM", e);
                    }
                }

                public enum RouteKind { HTTP, SSE, WS }

                record WebDispatchResult(RouteKind kind, String response, WebRoute route, byte[] body) {
                    WebDispatchResult(RouteKind kind, String response, WebRoute route) {
                        this(kind, response, route, null);
                    }
                }

                public static final class WebRoute {
                    final String method;
                    final String[] segments;
                    final boolean[] params;
                    final Object handler;
                    final RouteKind kind;

                    WebRoute(RouteKind kind, String method, String path, Object handler) {
                        this.kind = kind;
                        this.method = method;
                        String[] raw = path.split("/");
                        this.segments = new String[raw.length];
                        this.params = new boolean[raw.length];
                        for (int i = 0; i < raw.length; i++) {
                            this.segments[i] = raw[i];
                            this.params[i] = raw[i].startsWith(":");
                        }
                        this.handler = handler;
                    }
                }

                public static final class WebRequest {
                    final String method;
                    final String path;
                    final String query;
                    final String rawHeaders;
                    final String body;
                    final String remoteAddr;
                    final boolean secure;
                    final java.util.Map<String, String> params = new java.util.HashMap<>();
                    final java.util.Map<String, String> queryParams = new java.util.HashMap<>();
                    final java.util.Map<String, String> headers = new java.util.HashMap<>();

                    WebRequest(String method, String path, String query, String rawHeaders, String body) {
                        this(method, path, query, rawHeaders, body, null, false);
                    }

                    WebRequest(String method, String path, String query, String rawHeaders, String body,
                            String remoteAddr, boolean secure) {
                        this.method = method;
                        this.path = path;
                        this.query = query;
                        this.rawHeaders = rawHeaders;
                        this.body = body;
                        this.remoteAddr = remoteAddr;
                        this.secure = secure;
                        if (!query.isEmpty()) {
                            for (String pair : query.split("&")) {
                                int eq = pair.indexOf('=');
                                if (eq < 0) queryParams.put(pair, "");
                                else queryParams.put(pair.substring(0, eq), pair.substring(eq + 1));
                            }
                        }
                        String[] lines = rawHeaders.split("\\r\\n");
                        for (int i = 1; i < lines.length; i++) {
                            int colon = lines[i].indexOf(':');
                            if (colon > 0) {
                                headers.put(lines[i].substring(0, colon).trim().toLowerCase(),
                                        lines[i].substring(colon + 1).trim());
                            }
                        }
                    }

                    String param(String name) {
                        return params.get(name);
                    }

                    String query(String name) {
                        return queryParams.get(name);
                    }

                    String header(String name) {
                        return headers.get(name.toLowerCase());
                    }
                }

                public static final class SseConnection implements SseSender {
                    private final java.io.OutputStream out;
                    private final byte[] nl = "\\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    private final java.util.concurrent.atomic.AtomicBoolean open =
                            new java.util.concurrent.atomic.AtomicBoolean(true);
                    private final java.util.concurrent.locks.ReentrantLock writeLock =
                            new java.util.concurrent.locks.ReentrantLock();

                    SseConnection(java.io.OutputStream out) {
                        this.out = out;
                    }

                    public void send(String data) {
                        if (!open.get()) return;
                        SSE_EVENTS_SENT.incrementAndGet();
                        writeData(data);
                    }

                    public void event(String name, String data) {
                        if (!open.get()) return;
                        SSE_EVENTS_SENT.incrementAndGet();
                        writeFrame("event: " + name + "\\n");
                        writeData(data);
                    }

                    public void close() {
                        if (open.compareAndSet(true, false)) {
                            writeLock.lock();
                            try {
                                out.flush();
                                out.close();
                            } catch (java.io.IOException ignored) {
                            } finally {
                                writeLock.unlock();
                            }
                        }
                    }

                    public boolean isOpen() {
                        return open.get();
                    }

                    private void writeData(String data) {
                        for (String line : data.split("\\n", -1)) {
                            writeFrame("data: " + line + "\\n");
                        }
                        writeFrame("\\n");
                    }

                    private void writeFrame(String frame) {
                        if (!open.get()) return;
                        writeLock.lock();
                        try {
                            out.write(frame.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            out.flush();
                        } catch (java.io.IOException e) {
                            open.set(false);
                        } finally {
                            writeLock.unlock();
                        }
                    }
                }

                public static final class WsFrame {
                    public final boolean fin;
                    public final int opcode;        // 0x1=TEXT, 0x2=BINARY, 0x8=CLOSE, 0x9=PING, 0xA=PONG, 0x0=continuation
                    public final byte[] payload;

                    WsFrame(boolean fin, int opcode, byte[] payload) {
                        this.fin = fin;
                        this.opcode = opcode;
                        this.payload = payload;
                    }

                    /**
                     * Encode a frame WITHOUT masking (server -> client per RFC 6455).
                     * FIN=1 by default; use overload for FIN=0.
                     */
                    public static byte[] encode(int opcode, byte[] payload, boolean fin) {
                        // Header byte 1: FIN(1) RSV(3) OPCODE(4)
                        int b1 = (fin ? 0x80 : 0x00) | (opcode & 0x0F);
                        // Header byte 2: MASK(1) + LEN(7)
                        long len = payload.length;
                        byte[] header;
                        int headerLen;
                        if (len <= 125) {
                            header = new byte[2];
                            header[1] = (byte) len;       // MASK=0
                            headerLen = 2;
                        } else if (len <= 0xFFFF) {
                            header = new byte[4];
                            header[1] = (byte) 126;       // MASK=0
                            header[2] = (byte) ((len >> 8) & 0xFF);
                            header[3] = (byte) (len & 0xFF);
                            headerLen = 4;
                        } else {
                            header = new byte[10];
                            header[1] = (byte) 127;       // MASK=0
                            for (int i = 0; i < 8; i++) {
                                header[2 + i] = (byte) ((len >> (56 - i * 8)) & 0xFF);
                            }
                            headerLen = 10;
                        }
                        header[0] = (byte) b1;
                        byte[] out = new byte[headerLen + (int) len];
                        System.arraycopy(header, 0, out, 0, headerLen);
                        System.arraycopy(payload, 0, out, headerLen, (int) len);
                        return out;
                    }

                    /**
                     * Decode a server-received (client -> server) frame. Payload is UNMASKED in
                     * the returned WsFrame. RFC 6455 requires client->server masking; we reject
                     * (close 1002) frames missing the mask bit.
                     */
                    public static WsFrame decodeClient(byte[] buf) throws java.io.IOException {
                        if (buf.length < 2) throw new java.io.IOException("frame too short: " + buf.length);
                        boolean fin = (buf[0] & 0x80) != 0;
                        int opcode = buf[0] & 0x0F;
                        boolean masked = (buf[1] & 0x80) != 0;
                        if (!masked) throw new java.io.IOException("client frame must be masked (RFC 6455 §5.1)");
                        long len = buf[1] & 0x7F;
                        int idx = 2;
                        if (len == 126) {
                            if (buf.length < 4) throw new java.io.IOException("frame truncated on extended length");
                            len = ((long)(buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
                            idx = 4;
                        } else if (len == 127) {
                            if (buf.length < 10) throw new java.io.IOException("frame truncated on extended length");
                            len = 0;
                            for (int i = 0; i < 8; i++) {
                                len = (len << 8) | (buf[2 + i] & 0xFF);
                            }
                            idx = 10;
                        }
                        if (len > maxFrameBytes.get()) {
                            throw new java.io.IOException("frame too large: " + len + " > " + maxFrameBytes.get());
                        }
                        if (buf.length < idx + 4 + len) throw new java.io.IOException("frame truncated (payload)");
                        byte[] mask = new byte[4];
                        System.arraycopy(buf, idx, mask, 0, 4);
                        idx += 4;
                        byte[] payload = new byte[(int) len];
                        for (long i = 0; i < len; i++) {
                            payload[(int) i] = (byte) (buf[idx + (int) i] ^ mask[(int) (i % 4)]);
                        }
                        return new WsFrame(fin, opcode, payload);
                    }

                    public static final java.util.concurrent.atomic.AtomicLong maxFrameBytes =
                            new java.util.concurrent.atomic.AtomicLong(1L << 20);        // 1 MiB
                    public static final java.util.concurrent.atomic.AtomicLong maxMessageBytes =
                            new java.util.concurrent.atomic.AtomicLong(8L << 20);     // 8 MiB
                    public static final int CLOSE_TOO_BIG = 1009;
                    public static final int CLOSE_PROTOCOL_ERROR = 1002;
                    public static final int CLOSE_UNSUPPORTED = 1003;
                }

                /** Interface para o KofRuntime acessar o envio WS sem ciclo de import. */
                public interface WsSender {
                    void sendText(String s);
                }

                /** Interface para o KofRuntime acessar o envio SSE sem ciclo de import. */
                public interface SseSender {
                    void send(String event);
                }

                public static final class WsConnection implements WsSender {
                    private final java.io.OutputStream out;
                    private final java.util.concurrent.locks.ReentrantLock writeLock =
                            new java.util.concurrent.locks.ReentrantLock();

                    WsConnection(java.io.OutputStream out) {
                        this.out = out;
                    }

                    public void sendText(String s) {
                        send(WsFrame.encode(0x1, s.getBytes(java.nio.charset.StandardCharsets.UTF_8), true));
                    }
                    public void sendBinary(byte[] payload) {
                        send(WsFrame.encode(0x2, payload, true));
                    }
                    public void ping(byte[] payload) {
                        if (payload.length > 125) throw new IllegalArgumentException("ping payload > 125");
                        send(WsFrame.encode(0x9, payload, true));
                    }
                    public void pong(byte[] payload) {
                        if (payload.length > 125) throw new IllegalArgumentException("pong payload > 125");
                        send(WsFrame.encode(0xA, payload, true));
                    }
                    public void close(int code, String reason) {
                        byte[] body = new byte[2 + (reason == null ? 0 : reason.length())];
                        body[0] = (byte) ((code >> 8) & 0xFF);
                        body[1] = (byte) (code & 0xFF);
                        if (reason != null) {
                            byte[] rb = reason.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            System.arraycopy(rb, 0, body, 2, rb.length);
                        }
                        send(WsFrame.encode(0x8, body, true));
                    }

                    private void send(byte[] frame) {
                        writeLock.lock();
                        try {
                            out.write(frame);
                            out.flush();
                        } catch (java.io.IOException e) {
                            // close silently
                        } finally {
                            writeLock.unlock();
                        }
                    }
                }

                public static final class WebApp {
                    final String id;
                    final java.util.List<WebRoute> routes = new java.util.ArrayList<>();
                    final java.util.List<Object> middlewares = new java.util.ArrayList<>();
                    final java.util.List<StaticDir> staticDirs = new java.util.ArrayList<>();
                    final java.util.List<String> healthPaths = new java.util.ArrayList<>();
                    // D-SEC C18: middleware composto registrado por
                    // app.security() — guardado para o aviso de produção no
                    // listen e para o dispatch não reexecutar a cadeia.
                    volatile SecurityMiddleware security;
                    final java.util.concurrent.atomic.AtomicInteger activeConnections =
                            new java.util.concurrent.atomic.AtomicInteger();
                    public static final int DEFAULT_MAX_CONNECTIONS = 1024;
                    volatile int maxConnections = DEFAULT_MAX_CONNECTIONS;
                    volatile java.net.ServerSocket serverSocket;
                    volatile boolean running;

                    WebApp(String id) {
                        this.id = id;
                    }

                    /** Diretório de arquivos estáticos servido sob um prefixo
                     *  de URL (app.serveDir("/img", "assets")) — content-type
                     *  derivado da extensão, sem o app colar base64 em String. */
                    public static final class StaticDir {
                        final String prefix;
                        final java.nio.file.Path dir;
                        StaticDir(String prefix, java.nio.file.Path dir) {
                            this.prefix = prefix;
                            this.dir = dir;
                        }
                    }
                }

                /**
                 * D-SEC C18: middleware composto de segurança. Aplicado na
                 * ordem FIXA rate-limit → cors → headers → cookies/session →
                 * csrf → auth → RBAC → rota. `invoke(...)` devolve {@code null}
                 * para continuar a cadeia ou uma String para responder (com o
                 * status em {@code KOF_WEB_STATUS}). Headers de resposta são
                 * acumulados em {@code KOF_SEC_RESPONSE_HEADERS} e sobrevivem
                 * ao clear do dispatch antes da rota.
                 */
                public static final class SecurityMiddleware {
                    boolean headers = true;
                    volatile String cors;
                    volatile int rateLimit;
                    volatile int rateWindow;
                    volatile boolean csrf;
                    volatile boolean requireAuth;
                    final java.util.List<String> roles = new java.util.concurrent.CopyOnWriteArrayList<>();

                    public Object invoke(String method, String path, String body,
                            String query, String rawHeaders) {
                        WebRequest req = KOF_WEB_REQUEST.get();
                        // 1. rate-limit (por IP remoto; off por default)
                        if (rateLimit > 0 && rateWindow > 0) {
                            String key = req != null ? req.remoteAddr : null;
                            if (key == null || key.isEmpty()) key = "anonymous";
                            if (!kof_sec_rate_limit(key, rateLimit, rateWindow)) {
                                kof_web_sec_header("Retry-After", String.valueOf(rateWindow));
                                KOF_WEB_STATUS.set(429);
                                return "{\\"error\\":\\"too many requests\\"}";
                            }
                        }
                        // 2. CORS (off por default; só age se houver Origin)
                        String corsPolicy = cors;
                        if (corsPolicy != null && req != null) {
                            String origin = req.header("origin");
                            if (origin != null) {
                                if (!kof_sec_cors_allowed(origin, corsPolicy)) {
                                    KOF_WEB_STATUS.set(403);
                                    return "{\\"error\\":\\"cors origin denied\\"}";
                                }
                                kof_web_sec_header("Access-Control-Allow-Origin",
                                        corsPolicy.contains("*") ? "*" : origin);
                                kof_web_sec_header("Vary", "Origin");
                                if ("OPTIONS".equalsIgnoreCase(method)
                                        && req.header("access-control-request-method") != null) {
                                    kof_web_sec_header("Access-Control-Allow-Methods",
                                            "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                                    String acrh = req.header("access-control-request-headers");
                                    kof_web_sec_header("Access-Control-Allow-Headers",
                                            acrh != null ? acrh
                                                    : "Content-Type, Authorization, X-CSRF-Token");
                                    kof_web_sec_header("Access-Control-Max-Age", "600");
                                    KOF_WEB_STATUS.set(204);
                                    return "";
                                }
                            }
                        }
                        // 3. headers de hardening (on por default)
                        if (headers) {
                            kof_web_sec_header("Content-Security-Policy", kof_sec_csp_header());
                            kof_web_sec_header("X-Content-Type-Options",
                                    kof_sec_content_type_options_header());
                            kof_web_sec_header("X-Frame-Options", kof_sec_frame_header());
                            kof_web_sec_header("Referrer-Policy", kof_sec_referrer_header());
                            if (req != null && req.secure) {
                                kof_web_sec_header("Strict-Transport-Security",
                                        kof_sec_hsts_header());
                            }
                        }
                        // 4. csrf double-submit cookie (off por default)
                        if (csrf && req != null) {
                            boolean safe = "GET".equalsIgnoreCase(method)
                                    || "HEAD".equalsIgnoreCase(method)
                                    || "OPTIONS".equalsIgnoreCase(method);
                            String cookieToken =
                                    kof_sec_cookie_get(req.header("cookie"), "csrf");
                            if (safe) {
                                if (cookieToken.isEmpty()) {
                                    kof_web_sec_header("Set-Cookie",
                                            "csrf=" + kof_sec_random_hex(32)
                                            + "; Path=/; SameSite=Lax"
                                            + (req.secure ? "; Secure" : ""));
                                }
                            } else {
                                String headerToken = req.header("x-csrf-token");
                                if (cookieToken.isEmpty() || headerToken == null
                                        || !kof_sec_constant_time_equals(cookieToken, headerToken)) {
                                    KOF_WEB_STATUS.set(403);
                                    return "{\\"error\\":\\"csrf token invalid\\"}";
                                }
                            }
                        }
                        // 5. auth: exige token válido se `requireAuth`; se
                        //    Authorization estiver presente, um token inválido
                        //    NUNCA passa (auth-if-present).
                        if (req != null) {
                            String authHeader = req.header("authorization");
                            boolean authenticated = kof_sec_auth_authenticated();
                            if (requireAuth && !authenticated) {
                                kof_web_sec_header("WWW-Authenticate", "Bearer");
                                KOF_WEB_STATUS.set(401);
                                return "{\\"error\\":\\"unauthorized\\"}";
                            }
                            if (!requireAuth && authHeader != null && !authenticated) {
                                kof_web_sec_header("WWW-Authenticate", "Bearer");
                                KOF_WEB_STATUS.set(401);
                                return "{\\"error\\":\\"invalid credentials\\"}";
                            }
                            // 6. RBAC (roles exigidas; implica auth)
                            if (!roles.isEmpty()) {
                                if (!authenticated) {
                                    kof_web_sec_header("WWW-Authenticate", "Bearer");
                                    KOF_WEB_STATUS.set(401);
                                    return "{\\"error\\":\\"unauthorized\\"}";
                                }
                                for (String role : roles) {
                                    if (!kof_sec_auth_has_role(role)) {
                                        KOF_WEB_STATUS.set(403);
                                        return "{\\"error\\":\\"forbidden\\"}";
                                    }
                                }
                            }
                        }
                        return null;
                    }
                }

                private static void kof_web_sec_header(String name, String value) {
                    if (value != null) KOF_SEC_RESPONSE_HEADERS.get().put(name, value);
                }

                public static String kof_web_app_new() {
                    String id = "app" + KOF_WEB_SEQ.incrementAndGet();
                    KOF_WEB_APPS.put(id, new WebApp(id));
                    return id;
                }

                private static WebApp kof_web_app(String appId) {
                    WebApp app = KOF_WEB_APPS.get(appId);
                    if (app == null) throw new IllegalArgumentException("unknown web app: " + appId);
                    return app;
                }

                public static void kof_web_configure(String appId, String key, Object value) {
                    WebApp app = kof_web_app(appId);
                    switch (key) {
                        case "maxConnections" -> app.maxConnections = ((Number) value).intValue();
                        case "maxFrameBytes" -> WsFrame.maxFrameBytes.set(((Number) value).longValue());
                        case "maxMessageBytes" -> WsFrame.maxMessageBytes.set(((Number) value).longValue());
                        case "idleMs" -> KofRuntime.idleMs.set(((Number) value).intValue());
                        default -> throw new IllegalArgumentException("unknown config key: " + key);
                    }
                }

                public static void kof_web_configure(String appId, String key, int value) {
                    kof_web_configure(appId, key, (Object) value);
                }

                public static String kof_web_stats(String name) {
                    return switch (name) {
                        case "SSE_CONNECTIONS_ACTIVE" -> String.valueOf(SSE_CONNECTIONS_ACTIVE.get());
                        case "WS_CONNECTIONS_ACTIVE" -> String.valueOf(WS_CONNECTIONS_ACTIVE.get());
                        case "SSE_EVENTS_SENT" -> String.valueOf(SSE_EVENTS_SENT.get());
                        case "WS_MESSAGES_RECEIVED" -> String.valueOf(WS_MESSAGES_RECEIVED.get());
                        case "WS_MESSAGES_SENT" -> String.valueOf(WS_MESSAGES_SENT.get());
                        default -> throw new IllegalArgumentException("unknown web counter: " + name);
                    };
                }

                public static void kof_web_route(String appId, String method, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    String m = method.toUpperCase();
                    if ("SSE".equals(m) || "WS".equals(m)) {
                        throw new IllegalArgumentException(
                                "route method " + m + " requires kof_web_sse_route/kof_web_ws_route");
                    }
                    kof_web_app(appId).routes.add(new WebRoute(RouteKind.HTTP, m, path, handler));
                }

                public static void kof_web_sse_route(String appId, String method, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    kof_web_app(appId).routes.add(
                            new WebRoute(RouteKind.SSE, method.toUpperCase(), path, handler));
                }

                public static void kof_web_ws_route(String appId, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    kof_web_app(appId).routes.add(
                            new WebRoute(RouteKind.WS, "WS", path, handler));
                }

                public static void kof_web_use(String appId, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("middleware is null");
                    kof_web_app(appId).middlewares.add(handler);
                }

                // D-SEC C18: `app.security()` — middleware composto. Sem opts,
                // defaults seguros (headers hardening). Opts documentados em
                // docs/stdlib/stdlib-web.md: headers (Bool), cors (String),
                // rateLimit ("limite/janelaSeg"), csrf (Bool), auth (Bool),
                // roles (String CSV ou List).
                public static void kof_web_security(String appId) {
                    kof_web_security_opts(appId, null);
                }

                public static void kof_web_security_opts(String appId, java.util.Map<?, ?> opts) {
                    WebApp app = kof_web_app(appId);
                    SecurityMiddleware sec = app.security;
                    if (sec == null) {
                        sec = new SecurityMiddleware();
                        app.security = sec;
                        app.middlewares.add(sec);
                    }
                    if (opts == null) return;
                    Object headers = opts.get("headers");
                    if (headers != null) sec.headers = kof_web_sec_bool(headers);
                    Object cors = opts.get("cors");
                    if (cors != null) sec.cors = String.valueOf(cors);
                    Object rate = opts.get("rateLimit");
                    if (rate != null) {
                        String spec = String.valueOf(rate);
                        int slash = spec.indexOf('/');
                        if (slash <= 0) {
                            throw new IllegalArgumentException(
                                    "rateLimit must be \\"limit/windowSeconds\\", got: " + spec);
                        }
                        sec.rateLimit = Integer.parseInt(spec.substring(0, slash).trim());
                        sec.rateWindow = Integer.parseInt(spec.substring(slash + 1).trim());
                    }
                    Object csrf = opts.get("csrf");
                    if (csrf != null) sec.csrf = kof_web_sec_bool(csrf);
                    Object auth = opts.get("auth");
                    if (auth != null) sec.requireAuth = kof_web_sec_bool(auth);
                    Object roles = opts.get("roles");
                    if (roles instanceof java.util.List<?> list) {
                        for (Object r : list) {
                            if (r != null) sec.roles.add(String.valueOf(r));
                        }
                    } else if (roles != null) {
                        for (String r : String.valueOf(roles).split(",")) {
                            if (!r.trim().isEmpty()) sec.roles.add(r.trim());
                        }
                    }
                }

                private static boolean kof_web_sec_bool(Object value) {
                    if (value instanceof Boolean b) return b;
                    String s = String.valueOf(value);
                    return !("false".equalsIgnoreCase(s) || "0".equals(s));
                }

                public static int kof_web_port(String appId) {
                    java.net.ServerSocket ss = kof_web_app(appId).serverSocket;
                    return ss == null ? -1 : ss.getLocalPort();
                }

                /**
                 * D-SEC C18: security by default — `listen` em produção
                 * (KOF_ENV=production) exige `app.security()` explícito; sem
                 * ele, avisa (nunca falha silenciosamente).
                 */
                private static void kof_web_warn_security_default(WebApp app) {
                    if (app.security == null
                            && "production".equalsIgnoreCase(System.getenv("KOF_ENV"))) {
                        System.err.println("kof.web: production mode without app.security() — "
                                + "no security middleware configured (D-SEC C18)");
                    }
                }

                public static void kof_web_close(String appId) {
                    WebApp app = kof_web_app(appId);
                    app.running = false;
                    if (app.serverSocket != null) {
                        try {
                            app.serverSocket.close();
                        } catch (java.io.IOException ignored) {
                        }
                    }
                }

""";
    }
}
