#!/usr/bin/env bash
# check_known_bugs_status.sh — machine ledger of known-bugs section statuses.
#
# Why: counting OPEN bugs "by eye" has repeatedly produced wrong numbers in
# commits and DOING ticks (estimates of 40, 13, 12 that disagreed with each
# other). The STABILITY condition says "all bugs resolved" — that must be a
# MEASUREMENT, not an opinion. Multi-face sections (§272 style) and buried
# **Status** lines defeat naive one-line greps.
#
# Per-section classification (body scanned = first 60 lines after the header):
# A section is classified by the LAST status-bearing line (header suffix,
# **Status**, Fechamento/Resolution/Fix, or "— ✅|🟡|🔴") — chronological append
# wins, history-quotes (🔴 inside prose without a status prefix) do not.
#   closed  last status is ✅-based.   live  last status is 🟡/🔴/OPEN (multi-face
#           counts as live while ANY face is open).   unknown = docs-lane debt.
# Cross-check: OPEN set must be identical between known-bugs.md and .pt_BR.md
# (docs-lang.sh checks pairing, not per-section state).
#
# Usage:
#   scripts/check_known_bugs_status.sh            # report + EN×PT drift (rc!=0 on drift)
#   scripts/check_known_bugs_status.sh --selftest # planted fixture must classify right
#
# Exit codes: 0 healthy; 1 drift/unknown; 2 selftest failure.

set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
EN="$REPO/docs/bugs-and-gaps/known-bugs.md"
PT="$REPO/docs/bugs-and-gaps/known-bugs.pt_BR.md"

classify() { # $1=file -> "NNN status" lines
  awk '
    function flush() {
      if (sid == "") return
      print num, (last == "" ? "unknown" : last)
    }
    /^#+ §[0-9]+/ {
      flush()
      sid++; n++
      hdr = $0; last = ""
      t = $0; sub(/^#*[ ]*§/, "", t); gsub(/[^0-9].*/, "", t); num = t
      # status embedded in the header line itself (suffix after the dash)
      if ($0 ~ /✅/) last = "closed"
      else if ($0 ~ /🟡|🔴/) last = "live"
      next
    }
    sid != "" {
      # status-bearing lines only (chronological append wins)
      if ($0 ~ /\*\*[Ss]tatus\*\*/ || $0 ~ /^[>\- ]*\*\*(Status|Estado|Resolu|Fechamento|Resolution|Fix|✅|🟡|🔴|CORRIGIDO|FECHADO|FIXED|RESOLVIDO)/ || $0 ~ /— *(✅|🟡|🔴)/ || $0 ~ /\*\*(✅|🟡|🔴) /) {
        if ($0 ~ /✅/ && $0 !~ /🟡|🔴|🟢.*🔴/) last = "closed"
        else if ($0 ~ /🟡|🔴|OPEN|ABERTO|PARTIAL|PARCIAL/) last = "live"
      }
    }
    END { flush() }
  ' "$1"
}

if [[ "${1:-}" == "--selftest" ]]; then
  FIX="$(mktemp -d)"
  cat > "$FIX/t.md" <<'EOF'
## §900 — closed sample — ✅ FIXED 18/09
**Status:** ✅ FIXED 18/09. Proof: green.
## §901 — live sample
- **Status:** 🟡 OPEN 18/09. Not fixed.
## §902 — multiface sample
**Face (a)** — ✅ FIXED 17/09 done.
**Face (b)** — 🟡 OPEN (lane nat): pending.
## §903 — no status token
just prose here
EOF
  OUT="$(classify "$FIX/t.md")"
  rm -rf "$FIX"
  echo "$OUT"
  echo "$OUT" | grep -qx "900 closed"      || { echo "SELFTEST FAIL: 900"; exit 2; }
  echo "$OUT" | grep -qx "901 live"        || { echo "SELFTEST FAIL: 901"; exit 2; }
  echo "$OUT" | grep -qx "902 live" || { echo "SELFTEST FAIL: 902"; exit 2; }
  echo "$OUT" | grep -qx "903 unknown"     || { echo "SELFTEST FAIL: 903"; exit 2; }
  echo "SELFTEST OK"
  exit 0
fi

EN_LEDGER="$(classify "$EN")"
PT_LEDGER="$(classify "$PT")"
EN_OPEN="$(echo "$EN_LEDGER" | awk '$2=="live"{print $1}' | sort -n)"
PT_OPEN="$(echo "$PT_LEDGER" | awk '$2=="live"{print $1}' | sort -n)"
EN_UNK="$(echo "$EN_LEDGER" | awk '$2=="unknown"{print $1}' | sort -n | tr '\n' ' ')"
PT_UNK="$(echo "$PT_LEDGER" | awk '$2=="unknown"{print $1}' | sort -n | tr '\n' ' ')"

rc=0
echo "EN open/partial ($(echo "$EN_OPEN" | grep -c .)): $(echo "$EN_OPEN" | tr '\n' ' ')"
echo "PT open/partial ($(echo "$PT_OPEN" | grep -c .)): $(echo "$PT_OPEN" | tr '\n' ' ')"
if [[ "$EN_OPEN" != "$PT_OPEN" ]]; then
  echo "DRIFT: EN×PT open-sets differ:"; diff <(echo "$EN_OPEN") <(echo "$PT_OPEN") | sed 's/^/  /'; rc=1
fi
if [[ -n "${EN_UNK// }" || -n "${PT_UNK// }" ]]; then
  echo "UNKNOWN (no readable status — docs-lane debt): EN[$EN_UNK] PT[$PT_UNK]"; rc=1
fi
[[ $rc -eq 0 ]] && echo "OK: statuses consistent EN×PT, no unknowns"
exit $rc
