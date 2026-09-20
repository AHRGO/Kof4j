[English](makealive.md) | [Português](makealive.pt_BR.md)

# Infrastructure as typed code — `kof.makealive`

**Date:** September 20, 2026
**Status:** 3.1 LANDED (universal plan Stage 3, MK-1 slice — core + state + fs/CLI/REST providers). Maintainer poll `DECISIONS.md` §D-MAKEALIVE answered Q1–Q4: namespace `kof.makealive` (the `kof.infra` literal is R1-denied), providers = generic bodies written by the user, state on `kof.db` from day one, flat injected host with English faces.

> **What this is:** a typed description of a desired world (an `Infrastructure`
> of resources with props and dependencies), a pure `plan` diff against the
> current `State`, and `apply`/`destroy` executed through a `Provider` of
> three function-values. The host is composition only — every runtime
> boundary (files, processes, HTTP, a database) lives in the provider body,
> written by you in plain Kof. There is no new syntax (row 3.2 is ⛔R4) and
> no compile-time graph gate (row 3.7 is ⛔R4; runtime refusal ships named).

## The contract (faces injected by `import kof.makealive`)

```kf
import kof.makealive

var d = Infrastructure("prod")          // a design
d.resource("file", "index")             // (type, name) — name unique per design
d.resource("file", "style")
d.requires("style", "index")            // dependency: style is created AFTER index
d.prop("index", "html", "<h1>hi</h1>") // desired attributes

var p = Provider(
    (r: Resource) -> Map<String, String>,          // read the world for r
    (r: Resource, Map<String, String>) -> Bool,    // converge r to the props
    (r: Resource) -> Bool                           // remove r from the world
)

var rep = apply(d, State(), p)     // Plan = diff(desired, state); apply
var rep2 = apply(d, rep.state, p)  // → no-op when the world already converges
var rep3 = destroy(d, rep2.state, p) // reverse topological order
```

Hard rules (plan §3, all pinned by `MakealiveE2ETest`):

- **Idempotence is by construction**: `plan` is a pure diff; a second apply of
  a converged design runs ZERO provider calls. The source of truth for
  "converged" is the provider's READ, never host memory.
- **State only on success**: `apply` writes the new `State` from per-operation
  successes; a failed set/delete does NOT get recorded and `Report` keeps the
  untouched remainder of the previous state — no silent partial world (R6).
  The failed operation is refused LOUD, naming the resource
  (`makealive: provider recusou set em 'index'`).
- **Guards throw by name**: duplicate resource name (builder), dependency
  cycle (names every member), empty names. `destroy` runs the topological
  order REVERSED, then insertion-reversed for orphans.
- **State is data**: `State.entries` is a `List<StateEntry>` and each entry
  keeps `propFlat` (alternating key, value) — iterated with `while` + index.
  `Map.keys()` works on JVM/JS but is target-dependent (Native); `plan`'s
  READ happens through YOUR provider, so your body decides the encoding.

## The state port: persisting `State` on `kof.db`

`mkSaveState(dbConn, design, gen, state)` / `mkLoadState(dbConn, design)`
(faces of an additive db slice — the host-core never references `orm`, so the
whole package still compiles for Native):

- one row per (resource, position-in-propFlat) of the entity `KofMkState`,
  plus a MARK row at `idx = -1` so a resource with zero props still survives;
- **updates are new generations** (`gen` is a column): the entity is
  immutable (SEM038), re-saving a key is a unique violation — `mkLoadState`
  returns the rows of the MAJOR `gen` of the design;
- JVM and JS are byte-identical (the JS face delegates to the same in-JVM
  `kof_platform.db*`); **Native refuses at runtime naming `ORM001`** (the
  loud stub, same split as `workflow-ckpt-host`). The flip is mechanical
  (swap the stub for the slice) as soon as D-DB-GAPS delivers `orm.create`
  for Native (F1d); `delete_all` already landed as F1a (20/09).

Proofs: `MakealiveDbHostE2ETest` (roundtrip, max-gen, empty design, guards).

## Providers: generic bodies (fs / CLI / REST), not new APIs

The v1 surface is the three generic system interfaces — each one is just
YOUR provider body in plain Kof, and each has an executable golden:

| provider | boundary used | golden |
|---|---|---|
| **fs** | `kof.io` `File` (`readText`/`writeText`/`delete`/`exists`) | `MakealiveFsProviderE2ETest` |
| **CLI** | `kof.shell` `run`/`pipeline` (`exitCode`/`stdout`) | `MakealiveCliProviderE2ETest` |
| **REST** | `kof.http` `get`/`put`/`delete` + `http.status(url)` | `MakealiveRestProviderE2ETest` |

Measured shapes (20/09, these are the contracts — `docs/development/makealive-plan.md` §4):

- **fs**: `writeText` returns Bool on JVM but **JS reports the raw bridge
  code `0/-1`** (§382 — `0` is falsy); the honest pattern is effect-then-
  verify: `f.writeText(x); return f.exists()`. `File` has NO mkdirs (rule
  6 — the parent must exist); `readText` of a missing file returns `null`
  (positive-form check: `if (t == null) return m`).
- **CLI**: `shell.run("prog", listOf("arg"))` → `Result{exitCode, stdout}`;
  a missing program is `exitCode -1` (never a throw); `pipeline(listOf(
  listOf(...), listOf(...)))` composes. Idempotence check = `test -f` /
  `test ! -e` through the same interface.
- **REST**: `http.get(url)` returns the **body** as String (404 = empty
  body, NO throw; connection failure = throw on BOTH engines — honest
  parity); the live status is a separate probe `http.status(url)` →
  `200/404/...`; `http.put(url, body)`/`http.delete(url)` return the body.
  A REST provider therefore asks `status` for existence and `get` for props.

The CLI/REST goldens run JVM first and JS SECOND **against the same shared
world** (the disk directory / the HTTP server), proving the READ-based
idempotence crosses engines, and byte-compare the identical programs.

## The reconciliation loop: `reconcile`

`reconcile(design, provider, intervalMs)` (row 3.3) turns `apply` into a
continuous loop: every tick runs `apply(design, last, provider)` inside a
`spawn` (the same CONC003-JS-01 shape as workflow `schedule`) and returns the
`scheduler.every` job id — stop it with `scheduler.cancel(id)`. Ticks may
overlap and that is safe by construction: idempotence is the provider's READ,
the `last` state is only a hint. There is **no Native stub** here — `every`
is real on every target (CRON001 gates `at`/cron, never `every`). The golden
(`MakealiveReconcileE2ETest`) proves the loop converges the world by itself
and that after `cancel` the world stays dead across 4 intervals. Waiting for
a tick in a test follows the §132 rule: ONE long `time.sleep`, never short
polling inside `while (a && b)` (see `training/idioms/concurrency.md`).

```kf
var id = reconcile(design, provider, 5000)   // converge every 5s
// ...
scheduler.cancel(id)                          // the loop dies
```

## Clouds stay OUT of the compiler

Concrete AWS/Azure/GCP providers are **official packages** (`infra-<cloud>`,
R1): they are just more provider bodies over `kof.http`/`kof.shell` with a
signing API. Nothing in `kof.makealive` knows a cloud exists.

## Open items / honest gaps

- **§380** (fixed 20/09, codegen): the nested-`if`-throw false-label theft on
  JS is closed — both shapes work; the single-level-guard shape
  (`if (bad && refuse) return false; if (bad) throw …`) remains legal and is
  still what the goldens use.
- **§382** (open, bridge): kof.io JS numeric codes (above).
- Rows 3.3 (reconcile via `scheduler`), 3.6 (`kof.security` secrets —
  security lane), 3.8 (`kof infra` CLI — maintainer decision) are the rest
  of Stage 3; 3.2/3.7 are ⛔R4.
