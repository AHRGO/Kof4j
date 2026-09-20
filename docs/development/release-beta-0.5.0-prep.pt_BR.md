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
