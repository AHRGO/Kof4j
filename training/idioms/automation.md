[English](automation.md) | [Português](automation.pt_BR.md)

# Idioms — Automation (scripts, jobs, pipelines, CI/CD)

**Status:** available (`kof.workflow` 2.1.2 + bundle 2.1.3: retry/deadLetter/checkpoint/schedule) · **Introduced:** 0.4.0-beta (Sep 2026) · **Vision:** `docs/architecture/UNIVERSAL-PLATFORM-VISION.md` §4.3

## What it is

The anti-fragment of `Bash + Python + YAML + jq + sed`: a pipeline is **Kof
code** — typed, testable, reviewable — and the *runner* is tooling. There is
no "jobs YAML" in Kof, and there never will be (VISION §4.3, "What NOT to
do"). Build steps call `kof.process`; the DAG, retry, dead-letter,
checkpoint and schedule come from `kof.workflow`; the whole file compiles and
runs on the same toolchain as the rest of the product.

## BAD → GOOD

```bash
# ❌ NEVER — the fragmented stack, glued by exit codes nobody can type-check
build() { mvn package || exit 1; }
deploy() { curl -f ... ; }
on_failure() { jq -r .reason dead.json; }
```

```kof
// ✅ IDIOMATIC — the same pipeline as typed Kof code
import kof.workflow

main() {
    val compile = job("compile", () -> {
        val r = process.run("mvn", "-o", "-q", "package")
        return r.exitCode == 0
    })
    val test = job("test", () -> {
        val r = process.run("mvn", "-o", "-q", "test")
        return r.exitCode == 0
    }).after(compile)
    val ship = job("ship", () -> {
        return process.run("kof", "deploy", "dist").exitCode == 0
    }).after(test)
    val rep = dag(listOf(compile, test, ship)).run()
    println(rep.summary())
    if (!rep.allOk()) { throw "pipeline red: " + rep.summary() }
}
```

An uncaught `throw` exits the process **non-zero** — measured: green
pipeline exits 0, a red one exits 1 with `pipeline red: ...` naming the
failed jobs (`StdlibIdiomsCompileTest#greenPipelineExitsZeroRedPipelineExitsNonZero`).
That *is* the CI contract today: `kof run pipeline.kf` in any runner
(GitHub Actions, cron, a human terminal).

## The reliability faces (all pure Kof, all in the typed `Report`)

```kof
val d = dag(listOf(build, flaky))

// retry with a typed backoff ((attempt:Int)->ms): exponential(100, 2) or your own
d.retry(flaky, 3, exponential(100, 2))
d.retryFixed(build, 2)                       // no wait

// dead-letter: Report.dead ALWAYS holds "job: motivo" for every job that
// exhausted its retries; the durable sink is opt-in per job:
d.deadLetter(flaky, (nome: String, motivo: String) -> {
    println("dlq: " + nome + " -> " + motivo)
    return true
})

// checkpoint on kof.db/kof.orm (NEVER a new subsystem): finished jobs
// restore as succeeded and do not re-execute on the next run
val ck = checkpoint(d, "jdbc:h2:mem:ci;DB_CLOSE_DELAY=-1", "release")

// cron-style scheduling, delegating to scheduler.at; returns the job id
val id = schedule(ck, "0 3 * * *")
```

`Report` fields: `succeeded`, `failed`, `skipped` (cascade), `retries`
(`"job: tentativas=N"`), `dead` (`"job: motivo"`), `allOk()`, `summary()`.
A failed job cascades `skipped` to its transitive dependents; a cycle is
rejected by `run()` with an actionable message.

## Real semantics (verified — measured 19/09)

| Face | JVM | SCRIPT | JS | NATIVE |
|---|---|---|---|---|
| `job`/`dag`/`after`/`run`/`retry`/`deadLetter` | ✅ | `COMP003` (compile target; interpreter runs pure-Kof hosts separately) | ✅ | ✅ compiles (golden `WorkflowE2ETest`) |
| `schedule(dag, expr)` | ✅ (delegates `scheduler.at`) | — | ✅ | compiles; **`CRON001` at runtime** (honest stub — never silent) |
| `checkpoint(dag, db, name)` | ✅ (H2 via `kof.db`) | — | ✅ | compiles; **`ORM001` at runtime** (honest stub) |

Guards: `WorkflowE2ETest` (goldens JVM==JS byte parity), `WorkflowPrimitivesE2ETest`,
`StdlibIdiomsCompileTest#automationWorkflowFormsCompile` (every form above
compiles on the 4 targets; gates printed, not hidden).

## What NOT to do

- **Don't invent a jobs YAML/JSON** — the pipeline is the code; a config
  format would be a second, untyped compiler.
- **Don't reimplement shell** — call the real tools through `kof.process`
  (it has `run`/`spawn`/pipes/stdin; that's the interop-first rule R9).
- **Don't poll the `Report` for a boolean CI result by hand** — the throw →
  exit-code idiom is the contract; `allOk()` decides.
- **Don't reach for a queue system** when you have a DAG: failure cascade +
  `dead` list is the in-language answer (`kof.mq` is pub/sub, a different tool).

## Runner note (row 2.6 — open design)

`kof workflow run <file.kf>` as a *dedicated* command (nicer red output,
structured report flags) is queued in `IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
2.6; its command/exit contract is a maintainer decision (rule 6). Nothing
blocks CI today: `kof run pipeline.kf` already gives green=0 / red≠0.
