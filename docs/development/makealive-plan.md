[English](makealive-plan.md) | [Português](makealive-plan.pt_BR.md)

# `Kof Makealive` — infrastructure as typed code (design plan · Stage 3 · rows 3.1–3.8)

**Type:** design plan — **awaiting sign-off (§6 poll)**; nothing in §2 ships
before the maintainer answers Q1–Q4 (rule 6).
**Tracker:** [`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`](IMPLEMENTATION-UNIVERSAL-PLATFORM.md)
Stage 3 (rows 3.1–3.8). **Companion (vision):**
[`docs/architecture/UNIVERSAL-PLATFORM-VISION.md`](../architecture/UNIVERSAL-PLATFORM-VISION.md)
§4.2 — the domain name, the imperative-vs-declarative verdict and the pipeline
are THERE-decided; this file is the executable decomposition.
**Owner lane:** `.18` (development), maintainer directive 19/09
("assume a frente do kof makealive no plano da plataforma universal").

---

## 1. Objective

Infrastructure as **typed Kof code** with plan/apply/state/reconciliation —
the conceptual replacement for Terraform (VISION §4.2): the code *brings to
life* the declared state. The pieces that make this pure Kof **today** are
already shipped: records/classes (data), `Map`/`List` (the graph), `throw`
(the honest error), `spawn`/`await`/`scheduler` (reconciliation),
`kof.io`/`kof.db` (state), `kof.http`/`kof.process` (providers as interop),
`kof.security` (secrets). Makealive **composes**; it invents no engine
(INTEROP-FIRST, R9) and grows no core (8.6 golden).

## 2. Proposal — a Kof-level stdlib package, NOT new syntax

The **canonical model is imperative-turned-data** (the VISION §4.2 verdict,
"A/B — and it is where the language shines"): typed resources + a builder +
normal functions. The declarative block `infra "prod" { ... }` is row **3.2**
— a new parse block gated by **R4** (the codegen hook does not exist at HEAD)
and by rule 6 — it is NOT in this plan's queue, and v1 does not wait for it.

Surface sketch (flat host idiom, like `kof.workflow`/`kof.supervisor —
DD-OTP-01 option A`; **shapes to be measured by the 3.0 recon before they
become a face**, never assumed):

- `Resource(kind, name)` — **record**: the typed unit of desired state.
- props: `Map<String,String>` (v1; richer types = follow-up, no struct ABI).
- `Infrastructure(nome)` — builder class: `resource(kind, name, props)` and
  `requires(child, parent)` build the graph; duplicates refused.
- `plan(desired, current)` — **pure diff**: `creates` / `updates` / `deletes`
  in deterministic (topological, then declaration) order. No side effects.
- `apply(design, provider)` — converge: create/update what plan says, persist
  state only on success; `destroy(design, provider)` — reverse topological.
- **provider = function values** (the Kof idiom for an interface, precedent
  `KofWfJob.corpo`): `read: (Resource) -> Map` + `set: (Resource, Map) -> Void`
  + `delete: (Resource) -> Void`. No compiler primitive, no new runtime.
- **v1 provider: local FS** (resources materialize as marker files under a
  state dir) — makes plan/apply/state/destroy/idempotency **measurable
  end-to-end with zero cloud credentials and zero FFI**; concrete clouds ride
  §3.5 as interop (REST via `kof.http`, CLI via `kof.shell`), never in-core.

### 2.1 The R1 collision — MEASURED 19/09 (this is why Q1 exists)

The tracker names the namespace `kof.infra` (row 3.1). The R1 machine gate
**hard-denies it**: probe `check_stdlib_boundary.sh` against a temp source
holding the literal `"kof.infra"` → **rc=1**,
`"VIOLATION: 'kof.infra' is a HARD-DENY heavy domain — forbidden in base
stdlib/platform (R1/AGENTS invariant 1; official package only)"`. The deny
list was drafted from invariant 1's `infra-<cloud>` (the *providers* are the
heavy domain) but the bare `infra` token also blocks the Makealive **core**.
Resolution is a maintainer decision → **Q1** (§6). Nothing ships until it is
answered; a ledger line cannot override a hard-deny (measured).

## 3. Contract

- **Idempotency by construction**: apply of an already-converged design is a
  no-op (the plan is empty) — the acceptance golden, not a slogan.
- **plan has no side effects**; only apply touches the world and the state.
- **cycles are refused at graph-build** with an actionable `throw` naming the
  cycle (workflow run() precedent, 4/4 targets); compile-time cycle detection
  is row 3.7 and waits for R4.
- **state advances only on success**: a failed apply leaves the previous state
  intact and names the resource that failed (R6, never a silent partial).
- **secrets are references only**: v1 stores a secret *name* (resolved at
  apply time by the caller); redaction/`Secret` is Stage 5 (row 3.6 🟡), never
  plaintext material in the state file.
- **no target gate in the composition layer** (workflow lesson): the host is
  sequential pure Kof; honest gaps (`CRON001`, `ORM001`, `PROC001`) surface at
  the CALL-SITE when the provider body crosses a runtime boundary.

## 4. Per-target ABI — to be MEASURED in the 3.0 recon, not assumed

| Shape | Expected | Proof plan |
|---|---|---|
| record `Resource` + accessors | all targets | recon JVM+JS run, Native compile |
| class + `Map<String,String>` field + iteration | JVM/JS/Script run; Native compile | recon |
| topological fixpoint + deterministic order | proven pattern (workflow) | cited, re-locked in resource shape |
| cycle → `throw` String | 4 targets | recon |
| `kof.io` file round-trip (state) | matrix row `kof.io` ✅✅✅ (lines 72/74) | recon JVM+JS parity; Native compile |
| JVM==JS byte parity of plan output | Q-parity rule | golden at 3.1 |

**§4 status 19/09 — MEDIDA by `MakealivePrimitivesE2ETest` (recon 3.0.1), 5/5 GREEN**
(JVM==JS byte-parity runs + Native compile pins; the throw/catch form also runs on
SCRIPT): every row above is locked today. Corrections measured: (i) `kof.io` is **not**
a static facade — the contract is `File("path")` constructor + instance methods
(`f.writeText/readText/delete/exists`; `IoE2ETest.fileTextRoundTrip` is the golden —
the "Static forms" line in `docs/stdlib/IO.md` is drift, flag to the docs lane, do not
rewrite it here); (ii) `mapOf().keys()` iteration order is target-dependent — the
design must drive iteration from an explicit `List` (locked); (iii) no ternary `?:` and
no `for (i in 0..n)` range in `.kf` — if/else and while-with-index or element `for`
(locked); (iv) `record` with `String` fields and `mapOf(k, v, ...)` class-field
initializers compile and run identical (locked).

## 5. Step queue

- **3.0.0 [plan + claim — 0 surface]** — this file (EN+PT), tracker Stage 3
  flip to 🟡, `DOING.md` claim. ✅ 19/09.
- **3.0.1 [recon — 0 code]** — `MakealivePrimitivesE2ETest` locks the §2/§4
  shapes on the 4 targets; outcome feeds back into this file (workflow 2.1.0
  discipline). ✅ 19/09 — 5/5 (JVM==JS parity, Native compile, SCRIPT throw/catch);
  achados devolvidos na nota "§4 status" acima — including the measured kof.io
  constructor+instance face (the sibling head's coordination line `c122d266` carried
  the same finding; my run clobbered their in-flight untracked file before I read it —
  the fix came from their DOING note, credited here). 🔵 next: 3.0.2 (⛔ Q1–Q4).
- **3.0.2 [design sign-off — ⛔ rule 6]** — maintainer poll Q1–Q4 (§6). No
  host, no compiler class, no ledger line before Q1 is answered.
- **3.1 [core]** — virtual-namespace injector (`CompilerMakealive`) +
  `makealive-host.kf` + ledger line (layer per Q1) + `MakealiveE2ETest`
  (plan/apply/destroy idempotency golden, JVM==JS byte parity, Native compile
  pin, the §3 guards).
- **3.3 [reconcile]** — `reconcile(design, provider, intervalMs)` delegating
  to `scheduler` (Native loud `CRON001` stub, same split as
  `workflow-sched-host.native.kf`).
- **3.4 [state]** — JSON-over-`kof.io` in v1 (VISION-sanctioned); `kof.db`
  backend as an additive follow-up (`DB001`/`ORM001` honest per target).
- **3.5 [providers as interop]** — generic REST provider (`kof.http`) + CLI
  provider (`kof.shell`); concrete clouds = **official packages**
  (`infra-<cloud>`, R1 — never a compiler literal).
- **3.2 [syntax `infra "prod" {}`]** — ⛔ R4 (codegen hook, tracker row R4:
  "does NOT exist at HEAD") + new parse block = rule 6. Out of v1.
- **3.7 [compile-time cycle]** — ⛔ R4 (same reason; runtime refusal ships in
  3.1 meanwhile).
- **3.8 [`kof infra` CLI]** — command contract = maintainer decision (rule 6,
  the 2.6 posture): `kof run infra.kf` is already the runner once 3.1 lands.

## 6. Open questions (maintainer decisions — do NOT resolve in code)

- **Q1 — namespace (the §2.1 collision).**
  **A) `kof.makealive`** *(recommended)* — the name IS the decided domain
  (VISION §4.2 "Kof Makealive"); passes hard-deny untouched; ships as the
  workflow/shell pattern (virtual namespace + pure-Kof host + ledger
  `platform` line).
  B) official package from day one — the strictest R1 reading ("official
  package only" is the gate's own message), but self-hosting the core on the
  1.5.3 registry whose live GitHub round-trip is still a pending smoke.
  C) edit the HARD-DENY list to scope it to `infra-*`/`cloud` — the gate
  encodes invariant 1; only she can move it.
- **Q2 — v1 provider:** local-FS provider only *(recommended)*, or also the
  REST generic in the MVP? (recon+3.1 stay identical either way; only the
  queue tail moves.)
- **Q3 — state backend v1:** JSON over `kof.io` *(recommended; VISION §4.2
  sanctions "or JSON in kof.io")* or `kof.db` from day one (inherits the
  `DB001`/`ORM001` non-JVM gates into the core golden)?
- **Q4 — surface shape:** flat injected host (workflow/supervisor idiom,
  no `makealive.` prefix) and the English faces `resource`/`requires`/`plan`/
  `apply`/`destroy` *(recommended — parity with `job`/`dag`/`run`)* — confirm
  before the 3.1 golden freezes names.

## 7. What NOT to do

- **No HCL inside Kof** — the declarative form (3.2), when it comes, desugars
  over records; it never gets its own semantics.
- **No provider repository for everything** — AWS/Azure/GCP are interop
  (REST/CLI) or official packages; the core knows no cloud by name.
- **No new compiler/runtime primitive** — every capability routes to an
  existing namespace and inherits its gap codes.
- **No language-core growth** — row 8.6's golden (`LanguageCoreSurfaceTest`)
  must stay green by construction, not by editing the golden.
- **No plaintext secrets in state** — reference only until Stage 5 ships the
  redacting `Secret`/`KeyHandle`.
