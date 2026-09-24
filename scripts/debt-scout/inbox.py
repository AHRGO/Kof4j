#!/usr/bin/env python3
"""inbox.py — the Debt Inbox for `C2` findings that have NO code
location (Wave 2, contract §38). Branch/governance drift, release-
process, architecture-decision findings — anything a human needs to
see but that SARIF can't anchor to a line.

**Not a GitHub Issue.** Contract §38 lists preference order: (1)
workflow/job summary, (2) structured artifact, (3) a single idempotent
`Debt Scout Inbox` Issue, (4) a future dashboard. This repo has no
Issue-publish capability authorized (`D-DEBT-SCOUT`/`D-DEBT-SCOUT-W2`),
so Wave 2 implements only options (1) and (2): a Markdown summary
(rendered into `GITHUB_STEP_SUMMARY` by the workflow, same as Wave 1's
own summary step) plus the structured JSON already in the uploaded
`candidates.json` artifact.

CLI:
  inbox.py < clusters.json   -> Markdown to stdout (empty inbox = empty output)
  inbox.py --selftest
"""
import json
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")


def needs_inbox(cluster):
    """A cluster belongs in the Inbox when it is C2 (or higher) AND has
    no located member — a located C2 goes to SARIF instead (contract
    §37: "prefer SARIF for anything with a code location")."""
    if cluster.get("confidence") not in ("C2", "C3"):
        return False
    for m in cluster.get("members", []):
        locations = m.get("locations") or []
        if locations and locations[0].get("path"):
            return False
    return True


def select_inbox_clusters(clusters):
    return [c for c in clusters if needs_inbox(c)]


def render_inbox_markdown(clusters):
    entries = select_inbox_clusters(clusters)
    lines = ["## KOF Technical Debt Scout — Debt Inbox", ""]
    if not entries:
        lines.append(
            "No C2/C3 findings without a code location this run. "
            "(This is expected while no cluster reaches C2 — see "
            "`confidence.py`.)"
        )
        return "\n".join(lines) + "\n"
    for cl in entries:
        tax = cl["taxonomy"]
        lines.append(
            f"### `{cl['debt_fingerprint']}` — {tax['debt_type']}/"
            f"{tax['primary_domain']}/{tax['mechanism']}"
        )
        lines.append(f"- confidence: `{cl['confidence']}`")
        triage = cl.get("kof_triage", {}) or {}
        lines.append(f"- classification: `{triage.get('classification', 'UNKNOWN')}`")
        lines.append(f"- contract source: `{triage.get('contract_source', ['UNKNOWN'])}`")
        lines.append(f"- action: {triage.get('action', 'UNKNOWN')}")
        for m in cl.get("members", []):
            lines.append(f"  - {m['claim']}")
        lines.append("")
    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def _cluster(confidence, has_location, fp="sha256:" + "c" * 64):
    member = {"claim": "a claim", "locations": (
        [{"path": "x.md", "line": None}] if has_location else []
    )}
    return {
        "debt_fingerprint": fp, "confidence": confidence,
        "taxonomy": {"debt_type": "DOCUMENTATION", "primary_domain": "CI_GOVERNANCE",
                     "mechanism": "DOC_CODE_DRIFT"},
        "kof_triage": {"classification": "GAP REAL", "contract_source": ["X"],
                        "action": "review"},
        "members": [member],
    }


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    check("a C0 cluster never needs the Inbox",
          needs_inbox(_cluster("C0", has_location=False)) is False)
    check("a C1 cluster never needs the Inbox",
          needs_inbox(_cluster("C1", has_location=False)) is False)
    check("a C2 cluster WITHOUT a location needs the Inbox",
          needs_inbox(_cluster("C2", has_location=False)) is True)
    check("a C2 cluster WITH a location goes to SARIF instead, "
          "never duplicated into the Inbox",
          needs_inbox(_cluster("C2", has_location=True)) is False)
    check("a C3 cluster without a location also needs the Inbox",
          needs_inbox(_cluster("C3", has_location=False)) is True)

    empty_md = render_inbox_markdown([_cluster("C0", has_location=False)])
    check("an empty inbox says so explicitly, never silently empty output",
          "No C2/C3 findings" in empty_md)

    populated = render_inbox_markdown([_cluster("C2", has_location=False)])
    check("a populated inbox includes the debt_fingerprint",
          "sha256:" + "c" * 64 in populated)
    check("a populated inbox includes the classification and action",
          "GAP REAL" in populated and "review" in populated)

    mixed = [_cluster("C0", has_location=False), _cluster("C2", has_location=True),
             _cluster("C2", has_location=False)]
    check("select_inbox_clusters filters correctly on a mixed batch "
          "(only the located-C2 is excluded)",
          len(select_inbox_clusters(mixed)) == 1)

    check("render_inbox_markdown never crashes on an empty list",
          "No C2/C3" in render_inbox_markdown([]))

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("inbox --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    clusters = json.load(sys.stdin)
    sys.stdout.write(render_inbox_markdown(clusters))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
