# Detectors — fixture convention

Every detector under this directory follows the same shape (see
`satd.py` as the reference implementation; `partial_decisions.py` is
the second, and the first that carries its governing contract by
construction in `contract_ids`):

- a `RULE_ID` / `RULE_VERSION` constant pair (contract §16, rule schema
  §60);
- a pure function that turns already-read text/data into signals, with
  **no IO** (`find_markers_in_text` in `satd.py`) — this is what unit
  tests exercise without touching the filesystem;
- a `scan(root)` function that does the IO (walk the tree, read files)
  and calls the pure function, producing schema-v2 candidates;
- a `--selftest` that proves, in order:
  1. the pure function's logic on synthetic fixtures (true positives
     AND explicit false-positive/near-miss fixtures — a detector with
     no false-positive fixture is not done, contract §70);
  2. every candidate the detector emits is schema-valid
     (`schema.validate_candidate`);
  3. a live run against this repo's own real tree doesn't crash and
     produces only the expected confidence level(s) — a live check is
     what caught two real bugs before they shipped in Wave 1
     (`branch_discovery.py`'s regex and `_drift_candidate`'s schema
     compliance) that a synthetic-only fixture suite had not exercised.

Before adding a new detector, read `DEBT_SCOUT_CONTRACT.md` §16-§34 for
the specific detector family's rules (SATD markers, contract drift,
cross-target, test debt, ...) and confirm it isn't redundant with an
existing repo tool first (the precedent: `satd.py` deliberately excludes
`*/src/main` and `*/src/test` Java because `scripts/audit-stubs.sh`
already owns that sweep — division of labor, not duplication, contract
§46/Anti-pattern 5).

A new detector never emits `confidence` above `C1` by itself — promotion
to `C2`/`C3` is `scripts/debt-scout/confidence.py`'s job, working from a
**cluster** of candidates plus qualification evidence, never a single
detector's own opinion.
