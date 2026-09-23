#!/usr/bin/env python3
"""cluster.py — root-cause clustering (Wave 2, `docs/development/
technical-debt/DEBT_SCOUT_CONTRACT.md` referencing V2 spec §35).

Groups candidates that are the SAME conceptual debt, not just the same
detector. The key is `debt_type + primary_domain + mechanism +
root_symbol + normalized_claim` — deliberately including the claim, so
two different SATD markers at two different lines do NOT merge just
because they share a file (there is no proof they share a root cause;
V2 §22's own counter-example: "5 bugs in JVM package therefore
architecture debt" is explicitly the WRONG conclusion). Two candidates
only merge here when they are, in substance, the identical finding
(exact same fingerprint) — real cross-location root-cause merging
needs a detector that actually proves the shared cause (contract §22),
which none of Wave 1/2's detectors do yet.

Each cluster gets one `debt_fingerprint` (`fingerprint.debt_fingerprint`),
computed once and attached to every member candidate.
`governing_contract_id` is `UNKNOWN` at this stage — `kof_first.py`
fills it in where it can be determined deterministically.

CLI:
  cluster.py < candidates.json   -> clusters JSON to stdout
  cluster.py --selftest
"""
import json
import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
import fingerprint  # noqa: E402
import schema  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

UNKNOWN_CONTRACT = "UNKNOWN"


def _root_symbol(candidate):
    locations = candidate.get("locations") or []
    if locations:
        return locations[0].get("path", "UNKNOWN")
    return candidate.get("claim", "")[:80]


def cluster_key(candidate):
    tax = candidate["taxonomy"]
    return (
        tax["debt_type"],
        tax["primary_domain"],
        tax["mechanism"],
        _root_symbol(candidate),
        candidate["claim"],
    )


def _shared_contract_id(members):
    """A detector that knows its governing contract by construction
    (e.g. partial_decisions.py: the finding IS the decision) carries it in
    `contract_ids`. The cluster keeps it only when EVERY member names the
    same single known id — any disagreement or absence stays UNKNOWN
    (kof_first.py may still resolve it; nothing here guesses)."""
    ids = set()
    for m in members:
        own = [c for c in (m.get("contract_ids") or []) if c != UNKNOWN_CONTRACT]
        if len(own) != 1:
            return UNKNOWN_CONTRACT
        ids.add(own[0])
    return ids.pop() if len(ids) == 1 else UNKNOWN_CONTRACT


def cluster_candidates(candidates):
    """candidates (list of schema-v2 dicts) -> list of cluster dicts,
    each carrying its own `debt_fingerprint` and the member candidates
    (each member also gets `debt_fingerprint` attached)."""
    groups = OrderedDict()
    for c in candidates:
        key = cluster_key(c)
        groups.setdefault(key, []).append(c)

    clusters = []
    for (debt_type, domain, mechanism, symbol, claim), members in groups.items():
        governing_contract_id = _shared_contract_id(members)
        debt_fp = fingerprint.debt_fingerprint(
            debt_type, domain, mechanism, governing_contract_id, symbol, claim,
        )
        for m in members:
            m["debt_fingerprint"] = debt_fp
            if not m.get("contract_ids"):
                m["contract_ids"] = [governing_contract_id]
        confidences = {m["confidence"] for m in members}
        # a cluster's own confidence is the HIGHEST member's — clustering
        # never silently downgrades a C1 finding by grouping it with C0s.
        order = ["C0", "C1", "C2", "C3"]
        cluster_confidence = max(confidences, key=order.index)
        clusters.append({
            "debt_fingerprint": debt_fp,
            "taxonomy": {
                "debt_type": debt_type,
                "primary_domain": domain,
                "mechanism": mechanism,
            },
            "root_symbol": symbol,
            "confidence": cluster_confidence,
            "member_count": len(members),
            "members": members,
        })
    return clusters


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def _candidate(debt_type, domain, mechanism, symbol, claim, confidence="C0"):
    return {
        "schema": 2,
        "candidate_id": "x",
        "rule_id": "TEST",
        "rule_version": "1.0.0",
        "claim": claim,
        "confidence": confidence,
        "taxonomy": {
            "debt_type": debt_type, "primary_domain": domain,
            "domains": [domain], "mechanism": mechanism,
        },
        "locations": [{"path": symbol, "line": 1}],
        "publication": {"public_safe": True, "eligible": False, "reason": "test"},
        "lineage": {"analyzed_sha": "deadbeef", "model_used": False},
        "evidence": [],
    }


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    a = _candidate("CODE", "CLI_TOOLING", "WORKAROUND", "f1", "TODO one")
    b = _candidate("CODE", "CLI_TOOLING", "WORKAROUND", "f2", "TODO two")
    clusters = cluster_candidates([a, b])
    check("two distinct findings (different file+claim) never merge "
          "without proven shared root cause", len(clusters) == 2)

    c = _candidate("CODE", "CLI_TOOLING", "WORKAROUND", "f1", "TODO one")
    clusters2 = cluster_candidates([a, c])
    check("two identical findings (same fingerprint) merge into one cluster",
          len(clusters2) == 1 and clusters2[0]["member_count"] == 2)

    every_candidate_has_fp = all("debt_fingerprint" in m
                                  for cl in clusters2 for m in cl["members"])
    check("every member candidate gets debt_fingerprint attached",
          every_candidate_has_fp)

    c1_high = _candidate("DOCUMENTATION", "CI_GOVERNANCE", "DOC_CODE_DRIFT",
                          "AGENTS.md", "branch drift", confidence="C1")
    c1_dup = _candidate("DOCUMENTATION", "CI_GOVERNANCE", "DOC_CODE_DRIFT",
                         "AGENTS.md", "branch drift", confidence="C0")
    clusters3 = cluster_candidates([c1_high, c1_dup])
    check("cluster confidence is the max of its members, never silently "
          "downgraded", clusters3[0]["confidence"] == "C1")

    total_members = sum(cl["member_count"] for cl in clusters2)
    check("no candidate is lost during clustering (member counts sum "
          "to the input size)", total_members == 2)

    root = os.path.join(os.path.dirname(__file__), "..", "..")
    real_config = os.path.join(root, ".debt-scout.yml")
    if os.path.exists(real_config):
        from detectors import satd
        live = satd.scan(root)
        live_clusters = cluster_candidates(live)
        live_members = sum(cl["member_count"] for cl in live_clusters)
        check("live clustering of the real repo's SATD scan loses no "
              "candidates", live_members == len(live))
        check("every live cluster's members are schema-valid after "
              "debt_fingerprint/contract_ids are attached",
              all(schema.validate_candidate(m) == []
                  for cl in live_clusters for m in cl["members"]))
    else:
        print("  FAIL — real repo .debt-scout.yml not found")
        ok = False

    shared = cluster_candidates([
        dict(_candidate("REQUIREMENTS_CONTRACT", "SECURITY", "PARTIAL_MIGRATION",
                        "docs/development/DECISIONS.md", "decision D-SEC is PARTIAL"),
             contract_ids=["D-SEC"])])
    unknown = cluster_candidates([
        _candidate("REQUIREMENTS_CONTRACT", "SECURITY", "PARTIAL_MIGRATION",
                   "docs/development/DECISIONS.md", "decision D-SEC is PARTIAL")])
    check("a member's own single contract id becomes the cluster's governing "
          "contract (and changes the debt fingerprint vs UNKNOWN)",
          shared[0]["members"][0]["contract_ids"] == ["D-SEC"]
          and shared[0]["debt_fingerprint"] != unknown[0]["debt_fingerprint"])
    check("a member with no contract id still gets UNKNOWN",
          unknown[0]["members"][0]["contract_ids"] == ["UNKNOWN"])

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("cluster --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    candidates = json.load(sys.stdin)
    print(json.dumps(cluster_candidates(candidates), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
