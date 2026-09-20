[Português](release-beta-0.5.0-prep.pt_BR.md) | [English](release-beta-0.5.0-prep.md)

# Release 0.5.0 — preparation (branch `beta-0.5.0`)

Decision: `DECISIONS.md` §D-BRANCH-0.5.0 (20/09, maintainer order). Active
branch is `beta-0.5.0`; `beta-0.4.0` only receives in-flight landings and
release prep. This doc is the queue — it stays in `docs/development/` until
the release is cut (three-states rule).

## Checklist (ordered — version number and tag are the maintainer's call, rule 6)

1. [ ] Land what is in flight: §374/#553 (`.22` — WIP in
       `JvmOpCollections`), §371/#550 (CLI cross build), §378/#554 (docs
       gate). Each closes with 4-target proof; docs lane ff `beta-0.5.0`
       after every landing on `beta-0.4.0`.
2. [ ] CodeQL debt (#555): **TRIAGE CLOSED 20/09 (unit I, §385)** — the 40
       in the window: 13 fixed in code with targeted tests, 26 dismissed with
       a real reason (25 test-harness `used in tests` + FP JEP 443 #876),
       #938 fixed by the tooling lane awaiting `main` re-scan. The gate is now
       BASELINE-driven (`scripts/codeql-baseline.txt`: only NEW alerts block;
       `CODEQL_GATE_SKIP` requires a reason, prints a banner, logs to
       `.git/codeql-gate-skips.log`; ignored in CI) — `scripts/codeql-gate.sh
       --fast` already measures GREEN with no skip (rc=0). Still needed to
       tick [x]: ff of `q555` + first re-scan closing the 14 ids tolerated in
       the baseline (prune those lines then); #563 (src/test family in CI)
       follows its own queue.
3. [ ] Version bump: `pom.xml` `<revision>0.4.7-beta</revision>` to whatever
       the maintainer decides at the cut. Check hardcoded version refs
       (tests/javadoc mention the artifact version) BEFORE bumping; never a
       unilateral edit.
4. [ ] CHANGELOG cut (EN+PT): a `0.5.0` section gathering the unreleased
       bullets; `AGENTS.md`(+PT) header `Version:` updated in the same
       commit.
5. [ ] Tally: `'Current build: **N**'` in `docs/backend-parity.md`(+PT) from
       the first GREEN hosted CI Build+Tests run on the release tip
       (measured from the job log, never memory).
6. [ ] Stability proof: full suite 0F/0E + 5/5 conformance matrix MEASURED
       on the tag candidate (AGENTS §Stability — tag only after green).
7. [ ] Tag + release notes (EN+PT); declare `beta-0.4.0` closed except for
       the residual-fix list.

## Open issues that travel to `beta-0.5.0`

#550 (§371), #553 (§374), #554 (§378), #555 (CodeQL umbrella). Announced on
each issue and via the `DOING.md`(+PT) banner.
