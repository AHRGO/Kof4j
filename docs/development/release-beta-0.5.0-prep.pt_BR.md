[English](release-beta-0.5.0-prep.md) | [Português](release-beta-0.5.0-prep.pt_BR.md)

# Release 0.5.0 — preparo (branch `beta-0.5.0`)

Decisão: `DECISIONS.md` §D-BRANCH-0.5.0 (20/09, ordem da mantenedora). Branch
ativa é `beta-0.5.0`; `beta-0.4.0` só recebe pousos em voo e preparo de
release. Este doc é a fila — fica em `docs/development/` até o corte (regra
dos três estados).

## Checklist (ordenado — número da versão e tag são decisão da mantenedora, regra 6)

1. [ ] Pousar o que está em voo: §374/#553 (`.22` — WIP em
       `JvmOpCollections`), §371/#550 (CLI cross build), §378/#554 (gate
       docs). Cada um fecha com prova nos 4 alvos; a lane docs faz ff da
       `beta-0.5.0` após cada pouso na 0.4.0.
2. [ ] Dívida CodeQL (#555): **TRIAGEM FECHADA 20/09 (unidade I, §385)** —
       os 40 da janela: 13 fixados no código com teste alvo, 26 descartados
       com motivo (25 harness `src/test` + FP JEP 443 #876), #938 da tooling
       lane. O portão agora é por BASELINE (`scripts/codeql-baseline.txt`:
       só alerta NOVO bloqueia; skip exige motivo e deixa rastro; ignorado
       em CI) — `scripts/codeql-gate.sh --fast` já mede VERDE sem skip
       (rc=0). Resta para marcar [x]: ff da `q555` + primeiro re-scan fechar
       os 14 `open` tolerados por id no baseline (podar as linhas então) e
       #563 (família src/test no CI) seguir na fila própria.
3. [ ] Bump de versão: **DECIDIDO 20/09/2026 — o release sai como
       `0.5.0-beta`** (sufixo beta mantido, sem codename; adendo do
       `D-RELEASE-0.5.0-GATE`). `VERSION`/`pom.xml` já estão em `0.5.0-beta`;
       restam só o CHANGELOG/tag. Conferir referências à versão codificadas
       (javadoc/testes citam a versão do artefato) ANTES do bump; nunca edição
       unilateral. **Auditado 21/09 (9093): limpo** — os hits `0.4.0`
       restantes são comentários de procedência (quando um port pousou) e
       `beta-0.4.0` usado como *nome de branch* pelo `codeql-gate.sh`
       (monitora as duas) e por fixtures de teste; nenhum codifica a versão do
       artefato.
4. [ ] Corte do CHANGELOG (EN+PT): seção `0.5.0` reunindo os bullets não
       lançados; cabeçalho `Version:` do `AGENTS.md`(+PT) atualizado no
       mesmo commit. (As lanes podem redigir já; o corte segue esperando as
       sete condições.)
5. [ ] Tally: `'Current build: **N**'` em `docs/backend-parity.md`(+PT) a
       partir da primeira CI Build+Tests hospedada VERDE no tip da release
       (contagem do log do job, nunca memória).
6. [ ] Prova de estabilidade: suíte completa 0F/0E + matriz de conformidade
       5/5 MEDIDOS no candidato à tag (AGENTS §Estabilidade — tag só com
       verde).
7. [ ] Tag + release notes (EN+PT); declarar `beta-0.4.0` fechada exceto
       pela lista de fixes residuais. **O `main` fica congelado até este
       release** (mantenedora 20/09/2026): os 12 alertas CodeQL pré-fix do
       `main` são portados no dia do release, não antes; o gate mede a
       `beta-0.5.0`.

## Issues abertas que viajam para `beta-0.5.0`

#550 (§371), #553 (§374), #554 (§378), #555 (guarda-chuva CodeQL). Avisadas
em cada issue e pelo banner no `DOING.md`(+PT).

## Gate de release (`D-RELEASE-0.5.0-GATE`, 20/09/2026, diretiva da mantenedora)

O release 0.5.0 só é cortado quando **todas as sete condições** valerem, cada
uma **medida** (nunca a olho). Este gate refina o checklist acima: o checklist
é a fila tática, estas sete são a aceitação. A diretiva da mantenedora é a
prioridade para "liberar o gate 0.5.0 para todos os agentes".

| # | Condição | Como é medida | Estado 21/09 (medido — nunca a olho) |
|---|---|---|---|
| 1 | Paridade 100% entre os alvos | matriz por alvo + paridade byte dos goldens onde o contrato exige; divergência = bug ou gap `XXX00x`. **Medida automaticamente** pelo `check_release_050_gate.sh` (roda `scripts/target-matrix.sh`, EG-5, e lê a linha `PARITY: 100%`) | GREEN (medido 21/09: `PARITY: 100%` em jvm/x86-64/riscv64/aarch64/JS/Script vs o oráculo JVM; o jar da árvore foi reconstruído por `scripts/build-kof-jar.sh`, que também o estampa — um rebase não finge mais "velho") |
| 2 | Nenhuma decisão pendente | `DECISIONS.md` sem pergunta aberta que mude a superfície | GREEN (sem candidato `[? MEL]` aberto; `decision-pending/` extinto) |
| 3 | Todos os `docs/development/*.md` soltos concluídos e movidos | regra dos três estados; só fica trabalho com implementação pendente | RED (3 docs com dono em curso: `ffi-abi-structs` [jonas], `IMPLEMENTATION-UNIVERSAL-PLATFORM` [9093], `makealive-plan` [.18]) |
| 4 | Estabilidade total | suíte completa 0F/0E + matriz 5/5 na candidata. **Medida automaticamente** a partir de um log real da suíte via `scripts/stability-report.sh` (`KOF_SUITE_LOG=…`); o GREEN exige o `TOTAL` 0F/0E **e** o log **estampado** (`SUITE-SHA` == tip, `SUITE-DIRTY=0`) — log de outro commit ou de árvore suja é `unknown`, nunca verde falso | NEEDS-MEASURE (exige um `safe-suite.sh` estampado em tip LIMPO; a árvore compartilhada tem WIP de outras lanes hoje) |
| 5 | 0 issues abertas que sejam bug | issues OPEN do GitHub com label `bug` = 0 | GREEN (0 issues de bug abertas; a condição lê o **rc da consulta** — API fora = `UNKNOWN`, nunca GREEN) |
| 6 | Todas as arestas fechadas | a fila EG INTEIRA (EG-1..EG-10) fechada + `1.0-blocks` abertos = 0; o 0.5.0 espera cada dono fechar/mover o próprio trabalho. **Mecanismos EG-5/EG-9/EG-10 FEITOS 20/09**; abertos: EG-8 | RED (EG-8; tabela EG ilegível = `UNKNOWN`, nunca GREEN) |
| 7 | Nada pendente em bugs-and-gaps | conjunto live do `check_known_bugs_status.sh` vazio + `specification-gaps.md` 0 abertos | RED (17 live no tip; ledger ilegível = `UNKNOWN`, nunca GREEN) |

Mecanizado por `scripts/check_release_050_gate.sh` (reporta cada condição como
GREEN / RED / NEEDS-MEASURE / UNKNOWN; teste RED-first
`scripts/tests/check-release-050-gate-test.sh`). Toda condição data-driven
**recusa GREEN quando a fonte não é legível** — jar velho, log da suíte de outro
commit ou de árvore suja, consulta ao GitHub que falha, tabela EG impossível de
parsear, ledger de bugs ilegível: o gate fica inconclusivo, nunca verde falso.
RED é esperado até a fila fechar — o gate é o motor, não um bloqueio a contornar.

### Recuperação — limpar as condições auto-medidas

```bash
scripts/build-kof-jar.sh                                # cond. 1: rebuilda + estampa o jar da árvore
scripts/target-matrix.sh                                #          -> PARITY: 100% (6 alvos core)
SAFE_SUITE_LOG="$PWD/.suite.log" scripts/safe-suite.sh  # cond. 4: rodar em árvore LIMPA
KOF_SUITE_LOG="$PWD/.suite.log" scripts/check_release_050_gate.sh
```
