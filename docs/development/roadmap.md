[English](roadmap.md) | [Português](roadmap.pt_BR.md)

# Kof — Long-Term Roadmap

**Last updated:** September 13, 2026 (plan merger: §23 = the SINGLE
implementation plan (ex-`ACTION_PLAN`+`IMPLEMENTATION_PLAN`); migration
cluster consolidated — `LEGACY_IR`+`DIFFERENTIAL_TESTING` merged into
`LEGACY_MIGRATION.md`)
**Version:** 0.4.0-beta (active branch `beta-0.4.0`)

---

## Philosophy

Kof must radically simplify modern development without sacrificing power, performance, security, or interoperability.

Principles:

- abstract recurring complexity;
- keep code extremely short and readable;
- offer native language/runtime APIs;
- keep compatibility with the existing Java ecosystem;
- avoid reinventing Java libraries for aesthetics alone;
- allow Kof to offer a modern experience without forcing the user to depend on external frameworks;
- place complexity in the implementation/runtime/compiler, not in the application code;
- preserve architectural freedom;
- allow monoliths, modularization, and later microservices without rewriting the entire application.

---

## 1. Platform Targets

### KofAndroid — Android

Kof compiled for Android applications (APK/AAB). Full design in
[docs/targets/KOFANDROID.md](../targets/KOFANDROID.md).

Objectives:
- same code, same intention: `Window(...)` opens a real app;
- reuse of the JVM backend (bytecode → dex) — there is no alternative codegen;
- `kof.ui` via a synthesized WebView host (same KofJS layer as desktop);
- direct interop with `android.*` via ExternalClasspath
  (`extends Activity`, `super.onCreate`, androidx annotations);
- honest gaps at compile-time (`AND001..003`).

Current state: 🟡 Phase 1 implemented — `kof build --target android` generates a
Maven project (zero Java/Kotlin/Gradle) with an Activity host IN KOF
(`android-host.kf`) compiled by the frontend itself; d8/aapt2/apksigner
pipeline via pom without dependencies.

### Kof4J — JVM

Kof compiled to JVM/bytecode.

Objectives:
- maximum compatibility with Java;
- access to Java libraries;
- compatibility with Maven/the existing ecosystem;
- execution as a JAR;
- possibility of using legacy frameworks such as Spring, Hibernate, etc.;
- primary backend during the initial consolidation.

Current state: ✅ stable (JVM V21, ASM, virtual threads, 819 tests 02/09;
native web stack with WebSocket/SSE, limits/counters and `kof.http`
retry/circuit — 30/08-04/09)

### KofNative — Native Binary

Kof compiled directly to native/binary code.

Objectives:
- ELF/PE/Mach-O depending on the platform;
- low consumption;
- extremely fast startup;
- possibility of servers without a JVM;
- native Kof runtime;
- reuse of the same language semantics;
- same application being compilable to JVM or Native.

Current state: ✅ stable x86_64 (free-list `kof_free_head` with mmap reuse; GC
mark-sweep pending — auto-GC disabled after a hang, memory returned only in the
`munmap` fallback; `spawn`/`await` via `pthread_create` + trampoline +
`pthread_join` with thread-safe allocator (futex) — 31/08; real FP in XMM —
FLT001; JSON objects/records + FP arrays — JSN001/002/003; native SQLite `.so`
direct; MySQL wire protocol WIP) + `native.risc` (riscv64: real codegen 02/09 — pure asm + qemu, NATIVE002
partial) + `native.arm` (aarch64: inherits from riscv via translator — `NativeArchEmitter.emitAarch64`, 39/39 E2E under qemu) *(synced 12/09: the line "codegen still placeholder" rotted — `NativeAarch64E2ETest` runs under qemu where there is a toolchain; honest guard skips on a host without cross)*

### KofJS — Web

Kof running on the server side/compiling to web applications.

KofJS must NOT be treated simply as "Kof that turns into JavaScript".

The vision is to generate modern frontend in a declarative and minimalist way:

```kof
page Home {
    column {
        text("Olá")
        button("Entrar") {
            login()
        }
    }
}
```

The intention is similar to Flutter's philosophy:
- declarative UI;
- components;
- composition;
- state;
- events;
- layouts;
- little verbosity;
- optimized HTML/CSS/JS generation.

Current state: 🟡 alpha — pipeline `.kf → Kof IR → KofJS → .mjs` functional with
execution on Kof's own embedded JS engine (no Node.js). Classes,
inheritance, List `map/filter/reduce`, String API, JSON, exceptions, pattern matching
`case String s` + `Point(x,y)`, basic `String?`, `kof.time`/`kof.io`/`kof.http` (via `Java HttpClient` interop + fetch fallback; retry/circuit at parity with the JVM — 30/08; scheduler via `setInterval` — 27/08; `spawn`/`await`/`channel<T>()` with real concurrency via async/await/Promise — CONC003 closed 03/09) and `kof run
--target=js` work. The web platform (HTML/CSS/JS, browser) is the next
phase. See: `docs/targets/KOFJS.md`.

### KofScript — Direct Execution

Runtime to execute Kof code directly.

Planned command:

```
kof run arquivo.kf
```

The internal implementation may evolve into interpretation, incremental compilation, JIT or hybrid execution, but the decision will be made later based on benchmarks.

Current state: ✅ implemented: `kof script app.kf [--watch]` + `kof repl` (top-level statements → `main()`, top-level `var`/`val` → `KofScriptGlobals`; Windows SIGPIPE fix). **0.3.0-beta: direct execution by interpretation** — `KofInterpreter` runs the SAME optimized frontend IR (without emitting bytecode, without a JVM fork; parity by construction with the JVM backend, proven in test). `KofCcompiler` (`kof c`) compiles a C subset → native x86_64 ELF (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`).

---

## 2. Multi-Target Principle

The language must have a single semantics:

```
Source
  ↓
Lexer
  ↓
Parser
  ↓
AST
  ↓
Type System
  ↓
Symbol Resolution
  ↓
Semantic Model
  ↓
Kof IR
  ├── Kof4J Backend
  ├── KofNative Backend
  ├── KofJS Backend
  └── KofScript Runtime
```

The Kof IR must remain independent of JVM, ASM, JavaScript or native code.

Backends are responsible for transforming the semantic representation into their platform.

Current state: ✅ architecture defined and partially implemented

---

## 3. Kof as a Backend Platform

The long-term vision is to allow building modern backends without Spring.

Do not reimplement Spring. Instead, transform recurring capabilities into Kof Runtime primitives.

Future objectives:
- HTTP / REST / WebSocket / SSE (WebSocket/SSE + JVM hardening completed 04/09; JS/Native follow-up);
- HTTP client;
- JSON;
- RPC;
- events / queues / pub/sub;
- concurrency / async;
- cache;
- configuration;
- observability / logging / metrics / tracing;
- health checks / graceful shutdown;
- validation / serialization / scheduling.

Conceptual example:

```kof
api "/users" {
    get "/{id}" {
        return User.find(id)
    }
    post "/" {
        return User.create(input())
    }
}
```

Current state: 🟡 partial — HTTP/routes (`kof.web` + TLS `listenSecure` +
**WebSocket `app.ws`** + **SSE `sse.*`** — 30/08, JVM; hardening
`app.configure`/`app.stats` — 04/09), JSON (complete in the 3
targets, 31/08), configuration (`kof.config` Native asm), logging (`kof.log`
Native asm), security (`kof.security` + G9), **cache (`kof.cache`, 3
targets — 30/08)**, **`kof.http` retry/circuit breaker (JVM+JS — 30/08)**,
concurrency (`spawn` + `await`/`Handle<T>` — JVM virtual threads, Native
pthread 31/08, JS sequential), `List map/filter/reduce`, `Box<T>`, pattern
matching and `String?` implemented (0.2.6-beta).
Missing (synced 12/09 against `backend-parity.md` — the list below was the
31/08 one; HTTP002/MySQL/cross-codegen have CLOSED since):
~~HTTP client in Native (HTTP002)~~ ✅ closed (Native HTTP/1.1 asm —
`backend-parity.md` §kof.http; https→throw declared), RPC (gRPC — see
below), tracing (OpenTelemetry), residual web in Native/JS
(~~WEB002/WEB001~~ **real base in both**: Native server `KofWebNativeE2ETest`
4/4 + GraalJS HttpServer `bc577aa`; residual TLS/ws/sse/path-params),
~~complete native MySQL~~ ✅ wire protocol + binary prepared statements 03/09
(`KofDbE2E` `nativeMysqlWireProtocol`/`nativeMysqlPreparedBinary`),
~~RISC/ARM codegen~~ ✅ complete core (real riscv64 02/09; aarch64 inherits via
translator; 39+39 E2E under qemu — advanced parity faces = NATIVE002),
GC mark-sweep (G-0 riscv ✅ `356f33b9`; G-1..G-5 decomposition in
`native-multiarch.md`).
(kof.mq pub/sub + queue = 3 targets — MQ001 closed 01/09)
See `docs/development/DECISIONS.md` §D-SPRING (Phases 5-14).

**gRPC in `kof.web` (new, 31/08 — planned)**: gRPC communication as
first-class in the web platform — `app.grpc { service ... }` with stubs
generated from `.proto`, server streaming + unary over HTTP/2 on the JVM
(`io.grpc` via `kof.web`), and client `grpc.call(endpoint, method, msg)`.
Scope: web Phase (same family as `app.ws`/`sse.*`); `.proto` → Kof IR
codegen; JVM parity first, Native/JS later.

### Concurrency — residual queue (updated 13/09 — was "0.2.6-beta, 31/08")

State 13/09: real concurrency **JVM** (virtual threads) + **Native**
(pthread, CONC001 closed 31/08: spawn/await + `done`/`poll`/`cancel`/
`cancelled`/`selectAny` — cooperative cancel by TID, selectAny by 1ms polling)
+ **JS** ✅ 03/09 (CONC003 closed — stmt/expr/cancel/selectAny with real
async/await/Promise) + **OTP supervision** (`kof.supervisor`: 1st slice
11/09 JVM+Script core, **S2-JVM 13/09** `startAll`/`lacoUnico` — see
`planning-otp-supervision.md`; **Native x86 ✅ 15/09** — §129 closed via DECISIONS
§2 option B, so `kof.supervisor` runs on Native x86; riscv/aarch=OTP001, JS=OTP002
§132 honest gates). The earlier `spawn→await→spawn` SIGSEGV (misaligned stack at
the `pthread_create` call site) was fixed 01/09 by `andq $-16` in
`kof_spawn_handle_new`. A related latent defect surfaced and was fixed 15/09 in the
same §129 unit: `kof_await` did not clear the handle's TID after joining, so the
implicit `kof_spawn_join_all` at the end of `main` **double-joined** every awaited
handle — a SIGSEGV in `__pthread_clockjoin_ex` once the TCB was recycled
(reproduced at HEAD with 50 spawns + 50 awaits, 3/3 crash; clean after the fix).

| Item | Description | Priority |
|------|-----------|------------|
| ~~`ExecutionException` unwrap~~ | ✅ 31/08 — `kof_await` re-throws the original cause (JVM) | — |
| ~~`await` with timeout~~ | ✅ 31/08 — `awaitTimeout(r, ms)`: value on time, exception catchable via `try/catch` on timeout (JVM `Future.get(ms)` + Native 1ms polling with deadline; JS sequential = parity) | — |
| ~~Cancellation~~ | ✅ 31/08 — `cancel(r)`/`cancelled()` cooperative via flag on the handle (JVM + Native by TID) | — |
| ~~Multiple wait~~ | ✅ 31/08 — `selectAny(h1, h2, ...)` → first ready handle (JVM + Native + JS) | — |
| ~~`done`/`poll`~~ | ✅ 31/08 — non-blocking over the handle (JVM + Native) | — |
| ~~Native Port~~ | ✅ 31/08 — `pthread_create` + trampoline + `pthread_join` + thread-safe allocator (futex); implicit join (CONC001 closed) | — |
| ~~JS Port~~ | ✅ 03/09 — spawn over Promise, native await via microtask (CONC003 closed) | — |
| ~~Scheduler/cron~~ | ✅ 31/08 — `every`/`at` JVM (`ScheduledExecutor`) + JS (`setInterval`) + **Native SCHED001** (thread per job, `usleep` ms→us + `active` flag, cooperative `cancel(id)`) | — |
| ~~Typed channels~~ | ✅ 31/08, real blocking in JS 03/09 — `channel<Int>()` with `send`/`receive` (JVM blocking `LinkedBlockingQueue` + Native FIFO futex + JS queue of pending resolvers) | — |

Criterion for "100%": the three targets running the same concurrent programs
with an empty golden diff (same pattern as metric 1 of the plan).

### Language — residual queue (P1/P2, updated 13/09)

| Item | Status | Plan |
|------|--------|-------|
| pattern matching | ✅ 0.2.6-beta — `switch (x) { case String s: ... }` + `case Point(x,y)` in JVM/Native/JS | guards and nested destructuring pending |
| null safety | ✅ 0.2.6-beta — basic `String?` with compile-time `?`-check; **no Option in the core** | advanced checks pending |
| higher-order in collections | ✅ 0.2.6-beta — `List map/filter/reduce` in JVM/Native/JS | `Map/Set` already ✅ 0.1.0 |
| multi-file modules | ✅ 0.2.6-beta — `import a.b.C` file handling fix (`CompilerDriver.java:243`) for large projects (`a/b/C.kf`) | residual unified visibility/import semantics |

---

## 4. Native Security

Kof's own security layer, inspired by needs solved by Spring Security, but NOT as a copy.

Objectives:
- authentication / authorization;
- JWT / OAuth/OIDC;
- sessions / roles / permissions;
- security policies / CSRF / CORS;
- secure headers / rate limiting;
- input validation / password hashing;
- audit logging / API security.

The philosophy must be declarative and secure by default:

```kof
security {
    auth jwt
    route "/admin" requires role("admin")
    route "/users" requires auth
    rate "/login" 10/minute
}
```

Current state: ✅ implemented (v1, docs/stdlib/security.md)

**Implemented (0.2.6-beta, includes 0.0.5):**
- `kof.security` with idiomatic API: `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` + G9 (`rateLimit`, `sessionCreate`, `apiKeyGenerate`).
- PBKDF2-HMAC-SHA256 password hashing (600k iterations, salt, constant-time,
  versioned format).
- Crypto: SHA-256/512, HMAC, AES-GCM, secure random — JVM, Native (asm, `kof_db_mysql_scramble` for MySQL) and JS.
- JWT HS256 (fixed alg — no algorithm confusion), exp/iss/aud.
- Secrets via env + redaction for logs; constant-time comparison; Native free-list.
- Web auth middleware (`auth.authenticated()`, `auth.hasRole(...)`).
- Target gaps with clear diagnostics (SECN001/002/003/004, HTTP002).

**Pending:**
- OAuth2/OIDC client (architecture prepared in docs/stdlib/security.md §2.3);
- audit logging;
- database integration (planned).

*(sessions, rate limiting and API keys closed in G9 — 3 targets;
JWT/passwords/SHA-512/AES-GCM in Native closed in asm — G10.)*

---

## 5. Data / ORM / Hibernate

The vision is not to replace Hibernate by force. Kof must keep Java interoperability and allow `import org.hibernate.Session`.

But a native data layer must exist in the future:

- SQL / NoSQL / transactions / connection pools;
- migrations / repositories / query APIs;
- PostgreSQL / MySQL / SQLite / MongoDB.

Conceptual experience:

```kof
entity User {
    id: Long
    name: String
    email: String
}

User.find(id)
User.findAll()
User.save(user)

User.query {
    where age > 18
    orderBy name
}

transaction {
    user.save()
    account.update()
}

sql """
    SELECT * FROM users WHERE active = true
"""
```

Principle: "Abstraction when it helps, SQL when needed."

Current state: 🟡 partial — **levels 0-2 and 4 implemented** (`kof.db` +
`kof.orm`, see `docs/stdlib/DATABASE_VISION.md`): idiomatic connection
(JDBC on the JVM; native SQLite via `.so`; MySQL handshake `kof_db_mysql_scramble` 27/08), SQL with prepared
statements, transactions, declarative `entity` at compile-time, CRUD
(`create/save/find/all/where/delete/count`), `orm.where` by field + operators, `saveAll` batch, `page`/`count`/`deleteAll`,
versioned migrations (`kof_migrations`) and MongoDB (official driver).
Missing: typed query DSL (`User.query { where age > 18 }`), connection
pooling, complete MySQL (query/prepared), kof.db/kof.orm outside the JVM (DB001/ORM001 JS), NoSQL beyond MongoDB.

---

## 6. Dependency Management

The user should not need to edit `pom.xml` directly.

Future commands:

```
kof init
kof install lombok
kof remove lombok
kof update
```

The language's own file (`kofdeps`). For Kof4J, the system may generate a temporary `pom.xml` in memory during the build and use Maven for resolution/download.

Current state: 🟡 MVP 01/09 — `kof deps init/add/remove/list/resolve` (file
`kofdeps`, Maven Central resolution → `~/.kof/deps`, classpath via
`kof build|run --deps`); POM transitive dependencies and registry pending.

---

## 7. Java Interoperability

Java compatibility is a strategic requirement. Kof must be able to use Java classes, methods, interfaces, libraries, annotations, Maven artifacts and legacy frameworks.

The existence of Kof native APIs must NOT break that capability.

Rule: "Legacy keeps working. Kof offers a better experience on top."

Current state: ✅ functional (records, classes, constructors, methods, fields)

---

## 8. Frontend

Declarative UI API conceptually inspired by Flutter.

Objectives:
- components / composition / layout;
- state / events / routing;
- forms / validation;
- responsive design / accessibility;
- animation / theming.

```kof
column {
    text("Hello")
    button("Click") { action() }
}
button.color = red
button.alignment = center
button.size = 10
```

KofJS must generate:

```
output/
├── index.html
├── assets/
├── app.js
└── app.css
```

Current state: ❌ not implemented

---

## 9. Frontend + Backend in the Same Project

A single Kof project can contain backend and frontend. The compiler must understand the contexts through the project's structure/declarations.

Shared models/types may in the future be used on both sides.

Current state: ❌ not implemented

---

## 10. Application Architecture

Kof must not impose MVC, Clean Architecture or Hexagonal Architecture. It must allow all of them.

```kof
// Simple
main() {
    get "/users" { return User.all() }
}

// Modular
app/
├── domain/
├── application/
├── infrastructure/
└── api/
```

Principle: "The language provides primitives; the architecture is the developer's choice."

Current state: ❌ not implemented

---

## 11. Monolith → Microservices

The goal is to allow evolution without rewriting:

```
monolith → modular monolith → services → microservices
```

`kof build` compilation can generate `app.jar` or a native `app`. Later the same project can be partitioned.

Current state: ❌ not implemented

---

## 12. Performance

Kof must allow implementing fast, efficient, scalable applications, with low consumption and fast startup.

Rules:
- compile-time > runtime magic;
- type information > reflection;
- generated code > runtime discovery;
- explicit semantics > hidden framework behavior.

Current state: ✅ JVM functional, Native functional

---

## 13. Observability

Native APIs for log, metric, trace, health, audit. Integration with OpenTelemetry.

Current state: 🟡 partial — `kof.log` with levels (JVM: structured JSON +
correlation ID; Native: asm, UTC — JS `console.*` 01/09) and `kof.observability`
(health/readiness/liveness, counter/increment/gauge, requestId/
correlationId — 3 targets). Missing: histogram + `/metrics` endpoint
(Prometheus), tracing/OpenTelemetry and `app.health("/health")`.

---

## 14. Standard Library / Runtime

Progressively:

```
kof-runtime / kof-http / kof-json / kof-data /
kof-security / kof-concurrency / kof-io / kof-ui
```

But do NOT create dozens of modules prematurely. First define contracts, types and architecture.

Current state: 🟡 in progress — already exist as stdlib namespaces (0.2.6-beta):
`kof.web` (JVM, `kof.http` JVM+JS), `kof.io`, `kof.time`, `kof.config` (JVM+Native free-list), `kof.log` (JVM+Native),
`kof.security` (3 targets, G9), `kof.db` + `kof.orm` (JVM; native SQLite + `kof_db_mysql_scramble`), `kof.validation`/`kof.observability`/`kof.mq` (3 targets),
`kof.process`, `kof.ui` + `KofScript`/`KofCcompiler`. The organization into separate modules will come after the
contracts stabilize.

---

## 15. Roadmap by Phases

### Phase 0 — Current Consolidation ✅

- parser;
- type system;
- symbol resolution;
- semantic model;
- Kof IR;
- JVM backend;
- Native backend (completed).

### Phase F — Runtime + Object Model ✅

- audit of the current runtime ✅
- Kof Runtime ABI defined ✅
- Object Model defined ✅
- ClassLayout / FieldLayout centralized ✅
- NativeRuntime (kof_alloc, kof_panic, etc.) ✅
- NativeBackend refactored (heap alloc, constructors, KofDup) ✅
- **Phase F.1 — String Model:** ✅
  - BuiltinTypes.STRING centralized ✅
  - KofString layout (type_id, flags, length, UTF-8 data) ✅
  - kof_string_from_literal ✅
  - kof_string_length ✅
  - kof_string_concat ✅
  - kof_string_equals ✅
  - kof_print_string / kof_println_string ✅
  - NativeBackend uses KofString for literals ✅
  - STRING_MODEL.md documented ✅
- **Phase F.2 — Array Model:** ✅
  - ArrayType in the Type System ✅
  - NewArrayExpr + ArrayAccessExpr in the AST ✅
  - Parser: new Type[size], expr[expr], expr.length ✅
  - SemanticAnalyzer: array type checking ✅
  - CompilerDriver: lowering to KofNewArray/KofArrayLoad/KofArrayStore/KofArrayLength ✅
  - NativeRuntime: kof_array_alloc, kof_array_length, kof_array_get, kof_array_set ✅
  - NativeBackend: complete lowering of array operations ✅
  - JVM Backend: NEWARRAY/IALOAD/IASTORE/ARRAYLENGTH ✅
  - ARRAY_MODEL.md documented ✅
  - 25 new tests (creation, access, length, long, string, loop, argument, return, empty) ✅
- **Phase F.3 — Inheritance:** ✅
  - SemanticAnalyzer: resolveInHierarchy() walks the superclass chain ✅
  - ClassLayout: buildWithSuper() includes inherited fields ✅
  - NativeBackend: allClassesMap to resolve superclasses ✅
  - CompilerDriver: super(args) with arguments, findSuperClass() ✅
  - Constructor chaining with explicit super(args) ✅
  - Access to inherited fields and methods ✅
  - 3-level inheritance ✅
  - INHERITANCE_MODEL.md documented ✅
  - 20 new tests (subclass, inherited fields, inherited methods, constructor chaining, 3 levels) ✅
- **Phase F.4 — Virtual Dispatch:** ✅
  - Object header extended: 8 → 16 bytes (type_id + flags + method_table_ptr) ✅
  - Method tables generated per class ✅
  - kof_init_object to initialize the header ✅
  - Virtual dispatch via vtable in NativeBackend ✅
  - JVM uses native INVOKEVIRTUAL ✅
  - Parser: support for `ClassName varName = value` ✅
  - CompilerDriver: NewExpr in inferExprType ✅
  - VIRTUAL_DISPATCH.md documented ✅
  - 11 new tests (override, polymorphism, 3 levels, slots) ✅
- **Phase F.5 — Interfaces:** ✅
  - KofCallKind.INTERFACE in the IR ✅
  - Parser: interface declaration + implements ✅
  - SemanticAnalyzer: isInterfaceType(), resolveInHierarchy() walks interfaces ✅
  - CompilerDriver: defines KofCallKind.INTERFACE for calls through an interface ✅
  - JvmBackend: INVOKEINTERFACE ✅
  - NativeBackend: dispatch via vtable for interfaces ✅
  - INTERFACES_MODEL.md documented ✅
  - 13 new tests ✅
- **Phase F.6 — Exceptions/Runtime Errors:** ✅
  - AST: ThrowStmt, TryStmt, CatchClause ✅
  - Parser: try/catch/finally ✅
  - IR: KofThrow ✅
  - JvmBackend: ATHROW ✅
  - NativeBackend: kof_panic for throw ✅
  - Runtime errors: kof_null_error, kof_bounds_error ✅
  - EXCEPTIONS_MODEL.md documented ✅
  - 14 new tests ✅
- **Phase F.7 — Memory Management:** ✅
  - kof_alloc with allocation tracking ✅
  - kof_free (no-op, documented) ✅
  - kof_memstats for debug ✅
  - MEMORY_MODEL.md documented ✅
> **Updated (0.2.6-beta):** interfaces (F.5), real exceptions (F.6, JVM +
> Native unwinding) and memory management (free-list `kof_free_head` + `kof_gc_collect` 27/08; `mmap` + reuse) are implemented.

### Phase 1 — Core

- runtime (consolidation);
- standard types;
- collections;
- IO;
- errors/exceptions;
- concurrency;
- serialization.

### Phase 2 — Developer Experience

- `kof init` / `kofdeps` / `kof install` / `kof remove`;
- `kof update` / `kof check` / `kof fmt` / `kof test` / `kof clean`;
- REPL / LSP.

### Phase 3 — Web Platform (`kof serve`)

- network syscalls in NativeRuntime (socket, bind, listen, accept, read, write, close) ✅;
- `kof serve` command in the CLI ✅;
- KofHttpServer (thread pool, Content-Length, query, headers, 404/500) ✅;
- `kof serve` with top-level handlers (`handle(...)`) ✅;
- JSON serialization (`json.encode`/`json.decode`) ✅;
- 8 in-process E2E tests (real sockets) ✅;
- Documentation (`docs/stdlib/http.md`) ✅;
- Path parameters (`:id`), query, headers, middleware `app.use` ✅
  (stack `web.app()` — Phase 1 of the Spring independence plan);
- WebSocket/SSE + hardening (`app.configure`/`app.stats`, connection cap,
  deadlines) ✅ JVM (30/08-04/09); JS/Native follow-up.

### Phase 4 — Security

- auth / authorization / JWT / OAuth/OIDC;
- sessions / policies / rate limiting;
- security defaults / audit.

> Ecosystem audit: the coverage matrix, gaps (G1-G12),
> priorities and strategy live in `docs/bugs-and-gaps/ecosystem-coverage.md`.
> P0 implementation order: target diagnostics (G7) → structured `kof.test`
> (G6) → `kof.config` (G3) → `kof.http` client (G2) →
> `kof.database` (G1) → validation (G4) → observability (G5) →
> scheduling (G8) → Native security (G10) → web security (G9, G12).

### Phase 5 — KofJS

- frontend / declarative UI / components;
- state / routing / forms / SSR;
- HTML/CSS/JS generation.

### Phase 6 — KofScript

- direct execution / fast startup;
- REPL / incremental execution / scripting APIs.

### Phase 7 — Complete Native

- full language support / native runtime;
- networking / database / security;
- production server support.

### Phase 8 — Platform Maturity

- distributed systems / service discovery;
- messaging / RPC;
- observability / deployment / cloud integrations.

### Phase 9 — Internal Refactor: 500-lines-per-class rule

> **Recorded 02/09/2026.** Architecture rule: no class may exceed
> **500 lines**. Current violations force a general refactor:

- `NativeRuntime.java` (~17,300 — embedded assembly) → modules by domain
  (`native/asm/*.s` or `NativeRuntime*` classes per area);
- `CompilerDriver.java` (~8,200) → extract helpers per area;
- `JsBackend.java` (~5,400) → separate emitter from embedded runtime;
- `Parser.java` / `SemanticAnalyzer.java` / `JvmBackend.java` → sub-parsers.

Acceptance criterion: `cloc`/`wc -l` per class — none above 500.
Details and size table: `docs/audits/complexity-audit.md` → "Architecture
rule — 500-lines-per-class limit".

---

## 16. Do Not

- do not copy Spring;
- do not copy Hibernate;
- do not create a giant monolithic framework;
- do not add annotations for everything;
- do not depend on reflection when compile-time is enough;
- do not couple the core to the JVM;
- do not create backend-specific APIs inside the language;
- do not sacrifice Java interoperability;
- do not implement giant features before consolidating the core;
- do not turn every problem into a new module;
- do not add complexity just because other languages do it that way.

---

## 17. Distribution and Tooling (0.2.6-beta)

Kof is a distributable platform, not just a JAR:

- self-contained distribution (compiler, CLI, runtime, stdlib, tooling, editor support, embedded JDK 21);
- OpenJDK embedded in the official package (Temurin 21, Tooling API Level 21);
- centralized versioning (`VERSION` 0.4.0-beta → pom/properties via `scripts/bump-version.sh`);
- releases by 2 jobs (`release.yml`: `test-and-bump` exports `bump_sha` → `package-and-release` checks the bump commit + version sanity check) on push to `main`, per platform linux-x86_64 / macos-arm64 / windows-x86_64 (tests 819 → bump → package 3 platforms → GitHub Release);
- `scripts/package.sh` PASS (dist layout + tar.gz/zip + SHA256SUMS + jars), golden 16/16, integration 9/9;
- official editor support: TextMate grammar + LSP (hover/completion + real diagnostics) + `kof editor install` (VS Code/Neovim/Vim/Emacs/Geany/Nano + honest step-10 IntelliJ 13/09: filetype XML + External Tools + LSP4IJ README, no plugin — issue #1);
- `kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/debug/info/lsp/install/version` PASS (18 commands; `fmt` and `config gen` 31/08).

References: `docs/distribution/`, `docs/tooling/`.

---

## 18. Kof Written in Kof (self-hosting)

Planned from now as a real architectural evolution, not a demonstration.

Prerequisites before migration:

- generics; collections; exceptions;
- stdlib; filesystem; strings; concurrency; HTTP;
- tooling; sufficient expressiveness of the language.

The current compiler remains architecturally prepared for the migration
(single frontend feeding compiler, LSP, formatter and diagnostics), but the
migration must **not** be attempted prematurely.

---

## 19. Kof + LLM

Kof is *Human First, LLM Friendly by Consequence*:

- less ceremony; fewer files; fewer artificial abstractions;
- less configuration; more intention.

The consistency of the design makes humans and LLMs understand the same
language the same way. The `training/` directory is an official part of that
strategy.

---

## 19.5 Kof Debugger (official tooling component)

First-class debugging: the programmer debugs **Kof code**,
regardless of target. Phases 1-3 implemented: DebugInfo in the IR with
source location per op, JVM LineNumberTable/SourceFile/LocalVariableTable
generated and **functional `kof debug` MVP** (DAP over stdio + raw JDWP: launch,
breakpoints by Kof line, `stopped`, stack trace with Kof functions/lines,
continue, disconnect). Phases 4-7 (Kof Editor, Native DWARF, JS source
maps, advanced) planned. See: `docs/debugging/debugger-architecture.md`,
`docs/debugging/debugging.md`, `docs/debugging/debug-adapter.md`.

## 20. Design Principles

1. Simplicity first.
2. Readability first.
3. Compile-time whenever possible.
4. Small and predictable runtime.
5. Secure by default.
6. Measurable performance.
7. Uncompromising interoperability.
8. Native abstractions for recurring problems.
9. Escape hatches always available.
10. One language, multiple targets.
11. Monolith and microservices must be architectural choices, not limitations of the language.
12. Code must express intention, not infrastructure.
13. Kof must hide complexity without hiding power.
14. Legacy compatibility is a feature.
15. No future decision may break the language's agnostic core.

---

## 21. Legacy Migration Platform (future plan)

Long-term initiative to analyze, recover, translate and modernize
legacy systems into Kof — **outside the 0.0.x scope**.

- Central document: `LEGACY_MIGRATION.md` (§4 = Legacy Semantic IR/Confidence;
  §8 = differential test + migration report)
- Planned components: `kof inspect`, `kof decompile`, `kof translate`,
  `kof migrate`, `kof compare`
- Architecture: `Legacy Input → Legacy Semantic IR → Kof AST → Kof IR → Backend`
- Java is a supported source, never a mandatory intermediate representation
- Related documents: `DECOMPILER.md`, `TRANSLATOR.md` (the old
  `LEGACY_IR.md` and `DIFFERENTIAL_TESTING.md` were merged into the central one 13/09;
  `IMPLEMENTATION_PLAN.md`/`ACTION_PLAN.md` became §23 of this roadmap)

**Do not implement anything from this section before consolidating the language,
compiler, runtime, stdlib and tooling.**

---

## 22. Universal Platform (future plan)

Long-term vision — Kof as a universal platform (one language for
applications **and** systems, infrastructure, automation, data, security and
science) **without** destroying the language's simplicity.

- Central document: `docs/development/future/PLAN-UNIVERSAL-PLATFORM.md` (architecture,
  **not** implementation order)
- Stages by capability/maturity: `FOUNDATION ✅` → `SYSTEMS` (in
  progress) → `AUTOMATION` → `INFRASTRUCTURE` → `DATA` → `SECURITY` →
  `SCIENTIFIC` → `BIO` → `UNIVERSAL`
- Expansion mechanism: **stdlib as compile-time dispatch tables** +
  FFI/interop + official packages — never a new target, never a new language
- Invariants (R1–R12): core/platform boundary, interop-first, honest scope
  per target (JVM-first/Native/JS-web), never silent per domain
  (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`), stability tiers
  (`stable`/`experimental`), small and stable core, security defense first,
  correct/deterministic in science
- Permanent non-goals: no open macros/type-classes/annotations/ownership/
  effect system; no homemade crypto; no reimplementing Arrow/BLAS/ML/
  aligners; no "Kali in Kof"; no per-domain target; no own SQL engine

**Do not implement anything from this section before the SYSTEMS stage closes**
(gap parity, GC mark-sweep, basic package manager — see
`docs/development/roadmap.md` §23 (ex-plan-platform-completion) P0–P5).

---

## 23. Consolidated Implementation Plan (Tiers 0–12)

> **This is the ONLY ordered implementation plan in the repo.** It merges
> `ACTION_PLAN.md` and `IMPLEMENTATION_PLAN.md` (deleted 13/09 — ~85% of the
> content was the SAME phases/tiers table in both, and the two
> diverged from the code). Every phase here moves the corresponding doc from
> `future/`→`docs/` when it gains code. Difficulty: `E` easy · `M`
> medium · `H` high · `R` research.
>
> **Cross-cutting rule (R12):** no future plan item is an action on the
> current state; new fronts (AUTOMATION/DATA/SCI/BIO) do not open before the
> SYSTEMS stage (§21/§22) closes. Non-goals (§16/§22): no open macros,
> type-classes, ownership, effect system, homemade crypto, reimplementing
> Arrow/BLAS/ML; no "Kali in Kof"; no own SQL engine.

### TIER 0 — Guardrails and processes (E, ≈ zero) ✅ 01/09

R1/R5/R6/R7/R9–R12 as invariants (AGENTS.md + §22); gap convention per
domain (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`) + parity matrix;
`stable`/`experimental` tiers (`docs/backend-parity.md`).

### TIER 1 — Closing the SYSTEMS stage (M–H, prerequisite for Tiers 6+)

| # | Item | Measured state (13/09) |
|---|------|----------------------|
| 1.1 | Parity gaps (`HTTP002`, `WEB001/002`, `CONC003`, `LOG001`, `MQ001`, `SCHED001`/`TIME001`, `SECN002`, `OBS002`, `MEDIA`) | 🟡 in progress — JS web server base `abbde60b`; residual per `backend-parity.md` |
| 1.2 | Automatic GC mark-sweep in Native | 🟡 riscv `356f33b9` ✅; x86 decomposed G-1..G-5 (`native-multiarch.md`) |
| 1.3 | Typed query DSL (`User.query {}`) | ✅ 01/09 (`KofOrmE2ETest`) |
| 1.4 | Package manager MVP (`kofdeps`) | 🟡 `kof deps` + Maven Central resolution; transitive/registry pending |
| 1.5 | Tracing/OpenTelemetry + `application{}` lifecycle | 🟡 W3C spans + lifecycle ✅ 3 targets; OTel export pending |

### TIER 2 — Compiler foundations (M) — **status corrected against the code**

> The old version marked 2.1.5 and 2.2.2 as "✅"; **they are not** (see below —
> audited at HEAD 13/09, not from memory).

| # | Item | REAL measured state |
|---|------|--------------------|
| 2.1.1–2.1.3 | `extern` syntax + type-check + gaps `FFI001`/`FFI002` (never silent drop) | ✅ `Parser.java:192` (PARSE090), `ExternalFunctionNode`, `FfiE2ETest` |
| 2.1.4 | **JVM** binding (FFM `java.lang.foreign`) | ✅ real `abs`/`atoi`(String→Int)/`sqrt`(Double→Double) via FFM |
| 2.1.5 | **Native** binding (`dlsym`) | ❌ **honest gap `FFI001`** — `dlopen` segfaults in the raw binary (no glibc init); it is NOT "✅ real" |
| 2.1.6 | struct/array marshalling | 🟡 String↔Int, Double↔Double (JVM); complete struct/array pending |
| 2.1.7 | JS: gap `FFI002` | ✅ |
| 2.2.1 | Inventory of implicit codegen (4 points: runtime `.source()`, `desugarTests`, `desugarApplication`, entity→record+schema) | ✅ the 4 exist (`CompilerPipeline:295-296`) |
| 2.2.2 | **Formal `CodegenStep` hook** | ❌ **does NOT exist at HEAD** — `d1c56bad` added it, the pipeline went back to calling the `desugar*` directly; the old "✅" was an over-claim from the `planning-future` branch |
| 2.2.3 | Migrate DDL/runner to the formal hook | ❌ blocked by 2.2.2 |
| 2.2.4 | `infra "prod" {}` base (codegen over records) | ❌ not started (zero `infra` parsing) |
| 2.3.1 | Constant-folding of domain constants | ✅ `"a"+"b"→"ab"` (`OptimizerConstantFold:100`) |
| 2.3.2 | Cycle detection in the `infra` graph at compile-time | ❌ blocked by 2.2.4 |
| 2.4.1 | Scoped resources (lightweight RAII over `try/finally`) | 🟡 design only (`future/scoped-resources-plan.md`); `using` syntax gated by bump |
| 2.5 | Variance / sealed | ✅ **DECIDED TO POSTPONE** — `enum`+`record`/`interface` cover the case; opens only with the scientific pipeline (bump) |

### TIER 3–5 — Legacy migration platform (Phases A–H) ✅ code+tests

`kof inspect/decompile/translate/compare/migrate` in the CLI (`Main.java`);
Legacy Semantic IR with Confidence Model (5 levels) + "never invent". Proof
measured 15/09 at HEAD (`7b0bfbe0`, fresh classes): **Decompile 67 + PostDom 6,
Translate 61, Compare 7, Migrate 3** — all green (the 13/09 numbers 57/33/6/3
were stale; the former "1 red cell" `qualifiedLocalTypeTranslates` is GREEN
since the `.22` lane closed it). Method body recovery still partial
(structural joins = Phase C, the biggest measured bottleneck: 2452 methods). The
detailed technical history lives in `LEGACY_MIGRATION.md` + `DECOMPILER.md`
(§7) — **do not duplicate here**; this table only gives the order.

### TIER 6–12 — Universal platform (not started; governed by `future/PLAN-UNIVERSAL-PLATFORM.md`)

| Tier | Stage | Scope (one line) |
|------|---------|--------------------|
| 6 | AUTOMATION | `kof.workflow`/`batch`/`shell`/`ssh` — jobs as Kof code, never YAML/bash |
| 7 | INFRASTRUCTURE | `infra "prod" {}` (codegen, not HCL) + reconciliation loop — deps 1.4, 2.2 |
| 8 | DATA | typed `dataframe` + Arrow/Parquet/statistics **via FFI** (wrapper, never reimplement) — deps 2.1, pkg manager |
| 9 | SECURITY | S2 `Secret`/`KeyHandle` · S3 `keys.*` · S4 asymmetric · S5 **PQC** (`liboqs`, NIST) · S6 hybrid · S7 `secure.channel`; only FFI to an audited lib |
| 10 | SCIENTIFIC | BLAS/LAPACK/GPU/MPI **via FFI**; Native SIMD (research); deps 2.1, 2.4, 1.2 |
| 11 | BIO | `kof-bio` (official package): FASTA/FASTQ/VCF + alignment via FFI/CLI — deps 6, 8, 10 |
| 12 | UNIVERSAL | total integration + mature pkg manager + LSP/debug/profiler per domain; **final test: the language core barely grew** |

### Critical path (what blocks what)

`Legacy-Class-File-Parser` → all Tiers 3–5 · `Decompiler-Structural` →
`Diff-Framework` → `Migration-Reports` · `2.1 FFI` → Tiers 8/9/10 (everything via
FFI) · `2.2 codegen hook` → `infra`/gRPC stubs · **TIER 1 (SYSTEMS) closes
before ANY Tier 6+ (R12).**
