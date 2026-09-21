[English](supply-chain-trust-boundary-2026-09-20.md) | [Português](supply-chain-trust-boundary-2026-09-20.pt_BR.md)

# Fronteira de confiança da cadeia de suprimentos do KOF v1 — baseline (20/09/2026)

> **Medição somente leitura.** Nenhum workflow, script ou arquivo de produção foi alterado para
> produzir este documento, e **nenhuma decisão é tomada aqui**: ele mapeia o que é verdade hoje
> para que a mantenedora responda às perguntas de contrato da §5. Fontes: arquivos do repositório
> no tip, a API do GitHub (somente leitura, como `jonasrochasilva-prog`) e o contrato ratificado
> (`D-RELEASE-1.0`, `D-1.0-EDGES`, `PROPOSAL-1.0-EXIT-GATE` §32.6/§32.7). Os fatos são
> re-verificáveis com os comandos indicados em cada linha.

## 1. O que o contrato já diz (ratificado) — e o que deixa indefinido

| Já ratificado | Onde |
|---|---|
| o pacote testado deve ter o **mesmo digest** do artefato que será publicado | `PROPOSAL-1.0-EXIT-GATE` §32.6 `[RATIFICADO]` |
| **identidade de artefato (SHA256/proveniência)** é gate obrigatório; **manifesto de evidência por alvo** | `D-1.0-EDGES` Q7 (gates do §35) |
| Registry = GitHub Releases + `SHA256SUMS` verificado antes de instalar | `D2-A` |

**Indefinido:** a raiz de confiança, a identidade do builder, o formato mínimo da proveniência, quem verifica, onde a política é aplicada e o que acontece com prova ausente/inválida. Classificação da frente: **AMBIGUIDADE DE CONTRATO** (o requisito existe; o conteúdo dele, não).

## 2. Baseline medido

| # | Medição | Resultado | Como re-verificar |
|---|---|---|---|
| M1 | **SHA testado × SHA publicado** (release real `0.4.9-beta`) | testado = `e790137ee1` (merge do #558); publicado = `22a186b9bf` (commit de bump do `kof-release-bot`, **sem assinatura**). As árvores diferem exatamente em `CHANGELOG.md`, `VERSION`, `version.properties`, `pom.xml`. | `git log --grep 'bump version to 0.4.9-beta'`; `git diff <pai> <bump> --name-only` |
| M2 | Forma do workflow de release | `release.yml`: testes completos (`mvn clean package` + golden + integração) rodam no job 1 no **SHA de disparo**; o job 1 então commita o bump de versão e dá push; o job 2 faz checkout do **SHA do bump** e recompila com `-DskipTests` numa matriz de 3 SOs (linux/windows/macos) e publica. Dentro do workflow de release os arquivos windows/macos são construídos uma vez e só passam por sanidade (`kof version`/`kof info`); a suíte completa roda ali só no Linux (o workflow `CI` separado tem um job `kof.io` multi-SO, que é outra execução). | `.github/workflows/release.yml` |
| M3 | Escopo do token do workflow de release | `permissions: contents: write` no **nível do workflow** (os dois jobs), e o job 1 faz `git push` direto na `main`. | `release.yml` linhas 14–15, 94 |
| M4 | Referências a actions de terceiros | **56** referências `uses:`, **0** fixadas por SHA completo de commit (todas por tag/branch, ex.: `actions/checkout@v7`, `softprops/action-gh-release@v3`, `docker://…gitleaks:v8.28.0`). Configuração do repo `sha_pinning_required=false`, `allowed_actions=all`. | `grep -h 'uses:' .github/workflows/*.yml`; `gh api repos/KofLang/Kof4j/actions/permissions` |
| M5 | Token padrão / aprovações | permissões padrão de workflow = `read`; `can_approve_pull_request_reviews=false`. 3 workflows não declaram `permissions:` no topo (herdam o padrão `read`). | `gh api repos/KofLang/Kof4j/actions/permissions/workflow` |
| M6 | Governança da fonte | `main` e `beta-0.5.0`: `protected=false`, **0 rulesets**, nenhum required status check visível. (É o que a API mostra das configurações visíveis do repositório; permissões de colaboradores e política de organização são outras dimensões e não foram medidas.) | `gh api repos/KofLang/Kof4j/branches/<b>/protection`, `.../rulesets` |
| M7 | Proveniência / attestations | nenhuma encontrada: a API de attestations devolve 404 para o digest de um asset real de release. As releases são publicadas por `github-actions[bot]`; assets = arquivo + `kof-cli-*.jar` + `SHA256SUMS` (o arquivo de checksum é produzido e publicado pelo mesmo job do artefato). O próprio GitHub registra um `digest` por asset no servidor. | `gh api repos/KofLang/Kof4j/attestations/sha256:<digest>` |
| M8 | Verificação do consumidor (Registry) | `kof deps resolve` verifica o `SHA256SUMS` **antes de instalar**, e agora cada fonte (`REG002/REG004`); o arquivo de checksum viaja **dentro do mesmo tarball**. | `DepsRegistry`, `DepsSources` |
| M9 | Já existe (não redesenhar) | CodeQL, Gitleaks, Dependabot (`maven` + `github-actions`, semanal), `SECURITY.md` (relato privado de vulnerabilidade), guarda de path traversal na extração, `REG00x` honestos, #564/#565/#563 fechadas com evidência de CI hospedado. | arquivos do repo |

## 3. Classificação dos achados

| Achado | Classe | Nota |
|---|---|---|
| M1/M2 descontinuidade do mesmo candidato | **lacuna de release-readiness já coberta pelo §32.6 ratificado** | pertence à lane de release/EXIT-GATE; não é afirmação de que uma release passada foi adulterada — só que, sem mudança, esse desenho não satisfaria o contrato de evidência do 1.0 |
| M3, M4 | **achado de hardening de segurança / lacuna de confiança do release** | não é `BUG REAL`; precisa da decisão do dono do workflow (Q7) |
| M6 | **ambiguidade de governança da fonte** | proveniência de build prova "commit X, workflow Y", não "o commit X foi autorizado" (Q8) |
| M7/M8 | **ambiguidade de contrato** | SHA256 dá integridade, não autenticidade: se artefato **e** checksum forem trocados pelo mesmo ator, o hash continua batendo (T3) |

## 4. Modelo de ameaças (o que cada camada pode e não pode afirmar)

| ID | Ameaça | SHA256SUMS hoje | Proveniência de build | Hardening de workflow/fonte |
|---|---|---|---|---|
| T1/T2 | corrupção / pacote alterado sem seu hash | detecta | detecta | — |
| T3 | pacote **e** checksum trocados juntos | **insuficiente sozinho** | ajuda se a prova vier de raiz independente | ajuda |
| T4/T5 | artefato de outro commit / recompilado após os testes | não | **forte** se o digest do subject atestado for o testado | a forma do pipeline (M2) é essencial |
| T6 | workflow/action comprometido | não | pode atestar um build comprometido | **essencial** (M3/M4) |
| T7 | credencial de publicação vazada | não | ajuda a detectar origem divergente | privilégio mínimo / credenciais efêmeras |
| T8 | commit não autorizado | não | proveniência ainda válida | **governança da fonte** (M6) |
| T9/T10 | rollback/replay; comprometimento de chave de assinatura | não | não sozinha | desenhos da classe TUF tratam disso (pós-1.0 salvo decisão contrária) |
| T11/T12 | dependência legítima maliciosa; falso-green do CI | não | prova origem, não segurança | gates confiáveis (`EG-2`) |

Não existe ferramenta única que cubra tudo; a cadeia é **confiança na fonte → política de build confiável → testar o artefato exato → digest → proveniência → publicar os mesmos bytes → verificar por política**.

## 5. Perguntas que só a mantenedora pode decidir (nenhuma é decidida aqui)

1. **Propriedade obrigatória no 1.0:** integridade, autenticidade, proveniência de build, proveniência de fonte/controle de mudança, ou combinação?
2. **Identidade confiável** que pode atestar uma release oficial (o workflow oficial, um workflow reutilizável confiável, uma identidade de mantenedor, uma identidade Sigstore, outra).
3. **Política da fonte:** basta proveniência de *qualquer* commit, ou a revisão também precisa ter passado por uma política (revisão/required checks)?
4. **Invariante do artefato exato** (construir uma vez → testar → atestar → publicar os mesmos bytes): regra formal? (O §32.6 já ratifica a igualdade de digest.)
5. **Onde a verificação é obrigatória:** só no gate de release, mais a doc de download, também em `kof deps resolve`, só para pacotes oficiais?
6. **Política de falha:** evidência ausente/inválida bloqueia a release, bloqueia o consumo, avisa, ou difere entre oficial e comunitário?
7. **Confiança no workflow:** actions fixadas por SHA, privilégio mínimo por job, workflow de build reutilizável/confiável, tudo isso, ou hardening fora do contrato?
8. **Proteção da fonte:** branch protection/rulesets, required checks, revisão, commits assinados, nenhum?
9. **SBOM:** gate do 1.0, evidência complementar ou pós-1.0?
10. **Rollback/frescor:** GitHub Releases + proveniência bastam para o 1.0, ou é exigida proteção explícita contra rollback/replay?
11. **Neutralidade de fornecedor:** nomear "GitHub Artifact Attestation" no contrato, ou enunciar propriedades neutras e permitir implementações equivalentes? (A pesquisa favorece a segunda.)
12. **Objeto do consumidor (decorre da #566(b)):** como pacotes agora são consumidos como módulos-fonte, o artefato que recebe digest/attestation para uma *biblioteca* é o tarball de fontes — confirmar.

## 6. Não feito aqui / próximos passos

- **Laboratório (fora do Kof4j):** validar empiricamente, num repositório meu, o que os GitHub Artifact Attestations entregam (verificação online/offline; adulteração, digest errado e repositório errado devem dar RED; permissões, custo, lock-in; actions fixadas por SHA e tokens por job). Os resultados serão anexados aqui.
- **Issue de design** (`[Design/Contract][Security]`) com a §5, só depois do laboratório e de checar duplicatas (nenhuma encontrada hoje para proveniência/attestation/SLSA/Sigstore).
- **Parada dura:** nenhuma mudança de produção (workflow, gate, `kof deps`) até a mantenedora registrar uma decisão; e nenhum arquivo `EM CURSO` de outra lane é tocado.
