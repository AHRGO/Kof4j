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
