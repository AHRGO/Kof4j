# Technical debt — historical ledger (maintainer-owned)

[English](tech-debt.md) | [Português](tech-debt.pt_BR.md)

**Status:** **OPEN — maintainer-owned working document (opened 23/09).**
The maintainer corrects and edits this file directly and takes the
important decisions here. Agents do NOT close items in this ledger on
their own: every item ends in either a maintainer decision (rule 6,
recorded in `DECISIONS.md`) or a fix landed by the owning lane with
proof. Rule: one item = one owner + one proof; no item is closed by
editing this text.

**Scope:** only debt that is REAL and MEASURED (failing gate, open
`§NNN` section, baseline entry, or reproduced symptom). Aspirational
refactors without a failing gate are NOT debt — they belong in
`docs/development/future/`.

**Authority chain per item:** `known-bugs.md §NNN` (symptom + root
cause) → owning lane in `DOING.md` → proof (test + suite) → this row
flipped to ✅. The gates that name the debt: `check_500.sh`,
`check_known_bugs_status.sh` (6 live 23/09), `check_release_050_gate.sh`
(cond. 7 = `bugs_gaps`), CodeQL baseline (`codeql-baseline.txt`).

---

## 1. Live bugs (§NNN with open faces — 6, measured 23/09)

| § | Face | Since | Owner / lane | What the fix must prove |
|---|---|---|---|---|
| §205 | heterogeneous `if`-expr print of `Object` on Native (direct case fixed slice 1; boxed-print face = §104b-ii residual) | 15/09 🟡 PARTIAL | nat | boxed/`Object` native print path; proof = conformance cell green on all 4 targets |
| §248 | interface default methods dropped on JS (`TypeError`) + Native (`null`, exit 0); JVM-only today | 15/09 🔴 OPEN | compiler `.22` | needs a **scope decision** (rule 6) before code |
| §271 | generic interface DISPATCH: erased `invokeinterface …(Object)Object`, no bridge on impl → `NoSuchMethodError` | 18/09 🔴 OPEN | compiler `.22` | Cluster A river, **rule 6** — bridge ABI decision first |
| §278 | Android: `kof.db`/`kof.orm` FIXED 20/09 (DB-2); **`kof.security` FIXED 23/09** (`D-TECHDEBT-23/09`, JCA shims, byte-parity); `kof.gpu` still refused (`GPU001` — its JVM stack needs FFM, absent on Android) | 17/09 🟡 PARTIAL | gaps-db `.15` | security done; gpu = separate Android GPU path (design decision) |
| §283 | aarch64 `time.interval`/`scheduler.every` without cancel never exits under qemu | 18/09 🟡 OPEN | nat | pre-existing, orthogonal to §253-B; proof = terminating qemu run |
| §423 | channels NEVER ported to riscv64/aarch64 Native (link `undefined reference to kof_channel_*`; honest NAT005 at lowering) | 21/09 🟡 OPEN | nat | the cross port, or keep the declared diagnostic |

Closed-reference (do NOT reopen without a regression): §444 ✅ 23/09
(TypeVariable branch in x86 `valueOf`), §446 ✅ 22/09 (check_500 split
`SemBinaryResultTyper` + `SemAssignmentAnalyzer`), §447 (Bool literal
box on Native), §442 (§280 slices), FFI D6 all slices (spec promoted to
`docs/ffi-abi-structs.md` 23/09), kof-c C3-residual (multi-eightbyte
struct, 23/09).

## 2. Size gate (`check_500.sh` — measured 23/09)

- **RED (blocks merge):** `nat/NativeBackend.java` 573 → **603**
  (crossed 600; split mandatory, precedent §442/§446).
- **Tolerated 500–599 (live debt, plan the split before it touches
  600):** `CompilerPipeline` 588 (−12 to red), `CollectionCallLowerer`
  584 (−16), `CompilerTypes` 581 (−19), `ExpressionBinaryLowerer` 576,
  `ExpressionLowerer` 585, `ExpressionTyper` 550, `CompilerComparisons`
  559, `RuntimeOrm7` 585 (−15), `KofInterpreter` 565, `CompilerCaptureScanner`
  568, `ExpressionMethodCallLowerer` 526, `CompilerDriverState` 528,
  `KofCParser` 504, `KofDebugJvmSession` 516, `RuntimeOrm5` 517,
  `RuntimeOrm8` 504, `SemExpressionTyper` 504 (+ 47-line baseline file
  `check_500-baseline.txt` — never grows inside the tolerated band).
- **Standing order:** whoever pushes a file past 600 owns the split in
  the same unit (behavior-preserving + same suite green).

## 3. Honest gaps by design (NOT bugs — keep the diagnostic, R6)

Native-callback/upcall (`FFI001`, no mechanism), `String[]`→`char**` +
`Buffer(U8)` native + cross `T[]`/`Buffer` (`FFI001`), cross
float/HFA/`> 16 B` struct (`FFI001`), `Buffer`/`Handle` surface D6-1=B
(rule 11/rule 6 — spec-first), variadics (decided: no general
variadics), `long double`/bitfields/packed (own gap codes when claimed).
These close only by maintainer decision + vertical slice, never by
silencing the diagnostic.

## 4. Process debt (agent-caused, already with standing rules)

- **Ghost SHAs / stale docs:** fixed pattern 22/09 (orphan `/tmp/w388`
  SHA in README; §280 re-measured on the merged tree).
  Standing rule: golden/proof pinned to code SHA, suite re-run on the
  merged tip before claiming green.
- **Shared-tree collisions:** two lanes + `git stash` is global (lost
  WIP 22/09); `/tmp` worktrees evaporate on power loss (rule 9).
  Standing rule: commit only own paths, `sync-push.sh`, DOING claim in
  the same commit, `preserve-both-sides` on rebase.
- **CodeQL triage backlog:** 69-line baseline + #973 (`structs` dead
  param in `KofCParser.checkParamBounds`, owner kof-c front) — remove
  the line only after the root fix + re-scan.
- **Selftest fragility:** the 0.5.0 gate selftest pinned a fixture doc
  (`ffi-abi-structs`) that has since been promoted — fixed 23/09 to
  `db-parity-plan`. Standing rule: fixtures pin STABLE names.

## 5. Decisions the maintainer takes here (open questions, 23/09)

1. **§248 scope:** JVM-only default methods, or port JS + Native?
2. **§271 bridge ABI:** emit bridges on generic-interface impls (erasure
   ABI line), or reject at compile?
3. **§278 Android:** port `kof.security`/`kof.gpu` stacks, or declare the
   refusal permanent?
4. **§423 channels cross:** schedule the riscv64/aarch64 port, or keep
   NAT005?
5. **Split order:** `NativeBackend` 603 first, or the nearest-to-red
   tolerated files (`CompilerPipeline` 588, `RuntimeOrm7` 585)?
6. **D6-1=B surface:** open the mutable-`struct` by-ref front now, or
   keep it parked (rule 11 spec-first)?


**Decided 23/09 (`D-TECHDEBT-23/09`, maintainer multiple-choice):** §248 = port JS+Native · §271 = emit bridges · §278 = port the stacks · §423 = schedule the port · split = all-in-batch · D6-1=B = open now. Queue: `roadmap.md` TIER 13.
Each answer lands as: decision line in `DECISIONS.md` (+PT) + queue in
`roadmap.md` §23/DOING in the SAME commit (rule: deciding without
recording = invisible; recording without queue = dead).
