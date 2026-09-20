[English](copilot-instructions.md) | [Português](copilot-instructions.pt_BR.md)

# Instruções do repositório KOF para o GitHub Copilot

Antes de triar ou implementar qualquer issue ou pull request, leia o `AGENTS.md` na raiz do repositório e o `docs/development/DECISIONS.md`.

Para triagem de issues e pull requests, use `.github/skills/kof-first-triage/SKILL.md` e aplique o D-KOF-FIRST antes de tratar um relato como bug.

Nunca use Java, Kotlin, C#, JavaScript, a JVM, outra runtime, um paper, um benchmark ou um fórum como oráculo da linguagem KOF. Primeiro prove sintaxe KOF válida, identifique o contrato KOF que governa, procure o idioma KOF existente e meça o comportamento atual quando necessário.

Não implemente gramática, semântica, inferência, análise de fluxo, operadores, comportamento de tipos ou superfície congelada de stdlib/API sob rótulo `fix:` sem que uma decisão KOF vigente autorize explicitamente esse contrato.

Para relatos NOT-VALID, mostre a forma KOF suportada e feche ou recomende o fechamento como não-bug. Para conflitos de contrato, cite a decisão ativa e não faça merge do bugfix conflitante. Para ideias legítimas fora do contrato, prepare um design request e aguarde a decisão da mantenedora.

Para bugs confirmados, siga o portão de qualidade do repositório: reproduzir -> RED -> correção da causa raiz -> teste de regressão -> GREEN -> paridade relevante entre alvos -> suíte exigida. Nunca afirme evidência que não foi realmente medida.
