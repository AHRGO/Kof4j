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

## Release gate (`D-RELEASE-0.5.0-GATE`, 09/20/2026, maintainer directive)

The 0.5.0 release is cut only when **all seven conditions** hold, each one
**measured** (never by eye). This gate refines the checklist above: the
checklist is the tactical queue, these seven are the acceptance. The
maintainer's directive is the priority for "releasing the 0.5.0 gate to all
agents".

| # | Condition | How it is measured | State 09/20 |
|---|---|---|---|
| 1 | 100% parity between targets | per-target matrix + golden byte parity where the contract requires; divergence = bug or diagnosed `XXX00x`. **Auto-measured** by `check_release_050_gate.sh` (runs `scripts/target-matrix.sh`, EG-5, and reads its `PARITY: 100%` line) | GREEN (6 core targets byte-parity vs JVM oracle) |
| 2 | No pending decision | `DECISIONS.md` has no open question changing the surface | NEEDS-REVIEW |
| 3 | All loose `docs/development/*.md` concluded and moved out | three-states rule; only work with pending implementation stays | RED (in-flight docs) |
| 4 | Total stability | full suite 0F/0E + 5/5 conformance matrix on the candidate. **Auto-measured** from a real suite log via `scripts/stability-report.sh` (`KOF_SUITE_LOG=…`), which requires the last `TOTAL` to be 0F/0E; the conformance guards are part of that suite | NEEDS-MEASURE (no RC-day run yet) |
| 5 | 0 open issues that are a bug | GitHub OPEN issues with a `bug` label = 0 (includes #566 — maintainer 09/20) | RED (#566) |
| 6 | All edges closed | the FULL EG queue (EG-1..EG-10) closed + open `1.0-blocks` = 0; the 0.5.0 waits until each owner closes/moves their own work. **EG-5/EG-9/EG-10 mechanisms DONE 20/09**; open: #566 + EG-8 | RED (#566 + EG-8) |
| 7 | Nothing pending in bugs-and-gaps | `check_known_bugs_status.sh` live set empty + `specification-gaps.md` 0 open | RED (19 live) |

Mechanized by `scripts/check_release_050_gate.sh` (reports each condition as
GREEN / RED / NEEDS-MEASURE; RED-first test
`scripts/tests/check-release-050-gate-test.sh`). RED is expected until the
queue closes — the gate is the driver, not a blocker to work around.
