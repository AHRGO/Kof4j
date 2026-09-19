[English](workflow-plan.md) | [Português](workflow-plan.pt_BR.md)

# `kof.workflow` — jobs, pipelines, retry, checkpoints, dead-letter (plano de design · Estágio 2 · TIER 2.1)

> **Estado: PROPOSTO (18/09) — aguardando decisão de escopo da mantenedora (regra 6). Zero código.**
> Este arquivo existe porque a linha **2.1** do `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` é uma
> linha `🔵` solta, dono `—`, enquanto todas as outras frentes greenfield em `future/`
> (`value-records`, `scoped-resources`, `shell-plan`, …) têm um plano concreto. Ele **propõe**
> virar essa linha numa fila de fatias executável; **não** autoriza abrir a frente — essa
> decisão é da mantenedora. Também **não** implementa nada: `kof.workflow` não está no lexer,
> no parser, em nenhum backend, nem na stdlib hoje (medido 18/09). Ancorado nas primitivas
> **reais, medidas** que ele compõe (§4).

## 1. Objetivo
Uma única idiomática tipada e componível para **trabalho de longa duração, retentável e
inspecionável**: definir um job, declarar suas dependências (DAG), executá-lo (agora ou em
schedule), retentar em falha com backoff, fazer checkpoint dos passos concluídos e estacionar
falhas terminais numa fila de dead-letter — **sem** que cada app reinvente o loop
`while (!ok && tries<N)`. Hoje as peças existem mas estão desconectadas (medido, §4):
`kof.scheduler.every/at` agenda *funções*, `kof.supervisor` reinicia *workers* (pure-Kof,
ratificado DD-OTP-01), `kof.process` roda *comandos*, `kof.orm` persiste *linhas*,
canais/`selectAny` coordenam *concorrência*. Falta a **cola que dá a todas as cinco um
contrato único**: `workflow` é uma camada de composição, **não** uma primitiva de runtime
nova — herda o comportamento das primitivas que conecta, exatamente como `shell` é açúcar
sobre `process` (ver [`shell-plan.pt_BR.md`](shell-plan.pt_BR.md)).

## 2. Proposta — pacote stdlib em nível Kof, NÃO sintaxe nova
Fato medido: o parser não tem produções `workflow`/`task`/`pipeline`, e o `roadmap.md`
§"KofJS — Web" é explícito ao dizer que JS não é superfície de segunda classe (regra 5). Então
`kof.workflow` é proposto como **código Kof comum** na árvore `stdlib` (como `kof.supervisor`
já é, por DD-OTP-01 opção A), **não** uma keyword ou forma gramatical nova. Esboço da idiomática
(forma apenas — cada decisão abaixo é um `Q` para a mantenedora no §6, não uma afirmação deste
documento):

```
import kof.workflow

var build = workflow.job("build") { ctx -> run("make", ["-j"]) }
var image = workflow.job("image").after(build) { ctx -> run("docker", ["build", "."]) }

var flow  = workflow.dag(build, image)
              .retry(image, times: 3, backoff: workflow.exponential(1s, 2.0))
              .checkpoint(kof.db)        // retoma jobs concluídos após crash
              .deadLetter(workflow.inMemory())  // falhas terminais estacionam aqui, não derrubam o run

var report = flow.run()                 // agora
var handle = flow.schedule("0 3 * * *") // cron, em cima do kof.scheduler.at
```

Inventárias de design (herdadas de precedente existente, não inventadas aqui):
- **DAG, não lista linear.** Ciclos são rejeitados **em runtime** por padrão (mesma classe de
  honestidade dos diagnósticos `SEM` — mensagem acionável, nunca um skip silencioso). Detecção
  de ciclo em tempo de compilação, se desejada, é uma linha à parte (**3.7** é essa decisão
  para o grafo infra; workflow a espelha).
- **Jobs são funções Kof `(Ctx) -> R`.** `Ctx` traz um logger (sobre `kof.log`), um store de
  `checkpoint` (sobre `kof.db`/`kof.orm`) e um `state: Map[String,Any]` para a entrada do
  próximo job. Sem IR de job próprio — reusa o tipo função (§155/§157 são a cautela que isto
  não toca).
- **Retry/backoff é um helper compartilhado**, hoje vivendo só dentro de `kof.http`
  (`NativeHttpCore.java`, `JsRuntimeUiLayout.java`, `RuntimeConcurrency.java` — medido);
  workflow eleva esse padrão a helper **genérico** usado por `http`, `process` e `workflow`
  verbatim (um único `retry(times, backoff, when: (err) -> Bool)`; não um fork).
- **Checkpoint = registro em formato `Result` gravado via o `kof.orm` existente** — mesma ABI
  que `shell` reusa o `process.Result` (um tipo só, sem segunda forma). Nenhum backend de
  persistência novo.
- **Dead-letter = uma view `Iterable` das falhas** — a implementação in-memory é um
  `List[Failed]`; a durável é uma tabela `kof.orm`. Nenhum subsistema de fila novo.
- **Scheduling reusa `kof.scheduler.at(cron)`.** Workflow **não** implementa parsing de cron.
  Se `at` levanta `CRON001` num alvo (native), `flow.schedule` levanta `CRON001` também — mesmo
  gap honesto, sem disfarce.
- **Supervision reusa `kof.supervisor` (OTP, DD-OTP-01 opção A).** Um run de workflow é uma
  árvore supervisionada; política de restart = a política do supervisor. `workflow` **não**
  reimplementa semântica de restart.

## 3. Contrato
- Pura composição sobre namespaces existentes (`process`, `scheduler`, `supervisor`, `orm`,
  canais `concurrency`, `Result`, `retry`/`backoff`) — nenhuma chamada de runtime `Kof*` nova,
  nenhum hook de backend novo, nenhum token de lexer novo, nenhum `Target` novo.
- **Superfície de linguagem aditiva apenas** (um namespace `kof.workflow` novo); nenhuma mudança
  no comportamento de qualquer primitiva existente (regra 6: mínima, reversível).
- Determinístico por alvo: um workflow que o alvo **não consegue rodar por completo** (ex.: um
  que precisa de `at(cron)` no native, ou `db.checkpoint` no native) falha **na definição** com
  o gap **existente** daquela primitiva (`CRON001`/`PROC001`/`ORM001`), nunca numa surpresa
  depois em `run()`. Se o workflow só usa `process` com `run`, funciona onde `process.run`
  funciona.
- Paridade cross-target (regra 5): a **mesma fonte** deve produzir o mesmo `Report` em todo alvo
  onde as peças de baixo existem. Onde não existem, o **mesmo** código honesto
  (`ORM001`/`CRON001`/`PROC001`) aparece naquele alvo.

## 4. ABI por alvo (escopo honesto R7) — MEDIDO em 18/09, não assumido
Tudo que `kof.workflow` compõe já existe; `workflow` em si não adiciona nada a backend
nenhum. Lido dos arquivos-fonte (não de memória), cada coluna cita o seu arquivo:

| primitiva | arquivo-fonte | JVM | ANDROID | JS | NATIVE |
|-----------|---------------|:---:|:-------:|:--:|:------:|
| `process.run` → `Result{stdout,stderr,exitCode}` | `KofProcess.java`, `ExpressionProcessCallLowerer.java` | ✅ | ✅ | ✅ (`kof_platform.processRun`) | ❌ `PROC001` |
| `process.spawn` (handle) | idem, `spawnCall` | ✅ | ✅ | ❌ `PROC001` (gate 18/09) | ❌ `PROC001` |
| `scheduler.every(fn, ms)` / `.cancel(h)` | `KofScheduler.java` `supportedOn` | ✅ | ✅ | ✅ | ✅ (`Rt B` cross) |
| `scheduler.at(cron5f, fn)` | idem, `gapCode` | ✅ | ✅ | ✅ (parser landado 17/09) | ❌ `CRON001` (honesto, R6) |
| `kof.orm.{create,save,find,all,where,page,migrate,delete,count,deleteAll,saveAll}` | `KofOrm.java`, JS `KofJsOrmBridge` | ✅ | ✅ | ✅ (JS fechado 18/09) | ❌ `ORM001` |
| canais + `spawn` + `selectAny` | `KofInterpreterConcurrency.java`, conc. JS/`native` (`CONC003` fechado 03/09, §132 resolvido 18/09) | ✅ | ✅ | ✅ | ✅ (workers = clone 220 + spinlock) |
| tipo `Result` (já reusado pelo `shell-plan`) | `KofProcess.RESULT` | ✅ | ✅ | ✅ | ✅ (como tipo) |
| `retry(times, backoff)` | hoje só dentro de `kof.http` (`NativeHttpCore.java`, `RuntimeConcurrency.java`, `JsRuntimeUiLayout.java`) | ⚠️ não geral | ⚠️ | ⚠️ | ⚠️ |
| `supervisor.one_for_one` (OTP) | `planning-otp-supervision.md` §Decision status (ratificado 13/09, DD-OTP-01 opção A pure-Kof) | ✅ | ✅ | ✅ | ⚠️ PARCIAL (riscv64/aarch64, 1 worker/supervisor — DD-OTP-03) |

**Consequências para `kof.workflow`:** em JVM + ANDROID + JS (com host Graal), um workflow
completo (jobs + DAG + retry + checkpoint + cron-schedule + dead-letter) está disponível desde o
dia um — cada primitiva é verde lá. No **Native**, `at(cron)` (`CRON001`) e `orm.checkpoint`
(`ORM001`) apresentam o gap honesto existente; **um `flow.run()` sem checkpoint e sem cron ainda
funciona lá** (jobs + DAG + retry + dead-letter in-memory, todos verdes no native). O plano **não**
disfarça os gaps `PROC001`/`ORM001`/`CRON001` do native — ele os herda verbatim.

## 5. Fila de passos (o todo executável que este doc existe para produzir)
Dono é `—` até a mantenedora atribuir; dono padrão proposto = **lane development**.

- **2.1.0 [recon — 0 código]** — congelar a tabela do §4 numa nota travada por teste: um
  `WorkflowPrimitivesE2ETest` que, para cada primitiva, afirma `supportedOn` em todo `Target` **e**
  roda o programa do §209 em JVM+JS (DAG + retry + round-trip de checkpoint via `kof.db`,
  park de dead-letter) + afirma os `CRON001`/`ORM001`/`PROC001` honestos no Native via pins estilo
  `DomainGapCodesTest`. Nenhuma superfície entregue.
- **2.1.1 [aprovação de design — ⛔ regra 6]** — a mantenedora aprova a forma (§2/§3): stdlib em
  nível Kof (esta proposta) vs namespace builtin do compilador, e as decisões reusa-vs-fork do §6.
  **Portão de toda a frente.** Nenhuma fatia seguinte até isto landar.
- **2.1.2 [MVP — stdlib pure-Kof, JVM+JS, paridade de um alvo]** — `job` + `dag` + `after` +
  `run` + `Report`; retry/backoff **extraído de** `kof.http` num helper compartilhado (paridade
  byte a byte JVM+JS+native por construção, pois *é* o mesmo código que `http` usa hoje);
  dead-letter in-memory. Golden `WorkflowE2ETest` contra as primitivas existentes.
- **2.1.3 [add-ons de paridade]** — `checkpoint(kof.db)` (usa `kof.orm` — honesto `ORM001` no
  native), `schedule(cron)` (usa `kof.scheduler.at` — honesto `CRON001` no native), e integração
  de supervisão delegando ao `kof.supervisor.one_for_one` quando o run do DAG é expresso como
  workers (em vez de uma caminhada síncrona simples).
- **2.1.4 [docs]** — doc de idiomática `docs/stdlib/workflow.md` (+PT), linha `backend-parity`,
  virar `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.1 `🔵 → 🟡` e fazer cascata 2.5/2.6 (que dependem
  de 2.1) de `🔵` para `⏳` com um pré-requisito real, e só quando shipado promover este arquivo
  para fora de `future/` pela regra da pasta.

## 6. Questões abertas (decisões da mantenedora — NÃO resolver em código)
- **Q1** — forma: um **pacote stdlib em nível Kof** (`stdlib/workflow.kf`, como `kof.supervisor`
  por DD-OTP-01 opção A) vs um namespace builtin do compilador (`KofWorkflow.java`, como
  `KofProcess`/`KofOrm`). Esta proposta recomenda nível-stdlib: a superfície inteira já é tipada e
  pura; a única razão para ser builtin seria mudar semântica de scheduling, que explicitamente
  reusamos (`kof.scheduler.at`).
- **Q2** — quanto do idiomático do §2 é v1: mínimo (`job`/`dag`/`after`/`run`/`Report`), ou
  incluindo `retry` + `checkpoint` + `deadLetter` no MVP, ou também `schedule(cron)`?
- **Q3** — o `retry(times, backoff)` genérico é um **refactor do retry existente de `kof.http`**
  num helper compartilhado (default desta proposta), ou uma camada aditiva à qual `http` migra
  **depois** (blast radius maior)? Refactor toca `NativeHttpCore.java`, `RuntimeConcurrency.java`,
  `JsRuntimeUiLayout.java` — cross-lane (`.17 nat`, `.22 compiler`, esta lane).
- **Q4** — durabilidade da dead-letter: "in-memory `Iterable`" é a única forma do v1, ou a
  implementação durável (tabela `kof.orm`) precisa shipar junta? Durável exige aceitar um gap
  honesto `ORM001` no Native **no mesmo release** (minha recomendação: sim — um release, ambas as
  faces, o gap já está catalogado e é honesto).

## 7. O que NÃO fazer
- Nenhum token de lexer/parser novo nem produção gramatical (isto **não** é uma keyword `pipeline`).
- Nenhuma primitiva de runtime nova em nenhum backend — toda ação roteia para um namespace
  existente (`process`/`scheduler`/`orm`/`supervisor`/`concurrency`) e herda seus gap codes.
- Nenhum fork de `retry`/`backoff` — ou refactor `kof.http` para compartilhar o helper, ou espera.
- Nenhuma camada nova de persistência para checkpoints — reusa `kof.db`/`kof.orm` (`ORM001` no
  native continua honesto, **não** escreve um `checkpoint` em asm no native para contorná-lo).
- Nenhuma reimplementação de restart/supervision — `kof.supervisor` (DD-OTP-01) é esse lar;
  `workflow` o compõe, nunca o duplica.
- Nenhum disfarce de `PROC001`/`CRON001`/`ORM001` no native — o gap honesto é o contrato.
- Nenhum código antes da aprovação **2.1.1**; nenhum alvo declarado verde antes de sua primitiva
  de baixo ser verde lá (medido no §4, re-verificado por `WorkflowPrimitivesE2ETest` no 2.1.0).
