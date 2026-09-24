#!/usr/bin/env python3
"""scan.py — the Wave 1+2 orchestrator CLI (`docs/development/
technical-debt/DEBT_SCOUT_CONTRACT.md` §11). Combines `config`,
`branch_discovery`, every detector under `detectors/`, and — Wave 2 —
`cluster`/`kof_first`/`priority`/`confidence` into one report, plus
`sarif`/`inbox` as separate emitters. This script still calls no
GitHub Issues API and writes nothing to GitHub itself — the workflow
that calls it owns SARIF upload (`security-events: write`), not this
script.

Hard invariant, checked at the orchestration layer too (defense in
depth, not trusting each module alone): the run REFUSES to proceed if
`.debt-scout.yml` fails validation, and the emitted report asserts
`issues_opened: 0` and that no candidate is `publication.eligible=true`
— violating either is a bug in this script, not a possible outcome.

CLI:
  scan.py --phase state        -> JSON: branch_discovery.discover() only
  scan.py --phase deterministic [--out FILE] [--check-duplicates]
                                -> JSON: full report incl. `clusters`
                                   (default phase; --check-duplicates
                                   calls `gh` over the network — off by
                                   default so tests stay network-free)
  scan.py --sarif-out FILE [--out FILE]   -> also writes the SARIF doc
  scan.py --inbox-out FILE [--out FILE]   -> also writes the Inbox md
  scan.py --metrics-out FILE              -> appends one JSONL metrics line
  scan.py --history                       -> git blame per located cluster
  scan.py --selftest
"""
import json
import os
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(__file__)
sys.path.insert(0, HERE)
import branch_discovery  # noqa: E402
import cluster as cluster_mod  # noqa: E402
import confidence  # noqa: E402
import config as config_mod  # noqa: E402
import history  # noqa: E402
import inbox  # noqa: E402
import kof_first  # noqa: E402
import ownership  # noqa: E402
import priority  # noqa: E402
import sarif  # noqa: E402
import schema  # noqa: E402
from detectors import partial_decisions, satd  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

SCANNER_VERSION = "0.2.0"


class ScanError(RuntimeError):
    pass


def _analyzed_sha(root):
    try:
        out = subprocess.run(
            ["git", "-C", root, "rev-parse", "HEAD"],
            capture_output=True, text=True, timeout=10,
        )
        if out.returncode == 0:
            return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return "UNKNOWN"


def run_state_phase(root="."):
    return branch_discovery.discover(root)


def run_deterministic_phase(root=".", check_duplicates=False, with_history=False):
    """Loads+validates config, runs every detector, clusters, qualifies
    (Wave 2: kof_first + priority + confidence), returns the full
    report dict. Raises ScanError if the config is invalid — a scan
    never runs on an unvalidated/rejected config.

    `check_duplicates=True` calls `gh` over the network (kof_first's
    dedup check) — OFF by default so this function stays safe to call
    from --selftest and from any test wrapper without a network
    dependency; production runs (the workflow) opt in explicitly.

    `with_history=True` runs `git blame` per located cluster
    (history.py) — OFF by default because it is the slowest step; a
    shallow clone yields NOT_CHECKED, never a fabricated origin.
    DOING.md ownership (ownership.py) is local and cheap: always on."""
    started = time.monotonic()
    config_path = os.path.join(root, ".debt-scout.yml")
    try:
        cfg = config_mod.load_config(config_path)
    except (config_mod.ConfigError, OSError) as e:
        raise ScanError(f"refusing to scan: invalid config: {e}") from e

    repo_state = branch_discovery.discover(root)
    candidates = list(repo_state.get("candidates", []))
    detectors_run = ["branch_discovery"]
    if cfg["rules"].get("satd"):
        candidates.extend(satd.scan(root))
        detectors_run.append("satd")
    if cfg["rules"].get("partial_decisions"):
        candidates.extend(partial_decisions.scan(root))
        detectors_run.append("partial_decisions")

    for c in candidates:
        errors = schema.validate_candidate(c)
        if errors:
            raise ScanError(f"a detector emitted a schema-invalid candidate: {errors}")
        if c["publication"]["eligible"] is True:
            raise ScanError(
                "a detector emitted publication.eligible=true — impossible "
                "in Wave 1/2 (no publisher exists); this is a bug in the "
                "detector, not a valid outcome"
            )

    analyzed_sha = _analyzed_sha(root)
    decisions_text = kof_first._read_decisions_md(root)
    clusters = cluster_mod.cluster_candidates(candidates)
    doing_text = ownership.read_doing(root)
    shallow = history.is_shallow(root) if with_history else None
    for cl in clusters:
        cl["ownership"] = ownership.owner_for_cluster(cl, doing_text)
        cl["historical_origin"] = (
            history.origin_for_cluster(root, cl, shallow) if with_history
            else {"status": "NOT_CHECKED", "reason": "history not requested (--history)"})
        # Live dedup only where it can change an outcome: C0 is never
        # promoted (confidence.py only promotes from C1), and the GitHub
        # search API allows 30 req/min — one search per cluster (70 on
        # this repo) made every cluster after the 30th NOT_CHECKED
        # (measured 23/09: D-ENUM207 lost its dedup to "gh exit 1").
        if check_duplicates and cl["confidence"] != "C0":
            dup = kof_first.check_duplicates(cl["debt_fingerprint"])
        elif check_duplicates:
            dup = {"status": "NOT_CHECKED", "matched": None,
                   "reason": "C0 is never promotable; live search skipped (API budget)"}
        else:
            dup = None
        cl["kof_triage"] = kof_first.build_context(cl, decisions_text, dup)
        cl["priority_vector"] = priority.compute_priority_vector(cl)
        confidence.apply_classification(cl)
        for errs_c in (schema.validate_candidate(m) for m in cl["members"]):
            if errs_c:
                raise ScanError(f"a cluster member became schema-invalid "
                                 f"after qualification: {errs_c}")

    by_confidence = {"C0": 0, "C1": 0, "C2": 0, "C3": 0}
    for c in candidates:
        by_confidence[c["confidence"]] = by_confidence.get(c["confidence"], 0) + 1
    clusters_by_confidence = {"C0": 0, "C1": 0, "C2": 0, "C3": 0}
    for cl in clusters:
        clusters_by_confidence[cl["confidence"]] = (
            clusters_by_confidence.get(cl["confidence"], 0) + 1)

    return {
        "run": {
            "analyzed_sha": analyzed_sha,
            "scanner_version": SCANNER_VERSION,
            "mode": cfg["mode"],
            "model_used": False,
            "duplicates_checked": check_duplicates,
            "history_checked": with_history,
            "detectors_run": detectors_run,
            "duration_seconds": round(time.monotonic() - started, 3),
        },
        "repository_state": {
            "default_branch": repo_state.get("default_branch"),
            "declared_active_branch": repo_state.get("declared_active_branch"),
            "declared_active_branch_exists": repo_state.get("declared_active_branch_exists"),
        },
        "candidates": candidates,
        "clusters": clusters,
        "summary": {
            "total_candidates": len(candidates),
            "by_confidence": by_confidence,
            "total_clusters": len(clusters),
            "clusters_by_confidence": clusters_by_confidence,
            "issues_opened": 0,
        },
    }


def emit_sarif(report):
    return sarif.build_sarif(report["candidates"], report["run"]["analyzed_sha"])


def emit_inbox(report):
    return inbox.render_inbox_markdown(report["clusters"])


def emit_metrics(report):
    """One JSONL observability record per run (V2 spec §76/§77). Counts
    only — no token/money figure is ever invented (no model is called)."""
    run, summary = report["run"], report["summary"]
    clusters = report["clusters"]
    return {
        "sha": run["analyzed_sha"],
        "scanner_version": run["scanner_version"],
        "mode": run["mode"],
        "detectors_run": len(run["detectors_run"]),
        "signals": summary["total_candidates"],
        "c0": summary["by_confidence"]["C0"],
        "c1": summary["by_confidence"]["C1"],
        "c2": summary["by_confidence"]["C2"],
        "c3": summary["by_confidence"]["C3"],
        "clusters": summary["total_clusters"],
        # c0..c3 above count CANDIDATES; promotion happens on CLUSTERS, so a
        # C2 cluster of C1 candidates showed as "c2: 0" (measured 23/09).
        "clusters_c0": summary["clusters_by_confidence"]["C0"],
        "clusters_c1": summary["clusters_by_confidence"]["C1"],
        "clusters_c2": summary["clusters_by_confidence"]["C2"],
        "clusters_c3": summary["clusters_by_confidence"]["C3"],
        "duplicates": sum(1 for cl in clusters
                          if (cl.get("kof_triage", {}).get("duplicate_precedent_check")
                              or {}).get("status") == "DUPLICATE"),
        "resolution_in_progress": sum(1 for cl in clusters
                                      if cl.get("lifecycle_state") == "RESOLUTION_IN_PROGRESS"),
        "history_found": sum(1 for cl in clusters
                             if cl["historical_origin"]["status"] == "FOUND"),
        "issues_opened": summary["issues_opened"],
        "model_calls": 0,
        "duration_seconds": run["duration_seconds"],
    }


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

    # A non-git, non-KOF temp directory: branch_discovery must degrade
    # gracefully (no exception), and with rules.satd off there should be
    # zero candidates and zero detectors run.
    with tempfile.TemporaryDirectory() as td:
        with open(os.path.join(td, ".debt-scout.yml"), "w", encoding="utf-8") as f:
            f.write(
                "schema: 2\nmode: shadow\n"
                "budget:\n  issues_per_run: 1\n  issues_per_day: 1\n  issues_per_week: 1\n"
                "confidence:\n  auto_issue_min: C3\n  inbox_min: C2\n"
                "trust:\n  min_reviewed_findings: 0\n  min_effective_precision: 1.0\n"
                "sarif:\n  enabled: false\n"
                "model:\n  enabled: false\n"
                "rules:\n  satd: false\n"
            )
        report = run_deterministic_phase(td)
        check("a non-git temp directory does not crash the deterministic phase",
              report["repository_state"]["default_branch"] is None)
        check("rules.satd=false runs zero candidates",
              report["summary"]["total_candidates"] == 0)
        check("issues_opened is always 0 in Wave 1",
              report["summary"]["issues_opened"] == 0)

        # invalid config must refuse to scan, not silently proceed
        with open(os.path.join(td, ".debt-scout.yml"), "w", encoding="utf-8") as f:
            f.write("schema: 2\nmode: canary\n")
        try:
            run_deterministic_phase(td)
            check("an invalid config (mode=canary) is refused", False)
        except ScanError:
            check("an invalid config (mode=canary) is refused", True)

        # a small fixture with a real TODO, satd enabled
        with open(os.path.join(td, ".debt-scout.yml"), "w", encoding="utf-8") as f:
            f.write(
                "schema: 2\nmode: shadow\n"
                "budget:\n  issues_per_run: 1\n  issues_per_day: 1\n  issues_per_week: 1\n"
                "confidence:\n  auto_issue_min: C3\n  inbox_min: C2\n"
                "trust:\n  min_reviewed_findings: 0\n  min_effective_precision: 1.0\n"
                "sarif:\n  enabled: false\n"
                "model:\n  enabled: false\n"
                "rules:\n  satd: true\n"
            )
        os.makedirs(os.path.join(td, "scripts"), exist_ok=True)
        with open(os.path.join(td, "scripts", "x.sh"), "w", encoding="utf-8") as f:
            f.write("#!/bin/sh\n# TODO: fixture marker\n")
        report2 = run_deterministic_phase(td)
        check("rules.satd=true finds the planted TODO fixture",
              report2["summary"]["total_candidates"] == 1
              and report2["summary"]["by_confidence"]["C0"] == 1)
        check("every candidate in the report is schema-valid",
              all(schema.validate_candidate(c) == [] for c in report2["candidates"]))
        check("history is NOT_CHECKED unless --history is requested",
              all(cl["historical_origin"]["status"] == "NOT_CHECKED"
                  for cl in report2["clusters"]))
        check("a missing DOING.md makes ownership NOT_CHECKED, never NOT_OWNED",
              all(cl["ownership"]["status"] == "NOT_CHECKED"
                  for cl in report2["clusters"]))

        # partial_decisions + ownership: a PARTIAL decision named by an
        # active DOING.md claim is RESOLUTION_IN_PROGRESS, not a new finding
        with open(os.path.join(td, ".debt-scout.yml"), "a", encoding="utf-8") as f:
            f.write("  partial_decisions: true\n")
        os.makedirs(os.path.join(td, "docs", "development"), exist_ok=True)
        with open(os.path.join(td, "docs", "development", "DECISIONS.md"), "w",
                  encoding="utf-8") as f:
            f.write("## D-SEC — security\n\n**State:** `PARTIAL`\n\n"
                    "## D-APP — app\n\n**State:** `PARTIAL`\n")
        with open(os.path.join(td, "DOING.md"), "w", encoding="utf-8") as f:
            f.write("> **⚡ EM CURSO (dono = lane sec): finishing `D-SEC`.**\n")
        report3 = run_deterministic_phase(td)
        by_id = {cl["members"][0]["contract_ids"][0]: cl for cl in report3["clusters"]
                 if cl["members"][0]["rule_id"] == partial_decisions.RULE_ID}
        check("partial_decisions emits one C1 cluster per incomplete decision",
              sorted(by_id) == ["D-APP", "D-SEC"]
              and all(cl["confidence"] == "C1" for cl in by_id.values()))
        check("the decision named by an active claim is OWNED + "
              "RESOLUTION_IN_PROGRESS; the other is NOT_OWNED",
              by_id["D-SEC"]["ownership"]["status"] == "OWNED"
              and by_id["D-SEC"].get("lifecycle_state") == "RESOLUTION_IN_PROGRESS"
              and by_id["D-APP"]["ownership"]["status"] == "NOT_OWNED")
        check("the governing contract of a partial decision is the decision itself",
              by_id["D-APP"]["kof_triage"]["contract_source"] == ["D-APP"])
        m = emit_metrics(report3)
        check("emit_metrics counts signals/clusters/resolution_in_progress and "
              "never invents model cost",
              m["signals"] == report3["summary"]["total_candidates"]
              and m["resolution_in_progress"] == 1 and m["model_calls"] == 0
              and m["issues_opened"] == 0
              and json.loads(json.dumps(m)) == m)

    # live run against the real repo this script ships in
    root = os.path.join(HERE, "..", "..")
    if os.path.exists(os.path.join(root, "AGENTS.md")):
        state = run_state_phase(root)
        # declared_active_branch_exists can legitimately be False here —
        # e.g. on a frozen `main` whose AGENTS.md still names a deleted
        # branch; that IS the tool working, not a test precondition.
        check("live --phase state resolves the real repo's branches",
              state["default_branch"] is not None
              and state["declared_active_branch_exists"] is not None)
        live_report = run_deterministic_phase(root)
        check("live --phase deterministic produces schema-valid candidates only",
              all(schema.validate_candidate(c) == [] for c in live_report["candidates"]))
        check("live run: issues_opened is 0",
              live_report["summary"]["issues_opened"] == 0)
        check("live run: no candidate is ever publish-eligible",
              all(c["publication"]["eligible"] is False for c in live_report["candidates"]))
        check("live run: clusters sum to the same candidate count "
              "(clustering never loses a candidate)",
              sum(cl["member_count"] for cl in live_report["clusters"])
              == live_report["summary"]["total_candidates"])
        check("live run: without --check-duplicates, no cluster reaches "
              "C2/C3 (no network call was made, so nothing was ready)",
              all(cl["confidence"] in ("C0", "C1") for cl in live_report["clusters"]))
        check("live run: run.duplicates_checked reflects the flag",
              live_report["run"]["duplicates_checked"] is False)
        check("live run: every cluster carries an ownership result",
              all(cl["ownership"]["status"] in ("OWNED", "NOT_OWNED")
                  for cl in live_report["clusters"]))
        hist_report = run_deterministic_phase(root, with_history=True)
        hist_statuses = {cl["historical_origin"]["status"] for cl in hist_report["clusters"]}
        if history.is_shallow(root) is False:
            check("live run --history on a full clone resolves at least one "
                  "real origin, and never crashes",
                  "FOUND" in hist_statuses)
        else:
            check("live run --history on a shallow clone is all NOT_CHECKED",
                  hist_statuses <= {"NOT_CHECKED"})
        live_sarif = emit_sarif(live_report)
        check("live run: emit_sarif produces a valid, serializable SARIF doc",
              live_sarif["version"] == "2.1.0"
              and json.dumps(live_sarif) is not None)
        live_inbox = emit_inbox(live_report)
        check("live run: emit_inbox never crashes and returns text",
              isinstance(live_inbox, str) and len(live_inbox) > 0)
    else:
        print("  FAIL — real repo root not found for the live check")
        ok = False

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("scan --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1

    root = "."
    if "--root" in argv:
        root = argv[argv.index("--root") + 1]
    phase = "deterministic"
    if "--phase" in argv:
        phase = argv[argv.index("--phase") + 1]

    check_dup = "--check-duplicates" in argv
    with_hist = "--history" in argv

    try:
        if phase == "state":
            result = run_state_phase(root)
        elif phase == "deterministic":
            result = run_deterministic_phase(root, check_duplicates=check_dup,
                                             with_history=with_hist)
        else:
            print(f"scan: unknown --phase {phase!r} (state|deterministic)", file=sys.stderr)
            return 2
    except ScanError as e:
        print(f"scan: {e}", file=sys.stderr)
        return 1

    text = json.dumps(result, indent=2, sort_keys=True)
    if "--out" in argv:
        out_path = argv[argv.index("--out") + 1]
        os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(text + "\n")
        print(f"scan: wrote {out_path}")
    else:
        print(text)

    if phase == "deterministic":
        if "--sarif-out" in argv:
            sarif_path = argv[argv.index("--sarif-out") + 1]
            os.makedirs(os.path.dirname(sarif_path) or ".", exist_ok=True)
            with open(sarif_path, "w", encoding="utf-8") as f:
                json.dump(emit_sarif(result), f, indent=2, sort_keys=True)
                f.write("\n")
            print(f"scan: wrote {sarif_path}")
        if "--inbox-out" in argv:
            inbox_path = argv[argv.index("--inbox-out") + 1]
            os.makedirs(os.path.dirname(inbox_path) or ".", exist_ok=True)
            with open(inbox_path, "w", encoding="utf-8") as f:
                f.write(emit_inbox(result))
            print(f"scan: wrote {inbox_path}")
        if "--metrics-out" in argv:
            metrics_path = argv[argv.index("--metrics-out") + 1]
            os.makedirs(os.path.dirname(metrics_path) or ".", exist_ok=True)
            with open(metrics_path, "a", encoding="utf-8") as f:
                f.write(json.dumps(emit_metrics(result), sort_keys=True) + "\n")
            print(f"scan: appended {metrics_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
