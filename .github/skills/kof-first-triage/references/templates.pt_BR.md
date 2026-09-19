[English](templates.md) | [Português](templates.pt_BR.md)

# Templates de triagem KOF-first

## Comentário de issue NOT-VALID

```markdown
## Not a bug under the current KOF contract

The reproducer uses:

```kof
<forma relatada>
```

The current KOF contract documents:

```kof
<forma suportada>
```

Contract source: `<caminho / decisão / teste>`.

The underlying intent is already expressible in KOF, so the current rejection does not demonstrate a compiler bug. Implementing the reported form would add a second language surface and therefore requires a design decision rather than a parser bugfix.

If there is interest in adding the alternative form, the appropriate follow-up is a separate design discussion:

> **Should KOF support <new form> in addition to <current form>?**

That discussion should cover grammar, ambiguity, documentation, positive/negative tests, tooling, and target impact before implementation.

Closing as not-a-bug under the current contract.
```

## Comentário de issue/PR CONTRACT CONFLICT

```markdown
## This change conflicts with the current KOF contract

The requested behavior would change `<behavior>`, but the active contract currently requires `<current behavior>`.

Normative source: `<DECISIONS entry / normative doc>`.

This is therefore not a bugfix under the current contract. Merging this change would revise the language contract through implementation rather than through a maintainer decision.

The proposal can still be evaluated as a language-design change. The correct next step is a separate decision request that states the current contract, the proposed replacement, compatibility and migration effects, cross-target impact, and tests. If approved, the decision must be recorded before production code changes.

Closing this bugfix / PR without merge under the current contract.
```

## Nota de fechamento de PR

```markdown
The linked issue does not pass the KOF-first bug gate. The patch changes accepted KOF surface or semantics instead of correcting a divergence from the current KOF contract.

No merge. If the capability is still desired, please move it to a separate design discussion and obtain an explicit language decision first.
```

## Design request

```markdown
# [Design] Should KOF support <new behavior> in addition to <current behavior>?

## Need
<necessidade do usuário/desenvolvedor>

## Current KOF contract
<fonte e comportamento atual>

## Current KOF idiom
<como isso é resolvido hoje>

## Why the current idiom may be insufficient
<lacuna restante>

## Proposed change
<mudança precisa na linguagem/API>

## Grammar and semantic impact
<parser, typer, lowering, runtime, ABI, diagnósticos>

## Cross-target impact
<JVM / Script / JS / Native / demais>

## Compatibility and migration
<ambiguidade, interpretação alterada, migração>

## Alternatives
<alternativas nativas do KOF>

## Decision requested
<uma pergunta objetiva para a mantenedora>
```
