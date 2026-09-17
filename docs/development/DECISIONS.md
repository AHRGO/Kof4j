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

**Queue:** `CmdNew`, complete manifest/dependency integration, and target gaps.

---

## D-SPRING — framework independence

**Date:** 2026-09-13

**State:** `IN_PROGRESS`

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
| 10 — native testing | `DECIDED`     |
| 11 — complete CLI   | `IN_PROGRESS` |
| 12 — blog E2E       | `IN_PROGRESS` |

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
7. **kof.db/orm in JS** — **DB001 CLOSED 16/09** (untyped `connect/execute/query/close/transaction` on the GraalJS-host bridge — `3e55df51`+`eb9140cb`); residual typed `db.query<T>` = `DB002` CLOSED 18/09: guest-side bind via `__kof_decode_<T>` (the host bridge has no `Class.forName` for JS classes — the wire stays untyped) + `kof.orm` = `ORM001` (same wall; WASM planned).

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

**State:** `DECIDED`

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
   whitelist is a compile-time diagnostic (`SEM073`) — never silently
   forwarded to `node.style` (R6). A malformed declaration is `SEM074`; an
   invalid value for a known property is `SEM075`.
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
units, `setStyle` on a Label, `SEM073`/`SEM074`/`SEM075`, non-literal,
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

## D-UNIVERSAL — promotion of `PLAN-UNIVERSAL-PLATFORM` to current work (R12 overridden)

**Date:** 2026-09-17

**State:** `DECIDED`

**Origin:** maintainer directive in chat, 17/09/2026: "se acabaram os docs
preciso que voce assuma a frente
docs/development/future/PLAN-UNIVERSAL-PLATFORM.pt_BR.md" → answered "Promover
p/ development/ e implementar".

### Contract

1. `PLAN-UNIVERSAL-PLATFORM.md` + `.pt_BR.md` **leave `future/`** and become
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
  `docs/development/PLAN-UNIVERSAL-PLATFORM.md` (and the `.pt_BR.md` pair).
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
