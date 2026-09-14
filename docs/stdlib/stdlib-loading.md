[English](stdlib-loading.md) | [Português](stdlib-loading.pt_BR.md)

# Stdlib by reachability (tree-shaking)

**Status:** implemented 12/09/2026 (`beta-0.4.0`, issue #97) · **Last
updated:** 13/09/2026

> The developer declares what they intend to use; the compiler includes
> **only** what is really necessary to run the program. No
> micromanagement of dependencies, no `--include=json.parser`,
> no manual list to avoid bloat.

## How it works (per target)

| Target | Mechanism | Granularity | Seeds |
|---|---|---|---|
| Native x86_64 | reachability pruning over the program text + keep-all fallback | 113 slices (`RuntimeSlices`) | `kof_*` tokens in the program's `.s` |
| Native riscv64/aarch64 | same pruning (`RiscvSlices` port, 48 pieces) + `ld --gc-sections` | piece + section per function | idem (full vocabulary incl. symbols without the `kof_` prefix) |
| JS | reachability pruning enabled in the writer | top-level unit (`JsRuntimeSlices`, 17 blocks) | `runtimeImports`/`ioRuntimeImports` that `JsBackend` already accumulates |

**Properties (all locked by test):**

- Conservative fallback: keep-all → byte-identical to pre-pruning; exception in
  the map → full runtime + warning (never a silent broken link).
- Deterministic: same input → same set → same order → same
  artifact (seeds in `TreeSet`, emission in inventory order).
- Multi-module JS = **union** of the closures + rewrite of the shared
  runtime (never "first module wins"); observable header
  `// kof:seeds`, `// kof:units N/600`, `// kof:fallback <bloco>: <motivo>`.
- False negative of a real call site is impossible (text seed errs only
  toward MORE — bigger binary, valid link).

## Numbers (hello world — locked in `ArtifactSizeTest`)

| Target | Before | After | Drop |
|---|---|---|---|
| x86_64 (bytes / syms) | 138.928 B / 627 | **32.520 B / 37** | −77% / −94% |
| riscv64 (bytes / syms) | 144.000 B / 258 | **133.288 B / 18** | −82% syms (bytes drop little: the `.bss` of the bump heap ~260 KB is fixed without mark-sweep) |
| aarch64 (bytes / syms) | — / — | **133.112 B / 18** | baseline locked for the 1st time |
| JS (runtime) | 177.412 B | **6.873 B** | −96,1% |

One-sided tolerance +5% only for bloat — shrinking is the goal; sabotage of
the baseline → FAIL. Gate: `ArtifactSizeTest` (sizes) + absence tests
per family (`nativeFamilyAbsenceAfterPrune`, `riscvFamilyAbsenceAfterPrune`,
`JsRuntimePruneWriterTest`).

## Honest limits

- **x86 without `--gc-sections`**: requires `kof_heap_root_end` + `emitStaticData`
  inside the root range of the conservative scan (bugfix queue).
- **riscv/aarch bytes**: only really drop with GC mark-sweep (the `.bss` of the
  bump heap is fixed) — see `docs/development/native-multiarch.md`.
- **`kof_platform` in JS** (issue #104): `uuid`/`random`/`security` outside the
  GraalJS host give `ReferenceError` — pruning **preserves** the behavior,
  it is neither a regression nor fixed here.

## References (code)

- `dev.kof.compiler.ArtifactSize` + `ArtifactSizeTest` (gate)
- `dev.kof.compiler.nat.RuntimeSlices` / `RiscvSlices` (maps)
- `dev.kof.compiler.js.JsRuntimeSlices` + `JsArtifactWriter` (JS writer)
- `kof build --print-sizes` (stable, additive JSON)

Original development plan:
`docs/stdlib/PLAN-TREE-SHAKING.md` (history of the S-1…S-6 implementation).
