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
3. [ ] Bump de versão: `pom.xml` `<revision>0.4.7-beta</revision>` para o
       número que a mantenedora decidir no corte. Conferir referências à
       versão codificadas (javadoc/testes citam a versão do artefato) ANTES
       do bump; nunca edição unilateral.
4. [ ] Corte do CHANGELOG (EN+PT): seção `0.5.0` reunindo os bullets não
       lançados; cabeçalho `Version:` do `AGENTS.md`(+PT) atualizado no
       mesmo commit.
5. [ ] Tally: `'Current build: **N**'` em `docs/backend-parity.md`(+PT) a
       partir da primeira CI Build+Tests hospedada VERDE no tip da release
       (contagem do log do job, nunca memória).
6. [ ] Prova de estabilidade: suíte completa 0F/0E + matriz de conformidade
       5/5 MEDIDOS no candidato à tag (AGENTS §Estabilidade — tag só com
       verde).
7. [ ] Tag + release notes (EN+PT); declarar `beta-0.4.0` fechada exceto
       pela lista de fixes residuais.

## Issues abertas que viajam para `beta-0.5.0`

#550 (§371), #553 (§374), #554 (§378), #555 (guarda-chuva CodeQL). Avisadas
em cada issue e pelo banner no `DOING.md`(+PT).

## Gate de release (`D-RELEASE-0.5.0-GATE`, 20/09/2026, diretiva da mantenedora)

O release 0.5.0 só é cortado quando **todas as sete condições** valerem, cada
uma **medida** (nunca a olho). Este gate refina o checklist acima: o checklist
é a fila tática, estas sete são a aceitação. A diretiva da mantenedora é a
prioridade para "liberar o gate 0.5.0 para todos os agentes".

| # | Condição | Como é medida | Estado 20/09 |
|---|---|---|---|
| 1 | Paridade 100% entre os alvos | matriz por alvo + paridade byte dos goldens onde o contrato exige; divergência = bug ou gap `XXX00x`. **Medida automaticamente** pelo `check_release_050_gate.sh` (roda `scripts/target-matrix.sh`, EG-5, e lê a linha `PARITY: 100%`) | GREEN (6 alvos core com paridade byte vs oráculo JVM) |
| 2 | Nenhuma decisão pendente | `DECISIONS.md` sem pergunta aberta que mude a superfície | NEEDS-REVIEW |
| 3 | Todos os `docs/development/*.md` soltos concluídos e movidos | regra dos três estados; só fica trabalho com implementação pendente | RED (docs em curso) |
| 4 | Estabilidade total | suíte completa 0F/0E + matriz 5/5 na candidata. **Medida automaticamente** a partir de um log real da suíte via `scripts/stability-report.sh` (`KOF_SUITE_LOG=…`), que exige que o último `TOTAL` seja 0F/0E; os guards de conformidade fazem parte dessa suíte | NEEDS-MEASURE (sem corrida do dia do RC ainda) |
| 5 | 0 issues abertas que sejam bug | issues OPEN do GitHub com label `bug` = 0 (inclui a #566 — mantenedora 20/09) | RED (#566) |
| 6 | Todas as arestas fechadas | a fila EG INTEIRA (EG-1..EG-10) fechada + `1.0-blocks` abertos = 0; o 0.5.0 espera cada dono fechar/mover o próprio trabalho. **Mecanismos EG-5/EG-9/EG-10 FEITOS 20/09**; abertos: #566 + EG-8 | RED (#566 + EG-8) |
| 7 | Nada pendente em bugs-and-gaps | conjunto live do `check_known_bugs_status.sh` vazio + `specification-gaps.md` 0 abertos | RED (19 live) |

Mecanizado por `scripts/check_release_050_gate.sh` (reporta cada condição como
GREEN / RED / NEEDS-MEASURE; teste RED-first
`scripts/tests/check-release-050-gate-test.sh`). RED é esperado até a fila
fechar — o gate é o motor, não um bloqueio a contornar.
