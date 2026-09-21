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
3. [ ] Version bump: **DECIDED 09/20/2026 — the release ships as
       `0.5.0-beta`** (beta suffix kept, no codename; `D-RELEASE-0.5.0-GATE`
       addendum). `VERSION`/`pom.xml` are already at `0.5.0-beta`; only the
       CHANGELOG/tag remain. Check hardcoded version refs (tests/javadoc
       mention the artifact version) BEFORE bumping; never a unilateral edit.
       **Audited 21/09 (9093): clean** — the remaining `0.4.0` hits are
       provenance comments (when a port landed) and `beta-0.4.0` used as a
       *branch name* by `codeql-gate.sh` (monitors both) and by test fixtures;
       none hardcode the artifact version.
4. [ ] CHANGELOG cut (EN+PT): a `0.5.0` section gathering the unreleased
       bullets; `AGENTS.md`(+PT) header `Version:` updated in the same
       commit. (Lanes may draft it now; the cut still waits on the seven
       conditions.)
5. [ ] Tally: `'Current build: **N**'` in `docs/backend-parity.md`(+PT) from
       the first GREEN hosted CI Build+Tests run on the release tip
       (measured from the job log, never memory).
6. [ ] Stability proof: full suite 0F/0E + 5/5 conformance matrix MEASURED
       on the tag candidate (AGENTS §Stability — tag only after green).
7. [ ] Tag + release notes (EN+PT); declare `beta-0.4.0` closed except for
       the residual-fix list. **`main` stays frozen until this release**
       (maintainer 09/20/2026): the 12 pre-fix CodeQL alerts on `main` are
       ported on release day, not before; the gate measures `beta-0.5.0`.

## Open issues that travel to `beta-0.5.0`

#550 (§371), #553 (§374), #554 (§378), #555 (CodeQL umbrella). Announced on
each issue and via the `DOING.md`(+PT) banner.

## Release gate (`D-RELEASE-0.5.0-GATE`, 09/20/2026, maintainer directive)

The 0.5.0 release is cut only when **all seven conditions** hold, each one
**measured** (never by eye). This gate refines the checklist above: the
checklist is the tactical queue, these seven are the acceptance. The
maintainer's directive is the priority for "releasing the 0.5.0 gate to all
agents".

| # | Condition | How it is measured | State 21/09 (measured — never by eye) |
|---|---|---|---|
| 1 | 100% parity between targets | per-target matrix + golden byte parity where the contract requires; divergence = bug or diagnosed `XXX00x`. **Auto-measured** by `check_release_050_gate.sh` (runs `scripts/target-matrix.sh`, EG-5, and reads its `PARITY: 100%` line) | GREEN (measured 21/09: `PARITY: 100%` on jvm/x86-64/riscv64/aarch64/JS/Script vs the JVM oracle; the tree jar was rebuilt with `scripts/build-kof-jar.sh`, which also stamps it so a rebase no longer fakes "stale"; on a rootless host the cross toolchain (binutils/qemu/libc) is set up by `scripts/setup-cross-toolchain.sh`, which extracts the `.deb`s to a local prefix and points `KOF_CROSS_SYSROOT` at it) |
| 2 | No pending decision | `DECISIONS.md` has no open question changing the surface | GREEN (no unresolved `[? MEL]` candidate; `decision-pending/` extinct) |
| 3 | All loose `docs/development/*.md` concluded and moved out | three-states rule; only work with pending implementation stays | RED (3 owned docs in flight: `ffi-abi-structs` [jonas], `IMPLEMENTATION-UNIVERSAL-PLATFORM` [9093], `makealive-plan` [.18]) |
| 4 | Total stability | full suite 0F/0E + 5/5 conformance matrix on the candidate. **Auto-measured** from a real suite log via `scripts/stability-report.sh` (`KOF_SUITE_LOG=…`); GREEN requires the `TOTAL` to be 0F/0E **and** the log **stamped** (`SUITE-SHA` == tip, `SUITE-DIRTY=0`) — a log from another commit or from a dirty tree is `unknown`, never a false GREEN | MEASURED GREEN on `8f459b8e` 21/09 ~06:0x (lane .18): first self-certifying `safe-suite.sh` log — `TOTAL: tests=3355 failures=0 errors=0 skipped=223`, `SUITE-SHA`==tip, `SUITE-DIRTY=0`, and `stability-report.sh` returned GREEN from it (mechanism bug found and fixed the same round: the old `safe-suite.sh` printed `TOTAL` to the console only and never appended it to the log, so NO stamped run could ever be certified). The candidate must still be re-measured on the final clean tip at cut time — that is condition 6's own rule, not this row's doubt |
| 5 | 0 open issues that are a bug | GitHub OPEN issues with a `bug` label = 0 | GREEN (0 open bug issues; the condition reads the **query's exit code** — an API failure is `UNKNOWN`, never GREEN) |
| 6 | All edges closed | the FULL EG queue (EG-1..EG-10) closed + open `1.0-blocks` = 0; the 0.5.0 waits until each owner closes/moves their own work. **EG-5/EG-9/EG-10 mechanisms DONE 20/09**; open: EG-8 | RED (EG-8; an unreadable EG table is `UNKNOWN`, never GREEN) |
| 7 | Nothing pending in bugs-and-gaps | `check_known_bugs_status.sh` live set empty + `specification-gaps.md` 0 open | RED (17 live at the tip; an unreadable ledger is `UNKNOWN`, never GREEN) |

Mechanized by `scripts/check_release_050_gate.sh` (reports each condition as
GREEN / RED / NEEDS-MEASURE / UNKNOWN; RED-first test
`scripts/tests/check-release-050-gate-test.sh`). Every data-driven condition
**refuses GREEN when its source is unreadable** — stale jar, a suite log from
another commit or from a dirty tree, a failed GitHub query, an unparsable EG
table, an unreadable bug ledger, a missing decision source or loose-doc list:
all seven conditions are inconclusive, never falsely green.
RED is expected until the queue closes — the gate is the driver, not a blocker
to work around.

### Recovery — clearing the auto-measured conditions

```bash
scripts/build-kof-jar.sh                                # cond. 1: rebuild + stamp the tree jar
scripts/target-matrix.sh                                #          -> PARITY: 100% (6 core targets)
SAFE_SUITE_LOG="$PWD/.suite.log" scripts/safe-suite.sh  # cond. 4: run on a CLEAN tree
KOF_SUITE_LOG="$PWD/.suite.log" scripts/check_release_050_gate.sh
```
