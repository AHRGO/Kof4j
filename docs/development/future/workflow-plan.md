[English](workflow-plan.md) | [Português](workflow-plan.pt_BR.md)

# `kof.workflow` — jobs, pipelines, retry, checkpoints, dead-letter (design plan · Stage 2 · TIER 2.1)

> **Status: PROPOSED (18/09) — awaiting maintainer scope decision (rule 6). Zero code.**
> This file exists because `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` row **2.1** is a bare
> `🔵` line with owner `—`, while every other greenfield front in `future/`
> (`value-records`, `scoped-resources`, `shell-plan`, …) has a concrete plan. It **proposes**
> turning that line into an executable todo; it does **not** authorize opening the front —
> that call is the maintainer's. It also does **not** implement anything: `kof.workflow`
> is not in the lexer, the parser, any backend, or the stdlib today (measured 18/09).
> Grounded in the **real, measured** primitives it would compose (§4).

## 1. Objective
One typed, composable idiom for **long-running, retryable, inspectable work**: define a
job, declare its dependencies (DAG), run it (now or on schedule), retry on failure with
backoff, checkpoint completed steps, and park terminal failures in a dead-letter queue
— **without** every app re-inventing a `while (!ok && tries<N)` loop. Today the pieces
exist but are disconnected (measured, §4): `kof.scheduler.every/at` schedules *functions*,
`kof.supervisor` restarts *workers* (pure-Kof, ratified DD-OTP-01), `kof.process` runs
*commands*, `kof.orm` persists *rows*, channels/`selectAny` coordinate *concurrency*. What is
missing is the **glue that gives all five one contract**: `workflow` is a
composition layer, **not** a new runtime primitive — it inherits behavior from the
primitives it wires, exactly the way `shell` is sugar over `process` (see
[`shell-plan.md`](shell-plan.md)).

## 2. Proposal — a Kof-level stdlib package, NOT new syntax
Measured fact: the parser has no `workflow`/`task`/`pipeline` productions, and `roadmap.md`
§"KofJS — Web" is explicit that JS is not a second-class surface (rule 5). So `kof.workflow`
is proposed as **ordinary Kof code** in the `stdlib` tree (like `kof.supervisor` already is
per DD-OTP-01 option A), **not** a new keyword or grammar form. Sketch of the idiom (shape
only — every decision below is a `Q` for the maintainer in §6, not a claim this doc makes):

```
import kof.workflow

var build = workflow.job("build") { ctx -> run("make", ["-j"]) }
var image = workflow.job("image").after(build) { ctx -> run("docker", ["build", "."]) }

var flow  = workflow.dag(build, image)
              .retry(image, times: 3, backoff: workflow.exponential(1s, 2.0))
              .checkpoint(kof.db)        // resume completed jobs after a crash
              .deadLetter(workflow.inMemory())  // terminal failures park here, don't crash the run

var report = flow.run()                 // now
var handle = flow.schedule("0 3 * * *") // cron, on top of kof.scheduler.at
```

Design invariants (inherited from existing precedent, not invented here):
- **DAG, not linear list.** Cycles are rejected **at run time** by default (same class of
  honesty as `@Deprecated`-vs-`SEM` diagnostics — an actionable message, never a silent
  skip). Compile-time cycle detection, if wanted, is a separate row (**3.7** is that
  decision for the infra graph; workflow mirrors it).
- **Jobs are Kof functions `(Ctx) -> R`.** `Ctx` carries a logger (over `kof.log`), a
  `checkpoint` store (over `kof.db`/`kof.orm`), and a `state: Map[String,Any]` for the
  next job's input. No bespoke job-IR — reuse the function type (D-FNTYPE / §155/§157 are
  the caution this stays out of).
- **Retry/backoff is a shared helper**, currently only living inside `kof.http`
  (`NativeHttpCore.java`, `JsRuntimeUiLayout.java`, `RuntimeConcurrency.java` — measured);
  workflow lifts that pattern to a **generic** helper used by `http`, `process`, and
  `workflow` verbatim (one `retry(times, backoff, when: (err) -> Bool)`; not a fork).
- **Checkpoint = `Result`-shaped record stored via the existing `kof.orm`** — same ABI
  as `shell` reusing `process.Result` (one type, no second shape). No new persistence
  backend.
- **Dead-letter = an `Iterable` view of failures** — the in-memory implementation is a
  `List[Failed]`; the durable one is a `kof.orm` table. No new queue subsystem.
- **Scheduling reuses `kof.scheduler.at(cron)`.** Workflow does **not** implement cron
  parsing. If `at` raises `CRON001` on a target (native), `flow.schedule` raises
  `CRON001` too — same honest gap, no papering over.
- **Supervision reuses `kof.supervisor` (OTP, DD-OTP-01 option A).** A workflow run is a
  supervised tree; restart policy = the supervisor's policy. `workflow` does **not**
  re-implement restart semantics.

## 3. Contract
- Pure composition over existing namespaces (`process`, `scheduler`, `supervisor`, `orm`,
  `concurrency` channels, `Result`, `retry`/`backoff`) — no new `Kof*` runtime call, no
  new backend hook, no new lexer token, no new `Target`.
- **Additive language surface only** (a new `kof.workflow` namespace); no change to the
  behavior of any existing primitive (rule 6: minimal, reversible).
- Deterministic per target: a workflow the target **cannot fully run** (e.g. one that
  needs `at(cron)` on native, or `db.checkpoint` on native) fails at **definition** time
  with that primitive's **existing** gap (`CRON001`/`PROC001`/`ORM001`), never at some
  later surprise in `run()`. If the workflow only uses `run`-only `process`, it works
  wherever `process.run` works.
- Cross-target parity (rule 5): the **same source** must produce the same `Report`
  on every target where the underlying pieces exist. Where they don't, the **same**
  honest code (`ORM001`/`CRON001`/`PROC001`) surfaces on that target.

## 4. Per-target ABI (R7 honest scope) — MEASURED at 18/09, not assumed
Everything `kof.workflow` composes already exists; `workflow` itself adds nothing new to
any backend. Read from the source files (not memory), each column cites its own file:

| primitive | source file | JVM | ANDROID | JS | NATIVE |
|-----------|-------------|:---:|:-------:|:--:|:------:|
| `process.run` → `Result{stdout,stderr,exitCode}` | `KofProcess.java`, `ExpressionProcessCallLowerer.java` | ✅ | ✅ | ✅ (`kof_platform.processRun`) | ❌ `PROC001` |
| `process.spawn` (handle) | same, `spawnCall` | ✅ | ✅ | ❌ `PROC001` (gated 18/09) | ❌ `PROC001` |
| `scheduler.every(fn, ms)` / `.cancel(h)` | `KofScheduler.java` `supportedOn` | ✅ | ✅ | ✅ | ✅ (`Rt B` cross) |
| `scheduler.at(cron5f, fn)` | same, `gapCode` | ✅ | ✅ | ✅ (parser landed 17/09) | ❌ `CRON001` (honest, R6) |
| `kof.orm.{create,save,find,all,where,page,migrate,delete,count,deleteAll,saveAll}` | `KofOrm.java`, JS `KofJsOrmBridge` | ✅ | ✅ | ✅ (JS closed 18/09) | ❌ `ORM001` |
| channels + `spawn` + `selectAny` | `KofInterpreterConcurrency.java`, JS/`native` conc (`CONC003` closed 03/09, §132 resolved 18/09) | ✅ | ✅ | ✅ | ✅ (workers = clone 220 + spinlock) |
| `Result` type (already reused by `shell-plan`) | `KofProcess.RESULT` | ✅ | ✅ | ✅ | ✅ (as type) |
| `retry(times, backoff)` | today only inside `kof.http` (`NativeHttpCore.java`, `RuntimeConcurrency.java`, `JsRuntimeUiLayout.java`) | ⚠️ not general | ⚠️ | ⚠️ | ⚠️ |
| `supervisor.one_for_one` (OTP) | `planning-otp-supervision.md` §Decision status (ratified 13/09, DD-OTP-01 option A pure-Kof) | ✅ | ✅ | ✅ | ⚠️ PARTIAL (riscv64/aarch64, 1 worker/supervisor — DD-OTP-03) |

**Consequences for `kof.workflow`:** on JVM + ANDROID + JS (with Graal host), a full
workflow (jobs + DAG + retry + checkpoint + cron-schedule + dead-letter) is available from
day one — every primitive is green there. On **Native**, `at(cron)` (`CRON001`) and
`orm.checkpoint` (`ORM001`) surface the existing honest gap; **a checkpointless
cronless `flow.run()` still works there** (jobs + DAG + retry + in-memory dead-letter, all
green on native). The plan **does not** paper over native's `PROC001`/`ORM001`/`CRON001`
gaps — it inherits them verbatim.

## 5. Step queue (the executable todo this doc exists to produce)
Owner is `—` until the maintainer assigns it; default proposed owner = **development lane**.

- **2.1.0 [recon — 0 code]** — freeze the §4 table into a test-backed note: one
  `WorkflowPrimitivesE2ETest` that, for each primitive, asserts `supportedOn` on every
  `Target` **and** runs the §209 program on JVM+JS (DAG + retry + checkpoint round-trip via
  `kof.db`, dead-letter park) + asserts the honest `CRON001`/`ORM001`/`PROC001` on Native
  via `DomainGapCodesTest`-style pins. No shipped surface.
- **2.1.1 [design sign-off — ⛔ rule 6]** — maintainer approves the shape (§2/§3):
  Kof-level stdlib (this proposal) vs compiler-builtin namespace, and the reuse-vs-fork
  calls in §6. **Gate on the whole front.** No further slices until this lands.
- **2.1.2 [MVP — pure-Kof stdlib, JVM+JS, one target parity]** — `job` + `dag` +
  `after` + `run` + `Report`; retry/backoff **extracted from** `kof.http` into a shared
  helper (JVM+JS+native byte-parity by construction, since it *is* the same code `http`
  uses today); in-memory dead-letter. Golden `WorkflowE2ETest` against the existing
  primitives.
- **2.1.3 [parity add-ons]** — `checkpoint(kof.db)` (uses `kof.orm` — honest `ORM001` on
  native), `schedule(cron)` (uses `kof.scheduler.at` — honest `CRON001` on native), and
  supervision integration by delegating to `kof.supervisor.one_for_one` when the DAG run
  is expressed as workers (rather than a plain synchronous walk).
- **2.1.4 [docs]** — idiom doc `docs/stdlib/workflow.md` (+PT), `backend-parity` row,
  flip `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.1 `🔵 → 🟡` and cascade 2.5/2.6 (which
  depend on 2.1) from `🔵` to `⏳` with a real prerequisite, and only when shipped
  promote this file out of `future/` per the folder rule.

## 6. Open questions (maintainer decisions — do NOT resolve in code)
- **Q1** — shape: a **Kof-level stdlib package** (`stdlib/workflow.kf`, like
  `kof.supervisor` per DD-OTP-01 option A) vs a compiler builtin namespace
  (`KofWorkflow.java`, like `KofProcess`/`KofOrm`). This proposal recommends stdlib-level:
  the entire surface is already typed and pure; the only reason to be a builtin would be
  to change scheduling semantics, which we explicitly reuse (`kof.scheduler.at`).
- **Q2** — how much of §2's idiom is v1: minimal (`job`/`dag`/`after`/`run`/`Report`),
  or including `retry` + `checkpoint` + `deadLetter` in the MVP, or also `schedule(cron)`?
- **Q3** — is the generic `retry(times, backoff)` a **refactor of `kof.http`'s existing
  retry** into a shared helper (this proposal's default), or an additive layer that
  `http` migrates to **later** (bigger blast radius)? Refactor touches
  `NativeHttpCore.java`, `RuntimeConcurrency.java`, `JsRuntimeUiLayout.java` — cross-lane
  (`.17 nat`, `.22 compiler`, this lane).
- **Q4** — dead-letter durability: is "in-memory `Iterable`" the only v1 shape, or must
  the durable (`kof.orm` table) implementation ship at the same time? Durable requires
  accepting an `ORM001` honest gap on Native **at the same release** (my recommendation:
  yes — one release, both faces, the gap is catalogued and honest already).

## 7. What NOT to do
- No new lexer/parser token or grammar production (this is **not** a `pipeline` keyword).
- No new runtime primitive on any backend — every action routes to an existing namespace
  (`process`/`scheduler`/`orm`/`supervisor`/`concurrency`) and inherits its gap codes.
- No fork of `retry`/`backoff` — either refactor `kof.http` to share the helper, or wait.
- No new persistence layer for checkpoints — reuse `kof.db`/`kof.orm` (`ORM001` on native
  stays honest, do **not** write a native `checkpoint` in asm to sidestep it).
- No re-implementation of restart/supervision — `kof.supervisor` (DD-OTP-01) is that
  home; `workflow` composes it, never duplicates it.
- No papering over `PROC001`/`CRON001`/`ORM001` on native — the honest gap is the
  contract.
- No code before **2.1.1** sign-off; no claiming a target until its underlying primitive is
  green there (measured in §4, re-verified by `WorkflowPrimitivesE2ETest` per 2.1.0).
