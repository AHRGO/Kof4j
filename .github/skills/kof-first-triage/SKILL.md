---
name: kof-first-triage
description: Apply the KOF-first contract gate when reviewing KofLang/Kof4j issues or pull requests, especially parser, syntax, semantic, stdlib, cross-target, diagnostics, generics, nullability, lambda, switch, and backend reports. Use this skill to determine whether a report is a real KOF bug, target divergence, real gap, design request, not-valid report, contract conflict, or contract ambiguity before suggesting or implementing code. It requires current repository evidence, KOF grammar and decisions, the documented KOF idiom, fresh measurement where needed, and blocks accidental language changes disguised as bug fixes.
---

# KOF-first Triage

Apply the repository's KOF-first rule before treating any report as a bug.

## Control sources

Read current repository evidence in this order:

1. `docs/development/DECISIONS.md` and the current decision that governs the behavior.
2. Specific normative files under `docs/language-reference/`.
3. Conformance and golden tests.
4. `training/`, `learn/`, and `training/anti-patterns/fake-idioms.md`.
5. Backend parity and documented gaps.
6. Current implementation.
7. External languages, runtimes, papers, forums, or benchmarks only after an internal gap is proved.

Always read `AGENTS.md`, especially D-NOT-JAVA, the docs-first rule, D-KOF-FIRST, and the quality gate.

Use `references/triage-playbook.md` for the detailed NOT-VALID / contract-conflict cases, precedents, and per-issue examples. Revalidate every example against the current branch before acting.

## Mandatory workflow

### Gate 0 - KOF validity

Prove that the reproducer is valid KOF before accepting its expected result.

Check the grammar, language reference, training, learn material, fake idioms, and relevant tests. Do not use compiler rejection alone as proof that syntax is invalid when normative docs say otherwise.

If the reproducer uses a form not recognized by the KOF contract, continue to Gate 1 instead of opening or fixing a bug.

### Gate 1 - Intent and KOF idiom

Identify the user's actual intent and search for the current KOF idiom that expresses it.

If the intent is already expressible in KOF, show the correct KOF form. Do not expand the language merely to accept a second spelling.

### Gate 2 - Governing contract

Record:

- contract source;
- exact KOF behavior it defines;
- whether the issue's expectation matches that behavior;
- whether a current decision explicitly rejects or freezes the proposed behavior.

Never treat Java, Kotlin, C#, JavaScript, the JVM, or another platform as the KOF contract.

### Gate 3 - Internal measurement

Use the valid KOF reproducer and measure the relevant targets when the classification depends on runtime or backend behavior.

Do not claim a test, target, or suite was run unless it was actually run in the current work.

### Gate 4 - Classification

Choose exactly one primary classification:

- `BUG REAL`: valid KOF + KOF contract defines expected behavior + implementation differs.
- `TARGET DIVERGENCE`: same valid KOF construct violates an agreed cross-target contract.
- `GAP REAL`: legitimate need remains without an adequate KOF syntax, idiom, API, composition, or decided solution.
- `DESIGN REQUEST`: requested behavior changes or extends KOF grammar, semantics, operators, type model, API, inference, flow rules, or other contract.
- `NOT-VALID`: reproducer depends on a non-KOF form and the intent already has a KOF idiom.
- `CONTRACT CONFLICT`: requested change directly contradicts an active KOF decision or frozen contract.
- `CONTRACT AMBIGUITY`: current decisions, docs, tests, and implementation do not determine a normative answer.

### Gate 5 - Action

For `NOT-VALID`:

1. cite the KOF contract;
2. show the correct KOF form;
3. explain why the current rejection is coherent;
4. close or recommend closing the issue as not-a-bug/not-planned;
5. close or recommend closing the PR without merge;
6. if the alternative form has merit, provide a separate design-question template instead of implementing it.

For `CONTRACT CONFLICT`:

1. cite the active decision that would be violated;
2. explain the current supported KOF behavior;
3. close or recommend closing the bugfix PR without merge;
4. explain that the change requires a new or superseding maintainer decision;
5. never self-ratify that decision.

For `DESIGN REQUEST`, `GAP REAL`, or `CONTRACT AMBIGUITY`:

- do not implement production code as a bugfix;
- prepare evidence and a focused design question for the maintainer;
- include grammar, semantic, compatibility, cross-target, migration, and test impact;
- wait for a recorded decision before implementation.

For `BUG REAL` or `TARGET DIVERGENCE`:

- implementation is allowed only when the task explicitly requests implementation and no existing owner/lane conflicts;
- follow RED -> root-cause fix -> GREEN -> relevant cross-target proof -> full required suite -> docs/changelog when needed;
- include the regression test in the same delivery as the fix.

## Hard stops

Stop before modifying production code when any of these is true:

- KOF validity has not been proved.
- The only expectation comes from another language or backend implementation.
- A KOF idiom already solves the intent and the patch only adds another spelling.
- The change adds accepted grammar without a decision authorizing that surface.
- The change alters a frozen semantic or stdlib contract without a decision.
- The contract is ambiguous.
- A report is labeled bug but is actually a feature or design question.

Do not create a replacement feature issue automatically. Provide the design proposal text and let the maintainer or contributor decide whether to open it unless explicitly asked to create it.

## Required evidence block

Before any conclusion, produce an internal evidence block with:

```text
KOF VALIDITY:
CONTRACT SOURCE:
CURRENT KOF IDIOM:
MEASUREMENT:
CLASSIFICATION:
DUPLICATE / PRECEDENT CHECK:
ACTION:
```

Do not omit fields. Use `UNKNOWN` when evidence is genuinely unavailable instead of guessing.

## Correct design-request shape

When an idea is legitimate but outside the current contract, frame it as:

```text
[Design] Should KOF support <new form or behavior> in addition to <current form>?
```

Include the need, current contract, current idiom, remaining gap, proposed change, grammar/semantic impact, cross-target impact, compatibility, alternatives, and the exact decision requested from the maintainer.

## Precedent

Use the reasoning pattern from #492/#496 and #509/#520:

- typed lambda intent was already supported;
- the documented KOF form was `name: Type`;
- `Type name` was a second surface, not a bug fix;
- the PR was closed without merge;
- any future support for both forms belongs in a separate language-design discussion.

Apply the same reasoning pattern, not the issue-specific answer, to new cases.
