#!/usr/bin/env python3
"""priority.py — the principal/interest/lock-in vector (Wave 2, contract
§11/§12, V2 spec §11/§12). Deliberately NOT a single score: "priority =
a+b+c+d" was the V1 mistake the V2 spec's own §0 calls out and rejects.

Each dimension gets a `level` (`none|low|medium|high|unknown`) and an
`evidence` list of short, concrete strings — never invented numbers
(hours, dollars, story points are explicitly forbidden, contract §11.1).
With today's two detectors (SATD markers, branch-drift), there is no
recurrence/interest measurement infrastructure yet, so every dimension
starts honestly at `unknown` or `none` with an empty evidence list
rather than a guessed value — a vector of `unknown` is the correct,
honest output when nothing has been measured, not a bug to paper over.

CLI:
  priority.py < cluster.json   -> the same cluster with `priority_vector` added
  priority.py --selftest
"""
import json
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

LEVELS = {"none", "low", "medium", "high", "unknown"}
DIMENSIONS = (
    "principal", "interest_observed", "lock_in", "reach", "recurrence",
    "migration_cost_growth", "security_relevance", "user_observability",
    "time_sensitivity", "uncertainty",
)


def _dim(level, evidence=None):
    if level not in LEVELS:
        raise ValueError(f"unknown level {level!r}, must be one of {sorted(LEVELS)}")
    return {"level": level, "evidence": list(evidence or [])}


def compute_priority_vector(cluster):
    """cluster (from cluster.py) -> priority_vector dict. Deterministic,
    conservative: only claims a level above `unknown`/`none` when the
    cluster itself carries the evidence for it."""
    members = cluster.get("members", [])
    member_count = cluster.get("member_count", len(members))
    domains = set()
    for m in members:
        domains.update(m.get("taxonomy", {}).get("domains", []))

    vector = {
        "principal": _dim("unknown", [
            "no repayment-cost measurement exists yet for this detector family",
        ]),
        "interest_observed": _dim("unknown", [
            "no recurrence/maintenance-incident tracking exists yet",
        ]),
        "lock_in": _dim("none", [
            "no evidence this is encoded in tests, docs-as-normative, or "
            "external/ecosystem code",
        ]),
        "reach": _dim(
            "medium" if len(domains) > 1 else "low",
            [f"touches {len(domains)} domain(s): {sorted(domains)}"],
        ),
        "recurrence": _dim(
            "low" if member_count > 1 else "none",
            [f"{member_count} identical-fingerprint member(s) in this cluster"],
        ),
        "migration_cost_growth": _dim("unknown", []),
        "security_relevance": _dim(
            "high" if "SECURITY" in domains else "none",
            ["primary_domain includes SECURITY"] if "SECURITY" in domains else [],
        ),
        "user_observability": _dim("unknown", []),
        "time_sensitivity": _dim("unknown", []),
        "uncertainty": _dim(
            "high" if cluster.get("confidence") in ("C0", "C1") else "medium",
            [f"cluster confidence is {cluster.get('confidence')}"],
        ),
    }
    return vector


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    try:
        _dim("critical")
        check("an invalid level is rejected", False)
    except ValueError:
        check("an invalid level is rejected", True)

    trivial = {
        "confidence": "C0", "member_count": 1,
        "members": [{"taxonomy": {"domains": ["CLI_TOOLING"]}}],
    }
    v = compute_priority_vector(trivial)
    check("all 10 dimensions are present", set(v.keys()) == set(DIMENSIONS))
    check("no dimension silently claims a numeric score — each is a dict "
          "with level+evidence", all(isinstance(d, dict) and "level" in d
                                     and "evidence" in d for d in v.values()))
    check("an unmeasured cluster reports principal=unknown, never a "
          "fabricated level", v["principal"]["level"] == "unknown")
    check("a single-member cluster reports recurrence=none",
          v["recurrence"]["level"] == "none")

    dup = {
        "confidence": "C1", "member_count": 3,
        "members": [{"taxonomy": {"domains": ["CLI_TOOLING", "CI_GOVERNANCE"]}}],
    }
    v2 = compute_priority_vector(dup)
    check("a multi-member cluster reports recurrence=low with evidence",
          v2["recurrence"]["level"] == "low" and v2["recurrence"]["evidence"])
    check("a cluster touching 2+ domains reports reach=medium",
          v2["reach"]["level"] == "medium")

    sec = {
        "confidence": "C2", "member_count": 1,
        "members": [{"taxonomy": {"domains": ["SECURITY"]}}],
    }
    v3 = compute_priority_vector(sec)
    check("a SECURITY-domain cluster reports security_relevance=high",
          v3["security_relevance"]["level"] == "high")

    check("nothing here ever produces a single aggregate score field "
          "(no 'priority' key at top level)", "priority" not in v)

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("priority --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    cl = json.load(sys.stdin)
    cl["priority_vector"] = compute_priority_vector(cl)
    print(json.dumps(cl, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
