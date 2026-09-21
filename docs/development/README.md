[English](README.md) | [Português](README.pt_BR.md)

# Development — living backlog (only work in development)

> **Base:** `0.5.0-beta` · branch `beta-0.5.0` · **updated:** 21/09/2026
> **Suite measured at this HEAD:** `3225` run (2762 kof-compiler + 50 kof-script
> + 7 kof-c-compiler + 406 kof-cli), **0 failures / 0 errors**, 221 skip (cross
> runs in the dedicated qemu job; the rest external-DB/toolchain guards + §255
> sysroot) — CI Build+Tests job of tip `404d8be6` on 20/09 ~18:14: the **first
> green on `beta-0.5.0`**, reactor `Kof 0.5.0-beta`. The §252 flake, the §181
> cross residual and §256(b) stay closed at code (`20495e48` / `c56c74a7` /
> `3a593734`). **Authoritative suite number = the CI job on the pushed SHA**
> (the gate `mvn test ... -Dmaven.test.failure.ignore=true`; check per module
> with `grep -rl FAILURE */target/surefire-reports/*.txt`), not this line — it
> rots with every commit. Refold of the `NativeRiscvAsm` concatenation to
> `<clinit>` (new anti-pattern `constant-folded-runtime-asm.md`) green in the
> `gate1585.log` gate (HEAD 54da1325).
> **3-state rule (`AGENTS.md`):** `docs/` = implemented/decided ·
> `development/` = **pending technical work** · `development/future/` =
> **plan only, zero code**. Concluded → move to a `docs/` submodule in the same
> commit; started → falls in here. The 12/09 sweep (`655afa6b`) moved 13 docs
> from `future/` to here (all with code) and 4 concluded to `docs/`.
> **Clarity refactor 13/09 (maintainer):** bugs/gaps/matrices →
> `docs/bugs-and-gaps/` (lines 2, 41, §2, §3, §4.2, §5); plans **halted by
> decision** were **ratified 13/09 and consolidated into `DECISIONS.md`** (the
> `decision-pending/` folder was extinguished — see §3). This README lists what
> **moves**; a taken decision lives in `DECISIONS.md` (rule 6: a front without a line
> there is not attacked).

**Sources of truth that are NOT here (they are not backlog):** `docs/status.md`
(what works + the suite gate), `docs/backend-parity.md` (parity
matrix with honest gaps), `docs/bugs-and-gaps/specification-gaps.md`
(SG-001–023 — maintainer queue COMPLETE, became a reference; SG-021/022 =
requests with no decision; **SG-023 ✅ DECIDED 21/09 — `D-PROPERTY`, no new
surface**).

---

## 0. What is live here (read first)

- **Pending (the release gate's condition 3):** `ffi-abi-structs.md`
  (**D6 DECIDED 20/09** — implementation in progress, owner `jonas`) ·
  `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (owner: session 9093, platform front) ·
  `makealive-plan.md` (**residual 3.6 only** — 3.2 **LANDED 21/09 `966c86a4`**
  via `D-MAKEALIVE-SYNTAX` (pure sugar over `design()`); 3.7 **CLOSED 21/09**
  as runtime-only; 3.8 shipped 20/09 `D-MAKEALIVE-CLI`; 3.6 = secrets via
  `kof.security`, Stage 5/security lane, `secrets-plan.md` — **promoted 21/09**,
  face 1 authorized) ·
  `db-parity-plan.md` (+PT — **`D-DB-GAPS` addendum 21/09**: total DB parity,
  every target accepts mariadb/mysql/sqlite/mongodb; S0 clears §421's silent
  accept; **owner docs/plataforma lane**, S0/S1 authorized 21/09) ·
  `type-system-extensions-plan.md` (+PT — **X5/X6 APPROVED 21/09**
  (`D-TYPE-VARIANCE`/`D-INTEROP-REFLECT`), promoted from `future/`; incremental
  slices with proof, each its own proof) ·
  `codegen-step-2.2.3-assessment.md` (+PT — **DECIDED 21/09 option B
  (`D-DESUGAR-STEP`)**: build the AST-phase `DesugarStep` registry
  (behavior-free); no code in this doc).
  Authority: `scripts/check_release_050_gate.sh` (`loose_docs`).
- **Living records here (not backlog):** `DECISIONS.md`,
  `PROPOSAL-1.0-EXIT-GATE.md`, `roadmap.md`, `release-beta-0.5.0-prep.md`.
- **§1 is the queue; §4.1/§4.2 are an AUDIT TRAIL** (what already left, with
  proof) — do not read them as work. How to act: §6.

---

## 1. Plan execution order (official queue of the development lane)

> Criterion: (1) front designated by the maintainer > (2) gate health >
> (3) pure-code work with no decision > (4) blocked items = DO NOT attack
> (rule 6). Living-record items (matrices/audits) have no "end" —
> they update with every closed gap, they do not pull priority.

| # | Plan | State | Why in this position | Concrete next step |
|---|---|---|---|---|
| 1 | `stdlib/PLAN-TREE-SHAKING.md` (#97) | ✅ **CONCLUDED 13/09** — S-1..S-6.1 ✅ (S-6.1 merged `0104f6d6` PR #106) + S-7 ✅ (consolidated into `docs/stdlib/stdlib-loading.md`, moved to `docs/stdlib/`) | front designated 11/09, closed; S-5-x86 remains in the bugfix queue (`root_end`, outside this plan) |
| 2 | ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **DONE 13/09 — F3 closed** (NativeBackend **498** ≤500 measured: `NativeSymbolMangling` 92 + `NativeStaticData` 116 + `emitMethodTable`→NativeClassMeta; the "GC lane in `nat/`" blocker expired — the refs no longer exist in the repo, dead-owner rule) — **PLAN CLOSED and MOVED 13/09** (F1–F9 all ✅; 3-state rule) | the ≤500 gate became a **ratchet locked in CI** (2652aa45, §140): debt does not grow and only shrinks; the authoritative count is `wc -l scripts/check_500-baseline.txt` (update by POINTING to the file, not by writing a number that rots with every split) | — (doc in `docs/architecture/`; if a new >500 residue appears, reopen as its own item) |
| 3 | ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ **CONCLUDED + PROMOTED 19/09** — §5 step-8: faces (1)–(5) all closed (GC G-0..G-6(a); DB001+CONC001 cross; FLT001; §107 record/nested on ALL 3 arches; per-arch parity columns; cross CI) — NATIVE002 CLOSED; remaining per-domain refusals (SECN000/OTP001/JSN004/RNG001/UI) are honest gap codes in `known-bugs.md` + `backend-parity.md`, not pending work of this doc | moved to `docs/` (3-state rule) | — |
| 4 | ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` (#83) | ✅ **CONCLUDED 19/09** — 1st slice ✅ 11/09 (core+`restartLimit`+`stop`) + **S2-JVM ✅ 13/09** + **S2-Native x86 ✅ 15/09** (§129 closed — TLS per-thread chain) + **S2-JS ✅ 18/09** (§132 closed; `OTP002` lifted) + **riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table `kof_exc_slots`; `OTP001` gate removed; `crossGateOtp001` runs the APP on both arches) | moved to `docs/` (3-state rule) | — (DD-OTP RATIFIED 13/09) |
| 5 | ~~`plan-editor-integration.md`~~ → `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | ✅ **CONCLUDED 14/09** — degrees 0–13 implemented and proven (`EditorIntegrationTest` 23/23; `kof editor` complete across 7 editors; release gate §19 green) | moved to `docs/tooling/` (3-state rule) | — |
| 6 | ~~`plan-stdlib-expansion.md`~~ → `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ **CONCLUDED 14/09** — S0–S13 implemented and validated on 5 targets; pending decisions consolidated in `DECISIONS.md` §D-STDLIB | moved to `docs/stdlib/` (3-state rule) | — |
| 7 | newly opened queue of `DECISIONS.md` (13/09): ~~`time.todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds` (D-STDLIB)~~ **✅ EXECUTED 13/09** (S7e-S7h, stdtime3-6 matrix, suite 1772/0/0; TIME003 = general queue) · ~~`CmdNew` (D-APP I1)~~ **✅ DONE 14/09** (`kof new --type mono\|backend\|frontend\|full-stack`, compilable skeletons, honest APP003, `CmdNewTest` 8/8, APP matrix in `backend-parity.md`) · ~~`chacha20Encrypt/Decrypt` (D-SEC)~~ **✅ DONE 14/09** · ~~`security.cookies`~~ **✅ DONE 14/09** · ~~`app.security()` (C18)~~ **✅ DONE 14/09** (composite middleware, fixed order, JVM; `KofWebE2ETest` 22/22 + `appSecurityPipelineE2E`; Native/JS `WEB006`; unified superset .18×.22) · ~~`--fat` (D-APP I3)~~ **✅ DONE 14/09** (`kof build --fat` → `kof-app.jar` executable with classes+runtime+deps; `CmdBuildFatTest` 4/4, `java -jar` proof; non-JVM honest refusal R6) · ~~blog E2E (D-SPRING F12)~~ **✅ DONE 14/09** (`KofBlogE2ETest` green; exposed+fixed 2 JVM bugs: `readRequest` counted body in chars vs `Content-Length` in bytes — hung a multibyte UTF-8 connection; raw JDBC CLOB on the read path) | `RATIFIED` (decision locked 13/09) | — | ~~TLS own cert (D-SEC)~~ **✅ DONE 14/09** (`app.listenSecure(port, certPem, keyPem)`, PKCS#8 PEM, JVM; `KofWebTlsTest` 7/7; Native/JS `WEB002`) · ~~OAuth resource-server (D-SEC layer 16)~~ **✅ DONE 14/09** (`auth.resourceServer(jwksUrl,issuer,aud)` + `resourceServerVerify`; RS/ES via JWKS, no alg confusion; integrates with `auth.authenticated`/`app.security`; `KofOAuthResourceServerTest` 4/4; Native/JS `SECN007`) — **§7 QUEUE EMPTY**; each line = unit-test-commit |
| 8 | `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (+ vision companion `docs/architecture/UNIVERSAL-PLATFORM-VISION.md`) | `IN PROGRESS` — **promoted from `future/` 17/09** (`DECISIONS.md` §D-UNIVERSAL, R12 overridden); split 17/09 into executable steps + vision companion | maintainer directive 17/09: promote and implement | **Stages 1–8 + R1–R12 as executable items** (status ✅/🟡/🔵/⛔ + owner lane + proof) — live state: **R1 ✅ DONE** (`5f1422c6` boundary gate+ledger+CI); **R6 ✅ machine gate** (`DomainGapCodesTest…` `19a740f2` + ledger sweep `c5897cd5`); **R5 ✅ machine gate 21/09** (tier in `scripts/stdlib_boundary.txt` + `check_stdlib_boundary.sh`, D4-A); **X8 ✅ 21/09** (property idiom `test`+`rng`+`assert`, `D-PROPERTY`); **1.5 ✅ OTel export landed** (`435b7013`; Native `OBS003`); 1.1 MEDIA = `MEDIA001/003` documented, queued behind the `.22` HTTP facades; 1.2 GC x86 = ✅ G-6(a) auto-collect landed 19/09 (`a904317e`, §260 CLOSED, D1-A); 1.4 registry = **✅ MVP 19/09** (D2-A: publish + pull 1.5.3-S2). Claim in `DOING.md` before code |
| 9 | ~~`workflow-plan.md`~~ + ~~`shell-plan.md`~~ (+PT) → `docs/workflow-plan.md` / `docs/shell-plan.md` | ✅ **CONCLUDED 19/09** — workflow: all five faces landed (`WorkflowE2ETest` 20/20, byte-parity JVM==JS, Native real); shell: 2.2.0–2.2.4 landed (`ShellE2ETest` 15/15; only residual = JS live-pipe `pipeline`, a platform item on tracker row 2.2, not a plan slice) | moved to `docs/` (3-state rule — a concluded plan may not stay in `development/`) | — |
| 10 | `D-WORKFLOW-RUN` (Stage 2 rows 2.5/2.6) — `kof workflow run` full runner + CI/CD pipeline example | ✅ **LANDED 19/09** (owner platform lane, sessão 19/09-3/9093): convention `pipeline(): KofWfDag`; `list`/`run --job`/`--dry-run`/`--json`; host `order()`/`runJob()` + `CmdWorkflow`; `examples/ci/ci-pipeline.kf` E2E golden (`CmdWorkflowTest` 9/9) | decision locked in `DECISIONS.md` §D-WORKFLOW-RUN; implemented directly (tooling slices, X9 `kof deploy` precedent) | residual: JS/Native runner faces are honest follow-up slices (R7) |
| 11 | `makealive-plan.md` (+PT) — D-MAKEALIVE (ratified 20/09, `DECISIONS.md`): infrastructure as typed code — **MK-1 core COMPLETE 20/09** (3.1: virtual namespace `kof.makealive` + generic REST/CLI providers + `kof.db` state; makealive E2E battery green, 8 classes, JVM==JS byte parity — plan §3.1) + **3.3 reconcile landed** (delegates to `scheduler.every`; `MakealiveReconcileE2ETest` 1/1 ×3) | `IN DEVELOPMENT` | residual **3.6 only** (secrets via `kof.security` — Stage 5, security lane; promoted 21/09 to `secrets-plan.md` (`D-SECRETS`); face 1 (`Secret`) authorized) / 3.2 **LANDED 21/09 `966c86a4`** (`D-MAKEALIVE-SYNTAX`: pure sugar over `design()`, `infra` stays an identifier; proof `InfraSyntaxE2ETest`) / 3.7 **CLOSED 21/09** as runtime-only (pure sugar has no compile-time graph — the 3.1 runtime refusal names the cycle) / 3.8 (`kof makealive` is the only verb, `kof infra` NOT added — reaffirmed 21/09) |
| — | `ffi-abi-structs.md` (+PT) — FFI struct/array ABI (D6) | `IN DEVELOPMENT` — **D6 DECIDED 20/09** (`DECISIONS.md` §D-FFI-STRUCT: **D6-1 = B** (`D-FFI-STRUCT-B` approved spec-first 21/09: mutable `struct` by-ref; records stay by-value read-only, `Buffer(U8)` covers out-buffer), D6-2 only `new T[n]`, D6-3 `Buffer(U8,INOUT)`, D6-4 full sret, D6-5 confined arena); spec §4 = DECIDED; 3.8a `AbiLayout` landed; **3.8b fatia 1 landed 20/09** (`e79ea4e6` — record by value as C struct **argument** on the JVM, D6-1) + **fatia 2 landed 21/09** (record **return** by value: register + sret, canonical-constructor reconstruction; D6-4) + **D6-5 arena fix landed 21/09** (scalar helpers confined-per-call + close; `FfiStructE2ETest` 10/10, `FfiE2ETest` 17/17) + **fatia 3 landed 21/09** (D6-2: scalar `T[]`→C `ptr`, **copy-in per call**; `FfiArrayE2ETest` 5/5) + **D6-3 out-buffer landed 21/09** (B1 `buffer.alloc`+`Buffer.bytes()` `BufferE2ETest` 4/4; B2 `Buffer(U8)` `extern` INOUT copy-in/call/copy-back `BufferFfiE2ETest` 4/4) | exec = owner **`jonas`** (post-#431 FFI front) + compiler/native + JS decision; **the docs lane only keeps the record** | remaining: JS struct bridge (cross-lane) and Native sret (3.7); the §1 `Arena.global` leak is **FIXED** (D6-5, no longer a fix-candidate) |
| — | `db-parity-plan.md` (+PT) — `D-DB-GAPS` addendum 21/09 | `IN DEVELOPMENT` — maintainer 21/09: **total DB parity** (every target accepts mariadb/mysql/sqlite/mongodb); measured matrix + slices S0–S4 | **owner: docs/plataforma lane** (`D-DB-PARITY-OWNER` 21/09; S0/S1 authorized); the docs lane keeps the record | S0 = interim honest diagnostic that clears §421; S1 `mariadb://` = mysql-wire alias (Native); S2 JDBC scheme parity JVM/JS/Android; S3 `mongodb://` interop-first (R9); S4 oracle |
| — | `codegen-step-2.2.3-assessment.md` (+PT) — roadmap 2.2.3 | `DECIDED 21/09 option B` (`D-DESUGAR-STEP`) — build the AST-phase `DesugarStep` registry (behavior-free) | measured 21/09: **phase mismatch** (hook = optimized IR; DDL = lowering; runner = AST desugar) | slices open: migrate the four desugars into registered `DesugarStep`s (behavior-free, freeze rule 3); the doc moves to `docs/architecture/` when concluded |
| — | `type-system-extensions-plan.md` (+PT) — X5 variance+sealed / X6 interop reflection | `OPEN — implementing` — **X5/X6 APPROVED 21/09** (`D-TYPE-VARIANCE`/`D-INTEROP-REFLECT`), promoted from `future/`; direction locked, surface pending | approved plan; gate condition 2 stays `NEEDS-REVIEW` (not RED) while the surface front is in flight | exec = compiler lane, incremental slices each with its own proof; the docs lane keeps the record |
| — | living records: `conformance-matrix.md`, `ecosystem-coverage.md`, `KOFUI-AUDIT.md`, `known-bugs.md` (in `docs/bugs-and-gaps/`); `roadmap.md` (here); `roadmap-audit.md`/`complexity-audit.md` (in `docs/audits/`) | `LIVE` | **they are not backlog** — matrix/audit/queue that update together with each closure | update the cell/section in the SAME commit that closes the gap |

**R12 rule (AGENTS.md):** nothing from `future/` (RAII, package-compiler,
bare-metal) opens before SYSTEMS closes (parity + GC + stability).
**Exception, maintainer decision 17/09** (`DECISIONS.md` §D-UNIVERSAL):
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` was **promoted to current work** with the R12 gate
**overridden** — its entry point is Stage 1 (SYSTEMS consolidation) + R1–R12,
so it attacks exactly the SYSTEMS items this rule requires closing.

---

## 2. Open bugs (queue in `docs/bugs-and-gaps/known-bugs.md`) — triage 13/09,
resynced 14/09 ~22:15, **live count resynced 21/09** (docs lane — living
record, the rule of §1 of the three-states table). The **authority** for the
live set is `scripts/check_known_bugs_status.sh` (EN×PT consistent), never a
number written by hand.

**The CHANGELOG cannot lie about the ledger**: `scripts/check_changelog_ledger.sh`
cross-checks every `§NNN ✅ FIXED` claim against that live set (same classifier, the
open list the gate prints) and REDs the silent-revert case that actually happened on
21/09 — a stale-base rebase flipped §388 `✅→🟡` while the CHANGELOG kept claiming the
flip, with zero conflict to warn anyone. Historical quotes of a half-closed item may
be waived only by a named line in `scripts/changelog-ledger-waivers.txt`, never by
editing the gate.

**Ledger links must actually land**: `scripts/check_ledger_anchors.sh` recomputes the
GitHub slug of every section heading and compares it — by exact string — to the
`pt-switch`/`en-switch` href of the opposite language (diacritics folded, punctuation
deleted, `_` kept). Measured 21/09: 11 of 26 hrefs were hand-abbreviations pointing at
nothing (including two this lane shipped the same morning). All regenerated to zero, and
`--selftest` plants a truncated slug so the class cannot silently return. Both gates sit
in the CI agent suite (`run-agent-tests.sh`) and fire per-change via `agent-verify.sh`.

**Living counts must match the authority**: `scripts/check_live_records.sh` extracts every
`N items`/`N live` declaration from this lane's two READMEs and requires it to equal the
classifier's live count. The class drifted twice on 21/09 — a phrase resync left a table row at
`18`, and the next resync missed the same row again, caught by the sister lane in `5a80625c`. A
number hard-coded in two places is a promise to drift; a *missing* declaration is a failure, not
a free pass (anti-neutering: a wording change must update the gate too). The hook wiring is
proven functionally — `agent-verify-wiring-test.sh` executes the real block skeleton, catching a
correctly-written regex trapped in a mis-nested `if` (a bug this lane planted and then fixed the
same hour).

The same gate also enforces EN↔PT parity of the `DECISIONS.md` decision IDs and of the section
numbering/level, and — added 21/09 — that the **`Pending (condition 3)` list in §0 equals the
loose set the release gate actually flags** (`ls docs/development/*.md` minus its `ALLOWLIST`).
The human registry may not disagree with the measurement: neither listing less nor more (a
planted extra loose doc was caught in both languages). It also checks the roadmap's **EG table** —
the source of release condition 6, which the gate reads in EN only — has the same EG-N rows and
the same closed/open state in EN and PT, by the gate's own `DONE|FEITO` rule (a planted PT
divergence is named). Finally, the section numbering/level parity that was checked for
`DECISIONS.md` now covers **every EN↔PT doc pair** in this directory — a `## 1.` in EN matched to
a `# 1.` in PT is named (a planted level slip in the PT README was caught).

**15 items in the open queue** (resynced 21/09 ~13:1x — 20→19 when
**§380** (JS nested-`if`/`throw` codegen) was formalized ✅ `9f383bcf`,
re-measured 16/0F at the tip; 19→18 when **§381** (entity-field keyword
OOM in the parser) was fixed ✅ `576a1dcb`; 18→17 when **§394** (test harness
leaks the served app) was fixed ✅ `d0464385` and **§353** (`io` method result
inside a lambda body — SEM014) was fixed ✅ 21/09 by the compiler lane; 17→18 when **§418** (riscv64 single-step debug
harness) was RE-PUBLISHED by the native lane the same day with fresh grounding after the
tree loss its §419 retraction records — count went down (fixes) and up (a real gap
re-surfaced) in one day, which is exactly why the script, not the prose, is the
authority; 18→19 when the db/orm lane OPENED §421 (native `db.connect` accepts any
scheme silently, refusal only at `kof_orm_*`) as its own honest catalog in F2c3 — counts
moving UP because lanes keep cataloguing against themselves is the ledger working, not
rotting; 19→18 when **§396** (println of a RECORD null on Native x86-64) was fixed ✅ `461a07e2` by the native lane the same day; 18→19 when the §396 lane OPENED **§422** (an `extern` unsupported signature "compiles clean") and 19→18 the SAME day when the FFI lane RESOLVED it — **NOT a bug, a stale test**: `Int[]` binds by design since D6-2, so the assertion was repointed to genuinely-unsupported signatures (`String[]`/`List<Int>` → `FFI001`, `Buffer(Int)` → `SEM096`), rejection intact (`CompilerDriverTest` 259/0F); 18→16 when the 21/09 nat orphan sweep closed **§192** (parseOrDefault cross hang — already fixed by `5d4d59b9`, the ledger had never been flipped; `KofMathTest.parseOrDefaultCrossArch` 1/1) and **§358** (`toString` on unbounded `T` native → honest `NAT004`; `NativeGenericDispatchGapE2ETest` 2/2); 16→15 when **§258** (the CodeQL umbrella, #775+#776) was FIXED ✅ 21/09 in-file by lane `.18` (`TestJdk.javaBin()` for #775 + the exhaustive `Target` `switch` for #776); by
`scripts/check_known_bugs_status.sh`; the number is a dated snapshot — the
script is the authority). The **32** counted on 14/09 and the 13/09 list
below are the HISTORICAL snapshot, preserved for the record (taken BEFORE the
§220–§239 wave). The conclusion holds WITH
correction: the items still open are owner/blocked/rule-6 — but the "ZERO
pure-code item" was REFUTED by the 14/09 wave itself: §236 (comparisonReturn
Bool×Int) and §238 (hoist of escaping local + sipush) were pure-code items of
the decompiler and **were fixed in the development lane** (unidades 2c,
`8719e304`+`f2371212`), while §233/§234 (test migration `split()->String[]` —
compiler lane) and §237 (`computeStack` — lane .22) catalogued as owned. The
rest of the 13/09 wave (§220–§232, §235, §239) belongs to lanes .15/.18/.22
or rule 6. Items from the 13/09 list that changed since: §129-[collection]
✅ 11/09 (`3645` — the OPEN §129 is the OTP one, number collision), the rest
continue as described. Closed 13/09: §89, §106 (+JS `ab85cfae`), §117, §131 (+residual
`73ca2d58`), §127-JVM, §155, §94, §156, §81 (BigInt), §163 (interpreter
2nd wide parameter); §157-160 and §65 closed/DOES-NOT-REPRODUCE.
All hanging on:

| Group | Bugs | Who unblocks |
|---|---|---|
| Ratified decision 13/09 — pending implementation | §161/NAT-STR01 (§89 ✅ `e33425b5`, §106 ✅ `5b939106`+JS `ab85cfae`, §117 ✅ `3734f2aa`, §131 ✅ `18a64d45`, §81 ✅ `839bd73f`, §163 ✅ `d2a8a618`; §45/DD-01 CLOSED 13/09 — see `docs/decisions/DD-01-finally-return.md`) | ratified queue / executor lanes |
| Rule-6 frozen | ~~§101~~ ✅ FIXED 14/09 (DECISIONS §1 option A — pure IEEE 754 on all targets) | nobody (contract) |
| Someone else's lane | §104b-ii + §107 remaining + §114 (bugfixer — record storage-box), §132 (OTP-JS) ✅ CLOSED 18/09 (#83-JS — landed on the dev/KofJS lane, not a foreign lane), §165 (js-slices — re-verified 13/09: does NOT reproduce in a clean build, probable non-bug) — §129 ✅ FIXED 15/09 (lane development `192.168.100.18`) | lane owners |

Fixed 13/09: **§89** (numeric conversion in a primitive = alias of `as` +
warning SEM090; 4 targets — `CoreRegressionE2ETest.numericConvertMethodAliasOfAs`),
**§106** (`json.encode(Map)` keys SORTED on the 4 targets — `JsonCompleteE2ETest`
+ `jsonenc-map` cell of the matrix; JS residual `ab85cfae`),
**§117** (cancel by real TID + linear probe on Native x86 — `KofConcurrency2Test`
34/0), **§131** (method overload by signature on the 4 backends —
`CoreRegressionE2ETest.methodOverloadByArity` + harness 4/4), **§94** (EQ/NE of Double/Float in the interpreter now IEEE —
`stdsqrt` cell 4/4 without exclusion), **§127-JVM** (cast to function type →
synthetic SAM interface; `LambdaE2ETest.castToFunctionTypeJvm/Native`),
**§155** (function type as type-arg → parser preserves the spaces of the type-ref;
`LambdaE2ETest.declaredFunctionTypeListJvm/Native`), **§156** (heterogeneous
list of lambdas with the same signature → element without className, SAM
dispatch; `LambdaE2ETest.heterogeneousLambdaListJvm/Native`), **§81** (Long=BigInt
in JS, real 64-bit parity — `839bd73f`) and **§163** (interpreter: 2nd
wide parameter `Long`/`Double` read as `null` — `KofInterpreterParityTest.
wideParametersOccupyTwoSlots` + `wideparams` cell 4/4; `d2a8a618`).
Fixed 12/09: §90 (web, #98), §125,
§139, §140 (gate→ratchet), §107-face
scalar, §108, §138, MATH001, TIME002, **§145/§146/§147 (issue #101,
`440730c8` — qemu proof 42+42)**.

---

## 3. Maintainer decisions (record: `DECISIONS.md`)

> Nothing here is "halted waiting" — the fronts that awaited a decision were
> **ratified 13/09** and live in `DECISIONS.md` (D-STDLIB/D-SEC/D-APP/
> D-SPRING/D-PLAT/D-PLATFORM) with the execution queue open. The rule
> remains: **a front without a line in `DECISIONS.md` is not attacked** (rule 6);
> a chat decision is locked there in the same commit. `known-bugs.md` =
> `docs/bugs-and-gaps/known-bugs.md`.

| Item | Where | What it awaits |
|---|---|---|
| DD-STDLIB-01 — `randomBytes`/`randomChoice` (S10c) | `docs/stdlib/DD-STDLIB-01-array-returns.md` (CLOSED 13/09, moved to docs/) | ✅ IMPLEMENTED 13/09 (option 6a: `randomBytesHex` alias of `hex` + choice=idiom; S10c CLOSED) |
| DD-STDLIB-02 — `time.format`/`boundaries` | `DECISIONS.md` §D-STDLIB | ✅ RATIFIED 13/09 (UTC-only, ISO scalars, zero pattern-DSL) — **queue released** (todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds) |
| DD-01 — `finally` on the `return` path | `docs/decisions/DD-01-finally-return.md` (CLOSED 13/09, moved to docs/) | ✅ IMPLEMENTED 13/09 (option 4a: FinallyFrame in the IR + finallyReturnJvm/Js gates; suite 1627/0; bug 45 CLOSED) |
| DD-OTP (concluded) | `docs/planning-otp-supervision.md` (CLOSED 19/09, moved to docs/) | ✅ RATIFIED 13/09 (option 1a: wrapper `(id, result)`) — **S2-JVM ✅ 13/09** (`Supervisor.startAll`/single selectAny loop) + **S2-Native x86 ✅ 15/09** (§129 closed, DECISIONS §2 option B) + **S2-JS ✅ 18/09** (§132 closed, `OTP002` lifted) + **riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table; `OTP001` gate removed) |
| `pow`/`-lm`, `roundTo`-mode | `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ `pow` **DONE 13/09** (7a: `-lm`; 5 targets MATH001 cross; `stdmathpow` matrix + `powCrossArchRefused` `d736e36e`) · `roundTo` **DONE 14/09** (DECISIONS §3 ratified; S1b.3: `math.roundTo(Double,Int)`, half-away-from-zero by deterministic decimal scaling, no libm; 5 targets — riscv/aarch B32; `stdmathround` matrix + `roundToCrossArch` under qemu + `KofScriptStdlibParityTest.mathRoundToParity`) |
| NAT-STR01 (case-map astral) | `known-bugs.md` §161 / conformance-matrix | ✅ OPEN BY DECISION 13/09 — implement UTF-8 astral in the natives |
| §129 (cross-thread unwind via TLS) | `known-bugs.md` | ✅ FIXED 15/09 (DECISIONS §2 option B: TLS per-thread chain + per-worker handler in the trampoline; x86_64; riscv/aarch remain `OTP001`) |
| json §106 | `known-bugs.md` | ✅ FIXED 13/09 (option 2b: sorted keys) — JVM/x86/Script/JS (`5b939106` + JS residual `ab85cfae`); riscv/aarch port gap tracked separately |

---

## 4. Index of what is IN DEVELOPMENT here

### 4.1 Platforms & migration (fell from `future/` 12/09 — code started; the `~~struck-through~~` ones were **ratified 13/09 and consolidated into `DECISIONS.md`** — the 6 files of `decision-pending/` were deleted)

| File | Real state | What remains to close |
|---|---|---|
| ~~`PLATFORM-PLAN.md`~~ → `DECISIONS.md` §D-PLATFORM (dead) | F1–3/8/9 with code (`ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/007, conformance 11 tests) | F1 resolved by the manifest; F4/F5→KOFUI-AUDIT/stdlib-web; F6 wasm/F7 android→D-APP Q7/Q10 table; F9→conformance-matrix |
| ~~`APPLICATION_MODEL.md`~~ → `DECISIONS.md` §D-APP (Q1–Q10 locked) | `application { onStart/onShutdown }` ✅ E2E 3 targets; I2 (full-stack) ✅ `FullStackE2ETest` | `CmdNew` (I1), I3 (`--fat`), System/distributed — queue |
| ~~`LEGACY_MIGRATION.md` + `DECOMPILER.md` + `TRANSLATOR.md`~~ → **`future/` (DEPRIORITIZED by the maintainer 15/09)** — umbrella §4 IR/Confidence, §8 diff-testing; ~~+ `DIFFERENTIAL_TESTING.md` + `LEGACY_IR.md`~~ (MERGED into the umbrella 13/09) | code stays in the repo: `inspect/decompile/translate/compare/migrate` (`Main.java:25-29`) + `Confidence`/`Type.fromJvmSignature`; **NOT current work — promotion needs her explicit decision**; **live count = `roadmap.md` §23 TIER 3–5** (do not duplicate the number here) | coverage: opaque switch/athrow, `inspect --java` (R5 of the audit), IR non-JVM |
| ~~`IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md`~~ → `roadmap.md` §23 | **MERGED 13/09** (redundancy ~85% between them; status over-claimed vs code — e.g.: `CodegenStep` ✅ nonexistent, Native FFI was FFI001) | §23 is the single plan; tiers 6–12 = `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (promoted 17/09, R12 overridden) |
| ~~`PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md`~~ → `docs/audits/` | comparison branch `planning-future`×beta **closed 13/09** — no open code of their own lives in them: R2 lives in `DECISIONS.md` §D-APP/§D-PLATFORM; R5 in the migration cluster (`DECOMPILER.md`/`LEGACY_MIGRATION.md` §4 Phase C) | — (outside `development/`) |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | CLOSED 13/09 (FinallyFrame IR + gates; bug 45 FIXED, suite 1627/0) | — (outside `development/`) |
| ~~`planning-stdlib-time-design.md`~~ → `DECISIONS.md` §D-STDLIB | `addDays`/`diffDays` on the 5 targets | ✅ RATIFIED 13/09 — queue released |

### 4.2 Living plans & audits

| File | Real state | Note |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUDED 13/09 (S-1..S-6.1 + S-7; consolidated into `docs/stdlib/stdlib-loading.md`) | S-5-x86 = bugfix queue (`root_end`), outside the plan |
| `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | S0–S6, S8–S12 ✅ (MATH001/TIME002 closed 11/09) | only pending decisions (§3) |
| ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` | ✅ CONCLUDED 19/09 — 1st slice ✅ JVM+Script; **S2-JVM ✅ 13/09**; **S2-Native x86 ✅ 15/09**; **S2-JS ✅ 18/09**; **riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table; `OTP001` removed) | moved to `docs/` (3-state rule) |
| `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | CLI/DAP/LSP/stdout-json ✅ | IntelliJ plugin |
| ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ PROMOTED 19/09 (faces (1)–(5) closed; NATIVE002 CLOSED) | per-domain refusals live in known-bugs/backend-parity |
| ~~`security-plan.md`~~ → `DECISIONS.md` §D-SEC | A ✅; B/C ✅; C11 cookies + C18 middleware + D16 OAuth + D17 TLS-cert **ratified 13/09** (runs with I2 of the app model) | ChaCha20 = queue; OAuth: resource-server→client, provider=NEVER |
| ~~`plan-platform-completion.md`~~ → `DECISIONS.md` §D-PLAT (dead) | P0–P3 ✅; P4 (health/tracing/metrics) ❌; P5: `kof fmt` ✅ 31/08, LSP/VS Code ❌ | P4/P5 already have a home (§23/backend-parity); blog E2E = D-SPRING F12 |
| ~~`plan-spring-independence.md`~~ → `DECISIONS.md` §D-SPRING | F1–9 ✅; F10–F12 ratified 13/09 — **all IMPLEMENTED; §D-SPRING `CONCLUDED` 19/09** (audit vs code) | no open front in this record; follow-ups live in the trackers |
| ~~`conformance-matrix.md`~~ → `docs/bugs-and-gaps/` | Feature×4 targets matrix locked by `ConformanceMatrixTest` (11) + doc-gate | live: updates with every gap |
| ~~`ecosystem-coverage.md`~~ → `docs/bugs-and-gaps/` | G1–G12 with `PARTIAL`/`PLANNED` (events, batch, AI) | coverage reference |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monolith→micro) | long term |
| ~~`roadmap-audit.md`~~ → `docs/audits/roadmap-audit.md` | matrix 06/09 + queue P0→P5 (P0 CLOSED 09/09) | re-audit when something closes |
| ~~`KOFUI-AUDIT.md`~~ → `docs/bugs-and-gaps/` | UI001-Native (R6 face: silent no-op) OPEN | UI lane |
| ~~`known-bugs.md`~~ → `docs/bugs-and-gaps/` | **15 live** (live count — authority is `scripts/check_known_bugs_status.sh`; resynced 21/09; 18→16 when §192 and §358 were closed by the nat orphan sweep 21/09; 16→15 when §258 was closed 21/09 (#775 `NumericFormatterE2ETest` + #776 `KofHttp.supportedOn`); was 20 — §380 `9f383bcf`, §381 `576a1dcb`, §394 `d0464385` and §353 21/09 closed; §400/§418/§421 catalogued/re-published; the §2 live set is the authority; the historical 14/09 count was 32; §81/§163/§127-JVM, §155, §94, §157-160 and §65 closed/DOES-NOT-REPRODUCE 13/09) | live queue |
| ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **DONE + MOVED 13/09** (F1–F9 all closed — F3: NativeBackend 498 ≤500 measured, GC lane blocker expired/dead-owner rule); ratchet `check_500-baseline.txt` (debts locked — authoritative number = `wc -l` of the file) in CI | plan CLOSED (3-state rule) |
| `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` | **UNDER DEVELOPMENT 17/09** — promoted from `future/` by maintainer decision, which **overrides the R12 gate** (`DECISIONS.md` §D-UNIVERSAL); entry point = Stage 1 (SYSTEMS consolidation) + R1–R12 | architecture for Tiers 6–12; vision/design frozen, only state claims synced to code |
| `PROPOSAL-1.0-EXIT-GATE.md` (+`.pt_BR.md`) | **KOF 1.0 EXIT GATE — RATIFIED 20/09/2026** by the maintainer (`DECISIONS.md` §D-RELEASE-1.0); promoted from `future/`: the gate (§8) + the queue (§23) are the binding stabilization meta — **Kof RC 1.0 / release 1.0 exist only when every item matches and no edge is open** | order of execution = the PROPOSAL's own §23, tracked in `roadmap.md` §24 (EG-1..EG-10); **all seven `[? MEL]` edges CLOSED 20/09 by `DECISIONS.md` §D-1.0-EDGES** — KofC + Android inside the 8-target Stable 1.0 with their own gates (EG-9/EG-10), the nine §35 reinforcement candidates are mandatory gates, the 1.0 line opens after the 0.5.0 cut + EG-1..EG-7; the only remaining edge is the maintainer's RC-opening declaration (EG-8) |

### 4.3 `future/` — plan only, zero code (not current work)

> The **full, authoritative index** of this folder (every plan + its trigger)
> is `future/README.md` — the rows below are the ones that most often gate
> current work; when in doubt, read that index, not this table.

| File | Trigger to fall in here |
|---|---|
| `PLAN-MULTIPARADIGMA.md` (multiparadigm / functional pipelines + declarative queries; 16/09, design only) | first functional increment begins (SYSTEMS closed, R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump with `using`/`resource_scope` decided |
| `PLAN-BAREMETAL-BOOT.md` (native → bare-metal/bootable; 15/09 maintainer directive) | SYSTEMS closed (R12) + first face (HAL seam) authorized |
| `PLAN-BOOTSTRAP.md` (the Bootstrapper: Kof written in Kof — **north star**, `DECISIONS.md` §D-BOOTSTRAP, 20/09) | 1.0 EXIT GATE closed + entry conditions E1–E6 (`roadmap.md` §24) |
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (legacy migration platform) | **back here 15/09 — DEPRIORITIZED by the maintainer**; promotion needs her explicit decision |

*(DD-STDLIB-01 `planning-stdlib-array-returns.md` **left `future/` 13/09** — decision 6a ratified, implemented and moved to `docs/stdlib/DD-STDLIB-01-array-returns.md`.)*

*(historical moves of 12/09: 13 docs fell from `future/` to here —
evidence in each line of §4.1; SG snapshot 08/09 → `docs/history/`)*

---

## 5. What is NO longer here (consolidated 12/09, with proof)

| Left for | Doc | Proof |
|---|---|---|
| `docs/bugs-and-gaps/specification-gaps.md` | SG-001–023 + E1–E3 | maintainer queue COMPLETE (summary of the doc itself); old snapshot → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | levels 0–4 | query DSL 01/09 (`KofOrmE2ETest` 32; JS parity 18/09), MySQL prepared (`nativeMysqlPreparedBinary`), pooling ✅; DB001/DB002/ORM001 (native) live in the parity matrix |
| `docs/audits/complexity-audit.md` | snapshot 02/09 | pre-SOLID-500 numbers; live gate = `scripts/check_500.sh` (ratchet) |
| `docs/history/roadmap-gap-2026-09-03.md` | dated gap report | pending items live in roadmap-audit/known-bugs |
| `docs/decisions/` | `planning-switch-expr`, `planning-mutability` | SYN001, DD-02/SEM037/SEM038 applied |
| `docs/ui/PLAN-CANVAS-WIDGET.md` | CANVAS001 | `UiE2ETest` 29/29 without exclusions |

---

## 6. How to use (autonomous agent)

```
1. READ docs/status.md + docs/backend-parity.md            → what works (gate)
2. READ the §1 queue of this README + DOING.md (owners)    → what is missing, without collision
3. BUGS: known-bugs.md §Aberto only with an owner at the table; decision → §3, do not edit
4. EXECUTE a scope → test (suite with -Dmaven.test.failure.ignore=true)
   → commit with DOING.md updated → move doc to docs/ if CLOSED
5. RE-DISPATCH: no item in the §1 queue without an owner AND suite green → REFUSE
   (AGENTS.md stability condition)
```

**Synchronization:** `git fetch && git pull --rebase --autostash` before EVERY
commit; re-read this README after the pull (another agent may have closed an
item of the queue). `DOING.md` marks owner/state; this README is the **queue**.

**Do not confuse:** `training/` + `learn/` + `docs/` = stable corpus.
`development/` = work that is not yet expected behavior. A frozen-contract
change never passes through here without bump + decision (rule 6).
