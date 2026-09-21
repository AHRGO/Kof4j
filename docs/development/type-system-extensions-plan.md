[English](type-system-extensions-plan.md) | [Português](type-system-extensions-plan.pt_BR.md)

# Type-system extensions — incremental plan (X5 variance + sealed · X6 interop reflection)

> **Status: APPROVED 21/09 — implementing in slices, each with proof** (rule 6
> satisfied). The maintainer reviewed the plan and authorized incremental slices;
> X5's v1 surface is frozen by `DECISIONS.md` §D-X5-SURFACE. Authority:
> `DECISIONS.md` §D-TYPE-VARIANCE (X5 = option C, **approved**) and
> §D-INTEROP-REFLECT (X6, **approved** — incremental plan). Queue:
> `roadmap.md` §2.8.4/§2.8.5. Governing rules: rule 6 (maintainer decides),
> rule 11 (Simplicity Law on any surface), `D-KOF-FIRST`.

## Why spec-first

Both fronts touch **frozen core** (the type system) or open a **new access path
to program structure**. The maintainer reviewed and **approved** this plan on
21/09 (`D-TYPE-VARIANCE`/`D-INTEROP-REFLECT`); slices now proceed incrementally.
Every slice below is additive and must carry its own proof (test/golden per
target, rule 5 of the freeze). Type-classes remain a **permanent non-goal**.

## X5 — variance + sealed types

### Goal

- **Sealed**: a class/record whose subtype set is **closed** and **known at
  compile time**, so the typer can prove a `switch` is **exhaustive** (no
  `default` needed).
- **Variance**: declare how a generic type parameter varies (`out`/`in`) so
  `List<Dog>` is assignable to `List<Animal>` with the compiler proving safety.

### Non-goals

- No type-classes, no higher-kinded types, no full effect system.
- No runtime representation change: variance is **erased**; sealed is a
  **compile-time** property (must hold on JVM/Native/JS with identical output).

### Surface (v1 frozen — `D-X5-SURFACE`)

```kof
sealed class Shape
class Circle(Float r) : Shape
class Square(Float s) : Shape

String describe(Shape s) {
    return switch (s) {
        case Circle c -> "circle"
        case Square q -> "square"
    }   // no default: exhaustive because Shape is sealed
}

class Box<out T>(T value)   // declaration-site variance (single-char, no ceremony)
```

**Answered 21/09 (`D-X5-SURFACE`):** (a) variance keyword = **`out`/`in`**
(declaration-site, single-char — passes rule 11); (b) `sealed` applies to
`class`/`record` **and** `interface`; (c) **use-site** projection
(`List<out T>`) **is in v1** (X5.4 becomes a v1 slice); (d) diagnostics stay in
the existing **`SEM0xx`** family (no new family). Compiler/frontend only, no
runtime surface.

### Slices (each = one committable unit with proof)

| # | Slice | Scope | Proof |
|---|-------|-------|-------|
| X5.0 | **spec + cells** | freeze the surface (questions a–d); write conformance cells `sealed`/`variance` + `training/idioms` draft | review; no code |
| X5.1 | **`sealed` declaration** | parser + typer: closed subtype set; a subtype outside it is a diagnostic | red-first typer test; compiles on JVM/Native/JS |
| X5.2 | **exhaustive `switch`** | typer proves all cases covered for a sealed subject; missing case = diagnostic | red-first (missing case fails), green (complete); cross-target E2E |
| X5.3 | **declaration-site variance** | `out`/`in` on generic params; assignment compatibility check | assignability tests; erasure byte-parity across targets |
| X5.4 | **use-site projection** | decide in review (default: **deferred**) | — |
| X5.5 | **parity + docs** | conformance cells, parity matrix, `training/` + `learn/` | suite green; docs-lang 100% |

### Risks / open questions

- Variance soundness with mutable collections (`List<T>.add`) — the whole point
  of `out`/`in` is to forbid the unsound assignment; the typer must reject it.
- Exhaustiveness interacts with `when`/`else` and nullable subjects — needs
  explicit rules before X5.2.
- Erasure must keep the current ABI byte-identical (no accidental boxing).

## X6 — interop reflection

### Goal

- A **read-only** view of a type's structure (field names/types) available
  **only at the interop boundary**, so external data (Arrow/Parquet/ML schemas)
  can bind to Kof records without hand-written mappers.

### Non-goals

- **Never** a language foundation: no runtime metaprogramming, no dynamic
  dispatch, no annotations-as-framework, no reflection in user control flow.
- No write path; no `Class.forName`-style dynamic loading in the language.

### Surface (approved — X6 plan, incremental)

- A member of an interop namespace (e.g. `interop.schema(record)`), returning
  an **immutable** list of field descriptors usable only by the binding layer.
- Open questions: exact name/shape; whether it is exposed as a method or a
  compile-time intrinsic; which target leads (JVM-first, per R7).

### Slices

| # | Slice | Scope | Proof |
|---|-------|-------|-------|
| X6.0 | **spec** | scope, surface, target posture; confirm "interop boundary only" | review; no code |
| X6.1 | **JVM** | host-side structural read (existing `java.lang.reflect` behind the FFI layer) | E2E: schema of a record discovered and matched to a golden |
| X6.2 | **Native/JS** | honest gap `REF001` (or minimal) — never a silent stub (R6) | pinned diagnostic on unsupported targets |
| X6.3 | **parity + docs** | binding E2E (Arrow/Parquet-shaped), parity matrix, `training/`/`learn/` | suite green; docs-lang 100% |

### Risks / open questions

- Temptation to grow into general reflection — the "interop boundary only"
  fence must be enforced and documented.
- Performance/ABI: reflection must not leak into hot paths or change record
  layout.

## Sequencing / dependencies

`X5.0 and X6.0 (specs) ✅ reviewed/approved 21/09 → X5.1–X5.5 and X6.1–X6.3`
(now open, incremental). Both depend on nothing in the current critical path and
must **not** preempt Stage 1 (SYSTEMS) or R3/R4 work.

## Evidence

- Decisions: `DECISIONS.md` §D-TYPE-VARIANCE, §D-INTEROP-REFLECT (21/09/2026),
  §D-X5-SURFACE (X5 v1 surface freeze: `out`/`in`, `sealed` class/record +
  interface, use-site projection in v1, `SEM0xx`, 21/09/2026).
- Queue: `roadmap.md` §2.8.4/§2.8.5; `IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  rows X5/X6.
- Non-goals: `docs/philosophy.md`, `training/anti-patterns/fake-idioms.md`.

## Surface decisions (RESOLVED 21/09/2026)

`DECISIONS.md` §D-X5-SURFACE fixes the questions above: (a) `out`/`in`;
(b) `class`/`record` + `interface`; (c) use-site projection **in v1** (X5.4 is no
longer deferred); (d) `SEM0xx`. The "FOR REVIEW — not decided" heading above is
historical; the surface is frozen.
