[English](planning-switch-expr.md) | [Português](planning-switch-expr.pt_BR.md)

# Plan — `SwitchExpr`: switch as an expression

**Status:** `DONE` (consolidated 10/09 — moved from `docs/development/` to `docs/`:
SYN001 implemented and proven; the DoD boxes below were outdated)
**Created:** 03/09/2026
**Gap:** `SYN001` — **CLOSED**
**Owner:** agente-switch-expr
**Base:** `main` (after `b77249c`)

> Proposal: pattern matching through **expressions** (switch expression),
> in the spirit of Java 14 switch expression / Rust `match` / Ruby `case...in`.
> Motivation: a switch *statement* has pass-through, weird case scoping and
> `break` verbosity; the expression form eliminates all three.

---

## 0. Real state of `switch` in Kof (verified in the compiler)

Before the change, what the compiler **already** does (important for the scope):

- **There is no pass-through.** Each case is lowered as an if/else chain and
  ends with an unconditional `KofJump(end)` (`CompilerDriver.java`, case
  `SwitchStmt`, lines ~2682 and ~2727).
- **`break` inside a case is a no-op.** The lowering of `SwitchStmt` never pushes
  a `break` label (`breakLabels` empty), so `BreakStmt` emits nothing
  (`CompilerDriver.java:2153-2155`). The `break` in the examples of
  `training/anti-patterns/fake-idioms.md` is decorative.
- **Enum exhaustiveness is already an error** (`SEM031`): a switch over an enum without
  `default` and without covering all constants does not compile.
- **Pattern matching already exists** in the statement form: `case String s:` and
  `case Point(var x, var y):` (`Parser.parseSwitchStatement`).
- **Expression precedent already exists:** `if` is already an expression (`IfExpr`,
  `var s = if (c) "a" else "b"`).

That is: the "danger" of the switch statement (fall-through) **has already been removed** in
Kof. The real value of the proposal is **switch as an expression** — usable as a value
(`var x = switch (obj) { ... }`, `return switch ... { }`, nested).

## 1. Proposed form (additive)

```kof
// SWITCH-EXPRESSION — new, cases with `->`, body = single expression
var label = switch (obj) {
    case String s -> s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}

// SWITCH-STATEMENT — existing, remains 100% valid (backward compatibility)
switch (op) {
    case "GET":     doGet()
    case "POST":    doPost()
    default:        throw "op desconhecida: " + op
}
```

Rules:

1. **Additive.** `case ... :` (statement) and `case ... ->` (expression)
   coexist; the choice is by token. Old code compiles unchanged.
2. **`default` required** in the expression form (or enum exhaustiveness,
   as in `SEM031` for the statement). Without default or exhaustion → error
   `SEM032` (clear diagnostic, never fall silently).
3. **Case body = ONE expression** (no block scope → no "weird scoping"
   pointed out in the proposal). `break`/`continue` have no place; the
   expression cannot contain `return` (the switch is the value).
4. **Pattern binding** (`case String s ->`, `case Point(var x, var y) ->`)
   follows the statement semantics: the binding is visible only in the case's
   expression.
5. **Result type:** common type of the arms (like `IfExpr`): if all are
   equal, that one; otherwise, the `default`'s. No magic coercion.

## 2. Where the code lives (architecture)

| Layer | File | Δ lines | Note |
|---|---|---|---|
| AST | `AstNodes.java` | +12 | `SwitchExpr` + `SwitchExprCase` (expression node) |
| Parser | `Parser.java` | +55 | `parseSwitchExpression` (arrow); dispatch in `parseExpression` |
| Semantics | `SemanticAnalyzer.java` | +35 | `inferType(SwitchExpr)` + pattern scope via shared helper |
| KIR (JVM+Native) | `CompilerDriver.java` | +90 | `emitSwitchExpr` — `CJump`/`Label`/expr chain, like `IfExpr`; **covers JVM and Native at once** (common KIR) |
| JS | `JsBackend.java` | +20 | generalization of `tryParseIfExpr` to tolerate binding prologue (`StoreLocal`) inside the arm |
| Formatter | `KofFormatter.java` | +10 | debug/print of `SwitchExpr` |
| Test | `KofSwitchExprE2ETest.java` (new) | ~180 | JVM/Native/JS E2E + statement backward-compat |
| Docs | `training/` + `docs/status.md` + `docs/backend-parity.md` | — | new idiom + gap SYN001 closed |

> Why not extract the lowering into a new file? `emitSwitchExpr`
> depends on private state of `CompilerDriver` (`currentUnit`,
> `enumConstantsOf`, `emitExpression`, `inferExprType`, debug positions) —
> a clean extraction would require a context interface disproportionate to
> ~90 lines. Per-file growth stays small and cohesive; a larger extraction is
> a future refactor (same note the repo already carries for `NativeRuntime`).

## 3. Lowering (KIR) — design

`emitSwitchExpr` reuses the comparison strategy of `SwitchStmt`:

```
load subject → #switchExpr
[no pattern]  per case:  load #switchExpr; <caseValue>; SUB/EQ|kof_string_equals/NE; CJump(body_i | next)
[pattern]      per case:  load #switchExpr; KofInstanceOf T; 0; CJump EQ(body_i | next)
Label body_i:
    [simple pattern]      load #switchExpr; KofCheckCast T; store s
    [destructuring pattern]  load #switchExpr; KofCheckCast T; store #t;
                           load #t; KofLoadField x; store x;  (per field)
    <body expression ops>     (leaves 1 value on the stack)
    Jump end
Label default: <default expression ops>
Label end:     (1 value on the stack = result)
```

Crucial difference from the statement: **each arm leaves exactly 1 value on the
stack** (the expression) instead of emitting statements. The `KofPop` that
`ExpressionStmt` would do is omitted — the value is the switch result.

**JS** backend: does not need a new structure. `parseExpressionFragment`
already recurses into nested `CJump+Label` (comment "if-expression or a
nested if-statement inside a branch", `JsBackend.java:1835-1847`); the
`SwitchExpr` appears as a chain of `IfExpr`/`JsConditional`. The only
change: `tryParseIfExpr` tolerates the **binding prologue** of the pattern case
(`LoadLocal; CheckCast; StoreLocal` before the arm expression) — without that,
the fragment's `break` at `KofStoreLocal` prevents recognition.

## 4. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Regression in the `switch` statement (the most used) | statement **is not touched**; backward-compat test runs the current corpus (`KofPatternMatchingTest`, `KofEnumSwitchTest`) as a gate |
| JS backend (re-parse of the KIR op by op) | reuse of the already-tested `IfExpr` path; new test `KofSwitchExprE2ETest` with `Js` per form (value, pattern, destructuring, default) |
| `Optimizer`/`IRStatistics` do not know the new op | there is **no** new `KofOperation` — total reuse of existing ops (zero change in those files) |
| `collectCaptures` (lambda) does not see the binding | new branch in `collectCapturesStmts`/`Expr` mirroring the one of `SwitchStmt` |
| Suite (gate 840) | runs full before the commit; per-target golden E2E already covers the statement |

## 5. DoD (Definition of Done)

- [x] `var x = switch (obj) { case ... -> ...; default -> ... }` compiles and
      runs on **JVM, Native (x86_64, riscv64, aarch64) and JS**
      (`KofSwitchExprE2ETest`; cross-arch in the bug 59 gates)
- [x] Simple pattern (`case String s ->`) and destructuring
      (`case Point(var x, var y) ->`) on the 3 targets
- [x] `default` absent in a non-exhaustive switch expr → `SEM032` (clear error)
      (`ExpressionParser`/`StatementLowerer` reference the code)
- [x] Existing switch statement: current suite 100% green (retrocompat)
- [x] Full suite `mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am` green
      (10/09: 1362 run / 0 failures)
- [x] `training/idioms/` + `fake-idioms.md` updated (new idiom)
      (`training/idioms/control-flow.md` §switch)
- [x] `docs/status.md` + `docs/backend-parity.md` + `DOING.md` → `DONE`
      (status.md:264 `| switch | ✅ | ✅ | ✅ |`; doc moved to `docs/` 10/09)
