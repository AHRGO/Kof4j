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
| 1.3.2 | §132 — cooperative scheduling in KofJS (supervisor) | 🟡 | `.18` | multi-session redesign (generators + logical clock); `OTP002` gate stays until it lands |

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

# Invariants R1–R12

| # | Invariant | Status | Proof / note |
|---|-----------|--------|--------------|
| R1 | Lock the core/platform boundary (§3.4 order as an invariant rule) | ✅ 17/09 | `5f1422c6` — `scripts/check_stdlib_boundary.sh` + ledger (31 namespaces) + CI + `--selftest`; AGENTS invariant 1 |
| R2 | Generalize "capability/link by use" to all packages/domains | 🔵 | seed: SQLite/MySQL `.so` linked only when the literal DSN appears; extension pending |
| R3 | Formalize FFI as first-class | 🟡 | first slice opened by #431 (raylib): increments (1) arity, (2) `void` returns, (3) String return, (4) `const char*` mixed with numerics — all additive on the JVM (`FfiE2ETest` pattern); structs stay R3-proper (design decision ⛔); native side waits on §61 |
| R4 | Formalize compile-time codegen (`CodegenStep`) | 🔵 | does NOT exist at HEAD (2.2.2); blocks `infra "prod" {}` (3.2) and DDL/runner migration |
| R5 | Stability tiers + official packages | 🟡 | tiers defined in `backend-parity.md` §Stability tiers; **per-namespace tier marking not yet applied** — decision ⛔ |
| R6 | Keep "never silent" for new domains | ✅ 17/09 | machine gate `DomainGapCodesTest.everyPinnedGapIsDocumentedInTheParityMatrix` (`19a740f2`) + full ledger sweep (`c5897cd5`, found §278) |
| R7 | Honest scope per target (JVM-first / Native systems / JS web) | ✅ | adopted strategy; enforced by the documented gaps (`OBS003`, `GPU001`, `PROC001`, `SECN00x`, `MEDIA00x`) |
| R8 | Keep the tooling on the SAME frontend | ✅ | current rule (LSP, `kof deps`, CLI consume the compiler frontend; no parallel parser) |
| R9 | Interop-first as the domain default | 🟡 | adopted; FFI formalization pending (R3) |
| R10 | Correct and deterministic by default (science) | 🔵 | applies from Stage 4/6 (property-based + golden) |
| R11 | Security: defense first | 🟡 | adopted (never homemade crypto; FFI to audited libs); PQC pending Stage 5 |
| R12 | Do not interrupt the present (meta-rule) | ✅ overridden 17/09 | `DECISIONS.md` §D-UNIVERSAL — overrides the *scheduling* gate, never the freeze/quality; still the default for the other `future/` plans |

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
non-decision items are parity gaps on the web/native lanes (1.1.3–1.1.9) and
the §132 JS scheduling redesign (1.3.2, `.18`).

See the companion [`UNIVERSAL-PLATFORM-VISION.md`](../architecture/UNIVERSAL-PLATFORM-VISION.md)
for the *why* behind every item above.
