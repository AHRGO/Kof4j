[English](DECISIONS.md) | [Português](DECISIONS.pt_BR.md)

# DECISIONS — record of the maintainer's decisions + ratified plans

**Last updated:** 13/09/2026 · **Who decides:** Mel Santos (maintainer)
**How to decide this kind of item:** the maintainer answers in the chat ("you can
go with the recommended one", or chooses another option); the agent locks the answer
here with date + chosen option — **the decision does not live in the chat, it lives here**.

> This document **replaces** the 6 files that lived in
> `docs/development/decision-pending/` (maintainer's decision 13/09:
> "turn them into a single doc for development"). The decisions were
> inventoried, audited against the code (never memory) and **ratified
> 13/09** with the accepted technical recommendation. What each file became:
>
> | Deleted file | Became |
> |---|---|
> | `planning-stdlib-time-design.md` | §D-STDLIB — execution in the STDLIB queue |
> | `security-plan.md` | §D-SEC — layers B/C/D became a queue; invariant architecture in `docs/stdlib/security.md` |
> | `plan-spring-independence.md` | §D-SPRING — phases 1–9 ✅ (history), 10–12 ratified here |
> | `plan-platform-completion.md` | §D-PLAT — P0–P2 ✅ historical; the real queue lives in `roadmap.md` §23 |
> | `APPLICATION_MODEL.md` | §D-APP (Q1–Q10 locked) |
> | `PLATFORM-PLAN.md` | §D-PLATFORM — **dead**: F1 absorbed by the manifest (already implemented, `KofProjectConfig`); F2–F9 already live in `roadmap.md` §23 |
>
> Invariants that nothing here changes: 0.2.6-beta freeze (operators, `==`,
> `spawn`, collections), R1–R12 of the universal vision, the ≤500 rule, suite as gate.

---

## D-STDLIB — time/calendar semantics (ratified 13/09)

**D1 (timezone) — UTC-only.** `today()`/`isToday` derive from `now()` in UTC in
ALL 5 targets. Whoever wants a timezone uses the explicit getter `tzOffsetSeconds()`
(JVM: host; JS: `Date.getTimezoneOffset`; Native: gap DIAG `TIME003` until
there is `TZ`/`/etc/localtime` in the asm). No accidental timezone parity — silent
cross-target divergence is forbidden by construction.

**D2 (composites) — no composite return.** The DD-STDLIB-01 lock already closed
13/09, but it is **not** to be reopened: the calendar enters as **pure scalar over
ISO** — the convention `addDays("YYYY-MM-DD", n) -> String` is already the living form
since S7a (audited in `KofTime.java:133`). No `startOf` returning a
tuple.

**D3 (`hoursBetween`) — complete, symmetric floor.** Consistent with
`daysBetween` (truncated toward zero): counts full whole days +
hour delta. No float (FLT001), no 12-arg signature.

**D4 (format) — zero pattern-DSL.** The stdlib exposes `formatDateIso(y,m,d) ->
STR` (invalidity ⇒ `""`, documented lenient face) and `parseDateIso(STR) ->
Int` (serial `daysFromEpoch`; invalid ⇒ 0). Patterns `dd/MM/yyyy` **do not
enter the base stdlib** (R1/R12 — a parsing engine is a package, not the base).

**D5 (`isToday`) — enters**, D1 dependency resolved:
`isToday(y,m,d) -> Bool` = equality with the UTC date of `now()`.

### STDLIB queue released (one unit-test-commit each)

| Item | Signature | Targets |
|---|---|---|
| `time.todayIso()` | `() -> STR` ("YYYY-MM-DD" UTC) | 5 |
| `time.formatDateIso(y,m,d)` | `(I,I,I) -> STR` (invalid ⇒ `""`) | 5 |
| `time.isToday(y,m,d)` | `(I,I,I) -> Bool` (UTC) | 5 |
| `time.hoursBetween(y,m,d,H, y,m,d,H)` | `(I×8) -> Int`, symmetric floor | 5 |
| `time.parseDateIso(STR)` | `(STR) -> Int` (serial; invalid ⇒ 0) | 5 |
| `time.tzOffsetSeconds()` | `() -> Int` (Native = `TIME003` DIAG) | JVM/JS/SCRIPT; Native gap |

Each line: golden from the JVM oracle (real measurement), dispatch in `KofTime`,
`training/idioms`, conformance matrix, honest gap where there is one.

> **✅ EXECUTED 13/09 (development lane, owner 192.168.100.18):** the 6
> lines above implemented and validated — todayIso/formatDateIso/isToday
> (S7e), hoursBetween (S7f + x86 emit fix for 7+ args), parseDateIso (S7g),
> tzOffsetSeconds (S7h, honest Native gap TIME003 — general queue). Proof:
> `KofTimeE2ETest` S7e-S7h (30/30) + matrix `stdtime3`/`stdtime4`/
> `stdtime5`/`stdtime6` + Script parity. Suite 1772/0/0. **D-STDLIB TIME
> queue CLOSED.**

---

## D-SEC — security (ratified 13/09)

**Invariant architecture (does not change — already documented in
`docs/stdlib/security.md`):** 18 layers; crypto never homegrown (JCA/WebCrypto/
audited asm); `SECN00x` for gap per target; current state verified in the
code 13/09: layer A and B ✅ (password/sha512/AES-GCM/JWT in the 3+targets with
honest `SECN001/2/3/4`), C10/12/13/14/15 ✅, D17 partial ✅.

**ChaCha20-Poly1305 — enters (RFC 8439), mirroring the real AES-GCM envelope**
(audited in `JvmStringSecurityRuntime.java:152-154` —
`aesgcm$<ivB64>$<ct+tagB64>`, `(plaintext, keyHex)` with a 32B key in hex):

```
chacha20$<nonceB64(12B)>$<ct+tagB64(16B tag)>
security.chacha20Encrypt(String text, String keyHex) -> String
security.chacha20Decrypt(String token, String keyHex) -> String
```

Same `SecCall` pattern as `kof_sec_aesgcm_*` (gap SECN002 in targets without
implementation). JS via WebCrypto `ChaCha20-Poly1305` (where it exists; the rest
= honest SECN002). Constant time, nonce never reused (documented).

> **✅ EXECUTED (14/09, step 2 — owner 192.168.100.18):** chacha20 JVM+JS
> (`kof_sec_chacha20_encrypt/decrypt`, SecCall identical to aesgcm). JS is a
> pure implementation (WebCrypto does **not** expose ChaCha20 in any
> main engine — the "where it exists" premise fell; proof: MDN
> SubtleCrypto.algorithms). Validated byte by byte against node:crypto and against
> the RFC 8439 §2.8.2 vector (Poly1305 AEAD: r/s LE, mac_data
> pad16(ct)||le64(0)||le64(ctLen)). **Native (x86/riscv/aarch64) remains an honest
> SECN002 gap at compile-time** — pure Poly1305 asm (130-bit
> arithmetic) stays in the queue, same precedent as SECN000. Constant time:
> tag compared with `MessageDigest.isEqual` (JVM) / accumulated XOR (JS).
> Tests: KofSecurityTest 32/32 (`chacha20*`), 4-module suite 0 failures.

**Cookies (C11) + security middleware (C18) — enter, RUN TOGETHER with
the Application Model** (the order only makes sense with `app.use`):

- `security.cookies`: parse/set with secure defaults (`HttpOnly`, `Secure`,
  `SameSite=Lax`, `Path=/`); API `cookieSet(name, value, opts-map)` and
  `cookieGet(request, name)`.
- `app.security()` — composite middleware applying the **fixed order**:
  rate-limit → cors → headers → cookies/session → csrf → auth → RBAC → route.
  Security by default: `listen` in production requires an explicit `app.security()`
  or a warning.

> **✅ EXECUTED (14/09, partial step 3 — owner 192.168.100.18):**
> `security.cookieSet(name,value[,opts])` and `security.cookieGet(header,name)`
> implemented in **JVM + JS** (`kof_sec_cookie_set/set_opts/get`), with
> secure defaults (`Path=/; SameSite=Lax; Secure; HttpOnly`) and opts-map
> (`path/domain/maxAge/expires/sameSite/secure/httpOnly`). **Native remains an honest
> SECN006 gap** (same precedent SECN000/002). Tests `KofSecurityTest`
> 39/39 (cookieSetDefaults/opts/get Jvm+Js, JVM→JS roundtrip, SECN006 cross).
> **✅ EXECUTED (14/09, step 4 — owner 192.168.100.22):**
> `app.security([opts])` (C18) implemented in the JVM runtime (`WebApp` fields +
> `kof_web_security` + `kof_web_security_pipeline` in `JvmRuntimeWebDispatch`).
> Ratified fixed order: rate-limit → cors → security headers (CSP/HSTS/nosniff/frame/referrer)
> → session (authHeader + publicPaths) → csrf. Proof: `KofWebE2ETest#appSecurityPipelineE2E`
> + full E2E validation in `KofBlogE2ETest`.

> **✅ MERGED (14/09, owner 192.168.100.18): the two C18 implementations were
> unified as a superset** (aggregation pact — neither side discarded). The `.22`
> API (`rateLimit` Number, `corsOrigin`, `sessionHeader`, `publicPaths`) and the
> `.18` API (`headers`, `cors`, `rateLimit` String, `csrf`, `auth`, `roles`) now
> live in a single `kof_web_security`/`kof_web_security_opts` + one pipeline
> (rate-limit → cors → headers → session → csrf → auth → RBAC) writing response
> headers to `KOF_SEC_RESPONSE_HEADERS`. Session is enforced on mutations
> (reads are public; an invalid session header never passes); `auth`/`roles`
> require a valid Bearer JWT. Proof: `KofWebE2ETest` 22/22 + `KofBlogE2ETest`
> 1/1 + `KofOAuthResourceServerTest` 4/4 + `KofSecurityTest` 41/41 = 68/0/0.

> **✅ EXECUTED (14/09, step 3 complete — owner 192.168.100.18):**
> `app.security()` (C18) implemented in **JVM** (`kof_web_security` /
> `kof_web_security_opts`, `SecurityMiddleware` registered on `app.middlewares`).
> Applies the **fixed order** rate-limit → CORS → headers → cookies/session →
> csrf → auth → RBAC → route. No-arg = secure defaults (hardening headers:
> CSP/nosniff/frame/referrer, HSTS only under TLS). Opts-map (all documented in
> `docs/stdlib/stdlib-web.md`): `headers` (Bool), `cors` (String origin/CSV/`*`,
> unlisted origin → 403, preflight → 204), `rateLimit` (`"limit/windowSeconds"`
> per remote IP → 429 + `Retry-After`), `csrf` (double-submit cookie), `auth`
> (require valid Bearer JWT), `roles` (String CSV or List). **Auth-if-present:**
> a request carrying an invalid token never passes, even without `auth:true`.
> **Security by default:** `listen`/`listenSecure` under `KOF_ENV=production`
> without `app.security()` warns on `stderr`. **Native/JS report `WEB006`**
> honestly (same precedent as WEB002/WEB005). Response headers from the
> middleware survive the dispatch clear via `KOF_SEC_RESPONSE_HEADERS`. Refactor:
> new fragment `JvmWebSecurityRuntime.java` keeps the §140 ratchet green
> (`JvmWebCoreRuntime` 699→495). Tests: `KofWebE2ETest` 22/22 (security headers,
> auth 401/200, auth-if-present, roles 403, CORS deny/preflight, CSRF, rate-limit
> 429, WEB006 Native+JS). Also fixed a pre-existing descriptor bug:
> `kof_sec_auth_user` was declared `(Ljava/lang/String;)` but takes no args.

**OAuth2/OIDC (D layer 16) — locked sequence:** (1) **resource server**
first (third-party JWT validation: JWKS + issuer/aud — cheap, closes
"who is the Google user?"), (2) client authorization-code + PKCE later;
**provider never** (non-goal, outside any plan).

> **✅ EXECUTED (14/09, owner 192.168.100.18):** step 1 — **OAuth2 resource
> server** in the JVM. `auth.resourceServer(jwksUrl, issuer, audience)` configures
> third-party JWT validation (fetches the public keys from the JWKS URL; empty
> issuer/audience = not required) and `auth.resourceServerVerify(token)` returns
> the claims JSON or `null`. Fixed allowlist **RS256/384/512 + ES256/384/512** —
> never `none` nor HS* (algorithm confusion rejected); RSA (`n`/`e`) and EC
> (`crv` P-256/384/521, `x`/`y`) JWK keys; `exp` + `iss` + `aud` (string or
> array). On an unknown `kid`, the JWKS is re-fetched once (key rotation). The
> resource server plugs into `auth.authenticated()`/`app.security({auth:true})`:
> after configuration, a token that fails HS256 falls back to JWKS validation.
> JWKS is cached in memory. **Native/JS report `SECN007`** honestly. Tests:
> `KofOAuthResourceServerTest` 4/4 (real RS256 token + local JWKS via
> `com.sun.net.httpserver`; iss/aud rejection; alg=none/tamper; integration with
> `app.security` 401/200; SECN007 Native+JS). API documented in
> `docs/stdlib/security.md`.

**TLS with own certificate — enters:** `app.listenSecure(port, certPem,

**TLS with own certificate — enters:** `app.listenSecure(port, certPem,
keyPem)` (PKCS#8 PEM; JVM first; Native/JS remain honest `WEB002`).
The current self-signed remains a dev convenience, not production.

> **✅ EXECUTADO (14/09, dono 192.168.100.18):** `app.listenSecure(port,
> certPem, keyPem)` no JVM — `kof_web_listen_secure_pem` monta o `SSLContext`
> a partir do cert X.509 PEM + chave PKCS#8 PEM (RSA/EC/DSA, via KeyFactory),
> sem `keytool` (produção não depende de toolchain externa). A variante de 1
> arg (self-signed de dev) fica intacta. Native/JS seguem `WEB002` honesto em
> compile-time (mesmo gate). Prova: `KofWebTlsTest` 7/7 (incl.
> `tlsOwnCertificateServesHttps` — handshake + 200 com cert gerado no teste;
> `tlsOwnCertificateGapOnNative` — WEB002).

---

## D-APP — Application Model (ratified 13/09)

The RFC's 10 open questions become decisions:

| Q | Decision |
|---|---|
| Q1 manifest | **`kof.toml`** — ✅ already implemented (`KofProjectConfig` INI subset; PKG006 uses it as the project root). Formalized decision, no new code. |
| Q2 bump | **0.4.0-beta** (new capability; the 0.4.0 line is already underway). |
| Q3 shared types | local package imported by front+backend, **after** the package manager; blocks nothing. |
| Q4 `kof serve --system` | **rejected** (confirmed) — alternative: `kof serve --list` (apps+ports, without starting). |
| Q5 `[frontend].api` | **documented convention** (not a rewrite feature). |
| Q6 fat jar | optional **`--fat` flag** (I3); default = explicit classpath. ✅ **EXECUTED 14/09**: `kof build --fat` (JVM) generates `kof-app.jar` (app classes + `dev.kof.runtime` runtime + external deps, `Main-Class` in the manifest, app first-wins, dep signatures discarded); proof `CmdBuildFatTest` 4/4 (`java -jar` runs the program; without the flag there is no jar; `--fat` outside the JVM refuses honestly R6). |
| Q7 Wasm | ✅ frontend column in the table when the target opens; **model does not change**. |
| Q8 `kof.proxy` | outside this RFC; convention today, stdlib only with 3+ apps asking. |
| Q9 frontend rebuild | **on demand by hash** (I2); watcher = future. |
| Q10 Android | **declare ✅** in the table (WebView + KofJS is already full-stack de facto; no new code). |

**RFC Plan I1–I3:** I1 (manifest+`kof new`) — **manifest already exists**;
`CmdNew` remains (backend/full-stack/frontend skeletons + `APP003` validation),
small scope. I2 (full-stack serve/build) ✅ and I3 (fat/deploy) ✅ (`--fat`,
14/09) executed with the RFC §23 increments. The APP001–003 matrix goes to
`docs/backend-parity.md` (R6 gap codes).

### D-APP.REF — the model on one page (RFC reference content)

**Definition.** A Kof Application is a directory containing a Kof module
(set of `.kf` with a `main()`) + optional manifest `kof.toml`
describing components (frontend, static) and execution (port/host, target).

```text
my-app/
├── kof.toml            # manifest (OPTIONAL — without it, current 1:1 convention)
├── kofdeps             # dependencies (exists today)
├── src/main.kf         # entrypoint (single main() = backend)
│   └── web/main.kf     # FRONTEND component (another module → js bundle)
└── src/static/         # STATIC component (css/img — pure copy)
```

Rules: (1) one application = one module = one `main()`; frontend is ANOTHER module
compiled to `js`; (2) components = zero/one/more (backend, frontend,
static); (3) smallest unit of `kof serve`/deploy (1 process, 1 port);
(4) System = deploy composition, not compilation; (5) **without `kof.toml`
= exactly current behavior**.

Manifest (INI subset already read by `KofProjectConfig`): `[app]`
(name/version/entry/target), `[serve]` (port/host; flag always wins),
`[frontend]` (path/entry/out/base/api/cors), `[static]` (path). Read by the
CLI, never by the compiler; manifest error = clear diagnostic (R6).

**Full-stack:** the backend mounts the bundle via `app.serveDir` (exists on the JVM) and
the frontend calls the API **over HTTP** (`kof.http` in js) — never a direct
call. The front→back contract is always HTTP/JSON ⇒ "same process" and
"remote service" are interchangeable (basis of the
monolith↔distributed portability).

Topologies (what changes is only the number of directories and who orchestrates —
language/compiler/CLI do not change): monolith (today's default), modular
monolith (package organization, nothing of the model), microservices (N
backend-only apps), microfrontends (N bundles + shell), full-stack (goal I2),
backend-only, frontend-only, distributed full-stack (+ gateway = app that
routes).

**Permanent structural rules (ex-spring, D-SPRING):** no stdlib component
depends on Spring; no backend generates Java-source as a mandatory
step; fundamental capabilities have a Kof-native API (Spring is a
consumed alternative); the independence test (Kof app without Spring) is worth
as much as the interoperability one.

---

## D-SPRING — phases 10/11/12 (ratified 13/09)

- **Phase 10 (native testing):** locked scope = `kof test` runs the project's
  tests with **Kof asserts** (no mandatory JUnit; interoperable when
  JVM); unit + HTTP (via `web.app` on an ephemeral port) first; property/
  stress/mocks are separate increments, never a gate.
- **Phase 11 (complete CLI):** consolidate what exists (`run/build/test/serve/
  fmt/deps/init/check` ✅ in `Main.java`) + `kof new` (I1 of D-APP); `kofdeps
  add/remove/list/resolve` already landed (Deps.java) — only the link with the
  manifest is missing (deps in `kof.toml`).
- **Phase 12 (blog E2E):** **NOW** — it is the platform validation, and it is the canonical
  app model (backend + frontend + db + auth + validation in a single
  `kof.toml`); runs on JVM and Native without changing a line (diagnosed gaps
  count as honest, not as failure).

## D-PLAT — platform-completion (ratified 13/09)

P0–P2 ✅ closed (history 0.1.0→0.2.6-beta). P3–P5 are not their own queue:
**each remaining line already has a home** — DB/orm in roadmap §23 and
`docs/stdlib/DATABASE_VISION.md`, observability (OTLP) in
`docs/backend-parity.md`, DX (`kof new`) in D-APP/I1. The document is dead.
The plan's **Definition of Done** ("compiles on the 3 targets or gap with
`supportedOn` + E2E per target + benchmark when plausible + docs
synced in the same commit + green suite") was not lost: it lives in
`docs/architecture/performance.md` §40–§41 (the source the plan itself
cited) and echoes in the Q0–Q6 gate of `AGENTS.md`.

## D-PLATFORM — PLATFORM-PLAN (dead 13/09)

F1 (project root) **resolved by the manifest** (`KofProjectConfig` +
PKG006); F2 targets = `docs/targets` + §23; F3 full-stack = D-APP I2; F4/F5 =
`KOFUI-AUDIT`/`docs/stdlib/stdlib-web.md`; F6 wasm/F7 android = D-APP
Q7/Q10 table; F8 script = `kof-cli CmdScript` ✅; F9 conformance =
`docs/bugs-and-gaps/conformance-matrix.md`. **Nothing unique remained — the file was absorbed, not discarded.**

## D-RELEASE — patch trigger (0.4.1) by volume of fixes (14/09)

**Maintainer's rule (14/09, after closing the 0.4.0 minor):** development
is now **patch stabilization**, not feature. The objective criterion to cut
a patch:

- **Trigger:** when `beta` is **between 100 and 150 commits ahead of
  `main`**, evaluate the bump to **`0.4.1`** (patch — only fixes, zero new
  capability; the `0.4.0` line was already released in #138).
- **Features do NOT stop nor are discarded in the window (maintainer's addition
  14/09):** between 0.4.0 and the trigger, **real evolution (features/improvements)
  competes with the bugfixes** — the 100–150 counter mixes the two. The patch
  window **does not freeze development** and the bump **cannot discard /
  revert / hold in a branch** the feature work done in the period: everything
  that is in `beta` with a green suite and proof enters the package. If the volume of
  **new capability** accumulated in the window is material (changed contract/operator
  — rule 6, or visible API surface), the evaluated bump stops being a
  patch: it becomes a **0.5.0-minor** (semver decides by content, not by calendar
  nor by the trigger). The release note lists fixes AND features.
- **Stable package average:** 100–150 accumulated fix commits = a package
  stable enough to be worth a release. Below that, it is noise; above, the
  fix backlog already justifies the version number.
- **Before bumping:** **fix the open issues** (`gh issue list --state
  open`) that belong to the bugs/parity lane — the patch ships with the known
  issues closed, not on top of them. Rule 6 (contract) issues stay
  open with a note, they do not block the patch.
- **After the bump:** return to normal development (`beta` reopens for the
  next minor/feature; the counter resets against the new `main`).

**Counter state (measurement 14/09, post-#138):** `main..beta = 1` (only
`a5eedbe2`), `beta..main = 1` (the PR merge). **Far from the trigger** — the loop
keeps accumulating fixes in beta; no agent bumps a version until it
gets close to 100. Whoever measures should note the count and the date here.

**How to measure:** `git rev-list --count origin/main..origin/beta-0.4.0`. When
crossing the range, open the issue "Release 0.4.1" (rule: every PR comes with an issue),
run the full suite green, close the bugs lane issues, and only then bump
`pom.xml` + `version.properties`.

---

## D-ASM-GATE — riscv/aarch asm gate OPTIONAL until native dev closes (14/09)

**Maintainer's decision (14/09):** *"leave the riscv and arm test optional
until development is complete"* — and the non-negotiable floor that accompanies it:
*"there can be no broken test on main nor on beta"*.

- **Context (measured root cause):** the tests
  `*CastSaturationLabelsAreUniquePerEmission` (regression lock of §181,
  `67db6c50`) asserted "the backend always keeps the `.s`". **False:** on a host
  WITH toolchain, `as`+`ld` link successfully and `NativeArchEmitter` DELETES the
  `.s` (it only keeps it with `KOF_KEEP_ASM`). Result: green on the dev host (without
  toolchain, the `ToolchainMissing` branch preserves the asm), **red in CI**
  (toolchain present) — beta gate broken since `67db6c50`/`fdf0dd92`.
- **Chosen option:** **honest and explicit** skip (rule Q5, never "passes by
  accident"): `Assumptions.assumeTrue(KOF_ASM_GATE)` at the start of the two tests.
  Default = skip (green CI); reactivate with `KOF_ASM_GATE=1` when the cross gate
  is required again. Body made **portable** (if `.s` exists → text
  inspection; otherwise → require the linked binary, mechanical proof).
- **The §181 regression remains proven in the 42 riscv + 42 aarch E2Es under qemu:**
  duplicate `.Lsat181` label = `as` fails = `success()` false = red E2E
  test. The text gate is redundant with qemu; it only adds on a host WITHOUT
  toolchain — that is why it can be optional without losing real protection.
- **Evidence:** `NativeRiscv64E2ETest.java`/`NativeAarch64E2ETest.java`
  (`KOF_ASM_GATE`); double local proof — without the flag: `Skipped: 2`; with
  `KOF_ASM_GATE=1`: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- **Reactivation condition:** when native development is complete
  (Native lane bugs — §184/§187/§181-adjacent — closed with matrix
  5/5), the `assumeTrue` is removed and the gate becomes mandatory in CI again.

---

## D-BASELINE — Toolchain baseline 21 → 25 (✅ decided 14/09, mantenedora)

- **Decisão:** o baseline de build da toolchain do repo sobe de **Java 21**
  para **Java 25** (LTS), pedida pela mantenedora na sessão da lane CodeQL
  (14/09) para destravar o codemod 100% preservador de comportamento dos
  findings `java/local-variable-is-never-read` (×82) e parte de
  `java/unused-parameter` (×81): **unnamed patterns/variables, JEP 443,
  finalizado no Java 22** (medido: `javac --release 21` recusa
  `case WhileStmt _ -> false;`).
- **O que muda (toolchain do repo, NÃO a linguagem):**
  - `pom.xml`: `<maven.compiler.release>`/`<release>` **21 → 25**.
  - `.github/workflows/*.yml` (ci/codeql/release/benchmark/android):
    `setup-java` `java-version` **21 → 25**.
  - `scripts/package.sh` (`--jdk`): Temurin embutido **21 → 25**.
  - README canônico + `.pt_BR`: "Requisitos: JDK 21+" → **25+**.
  - `codeql.yml`: o setup-JDK também (a análise passa a ser em 25).
- **O que NÃO muda (regra 6 — alvo de runtime de programa Kof, congelado):**
  - `JvmBackend.java:163` continua emitindo `V21` (version V21). Programas Kof
    compilados seguem rodando em **JVM 21+** — a toolchain do repo é uma
    coisa, o bytecode emitido ao usuário é outra (retrocompatibilidade
    aditiva, AGENTS.md Congelamento 1/2). Subir o bytecode emitido seria
    exigir JVM 25 do usuário do Kof = **não foi isso que foi pedido**.
  - O template Android (`AndroidProjectWriter` `<javac release="21">`) fica
    **21** — alvo do APK do usuário (API/level próprio), não a toolchain.
  - `JvmRuntime.java:95` guarda `--release 21 --enable-preview` só quando
    `Runtime.version().feature() < 22` (caminho do vulkan preview) —
    intocado: continua correto em qualquer JDK 21..25.
- **Evidência:** `mvn -o -pl kof-compiler -am compile` verde com
  `Compiling ... with javac [debug release 25]`.
- **Fila aberta pela decisão (mesmo commit DOING):** (1) gate completo 4
  módulos em JDK 25; (2) codemod `_` nos 82 local-var + callers dos 81
  unused-param; (3) CHANGELOG/STATUS notam o novo baseline.
- **Bump de versão da linguagem?** Não — **0.4.0-beta segue**: é mudança de
  *build da toolchain*, não de contrato da Kof (programa Kof compilado hoje
  compila e roda amanhã em JVM 21+).

---

## D-ENGINEERING — do not reinvent the wheel (14/09, maintainer's principle)

**Maintainer's rule (14/09):** *"about the compiler's internal logic, always take
inspiration from the way **Java and C** solve the problems, as long as the
frontend and the way of writing stay idiomatic and the output stays
deterministic; you may take inspiration from how other languages solve the
backend. We do not want to reinvent the wheel."*

- **Scope:** internal compiler/backend logic (lowering, runtime helpers,
  codegen, semantics). **Not** the Kof frontend: syntax and the way the user
  writes stay idiomatic Kof (rule 6 — frozen surface is not changed by this).
- **Reference order:** (1) **Java** (JLS/JVMS + `java.lang`/`java.math`
  behaviour — the strongest anchor, since the JVM backend already targets it),
  (2) **C** (ISO C / libm semantics for the native backend), (3) other
  languages only for *backend* technique (never for the Kof surface).
- **Invariants kept:** deterministic output (same input → same bytes/result on
  every target) and the honest-gap rule (R6: never a silent wrong answer; an
  unimplemented face reports a code, it does not guess).
- **First applications (same day):** §D-BACKEND-SEMANTICS below.

---

## D-BACKEND-SEMANTICS — 6 decisions from the 14/09 chat (owner 192.168.100.18)

**✅ QUEUE CLOSED — 6/6 implemented:** §101 (1), §129 (2), `roundTo` (3),
§179 (4), `app.security()`/Spring (5), §180 (6). The `## 7` queue of
`docs/development/README.md` is empty; this section is now a record, not a backlog.

The maintainer answered the open list. Options chosen and their execution:

### 1. §101 — relational operators with NaN → **option A (pure IEEE 754)**
All targets must agree with IEEE 754: **every relational comparison with NaN is
`false`** (and `!=` is `true`). The riscv/aarch behaviour is the reference
(it is already IEEE); **JVM/x86/JS are aligned to it**. Concretely: the JVM
lowering must not rely on the `dcmpl`/`dcmpg` quirk that returns `true` for
`1.0 < NaN` / `1.0 <= NaN`; the comparison result is computed IEEE-correctly
(an unordered result forces `false` for `<`, `<=`, `>`, `>=` and `true` for
`!=`). JS follows IEEE by construction (`<` with NaN is `false`). This is
Java's own contract (JLS 15.20.1: NaN comparisons are all `false`), so the
JVM was the outlier, not the reference.

**Done (14/09, owner 192.168.100.18):** JVM uses `FCMPG`/`DCMPG` for `<`/`<=`
and `FCMPL`/`DCMPL` for `>`/`>=` (`JvmOpEmitter` via
`JvmLiteralEmitter.floatCmpIsG`/`condCmpIsG`); Native x86 fixed in
`NativeX86Arith` (value path) and `NativeOpHelpers` (jump path) — the `setb`/`jb`
of `LT` lacked the unordered guard that `LE`/`GE` already had. riscv/aarch
already IEEE. Proof: `BackendParityTest.parityNanRelationalIeee` (JVM×JS) and
`ComponentCoreE2ETest.nanRelationalIsIeeeOnAllTargets` (JVM+Native+JS, value and
jump paths, Double and Float).

### 2. §129 — cross-thread unwind in Native → **option B (frame per thread)**
Give the Native unwinder a **per-thread exception frame** (not a single global
`kof_exc_chain`, not TLS-by-TID-as-shared-chain): each thread owns its
handler/frame chain, so a `throw` with no handler inside a `spawn` worker marks
the worker's handle as exceptionally-complete instead of `longjmp`-ing out of
the thread. Inspected from Java (per-thread exception state) and C
(`setjmp`/`longjmp` frames are stack-local). Affects the shared runtime
(`RuntimeDb4`/GC); the chain becomes thread-scoped. Unblocks **OTP S2-Native**
(§129), whose gate is `OTP001` until this closes.

**Done (15/09, owner 192.168.100.18) — Native x86:** the chain is now TLS
local-exec (`.section .tbss,"awT",@nobits` + `%fs:kof_exc_chain@tpoff`) in
`RuntimeGc.emitPanic`, `NativeMethodEmitter` (`KofTryStart`/`KofTryEnd`),
`RuntimeDb4` (tx frames) and `RuntimeStringParseOrDefault`; `ld.so` initialises
the main thread's TLS and `pthread_create` the worker's (validated: a worker
that writes the chain does not touch main's). `kof_spawn_trampoline` installs a
per-worker handler frame and, on `throw` with no inner handler, publishes the
cause on the handle (`handle->exc` at 40) instead of unwinding into main's
stack; `kof_await`/`kof_await_timeout`/`kof_select_any` rethrow it on the
consumer (JVM parity), and `kof_await` zeroes the joined TID so the implicit
`kof_spawn_join_all` never double-joins (SIGSEGV with a recycled TCB). The
handler frame keeps the handle at `32(%rsp)` because the worker may clobber the
callee-saved `%r12`. `CompilerSupervisor` now emits `OTP001` only for
riscv/aarch (raw `clone`, no TLS + `selectAny`/CONC001). Proof:
`KofConcurrency2Test.spawnWorkerThrow*Native` (4), `KofSupervisorE2ETest`
`supervisorNativeParityX86`/`supervisorNativeS2ParityX86` + `crossGateOtp001`;
`KofSupervisorE2ETest` 15/15, `KofConcurrency2Test` 40/0, `ExceptionsE2ETest`
11/0, `NativeE2ETest` 65/0.

### 3. `roundTo` — **implement, rational (inspired by Java + C)**
Approved for implementation (the 13/09 `pow` ratification had left it open).
Rational design, Java+C-inspired:
- **C `round()` family** = round-half-away-from-zero (`round(2.5)=3`,
  `round(-2.5)=-3`) — the C floor is the anchor for the *rounding mode*.
- **Java `BigDecimal.setScale(n, RoundingMode.HALF_UP)`** = the anchor for the
  *decimal scale* (rounding a `Double`/`Float` to N decimal places).
- **Surface (Kof-idiomatic):** `math.roundTo(value, decimals)` returns the same
  numeric type as `value`, `decimals` an `Int` (0 = integer rounding).
  Deterministic: pure decimal scaling, no locale, no pattern DSL (same
  precedent as `time.format`, §D-STDLIB). Cross-target golden cell.

**Done (14/09, owner 192.168.100.18):** S1b.3 — `kof_math_roundTo(Double,Int)`
on the 5 targets. **Arithmetic** contract (not decimal-string): `p=10^|d|` by
REPEATED multiplication (each step is 1 correctly-rounded IEEE op →
byte-identical); `d>=0`: `roundHalfAway(v*p)/p`, `d<0`: `roundHalfAway(v/p)*p`
(negative decimals round to tens/hundreds); `|d|` saturates at 308; overflow of
`v*p` → returns `v` (no-op). `roundHalfAway` = trunc + remainder correction
(`|f|>=0.5` → ±1; avoids the `floor(x+0.5)` double-rounding). Locked
consequence: `roundTo(2.675,2)==2.68` (the double `2.675*100` rounds to
`267.5`). No libm. Backends: JVM (`JvmStringMathRuntime`), SCRIPT (reflection),
JS (`kofMathRoundTo`), x86 (`RuntimeMath`), riscv (slice B32, aarch via
translator). Proof: `KofMathTest.roundTo{Jvm,Native,Js,CrossArch}` + SEM025
guard + `ConformanceMatrixTest.stdmathround` (4 targets) +
`KofScriptStdlibParityTest.mathRoundToParity`.

### 4. §179 — declared `kof.ui`/`kof.media` type → **option A (map the builtin)**
`MemberResolver.resolveType`, after `qualifyDeep`, maps `ClassType("", name)`
to `KofUi.constructorType(name)`/`KofMedia` when `name` is a builtin UI/media
type **and** it was not resolved by an import/module class — **user shadowing
is preserved** (a user class named `Label` still wins). Fixes the JVM
`VerifyError` (`LLabel;` descriptor vs `int` handle) for declared
var/param/field/return of UI/media types.

**Done (14/09, owner 192.168.100.18):** the mapping lives in
`CompilerTypes.qualifyDeep` (step 2b: after `simpleNamePackage` returns null and
neither the module nor the `SymbolTable` declares the name), via
`builtinDeclaredType` (`KofUi.typeByName` for every UI type + `KofMedia.IMAGE_DATA`)
and the `unitDeclaresType` shadowing guard; `MemberResolver.resolveType` routes
through `qualifyDeep`, and `StatementLowerer`'s `VarDeclStmt` now resolves with
the semantic analyzer (the 2-arg `toType` skipped `qualifyDeep`, so a declared
local kept the empty package). Proof: `ComponentCoreE2ETest.
declaredUiAndMediaTypesCompileAndRun` + `userClassShadowsBuiltinUiTypeName`
(JVM+Native+JS).

### 5. `app.security()` → **inspired by Spring Security (option: framework model)**
Refine the composite middleware to the **Spring Security mental model**, keeping
the Kof surface idiomatic and the output deterministic:
- **`HttpSecurity`-style chain:** the middleware order is fixed and
  framework-owned (not hand-composed by the user) — the current fixed order
  (rate-limit → CORS → headers → session → CSRF → auth → RBAC) is exactly
  Spring's filter-chain idea.
- **`authorizeHttpRequests`:** public paths are an **allow-list** of matchers;
  everything not matched requires authentication. Reads are NOT implicitly
  public: **the default is authenticated** (Spring's `anyRequest().authenticated()`),
  with explicit `permitAll` matchers. This reverses the interim "reads public"
  choice from the merge — the blog E2E sends the session token on its GETs.
- **CSRF:** on by default for state-changing methods (Spring enables it by
  default); safe methods issue the cookie. **Session:** header-token mode stays
  (Kof has no servlet session), same "authenticated by default" rule.
- **Native/JS:** remain an honest gap (`WEB006`).

> **✅ EXECUTED (14/09, owner 192.168.100.18):** `app.security()` adopted the
> Spring model above. **CSRF ON by default** once `app.security()` is configured
> (`kof_web_security_opts` sets `securityCsrf = true`; `csrf:false` disables
> explicitly) — safe methods issue the double-submit cookie, state-changing
> methods require it. **Reads are authenticated by default**: the session guard
> already enforces auth on every non-`publicPaths` request (including GET), so
> the merge's "reads public" note was stale. **`permitAll`** accepted as an
> alias of `publicPaths` (allow-list of matchers). No-arg `app.security()` stays
> headers-only (GET `/hello` → 200), and apps that never call `app.security()`
> are unaffected. Proof: `KofWebE2ETest` 25/25 (new `securityCsrfIsOnByDefault`,
> `securityPermitAllAliasIsPublicPaths`) + `KofBlogE2ETest` (double-submit on
> its POSTs) + `KofOAuthResourceServerTest` 4/4.

### 6. §180 — `println(double/float)` in Native x86 → **inspired by Java**
Align the Native to **`Double.toString`/`Float.toString` (Java)**:
shortest round-trip decimal, `Float` printed with its own shortest form (not
the double expansion), Java's scientific-notation threshold (`1e7`→`1.0E7`,
`1e-3`→`0.001`). Inspected from Java (Ryu/Grisu-style shortest representation;
a bounded `%.{1..17}g`+`strtod` round-trip loop is an acceptable deterministic
implementation) — no reinvention of the algorithm beyond what the JDK already
defines. Cross-target golden cell (`floatprint`).

> **✅ EXECUTED (15/09, owner 192.168.100.18):** the Native x86_64 now uses the
> bounded `%.*e`+`strtod` round-trip loop (the option sanctioned above). New
> runtime fragment `RuntimeDtoa` emits `kof_dtoa_format`,
> `kof_double_to_string` and `kof_float_to_string`: for each precision `0..16`
> (double) / `0..8` (float) it formats with `snprintf("%.*e")` and re-parses
> with `strtod`, keeping the **shortest** precision that round-trips bit-exact;
> then it reformats to Java's style (scientific only when `|x|>=1e7` or
> `<1e-3`, uppercase `E`, mantissa always with a fractional part) and `Float`
> keeps **its own** shortest form (not the double expansion). `RuntimePrintNum`
> (the unboxed `print`), `RuntimeJsonEncode` (NaN/±Inf → `null`) and the
> `RuntimeCollectionToString` `.Lce_double`/`.Lce_float` branches delegate to it;
> `RuntimeStringConv` no longer carries the float/double conversion (int/char/
> long/bool only). **libc constraint:** `snprintf`/`strtod` are needed, so this
> is **x86_64 only**; riscv/aarch stay `FLT001`. The Kof runtime `_start` does
> **not** guarantee 16-byte stack alignment, so each dtoa entry point does
> `andq $-16, %rsp` before the libc calls (glibc `movaps` needs 16B — the
> misalignment was the SIGSEGV root cause). Proof: `ConformanceMatrixTest.doubleprint`
> (Native exclusion removed, extended with `0.001`/`1.0E-4`/`3.4028235E38`/`-0.0`)
> — Native == JVM byte-for-byte; `KofMathTest` 29/29, `JsonE2ETest` 18/18,
> `JsonCompleteE2ETest` 9/9, `NativeRuntimeSliceRegistryTest` 7/7,
> `ConformanceMatrixDocTest` green.

---

## D-NULL — §125/SEM048 amendment: "null for primitives" was a MISREAD (✅ decided 15/09, maintainer, in person)

> **The maintainer corrected the record directly: "I do NOT forbid null for
> primitives — I forbid null when there is null safety. You all understood it
> wrong."** Every catalog entry that read §125/SEM048 as *"a primitive can never
> hold null, therefore a boxed `Int?` is forbidden (rule 6)"* is **wrong**, and
> the "boxed `T?` reopens §125 → rule 6" reasoning that parked #259/#266/#252
> (and that justified the `6553ac2e` revert of §241) is **void**.

What §125/SEM048 actually forbids is **fabricating `null` at a site that has no
null-safety** — the `null` **literal** in an assignment / return / argument.
Null safety is by **narrowing** (`if (x != null)`); `null` arrives only from an
API that returns `T?`. Two consequences, both now the contract:

- **(a) `Int?`/`Boolean?`/`Double?` CAN genuinely hold `null`.** The boxed
  `Nullable(primitive)` face (the #252 family) is **NOT a rule-6 freeze** — §125 never
  banned it. The work is *"complete the boxing across the 4
  targets"* (JVM + Script + JS + Native in lockstep — exactly what §241's
  half-landed change failed to do), tracked as the §241/#266/#259 queue, **not**
  parked behind a decision that doesn't exist.
- **(b) A bare `Int` (no `?`) still has no null** — and the compiler must say so
  at **compile time**, never a silent `VerifyError` at load. `f(null)` where `f`
  takes a non-nullable `Int` is **SEM048** (mirrors the `x = null` assignment
  guard and the `return null` §125 guard). Implemented 15/09 by lane
  bugs-and-gaps `192.168.100.15`: `SemanticAnalyzer.checkNullArgs` shared guard
  + post-pass over `resolvedMethods`/`resolvedConstructors` (instance method +
  constructor) + a call-site check in `BuiltinCallTyper`'s top-level branch (the
  resolved maps never see top-level calls). A `NullableType` formal NEVER hits
  the guard — `handle(Boolean? flag)` + `handle(null)` must compile (that's
  (a), the #266 core case). Proof: `NullArgPrimitiveParamE2ETest` 7/7 (4 rejects
  instance/ctor/top-level/Long + 2 non-regression "no SEM048 on Boolean?/String?
  + null" + real-value pass); cross-target the diagnostic is identical on
  jvm/js/native and under `kof run --target script` (shared frontend).

> **✅ DECIDED 15/09** — §125 stays FROZEN as "no fabricated null literal", but
> its *scope* is corrected: it never banned boxed `T?` null. The boxed-4-targets
> work is **not blocked by rule 6** — but whether/when to open it as a work front is
> the **maintainer's decision** (the lane only records the corrected scope, §rule 6).
> Related catalog: §250 (part (c), fixed), §241/#252/#259/#266 (the boxing queue).

---

## How to update this doc

Decided anything else in the chat → lock it here (date + option + code
evidence when there is any). A decided item that becomes code: leaves here for the queue
of `roadmap.md` §23/DOING; the line stays marking `✅ decided <date>` (the
record is permanent — the `decision-pending/` folder no longer exists).
