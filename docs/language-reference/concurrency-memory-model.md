[English](concurrency-memory-model.md) | [Português](concurrency-memory-model.pt_BR.md)

# Kof Concurrency Memory Model (SG-020)

> **State:** SPEC ADOPTED (maintainer decision 11, 09/09) — implemented
> and VALIDATED 10/09: the 5 HB edges of §2 have proof in
> `KofConcurrency2Test`/`SpawnE2ETest` (green in the suite). SG-020 CLOSED — the
> document left `docs/development/` (there is no pending work here).
> **Origin:** maintainer decision 11 on the spec gaps ("implement
> concurrent memory model with green threads").
> **Date:** 09/09/2026 · lane spec-gaps · moved to docs/ 11/09.

## 1. What is specified

Kof's concurrency model is **sequentially consistent (SC)** on all
targets. Every observation of a concurrent write by another flow of
execution obeys **total** happens-before — with no observable reordering
within the same flow.

Primitives (all from the language, never mechanism):

| Primitive | Semantics |
|---|---|
| `spawn f()` / `spawn { ... }` | creates a flow of execution (green thread); fire-and-forget when the value is discarded |
| `val h = spawn f()` → `await h` | Handle<T>; `await` **blocks** the caller until the return and establishes HB |
| `time.interval(ms, fn)` / `scheduler.every(ms) { }` | recurring job; cancellation by id |
| `Channel<T>` | FIFO; `send` blocks if full, `receive` blocks if empty; each value delivered to exactly 1 receiver |

## 2. Happens-before (normative rules)

1. **spawn:** everything the parent wrote before the `spawn` is visible to the
   child (HB = spawn edge).
2. **await:** everything the task wrote before returning is visible to the
   awaiter after the `await` (HB = await edge).
3. **Channel:** `send(v)` HB the `receive()` that delivers `v`.
4. **Cancellation** (`cancel(h)`/`h.cancelled()`): the cancellation is
   HB-observable by the target task at its next scheduling point; there
   is no synchronous preemption.
5. **Locals are private** to the flow; **statics and fields of shared
   objects** follow SC (atomic writes per field; no observable reordering
   tornadoes).
6. **Data race** (same variable without the edges above): the behavior is
   defined as **reading the most recent value in synchronization
   order** (SC) — never a "torn" value for one-word types
   (Int/Bool/Char/refs). `Long`/`Double` on 32-bit native targets do not
   exist (targets are 64-bit) — no word-tearing on any target.

## 3. Per-target mapping (how the spec is honored)

| Target | Flow (green thread) | Synchronization primitive | HB guaranteed by |
|---|---|---|---|
| **JVM** | virtual thread (`Thread.startVirtualThread`, 21+; platform on ART) | JMM | `CompletableFuture` (spawn/await), `LinkedBlockingQueue` (Channel) — both guarantee HB per JMM §17.4 |
| **Native x86_64** | `clone(220)` with shared heap | x86-TSO (strong hardware) | futex (`SYS_futex` 202) WAIT/WAKE on spawn/await/channel; lock `lock cmpxchg` |
| **Native riscv64/aarch64** | inherits x86 via translator | weak — needs an **explicit fence** | futex + `fence rw,rw` / `dmb ish` at the HB points (already emitted by the runtime: `amoswap` acq/rel, `fence`→`dmb ish` in the translator) |
| **Interp/Script** | virtual thread (same JVM runtime) | same as JVM | same as JVM |

**Parity rule (R5/R6):** any new target must prove the 5 HB edges
of §2 with the E2Es of §4 before leaving `experimental`.

## 4. Conformance proofs (DoD)

1. `spawnWriteBeforeAwaitIsVisible` — parent writes `x=41`, spawn reads and adds,
   await returns 42 (edge 1+2).
2. `channelSendHappensBeforeReceive` — send of a constructed value, receive
   observes the constructor's side effect (edge 3).
3. `staticsAreSequentiallyConsistent` — N spawns incrementing the SAME static
   N×1000 times; exact final result N×1000 (edge 5, atomicity per
   field).
4. `noWordTearingOnLong` — Long static written by 1 flow, read by another
   in a loop, never observes a partial value (edge 6).
5. Cancellation: `cancel` followed by `cancelled()` observable in the task
   (edge 4) — covered by the existing handle tests.

> **Current state of the proofs (validated 10/09):** (1)(2)(5) already covered by
> `SpawnE2ETest`/`KofConcurrency2Test` (channel JVM+Native, spawn/await
> cross-target). **(3) `staticsAreSequentiallyConsistent` (4×sum 0..999 =
> 1998000, HB of await) and (4) `noWordTearingOnLong` (reader never observes a
> partial value) IMPLEMENTED** — `KofConcurrency2Test:699/:737`, green in the
> suite (29/0/1-skip qemu). All 5 HB edges of §2 have proof.

> **⚠️ Amendment 11/09 — rule 5 still has no proof.** Review of
> `KofConcurrency2Test:699` (`staticsAreSequentiallyConsistent`): the program
> declares `Resultado.r1..r4` and **never uses them** — the four tasks call
> `soma1000()`, a pure function with no shared state, and the parent sums the values
> returned by the `await`s. There is **no concurrent write to a shared field**
> in the test. What it demonstrates is edge **2 (await)**, not rule
> **5 (SC for statics and object fields)**. The test's own comment already
> acknowledges the limit: *"read-modify-write data race is NOT atomic
> by the model's definition"*.
>
> `noWordTearingOnLong` (`:737`) **is** a real proof of rule 6 — there is a
> concurrent write to `Estado.v` with a read in a loop.
>
> **Gap that matters for `docs/development/planning-otp-supervision.md`
> (DD-OTP-08):** the **stop-flag** pattern — one flow writes `Bool = true`, another
> reads in a loop until it observes it — is not covered by any test. `Estado.pronto` is
> written in `noWordTearingOnLong` and never read. Since `volatile` is a non-goal
> (§5) and `BoxClassFactory.java:25` creates the field as a plain
> `AccessFlags.PUBLIC` (`ACC_VOLATILE` does not appear anywhere in the compiler),
> on the JVM the visibility of the flag depends entirely on rule 5 holding —
> precisely the one that lacks proof. Without it, `.stop()` may never be observed by a
> long-running worker: a failure that **passes** in a short test and **hangs** in production.
>
> **Pending (unblocks DD-OTP-08):** either an E2E of the stop-flag pattern
> (writer + reader in a loop, with a deadline that fails if the write is never
> observed), or rewriting rule 5 to what the proofs actually cover.

## 5. What is NOT specified (non-goals)

- Explicit `volatile`/`synchronized` on the language surface — Kof does not
  expose mechanism (rule 1: intention, not mechanism). If a program needs
  fine control, the correct abstraction is Channel (messages, not memory).
- Preemption with a quantum — green threads are cooperative at scheduling
  points (await/channel/time), with no guaranteed time slice.
- Relaxed/acquire-release model exposed to the user — SC-only is the spec;
  relaxing it would be a contract break (bump + discussion).
