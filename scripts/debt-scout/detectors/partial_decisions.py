#!/usr/bin/env python3
"""partial_decisions.py — the partial/blocked decision detector (V2 spec
§17 "Detector de decisoes parciais"; contract §11 Wave 1 scope).

Reads the BODY of `docs/development/DECISIONS.md` (not the index — the
index is a hint that can drift, see kof_first.py) and emits one signal
per decision whose `**State:**` line is `PARTIAL`, `BLOCKED` or
`IN_PROGRESS`.

**A partial decision is NOT debt by itself** (V2 §17: "`PARTIAL` nao
significa divida automaticamente"). Debt only appears with *partial
state + missing tracking + recurring cost*. So every signal here is
`C1` — a real structural fact (the normative ledger itself says the
contract is incomplete), never more. Whether it is tracked is answered
later by `ownership.py` (DOING.md) and the live duplicate check; the
recurring-cost part has no deterministic detector yet, so nothing from
here can reach C3 — the honest result.

The governing contract is known by construction (it IS the decision),
so each candidate carries `contract_ids: [<decision id>]`.

CLI:
  partial_decisions.py [--root DIR]  -> JSON list of C1 candidates
  partial_decisions.py --selftest
"""
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import fingerprint  # noqa: E402
import schema  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

RULE_ID = "KOF-DEBT-DECISION-001"
RULE_VERSION = "1.0.0"
DECISIONS_PATH = "docs/development/DECISIONS.md"
INCOMPLETE_STATES = ("PARTIAL", "BLOCKED", "IN_PROGRESS")

_HEADER_RE = re.compile(r"^## (D-[A-Z][A-Z0-9.-]*[A-Z0-9])\b(.*)$")
_STATE_RE = re.compile(r"\*\*State:\*\*\s*`?([A-Z_]+)`?")
_STATE_LOOKAHEAD = 12  # the State line sits right under the header

# Coarse, explicit decision-id -> domain map; anything else is governance.
_DOMAIN_BY_PREFIX = (
    ("D-SEC", "SECURITY"),
    ("D-ENUM", "SEMANTICS"),
    ("D-NULL", "NULLABILITY"),
    ("D-FFI", "FFI_ABI"),
    ("D-APP", "CLI_TOOLING"),
    ("D-DB", "STDLIB"),
    ("D-TIME", "STDLIB"),
)


def domain_for(decision_id):
    for prefix, domain in _DOMAIN_BY_PREFIX:
        if decision_id == prefix or decision_id.startswith(prefix):
            return domain
    return "CI_GOVERNANCE"


def find_incomplete_decisions(text):
    """Pure: DECISIONS.md text -> list of (decision_id, header_lineno,
    state, title). Only the first State line within the lookahead window
    of a `## D-...` header counts (a State mentioned deeper in the body
    belongs to a sub-item, not the decision)."""
    lines = text.splitlines()
    found = []
    for i, line in enumerate(lines):
        m = _HEADER_RE.match(line)
        if not m:
            continue
        decision_id, rest = m.group(1), m.group(2)
        title = rest.strip(" —-").strip()
        for j in range(i + 1, min(i + 1 + _STATE_LOOKAHEAD, len(lines))):
            if _HEADER_RE.match(lines[j]) or lines[j].startswith("## "):
                break
            s = _STATE_RE.search(lines[j])
            if s:
                if s.group(1) in INCOMPLETE_STATES:
                    found.append((decision_id, i + 1, s.group(1), title))
                break
    return found


def _analyzed_sha(root):
    try:
        out = subprocess.run(["git", "-C", root, "rev-parse", "HEAD"],
                             capture_output=True, text=True, timeout=10)
        if out.returncode == 0:
            return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return "UNKNOWN"


def _candidate(decision_id, lineno, state, title, analyzed_sha):
    claim = f"decision {decision_id} is {state} in the normative ledger"
    finding_fp = fingerprint.finding_fingerprint(RULE_ID, decision_id, claim)
    domain = domain_for(decision_id)
    return {
        "schema": 2,
        "candidate_id": finding_fp.split(":", 1)[1][:16],
        "rule_id": RULE_ID,
        "rule_version": RULE_VERSION,
        "finding_fingerprint": finding_fp,
        "claim": claim,
        "confidence": "C1",
        "taxonomy": {
            "debt_type": "REQUIREMENTS_CONTRACT",
            "primary_domain": domain,
            "domains": sorted({domain, "CI_GOVERNANCE"}),
            "mechanism": "PARTIAL_MIGRATION",
        },
        "contract_ids": [decision_id],
        "locations": [{"path": DECISIONS_PATH, "line": lineno}],
        "publication": {
            "public_safe": True,
            "eligible": False,
            "reason": "C1 — an incomplete decision is a structural signal; "
                      "tracking and recurring cost are not proven (V2 §17)",
        },
        "lineage": {"analyzed_sha": analyzed_sha, "model_used": False,
                    "rule_id": RULE_ID, "rule_version": RULE_VERSION},
        "evidence": [{"type": "DECISION", "ref": f"{DECISIONS_PATH}:{lineno}",
                      "note": f"{decision_id} — {title}" if title else decision_id}],
    }


def scan(root="."):
    path = os.path.join(root, DECISIONS_PATH)
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
    except OSError:
        return []
    sha = _analyzed_sha(root)
    return [_candidate(d, ln, st, t, sha) for d, ln, st, t in find_incomplete_decisions(text)]


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

_FIXTURE = """# Decisions
## 0. Decision index
- **D-SEC** — security (index mention, not a body)

## D-SEC — security

**Date:** 2026-09-13
**State:** `PARTIAL`

## D-OK — done thing

**State:** `IMPLEMENTED`

## D-DEEP — state far below the header

para 1
para 2
para 3
para 4
para 5
para 6
para 7
para 8
para 9
para 10
para 11
**State:** `PARTIAL`

## D-ENUM207 — enum identity

**State:** `IN_PROGRESS`
### sub
**State:** `BLOCKED`

## D-X.1 — dotted

**Date:** x · **State:** `BLOCKED` (reason)
"""


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    found = find_incomplete_decisions(_FIXTURE)
    ids = [f[0] for f in found]
    check("PARTIAL, IN_PROGRESS and inline BLOCKED decisions are found",
          ids == ["D-SEC", "D-ENUM207", "D-X.1"])
    check("an IMPLEMENTED decision is not a signal", "D-OK" not in ids)
    check("a State line beyond the header window is not attributed "
          "(no guessing)", "D-DEEP" not in ids)
    check("only the FIRST State line counts (sub-item BLOCKED ignored)",
          [f[2] for f in found if f[0] == "D-ENUM207"] == ["IN_PROGRESS"])
    check("the index bullet is not mistaken for a body header",
          sum(1 for f in found if f[0] == "D-SEC") == 1)
    check("dotted decision IDs are kept whole", "D-X.1" in ids)

    c = _candidate("D-SEC", 5, "PARTIAL", "security", "f" * 40)
    check("an emitted candidate is schema-valid", schema.validate_candidate(c) == [])
    check("an emitted candidate is always C1, never eligible",
          c["confidence"] == "C1" and c["publication"]["eligible"] is False)
    check("governing contract is carried by construction",
          c["contract_ids"] == ["D-SEC"])
    check("D-SEC maps to the SECURITY domain (privately routed downstream)",
          c["taxonomy"]["primary_domain"] == "SECURITY")
    c2 = _candidate("D-SEC", 999, "PARTIAL", "security", "0" * 40)
    check("fingerprint is stable across line/sha movement",
          c["finding_fingerprint"] == c2["finding_fingerprint"])

    root = os.path.join(os.path.dirname(__file__), "..", "..", "..")
    if os.path.exists(os.path.join(root, DECISIONS_PATH)):
        live = scan(root)
        check(f"live: real DECISIONS.md scanned ({len(live)} incomplete decisions)",
              all(schema.validate_candidate(x) == [] for x in live)
              and all(x["confidence"] == "C1" for x in live))
    else:
        print("  FAIL — real DECISIONS.md not found for the live check")
        ok = False
    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("partial_decisions --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    root = argv[argv.index("--root") + 1] if "--root" in argv else "."
    print(json.dumps(scan(root), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
