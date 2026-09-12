# Development — Documentos em Andamento (não concluídos)

> **Criado em:** 07/09/2026 · **Origem:** varredura de `docs/` por status `❌` / `🟡` / `PARTIAL` / `NOT STARTED` / `PLANNED` / `TODO` / `gap`
> **Propósito:** separar o que **ainda não está concluído** do que já é referência estável em `docs/`.
> `docs/` mantém só o que é **prova** (comportamento previsto, suíte verde, stable). `development/` é o **backlog vivo** — planos, audits, gaps e roadmaps que guiam o próximo trabalho.

**Regra para agentes:**
- `docs/status.md` + `docs/backend-parity.md` continuam sendo a **fonte de verdade do que funciona hoje** (sempre em `docs/`).
- `development/` é a **fila de prioridade**: se precisa saber *o que falta*, leia aqui primeiro.
- Ao fechar um item: mova o doc correspondente de `development/` de volta para `docs/` (ou marque como `FEITO` e arquive), no mesmo commit que fecha o gap (com prova: teste verde/suíte).

---

## Índice — o que foi movido de `docs/` para `development/`

### 1. Futuro de verdade (`future/` — só plano, zero código)

> **12/09 — varredura da regra dos 3 estados:** dos 17 docs que estavam em
> `future/`, **13 caíram para `docs/development/`** (todos têm código iniciado —
> plataforma de migração `kof inspect/decompile/translate/compare/migrate` com
> 63 testes, `PLATFORM-PLAN` F1–3/8/9 com `KofProjectConfig`+`Target.SCRIPT`+
> matriz de conformidade, `application {}` com E2E nos 3 targets, `addDays`/
> `diffDays` implementados, bug 45 com a face JS corrigida). Em `future/` só
> restam os **3 sem uma linha de código** (ver `future/README.md`).

| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `future/README.md` | regra + índice da pasta | — |
| `future/PLAN-UNIVERSAL-PLATFORM.md` | visão de longo prazo (R1–R12); nenhum `ml`/`bio`/`hpc`/`infra-*` no código | `NOT STARTED` (aguardar SYSTEMS, R12) |
| `future/scoped-resources-plan.md` | RAII leve TIER 2.4 — zero `resource_scope`/`kof_resource`/`using` | `PLANNED` (gated por bump) |
| `future/planning-stdlib-array-returns.md` | DD-STDLIB-01 — `randomBytes`/`randomChoice` não existem no `KofRandom` | `PROPOSED` (decisão mantenedora) |

**Docs de plataforma/migração que moravam em `future/` e agora estão aqui (EM CURSO):**

| Arquivo | Por que caiu (evidência no código) | Estado |
|---|---|---|
| `PLATFORM-PLAN.md` | F1–3+8+9 com código: `ProjectLocator`, `KofProjectConfig` (+teste), `Target.SCRIPT`, PKG006/PKG007 em `CompilerImports`, conformance travada por 11 testes; F6 (WASM) não iniciada | `EM CURSO` |
| `APPLICATION_MODEL.md` | `application { onStart/onShutdown }` parseado (`Parser.java:101-132`) + desugared (`CompilerDesugar.java:247-254`) + E2E nos 3 targets; distribuído (System/packaging) não iniciado | `EM CURSO` |
| `LEGACY_MIGRATION.md` | os 5 comandos existem (`Main.java:25-29`); cobertura de recuperação ainda parcial | `EM CURSO` |
| `DECOMPILER.md` | `Decompile.java` + decoders (~2.1k linhas), `DecompileTest` 45/45; corpo de método complexo ainda → stub honesto | `EM CURSO` |
| `TRANSLATOR.md` | `Translate.java` + lexer próprio, `TranslateTest` 9/9 (output compila e roda); subconjunto Java a ampliar | `EM CURSO` |
| `DIFFERENTIAL_TESTING.md` | `Compare.java`, `CompareTest` 6/6 (stdout/exit/stderr); além de stdout ainda pendente | `EM CURSO` |
| `LEGACY_IR.md` | `Confidence.java` (§4, 5 níveis), `Type.fromJvmSignature` + atributo `Signature` (`367d6c4`); IR non-JVM não iniciada | `EM CURSO` |
| `IMPLEMENTATION_PLAN.md` | Fases A–H têm código+testes; tiers 6–12 não iniciados | `EM CURSO` |
| `ACTION_PLAN.md` | idem — ordem Tiers 0–12; parciais | `EM CURSO` |
| `PLANNING-FUTURE-AUDIT.md` | auditoria com itens abertos (R2 kof.toml × AppManifest, R5 `inspect --java`/switch-athrow); tabela corrigida 12/09 (Fase D estava defasada) | `ABERTO` |
| `planning-future-reconcile.md` | reconcile da branch `planning-future` com a beta — partes aplicadas, registro em curso | `ABERTO` |
| `planning-finally-return.md` | face JS do bug 45 **corrigida** (`c727fee` + `CoreRegressionE2ETest.finallyReturnJs`); DD-01 p/ JVM/Native/interp aguarda decisão | `EM CURSO` (JS feito; decisão pendente) |
| `planning-stdlib-time-design.md` | `addDays`/`diffDays` (o formato D2 do doc) implementados nos 5 alvos (TIME002 11/09); `format`/`boundaries` aguardam decisão | `EM CURSO` (parcialmente decidido) |
| `docs/ui/PLAN-CANVAS-WIDGET.md` | Canvas widget (CANVAS001) — **consolidado; movido p/ `docs/ui/` 12/09** (`5a9cac46` CANVAS001 FECHADO; `UiE2ETest` 29/29 sem exclusões medido hoje; UI009 drawImage `6e3181f`) | `FEITO` |

### 2. Roadmaps & Audits
| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `roadmap.md` | §§8–11 ❌ não implementado (Frontend, Frontend+Backend same project, Architectura, Monólito→Micro) | `PARTIAL` (Fase 0 ✅, resto 🟡/❌) |
| `roadmap-audit.md` | matriz 06/09: 13 itens — 5× `PARTIAL`, 4× `NOT STARTED` | `PARTIAL` |
| `docs/history/roadmap-gap-2026-09-03.md` | **movido p/ docs/history/ 12/09** — gap report datado 03/09 (0.2.6-beta): números e pendências que o roadmap-audit/known-bugs já carregam vivas; as discrepâncias listadas foram corrigidas nas sessões seguintes — é snapshot, não backlog | `HISTÓRICO` |
| `ecosystem-coverage.md` | matriz G1–G12: muitos `PARTIAL`/`PLANNED` (events, messaging, OAuth2, batch, AI) | `PARTIAL` |
| `conformance-matrix.md` | matriz Feature×target (JVM/Native/Script/JS) com exclusões por gap — a célula `collprint` ainda exclui native (record/aninhado = §107/§104b-ii); doc-viva, atualizada a cada gap de paridade | `PARCIAL` (matriz é o registro, não o backlog) |
| `KOFUI-AUDIT.md` | matriz de gaps `UI00x` do kof.ui — UI001-Native no-op silencioso ABERTO (face R6: kof.ui no Native roda sem diagnóstico); demais UI002–009 ✅/decisão | `ABERTO` (UI001-Native) |
| `docs/history/actual-state.md` | **movido p/ docs/history/ 12/09** — snapshot histórico 0.2.6-beta (registro, não backlog) | `HISTÓRICO` |
| `docs/history/language-state.md` | **movido p/ docs/history/ 12/09** — snapshot histórico 02/09 (SG-E2; registro, não backlog) | `HISTÓRICO` |

### 3. Plans de Plataforma
| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `plan-platform-completion.md` | P0–P5: P3 (query DSL) ✅ mas P4–P5 (health/tracing/LSP/debug) pendentes | `PARTIAL` |
| `plan-spring-independence.md` | Fases 5–14: web completa + gRPC planejados, GC pending | `PARTIAL` |
| `planning-switch-expr.md` | **movido p/ `docs/decisions/planning-switch-expr.md` 10/09** (SYN001 FECHADO — nada pendente; concluído não fica em development/) | `FEITO` |
| `planning-mutability.md` | **movido p/ `docs/decisions/planning-mutability.md` 10/09** (DD-02/SEM037/SEM038 aplicados, #42 fechada) | `FEITO` |
| `planning-finally-return.md` | **CAIU de `future/` 12/09** (face JS do bug 45 corrigida `c727fee`; decisão DD-01 p/ JVM/Native/interp pendente) | `EM CURSO` |
| `planning-stdlib-time-design.md` | **CAIU de `future/` 12/09** (DD-STDLIB-02 — `addDays`/`diffDays` implementados nos 5 alvos; `format`/`boundaries` aguardam decisão) | `EM CURSO` |
| `planning-stdlib-array-returns.md` | **movido p/ `docs/development/future/` 11/09** (DD-STDLIB-01 PROPOSED — zero código; trava de dispatch Array/objeto) | `PLANEJADO (future)` |
| `plan-stdlib-expansion.md` | STDLIB universal S1–S12: S1–S8+S10–S12 ✅ 09–11/09 (TIME002 fechado 11/09 fatia B33; MATH001 fechado 11/09 fatia B32); pendentes: S10c (DD-STDLIB-01) + S7 `format`/`boundaries` (decisão de superfície) — `pow`/`roundTo`-mode ag. mantenedora | `EM CURSO` |
| `planning-otp-supervision.md` | DD-OTP-01..13: supervisão OTP one_for_one (issue #83) — recomend. stdlib puro-Kof + fábrica + escalate-callback + flag própria (5 alvos grátis) — decide a mantenedora | `PROPOSED` |
| `PLAN-TREE-SHAKING.md` | **movido de `future/` 12/09** — stdlib por alcançabilidade (issue #97, frente designada pela mantenedora): S-1 (T0) ✅ 12/09 (`ArtifactSize` parser ELF64 + `ArtifactSizeTest` gate 5% + `kof build --print-sizes` — números da §1 do plano travados por teste); S-2 (T1a.1) ✅ 12/09 (`RuntimeSlices` — mapa provides/needs por reflexão derivada do fonte, paridade byte-idêntica; hello-needs = 14/611 símbolos); S-3..S-5 (T1a.2 poda x86→riscv) + S-6 (T2 JS por família) + S-7 (docs consolidadas) pendentes | `EM CURSO` |
| `refactoring/PLAN-SOLID-500.md` | regra ≤500 linhas: Fases 4–8 fechadas, mas F1–3 + 9 com resíduo 502 → 493 | `EM CURSO` |

### 4. Gaps & Bugs
| Arquivo | Por que está aqui | Estado |
|---|---|---|
 | `docs/language-reference/specification-gaps.md` | **movido p/ docs/language-reference/ 12/09** — as 23 entradas SG-001–020+E1–E3 estão TODAS resolvidas (SG-001/002/003/005–020 APLICADOS 06–12/09 com decisão da mantenedora; C/D/E ✅) — referência do que a spec exige, não backlog; o resumo do doc confirma a fila do maintainer COMPLETA | `FEITO` (referência) |
 | `known-bugs.md` | fila viva: abertos atacáveis = §45 (finally+return, lowerers) + §104b-ii (Object.equals/record-em-coleção + storage-box de record no asm, Native); 🟡 PARCIAIS-honestos = §107 (println coleção: face escalar ✅ CORRIGIDA 12/09 nos 3 nativos — `f3b3821c`+cross B39, golden JVM byte-idêntico; restam record/aninhado=`?` e FP-cross=FLT001, ambos recusa visível e ambos pendurados no §104b-ii) ; congelados/regra 6 = §94/§96/§98/§101/§106 (json.encode Map); corrigidos 08–12/09: 1–8/10–17/19–20/26 + 39/44/46/48/50/59/62–64/96–105 + §104c (JS) + §107-JS + §107-escalar (Native x86+riscv+aarch) + §108 + §109–§112 (paridade sweep 11/09) + §138 + MATH001/TIME002 | `ABERTO` (fila §45/§104b-ii; §107/§108/§138 FECHADOS 11–12/09) |
| `security-plan.md` | 18 camadas: A ✅ mas B/C/D com ❌ (cookies, middleware, OAuth2, TLS cert próprio) | `PARTIAL` |

### 5. Native Multiarch
| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `native-multiarch.md` | NATIVE002: core riscv64/aarch64 ✅ 26/26 mas paridade avançada (JSON/DB/HTTP/mq/cache) ❌ + GC riscv/aarch sem | `EM DESENVOLVIMENTO (parcial)` |
| `docs/stdlib/DATABASE_VISION.md` | **movido p/ docs/stdlib/ 12/09** — os '❌' da linha antiga estavam FALSOS: nível 3 (query DSL tipada) implementado 01/09 (`KofOrmE2ETest` 22, `User.query(db){...}`→`db.query<T>`), pooling ✅, MySQL prepared binário ✅ 03/09 (`nativeMysqlPreparedBinary`); DB001/ORM001 em riscv/aarch+JS são gaps honestos já na matriz de paridade, não trabalho desta doc | `FEITO` (visão realizada) |
| `docs/architecture/complexity-audit.md` | **movido p/ docs/architecture/ 12/09** — auditoria fotográfica de 02/09 (0.2.6-beta, 810 testes); os números dela (NativeRuntime 17.7k, CompilerDriver 8.2k) já não existem — as classes foram splitadas pelo PLAN-SOLID-500; o acompanhamento vivo do gate ≤500 é `scripts/check_500.sh` + `refactoring/PLAN-SOLID-500.md`, não esta doc | `HISTÓRICO` (registro) |

---

## O que ficou em `docs/` (concluído / referência estável)

Estes **não** foram movidos — são prova ou referência estável:

| Arquivo | Por que ficou |
|---|---|
| `docs/status.md` | gate da suíte (910 testes) + build — fonte de verdade do loop autônomo |
| `docs/backend-parity.md` | matriz JVM×Native×JS — referência de paridade (gaps com código, mas matriz é estável) |
| `docs/architecture/architecture.md` | ADR multi-target (atualizado 06/09, SG-E1 corrigido) |
| `docs/architecture/compiler-architecture.md` | pipeline real frontend→IR→backends (fonte atual) |
| `docs/stdlib/security.md` | auditoria v1 + matriz (G9 fechado) |
| `docs/stdlib/stdlib.md` + `docs/stdlib/*.md` | stdlib estável (kof.*) |
| `docs/language-reference/concurrency.md` | spawn/await/channel/scheduler (CONC003 fechado) |
| `docs/language-reference/concurrency-memory-model.md` | SG-020 — spec SC + 5 bordas HB (ADOTADA 09/09, validada 10/09 — KofConcurrency2Test; movida p/ docs/ 11/09: nada pendente) |
| `docs/stdlib/observability.md`, `docs/architecture/performance.md`, `docs/philosophy.md` | referência estável |
| `docs/debugging/*` (debugging, debugger-architecture, debug-adapter, faces jvm/native/js) | DAP MVP (Fase 3) — parcial mas tooling base estável |
| `docs/stdlib/http.md`, `docs/stdlib/stdlib-*.md`, `docs/runtime/*` | runtime models (STRING/ARRAY/INHERITANCE completos) |
| `docs/language-reference/*` | spec extraída do código + probes (parcial mas separada como linguagem≠compilador) |
| `docs/targets/*`, `docs/tooling/*`, `docs/ui/*`, `docs/distribution/*` | docs por domínio (estáveis) |
| `docs/distribution/releases.md`, `docs/distribution/LICENSING.md`, `docs/comparison/*` (kof-vs-java, KOF_VS_SPRING) | histórico/licença/comparativo |

> **Critério de aceite seletivo:** um doc foi para `development/` **se** (a) seu título/contéudo declara `PLANNED`/`NOT STARTED`/`PARTIAL`/`EM CURSO`/`EM DESENVOLVIMENTO`/`TODO`/`❌`/`🟡`/`gap` **ou** (b) ele é um **plano/roadmap/audit** cujo propósito é listar o que falta (não o que funciona). Docs que apenas *mencionam* gaps mas cujo corpo é referência estável (ex.: `backend-parity.md` lista gaps mas a matriz é a referência oficial) ficaram em `docs/`.

---

## Como usar (para o agente autônomo)

```
1. LEIA docs/status.md + docs/backend-parity.md          → o que funciona (gate)
2. LEIA development/roadmap-audit.md + development/roadmap.md
      + language-reference/specification-gaps.md         → o que falta (fila P0→P5)
3. ESCOLHA o maior valor SEM dono EM CURSO no DOING.md
4. EXECUTE um escopo → teste → commit → atualize DOING.md
5. AO FECHAR: mova o doc de development/ de volta para docs/ no mesmo commit
```

**Sincronização:** `docs/` e `development/` são versionados juntos. Pull antes de cada commit (`git fetch && git pull --rebase --autostash`) — se outro agente moveu um doc de `development/` para `docs/` (item fechado), você verá o rename no rebase.

**Não confundir:** `training/` + `learn/` + `docs/` = **corpus estável** (comportamento previsto). `development/` = **backlog vivo** (trabalho que ainda não é comportamento previsto). Nunca mude comportamento congelado via `development/` sem bump + doc (regra 6).
