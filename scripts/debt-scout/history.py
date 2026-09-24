#!/usr/bin/env python3
"""history.py — git-history helpers (V2 spec §21 "Detector historico de
workaround" / §34 "Historical Origin Analysis"; contract §3 step
"Issues/PRs/history"). Answers, for one located finding, only what the
history actually PROVES:

  FIRST_SEEN (sha + date) / ORIGINAL_CONTEXT (the commit subject,
  verbatim) / RELATED_REFS (#NNN, §NNN, D-XXX literally named in that
  subject)

It never writes "the author probably did this because ..." — the only
context it reports is the literal commit subject; anything else is
`UNKNOWN` (V2 §21: "Se o motivo nao estiver documentado:
ORIGINAL_CONTEXT: UNKNOWN").

**Shallow clones are not history.** `actions/checkout` defaults to
`fetch-depth: 1`; there, `git blame` attributes every line to the one
boundary commit, which would be a fabricated "first seen". A shallow
repo therefore yields `status: NOT_CHECKED` (never `FOUND`), and
`confidence.py` treats NOT_CHECKED as "history was not searched".

Statuses:
  FOUND        — blame resolved a real (non-boundary) introducing commit
  UNKNOWN      — history was searched but proves nothing (boundary
                 commit, uncommitted line, file not tracked)
  NOT_CHECKED  — no location, shallow clone, git unavailable, or error
Only FOUND and UNKNOWN count as "historical origin searched".

CLI:
  history.py --path FILE --line N [--root DIR]  -> JSON origin record
  history.py --selftest
"""
import json
import os
import re
import subprocess
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

SEARCHED_STATUSES = ("FOUND", "UNKNOWN")
_ZERO_SHA = "0" * 40
_REF_RE = re.compile(r"(#\d+|§\d+|\bD-[A-Z][A-Z0-9.-]*[A-Z0-9])")


def parse_blame_porcelain(text):
    """Pure: `git blame --porcelain -L n,n` output -> dict with sha,
    author_time (int epoch or None), summary, boundary (bool). Returns
    None when the text carries no header line."""
    lines = text.splitlines()
    if not lines:
        return None
    head = lines[0].split()
    if not head or not re.fullmatch(r"[0-9a-f]{40}", head[0]):
        return None
    rec = {"sha": head[0], "author_time": None, "summary": None, "boundary": False}
    for line in lines[1:]:
        if line.startswith("\t"):
            break
        key, _, value = line.partition(" ")
        if key == "author-time":
            try:
                rec["author_time"] = int(value)
            except ValueError:
                pass
        elif key == "summary":
            rec["summary"] = value
        elif key == "boundary":
            rec["boundary"] = True
    return rec


def related_refs(summary):
    """Pure: the #NNN / §NNN / D-XXX references LITERALLY present in a
    commit subject, in order, deduplicated. Nothing inferred."""
    if not summary:
        return []
    seen = []
    for m in _REF_RE.findall(summary):
        if m not in seen:
            seen.append(m)
    return seen


def record_from_blame(rec):
    """Pure: parsed blame -> origin record (the shape scan.py attaches)."""
    if rec is None:
        return _record("UNKNOWN", reason="blame produced no parsable header")
    if rec["sha"] == _ZERO_SHA:
        return _record("UNKNOWN", reason="line is not committed yet")
    if rec["boundary"]:
        return _record("UNKNOWN", reason="blame stopped at a boundary commit "
                       "(history before it is not available)")
    return _record(
        "FOUND",
        first_seen_sha=rec["sha"],
        first_seen_epoch=rec["author_time"],
        original_context=rec["summary"] or "UNKNOWN",
        related_refs=related_refs(rec["summary"]),
    )


def _record(status, first_seen_sha=None, first_seen_epoch=None,
            original_context="UNKNOWN", related_refs=(), reason=None):
    return {
        "status": status,
        "first_seen_sha": first_seen_sha,
        "first_seen_epoch": first_seen_epoch,
        "original_context": original_context,
        "related_refs": list(related_refs),
        "intent": "UNKNOWN",  # never inferred (V2 §2/§34)
        "reason": reason,
    }


def _git(root, *args, timeout=30):
    return subprocess.run(["git", "-C", root, *args],
                          capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace")


def is_shallow(root):
    """True / False / None (not a git repo or git unavailable)."""
    try:
        out = _git(root, "rev-parse", "--is-shallow-repository", timeout=10)
    except (OSError, subprocess.SubprocessError):
        return None
    if out.returncode != 0:
        return None
    return out.stdout.strip() == "true"


def origin_for_location(root, path, line, shallow=None):
    if shallow is None:
        shallow = is_shallow(root)
    if shallow is None:
        return _record("NOT_CHECKED", reason="not a git repository / git unavailable")
    if shallow:
        return _record("NOT_CHECKED", reason="shallow clone — blame would fabricate "
                       "a first-seen at the clone boundary")
    try:
        out = _git(root, "blame", "--root", "--porcelain", "-L", f"{int(line)},{int(line)}",
                   "--", path)
    except (OSError, subprocess.SubprocessError, ValueError) as e:
        return _record("NOT_CHECKED", reason=f"git blame failed: {e}")
    if out.returncode != 0:
        return _record("UNKNOWN", reason="file/line not tracked at HEAD")
    return record_from_blame(parse_blame_porcelain(out.stdout))


def origin_for_cluster(root, cluster, shallow=None):
    """Uses the first member's first location. No location -> NOT_CHECKED
    (a governance-wide finding has no single line to blame)."""
    for m in cluster.get("members", []):
        for loc in m.get("locations") or []:
            if loc.get("path") and loc.get("line"):
                return origin_for_location(root, loc["path"], loc["line"], shallow)
    return _record("NOT_CHECKED", reason="finding has no file/line location")


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

_SHA = "a" * 40
_PORCELAIN = (
    f"{_SHA} 12 12 1\n"
    "author Someone\nauthor-time 1790000000\nauthor-tz +0000\n"
    "summary fix(x): close §252 and #441 per D-BRANCH-0.5.0\n"
    "filename scripts/x.sh\n\t# TODO: fixture\n"
)


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    rec = parse_blame_porcelain(_PORCELAIN)
    check("porcelain header parses sha/time/summary",
          rec["sha"] == _SHA and rec["author_time"] == 1790000000
          and rec["summary"].startswith("fix(x)") and rec["boundary"] is False)
    origin = record_from_blame(rec)
    check("a real introducing commit is FOUND with the verbatim subject",
          origin["status"] == "FOUND" and origin["first_seen_sha"] == _SHA
          and origin["original_context"] == rec["summary"])
    check("related refs are exactly the literal #/§/D- tokens in the subject",
          origin["related_refs"] == ["§252", "#441", "D-BRANCH-0.5.0"])
    check("intent is never inferred", origin["intent"] == "UNKNOWN")

    boundary = parse_blame_porcelain(_PORCELAIN.replace("filename", "boundary\nfilename"))
    check("a boundary commit is UNKNOWN, never FOUND",
          record_from_blame(boundary)["status"] == "UNKNOWN")
    uncommitted = parse_blame_porcelain(_PORCELAIN.replace(_SHA, _ZERO_SHA))
    check("an uncommitted line (zero sha) is UNKNOWN",
          record_from_blame(uncommitted)["status"] == "UNKNOWN")
    check("garbage blame output is UNKNOWN, not a crash",
          record_from_blame(parse_blame_porcelain("not blame\n"))["status"] == "UNKNOWN")
    check("a subject with no refs yields []", related_refs("chore: tidy") == [])

    check("a shallow clone is NOT_CHECKED (never a fabricated origin)",
          origin_for_location(".", "AGENTS.md", 1, shallow=True)["status"] == "NOT_CHECKED")
    check("a no-location cluster is NOT_CHECKED",
          origin_for_cluster(".", {"members": [{"locations": []}]},
                             shallow=False)["status"] == "NOT_CHECKED")

    root = os.path.join(os.path.dirname(__file__), "..", "..")
    shallow = is_shallow(root)
    if shallow is False:
        live = origin_for_location(root, "AGENTS.md", 1, shallow=False)
        check("live: AGENTS.md line 1 resolves to a real commit on this "
              "full clone", live["status"] == "FOUND"
              and re.fullmatch(r"[0-9a-f]{40}", live["first_seen_sha"] or ""))
        missing = origin_for_location(root, "no/such/file.txt", 1, shallow=False)
        check("live: an untracked path is UNKNOWN, not a crash",
              missing["status"] == "UNKNOWN")
    else:
        print(f"  skip — live blame check (is_shallow={shallow}; a shallow or "
              "non-git checkout has no history to prove against)")
    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("history --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    root = argv[argv.index("--root") + 1] if "--root" in argv else "."
    if "--path" not in argv or "--line" not in argv:
        print("usage: history.py --path FILE --line N [--root DIR]", file=sys.stderr)
        return 2
    path = argv[argv.index("--path") + 1]
    line = argv[argv.index("--line") + 1]
    print(json.dumps(origin_for_location(root, path, line), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
