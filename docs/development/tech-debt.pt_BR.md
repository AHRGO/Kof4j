# Dívida técnica — ledger histórico (da mantenedora)

[English](tech-debt.md) | [Português](tech-debt.pt_BR.md)

**Status:** **ABERTO — documento de trabalho da mantenedora (aberto 23/09).**
A mantenedora corrige e edita este arquivo diretamente e toma aqui as
decisões importantes. Agentes NÃO fecham itens deste ledger sozinhos:
todo item termina ou numa decisão da mantenedora (regra 6, registrada em
`DECISIONS.md`) ou num fix da lane dona com prova. Regra: um item = um
dono + uma prova; nenhum item se fecha editando este texto.

**Escopo:** só dívida REAL e MEDIDA (gate falhando, seção `§NNN` aberta,
entrada de baseline, ou sintoma reproduzido). Refactor aspiracional sem
gate falhando NÃO é dívida — vai para `docs/development/future/`.

**Cadeia de autoridade por item:** `known-bugs.md §NNN` (sintoma + causa)
→ lane dona no `DOING.md` → prova (teste + suíte) → esta linha vira ✅.
Os gates que nomeiam a dívida: `check_500.sh`,
`check_known_bugs_status.sh` (6 vivos 23/09), `check_release_050_gate.sh`
(cond. 7 = `bugs_gaps`), baseline do CodeQL (`codeql-baseline.txt`).

---

## 1. Bugs vivos (§NNN com faces abertas — 6, medido 23/09)

| § | Face | Desde | Dono / lane | O que o fix precisa provar |
|---|---|---|---|---|
| §205 | print heterogêneo de `Object` no Native (caso direto fixado fatia 1; face boxed-print = residual §104b-ii) | 15/09 🟡 PARCIAL | nat | caminho de print nativo de boxed/`Object`; prova = célula de conformidade verde nos 4 alvos |
| §248 | default methods de interface caídos no JS (`TypeError`) + Native (`null`, exit 0); hoje só JVM | 15/09 🔴 ABERTO | compiler `.22` | precisa de **decisão de escopo** (regra 6) antes do código |
| §271 | DISPATCH de interface genérica: `invokeinterface …(Object)Object` apagado, sem bridge no impl → `NoSuchMethodError` | 18/09 🔴 ABERTO | compiler `.22` | rio do Cluster A, **regra 6** — decisão de ABI de bridge primeiro |
| §278 | Android recusa `kof.security`/`kof.gpu` (`SECN00x`/`GPU001`); face `kof.db`/`kof.orm` CORRIGIDA 20/09 (DB-2) | 17/09 🟡 PARCIAL | gaps-db `.15` | stacks que rodem no Android, ou recusa permanente honesta |
| §283 | `time.interval`/`scheduler.every` no aarch64 sem cancel nunca sai sob qemu | 18/09 🟡 ABERTO | nat | pré-existente, ortogonal ao §253-B; prova = run qemu terminando |
| §423 | channels NUNCA portados p/ Native riscv64/aarch64 (link `undefined reference to kof_channel_*`; NAT005 honesto no lowering) | 21/09 🟡 ABERTO | nat | o port cross, ou manter o diagnóstico declarado |

Referência fechada (NÃO reabrir sem regressão): §444 ✅ 23/09 (ramo
TypeVariable no `valueOf` x86), §446 ✅ 22/09 (split `SemBinaryResultTyper`
+ `SemAssignmentAnalyzer`), §447 (box de literal Bool no Native), §442
(fatias §280), D6 FFI todas as fatias (spec promovida para
`docs/ffi-abi-structs.md` 23/09), C3-residual do kof-c (struct
multi-eightbyte, 23/09).

## 2. Gate de tamanho (`check_500.sh` — medido 23/09)

- **VERMELHO (bloqueia merge):** `nat/NativeBackend.java` 573 → **603**
  (cruzou 600; split obrigatório, precedente §442/§446).
- **Tolerado 500–599 (dívida viva, planeje o split antes de encostar em
  600):** `CompilerPipeline` 588 (−12 p/ vermelho), `CollectionCallLowerer`
  584 (−16), `CompilerTypes` 581 (−19), `ExpressionBinaryLowerer` 576,
  `ExpressionLowerer` 585, `ExpressionTyper` 550, `CompilerComparisons`
  559, `RuntimeOrm7` 585 (−15), `KofInterpreter` 565, `CompilerCaptureScanner`
  568, `ExpressionMethodCallLowerer` 526, `CompilerDriverState` 528,
  `KofCParser` 504, `KofDebugJvmSession` 516, `RuntimeOrm5` 517,
  `RuntimeOrm8` 504, `SemExpressionTyper` 504 (+ arquivo de baseline
  `check_500-baseline.txt` de 47 linhas — nunca cresce na faixa tolerada).
- **Ordem permanente:** quem empurrar um arquivo além de 600 é dono do
  split na mesma unidade (behavior-preserving + mesma suíte verde).

## 3. Gaps honestos por design (NÃO são bugs — manter o diagnóstico, R6)

Callback/upcall no Native (`FFI001`, sem mecanismo), `String[]`→`char**` +
`Buffer(U8)` nativo + `T[]`/`Buffer` no cross (`FFI001`), struct
float/HFA/`> 16 B` no cross (`FFI001`), superfície `Buffer`/`Handle`
D6-1=B (regra 11/regra 6 — spec-first), variadics (decidido: sem
variadics gerais), `long double`/bitfields/packed (gap codes próprios
quando reivindicados). Estes só fecham por decisão da mantenedora +
fatia vertical, nunca silenciando o diagnóstico.

## 4. Dívida de processo (causada por agentes, já com regras permanentes)

- **SHAs fantasmas / docs stale:** padrão corrigido 22/09 (SHA órfão
  `/tmp/w388` no README; §280 re-medido na árvore integrada).
  Regra permanente: golden/prova pinados ao SHA de código, suíte re-rodada
  no tip integrado antes de declarar verde.
- **Colisões na árvore compartilhada:** duas lanes + `git stash` é global
  (WIP perdido 22/09); worktrees `/tmp` evaporam na queda de luz (regra 9).
  Regra permanente: commitar só paths próprios, `sync-push.sh`, claim no
  DOING no mesmo commit, `preserve-both-sides` no rebase.
- **Backlog de triagem CodeQL:** baseline de 69 linhas + #973 (param
  `structs` morto em `KofCParser.checkParamBounds`, dono frente kof-c) —
  remover a linha só após o fix raiz + re-scan.
- **Fragilidade de selftest:** o selftest do gate 0.5.0 pinava um doc
  fixture (`ffi-abi-structs`) que já foi promovido — corrigido 23/09 para
  `db-parity-plan`. Regra permanente: fixtures pinam nomes ESTÁVEIS.

## 5. Decisões que a mantenedora toma aqui (perguntas abertas, 23/09)

1. **Escopo §248:** default methods só-JVM, ou portar JS + Native?
2. **ABI de bridge §271:** emitir bridges nos impls de interface
   genérica (linha de ABI de erasure), ou rejeitar em compile?
3. **Android §278:** portar as stacks `kof.security`/`kof.gpu`, ou
   declarar a recusa permanente?
4. **Channels cross §423:** agendar o port riscv64/aarch64, ou manter o
   NAT005?
5. **Ordem de split:** `NativeBackend` 603 primeiro, ou os tolerados mais
   perto do vermelho (`CompilerPipeline` 588, `RuntimeOrm7` 585)?
6. **Superfície D6-1=B:** abrir agora a frente `struct` mutável by-ref, ou
   manter estacionada (regra 11 spec-first)?


**Decidido 23/09 (`D-TECHDEBT-23/09`, múltipla escolha da mantenedora):** §248 = portar JS+Native · §271 = emitir bridges · §278 = portar as stacks · §423 = agendar o port · split = todos em lote · D6-1=B = abrir agora. Fila: `roadmap.pt_BR.md` TIER 13.
Cada resposta pousa como: linha de decisão em `DECISIONS.md` (+PT) + fila
em `roadmap.md` §23/DOING no MESMO commit (regra: decidir sem registrar =
invisível; registrar sem abrir fila = morto).
