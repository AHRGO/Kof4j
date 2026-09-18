[English](shell-plan.md) | [Português](shell-plan.pt_BR.md)

# `kof.shell` — idiomatic shell over `kof.process` (design plan · Stage 2 · TIER 2.2)

> **Status: PROPOSED (18/09) — awaiting maintainer scope decision (rule 6). Zero code.**
> This file exists because `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` row **2.2** is a bare
> `🔵` line with owner `—`, while every other greenfield front in `future/`
> (`value-records`, `scoped-resources`, `PLAN-BAREMETAL-BOOT`, …) has a concrete plan.
> It **proposes** turning that line into an executable todo; it does **not** authorize
> opening the front — that call is the maintainer's. It also does **not** implement
> anything: `kof.shell` is not in the lexer, the parser, any backend, or the stdlib today
> (measured 18/09). Grounded in the **real, measured** `kof.process` surface (§4).

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

var r = shell.run("git", ["status", "--short"])      // Result reused from kof.process
if (r.ok()) println(r.stdout)

var n = shell.run("wc", ["-l"], cwd: "/src", env: {"CC": "clang"}).stdout.trim()

// stdout of A -> stdin of B, argv kept as lists (never re-parsed by a shell)
var out = shell.pipeline(shell.cmd("ls", ["-1"]), shell.cmd("wc", ["-l"])).stdout
```

Design invariants (borrowed from existing repo debts/precedent, not invented):
- **argv is always a `List<String>`** — the command is **never** concatenated into a
  string handed to a shell (this is the `concatenated-command-line` security class the
  repo repeatedly re-cleans; `shell` must be the good-path idiom that makes it hard to
  shoot yourself in the foot).
- `Result` **is** `kof.process`'s `Result` (`stdout`, `stderr`, `exitCode`) — one type, no
  second shape to keep in sync.
- `ok()` == `exitCode == 0`; any other gate is explicit.
- No implicit glob / `~` expansion / redirection unless a later, separate signed-off slice
  designs them — keeping v1 to `run` + `pipeline` + `cmd` + `cwd/env` + `ok()`.

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

## 5. Step queue (the executable todo this doc exists to produce)
Owner is `—` until the maintainer assigns it; default proposed owner = **development lane**.

- **2.2.0 [recon — 0 code]** *(JS-`spawn` portion DONE 18/09: it was unbound → now gated
  `PROC001`)* — enumerate exactly which `process` calls each target reaches today and produce
  the parity table above as a test-backed note. *Proof:* `DomainGapCodesTest` `processSpawn*`
  pins + recon commit; no shipped surface.
- **2.2.1 [design sign-off — ⛔ rule 6]** — maintainer approves the **function form** (§2) or
  redirects. **Gate on the whole front.** No further slices until this lands.
- **2.2.2 [MVP — JVM, one target]** — `run` + `pipeline` + `cmd` + `cwd`/`env` + `ok()`,
  lowered over existing `kof.process`; `ShellE2ETest` golden against a real, dependency-free
  command (e.g. `tr`/`wc`), asserting **argv-as-list** (no `sh -c`). JVM-only, honest gap
  elsewhere.
- **2.2.3 [parity]** — extend the golden to JS as `process` is confirmed there; Native stays
  `PROC001` until the native-lane `process.run` ships.
- **2.2.4 [docs]** — idiom doc `docs/stdlib/shell.md` (+PT), `backend-parity` row, flip
  `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.2 `🔵 → 🟡`, and only when shipped promote this file
  out of `future/` per the folder rule.

## 6. Open questions (maintainer decisions — do NOT resolve in code)
- **Q1** — function-form (this proposal) vs a shell-infix grammar (backtick/`|`) — the latter
  is a rule-6 grammar change; this doc recommends function-form.
- **Q2** — home of the surface: a compiler builtin namespace (`KofShell.java`, like
  `KofProcess`) vs a Kof-level stdlib package. Affects every backend, not just sugar.
- **Q3** — are glob / `~` / redirection in-scope for v1, or explicitly out (this doc's default)?

## 7. What NOT to do
- No `sh -c` string execution and no command-string concatenation (injection class).
- No grammar change (backtick/pipe/redir) without a signed-off rule-6 decision.
- No parallel process runtime — reuse `kof.process` and its gap codes verbatim.
- No code before **2.2.1** sign-off; no claiming Native support that `process` doesn't have.
