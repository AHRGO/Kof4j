[English](observability.md) | [Português](observability.pt_BR.md)

# kof.observability — Health, Metrics e Request IDs (G5)

**Última atualização:** 17 de setembro de 2026
**Versão:** 0.4.0-beta (`VERSION` 0.4.0-beta)

> **Status:** DONE (JVM/Native/JS) — `KofObservabilityTest` 7/7 (VERSION 0.4.0-beta, free-list Native); API confirmada em `KofObservability.java`; histogramas/spans/export Prometheus fechados como `OBS002` (16/09)
> **Módulo:** `kof.observability` — `observability.*`
> **Targets:** JVM ✅ · Native x86_64 ✅ (free-list) · Native riscv64 ✅ · JS ✅ — sem gaps (G5 fechado 0.2.6-beta; OBS002 16/09)

---

## 1. Motivação

Observar produção exige três primitivas mínimas: **saber se o serviço está saudável** (health/readiness/liveness), **contar/medir o que acontece** (metrics) e **rastrear uma requisição fim-a-fim** (request/correlation IDs). O `kof.log` já cobre logging estruturado (JVM/Native); o `kof.observability` fecha o ciclo P0 ao expor essas três famílias nos três backends com a mesma API.

Princípio mantido: *intenção → Kof → stdlib → runtime/backend → plataforma* — sem framework externo, sem agente, sem sidecar obrigatório. Quando a plataforma precisa de Prometheus/OpenTelemetry, ela consome as primitivas do `kof.observability`.

---

## 2. API

| Chamada | Assinatura Kof | Retorno | Descrição |
|---------|----------------|---------|-----------|
| `observability.health()` | `() -> String` | `"UP"` | Health agregado — compatível com Spring Boot Actuator `/health` |
| `observability.readiness()` | `() -> Bool` | `true` | Pronto para receber tráfego |
| `observability.liveness()` | `() -> Bool` | `true` | Processo vivo (não precisa restart) |
| `observability.counter(name)` | `(String) -> Int` | novo valor | Incrementa contador nomeado em 1 |
| `observability.increment(name, delta)` | `(String, Int) -> Int` | novo valor | Incrementa contador em `delta` |
| `observability.gauge(name, value)` | `(String, Int) -> Void` | — | Define gauge nomeado |
| `observability.histogram(name, value)` | `(String, Int) -> Void` | — | Registra `value` no histograma nomeado (soma + contagem) |
| `observability.metrics()` | `() -> String` | texto Prometheus | Exporta counters/gauges/histogramas em formato texto Prometheus |
 | `observability.requestId()` | `() -> String` | UUID/hex | Gera ID de requisição (16 bytes aleatórios → 32 hex) |
 | `observability.correlationId()` | `() -> String` | UUID/hex | Alias de `requestId()` — para propagação entre serviços |
 | `observability.traceId()` | `() -> String` | 32 hex | ID de trace (W3C Trace Context) — 16 bytes aleatórios |
 | `observability.spanId()` | `() -> String` | 16 hex | ID de span (W3C Trace Context) — 8 bytes aleatórios |
 | `observability.spanStart(name)` | `(String) -> String` | handle | Inicia um span cronometrado W3C; retorna handle opaco (`traceId+spanId`) |
 | `observability.spanEnd(handle)` | `(String) -> String` | JSON | Encerra o span; retorna `{traceId,spanId,parentSpanId,name,startMicros,endMicros,durationMicros}` |

Todas as chamadas são **disponíveis nos três targets** (JVM/Native/JS) — `supportedOn` retorna `true` sempre; não há `OBS001` em uso normal. Histogramas, spans cronometrados e o export texto Prometheus estão implementados (`OBS002` fechado 16/09); qualquer gap futuro reportará `OBS00x`.

### Exemplo

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
    observability.histogram("http.latency", 12) // soma=12, contagem=1

    // request tracking
    val req = observability.requestId()      // "a3f1c9e2b4d64a8f9c0e1d2f3a4b5c6d"
    val corr = observability.correlationId() // outro ID, propagável em header
    println(req + " " + corr)

    // tracing (W3C Trace Context) — IDs puros, sem store, 3 targets
    val trace = observability.traceId() // 32 hex
    val span  = observability.spanId()  // 16 hex
    println(trace + "-" + span) // ex.: header traceparent: 00-<trace>-<span>-01

    // span cronometrado (OBS002) + export Prometheus
    val h = observability.spanStart("op")
    val json = observability.spanEnd(h)   // {"traceId":..,"durationMicros":..}
    println(json)
    println(observability.metrics())      // # TYPE .. / nome valor
}
```

---

## 3. Semântica por target

### JVM

- **Health/readiness/liveness:** constantes (`"UP"` / `true`) — prontas para customização futura (ex.: checar `kof.db`).
- **Metrics:** `ConcurrentHashMap<String, AtomicInteger>` para counters, `ConcurrentHashMap<String, Integer>` para gauges, `ConcurrentHashMap<String, long[]>` (`[soma, contagem]`, sincronizado) para histogramas — thread-safe, sem persistência (memória do processo, como Micrometer `simple`). `metrics()` renderiza em formato texto Prometheus (`name_count`/`name_sum`).
- **Spans:** `spanStart` guarda `System.nanoTime()` sob o handle `traceId+spanId`; `spanEnd` retorna o JSON do span com `durationMicros`.
- **Request IDs:** `UUID.randomUUID().toString()` (36 chars com hífens, variante 4).

### Native (asm x86-64, sem libc)

- **Health:** aloca `KofString` "UP" via `kof_string_from_literal` (`.Lstr_obs_up`).
- **Readiness/liveness:** `mov $1, %eax; ret`.
- **Metrics:** `.bss` com 32 slots (`512` bytes) para counters e gauges — cada slot `16` bytes (`ptr` + `int` + pad). Busca linear com comparação de conteúdo (`length` em `16(%rdi)` + bytes em `24(%rdi)`); `counter`/`increment` incrementam, `gauge` sobrescreve. Sem persistência; overflow silencioso após 32 nomes distintos (retorna `0`).
- **Histogramas/spans/metrics (`OBS002`):** slots `.bss` estendidos para histogramas (soma+contagem) e spans (nanos de início); `metrics()` monta o texto Prometheus em asm.
- **Request IDs:** tail-call para `kof_sec_random_hex(16)` — `getrandom(2)` → `32` hex chars (sem hífens, `318` syscall), mesma entropia do `kof.security`.

### JS (kof-runtime.mjs)

- **Health/readiness/liveness:** `"UP"` / `1`.
- **Metrics:** objetos `__kofObsCounters` / `__kofObsGauges` / `__kofObsHistograms` (`{sum,count}`) em closure — `counter`/`increment`/`gauge`/`histogram` manipulam o dicionário JS; `metrics()` renderiza texto Prometheus.
- **Spans:** `Map` `__kofObsSpans` com chave no handle — `spanStart` registra `Date.now()*1000`, `spanEnd` retorna o JSON do span.
- **Request IDs:** `crypto.randomUUID()` quando disponível, fallback `Math.random` com formato `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`.

---

## 4. Testes

`kof-compiler/src/test/java/dev/kof/compiler/KofObservabilityTest.java` — 7 testes (JVM/Native/JS), 7/7:

- `observabilityJvm` / `observabilityNative` / `observabilityJs` — health/readiness/liveness, counter sequencial, gauge, `histogram` + `metrics()` (Prometheus `_count`/`_sum`), `requestId`/`correlationId`.
- `tracingJvmNativeJs` — `traceId` (32 hex) / `spanId` (16 hex) W3C nos três targets.
- `spansWithTiming` — JSON de `spanStart`/`spanEnd` (`spanId`, `name`) nos três targets.
- `applicationLifecycle` / `applicationLifecycleEmptyBlocks` — hooks `application { onStart/onShutdown }`.

Todos os testes passam com `KOF_KEEP_ASM=1` preservando `Main.s` para inspeção.

---

## 5. Integração com o ecossistema

```
kof.config ──► kof.observability (config de logging/metrics)
kof.web ──► kof.observability (request IDs, metrics por rota)
kof.security ──► kof.observability (audit logging futuro)
kof.observability ──► kof.bench/profile (tooling já existente)
```

Próximos passos (fora do P0-G5): rota HTTP `/metrics` servindo `observability.metrics()`, **export** OpenTelemetry do JSON de span, e health customizável com checks de `kof.db`/`kof.mq`.

---

## 6. Definition of Done (G5)

- ✅ API idiomática (`observability.*`) + type safety (dispatch compile-time)
- ✅ Targets JVM/Native/JS (sem gaps, `supportedOn` = true)
- ✅ Testes `KofObservabilityTest` 7/7 + `KofSecurityTest` 25/25 + `KofValidationTest` 3/3 sem regressão
- ✅ Benchmark não aplicável (operações O(1) / syscall `getrandom`)
- ✅ Security review: `requestId` usa `SecureRandom` (JVM) / `getrandom` (Native) / `crypto.randomUUID` (JS) — sem vazamento
- ✅ Docs: este arquivo + `docs/bugs-and-gaps/ecosystem-coverage.md` §3.9/§4/§7 + `docs/stdlib/stdlib.md` §3
- ✅ Exemplo real: snippet acima roda nos três targets
