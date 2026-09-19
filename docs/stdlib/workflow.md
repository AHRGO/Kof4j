[English](workflow.md) | [Português](workflow.pt_BR.md)

# Workflows — `kof.workflow`

**Date:** September 19, 2026
**Status:** MVP implemented (universal plan Stage 2, row 2.1, slice 2.1.2) — `VERSION` 0.4.0-beta

> **MVP scope (Q2, maintainer poll 19/09):** `job` / `dag` / `after` / `run` / `Report`.
> **2.1.3 face 1 LANDED 19/09:** `retry` (Q3 — the workflow's own additive helper;
> `kof.http` is NOT touched, its migration is a separate signed slice). `checkpoint`,
> `deadLetter` and `schedule` still ship with the rest of the 2.1.3 bundle.

---

## 1. What it is

`kof.workflow` is a **composition layer over plain Kof**, written in Kof itself
(resource `dev/kof/workflow-host.kf`) and injected **flat** by the compiler when you
`import kof.workflow` — the same mechanism and the same DD-OTP-01 option A as
`kof.supervisor`. Because the host is a virtual package, the surface is bare
`job(...)`/`dag(...)`; there is **no `workflow.` prefix and no `workflow` identifier**
after the import.

The MVP executes a DAG **sequentially and deterministically** (a fixpoint loop). It
crosses no runtime boundary — no threads, no `await`, no `process` — so there is **no
target gate**: the same source runs byte-identical on JVM and JS and compiles on
Native. Honest gaps (`PROC001`, `CRON001`, `ORM001`) appear only in the job *bodies*
you write, never in the layer.

## 2. Surface

```
import kof.workflow

job(String nome, () -> Bool corpo) -> KofWfJob
KofWfJob.after(KofWfJob dep) -> KofWfJob        // chainable; guards null/self-dep
dag(List<KofWfJob> jobs) -> KofWfDag            // guards empty dag / dup names / null job
KofWfDag.run() -> KofWfReport                   // sequential topological fixpoint
KofWfDag.retry(KofWfJob j, Int times, (Int) -> Int backoffMs) -> KofWfDag
KofWfDag.retryFixed(KofWfJob j, Int times) -> KofWfDag   // immediate, no sleep
exponential(Int baseMs, Int factor) -> (Int) -> Int       // 19/09: backoff(1)=base, *factor each try

Report fields: succeeded failed skipped errors retries  // List<String> each
Report.allOk() -> Bool                          // no failures, no skips
Report.summary() -> String                      // "ok=... failed=... skipped=..."
```

Rules:

- A body returns `Bool` (`true` = success). `false` **or a thrown string** fails the
  job; the thrown reason is recorded in `errors` as `"nome: motivo"` (a `false` is
  recorded as `"nome: false"`).
- A failed or skipped job **poisons its transitive dependents** — they are `skipped`
  and never run. Independent branches keep running.
- A **cycle is rejected at run time** with `workflow: ciclo detectado entre: ...`
  (an actionable message — never a silent hang, per the plan §2 invariant).
- `retry(job, times, backoff)`: the body re-runs after `sleep(backoff(attempt))` up to
  `times` extra attempts (throw and `false` both retry); `Report.retries` records
  `"nome: tentativas=N"`, and `errors` keeps the LAST reason if it still fails.
  `retryFixed` is the same with zero wait. Only jobs that are members of the dag can
  be configured (guard message says so).

## 3. Idiom

```
import kof.workflow

main() {
    var acc = listOf("start")
    var build = job("build", () -> { acc.add("built"); return true })
    var image = job("image", () -> { acc.add("imaged"); return true }).after(build)
    var ship  = job("ship",  () -> true).after(image)
    var lint  = job("lint",  () -> true)

    var rep = dag(listOf(ship, image, build, lint)).run()   // order = deps, not input
    println(rep.summary())   // ok=build,lint,image,ship failed= skipped=
    println(rep.allOk())     // true
    println(acc.get(1))      // built — closure state proves real sequencing
}
```

A job that shells out uses the `kof.process` Result (its own gap table applies):

```
var compile = job("compile", () -> process.run("make", listOf("-j4")).exitCode == 0)
```

`listOf(...)` everywhere — Kof has no `[]` list literal and no `{}` map literal
(measured; pinned negative by `WorkflowPrimitivesE2ETest`).

## 4. Failure semantics at a glance

| body outcome | Report | dependents |
|---|---|---|
| body `true` | `succeeded` | run when all deps succeeded |
| `false` (after retries exhausted) | `failed` + `errors` `"nome: false"` | `skipped` (transitively) |
| `throw "why"` (after retries) | `failed` + `errors` `"nome: why"` | `skipped` (transitively) |
| cycle in deps | `run()` throws `workflow: ciclo detectado entre: ...` | — |
| retried job | `retries` `"nome: tentativas=N"` | — |

Construction guards (`dag`/`job`/`after` throw immediately): empty name, null body,
null dependency, self-dependency, duplicate names in one dag, empty dag.

## 5. Known Kof edges (hit while building the host)

- A field **without an initializer** directly above a lambda-typed field breaks the
  parser's class-body lookahead — initialize (`String nome = null`). Grammar is
  rule-6 territory; worked around in the host.
- A block lambda whose **only exit is `throw`** types as `Void` — keep a trailing
  `return` so the body types as `() -> Bool`.
- A function-typed local **inferred with `var`** from a field loses its type —
  annotate: `var f: () -> Bool = job.corpo`.

## 6. Proof

`WorkflowE2ETest` 8/8 (exact stdout goldens, JVM==JS byte-parity): linear order,
failure cascade, throw-with-reason, cycle message, guard set, real bodies through a
captured list, retry (recover-on-3rd + exhaust-with-reason + exponential), Native
compile. The shape layer is pinned by
`WorkflowPrimitivesE2ETest` (6/6, incl. the negative syntax pins). Plan:
`docs/development/workflow-plan.md` §5.
