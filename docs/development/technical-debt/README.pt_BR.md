[English](README.md) | [Português](README.pt_BR.md)

# KOF Technical Debt Scout

**Status:** EM DESENVOLVIMENTO — Wave 1 PRONTA, Wave 2 (qualificação de
evidência) PRONTA, as duas somente shadow. Ver `DOING.md` para o
claim/dono vivo e o próximo passo.

Esta é uma ferramenta que encontra e documenta dívida técnica
*histórica* antes que uma escolha temporária de implementação, uma
divergência de target ou uma suposição obsoleta virem, silenciosamente,
compatibilidade que não pode mais ser mudada. Ela não corrige nada e não
decide contrato de linguagem — ela produz evidência para um humano (hoje:
para `docs/development/tech-debt.md`, o ledger mantido pela mantenedora,
aberto em 23/09/2026).

## Ordem de leitura

1. `DEBT_SCOUT_CONTRACT.md` (EN) — o contrato operacional: definições,
   hard stops, o gate KOF-first, escala de confiança, fingerprints,
   roteamento de publicação/trust rollout, regras de privilégio. É isso
   que o código em `scripts/debt-scout/` implementa.
2. `TAXONOMY.md` (EN) — a classificação de três eixos que todo candidato usa.
3. `docs/development/DECISIONS.md` §`D-DEBT-SCOUT`/§`D-DEBT-SCOUT-W2` —
   os registros de decisão que autorizaram esta frente e o limite de
   escopo atual (ainda não existe capacidade de publicar Issue).

## De onde veio o desenho

Dois documentos de pesquisa foram fornecidos pelo usuário na sessão que
abriu esta frente (22–23/09/2026): uma proposta inicial e uma V2
revisada por pesquisa que a substitui estruturalmente (candidato ≠
dívida confirmada; `C2` nunca abre Issue automática; prioridade é vetor,
nunca um score único; reusar `scripts/agent-*.sh` em vez de um sistema de
governança paralelo). Esses documentos-fonte têm 100+ seções cada e
**não** são copiados para o repositório — `DEBT_SCOUT_CONTRACT.md` é a
destilação condensada e sincronizada com o código que os scripts de fato
seguem. Quando os dois divergem, o contrato desta pasta vence.

## O que existe hoje (Wave 1 + Wave 2)

```text
scripts/debt-scout/
├── config.py            — carrega/valida .debt-scout.yml (só stdlib)
├── schema.py             — schema v2 do Candidate (validação)
├── taxonomy.py            — enums de 3 eixos (cruzados contra TAXONOMY.md)
├── fingerprint.py        — fingerprints de finding/dívida (estável, sha256)
├── branch_discovery.py   — resolve branch default/ativa; sinaliza drift
│                            de contrato em vez de hardcodar uma ref
├── detectors/
│   ├── README.md          — a convenção de fixture que todo detector segue
│   ├── satd.py            — detector de marcadores SATD (TODO/FIXME/HACK/XXX)
│   └── partial_decisions.py — decisões PARTIAL/BLOCKED/IN_PROGRESS (C1)
├── history.py             — origem via git blame (subject literal; clone raso = NOT_CHECKED)
├── ownership.py           — cruza claims ativas do DOING.md (RESOLUTION_IN_PROGRESS)
├── cluster.py             — clustering de causa-raiz + debt_fingerprint
├── priority.py            — vetor principal/interest/lock-in (nunca score)
├── kof_first.py           — context builder KOF-first determinístico
├── confidence.py          — classificador C2/C3 (checklist de evidência obrigatória)
├── sarif.py               — escritor SARIF 2.1.0 pros achados com localização
├── inbox.py               — Debt Inbox pros achados C2+ sem localização
└── scan.py               — CLI orquestrador (--phase state|deterministic,
                             --check-duplicates, --history, --sarif-out,
                             --inbox-out, --metrics-out)
```

Todo módulo tem `--selftest` e/ou um teste `scripts/tests/debt-scout-*.sh`,
cada um incluindo uma rodada ao vivo contra os dados reais deste repo —
essa disciplina pegou 4 bugs reais antes de irem pro ar (ver as entradas
Wave 1/Wave 2 do `DOING.md` de cada um). **Nenhum script chama a API de
escrita de Issues do GitHub.** Nenhum workflow em `.github/workflows/`
concede `issues: write` a este sistema — a Wave 2 adicionou exatamente
um privilégio novo, `security-events: write` (só upload de SARIF),
justificado em `scripts/workflow-permissions.txt`.

## O que ainda NÃO existe (não assumir que roda)

O publisher canary de C3 (a única coisa que algum dia abriria uma Issue
no GitHub), qualquer etapa de qualificação com LLM, os detectores que
provariam `current_implementation_identified` / `debt_mechanism_proved`
/ `exit_condition_expressible` (logo nenhum cluster chega a `C3` ainda
— o resultado honesto), um detector dedicado de testes pulados
(deliberadamente NÃO construído: `scripts/audit-stubs.sh` §5/§6/§12 já
cobre `@Disabled`/`assumeTrue`/weak-green, mesma divisão de trabalho do
`satd.py`), tombstones/feedback/circuit breakers por regra (Wave 4 do
V2), o experimento de corpus estilo Crater e as
superfícies de CLI `kof debt`/`kof fix`. Cada um é uma unidade futura
separada e com escopo explícito — ver `DEBT_SCOUT_CONTRACT.md` §11 e
`DOING.md`. Avançar além da Wave 2 precisa do próprio registro em
`DECISIONS.md` com a autorização de fase da mantenedora.
