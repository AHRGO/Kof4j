[English](roadmap.md) | [Português](roadmap.pt_BR.md)

# Kof — Roadmap de Longo Prazo

**Última atualização:** 15 de setembro de 2026 (§23 ganha 2.6 = fila
D-NULL-INTENT N1→N4 [lane compiler, decisão da mantenedora 15/09]; TIER 3–5
marcado DESPRIORIZADO pela mantenedora 15/09 — trio de volta a `future/`).
(antes: fusão de planos: §23 = plano
de implementação ÚNICO (ex-`ACTION_PLAN`+`IMPLEMENTATION_PLAN`); cluster de
migração consolidado — `LEGACY_IR`+`DIFFERENTIAL_TESTING` fundidos em
`LEGACY_MIGRATION.md`)
**Versão:** 0.4.0-beta (branch ativa `beta-0.4.0`)

---

## Filosofia

Kof deve simplificar radicalmente o desenvolvimento moderno sem sacrificar poder, performance, segurança ou interoperabilidade.

Princípios:

- abstrair complexidade recorrente;
- manter o código extremamente curto e legível;
- oferecer APIs nativas da linguagem/runtime;
- manter compatibilidade com o ecossistema Java existente;
- evitar reinventar bibliotecas Java apenas por estética;
- permitir que Kof ofereça uma experiência moderna sem obrigar o usuário a depender de frameworks externos;
- colocar complexidade na implementação/runtime/compiler, e não no código da aplicação;
- preservar liberdade arquitetural;
- permitir monólitos, modularização e posteriormente microserviços sem reescrever a aplicação inteira.

---

## 1. Targets da Plataforma

### KofAndroid — Android

Kof compilado para aplicativos Android (APK/AAB). Design completo em
[docs/targets/KOFANDROID.md](../targets/KOFANDROID.md).

Objetivos:
- mesmo código, mesma intenção: `Window(...)` abre um app de verdade;
- reuso do backend JVM (bytecode → dex) — não há codegen alternativo;
- `kof.ui` via WebView host sintetizado (mesma camada KofJS do desktop);
- interop direta com `android.*` via ExternalClasspath
  (`extends Activity`, `super.onCreate`, annotations androidx);
- gaps honestos por compile-time (`AND001..003`).

Estado atual: 🟡 Fases 1-4 implementadas — `kof build --target android` gera
projeto Maven (zero Java/Kotlin/Gradle) com host Activity EM KOF
(`android-host.kf`) compilada pelo próprio frontend; pipeline
d8/aapt2/apksigner via pom sem dependências. Fase 2: o manifest carrega
label/permissões; `--apk`/`--keystore` constroem o artefato direto. Fase 3:
WebView responsivo (`<meta viewport>` device-width + CSS de tela estreita +
`setUseWideViewPort`/`setLoadWithOverviewMode` no host). Fase 4:
`--min-sdk`/`--target-sdk` chegam ao `<uses-sdk>`, ao platform jar e ao
`d8 --min-api` (defaults 24/34). `kof.web` é gap de compile-time imposto
(`AND002`). Pendente (Fases 5+, sem dono): `--aab` (precisa de `bundletool`,
recusado honestamente hoje), metadado de ícone declarativo (decisão pendente).
Detalhes em [docs/targets/KOFANDROID.md](../targets/KOFANDROID.md).

### Kof4J — JVM

Kof compilado para JVM/bytecode.

Objetivos:
- máxima compatibilidade com Java;
- acesso a bibliotecas Java;
- compatibilidade com Maven/ecossistema existente;
- execução como JAR;
- possibilidade de utilizar frameworks legados como Spring, Hibernate etc.;
- backend principal durante a consolidação inicial.

Estado atual: ✅ estável (JVM V21, ASM, virtual threads, 819 testes 02/09;
web stack nativa com WebSocket/SSE, limites/contadores e `kof.http`
retry/circuit — 30/08-04/09)

### KofNative — Binário Nativo

Kof compilado diretamente para código nativo/binário.

Objetivos:
- ELF/PE/Mach-O conforme plataforma;
- baixo consumo;
- startup extremamente rápido;
- possibilidade de servidores sem JVM;
- runtime Kof nativo;
- reutilização da mesma semântica da linguagem;
- mesma aplicação podendo ser compilada para JVM ou Native.

Estado atual: ✅ estável x86_64 (free-list `kof_free_head` com reuso mmap; GC
mark-sweep pendente — auto-GC desativado após hang, memória devolvida só no
`munmap` fallback; `spawn`/`await` via `pthread_create` + trampoline +
`pthread_join` com allocator thread-safe (futex) — 31/08; FP real em XMM —
FLT001; JSON objetos/records + arrays FP — JSN001/002/003; SQLite nativo `.so`
direto; MySQL wire protocol WIP) + `native.risc` (riscv64: codegen real 02/09 — asm puro + qemu, NATIVE002
parcial) + `native.arm` (aarch64: herda do riscv via tradutor — `NativeArchEmitter.emitAarch64`, 39/39 E2E sob qemu) *(sincronizado 12/09: a linha "codegen ainda placeholder" apodreceu — `NativeAarch64E2ETest` executa sob qemu onde há toolchain; guard honesto pula em host sem cross)*

### KofJS — Web

Kof executando no lado servidor/compilando para aplicações web.

KofJS NÃO deve ser tratado simplesmente como "Kof que vira JavaScript".

A visão é gerar frontend moderno de forma declarativa e minimalista:

```kof
page Home {
    column {
        text("Olá")
        button("Entrar") {
            login()
        }
    }
}
```

A intenção é semelhante à filosofia do Flutter:
- UI declarativa;
- componentes;
- composição;
- estado;
- eventos;
- layouts;
- pouca verbosidade;
- geração otimizada de HTML/CSS/JS.

Estado atual: 🟡 alpha — pipeline `.kf → Kof IR → KofJS → .mjs` funcional com
execução na engine JS embarcada do próprio Kof (sem Node.js). Classes,
herança, List `map/filter/reduce`, String API, JSON, exceções, pattern matching
`case String s` + `Point(x,y)`, `String?` básica, `kof.time`/`kof.io`/`kof.http` (via `Java HttpClient` interop + fetch fallback; retry/circuit em paridade com o JVM — 30/08; scheduler via `setInterval` — 27/08; `spawn`/`await`/`channel<T>()` com concorrência real via async/await/Promise — CONC003 fechado 03/09) e `kof run
--target=js` funcionam. A plataforma web (HTML/CSS/JS, browser) é a próxima
fase. Ver: `docs/targets/KOFJS.md`.

### KofScript — Execução Direta

Runtime para executar código Kof diretamente.

Comando planejado:

```
kof run arquivo.kf
```

A implementação interna poderá evoluir para interpretação, compilação incremental, JIT ou execução híbrida, mas a decisão será tomada posteriormente com base em benchmarks.

Estado atual: ✅ implementado: `kof script app.kf [--watch]` + `kof repl` (statements de topo → `main()`, `var`/`val` de topo → `KofScriptGlobals`; Windows SIGPIPE fix). **0.3.0-beta: execução direta por interpretação** — `KofInterpreter` roda a MESMA IR otimizada do frontend (sem emitir bytecode, sem fork de JVM; paridade por construção com o backend JVM, provada em teste). `KofCcompiler` (`kof c`) compila C subset → ELF x86_64 nativo (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`).

---

## 2. Princípio Multi-Target

A linguagem deve possuir uma semântica única:

```
Source
  ↓
Lexer
  ↓
Parser
  ↓
AST
  ↓
Type System
  ↓
Symbol Resolution
  ↓
Semantic Model
  ↓
Kof IR
  ├── Kof4J Backend
  ├── KofNative Backend
  ├── KofJS Backend
  └── KofScript Runtime
```

A Kof IR deve permanecer independente de JVM, ASM, JavaScript ou código nativo.

Backends são responsáveis por transformar a representação semântica em sua plataforma.

Estado atual: ✅ arquitetura definida e parcialmente implementada

---

## 3. Kof como Plataforma de Backend

A visão de longo prazo é permitir construir backends modernos sem Spring.

Não reimplementar Spring. Em vez disso, transformar capacidades recorrentes em primitivas do Kof Runtime.

Objetivos futuros:
- HTTP / REST / WebSocket / SSE (WebSocket/SSE + hardening JVM concluídos 04/09; JS/Native follow-up);
- HTTP client;
- JSON;
- RPC;
- eventos / filas / pub/sub;
- concorrência / async;
- cache;
- configuração;
- observabilidade / logging / métricas / tracing;
- health checks / graceful shutdown;
- validation / serialization / scheduling.

Exemplo conceitual:

```kof
api "/users" {
    get "/{id}" {
        return User.find(id)
    }
    post "/" {
        return User.create(input())
    }
}
```

Estado atual: 🟡 parcial — HTTP/rotas (`kof.web` + TLS `listenSecure` +
**WebSocket `app.ws`** + **SSE `sse.*`** — 30/08, JVM; hardening
`app.configure`/`app.stats` — 04/09), JSON (completo nos 3
targets, 31/08), configuração (`kof.config` asm Native), logging (`kof.log`
asm Native), segurança (`kof.security` + G9), **cache (`kof.cache`, 3
targets — 30/08)**, **`kof.http` retry/circuit breaker (JVM+JS — 30/08)**,
concorrência (`spawn` + `await`/`Handle<T>` — JVM virtual threads, Native
pthread 31/08, JS sequencial), `List map/filter/reduce`, `Box<T>`, pattern
matching e `String?` implementados (0.2.6-beta).
Faltam (sincronizado 12/09 contra `backend-parity.md` — a lista abaixo era a
de 31/08; HTTP002/MySQL/cross-codegen FECHARAM desde então):
~~HTTP client no Native (HTTP002)~~ ✅ fechado (Native HTTP/1.1 asm —
`backend-parity.md` §kof.http; https→throw declarado), RPC (gRPC — ver
abaixo), tracing (OpenTelemetry), web residual no Native/JS
(~~WEB002/WEB001~~ **base real nos dois**: server Native `KofWebNativeE2ETest`
4/4 + GraalJS HttpServer `bc577aa`; residual TLS/ws/sse/path-params),
~~MySQL nativo completo~~ ✅ wire protocol + prepared statements binários 03/09
(`KofDbE2E` `nativeMysqlWireProtocol`/`nativeMysqlPreparedBinary`),
~~RISC/ARM codegen~~ ✅ core completo (riscv64 real 02/09; aarch64 herda via
tradutor; 39+39 E2E sob qemu — faces de paridade avançada = NATIVE002),
GC mark-sweep (G-0 riscv ✅ `356f33b9`; decomposição G-1..G-5 em
`native-multiarch.md`).
(kof.mq pub/sub + queue = 3 targets — MQ001 fechado 01/09)
Ver `docs/development/DECISIONS.md` §D-SPRING (Fases 5-14).

**gRPC no `kof.web` (novo, 31/08 — planejado)**: comunicação gRPC como
primeira classe na plataforma web — `app.grpc { service ... }` com stubs
gerados a partir de `.proto`, server streaming + unary sobre HTTP/2 no JVM
(`io.grpc` via `kof.web`), e client `grpc.call(endpoint, method, msg)`.
Escopo: Fase web (mesma família de `app.ws`/`sse.*`); codegen `.proto` → IR
Kof; parity JVM primeiro, Native/JS depois.

### Concorrência — fila residual (atualizado 13/09 — era "0.2.6-beta, 31/08")

Estado 13/09: concorrência real **JVM** (virtual threads) + **Native**
(pthread, CONC001 fechado 31/08: spawn/await + `done`/`poll`/`cancel`/
`cancelled`/`selectAny` — cancel cooperativo por TID, selectAny por polling
1ms) + **JS** ✅ 03/09 (CONC003 fechado — stmt/expr/cancel/selectAny com
async/await/Promise reais) + **supervisão OTP** (`kof.supervisor`: 1ª fatia
11/09 núcleo JVM+Script, **S2-JVM 13/09** `startAll`/`lacoUnico` — ver
`planning-otp-supervision.md`; **Native x86 ✅ 15/09** — §129 fechado via
DECISIONS §2 opção B, então `kof.supervisor` roda no Native x86; riscv/aarch=OTP001
(gate honesto da era §132). **JS ✅ 18/09 — §132 resolvido:** `time.sleep` virou um
ponto de await async cooperativo (o compilador colore async o método que o alcança,
`kofTimeSleep` devolve Promise, a bomba do host `KofJsRunner` a drena), então um worker
spawnado de dentro de outra task dispara e `OTP002` foi levantado — `kof.supervisor`
agora roda em JS com paridade. O SIGSEGV anterior de `spawn→await→spawn` (pilha
desalinhada no site do `pthread_create`) foi corrigido 01/09 com `andq $-16` em
`kof_spawn_handle_new`. Um defeito latente relacionado apareceu e foi corrigido
15/09 na mesma unidade do §129: `kof_await` não limpava o TID do handle após o
join, então o `kof_spawn_join_all` implícito no fim da `main` **dava double join**
em todo handle já awaited — SIGSEGV em `__pthread_clockjoin_ex` assim que o TCB era
reciclado (reproduzido no HEAD com 50 spawns + 50 awaits, 3/3 crash; limpo após o
fix).

| Item | Descrição | Prioridade |
|------|-----------|------------|
| ~~Unwrap de `ExecutionException`~~ | ✅ 31/08 — `kof_await` re-lança a causa original (JVM) | — |
| ~~`await` com timeout~~ | ✅ 31/08 — `awaitTimeout(r, ms)`: valor no prazo, exceção capturável via `try/catch` no estouro (JVM `Future.get(ms)` + Native polling 1ms com deadline; JS deadline-poll `kofAwaitTimeout` — CONC003) | — |
| ~~Cancelamento~~ | ✅ 31/08 — `cancel(r)`/`cancelled()` cooperativo via flag no handle (JVM + Native por TID) | — |
| ~~Espera múltipla~~ | ✅ 31/08 — `selectAny(h1, h2, ...)` → primeiro handle pronto (JVM + Native + JS) | — |
| ~~`done`/`poll`~~ | ✅ 31/08 — não-bloqueantes sobre o handle (JVM + Native) | — |
| ~~Port Native~~ | ✅ 31/08 — `pthread_create` + trampoline + `pthread_join` + allocator thread-safe (futex); join implícito (CONC001 fechado) | — |
| ~~Port JS~~ | ✅ 03/09 — spawn sobre Promise, await nativo via microtask (CONC003 fechado) | — |
| ~~Scheduler~~ | ✅ 31/08 — `every`/`cancel` JVM (`ScheduledExecutor`) + JS (`setInterval`) + **Native SCHED001** (thread por job, `usleep` ms→us + flag `active`, `cancel(id)` cooperativo); `at(cron)` real 17/09 (parser de 5 campos em UTC, JVM + JS) | `at(cron)` no Native → **CRON001** (recusa em compile-time — sem parser em asm) |
| ~~Canais tipados~~ | ✅ 31/08, bloqueio real no JS 03/09 — `channel<Int>()` com `send`/`receive` (JVM `LinkedBlockingQueue` bloqueante + Native FIFO futex + JS fila de resolvers pendentes) | — |

Critério de "100%": os três targets executando os mesmos programas
concorrentes com golden diff vazio (mesmo padrão da métrica 1 do plano).

### Linguagem — fila residual (P1/P2, atualizado 13/09)

| Item | Status | Plano |
|------|--------|-------|
| pattern matching | ✅ 0.2.6-beta — `switch (x) { case String s: ... }` + `case Point(x,y)` em JVM/Native/JS | guards e destructuring aninhado pendentes |
| null safety | ✅ 0.2.6-beta — `String?` básica com `?`-check em compile-time; **sem Option no core** | checks avançados pendentes |
| higher-order em coleções | ✅ 0.2.6-beta — `List map/filter/reduce` em JVM/Native/JS | `Map/Set` já ✅ 0.1.0 |
| módulos multi-arquivo | ✅ 0.2.6-beta — `import a.b.C` file handling fix (`CompilerDriver.java:243`) para projetos grandes (`a/b/C.kf`) | semântica unificada de visibilidade/import residual |

---

## 4. Segurança Nativa

Camada de segurança própria do Kof, inspirada em necessidades resolvidas por Spring Security, mas NÃO como cópia.

Objetivos:
- authentication / authorization;
- JWT / OAuth/OIDC;
- sessions / roles / permissions;
- security policies / CSRF / CORS;
- secure headers / rate limiting;
- input validation / password hashing;
- audit logging / API security.

A filosofia deve ser declarativa e segura por padrão:

```kof
security {
    auth jwt
    route "/admin" requires role("admin")
    route "/users" requires auth
    rate "/login" 10/minute
}
```

Estado atual: ✅ implementado (v1, docs/stdlib/security.md)

**Implementado (0.2.6-beta, inclui 0.0.5):**
- `kof.security` com API idiomática: `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` + G9 (`rateLimit`, `sessionCreate`, `apiKeyGenerate`).
- Password hashing PBKDF2-HMAC-SHA256 (600k iterações, salt, constant-time,
  formato versionado).
- Crypto: SHA-256/512, HMAC, AES-GCM, random seguro — JVM, Native (asm, `kof_db_mysql_scramble` para MySQL) e JS.
- JWT HS256 (alg fixo — sem confusão de algoritmo), exp/iss/aud.
- Secrets por env + redação para logs; comparison constant-time; free-list Native.
- Web auth middleware (`auth.authenticated()`, `auth.hasRole(...)`).
- Gaps de target com diagnóstico claro (SECN001/002/003/004, HTTP002).

**Pendente:**
- OAuth2/OIDC client (arquitetura preparada em docs/stdlib/security.md §2.3);
- audit logging;
- integração com database (planejado).

*(sessions, rate limiting e API keys fechadas em G9 — 3 targets;
JWT/passwords/SHA-512/AES-GCM no Native fechados em asm — G10.)*

---

## 5. Data / ORM / Hibernate

A visão não é substituir Hibernate à força. Kof deve manter Java interoperability e permitir `import org.hibernate.Session`.

Mas deve existir futuramente uma camada de dados nativa:

- SQL / NoSQL / transactions / connection pools;
- migrations / repositories / query APIs;
- PostgreSQL / MySQL / SQLite / MongoDB.

Experiência conceitual:

```kof
entity User {
    id: Long
    name: String
    email: String
}

User.find(id)
User.findAll()
User.save(user)

User.query {
    where age > 18
    orderBy name
}

transaction {
    user.save()
    account.update()
}

sql """
    SELECT * FROM users WHERE active = true
"""
```

Princípio: "Abstração quando ajuda, SQL quando precisa."

Estado atual: 🟡 parcial — **nível 0-2 e 4 implementados** (`kof.db` +
`kof.orm`, ver `docs/stdlib/DATABASE_VISION.md`): conexão idiomática
(JDBC no JVM; SQLite nativo via `.so`; MySQL handshake `kof_db_mysql_scramble` 27/08), SQL com prepared
statements, transactions, `entity` declarativo em compile-time, CRUD
(`create/save/find/all/where/delete/count`), `orm.where` por campo + operadores, `saveAll` batch, `page`/`count`/`deleteAll`,
migrations versionadas (`kof_migrations`) e MongoDB (driver oficial).
Faltam: query DSL tipada (`User.query { where age > 18 }`), connection
pooling, MySQL completo (query/prepared), `kof.db`/`kof.orm` fora do JVM (**JS `DB001` FECHADO 16/09** — nao-tipado no host GraalJS; tipado `query<T>` = `DB002` FECHADO 18/09; `kof.orm` = `ORM001` FECHADO 18/09 no JS, residual Native `ORM001`), NoSQL além do MongoDB.

---

## 6. Dependency Management

O usuário não deveria precisar editar `pom.xml` diretamente.

Comandos futuros:

```
kof init
kof install lombok
kof remove lombok
kof update
```

Arquivo próprio da linguagem (`kofdeps`). Para Kof4J, o sistema poderá gerar `pom.xml` temporário em memória durante o build e utilizar Maven para resolução/download.

Estado atual: 🟡 MVP 01/09 — `kof deps init/add/remove/list/resolve` (arquivo
`kofdeps`, resolução Maven Central → `~/.kof/deps`, classpath via
`kof build|run --deps`); **dependências transitivas do POM ✅ 16/09** (resolvidas
delegando ao Maven via `pom.xml` temporário + `dependency:build-classpath`
(R9: o resolvedor de grafo do Maven já existe — nunca reimplementado); o fecho vai
para um `kofdeps.lock` portável (lista GAV) consumido por `resolve`/`build`/`run
--deps`; degradação honesta quando `mvn` não está no PATH — warning explícito,
nunca classpath truncado em silêncio; prova `DepsTransitiveTest` 10/10 incluindo
E2E com Maven real `jgrapht-core:1.4.0 → org.jheaps:jheaps:0.11`);
**registry pendente** (precisa de decisão da mantenedora — formato/hosting público).

---

## 7. Java Interoperability

A compatibilidade Java é requisito estratégico. Kof deve conseguir utilizar classes, métodos, interfaces, bibliotecas, annotations, Maven artifacts e frameworks legados Java.

A existência de APIs nativas do Kof NÃO deve quebrar essa capacidade.

Regra: "Legado continua funcionando. Kof oferece uma experiência melhor por cima."

Estado atual: ✅ funcional (records, classes, constructors, methods, fields)

---

## 8. Frontend

API de UI declarativa inspirada conceitualmente em Flutter.

Objetivos:
- componentes / composição / layout;
- estado / eventos / routing;
- forms / validation;
- responsive design / accessibility;
- animation / theming.

```kof
column {
    text("Hello")
    button("Click") { action() }
}
button.color = red
button.alignment = center
button.size = 10
```

O KofJS deverá gerar:

```
output/
├── index.html
├── assets/
├── app.js
└── app.css
```

Estado atual: ❌ não implementado

---

## 9. Frontend + Backend no Mesmo Projeto

Um mesmo projeto Kof pode conter backend e frontend. O compilador deve entender os contextos através da estrutura/declarações do projeto.

Shared models/types poderão futuramente ser utilizados nos dois lados.

Estado atual: ❌ não implementado

---

## 10. Arquitetura de Aplicação

Kof não deve impor MVC, Clean Architecture ou Hexagonal Architecture. Deve permitir todas.

```kof
// Simples
main() {
    get "/users" { return User.all() }
}

// Modular
app/
├── domain/
├── application/
├── infrastructure/
└── api/
```

Princípio: "A linguagem fornece primitivas; a arquitetura é escolha do desenvolvedor."

Estado atual: ❌ não implementado

---

## 11. Monólito → Microserviços

A meta é permitir evolução sem reescrita:

```
monolith → modular monolith → services → microservices
```

Compilação `kof build` pode gerar `app.jar` ou `app` nativo. Posteriormente o mesmo projeto pode ser particionado.

Estado atual: ❌ não implementado

---

## 12. Performance

Kof deve permitir implementar aplicações rápidas, eficientes, escaláveis, com baixo consumo e startup rápido.

Regras:
- compile-time > runtime magic;
- type information > reflection;
- generated code > runtime discovery;
- explicit semantics > hidden framework behavior.

Estado atual: ✅ JVM funcional, Native funcional

---

## 13. Observabilidade

APIs nativas para log, metric, trace, health, audit. Integração com OpenTelemetry.

Estado atual: 🟡 parcial — `kof.log` com níveis (JVM: JSON estruturado +
correlation ID; Native: asm, UTC — JS `console.*` 01/09) e `kof.observability`
(health/readiness/liveness, counter/increment/gauge, requestId/
correlationId — 3 targets). Faltam: histogram + endpoint `/metrics`
(Prometheus), tracing/OpenTelemetry e `app.health("/health")`.

---

## 14. Standard Library / Runtime

Progressivamente:

```
kof-runtime / kof-http / kof-json / kof-data /
kof-security / kof-concurrency / kof-io / kof-ui
```

Mas NÃO criar dezenas de módulos prematuramente. Primeiro definir contratos, tipos e arquitetura.

Estado atual: 🟡 em progresso — já existem como namespaces da stdlib (0.2.6-beta):
`kof.web` (JVM, `kof.http` JVM+JS), `kof.io`, `kof.time`, `kof.config` (JVM+Native free-list), `kof.log` (JVM+Native),
`kof.security` (3 targets, G9), `kof.db` + `kof.orm` (JVM; SQLite native + `kof_db_mysql_scramble`), `kof.validation`/`kof.observability`/`kof.mq` (3 targets),
`kof.process`, `kof.ui` + `KofScript`/`KofCcompiler`. A organização em módulos separados virá depois dos
contratos estabilizarem.

---

## 15. Roadmap por Fases

### Fase 0 — Consolidação Atual ✅

- parser;
- type system;
- symbol resolution;
- semantic model;
- Kof IR;
- JVM backend;
- Native backend (concluído).

### Fase F — Runtime + Object Model ✅

- auditoria do runtime atual ✅
- Kof Runtime ABI definida ✅
- Object Model definido ✅
- ClassLayout / FieldLayout centralizados ✅
- NativeRuntime (kof_alloc, kof_panic, etc.) ✅
- NativeBackend refatorado (heap alloc, constructors, KofDup) ✅
- **Fase F.1 — String Model:** ✅
  - BuiltinTypes.STRING centralizado ✅
  - KofString layout (type_id, flags, length, UTF-8 data) ✅
  - kof_string_from_literal ✅
  - kof_string_length ✅
  - kof_string_concat ✅
  - kof_string_equals ✅
  - kof_print_string / kof_println_string ✅
  - NativeBackend usa KofString para literals ✅
  - STRING_MODEL.md documentado ✅
- **Fase F.2 — Array Model:** ✅
  - ArrayType no Type System ✅
  - NewArrayExpr + ArrayAccessExpr no AST ✅
  - Parser: new Type[size], expr[expr], expr.length ✅
  - SemanticAnalyzer: type checking de arrays ✅
  - CompilerDriver: lowering para KofNewArray/KofArrayLoad/KofArrayStore/KofArrayLength ✅
  - NativeRuntime: kof_array_alloc, kof_array_length, kof_array_get, kof_array_set ✅
  - NativeBackend: lowering completo das operações de array ✅
  - JVM Backend: NEWARRAY/IALOAD/IASTORE/ARRAYLENGTH ✅
  - ARRAY_MODEL.md documentado ✅
  - 25 novos testes (criação, acesso, length, long, string, loop, argumento, retorno, vazio) ✅
- **Fase F.3 — Inheritance:** ✅
  - SemanticAnalyzer: resolveInHierarchy() caminha cadeia de superclasses ✅
  - ClassLayout: buildWithSuper() inclui fields herdados ✅
  - NativeBackend: allClassesMap para resolver superclasses ✅
  - CompilerDriver: super(args) com argumentos, findSuperClass() ✅
  - Constructor chaining com super(args) explícito ✅
  - Acesso a fields e métodos herdados ✅
  - Herança de 3 níveis ✅
  - INHERITANCE_MODEL.md documentado ✅
  - 20 novos testes (subclasse, fields herdados, methods herdados, constructor chaining, 3 níveis) ✅
- **Fase F.4 — Virtual Dispatch:** ✅
  - Object header estendido: 8 → 16 bytes (type_id + flags + method_table_ptr) ✅
  - Method tables geradas por classe ✅
  - kof_init_object para inicializar header ✅
  - Virtual dispatch via vtable no NativeBackend ✅
  - JVM usa INVOKEVIRTUAL nativo ✅
  - Parser: suporte a `ClassName varName = value` ✅
  - CompilerDriver: NewExpr no inferExprType ✅
  - VIRTUAL_DISPATCH.md documentado ✅
  - 11 novos testes (override, polymorphism, 3 níveis, slots) ✅
- **Fase F.5 — Interfaces:** ✅
  - KofCallKind.INTERFACE na IR ✅
  - Parser: interface declaration + implements ✅
  - SemanticAnalyzer: isInterfaceType(), resolveInHierarchy() caminha interfaces ✅
  - CompilerDriver: define KofCallKind.INTERFACE para chamadas via interface ✅
  - JvmBackend: INVOKEINTERFACE ✅
  - NativeBackend: dispatch via vtable para interfaces ✅
  - INTERFACES_MODEL.md documentado ✅
  - 13 novos testes ✅
- **Fase F.6 — Exceptions/Runtime Errors:** ✅
  - AST: ThrowStmt, TryStmt, CatchClause ✅
  - Parser: try/catch/finally ✅
  - IR: KofThrow ✅
  - JvmBackend: ATHROW ✅
  - NativeBackend: kof_panic para throw ✅
  - Runtime errors: kof_null_error, kof_bounds_error ✅
  - EXCEPTIONS_MODEL.md documentado ✅
  - 14 novos testes ✅
- **Fase F.7 — Memory Management:** ✅
  - kof_alloc com tracking de alocações ✅
  - kof_free (no-op, documentado) ✅
  - kof_memstats para debug ✅
  - MEMORY_MODEL.md documentado ✅
> **Atualizado (0.2.6-beta):** interfaces (F.5), exceptions reais (F.6, JVM +
> Native unwinding) e memory management (free-list `kof_free_head` + `kof_gc_collect` 27/08; `mmap` + reuso) estão implementados.

### Fase 1 — Core

- runtime (consolidação);
- standard types;
- collections;
- IO;
- errors/exceptions;
- concurrency;
- serialization.

### Fase 2 — Developer Experience

- `kof init` / `kofdeps` / `kof install` / `kof remove`;
- `kof update` / `kof check` / `kof fmt` / `kof test` / `kof clean`;
- REPL / LSP.

### Fase 3 — Web Platform (`kof serve`)

- syscalls de rede no NativeRuntime (socket, bind, listen, accept, read, write, close) ✅;
- `kof serve` command no CLI ✅;
- KofHttpServer (thread pool, Content-Length, query, headers, 404/500) ✅;
- `kof serve` com handlers top-level (`handle(...)`) ✅;
- JSON serialization (`json.encode`/`json.decode`) ✅;
- 8 testes E2E in-process (sockets reais) ✅;
- Documentação (`docs/stdlib/http.md`) ✅;
- Path parameters (`:id`), query, headers, middleware `app.use` ✅
  (stack `web.app()` — Fase 1 do plano Spring independence);
- WebSocket/SSE + hardening (`app.configure`/`app.stats`, connection cap,
  deadlines) ✅ JVM (30/08-04/09); JS/Native follow-up.

### Fase 4 — Security

- auth / authorization / JWT / OAuth/OIDC;
- sessions / policies / rate limiting;
- security defaults / audit.

> Auditoria do ecossistema: a matriz de cobertura, gaps (G1-G12),
> prioridades e estratégia vivem em `docs/bugs-and-gaps/ecosystem-coverage.md`.
> Ordem de implementação P0: diagnóstico de target (G7) → `kof.test`
> estruturado (G6) → `kof.config` (G3) → `kof.http` client (G2) →
> `kof.database` (G1) → validation (G4) → observability (G5) →
> scheduling (G8) → security Native (G10) → web security (G9, G12).

### Fase 5 — KofJS

- frontend / declarative UI / components;
- state / routing / forms / SSR;
- HTML/CSS/JS generation.

### Fase 6 — KofScript

- direct execution / fast startup;
- REPL / incremental execution / scripting APIs.

### Fase 7 — Native Completo

- full language support / native runtime;
- networking / database / security;
- production server support.

### Fase 8 — Maturidade da Plataforma

- distributed systems / service discovery;
- messaging / RPC;
- observability / deployment / cloud integrations.

### Fase 9 — Refactor Interno: regra de 500 linhas por classe

> **Registrado 02/09/2026.** Regra de arquitetura: nenhuma classe pode
> ultrapassar **500 linhas**. Violações atuais obrigam refactor geral:

- `NativeRuntime.java` (~17.300 — assembly embutido) → módulos por domínio
  (`native/asm/*.s` ou classes `NativeRuntime*` por área);
- `CompilerDriver.java` (~8.200) → extrair helpers por área;
- `JsBackend.java` (~5.400) → separar emitter do runtime embutido;
- `Parser.java` / `SemanticAnalyzer.java` / `JvmBackend.java` → sub-parsers.

Critério de aceite: `cloc`/`wc -l` por classe — nenhuma acima de 500.
Detalhes e tabela de tamanhos: `docs/audits/complexity-audit.md` → "Regra de
arquitetura — limite de 500 linhas por classe".

---

## 16. Não Fazer

- não copiar Spring;
- não copiar Hibernate;
- não criar um framework monolítico gigante;
- não adicionar annotations para tudo;
- não depender de reflection quando compile-time for suficiente;
- não acoplar o core à JVM;
- não criar APIs específicas de um backend dentro da linguagem;
- não sacrificar Java interoperability;
- não implementar features gigantes antes de consolidar o core;
- não transformar cada problema em um novo módulo;
- não adicionar complexidade só porque outras linguagens fazem assim.

---

## 17. Distribuição e Tooling (0.2.6-beta)

O Kof é uma plataforma distribuível, não apenas um JAR:

- distribuição autocontida (compiler, CLI, runtime, stdlib, tooling, editor support, JDK 25 embutido);
- OpenJDK embutido no pacote oficial (Temurin 25, tooling API level 21);
- versionamento centralizado (`VERSION` 0.4.0-beta → pom/properties via `scripts/bump-version.sh`);
- releases por 2 jobs (`release.yml`: `test-and-bump` exporta `bump_sha` → `package-and-release` checkeia o commit de bump + sanity check de versão) por push na `main`, por plataforma linux-x86_64 / macos-arm64 / windows-x86_64 (testes 819 → bump → package 3 plataformas → GitHub Release);
- `scripts/package.sh` PASS (layout dist + tar.gz/zip + SHA256SUMS + jars), golden 16/16, integration 9/9;
- editor support oficial: grammar TextMate + LSP (hover/completion + diagnostics reais) + `kof editor install` (VS Code/Neovim/Vim/Emacs/Geany/Nano + IntelliJ degrau-10 honesto 13/09: filetype XML + External Tools + README LSP4IJ, sem plugin — issue #1);
- `kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/decompile/translate/compare/migrate/debug/info/lsp/install/deps/editor/init/new/version` PASS (26 comandos; `fmt` e `config gen` 31/08).

Referências: `docs/distribution/`, `docs/tooling/`.

---

## 18. Kof Escrito em Kof (auto-hospedagem)

Planejado desde já como evolução arquitetural real, não demonstração.

Pré-requisitos antes da migração:

- generics; collections; exceptions;
- stdlib; filesystem; strings; concurrency; HTTP;
- tooling; expressividade suficiente da linguagem.

O compilador atual permanece arquiteturalmente preparado para a migração
(frontend único alimentando compiler, LSP, formatter e diagnostics), mas a
migração **não** deve ser tentada prematuramente.

---

## 19. Kof + LLM

Kof é *Human First, LLM Friendly by Consequence*:

- menos ceremony; menos arquivos; menos abstrações artificiais;
- menos configuração; mais intenção.

A consistência do design faz com que humanos e LLMs entendam a mesma
linguagem da mesma forma. O diretório `training/` é parte oficial dessa
estratégia.

---

## 19.5 Kof Debugger (componente oficial de tooling)

Debugging de primeira classe: o programador depura **código Kof**,
independentemente do target. Fases 1-3 implementadas: DebugInfo na IR com
source location por op, JVM LineNumberTable/SourceFile/LocalVariableTable
gerados e **`kof debug` MVP funcional** (DAP over stdio + JDWP cru: launch,
breakpoints por linha Kof, `stopped`, stack trace com funções/linhas Kof,
continue, disconnect). Fases 4-7 (Kof Editor, Native DWARF, JS source
maps, avançado) planejadas. Ver: `docs/debugging/debugger-architecture.md`,
`docs/debugging/debugging.md`, `docs/debugging/debug-adapter.md`.

## 20. Princípios de Design

1. Simplicidade primeiro.
2. Legibilidade primeiro.
3. Compile-time sempre que possível.
4. Runtime pequeno e previsível.
5. Segurança por padrão.
6. Performance mensurável.
7. Interoperabilidade sem compromisso.
8. Abstrações nativas para problemas recorrentes.
9. Escape hatches sempre disponíveis.
10. Uma linguagem, múltiplos targets.
11. Monólito e microserviços devem ser escolhas arquiteturais, não limitações da linguagem.
12. O código deve expressar intenção, não infraestrutura.
13. Kof deve esconder complexidade sem esconder poder.
14. Compatibilidade com legado é feature.
15. Nenhuma decisão futura deve quebrar o core agnostic da linguagem.

---

## 21. Legacy Migration Platform (plano futuro)

Iniciativa de longo prazo para analisar, recuperar, traduzir e modernizar
sistemas legados para Kof — **fora do escopo 0.0.x**.

- Documento central: `future/LEGACY_MIGRATION.md` (§4 = Legacy Semantic
  IR/Confidence; §8 = teste diferencial + migration report) —
  **DESPRIORIZADO pela mantenedora 15/09: o trio + work-logs voltaram para
  `future/`; o código em kof-cli fica, promoção exige decisão explícita dela**
- Componentes planejados: `kof inspect`, `kof decompile`, `kof translate`,
  `kof migrate`, `kof compare`
- Arquitetura: `Legacy Input → Legacy Semantic IR → Kof AST → Kof IR → Backend`
- Java é origem suportada, nunca representação intermediária obrigatória
- Documentos relacionados: `future/DECOMPILER.md`, `future/TRANSLATOR.md` (os antigos
  `LEGACY_IR.md` e `DIFFERENTIAL_TESTING.md` foram fundidos no central 13/09;
  `IMPLEMENTATION_PLAN.md`/`ACTION_PLAN.md` viraram o §23 deste roadmap)

**Não implementar nada desta seção antes da consolidação da linguagem,
compilador, runtime, stdlib e tooling.**

---

## 22. Plataforma Universal (em desenvolvimento — R12 sobreposto)

Visão de longo prazo — Kof como plataforma universal (uma linguagem para
aplicações **e** sistemas, infraestrutura, automação, dados, segurança e
ciência) **sem** destruir a simplicidade da linguagem.

- Documento central: `docs/development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  (arquitetura — **EM DESENVOLVIMENTO** desde 17/09/2026; promovido de
  `future/` por decisão da mantenedora, `DECISIONS.md` §D-UNIVERSAL)
- Estágios por capacidade/maturidade: `FOUNDATION ✅` → `SYSTEMS` (em
  andamento) → `AUTOMATION` → `INFRAESTRUTURA` → `DATA` → `SECURITY` →
  `SCIENTIFIC` → `BIO` → `UNIVERSAL`
- Mecanismo de expansão: **stdlib como tabelas de dispatch em compile-time** +
  FFI/interop + pacotes oficiais — nunca novo target, nunca linguagem nova
- Invariantes (R1–R12): fronteira core/plataforma, interop-first, escopo
  honesto por target (JVM-first/Native/JS-web), nunca silencioso por domínio
  (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`), tiers de estabilidade
  (`stable`/`experimental`), core pequeno e estável, segurança defesa primeiro,
  correto/determinístico em ciência
- Non-goals permanentes: sem macros abertas/type-classes/annotations/ownership/
  effect system; sem cripto caseira; sem reimplementar Arrow/BLAS/ML/
  alinhadores; sem "Kali em Kof"; sem target por domínio; sem motor SQL próprio

> **Estado 17/09:** R1 ✅ FEITO (`5f1422c6` — gate `scripts/check_stdlib_boundary.sh` + ledger na CI, AGENTS invariante 1). R2–R12: fila aberta por D-UNIVERSAL; unidades de código seguem a ordem de valor do §23.
>
> **Portão R12 sobreposto em 17/09/2026** (`DECISIONS.md` §D-UNIVERSAL): a
> mantenedora autorizou abrir esta frente **com o SYSTEMS ainda em andamento**.
> O ponto de entrada é o Estágio 1 (consolidação SYSTEMS) + R1–R12; o Tier 6+
> (AUTOMATION/INFRA/DATA/…) mantém sua ordem no §23. Para **qualquer outra**
> frente, o R12 continua o default: não abrir `infra`/`data`/`sci` antes do
> SYSTEMS fechar (paridade de gaps, GC mark-sweep, package manager básico —
> §23 P0–P5).

---

## 23. Plano de Implementação Consolidado (Tiers 0–12)

> **Este é o ÚNICO plano de implementação ordenado do repo.** Funde
> `ACTION_PLAN.md` e `IMPLEMENTATION_PLAN.md` (apagados 13/09 — ~85% do
> conteúdo era a MESMA tabela de fases/tiers entre os dois, e as duas
> divergiam do código). Toda fase aqui move o doc correspondente de
> `future/`→`docs/` quando ganha código. Dificuldade: `E` fácil · `M`
> médio · `H` alto · `R` pesquisa.
>
> **Regra transversal (R12):** nenhum item de plano futuro é ação sobre o
> estado atual; frentes novas (AUTOMATION/DATA/SCI/BIO) não abrem antes do
> estágio SYSTEMS (§21/§22) fechar. Non-goals (§16/§22): sem macros abertas,
> type-classes, ownership, effect system, cripto caseira, reimplementar
> Arrow/BLAS/ML; sem "Kali em Kof"; sem motor SQL próprio.

### TIER 0 — Guardrails e processos (E, ≈ zero) ✅ 01/09

R1/R5/R6/R7/R9–R12 como invariantes (AGENTS.md + §22); convenção de gaps por
domínio (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`) + matriz de paridade;
tiers `stable`/`experimental` (`docs/backend-parity.md`).

### TIER 1 — Fechamento do estágio SYSTEMS (M–H, pré-requisito p/ Tiers 6+)

| # | Item | Estado medido (13/09) |
|---|------|----------------------|
| 1.1 | Gaps de paridade (`HTTP002`, resíduo web `WEB002`/`WEB003`/`WEB004`, ~~`CONC003`~~ ✅ 03/09, ~~`LOG001`~~ ✅ 01/09, ~~`MQ001`~~ ✅ 01/09, ~~`SCHED001`~~ ✅ 31/08, ~~`TIME001`~~ ✅ 02–05/09, ~~`SECN002`~~ ✅ 01/09, ~~`OBS002`~~ ✅ 01/09, `MEDIA`) | 🟡 em progresso — JS web server base ✅ 16/09 (WEB001 fechado; DB001 fechado); residual por `backend-parity.md` (HTTP002 https/TLS nativo, gap codes ws/sse, MEDIA) |
| 1.2 | GC mark-sweep automático no Native | 🟡 riscv `356f33b9` ✅; x86 decomposto G-1..G-5 (`native-multiarch.md`) |
| 1.3 | Query DSL tipada (`User.query {}`) | ✅ 01/09 (`KofOrmE2ETest`) |
| 1.4 | Package manager MVP (`kofdeps`) | 🟡 `kof deps` + resolução Maven Central; **transitivos ✅ 16/09** (delegação ao Maven + `kofdeps.lock`, `DepsTransitiveTest` 10/10 incl. E2E com Maven real); **registry pendente (decisão da mantenedora)** |
| 1.5 | Tracing/OpenTelemetry + lifecycle `application{}` | 🟡 spans W3C + lifecycle ✅ 3 targets; **export OTel ✅ JVM/JS (`exportSpans()` → OTLP/JSON, `OBS003`); gap honesto no Native `OBS003`** |
| 1.6 | **Native → bare-metal/bootável** (microcontrolador, BIOS legado, UEFI) — diretiva da mantenedora 15/09 | ⚪ **só plano** — costura HAL `kof_plat_*` + perfil freestanding, faces B-0…B-5 em `docs/development/future/PLAN-BAREMETAL-BOOT.md`; sem agendamento; MCU depende de 1.2 |

### TIER 2 — Fundações de compilador (M) — **status corrigido contra o código**

> A versão antiga marcava 2.1.5 e 2.2.2 como "✅"; **não são** (ver abaixo —
> auditado em HEAD 13/09, não de memória).

| # | Item | Estado REAL medido |
|---|------|--------------------|
| 2.1.1–2.1.3 | Sintaxe `extern` + type-check + gaps `FFI001`/`FFI002` (nunca drop silencioso) | ✅ `Parser.java:192` (PARSE090), `ExternalFunctionNode`, `FfiE2ETest` |
| 2.1.4 | Binding **JVM** (FFM `java.lang.foreign`) | ✅ `abs`/`atoi`(String→Int)/`sqrt`(Double→Double) reais via FFM |
| 2.1.5 | Binding **Native** (`dlsym`) | ❌ **gap honesto `FFI001`** — `dlopen` segfaulta no binário cru (sem init glibc); NÃO é "✅ real" |
| 2.1.6 | Marshalling struct/array | 🟡 String↔Int, Double↔Double (JVM); struct/array completo pendente |
| 2.1.7 | JS: gap `FFI002` | ✅ |
| 2.2.1 | Inventário do codegen implícito (4 pontos: runtime `.source()`, `desugarTests`, `desugarApplication`, entity→record+schema) | ✅ os 4 existem (`CompilerPipeline:295-296`) |
| 2.2.2 | **Hook formal `CodegenStep`** | ❌ **NÃO existe no HEAD** — `d1c56bad` adicionou, a pipeline voltou a chamar os `desugar*` direto; o "✅" antigo era sobre-claim da branch `planning-future` |
| 2.2.3 | Migrar DDL/runner p/ o hook formal | ❌ bloqueado por 2.2.2 |
| 2.2.4 | Base de `infra "prod" {}` (codegen sobre records) | ❌ não iniciado (zero parse de `infra`) |
| 2.3.1 | Constant-folding de constantes de domínio | ✅ `"a"+"b"→"ab"` (`OptimizerConstantFold:100`) |
| 2.3.2 | Detecção de ciclo no grafo `infra` em compile-time | ❌ bloqueado por 2.2.4 |
| 2.4.1 | Scoped resources (RAII leve sobre `try/finally`) | 🟡 só design (`future/scoped-resources-plan.md`); sintaxe `using` gated por bump |
| 2.5 | Variance / sealed | ✅ **DECIDIDO ADIAR** — `enum`+`record`/`interface` cobrem o caso; abre só com pipeline científica (bump) |

#### 2.6 — Nullability por INTENÇÃO EXPLÍCITA (fila N1→N4 de DECISIONS §D-NULL-INTENT, 15/09)

**Decidido pela mantenedora em pessoa 15/09** (registro: `DECISIONS.md`
§D-NULL-INTENT, EN+PT — a "opção A" do §125 (dobra silenciosa `null→0`) está
REVOGADA). Lane: **compiler** (contrato nos 4 backends — não a lane docs).

| # | Passo | Escopo (uma linha) | Depende de |
|---|-------|--------------------|------------|
| 2.6.1 | **N1** — JVM+Script+JS: `Nullable(primitivo)` carrega null REAL | `T?` boxed em retorno/campo/slot nos 3 targets com tipo boxed; virar a célula `nullableprint` + as 3 paridades null-branch de `KofInterpreterParityTest` no MESMO commit do comportamento (regra 1) | — |
| 2.6.2 | **N2** — Native: null real via ABI de box tagged §104b-ii | box `typeId=3` + `object_to_string`/unbox com dispatch; x86 à mão + riscv à mão + aarch64 via tradutor | §104b-ii / §205 fatia 2 dividem este ABI |
| 2.6.3 | **N3** — `== null` em NÃO-nullable: legal, constant-foldable, NUNCA diagnóstico | a intenção é a própria comparação; regra 2 (retrocompat): código existente que compara continua compilando | N1 |
| 2.6.4 | **N4** — auditar as faces restantes de null silencioso | map-miss `0` (SG-008), campo não-inicializado `0`, unbox-de-null `0` — cada um ganha decisão ou diagnóstico honesto (R6) | N1–N3 |

#### 2.7 — Value records / tipos de valor de primeira classe (fila de `D-VALUE-RECORD`, 16/09)

**Decidido pela mantenedora 16/09** (registro: `DECISIONS.md` §D-VALUE-RECORD;
origem issue #275). Aditivo, retrocompatível. **Apenas planejado — não é
trabalho atual** (R12: frentes novas não abrem antes de o estágio SYSTEMS
fechar; lanes não devem atacar sem nova autorização).

| # | Etapa | Escopo (uma linha) | Depende de |
|---|-------|--------------------|------------|
| 2.7.1 | **keyword `value` no front-end** | `value record Name(campos)` faz parse e tipagem como `record` + modificador `value`; sem semântica de identidade ainda | — |
| 2.7.2 | **ABI JVM** | mapeamento value/inline class (`invokevirtual` com semântica de valor, sem identidade `Object`) | 2.7.1 |
| 2.7.3 | **ABI Native** | passagem por valor (struct por valor / registradores) | 2.7.1 |
| 2.7.4 | **ABI JS** | objeto congelado comum (sem identidade) | 2.7.1 |
| 2.7.5 | **paridade + docs** | células de conformidade `valuerecord` + matriz de paridade + `training/` + `learn/` | 2.7.1–2.7.4 |
### TIER 3–5 — Plataforma de migração legado (Fases A–H) ✅ código+testes

`kof inspect/decompile/translate/compare/migrate` no CLI (`Main.java`);
Legacy Semantic IR com Confidence Model (5 níveis) + "nunca inventar". Prova
medida 15/09 em HEAD (`7b0bfbe0`, classes frescas): **Decompile 67 + PostDom 6,
Translate 61, Compare 7, Migrate 3** — todas verdes (os números de 13/09
57/33/6/3 estavam defasados; a então "1 célula vermelha"
`qualifiedLocalTypeTranslates` está VERDE desde que a lane `.22` a fechou).
Recuperação de corpo de método ainda parcial
(joins estruturais = Fase C; re-medido 15/09 com probe instrumentado:
1793 stubs, a maior família são prefixos de teste com computação/invokes —
519/566 TRAPs exigindo o walker de post-dominador; o número antigo "2452" do
StoreCat está defasado, o sub-caso de join if-then puro já está recuperado). O
histórico técnico detalhado vive em `future/LEGACY_MIGRATION.md` +
`future/DECOMPILER.md` (§7) — **não duplicar aqui**; esta tabela só dá a
ordem. **DESPRIORIZADO 15/09 (mantenedora): TIER 3–5 não é trabalho atual.**

### TIER 6–12 — Plataforma universal (arquitetura **EM DESENVOLVIMENTO** 17/09 — R12 sobreposto; regidos por `docs/development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`)

| Tier | Estágio | Escopo (uma linha) |
|------|---------|--------------------|
| 6 | AUTOMATION | `kof.workflow`/`batch`/`shell`/`ssh` — jobs como código Kof, nunca YAML/bash |
| 7 | INFRAESTRUTURA | `infra "prod" {}` (codegen, não HCL) + reconciliation loop — deps 1.4, 2.2 |
| 8 | DATA | `dataframe` tipado + Arrow/Parquet/estatística **por FFI** (wrapper, nunca reimplementar) — deps 2.1, pkg manager |
| 9 | SECURITY | S2 `Secret`/`KeyHandle` · S3 `keys.*` · S4 assimétrica · S5 **PQC** (`liboqs`, NIST) · S6 híbrido · S7 `secure.channel`; só FFI a lib auditada |
| 10 | SCIENTIFIC | BLAS/LAPACK/GPU/MPI **por FFI**; SIMD Native (pesquisa); deps 2.1, 2.4, 1.2 |
| 11 | BIO | `kof-bio` (pacote oficial): FASTA/FASTQ/VCF + alinhamento via FFI/CLI — deps 6, 8, 10 |
| 12 | UNIVERSAL | integração total + pkg manager maduro + LSP/debug/profiler por domínio; **teste final: o core da linguagem quase não cresceu** |

### Critical path (o que bloqueia o quê)

`Legacy-Class-File-Parser` → todos os Tiers 3–5 · `Decompiler-Structural` →
`Diff-Framework` → `Migration-Reports` · `2.1 FFI` → Tiers 8/9/10 (tudo por
FFI) · `2.2 codegen hook` → `infra`/gRPC stubs · **TIER 1 (SYSTEMS) fecha
antes de QUALQUER Tier 6+ (R12).**
