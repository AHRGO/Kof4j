package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.web D-SEC C18 (middleware composto app.security()) — parte 3/3 de
 * JvmWebRuntime. Concatenacao preserva ordem e conteudo byte-a-byte.
 */
public final class JvmWebSecurityRuntime {

    private JvmWebSecurityRuntime() {}

    static String source() {
        return """
                // D-SEC C18: headers de resposta do middleware de security
                // (CSP/HSTS/CORS/Set-Cookie) — separados de KOF_WEB_HEADERS
                // porque o dispatch limpa estes ANTES de invocar a rota; estes
                // sobrevivem ate o build da resposta.
                private static final ThreadLocal<java.util.Map<String, String>> KOF_SEC_RESPONSE_HEADERS =
                        ThreadLocal.withInitial(java.util.LinkedHashMap::new);

                /**
                 * D-SEC C18: middleware composto de seguranca. Aplicado na
                 * ordem FIXA rate-limit -> cors -> headers -> cookies/session
                 * -> csrf -> auth -> RBAC -> rota. `invoke(...)` devolve
                 * {@code null} para continuar a cadeia ou uma String para
                 * responder (com o status em {@code KOF_WEB_STATUS}). Headers
                 * de resposta sao acumulados em {@code KOF_SEC_RESPONSE_HEADERS}
                 * e sobrevivem ao clear do dispatch antes da rota.
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
                        // 2. CORS (off por default; so age se houver Origin)
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
                        // 5. auth: exige token valido se `requireAuth`; se
                        //    Authorization estiver presente, um token invalido
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

                /**
                 * D-SEC C18: security by default — `listen` em producao
                 * (KOF_ENV=production) exige `app.security()` explicito; sem
                 * ele, avisa (nunca falha silenciosamente).
                 */
                private static void kof_web_warn_security_default(WebApp app) {
                    if (app.security == null
                            && "production".equalsIgnoreCase(System.getenv("KOF_ENV"))) {
                        System.err.println("kof.web: production mode without app.security() — "
                                + "no security middleware configured (D-SEC C18)");
                    }
                }

""";
    }
}
