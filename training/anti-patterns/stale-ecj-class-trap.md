[English](stale-ecj-class-trap.md) | [Português](stale-ecj-class-trap.pt_BR.md)

# The "297 errors" that weren't a regression — stale ECJ error-stubs in `target/classes`

## Problem

Maven here compiles with **ECJ** (the Eclipse compiler), not javac. When a
source file has a compile error, ECJ does not stop the build the way javac
does: it **emits a `.class` that throws
`java.lang.Error: Unresolved compilation problem`** at the exact line, and
the reactor can sail past it. That class then **lives in
`<module>/target/classes/`** and gets bundled into whatever the tests load —
and because the build is incremental, an old broken class can survive long
after the source was fixed, if the touched file's timestamps never forced a
recompile of the **dependency** module.

The trap: you run the full suite on a tip that moved code, and instead of a
handful of focused failures you see **hundreds of ERRORS** (this repo, 16/09:
297 errors — every test that touches `--target js` blew up with
`Unresolved compilation problem: KofJsWebview cannot be resolved`
at `KofJsRunner.run`), and the diagnosis instinct says "someone shipped a
regression". Nobody did: `kof-runtime/target/classes/.../KofJsRunner.class`
was a stale error-stub from an earlier interrupted build, while the CURRENT
sources compile clean. A `grep -rl FAILURE` on the reports reads like a fire;
a `mvn -pl kof-runtime clean` and a second run reads like nothing ever
happened. This is the **same trap family the repo already knows as §165 /
§257** (`static final` inlining showing stale values) — this entry is the
"throws at runtime" face of it, which masquerades as a MASS regression
instead of a wrong value.

## Bad

```
tip moves code (someone else's commit) → mvn test -o (incremental)
→ 297 errors across every JS-touching test
→ conclusion: "the .18's fatia A regressed the runtime" (WRONG)
→ "fix": revert / blame / lower the assertions to pass  ← forbidden (Q5)
```

## Preferred

```
297 errors on a tip you didn't break
→ (1) read ONE stack trace before believing the count:
      grep -m1 -A8 '<<< ERROR' kof-compiler/target/surefire-reports/<First>.txt
      "Unresolved compilation problem" = STUB, not a real failure
→ (2) rebuild the implicated module CLEAN:
      mvn -o -pl kof-runtime clean compile
      strings kof-runtime/target/classes/<Suspect>.class | grep -c "Unresolved compilation"  → must be 0
→ (3) re-run the suite. If it goes 297 → 0 errors, the "regression" was
      environmental: record the trap-hit in the commit message and move on.
→ (4) only if errors SURVIVE the clean rebuild do you have a real red —
      then Q0 applies: root cause, owner, known-bugs entry.
```

## Why

- The rule "the suite is the gate" presumes the suite measures the SOURCES.
  With ECJ + incremental builds, it can measure a **mix** of sources and
  ghosts — the number is real but the cause is not in any commit.
- "Any failure that isn't a documented environmental guard is YOURS"
  (AGENTS.md) is still true — but **diagnosing** whose it is comes before
  **acting** on it. A mass-error reading has a signature: hundreds of
  ERRORS (not failures), all threads through the same `Error:` line, in
  module(s) you did not touch.
- Blaming another lane's pushed code without reading a stack trace is the
  fastest way to poison multi-agent coordination (the .18 was mid-DB001 when
  this fired on 16/09 — the "revert that fatia" conclusion was one grep away).
- Related in this repo: §165 (same trap family, value face), §257 (same
  family, `static final` face), `weak-green-proof.md` (the mirror error:
  believing a green). This entry: **don't believe a red either — verify the
  classes are the ones you think they are.**
