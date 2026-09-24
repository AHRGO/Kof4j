#!/usr/bin/env python3
"""decision_evidence.py — deterministic qualification of an incomplete
decision (PARTIAL/BLOCKED/IN_PROGRESS) for `partial_decisions.py`
(source spec V2 §17: "partial state + missing tracking + recurring cost").

It reads only STRUCTURED ledger fields, never free prose:
  - the `**State:**` lines of the decision's own `###` sub-items;
  - `known-bugs.md` `§NNN` HEADINGS that name the decision id;
  - `backend-parity.md` table ROWS that name the decision id;
and resolves, against the real tree, the backticked class names the
decision cites and the gap codes (`SECN002`, `APP002`, ...) of its
incomplete sub-items.

Outcome (`kind`), each with its proof:
  PARTIAL_PROVED  — at least one complete AND one incomplete item: the
                    partial migration is proved, not just declared.
  STALE_STATE     — every item/ledger entry naming the decision is
                    complete, yet its State still says incomplete: a
                    doc/ledger drift (the KOF-first gate reads this
                    ledger FIRST, so a stale state misleads it).
  UNPROVED        — nothing structured to judge; stays a bare signal.
`tracked_gap_refs` lists where the incomplete part is already recorded as
an honest gap. Such a finding is TRACKED (V2 §5 hard stop: "behavior is
already an honestly documented gap") and never becomes an Issue.

CLI:
  decision_evidence.py --selftest
"""
import os
import re
import subprocess
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

_STATE_RE = re.compile(r"\*\*State:\*\*\s*(.+)$")
_SUB_RE = re.compile(r"^### ")
_INCOMPLETE_RE = re.compile(r"remains a gap|not implemented|\bpending\b|\bresidual\b|"
                            r"\bOPEN\b|\bPARTIAL\b|\bIN_PROGRESS\b|\bBLOCKED\b", re.I)
_COMPLETE_RE = re.compile(r"\bimplemented\b|✅|\bDONE\b|\bFIXED\b|\bCLOSED\b|"
                          r"\bIMPLEMENTED\b|\bCONCLUDED\b", re.I)
_GAP_CODE_RE = re.compile(r"\b[A-Z]{3,5}\d{3}\b")
_TICK_NAME_RE = re.compile(r"`([A-Z][A-Za-z0-9]{2,})`")
_KB_HEADING_RE = re.compile(r"^#{2,3} (§\d+)\b(.*)$")


def _is_incomplete(text):
    return bool(_INCOMPLETE_RE.search(re.sub(r"\bnot implemented\b", " not implemented ", text)))


def _is_complete(text):
    cleaned = re.sub(r"not implemented", "", text, flags=re.I)
    return bool(_COMPLETE_RE.search(cleaned))


def sub_items(section_text):
    """Pure: decision body (header excluded) -> list of (title, block_text,
    state_or_None) for each `###` sub-item."""
    items, title, buf = [], None, []
    for line in section_text.splitlines():
        if _SUB_RE.match(line):
            if title is not None:
                items.append((title, "\n".join(buf)))
            title, buf = line[4:].strip(), []
        elif title is not None:
            buf.append(line)
    if title is not None:
        items.append((title, "\n".join(buf)))
    out = []
    for t, block in items:
        state = None
        for line in block.splitlines():
            m = _STATE_RE.search(line)
            if m:
                state = m.group(1).strip()
                break
        out.append((t, block, state))
    return out


def ledger_refs(decision_id, known_bugs_text, parity_text):
    """Pure: structured ledger entries naming `decision_id` ->
    list of {source, ref, text, complete, incomplete}."""
    refs = []
    kb_lines = (known_bugs_text or "").splitlines()
    for i, line in enumerate(kb_lines):
        m = _KB_HEADING_RE.match(line)
        if m and re.search(rf"(?<![A-Z0-9-]){re.escape(decision_id)}(?![A-Z0-9-])", line):
            body = []
            for nxt in kb_lines[i + 1:]:
                if re.match(r"^#{1,3} ", nxt):
                    break
                body.append(nxt)
            refs.append({"source": "docs/bugs-and-gaps/known-bugs.md", "ref": m.group(1),
                         "text": line.strip()[:240], "body": "\n".join(body),
                         "complete": _is_complete(m.group(2)),
                         "incomplete": _is_incomplete(m.group(2)) and not _is_complete(m.group(2))})
    for lineno, line in enumerate((parity_text or "").splitlines(), start=1):
        if line.startswith("|") and re.search(
                rf"(?<![A-Z0-9-]){re.escape(decision_id)}(?![A-Z0-9-])", line):
            refs.append({"source": "docs/backend-parity.md", "ref": f"line {lineno}",
                         "text": line.strip()[:240], "complete": _is_complete(line),
                         "incomplete": _is_incomplete(line)})
    return refs


def qualify(decision_id, state, section_text, known_bugs_text, parity_text,
            repo_basenames, code_hits, test_hits):
    """Pure. `repo_basenames`: {"KofSecurity": "path/KofSecurity.java"};
    `code_hits`/`test_hits`: {gap_code: [paths]} (src/main and src/test)."""
    subs = sub_items(section_text)
    complete, incomplete, gap_codes = [], [], []
    for title, block, sub_state in subs:
        if not sub_state:
            continue
        if _is_incomplete(sub_state):
            incomplete.append(f"{title}: {sub_state}")
            for code in _GAP_CODE_RE.findall(block):
                if code not in gap_codes:
                    gap_codes.append(code)
            if _is_complete(sub_state):
                complete.append(f"{title}: {sub_state}")
        elif _is_complete(sub_state):
            complete.append(f"{title}: {sub_state}")
    refs = ledger_refs(decision_id, known_bugs_text, parity_text)
    for r in refs:
        if r["incomplete"]:
            incomplete.append(f"{r['source']} {r['ref']}")
            # the residual's own code: the nearest gap code in the 40 chars
            # BEFORE each "residual" marker (`APP002` (**residual**: ...)
            for m in re.finditer(r"residual", r["text"], re.I):
                before = _GAP_CODE_RE.findall(r["text"][max(0, m.start() - 40):m.start()])
                if before and before[-1] not in gap_codes:
                    gap_codes.append(before[-1])
        if r["complete"]:
            complete.append(f"{r['source']} {r['ref']}")

    if complete and incomplete:
        kind = "PARTIAL_PROVED"
    elif complete and not incomplete:
        kind = "STALE_STATE"
    else:
        kind = "UNPROVED"

    impl = []
    for name in _TICK_NAME_RE.findall(section_text):
        path = repo_basenames.get(name)
        if path and path not in impl:
            impl.append(path)
    for code in gap_codes:
        for path in code_hits.get(code, []):
            if path not in impl:
                impl.append(path)
    if kind == "STALE_STATE":
        # what proves the ledger is outdated: the code/tests the CLOSED
        # entries cite as their fix/proof — only names that resolve to
        # files that exist in the tree count
        for r in refs:
            if r["complete"]:
                for name in _TICK_NAME_RE.findall(r.get("body", "")):
                    path = repo_basenames.get(name)
                    if path and path not in impl:
                        impl.append(path)
    tests = sorted({p for code in gap_codes for p in test_hits.get(code, [])})
    tracked = sorted({r["source"] + " " + r["ref"] for r in refs
                      if r["incomplete"] or (kind == "PARTIAL_PROVED"
                                             and any(c in r["text"] for c in gap_codes))})

    if kind == "STALE_STATE":
        exit_condition = (f"{decision_id} State stops saying {state} and matches the "
                          f"closed ledger entries ({', '.join(complete)}) — a maintainer "
                          "ledger edit (rule 6), never an agent edit")
    elif kind == "PARTIAL_PROVED":
        exit_condition = (f"every incomplete item of {decision_id} is closed "
                          f"({', '.join(gap_codes) or 'see incomplete items'}) and its "
                          f"State leaves {state}")
    else:
        exit_condition = None
    return {
        "kind": kind,
        "complete_items": complete,
        "incomplete_items": incomplete,
        "gap_codes": gap_codes,
        "implementation_refs": impl,
        "test_refs": tests,
        "tracked_gap_refs": tracked if kind == "PARTIAL_PROVED" else [],
        "exit_condition": exit_condition,
        "lock_in": ("medium", [f"incomplete state is asserted by tests via "
                               f"{', '.join(gap_codes)}: {', '.join(tests)}"]) if tests
        else (("high", ["the stale state lives in the normative ledger the KOF-first "
                        "gate reads first (DECISIONS.md)"]) if kind == "STALE_STATE"
              else ("none", [])),
    }


# --------------------------------------------------------------------------
# IO collector (real repo)
# --------------------------------------------------------------------------

def _read(root, rel):
    try:
        with open(os.path.join(root, rel), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return ""


def _git_lines(root, *args):
    try:
        out = subprocess.run(["git", "-C", root, *args], capture_output=True,
                             text=True, timeout=60, encoding="utf-8", errors="replace")
    except (OSError, subprocess.SubprocessError):
        return []
    return out.stdout.splitlines() if out.returncode == 0 else []


class RepoIndex:
    """Lazily built once per scan: basenames + gap-code hits."""

    def __init__(self, root):
        self.root = root
        self.known_bugs = _read(root, "docs/bugs-and-gaps/known-bugs.md")
        self.parity = _read(root, "docs/backend-parity.md")
        self.basenames = {}
        for path in _git_lines(root, "ls-files", "*.java", "*.kf", "*.py", "*.sh"):
            name = os.path.splitext(os.path.basename(path))[0]
            self.basenames.setdefault(name, path)
        self._hits = {}

    def hits(self, code):
        if code not in self._hits:
            files = _git_lines(self.root, "grep", "-l", "-w", code, "--", "*/src/*")
            self._hits[code] = ([f for f in files if "/src/main/" in f],
                                [f for f in files if "/src/test/" in f])
        return self._hits[code]

    def qualify(self, decision_id, state, section_text):
        codes = set(_GAP_CODE_RE.findall(section_text))
        for r in ledger_refs(decision_id, self.known_bugs, self.parity):
            codes.update(_GAP_CODE_RE.findall(r["text"]))
        code_hits = {c: self.hits(c)[0] for c in codes}
        test_hits = {c: self.hits(c)[1] for c in codes}
        return qualify(decision_id, state, section_text, self.known_bugs, self.parity,
                       self.basenames, code_hits, test_hits)


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

_SEC = """
### D-SEC.1 — ChaCha
Native without implementation reports `SECN002`.
**State:** JVM + JS implemented. Native remains a gap.
**Evidence:** `KofSecurityTest`.
### D-SEC.2 — Cookies
**State:** JVM + JS implemented.
"""
_KB = """### §211 — enum identity (issue #207) — ✅ CLOSED 15/09 (D-ENUM207 lane)
- fixed in `CompilerEnumLowering`; proof `EnumIdentityE2ETest`; also `NoSuchClass`.
### §999 — something else — OPEN (D-OTHER)
- `OtherThing`
"""
_PARITY = ("| **App model (D-APP)** | `APP001` ok, `APP002` (**residual**: port never "
           "consumed) | ✅ `CmdNew` |\n")


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    subs = sub_items(_SEC)
    check("sub-items and their State lines are parsed",
          [s[2] for s in subs] == ["JVM + JS implemented. Native remains a gap.",
                                   "JVM + JS implemented."])
    q = qualify("D-SEC", "PARTIAL", _SEC, "", "", {"KofSecurityTest": "t/KofSecurityTest.java"},
                {"SECN002": ["m/KofSecurity.java"]}, {"SECN002": ["t/KofSecurityTest.java"]})
    check("mixed sub-states prove the partial migration", q["kind"] == "PARTIAL_PROVED")
    check("the gap code comes from the incomplete sub-item's block", q["gap_codes"] == ["SECN002"])
    check("implementation = cited class files + files emitting the gap code",
          q["implementation_refs"] == ["t/KofSecurityTest.java", "m/KofSecurity.java"])
    check("tests asserting the gap code make the lock-in 'medium' with evidence",
          q["lock_in"][0] == "medium" and "KofSecurityTest" in q["lock_in"][1][0])
    check("an expressible exit condition names the gap code",
          "SECN002" in q["exit_condition"])

    qe = qualify("D-ENUM207", "IN_PROGRESS", "Enum identity was reassigned.", _KB, "",
                 {"CompilerEnumLowering": "m/CompilerEnumLowering.java",
                  "EnumIdentityE2ETest": "t/EnumIdentityE2ETest.java",
                  "OtherThing": "m/OtherThing.java"}, {}, {})
    check("stale state: implementation = files the CLOSED entry cites that exist "
          "(unresolved names and other entries' names never count)",
          qe["implementation_refs"] == ["m/CompilerEnumLowering.java", "t/EnumIdentityE2ETest.java"])
    check("every ledger entry naming the id closed -> STALE_STATE",
          qe["kind"] == "STALE_STATE" and qe["complete_items"] == ["docs/bugs-and-gaps/known-bugs.md §211"])
    check("stale-state exit condition is a maintainer ledger edit, never an agent edit",
          "maintainer ledger edit" in qe["exit_condition"])
    check("a heading for a different id is never attributed",
          all("§999" not in c for c in qe["complete_items"] + qe["incomplete_items"]))

    qa = qualify("D-APP", "PARTIAL", "no sub states here", "", _PARITY, {}, {}, {})
    check("a parity row with ✅ + **residual** proves partial and names the residual code",
          qa["kind"] == "PARTIAL_PROVED" and qa["gap_codes"] == ["APP002"])
    check("the residual already in backend-parity.md is a tracked honest gap",
          qa["tracked_gap_refs"] == ["docs/backend-parity.md line 1"])
    check("no test asserts APP002 -> lock-in stays none (not invented)",
          qa["lock_in"][0] == "none")

    qu = qualify("D-X", "PARTIAL", "prose only, nothing structured", "", "", {}, {}, {})
    check("nothing structured -> UNPROVED, no exit condition invented",
          qu["kind"] == "UNPROVED" and qu["exit_condition"] is None)
    check("'not implemented' is incomplete, never complete",
          _is_incomplete("Native not implemented") and not _is_complete("Native not implemented"))
    check("an id is matched whole (D-SEC never matches D-SECURITY)",
          ledger_refs("D-SEC", "### §1 — x (D-SECURITY) ✅ FIXED\n", "") == [])

    root = os.path.join(os.path.dirname(__file__), "..", "..", "..")
    if os.path.exists(os.path.join(root, "docs/development/DECISIONS.md")):
        idx = RepoIndex(root)
        check("live: repo index resolves a known class basename",
              idx.basenames.get("KofProjectConfig", "").endswith("KofProjectConfig.java"))
    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("decision_evidence --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    print("usage: decision_evidence.py --selftest (used by partial_decisions.py)", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
