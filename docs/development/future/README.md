[English](README.md) | [Português](README.pt_BR.md)

# docs/development/future/ — future plans only (zero code)

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
| `PLAN-MULTIPARADIGMA.md` | multiparadigm / functional pipelines and declarative queries (`users.filter{...}.map{...}`), diagnosis at HEAD 16/09 | **design only, zero code in the doc** (§8 lists no file changed); promoted only when the first functional increment ships (SYSTEMS closed, R12) |
| `scoped-resources-plan.md` | lightweight RAII (TIER 2.4, `using`/`resource_scope`) | pure design — zero occurrences of `resource_scope`/`kof_resource`/`using` in the lexer/parser/runtime; gated by bump |
| `value-records-plan.md` | value records / first-class value types (TIER 2.7, `value record`) — accepted 16/09 (issue #275, `DECISIONS.md` §D-VALUE-RECORD) | **zero code** — the feature does not exist in the lexer/parser/backends; design only, gated by R12 + explicit authorization to open the front |
| `shell-plan.md` | `kof.shell` — idiomatic shell over `kof.process` (TIER 2.2, Stage 2) — **SIGNED-OFF 18/09, owner `.18`; MVP landed same day** | Function-form builtin namespace over the measured `kof.process` surface (Q1–Q3 answered by the maintainer poll): `cmd`/`run`/`ok` real on JVM+JS (byte parity, `ShellE2ETest`), `pipeline` JVM-only with honest `PROC001` on JS/Native (inherited from `process.spawn`); glob/`~`/redir stay out of v1 |
| `workflow-plan.md` | `kof.workflow` — jobs/pipelines/retry/checkpoints/dead-letter (TIER 2.1, Stage 2) — **PROPOSED 18/09, awaiting maintainer scope decision (rule 6)** | **zero code** — `kof.workflow` is not in the lexer/parser/backends; design proposal turning the bare tracker row 2.1 (`🔵`, owner `—`) into an executable slice todo, gated by Q1–Q4. It is a **composition layer** over already-shipped primitives — ABI measured in §4 across `kof.process`/`scheduler`/`orm`/`supervisor`/`concurrency` (JVM+ANDROID+JS fully green; Native = `PROC001`/`CRON001`/`ORM001` honest, inherited verbatim) |
| `PLAN-BAREMETAL-BOOT.md` | **native → bare-metal/bootable** (HAL seam B-0…B-5: freestanding, UEFI, legacy BIOS, MCU) — maintainer directive 15/09 | **zero code** — the runtime is hardwired to Linux syscalls, x86 needs `-lc`/`-dynamic-linker`, 32-bit codegen absent; classified per `PLAN-TREE-SHAKING.md` §T3 ("real embedded = RTOS/bare-metal backend of its own") — moves to `docs/` when B-1 produces a dynamic-free ELF |
| `DECOMPILER.md` + `TRANSLATOR.md` + `LEGACY_MIGRATION.md` | legacy migration platform (decompiler/translator/IR/diff-testing) | **DEPRIORITIZED by the maintainer 15/09 — back from `docs/development/`.** Code stays in kof-cli (`DecompileTest` 67/67, `TranslateTest` 61/61); the QUEUE is paused: promotion needs her explicit decision |
| ~~`planning-stdlib-array-returns.md`~~ → `docs/stdlib/DD-STDLIB-01-array-returns.md` | DD-STDLIB-01 | **CLOSED 13/09** — decision 6a + implementation (`randomBytesHex`->String; choice=idiom), moved to docs/ |

> **`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` left `future/` on 17/09/2026** — promoted to
> `docs/development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` as **current work** by maintainer
> decision, which **overrides the R12 gate** (see `DECISIONS.md` §D-UNIVERSAL).
> The vision/design is unchanged; the entry point is Stage 1 (SYSTEMS
> consolidation) and the executable recommendations R1–R12.

## Historical: what left `future/` earlier (snapshot 12/09 — NOT current state)

> **Do not read this as the current state** (updated 17/09). The migration cluster
> **returned to `future/` on 15/09** (row above), and the platform/app-model docs
> were **ratified and consolidated into `DECISIONS.md` on 13/09** (the 6 files of
> `decision-pending/` were deleted). The table is kept only as a record of the
> 12/09 fall.

| Doc | Destination / current home |
|-----|------------------|
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (the `IMPLEMENTATION_PLAN.md`+`ACTION_PLAN.md` were MERGED into `roadmap.md` §23 and `DIFFERENTIAL_TESTING.md`+`LEGACY_IR.md` into `LEGACY_MIGRATION.md`, all 13/09) | **back in `future/` 15/09** (deprioritized) — code+tests live in kof-cli (`kof inspect/decompile/translate/compare/migrate`, `Main.java`); live count in `roadmap.md` §23 TIER 3–5 |
| `PLATFORM-PLAN.md` | `DECISIONS.md` §D-PLATFORM (ratified 13/09; the file was deleted) |
| `APPLICATION_MODEL.md` | `DECISIONS.md` §D-APP (Q1–Q10 locked 13/09; the file was deleted) |
| `PLANNING-FUTURE-AUDIT.md`, `planning-future-reconcile.md` | `docs/audits/` (closed 13/09); R2→`DECISIONS.md` §D-APP/§D-PLATFORM, R5→migration cluster |
| `planning-finally-return.md` | `docs/decisions/DD-01-finally-return.md` (CLOSED 13/09; bug 45 FIXED) |
| `planning-stdlib-time-design.md` | `DECISIONS.md` §D-STDLIB (ratified 13/09; the file was deleted) — `addDays`/`diffDays` on 5 targets (TIME002 11/09) |

## When to move from `future/` to `docs/`

When the item stops being "plan only" and **there is code under development**,
even partially:

1. Move/rewrite the doc in `docs/` with **status `UNDER DEVELOPMENT`**;
2. Document **what is already done** (real files/lines) vs **what is missing**;
3. Include a **"how to finish"** section (step by step with dependencies);
4. Update `docs/backend-parity.md` / `docs/status.md` to point to the new
   path;
5. Keep the gap-code (e.g.: `NATIVE002`) until the item closes.
