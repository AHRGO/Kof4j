[English](scoped-resources-plan.md) | [Português](scoped-resources-plan.pt_BR.md)

# Scoped Resources — lightweight RAII (design plan · TIER 2.4)

**Status:** Plan (design) — implementation gated by version bump (frozen semantics 0.2.6-beta)
**Source:** `../PLAN-UNIVERSAL-PLATFORM.md` §7 · `roadmap.md` §23 TIER 2.4.1 (former `ACTION_PLAN.md`)

## 1. Objective

Release **scarce resources** (FFI handle, file, connection, GPU) when leaving the
scope, without introducing `ownership`/`borrowing` (permanent non-goal).

The doctrine (UNIVERSAL §7) is explicit:

> *"Resource management (RAII/scoped): Yes, **lightweight** — handle FFI handles,
> files, GPU, connections without leaking. **B/C** — a lightweight
> `auto-closed`/scope (**no ownership**)."*

Kof **already has** the mechanism (`try/finally` + GC). The scoped-resource is
**intent** sugar over the mechanism — the same pattern already used by
`test "name" {}` and `application { onStart/onShutdown }` (compile-time desugar,
zero special runtime).

## 2. Proposal (candidate syntax)

```kof
using (conn = db.connect(url)) {
    validate(conn)
    store(conn, record)
}                       // conn.close() runs even if `store` throws
```

Desugar (compile-time, identical to the already formalized `CodegenStep`):

```kof
{
    var conn = db.connect(url)
    try {
        validate(conn)
        store(conn, record)
    } finally {
        conn.close()
    }
}
```

### Variations considered

| Name | Syntax | Verdict |
|------|---------|----------|
| `using (x = expr) { }` | explicit, `close()` convention | ✅ candidate (familiar, no ownership) |
| `scoped { }` | implicit (any resource in scope) | ❌ magic — requires "recurse" analysis |
| `with` | collides with `switch`/pattern semantics | ❌ |

## 3. Semantics

- `using (x = e) { body }` declares a binding `x` scoped to the block.
- The cleanup is **a convention function** `close()` on the resource type
  (compiled as `x.close()`); if the type does not expose `close()`, a
  compile-time diagnostic (never silent fallback — R6).
- The `finally` guarantees closing on **both** paths (success/exception).
- Multiple resources: `using (a = f(); b = g()) { }` closes in reverse order
  (`b.close()` → `a.close()`), like `try-with-resources`.
- **No** transfer of ownership; `x` does not escape the block (return/external
  assignment is an error — the compiler does not try to "move").

## 4. Non-goals (this is not it)

- It is not `ownership`/`borrowing` (E, UNIVERSAL §7).
- It is not a complete effect system (D, research).
- It is not an `@AutoClose` annotation.
- It does not add a `Resource` type/interface to the stdlib *before* deciding
  the shape of the FFI/GPU boundary (2.1.6).

## 5. How it plugs into the existing codegen

The desugar enters the `CodegenStep` pipeline (TIER 2.2.2, already implemented in
`CompilerDriver.runCodegen`), flanked by `desugarTests`/`desugarApplication`:

```text
unit → desugarUsing → desugarTests → desugarApplication → lowering
```

## 6. Gate and order

| Item | State |
|------|--------|
| Mechanism (`try/finally` + GC) | ✅ already exists |
| Desugar hook (`CodegenStep`) | ✅ TIER 2.2.2 |
| `using` syntax | ⏳ **gated by frozen semantics** (bump 0.3.0) |
| `close()` convention + diagnostic | ⏳ same gate |

> The SYSTEMS stage (Tier 1) has already closed (09/03 — DOING.md), so TIER 2 is
> open. The real gate here is not R12: it is the **frozen semantics** (0.2.6-beta).
> Implementing the syntax now would violate "frozen semantics" (AGENTS.md) —
> a language change requires **version bump + discussion**, never silent
> addition. What is delivered is the design + the desugar ready to activate in 0.3.0.
