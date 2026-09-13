# Development — backlog vivo (só trabalho em desenvolvimento)

> **Base:** `0.4.0-beta` · branch `beta-0.4.0` · **atualizado:** 13/09/2026
> **Suíte medida neste HEAD:** `1611` testes (1439 kof-compiler + 31 kof-script
> + 5 kof-c-compiler + 136 kof-cli), **0 falhas** (13 erros = só `node` ausente, ambientais; o §149 JS foi corrigido — era `KofRandomTest`), 5 skip (guardas de
> toolchain/node) — com cross riscv/aarch 42+42 sob qemu real (G-0/§142 somaram os
> testes de header/OOM). Refold da concatenação do `NativeRiscvAsm` para
> `<clinit>` (anti-pattern novo `constant-folded-runtime-asm.md`) verde no
> gate `gate1585.log` (HEAD 54da1325).
> **Regra dos 3 estados (`AGENTS.md`):** `docs/` = implementado/decidido ·
> `development/` = **trabalho técnico pendente** · `development/future/` =
> **só plano, zero código**. Concluiu → move p/ submódulo de `docs/` no mesmo
> commit; iniciou → cai p/ cá. A varredura de 12/09 (`655afa6b`) moveu 13 docs
> de `future/` p/ cá (todos com código) e 4 concluídos p/ `docs/`.
> **Refactor de clareza 13/09 (mantenedora):** bugs/gaps/matrizes →
> `docs/bugs-and-gaps/` (linhas 2, 41, §2, §3, §4.2, §5); planos **parados por
> decisão** → `docs/development/decision-pending/` (§3 inteiro, §4.1/§4.2
> linhas deles). Este README lista o que **anda**; o que espera ordem mora em
> `decision-pending/` e NÃO puxa prioridade sem a mantenedora (regra 6).

**Fontes de verdade que NÃO estão aqui (não são backlog):** `docs/status.md`
(o que funciona + gate da suíte), `docs/backend-parity.md` (matriz de
paridade com gaps honestos), `docs/bugs-and-gaps/specification-gaps.md`
(SG-001–020 — fila do maintainer COMPLETA 12/09, virou referência).

---

## 1. Ordem de execução dos planos (fila oficial da lane development)

> Critério: (1) frente designada pela mantenedora > (2) saúde do gate >
> (3) trabalho de código-puro sem decisão > (4) itens bloqueados = NÃO atacar
> (regra 6). Itens de registro vivo (matrizes/auditorias) não têm "fim" —
> atualizam-se a cada gap fechado, não puxam prioridade.

| # | Plano | Estado | Por que nesta posição | Próximo passo concreto |
|---|---|---|---|---|
| 1 | `stdlib/PLAN-TREE-SHAKING.md` (#97) | ✅ **CONCLUÍDO 13/09** — S-1..S-6.1 ✅ (S-6.1 mergeado `0104f6d6` PR #106) + S-7 ✅ (consolidado em `docs/stdlib/stdlib-loading.md`, movido p/ `docs/stdlib/`) | frente designada 11/09, fechada; S-5-x86 segue fila bugfix (`root_end`, fora deste plano) |
| 2 | `refactoring/PLAN-SOLID-500.md` | `EM CURSO` — **F2 ✅ FEITA 12/09** (CompilerDriver 487 ≤500 medido, ratchet sem violação — critério autoritativo cumprido, regra do dono-morto); resta **só F3** (NativeBackend 664) | gate ≤500 virou **ratchet travado no CI** (2652aa45, §140): dívida não cresce e só encolhe; a contagem autoritativa é `wc -l scripts/check_500-baseline.txt` (**8** neste HEAD — era 17 no §140, splits derrubaram p/ 12 e o split-7 `e45140d6` tirou `ExpressionMethodCallLowerer`; atualize APONTANDO p/ o arquivo, não gravando nº que apodrece a cada split) | F3 = extrair ~164 linhas do NativeBackend p/ ≤500 — **BLOQUEADA pela lane GC/tree-shaking viva em `nat/`** (G-0 18:59); abrir quando a fila `nat/` liberar |
| 3 | `native-multiarch.md` (NATIVE002) | `EM CURSO` — ~30 faces cross fechadas sob qemu (41+41 medidas 12/09) | paridade riscv/aarch = condição de estabilidade do release | GC mark-sweep p/ riscv/aarch (faces restantes da §5; JS é outra frente) |
| 4 | `planning-otp-supervision.md` (#83) | `EM CURSO` — 1ª fatia ✅ 11/09 (núcleo+`restartLimit`+`stop`) + **S2-JVM ✅ 13/09** (`startAll`/`lacoUnico` + wrapper de identidade; `KofSupervisorE2ETest` 8/8; Native=OTP001 §129, JS=OTP002 §132) | **DD-OTP RATIFICADAS 13/09** (opção 1a: S2 JVM; riscv/aarch PARTIAL) — ver §3 | S2-Native x86 pendente do §129 (unwind cross-thread, lane nat); promover OTP001/002 só com a face cross decidida |
| 5 | `plan-editor-integration.md` (EDI001) | `EM CURSO` — graus 1-3, 4-10, 11, 12 ✅ | único degrau sem dono pendente é tooling | plugin IntelliJ (DAP/LSP já funcionam via CLI) |
| 6 | `plan-stdlib-expansion.md` | `EM CURSO` — S0–S6, S8–S12 ✅ | só o que NÃO depende de decisão anda | `pow`/`-lm` e S10c (`randomBytesHex`) **DECIDIDOS 13/09** → implementar (§3); só `format`/`boundaries` (DD-STDLIB-02) segue na mesa da mantenedora |
| 7 | `decision-pending/PLATFORM-PLAN.md` F4/F5/F7 + `decision-pending/APPLICATION_MODEL.md` I2+ | `PARADO` (parciais) | dependem de decisão/prioridade da release | sem próximo passo próprio: entram na medida em que a fila acima fecha |
| — | `decision-pending/security-plan.md` (camadas B/C/D) | `PARTIAL` | cada camada B/C/D tem decisão pendente (ChaCha20 formato, keys, OAuth2) | atacar só com ordem explícita da mantenedora |
| — | `decision-pending/plan-platform-completion.md` (P4/P5), `decision-pending/plan-spring-independence.md` (Fases 12–14) | `PARTIAL` | P4/P5 e Fase 12 dependem do core estável + decisões | idem — não abrir por conta |
| — | registros vivos: `conformance-matrix.md`, `ecosystem-coverage.md`, `KOFUI-AUDIT.md`, `known-bugs.md` (em `docs/bugs-and-gaps/`); `roadmap.md` (aqui); `roadmap-audit.md`/`complexity-audit.md` (em `docs/audits/`) | `VIVA` | **não são backlog** — matriz/auditoria/fila que se atualizam junto com cada fechamento | atualizar célula/seção no MESMO commit que fecha o gap |

**Regra R12 (AGENTS.md):** nada de `future/` (plataforma universal, RAII,
package-compiler) abre antes de SYSTEMS fechar (paridade + GC + estabilidade).

---

## 2. Bugs abertos (fila em `docs/bugs-and-gaps/known-bugs.md`) — triagem 13/09

**14 seções sem ✅ no cabeçalho** (§127-JVM, §155 e §94 fechados 13/09; §156 aberto 13/09 — infra de tipos; §157-160 e §65 fechados/NÃO-REPRODUZ 13/09) — e a conclusão honesta
(`known-bugs.md:11`): **há UM item de código-puro-sem-decisão: §156**.
Todos pendurados em:

| Grupo | Bugs | Quem destrava |
|---|---|---|
| Decisão ratificada 13/09 — implementação pendente | §81, §89, §106, §117, §131, §161/NAT-STR01 (§45/DD-01 FECHADO 13/09 — ver `docs/decisions/DD-01-finally-return.md`) | fila ratificada / lanes executoras |
| Congelado regra-6 | §101 | ninguém (contrato) |
| Lane alheia | §104b-ii + §107 restante + §114 (bugfixer — storage-box de record), §129 (lane nat), §132 (OTP-JS) | donos das lanes |
| Infra de tipos (sem dono) | §156 (`List` heterogêneo de lambdas → CCE JVM) | agente de tipos |

Corrigidos 13/09: **§94** (EQ/NE de Double/Float no interpretador agora IEEE —
célula `stdsqrt` 4/4 sem exclusão), **§127-JVM** (cast p/ tipo-função →
interface SAM sintética; `LambdaE2ETest.castToFunctionTypeJvm/Native`),
**§155** (tipo-função como type-arg → parser preserva os espaços do type-ref;
`LambdaE2ETest.declaredFunctionTypeListJvm/Native`). Corrigidos 12/09: §90 (web, #98), §125,
§139, §140 (gate→ratchet), §107-face
escalar, §108, §138, MATH001, TIME002, **§145/§146/§147 (issue #101,
`440730c8` — prova qemu 42+42)**.

---

## 3. Bloqueados por decisão da mantenedora (NUNCA atacar sem ordem)

> Os "Onde" abaixo: `decision-pending/` = `docs/development/decision-pending/`;
> `known-bugs.md` = `docs/bugs-and-gaps/known-bugs.md`. Ao decidir, o item sai
> de `decision-pending/` (volta p/ `development/` se vira código, p/ `docs/` se
> já estava pronto).

| Item | Onde | O que espera |
|---|---|---|
| DD-STDLIB-01 — `randomBytes`/`randomChoice` (S10c) | `docs/stdlib/DD-STDLIB-01-array-returns.md` (FECHADO 13/09, movido p/ docs/) | ✅ IMPLEMENTADO 13/09 (opção 6a: `randomBytesHex` alias de `hex` + choice=idiom; S10c FECHADO) |
| DD-STDLIB-02 — `time.format`/`boundaries` | `planning-stdlib-time-design.md` (caiu 12/09: `addDays`/`diffDays` ✅) | decisão de superfície (AINDA PENDENTE) |
| DD-01 — `finally` no caminho de `return` | `docs/decisions/DD-01-finally-return.md` (FECHADO 13/09, movido p/ docs/) | ✅ IMPLEMENTADO 13/09 (opção 4a: FinallyFrame na IR + gates finallyReturnJvm/Js; suíte 1627/0; bug 45 FECHADO) |
| DD-OTP (restante) | `planning-otp-supervision.md` | ✅ RATIFICADAS 13/09 (opção 1a: S2 JVM + wrapper `(id, resultado)`; riscv/aarch PARTIAL) — implementar S2 |
| `pow`/`-lm`, `roundTo`-mode | `plan-stdlib-expansion.md` | ✅ DECIDIDO 13/09 (opção 7a: link `-lm` aprovado) — implementar `pow` |
| NAT-STR01 (case-map astral) | `known-bugs.md` §161 / conformance-matrix | ✅ ABERTO POR DECISÃO 13/09 — implementar UTF-8 astral nos nativos |
| §129 (unwind cross-thread via TLS) | `known-bugs.md` | ✅ ABERTO POR DECISÃO 13/09 — lane nat |
| json §106 | `known-bugs.md` | ✅ DECIDIDO 13/09 (opção 2b: chaves sorted) — implementar |

---

## 4. Índice do que está EM DESENVOLVIMENTO aqui

### 4.1 Plataformas & migração (caíram de `future/` 12/09 — código iniciado; os `~~riscados~~` já migraram p/ `decision-pending/` no refactor 13/09)

| Arquivo | Estado real | O que falta p/ fechar |
|---|---|---|
| ~~`PLATFORM-PLAN.md`~~ → `decision-pending/` | F1–3/8/9 com código (`ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/007, conformance 11 testes) | F4/F5, F6 (WASM001), F7 — parado por decisão |
| ~~`APPLICATION_MODEL.md`~~ → `decision-pending/` | `application { onStart/onShutdown }` ✅ E2E 3 targets; I2 (full-stack) ✅ `FullStackE2ETest` | I3/I4 (distribuído, packaging, System) — Q1/Q2 mantenedora |
| `LEGACY_MIGRATION.md` + `DECOMPILER.md` + `TRANSLATOR.md` + `DIFFERENTIAL_TESTING.md` + `LEGACY_IR.md` | plataforma completa no CLI: `inspect/decompile/translate/compare/migrate` (`Main.java:25-29`), 63 testes kof-cli + `Confidence`/`Type.fromJvmSignature` | cobertura: switch/athrow opacos, `inspect --java` (R5 do audit), IR non-JVM |
| `IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md` | Fases A–H têm código+testes | tiers 6–12 = `future/` (R12) |
| ~~`PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md`~~ → `docs/audits/` | comparação branch `planning-future`×beta **encerrada 13/09** — nada de código aberto próprio mora nelas: R2 vive em `decision-pending/APPLICATION_MODEL.md`+`PLATFORM-PLAN.md`; R5 no cluster migração (`DECOMPILER.md`/`LEGACY_IR.md` Fase C) | — (fora de `development/`) |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | FECHADO 13/09 (FinallyFrame IR + gates; bug 45 CORRIGIDO, suíte 1627/0) | — (fora de `development/`) |
| ~~`planning-stdlib-time-design.md`~~ → `decision-pending/` | `addDays`/`diffDays` nos 5 alvos | decisão `format`/`boundaries` (§3) |

### 4.2 Plans & auditorias vivas

| Arquivo | Estado real | Nota |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUÍDO 13/09 (S-1..S-6.1 + S-7; consolidado em `docs/stdlib/stdlib-loading.md`) | S-5-x86 = fila bugfix (`root_end`), fora do plano |
| `plan-stdlib-expansion.md` | S0–S6, S8–S12 ✅ (MATH001/TIME002 fechados 11/09) | só decisões pendentes (§3) |
| `planning-otp-supervision.md` | 1ª fatia ✅ JVM+Script; **S2-JVM ✅ 13/09** (`startAll`/`lacoUnico`); gates OTP001/OTP002 honestos | S2-Native x86 pendente do §129 (lane nat) |
| `plan-editor-integration.md` | CLI/DAP/LSP/stdout-json ✅ | plugin IntelliJ |
| `native-multiarch.md` | re-auditoria 12/09 sob qemu: ~30 faces cross fechadas | GC riscv/aarch + faces §5 |
| ~~`security-plan.md`~~ → `decision-pending/` | A ✅; B/C/D ❌ (csrf/cors/headers cross, OAuth2, TLS cert, keys) | decisão por camada |
| ~~`plan-platform-completion.md`~~ → `decision-pending/` | P0–P3 ✅; P4 (health/tracing/metrics) ❌; P5: `kof fmt` ✅ 31/08, LSP/VS Code ❌ | app E2E final (blog/API nos 2 targets) fecha o plano |
| ~~`plan-spring-independence.md`~~ → `decision-pending/` | F1–5,7 ✅ (web/json/config/log/db/security v1); F8–11 parciais ([ ] em tracing/pooling/queues/auth); F12 (app web completa) é o teste; F13/14 planejadas | Fase 12 = gatilho; starter só depois |
| ~~`conformance-matrix.md`~~ → `docs/bugs-and-gaps/` | matriz Feature×4 targets travada por `ConformanceMatrixTest` (11) + doc-gate | viva: atualiza com cada gap |
| ~~`ecosystem-coverage.md`~~ → `docs/bugs-and-gaps/` | G1–G12 com `PARTIAL`/`PLANNED` (events, batch, AI) | referência de cobertura |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monólito→micro) | longo prazo |
| ~~`roadmap-audit.md`~~ → `docs/audits/roadmap-audit.md` | matriz 06/09 + fila P0→P5 (P0 FECHADO 09/09) | re-audit quando algo fecha |
| ~~`KOFUI-AUDIT.md`~~ → `docs/bugs-and-gaps/` | UI001-Native (face R6: no-op silencioso) ABERTO | lane UI |
| ~~`known-bugs.md`~~ → `docs/bugs-and-gaps/` | 14 abertos (triagem §2 acima; §127-JVM, §155, §94, §157-160 e §65 fechados/NÃO-REPRODUZ 13/09) | fila viva |
| `refactoring/PLAN-SOLID-500.md` | F1–2, 4–9 ✅ (**F2 fechada 12/09** — 487 ≤500 medido); **só F3 em curso** (NativeBackend 664, bloqueada pela lane GC em `nat/`); ratchet `check_500-baseline.txt` (dívidas travadas — nº autoritativo = `wc -l` do arquivo; **8** neste HEAD, era 17 no §140) no CI | F3 fecha o plano |

### 4.3 `future/` — só plano, zero código (não é trabalho atual)

| Arquivo | Gatilho p/ cair p/ cá |
|---|---|
| `PLAN-UNIVERSAL-PLATFORM.md` | decisão + SYSTEMS fechado (R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump com `using`/`resource_scope` decidido |

*(DD-STDLIB-01 `planning-stdlib-array-returns.md` **saiu de `future/` 13/09** — decisão 6a ratificada, implementado e movido p/ `docs/stdlib/DD-STDLIB-01-array-returns.md`.)*

*(movimentos históricos de 12/09: 13 docs caíram de `future/` p/ cá —
evidência em cada linha de §4.1; snapshot SG 08/09 → `docs/history/`)*

---

## 5. O que NÃO está mais aqui (consolidado 12/09, com prova)

| Saiu p/ | Doc | Prova |
|---|---|---|
| `docs/bugs-and-gaps/specification-gaps.md` | SG-001–020 + E1–E3 | fila do maintainer COMPLETA (resumo do próprio doc); snapshot antigo → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | níveis 0–4 | query DSL 01/09 (`KofOrmE2ETest` 22), MySQL prepared (`nativeMysqlPreparedBinary`), pooling ✅; DB001/ORM001 vivem na matriz de paridade |
| `docs/audits/complexity-audit.md` | snapshot 02/09 | números pré-SOLID-500; gate vivo = `scripts/check_500.sh` (ratchet) |
| `docs/history/roadmap-gap-2026-09-03.md` | gap report datado | pendências vivem em roadmap-audit/known-bugs |
| `docs/decisions/` | `planning-switch-expr`, `planning-mutability` | SYN001, DD-02/SEM037/SEM038 aplicados |
| `docs/ui/PLAN-CANVAS-WIDGET.md` | CANVAS001 | `UiE2ETest` 29/29 sem exclusões |

---

## 6. Como usar (agente autônomo)

```
1. LEIA docs/status.md + docs/backend-parity.md            → o que funciona (gate)
2. LEIA a fila §1 deste README + DOING.md (donos)          → o que falta, sem colisão
3. BUGS: known-bugs.md §Aberto só com dono na mesa; decisão → §3, não editar
4. EXECUTE um escopo → teste (suíte com -Dmaven.test.failure.ignore=true)
   → commit com DOING.md atualizado → mova doc p/ docs/ se FECHOU
5. RE-DISPARO: sem item na fila §1 sem dono E suíte verde → RECUSE
   (condição de estabilidade AGENTS.md)
```

**Sincronização:** `git fetch && git pull --rebase --autostash` antes de TODO
commit; releia este README depois do pull (outro agente pode ter fechado um
item da fila). `DOING.md` marca dono/estado; este README é a **fila**.

**Não confundir:** `training/` + `learn/` + `docs/` = corpus estável.
`development/` = trabalho que ainda não é comportamento previsto. Mudança de
contrato congelado nunca passa por aqui sem bump + decisão (regra 6).
