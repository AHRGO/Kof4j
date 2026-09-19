[Português](DECISIONS.pt_BR.md) | [English](DECISIONS.md)

# DECISIONS — language decision record

**Last updated:** 2026-09-15

**Maintainer:** Mel Santos

**Nature:** normative and historical record of language architecture, semantics, and evolution decisions.

> This file records decisions that have already been made. It is not a backlog, an implementation diary, or a collection of open proposals.

> A decision recorded here remains the source of truth until it is formally superseded by another decision. Code, tests, and the roadmap must converge on this contract.

---

## 1. Authority and change rules

### 1.1 Who decides

The maintainer decides matters of contract, semantics, architecture, and language direction.

Agents and contributors may:

* investigate alternatives;
* propose decisions;
* implement approved decisions;
* fix bugs and divergences from the contract;
* update execution evidence and status.

Agents and contributors **must not alter the contract of a decision through their own interpretation**.

### 1.2 How a decision enters this file

A decision is considered effective only when recorded with:

* stable identifier;
* date;
* scope;
* contract;
* decision taken;
* relationship to previous decisions, when applicable;
* implementation status, if any.

The decision does not live in chat. Chat may contain the discussion; this file contains the normative outcome.

### 1.3 How a decision is revised

An effective decision may only be changed by a new entry that:

1. identifies the previous decision;
2. explains what changes;
3. records the new decision;
4. preserves the history;
5. updates the roadmap and affected documentation.

A superseded decision is not deleted.

### 1.4 Allowed states

| State         | Meaning                                                  |
| ------------- | -------------------------------------------------------- |
| `DECIDED`     | Contract approved, not yet fully implemented             |
| `IN_PROGRESS` | Implementation in progress                               |
| `IMPLEMENTED` | Implementation completed and validated                   |
| `PARTIAL`     | Part of the contract implemented; explicit gaps remain   |
| `BLOCKED`     | Implementation depends on another decision or capability |
| `SUPERSEDED`  | Replaced by another decision                             |
| `REJECTED`    | Analyzed alternative that was rejected                   |
| `CLOSED`      | Record closed with no remaining backlog                  |

Implementation status **does not alter the contract**.

---

## 2. Global invariants

These rules remain valid regardless of the decisions below.

### G-01 — Surface freeze

The 0.2.6-beta freeze remains in effect for explicitly frozen items:

* operators;
* `==`;
* `spawn`;
* collections;
* other items covered by rule 6.

A later decision may alter a frozen contract only when it explicitly records the corresponding revision.

### G-02 — Universal vision

R1–R12 remain invariants of the language's universal vision.

### G-03 — Complexity limit

The ≤500 rule remains in effect.

### G-04 — Suite as gate

The conformance suite remains the integration gate. Code cannot be considered complete merely because it compiles locally.

### G-05 — Honest gap

When a capability does not exist on a given target, the system must:

* report the documented gap;
* use the corresponding diagnostic code;
* never produce a silently incorrect result;
* never simulate nonexistent support as if it were real support.

### G-06 — Parity

When a decision defines observable behavior, implementation must seek the same contract across all supported targets.

A difference between targets is acceptable only when:

1. it is explicitly documented;
2. it has a defined diagnostic or behavior;
3. it is represented in the conformance matrix.

### G-07 — Determinism

The same input, same contract, and same target must produce a deterministic result.

When the decision requires cross-target parity, the observable result must be equivalent, except for explicitly recorded gaps.

### G-08 — Do not reinvent the wheel

The compiler and runtime internals should primarily draw inspiration from:

1. Java — JLS, JVMS, and `java.lang`/`java.math` behavior;
2. C — ISO C and `libm`, especially for the native backend;
3. other languages, when the reference is a backend technique.

This rule does not authorize copying another language's surface. Syntax, ergonomics, and writing model remain Kof-specific decisions.

---

# 3. Current decisions

## D-STDLIB — time and calendar

**Date:** 2026-09-13

**State:** `IMPLEMENTED`

**Scope:** date, time, and calendar semantics of the stdlib.

### Contract

**D-STDLIB.1 — UTC as the default reference**

`today()` and `isToday` derive from `now()` in UTC on all targets.

The local timezone may only be obtained through an explicit API:

```kof
time.tzOffsetSeconds()
```

Native without timezone support reports `TIME003`.

There is no accidental timezone parity between targets.

**D-STDLIB.2 — Scalar calendar**

The base calendar API uses scalar values over ISO.

Example:

```kof
time.addDays("YYYY-MM-DD", n) -> String
```

The base stdlib does not introduce compound return values for calendar operations.

**D-STDLIB.3 — Hour difference**

`hoursBetween` counts complete integer hours, truncated toward zero, consistent with `daysBetween`.

It does not use float nor a 12-argument signature.

**D-STDLIB.4 — ISO formatting**

```kof
time.formatDateIso(y, m, d) -> String
```

Invalid date returns `""`.

**D-STDLIB.5 — ISO parsing**

```kof
time.parseDateIso(value) -> Int
```

Returns the `daysFromEpoch` serial.

Invalid input returns `0`.

Arbitrary patterns such as `dd/MM/yyyy` are not part of the base stdlib.

**D-STDLIB.6 — isToday**

```kof
time.isToday(y, m, d) -> Bool
```

Compares against the UTC date derived from `now()`.

### Ratified API

| Function                    | Contract                  | Targets                   |
| --------------------------- | ------------------------- | ------------------------- |
| `time.todayIso()`           | `() -> String`            | 5                         |
| `time.formatDateIso(y,m,d)` | `(Int,Int,Int) -> String` | 5                         |
| `time.isToday(y,m,d)`       | `(Int,Int,Int) -> Bool`   | 5                         |
| `time.hoursBetween(...)`    | `(Int × 8) -> Int`        | 5                         |
| `time.parseDateIso(String)` | `String -> Int`           | 5                         |
| `time.tzOffsetSeconds()`    | `() -> Int`               | JVM/JS/SCRIPT; Native gap |

### Evidence

S7e–S7h implementation completed on 2026-09-13.

* `KofTimeE2ETest`: 30/30
* `stdtime3`–`stdtime6` matrices
* Script parity
* Suite: 1772/0/0

**Implementation reference:** `docs/stdlib/time.md`

**Queue:** closed.

---

## D-SEC — security

**Date:** 2026-09-13–14

**State:** `PARTIAL`

**Scope:** cryptography, cookies, security middleware, and OAuth2/OIDC.

### Invariants

* Cryptography is not implemented from scratch.
* JVM uses JCA where applicable.
* JS uses WebCrypto where applicable.
* Native uses an audited implementation or reports a gap.
* Algorithms, token formats, and validation rules are observable contracts.
* Gaps are reported through `SECN00x`.

### D-SEC.1 — ChaCha20-Poly1305

**Decision:** add ChaCha20-Poly1305 support according to RFC 8439.

Format:

```text
chacha20$<nonceB64(12B)>$<ct+tagB64(16B tag)>
```

API:

```kof
security.chacha20Encrypt(text, keyHex) -> String
security.chacha20Decrypt(token, keyHex) -> String
```

The key must be 32 bytes represented in hexadecimal.

Nonce must never be reused with the same key.

Native without implementation reports `SECN002`.

**State:** JVM + JS implemented. Native remains a gap.

**Evidence:** RFC 8439 vector + `node:crypto` + `KofSecurityTest`.

### D-SEC.2 — Cookies

API:

```kof
security.cookieSet(name, value, opts)

security.cookieGet(header, name)
```

Defaults:

* `HttpOnly`;
* `Secure`;
* `SameSite=Lax`;
* `Path=/`.

Native without implementation reports `SECN006`.

**State:** JVM + JS implemented.

### D-SEC.3 — `app.security()` middleware

The pipeline order is fixed:

```text
rate-limit

→ CORS

→ security headers

→ cookies/session

→ CSRF

→ authentication

→ RBAC

→ route
```

The user configures policies but does not reconstruct the internal order.

Without arguments, `app.security()` applies hardening defaults.

In production, `listen`/`listenSecure` without `app.security()` emits a warning.

### D-SEC.4 — Default authentication

When `app.security()` is configured:

* authentication is required by default;
* read methods are not implicitly public;
* public paths must be declared through an allow-list;
* `permitAll` is an alias for `publicPaths`;
* an invalid token never passes silently;
* CSRF is enabled by default for state-changing methods;
* `csrf:false` explicitly disables this protection.

### D-SEC.5 — OAuth2/OIDC

Implementation follows this order:

1. resource server;
2. client authorization-code + PKCE;
3. provider: out of scope.

The resource server validates third-party JWTs through JWKS, issuer, and audience.

Allowed algorithms:

* RS256/384/512;
* ES256/384/512.

Rejected:

* `none`;
* HS*;
* algorithms outside the allow-list.

Native/JS without implementation report `SECN007`.

### D-SEC.6 — TLS

API:

```kof
app.listenSecure(port, certPem, keyPem)
```

The key must be in PKCS#8 PEM.

JVM is the first target.

Self-signed remains a development convenience, not a production configuration.

### Evidence

* `KofSecurityTest`
* `KofWebE2ETest`
* `KofBlogE2ETest`
* `KofOAuthResourceServerTest`

**Reference:** `docs/stdlib/security.md`

**Implementation:** `docs/stdlib/stdlib-web.md`

**Remaining queue:** Native crypto/TLS and documented gaps.

---

## D-APP — application model

**Date:** 2026-09-13

**State:** `PARTIAL`

**Scope:** manifest, component composition, and application execution.

### Contract

**D-APP.1 — Manifest**

The application manifest is:

```text
kof.toml
```

It is optional.

Without a manifest, current behavior must remain 1:1.

**D-APP.2 — Application unit**

An application is a directory containing a Kof module and a `main()`.

Possible components:

* backend;
* frontend;
* static.

An application is the smallest unit of `kof serve` and deployment.

**D-APP.3 — Frontend**

Frontend is another Kof module compiled to JS.

The backend does not call frontend functions directly.

Front→back communication occurs through HTTP/JSON.

**D-APP.4 — System**

System is deployment composition, not compilation composition.

`kof serve --system` remains rejected.

The approved alternative is:

```text
kof serve --list
```

**D-APP.5 — Fat jar**

The flag:

```text
kof build --fat
```

is optional.

The default remains an explicit classpath.

**D-APP.6 — Rebuild**

The frontend is rebuilt on demand by hash.

Watcher remains future.

**D-APP.7 — Targets**

Wasm enters the matrix when the target is opened.

Android is declared supported through the WebView + KofJS model, without changing the application model.

### Manifest

Sections:

```toml
[app]

[serve]

[frontend]

[static]
```

The manifest is read by the CLI, not by the compiler.

Manifest errors must produce a clear diagnostic.

### Allowed topologies

* monolith;
* modular monolith;
* microservices;
* microfrontends;
* full-stack;
* backend-only;
* frontend-only;
* distributed full-stack;
* gateway.

The model does not change between these topologies.

### Evidence

* `KofProjectConfig`
* `CmdBuildFatTest`
* `KofBlogE2ETest`
* `KofWebE2ETest`

**Reference:** `docs/architecture/application-model.md`

**Queue:** `CmdNew` ✅ (`new` in `Main.java:37`); manifest/dependency integration ✅ (`kofdeps` + transitive lock 1.5.2 + registry pull 1.5.3-S2, 19/09); target gaps → tracked in `docs/backend-parity.md` (ledger, not this record).

---

## D-SPRING — framework independence

**Date:** 2026-09-13 · **Concluded:** 2026-09-19 (audit vs code, this commit)

**State:** `CONCLUDED`

### Contract

* No stdlib component depends on Spring.
* No backend generates Java source as a mandatory step.
* Fundamental capabilities have a Kof-native API.
* Spring may be consumed as an interoperability alternative.
* The independence test has the same weight as the interoperability test.

### Phases

| Phase               | State         |
| ------------------- | ------------- |
| 1–9                 | `IMPLEMENTED` |
| 10 — native testing | `IMPLEMENTED 19/09` (`kof test` harness: `test "nome" { }` → runner sintetizado, `CmdTest.java:15,78`; CliFlagStrictness/CmdBuildAndroidAab cover the face; suite 2772/0F) |
| 11 — complete CLI   | `IMPLEMENTED 19/09` (run/build/test/serve/fmt/deps/init/check all wired in `Main.java:18-41`; deps = Maven + registry 1.5.3-S2) |
| 12 — blog E2E       | `IMPLEMENTED 19/09` (`KofBlogE2ETest` green in the full reactor suite) |

### Phase 10

`kof test` must execute project tests with Kof asserts.

JUnit is not mandatory.

Initial scope:

* unit;
* HTTP with `web.app` on an ephemeral port.

Property testing, stress, and mocks are separate increments.

### Phase 11

Consolidate:

```text
run

build

test

serve

fmt

deps

init

check

new
```

`kofdeps add/remove/list/resolve` already exists.

Dependency integration into the manifest remains.

### Phase 12

The blog E2E is the canonical validation application for the platform:

* backend;
* frontend;
* database;
* authentication;
* validation;
* manifest.

Validation must be performed per target, with honest gaps.

---

## D-RELEASE — patch evaluation criterion

**Date:** 2026-09-14

**State:** `DECIDED`

### Contract

The 0.4.0 line has already been released in #138.

Development continues normally after the release.

When beta is between 100 and 150 commits ahead of main, a patch release should be evaluated.

### Rule

```text
git rev-list --count origin/main..origin/beta-0.4.0
```

When crossing the range:

1. open a release issue;
2. run the complete suite;
3. close known issues in the bugs/parity lane;
4. evaluate the accumulated content;
5. update the version only after the evaluation.

### SemVer

If there is a new material capability that changes the contract, operator, or API surface, the evaluated version ceases to be a patch and becomes minor.

The counter does not freeze features.

Features and fixes completed with a green suite enter the package.

**Status recorded on 2026-09-14:** `main..beta = 1`.

---

## D-ASM-GATE — riscv/aarch ASM gate

**Date:** 2026-09-14

**State:** `DECIDED`

### Contract

The specific ASM inspection gate for riscv/aarch is optional while native development is incomplete.

Main and beta must never remain with broken tests.

### Rule

The gate may be reactivated with:

```text
KOF_ASM_GATE=1
```

The default is an explicit skip.

The test body remains portable:

* if `.s` exists, inspect the text;
* if it does not exist, require the linked binary.

### Protection maintained

The duplicated-label regression remains covered by the E2Es under qemu.

### Reactivation

When the Native lane is complete and the 5/5 matrix is green, `assumeTrue` must be removed and the gate becomes mandatory again.

---

## D-BACKEND-SEMANTICS — backend semantics

**Date:** 2026-09-14–15

**State:** `IMPLEMENTED`

This section records semantic decisions that have already been ratified and implemented.

### §101 — NaN in relational comparisons

**Decision:** pure IEEE 754.

Relational comparisons with NaN return `false`.

`!=` returns `true`.

All targets must agree.

### §129 — cross-thread unwind

**Decision:** exception frame per thread.

The exception chain is thread-scoped.

A worker without an internal handler publishes the exception to the handle.

The consumer rethrows it in `await`, `await_timeout`, and `select_any`.

**Extension 19/09 — riscv64/aarch64 mechanism (maintainer's decision in chat):**
the same §129 contract is ported to the cross targets using **real TLS via
`clone`** (not a per-TID table). Each thread gets its own chain head: the main
thread in `_start` (our entry point — it does **not** go through
`__libc_start_main`, so `tp` is ours to set) and each worker via
`CLONE_SETTLS` + a per-worker TLS block (the clone flags already carry
`CLONE_SETTLS`; today `a3`/tls is passed as `0`). `kof_spawn_trampoline`
installs the per-worker handler frame and publishes the cause on `handle->exc`
(offset 48 on riscv), as the x86_64 option B does. **Known blocker to solve in
the implementation:** the aarch64 translator maps riscv `tp` → `x4`
(`NativeAarch64Helpers:74`), which collides with `a4` → `x4` (`:98`) and does
not read `TPIDR_EL0` (the real aarch64 thread pointer, via `mrs`) — the shared
access sites must be made to work on both arches before the port lands.
Evidence of the RED baseline: two cross tests mirroring the x86 §129 hang under
qemu (worker `throw` longjmps the global `kof_exc_chain` of `main`).

**Correction 19/09 — TLS-via-`tp` is ABI-unsafe; mechanism changed to a per-TID
table (measured, agent).** The "real TLS via `clone`" mechanism above was
implemented and **provably breaks libc**: overwriting the thread pointer
(riscv `tp`=x4 / aarch64 `TPIDR_EL0`) desynchronizes the C library's own TLS.
Under qemu this produced `SIGSEGV` (exit 139) in aarch64 tests that call
`snprintf`/`strtod` through `RuntimeDtoa` (B45): `nativeValueOfDoubleFloatMatchesJvmGolden`,
`aarch64NegativeFloatDoubleRuns`, `nativeCollectionPrintMatchesJvmGolden`. On
riscv the same change also regressed `crossNativeConcurrencyHelpersRun`
(`done(a)` true→false). Root cause: our `_start` is our own, but any Kof program
can still call libc (dtoa/format), so `tp` is **not** ours to repurpose.
**Resolution (deviation from the mechanism, contract unchanged):** the §129
*contract* (thread-scoped chain, worker publishes to `handle->exc`, consumer
rethrows in `await`/`await_timeout`/`select_any`) is kept exactly; only the
*mechanism* changes to a **per-TID table** `kof_exc_slots` (256 entries × 16 B
`[tid, chain]`, key `gettid`=a7 178, linear probe, same pattern as
`kof_cancel_slots`/CONC001), with a `kof_exc_slot()` helper returning
`&chain` for the current thread. This is the safer second option and does not
touch the thread pointer. Proof: the two cross tests now pass green on
riscv64/aarch64 under qemu (`KofConcurrency2Test`
`spawnWorkerThrowIsolatedFromSiblingsCrossArch` +
`spawnWorkerThrowUnhandledPropagatesCrossArch`, 138/0 in the run of 19/09).

### `roundTo`

**Decision:** arithmetic decimal rounding.

```kof
math.roundTo(value, decimals)
```

* same numeric type as the value;
* `decimals = 0` rounds to an integer;
* negative values round to tens, hundreds, etc.;
* half-away-from-zero mode;
* no locale;
* no pattern DSL;
* deterministic.

### §179 — builtin UI/media types

Declared builtin types are mapped by the resolver without breaking user shadowing.

A user class with the same name continues to win.

### `app.security()`

The mental model is inspired by Spring Security:

* fixed filter chain;
* authentication by default;
* explicit allow-list for public routes;
* CSRF by default;
* Kof-owned surface.

### §180 — float/double printing

Native must align with the observable behavior of Java's `Double.toString` and `Float.toString`:

* shortest round-trip;
* `Float` maintains its own representation;
* scientific notation according to the defined threshold;
* uppercase `E`;
* mantissa with a fractional part.

---

## D-BASELINE — toolchain baseline

**Date:** 2026-09-14

**State:** `IMPLEMENTED`

The repository build baseline moves from Java 21 to Java 25.

This changes the repository toolchain, not the minimum runtime of Kof programs.

### What changes

* `pom.xml`: `release=25`;
* CI;
* CodeQL;
* release;
* benchmark;
* Android tooling;
* embedded JDK in `package.sh`;
* build documentation.

### What does not change

* `JvmBackend` continues emitting V21 bytecode;
* Android continues with `release="21"`;
* `KofVersion.TOOLING_API=21`;
* Kof programs continue with minimum JVM runtime 21+.

---

## D-NULL — nullability and primitives

**Date:** 2026-09-15

**State:** `DECIDED`

**Revision of:** §125 / SEM048

### Historical correction

The previous decision was interpreted incorrectly.

The contract never prohibited `T?` boxed from carrying `null`.

What is prohibited is fabricating `null` at a point without null-safety.

### Contract

* `Int?`, `Boolean?`, `Double?`, and other `T?` types may carry `null`;
* `Int`, `Boolean`, `Double`, and other non-nullable types do not carry `null`;
* `null` literal at a non-nullable point is a compile-time error;
* boxed `T?` is not frozen by rule 6;
* behavior must be completed across targets in lockstep.

### State

Boxed nullable work remains in queue §241/#252/#259/#266.

The previous catalog that treated this as prohibited has been corrected.

---

## D-NULL-INTENT — explicit nullability intent

**Date:** 2026-09-15

**State:** `DECIDED`

> **Revision 19/09 (`D-TROOL`):** `Bool` left this family as a nullable
> surface — `Nullable(Bool)` is now refused with `SEM095` and the three-state
> type is `Troolean`. The rest of the contract (real boxed `T?` for
> primitives/refs, `= null` refusal, null-default of uninstantiated
> declarations) stands unchanged.

**Revision of:** option A of §125

### Contract

Null is not expected by default.

A declaration without explicit intent cannot produce or carry `null`.

Nullability intent is expressed through the comparison:

```kof
if (x == null) { ... }

if (x != null) { ... }
```

This applies to all types.

When intent exists:

* `T?` may carry real null;
* `x == null` must respond correctly;
* `println(x)` must print `null`;
* behavior must be equivalent across all targets.

The `null` literal remains prohibited at non-nullable points.

### Implementation rules

* `Int?` and other nullable primitives must use real boxed representation;
* there can be no silent folding of `null → 0`;
* there can be no silent folding of `null → false`;
* unboxing null must have defined behavior;
* uninitialized fields must have defined behavior;
* map-miss must not invent a value.

### Queue

1. JVM + Script + JS;
2. Native;
3. intent in non-nullable declarations;
4. audit and elimination of silent paths.

### Scope protection

Implementation of the intent core is under the maintainer's responsibility.

Lanes must not attack the boxed-nullable core of #266/#259 without new authorization.

**Authorization (16/09, maintainer via chat, hierarchy rule):** the maintainer
explicitly delegated the D-NULL-INTENT front to the agent lane (`192.168.100.22`,
issue-watcher/compiler) — the "new authorization" requirement above is hereby
met. The boxed-nullable core of #266/#259 is UNLOCKED for implementation by
this lane, following the D-NULL-INTENT queue (1. JVM + Script + JS; 2. Native;
3. intent in non-nullable declarations; 4. audit and elimination of silent
paths). The contract itself (explicit intent via `== null`, real boxed `T?`,
no silent folding) is unchanged. Recorded here by the lane before/with the
implementation, per the hierarchy rule (a later explicit maintainer directive
supersedes a documented restriction).

Lanes may work on:

* SEM048/SEM049 audit;
* catalog of silent paths;
* fixes that do not overlap the protected implementation.

---

## D-PRINT — implicit Char conversion

**Date:** 2026-09-15

**State:** `DECIDED`

### Contract

`println` prints `Char` as a character.

```kof
println('A') // A
```

Concatenation also preserves the character:

```kof
"char: " + 'A' // char: A
```

Numeric conversion requires an explicit API.

Storing `Char` in collections remains a separate contract.

---

## D-NARROW-WHILE — flow narrowing

**Date:** 2026-09-15

**State:** `DECIDED`

Existing narrowing in `if` must be extended to:

* `while` condition;
* class-field receivers;
* scopes narrowed by null comparison.

Reassignment within the narrowed scope does not change the declaration's nullability.

The SEM012 false positive from case #159 must be eliminated.

---

## D-ENUM207 — enum identity

**Date:** 2026-09-15

**State:** `IN_PROGRESS`

Enum identity implementation was reassigned to the bugs-and-gaps lane.

The semantic change:

```kof
Dir.N == "N"
```

must be treated as a contract decision, not as a simple local fix.

The final decision on the semantics remains recorded in this section before changing the behavior.

---

## D-VALUE-RECORD — value records / first-class value types

**Date:** 2026-09-16

**State:** `DECIDED`

**Origin:** issue #275 (feature proposal).

### Context

Kof already has concise immutable `record` (e.g. `record Vec2(Float x, Float y)`),
but has no way to explicitly declare that a user-defined aggregate has
**value semantics and no object identity**. For small data-oriented types
(vectors, coordinates, colors, ranges, parser tokens, iterator state),
requiring a separate object allocation adds allocation pressure, GC work,
indirection and worse cache locality. Relying on JVM escape analysis does not
express intent and does not hold across the Native/JS backends.

### Decision

The suggestion is **accepted**: add an entry to the implementation queue in
`docs/development/future/` for future engineering and development of a value
form of `record` (`value record`).

### Contract

* A `value record` has the same concise immutable data-model as an existing
  Kof record, but explicitly no observable object identity.
* Equality/hash by fields (already the record contract).
* Additive and backward compatible: ordinary `record` retains its existing
  semantics; existing code keeps compiling and running (rule 2).
* Per-target ABI is an explicit scope decision before code (R7 honest scope):
  JVM → value/inline class; Native → pass-by-value (struct by value/registers);
  JS → plain frozen object.
* Boundary: core stdlib, not an official package (R1).

### Status

Planned only — **not current development**. No implementation in progress.
Lanes must not open this front without a new authorization (rule 6 / R12:
new fronts do not open before the SYSTEMS stage closes).

### Implementation

Queue entry added to `docs/development/roadmap.md` §23 (TIER 2) and this
decision recorded; engineering scheduled for the future queue.

### Relationships

* `Related:` #275 (issue)

---

## D-DEV-PRIORITY — "Em desenvolvimento" is the absolute priority of every lane

**Date:** 2026-09-16

**State:** `ACTIVE` (supersedes any per-lane preference ordering)

The maintainer's rule (16/09, chat): **total priority is completing the
`Em desenvolvimento` roadmap** — every agent, every lane, every autonomous
re-trigger picks its next task from this list, in order, before anything else
(other gaps, other queues, new fronts). Cataloguing and bug-fixing continue as
usual (the quality gate is never relaxed), but TASK SELECTION follows the
fronts below.

**The fronts (as stated by the maintainer 16/09):**

1. **Standard Library** — contracts in stabilization (the S-series:
   `PLAN-STDLIB-EXPANSION`; faces still open ride the per-item queue).
2. **GC auto-collect** — safe-points + per-frame root map.
3. **Package manager beyond MVP** — registry.
4. **Debugger beyond JVM MVP** — DAP over stdio is already on JVM; JS
   source maps line ✅; DWARF Native line ✅ partial — native
   variables/expressions and breakpoints pending + VS Code ext.
5. **KofJS — the web platform in the browser** — ES Modules via GraalJS;
   web server base ✅ (`HttpServer` + `KofJsWebQueue`); SSE handler-scoped ✅
   16/09 (`7cd69a7b`); residual per feature: ws = **WEB004**, TLS = **WEB002**,
   sse post-return push/multi-client = **WEB003**, path params/keep-alive =
   **WEB001** (canonical row: `backend-parity.md` "web on Native/JS").
6. **kof.web in Native** — residual per feature: TLS = **WEB002**, path
   params/keep-alive = **WEB001**, ws = **WEB004**, sse = **WEB003**.
7. **kof.db/orm in JS** — **DB001 CLOSED 16/09** (untyped `connect/execute/query/close/transaction` on the GraalJS-host bridge — `3e55df51`+`eb9140cb`); residual typed `db.query<T>` = `DB002` CLOSED 18/09: guest-side bind via `__kof_decode_<T>` (the host bridge has no `Class.forName` for JS classes — the wire stays untyped) + `kof.orm` = `ORM001` CLOSED 18/09: `KofJsOrmBridge` runs the same SQL as `JvmOrmRuntime` on the GraalJS host, typed records bound guest-side via `__kof_decode_<T>` (byte-parity E2E; WASM planned).

**Relation to the other rules:** this decides **order**, not **what is
acceptable** — Q0–Q7, the freeze, rule 6 and the three-states rule keep all
their force. A blocked front (rule 6, another owner `EM CURSO`, or the
`§258`-style gate) is recorded and the agent takes the NEXT front in this
list — the list is the queue, not a suggestion. `AGENTS.md` priority rule
("loose `.md` first") and the roadmap §23 tiers stay subordinate to this
decision while the `Em desenvolvimento` list has open items.

---

## D-DIAG-EN — tooling and diagnostics in English; docs stay EN+PT

**Date:** 2026-09-16

**State:** `DECIDED`

**Origin:** issue #324 (maintainer decision in the chat: "aprovo a tradução
completa para inglês"; scope confirmed 16/09: "tooling da linguagem 100% em
ingles mas as documentações precisam de ingles + pt. porem toda mensagem de
erro da linguagem precisa ser em ingles pro usuario. até pq kof é uma
plataforma universal").

### Context

The compiler emits ~144 user-visible message fragments in Portuguese across
parser, typer, lowerers and the JVM/JS/Native runtimes, and ~37 test files
assert those fragments. The internal-repr half of #324 was fixed by
`130aa213` (`Type.display`); the language half was blocked here as rule 6
until this decision.

### Contract

1. **Every user-visible message the language tooling emits is English**:
   compiler diagnostics (PARSE/SEM/… codes), runtime error strings thrown by
   the stdlib runtimes (JVM/JS/Native/interpreter), CLI/REPL/LSP output,
   build/decompile logs, and generated config templates.
2. **Documentation is a different axis and stays bilingual** — every doc
   keeps its EN + PT pair (`docs-lang.sh` invariants unchanged).
3. **Codes never change** — only the message text (SEM048 stays SEM048).
   Tests that match message *text* migrate with their unit; tests that match
   *codes* are untouched.
4. Rationale: Kof is a universal platform — the tool's language travels with
   the tool, not with the user's locale.

### Implementation

Queue opened in DOING (lane issues: translation in per-package units; files
staged/IN-PROGRESS of other agents are excluded from each unit until they
land). Docs quoting PT message texts (learn/training) sync as a follow-up
doc unit per package translated.

### Relationships

- Related: #324 (internal-repr half fixed by `130aa213`), R6 (surgical,
  never-silent diagnostics), G-01 (surface freeze — message *codes* frozen;
  *text* of this axis is set by this decision).

---

## D-DECL-RETURN — declared return type is law (#333)

**Date:** 2026-09-16

**State:** `IMPLEMENTED` (top-level/ctor) — `b1ea1718`, 19/09. See note below
for the method scope

**Origin:** issue #333 (maintainer decision in the chat, 16/09: "classe
definida como int deve obrigatoriamente retornar int"; "função definida como
int deve obrigatoriamente retornar int e o mesmo vale pras outras tipagem.
função de um tipo declarado deve retornar aquele tipo").

### Contract

1. **The declared return type is the law** in every backend: a function or
   method declared `T` must return a value assignable to `T`; a function
   declared `void` must NOT `return <value>` — that is a compile-time
   diagnostic (never silent re-typing).
2. **The silent re-inference `void→T` is removed from both paths** that
   today disagree (root cause measured in the #333 thread):
   `SemanticAnalyzer.analyzeMethodBody` (re-types the class symbol) and
   `CompilerFunctionLowering.lowerFunctionInner` (re-types only the
   definition descriptor — call-sites keep resolving `()V` → the
   `NoSuchMethodError`). Every call-site resolves against the **declared**
   type.
3. **Inference only where no type is declared** (`main()`, and unannotated
   top-level forms as today).
4. This is a **deliberate contract change** (freeze rule 1): code that
   today compiles by silently re-typing a declared `void` starts failing
   with the diagnostic above — approved by the maintainer with this record;
   CHANGELOG entry lands with the implementation commit.

### Implementation

Owner: compiler lane (issue sweep claimed in DOING, 17/09). Diagnostic
wording must follow D-DIAG-EN (English).

**As landed (`b1ea1718`, 19/09, `SEM093`) — measured on the 0.4.6 tip jar:**

- **Item 1 (void declared):** enforced for **top-level functions and
  constructors**; a **class method** declared `void` with `return <value>`
  still compiles via the §130/bug-26 both-sides re-inference (the maintainer's
  commit states it deliberately: "Metodos ficam de fora"). Face b of the #333
  thread (`b.m()` printing through the re-typed slot) therefore stays
  accepted-by-design unless the maintainer later narrows §130.
- **Item 2 (silent re-typing):** the `FunctionLowering` descriptor-only
  re-typing (the NoSuchMethodError half) is **gone for top-level**; the
  `analyzeMethodBody` symbol re-typing survives for methods with the call-sites
  resolving against the retyped symbol (consistent pair — no link crash).
- **Item 3 (no declared type):** for **top-level** the "as today" behavior was
  deliberately tightened — `f() { return 5 }` (unannotated) is now `SEM093`
  (proof: `VoidReturnValueE2ETest#untypedTopLevelWithReturnRejected`),
  because the untyped top-level was exactly the silent-NSME face. Untyped
  **methods** keep inferring (§130). The record here governs; a future
  relaxation is the maintainer's call (rule 6).
- **Item 4:** CHANGELOG entry lands in this same commit (EN+PT).

### Relationships

- Closes the rule-6 block on #333 (the fix was catalogued, waiting for
  exactly this decision).
- Related: D-NULL-INTENT (declared-vs-inferred contract family), bug 62.

---

## D-NOT-JAVA — Kof is not Java/Kotlin: a foreign-language feature request is NOT a Kof bug

**Date:** 2026-09-18

**State:** `DECIDED`

**Origin:** maintainer directive, 18/09 (issue sweep): "ele ta abrindo issue de
java no kof. kof não é java. não tem string builder no kof. responde todas e as
que não forem relativas a kof ou que ele usou treinamento errado devem ser
ignoradas e fechadas. adiciona isso como regra absoluta."

### Contract

1. A request for a construct that does not exist in Kof because it is
   **translated Java/Kotlin/C#** is **not a bug**: the compiler rejecting it is
   correct behavior. Examples measured in the sweep: `StringBuilder`,
   top-level `val`/`var`, `fun`/`val` keywords, `v is Car`, `"""triple
   quotes"""`, `Pair`, `it` implicit lambda parameter, `mutableListOf`, Elvis
   `?:`, `?.`, `0..n` ranges, `!!`, named arguments, Kotlin-style primary
   constructor with body, `catch (e: Type)`, `object`, `open`/`override`.
2. Handling: **answer once with the Kof idiom that replaces it** (the idiom
   table of `AGENTS.md` §"Idiom table") and **close the issue** as
   not-valid. Do not implement the foreign feature; do not "improve the
   diagnostic" of a correct rejection.
3. Exception (real work): Kof *claims* the construct in `training/`/`learn/`/
   docs and the compiler disagrees with its own documentation — that is a
   bug (freeze rule 4), and *how* the feature would exist is a design
   decision reserved to the maintainer (rule 6).
4. The rule is recorded as iron rule **§8 of AGENTS.md** (EN+PT) — absolute.

### Evidence

- Sweep 18/09: closed under this rule: #407 (top-level val/var), #417
  (`mutableListOf`), #418 (`Pair`), #422 (`it` + `any/all/none` chain), #424
  (`StringBuilder`), #425 (`object`), #406 (`v is Car`), #410 (`0..n`), #414
  (Elvis `?:`), #416 (`!!`), #411 + #412 (`: Type` syntax family), #419
  (destructured `for ((k,v) in map)`), #420 (`open`/`override`), #421
  (primary-ctor-with-body), #404 (`catch (e: Type)` form — real issue tracked
  as #427 where applicable), #364 (`"""triple quotes"""`), #367 (named args),
  #415 (`s[0]` on String — decided: strings are not indexable in Kof; use
  `charAt`).
- Corpus authority: `AGENTS.md` §"Fake idioms — DO NOT EXIST in Kof",
  `training/anti-patterns/fake-idioms.md`, `learn/15` (`is`/binding not
  supported → `switch`/`as`).

### Relationships

- Generalizes the precedent of the `let/const` sugar cluster (§263) and
  `Int.MAX_VALUE` (bug 99 / SEM050).
- Does NOT cover: bugs where the reproducer is valid Kof (e.g. #403, #336,
  #313 — those stay and got fixed) nor the nullable-primitive cluster
  (D-NULL-INTENT) nor unqualified-JDK-type resolution (§268/D-DECL family).
## D-UI-STYLE — declarative `style` (UI007)

**Date:** 2026-09-17

**State:** `DECIDED`

**Origin:** maintainer decision in the chat, answering the five open
questions of `KOFUI-AUDIT.md` §UI007 ("UI007 — design proposal"). The item
was `BLOQUEADO` by rule 6 (API surface); this record unblocks it.

### Context

UI007 asks for "declarative `style` (idiomatic CSS), own parser". The exact
surface is an API freeze, so it could not be implemented on the agent's
judgement. The existing `Style(Int, Int, Int, Int)` (background, foreground,
padding, radius — `kof_ui_style_new`) is already shipped on all four targets
(real only on KofJS; documented no-op elsewhere).

### Contract

1. **Surface.** `Style("<declarations>")` — one String literal argument,
   `prop: value;` pairs separated by `;`. It produces a `kof.ui.Style`
   value, consumed by `View(style)` exactly like the 4-Int form. The
   existing `Style(4 Ints)` is **untouched** (additive, backward compatible).
2. **Parse in the compiler (Q4).** The declarations are parsed and validated
   at compile time; the lowering carries the **normalized** CSS text. A
   non-literal argument is a diagnostic (no runtime parse).
3. **Colors (Q1).** Accept hex CSS (`#rgb`, `#rrggbb`, `#rrggbbaa`), the CSS
   color names and the `Palette` names (`red`, `cyan`, …) — the same table
   `Palette` uses. The compiler validates the value and keeps it in the
   normalized CSS (the browser resolves the name). The 4-Int `Style` form
   remains the way to pass a computed `Color` value (the String form takes a
   literal only).
4. **Units (Q2).** A bare integer means `px`; the suffixes `px`, `%`, `em`
   and `rem` are accepted.
5. **Properties (Q3).** A **typed whitelist**. A property outside the
   whitelist is a compile-time diagnostic (`SEM076`) — never silently
   forwarded to `node.style` (R6). A malformed declaration is `SEM077`; an
   invalid value for a known property is `SEM078`.
6. **Scope (Q5).** `setStyle(style)` — taking the `Style` value, exactly like
   `View(style)` — is available on **every DOM widget** (`KofUi.isDomWidget`),
   not only `View`, through the shared `kof_ui_widget_set_style` family (the
   UI005 pattern, same shape as `setFont(font)`).

### Invariants

- **No silent regression on the 4-Int form**: `Style(Int, Int, Int, Int)`
  keeps its exact semantics on all targets.
- **Honest per-target parity**: the declarative style is real on KofJS and
  a **no-op** on JVM/Native/Script, matching the existing `Style` gap
  (UI001). The no-op stays documented; it is not turned into a silent
  fallback.
- **Diagnostics follow D-DIAG-EN** (English message text; codes stable).
- The new codes are **additive** and do not renumber anything.

### Rejected alternatives

- **Parse at runtime** (Q4 option): rejected — "own parser" plus compile-time
  validation (Q3) requires the compiler; a runtime parse would also make the
  unknown-property error a runtime failure instead of a diagnostic.
- **`setStyle` only on `View`** (Q5 option): rejected by the maintainer in
  favour of the wider surface (every DOM widget).

### Implementation

Claimed in `DOING.md` (UI007, lane UI/style); roadmap §8 Frontend. Slice A =
compiler parser + `Style(String)` + lowering + JS runtime + tests; slice B =
`setStyle` on every DOM widget.

### Evidence

`UiStyleCssE2ETest` 10/10 (JVM + Native + Script + JS: happy path, CSS names,
units, `setStyle` on a Label, `SEM076`/`SEM077`/`SEM078`, non-literal,
4-Int non-regression) + `KofJsBrowserE2ETest` (headless Chrome, real DOM:
`declarativeStyleRendersInRealBrowserDom`,
`setStyleRendersOnAnyDomWidgetInRealBrowser`); full 4-module suite green
outside the pre-existing §252 flake and §181/§256 cross reds; `docs-lang.sh
check` 0/0/0.

### Relationships

- Closes the rule-6 block on UI007 (`KOFUI-AUDIT.md` §UI007).
 - Related: UI001 (silent no-op family), UI005 (shared `kof_ui_widget_*`
   family), D-DIAG-EN.

---


## D-UI-TOKENS — design-system tokens (Fase 10, pillar 9)

**Date:** 2026-09-18

**State:** `DECIDED`

**Origin:** maintainer scope authorisation (chat, 18/09 — the "all" answer
for the Component Core phases 8–11). The exact surface (namespace names,
member names, px values) is an API freeze (rule 6); it is locked here,
following the **D-UI-STYLE Q2** convention (a bare Int is pixels) and the
8px grid (Material/Tailwind consensus) so the tokens are predictable and
idiomatic. The *shape* (five constant namespaces) is the contract; the
specific scale values may be amended by the maintainer without changing the
API shape.

### Context

`KOFUI-AUDIT.md`/`architecture.md` §2.1 pillar 9 ("Design system — Theme +
tokens") lists the tokens `Color/Type/Spacing/Border/Radius/Elevation`.
Today only `Color` (via `Palette`/`Color`) and `Theme` exist; there are no
Spacing/Border/Radius/Elevation/Typography tokens, so layouts hard-code
literals (`padding: 16`, `border-radius: 4`) instead of naming the design
intent.

### Contract

1. **Surface.** Five constant namespaces, each a compile-time fold to a bare
   `Int` (px):

   | Namespace | Members (→ px) |
   |-----------|----------------|
   | `Spacing` | `xs`=4 `sm`=8 `md`=16 `lg`=24 `xl`=32 |
   | `Radius` | `none`=0 `sm`=2 `md`=4 `lg`=8 `full`=9999 |
   | `Border` | `hairline`=1 `thin`=2 `medium`=4 `thick`=8 |
   | `Elevation` | `none`=0 `sm`=1 `md`=2 `lg`=3 `xl`=4 |
   | `Typography` | `xs`=12 `sm`=14 `md`=16 `lg`=20 `xl`=24 `hero`=32 |

2. **Fold in the compiler (shared frontend).** A `FieldAccessExpr`
   `Namespace.member` is folded to a `KofLoadLiteral(Int)` by the same
   idiom as `Palette`. Because the fold lives in the shared frontend, all
   four targets (JVM/Native/Script/JS) carry the same constant —
   **cross-target parity by construction**.

3. **R6 — no silent 0.** An unknown member (`Spacing.huge`) and a method
   call on a namespace (`Spacing.of(4)`) are a compile-time diagnostic
   **`SEM079`** (English message, lists the valid members). The pre-existing
   `Palette.nope` silent hole is a separate, catalogued gap (it is the
   compiler lane's surface; tokens deliberately do not replicate it).

4. **Additive & backward compatible.** No existing identifier is shadowed
   (`Spacing`/`Radius`/`Border`/`Elevation`/`Typography` were unused).
   Tokens compose with the existing primitives (`Label.setFontSize(
   Typography.lg)`, `Style("padding: " + Spacing.md + …)` is the *style*
   literal form — the tokens carry the same px values).

### Invariants

- The five namespaces are **constants only** (no methods, no `var` form).
- Values are plain `Int` px (D-UI-STYLE Q2); `full`=9999 is the CSS
  "pill" idiom for fully rounded.
- Diagnostics follow D-DIAG-EN. **Amendment (18/09, cross-lane collision):**
  the codes in this decision and in D-UI-STYLE were renumbered — the compiler
  lane had already landed `SEM073` (reduce-arity, `MemberCallTyper`) and
  `SEM074` (primitive-method, `SemMethodCallTyper`) on `beta-0.4.0`. Style:
  Style: `SEM073/74/75` → **`SEM076/77/78`**; tokens: `SEM076` → **`SEM079`** (second renumber, 18/09 merge: `beta-0.4.0` took `SEM073/74` for reduce-arity/primitive-method and `SEM075` for the static-field diagnostic — this lane shifted again to stay unique).
  Only the labels moved; no semantics changed (the decisions stand as decided).
- No runtime surface is added: the fold is compile-time, so there is no
  per-target no-op to document (unlike UI001) — the value is in the IR.

### Rejected alternatives

- **A `Tokens` namespace with nested members** (`Tokens.Spacing.md`):
  rejected — an extra level of ceremony for no gain; the flat
  `Spacing.md` matches `Palette.red` and the idiom table.
- **Runtime objects / `var` tokens**: rejected — tokens are compile-time
  constants; a runtime form would add a no-op per target (UI001 family)
  for zero benefit.

### Implementation

Claimed in `DOING.md` (Fases 8–11, lane UI/style). `KofUiTokens.java`
(fold table + messages) + the six `Palette` touch points
(`SemExpressionTyper`×2, `ExpressionTyper`, `ExpressionLowerer`,
`ExpressionMethodCallLowerer`, `MemberCallTyper`).

### Evidence

`UiTokensE2ETest` 7/7 — golden table on JVM + Native + Script (same shared
fold → identical output), JS DOM (value lands in rendered text),
`SEM079` unknown member, `SEM079` method call, composition with
`Style`/widget; full 4-module suite green outside the pre-existing §252
flake and §181/§256 cross reds; `docs-lang.sh check` 0/0/0.

### Relationships

- Delivers pillar 9 of `architecture.md` §2.1 (Fase 10).
- Related: D-UI-STYLE (Q2 px convention), `Palette` (fold idiom), UI001,
  D-DIAG-EN.

---

## D-UI-APPSTATE — Fase 8: `AppState(initial)` is the application-scoped root store

**Date:** 2026-09-18

**State:** `DECIDED`

**Context:** `docs/ui/architecture.md` §2.6 defines three state scopes. The
local one (`state`/`text`/`flag` on `Component`) and the shared `Store`
(get/set/subscribe/unsubscribe) already worked; the **application root** was
the missing scope (Fase 8). While wiring it, §301 was measured and fixed
first: JS `Store.unsubscribe` was a silent no-op (wrapper-vs-raw identity),
so the "cleanup" leg of §2.6 had no working primitive — see `known-bugs.md`
§301.

**Decision (minimal contract):**
- `AppState(initial)` — one argument, returns the **app-scoped store**: a
  **create-or-get singleton** over the Store machinery. The first call
  creates with `initial`; later calls return the same handle and **ignore**
  their `initial` (documented; the value lives in the runtime, one slot per
  process).
- The returned handle is a `Store` — methods are exactly `get`/`set`/
  `subscribe`/`unsubscribe`; no new surface, no `State`/`Signal` machinery.
- Reachability is the point: components call `AppState(0)` anywhere instead
  of prop-drilling a handle.
- `storesLive()` counts the app-state slot (leak probe unchanged).
- Subscription cleanup on unmount stays **manual** (`unsubscribe(h)` — now
  real per §301): auto-attributing subscriptions to components is a bigger
  contract (which component is "current" during a subscribe?) — rule 6, not
  decided here.
- JVM/Native keep the documented Store no-ops (UI is KofJS — backend-parity);
  the JVM singleton still counts once in `storesLive()`.

**Amendable without breaking code:** the initial-value-ignored semantics and
any later extension (e.g., auto-unsub) are recorded here first; the call
shape itself is frozen.

**Evidence:** `ComponentCoreE2ETest.appStateIsCreateOrGetSingleton` +
`appStateDrivesComponentsWithoutPropDrilling` (RED pre-feature — SEM015
"Undefined function: 'AppState'"; green post-wiring); golden measured per
target (JS `10,10,x=10,x=42,,1`; JVM `0,0,"",1`; Native `0,0,"",0`);
ComponentCore 24/24 + UiE2E 29 + browser 28 + Router 4 + style/tokens 17 +
CoreRegression 102 + CompilerDriver 256 green.

- Related: D-UI-STYLE, D-UI-TOKENS, §301, D-BACKEND-SEMANTICS (no-op stores).

---

## D-UI-DIFF — Fase 9 (partial update / node reuse): **DECIDED (B) — root-kind reuse**

**Date:** 2026-09-18 · **Decision date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — option **(B) root-kind reuse patch**. Queue open in `DOING.md` (owner .17, lane kof-ui).

**Context:** `architecture.md` Phase 9 wants "partial update": reuse the DOM
node when the view re-renders the same widget at the same position. Today
re-render is rebuild+prune: §300 removed the leak, but identity is still
re-created — **measured 18/09 (embedded host, scratch probe):** a
`view (s) -> Label("v="+s)` component's root label handle is `3` after 1
state write and `7` after 5 (one fresh handle per render; old subtree
pruned, correct but new). Consequences: any reference a user kept to a
widget from a previous render is stale, and real-DOM state (input focus,
caret, scroll, CSS transitions) is lost on every state write.

**Options (not decided here — rule 6, §2.7 lifecycle/identity is frozen):**
- **(A) Full positional reconciler** (VDOM-lite): builders emit descriptors,
  a diff-by-(position, kind) patches properties in place. Biggest gain,
  biggest risk: needs a property-copy table per widget family, and makes old
  handles stay live — an identity contract change.
- **(B) Root-kind reuse patch** (first slice): when old and new root are the
  same kind, copy the value-bearing properties onto the OLD DOM node and
  discard the new node — one widget at a time, measurable, but still a
  handle-identity change at the root (aliasing decision required).
- **(C) Keep rebuild; no reuse.** Honest, simple; focus/caret loss stays a
  documented limitation (current state).

**Recommendation (lane UI/style):** (B) behind an explicit identity note —
smallest cohesive unit that fixes the user-visible pain (focus loss on the
common single-root-widget case) without a VDOM layer. The decision (which
option + the handle-continuity contract) is the maintainer's.

**Decision (maintainer, 18/09) — the identity contract of (B):** when the
previous and next render of a `view`-component produce the **same root
kind**, the OLD DOM node is kept and the value-bearing properties are copied
from the fresh node onto it; the old root handle **stays live** (identity
continuity — this is the aliasing call option B requires). Different root
kind → rebuild + prune exactly as today (§300). No VDOM layer, no keying,
no positional diff of children in this slice. Proof expected: probe shows
the same handle across state writes when kind is stable; focus-bearing
element survives the write; kind change still prunes (no regression of
§300).

**Related:** §300 (prune), §301 (unsubscribe), Fase 9 audit lines,
D-UI-APPSTATE (manual-unsub stance — now superseded by D-UI-AUTOUNSUB),
D-UI-CANCELLED.

---

## D-UI-AUTOUNSUB — Store subscriptions are component-scoped automatically: **DECIDED (A)**

**Date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — option **(A) automatic per-component scope**.

**Context:** since §301 `unsubscribe(h)` is a real primitive, but cleanup
is manual — a component that `subscribe`s on mount leaks the callback (and
its captured closure) after the component is pruned (§300's registry knows
exactly when). D-UI-APPSTATE recorded "cleanup stays manual" as the
*pre-decision* stance.

**Decision:** a `subscribe` performed **while a component is the current
render target** is bound to that component; when the component leaves the
tree (subtree prune, §300), the runtime unsubscribes it automatically.
Subscribes **outside** a component context (application-scoped, e.g. an
`AppState` observer created in `main`) keep manual semantics — the primitive
from §301 continues to work for them. Backward compatible: nothing that
compiles today changes behavior except leaked subscriptions dying with
their component.

**Related:** §300 (subtree registry), §301 (unsubscribe), D-UI-APPSTATE
(stance superseded), D-UI-DIFF (same lifecycle plumbing).

---

## D-UI-CANCELLED — `cancelled()` in async UI actions: **DECIDED (A) — origin-component scope**

**Date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — option **(A) true when the originating component
left the tree**.

**Context:** `cancelled()` inside an async action callback currently is
conservative (almost always `false`) — a late `spawn`/`http` response can
still write into a DOM subtree that §300 already pruned. Option B
(state-version based) was rejected as over-aggressive (kills legitimate
updates, heavy contract change); option C (manual handles) rejected as
ceremony.

**Decision:** the action remembers the component instance it was created
in; `cancelled()` returns `true` once that instance's node is no longer in
the live tree (same registry as §300). Async callbacks should guard the DOM
touch with `cancelled()` — when true, the response is dropped. Non-DOM side
effects are the programmer's business (unchanged).

**Related:** §300, D-UI-AUTOUNSUB (same lifecycle substrate), Fase 8
(`view`/actions), D-BACKEND-SEMANTICS (`spawn`/`await` frozen — this is UI
observability, not a concurrency contract change).

---

## D-UI-SCOPE — `kof.ui` rule updates: **DECIDED — the sole named exception to rule 5**

**Date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — exception ratified; this record converts the verbal directive into a contract.

**Origin:** maintainer directive in chat, 18/09/2026: "regra do ui so vale pra js e
webasm" — kof.ui rule updates only apply to KofJS and Kof WebASM, not JVM/Native.
The statement arrived without a decision id (rule 6: contracts change only by
recorded decision). This record ratifies it as the maintainer's chosen form
(single named rule, recommended wording for the WASM condition).

### Context

AGENTS rule 5 makes absolute parity law ("same output on every target"). kof.ui
rule work has always been JS-only in practice: `kof_dom_patch` (§257/§300), diff
caching (D-UI-DIFF), `cancelled()` (D-UI-CANCELLED), the Router (Phase 7) — the
JVM/Native sides are no-op or degrade by design. Filing those as "parity gaps"
was miscoding intent as debt. The maintainer's statement turns practice into
law; this decision makes it a **named** exception so ledgers, the parity matrix,
and CI queries stop treating it as a bug.

### Contract

1. **`kof.ui` rule updates** — changes to the UI-language semantics (rendering
   rules, state-driven re-render, signal propagation, `when`/`each`, diffing,
   cancellation/`cancelled()`, the §300 lifecycle substrate) — are the **sole
   named exception** to rule 5. The parity duty binds **KofJS** (today) and
   **Kof WebASM** *the moment `Target.WASM` exists* (the enum is
   `JVM/NATIVE/ANDROID/JS` — measured 18/09; there is no WASM surface to break
   yet). It does **not** bind JVM/Native, ever: absence of UI rules there is the
   design, not a gap.
2. **Not** inside the exception: the rest of `kof.ui` (API signatures and what
   the parity matrix already measures row-by-row — unchanged), everything
   outside `kof.ui`, and rule 5 for core semantics and stdlib outputs. The
   exception is **prospective**: it prevents *new* parity obligations on
   JVM/Native for rule updates; it does not rewrite existing matrix rows.
3. The exception is **named and enumerable** (exactly this surface, exactly
   this target set). It is not a template: any future exception needs a new
   `D-` section ratified by the maintainer (rule 6).

### Consequences

- `docs/backend-parity.md` gets the carve-out under the Principle section, EN+PT,
  same commit as this decision.
- A UI-rule change is landed with JS (+ WebASM when it exists) tests only; do
  **not** add JVM/Native parity assertions for rule behavior, and do not open
  `known-bugs` items for their absence.
- Existing records D-UI-STYLE / D-UI-TOKENS / D-UI-DIFF / D-UI-AUTOUNSUB /
  D-UI-CANCELLED are all consistent with this carve-out (they shipped on KofJS
  only, by fact).

### Related

- AGENTS rules 5, 6, 10; `docs/backend-parity.md` §Principle.
- D-UI-DIFF, D-UI-AUTOUNSUB, D-UI-CANCELLED (the substrate this governs).
- `IMPLEMENTATION-UNIVERSAL-PLATFORM` R7 (browser honest degrade) — related but
  distinct: R7 is a runtime degrade with a diagnostic, this is a scope exclusion.

---

## D-UNIVERSAL — promotion of `IMPLEMENTATION-UNIVERSAL-PLATFORM` to current work (R12 overridden)

**Date:** 2026-09-17

**State:** `DECIDED`

**Origin:** maintainer directive in chat, 17/09/2026: "se acabaram os docs
preciso que voce assuma a frente
docs/development/future/PLAN-UNIVERSAL-PLATFORM.pt_BR.md" (original name;
renamed to `IMPLEMENTATION-UNIVERSAL-PLATFORM` in the same promotion) → answered
"Promover p/ development/ e implementar".

### Contract

1. `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` + `.pt_BR.md` **leave `future/`** and become
   current work in `docs/development/`, status **UNDER DEVELOPMENT**.
2. The promotion gate of `docs/development/README.md` §4.3 ("decision +
   SYSTEMS closed (R12)") is **overridden by this decision**: the maintainer
   authorizes the front to open with SYSTEMS still in progress.
3. The document stops being "vision only": its own header rule ("implements
   nothing, does not move files, does not open a new front") is **revoked and
   rewritten** in the same commit as the move.
4. The **first entry point is Stage 1 (SYSTEMS consolidation, §10 Estágio 1)**
   and the executable recommendations **R1–R12 (§15)** — not Tier 6+
   (AUTOMATION/INFRA/DATA/…), which keep their order in `roadmap.md` §23.
5. The vision/design of the document is **not** edited by agents: only state
   claims are synced to the real code (three-state rule), and each
   implementation unit follows Q0–Q7 like any other change.
6. Frozen core semantics remain frozen; every change is additive and per
   target (R6/R7 apply).

### Invariants

- The plan does **not** become a license to break the freeze (rule 6 of
  `AGENTS.md` still holds for operators/precedence/order of evaluation).
- `roadmap.md` §23 remains the **single ordered plan**; this document supplies
  the architecture for Tiers 6–12.
- No heavy domain (`ml`/`bio`/`hpc`) enters the base stdlib (R1).

### Rejected alternatives

- **Respect R12 and close SYSTEMS first** (do the TIER 1 items before
  promoting) — rejected by the maintainer, who chose to promote now.
- **Promote only the document without opening implementation** — rejected:
  the directive is "promover **e implementar**".

### Implementation

- Files moved: `docs/development/future/PLAN-UNIVERSAL-PLATFORM.md` →
  `docs/development/PLAN-UNIVERSAL-PLATFORM.md` (and the `.pt_BR.md` pair;
  promotion of 17/09), then **renamed** to
  `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` when split into the executable
  tracker + the vision companion
  `docs/architecture/UNIVERSAL-PLATFORM-VISION.md`.
- Queue: `roadmap.md` §23 TIER 6–12 now points at the new path and records the
  R12 override; the first executable unit is chosen from Stage 1 / R1–R12.
- Tracking: `DOING.md` + `DOING.pt_BR.md`.

### Evidence

- Move + header rewrite + reference sync in the same commit; `docs-lang.sh
  check` 0/0/0; `check_500.sh` rc=0.

### Relationships

- `Overrides: R12` (the meta-rule "do not interrupt the present").
- `Related:` `roadmap.md` §23 (Tiers 0–12), §22 (Universal Platform),
  `AGENTS.md` §"Platform invariants".

---


## D-TRIAGE — philosophy check precedes the issue (docs-first gate)

**Date:** 2026-09-18

**State:** `DECIDED`

### Contract

A request that imports a **foreign stack** into Kof (HTML tags/CSS/`innerHTML`
into `kof.ui`, framework layers, template engines — case #449 "RawView") is
NOT a missing feature and NOT a bug: it is a philosophy violation by an author
who has not read the docs. The FIRST reply must send the author to the reading
(`docs/philosophy.md` "What Kof Is NOT", `training/idioms/<area>.md`,
`training/anti-patterns/`) with one line of WHY and the Kof idiom that covers
the real need. The issue stays open only if the need survives the reading AND
no Kof abstraction covers it — resolution then is a maintainer decision
(D-UI-* family), never the imported syntax. Rule 9 of `AGENTS.md` (generalizing
rule 8 "Kof is not Java"); the issue templates carry a required checkbox that
makes the human sign the same gate.

### Evidence

#449 closed by maintainer 18/09 (RawView with `setCss`/`setHtml`); rule in
`AGENTS.md`/`AGENTS.pt_BR.md` §"Iron rules" item 9; philosophy bullet "It is
not markup in disguise" EN+PT; `feature_request.yml`/`bug_report.yml`
checkboxes. Governance-only change (no code).

---

# 4. Rejected or superseded decisions

This section is historical. It does not define current behavior.

| ID                  | Previous decision                  | State        | Superseded by                    |
| ------------------- | ---------------------------------- | ------------ | -------------------------------- |
| §125                | `null` for primitive folds to zero | `SUPERSEDED` | D-NULL                           |
| §125                | boxed `T?` would be prohibited     | `SUPERSEDED` | D-NULL                           |
| §125                | silent `null` in return/assignment | `SUPERSEDED` | D-NULL-INTENT                    |
| C18 interim         | public reads by default            | `SUPERSEDED` | D-SEC.4                          |
| D-PLATFORM          | separate platform plan             | `CLOSED`     | D-APP + roadmap §23              |
| D-PLAT              | separate completion plan           | `CLOSED`     | roadmap §23 + Definition of Done |
| D-ASM-GATE previous | ASM inspection always mandatory    | `SUPERSEDED` | current D-ASM-GATE               |

---

# 5. Relationship with other documents

This file defines **the contract**.

The other documents define:

| Document                 | Responsibility                             |
| ------------------------ | ------------------------------------------ |
| `roadmap.md`             | What will be implemented and in what order |
| `DOING.md`               | What is being executed now                 |
| `AGENTS.md`              | Operational rules for agents               |
| `docs/architecture/`     | Detailed architecture                      |
| `docs/stdlib/`           | Stdlib contracts and APIs                  |
| `docs/backend-parity.md` | Parity matrix and gaps                     |
| `docs/bugs-and-gaps/`    | Bugs, gaps, and regressions                |
| `training/`              | Learning material and corpus               |
| `CHANGELOG`              | Release history                            |

### Precedence rule

In case of conflict:

1. current decision in this file;
2. specific normative documentation;
3. conformance tests;
4. current implementation;
5. chat history.

Code and tests that contradict a current decision indicate a divergence to be corrected, not an automatic new decision.

---

# 6. How to record a new decision

Use this format:

```markdown
**## D-XXXX — title**

**Date:** YYYY-MM-DD

**State:** DECIDED | IN_PROGRESS | IMPLEMENTED | PARTIAL |
BLOCKED | SUPERSEDED | REJECTED | CLOSED

**Scope:** ...

**### Context**

Why the decision was necessary.

**### Decision**

Approved contract, without unnecessary implementation details.

**### Invariants**

What must not be broken.

**### Rejected alternatives**

Only when necessary to preserve the reasoning.

**### Implementation**

Reference to the roadmap or DOING.

**### Evidence**

Tests, commits, matrix, or documentation.

**### Relationships**

- `Supersedes: ...`

- `Depends on: ...`

- `Related: ...`
```

---

# 7. Final rule

**Decisions are permanent until superseded. Implementations are revisable.**

Code may change.

Tests may change.

The roadmap may change.

The contract changes only through a recorded decision.

---

## D-POLL-19 — every pending decision resolved (multiple-choice poll, maintainer 19/09)

**Date:** 2026-09-19 · **State:** `DECIDED` (batch) · **Answers (maintainer):**
D1-A · D2-A · D3-A · D4-A · D5-B · D6-A · D7-A · #401 = "BUG REAL — `List<Int>` is
different from `List<String>`" (= option A, compile-time rejection) · X8-A · LSP-A
(rename cross-file) · LSP-A (hover signatures via `StdCatalog`).

| # | Decision (option) | Unblocks / queue |
|---|---|---|
| D1 (A) | GC x86 auto-collect re-baseline **approved now** | 1.2.2/1.2.3 proceed; Stage 6 gate open — execution = native lane |
| D2 (A) | registry MVP = **local + GitHub Releases as official host** (publish = Release with artifact + SHA256SUMS) | 1.5.3 ⛔→open; `kof deploy --publish` face (docs→platform lane); 8.2 package manager |
| D3 (A) | bare-metal/bootable **design plan authorized** | 1.7: plan doc in `docs/development/` (native lane drafts, maintainer reviews) |
| D4 (A) | **conservative default**: every namespace is born `experimental`; promotion per-namespace with the R5 DoD | R5; `docs/backend-parity.md` §Stability (default line added 19/09) |
| D5 (B) | **no new syntax** — scoped resources = `close()` + `try/finally`; `using` is OFF | 6.5 ships as pattern, not grammar; `future/scoped-resources` stays design-only |
| D6 (A) | R3 struct/array ABI: **written spec first, review, then code** | spec doc `docs/development/ffi-abi-structs.md` (drafted by docs→platform lane 19/09, design-only); implementation = compiler lane |
| D7 (A) | value records (TIER 2.7) **front opened now** | roadmap §23 2.7.1+ queue active — compiler lane (needs coordination with Cluster A) |
| #401 | **real bug**: `List<Int>` assigned as `List<String>` must be REJECTED at compile time | §270/§271 Cluster A (`.22`) executes; freeze rule 1 satisfied — code that compiles today fails at runtime, so tightening matches the documented contract |
| X8 (A) | `kof.test` runner implemented **exactly as roadmap §G6 specifies** | X8 slice 3 — docs→platform lane |
| LSP-A (rename) | **cross-file rename via WorkspaceEdit** on the same textual convention as `references` ("first hit" honesty documented) | X10-continuation — docs→platform lane |
| LSP-A (signatures) | hover signatures: **`StdCatalog` extended with signatures extracted from the typer** (single source) | X10-continuation — docs→platform lane |

**Evidence:** maintainer's message 19/09 ~03:5x (-03), one-line multi-choice
answers; ratification commit updates the D-table in
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (+PT), `roadmap.md` §23 (D7),
`backend-parity.md` (D4) and `known-bugs.md` §270 (#401).

---

## D-TROOL — `Bool` is never nullable; the three-valued type is `Troolean` (maintainer 19/09)

**Date:** 2026-09-19 · **State:** `DECIDED` · **Revision of:** the `Nullable(Bool)`
face of D-NULL-INTENT (the boxed-nullable machinery stays; `Bool` stops using it
as surface syntax) · **Queue:** new front under §23 Tier 2.6 (null-intent family).

### Contract (maintainer's words, 19/09 ~12:0x -03)

1. A nullable variable declared **without instantiation** already has `null` as
   its value — by default, no ceremony. *(already the measured behavior of
   `String? s`, `Int? q`, `Bool? b`: JVM/Script/JS print `null` / `== null` is
   true — this clause CONFIRMS the current D-NULL-INTENT default and freezes it.)*
2. Assigning `null` to a nullable through code (`x = null`) **remains refused**
   (SEM048 unchanged). `null` reaches a variable only through (a) the default of
   the non-instantiated declaration or (b) an API/function returning `null`.
3. **Primitive values are not interfered with**: `Int n` keeps its `0` default —
   only `T?` declarations carry null.
4. **`Bool` cannot be nullable**: it has exactly two values. `Bool?` (and every
   `Nullable(Bool)` written by the user) becomes a **compile-time refusal,
   `SEM095`** with the message pointing at the replacement. (SEM094 is reserved
   for the switch-return gate shipped by the bot's PR #481 — if that one ever
   re-lands first, the codes swap and this entry updates.)
5. For `true / false / null` the language gains **`Troolean`** — a 3-state type
   with its logic (Kleene): `!`, `&&`, `||` follow the truth tables
   (`NOT U=U`; `AND`: F dominates, U second; `OR`: T dominates, U second),
   comparison against `true`/`false` and the intent-check `== null` work,
   un-declared instance = `null`(unknown), functions may `return null` into a
   `Troolean`. `println` shows `true`/`false`/`null` (no 0/1 — §306(b) canonical
   already does this for the boxed Boolean).

### Rationale and scope notes

- The clause pair (1)(2) makes `Bool? b = null` illegal but `Bool? b` (uninstantiated)
  legal — for `Bool` specifically, clause (4) removes the whole surface: there is
  no way to hold the unknown state in a `Bool?` anymore, which is exactly the
  confused family behind #462 (value-context VerifyError) and #486 (JVM
  VerifyError + JS `null` leak on `&&`/`||` over `Bool?`). Both issues are
  CLOSED by this decision: the reproducer becomes a `SEM095` diagnostic and the
  idiom is `Troolean` (rule 8 close: the foreign construct's replacement is now
  IN the language).
- Internal representation decision (lane, no new runtime class needed on
  JVM/Script/JS): `Troolean` is a **nominal type in the front end** (type name
  registered; `Type.PrimitiveType` "troolean" sort) that lowers per backend using
  the machinery the box already has — JVM/Script/JS reuse the boxed `Boolean`
  slot of §295/§306 (the writer/reader cluster already fixed them), the
  three-valued operators desugar in the front end into Kleene tables over the
  existing comparison machinery; **Native** maps to a 3-state `byte` (0=F,1=T,2=U) —
  if a Native path cannot honor a face, the honest R6 diagnostic `NAT-TROOL001`
  replaces any silent fallback (freeze rule 5: parity or diagnosed gap).
- Frozen-semantics audit (rule 1/6): this TIGHTENS (a construct that compiled
  now gets a diagnostic — same class of change the maintainer approved for #401:
  code that compiles today dies at runtime, so refusing it at compile time
  matches the documented contract). `Bool` non-nullability is consistent with
  §306's own truth (the JVM reader faces of `Bool?` were crash-faces; #462/#486).
  Version note + migration entry go in CHANGELOG (0.4.0 line).

### Queue (roadmap §23 Tier 2.6, D-TROOL)

1. Front end: register `Troolean`; refuse `Bool?`/`Nullable(Bool)` written by
   the user with `SEM095` (message: "`Bool` has two values; for
   true/false/unknown use `Troolean`"); tests: `TrooleanLawE2ETest`
   (SEM095 face + declaration default + `return null` narrowing).
2. Operators: Kleene `!`, `&&`, `||`, `==` on `Troolean`, `runAll3` (JVM+Script+JS),
   edges: all nine AND/OR combinations + NOT, nested chains, condition position.
3. Native: 3-state byte + `NAT-TROOL001` honest gap where a face cannot land.
4. Migration of the 4 test files that write `Bool?` (ConformanceMatrix,
   NullablePrimitiveContract, NullableBoolTruthiness, KofInterpreterParity) +
   corpus: `training/language/types.md`, nullability idiom docs,
   `fake-idioms.md` (add the `Bool?` row → Troolean), DECISIONS D-NULL-INTENT
   revision note, CHANGELOG migration entry, `backend-parity.md` matrix cell.

**Evidence:** maintainer's message 19/09 ~12:0x (-03), two clauses (the
definition + "implement and close the related issues"). Lane picks: the
implementation is front-end-centric and the boxed-nullable machinery is already
built (`.22` shipped §295/§306 18–19/09); coordination claimed in `DOING.md`
same commit (the `SemExpressionTyper`/`ExpressionLowerer` files are the same
`.22` touched today — they hold NO other unclaimed `Bool?`-face work after
#462/#486 are closed here).

---

## D-KOF-FIRST — internal contract before external comparison (`KOF-first, external-second`)

**Date:** 2026-09-19 · **State:** `PROPOSED` (awaiting maintainer ratification; until ratified it governs agent triage as a working rule, never as a ratified contract) · **Scope:** issue/PR triage, bug hunting, gap classification, use of external references · **Related:** `D-NOT-JAVA` (rule 8), `D-TRIAGE` (rule 9), the precedence rule of §5

### Context

The risk is not a wrong issue; it is the language evolving by accident. A
foreign expectation enters as a "bug", gets a plausible patch, a test freezes
the new behavior, the documentation starts teaching it — and the Kof surface
has grown without a decision. The repository already carries the pieces of the
answer (rule 8 "Kof is not Java", rule 9 "philosophy check precedes the
issue", `D-NOT-JAVA`, `D-TRIAGE`, and the precedence rule that puts
`DECISIONS.md` above implementation) and, at the same time, the measured cases
that motivated this rule: #410 (`0..n` as a range), #416 (`!!`), #407
(top-level `val`/`var`), #424 (`StringBuilder`), #415 (`String[i]`), #449
(`RawView`), #483 (`name() -> Type`), #492/PR #496 (`(Int x) -> x * x`).

### Contract

1. **No external result is an oracle.** A language, specification, forum,
   benchmark, paper or runtime does not, by itself, define Kof's expected
   behavior.
2. **The reproducer must be valid Kof.** Before opening or validating an
   issue, prove the snippet uses grammar and syntax Kof recognizes.
3. **Kof's contract comes before the implementation.** Identify the decision,
   normative documentation, conformance test or applicable rule *before*
   classifying the observed behavior.
4. **The Kof idiom is searched before the foreign feature.** If the need is
   already met by an existing Kof abstraction, rejecting foreign syntax is not
   a bug.
5. **Internal divergence precedes external comparison.** A bug is demonstrated
   as a divergence between Kof and its own contract, or between targets
   governed by the same contract.
6. **A gap must be proved.** There is a gap only when the legitimate need
   remains with no satisfactory solution inside current Kof.
7. **External research begins only after the gap.** Once the internal problem
   is proved, other languages and the literature may be studied.
8. **External references supply principles, not surface.** Extract
   invariants, techniques, formal models, known failures, trade-offs.
9. **Every external solution is translated back into Kof.** Name, syntax, API,
   semantics and ergonomics are evaluated against Kof's philosophy, decisions,
   targets and abstractions.
10. **A contract change is a decision, not a bugfix.** A proposal that changes
    grammar, semantics, operators, the type model or a frozen API requires an
    explicit maintainer decision (rule 6).

### Classification (Gate 4 — nothing gets a production patch without one)

| Category | Exists when |
|---|---|
| `BUG REAL` | valid Kof program + Kof contract defines the behavior + implementation differs |
| `TARGET DIVERGENCE` | the same valid Kof construct behaves differently across targets with no honest documented gap |
| `GAP REAL` | legitimate need + no adequate Kof syntax/idiom/stdlib/composition + no decision rejecting it |
| `DESIGN REQUEST` | intent is to change, extend or replace a surface/semantics decision |
| `NOT-VALID` | the reproducer depends on a construct that is not Kof and a Kof idiom covers the intent |
| `CONTRACT AMBIGUITY` | docs, decisions, tests and implementation do not settle which behavior is normative → evidence + alternatives + maintainer decision, never an automatic fix |

### Gates (the pipeline, in order)

- **Gate 0 — is the reproducer Kof?** Check `docs/language-reference/`
  (grammar, syntax, types), the feature's own doc, `training/`, `learn/`,
  `training/anti-patterns/fake-idioms.md`, this file. Not Kof → no bug is
  demonstrated; go to Gate 1.
- **Gate 1 — intent and idiom.** Never stop at "this syntax does not exist":
  name the real intent and the Kof idiom that expresses it. Idiom resolves →
  `NOT-VALID`.
- **Gate 2 — governing contract.** `DECISIONS.md` → normative docs →
  conformance/golden → parity matrix → implementation; chat history only as
  auxiliary evidence. Record `contract source` / `contract statement` /
  `expected Kof behavior`.
- **Gate 3 — internal measurement.** Run the **valid Kof** reproducer on the
  relevant targets (JVM / Script / JS / Native x86 / Native riscv64-aarch64
  when applicable).
- **Gate 4 — classification.** One of the six categories above.
- **Gate 5 — proof of the gap.** For `GAP REAL`, answer *no* to all: valid
  Kof syntax exists? documented idiom exists? stdlib/API exists? composition
  of Kof resources solves it reasonably? a decision consciously rejects that
  surface? already catalogued gap?
- **Gate 6 — external research.** Now, and only now.
- **Gate 7 — translation back to Kof** (what internal problem it solves,
  which principle is reusable, what is specific to the source language,
  conflict with any Kof decision, new syntax/API, accidental complexity,
  parity, honest gap on some target, expressible with existing mechanisms).
- **Gate 8 — decision.** Contract change → comparative proposal, trade-offs,
  migration and per-target impact, technical recommendation **without
  self-ratification**, maintainer's decision.
- **Gate 9 — implementation and proof.** RED reproducing the contract → root
  cause fix → GREEN → cross-target conformance → golden/migration → docs and
  CHANGELOG.

### Blocked without a decision

An automatic production PR is appropriate **only** for a confirmed `BUG REAL`,
a confirmed `TARGET DIVERGENCE`, or the implementation of an already ratified
decision. It is blocked while the issue is `CONTRACT AMBIGUITY`,
`DESIGN REQUEST` or an unratified `GAP`. Any parser/lexer diff that introduces
a newly accepted form must answer *which decision authorizes this new
surface* — with no decision, `STOP`.

### Evidence block (issues and PRs)

Issues and bug-hunter reports carry `KOF VALIDITY` (grammar source,
syntax/documentation source, reproducer validated as Kof), `CONTRACT`
(decision/source, expected behavior), `MEASUREMENT` (targets, actual
behavior), `CLASSIFICATION` and `DUPLICATE CHECK`. If `KOF VALIDITY` cannot be
proved, no issue is opened automatically. PRs carry the contract source, the
valid Kof reproducer, the RED before the production change, the root cause,
the fix, why it does or does not change the Kof contract, the regression
proof and the cross-target impact.

### Evidence

Maintainer-facing proposal `KOF_FIRST_CONTRACT_RULE.md` (19/09); rules 8 and 9
of `AGENTS.md`/`AGENTS.pt_BR.md`; `D-NOT-JAVA`, `D-TRIAGE`, precedence rule of
§5; measured cases #407, #410, #415, #416, #424, #449, #483, #492/#496.
Governance-only change: no code, no semantics, no surface touched.
## D-SCHED-DURATION — idiomatic duration expressions in `scheduler.at`

**Date:** 2026-09-19 · **State:** `DECIDED` (maintainer directive in chat:
"coloca pra aceitar expressões idiomáticas também. scheduler.at(30m) por
exemplo, pode ter s, m, h, d, M, a" + "e aceitar expressões compostas
(1d&30m) por exemplo")

**Decision (additive, freeze rule 2):** the first argument of
`scheduler.at(expr, fn)` accepts, BESIDES the 5-field UTC cron (unchanged),
an **idiomatic duration expression**:

* `term := digits unit`, `unit ∈ { s, ms, m, h, d, M, a }` — `s` seconds, `ms` milliseconds,
  `m` minutes, `h` hours, `d` days (fixed, in ms); `M` months and `a` years
  advance the UTC CALENDAR (month/year boundary, clamped to the target
  month's last day — `2024-01-31` + `1M` = `2024-02-29`);
* composition with **`&`** (e.g. `1d&30m`, `1M&15m`): the fixed terms (s/m/h/d)
  sum in ms and are applied as an OFFSET after the calendar advance;
* semantics: first fire after the interval counted from now, then repeatedly
  (fixed interval, or the calendar-advanced next instant for M/a — anchor
  advances from the previous fire, never from `now`, no drift);
* a string that is NOT a duration keeps the cron path (same 5-field parser,
  same errors); a MALFORMED duration (unknown unit, zero/negative term,
  empty term) throws with a clear message — never silent (R6);
* targets: JVM + JS (same algorithm, byte-parity golden via probe); Native
  keeps the existing honest compile-time `CRON001` refusal (the gap already
  covers the whole `at` surface).

**Evidence:** maintainer's messages 19/09 (this session, lane .18). First
consumer: `flow.schedule(cron)` of the `kof.workflow` 2.1.3 bundle (same
honest gap on Native).
