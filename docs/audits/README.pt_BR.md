[English](README.md) | [Português](README.pt_BR.md)

# docs/audits/ — auditorias (foto de estado vs. realidade)

> **Regra desta pasta** (criada 13/09, decisão da mantenedora): documento cujo
> trabalho é **comparar o que os planos/docs afirmam contra o código, testes e
> releases reais** — matriz de implementado-vs-planejado — mora aqui, não em
> `development/` (que é fila de trabalho técnico pendente) nem em `bugs-and-gaps/`
> (fila por alvo). A auditoria **não fecha sozinha**: ela aponta trabalho, que é
> reivindicado nos planos/registros das outras pastas.

## Estado atual

| Doc | O que audita | Vivacidade |
|---|---|---|
| `roadmap-audit.md` | roadmap × código (matriz + fila P0→P5) | **viva** — re-audit quando algo fecha |
| `complexity-audit.md` | contagem de linhas/classe (02/09) | **snapshot** — gate vivo = `scripts/check_500.sh` (ratchet CI) |
| `PLANNING-FUTURE-AUDIT.md` | branch `planning-future` × beta (07–08/09) | **encerrada** — R2 vive em `DECISIONS.md` x{a7}D-APP/x{a7}D-PLATFORM (ratificado 13/09), R5 no cluster migração |
| `planning-future-reconcile.md` | merge da branch (05/09) | **encerrada** — checklist cumprido (port dos tiers) |

## Como usar

1. **Nunca** atacar trabalho direto daqui — o que uma auditoria marca como
   pendente tem que ter casa: bug → `docs/bugs-and-gaps/known-bugs.md`; plano
   com código a escrever → doc do plano em `docs/development/`; decisão →
   `docs/development/DECISIONS.md` (a pasta `decision-pending/` foi extinta 13/09).
2. Ao fechar o que uma auditoria apontou, **atualize a linha da auditoria no
   MESMO commit** (ela é registro, não opinião congelada).
3. Auditoria encerrada (nada vivo apontando para trabalho não-casado) fica
   aqui como histórico datado — não volta para `development/`.
