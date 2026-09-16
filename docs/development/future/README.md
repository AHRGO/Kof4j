[English](README.md) | [Português](README.pt_BR.md)

# docs/future/ — future plans only (zero code)

**Rule for this folder:** here lives **only** what is **a plan for the future** —
an architecture/vision document **with no implemented code** (or with code that
is explicitly non-deliverable and outside the current scope).

> If an idea **is already being implemented** (even partially), the
> corresponding doc **does not stay here** — it lives in `docs/` and documents
> the **real state** (what already exists) + **how to finish it**. That way the
> reader knows exactly where the thing stands and what is missing.

## Recent example (09/01)

- `kof-native-risc-arm.md` **left here** for `docs/development/native-multiarch.md`: the
  plumbing (enum `Target.NATIVE_RISCV64/AARCH64`, CLI `native.risc/arm`,
  dispatch, cross-as/ld) is already in the code, so the item is **under development**
  and started being documented with real state + a finishing plan.

## What stays here (plan only, no code)

| Doc | Topic | Why it stays in `future/` |
|-----|------|---------------------------|
| `PLAN-UNIVERSAL-PLATFORM.md` | long-term vision (Kof as a universal platform) | 100% vision/strategy — it is not an implementation order; no `ml`/`bio`/`hpc`/`infra-*` package in the code |
| `scoped-resources-plan.md` | lightweight RAII (TIER 2.4, `using`/`resource_scope`) | pure design — zero occurrences of `resource_scope`/`kof_resource`/`using` in the lexer/parser/runtime; gated by bump |
| `PLAN-BAREMETAL-BOOT.md` | **native → bare-metal/bootable** (HAL seam B-0…B-5: freestanding, UEFI, legacy BIOS, MCU) — maintainer directive 15/09 | **zero code** — the runtime is hardwired to Linux syscalls, x86 needs `-lc`/`-dynamic-linker`, 32-bit codegen absent; classified per `PLAN-TREE-SHAKING.md` §T3 ("real embedded = RTOS/bare-metal backend of its own") — moves to `docs/` when B-1 produces a dynamic-free ELF |
| ~~`planning-stdlib-array-returns.md`~~ → `docs/stdlib/DD-STDLIB-01-array-returns.md` | DD-STDLIB-01 | **CLOSED 09/13** — decision 6a + implementation (`randomBytesHex`->String; choice=idiom), moved to docs/ |
| `DECOMPILER.md` + `TRANSLATOR.md` + `LEGACY_MIGRATION.md` | legacy migration platform (decompiler/translator/IR/diff-testing) | **DEPRIORITIZED by the maintainer 15/09 — back from `docs/development/`. Code stays in kof-cli (DecompileTest 67/67, TranslateTest 61/61); the QUEUE is paused: promotion needs her explicit decision |

## Already fell to `docs/development/` (started — rule of 3 states, 09/12)

| Doc | Trigger for the fall |
|-----|------------------|
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (the `IMPLEMENTATION_PLAN.md`+`ACTION_PLAN.md` from this list were MERGED into `roadmap.md` §23 and the `DIFFERENTIAL_TESTING.md`+`LEGACY_IR.md` into `LEGACY_MIGRATION.md`, all 09/13) | migration platform with code+tests: `kof inspect/decompile/translate/compare/migrate` in `Main.java:25-29`, `Confidence.java`, `Type.fromJvmSignature` (live count in `roadmap.md` §23) |
| `PLATFORM-PLAN.md` | Phases 1–3, 8, 9 with code: `ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/PKG007, `conformance-matrix.md` locked by 11 tests |
| `APPLICATION_MODEL.md` | `application { onStart/onShutdown }` parsed+desugared+E2E on the 3 targets; `KofProjectConfig` |
| ~~`PLANNING-FUTURE-AUDIT.md`, `planning-future-reconcile.md`~~ → `docs/audits/` | audits **closed 09/13** (branch×beta comparison); R2→`DECISIONS.md` §D-APP/§D-PLATFORM (ratified 09/13; the 6 files were deleted), R5→migration cluster |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | CLOSED 09/13 (FinallyFrame IR + gates finallyReturnJvm/Js; bug 45 FIXED) |
| `planning-stdlib-time-design.md` | `addDays`/`diffDays` (the doc's D2 format) implemented on the 5 targets (TIME002 09/11) |

## When to move from `future/` to `docs/`

When the item stops being "plan only" and **there is code under development**,
even partially:

1. Move/rewrite the doc in `docs/` with **status `UNDER DEVELOPMENT`**;
2. Document **what is already done** (real files/lines) vs **what is missing**;
3. Include a **"how to finish"** section (step by step with dependencies);
4. Update `docs/backend-parity.md` / `docs/status.md` to point to the new
   path;
5. Keep the gap-code (e.g.: `NATIVE002`) until the item closes.
