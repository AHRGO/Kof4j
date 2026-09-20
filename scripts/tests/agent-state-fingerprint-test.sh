#!/usr/bin/env bash
#
# agent-state-fingerprint-test.sh — o fingerprint é ESTÁVEL (sem timestamp
# volátil) e sensível ao que justifica um re-despacho.
#
#   F1  auto-loop: repetível e sem relógio
#   F2  auto-loop: cada componente muda o fingerprint (HEAD, DOING, known-bugs,
#       docs/development, árvore suja)
#   F3  auto-loop: componente `ci` só entra quando o gh responde; gh fora = `na`
#   F4  issue-watcher: snapshot por issue (título/corpo/comentário EXTERNO)
#   F5  issue-watcher: comentário do próprio worker NÃO muda o snapshot
#   F6  issue-watcher: ordenação numérica (9 antes de 10) e issue sem comentário
#   F7  falha transitória do GitHub = exit 20 e nenhuma saída
#
# Uso: scripts/tests/agent-state-fingerprint-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

FP="$REPO_ROOT/scripts/agent-state-fingerprint.sh"
fp_of() { bash "$FP" "$@" | sed -n 's/^fingerprint=//p'; }
comp() { bash "$FP" "$@" | grep -v '^fingerprint='; }

echo "F1 — auto-loop repetível e sem relógio"
mk_env; mk_repo
A="$(fp_of auto-loop --repo "$REPO")"
sleep 1
B="$(fp_of auto-loop --repo "$REPO")"
assert_eq "$A" "$B" "mesmo estado, mesmo fingerprint (1s de intervalo)"
[ "${#A}" -eq 64 ] && pass "sha256 hex de 64 chars" || fail "fingerprint não é sha256: '$A'"

echo "F2 — cada componente muda o fingerprint"
mk_env; mk_repo
BASE="$(fp_of auto-loop --repo "$REPO")"
( cd "$REPO" && echo "x" > DOING.md )
D1="$(fp_of auto-loop --repo "$REPO")"
[ "$D1" != "$BASE" ] && pass "DOING.md editado muda" || fail "DOING.md editado não mudou"
( cd "$REPO" && git checkout -q DOING.md )
assert_eq "$BASE" "$(fp_of auto-loop --repo "$REPO")" "desfazer volta ao fingerprint original"
( cd "$REPO" && echo "y" > docs/bugs-and-gaps/known-bugs.md )
[ "$(fp_of auto-loop --repo "$REPO")" != "$BASE" ] && pass "known-bugs.md muda" || fail "known-bugs.md não mudou"
( cd "$REPO" && git checkout -q docs/bugs-and-gaps/known-bugs.md && echo "z" > wip.txt )
[ "$(fp_of auto-loop --repo "$REPO")" != "$BASE" ] && pass "árvore suja (arquivo novo) muda" || fail "árvore suja não mudou"
( cd "$REPO" && rm wip.txt && echo "novo plano" > docs/development/novo.md && git add -A && git commit -q -m plan )
COMP="$(comp auto-loop --repo "$REPO")"
assert_contains "$COMP" "docs_dev=" "componente docs_dev presente"
[ "$(fp_of auto-loop --repo "$REPO")" != "$BASE" ] && pass "commit novo (HEAD + docs/development) muda" || fail "commit não mudou"

echo "F3 — componente ci é opcional (gh fora = na)"
mk_env; mk_repo
export AGENT_FP_CI=1
assert_contains "$(comp auto-loop --repo "$REPO")" "ci=na" "sem runs no gh: ci=na"
printf 'CI:completed:success\n' > "$FAKE_GH_DIR/runs.txt"
C1="$(fp_of auto-loop --repo "$REPO")"
printf 'CI:completed:failure\n' > "$FAKE_GH_DIR/runs.txt"
C2="$(fp_of auto-loop --repo "$REPO")"
[ "$C1" != "$C2" ] && pass "CI do HEAD mudando muda o fingerprint" || fail "ci não influenciou"
export FAKE_GH_FAIL=1
assert_contains "$(comp auto-loop --repo "$REPO")" "ci=na" "gh falhando não derruba o gate: ci=na"
unset FAKE_GH_FAIL; export AGENT_FP_CI=0

echo "F4 — issue-watcher: snapshot por issue"
mk_env
set_issue 10 "titulo" "corpo"
add_comment 10 100 alice
S1="$(comp issue-watcher)"
assert_contains "$S1" "issue:10 " "linha da issue #10"
assert_contains "$S1" " c=100" "último comentário externo = 100"
F_A="$(fp_of issue-watcher)"
set_issue 10 "titulo" "corpo v2"
[ "$(fp_of issue-watcher)" != "$F_A" ] && pass "corpo editado muda" || fail "corpo editado não mudou"
set_issue 10 "titulo novo" "corpo v2"
comp issue-watcher | grep -q ' t=' && pass "hash do título presente" || fail "sem hash do título"

echo "F5 — comentário do próprio worker não muda o snapshot"
mk_env
set_issue 10 "titulo" "corpo"
add_comment 10 100 alice
F_A="$(fp_of issue-watcher)"
add_comment 10 101 'kof-agent-worker[bot]'
add_comment 10 102 'Kof-agent-worker'
assert_eq "$F_A" "$(fp_of issue-watcher)" "comentários do bot ignorados"
add_comment 10 103 melmonfre
[ "$(fp_of issue-watcher)" != "$F_A" ] && pass "comentário humano (melmonfre) muda" || fail "humano não mudou"

echo "F6 — ordenação numérica e issue sem comentário"
mk_env
set_issue 10 "dez" ""
set_issue 9 "nove" ""
LINES="$(comp issue-watcher | grep '^issue:')"
assert_eq "issue:9" "$(printf '%s\n' "$LINES" | head -n1 | cut -d' ' -f1)" "issue 9 antes da 10"
assert_contains "$LINES" "issue:9 " "issue sem comentário aparece"
assert_contains "$LINES" " c=0" "sem comentários = c=0 (issue nova é detectável por presença)"

echo "F7 — falha do GitHub = exit 20, sem saída"
mk_env
set_issue 10 "titulo" ""
export FAKE_GH_FAIL=1
OUT="$(bash "$FP" issue-watcher 2>/dev/null)"; RC=$?
assert_eq 20 "$RC" "exit 20 (transitório)"
assert_eq "" "$OUT" "nenhum fingerprint emitido na falha"

finish
