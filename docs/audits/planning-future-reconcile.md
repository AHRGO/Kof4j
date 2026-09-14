[English](planning-future-reconcile.md) | [Português](planning-future-reconcile.pt_BR.md)

# Reconciliation planning-future ↔ beta-0.3.0

**Date:** 09/05/2026 · **Branch:** `planning-future` (do not leave it; only synchronize with `beta-0.3.0`)

## What `planning-future` delivers

1. **Legacy migration platform** (docs/future, Phases A–H):
   `kof inspect` / `decompile` / `translate` / `compare` / `migrate` + `Confidence`.
2. **Formalized FFI** (TIER 2.1): `extern` syntax, gap `FFI001`/`FFI002`,
   real JVM binding (FFM) and Native x86-64 (dlopen/dlsym) — `abs`/`atoi`/`sqrt`.
3. **Codegen hook** (TIER 2.2) `CodegenStep` + **ct-eval** (2.3) string-concat folding.
4. **Decisions** 2.4 (scoped resources — design) and 2.5 (variance/sealed — defer).

## Merge state (09/05)

- `planning-future` merged with `origin/beta-0.3.0` (commit `4997e56`).
- Conflicts resolved: `JvmRuntime` (preview gate = `usesExtern` + `version<22`)
  and `DOING.md` (entries from both agents preserved).
- Version now `0.3.0-beta`.

## Pre-existing failure of beta-0.3.0 (NOT from planning-future)

`NativeE2ETest.execStringCharAt` → expects `72\n111`, gets `H\no`.
- Reproduces **identically** on the clean `origin/beta-0.3.0` (isolated worktree confirmed).
- Cause: semantics of `println(char)`/`charAt` in Native changed in the refactor/String-methods.
- **Owner:** refactor agent on `beta-0.3.0` — do not fix here (avoids conflict).

## Normalization checklist (when the ≤500-line refactor closes)

1. `git fetch` + merge `origin/beta-0.3.0` again into `planning-future`.
2. Re-anchor my additions that the refactor moves (contact points):
   - `CompilerDriver`: `externSignatures`, `isExternBound`, lowering branch
     in `case MethodCallExpr`, `CodegenStep`/`runCodegen`.
   - `SemanticAnalyzer.findExtern` · `Parser.parseExternDeclaration` (resolve
     the `extern` name without SEM015).
   - Already isolated in new classes: `NativeFfiRuntime`, `JvmFfiRuntime` (≤100 lines).
3. Run the full suite + E2E: `FfiE2ETest`, `ClassFileE2ETest`,
   `DecompileTest`, `TranslateTest`, `CompareTest`, `MigrateTest`, `OptimizerTest`.
4. Report/track `execStringCharAt` (if still red) to the refactor owner.

## Coexistence rule

Do not bloat the gigantic classes (`CompilerDriver`, `NativeRuntime`, `JvmRuntime`, …).
New FFI/migration code goes into new classes ≤500 lines (a pattern already followed
with `NativeFfiRuntime`/`JvmFfiRuntime`).
