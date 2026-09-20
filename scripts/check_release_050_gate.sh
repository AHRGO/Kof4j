#!/usr/bin/env bash
# check_release_050_gate.sh — the 0.5.0 RELEASE GATE (D-RELEASE-0.5.0-GATE,
# 09/20/2026 maintainer directive). The 0.5.0 release (precondition for opening
# the 1.0 line, D-1.0-EDGES Q1) is cut only when all SEVEN conditions hold,
# each one MEASURED — never by eye.
#
#   1 parity       100% parity between targets (measured per-target matrix)
#   2 decisions    no pending decision that changes the surface
#   3 loose docs   all loose docs/development/*.md concluded and moved out
#   4 stability    full suite 0F/0E + 5/5 conformance matrix on the candidate
#   5 bug issues   0 OPEN GitHub issues that are a bug
#   6 edges        all edges closed (open 1.0-blocks = 0 + EG queue that gates
#                  the release closed)
#   7 bugs/gaps    nothing pending in known-bugs.md / specification-gaps.md
#
# Each condition is GREEN / RED / NEEDS-MEASURE / NEEDS-REVIEW / UNKNOWN.
# Exit: 0 = all GREEN; 1 = at least one RED; 2 = no RED but something
# inconclusive (NEEDS-*/UNKNOWN); 3 = a required data source is unavailable.
#
# RED is EXPECTED until the queue closes — the gate is the driver, not a
# blocker to work around.
#
# Offline / --selftest use data-source overrides (no network, no gh):
#   R050_OPEN_ISSUES_TSV  file "number<TAB>labels"
#   R050_KNOWN_BUGS_CMD   command printing the known-bugs ledger (default gate)
#   R050_EG_TSV           file "EG-N<TAB>state" (default: parse roadmap §24)
#   R050_LOOSE_MD_FILE    file listing loose md basenames (default: ls)
#   R050_PENDING_FILE     file whose first line is the pending-decision count
#   R050_PARITY_FILE      file containing "PARITY: 100%" when parity holds
#   R050_MATRIX_CMD       command that runs the per-target matrix (default
#                         `bash scripts/target-matrix.sh`, §14/EG-5); set empty
#                         to disable the auto-measure and stay NEEDS-MEASURE
#   KOF_SUITE_LOG         path to a real suite log; when set, condition 4 is
#                         auto-measured by scripts/stability-report.sh (0F/0E)
#
# Usage: scripts/check_release_050_gate.sh [--selftest]
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

OPEN_ISSUES_TSV="${R050_OPEN_ISSUES_TSV:-}"
KNOWN_BUGS_CMD="${R050_KNOWN_BUGS_CMD:-bash scripts/check_known_bugs_status.sh}"
EG_TSV="${R050_EG_TSV:-}"
LOOSE_MD_FILE="${R050_LOOSE_MD_FILE:-}"
PENDING_FILE="${R050_PENDING_FILE:-}"
PARITY_FILE="${R050_PARITY_FILE:-}"
R050_STABILITY_FILE="${R050_STABILITY_FILE:-}"
R050_SPEC_GAPS_FILE="${R050_SPEC_GAPS_FILE:-}"

# docs that are living/meta by nature and stay in docs/development (the
# three-states rule keeps them there while the phase is open).
ALLOWLIST="DECISIONS.md DECISIONS.pt_BR.md README.md README.pt_BR.md roadmap.md roadmap.pt_BR.md PROPOSAL-1.0-EXIT-GATE.md PROPOSAL-1.0-EXIT-GATE.pt_BR.md release-beta-0.5.0-prep.md release-beta-0.5.0-prep.pt_BR.md"

# state per condition: GREEN|RED|NEEDS-MEASURE|NEEDS-REVIEW|UNKNOWN
declare -A STATE DETAIL

c_parity() {
  local report="$PARITY_FILE" provided=1
  if [ -z "$report" ]; then
    provided=0
    # auto-mede: roda o harness da matriz (§14/EG-5) e le a linha PARITY.
    # R050_MATRIX_CMD sobrescreve (vazio = desliga → NEEDS-MEASURE, uso offline).
    local cmd="${R050_MATRIX_CMD-bash scripts/target-matrix.sh}"
    if [ -z "$cmd" ] || [ ! -f scripts/target-matrix.sh ]; then
      STATE[parity]=NEEDS-MEASURE
      DETAIL[parity]="run the per-target matrix on the candidate (JVM/x86-64/riscv64/aarch64/JS/Script); divergence = bug or XXX00x gap"
      return
    fi
    report="$(mktemp)"
    $cmd > "$report" 2>&1 || true
  fi
  if grep -q "PARITY: 100%" "$report" 2>/dev/null; then
    STATE[parity]=GREEN; DETAIL[parity]="per-target matrix reports 100%"
  elif [ "$provided" -eq 1 ] || grep -q "PARITY: 0%" "$report" 2>/dev/null; then
    STATE[parity]=RED; DETAIL[parity]="matrix reports a divergence — see $report"
  else
    STATE[parity]=NEEDS-MEASURE; DETAIL[parity]="matrix could not certify (missing toolchain/qemu) — see $report"
  fi
}

c_decisions() {
  if [ -n "$PENDING_FILE" ]; then
    local n; n="$(head -1 "$PENDING_FILE" 2>/dev/null | tr -dc '0-9')"; n="${n:-0}"
    if [ "$n" -eq 0 ]; then STATE[decisions]=GREEN; DETAIL[decisions]="no pending decision"
    else STATE[decisions]=RED; DETAIL[decisions]="$n pending decision(s)"; fi
    return
  fi
  if [ -d docs/development/decision-pending ]; then
    STATE[decisions]=RED; DETAIL[decisions]="decision-pending/ folder exists"
  else
    local open
    open="$(grep -rhoE '\[[?] *MEL *\]' docs/development/PROPOSAL-1.0-EXIT-GATE.md 2>/dev/null | wc -l | tr -d ' ')"
    STATE[decisions]=NEEDS-REVIEW
    DETAIL[decisions]="decision-pending/ extinct; $open §35 [? MEL] marker(s) in the PROPOSAL — confirm none is an open surface decision"
  fi
}

c_loose_docs() {
  local list extra
  if [ -n "$LOOSE_MD_FILE" ]; then
    list="$(cat "$LOOSE_MD_FILE" 2>/dev/null)"
  else
    list="$(cd docs/development 2>/dev/null && ls *.md 2>/dev/null)"
  fi
  extra=""
  local f
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    case " $ALLOWLIST " in *" $f "*) : ;; *) extra="$extra $f" ;; esac
  done <<< "$list"
  if [ -z "${extra// }" ]; then STATE[loose_docs]=GREEN; DETAIL[loose_docs]="no work doc left in docs/development/"
  else STATE[loose_docs]=RED; DETAIL[loose_docs]="conclude + move:$extra"; fi
}

c_stability() {
  if [ -n "$R050_STABILITY_FILE" ]; then
    if grep -q "STABILITY: GREEN" "$R050_STABILITY_FILE" 2>/dev/null; then
      STATE[stability]=GREEN; DETAIL[stability]="suite 0F/0E + 5/5 matrix recorded on the candidate"
    else
      STATE[stability]=RED; DETAIL[stability]="recorded stability report is not green"
    fi
    return
  fi
  # auto-mede do log de uma corrida REAL da suite (KOF_SUITE_LOG), sem re-rodar:
  # scripts/stability-report.sh le a ultima linha TOTAL e exige 0F/0E.
  if [ -n "${KOF_SUITE_LOG:-}" ] && [ -f scripts/stability-report.sh ]; then
    local report; report="$(mktemp)"
    bash scripts/stability-report.sh --suite-log "$KOF_SUITE_LOG" > "$report" 2>&1 || true
    if grep -q "STABILITY: GREEN" "$report"; then
      STATE[stability]=GREEN; DETAIL[stability]="suite log 0F/0E on the candidate ($KOF_SUITE_LOG)"
    elif grep -q "STABILITY: RED" "$report"; then
      STATE[stability]=RED; DETAIL[stability]="suite log has failures/errors ($KOF_SUITE_LOG)"
    else
      STATE[stability]=NEEDS-MEASURE; DETAIL[stability]="suite log unreadable (no TOTAL) — see $report"
    fi
    return
  fi
  STATE[stability]=NEEDS-MEASURE
  DETAIL[stability]="run scripts/safe-suite.sh (0F/0E) + the 5/5 conformance matrix on the candidate"
}

c_bug_issues() {
  local rows
  if [ -n "$OPEN_ISSUES_TSV" ]; then
    rows="$(cat "$OPEN_ISSUES_TSV" 2>/dev/null)"
  else
    eval "$(scripts/gh-as-agent.sh token 2>/dev/null)" || true
    rows="$(gh issue list --repo KofLang/Kof4j --state open --limit 200 \
      --json number,labels --jq '.[] | "\(.number)\t\([.labels[].name]|join(","))"' 2>/dev/null)" || rows=""
    if [ -z "$rows" ] && ! gh auth status >/dev/null 2>&1; then
      STATE[bug_issues]=UNKNOWN; DETAIL[bug_issues]="gh unavailable — cannot enumerate open issues"
      return
    fi
  fi
  local bugs=""
  while IFS=$'\t' read -r num labels; do
    [ -z "$num" ] && continue
    case ",$labels," in *,bug,*) bugs="$bugs #$num" ;; esac
  done <<< "$rows"
  if [ -z "${bugs// }" ]; then STATE[bug_issues]=GREEN; DETAIL[bug_issues]="0 open bug issues"
  else STATE[bug_issues]=RED; DETAIL[bug_issues]="open bug issue(s):$bugs"; fi
}

c_edges() {
  # Criterion 6 counts the FULL edge queue (maintainer 09/20/2026): every open
  # EG item, EG-1 through EG-10 (no 1.0-phase exemption), plus the open
  # `1.0-blocks` issues.
  local eg_open="" blocks=0
  if [ -n "$EG_TSV" ]; then
    while IFS=$'\t' read -r eg st; do
      [ -z "$eg" ] && continue
      case "$st" in *DONE*|*FEITO*) : ;; *) eg_open="$eg_open $eg" ;; esac
    done < "$EG_TSV"
    blocks="${R050_OPEN_BLOCKS:-0}"
  else
    eg_open="$(awk -F'|' '/^\| *EG-[0-9]+ /{ id=$2; gsub(/ /,"",id); if ($0 !~ /DONE|FEITO/) print id }' docs/development/roadmap.md 2>/dev/null | tr '\n' ' ')"
    eval "$(scripts/gh-as-agent.sh token 2>/dev/null)" || true
    local out; out="$(bash scripts/check_release_blockers.sh --rc-gate 2>&1)"
    blocks="$(printf '%s\n' "$out" | sed -n 's/.*-- \([0-9]*\) open 1.0-blocks.*/\1/p' | head -1)"
    blocks="${blocks:-0}"
  fi
  if [ -z "${eg_open// }" ] && [ "${blocks:-0}" -eq 0 ]; then
    STATE[edges]=GREEN; DETAIL[edges]="no open edge (EG-1..EG-10 closed, 0 open 1.0-blocks)"
  else
    STATE[edges]=RED
    DETAIL[edges]="open edge(s):${eg_open:- none}; open 1.0-blocks: $blocks"
  fi
}

c_bugs_gaps() {
  local kb_out kb_n=0 sg_n=0
  kb_out="$($KNOWN_BUGS_CMD 2>/dev/null)"
  kb_n="$(printf '%s\n' "$kb_out" | sed -n 's/^EN open\/partial (\([0-9]*\)).*/\1/p' | head -1)"
  kb_n="${kb_n:-0}"
  if [ -n "$R050_SPEC_GAPS_FILE" ]; then
    sg_n="$(grep -cE '🟡|🔴' "$R050_SPEC_GAPS_FILE" 2>/dev/null | tr -d ' ')"; sg_n="${sg_n:-0}"
  else
    sg_n="$(grep -cE '🟡|🔴' docs/bugs-and-gaps/specification-gaps.md 2>/dev/null | tr -d ' ')"; sg_n="${sg_n:-0}"
  fi
  if [ "$kb_n" -eq 0 ] && [ "$sg_n" -eq 0 ]; then
    STATE[bugs_gaps]=GREEN; DETAIL[bugs_gaps]="no live known-bug, 0 open spec gap"
  else
    STATE[bugs_gaps]=RED; DETAIL[bugs_gaps]="$kb_n live known-bug(s), $sg_n open spec gap(s)"
  fi
}

if [ "${1:-}" = "--selftest" ]; then
  T="$(mktemp -d)"
  trap 'rm -rf "$T"' EXIT
  fail() { echo "SELFTEST FAIL: $*" >&2; exit 2; }

  # clean fixture -> every measurable condition GREEN
  printf '100\tdocumentation,post-1.0\n' > "$T/issues"
  printf 'EG-1\tDONE\nEG-2\tDONE\n' > "$T/eg"
  printf 'PARITY: 100%%\n' > "$T/parity"
  printf 'STABILITY: GREEN\n' > "$T/stab"
  printf '0\n' > "$T/pending"
  printf 'DECISIONS.md\nroadmap.md\n' > "$T/loose"
  : > "$T/spec"
  cat > "$T/kb" <<'EOF'
EN open/partial (0): 
PT open/partial (0): 
EOF
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" \
  R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" R050_SPEC_GAPS_FILE="$T/spec" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
    bash "$0" > "$T/out"; rc=$?
  [ "$rc" -eq 0 ] || fail "clean fixture should be exit 0, got $rc"
  grep -q 'parity .*GREEN' "$T/out" || fail "clean parity not GREEN"
  grep -q 'bugs_gaps .*GREEN' "$T/out" || fail "clean bugs_gaps not GREEN"

  # dirty fixture -> REDs on every condition that has data
  printf '561\tbug,1.0-blocks\n563\tbug,1.0-blocks\n566\tbug,1.0-outside\n' > "$T/issues"
  printf 'EG-1\tDONE\nEG-5\tOPEN\nEG-9\tOPEN\n' > "$T/eg"
  printf 'PARITY: 97%%\n' > "$T/parity"
  printf 'STABILITY: RED\n' > "$T/stab"
  printf '2\n' > "$T/pending"
  printf 'DECISIONS.md\nmakealive-plan.md\n' > "$T/loose"
  printf 'EN open/partial (19): 188 192\nPT open/partial (19): 188 192\n' > "$T/kb"
  printf '🟡\n' > "$T/spec"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" \
  R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" R050_SPEC_GAPS_FILE="$T/spec" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=1 \
    bash "$0" > "$T/out2"; rc=$?
  [ "$rc" -eq 1 ] || fail "dirty fixture should be exit 1, got $rc"
  grep -q 'parity .*RED' "$T/out2" || fail "dirty parity not RED"
  grep -q 'bug_issues .*RED' "$T/out2" || fail "dirty bug_issues not RED"
  grep -q 'edges .*RED' "$T/out2" || fail "dirty edges not RED"
  grep -q 'loose_docs .*RED' "$T/out2" || fail "dirty loose_docs not RED"
  grep -q 'bugs_gaps .*RED' "$T/out2" || fail "dirty bugs_gaps not RED"

  # inconclusive fixture -> exit 2 (no RED, but NEEDS-*)
  printf '100\tdocumentation,post-1.0\n' > "$T/issues"
  printf 'EG-1\tDONE\n' > "$T/eg"
  : > "$T/pending"; printf 'DECISIONS.md\n' > "$T/loose"
  : > "$T/spec"
  printf 'EN open/partial (0):\nPT open/partial (0):\n' > "$T/kb"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  R050_MATRIX_CMD=: KOF_SUITE_LOG= \
    bash "$0" > "$T/out3"; rc=$?
  [ "$rc" -eq 2 ] || fail "inconclusive fixture should be exit 2, got $rc"
  grep -q 'NEEDS-MEASURE' "$T/out3" || fail "inconclusive run should surface NEEDS-MEASURE"

  echo "SELFTEST OK"
  exit 0
fi

c_parity; c_decisions; c_loose_docs; c_stability; c_bug_issues; c_edges; c_bugs_gaps

ORDER=(parity decisions loose_docs stability bug_issues edges bugs_gaps)
red=0; incon=0
echo "== 0.5.0 release gate (D-RELEASE-0.5.0-GATE) =="
for k in "${ORDER[@]}"; do
  printf '  %-11s %-13s %s\n' "$k" "${STATE[$k]}" "${DETAIL[$k]}"
  case "${STATE[$k]}" in
    RED) red=$((red+1)) ;;
    GREEN) : ;;
    *) incon=$((incon+1)) ;;
  esac
done
echo "  -- $red RED, $incon inconclusive of ${#ORDER[@]}"
if [ "$red" -gt 0 ]; then exit 1; fi
if [ "$incon" -gt 0 ]; then exit 2; fi
echo "  0.5.0 RELEASE GATE: GREEN — all seven conditions measured and holding"
exit 0
