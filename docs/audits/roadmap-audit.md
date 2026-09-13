# Roadmap Audit — Matriz de Implementação (06/09/2026)

> Fonte: auditoria de código real (3 explorações com evidências file:line) +
> execução de suíte. Regra: estado REAL, não o que o roadmap diz.
> Baseline: `0.3.0-beta`, suíte anteriormente declarada 910 testes.
> **Re-sincronizada 12/09** (doc-vs-realidade, célula a célula contra o HEAD):
> contagens de teste citadas abaixo = gate 12/09 regenerado dos surefire-reports
> (`docs/status.md` §Testes, `e2c03812`); células cujo estado mudou desde 06/09
> foram reescritas com prova (SG-009/WEB001/conformance/LSP/GC — ver cada linha).
> **Toque 13/09:** P4 (conformance suite estruturada + SG-009 subtipagem)
> marcado FECHADO — contradizia a própria linha 25 (SG-009 ✅ desde 10/09).

## Matriz de estado

| Item | Estado real | Código | Testes | Gap principal |
|---|---|---|---|---|
| 1. Standard Library | **PARTIAL (bom)** | 23 namespaces em `Kof*.java` com gates R6 (`supportedOn`/`gapCode`) | E2E por área (KofCache/Db/Http/Mq/Time/…) | web JS tem base REAL (GraalJS HttpServer, `bc577aa` 03/09 — não é mais stub silencioso; ws/sse = **WEB001 residual declarado**, `kofWebStub` sobra só como fallback para funções web não implementadas); sec sem cross = gap honesto SECN000 (`KofSecurityTest.crossNativeReportsSecn000`); db/orm JVM-only (gaps honestos); `scheduler.at` cron fake |
| 2. GC auto-collect | **PARTIAL** | mark-sweep real x86_64 (`RuntimeGc.java`); **SEM safe-points/root-map**; auto-collect desligado (`RuntimeMemory.java:121-133`); riscv/aarch bump **sem GC** (12/09: G-0 pousou — bloco-header 32B + guard OOM honesto, `356f33b9`; ainda sem free-list/mark-sweep) | `KofGcE2ETest` 3/3 (sweep/keep/reuse) | face (1) decomposta em **G-1..G-5** (`native-multiarch.md` §decomposição 12/09): G-1 free-list riscv (sem pré-requisito) → G-2 header flags → G-3 mark (depende do `kof_heap_root_end` da S-5-x86, fila bugfix) → G-4 sweep → G-5 aarch herda |
| 3. Package Manager | **MVP** | `Deps.java` (flat Maven Central, cache `~/.kof/deps`) | `DepsTest` 4/4 | POM/transitivas, lockfile, ranges, publish |
| 4. Async | **PARTIAL** | JVM vthreads + Handle/await/timeout; JS Promise real (CONC003 ✅); Native pthread | `KofConcurrency2Test` 33 (gate 12/09) | timeout/cancel/selectAny fechados nos 3 targets (`status.md` §Concorrência); gap restante: select sobre channels |
| 5. Concurrency G8 | **PARTIAL (bom)** | spawn/await/cancel/selectAny/awaitTimeout/channel/scheduler 3 targets | idem | `scheduler.at` cron = 60s fixo (MVP declarado); cancel por TID%256 |
| 6. KofAndroid | **DONE (com ressalva)** | `Target.ANDROID`; `--apk` pipeline (d8/aapt2/apksigner); `AndroidProjectWriter` (Maven) | pipeline depende de ANDROID_HOME | lifecycle/ART runtime cobertos na Fase 2; consolidar docs |
| 7. Debugger | **MVP** | DAP stdio JVM (`KofDebug.java`), breakpoints JDWP reais; DWARF line-only | docs/debugging/debug-adapter.md | **locals = placeholder** (`"line N"`, `KofDebug.java:197` — verificado no HEAD 12/09); stepping/evaluate; VS Code ext |
| 8. KofJS | **PARTIAL (alpha → funcional)** | ESM + source maps V3 + GraalJS + runtimes DOM/UI (9 arquivos); web base real (GraalJS HttpServer, `bc577aa`) | `KofJsE2ETest` 40 (gate 12/09) | ws/sse **WEB001 residual declarado** (gap honesto em `backend-parity.md` — não é mais stub silencioso); serve×JS indireto |
| 9. LSP | **PARTIAL (bom)** | diagnostics reais via CompilerDriver (fonte única); hover/completion/references/rename textuais + **go-to-definition ✅** (`definitionProvider`, `LspServer.java:323`) | `LspServerTest` 4/4 | hover/completion/rename devem usar SymbolTable (hoje textuais) |
| 10. KofScript | **PARTIAL (bom)** | interpretador de IR compartilhado (mesma semântica por construção) | `KofScriptTest` 31 (gate 12/09) + gate paridade | globals por regex multiline-fragil; REPL re-avalia tudo |
| 11. Language Spec | **PARTIAL (bom)** | `docs/language-reference/` 16 arquivos + specification-status | — | **fila SG COMPLETA** (23 entradas SG-001–020 + E1–E3 todas resolvidas 06–12/09, maioria por decisão da mantenedora; SG-009 subtipagem ✅ SEM021 `StatementAnalyzer.java:154` — a célula "20 gaps abertos" da auditoria 06/09 apodreceu); gramática não-normativa |
| 12. Conformance Suite | **PARTIAL** | `conformance-matrix.md` (07/09) — Feature×4 targets, células travadas por `ConformanceMatrixTest` (11) + `ConformanceMatrixDocTest`; BackendParityTest 16 casos JVM×JS×Nat | ConformanceMatrixTest · BackendParityTest | lotes seguintes por categoria (concorrência/tempo ficam nas suítes de E2E) |
| 13. Full Web Platform | **NOT STARTED** | routing parcial no kof.ui; validação existe | — | declarativo/forms/SSR — depende de 8+9 |
| gRPC | **NOT STARTED** | — | — | planejado; não iniciar antes de P0-P2 |
| Auto-hosting | **NOT STARTED** | — | — | documentado como gap |

## Bugs semânticos críticos (P0) — FALLBACKS SILENCIOSOS UNKNOWN

Auditoria encontrou **12 fallbacks silenciosos** que aceitam programas
semanticamente inválidos (o compilador infere UNKNOWN e segue, em vez de
diagnosticar). Os 4 maiores (todos em `SemExpressionTyper`/`MemberCallTyper`):

1. **#7 — maior**: método inexistente em namespace builtin (`db.*`, `log.*`,
   `http.*`, `mq.*`, `time.*`, `security.*`, …) → UNKNOWN sem SEM025. Só
   `process`/List/Map/Set foram corrigidos (7ec8b9d, bugs 31/34).
2. **#3 —** `obj.campoInexistente()` → UNKNOWN sem diagnóstico
   (SemExpressionTyper.java:312).
3. **#6 —** `super.metodoInexistente()` → UNKNOWN (MemberCallTyper.java:92)
   enquanto caminho normal de classe emite SEM025.
4. **#8 —** receiver UNKNOWN + método inexistente → sem diagnóstico
   (SEM025 só quando `isKnownReceiver`, MemberCallTyper.java:384-386).

Regra do plano: **inferência nunca cria declaração implícita; identificador
inexistente deve falhar.** Estes casos são a prioridade P0.

> **STATUS 07/09 (verificado no código + testes, não nesta tabela):** #7
> (namespaces builtin → SEM025), #3 (campo inexistente em classe conhecida)
> e #6 (super.metodoInexistente) estão **CORRIGIDOS** — helper
> `unknownNamespaceMethod` (MemberCallTyper) + gate `isKnownReceiver`
> (SemExpressionTyper); prova `SemanticResolutionTest` (6/6 verde: matriz
> de 12 namespaces + web.app + super + campo + falso-positivo). #8
> (receiver UNKNOWN + método inexistente) é **error-recovery legítimo** —
> sem o tipo do receiver não há como diagnosticar sem falso-positivo;
> manter UNKNOWN. P0 de fallbacks semânticos: **FECHADO**.

## Ordem de execução (ajustada pela auditoria)

- **P0**: ~~fallbacks semânticos~~ **FECHADO 07/09** (status no bloco acima).
- **P1**: GC auto-collect — face cross decomposta em **G-1..G-5** (12/09,
  `native-multiarch.md` §decomposição; G-0 ✅ `356f33b9`, G-1 free-list riscv é
  o próximo degrau sem pré-requisito; G-3 depende do `kof_heap_root_end` da
  S-5-x86, fila bugfix).
- **P2**: PM lockfile+transitivas; debugger locals via JDWP VariableTable;
  LSP hover/references via SymbolTable (hoje textuais; go-to-definition ✅).
- **P3**: cron real (`scheduler.at`); KofScript globals via frontend.
- **P4**: ~~conformance suite estruturada; spec §subtipagem (SG-009)~~ **FECHADO**
  (conformance matrix Feature×4 targets + `ConformanceMatrixTest`/`ConformanceMatrixDocTest`
  como gate de CI — Fase 9; SG-009 subtipagem nominal ✅ SEM021 10/09 — ver linha 25).
- **P5**: web platform, gRPC, auto-hosting (não iniciar antes).

## Evidência bruta

Ver relatório de auditoria 06/09 (3 explorações): stdlib (23 áreas +
paridade por target), tooling (CLI 20 comandos, PM, debugger, KofJS, LSP,
KofScript), semântica (12 fallbacks, SEM025 cobertura, pipeline).
