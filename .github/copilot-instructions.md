[English](copilot-instructions.md) | [Português](copilot-instructions.pt_BR.md)

# KOF repository instructions for GitHub Copilot

Before triaging or implementing any issue or pull request, read the repository-root `AGENTS.md` and `docs/development/DECISIONS.md`.

For issue and pull-request triage, use `.github/skills/kof-first-triage/SKILL.md` and apply D-KOF-FIRST before treating a report as a bug.

Never use Java, Kotlin, C#, JavaScript, the JVM, another runtime, a paper, a benchmark, or a forum as the KOF language oracle. First prove valid KOF syntax, identify the governing KOF contract, search the existing KOF idiom, and measure current behavior when needed.

Do not implement new grammar, semantics, inference, flow analysis, operators, type behavior, or frozen stdlib/API surface under a `fix:` label unless a current KOF decision explicitly authorizes that contract.

For NOT-VALID reports, show the supported KOF form and close or recommend closure as not-a-bug. For contract conflicts, cite the active decision and do not merge the conflicting bugfix. For legitimate ideas outside the contract, prepare a design request and wait for the maintainer decision.

For confirmed bugs, follow the repository quality gate: reproduce -> RED -> root-cause fix -> regression test -> GREEN -> relevant target parity -> required suite. Never claim evidence that was not actually measured.
