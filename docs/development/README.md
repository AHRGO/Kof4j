[English](README.md) | [Português](README.pt_BR.md)

# Development — living backlog (only work in development)

> **Base:** `0.4.0-beta` · branch `beta-0.4.0` · **updated:** 13/09/2026
> **Suite measured at this HEAD:** `1662` run (1479 kof-compiler + 31 kof-script
> + 5 kof-c-compiler + 147 kof-cli), **0 failures** (13 errors = only missing `node`, environmental — all `*Js`), 157 skip (toolchain/node
> guards; without qemu the 84 cross are skipped) — with cross riscv/aarch
> 42+42 under real qemu (G-0/§142 added the
> header/OOM tests). Post-§131/§163 gate measured 13/09 (`gate_final2.log`, BUILD
> SUCCESS). **Authoritative suite number = the run on the host** (the gate
> `mvn test ... -Dmaven.test.failure.ignore=true`; check per module with
> `grep -rl FAILURE */target/surefire-reports/*.txt`), not this line — it
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
(SG-001–020 — maintainer queue COMPLETE 12/09, became a reference).

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
| 3 | `native-multiarch.md` (NATIVE002) | `IN PROGRESS` — ~30 cross faces closed under qemu (41+41 measured 12/09) | riscv/aarch parity = release stability condition | GC mark-sweep for riscv/aarch (remaining faces of §5; JS is another front) |
| 4 | `planning-otp-supervision.md` (#83) | `IN PROGRESS` — 1st slice ✅ 11/09 (core+`restartLimit`+`stop`) + **S2-JVM ✅ 13/09** (`startAll`/`lacoUnico` + identity wrapper; `KofSupervisorE2ETest` 8/8; Native=OTP001 §129, JS=OTP002 §132) | **DD-OTP RATIFIED 13/09** (option 1a: S2 JVM; riscv/aarch PARTIAL) — see §3 | S2-Native x86 pending on §129 (cross-thread unwind, nat lane); promote OTP001/002 only once the cross face is decided |
| 5 | `plan-editor-integration.md` (EDI001) | `IN PROGRESS` — degrees 1-3, 4-10, 11, 12 ✅ | the only step with no owner pending is tooling | IntelliJ plugin (DAP/LSP already work via CLI) |
| 6 | `plan-stdlib-expansion.md` | `IN PROGRESS` — S0–S6, S8–S12 ✅ | only what does NOT depend on a decision moves | `pow` ✅ DONE 13/09 (`d736e36e`) and S10c `randomBytesHex` ✅ DONE (6a) — **nothing without a pending decision in this doc**; only `format`/`boundaries` (DD-STDLIB-02) remain on the maintainer's desk |
| 7 | newly opened queue of `DECISIONS.md` (13/09): ~~`time.todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds` (D-STDLIB)~~ **✅ EXECUTED 13/09** (S7e-S7h, stdtime3-6 matrix, suite 1772/0/0; TIME003 = general queue) · ~~`CmdNew` (D-APP I1)~~ **✅ DONE 14/09** (`kof new --type mono\|backend\|frontend\|full-stack`, compilable skeletons, honest APP003, `CmdNewTest` 8/8, APP matrix in `backend-parity.md`) · `chacha20Encrypt/Decrypt` (D-SEC) · `security.cookies` + `app.security()` (with I2 of the app model) · ~~`--fat` (D-APP I3)~~ **✅ DONE 14/09** (`kof build --fat` → `kof-app.jar` executable with classes+runtime+deps; `CmdBuildFatTest` 4/4, `java -jar` proof; non-JVM honest refusal R6) · ~~blog E2E (D-SPRING F12)~~ **✅ DONE 14/09** (`KofBlogE2ETest` green; exposed+fixed 2 JVM bugs: `readRequest` counted body in chars vs `Content-Length` in bytes — hung a multibyte UTF-8 connection; raw JDBC CLOB on the read path) | `RATIFIED` (decision locked 13/09) | — | remaining §7 queue: `app.security()` (C18) + OAuth resource-server; each line = unit-test-commit |
| — | living records: `conformance-matrix.md`, `ecosystem-coverage.md`, `KOFUI-AUDIT.md`, `known-bugs.md` (in `docs/bugs-and-gaps/`); `roadmap.md` (here); `roadmap-audit.md`/`complexity-audit.md` (in `docs/audits/`) | `LIVE` | **they are not backlog** — matrix/audit/queue that update together with each closure | update the cell/section in the SAME commit that closes the gap |

**R12 rule (AGENTS.md):** nothing from `future/` (universal platform, RAII,
package-compiler) opens before SYSTEMS closes (parity + GC + stability).

---

## 2. Open bugs (queue in `docs/bugs-and-gaps/known-bugs.md`) — triage 13/09

**8 items in the open queue** (§101, §107 🟡, §104b-ii, §114, §129, §132, §161,
§165) — and the honest conclusion (`known-bugs.md:11`): **open queue = 8 items,
all with a decision/owner/blocker — ZERO pure-code-without-decision item in this
lane**. Closed 13/09: §89, §106 (+JS `ab85cfae`), §117, §131 (+residual
`73ca2d58`), §127-JVM, §155, §94, §156, §81 (BigInt), §163 (interpreter
2nd wide parameter); §157-160 and §65 closed/DOES-NOT-REPRODUCE.
All hanging on:

| Group | Bugs | Who unblocks |
|---|---|---|
| Ratified decision 13/09 — pending implementation | §161/NAT-STR01 (§89 ✅ `e33425b5`, §106 ✅ `5b939106`+JS `ab85cfae`, §117 ✅ `3734f2aa`, §131 ✅ `18a64d45`, §81 ✅ `839bd73f`, §163 ✅ `d2a8a618`; §45/DD-01 CLOSED 13/09 — see `docs/decisions/DD-01-finally-return.md`) | ratified queue / executor lanes |
| Rule-6 frozen | §101 | nobody (contract) |
| Someone else's lane | §104b-ii + §107 remaining + §114 (bugfixer — record storage-box), §129 (nat lane), §132 (OTP-JS), §165 (js-slices — re-verified 13/09: does NOT reproduce in a clean build, probable non-bug) | lane owners |

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
| DD-OTP (remaining) | `planning-otp-supervision.md` | ✅ RATIFIED 13/09 (option 1a: wrapper `(id, result)`; riscv/aarch PARTIAL) — **S2-JVM ✅ IMPLEMENTED 13/09** (`Supervisor.startAll`/single selectAny loop; `KofSupervisorE2ETest` 8/8); remains **S2-Native x86** (pending on §129, nat lane) and OTP002-JS (§132) |
| `pow`/`-lm`, `roundTo`-mode | `plan-stdlib-expansion.md` | ✅ `pow` **DONE 13/09** (7a: `-lm`; 5 targets MATH001 cross; `stdmathpow` matrix + `powCrossArchRefused` `d736e36e`) · `roundTo` **NOT approved by 7a** (ratification = pow only; "+roundTo" was an agent note in the plan — undefined surface/signature = rule 6, awaits maintainer decision) |
| NAT-STR01 (case-map astral) | `known-bugs.md` §161 / conformance-matrix | ✅ OPEN BY DECISION 13/09 — implement UTF-8 astral in the natives |
| §129 (cross-thread unwind via TLS) | `known-bugs.md` | ✅ OPEN BY DECISION 13/09 — nat lane |
| json §106 | `known-bugs.md` | ✅ DECIDED 13/09 (option 2b: sorted keys) — implement |

---

## 4. Index of what is IN DEVELOPMENT here

### 4.1 Platforms & migration (fell from `future/` 12/09 — code started; the `~~struck-through~~` ones were **ratified 13/09 and consolidated into `DECISIONS.md`** — the 6 files of `decision-pending/` were deleted)

| File | Real state | What remains to close |
|---|---|---|
| ~~`PLATFORM-PLAN.md`~~ → `DECISIONS.md` §D-PLATFORM (dead) | F1–3/8/9 with code (`ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/007, conformance 11 tests) | F1 resolved by the manifest; F4/F5→KOFUI-AUDIT/stdlib-web; F6 wasm/F7 android→D-APP Q7/Q10 table; F9→conformance-matrix |
| ~~`APPLICATION_MODEL.md`~~ → `DECISIONS.md` §D-APP (Q1–Q10 locked) | `application { onStart/onShutdown }` ✅ E2E 3 targets; I2 (full-stack) ✅ `FullStackE2ETest` | `CmdNew` (I1), I3 (`--fat`), System/distributed — queue |
| `LEGACY_MIGRATION.md` (umbrella — §4 IR/Confidence, §8 diff-testing) + `DECOMPILER.md` + `TRANSLATOR.md` ~~+ `DIFFERENTIAL_TESTING.md` + `LEGACY_IR.md`~~ (MERGED into the umbrella 13/09) | complete platform in the CLI: `inspect/decompile/translate/compare/migrate` (`Main.java:25-29`) + `Confidence`/`Type.fromJvmSignature`; **live count = `roadmap.md` §23 TIER 3–5** (do not duplicate the number here) | coverage: opaque switch/athrow, `inspect --java` (R5 of the audit), IR non-JVM |
| ~~`IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md`~~ → `roadmap.md` §23 | **MERGED 13/09** (redundancy ~85% between them; status over-claimed vs code — e.g.: `CodegenStep` ✅ nonexistent, Native FFI was FFI001) | §23 is the single plan; tiers 6–12 = `future/` (R12) |
| ~~`PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md`~~ → `docs/audits/` | comparison branch `planning-future`×beta **closed 13/09** — no open code of their own lives in them: R2 lives in `DECISIONS.md` §D-APP/§D-PLATFORM; R5 in the migration cluster (`DECOMPILER.md`/`LEGACY_MIGRATION.md` §4 Phase C) | — (outside `development/`) |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | CLOSED 13/09 (FinallyFrame IR + gates; bug 45 FIXED, suite 1627/0) | — (outside `development/`) |
| ~~`planning-stdlib-time-design.md`~~ → `DECISIONS.md` §D-STDLIB | `addDays`/`diffDays` on the 5 targets | ✅ RATIFIED 13/09 — queue released |

### 4.2 Living plans & audits

| File | Real state | Note |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUDED 13/09 (S-1..S-6.1 + S-7; consolidated into `docs/stdlib/stdlib-loading.md`) | S-5-x86 = bugfix queue (`root_end`), outside the plan |
| `plan-stdlib-expansion.md` | S0–S6, S8–S12 ✅ (MATH001/TIME002 closed 11/09) | only pending decisions (§3) |
| `planning-otp-supervision.md` | 1st slice ✅ JVM+Script; **S2-JVM ✅ 13/09** (`startAll`/`lacoUnico`); honest OTP001/OTP002 gates | S2-Native x86 pending on §129 (nat lane) |
| `plan-editor-integration.md` | CLI/DAP/LSP/stdout-json ✅ | IntelliJ plugin |
| `native-multiarch.md` | re-audit 12/09 under qemu: ~30 cross faces closed | GC riscv/aarch + faces §5 |
| ~~`security-plan.md`~~ → `DECISIONS.md` §D-SEC | A ✅; B/C ✅; C11 cookies + C18 middleware + D16 OAuth + D17 TLS-cert **ratified 13/09** (runs with I2 of the app model) | ChaCha20 = queue; OAuth: resource-server→client, provider=NEVER |
| ~~`plan-platform-completion.md`~~ → `DECISIONS.md` §D-PLAT (dead) | P0–P3 ✅; P4 (health/tracing/metrics) ❌; P5: `kof fmt` ✅ 31/08, LSP/VS Code ❌ | P4/P5 already have a home (§23/backend-parity); blog E2E = D-SPRING F12 |
| ~~`plan-spring-independence.md`~~ → `DECISIONS.md` §D-SPRING | F1–9 ✅/partial; F10–F12 **ratified 13/09** (scope locked) | F12 blog E2E **NOW** (platform validation); starter only afterwards |
| ~~`conformance-matrix.md`~~ → `docs/bugs-and-gaps/` | Feature×4 targets matrix locked by `ConformanceMatrixTest` (11) + doc-gate | live: updates with every gap |
| ~~`ecosystem-coverage.md`~~ → `docs/bugs-and-gaps/` | G1–G12 with `PARTIAL`/`PLANNED` (events, batch, AI) | coverage reference |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monolith→micro) | long term |
| ~~`roadmap-audit.md`~~ → `docs/audits/roadmap-audit.md` | matrix 06/09 + queue P0→P5 (P0 CLOSED 09/09) | re-audit when something closes |
| ~~`KOFUI-AUDIT.md`~~ → `docs/bugs-and-gaps/` | UI001-Native (R6 face: silent no-op) OPEN | UI lane |
| ~~`known-bugs.md`~~ → `docs/bugs-and-gaps/` | 8 open (triage §2 above; §81/§163/§127-JVM, §155, §94, §157-160 and §65 closed/DOES-NOT-REPRODUCE 13/09) | live queue |
| ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **DONE + MOVED 13/09** (F1–F9 all closed — F3: NativeBackend 498 ≤500 measured, GC lane blocker expired/dead-owner rule); ratchet `check_500-baseline.txt` (debts locked — authoritative number = `wc -l` of the file) in CI | plan CLOSED (3-state rule) |

### 4.3 `future/` — plan only, zero code (not current work)

| File | Trigger to fall in here |
|---|---|
| `PLAN-UNIVERSAL-PLATFORM.md` | decision + SYSTEMS closed (R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump with `using`/`resource_scope` decided |

*(DD-STDLIB-01 `planning-stdlib-array-returns.md` **left `future/` 13/09** — decision 6a ratified, implemented and moved to `docs/stdlib/DD-STDLIB-01-array-returns.md`.)*

*(historical moves of 12/09: 13 docs fell from `future/` to here —
evidence in each line of §4.1; SG snapshot 08/09 → `docs/history/`)*

---

## 5. What is NO longer here (consolidated 12/09, with proof)

| Left for | Doc | Proof |
|---|---|---|
| `docs/bugs-and-gaps/specification-gaps.md` | SG-001–020 + E1–E3 | maintainer queue COMPLETE (summary of the doc itself); old snapshot → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | levels 0–4 | query DSL 01/09 (`KofOrmE2ETest` 22), MySQL prepared (`nativeMysqlPreparedBinary`), pooling ✅; DB001/ORM001 live in the parity matrix |
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
