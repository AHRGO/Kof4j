[English](stdlib-web.md) | [Português](stdlib-web.pt_BR.md)

# stdlib web — Kof's Native Web Stack

**Last updated:** September 4, 2026
**Version:** 0.4.0-beta (`kof.http` JVM+JS + retry/circuit; WebSocket/SSE JVM + hardening)
**Status:** implemented (Phase 1 of the Spring independence plan) — `kof serve` + `kof.http` JVM+JS + `app.ws`/`app.sse` JVM + limits/counters

---

## 1. Philosophy

> A Kof web application does not need Spring. HTTP, routes, JSON, request
> context and middleware are part of the Kof ecosystem.

No external dependency: the HTTP server is generated inside the JVM runtime of
the compiled program itself (`dev.kof.runtime.KofRuntime`). No servlet
container, no Spring MVC, no annotations.

## 2. Complete example

```kof
record User(String name, Int age)

main() {
    var app = web.app()

    // Middleware: returns null to continue; String to respond directly
    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }

    app.get("/hello") {
        return "Hello from Kof"
    }

    // Path parameter + query string
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }

    app.get("/agent") {
        return "agent=" + header("user-agent")
    }

    app.get("/me") {
        return method() + " " + path()
    }

    // Request body
    app.post("/echo") {
        return "got:" + body()
    }

    // End-to-end typed JSON
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }

    app.listen(8080)
}
```

```bash
kof serve app.kf              # compiles and runs (the app calls app.listen)
kof run app.kf                # same — the program starts its own server
```

## 3. API

### `web.app()`

Creates an application. The returned value (`kof.web.App`) is a handle; at
runtime it is an internal registry identifier.

### Routes

| Call | HTTP method |
|---------|-------------|
| `app.get(path) { ... }` | GET |
| `app.post(path) { ... }` | POST |
| `app.put(path) { ... }` | PUT |
| `app.delete(path) { ... }` | DELETE |
| `app.patch(path) { ... }` | PATCH |
| `app.options(path) { ... }` | OPTIONS |

The body `{ ... }` is a trailing lambda — the route handler. A handler can
also be passed explicitly: `app.get("/x", handler)`.

- `path` supports segments with a parameter: `/users/:id` (`:` prefix).
- The handler returns `String` (response body, 200) or `null` (404).
- The response detects JSON automatically when the body starts with `{` or `[`
  (`Content-Type: application/json`).

### Middleware

`app.use { ... }` registers a middleware executed before routing.
Return `null` → continues; return `String` → immediate response (200).

### Server

### Segurança (`app.security()`) — D-SEC C18 (14/09)

| Chamada | Descrição |
|---------|-----------|
| `app.security()` | Middleware composto com defaults seguros (headers de hardening) |
| `app.security(opts)` | Idem, com overrides via `Map` |

Aplica a **ordem fixa** rate-limit → CORS → headers → cookies/session → csrf →
auth → RBAC → rota (D-SEC). Substitui a cadeia manual de `app.use`.

Sem argumentos, liga os **headers de hardening** (sempre seguros) e o **CSRF**
para métodos que mudam estado:

- `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; base-uri 'self'`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- `Strict-Transport-Security` — só sob TLS (`listenSecure`)

Opts documentados (chaves do `Map`; qualquer outra é ignorada):

| Chave | Tipo | Default | Efeito |
|-------|------|---------|--------|
| `headers` | `Bool` | `true` | Liga/desliga os headers acima |
| `cors` / `corsOrigin` | `String` | off | Origem permitida, CSV ou `*`. Origem não listada → 403; preflight `OPTIONS` → 204 |
| `rateLimit` | `String` ou `Number` | off | `"limite/janelaSegundos"` (ex.: `"100/60"`) ou só o limite. Por IP remoto; excedeu → 429 + `Retry-After` |
| `csrf` | `Bool` | `true` | Double-submit cookie: emite `csrf` (SameSite=Lax) em métodos seguros; exige `X-CSRF-Token` casando com o cookie em POST/PUT/PATCH/DELETE, senão 403. `csrf:false` desliga |
| `sessionHeader` | `String` | off | Nome do header de sessão. Fora dos `publicPaths`, **toda** request (GET incluído) exige sessão válida; ausente/inválida → 401 |
| `publicPaths` / `permitAll` | `String` CSV | — | Allow-list de matchers públicos (ex.: `"/register,/login"`); todo o resto exige autenticação |
| `auth` | `Bool` | `false` | Exige `Authorization: Bearer` JWT válido (secret via `auth.secret`); ausente/inválido → 401 + `WWW-Authenticate` |
| `roles` | `String` CSV ou `List` | — | Exige todas as roles (claims `roles`); falta → 403 (implica auth) |

**Auth-if-present:** mesmo sem `auth: true`, uma request que **traz**
`Authorization` com token inválido nunca passa (401) — evita "token ruim vira
anônimo".

```kof
main() {
    auth.secret(secrets.get("JWT_SECRET", "dev"))
    var app = web.app()
    var o = mapOf()
    o.put("cors", "https://app.example")
    o.put("rateLimit", "100/60")
    o.put("auth", true)
    o.put("roles", "admin")
    app.security(o)
    app.get("/admin") { return "ok" }
    app.listen(8080)
}
```

**Security by default:** `listen`/`listenSecure` com `KOF_ENV=production` sem
`app.security()` avisa em `stderr` (nunca falha silenciosamente).

**JVM-only** — Native/JS reportam `WEB006` (gap honesto, mesmo precedente
`WEB002`/`WEB005`).

### Servidor

| Call | Description |
|---------|-----------|
| `app.listen(port)` | Starts the server (blocking) on `0.0.0.0` |
| `app.listenSecure(port)` | Same, with TLS (JVM; self-signed `keytool` + `SSLServerSocket`) |
| `app.port()` | Port actually bound (useful with `listen(0)`) |
| `app.close()` | Shuts down the server (graceful shutdown) |

`app.listen(0)` binds an ephemeral port; `app.port()` reveals the real port.
`app.listenSecure` is available on the JVM (Native/JS `WEB002`).

| `app.listen(port)` | Inicia o servidor (bloqueante) em `0.0.0.0` |
| `app.listenSecure(port)` | Idem, com TLS self-signed de dev (JVM; `keytool` + `SSLServerSocket`) |
| `app.listenSecure(port, certPem, keyPem)` | TLS com **certificado próprio** (PKCS#8 PEM) — produção (JVM) |
| `app.port()` | Porta efetivamente vinculada (útil com `listen(0)`) |
| `app.close()` | Encerra o servidor (graceful shutdown) |

`app.listen(0)` vincula uma porta efêmera; `app.port()` revela a porta real.
`app.listenSecure` está disponível no JVM (Native/JS `WEB002`). A variante de
3 args usa o par cert/chave do usuário (`-----BEGIN CERTIFICATE-----` /
`-----BEGIN PRIVATE KEY-----`, chave PKCS#8 RSA/EC/DSA); o self-signed de 1
arg continua como conveniência de dev, não de produção (D-SEC).

### Static files (`app.serveDir`) (31/08)

| Call | Description |
|---------|-----------|
| `app.serveDir(prefix, dir)` | Serves the files from `dir` under `prefix` (fallback after dynamic routes) |

The handler returns the **binary file** from disk with `Content-Type` by
extension (HTML/CSS/JS, images, audio, **video**, fonts, PDF...),
`Cache-Control` and protection against path traversal (`..`). It is the
alternative to pasting base64/HTML/CSS into a `String` literal in the source —
the app handles the FILE.

**Range requests**: `serveDir` answers `Range: bytes=...` with `206 Partial
Content` + `Content-Range` + `Accept-Ranges: bytes` (and `416` for an invalid
range). This is what allows `<video>`/`<audio>` to navigate and seek in the
browser — without Range, the player cannot position in the middle of the file.

```kof
var app = web.app()
app.serveDir("/media", "assets")   // GET /media/clip.mp4 → bytes + Range 206
app.listen(8080)
```

```html
<video src="/media/clip.mp4" controls></video>
```

Relative paths of the app resolve against the project root
(`-Dkof.root`, set by the `run`/`serve` CLI). **JVM-only** — Native/JS
report `WEB005` (documented gap).

### Health (`app.health`) (01/09)

| Call | Description |
|---------|-----------|
| `app.health(path)` | Registers a built-in health endpoint (e.g.: `/health`) |

`app.health("/health")` responds with the app state in JSON
(`{"status":"UP","ready":true,"alive":true}` — value of
`observability.health()/readiness()/liveness()`) **before the middlewares**:
load balancer/health-check probes do not go through auth/middleware. The app
can also mount its own: `app.get("/health") { return
observability.health() }`.

```kof
var app = web.app()
app.health("/health")   // GET /health → {"status":"UP","ready":true,"alive":true}
app.listen(8080)
```

### WebSocket (`app.ws`, RFC 6455) (30/08)

| Call | Description |
|---------|-----------|
| `app.ws(path) { ... }` | WebSocket route (route kind `WS`) |
| `wsMessage()` | Text of the `TEXT` message that triggered the handler (String) |
| `wsSend(text)` | Sends a `TEXT` frame back over the current connection |

The RFC 6455 handshake and the frame codec (with client→server masking) are
implemented inside the generated HTTP engine; the Kof handler is called per
`TEXT` message. The runtime also handles `PING`→`PONG`, `CLOSE` (ack) and
discards frames above the configurable frame limit (default 1 MiB, close
`1009`).

```kof
app.ws("/chat") {
    var m = wsMessage()
    if (m == "bye") {
        return
    }
    wsSend("echo: " + m)
}
```

### Server-Sent Events (`app.sse`) (30/08)

| Call | Description |
|---------|-----------|
| `app.sse(path) { ... }` | SSE route (route kind `SSE`); the handler receives the sender as the `sse` parameter |
| `sse.send(data)` | Unnamed event (`data: ...`) |
| `sse.event(name, data)` | Named event (`event: name\ndata: ...`) |
| `sse.close()` | Ends the client stream |
| `sse.isOpen()` | `Bool` — the stream is still open |

On the **JS** host (Graal) SSE is **handler-scoped**: events are written during
the handler body and the stream closes when the handler returns — the pump is
single-thread, so post-return push and multiple concurrent clients are the
`WEB003` residual there. On the JVM each SSE connection is independent
(ThreadLocal per connection). In both, the headers
`Content-Type: text/event-stream`, `Cache-Control: no-cache`,
`Connection: keep-alive` and `X-Accel-Buffering: no` are emitted.

```kof
app.sse("/events") {
    sse.send("one")
    sse.event("tick", "two")
    sse.close()
}
```

`app.ws` is available on the **JVM**; `app.sse` works on the JVM and (since
16/09, handler-scoped) on **JS**. On other targets / the JS residual they are
compile-time documented gaps: WebSocket → `WEB004`, SSE → `WEB003` (Native).

### Limits and observability (`app.configure`, `app.stats`)

| Call | Description |
|---------|-----------|
| `app.configure("maxConnections", n)` | Cap on concurrent connections (default `1024`); above that it responds `503` |
| `app.configure("maxFrameBytes", n)` | Mutable WebSocket frame limit (default `1 MiB`) |
| `app.configure("maxMessageBytes", n)` | Mutable WebSocket message limit (default `8 MiB`) |
| `app.configure("idleMs", n)` | Idle timeout applied to WebSocket and SSE deadline |
| `stats("SSE_CONNECTIONS_ACTIVE")` | Active SSE connections |
| `stats("WS_CONNECTIONS_ACTIVE")` | Active WebSocket connections |
| `stats("SSE_EVENTS_SENT")` | SSE events sent |
| `stats("WS_MESSAGES_RECEIVED")` / `stats("WS_MESSAGES_SENT")` | WS messages received/sent |

`app.configure` acts on the current app (per handle); the statistics are
global per JVM and returned as `String`.

### Request context (inside handlers/middleware)

| Function | Returns |
|--------|---------|
| `param("id")` | Path parameter (`String` — only a matched route reaches the handler) |
| `query("name")` | Query parameter (`String?` — `null` if absent; narrow before deref) |
| `header("x-auth")` | Case-insensitive header (`String?` — `null` if absent; narrow before deref) |
| `body()` | Raw request body |
| `method()` | HTTP method ("GET", "POST", ...) |
| `path()` | Request path |
| `status(code, body)` | Sets the response status and returns the body — use as a return (e.g.: `return status(201, "{\"ok\":true}")`) |
| `headerSet(name, value)` | Adds a response header (e.g.: `headerSet("X-Total", "42")`) |

The context is per-request (ThreadLocal at runtime) — handlers can be
concurrent without shared state. `status(code, body)` and
`headerSet(name, value)` allow rich responses (custom status + headers) —
previously handlers only produced automatic 200/404.

## 4. Concurrency

Each connection is handled on a virtual thread (JVM). The programmer writes
synchronous handlers; the runtime decides the strategy. SSE handlers run on
the shared `KOF_SSE_HANDLERS` and have a deadline of `idleMs * 4`; on timeout
the stream is closed and the task cancelled.

## 5. Current limitations (Phase 1, 0.4.0-beta)

- The `js` target supports the web stack base (`web.app()` + routes + context-fns with runtime: param/query/header/body/method/path/status/headerSet — `WEB001` fatia honestidade 16/09) plus **SSE handler-scoped** (`app.sse` + `sse.send/event/close/isOpen` + `sse()`, framing/headers idem JVM — 16/09); residual gaps: SSE push after the handler returns and multiple concurrent clients (`WEB003`), `app.ws` (`WEB004`) and `stats()` report at compile-time; `kof.http` already works on JS via `Java HttpClient`.
- The `native` target (`x86_64`/`riscv64`/`aarch64`) has had the web server base since 03/09 (`NativeWebRuntime.java`: accept/route/lambda/body, `KofWebNativeE2ETest` 4/4); residual: TLS `WEB002`, ws `WEB004`, sse `WEB003`, path params/keep-alive/`status()`/`headerSet()` `WEB001`.
- `app.ws` is JVM-only (Native/JS → `WEB004` at compile-time). `app.sse` is JVM + JS handler-scoped (16/09; Native `WEB003`, JS post-return push `WEB003` residual).
- `app.serveDir` (static files + Range 206/416) is JVM-only (Native/JS `WEB005`).
- PR6 hardening (connection cap, `maxFrameBytes`/`maxMessageBytes` limits,
  `idleMs`, `app.stats`) is JVM; backpressure and fragmentation remain follow-up.
- `kof.http` client — ✅ JVM+JS (27/08; `timeout/retry/circuit` in parity 30/08), Native ✅ (asm HTTP/1.1; configurators `timeout/retry/circuit` are silent no-ops — see §259; HTTP002 branch is dead).
- Middleware/routes for HTTP methods other than those listed: in the future.

> Closed in this phase (27–30/08): status codes + custom headers
> (`status(code, body)` / `headerSet(name, value)`); `kof.cache` on the 3
> targets; `WebSocket` (`app.ws`) + `SSE` (`app.sse`) on the JVM; `http.retry`/
> `http.circuit` in JVM+JS parity.

## 6. Tests (0.4.0-beta)

`KofWebE2ETest` 10 + `KofHttpServerTest` 8 + `KofHttpE2ETest` 4 (JVM+JS,
27/08) + `KofWebTlsTest` 5 + `KofWebSseE2ETest` 7 + `KofWebWsE2ETest` 11 +
`KofWebStreamE2ETest` 4 + `KofWsFrameTest` 7 + `KofHttpResilienceE2ETest` 3 +
`KofWebHardeningTest` 6 —
each test compiles a Kof program, runs the bytecode/JS as a subprocess and
exercises the server/client with real sockets (routing, path params, query,
headers, body, JSON round-trip, middleware, 404, multiple routes with trailing
lambda, `http.get/post/put/delete` + TLS + `retry`/`circuit`, RFC 6455
WebSocket handshake, frame codec with masking, named/multi-line SSE events,
concurrent WS/SSE streaming).

## 7. Architecture

```
Kof source (.kf)
   ↓ CompilerDriver
Kof IR (KofCall kof_web_*)
   ↓ JvmBackend
JVM bytecode
   ↓
dev.kof.runtime.KofRuntime (generated)  ← HTTP engine embedded in the program
   ├── KOF_WEB_APPS (app registry)
   ├── WebRoute (method, segments, params, handler, kind)
   ├── SseConnection / WsConnection / WsFrame
   ├── WebRequest (method, path, query, headers, body)
   └── accept loop (virtual threads) + dispatch
```

The `kof_web_*` calls are resolved at compile-time by the `KofWeb` table
(a dance analogous to `KofIo`): the programmer never sees threads, sockets or
HTTP parsing.

## 8. References

- Plan: `docs/development/DECISIONS.md` §D-SPRING (Phase 1)
- Status: `docs/status.md`
- Roadmap: `docs/development/roadmap.md` (Phase 3 — Web Platform)
