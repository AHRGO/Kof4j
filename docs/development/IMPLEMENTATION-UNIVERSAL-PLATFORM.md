[English](IMPLEMENTATION-UNIVERSAL-PLATFORM.md) | [Português](IMPLEMENTATION-UNIVERSAL-PLATFORM.pt_BR.md)

# Implementation — Kof as a Universal Platform

**Type:** implementation tracking — **UNDER DEVELOPMENT** since 17/09/2026
(promoted from `future/` by maintainer decision; the R12 gate is **overridden**
— see `DECISIONS.md` §D-UNIVERSAL)
**Companion (vision/architecture):** [`docs/architecture/UNIVERSAL-PLATFORM-VISION.md`](../architecture/UNIVERSAL-PLATFORM-VISION.md)
— philosophy, domain map, architectural model, stdlib/interop strategy, risks
and non-goals that justify these steps.
**Base:** real state 0.4.0-beta — 7 targets (jvm stable, native x86_64 stable,
native.risc/native.arm toolchain+qemu, js alpha GraalJS, kofc native-only,
android Phases 1–4), stdlib as **compile-time dispatch tables** with diagnosed
gaps, real FFI (SQLite `.so`, FFM Vulkan compute, Java + GraalJS interop).

> **Rule of this document:** this is the **executable** face of the universal
> platform. Every item below is a unit of work with a status, an owner lane and
> (when landed) a proof. The staged capability order is fixed
> (FOUNDATION → SYSTEMS → AUTOMATION → INFRASTRUCTURE → DATA → SECURITY →
> SCIENTIFIC COMPUTING → BIOINFORMATICS → UNIVERSAL PLATFORM) and **Stage 1
> closes before any later stage opens** (R12, overridden only for the
> *scheduling* of this plan — never for the freeze/quality rules). Each unit
> lands like any other change: Q0–Q7, additive, zero regression, proof in the
> same commit. The frozen core semantics stay 100% intact.

---

## Status legend

| Mark | Meaning |
|------|---------|
| ✅ | **DONE** — landed with proof (date + commit + test) |
| 🟡 | **PARTIAL / IN PROGRESS** — partially landed or an owner is working it |
| 🔵 | **TODO** — executable, unblocked, no owner yet |
| ⛔ | **NEEDS DECISION** — a maintainer decision (rule 6), never an agent edit |
| 🔒 | **BLOCKED** — depends on another item landing first |

Owner lanes are identified by the local-IP convention of `DOING.md`
(`.15` = bugs-and-gaps/docs, `.17`/`.18` = native/development, `.22` = compiler).
Claim an item in `DOING.md` **in the same commit** that starts the work.

## Summary

| Stage | Name | Status | Blocker |
|-------|------|--------|---------|
| 1 | SYSTEMS (consolidation) | 🟡 in progress | GC x86 sign-off ⛔, registry ⛔ |
| 2 | AUTOMATION | 🔵 not started | Stage 1 |
| 3 | INFRASTRUCTURE (Kof Makealive) | 🔵 not started | Stage 2, R3 (FFI), R4 (codegen hook) |
| 4 | DATA (engineering / science / ML) | 🔵 not started | Stage 3, R3 (FFI) |
| 5 | SECURITY (expansion) | 🔵 not started | Stage 3, R3 (FFI) |
| 6 | SCIENTIFIC COMPUTING | 🔵 not started | Stage 4, R3, GC (1.2) |
| 7 | BIOINFORMATICS | 🔵 not started | Stages 2/4/6 |
| 8 | UNIVERSAL PLATFORM | 🔵 not started | all previous |

Invariants: **R1 ✅ · R6 ✅ · R7 ✅ · R8 ✅ · R12 ✅ (overridden)** ·
**R2 🔵 · R3 🟡 · R4 🔵 · R5 🟡 · R9 🟡 · R10 🔵 · R11 🟡**

Cross-cutting queue (not a stage): **X1–X10** — gRPC, Python/R, WASM,
compile-time eval, variance/sealed, interop reflection, DWARF/source-map
debugger, property-based tests, `kof deploy`, domain LSP. Permanent non-goals
(VISION §12) listed at the end. **Gap audit 18/09** closed the VISION×tracker
drift (these items had no executable entry).

---

# Stage 1 — SYSTEMS (consolidation of what is already "systems")

**Objective:** close the *systems* parity gaps that already exist — do not open
a new domain. **This stage closes before any Tier 6+ (R12).**

### 1.1 Parity gaps (web / HTTP / media)

| # | Item | Status | Owner | Proof / note |
|---|------|--------|-------|--------------|
| 1.1.1 | `WEB001` — JS web server base | ✅ 16/09 | web lane | `WEB001` closed; JS GraalJS HttpServer |
| 1.1.2 | `WEB005` — `app.serveDir` gap code on non-JVM | ✅ 17/09 | `.15` | `b4957c06`; `KofMediaE2ETest.serveDirOnNonJvmEmitsWeb005NotWeb001` (JS + 3 native targets); was emitting phantom `WEB001` (§275) |
| 1.1.3 | `WEB002` — TLS on JS/Native | 🔵 | web lane | honest compile-time gap; pinned by `DomainGapCodesTest` |
| 1.1.4 | `WEB003` — SSE on Native (JS handler-scoped ✅ 16/09) | 🔵 | web lane | honest gap; pinned |
| 1.1.5 | `WEB004` — WebSocket on Native | 🔵 | web lane | honest gap; pinned |
| 1.1.6 | `WEB006` — security middleware on JS/Native | 🔵 | web lane | honest gap; pinned |
| 1.1.7 | `HTTP002` — https + real DNS on Native | 🔵 | native lane | HTTP/1.1 asm landed 03/09; `timeout`/`retry`/`circuit` REAL on the 4 native targets since 17/09 (§259 CLOSED). `HTTP002` is a **reserved** code (branch dead — `KofHttp.supportedOn` always true) |
| 1.1.8 | `MEDIA001`/`MEDIA003` — media handles / mic on non-JVM | 🔵 | queued behind the HTTP facades (`.22`) | JVM-only; honest compile-time gap; documented in the matrix |
| 1.1.9 | `ORM001` — `kof.orm` on Native | 🔵 | native lane | JVM + JS closed (JS 18/09, `KofJsOrmBridge`); Native still `ORM001` |
| 1.1.10 | §278 — Android reuses `JvmBackend` but refuses `kof.db`/`kof.security`/`kof.gpu` (`DB001`/`SECN00x`/`GPU001`) | 🔵 | compiler lane (rule 6) | measured with `CompilerDriver(Target.ANDROID)`; catalogued `known-bugs.md` §278; pin `DomainGapCodesTest.androidRefusesDbAndCryptoWithTheDocumentedCodes` |

### 1.2 GC mark-sweep in Native

| # | Item | Status | Owner | Proof / note |
|---|------|--------|-------|--------------|
| 1.2.1 | GC on riscv64 | ✅ | native lane | `356f33b9` |
| 1.2.2 | GC on x86_64 — decomposed G-1..G-5 | 🟡 | native lane | `docs/development/native-multiarch.md`; **auto-collect disabled** for requiring safe-points (`status.md` #1); `KofGcE2ETest` 3/3 |
| 1.2.3 | Re-baseline sign-off for x86 auto-collect | ⛔ | **maintainer** | §260 G-6(a) re-measured — the gate bites; needs maintainer re-baseline decision |

### 1.3 Event-loop / real JS async

| # | Item | Status | Owner | Proof / note |
|---|------|--------|-------|--------------|
| 1.3.1 | `CONC003` — real JS async/await/Promise | ✅ 03/09 | JS lane | residual `CONC003-JS-01` (only task-lambdas can be async) |
| 1.3.2 | §132 — cooperative scheduling in KofJS (supervisor) | ✅ 18/09 | `.18` | shipped as **cooperative async `time.sleep`** (`06d8b322`) — NOT the earlier generators+logical-clock sketch: await-point via `computeAsyncColoring` + Promise `kofTimeSleep` + `KofJsRunner` host pump; `OTP002` lifted. Proof: `AsyncSleepJsE2ETest` + `KofSupervisorE2ETest#supervisorJsParity`/`#supervisorJsS2Parity` (JS -> `restarts=2 fabrica=3`) |

### 1.4 Typed query DSL

| # | Item | Status | Owner | Proof / note |
|---|------|--------|-------|--------------|
| 1.4.1 | `User.query { where ... }` | ✅ 01/09 | `.18` | `KofOrmE2ETest`; `0112bf32` (§193) |

### 1.5 Package manager MVP (`kofdeps`)

| # | Item | Status | Owner | Proof / note |
|---|------|--------|-------|--------------|
| 1.5.1 | `kof deps` + Maven Central resolution | ✅ | tooling lane | — |
| 1.5.2 | Transitive resolution + `kofdeps.lock` | ✅ 16/09 | tooling lane | `DepsTransitiveTest` 10/10 (incl. real-Maven E2E) |
| 1.5.3 | Registry MVP | ⛔ | **maintainer** | needs maintainer decision (scope/hosting) |

### 1.6 Tracing / OpenTelemetry + `application{}` lifecycle

| # | Item | Status | Owner | Proof / note |
|---|------|--------|-------|--------------|
| 1.6.1 | W3C spans + `application{}` lifecycle (3 targets) | ✅ | platform lane | `KofObservabilityTest` 10/10 |
| 1.6.2 | OTel export `exportSpans()` → OTLP/JSON (JVM/JS) | ✅ 17/09 | platform lane | `435b7013` |
| 1.6.3 | `OBS003` — OTel export on Native | 🔵 | native lane | honest compile-time gap (R7 JVM-first); pinned by `DomainGapCodesTest` |

### 1.7 Native → bare-metal / bootable

| # | Item | Status | Owner | Proof / note |
|---|------|--------|-------|--------------|
| 1.7.1 | HAL seam `kof_plat_*` + freestanding profile (faces B-0…B-5) | 🔵 | native lane | `docs/development/future/PLAN-BAREMETAL-BOOT.md`; **not scheduled** — MCU depends on 1.2 |
| 1.7.2 | Scheduling of the bare-metal faces | ⛔ | **maintainer** | 15/09 directive; plan only, no owner assigned |

---

# Stage 2 — AUTOMATION (unified layer)

**Objective:** Kof as a *unified* automation layer (replace
Bash+Python+YAML+jq+sed+awk **in a single typed language**).
**Dependencies:** Stage 1 (concurrency, scheduler, mq ready).
**NOT to do:** do not reimplement bash; jobs are **Kof code**, not YAML.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 2.1 | `kof.workflow` / `kof.batch` — jobs, pipelines, retry, checkpoints, dead-letter | 🔵 | — | Stage 1 |
| 2.2 | `kof.shell` — idiomatic shell over `kof.process` | 🔵 | — | Stage 1 |
| 2.3 | `kof.ssh` — via FFI/interop | 🔵 | — | R3 (FFI) |
| 2.4 | Mature cron/scheduler | 🟡 | concurrency lane | `at(cron)` real 5-field UTC on JVM/JS since 17/09 (§274); Native `CRON001` honest gap |
| 2.5 | CI/CD pipelines as **Kof code** | 🔵 | — | 2.1 |
| 2.6 | Tooling: `kof workflow run` | 🔵 | — | 2.1 |

---

# Stage 3 — INFRASTRUCTURE (IaC + cloud) — **Kof Makealive**

**Objective:** infrastructure as **typed Kof code** with
plan/apply/state/reconciliation.
**Dependencies:** Stages 1–2; **formalized FFI** (R3, architectural
dependency); package capabilities (1.5).
**NOT to do:** HCL inside Kof; a provider repository for *everything*;
coupling the core to a provider.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 3.1 | `kof.infra` — resource records + dependency graph + diff | 🔵 | — | R4 (codegen hook, 2.2.2) |
| 3.2 | `infra "prod" { ... }` — desugar over records (compile-time codegen) | 🔵 | — | R4 |
| 3.3 | Reconciliation loop (spawn/await + channel) | 🔵 | — | Stage 1 (2.1) |
| 3.4 | State in `kof.db` | 🔵 | — | 3.1 |
| 3.5 | Providers via FFI/REST/CLI (AWS/Azure/GCP — interop) | 🔵 | — | R3 |
| 3.6 | Secrets via `kof.security` | 🟡 | security lane | `kof.security` exists; `Secret`/`KeyHandle` pending (Stage 5) |
| 3.7 | Cycle detection in the `infra` graph at compile-time | 🔵 | — | 3.1 |
| 3.8 | Tooling: `kof infra plan/apply/destroy` | 🔵 | — | 3.1 |

---

# Stage 4 — DATA (data engineering / science / ML)

**Objective:** an **orchestrated** scientific layer (not reimplemented).
**Dependencies:** Stages 1–3; R3 (FFI); Arrow as the exchange standard.
**NOT to do:** **do not build an ML/NumPy framework in Kof** — Kof provides the
*typed wrapper + pipeline*, the *engine* stays outside.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 4.1 | Typed `dataframe` (lazy, columnar) | 🔵 | — | R3 |
| 4.2 | **Arrow/Parquet via FFI** (typed wrapper) | 🔵 | — | R3 |
| 4.3 | Statistics/probability (wrapper + FFI) | 🔵 | — | R3 |
| 4.4 | `kof.ml` — inference via FFI (ONNX/libtorch); orchestrated training | 🔵 | — | R3 |
| 4.5 | Light visualization (SVG/`kof.ui` + FFI) | 🟡 | — | `kof.ui` exists; data-viz bindings pending |
| 4.6 | Experiment tracking (light, over `kof.db`/`kof.io`) | 🔵 | — | 3.4 |
| 4.7 | Tooling: pipeline profiling | 🔵 | — | 4.1 |

---

# Stage 5 — SECURITY (expansion)

**Objective:** from "application security" (already strong) to **platform
security** (network, forensics, defensive) — plus a **modern cryptographic
layer + post-quantum** (vision §4.8.1).
**Absolute rule:** **never** homemade crypto — every new primitive (incl. PQC)
is FFI to an audited lib; identical API across targets; gap = diagnosis
(`SECN00x`/`SECPQ`), never a weak stub.
**Dependencies:** Stages 1–3; R3 (FFI).
**NOT to do:** reimplement audited crypto stacks; offensive work without
legitimate/controlled context; defend *first*.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 5.1 | S2 — `Secret` type + `KeyHandle` (forced redaction) | 🔵 | security lane | R3 |
| 5.2 | S3 — `keys.*` (generate/derive/rotate/store) | 🔵 | security lane | 5.1 |
| 5.3 | S4 — asymmetric crypto (RSA/ECC/X.509/TLS) via FFI | 🟡 | security lane | RS/ES JWT already on JVM; X.509/TLS pending |
| 5.4 | S5 — **PQC** hybrid (ML-KEM-768 + ML-DSA-65 + HKDF + AES-256-GCM) via `liboqs` | 🔵 | security lane | R3; gap code `SECPQ` |
| 5.5 | S6 — hybrid KEM+KDF+AEAD | 🔵 | security lane | 5.4 |
| 5.6 | S7 — `secure.channel` (KEM+KDF+AEAD+auth+replay) | 🔵 | security lane | 5.5 |
| 5.7 | `kof.net` / packet parsing (FFI to `libpcap`) | 🔵 | — | R3 |
| 5.8 | Forensics (FFI to parse libs + Kof pipelines) | 🔵 | — | 5.7, Stage 2 |
| 5.9 | Security automation / threat-intel (`kof.http` + `spawn`/`channel` + `kof.log`) | 🔵 | — | Stage 2 |
| 5.10 | Defensive (monitoring/detection/audit over `kof.observability` + `kof.log` + `kof.db`) | 🟡 | — | pieces exist (1.6) |

---

# Stage 6 — SCIENTIFIC COMPUTING (numeric / HPC)

**Objective:** Kof as a **typed scientific orchestration language** + numeric
zone via FFI.
**Dependencies:** Stages 1–4; R3 (FFI); GC mark-sweep (1.2).
**NOT to do:** reimplement BLAS/LAPACK/NumPy; ownership/borrowing in the core
(the non-GC zone is via FFI to C/Rust).

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 6.1 | Linear algebra via **FFI to BLAS/LAPACK** (wrapper) | 🔵 | — | R3 |
| 6.2 | SIMD/vectorization (Native — research) | 🔵 | native lane | 1.2 |
| 6.3 | GPU — Vulkan via FFI (exists); CUDA/OpenCL via FFI | 🟡 | — | Vulkan compute exists; CUDA/OpenCL pending |
| 6.4 | Data-parallel (research) | 🔵 | — | 6.2 |
| 6.5 | Scoped resources (GPU/files/connections) | 🟡 | compiler lane | `future/scoped-resources-plan.md`; `using` syntax gated by bump ⛔ |
| 6.6 | Distributed (FFI to MPI + Kof orchestration) | 🔵 | — | R3, 2.1 |
| 6.7 | Tooling: HPC profiling | 🔵 | — | 6.1 |

---

# Stage 7 — BIOINFORMATICS

**Objective:** a typed platform for **scientific/genomic pipelines**.
**Dependencies:** Stages 2, 4, 6.
**NOT to do:** turn Kof into an exclusive biology language; reimplement
aligners/variant callers.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 7.1 | `kof-bio` (official package): FASTA/FASTQ/VCF/BAM as typed records | 🔵 | — | R3, R5 |
| 7.2 | Alignment/variants via **FFI/CLI** (BLAST/htslib — do not reimplement) | 🔵 | — | 7.1, R3 |
| 7.3 | Genomic pipelines (Stage 2 `workflow` model + checkpointing) | 🔵 | — | 2.1 |
| 7.4 | HPC (Stage 6) | 🔵 | — | 6.1 |
| 7.5 | Lab automation (`kof.http` REST + `kof.process` via FFI) | 🟡 | — | pieces exist |

---

# Stage 8 — UNIVERSAL PLATFORM (integration)

**Objective:** an application **+** its infra **+** its deploy **+** its data
pipeline **+** its security **+** its research — **in the same language**, with
the same development experience.
**Dependencies:** all the previous ones; package manager; FFI.
**NOT to do:** let the core grow to "support" the platform — the core must
**not change** (or change almost nothing) up to here.

| # | Item | Status | Owner | Depends on |
|---|------|--------|-------|------------|
| 8.1 | Total integration of Stages 1–7 | 🔵 | — | all |
| 8.2 | Mature package manager | 🔵 | — | 1.5.3 (registry ⛔) |
| 8.3 | LSP/debug/profiler by domain | 🟡 | — | LSP exists; per-domain pending |
| 8.4 | Multi-target deploy (same source → JVM/Native/JS) | 🟡 | — | 7 targets work today; maturity pending |
| 8.5 | Documentation/corpus (`training/`) of the domains | 🔵 | docs lane | per domain |
| 8.6 | **Final test:** the language core barely grew | 🔵 | — | verification at the end |

---

# Cross-cutting queue (VISION §6.1 interop + §7 compiler + §9 tooling)

> Gap audit 18/09: these capabilities were described in the VISION companion
> (interop surfaces, compiler requirements, tooling) but had **no executable
> item** in the Stage 1–8 tables. They are cross-cutting, not a domain stage.
> Each enters as its own unit when its stage opens; none changes the frozen
> core. `⛔` = a maintainer decision (rule 6) is required before any edit.

| # | Item | Status | Owner | Source / note |
|---|------|--------|-------|---------------|
| X1 | gRPC in `kof.web` (`app.grpc { }` + `.proto` → IR codegen + `grpc.call`) | 🔵 | web lane | VISION §6.1 "B/C"; `roadmap.md` §19 (31/08) — JVM parity first, Native/JS later |
| X2 | Python/R interop (CLI/`kof.process` + JSON protocol) | 🔵 | — | VISION §6.1 "B"; the scientific ecosystem as a *tool*, not a dependency (Stage 4) |
| X3 | WebAssembly target/interop | 🔵 | — | VISION §6.1 "D" (future); component portability — research, not scheduled |
| X4 | Light compile-time evaluation (domain const-folding, schema/cycle validation) | 🔵 | compiler lane | VISION §7 "B" — extends the optimizer; NOT a general TCC; distinct from R4 (codegen) |
| X5 | Variance / sealed types | ⛔ | **maintainer** | VISION §7 "B/C" — useful for scientific collections; a core type-system change (rule 6); type-classes stay rejected |
| X6 | Interop reflection (restricted to interop) | ⛔ | **maintainer** | VISION §7 "C" — ML/science schema discovery; a core change (rule 6); never a foundation |
| X7 | Debugger Native DWARF + JS source maps | 🟡 | tooling lane | VISION §9; `roadmap.md` §19.5 phases 4–7 — JS source map V3 landed 01/09 (`KofJsSourceMapTest`); Native DWARF pending |
| X8 | Property-based testing | 🔵 | — | VISION §9 / R10 — numeric invariants (science); extends `kof.test` |
| X9 | `kof deploy` (build + package + publish) | 🔵 | tooling lane | VISION §9 — on top of the existing packager |
| X10 | Domain-sensitive LSP (completion + go-to-definition in packages) | 🔵 | tooling lane | VISION §9 — same frontend, no parallel parser |

---

# Permanent non-goals (VISION §12)

> Explicit and permanent: these are **not** work items and must not be opened as
> gaps. They protect the language's identity (the anti-god-language fence).

Kof is **not**: a god-language · a shell · the Arrow/Parquet/BLAS/CUDA engine ·
an ML framework · a DBMS · a cloud provider repository · a genomic aligner ·
"Kali in Kof" · a notebook/IDE/kernel · ownership/borrowing · annotations/open
macros/type-classes as a foundation · JS parity for heavy domains · a target per
domain · a reimplementation of the scientific ecosystem.

---

# Invariants R1–R12

| # | Invariant | Status | Proof / note |
|---|-----------|--------|--------------|
| R1 | Lock the core/platform boundary (§3.4 order as an invariant rule) | ✅ 17/09 | `5f1422c6` — `scripts/check_stdlib_boundary.sh` + ledger (31 namespaces) + CI + `--selftest`; AGENTS invariant 1 |
| R2 | Generalize "capability/link by use" to all packages/domains | 🔵 | seed: SQLite/MySQL `.so` linked only when the literal DSN appears; extension pending |
| R3 | Formalize FFI as first-class | 🟡 | **JVM scalar ABI + `void` 18/09 (`.18`)**: `kof_ffi`/`kof_ffi_void` bind arbitrary arity over {Int,Long,Float,Double,Boolean,String} in/out, `String` reads `char*`, `void` returns discarded as statement. `FfiE2ETest` covers `pow`/`strstr`/`srand`/`atol→labs` (Long) + `FfiSignatureTest` locks the full scalar→layout mapping. **JS parity CLOSED 18/09 (3.6 F1+F2+F3, `.18`)**: the same scalar ABI now binds on the JS target via the host FFM bridge `KofJsFfiBridge` (`extern`→`kofFfi`→`kof_platform.ffi` `ProxyExecutable`), proven byte-for-byte JVM↔JS (`FfiE2ETest` +7 `assertJvmJsParity`); a browser has no host → honest runtime degrade (R7, like `kof.io`). See §R3-slices for the full decomposition — remaining: opaque handles/out-buffers (3.3 ⛔), callbacks/upcalls **JS parity** (3.4-C3; the JVM binds callbacks ✅ 18/09), variadics (3.5 ⛔), struct/array D6 (3.8 ⛔), Native parity (§61, 3.7). Only Native (`FFI001`) + non-scalar signatures on JS (`FFI002`) remain honest per-target gaps (R7). |
| R4 | Formalize compile-time codegen (`CodegenStep`) | 🔵 | does NOT exist at HEAD (2.2.2); blocks `infra "prod" {}` (3.2) and DDL/runner migration |
| R5 | Stability tiers + official packages | 🟡 | tiers defined in `backend-parity.md` §Stability tiers; **per-namespace tier marking not yet applied** — decision ⛔ |
| R6 | Keep "never silent" for new domains | ✅ 17/09 | machine gate `DomainGapCodesTest.everyPinnedGapIsDocumentedInTheParityMatrix` (`19a740f2`) + full ledger sweep (`c5897cd5`, found §278) |
| R7 | Honest scope per target (JVM-first / Native systems / JS web) | ✅ | adopted strategy; enforced by the documented gaps (`OBS003`, `GPU001`, `PROC001`, `SECN00x`, `MEDIA00x`) |
| R8 | Keep the tooling on the SAME frontend | ✅ | current rule (LSP, `kof deps`, CLI consume the compiler frontend; no parallel parser) |
| R9 | Interop-first as the domain default | 🟡 | adopted; FFI formalization pending (R3) |
| R10 | Correct and deterministic by default (science) | 🔵 | applies from Stage 4/6 (property-based + golden) |
| R11 | Security: defense first | 🟡 | adopted (never homemade crypto; FFI to audited libs); PQC pending Stage 5 |
| R12 | Do not interrupt the present (meta-rule) | ✅ overridden 17/09 | `DECISIONS.md` §D-UNIVERSAL — overrides the *scheduling* gate, never the freeze/quality; still the default for the other `future/` plans |


## R3 slices — FFI decomposition to full parity

Incremental R3 slices toward "total FFI parity" (maintainer directive 18/09).
⛔ = maintainer design decision (rule 6); 🔵 = still open; ✅ = landed.

| # | Slice | Status | Owner | Prerequisite |
|---|-------|--------|-------|--------------|
| 3.1 | JVM: general scalar ABI — arbitrary arity, {Int,Long,Float,Double,Boolean,String} in and out, String reads back char* | ✅ 18/09 (.18) | dev .18 | — |
| 3.2 | JVM: void return (kof_ffi_void, V descriptor; result discarded as statement) | ✅ 18/09 (.18) | .18 | — |
| 3.3 | JVM: opaque handles / out-buffers (void*, T*, Array<Byte> as buffer) — pointer-to-opaque / byte-buffer type, NOT the full D6 struct ABI | ⛔ surface decision | maintainer | design |
| 3.4 | JVM: callbacks / upcalls (Linker.upcallStub) — a Kof function handed to C as a function pointer | 🟡 **C1+C2 ✅ (JVM binda)** · C3 JS open | .18 | closure semantics + GC rooting (R12/1.2); synchronous/non-escaping only, primitive callback ABI; see §R3-3.4 |
| 3.5 | JVM: variadics (printf, execlp) — how to represent `...` in a Kof signature | ⛔ surface decision | maintainer | design |
| 3.6 | JS: parity via host bridge (the GraalJS/node runner IS a JVM with java.lang.foreign on the host) — browser stays an honest runtime degrade (R7: no host `kof_platform.ffi`) | ✅ 18/09 (.18) | .18 | 3.1/3.2 ABI |
| 3.6.F1 | Host FFM bridge `KofJsFfiBridge` + `KofJsFfiBridgeTest` (8/8) — same downcall as `kof_ffi`, proven at host level; compiler gate left CLOSED (zero backend risk) | ✅ 18/09 (.18) | .18 | — |
| 3.6.F2 | Compiler JS routing: `isExternBound` JS branch + lower `extern`→`kofFfi`/`kofFfiVoid`→`kof_platform.ffi` (`JsRuntimeOps` route + `JsRuntimeIo` helper + `KofJsRunner` `ProxyExecutable`) — opens the JS scalar gate | ✅ 18/09 (.18) | .18 | F1 |
| 3.6.F3 | Byte-for-byte JVM↔JS parity E2E — `FfiE2ETest` +7 `assertJvmJsParity` (abs/atoi/sqrt/pow/atol→labs Long/strstr/srand void): same `.kf`, identical output on both targets | ✅ 18/09 (.18) | .18 | F2 |
| 3.7 | Native: dlopen/dlsym in asm — depends on §61 (init glibc/TLS in _start) | 🔵 | native lane | §61 |
| 3.8 | Struct/array ABI (full D6) | ⛔ | maintainer | D6 |
| 3.9 | Meta-parity: same extern source with the same behavior on every CAPABLE target (R7 honest-scope on the incapable ones) | meta | — | 3.1–3.8 |

3.1+3.2 landed 18/09 → the JVM has the full scalar ABI + void, **proof-hardened**
(`Int`/`Long`/`Double`/`String`/`void` e2e-proven against libc/libm incl. `Long` via
`atol`→`labs`; whole set mapping-locked by `FfiSignatureTest`). **3.6 (JS parity)
CLOSED 18/09** across F1 (host bridge, gate closed) → F2 (compiler JS routing opens
the scalar gate) → F3 (byte-for-byte JVM↔JS parity E2E, +7). On the JS target the
**scalar ABI now binds on the GraalJS/node host runner** (FFM on the host, no guest
bytecode); a browser has no `kof_platform.ffi` host so it throws an honest runtime
error (R7, same degrade as `kof.io`); non-scalar signatures (array/struct/pointer)
still `FFI002` at compile time (3.3/3.5/3.8 ⛔). **Callback/upcall (3.4): design
measured (probe green) and C1+C2 landed 18/09 — the JVM now binds primitive callbacks**
(`extern` with a function-typed param → `Linker.upcallStub` over the Kof function
value, `JvmFfiCallbackE2ETest` computes `42/42/6.0/7.5` across Int/Long/Double/mixed);
JS callback parity (C3) stays `FFI002` — full design in §R3-3.4 below. Otherwise the
next R3 work is the native §61 (3.7, native lane) or maintainer decisions
(3.3/3.5/3.8).

### §R3-3.4 — callbacks / upcalls (a Kof function handed to C)

**Goal.** `extern` accepts a Kof function value as a *callback* parameter: C is given
a real function pointer that, when invoked, runs the Kof closure and returns its
result — the FFM **upcall** mirror of the 3.1 downcall.

**Surface.** A function-typed `extern` parameter, e.g.
`extern "lib.so" each(Int n, (Int, Int) -> Int cb): Int`; the closure is lowered into
the `Object[]` arg as the Kof function value (a `FunctionValue` implementing a
synthetic specialized interface, e.g. `int invoke(int,int)` — measured). Callback
**parameters are restricted to the primitive set {Int, Long, Float, Double, Boolean}**
and the callback return to primitive-or-void: the specialized interface already gives
unboxed FFM carriers, so no boxing adapter is needed. **String is NOT yet supported
inside a callback** (an `ADDRESS`↔`String` conversion is not wired at the upcall
boundary) — it stays an honest `FFI001`/`FFI002` (a follow-on, not a silent stub).

**Signature encoding.** `FfiSignature.signature` encodes a callback parameter as a
**nested paren token `(<retchar><paramchars>)`** (so it stays 1:1 with the argument —
e.g. `each(Int n, (Int,Int)->Int cb): Int` → `ii(iii)` : ret `i`, param `i`, callback
token `(iii)`); native layout = `ADDRESS` (function pointer). `kof_ffi` parses with a
cursor (a `(` consumes its nested descriptor up to the matching `)`).

**Runtime (generated `kof_ffi`).** On a `(` (callback) arg the incoming Kof function
object is turned into a stub by finding its `invoke` reflectively (by name + the
callback arity), unreflecting it and pinning the carrier type:
`Linker.upcallStub(lookup.unreflect(invoke).bindTo(closure).asType(unboxedCarrierType),
innerFnDesc, arena)` → a `MemorySegment` used as the `ADDRESS` spreader arg. Because
the Kof interface is **specialized** (`int invoke(int,int)`) the `.asType(...)` is a
no-op — the carriers already match the FFM `ValueLayout`s; the `.asType` remains as the
general boxing/unboxing bridge (**measured** in C1, where an erased
`Object invoke(Object,Object)->Object` closure bridged the same way returned `42`
through a real C upcall). Rooting: the stub is allocated in the call's
`Arena.ofConfined()` (≈ `kof_ffi`'s existing confined arena) and stays alive exactly
while C holds it.

**Honest restriction (slice scoping, R6/R7).** **The `extern` callback contract is
synchronous, non-escaping** — the stub lives exactly for the duration of the call
(confined `Arena`), so it is valid while C calls back *before returning* (`qsort`-style
comparators, `each`, `foreach`). This mirrors C's own rule that you must not free a
callback the callee still holds: passing a Kof callback to an API that *stores* it past
return (`atexit`, `signal`, async) is **out of contract** and would be use-after-free.
The compiler cannot observe C's retention, so this is a **documented contract**, not a
silent stub; an **explicit persistent-callback binding** (a real GC root, R12) is a
separate future slice. What the gate *does* catch at **compile time** (R6, honest
`FFI001`/`FFI002`) are non-bindable ABIs: a `String`/struct/pointer inside a callback,
or callback-as-return.

**Per-target posture.** **JVM**: binds (FFM upcall on the host, same as downcall).
**JS**: parity *is* achievable — the GraalJS/node runner is a JVM, so `KofJsFfiBridge`
gets the identical upcall path; a browser has no host → honest runtime degrade (R7).
**Native**: §61 (3.7).

**Slices (mirror 3.6's F1→F3 discipline).** **C1** = host-level mechanism pin
(`JvmFfiCallbackTest`: upcallStub + `.asType` closure bridge + `Arena` rooting +
`ADDRESS` spreader, against a gcc-built temp `.so`), compiler gate **closed**, zero
backend risk. **C2** = `FfiSignature` callback token + `JvmFfiRuntime.kof_ffi`
cursor parse + `Linker.upcallStub`/`.asType` bridge + `isExternBound` JVM branch
→ opens the JVM callback gate, proven by `JvmFfiCallbackE2ETest` (a real `.kf`
computes `42/42/6.0/7.5` across Int/Long/Double/mixed callback ABIs; JS callback
stays `FFI002`; a non-bindable String-in-callback stays `FFI001`). **C3** = JS
callback parity (`KofJsFfiBridge` upcall + GraalJS re-entrancy) + browser degrade.
Status: **C1+C2 landed 18/09 (the JVM binds primitive callbacks)**, C3 open.

---

# Decisions needed (rule 6 — maintainer)

| # | Decision | Blocks |
|---|----------|--------|
| D1 | GC x86 auto-collect re-baseline sign-off (§260 G-6(a)) | 1.2.2/1.2.3, Stage 6 |
| D2 | Package registry MVP — scope/hosting | 1.5.3, Stage 3+, 8.2 |
| D3 | Bare-metal/bootable scheduling | 1.7 |
| D4 | R5 per-namespace stability tiers (which namespaces are `stable` vs `experimental`) | R5, Stage 7 (`kof-bio` official package) |
| D5 | `using` scoped-resources syntax (gated by bump) | 6.5 |
| D6 | R3 struct/array ABI design (signature-level) | 3.5, 4.2, 5.4, 6.1 |
| D7 | Value records / first-class value types (queue §2.7 of `roadmap.md` §23) | TIER 2.7 — planned, needs authorization to open |

---

# Critical path

`Stage 1 (SYSTEMS) closes` → Stages 2/3 → Stages 4/5 → Stage 6 → Stage 7 →
Stage 8.

Cross-cutting: **R3 (formalized FFI)** is the backbone of Stages 3–7 and
**R4 (codegen hook)** gates Stage 3 (`infra`). Within Stage 1, the remaining
non-decision items are parity gaps on the web/native lanes (1.1.3–1.1.10) and
the §132 JS scheduling redesign (1.3.2, `.18`) — **CLOSED 18/09** (`06d8b322`), so Stage 1 now awaits only the web/native parity gaps + the maintainer decisions D1–D3.

See the companion [`UNIVERSAL-PLATFORM-VISION.md`](../architecture/UNIVERSAL-PLATFORM-VISION.md)
for the *why* behind every item above.
