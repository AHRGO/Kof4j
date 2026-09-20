[English](unwrap-predicate-dead-gate.md) | [Português](unwrap-predicate-dead-gate.pt_BR.md)

# A gate stacked behind an UNWRAPPING predicate is dead code — check what the
predicate does to the case you want to catch, not only to the cases it keeps

## Problem

Codebases grow predicates that are *permissive by design* — they answer for a
whole family of types. Adding a specialized branch **after** such a predicate
(`if (A) ... else if (B_special) ...`) silently never runs when `A` already
matches the special case. The branch is dead code that *looks* correct in
review, the test for the special case is written, and it still passes — but
for the wrong reason or against the wrong path.

## Real case in this repo (20/09, §361, fix `e293c4a5`)

The boxed-nullable field-writer fix gates the erasure-box emission on
`TypeMetrics.isNullablePrimitive(fld.type())`. The first attempt added it as
an `else if` after the existing primitive→primitive widening branch:

```java
// ❌ Bad — looked correct, shipped RED 7/9 anyway
if (isPrimitiveType(a) && isPrimitiveType(b)) { emitWideningIfNeeded(a, b); }
else if (isNullablePrimitive(t)) { emitErasureBox(v); }
```

`TypeMetrics.isPrimitiveType` **unwraps** `NullableType`:
`isPrimitiveType(Int?) == true` (TypeMetrics.java:18). Every `Int?` store was
swallowed by the first branch before the new gate was ever reached — the
precise predicate was right, the *position* made it dead code. The landing fix
had to exclude nullable from the plain-widening branch
(`&& !isNullablePrimitive(...)`) as well.

## Preferred

```java
// ✅ GOOD — the unwrapping branch excludes the special case it must not eat
if (isPrimitiveType(a) && isPrimitiveType(b) && !isNullablePrimitive(t)) { emitWideningIfNeeded(a, b); }
else if (isNullablePrimitive(t)) { emitErasureBox(v); }
```

General form: before adding `else if (special)`, read the predicate of every
**preceding** branch and answer "does it already match `special`?" — if yes,
either narrow it (`&& !special`) or handle `special` **first**. And when the
fix claims to cover a family (`Int?/Long?/Double?/Char?`), write the test
matrix for EVERY member of the family — §361's 9/9 was false-green exactly on
the face this anti-pattern left out (`Char?`, still open as §368).

## Why

A dead branch is worse than no branch: it records intent the machine never
executes, so the next reader (or the next agent) believes the case is handled.
This is the compile-side twin of `weak-green-proof.md` (a proof that asserts
"no crash" instead of the contract) and of the §294-2a/§295(b) trap family —
`erasesToReference` returning FALSE for `NullableType` was the same mistake
mirrored in the other direction (a gate that did NOT fire for the special
case, instead of firing and being skipped). Related: `docs/bugs-and-gaps/known-bugs.md`
§361 addendum + §368, `DECISIONS.md` D-NULL-INTENT.
