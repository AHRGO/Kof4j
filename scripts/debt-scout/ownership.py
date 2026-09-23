#!/usr/bin/env python3
"""ownership.py — `DOING.md` ownership cross-check (contract §3 step
"DOING.md ownership"; V2 spec §39 "DOING owner on same unit ->
RESOLUTION_IN_PROGRESS"; `AGENTS.md` §"Multi-agent coordination").

A finding whose file/directory or decision ID is named inside an ACTIVE
claim (`EM CURSO` / `IN PROGRESS` / `IN_PROGRESS`, not `FEITO`/`DONE`)
already has an owner — the Scout must not create a parallel tracking
record for it. This module only answers that question; it never edits
`DOING.md` (the Scout is not a second writer of any ledger).

Matching is deliberately literal: a claim covers a finding only when
the claim text names the finding's exact path, a directory prefix of it
(`scripts/debt-scout/` covers `scripts/debt-scout/x.py`), or its exact
decision ID. No fuzzy/keyword matching — a false "owned" would silently
suppress a real finding, and a false "not owned" is caught later by the
live duplicate check anyway.

Statuses:
  OWNED        — an active claim names this finding (owner + line reported)
  NOT_OWNED    — DOING.md was read and no active claim names it
  NOT_CHECKED  — DOING.md missing/unreadable
Only OWNED and NOT_OWNED count as "owner collision checked".

CLI:
  ownership.py --symbol PATH_OR_ID [--root DIR]  -> JSON result
  ownership.py --selftest
"""
import json
import os
import re
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

CHECKED_STATUSES = ("OWNED", "NOT_OWNED")
_ACTIVE_RE = re.compile(r"EM CURSO|IN[ _]PROGRESS")
_DONE_RE = re.compile(r"✅|\bFEITO\b|\bDONE\b")
_OWNER_RE = re.compile(r"dono\s*=\s*([^,;)]+)|owner\s*=\s*([^,;)]+)", re.IGNORECASE)
_TICK_RE = re.compile(r"`([^`\n]+)`")
_HEADER_CHARS = 160  # the state word lives in the bold header of a claim


def parse_active_claims(doing_text):
    """Pure: DOING.md text -> list of {line, owner, refs}. A claim is a
    line whose header (first ~160 chars) says EM CURSO/IN PROGRESS and
    does not say FEITO/DONE/✅. `refs` are the backticked tokens on
    that line, stripped — paths, dirs and decision IDs alike."""
    claims = []
    for lineno, line in enumerate(doing_text.splitlines(), start=1):
        header = line[:_HEADER_CHARS]
        if not _ACTIVE_RE.search(header) or _DONE_RE.search(header):
            continue
        if not line.lstrip().startswith(("> **", "**", "- **", "|")):
            continue  # prose that merely mentions the words, not a claim
        m = _OWNER_RE.search(line)
        owner = (m.group(1) or m.group(2)).strip() if m else "UNKNOWN"
        refs = {t.strip() for t in _TICK_RE.findall(line)}
        claims.append({"line": lineno, "owner": owner, "refs": refs})
    return claims


def _covers(ref, symbol):
    if not ref or not symbol:
        return False
    if ref == symbol:
        return True
    return ref.endswith("/") and symbol.startswith(ref)


def owner_for_symbols(symbols, claims):
    """Pure: first active claim covering any of `symbols` -> OWNED
    record, else NOT_OWNED."""
    for claim in claims:
        for sym in symbols:
            if any(_covers(ref, sym) for ref in claim["refs"]):
                return {"status": "OWNED", "owner": claim["owner"],
                        "doing_line": claim["line"], "matched": sym}
    return {"status": "NOT_OWNED", "owner": None, "doing_line": None, "matched": None}


# Coordination ledgers that nearly every claim names in passing. Naming
# one of them is not owning each item inside it — measured: on the real
# repo, path-matching these marked 36 of 69 clusters "owned" (DOING.md
# alone: 17), which would have silently suppressed real findings.
SHARED_LEDGER_PATHS = frozenset({
    "DOING.md",
    "AGENTS.md", "AGENTS.pt_BR.md",
    "docs/development/DECISIONS.md", "docs/development/DECISIONS.pt_BR.md",
    "docs/bugs-and-gaps/known-bugs.md", "docs/bugs-and-gaps/known-bugs.pt_BR.md",
    "docs/status.md", "docs/status.pt_BR.md",
    "docs/development/roadmap.md", "docs/development/roadmap.pt_BR.md",
    "CHANGELOG.md", "CHANGELOG.pt_BR.md",
})


def cluster_symbols(cluster):
    """The ownership unit of a cluster. A known governing contract id
    (e.g. a decision) IS the unit — its ledger file path is not. Without
    one, the finding's own paths are used, minus shared ledgers."""
    ids = []
    for m in cluster.get("members", []):
        for cid in m.get("contract_ids") or []:
            if cid and cid != "UNKNOWN" and cid not in ids:
                ids.append(cid)
    if ids:
        return ids
    syms = []
    candidates = [cluster.get("root_symbol")] + [
        loc.get("path") for m in cluster.get("members", [])
        for loc in (m.get("locations") or [])]
    for sym in candidates:
        if sym and sym not in SHARED_LEDGER_PATHS and sym not in syms:
            syms.append(sym)
    return syms


def read_doing(root):
    try:
        with open(os.path.join(root, "DOING.md"), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def owner_for_cluster(cluster, doing_text):
    if doing_text is None:
        return {"status": "NOT_CHECKED", "owner": None, "doing_line": None,
                "matched": None}
    syms = cluster_symbols(cluster)
    result = owner_for_symbols(syms, parse_active_claims(doing_text))
    if not syms:
        result["reason"] = ("finding lives in a shared coordination ledger; "
                            "a path mention there is not an ownership unit")
    return result


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

_DOING_FIXTURE = """# DOING
> **⚡ EM CURSO (23/09, dono = sessão 9093, lane baremetal): B-2 UEFI.** toca `kof-compiler/src/main/java/dev/kof/nat/Uefi.java` e `scripts/boot/`.
> **✅ FEITO (22/09, dono = sessão 9092): X6 fechado.** tocou `scripts/done.sh`.
> **🔧 §442 IN PROGRESS — reivindicado 22/09, dono = lane docs.** `D-ENUM207` ainda aberto.
Regra: não toque no que está `EM CURSO` (prosa, não claim) `scripts/prose.sh`.
"""


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    claims = parse_active_claims(_DOING_FIXTURE)
    check("exactly the 2 active claims are parsed (FEITO and prose skipped)",
          [c["line"] for c in claims] == [2, 4])
    check("owner is extracted from 'dono = ...'",
          claims[0]["owner"] == "sessão 9093")

    owned = owner_for_symbols(["kof-compiler/src/main/java/dev/kof/nat/Uefi.java"], claims)
    check("an exact path in an active claim is OWNED",
          owned["status"] == "OWNED" and owned["doing_line"] == 2)
    check("a directory ref covers a file under it",
          owner_for_symbols(["scripts/boot/efi.sh"], claims)["status"] == "OWNED")
    check("a decision ID in an active claim is OWNED",
          owner_for_symbols(["D-ENUM207"], claims)["status"] == "OWNED")
    check("a path only named by a FEITO claim is NOT_OWNED",
          owner_for_symbols(["scripts/done.sh"], claims)["status"] == "NOT_OWNED")
    check("a path only named in prose is NOT_OWNED",
          owner_for_symbols(["scripts/prose.sh"], claims)["status"] == "NOT_OWNED")
    check("no substring/fuzzy match: 'scripts/boot' (no slash) != 'scripts/booty.sh'",
          owner_for_symbols(["scripts/booty.sh"], claims)["status"] == "NOT_OWNED")
    check("missing DOING.md is NOT_CHECKED, never NOT_OWNED",
          owner_for_cluster({"members": []}, None)["status"] == "NOT_CHECKED")

    cl = {"root_symbol": "scripts/x.sh",
          "members": [{"locations": [{"path": "scripts/x.sh", "line": 3}],
                       "contract_ids": ["UNKNOWN", "D-SEC"]}]}
    check("a known contract id is the ownership unit (its paths are not)",
          cluster_symbols(cl) == ["D-SEC"])
    cl_path = {"root_symbol": "scripts/x.sh",
               "members": [{"locations": [{"path": "scripts/x.sh", "line": 3}],
                            "contract_ids": ["UNKNOWN"]}]}
    check("without a contract id, the finding's own path is the unit",
          cluster_symbols(cl_path) == ["scripts/x.sh"])
    ledger = {"root_symbol": "DOING.md",
              "members": [{"locations": [{"path": "DOING.md", "line": 9}]}]}
    check("a shared ledger path is never an ownership unit",
          cluster_symbols(ledger) == [])
    owned_by_ledger = owner_for_cluster(
        ledger, "> **⚡ EM CURSO (dono = x):** edita `DOING.md`.\n")
    check("a claim that merely names DOING.md does not own a finding in it",
          owned_by_ledger["status"] == "NOT_OWNED" and "reason" in owned_by_ledger)

    root = os.path.join(os.path.dirname(__file__), "..", "..")
    text = read_doing(root)
    if text is not None:
        live = parse_active_claims(text)
        check(f"live: real DOING.md parses without crashing ({len(live)} active claims)",
              isinstance(live, list))
        check("live: every live claim line is a real active header",
              all(_ACTIVE_RE.search(text.splitlines()[c["line"] - 1][:_HEADER_CHARS])
                  for c in live))
    else:
        print("  FAIL — real DOING.md not found for the live check")
        ok = False
    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("ownership --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    root = argv[argv.index("--root") + 1] if "--root" in argv else "."
    if "--symbol" not in argv:
        print("usage: ownership.py --symbol PATH_OR_ID [--root DIR]", file=sys.stderr)
        return 2
    sym = argv[argv.index("--symbol") + 1]
    text = read_doing(root)
    if text is None:
        result = {"status": "NOT_CHECKED"}
    else:
        result = owner_for_symbols([sym], parse_active_claims(text))
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
