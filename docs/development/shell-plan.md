[English](shell-plan.md) | [Português](shell-plan.pt_BR.md)

# `kof.shell` — idiomatic shell over `kof.process` (design plan · Stage 2 · TIER 2.2)

> **Status: v1 LANDED (18/09 `34e4344f`, `.18` lane)** — `cmd`/`run`/`ok` reais em JVM+JS
> (byte-parity nos 5 casos do `ShellE2ETest`), `pipeline` real no JVM; pipeline em JS/Native =
> `PROC001` honesto em compile-time (nunca `ReferenceError` cru — disciplina §235). Q1–Q3 da
> enquete da mantenedora respondidos: forma-função ✓, builtin `KofShell.java` ✓, glob/`~`/
> redireção **FORA do v1** (faces futuras, não dívida). A superfície do §2 foi reescrita para o
> que o parser realmente aceita hoje (não existem argumentos nomeados em Kof — o rascunho antigo
> usava `cwd:`). O MVP v1 landou; restam como desenvolvimento declarado: pipeline JS/Native
> (libera `PROC001`) e as faces v2 (glob/`~`/redireção). Por isso este plano saiu de `future/`
> (a regra "zero código" não vale mais) e vive em `docs/development/` até as faces residuais
> fecharem.

## 1. Objective
One typed, composable idiom for driving OS commands — run, capture, pipeline, gate on
exit code — **without** ever stringifying a command into `sh -c`. Today `kof.process`
already gives the primitives (see §4); what is missing is ergonomics: pipeline wiring,
`cwd`/`env` shaping, and an honest per-target story. `kof.shell` is **sugar over
`kof.process`** — it adds no new runtime primitive where `process` already covers the
call, so it inherits `process`'s behavior rather than forking a parallel one.

## 2. Proposal — function form, NOT shell-infix (the key design decision)
Measured fact: Kof's `Lexer`/`Parser` have **no** backtick, `$()`, `|`-pipe, `&&`, `||` or
`>` redirection operators, and adding them is a **grammar change (rule 6)**. So `kof.shell`
is proposed as a **plain function/builder API** on the existing namespaces, e.g.:

```
import kof.shell

var r = shell.run("git", ["status", "--short"])   // Result reused from kof.process
if (shell.ok(r)) println(r.stdout)                // ok() is a namespace fn: process Result has no methods

var n = shell.run(shell.cmd("wc", ["-l"])).stdout.trim()   // cmd() builds argv; run() overload takes it

// stdout of A -> stdin of B; argv kept as lists (never re-parsed by a shell)
var out = shell.pipeline([shell.cmd("ls", ["-1"]), shell.cmd("wc", ["-l"])]).stdout

// cwd/env is a 2.2.3 overload (Kof has no named arguments — syntax corrected at sign-off 18/09):
var x = shell.runWith(shell.cmd("make", ["-j4"]), "/src", {"CC": "clang"})
```

Surface types (v1): `cmd(String program, List<String> args) -> List<String>` (argv builder,
`[program] + args`, zero parsing), `run(String, List<String>) -> Result`, `run(List<String>)
-> Result` (argv overload), `pipeline(List<List<String>>) -> Result` (last stage's `Result`
carries the chain outcome), `ok(Result) -> Bool` (`exitCode == 0`).

Design invariants (borrowed from existing repo debts/precedent, not invented):
- **argv is always a `List<String>`** — the command is **never** concatenated into a
  string handed to a shell (this is the `concatenated-command-line` security class the
  repo repeatedly re-cleans; `shell` must be the good-path idiom that makes it hard to
  shoot yourself in the foot).
- `Result` **is** `kof.process`'s `Result` (`stdout`, `stderr`, `exitCode`) — one type, no
  second shape to keep in sync.
- `shell.ok(r)` == `exitCode == 0`; any other gate is explicit.
- No implicit glob / `~` expansion / redirection unless a later, separate signed-off slice
  designs them — keeping v1 to `run` + `pipeline` + `cmd` + `ok()` and `cwd/env` in 2.2.3.

## 3. Contract
- Pure lowering over `kof.process` where possible → same semantics, same binding, no new
  `Kof*` runtime surface on targets `process` already covers.
- Additive language surface only (a new `kof.shell` namespace); no change to existing
  `process` behavior (rule 6: minimal, reversible).
- Deterministic and platform-honest: a command `shell` cannot run on a target raises that
  target's **existing** `process` gap (`PROC001`), it never silently degrades.

## 4. Per-target ABI (R7 honest scope) — MEASURED at 18/09, not assumed
From `KofProcess.java` + `ExpressionProcessCallLowerer.java` (read, not inferred):

| target | `process.run` | `process.spawn` | `process.exit` | source of truth |
|--------|:---:|:---:|:---:|-----------------|
| **JVM** | ✅ | ✅ | ✅ (`System.exit`) | `KofProcess.RESULT/HANDLE`, ProcessBuilder |
| **JS** | ✅ (`kof_platform.processRun`, node host) | ❌ `PROC001` — JS binds no `kof_process_spawn`/*handle* op (measured `ReferenceError`; gated honest 18/09) | ✅ (sentinel) | `js/JsRuntimeOps.java` `isRuntimeOp` lists run/exit only |
| **Native** | ❌ `PROC001` (compile-time) | ❌ `PROC001` (compile-time) | ✅ (syscall) | `ExpressionProcessCallLowerer` spawn/run `PROC001` gates |

Consequence for `kof.shell`: `run`/`exit` are real on JVM+JS from day one; but **`spawn` is
JVM-only** — an inherited `PROC001` on **both Native and JS** until the platform lands a
live-pipe binding there (a separate item, **not** this plan's scope). `shell` must not paper
over that — it reports the same honest gap. *(Corrected 18/09: an earlier draft trusted the
`ExpressionProcessCallLowerer` comment "JVM/JS support it"; measuring the JS backend shows it
emits a raw `kof_process_spawn(...)` call with no binding, and the fix gates JS spawn to an
honest `PROC001` — see `DomainGapCodesTest.processSpawnOnJsIsProc001`.)*

### 4.1 Wiring map for a new builtin namespace (measured 18/09)
Every cell of the table above is already locked by tests, so no recon test is added
(`DomainGapCodesTest` pins native-run, native-spawn and js-spawn `PROC001` + JVM-spawn
gap-free; `CoreRegressionE2ETest.processRun` (F4) `runBoth` runs `process.run` on JVM **and
JS**). What `KofShell.java` must register, measured from how `process` is wired:

| touchpoint | file:line | what goes in |
|------------|-----------|--------------|
| parser/typer member-call | `MemberCallNamespaces.java:90` | `shell` receiver → type the call (like `process`) |
| typer method-call (no member) | `MethodCallNamespaces.java:145` | same result types on the non-receiver path |
| bare-identifier whitelist | `SemExpressionTyper.java:90,152` | add `"shell"` so `shell` isn't "unknown identifier" |
| lowering dispatch | `ExpressionMethodCallLowerer.java:242` | `shell.*` → new `ExpressionShellCallLowerer` |
| JVM runtime binding | `jvm/JvmRuntimeCallDescriptors.java`, `JvmRuntimeReturnDescriptors.java`, `JvmRuntime.java` (name list) | `kof_shell_pipeline` (the only *new* binding; `run`/`cmd`/`ok` lower onto existing `kof_process_run` + list/bool helpers) |
| LSP catalog | `StdCatalog.java:45` + `StdCatalogTest.java:89,194,276` | `m.put("shell", KofShell.functions())` + catalog guard update |

`pipeline` cannot reuse `kof_process_spawn` handles from the IR (that would need
read/write/exit loops per stage); it lowers to one new JVM helper `kof_shell_pipeline
(List<List<String>>) -> Result` (ProcessBuilder chain, stdout→stdin in the runtime, last
stage's outcome). JS/Native hit the inherited spawn gap at **compile time** — the shell
lowerer must gate `pipeline` to `PROC001` there exactly like `process.spawn` does, never
emit a call that would `ReferenceError` (the §235 lesson).

## 5. Step queue (the executable todo this doc exists to produce)
Owner: **lane `.18`** (assigned by the 18/09 maintainer greenlight; default proposed owner =
development lane, confirmed).

- **2.2.0 [recon — 0 code]** ✅ DONE 18/09 — parity table §4 measured from the lowerer +
  the runtime bindings, and **every cell was already locked by existing tests** (see §4.1),
  so no duplicate pin was written.
- **2.2.1 [design sign-off — ⛔ rule 6]** ✅ DONE 18/09 — maintainer poll: Q1 function-form
  ✓, Q2 builtin `KofShell.java` ✓, Q3 glob/`~`/redir **out** ✓. Concrete §2 surface adopted
  (positional/overload form; `shell.ok(r)` namespace fn because process `Result` carries no
  methods — the earlier `r.ok()`/`cwd:` examples were rewritten, they do not parse today).
- **2.2.2 [MVP — JVM + JS for `run`, JVM-only for `pipeline`]** — `cmd` + `run` (both
  overloads, lowered through existing `kof_process_run`) + `ok` + `pipeline` (new
  `kof_shell_pipeline` helper); `ShellE2ETest` golden against real, dependency-free commands
  (`wc`/`tr`), asserting **argv-as-list** (no `sh -c`); js-pipeline/native-pipeline
  `PROC001` pins; JS run parity via `runBoth`. `cwd/env` (`runWith`) moved to 2.2.3 — it
  needs *new* runtime bindings on both JVM and JS host, which would grow the MVP blast
  radius for nothing.
- **2.2.3 [parity + add-ons]** — `runWith(cwd, env)` on JVM + JS host binding; JS
  `pipeline` when (and only if) a JS live-pipe `process.spawn` binding lands (separate
  platform item); Native stays `PROC001` until the native-lane `process.run` ships.
- **2.2.4 [docs]** — idiom doc `docs/stdlib/shell.md` (+PT), `backend-parity` row, flip
  `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.2 `🟡 → ✅` (the row moves with 2.2.2's tests), and
  only when shipped promote this file out of `future/` per the folder rule.

## 6. Open questions (maintainer decisions — do NOT resolve in code)
**All three ANSWERED 18/09 by the maintainer poll** (recreate via the same multi-choice
decision if ever revisited — rule 6):

- **Q1 — ANSWERED: function-form** (this proposal) — not a shell-infix grammar
  (backtick/`|`), which would be a rule-6 grammar change.
- **Q2 — ANSWERED: compiler builtin namespace `KofShell.java`**, like `KofProcess` —
  not a Kof-level stdlib package.
- **Q3 — ANSWERED: glob / `~` / redirection are OUT of v1** (this doc's default);
  a later signed-off slice may revisit them.

## 7. What NOT to do
- No `sh -c` string execution and no command-string concatenation (injection class).
- No grammar change (backtick/pipe/redir) without a signed-off rule-6 decision.
- No parallel process runtime — reuse `kof.process` and its gap codes verbatim.
- No code before **2.2.1** sign-off; no claiming Native support that `process` doesn't have.
