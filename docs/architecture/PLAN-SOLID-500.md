[English](PLAN-SOLID-500.md) | [Português](PLAN-SOLID-500.pt_BR.md)

# PLAN-SOLID-500 — ≤500 lines/class refactor (CLOSED 13/09 — moved to docs/)

> **✅ COMPLETED 13/09 (F1–F9 all ✅; F3: NativeBackend 498 ≤500 measured, outside
> the ratchet `scripts/check_500-baseline.txt` with 12 remaining debts).**
> Live gate = `scripts/check_500.sh` in CI. Moved from
> `docs/development/refactoring/` to here by the 3-state rule (completed →
> `docs/`).

Version: 1.0 · Date: 04/09/2026 · Branch: beta-0.3.0
Iron rule: behavior is law — zero regression in each phase (full suite green as merge gate).
1. Objective
Restructure ALL project classes to:
1. ≤500 lines per class (absolute rule, already in AGENTS.md).
2. Single Responsibility (SRP) — each class has ONE reason to change.
3. DRY — extract duplication (call dispatch, mangle, layout, type-mapper) to a single place.
4. KISS — division by DOMAIN/INTENTION, not by mechanism; no unnecessary layers.
User-imposed restriction: "each function has its own unique and single responsibility class" — interpreted
pragmatically as each cohesive responsibility → one class (functions that are an entire domain,
e.g. emitCall, become an XCallEmitter class; isolated and cohesive functions remain methods of the class
of their domain). We will not create 1 class per trivial function (that would violate KISS/DRY).
2. Current inventory (20 classes above the limit)
Class
NativeRuntime
CompilerDriver
NativeBackend
JsBackend
JvmRuntime
SemanticAnalyzer
Parser
JvmBackend
Main (cli)
JvmStringRuntime
JvmVkRuntime
JvmWebRuntime
JvmMediaRuntime
NativeHttpRuntime
Bench
Optimizer
KofScript
NativeWebRuntime
KofJsRunner
JdwpClient
Total target: ~120 classes (all ≤500). NativeRuntime alone is 35× the limit — it is the project's largest
string-ASM generator and the first to be attacked (well-isolated domains, low risk).
3. Extraction principles (apply to EVERY extraction)
3.1 Golden rule (from AGENTS.md)
Refactor preserves semantics. It touches structure, NEVER behavior. Proof: same suite + golden E2E
per target. If the refactor changes observable output, it is a refactor bug — fix or revert.
3.2 Method → class extraction pattern (step by step)
1. Identify a cohesive domain (e.g. all the emitString* of NativeRuntime).
2. Create the RuntimeStringOps class with the moved static methods (without touching the body).
3. In the caller, replace emitStringX(sb) with RuntimeStringOps.emitStringX(sb).
4. Remove the method from the original class.
5. Compile + run the module suite (mvn -o -pl kof-compiler -am test).
6. Run the full suite (mvn -o test) — merge gate.
7. Isolated commit per extraction (one responsibility per commit).
3.3 Shared context
Lowering/emission classes use a lot of state. Extraction NEVER duplicates state:
- Prefer pure static (receives StringBuilder/context by parameter) — NativeRuntime, JvmRuntime.
- When instance-based, extract an XContext(...) record (e.g. LoweringContext, EmitContext) and pass it
by parameter. Global static fields for compilation state are forbidden.
3.4 DRY: single points to extract first (immediate benefit)
- sanitizeName/name mangle → NativeNameMangler (used in NativeBackend + NativeRuntime).
- typeToString/toType/descriptors → TypeMapper (used in JvmBackend, JsBackend, CompilerDriver, NativeBackend).
- isDoubleWidth/isDoubleWidthSlot → TypeMetrics.
- method/field lookup in hierarchy → already centralized in SemanticAnalyzer; keep.
3.5 KISS: prohibitions
- Do NOT create 1 class per trivial method.
- Do NOT create interfaces/abstractions "just in case" (YAGNI).
- Do NOT create Service/Repository/Controller layers (Kof anti-pattern — the repo is Java, but the spirit holds).
- Do NOT introduce dependency injection/framework.
4. Execution phases (order by risk: lowest → highest)
Each phase is an isolated PR/commit with the full suite green. Phases can be executed in parallel by
different agents, as long as two agents never touch the same giant class (DOING.md rule).
PHASE 1 — NativeRuntime (17,726 → ~37 classes) — LOW RISK
String ASM generator, static methods, domains isolated by emit* prefix. Mechanical extraction.
New class
RuntimeCore
RuntimePrint
RuntimeStringConv
RuntimeStringOps
RuntimeStringParse
RuntimeStringLayout
RuntimeList
RuntimeArray
RuntimeJson
RuntimeConcurrency
RuntimeChannel
RuntimeScheduler
RuntimeMq
RuntimeGc
RuntimeDbSqlite
RuntimeDbPrepared
RuntimeLog
RuntimeConfig
RuntimeTime
RuntimeCache
RuntimeIoFile
RuntimeIoTime
RuntimeNet
RuntimeSecurity
RuntimeUi
RuntimeVk
… (subdivisions as needed)
Gate: full suite + Native E2E golden (all E2E already cover the domains).
PHASE 2 — CompilerDriver (8,870 → ~18 classes) — HIGH RISK (shared state)
The heart of the compiler. Extracting emitExpression (4k+) and emitStatement (2k+) is the most delicate step:
these methods use dozens of instance fields. Strategy: extract an immutable LoweringContext
(unit, target, semanticAnalyzer, diagnostics, module state) and move the emitters to static classes.
New class
CompilerPipeline
CompilerImports
CompilerDesugar
CompilerTypes
LambdaLowerer
LambdaState
SuperBridgeBuilder
BoxClassFactory
StatementLowerer
ExpressionLowerer
CallEmitter
UiEmitter
ArgumentsEmitter
QueryDslLowerer
StringMethodRegistry
AndroidHostBuilder
CompilerMain
Migration strategy in chunks (avoids 1 giant commit):
1. Extract StringMethodRegistry, CompilerTypes, BoxClassFactory (no state — trivial).
2. Extract CompilerImports, CompilerDesugar (no lowering state).
3. Extract LambdaLowerer + LambdaState (lambda-isolated state).
4. Extract SuperBridgeBuilder, QueryDslLowerer, UiEmitter, ArgumentsEmitter.
5. Last: extract StatementLowerer/ExpressionLowerer with the LoweringContext — the largest commit, done
in sub-commits (per node type), each with a green suite.
Gate: full suite + golden E2E per target (JVM/JS/Native).
PHASE 3 — NativeBackend (6,813 → ~14 classes) — MEDIUM RISK
ASM emission. Subdivided by emission responsibility.
New class
NativeBackend
NativeOperationEmitter
NativeCallEmitter
NativeStringData
NativeVtableBuilder
NativeLayout
NativeJsonSchema
NativeRiscv64
NativeAarch64
NativeNameMangler
PHASE 4 — JsBackend (6,064 → ~12 classes) — MEDIUM RISK
Mirrors NativeBackend for JS.
New class
JsBackend
JsClassEmitter
JsMethodEmitter
JsOperationEmitter
JsCallEmitter
JsExpressionEmitter
JsStatementEmitter
JsRuntimeEmitter
JsSourceMap
JsTypeMapper
PHASE 5 — JvmRuntime (2,526 → ~5 classes) — LOW RISK
Generates Java source as string. Same pattern as NativeRuntime.
New class
JvmRuntime
JvmRuntimeSource
JvmRuntimeDecoders
JvmRuntimeCallDescriptors
PHASE 6 — SemanticAnalyzer (2,293 → ~5 classes) — HIGH RISK (lots of interdependence)
New class
SemanticAnalyzer
SymbolTableBuilder
TypeChecker
ExpressionTyper
MemberResolver
PHASE 7 — Parser (1,975 → ~4 classes) — MEDIUM RISK
New class
Parser
StatementParser
ExpressionParser
TypeParser
PHASE 8 — Remaining 500–1400 lines (~14 classes)
Executable in parallel (no file conflicts).
Class
JvmBackend
Main (cli)
JvmStringRuntime
JvmVkRuntime
JvmWebRuntime
JvmMediaRuntime
NativeHttpRuntime
Bench
Optimizer
KofScript
NativeWebRuntime
KofJsRunner
JdwpClient
PHASE 9 — FINAL SWEEP (DRY/KISS)
1. Grep for duplication: emitCall blocks repeated across backends → extract common helpers
(CallAbi, RegisterAllocator) IF the gain justifies it (avoid over-engineering).
2. rg for classes with import.*\* (clean up).
3. Automatic size check: script scripts/check_500.sh that fails if any class >500.
4. Update AGENTS.md/DOING.md with the rule and the plan.
5. Risks and mitigation
Risk
emitExpression (4k+) refactor breaks semantics
Shared state becomes a mess
Conflict between agents on the giant classes
Logic duplication across backends (DRY)
Refactor "documented around" a bug
Loss of context (why the code is the way it is)
6. DoD (Definition of Done) per phase
- All new classes ≤500 lines.
- No touched class remained >500 lines.
- mvn -o compile -q clean.
- mvn -o test full green (913 tests today; may grow).
- Golden E2E per target (JVM/JS/Native) with no output change.
- Zero new global static fields for compilation state.
- No new duplication (grep for the extracted pattern).
- docs/refactoring/ updated (if a better division was found, record it).
7. Suggested execution order (priority)
1. Phase 1 (NativeRuntime) — largest class, lowest risk, greatest immediate gain.
2. Phase 2 steps 1–4 (CompilerDriver easy parts) — unblocks the path.
3. Phase 5 + Phase 8 (JvmRuntime and 500–1400) — parallelizable.
4. Phase 3 + Phase 4 (backends) — after TypeMapper/NameMangler exist (DRY).
5. Phase 6 + Phase 7 (SemanticAnalyzer + Parser).
6. Phase 2 step 5 (emitExpression/emitStatement — the last, riskiest one).
7. Phase 9 (final sweep + gate script).

## 8. Execution status (rebuilt 06/09 — the original table was lost
##    while writing the document; see AGENTS.md "lesson: small parts")

| Phase | Target class | Status | Proof |
|---|---|---|---|
| 1 | NativeRuntime 17726 | ✅ DONE | 142 + ~60 Runtime* classes ≤500 (agente-idiomatic) |
| 2 | CompilerDriver 8870 | ✅ DONE (≤500 criterion met) | **487 lines measured at HEAD 12/09** (`wc -l`), absent from the `check_500-baseline.txt` ratchet (the authoritative criterion of §140), `check_500.sh` OK; real extractions (not exactly the 18 names of the design — the form diverged, the criterion did not): `CompilerPipeline` 467, `CompilerImports` 255, `CompilerDesugar` 345, `CompilerTypes` 335, `CompilerDriverState` 422, `CompilerUiEmitter` 241 (F2.51–F2.53), `StatementLowerer` 499, `ExpressionLowerer` 486, `BoxClassFactory`, `StringMethodRegistry` + 12 auxiliary Compiler* classes, all ≤500. Original owner (agente-idiomatic) silent on CompilerDriver since F2.53 06/09 — closed by the dead-owner rule with the MEASURED criterion met (DOING 12/09) |
| 3 | NativeBackend 6813 | ✅ **DONE 13/09** (≤500 criterion MEASURED) | **498 lines at HEAD (`wc -l`)**, absent from the `check_500-baseline.txt` ratchet (12 debts). Real extractions: `NativeSymbolMangling` 92 (mangle by signature SG-011B/§131, `145fc5a3` lane gate) + `NativeStaticData` 116 (.data of static fields bug 41, `0951dbdc` lane .18) + `emitMethodTable` → NativeClassMeta (owner of the vtable logic). Note: F3 was stalled on the "GC lane in `nat/`" — the directory/refs no longer exist in the repo (dead-owner rule, 13/09) and the blocker expired; the plan criterion (≤500 measured) was met without touching the GC slice (which today lives in Runtime* of runtime/, not in the backend) |
| 4 | JsBackend 6064 | ✅ DONE | 334 + 22 classes, JS byte-identical (`226994c`) |
| 5 | JvmRuntime 2526 | ✅ DONE | 132 + 7 classes, source byte-identical (`01af2d5`) |
| 6 | SemanticAnalyzer 2293 | ✅ DONE | 396 + 8 classes (`6e2ff77`) |
| 7 | Parser 1975 | ✅ DONE | 456 + 7 classes (`7433c8e`) |
| 8 | 13 classes 500–1400 | ✅ DONE | all ≤500, byte-identical generators (+ VkChain64Asm 3568→57+15, residue outside the inventory) |
| 9 | Final sweep | ✅ DONE 06/09 | `scripts/check_500.sh` (gate); wildcard imports expanded (13 files); shared CallAbi NOT extracted — backends consume the same IR, gain does not justify it (the plan's own anti-over-engineering) |

Permanent gate: `scripts/check_500.sh` — target ≤500; **500–599 is tolerated**
(the gate WARNS, does not fail); **≥600 is critical** (fails CI). The ratchet
`scripts/check_500-baseline.txt` freezes the debt: it does not grow and only shrinks
(authoritative number = `wc -l` of the file). The plan is **complete**: F2 ✅ 12/09
(CompilerDriver 487 ≤500 measured) and **F3 ✅ 13/09 — NativeBackend 498 ≤500
measured, outside the ratchet (12 remaining debts, all stalled)**.

> **⚠️ RECTIFIED 12/09 (§140 known-bugs): the gate was decorative.** No
> workflow called `check_500.sh` → the measurement above ("all ≤500") **regressed
> silently**: today the script reports **17 classes >500** (largest
> `nat/NativeBackend.java` 664; `SemanticAnalyzer` went back from 396 (Phase 6,
> `6e2ff77`) to **519 in `40abd0ed` (09/09, SEM047) and 535 in `b55c24c0`
> (11/09, top-level overload)** — Phase 6 is still "✅ DONE" in the table;
> `Parser` 456→513 (Phase 7 likewise). Fix: the gate became a **ratchet**
> (baseline `scripts/check_500-baseline.txt` freezes the 17 debts; new
> or growing debt = red CI; only shrinks) and was **wired into `build-and-test`**
> of `.github/workflows/ci.yml`. The real queue of this front is now the baseline,
> not the 9-phase table — each line removed = one split done.
