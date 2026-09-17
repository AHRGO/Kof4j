[English](DD-01-finally-return.md) | [Português](DD-01-finally-return.pt_BR.md)

# DD-01 — `finally` on the `return` path of `try` (CLOSED 13/09 — moved to docs/)

> **✅ DECIDED 13/09 (maintainer, option 4a):** approves the proposal below (single lowering with `FinallyFrame` in the IR, mirroring the JVM bytecode, JS parser reconstructs) + **bump 0.3.0→0.3.1**. Implementation released to the lowerers lane.
>
> **Status:** ✅ **IMPLEMENTED 13/09** (option 4a ratified; the JS face of bug 45
> had already been fixed in `c727fee`): `FinallyFrame` in the IR
> (`CompilerDriverState`), `ReturnStmt` with active frame → store `#retVal` + jump
> `returnFinallyLabel`; the try epilogue runs the finally and returns/chains to the
> outer frame; lambda/method/function save the frame stack; JS: native try/finally
> + epilogue only with the return (avoids duplicate finally), `#retVal` pre-declared.
> Gates: `CoreRegressionE2ETest.finallyReturnJvm` (try-return, catch-return, void)
> + `finallyReturnJs`. 4-module suite **1627/0**. Bug gap 45 CLOSED (JVM/Native/
> interp/JS all `fin`+value). · **Gap:** bug 45 ·
> **Lane:** lowerers · **Created:** 08/09/2026 · **Proposed bump:** 0.3.0 → 0.3.1 ·
> **Moved from `future/` to `development/` 12/09** (implementation started — rule of 3 states)

## The conflict

`training/idioms/errors.md:107` documents the **expected** behavior:

> `finally` runs on the normal path, on the caught path and on propagation.

`return` inside `try` **is** the normal path. Expected (Java/Kotlin and the Kof
corpus): `finally` runs **and** the `return` value is preserved.

The current code, however:

| Target | `Int f(){ try { return 1 } finally { println("fin") } }` | Runs `fin`? | Value |
|---|---|---|---|
| JVM | `1` | **no** | ok |
| Native | `1` | **no** | ok |
| Interpreter | `1` | **no** | ok |
| JS | `fin` + `undefined` | yes | **lost** |

The first 3 **agree on the wrong** (they discard the side effect of the
`finally`); JS runs the `finally` but **loses the value**. None of the 4 reaches
the expected. The previous agent (07/09) labeled the 3 as "frozen by construction"
(rule 6), but that **contradicts the corpus** → by rule 4, the expected is law:
this is a **code bug**, not a frozen design decision.

**Why it is still a design decision *how to implement it*:** fixing it changes the
evaluation order (rule 6 — "frozen semantics 0.2.6-beta": evaluation
order). The *direction* is in the corpus (run `finally`); the *mechanism* and the
impact on the 4 backends + the JS reconstructor require experience → this DD asks
for a bump + sign-off before editing the lowering.

## Fix proposal (single lowering, propagates via IR)

Today the return-of-the-try does a direct `return`, skipping the `finally` block.
Proposal (mirroring what the real JVM bytecode does):

1. lowering of `TryStmt` with `finally` opens a **frame** in the driver stack
   (`Deque<FinallyFrame>` — analogous to `breakLabels`):
   `{ rethrowLabel, finallyLabel, valueSlot (if the function returns a value), returnType }`.
2. lowering of `ReturnStmt` with an active frame: instead of a direct `return`, it does
   `KofStoreLocal(#retVal, slot)` → `KofJump(finallyLabel)`; the `finally`
   epilogue ends with `KofLoadLocal(#retVal)` + `KofReturn`. (Void:
   only `KofJump(finallyLabel)`, the epilogue falls into `doneLabel`.)
3. `CompilerLambdaClass` (lambda body lowered in the same driver) **saves and
   zeroes** the stack on entry, restores on exit — otherwise a `finally` of the
   outer method would leak into the lambda (same pattern as `savedMutated`).
4. `JsControlFlowParser.parseTryStatement`: recognize the new IR form
   (store→jump-finally→load+return) preserving the value — JS **reconstructs**
   try/finally from the IR, does not emit raw; without this point JS would regress.

Alternative (lower IR risk, higher parser risk): the JVM backend already has a
native `finally`; but Native/interp/JS do not have a "real finally" — they all
consume the IR — so the fix **must** be in the IR (option above), not per
target.

## Acceptance gate (rule 3: refactor preserves semantics; here it is a *fix*)

- New cases in `ConformanceMatrixTest` (4 targets): `fin`+`1` in the
  return-in-try; `fin`+`1` in the catch-return; `fin`+`2` in the normal try;
  propagation keeps the throw **after** the finally.
- `finally` with `return` **inside** the finally itself (shadows the one of the try) —
  edge case to decide (Java: finally return wins).
- Golden/E2E per target; `BackendParityTest.finally-return` leaves the exclusion.
- Bump 0.3.1 in `KofVersion` + note in `docs/` (evaluation order change).

## Risks

- Evaluation order is frozen (0.2.6-beta): any change needs a
  bump + doc + migration. This DD asks for that authorization; **do not** edit the
  lowering before it (rule 6 + autonomous mode stop condition 1).
- The `Optimizer` may shrink/reorder the epilogue ops — validate that the
  store/load of `#retVal` survives optimization (new slot, no other
  readers → DCE risk). Mitigation: `locals.add` named `#retVal` + test
  with `-Xverify:all` (kitchen-sink already runs the strict verifier).
