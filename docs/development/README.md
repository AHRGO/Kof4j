# Development — backlog vivo (só trabalho em desenvolvimento)

> **Base:** `0.3.22-beta` · branch `beta-0.4.0` · **atualizado:** 13/09/2026
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

**Fontes de verdade que NÃO estão aqui (não são backlog):** `docs/status.md`
(o que funciona + gate da suíte), `docs/backend-parity.md` (matriz de
paridade com gaps honestos), `docs/language-reference/specification-gaps.md`
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
| 2 | `refactoring/PLAN-SOLID-500.md` | `EM CURSO` — **F2 ✅ FEITA 12/09** (CompilerDriver 487 ≤500 medido, ratchet sem violação — critério autoritativo cumprido, regra do dono-morto); resta **só F3** (NativeBackend 664) | gate ≤500 virou **ratchet travado no CI** (2652aa45, §140): dívida não cresce e só encolhe; a contagem autoritativa é `wc -l scripts/check_500-baseline.txt` (**9** neste HEAD — era 17 no §140, splits derrubaram p/ 12 e o split-7 `e45140d6` tirou `ExpressionMethodCallLowerer`; atualize APONTANDO p/ o arquivo, não gravando nº que apodrece a cada split) | F3 = extrair ~164 linhas do NativeBackend p/ ≤500 — **BLOQUEADA pela lane GC/tree-shaking viva em `nat/`** (G-0 18:59); abrir quando a fila `nat/` liberar |
| 3 | `native-multiarch.md` (NATIVE002) | `EM CURSO` — ~30 faces cross fechadas sob qemu (41+41 medidas 12/09) | paridade riscv/aarch = condição de estabilidade do release | GC mark-sweep p/ riscv/aarch (faces restantes da §5; JS é outra frente) |
| 4 | `planning-otp-supervision.md` (#83) | `EM CURSO` — 1ª fatia ✅ 11/09 (núcleo+`restartLimit`+`stop` testados JVM+Script; `KofSupervisorE2ETest` 6/6; Native=OTP001 §129, JS=OTP002 §132) | **autORIZADO pela mantenedora** (issue #83) | ~~fatia 2 (limite/shutdown JVM)~~ JÁ FEITOS na 1ª fatia (célula defasada — sincronizada 12/09); fatia S2 (selectAny N-workers) = ratificação DD + design do wrapper de identidade (§128 ✅ 12/09, mas selectAny não devolve QUAL handle morreu — ver atualização 12/09 no plano); promover OTP001/002 só com decisão |
| 5 | `plan-editor-integration.md` (EDI001) | `EM CURSO` — graus 1-3, 4-10, 11, 12 ✅ | único degrau sem dono pendente é tooling | plugin IntelliJ (DAP/LSP já funcionam via CLI) |
| 6 | `plan-stdlib-expansion.md` | `EM CURSO` — S0–S6, S8–S12 ✅ | só o que NÃO depende de decisão anda | nada de código-puro: `pow`/`-lm`, `format`/`boundaries`, S10c estão TODOS na mesa da mantenedora → ver §3 |
| 7 | `PLATFORM-PLAN.md` F4/F5/F7 + `APPLICATION_MODEL.md` I2+ | `EM CURSO` (parciais) | dependem de decisão/prioridade da release | sem próximo passo próprio: entram na medida em que a fila acima fecha |
| — | `security-plan.md` (camadas B/C/D) | `PARTIAL` | cada camada B/C/D tem decisão pendente (ChaCha20 formato, keys, OAuth2) | atacar só com ordem explícita da mantenedora |
| — | `plan-platform-completion.md` (P4/P5), `plan-spring-independence.md` (Fases 12–14) | `PARTIAL` | P4/P5 e Fase 12 dependem do core estável + decisões | idem — não abrir por conta |
| — | registros vivos: `conformance-matrix.md`, `ecosystem-coverage.md`, `roadmap.md`, `roadmap-audit.md`, `KOFUI-AUDIT.md`, `known-bugs.md` | `VIVA` | **não são backlog** — matriz/auditoria/fila que se atualizam junto com cada fechamento | atualizar célula/seção no MESMO commit que fecha o gap |

**Regra R12 (AGENTS.md):** nada de `future/` (plataforma universal, RAII,
package-compiler) abre antes de SYSTEMS fechar (paridade + GC + estabilidade).

---

## 2. Bugs abertos (fila em `known-bugs.md`) — triagem 13/09

**13 seções sem ✅ no cabeçalho** (§9 triado ✅ 13/09 — `nativeLambdaMutableCapture` 1/1) — e a conclusão honesta
(`known-bugs.md:11`): **nenhum item de código-puro-sem-decisão restou na lane**.
Todos pendurados em:

| Grupo | Bugs | Quem destrava |
|---|---|---|
| Decisão da mantenedora (regra 6) | §45 (`planning-finally-return` — JVM/Native/interp), §81, §89, §106, §131, §127-JVM | mantenedora |
| Congelados regra-6 | §94, §101, §117, §129-TLS | ninguém (contrato) |
| Lane alheia | §65/§132 (UI/web/OTP-JS), §104b-ii + §107 restante + §114 (bugfixer — storage-box de record) | donos das lanes |

Corrigidos 12/09: §90 (web, #98), §125, §139, §140 (gate→ratchet), §107-face
escalar, §108, §138, MATH001, TIME002, **§145/§146/§147 (issue #101,
`440730c8` — prova qemu 42+42)**.

---

## 3. Bloqueados por decisão da mantenedora (NUNCA atacar sem ordem)

| Item | Onde | O que espera |
|---|---|---|
| DD-STDLIB-01 — `randomBytes`/`randomChoice` (S10c) | `future/planning-stdlib-array-returns.md` | decisão de dispatch Array/objeto |
| DD-STDLIB-02 — `time.format`/`boundaries` | `planning-stdlib-time-design.md` (caiu 12/09: `addDays`/`diffDays` ✅) | decisão de superfície |
| DD-01 — `finally` no caminho de `return` (JVM/Native/interp) | `planning-finally-return.md` (caiu 12/09: JS ✅ `c727fee`) | mudança de IR 4-backend |
| DD-OTP (restante) | `planning-otp-supervision.md` | promover OTP001 (Native)/OTP002 (JS) |
| `pow`/`-lm`, `roundTo`-mode | `plan-stdlib-expansion.md` | decisão de link/contrato |
| NAT-STR01 (case-map astral), TLS §129, json §106 | `known-bugs.md` | regra 6 / design |

---

## 4. Índice do que está EM DESENVOLVIMENTO aqui

### 4.1 Plataformas & migração (caíram de `future/` 12/09 — código iniciado)

| Arquivo | Estado real | O que falta p/ fechar |
|---|---|---|
| `PLATFORM-PLAN.md` | F1–3/8/9 com código (`ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/007, conformance 11 testes) | F4/F5, F6 (WASM001), F7 |
| `APPLICATION_MODEL.md` | `application { onStart/onShutdown }` ✅ E2E 3 targets; I2 (full-stack) ✅ `FullStackE2ETest` | I3/I4 (distribuído, packaging, System) — Q1/Q2 mantenedora |
| `LEGACY_MIGRATION.md` + `DECOMPILER.md` + `TRANSLATOR.md` + `DIFFERENTIAL_TESTING.md` + `LEGACY_IR.md` | plataforma completa no CLI: `inspect/decompile/translate/compare/migrate` (`Main.java:25-29`), 63 testes kof-cli + `Confidence`/`Type.fromJvmSignature` | cobertura: switch/athrow opacos, `inspect --java` (R5 do audit), IR non-JVM |
| `IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md` | Fases A–H têm código+testes | tiers 6–12 = `future/` (R12) |
| `PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md` | auditorias com R2 (kof.toml × AppManifest) e R5 abertos; tabela da Fase D corrigida 12/09 | R2/R5 → decisões; doc pode consolidar ao fechar |
| `planning-finally-return.md` | JS corrigido (`c727fee` + `finallyReturnJs`) | decisão DD-01 (§3) |
| `planning-stdlib-time-design.md` | `addDays`/`diffDays` nos 5 alvos | decisão `format`/`boundaries` (§3) |

### 4.2 Plans & auditorias vivas

| Arquivo | Estado real | Nota |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUÍDO 13/09 (S-1..S-6.1 + S-7; consolidado em `docs/stdlib/stdlib-loading.md`) | S-5-x86 = fila bugfix (`root_end`), fora do plano |
| `plan-stdlib-expansion.md` | S0–S6, S8–S12 ✅ (MATH001/TIME002 fechados 11/09) | só decisões pendentes (§3) |
| `planning-otp-supervision.md` | 1ª fatia ✅ JVM+Script; gates OTP001/OTP002 honestos | fatia 2 (§1 item 4) |
| `plan-editor-integration.md` | CLI/DAP/LSP/stdout-json ✅ | plugin IntelliJ |
| `native-multiarch.md` | re-auditoria 12/09 sob qemu: ~30 faces cross fechadas | GC riscv/aarch + faces §5 |
| `security-plan.md` | A ✅; B/C/D ❌ (csrf/cors/headers cross, OAuth2, TLS cert, keys) | decisão por camada |
| `plan-platform-completion.md` | P0–P3 ✅; P4 (health/tracing/metrics) ❌; P5: `kof fmt` ✅ 31/08, LSP/VS Code ❌ | app E2E final (blog/API nos 2 targets) fecha o plano |
| `plan-spring-independence.md` | F1–5,7 ✅ (web/json/config/log/db/security v1); F8–11 parciais ([ ] em tracing/pooling/queues/auth); F12 (app web completa) é o teste; F13/14 planejadas | Fase 12 = gatilho; starter só depois |
| `conformance-matrix.md` | matriz Feature×4 targets travada por `ConformanceMatrixTest` (11) + doc-gate | viva: atualiza com cada gap |
| `ecosystem-coverage.md` | G1–G12 com `PARTIAL`/`PLANNED` (events, batch, AI) | referência de cobertura |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monólito→micro) | longo prazo |
| `roadmap-audit.md` | matriz 06/09 + fila P0→P5 (P0 FECHADO 09/09) | re-audit quando algo fecha |
| `KOFUI-AUDIT.md` | UI001-Native (face R6: no-op silencioso) ABERTO | lane UI |
| `known-bugs.md` | 13 abertos (triagem §2 acima; retificado de "14" — contagem conferida seção a seção 13/09) | fila viva |
| `refactoring/PLAN-SOLID-500.md` | F1–2, 4–9 ✅ (**F2 fechada 12/09** — 487 ≤500 medido); **só F3 em curso** (NativeBackend 664, bloqueada pela lane GC em `nat/`); ratchet `check_500-baseline.txt` (dívidas travadas — nº autoritativo = `wc -l` do arquivo; **9** neste HEAD, era 17 no §140) no CI | F3 fecha o plano |

### 4.3 `future/` — só plano, zero código (não é trabalho atual)

| Arquivo | Gatilho p/ cair p/ cá |
|---|---|
| `PLAN-UNIVERSAL-PLATFORM.md` | decisão + SYSTEMS fechado (R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump com `using`/`resource_scope` decidido |
| `planning-stdlib-array-returns.md` (DD-STDLIB-01) | decisão da mantenedora destrava o dispatch |

*(movimentos históricos de 12/09: 13 docs caíram de `future/` p/ cá —
evidência em cada linha de §4.1; snapshot SG 08/09 → `docs/history/`)*

---

## 5. O que NÃO está mais aqui (consolidado 12/09, com prova)

| Saiu p/ | Doc | Prova |
|---|---|---|
| `docs/language-reference/specification-gaps.md` | SG-001–020 + E1–E3 | fila do maintainer COMPLETA (resumo do próprio doc); snapshot antigo → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | níveis 0–4 | query DSL 01/09 (`KofOrmE2ETest` 22), MySQL prepared (`nativeMysqlPreparedBinary`), pooling ✅; DB001/ORM001 vivem na matriz de paridade |
| `docs/architecture/complexity-audit.md` | snapshot 02/09 | números pré-SOLID-500; gate vivo = `scripts/check_500.sh` (ratchet) |
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
