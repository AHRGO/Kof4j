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
