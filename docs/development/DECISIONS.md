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
> **`app.security()` (C18) still NOT implemented** — it depends on the
> `app.use` middleware of the app model (I2), which is the next unit of this front.

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

## How to update this doc

Decided anything else in the chat → lock it here (date + option + code
evidence when there is any). A decided item that becomes code: leaves here for the queue
of `roadmap.md` §23/DOING; the line stays marking `✅ decided <date>` (the
record is permanent — the `decision-pending/` folder no longer exists).
