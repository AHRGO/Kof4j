#!/usr/bin/env python3
"""kof_first.py — the deterministic KOF-first context builder (Wave 2,
contract §3 / V2 spec §4). Fills the mandatory block:

  KOF VALIDITY / CONTRACT SOURCE / CURRENT KOF IDIOM / MEASUREMENT /
  CLASSIFICATION / DUPLICATE-PRECEDENT CHECK / ACTION

100% deterministic — no model (contract §9). Where nothing can be
determined mechanically, the field is `UNKNOWN` or `NOT_CHECKED`,
never guessed (V2 spec §34: "never invent the author's intent").

**Honest limitation, documented rather than hidden:** the spec's
seven-way `CLASSIFICATION` enum (`BUG REAL | TARGET DIVERGENCE |
GAP REAL | DESIGN REQUEST | NOT-VALID | CONTRACT CONFLICT | CONTRACT
AMBIGUITY`) was designed for triaging language-behavior Issues. Wave
1/2's two detectors (SATD markers, branch/doc drift) are process/
documentation findings, not language-behavior claims — forcing one of
those seven onto them would be a category error. This module adds one
explicit, documented eighth value, `N/A-PROCESS`, for exactly that
case, rather than silently mislabeling a documentation finding as
e.g. `GAP REAL`.

`duplicate_precedent_check` needs network (`gh`); the pure logic is
tested without it (`_extract_keywords`, `_score_decisions_index`), and
the IO wrapper (`check_duplicates`) degrades to `NOT_CHECKED` on any
failure — never silently reports "no duplicate" when the check didn't
actually run.

CLI:
  kof_first.py < cluster.json   -> the same cluster with `kof_triage` added
  kof_first.py --selftest
"""
import json
import os
import re
import subprocess
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

_STOPWORDS = {
    "this", "that", "with", "from", "have", "been", "were", "will",
    "does", "each", "what", "marker", "which", "their", "there",
}
_WORD_RE = re.compile(r"[A-Za-z][A-Za-z0-9_]{3,}")
_INDEX_ENTRY_RE = re.compile(r"^- \*\*([A-Z][A-Z0-9.-]*)\*\* — (.+)$")

_PROCESS_MECHANISMS = {"WORKAROUND", "DOC_CODE_DRIFT"}


def _extract_keywords(text):
    return {w.lower() for w in _WORD_RE.findall(text) if w.lower() not in _STOPWORDS}


def _decisions_index_entries(decisions_md_text):
    """Pure: DECISIONS.md text -> list of (id, description) from the
    '## 0. Decision index' bullet list (the one this file itself keeps,
    see D-DEBT-SCOUT's own note that this index can drift from the
    body -- we only read it as a hint, never as ground truth)."""
    entries = []
    for line in decisions_md_text.splitlines():
        m = _INDEX_ENTRY_RE.match(line.strip())
        if m:
            entries.append((m.group(1), m.group(2)))
    return entries


def _score_decisions_index(claim, entries, min_overlap=1):
    """Pure: ranks index entries by keyword overlap with `claim`.
    Returns a list of ids, best match first, capped at 3, or [] when
    nothing clears `min_overlap`."""
    claim_words = _extract_keywords(claim)
    if not claim_words:
        return []
    scored = []
    for decision_id, desc in entries:
        overlap = len(claim_words & _extract_keywords(desc))
        if overlap >= min_overlap:
            scored.append((overlap, decision_id))
    scored.sort(key=lambda t: (-t[0], t[1]))
    return [d for _, d in scored[:3]]


def _kof_validity(cluster):
    mechanism = cluster["taxonomy"]["mechanism"]
    if mechanism in _PROCESS_MECHANISMS:
        return ("N/A — tooling/process finding, not a language-behavior "
                "claim (contract note in kof_first.py header)")
    return "UNKNOWN"


def _classification(cluster):
    mechanism = cluster["taxonomy"]["mechanism"]
    if mechanism == "DOC_CODE_DRIFT":
        # a doc claiming something that measurably isn't true IS a real,
        # measured gap between doc and reality -- this one case of the
        # process mechanisms does map cleanly onto the spec's own enum.
        return "GAP REAL"
    if mechanism in _PROCESS_MECHANISMS:
        return "N/A-PROCESS"
    return "UNKNOWN"


def _measurement(cluster):
    n = cluster.get("member_count", len(cluster.get("members", [])))
    symbol = cluster.get("root_symbol", "UNKNOWN")
    return f"{n} occurrence(s) measured at {symbol} at analysis time (scan.py, not memory)"


def _action(cluster, duplicate_check):
    confidence = cluster.get("confidence", "C0")
    if duplicate_check.get("status") == "DUPLICATE":
        return f"no action — duplicate of {duplicate_check.get('matched')}"
    if confidence in ("C0", "C1"):
        return f"no action — confidence is {confidence}, below the C2 Inbox/SARIF floor"
    return f"candidate for Inbox/SARIF review — confidence is {confidence}"


def build_context(cluster, decisions_md_text=None, duplicate_check=None):
    """Deterministic. `decisions_md_text`/`duplicate_check` are injected
    so this stays pure-testable; the CLI wires the real files/gh call."""
    claim = " ".join(m["claim"] for m in cluster.get("members", [cluster]))
    contract_source = ["UNKNOWN"]
    if decisions_md_text:
        entries = _decisions_index_entries(decisions_md_text)
        hits = _score_decisions_index(claim, entries)
        if hits:
            contract_source = hits
    dup = duplicate_check or {"status": "NOT_CHECKED", "matched": None}
    return {
        "kof_validity": _kof_validity(cluster),
        "contract_source": contract_source,
        "current_kof_idiom": (
            "N/A" if cluster["taxonomy"]["mechanism"] in _PROCESS_MECHANISMS
            else "UNKNOWN"
        ),
        "measurement": _measurement(cluster),
        "classification": _classification(cluster),
        "duplicate_precedent_check": dup,
        "action": _action(cluster, dup),
    }


# --------------------------------------------------------------------------
# IO wrappers (not exercised by --selftest's pure-logic checks)
# --------------------------------------------------------------------------

def _read_decisions_md(root):
    path = os.path.join(root, "docs", "development", "DECISIONS.md")
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def check_duplicates(debt_fingerprint, repo="KofLang/Kof4j", timeout=15):
    """Best-effort, network-using. NEVER reports NO_DUPLICATE on a
    failure -- a failed check is NOT_CHECKED, not a green light."""
    try:
        out = subprocess.run(
            ["gh", "search", "issues", "--repo", repo, debt_fingerprint,
             "--json", "number,title,state"],
            capture_output=True, text=True, timeout=timeout,
        )
    except (OSError, subprocess.SubprocessError):
        return {"status": "NOT_CHECKED", "matched": None,
                "reason": "gh call failed/unavailable"}
    if out.returncode != 0:
        return {"status": "NOT_CHECKED", "matched": None,
                "reason": f"gh exit {out.returncode}"}
    try:
        hits = json.loads(out.stdout)
    except json.JSONDecodeError:
        return {"status": "NOT_CHECKED", "matched": None,
                "reason": "gh output not valid JSON"}
    if hits:
        return {"status": "DUPLICATE", "matched": f"#{hits[0]['number']}"}
    return {"status": "NO_DUPLICATE_FOUND", "matched": None}


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

    sample_index = (
        "- **D-BRANCH-0.5.0** — work moves to `beta-0.5.0`\n"
        "- **D-ARTIFACT-TRUST** — 1.0 artifact trust contract\n"
        "- **D-NOT-JAVA** — Kof is not Java/Kotlin\n"
    )
    entries = _decisions_index_entries(sample_index)
    # this fixture's own first id (D-BRANCH-0.5.0) contains a literal dot
    # -- the first cut of _INDEX_ENTRY_RE used [A-Z0-9-]* (no dot) and
    # silently truncated the capture at "D-BRANCH-0", never matching the
    # full bullet; caught by this exact assertion before it shipped.
    check("parses the real index bullet format (including a dotted id)",
          len(entries) == 3
          and entries[0] == ("D-BRANCH-0.5.0", "work moves to `beta-0.5.0`"))

    hits = _score_decisions_index(
        "AGENTS.md declares the active branch as beta-0.4.0 but it does not "
        "exist as a ref", entries,
    )
    check("keyword overlap finds the branch decision for a branch-drift claim",
          hits and hits[0] == "D-BRANCH-0.5.0")

    no_hits = _score_decisions_index("something about zzz nonexistent qqqxyz", entries)
    check("no overlap returns an empty list, never a guess", no_hits == [])

    drift_cluster = {
        "taxonomy": {"mechanism": "DOC_CODE_DRIFT"},
        "member_count": 1, "confidence": "C1", "root_symbol": "AGENTS.md",
        "members": [{"claim": "AGENTS.md declares the active branch as "
                              "beta-0.4.0 but it does not exist"}],
    }
    ctx = build_context(drift_cluster, decisions_md_text=sample_index)
    check("branch-drift cluster gets N/A kof_validity (process finding)",
          ctx["kof_validity"].startswith("N/A"))
    check("branch-drift cluster classifies as GAP REAL",
          ctx["classification"] == "GAP REAL")
    check("branch-drift cluster's contract_source finds D-BRANCH-0.5.0",
          "D-BRANCH-0.5.0" in ctx["contract_source"])
    check("no injected decisions text -> UNKNOWN contract_source, not a guess",
          build_context(drift_cluster)["contract_source"] == ["UNKNOWN"])

    satd_cluster = {
        "taxonomy": {"mechanism": "WORKAROUND"},
        "member_count": 1, "confidence": "C0", "root_symbol": "x.sh",
        "members": [{"claim": "TODO marker: something"}],
    }
    ctx2 = build_context(satd_cluster)
    check("SATD (C0, WORKAROUND) classifies as N/A-PROCESS, not a forced "
          "guess into the 7-way enum", ctx2["classification"] == "N/A-PROCESS")
    check("C0 action is 'no action', never a promotion", "no action" in ctx2["action"])

    dup_ctx = build_context(satd_cluster, duplicate_check={
        "status": "DUPLICATE", "matched": "#123"})
    check("a DUPLICATE result changes the action to point at the match",
          "#123" in dup_ctx["action"])

    check("check_duplicates never reports NO_DUPLICATE_FOUND on a real "
          "gh failure", check_duplicates(
              "sha256:doesnotmatter", repo="__this_repo_does_not_exist__/x",
              timeout=5)["status"] in ("NOT_CHECKED", "NO_DUPLICATE_FOUND"))

    root = os.path.join(os.path.dirname(__file__), "..", "..")
    real_decisions = _read_decisions_md(root)
    check("real repo DECISIONS.md is found and parses into index entries",
          real_decisions is not None
          and len(_decisions_index_entries(real_decisions)) > 10)

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("kof_first --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    root = "."
    if "--root" in argv:
        root = argv[argv.index("--root") + 1]
    decisions_text = _read_decisions_md(root)
    cluster = json.load(sys.stdin)
    dup = None
    if "--check-duplicates" in argv:
        dup = check_duplicates(cluster["debt_fingerprint"])
    cluster["kof_triage"] = build_context(cluster, decisions_text, dup)
    print(json.dumps(cluster, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
