[English](concurrency.md) | [Português](concurrency.pt_BR.md)

# Idioms — Concurrency

**Status:** available (3 targets) · **Introduced:** 0.0.5-alpha · **Updated:**  0.4.0-beta (Sep 2026) (31/08: CONC001 closed; 15/09: CONC001 cross helpers) · **JS:** event-loop (CONC003 closed 03/09)

## What it is

`spawn` runs a task concurrently without exposing threads:

```kof
void processar(Int id) {
    println("processando " + id)
}
main() {
    spawn processar(1)
    spawn processar(2)
    spawn {
        println("background")
    }
    println("fim")
}

// With result (0.3.22-beta)
main() {
    val r = spawn trabalho()   // typed Handle<T>
    var v = await r            // blocks; T with primitive unboxing
    println(v)
}

// Lambda literal with return + Handle (0.3.22-beta)
main() {
    var n = 21
    var h = spawn { return n * 2 }   // Handle<Int>
    println(await h)                 // 42
}
```

## Real semantics (verified — 0.3.22-beta)

- the task runs in parallel: JVM virtual threads; **Native `pthread_create` + trampoline + `pthread_join` (CONC001 closed 31/08)**; JS event-loop (statement and expression covered; real async = CONC003 closed 03/09);
- the program **waits for the tasks before exiting** (implicit join: `kof_spawn_join_all` at the end of main on Native);
- `val r = spawn f()` returns a typed `Handle<T>`; `await r` with unboxing;
- `var h = spawn { return expr }` (lambda literal with `return` + Handle) works on JVM/JS/interpreter — **gap: Native x86_64 → SIGSEGV (bug 46, known-bugs.md)**; use `spawn fn(arg)` (named function) as a workaround on Native until the fix;
- an exception in the task does not bring down the program;
- **KofScript** top-level `let` also supports spawn/await via KofScriptGlobals.

## When to use

- independent work that can run in parallel (queue processing,
  I/O, notifications);
- background tasks;
- when the result is needed — use `val r = spawn f(); await r`.

## When not to use

- when order matters and there is no synchronization.
- JS for real CPU parallelism (single-thread event-loop — concurrency is real,
  parallelism is not; CONC003 closed 03/09).

## BAD — exposing the platform

```kof
// DOES NOT EXIST — there is no Thread/Executor in the language
var t = new Thread(() -> work())
t.start()
```

## GOOD

```kof
spawn work()
val r = spawn compute()
var v = await r
```

## GOOD — kof.time interval as a scheduler

```kof
// periodic: interval/cancel JVM only (TIME001 on Native/JS)
var id = time.interval(1000, () -> println("tick"))
```

For scheduled `every`/`at`, `kof.scheduler` exists on JVM/JS
(`Native SCHED001`): `scheduler.every(100) { ... }`, `scheduler.at("0 3 * * *") { ... }`, `scheduler.cancel(id)`.

## WHY

`spawn` expresses intent. Thread/Runnable/Executor are platform
mechanisms — the decision of how to execute belongs to the runtime.

## Honest limitations (0.3.22-beta)

- ~~Native: CONC001~~ — ✅ closed 31/08 (pthread_create + trampoline + await/pthread_join + futex thread-safe allocator + implicit join);
- JS: ~~sequential~~ → real event-loop async — `spawn`/`await` cover statement
  and expression (CONC003 closed 03/09); known limitations: `cancelled()`
  always `0` (no thread-local for the current task) and only task-lambdas
  become `async function` (CONC003-JS-01);
- producer/consumer queues: `kof.mq` — 3 targets (Native 01/09, MQ001 closed; pub/sub + `mq.queue()`/`push`/`pop`);
- lambdas with capture work in spawn (BoxN).

## GOOD — kof.supervisor: supervised restart (OTP, issue #83)

```kof
// A worker failure does NOT kill the system: the supervisor observes, restarts with
// a NEW factory, respects the limit, and escalates when it overflows.
import kof.supervisor

class Conecta implements KofWorkerFactory {
    KofWorker novo() { return WorkerConexao() }   // new object per restart
}
class WorkerConexao implements KofWorker {
    Object run() {
        // throws (exception is String) → the supervisor captures the failure
        throw "conexao caiu"
    }
}
main() {
    var s = supervisor("net")
        .child("conn", Conecta(), "permanent")   // permanent: falls → restarts
        .restartLimit(5)
    s.start()
    // ... s.stop(2000) to shut down in a controlled way; s.stats() observes ...
}
```

The factory (`KofWorkerFactory.novo()`) returns a **new** `KofWorker` on each
restart — the object that failed is not restarted, it is re-fabricated (state
isolation). The three policies: `permanent` (falls → always restarts),
`transient` (ends normally → stops; only restarts if it fails), `temporary`
(never restarts — counts as discarded). `escalate(cb)` calls
`disparou(id, motivo, reinicios)` at the limit (without `escalate` the
supervisor **stops restarting and warns** — never silent).

Honest parity: **JVM + Script** (interpreter) deliver the core. NATIVE =
`OTP001` (the `throw` in a task on the current native backend falls into the
global handler chain — §129), JS = `OTP002` (single-thread event-loop does not
schedule task-of-task — §132). In both, `import kof.supervisor` fails at
compile-time with a clear diagnostic, never a binary that hangs.

## WHY

Supervision is **intent**, not mechanism: the user declares *what* to watch
(factory + policy + limit), not *how* to reapply threads. The platform
(`spawn`/`await`/`try-catch`) already exists; the supervisor is Kof code on
top.

## GOOD — async fetch: `var h = spawn http.get(url); await h`

```kof
// ❌ BAD — "parallel" with threads/futures from another language, or synchronous on JS
val a = http.get(urlA)            // blocks the whole thread until it responds
val b = http.get(urlB)            // sequential: adds up the latencies

// ✅ GOOD — the language already has Handle: spawn gives concurrency, await gets the value
var ha = spawn http.get(urlA)
var hb = spawn http.get(urlB)
val a = await ha                  // fires before waiting; latency = max(a,b)
val b = await hb

// ✅ GOOD — "any one first"
val first = await selectAny(spawn http.get(a), spawn http.get(b))
```

On JVM/Script/Native the worker thread does the I/O; on **Node/browser**
`http.*` is real `fetch` — the `Handle` carries the Promise, and the `await`
resolves the body (`spawn`+`await` is the ONLY path that carries in pure JS;
a synchronous call there returns the raw Promise — §133). Never
`Thread`/`Future`/`async`/`await` from another language: `spawn`/`await` cover
all three.

## Related anti-patterns

- `fake-idioms.md` — `async`/`await`/Thread do not exist (use `spawn`/`await`)
