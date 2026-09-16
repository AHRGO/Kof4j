[English](planning-mutability.md) | [Português](planning-mutability.pt_BR.md)

# Planning — Mutability validators: `val` and record (DD-02, GitHub #42)

> **Status:** `APPLIED` (direct error, `cd0da824`+`ed0475c8`; reverting to warning = 1 line) · **Issues:** #42 (CLOSED), #53 (open: record+explicit ctor → ClassFormatError JVM, pre-existing) ·
> **Bump:** 0.3.1 → 0.3.2-beta · **Gate:** full suite + synced corpus ·
> **Created:** 09/09/2026 (analysis of the migration/issues lane session)

## The conflict (rule 6: this is a design decision, not an edit)

The corpus **promises** controlled mutability:

- `learn/04-variables-and-types.md:22` — `// PI = 2.0  // ERROR: cannot reassign`
- `learn/07-classes-and-objects.md:52` — `// u.name = "Ana"  // runtime ERROR: record is immutable`
- AGENTS.md — `record` = "**immutable** data"; `class X(...)` = record (immutable)

The compiler **does not enforce** any of that (bug #42):

| program | corpus says | JVM today | KofJS today | interp today |
|---|---|---|---|---|
| `val x = 1; x = 2` | ERROR | `2` (mutates) | `2` (mutates) | `2` (mutates) |
| `record P(Int x); p.x = 9` | ERROR | `IllegalAccessError` | `TypeError` | `9` (**mutates!**) |

Three separate defects, one root: the mutability information is **destroyed
in the parser**. `StatementParser.parseVarDecl` (kof-compiler/.../parser/
StatementParser.java:354-357) accepts `VAL` but stores `type="var"` — the AST does not
differentiate `val`/`var`. The `SymbolTable.LocalVariableSymbol` (register
`name, type, index`) has no mutability flag, so
`StatementAnalyzer.analyzeAssignmentStatement` could not even *check* today.

**Why it requires a decision:** today `val x = 1; x = 2` **compiles**. Adding the
guard breaks code that runs — backward compatibility is law (rule 2), except by
deliberate bump. This DD proposes the **additive two-stage path**.

## The design — "works in 100% of cases" = 100% of what is DECIDABLE

The first naive proposal ("one SEM guard covers everything") **does not close the
problem in 100%**. The problem has a formal boundary: reassignment in an arbitrary
control-flow loop is **undecidable** in general (halting — the assignment may
never execute). Every honest design needs a static approximation. The
consolidated industry pattern (Java `final`/effectively-final, Kotlin
comp-time, Rust `mut` on the binding, JS `const` in the parser) solves it like this:

> **Decidable rule:** the assignment is illegal **if the program syntax points
> to the immutable binding** (name resolved lexically to a `val`/wide
> parameter/record component). It does not matter whether the branch "never runs": code that
> *can* reassign an immutable is not valid code — it is the contract of
> `val` ("constant value", learn/04).

Under this rule, 100% of Kof programs are classified correctly (there is no
false negative: every assignment has a syntactic target that resolves to a symbol;
and there is no false positive: assigning to a `val` is always invalid per the corpus). The
approximation is EXACT for the language — the requested "100%" has no cost.

### Scope of what is immutable (normative table)

| binding | mutable? | reassign the name | write component |
|---|---|---|---|
| `var x` / explicit type (`Int x`) | yes | ok | ok (if class field) |
| `val x` | **no** | **SEM037** | n/a (reference points to object; the object's internal state follows its own rules) |
| function/method parameter | **decision DD-02a** | mutates ok today | n/a |
| `record R(...)` / `class R(fields)` | components **no** | n/a | **SEM038** |
| `class` field (with `constructor`) | yes | ok | ok |
| receiver `this` | no | **SEM039** (bonus) | ok |

Pending decision **DD-02a** (parameters): the corpus does not speak. Java allows
reassigning a parameter; JS too; Kotlin forbids it. Recommendation: **allow**
(parity with what the 4 backends already do today, zero broken code;
parameter immutability is style, not semantics — it can become lint later).

### Implementation (a single phase, three guards in the analyzer — not in the parser)

The correct place is **compile time** (static), not runtime: the error appears in
`kof check`, in the 4 targets with a single implementation (single source of truth
in the analyzer — platform rule 3; no checking in each backend's
lowering, which would create divergence again).

1. **Carry the information (additive to the AST, backward compatible):**
   `VarDeclStmt` gains `boolean mutable` (parser: `ctx.check(TokenType.VAL)` →
   `mutable=false`; others → `true`). Record is Java: the canonical constructor with
   `mutable=true` preserves all the ~40 existing call-sites (including
   desugars — which are generated code and always mutable). Rejected alternative:
   token `type="val"` (fragile, stringly-typed — the rule "intention, not
   mechanism" applies to internal nodes too).

2. **Persist in the symbol:** `LocalVariableSymbol(name, type, index, mutable)`
   and `FieldSymbol(..., immutable)` — for record/components and `this` the analyzer
   marks `immutable=true` at definition.

3. **The three guards in `analyzeAssignmentStatement`**
   (kof-compiler/.../StatementAnalyzer.java:24 — the SINGLE checkpoint of every
   assignment-statement of the 4 paths: JVM/JS/Native/interpreter all pass
   through `CompilerPipeline.java:301-304`, which aborts before lowering):
   - target `IdentifierExpr` → symbol with `mutable=false` → **SEM037**
     `"cannot assign to 'x': declared val"`;
   - target `MemberExpr` whose receiver resolves to a record/component — reuse the
     EXISTING predicate `CompilerTypes.isRecordType(Type, unit, analyzer)`
     (kof-compiler/.../CompilerTypes.java:188; already distinguishes canonical record from
     `class X(...)`-record) → **SEM038** `"cannot assign to 'p.x': record is
     immutable"`;
   - target `this` (name `this` in a context that is not a constructor) → **SEM039**.
   - `+=`/`-=`/etc. pass through the SAME path (AssignmentExpr with a
     compound operator — StatementParser reuses the node) → covered without extra code.

   Codes **SEM037/038/039** already reserved in this analysis: the highest used
   today are SEM033-SEM036 (free from 037 on).

### Why it closes the interpreter case and the cross-target case (rules 4 and 5)

- **Interpreter**: runs `analyze()` first (same pipeline) → the divergence
  "`interp mutates silently`" dies at the source: the program does not even reach
  interpreting (SEM error, not runtime).
- **JVM/JS today throw a runtime error** (`IllegalAccessError`/`TypeError`):
  with the guard, these programs start failing at compile — the runtime-err
  stops being reachable by new code; there is no output change for code
  that runs today (only code invalid per the corpus stops compiling).
- **Parity**: a single guard in the frontend → the 4 backends inherit it
  byte-identical (same diagnostic).

### Two-stage migration (backward compatibility, rule 2)

- **0.3.2-beta (this DD):** SEM037/038/039 as **WARNING** (new code:
  `kof check` warns; `--strict` promotes to error). Existing code does NOT break
  (it only gains a warning) — the language becomes honest without betraying the promise "runs today,
  runs tomorrow". `training/` + `learn/` updated in the same release
  (corpus rule).
- **0.4.0:** warn → **error** (major bump = documented deliberate break,
  migration note with a regex for `sed` in the cases that really need `var`).

### DoD test cases (100% of the decision classes)

1. `val x=1; x=2` → SEM037; `var`/explicit type → ok (do not regress).
2. `val x=1; x+=2` → SEM037 (compound passes through the same node).
3. `record P(Int x); p.x=9` → SEM038 in the 4 paths (single pipeline; the test
   interprets the same source and requires the SAME SEM failure before run).
4. `class X(...)`-record → SEM038 (parser already classifies it as a record — reuse).
5. `val l = listOf(1); l.add(2)` → **ok** (the immutable is the binding, not the object
   — learn/17:64 is literal about that; the test locks that boundary).
6. `this` in a record method (`bump(){ this.x=9 }`) → SEM038/039.
7. Reassigned parameter → ok (DD-02a) + optional future lint.
8. Lexical shadowing: `val x=1; { var x=2; x=3 }` → ok (resolves to the inner
   scope's symbol — `scope.resolve` already does that; the test proves the guard is not
   by name, it is by symbol).
9. Direct mutable class fields `u.age=27` → ok (do not confuse with #4).
10. Cross-target golden: invalid program → the 4 backends emitting the same
    list of SEM diagnostics (parity).

## What this design does NOT solve (R6 honesty)

- Deep mutability (`val` of a container): out — the corpus defines the binding,
  not the object (learn/17).
- Data flow (definite assignment of `val` without an initializer): `val x; x=1;`
  today creates a symbol with init null; the SEM037 guard forbids the second step,
  leaving `val x;` empty — acceptable (same Kotlin result); "val must have
  an initializer" is lint (SEM0xx reserved, phase 2).
- KofScript's `let`/`const` (aliases) → inherit: `const` → `val`, `let` → `var`
  (the .ks parser only needs to map to the same boolean).
  ⚠️ *Register note 16/09: this never happened — the `let`/`const` sugar was
  REMOVED from KofScript (`183cb048`, 06/09: "KofScript is pure Kof, no sugar
  from another language"). `let x = 5` is now `SEM011`/`PARSE011`. Historical
  line kept for traceability.*

## Files touched (when approved)

`parser/StatementParser.java` (flag `VAL`), `VarDeclStmt.java`,
`SymbolTable.java` (flags), `StatementAnalyzer.java` (3 guards ~40 lines),
reservations in `docs/diagnostics*`, `training/idioms/classes|records.md`
+ `learn/04|07` (two-stage note), `KofSemanticTest`/cross-target E2E.
