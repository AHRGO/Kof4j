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
| 1 | SYSTEMS (consolidation) | 🟡 in progress | GC x86 sign-off ⛔ (registry ✅ 19/09) |
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
| 1.2.2 | GC on x86_64 — decomposed G-1..G-5 + G-6 | ✅ | native lane; exec landed by `.18` under maintainer order | `docs/development/native-multiarch.md`; **auto-collect ON (G-6(a) 19/09, option D1-A)**: the free-list-exhaustion trigger fires `collect_now` once per program (blanket-spill 15 GPRs; gate `spawn_count==0` with `incq` hoisted to `kof_spawn_handle_new` entry); §260 CLOSED; in-repo cap-test `gcAutoCollectFitsUnderMemoryCap`; `KofGcE2ETest` 4/4; MT worker-stack face catalogued; hello 44→84 syms re-baselined with cause (bytes within the gate) |
| 1.2.3 | Re-baseline sign-off for x86 auto-collect | ✅ | maintainer | D1-A DECIDIDO 19/09; EXECUTADO 19/09 (`.18`, ordem da mantenedora): hello 44→84 syms re-baselined COM causa no comentário, bytes dentro do gate +5%; §260 CLOSED; trigger ligado (G-6(a)) com MT = comportamento antigo |

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
| 1.5.3 | Registry MVP | ✅ 19/09 | docs→platform lane | D2-A: **publish ✅** (`b1ea1718`-era) + **pull ✅ S2 19/09** — `DepsRegistry`: `owner/repo[@ver]` → asset `<repo>-<ver>.tar.gz` da API GitHub Releases, `SHA256SUMS` verificado ANTES de instalar, cache `~/.kof/deps/kof/`, `latest` pinna a versão no `kofdeps`; REG001–004 honestos (R6); `DepsRegistryTest` 6/6 + vizinhos `Deps*` 20/20; build/run consomem via `Deps.classpath()`; live GitHub round-trip = smoke manual pendente |

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
| 2.1 | `kof.workflow` / `kof.batch` — jobs, pipelines, retry, checkpoints, dead-letter | 🟡 | `.18` | Stage 1; **`workflow-plan.md`** SIGNED-OFF 19/09 (Q1–Q4 by maintainer poll); 2.1.0 recon DONE (`WorkflowPrimitivesE2ETest` 6/6 + lambda `Result` descriptor fix `8ec07214`); **MVP 2.1.2 LANDED 19/09** — pure-Kof host `workflow-host.kf` injected flat (`import kof.workflow` → `job`/`dag`/`after`/`run`/`Report`, no target gate, JVM==JS byte-parity, `WorkflowE2ETest` 8/8) + 2.1.4 docs (stdlib/workflow.md EN+PT, parity row, this flip); 2.1.3 started — retry face LANDED 19/09 (`flow.retry`/`retryFixed`/`exponential`/`Report.retries`); checkpoint/deadLetter/schedule pending |
| 2.2 | `kof.shell` — idiomatic shell over `kof.process` | 🟡 | `.18` | Stage 1; **`development/shell-plan.md`** SIGNED-OFF 18/09 (Q1–Q3 by maintainer poll); MVP 18/09: `cmd`/`run`/`ok` on JVM+JS byte-parity, `pipeline` JVM-only (`PROC001` on JS/Native, inherited from `process.spawn`); glob/`~`/redir out of v1; `ShellE2ETest` 11/11; 2.2.4 stdlib doc DONE 19/09 (`docs/stdlib/shell.md` +PT); 2.2.3 (`runWith` cwd/env, JS live pipes) open — row flips ✅ with it |
| 2.3 | `kof.ssh` — via FFI/interop | 🔵 | — | R3 (FFI) |
| 2.4 | Mature cron/scheduler | 🟡 | concurrency lane | `at(cron)` real 5-field UTC on JVM/JS since 17/09 (§274); Native `CRON001` honest gap |
| 2.5 | CI/CD pipelines as **Kof code** | ⏳ | — | 2.1 (MVP landed 19/09 — `dag`/`job` run on JVM+JS; full prerequisite needs 2.1.3 retry/checkpoint) |
| 2.6 | Tooling: `kof workflow run` | ⏳ | — | 2.1 (MVP landed 19/09 — `dag`/`job` run on JVM+JS; full prerequisite needs 2.1.3 retry/checkpoint) |

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
| 6.5 | Scoped resources (GPU/files/connections) | 🟡 | compiler lane | `future/scoped-resources-plan.md`; `using` syntax gated by bump ⛔ — **D5-B ✅ 19/09**: no new syntax — `close()` + `try/finally` pattern |
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
| 8.2 | Mature package manager | ✅ | plataforma lane (.15) | **19/09 (audit medido contra o DoD do roadmap 1.4)**: subcomandos `init/add/remove/list/resolve` (`Deps.java:48`); Maven Central GAV exato + transitivo via delegação Maven + `kofdeps.lock` (16/09, `DepsTransitiveTest` 10/10); **checksum sha256 verificado no pull** (`Deps.verifyChecksum`→`CmdDeploy.sha256Hex`); registry GitHub Releases: publish D2-A + pull 1.5.3-S2 (`DepsRegistryTest` 6/6). Modelo = GAV exato + lock reprodutível; **não medido** range/semver solving nem registry privado além de `owner/repo` — reabrir só com necessidade concreta (KOF-first: nenhum pedido hoje). |
| 8.3 | LSP/debug/profiler by domain | 🟡 | tooling/docs lane (.15) for LSP | **LSP domain-aware ✅ 19/09**: completion + hover read `StdCatalog` (namespace lists members; member in the exact `ns.` context names its face; loose = honest null). **rename cross-file ✅ 19/09 (LSP-A, `LspRename`)** + hygiene fix: the sibling scan never treats /tmp or the FS root as a project (depth 1 there; 485 foreign `.kf` measured in /tmp). **8.3-B DONE 19/09 (`.18`)**: `workspace/symbol`/hover/definition/references cover deps outside the file's parent via `initialize`'s `rootUri` (`LspProject.siblings(self,root)`, dedup+sorted; no rootUri = exact old behavior). **Pending:** debugger/profiler by domain (= X7 + future faces; profiler does not exist yet); signatureHelp per domain — **hover SIGNATURES ✅ 19/09 (LSP-A fatias 1–6: 31/32 ns, 263 membros/280 formas; tabela = artefato de `scripts/gen_signatures.py`, NUNCA edição manual)**: `StdCatalog.signaturesOf` (tabela em `StdCatalog`, fonte única) alimenta o hover de `db`/`http` com um overload por linha, travada COMPORTAMENTO-A-DISPATCHER (`StdCatalogSignaturesTest`: a aridade gravada binda no `staticCall` real, a proibida não). No caminho, **DOIS bugs reais do catálogo X10 achados pela fatia**: `KofDb.functions()` e `KofProcess.functions()` só tinham os `case` (connect/close/transaction) — `query`/`execute` viviam nas famílias `isQuery/isExecute` e sumiam do completion/hover (query/execute 3→5; run/spawn/exit 1→3) + lock passou a somar os literais das famílias (fonte-a-fonte). **Restante:** so `json` sem tabela por honestidade (dispatch por tipo no lowerer `JsonDispatch`, não por aridade travável no typer; membro sem tabela mantém hover simples — R6, com teste-mate). A fatia 3 trocou o lock de case-literals por um que segue vírgulas e **achou 4 drifts de catálogo ainda maiores** (net 1→8, encoding 2→8, math 19→23, strings 7→26 — 38 membros bindando no dispatcher, invisíveis no completion desde a fatia 1; todos corrigidos com trava comportamento-a-dispatcher) **REQUEST `textDocument/signatureHelp` ✅ 19/09 (LSP-A, `LspSignatureHelp`)**: a mesma tabela do hover — dentro de `ns.member(` com tabela devolve as formas + `parameters` + `activeParameter` (vírgulas de top-level, ciente de string/escape/parênteses/colchetes; clamp na forma mais larga); fora de chamada, membro sem tabela, ambíguo ou desconhecido = null honesto (R6; `json` fica sem tabela até haver dispatcher por aridade no typer). Capability `signatureHelpProvider` anunciada; travada no round-trip do servidor (`LspServerTest`) + 7 casos de borda (`LspSignatureHelpTest`) | |
| 8.4 | Multi-target deploy (same source → JVM/Native/JS) | 🟡 | tooling/docs lane (.15) | **core ✅ 19/09 (X9 fatia 4)**: `--target jvm,native,js`/`all` = same source, one release per face + `.deploy-manifest.json` (SUCCESS/FAIL honesto por alvo, R6/R7, exit 1 se alguma falha); maturity restante: faces cross (sysroot/DEP001) + publish (D2) — **D2-A ✅ 19/09**: `--publish` deixou de ser ⛔ (face GitHub Releases na fila) |
| 8.5 | Documentation/corpus (`training/`) of the domains | ✅ | docs lane | **FEITO 19/09 (3 fatias)**: `stdlib.md` (+time/process/net→cache/config/log/net + gpu/media + matriz por-target MEDIDA), `security.md` NOVO (+PT; 14 probes por-membro; no caminho 3 labels errados da tabela corrigidos NA RAIZ no gerador + trava de label + round-trip golden), observability metrics/health em `architecture.md`, `orm.saveAll` em `database.md`. Guardas: `StdlibIdiomsCompileTest` 18/18 + `KofScriptStdlibParityTest` (goldens interpretador==JVM medidos). Residual honesto: golden cross riscv/aarch das faces novas (guarda qemu — lane nat). |
| 8.6 | **Final test:** the language core barely grew | ✅ | plataforma lane (.15) | **19/09: `LanguageCoreSurfaceTest` 6/6** — trava por golden MEDIDO no tip (jshell, nunca memória): 64 palavras reservadas, 17 binários, 17 unários, 6 comparações, 119 tokens (ordem incluída), 8 variantes seladas de `Type` + `isSealed`. Qualquer crescimento do núcleo (gramática/operators/type-model) vira falha vermelha = rule 6 com bump+doc+migração no mesmo commit; feature de plataforma não passa mais por dentro do núcleo sem decisão explícita. |

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
| X8 | Property-based testing | 🟡 | docs→platform lane (192.168.100.15) | slices 1–2 ✅ 18/09: `rng` namespace (seedable xorshift128+splitmix32) on JVM+JS+**NATIVE x86_64** — `KofRngTest` 11/11 incl. JVM==JS and JVM==NATIVE byte parity (asm `RuntimeRng`, bits by construction) + honest `RNG001` on cross/ANDROID (`a71f761c`,`1ff54c6e`,`367af29d`); slice 3 = `kof.test` property runner; cross port needs qemu (native lane) — **X8-A ✅ 19/09**: kof.test = spec exata do roadmap §G6; **fatia 3 em curso** — **timeouts ✅ 19/09** (`kof test --timeout <sec>` mata o filho JVM/Native no prazo, FAIL honesto R6, JS best-effort declarado; `CmdTestTimeoutTest` 3/3); restante G6-next: named suites por diretório + fixtures; runner *property* = API de superfície ainda sem spec (regra 6, registrada no DOING) |
| X9 | `kof deploy` (build + package + publish) | 🟡 | tooling/docs lane (192.168.100.15) | slices 1–3 ✅ 18/09: JVM (fat jar) + NATIVE (ELF 0755) + JS (.mjs) + ANDROID (APK via build --apk pipeline) — release = artifact + RELEASE.md + SHA256SUMS + tar.gz (`CmdDeployTest` 9/9+1-skip, module 322/322; `154ea1a4`, `bfdd452a`, slice 3); cross riscv/arm = `DEP001` honest; `--publish`/registry = ⛔ D2; **slice 4 ✅ 19/09 (linha 8.4)**: multi-target da MESMA fonte — `--target jvm,native,js`/`all`, uma release por face (subdir `-jvm/-native/-kofjs`) + `DEPLOY-MANIFEST` (`.deploy-manifest.json`), FAIL por alvo não derruba os demais (R6), exit 1 com falha; `CmdDeployTest` 13 (11+2-skip), cli 339/0F — **slice 5 ✅ 19/09 (D2-A)**: `--publish` face = GitHub Releases (tar.gz+manifest, 422=reuse, no token = honest fail); multi-target (8.4) landed `28b004c4` |
| X10 | Domain-sensitive LSP (completion + go-to-definition in packages) | ✅ | docs→platform lane (192.168.100.15) | slices 1–3 ✅ 18/09: `StdCatalog` = **31 namespaces** completados por membros REAIS do typer (7 KofStd + time/http/db/cache/process + segurança×6 + json/log/orm/config/gpu/mq/validation/observability/tetris + Image/Audio/Video/Mic) — single-source travado contra a fonte (`StdCatalogTest` 10/10; **drift do db corrigido 19/09** (`functions()` 3→5: `query`/`execute` viviam nas familias `isQuery/isExecute` fora do lock de `case`; trava agora soma os literais das familias), `LspServerTest` 25/25; `48633d98`, `e79a3ea0`, `9e4d1728`); web/app-DSL + ui + ffi ficam de fora (R6 honesto); fatia 4 ✅ 18/09: go-to-definition **cruza arquivos do projeto** (`crossFileDefinition`, walk ≤6 + first-hit, convenção única `LspSymbols`; `null` honesto) — `0a4497c7`, `LspServerTest` 27/27; fatia 5 ✅ 18/09: **referências também cruzam arquivos** (read-only; varredura extraída p/ `LspProject` no split ≤600) — `f5df2362`, `LspServerTest` 28/28; fatia 6 ✅ 18/09: **`workspace/symbol`** indexa buffers + .kf irmãos (filtro/ordenação LSP) — `c04e16a4`, `LspServerTest` 29/29; fatia 7 ✅ 18/09: **hover de símbolos do projeto** (buffer+cross-file, linha completa; bug de framing byte-vs-char no teste-mate) — `848b7df1`, `LspServerTest` 30/30. **X10 CONCLUÍDA** (rename cross-file e assinaturas de membros = perguntas de superfície rule 6 no DOING) — **pós-X10 (LSP-A ✅ 19/09)**: rename cross-file + assinatura de hover (StdCatalog) aprovados ; **hover lock ✅ 19/09 (LSP-A)**: `LspServerTest.hoverCoversSliceThreeNamespacesFromSingleSource` (36/36) trava hover+completion sobre os 31 namespaces pela fonte-única do catálogo — zero código novo de tooling; assinatura de hover segue rule 6 (metadado embutido nos 24 typers `Kof*` — registrado no 8.3) |

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
| R3 | Formalize FFI as first-class | 🟡 | **JVM scalar ABI + `void` 18/09 (`.18`)**: `kof_ffi`/`kof_ffi_void` bind arbitrary arity over {Int,Long,Float,Double,Boolean,String} in/out, `String` reads `char*`, `void` returns discarded as statement. `FfiE2ETest` covers `pow`/`strstr`/`srand`/`atol→labs` (Long) + `FfiSignatureTest` locks the full scalar→layout mapping. **JS parity CLOSED 18/09 (3.6 F1+F2+F3, `.18`)**: the same scalar ABI now binds on the JS target via the host FFM bridge `KofJsFfiBridge` (`extern`→`kofFfi`→`kof_platform.ffi` `ProxyExecutable`), proven byte-for-byte JVM↔JS (`FfiE2ETest` +7 `assertJvmJsParity`); a browser has no host → honest runtime degrade (R7, like `kof.io`). See §R3-slices for the full decomposition. **Callbacks/upcalls (3.4) JVM+JS parity CLOSED 18/09 (C1→C3.4)**: `extern` with a function-typed param binds on both the JVM and the JS host runner — a Kof function value handed to C as a real function pointer (`Linker.upcallStub`), proven byte-for-byte JVM↔JS (`42/42/6.0/7.5` across Int/Long/Double/mixed; `5/104/2026` across `String`-arg — `char*`->`String` at the upcall boundary); the JS bridge calls the compiled `Lambda` object's `invoke` method (a Kof function value is an object, not a native arrow — discovered in C3.2); synchronous/non-escaping; callback ABI = primitives + `String` as an argument; a `String` **return** stays non-bindable (`FFI001`/`FFI002`); a browser degrades honestly (R7). Remaining: opaque handles/out-buffers (3.3 ⛔), variadics (3.5 ⛔), struct/array D6 (3.8 ⛔), Native parity (§61, 3.7). Only Native (`FFI001`) + non-scalar signatures on JS (`FFI002`) remain honest per-target gaps (R7). — **D6-A ✅ 19/09**: struct/array = spec-first (ver 3.8) |
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
| 3.4 | JVM+JS: callbacks / upcalls (Linker.upcallStub) — a Kof function handed to C as a function pointer | ✅ **C1→C3.4 landed 18/09 (JVM+JS bind primitive AND String-arg callbacks, byte-for-byte parity)** | .18 | closure semantics + GC rooting (R12/1.2); synchronous/non-escaping only; callback ABI = primitives + `String` arg (char*->String); `String`/struct/pointer **return** stays gated; JS bridge calls the `Lambda` object's `invoke`; see §R3-3.4 |
| 3.5 | JVM: variadics (printf, execlp) — how to represent `...` in a Kof signature | ⛔ surface decision | maintainer | design |
| 3.6 | JS: parity via host bridge (the GraalJS/node runner IS a JVM with java.lang.foreign on the host) — browser stays an honest runtime degrade (R7: no host `kof_platform.ffi`) | ✅ 18/09 (.18) | .18 | 3.1/3.2 ABI |
| 3.6.F1 | Host FFM bridge `KofJsFfiBridge` + `KofJsFfiBridgeTest` (8/8) — same downcall as `kof_ffi`, proven at host level; compiler gate left CLOSED (zero backend risk) | ✅ 18/09 (.18) | .18 | — |
| 3.6.F2 | Compiler JS routing: `isExternBound` JS branch + lower `extern`→`kofFfi`/`kofFfiVoid`→`kof_platform.ffi` (`JsRuntimeOps` route + `JsRuntimeIo` helper + `KofJsRunner` `ProxyExecutable`) — opens the JS scalar gate | ✅ 18/09 (.18) | .18 | F1 |
| 3.6.F3 | Byte-for-byte JVM↔JS parity E2E — `FfiE2ETest` +7 `assertJvmJsParity` (abs/atoi/sqrt/pow/atol→labs Long/strstr/srand void): same `.kf`, identical output on both targets | ✅ 18/09 (.18) | .18 | F2 |
| 3.7 | Native: dlopen/dlsym in asm — depends on §61 (init glibc/TLS in _start) | 🔵 | native lane | §61 |
| 3.8 | Struct/array ABI (full D6) | 🟡 | compiler lane (pós-spec) | **D6-A ✅ 19/09**: spec **rascunho escrita 19/09** (`ffi-abi-structs.md`+PT: ABIs medidas, 3 exemplos-resolução como golden, D6-1..D6-5 = decisões da mantenedora antes de 3.8, verruga `Arena.global` §1 catalogada); revisão da mantenedora, depois código |
| 3.9 | Meta-parity: same extern source with the same behavior on every CAPABLE target (R7 honest-scope on the incapable ones) | meta | — | 3.1–3.8 |

3.1+3.2 landed 18/09 → the JVM has the full scalar ABI + void, **proof-hardened**
(`Int`/`Long`/`Double`/`String`/`void` e2e-proven against libc/libm incl. `Long` via
`atol`→`labs`; whole set mapping-locked by `FfiSignatureTest`). **3.6 (JS parity)
CLOSED 18/09** across F1 (host bridge, gate closed) → F2 (compiler JS routing opens
the scalar gate) → F3 (byte-for-byte JVM↔JS parity E2E, +7). On the JS target the
**scalar ABI now binds on the GraalJS/node host runner** (FFM on the host, no guest
bytecode); a browser has no `kof_platform.ffi` host so it throws an honest runtime
error (R7, same degrade as `kof.io`); non-scalar signatures (array/struct/pointer)
still `FFI002` at compile time (3.3/3.5/3.8 ⛔). **Callback/upcall (3.4): FULLY LANDED
18/09 (C1→C3.4) — the JVM *and* the JS host runner now bind primitive callbacks AND
`String`-argument callbacks**
(`extern` with a function-typed param → `Linker.upcallStub` over the Kof function
value; a real `.kf` computes `42/42/6.0/7.5` across Int/Long/Double/mixed and `5/104/2026`
across String args, byte-for-byte JVM↔JS in `JvmFfiCallbackE2ETest`; the JS bridge invokes
the compiled `Lambda` object's `invoke` method, reading a `char*` callback arg to a Kof
`String` at the boundary — full design + the C3.2 object-vs-arrow discovery in §R3-3.4
below).
Otherwise the next R3 work is the native §61 (3.7, native lane) or maintainer decisions
(3.3/3.5/3.8).

### §R3-3.4 — callbacks / upcalls (a Kof function handed to C)

**Goal.** `extern` accepts a Kof function value as a *callback* parameter: C is given
a real function pointer that, when invoked, runs the Kof closure and returns its
result — the FFM **upcall** mirror of the 3.1 downcall.

**Surface.** A function-typed `extern` parameter, e.g.
`extern "lib.so" each(Int n, (Int, Int) -> Int cb): Int`; the closure is lowered into
the `Object[]` arg as the Kof function value (a `FunctionValue` implementing a
synthetic specialized interface, e.g. `int invoke(int,int)` — measured). Callback
**parameters are the bindable set {Int, Long, Float, Double, Boolean} plus `String`
(fatia 3.4-C3.4)** — a `String` callback parameter arrives as a C `char*` that the runtime
reads into a Kof `String` (the upcall mirror of the downcall `getString`); the callback
return is primitive-or-void **only**: returning a `String` would hand C a `char*` whose
memory owner is not observable under the synchronous contract, so a `String` **return**
stays an honest `FFI001`/`FFI002` (never a silent stub, R6). The primitive carriers are
already unboxed by the specialized interface, so no boxing adapter is needed for them.

**Signature encoding.** `FfiSignature.signature` encodes a callback parameter as a
**nested paren token `(<retchar><paramchars>)`** (so it stays 1:1 with the argument —
e.g. `each(Int n, (Int,Int)->Int cb): Int` → `ii(iii)` : ret `i`, param `i`, callback
token `(iii)`; a String arg rides the same token — `f(Int n, (String)->Int cb): Int` →
`i(iS)`); native layout = `ADDRESS` (function pointer). `kof_ffi` parses with a
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
through a real C upcall). **A `String` callback arg (3.4-C3.4)**: the `char*` the C side
passes has the `ADDRESS` native carrier, so the stub's method type takes a
`MemorySegment` there; `MethodHandles.filterArguments` inserts a `kof_ffi_cstr`
(`reinterpret(MAX).getString(0)`, NULL→null) that turns it into a `String` **before** the
Kof `invoke` runs, so the closure sees a real Kof string (content and all — proven by
`atol`-inside-callback). Rooting: the stub is allocated in the call's
`Arena.ofConfined()` (≈ `kof_ffi`'s existing confined arena) and stays alive exactly
while C holds it. **JS mirror (`KofJsRunner`)**: the cursor parse lives in `KofJsFfiBridge.call`
(a `(` slot → `ADDRESS`, the pre-built stub passed straight through), and `jsCallbackStub`
builds the `upcallStub` whose bridge is a fixed-arity static `executeJsX` reached via
`MethodHandles.asVarargsCollector` (NOT `asSpreader`, which the JDK rejects on a varargs
handle) calling `fn.getMember("invoke").execute(...)` — because on JS the function value is
a `Lambda` object, not a callable; a `String` callback arg arrives as the `MemorySegment`
carrier and `executeJsX` reads it via the same `getString` (→ host String → JS string)
before the call; the confined stub arena is opened by the `ProxyExecutable`
and closed after the synchronous downcall returns.

**Honest restriction (slice scoping, R6/R7).** **The `extern` callback contract is
synchronous, non-escaping** — the stub lives exactly for the duration of the call
(confined `Arena`), so it is valid while C calls back *before returning* (`qsort`-style
comparators, `each`, `foreach`). This mirrors C's own rule that you must not free a
callback the callee still holds: passing a Kof callback to an API that *stores* it past
return (`atexit`, `signal`, async) is **out of contract** and would be use-after-free.
The compiler cannot observe C's retention, so this is a **documented contract**, not a
silent stub; an **explicit persistent-callback binding** (a real GC root, R12) is a
separate future slice. What the gate *does* catch at **compile time** (R6, honest
`FFI001`/`FFI002`) are non-bindable ABIs: a struct/pointer arg, a `String` **return**
(a `String` callback **arg** binds since 3.4-C3.4), or callback-as-return.

**Per-target posture.** **JVM**: binds (FFM upcall on the host, same as downcall; a `String` arg crosses as `MemorySegment` read to Kof `String` by `kof_ffi_cstr`). **JS**: **binds (C3.2/C3.3 landed 18/09; String arg 3.4-C3.4)** — the GraalJS/node runner is a JVM, so the `KofJsRunner` `ProxyExecutable` gets the identical upcall path; a callback arg is marshalled by building `Linker.upcallStub` over the Kof function value. **Discovery (C3.2)**: a compiled Kof function value is NOT a native JS arrow — it is a `Lambda…` **object** with an `invoke` method, so the stub's bridge calls `fn.getMember("invoke").execute(...)` (the C3.1 pin originally exercised a raw `Value.execute` on a native arrow and was corrected to the object-`invoke` framing); primitive carriers bridge through Graal's `asInt`/`asLong`/`asFloat`/`asDouble`/`asBoolean`; a `String` arg arrives as `MemorySegment` and `executeJs*` reads it via `getString` before `Value.execute` (3.4-C3.4). The re-entrant scenario (JS → host `ProxyExecutable` → native downcall → `Linker.upcallStub` → back into `Value.invoke`) runs on the same thread; proven byte-for-byte JVM↔JS (`jvmAndJsCallbacksMatchByteForByte`: `42/42/6.0/7.5`; `stringCallbackArgsBindAndMatchJvmJs`: `5/104/2026`). A browser has no host → honest runtime degrade (R7); a non-bindable callback (e.g. `String` **return**) still fails to compile on JS (`FFI002`). **Native**: §61 (3.7).

**Slices (mirror 3.6's F1→F3 discipline).** **C1** = host-level mechanism pin
(`JvmFfiCallbackTest`: upcallStub + `.asType` closure bridge + `Arena` rooting +
`ADDRESS` spreader, against a gcc-built temp `.so`), compiler gate **closed**, zero
backend risk. **C2** = `FfiSignature` callback token + `JvmFfiRuntime.kof_ffi`
cursor parse + `Linker.upcallStub`/`.asType` bridge + `isExternBound` JVM branch
→ opens the JVM callback gate, proven by `JvmFfiCallbackE2ETest` (a real `.kf`
computes `42/42/6.0/7.5` across Int/Long/Double/mixed callback ABIs; JS callback
stays `FFI002`; a String-`return` callback stays `FFI001`). **C3** = JS callback
parity, split like F1→F3: **C3.1** = host-level re-entrancy pin (`KofJsFfiCallbackBridgeTest`
— JS→native→upcall→callback re-entrant, Int `42`/Long `42L`/loop `46`; gate still
closed) ✅; **C3.2** = add the upcall path to `KofJsFfiBridge` + marshal a callback
`Value` in the `KofJsRunner` `ProxyExecutable` + open the JS branch of `isExternBound`
for bindable callbacks ✅; **C3.3** = byte-for-byte JVM↔JS callback parity E2E + browser
honest degrade (R7) ✅; **C3.4** = `String` as a callback **argument** (the `char*`
the C side passes is read into a Kof `String` at the upcall boundary — JVM
`filterArguments`+`kof_ffi_cstr`, JS `executeJs*` `getString`; String **return** still
gated) ✅ 18/09. Status: **C1→C3.4 all landed 18/09** — the JVM and the JS host runner
bind primitive callbacks AND String-arg callbacks (byte-for-byte parity: `42/42/6.0/7.5`
scalars, `5/104/2026` String args); the C3.1 pin's arrow/`Value.execute`
framing was corrected to the real `Lambda`-object `invoke` convention in C3.2. The only
non-bindable callback shapes left are a `String`/struct/pointer **return** or nested
callback-as-return — honest `FFI001`/`FFI002`.

---

# Decisions — ✅ ALL RESOLVED 19/09 (D-POLL-19 — ver `DECISIONS.md`)

| # | Decision | Status | Blocks/unblocked |
|---|---|---|---|
| D1 | GC x86 auto-collect re-baseline sign-off (§260 G-6(a)) | 1.2.2/1.2.3, Stage 6 | `DECIDED (A) 19/09` |
| D2 | Package registry MVP — scope/hosting | 1.5.3, Stage 3+, 8.2 | `DECIDED (A) 19/09` |
| D3 | Bare-metal/bootable scheduling | 1.7 | `DECIDED (A) 19/09` |
| D4 | R5 per-namespace stability tiers (which namespaces are `stable` vs `experimental`) | R5, Stage 7 (`kof-bio` official package) | `DECIDED (A) 19/09` |
| D5 | `using` scoped-resources syntax (gated by bump) | 6.5 | `DECIDED (B) 19/09` |
| D6 | R3 struct/array ABI design (signature-level) | 3.5, 4.2, 5.4, 6.1 | `DECIDED (A) 19/09` |
| D7 | Value records / first-class value types (queue §2.7 of `roadmap.md` §23) | TIER 2.7 — planned, needs authorization to open | `DECIDED (A) 19/09 — front OPEN` |

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
