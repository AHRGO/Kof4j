#!/usr/bin/env python3
"""confidence.py — the C2/C3 confidence classifier (Wave 2, contract §5
"Escala de confianca" / V2 spec §7). Promotes a cluster past `C1` only
when there is real, checkable evidence for it — never by averaging many
weak signals (contract §5: "Confianca nao e media matematica").

**C3 requires every item of the mandatory checklist (§7).** Each item is
real evidence attached by a detector or qualifier, never a default:
`current_implementation_identified` / `debt_mechanism_proved` come from
`detectors/decision_evidence.py` (structured ledger fields + files that
exist in the tree), `exit_condition_expressible` from a detector that
formulates one, history from `history.py`, ownership from
`ownership.py`. `not_an_already_documented_gap` is the V2 §5 hard stop:
an incomplete part already recorded as an honest gap is `TRACKED`,
never an Issue. Any missing item keeps the cluster below `C3` and stays
visible in `c3_checklist`.

**C2 requires convergent-but-incomplete evidence:** a resolved
governing contract, a completed (not `NOT_CHECKED`) duplicate check,
and a meaningful classification. Without `check_duplicates` actually
running (needs network), no cluster reaches `C2` either — `confidence.py`
never promotes on missing information.

Never downgrades: a `C2`/`C3` cluster is returned unchanged (a
classifier can only add confidence with new evidence, contract §13
"ACCEPTED"/lifecycle states are a maintainer action, not this module's).

CLI:
  confidence.py < cluster-with-kof_triage-and-priority_vector.json
      -> the same cluster with `confidence` possibly promoted +
         `c3_checklist` attached for transparency
  confidence.py --selftest
"""
import json
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

_ORDER = ["C0", "C1", "C2", "C3"]

C3_REQUIREMENTS = (
    "governing_contract_identified",
    "current_implementation_identified",
    "observable_behavior_measured",
    "debt_mechanism_proved",
    "cost_lockin_evidence_exists",
    "historical_origin_searched",
    "duplicates_checked",
    "owner_collision_checked",
    "security_publication_gate_passed",
    "exit_condition_expressible",
    "not_an_already_documented_gap",
)


def evaluate_c3_checklist(cluster):
    triage = cluster.get("kof_triage", {}) or {}
    pv = cluster.get("priority_vector", {}) or {}
    dup = triage.get("duplicate_precedent_check", {}) or {}
    contract_source = triage.get("contract_source") or []
    interest_level = (pv.get("interest_observed") or {}).get("level")
    lockin_level = (pv.get("lock_in") or {}).get("level")
    domains = set()
    for m in cluster.get("members", []):
        domains.update(m.get("taxonomy", {}).get("domains", []))
    return {
        "governing_contract_identified": bool(contract_source)
        and contract_source != ["UNKNOWN"],
        # decision_evidence.py: real repo files the finding resolves to
        # (cited classes / files emitting its gap code). Nothing else counts.
        "current_implementation_identified": any(
            (m.get("qualification") or {}).get("implementation_refs")
            for m in cluster.get("members", [])),
        "observable_behavior_measured": bool(cluster.get("members")),
        # A marker is a SIGNAL, never a proven cause. Proved only when a
        # detector shows the mechanism from structured ledger fields:
        # mixed complete/incomplete items, or a state older than every
        # closed entry naming it.
        "debt_mechanism_proved": any(
            (m.get("qualification") or {}).get("kind") in ("PARTIAL_PROVED", "STALE_STATE")
            for m in cluster.get("members", [])),
        "cost_lockin_evidence_exists": (
            interest_level not in (None, "unknown")
            or lockin_level not in (None, "none", "unknown")
        ),
        # history.py: FOUND/UNKNOWN = searched; NOT_CHECKED (shallow
        # clone, no location, git failure) = not searched.
        "historical_origin_searched": (cluster.get("historical_origin") or {})
        .get("status") in ("FOUND", "UNKNOWN"),
        "duplicates_checked": dup.get("status") in ("NO_DUPLICATE_FOUND", "DUPLICATE"),
        # ownership.py: only a completed read of DOING.md that found NO
        # active owner clears this — OWNED means someone is already on it.
        "owner_collision_checked": (cluster.get("ownership") or {})
        .get("status") == "NOT_OWNED",
        "security_publication_gate_passed": "SECURITY" not in domains,
        "exit_condition_expressible": bool(cluster.get("exit_condition")),
        # V2 §5 hard stop: the incomplete part is already an honestly
        # documented gap in a ledger -> TRACKED, never an Issue.
        "not_an_already_documented_gap": not _tracked_gap_refs(cluster),
    }


def _tracked_gap_refs(cluster):
    refs = []
    for m in cluster.get("members", []):
        refs += (m.get("qualification") or {}).get("tracked_gap_refs") or []
    return refs


def _looks_c2_ready(cluster):
    triage = cluster.get("kof_triage", {}) or {}
    contract_source = triage.get("contract_source") or []
    dup = triage.get("duplicate_precedent_check", {}) or {}
    classification = triage.get("classification")
    owned = (cluster.get("ownership") or {}).get("status") == "OWNED"
    return (
        not owned  # V2 §39: DOING owner on the same unit -> RESOLUTION_IN_PROGRESS
        and bool(contract_source) and contract_source != ["UNKNOWN"]
        and dup.get("status") == "NO_DUPLICATE_FOUND"
        and classification not in (None, "UNKNOWN")
    )


def classify(cluster):
    """Returns (new_confidence, checklist_dict). Never downgrades. `C3`
    is terminal (never reconsidered here — contract §13's ACCEPTED/
    STALE lifecycle states are a maintainer action, not this module's).
    `C2` IS reconsidered on every call, because new evidence can arrive
    between runs and this is exactly where a C2 -> C3 promotion must be
    detected; a stale top-of-function guard that also blocked C2 would
    make that promotion path dead code — caught by this module's own
    C2->C3 selftest fixture before it shipped."""
    current = cluster.get("confidence", "C0")
    if current == "C3":
        return current, None

    checklist = evaluate_c3_checklist(cluster)
    if current == "C1" and _looks_c2_ready(cluster):
        # promoted to C2 in THIS call; fall through so the C3 checklist is
        # evaluated too. Returning here made C3 unreachable in a real scan
        # (candidates always start at C1 and scan.py classifies once) —
        # found 23/09; the old C2->C3 fixture started from a hand-made C2.
        current = "C2"

    if current == "C2" and all(checklist.values()):
        # C3 only from a real C2 — a cluster that does not satisfy the C2
        # readiness gate can never leapfrog to C3.
        return "C3", checklist

    return current, checklist


def apply_classification(cluster):
    new_confidence, checklist = classify(cluster)
    cluster["confidence"] = new_confidence
    if (cluster.get("ownership") or {}).get("status") == "OWNED":
        cluster["lifecycle_state"] = "RESOLUTION_IN_PROGRESS"
    elif _tracked_gap_refs(cluster):
        cluster["lifecycle_state"] = "TRACKED"
        cluster["tracked_by"] = _tracked_gap_refs(cluster)
    if checklist is not None:
        cluster["c3_checklist"] = checklist
    return cluster


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def _cluster(confidence, contract_source=None, dup_status="NOT_CHECKED",
             classification="UNKNOWN", interest="unknown", lockin="none",
             domains=("CLI_TOOLING",)):
    return {
        "confidence": confidence,
        "members": [{"taxonomy": {"domains": list(domains)}}],
        "kof_triage": {
            "contract_source": contract_source or ["UNKNOWN"],
            "duplicate_precedent_check": {"status": dup_status},
            "classification": classification,
        },
        "priority_vector": {
            "interest_observed": {"level": interest},
            "lock_in": {"level": lockin},
        },
    }


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    bare_c0 = _cluster("C0")
    new_conf, _ = classify(bare_c0)
    check("a bare C0 with no kof_triage evidence stays C0", new_conf == "C0")

    bare_c1 = _cluster("C1")
    new_conf2, _ = classify(bare_c1)
    check("a C1 with UNKNOWN contract and NOT_CHECKED dedup stays C1 "
          "(never promotes on missing information)", new_conf2 == "C1")

    ready_c1 = _cluster("C1", contract_source=["D-BRANCH-0.5.0"],
                         dup_status="NO_DUPLICATE_FOUND", classification="GAP REAL")
    new_conf3, checklist3 = classify(ready_c1)
    check("a C1 with contract+dedup+classification promotes to C2",
          new_conf3 == "C2")
    check("promoting to C2 attaches the c3_checklist for transparency",
          checklist3 is not None)

    almost_c3_from_c2 = _cluster("C2", contract_source=["D-BRANCH-0.5.0"],
                                  dup_status="NO_DUPLICATE_FOUND",
                                  classification="GAP REAL",
                                  interest="high", lockin="documented")
    new_conf4, checklist4 = classify(almost_c3_from_c2)
    check("even with every OTHER signal present, a C2 with today's "
          "detectors NEVER reaches C3 (without history/ownership evidence "
          "attached, and with current_implementation_identified, "
          "debt_mechanism_proved, exit_condition_expressible honestly "
          "False)", new_conf4 == "C2")
    check("the checklist explains exactly why (each missing item is "
          "visible, not hidden)",
          checklist4 is not None and checklist4["historical_origin_searched"] is False)

    already_c2_no_new_evidence = _cluster("C2")  # UNKNOWN contract, NOT_CHECKED dedup
    new_conf5, checklist5 = classify(already_c2_no_new_evidence)
    check("a C2 with no new evidence is re-evaluated but never "
          "DOWNGRADED back to C1/C0",
          new_conf5 == "C2" and checklist5 is not None)

    already_c3 = _cluster("C3")
    new_conf6, _ = classify(already_c3)
    check("a cluster already at C3 is left untouched", new_conf6 == "C3")

    security_cluster = _cluster("C1", contract_source=["X"],
                                 dup_status="NO_DUPLICATE_FOUND",
                                 classification="GAP REAL",
                                 domains=("SECURITY",))
    checklist_sec = evaluate_c3_checklist(security_cluster)
    check("a SECURITY-domain cluster fails security_publication_gate_passed",
          checklist_sec["security_publication_gate_passed"] is False)

    check("evaluate_c3_checklist always returns exactly the documented "
          "10 requirement keys",
          set(evaluate_c3_checklist(bare_c1).keys()) == set(C3_REQUIREMENTS))

    # positive path: when every requirement genuinely holds, C2 -> C3
    # must actually fire — proven with a fixture, not left as dead code.
    hypothetically_complete_c2 = _cluster("C2")
    new_conf7, _ = classify(hypothetically_complete_c2)
    original_evaluate = globals()["evaluate_c3_checklist"]
    globals()["evaluate_c3_checklist"] = lambda cluster: {k: True for k in C3_REQUIREMENTS}
    try:
        new_conf8, checklist8 = classify(hypothetically_complete_c2)
    finally:
        globals()["evaluate_c3_checklist"] = original_evaluate
    check("when every C3 requirement genuinely holds, C2 -> C3 actually "
          "promotes (the happy path is not dead code)",
          new_conf7 == "C2" and new_conf8 == "C3" and all(checklist8.values()))

    with_history = _cluster("C2")
    with_history["historical_origin"] = {"status": "FOUND"}
    with_history["ownership"] = {"status": "NOT_OWNED"}
    cl_h = evaluate_c3_checklist(with_history)
    check("history FOUND + ownership NOT_OWNED clear exactly those two items",
          cl_h["historical_origin_searched"] is True
          and cl_h["owner_collision_checked"] is True)
    shallow = _cluster("C2")
    shallow["historical_origin"] = {"status": "NOT_CHECKED"}
    shallow["ownership"] = {"status": "NOT_CHECKED"}
    cl_s = evaluate_c3_checklist(shallow)
    check("history/ownership NOT_CHECKED never count as searched/checked",
          cl_s["historical_origin_searched"] is False
          and cl_s["owner_collision_checked"] is False)
    owned = _cluster("C1", contract_source=["D-SEC"],
                     dup_status="NO_DUPLICATE_FOUND", classification="N/A-PROCESS")
    owned["ownership"] = {"status": "OWNED", "owner": "x"}
    apply_classification(owned)
    check("an OWNED cluster is never promoted to C2 and is marked "
          "RESOLUTION_IN_PROGRESS (V2 §39)",
          owned["confidence"] == "C1"
          and owned.get("lifecycle_state") == "RESOLUTION_IN_PROGRESS")
    still_c2 = _cluster("C2", contract_source=["D-BRANCH-0.5.0"],
                        dup_status="NO_DUPLICATE_FOUND", classification="GAP REAL",
                        interest="high", lockin="documented")
    still_c2["historical_origin"] = {"status": "FOUND"}
    still_c2["ownership"] = {"status": "NOT_OWNED"}
    check("even with history + ownership cleared, C3 stays unreachable while "
          "mechanism/implementation/exit condition are unproven",
          classify(still_c2)[0] == "C2")

    full = _cluster("C1", contract_source=["D-X"], dup_status="NO_DUPLICATE_FOUND",
                    classification="GAP REAL", interest="medium", lockin="medium")
    full["historical_origin"] = {"status": "FOUND"}
    full["ownership"] = {"status": "NOT_OWNED"}
    full["exit_condition"] = "D-X reaches IMPLEMENTED"
    full["members"][0]["qualification"] = {"kind": "PARTIAL_PROVED",
                                           "implementation_refs": ["a/B.java"],
                                           "tracked_gap_refs": []}
    check("a C1 with every piece of evidence reaches C3 in ONE classify call "
          "(the real scan classifies once; C1->C2 used to return early)",
          classify(full)[0] == "C3")
    tracked = json.loads(json.dumps(full))
    tracked["members"][0]["qualification"]["tracked_gap_refs"] = ["docs/backend-parity.md line 1"]
    apply_classification(tracked)
    check("an incomplete part already documented as an honest gap is TRACKED, "
          "never C3 (V2 §5 hard stop)",
          tracked["confidence"] == "C2" and tracked["lifecycle_state"] == "TRACKED"
          and tracked["c3_checklist"]["not_an_already_documented_gap"] is False)
    unproved = json.loads(json.dumps(full))
    unproved["members"][0]["qualification"] = {"kind": "UNPROVED", "implementation_refs": []}
    ev = evaluate_c3_checklist(unproved)
    check("an UNPROVED qualification never counts as mechanism or implementation",
          ev["debt_mechanism_proved"] is False and ev["current_implementation_identified"] is False)

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("confidence --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    cluster = json.load(sys.stdin)
    print(json.dumps(apply_classification(cluster), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
