[English](uncatalogued-stubs-audit.md) | [Português](uncatalogued-stubs-audit.pt_BR.md)

# Uncatalogued stubs / incomplete development — review ledger

> **Living record of the REVIEW front** (session 9094, branch `beta-0.5.0`).
> It is **not** a bug ledger: a *confirmed* finding graduates to
> `known-bugs.md` (`§NNN`, EN+PT) with repro; a *candidate* stays here until it
> is measured. The machine inventory is reproducible:
> `scripts/audit-stubs.sh` (read-only).

**Started:** 21/09/2026 · **Base measured:** `16340f62` · **Scope:**
`*/src/main` (761 `.java`) + the 12 `.kf` host files under
`kof-compiler/src/main/resources/dev/kof/`.

## Why this front exists

The maintainer asked for a sweep of the **whole codebase** for stubs / partial
development that is **not** documented. The risk this front addresses is
precisely the silent one (R6): a path that *pretends* to work — a facade
`return null`/`return 0`, a `default` that swallows, a swallowed exception —
that no ledger entry points to.

## Method (reproducible)

```bash
bash scripts/audit-stubs.sh /home/mel/Kof4j > /tmp/slice1-report.txt
```

**Noise warning (measured, not assumed):** Kof comments are in Portuguese,
where `todo` = *every* and `stub honesto` = a **deliberate R6 refusal**
(documented in the code itself, e.g. `NativeMethodEmitter:387`,
`KofJsDbBridge:103`). A raw match is therefore a **candidate**, never a
finding — every candidate is triaged by hand and compared against
`docs/bugs-and-gaps/*` before being catalogued. This front explicitly **does
not** re-catalogue the legitimate honest refusals.

## Slice 1 — measured results (21/09, base `16340f62`)

| Signal | Raw | Triaged |
|---|---|---|
| Case-sensitive `TODO/FIXME/XXX/HACK` (`.java`) | 12 | **0 real** — all 12 are Portuguese "todo" (every) or `§`-referenced fix notes |
| `UnsupportedOperationException` | 3 | **0 stubs** — 1 is a string in a list; 2 are honest hard-fails (`NativeMethodEmitter:387` "R6: never silent"; `KofJsDbBridge:103` "DB002") |
| Empty `catch` (`.java`) | 31 | **30 benign** (file cleanup / env probe / reflective fallback) + **1 candidate** (below) |
| `@Disabled` / `@Ignore` (tests) | 0 | 0 — no disabled tests hiding pending work |
| `assumeTrue` / `Assumptions.` | 216 | all with an **honest environment reason** (toolchain C, MySQL, node, qemu, H2) — a contract of the house, not a mask |

**Conclusion of slice 1:** the code has **no abandoned `TODO` marker and no
disabled test**. Its "stubs" are the designed R6 refusal (an honest
diagnostic), which is correct behaviour, not debt. The remaining audit value
is therefore **semantic** (parity / partial handling), not greppable.

## Candidates (unverified — NOT catalogued as bugs yet)

### UI-JS-1 — JS UI event handlers swallow exceptions silently

- `kof-compiler/src/main/java/dev/kof/compiler/js/JsRuntimeUiEvents.java:62`
  (and `:78`, `:153`, `:173`): `try { … fn(kofEv) } catch (e) {}` — a throwing
  listener is **silently dropped**, with no `console.error`.
- Contrast: `JsRuntimeUiComponents.java:226` explicitly does
  `console.error("[kof] " + where + ": " + detail)` and re-throws — the JS UI
  runtime has two different policies for the same "user callback failed" case.
- **Status:** unverified. To graduate to `§`, measure the JVM/Script policy for
  a throwing UI callback: if they propagate (or log) and JS drops silently,
  that is a real R6/parity divergence and gets a `§NNN` + repro.
  **Next pass:** locate the JVM/Script UI callback dispatch, build a throwing
  handler on each target and compare the observable output.

## Slice 2 — parity invariant + one doc-drift finding (21/09)

**Invariant checked (grep + read of all call-sites):** for every
`Kof*.supportedOn(...) == false` there is a `gapCode(...)` **emitted at the
same lowering site** — e.g. `ExpressionStaticCallLowerer:139/146` (db),
`ExpressionMethodCallLowerer:411/413` (std/buffer/rng/math), `:429/432`
(observability), `ExpressionTimeCallLowerer:19/27` and `:53/60`,
`ExpressionDbCallLowerer:20/28`, `ExpressionLogCallLowerer:19/27`,
`ExpressionOrmCallLowerer:35` + `CompilerOrmSupport:77/83`,
`ExpressionSchedulerCallLowerer:19/21`. `KofGpu` has no `gapCode()` method but
emits `GPU001` inline at `ExpressionMethodCallLowerer:356-359`. **No silent
parity gap found** in the `Kof*` surface.

**DRIFT-NET-1 (found, fixed, comment-only):** `KofNet.java` advertised the
opposite of reality — the class comment said *"riscv/aarch ainda gated em
compile-time"* and `supportedOn` said *"byte-scan nativo pendente"*, while
`conformance-matrix.md` §net records **NET001 CLOSED 09/09** and
`NativeRiscvAsmRtB24` implements `kof_net_queryEncode`/`queryDecode` (slice
B24, aarch via translator; proof `KofNetTest.netOnCrossArch`). The
`supportedOn` returns `true` for **all** targets (correct), making
`gapCode()="NET001"` **vestigial**. Fixed the two stale comments and annotated
the vestigial one — **no behaviour change**, so Q1's test does not apply (a
comment cannot regress); proof = `mvn -o -pl kof-compiler -am compile` rc=0.

## Next passes (planned — not yet executed)

1. **Parity asymmetry check** (highest yield): for every
   `Kof*.supportedOn(function, target)` × `Target`, assert an unsupported path
   carries a non-null `gapCode()`. A supported-on-one-target/absent-on-another
   path **without a gap code is the silent incompleteness** this front hunts.
   Assinaturas mistas (`supportedOn(Target)` whole-namespace vs
   `supportedOn(String, Target)` per-function) ⇒ implemented as a JUnit test
   (`StdParityGapAuditTest`) so the proof is a green/red, not a grep.
2. **Q7 facade sweep:** methods returning `null`/`0`/`""` on a path that should
   compute, `default:` branches that hide an unhandled case, emitters that
   return empty without a diagnostic.
3. **Doc/code drift:** features marked done in `docs/` whose code is partial
   (cross-check the parity matrices and the tracker against the code).
4. **`.kf` host files:** the 12 files under `kof-compiler/src/main/resources/dev/kof/`
   (supervisor/workflow/makealive/android hosts) — triage their explicit
   "STUB" markers (some are declared by design).
5. **Close UI-JS-1** by measurement.

## Provenance

- Inventory script: `scripts/audit-stubs.sh` (read-only, idempotent).
- Base: `16340f62`; counts re-measured on this base.
- Prior recon artifacts: `/tmp/opencode/audit/{markers,candidates,hard}.txt`.
