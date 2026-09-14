[English](roadmap-audit.md) | [Português](roadmap-audit.pt_BR.md)

# Roadmap Audit — Implementation Matrix (09/06/2026)

> Source: audit of real code (3 explorations with file:line evidence) +
> suite execution. Rule: REAL state, not what the roadmap says.
> Baseline: `0.3.0-beta`, suite previously declared 910 tests.
> **Re-synchronized 09/12** (doc-vs-reality, cell by cell against HEAD):
> test counts cited below = gate 09/12 regenerated from the surefire-reports
> (`docs/status.md` §Tests, `e2c03812`); cells whose state changed since 09/06
> were rewritten with proof (SG-009/WEB001/conformance/LSP/GC — see each line).
> **Touch 09/13:** P4 (structured conformance suite + SG-009 subtyping)
> marked CLOSED — it contradicted its own line 25 (SG-009 ✅ since 09/10).

## State matrix

| Item | Real state | Code | Tests | Main gap |
|---|---|---|---|---|
| 1. Standard Library | **PARTIAL (good)** | 23 namespaces in `Kof*.java` with R6 gates (`supportedOn`/`gapCode`) | E2E per area (KofCache/Db/Http/Mq/Time/…) | web JS has a REAL base (GraalJS HttpServer, `bc577aa` 09/03 — no longer a silent stub; ws/sse = **declared residual WEB001**, `kofWebStub` remains only as a fallback for unimplemented web functions); sec without cross = honest gap SECN000 (`KofSecurityTest.crossNativeReportsSecn000`); db/orm JVM-only (honest gaps); `scheduler.at` fake cron |
| 2. GC auto-collect | **PARTIAL** | real x86_64 mark-sweep (`RuntimeGc.java`); **NO safe-points/root-map**; auto-collect off (`RuntimeMemory.java:121-133`); riscv/aarch bump **without GC** (09/12: G-0 landed — 32B header-block + honest OOM guard, `356f33b9`; still no free-list/mark-sweep) | `KofGcE2ETest` 3/3 (sweep/keep/reuse) | face (1) decomposed into **G-1..G-5** (`native-multiarch.md` §decomposition 09/12): G-1 free-list riscv (no prerequisite) → G-2 header flags → G-3 mark (depends on the `kof_heap_root_end` from S-5-x86, bugfix queue) → G-4 sweep → G-5 aarch inherits |
| 3. Package Manager | **MVP** | `Deps.java` (flat Maven Central, cache `~/.kof/deps`) | `DepsTest` 4/4 | POM/transitives, lockfile, ranges, publish |
| 4. Async | **PARTIAL** | JVM vthreads + Handle/await/timeout; real JS Promise (CONC003 ✅); Native pthread | `KofConcurrency2Test` 33 (gate 09/12) | timeout/cancel/selectAny closed on the 3 targets (`status.md` §Concurrency); remaining gap: select over channels |
| 5. Concurrency G8 | **PARTIAL (good)** | spawn/await/cancel/selectAny/awaitTimeout/channel/scheduler 3 targets | idem | `scheduler.at` cron = fixed 60s (declared MVP); cancel by TID%256 |
| 6. KofAndroid | **DONE (with caveat)** | `Target.ANDROID`; `--apk` pipeline (d8/aapt2/apksigner); `AndroidProjectWriter` (Maven) | pipeline depends on ANDROID_HOME | lifecycle/ART runtime covered in Phase 2; consolidate docs |
| 7. Debugger | **MVP** | DAP stdio JVM (`KofDebug.java`), real JDWP breakpoints; DWARF line-only | docs/debugging/debug-adapter.md | **locals = placeholder** (`"line N"`, `KofDebug.java:197` — verified in HEAD 09/12); stepping/evaluate; VS Code ext |
| 8. KofJS | **PARTIAL (alpha → functional)** | ESM + V3 source maps + GraalJS + DOM/UI runtimes (9 files); real web base (GraalJS HttpServer, `bc577aa`) | `KofJsE2ETest` 40 (gate 09/12) | ws/sse **declared residual WEB001** (honest gap in `backend-parity.md` — no longer a silent stub); serve×JS indirect |
| 9. LSP | **PARTIAL (good)** | real diagnostics via CompilerDriver (single source); textual hover/completion/references/rename + **go-to-definition ✅** (`definitionProvider`, `LspServer.java:323`) | `LspServerTest` 4/4 | hover/completion/rename must use SymbolTable (currently textual) |
| 10. KofScript | **PARTIAL (good)** | shared IR interpreter (same semantics by construction) | `KofScriptTest` 31 (gate 09/12) + parity gate | globals via fragile multiline regex; REPL re-evaluates everything |
| 11. Language Spec | **PARTIAL (good)** | `docs/language-reference/` 16 files + specification-status | — | **SG queue COMPLETE** (23 entries SG-001–020 + E1–E3 all resolved 09/06–09/12, most by maintainer's decision; SG-009 subtyping ✅ SEM021 `StatementAnalyzer.java:154` — the "20 open gaps" cell of the 09/06 audit rotted); non-normative grammar |
| 12. Conformance Suite | **PARTIAL** | `conformance-matrix.md` (09/07) — Feature×4 targets, cells locked by `ConformanceMatrixTest` (11) + `ConformanceMatrixDocTest`; BackendParityTest 16 cases JVM×JS×Nat | ConformanceMatrixTest · BackendParityTest | following batches by category (concurrency/time stay in the E2E suites) |
| 13. Full Web Platform | **NOT STARTED** | partial routing in kof.ui; validation exists | — | declarative/forms/SSR — depends on 8+9 |
| gRPC | **NOT STARTED** | — | — | planned; do not start before P0-P2 |
| Auto-hosting | **NOT STARTED** | — | — | documented as a gap |

## Critical semantic bugs (P0) — SILENT UNKNOWN FALLBACKS

The audit found **12 silent fallbacks** that accept semantically
invalid programs (the compiler infers UNKNOWN and proceeds, instead of
diagnosing). The 4 biggest (all in `SemExpressionTyper`/`MemberCallTyper`):

1. **#7 — biggest**: nonexistent method in a builtin namespace (`db.*`, `log.*`,
   `http.*`, `mq.*`, `time.*`, `security.*`, …) → UNKNOWN without SEM025. Only
   `process`/List/Map/Set were fixed (7ec8b9d, bugs 31/34).
2. **#3 —** `obj.campoInexistente()` → UNKNOWN without diagnostic
   (SemExpressionTyper.java:312).
3. **#6 —** `super.metodoInexistente()` → UNKNOWN (MemberCallTyper.java:92)
   while the normal class path emits SEM025.
4. **#8 —** UNKNOWN receiver + nonexistent method → no diagnostic
   (SEM025 only when `isKnownReceiver`, MemberCallTyper.java:384-386).

Plan rule: **inference never creates an implicit declaration; a nonexistent
identifier must fail.** These cases are the P0 priority.

> **STATUS 09/07 (verified in code + tests, not in this table):** #7
> (builtin namespaces → SEM025), #3 (nonexistent field in a known class)
> and #6 (super.metodoInexistente) are **FIXED** — helper
> `unknownNamespaceMethod` (MemberCallTyper) + gate `isKnownReceiver`
> (SemExpressionTyper); proof `SemanticResolutionTest` (6/6 green: matrix
> of 12 namespaces + web.app + super + field + false-positive). #8
> (UNKNOWN receiver + nonexistent method) is **legitimate error-recovery** —
> without the receiver's type there is no way to diagnose without a false-positive;
> keep UNKNOWN. P0 of semantic fallbacks: **CLOSED**.

## Execution order (adjusted by the audit)

- **P0**: ~~semantic fallbacks~~ **CLOSED 09/07** (status in the block above).
- **P1**: GC auto-collect — cross face decomposed into **G-1..G-5** (09/12,
  `native-multiarch.md` §decomposition; G-0 ✅ `356f33b9`, G-1 free-list riscv is
  the next step without a prerequisite; G-3 depends on the `kof_heap_root_end` from
  S-5-x86, bugfix queue).
- **P2**: PM lockfile+transitives; debugger locals via JDWP VariableTable;
  LSP hover/references via SymbolTable (currently textual; go-to-definition ✅).
- **P3**: real cron (`scheduler.at`); KofScript globals via frontend.
- **P4**: ~~structured conformance suite; spec §subtyping (SG-009)~~ **CLOSED**
  (conformance matrix Feature×4 targets + `ConformanceMatrixTest`/`ConformanceMatrixDocTest`
  as CI gate — Phase 9; SG-009 nominal subtyping ✅ SEM021 09/10 — see line 25).
- **P5**: web platform, gRPC, auto-hosting (do not start before).

## Raw evidence

See the 09/06 audit report (3 explorations): stdlib (23 areas +
parity per target), tooling (CLI 20 commands, PM, debugger, KofJS, LSP,
KofScript), semantics (12 fallbacks, SEM025 coverage, pipeline).
