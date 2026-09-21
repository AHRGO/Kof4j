[English](release-beta-0.5.0-prep.md) | [Português](release-beta-0.5.0-prep.pt_BR.md)

# Release 0.5.0 — preparo (branch `beta-0.5.0`)

Decisão: `DECISIONS.md` §D-BRANCH-0.5.0 (20/09, ordem da mantenedora). Branch
ativa é `beta-0.5.0`; `beta-0.4.0` só recebe pousos em voo e preparo de
release. Este doc é a fila — fica em `docs/development/` até o corte (regra
dos três estados).

## Checklist (ordenado — número da versão e tag são decisão da mantenedora, regra 6)

1. [x] Pousar o que está em voo: §374/#553 (`.22` — WIP em
       `JvmOpCollections`), §371/#550 (CLI cross build), §378/#554 (gate
       docs). **TODOS OS TRÊS POUSARAM 20/09 — ✅ FIXED** (`§374` box-if-primitive
       no class load, `BareCollectionFieldE2ETest` 8/8; `§371` CLI embarcada cross,
       `ShippedCliCrossSmokeTest` 2/2 + `RuntimeSourceLoaderTest` 6/6; `§378`
       cruzamento EN×PT do conjunto aberto — provas no `known-bugs.md`). A lane
       docs faz ff da `beta-0.5.0` após cada pouso na 0.4.0.
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

#555 (guarda-chuva CodeQL — o único ainda aberto; **#550/#553/#554 pousaram
20/09** com suas seções ✅ FIXED). Avisadas em cada issue e pelo banner no
`DOING.md`(+PT).

## Gate de release (`D-RELEASE-0.5.0-GATE`, 20/09/2026, diretiva da mantenedora)

O release 0.5.0 só é cortado quando **todas as sete condições** valerem, cada
uma **medida** (nunca a olho). Este gate refina o checklist acima: o checklist
é a fila tática, estas sete são a aceitação. A diretiva da mantenedora é a
prioridade para "liberar o gate 0.5.0 para todos os agentes".

| # | Condição | Como é medida | Estado 21/09 (medido — nunca a olho) |
|---|---|---|---|
| 1 | Paridade 100% entre os alvos | matriz por alvo + paridade byte dos goldens onde o contrato exige; divergência = bug ou gap `XXX00x`. **Medida automaticamente** pelo `check_release_050_gate.sh` (roda `scripts/target-matrix.sh`, EG-5, e lê a linha `PARITY: 100%`) | GREEN (medido 21/09: `PARITY: 100%` em jvm/x86-64/riscv64/aarch64/JS/Script vs o oráculo JVM; o jar da árvore foi reconstruído por `scripts/build-kof-jar.sh`, que também o estampa — um rebase não finge mais "velho"; num host sem root o toolchain cross (binutils/qemu/libc) é montado por `scripts/setup-cross-toolchain.sh`, que extrai os `.deb`s para um prefixo local e aponta `KOF_CROSS_SYSROOT` para ele). **Re-medido 21/09 no tip `c8d62388`: DE VOLTA A GREEN** — `scripts/build-kof-jar.sh` (reconstrói + estampa o `lib/kof.jar` pelo conteúdo da fonte) e então `scripts/target-matrix.sh` sob o env do `scripts/setup-cross-toolchain.sh --export` (`PATH`/`LD_LIBRARY_PATH`/`KOF_CROSS_SYSROOT`) → `PARITY: 100%` (jvm/x86_64/riscv64/aarch64/js/script byte-a-byte vs o oráculo JVM; kofc=EG-9, android=EG-10 delegados). O NEEDS-MEASURE anterior no mesmo dia (pousos makealive 3.2 `966c86a4`, §422 `9d97b268`, …) é a regra da própria condição, re-instaurada no corte pela condição 6 — **não uma regressão de paridade** |
| 2 | Nenhuma decisão pendente | `DECISIONS.md` sem pergunta aberta que mude a superfície | NEEDS-REVIEW (**não RED**) — 3 frentes aprovadas com `State: OPEN` em curso (`D-TYPE-VARIANCE`/X5, `D-INTEROP-REFLECT`/X6, `D-SECRETS`); pela condição 2 de `D-RELEASE-0.5.0-GATE`, frente aprovada ABERTA não bloqueia o corte e nunca é "nada espera" |
| 3 | Todos os `docs/development/*.md` soltos concluídos e movidos | regra dos três estados; só fica trabalho com implementação pendente | RED (6 docs com dono em curso: `ffi-abi-structs` [jonas], `IMPLEMENTATION-UNIVERSAL-PLATFORM` [9093], `makealive-plan` [.18], `db-parity-plan` [docs/plataforma], `secrets-plan` [security], `type-system-extensions-plan` [compiler]) |
| 4 | Estabilidade total | suíte completa 0F/0E + matriz 5/5 na candidata. **Medida automaticamente** a partir de um log real da suíte via `scripts/stability-report.sh` (`KOF_SUITE_LOG=…`); o GREEN exige o `TOTAL` 0F/0E **e** o log **estampado** (`SUITE-SHA` == tip, `SUITE-DIRTY=0`) — log de outro commit ou de árvore suja é `unknown`, nunca verde falso | GREEN (medido 21/09 em `67b087f9`): corrida completa nova do `safe-suite.sh` — `TOTAL: tests=3440 failures=0 errors=0 skipped=225`, `SUITE-SHA`==tip, `SUITE-DIRTY=0`, e o `stability-report.sh` devolveu GREEN a partir do log estampado. O RED anterior em `96af9b63` (`tests=3413 failures=1`) era o **teste stale do §422** `CompilerDriverTest#externProducesHonestGapNotSilentDrop`, que ainda exigia `FFI001` para um extern `Int[]` que o `7c6413d4` (D6-2) tornou bindável no JVM de propósito — **RESOLVIDO 21/09 pela frente FFI**: `7c89f531` reapontou a asserção para assinaturas realmente não-ligadas (`String[]`/`List<Int>`→`FFI001`, `Buffer(Int)`→`SEM096`) e `9d97b268` fechou o §422 (`CompilerDriverTest` 259/0F/0E), então o vermelho stale sumiu. A baseline `8f459b8e` (`tests=3355`, primeiro log autocertificável — o `safe-suite.sh` antigo imprimia o `TOTAL` só no console e nunca o anexava, então NENHUMA corrida estampada podia ser certificada até o conserto do mecanismo) segue como história. O candidato ainda exige re-medição no tip final limpo na hora do corte — isso é a regra da condição 6, não dúvida desta linha |
| 5 | 0 issues abertas que sejam bug | issues OPEN do GitHub com label `bug` = 0 | GREEN (0 issues de bug abertas; a condição lê o **rc da consulta** — API fora = `UNKNOWN`, nunca GREEN; num host sem `gh`, `scripts/fetch-open-issues.sh` fornece o `R050_OPEN_ISSUES_TSV` pela API pública — medido 21/09: 0 issues de bug abertas, a #580 é documentation/enhancement) |
| 6 | Todas as arestas fechadas | a fila EG INTEIRA (EG-1..EG-10) fechada + `1.0-blocks` abertos = 0; o 0.5.0 espera cada dono fechar/mover o próprio trabalho. **Mecanismos EG-5/EG-9/EG-10 FEITOS 20/09**; abertos: EG-8 | RED (EG-8; tabela EG ilegível = `UNKNOWN`, nunca GREEN; enumeração de `1.0-blocks` falha deixa essa parte `UNKNOWN`, nunca um 0 implícito — `R050_OPEN_BLOCKS` fornece a contagem offline) |
| 7 | Nada pendente em bugs-and-gaps | conjunto live do `check_known_bugs_status.sh` vazio + `specification-gaps.md` 0 abertos | RED (23 live no tip — snapshot 21/09 após o fix S0 do §421 + §423–§431 catalogados; o script é a autoridade; ledger ilegível = `UNKNOWN`, nunca GREEN) |

Mecanizado por `scripts/check_release_050_gate.sh` (reporta cada condição como
GREEN / RED / NEEDS-MEASURE / UNKNOWN; teste RED-first
`scripts/tests/check-release-050-gate-test.sh`). Toda condição data-driven
**recusa GREEN quando a fonte não é legível** — jar velho, log da suíte de outro
commit ou de árvore suja, consulta ao GitHub que falha, tabela EG impossível de
parsear, ledger de bugs ilegível, fonte de decisão ou lista de docs soltos
ausente: as sete condições ficam inconclusivas, nunca verde falso.
RED é esperado até a fila fechar — o gate é o motor, não um bloqueio a contornar.

### Recuperação — limpar as condições auto-medidas

```bash
eval "$(scripts/setup-cross-toolchain.sh --export)"     # cond. 1: binutils/qemu/libc cross (host sem root; uma vez)
scripts/build-kof-jar.sh                                # cond. 1: rebuilda + estampa o jar da árvore (após o último commit do compiler)
scripts/target-matrix.sh                                #          -> PARITY: 100% (6 alvos core)
scripts/fetch-open-issues.sh > /tmp/open-issues.tsv     # cond. 5: quando o `gh` não existe (API pública)
SAFE_SUITE_LOG="$PWD/.suite.log" scripts/safe-suite.sh  # cond. 4: rodar em árvore LIMPA
R050_OPEN_ISSUES_TSV=/tmp/open-issues.tsv \
KOF_SUITE_LOG="$PWD/.suite.log" scripts/check_release_050_gate.sh
```
