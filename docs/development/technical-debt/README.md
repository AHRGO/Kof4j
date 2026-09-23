[English](README.md) | [Português](README.pt_BR.md)

# KOF Technical Debt Scout

**Status:** IN DEVELOPMENT — Wave 1 DONE, Wave 2 (evidence qualification)
DONE, both shadow-only. See `DOING.md` for the live claim/owner and next
step.

This is tooling that finds and documents *historical* technical debt
before a temporary implementation choice, a target divergence, or an
obsolete assumption silently becomes compatibility that cannot be
changed anymore. It does not fix anything and it does not decide
language contract — it produces evidence for a human (today: for
`docs/development/tech-debt.md`, the maintainer-owned ledger opened
2026-09-23).

## Read in this order

1. `DEBT_SCOUT_CONTRACT.md` — the operating contract: definitions, hard
   stops, the KOF-first gate, confidence scale, fingerprints, publication
   routing/trust rollout, privilege rules. This is what the code under
   `scripts/debt-scout/` implements.
2. `TAXONOMY.md` — the three-axis classification every candidate uses.
3. `docs/development/DECISIONS.md` §`D-DEBT-SCOUT`/§`D-DEBT-SCOUT-W2` —
   the decision records that authorized this front and its current
   scope limit (no Issue publication capability exists yet).

## Where the design came from

Two research documents were supplied by the user in the session that
opened this front (2026-09-22/23): an initial proposal and a
research-revised V2 that supersedes it structurally (candidate ≠
confirmed debt; `C2` never auto-opens an Issue; priority is a vector,
never a single score; reuse `scripts/agent-*.sh` instead of a parallel
governance system). Those source documents are 100+ sections each and
are **not** copied into this repo — `DEBT_SCOUT_CONTRACT.md` is the
condensed, code-synchronized distillation the scripts actually follow.
When the two disagree, the contract in this folder wins.

## What exists today (Wave 1 + Wave 2)

```text
scripts/debt-scout/
├── config.py            — loads/validates .debt-scout.yml (stdlib only)
├── schema.py             — Candidate schema v2 (validation)
├── taxonomy.py            — 3-axis enums (cross-checked against TAXONOMY.md)
├── fingerprint.py        — finding/debt fingerprints (stable, sha256)
├── branch_discovery.py   — resolves default/active branch; flags
│                            contract drift instead of hardcoding a ref
├── detectors/
│   ├── README.md          — the fixture convention every detector follows
│   ├── satd.py            — SATD marker detector (TODO/FIXME/HACK/XXX)
│   └── partial_decisions.py — PARTIAL/BLOCKED/IN_PROGRESS decisions (C1)
├── history.py             — git-blame origin (verbatim subject; shallow = NOT_CHECKED)
├── ownership.py           — DOING.md active-claim cross-check (RESOLUTION_IN_PROGRESS)
├── cluster.py             — root-cause clustering + debt_fingerprint
├── priority.py            — principal/interest/lock-in vector (never a score)
├── kof_first.py           — deterministic KOF-first context builder
├── confidence.py          — C2/C3 classifier (mandatory-evidence checklist)
├── sarif.py               — SARIF 2.1.0 writer for located findings
├── inbox.py               — Debt Inbox for no-location C2+ findings
└── scan.py               — orchestrator CLI (--phase state|deterministic,
                             --check-duplicates, --history, --sarif-out,
                             --inbox-out, --metrics-out)
```

Every module has a `--selftest` and/or a `scripts/tests/debt-scout-*.sh`
test, each including a live run against this repo's own real data — that
discipline caught 4 real bugs before they shipped (see `DOING.md`'s
Wave-1/Wave-2 entries for each one). **No script calls the GitHub Issues
write API.** No workflow in `.github/workflows/` grants this system
`issues: write` — Wave 2 added exactly one new privilege,
`security-events: write` (SARIF upload only), justified in
`scripts/workflow-permissions.txt`.

## What does NOT exist yet (do not assume it runs)

The C3 canary publisher (the only thing that would ever open a GitHub
Issue), any LLM-backed qualification step, the detectors that would
prove `current_implementation_identified` / `debt_mechanism_proved` /
`exit_condition_expressible` (so no cluster can reach `C3` yet — the
honest result), a dedicated skipped-test detector (deliberately NOT
built: `scripts/audit-stubs.sh` §5/§6/§12 already owns
`@Disabled`/`assumeTrue`/weak-green, same division of labor as
`satd.py`), tombstones/feedback/rule circuit breakers (V2 Wave 4),
the ecosystem/Crater-style corpus experiment, and `kof debt`/`kof fix`
CLI surfaces. Each is a separate, explicitly scoped future unit — see
`DEBT_SCOUT_CONTRACT.md` §11 and `DOING.md`. Advancing past Wave 2 needs
its own `DECISIONS.md` entry with the maintainer's phase authorization.
