[English](observability.md) | [Português](observability.pt_BR.md)

# kof.observability — Health, Metrics and Request IDs (G5)

**Last updated:** September 12, 2026
**Version:** 0.4.0-beta (`VERSION` 0.4.0-beta)

> **Status:** DONE (JVM/Native/JS) — `KofObservabilityTest` 3/3 (0.2.6-beta, free-list Native); API confirmed in `KofObservability.java`
> **Module:** `kof.observability` — `observability.*`
> **Targets:** JVM ✅ · Native x86_64 ✅ (free-list) · Native riscv64 ✅ · JS ✅ — no gaps (G5 closed, 0.2.6-beta)

---

## 1. Motivation

Observing production requires three minimal primitives: **knowing whether the service is healthy** (health/readiness/liveness), **counting/measuring what happens** (metrics) and **tracing a request end-to-end** (request/correlation IDs). `kof.log` already covers structured logging (JVM/Native); `kof.observability` closes the P0 cycle by exposing these three families on the three backends with the same API.

Principle kept: *intent → Kof → stdlib → runtime/backend → platform* — no external framework, no agent, no mandatory sidecar. When the platform needs Prometheus/OpenTelemetry, it consumes the primitives of `kof.observability`.

---

## 2. API

| Call | Kof signature | Return | Description |
|---------|----------------|---------|-----------|
| `observability.health()` | `() -> String` | `"UP"` | Aggregate health — compatible with Spring Boot Actuator `/health` |
| `observability.readiness()` | `() -> Bool` | `true` | Ready to receive traffic |
| `observability.liveness()` | `() -> Bool` | `true` | Process alive (no restart needed) |
| `observability.counter(name)` | `(String) -> Int` | new value | Increments the named counter by 1 |
| `observability.increment(name, delta)` | `(String, Int) -> Int` | new value | Increments the counter by `delta` |
| `observability.gauge(name, value)` | `(String, Int) -> Void` | — | Sets the named gauge |
 | `observability.requestId()` | `() -> String` | UUID/hex | Generates a request ID (16 random bytes → 32 hex) |
 | `observability.correlationId()` | `() -> String` | UUID/hex | Alias of `requestId()` — for propagation between services |
 | `observability.traceId()` | `() -> String` | 32 hex | Trace ID (W3C Trace Context) — 16 random bytes |
 | `observability.spanId()` | `() -> String` | 16 hex | Span ID (W3C Trace Context) — 8 random bytes |

All calls are **available on the three targets** (JVM/Native/JS) — `supportedOn` always returns `true`; there is no `OBS001` in normal use. Future gaps (e.g., Prometheus export) will report `OBS00x`.

### Example

```kof
main() {
    // health
    assert(observability.health() == "UP")
    assert(observability.readiness())
    assert(observability.liveness())

    // metrics
    val c1 = observability.counter("http.requests")
    val c2 = observability.counter("http.requests") // 2
    val c3 = observability.increment("http.requests", 10) // 12
    observability.gauge("cpu.load", 42)

    // request tracking
    val req = observability.requestId()      // "a3f1c9e2b4d64a8f9c0e1d2f3a4b5c6d"
    val corr = observability.correlationId() // another ID, propagatable in a header
    println(req + " " + corr)

    // tracing (W3C Trace Context) — pure IDs, no store, 3 targets
    val trace = observability.traceId() // 32 hex
    val span  = observability.spanId()  // 16 hex
    println(trace + "-" + span) // e.g.: header traceparent: 00-<trace>-<span>-01
}
```

---

## 3. Semantics per target

### JVM

- **Health/readiness/liveness:** constants (`"UP"` / `true`) — ready for future customization (e.g., checking `kof.db`).
- **Metrics:** `ConcurrentHashMap<String, AtomicInteger>` for counters, `ConcurrentHashMap<String, Integer>` for gauges — thread-safe, no persistence (process memory, like Micrometer `simple`).
- **Request IDs:** `UUID.randomUUID().toString()` (36 chars with hyphens, variant 4).

### Native (asm x86-64, without libc)

- **Health:** allocates `KofString` "UP" via `kof_string_from_literal` (`.Lstr_obs_up`).
- **Readiness/liveness:** `mov $1, %eax; ret`.
- **Metrics:** `.bss` with 32 slots (`512` bytes) for counters and gauges — each slot `16` bytes (`ptr` + `int` + pad). Linear search with content comparison (`length` at `16(%rdi)` + bytes at `24(%rdi)`); `counter`/`increment` increment, `gauge` overwrites. No persistence; silent overflow after 32 distinct names (returns `0`).
- **Request IDs:** tail-call to `kof_sec_random_hex(16)` — `getrandom(2)` → `32` hex chars (without hyphens, `318` syscall), same entropy as `kof.security`.

### JS (kof-runtime.mjs)

- **Health/readiness/liveness:** `"UP"` / `1`.
- **Metrics:** objects `__kofObsCounters` / `__kofObsGauges` in a closure — `counter`/`increment`/`gauge` manipulate the JS dictionary.
- **Request IDs:** `crypto.randomUUID()` when available, fallback `Math.random` with the format `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`.

---

## 4. Tests

`kof-compiler/src/test/java/dev/kof/compiler/KofObservabilityTest.java` — 3 tests (JVM/Native/JS):

- `observabilityJvm` — health/readiness/liveness, sequential counter (`1→2→5`), gauge, `requestId`/`correlationId` non-empty and distinct.
- `observabilityNative` — same scenario in assembly (checks `health() == "UP"` and increment `1→2→7`).
- `observabilityJs` — health `"UP"`, readiness/liveness `true`, counter `1→2→12`, requestIds with `length > 0`.

All tests pass with `KOF_KEEP_ASM=1` preserving `Main.s` for inspection.

---

## 5. Integration with the ecosystem

```
kof.config ──► kof.observability (logging/metrics config)
kof.web ──► kof.observability (request IDs, per-route metrics)
kof.security ──► kof.observability (future audit logging)
kof.observability ──► kof.bench/profile (existing tooling)
```

Next steps (outside P0-G5): Prometheus export (`/metrics`), `tracing`/`OpenTelemetry` (spans), `kof.observability.metrics()` JSON dump, customizable health with `kof.db`/`kof.mq` checks.

---

## 6. Definition of Done (G5)

- ✅ Idiomatic API (`observability.*`) + type safety (compile-time dispatch)
- ✅ JVM/Native/JS targets (no gaps, `supportedOn` = true)
- ✅ Tests `KofObservabilityTest` 3/3 + `KofSecurityTest` 25/25 + `KofValidationTest` 3/3 without regression
- ✅ Benchmark not applicable (O(1) operations / `getrandom` syscall)
- ✅ Security review: `requestId` uses `SecureRandom` (JVM) / `getrandom` (Native) / `crypto.randomUUID` (JS) — no leakage
- ✅ Docs: this file + `docs/bugs-and-gaps/ecosystem-coverage.md` §3.9/§4/§7 + `docs/stdlib/stdlib.md` §3
- ✅ Real example: snippet above runs on the three targets
