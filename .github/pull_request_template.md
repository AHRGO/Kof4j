[English](pull_request_template.md) | [Português](pull_request_template.pt_BR.md)

<!--
  KOFLANG / KOF4J PULL REQUEST POLICY

  ⚠️ ATTENTION: NEVER OPEN PULL REQUESTS DIRECTLY AGAINST THE `main` BRANCH!
  The `main` branch is reserved for stable releases controlled by the maintainer.
  All development, fixes and contributions must be opened against the active
  `beta` branch (current example: `beta-0.4.0`).
  
  PRs opened against the `main` branch by unauthorized accounts will be
  automatically blocked by the repository's guard action.
-->

## 🎯 Target Base Branch
- [ ] I confirm that this PR targets a **`beta-*`** branch (e.g.: `beta-0.4.0`) and **NOT** `main`.

---

## 📝 Description of the Change
<!-- Describe clearly and concisely what was added, fixed or refactored. -->

---

## 🔗 Related Issue
<!-- Every PR must reference an open issue (e.g.: Fixes #123, Closes #456). Rule 6 of AGENTS.md. -->
Fixes #

---

## 📐 KOF-First Contract (`D-KOF-FIRST`)
<!-- Every change answers the four gates below. "Language X does it this way" is never a contract source. -->
**Valid Kof reproducer** (the snippet that exercises the change):

```kof

```

- **Contract source** (DECISIONS.md entry, normative doc, conformance/golden test, or parity matrix) that defines the expected behavior:
- **RED before the production change** — target(s), expected by the Kof contract, actual:
- **Root cause** (not the symptom):
- **Classification** (`Real bug` / `Target divergence` / `Real gap` / `Design request` / `Not-valid` / `Contract ambiguity`):
- **Does this change the Kof surface?** If yes, the maintainer's decision authorizing it (rule 6) — a new accepted grammar form is a language feature, not a parser fix:
- **External references used** (implementation/theory only; none of them defines the Kof surface):

---

## 🧪 How It Was Tested (Quality Gate)
<!-- The test PROVES that the code works. List the commands run and the tests added. -->
- [ ] `mvn -o -pl kof-compiler -am compile -q` ran without errors
- [ ] Tests added/changed covering the happy path and edge cases (Q3)
- [ ] Suite run and green (`mvn test ...`)

---

## 📋 Pre-Submission Checklist
- [ ] No change contains stubs or facade TODOs (Q7)
- [ ] `AGENTS.md` rules respected (≤500 lines/class, zero regression)
- [ ] Documentation or `DOING.md` updated if applicable
