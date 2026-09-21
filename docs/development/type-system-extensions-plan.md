[English](type-system-extensions-plan.md) | [Português](type-system-extensions-plan.pt_BR.md)

# Type-system extensions — incremental plan (X5 variance + sealed · X6 interop reflection)

> **Status: APPROVED 21/09 — implementing in slices with proof** (exec =
> compiler lane). The maintainer voted X5 = option C and X6 (incremental) and
> answered the X5 surface questions (`DECISIONS.md` §D-TYPE-VARIANCE,
> §D-INTEROP-REFLECT, §D-X5-SURFACE); the text below is kept as the measured
> spec. Queue: `roadmap.md` §2.8.4/§2.8.5. Governing rules: rule 6 (maintainer
> decides), rule 11 (Simplicity Law on any surface), `D-KOF-FIRST`.

## Why spec-first

Both fronts touch **frozen core** (the type system) or open a **new access path
to program structure**. The intent is recorded and the slicing is proposed here;
nothing is implemented until the maintainer reviews this document. Every slice
below is additive and must carry its own proof (test/golden per target, rule 5
of the freeze). Type-classes remain a **permanent non-goal**.

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

### Proposed surface (FOR REVIEW — not decided)

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

Open design questions for the maintainer: (a) exact keyword for variance
(`out`/`in` vs none) — must pass rule 11; (b) does `sealed` apply to
`class`/`record` only, or also to interfaces; (c) is **use-site** projection
(`List<out T>`) in v1 or deferred; (d) diagnostic code family for
non-exhaustive `switch` and variance violations.

### Slices (each = one committable unit with proof)

| # | Slice | Scope | Proof |
|---|-------|-------|-------|
| X5.0 | **spec + cells** | surface **frozen 21/09** (`D-X5-SURFACE` answered a–d); write conformance cells `sealed`/`variance` + `training/idioms` draft | ✅ frozen; no code |
| X5.1 | **`sealed` declaration** | parser + typer: closed subtype set; a subtype outside it is a diagnostic | ✅ **DONE 21/09** — contextual keyword (`sealed` before `class`/`record`/`interface`) + `SEM080` (direct subtype outside the sealed type's compilation unit); `SealedTypeE2ETest` 6 tests (JVM/Script run, JS/Native compile, red-first SEM080, identifier retro-compat) |
| X5.2 | **exhaustive `switch`** | typer proves all cases covered for a sealed subject; missing case = diagnostic | ✅ **DONE 21/09** — `SEM081` (missing direct subtype, no `default`); `SealedTypeE2ETest` green JVM/Script/JS + red-first SEM081 + default control |
| X5.3 | **declaration-site variance** | `out`/`in` on generic params; assignment compatibility check | ✅ **DONE 21/09** — parser (`TypeParser`) + `TypeParams.variance` + registro por tipo (classe/record/interface); `TypeChecker.genericArgsCompatible` aplica `out` (covariante)/`in` (contravariante)/invariante (§270) sobre args do MESMO raw; **guard de solidez SEM082** (posição errada de `out`/`in`) em `VarianceChecks`; `SemExpressionTyper` alinhado ao emit (args do `new` em tipo genérico do módulo); erasure intacta (compile-time só). `TypeVarianceE2ETest` 9 testes (covariante JVM/Script/JS, contravariante, invariante rejeitado, SEM082 ×3, `out` como identificador) |
| X5.3b | **variance em herança** | type-args de `extends`/`implements` com variança divergente | v1 limita a guarda de posição aos membros; herança fica como limite documentado |
| X5.4 | **use-site projection** | **in v1** (`List<out T>`) — `D-X5-SURFACE` overrode the "deferred" default | assignability tests; erasure byte-parity across targets |
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

### Proposed surface (FOR REVIEW)

- A member of an interop namespace (e.g. `interop.schema(record)`), returning
  an **immutable** list of field descriptors usable only by the binding layer.
- Open questions: exact name/shape; whether it is exposed as a method or a
  compile-time intrinsic; which target leads (JVM-first, per R7).

### Slices

| # | Slice | Scope | Proof |
|---|-------|-------|-------|
| X6.0 | **spec** | scope, surface, target posture; confirm "interop boundary only" | ✅ approved 21/09; no code |
| X6.1 | **JVM** | host-side structural read (existing `java.lang.reflect` behind the FFI layer) | E2E: schema of a record discovered and matched to a golden |
| X6.2 | **Native/JS** | honest gap `REF001` (or minimal) — never a silent stub (R6) | pinned diagnostic on unsupported targets |
| X6.3 | **parity + docs** | binding E2E (Arrow/Parquet-shaped), parity matrix, `training/`/`learn/` | suite green; docs-lang 100% |

### Risks / open questions

- Temptation to grow into general reflection — the "interop boundary only"
  fence must be enforced and documented.
- Performance/ABI: reflection must not leak into hot paths or change record
  layout.

## Sequencing / dependencies

`X5.0 and X6.0 (specs) → maintainer review → X5.1–X5.5 and X6.1–X6.3`.
Both depend on nothing in the current critical path and must **not** preempt
Stage 1 (SYSTEMS) or R3/R4 work; they are a queue, not current work.

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
