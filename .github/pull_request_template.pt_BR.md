[English](pull_request_template.md) | [Português](pull_request_template.pt_BR.md)

<!--
  POLÍTICA DE PULL REQUESTS DO KOFLANG / KOF4J

  ⚠️ ATENÇÃO: NUNCA ABRA PULL REQUESTS DIRETAMENTE CONTRA A BRANCH `main`!
  A branch `main` é reservada para releases estáveis e controladas pela mantenedora.
  Todo desenvolvimento, correções e contribuições devem ser abertos contra a
  branch `beta` ativa (exemplo atual: `beta-0.4.0`).
  
  PRs abertos contra a branch `main` por contas não-autorizadas serão
  bloqueados automaticamente pela action de guarda do repositório.
-->

## 🎯 Branch Base de Destino
- [ ] Confirmo que este PR está apontando para uma branch **`beta-*`** (ex: `beta-0.4.0`) e **NÃO** para a `main`.

---

## 📝 Descrição da Mudança
<!-- Descreva de forma clara e concisa o que foi adicionado, corrigido ou refatorado. -->

---

## 🔗 Issue Relacionada
<!-- Todo PR deve referenciar uma issue aberta (ex: Fixes #123, Closes #456). Regra 6 do AGENTS.md. -->
Fixes #

---

## 📐 Contrato KOF-primeiro (`D-KOF-FIRST`)
<!-- Toda mudança responde aos quatro portões abaixo. "A linguagem X faz assim" nunca é fonte de contrato. -->
**Reproducer Kof válido** (o trecho que exercita a mudança):

```kof

```

- **Fonte do contrato** (entrada do DECISIONS.md, doc normativo, teste de conformidade/golden, ou matriz de paridade) que define o comportamento esperado:
- **RED antes da mudança de produção** — alvo(s), esperado pelo contrato Kof, obtido:
- **Causa raiz** (não o sintoma):
- **Classificação** (`Bug real` / `Divergência de alvo` / `Gap real` / `Design request` / `Not-valid` / `Ambiguidade de contrato`):
- **Isto altera a superfície do Kof?** Se sim, a decisão da mantenedora que autoriza (regra 6) — uma forma nova aceita pela gramática é feature de linguagem, não conserto de parser:
- **Referências externas usadas** (só de implementação/teoria; nenhuma delas define a superfície do Kof):

---

## 🧪 Como Foi Testado (Portão de Qualidade)
<!-- O teste PROVA que o código funciona. Liste os comandos rodados e os testes adicionados. -->
- [ ] `mvn -o -pl kof-compiler -am compile -q` rodou sem erros
- [ ] Testes adicionados/alterados cobrindo o caminho feliz e bordas (Q3)
- [ ] Suíte rodada e verde (`mvn test ...`)

---

## 📋 Checklist de Pré-Submissão
- [ ] Nenhuma alteração contém stubs ou TODOs de fachada (Q7)
- [ ] Regras do `AGENTS.md` respeitadas (≤500 linhas/classe, zero regressão)
- [ ] Documentação ou `DOING.md` atualizados se aplicável
