#!/usr/bin/env python3
"""sarif.py — SARIF 2.1.0 writer for located `C0`/`C1`/`C2` findings
(Wave 2, contract §37). This is the case SARIF/code-scanning exists
for: a signal with a real file location gets a code-scanning alert
next to the code, not an Issue (an Issue is a `C3`-only, still-
unauthorized capability — `D-DEBT-SCOUT`/`D-DEBT-SCOUT-W2`).

One SARIF `result` per candidate (not per cluster — a cluster's
members can have different locations even when they share a
`debt_fingerprint`; only genuinely identical findings collapse to one
cluster today, so in practice this is usually 1:1). `partialFingerprints`
carries the STABLE `finding_fingerprint` (never a line number — SARIF's
own fingerprint mechanism is what lets GitHub track an alert across a
line-shifting refactor; contract §37 forbids a commit SHA there for
the same reason). A candidate with no location (`locations: []`, e.g.
today's branch-drift check) is skipped here — it belongs in the Debt
Inbox (`inbox.py`), not SARIF.

CLI:
  sarif.py --analyzed-sha SHA < candidates.json  -> SARIF 2.1.0 JSON
  sarif.py --selftest
"""
import json
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

SARIF_SCHEMA = (
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/"
    "Schemata/sarif-schema-2.1.0.json"
)
TOOL_NAME = "kof-debt-scout"
TOOL_VERSION = "0.1.0"
CATEGORY = "/debt-scout/wave2"

_LEVEL_BY_CONFIDENCE = {"C0": "note", "C1": "note", "C2": "warning", "C3": "error"}


def has_location(candidate):
    locations = candidate.get("locations") or []
    return bool(locations) and locations[0].get("path")


def result_for_candidate(candidate):
    """One candidate -> one SARIF result dict, or None if it has no
    location (contract §37/§38: no-location findings go to the Inbox)."""
    if not has_location(candidate):
        return None
    location = candidate["locations"][0]
    physical = {"artifactLocation": {"uri": location["path"]}}
    if location.get("line") is not None:
        physical["region"] = {"startLine": int(location["line"])}
    return {
        "ruleId": candidate["rule_id"],
        "level": _LEVEL_BY_CONFIDENCE.get(candidate.get("confidence", "C0"), "note"),
        "message": {"text": candidate["claim"]},
        "locations": [{"physicalLocation": physical}],
        "partialFingerprints": {
            "debtScoutFindingFingerprint/v1": candidate.get(
                "finding_fingerprint", "sha256:UNKNOWN"),
        },
        "properties": {
            "confidence": candidate.get("confidence", "C0"),
            "debt_fingerprint": candidate.get("debt_fingerprint", "UNKNOWN"),
            "kof-debt-schema": 2,
        },
    }


def _rules(candidates):
    seen = {}
    for c in candidates:
        rule_id = c["rule_id"]
        if rule_id not in seen:
            seen[rule_id] = {
                "id": rule_id,
                "name": rule_id,
                "shortDescription": {"text": f"KOF Technical Debt Scout rule {rule_id}"},
                "properties": {"security-severity": "0.0"},
            }
    return list(seen.values())


def build_sarif(candidates, analyzed_sha="UNKNOWN"):
    located = [c for c in candidates if has_location(c)]
    results = [result_for_candidate(c) for c in located]
    return {
        "$schema": SARIF_SCHEMA,
        "version": "2.1.0",
        "runs": [{
            "tool": {"driver": {
                "name": TOOL_NAME,
                "version": TOOL_VERSION,
                "informationUri": "https://github.com/KofLang/Kof4j/tree/beta-0.5.0/docs/development/technical-debt",
                "rules": _rules(located),
            }},
            "results": results,
            "runAutomationDetails": {"id": f"{CATEGORY}/{analyzed_sha}/"},
        }],
    }


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def _candidate(rule_id="KOF-DEBT-TEST-001", confidence="C0", path="f.sh",
               line=3, claim="a test marker", finding_fp="sha256:" + "a" * 64):
    locations = [{"path": path, "line": line}] if path else []
    return {
        "rule_id": rule_id, "confidence": confidence, "claim": claim,
        "locations": locations, "finding_fingerprint": finding_fp,
        "debt_fingerprint": "sha256:" + "b" * 64,
    }


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    located = _candidate()
    r = result_for_candidate(located)
    check("a located candidate produces a SARIF result", r is not None)
    check("the result carries the finding_fingerprint (not a line number "
          "or commit SHA) as the stable fingerprint",
          r["partialFingerprints"]["debtScoutFindingFingerprint/v1"]
          == located["finding_fingerprint"])
    check("region.startLine is set for a candidate with a known line",
          r["locations"][0]["physicalLocation"]["region"]["startLine"] == 3)

    no_line = _candidate(line=None)
    r2 = result_for_candidate(no_line)
    check("a candidate with a path but no line still gets an "
          "artifactLocation, just no region",
          r2 is not None and "region" not in r2["locations"][0]["physicalLocation"])

    no_location = _candidate(path=None)
    check("a candidate with no location is skipped (goes to the Inbox "
          "instead, never fabricates a SARIF location)",
          result_for_candidate(no_location) is None)

    check("confidence maps to SARIF level: C0/C1->note, C2->warning, C3->error",
          result_for_candidate(_candidate(confidence="C0"))["level"] == "note"
          and result_for_candidate(_candidate(confidence="C1"))["level"] == "note"
          and result_for_candidate(_candidate(confidence="C2"))["level"] == "warning"
          and result_for_candidate(_candidate(confidence="C3"))["level"] == "error")

    doc = build_sarif([located, no_location], analyzed_sha="deadbeef")
    check("the SARIF document declares version 2.1.0", doc["version"] == "2.1.0")
    check("only the located candidate produces a result "
          "(no-location candidates are silently excluded, never a "
          "fabricated location)", len(doc["runs"][0]["results"]) == 1)
    check("runAutomationDetails.id carries the analyzed SHA, never a "
          "bare category with no run identity",
          "deadbeef" in doc["runs"][0]["runAutomationDetails"]["id"])
    check("the driver declares exactly the rules that produced a result "
          "(one entry for KOF-DEBT-TEST-001)",
          [r["id"] for r in doc["runs"][0]["tool"]["driver"]["rules"]]
          == ["KOF-DEBT-TEST-001"])

    two_rules = build_sarif([
        _candidate(rule_id="A"), _candidate(rule_id="B"), _candidate(rule_id="A"),
    ])
    check("multiple candidates from the same rule share one rules[] entry, "
          "not duplicated per result",
          len(two_rules["runs"][0]["tool"]["driver"]["rules"]) == 2)

    json.dumps(doc)  # must be JSON-serializable end to end
    check("the built SARIF document is JSON-serializable", True)

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("sarif --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    analyzed_sha = "UNKNOWN"
    if "--analyzed-sha" in argv:
        analyzed_sha = argv[argv.index("--analyzed-sha") + 1]
    candidates = json.load(sys.stdin)
    print(json.dumps(build_sarif(candidates, analyzed_sha), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
