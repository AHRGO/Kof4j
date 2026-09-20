#!/usr/bin/env bash
#
# agent-dispatch-gate-test.sh — as decisões do gate, seus exit codes DISTINTOS
# e a telemetria. (Os cenários T1–T7 da Onda 1, no nível do gate.)
#
#   G1  auto-loop: 1º despacho; estado igual = 10; mudou = 0 com o motivo
#   G2  auto-loop: run PRODUTIVO continua (T5) e run vazio para (T6)
#   G3  códigos 0/10/11/12/20 são distintos
#   G4  watcher: baseline; issue nova sem comentário despacha (T2) e só é
#       consumida após `record --rc 0`
#   G5  watcher: bot não reativa (T3); humano reativa (T4)
#   G6  watcher: falha de API = 20, não persiste, não vira "estável" (T7)
#   G7  falhas do agente: 1ª retenta, 2ª cooldown (12), sucesso zera
#   G8  só remoção de issue = 11
#   G9  --dry-run não persiste nem grava telemetria
#   G10 stats: ticks/dispatches/avoidance/falhas
#   G11 shadow: registra legacy_would_call + new_gate
#   G12 seed: baseline sem despacho
#
# Uso: scripts/tests/agent-dispatch-gate-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

GATE="$REPO_ROOT/scripts/agent-dispatch-gate.sh"
decide_loop() { bash "$GATE" decide auto-loop --session s1 --repo "$REPO" "$@"; }
decide_watch() { bash "$GATE" decide issue-watcher --session s1 "$@"; }
rc_of() { "$@" >/dev/null 2>&1; echo $?; }

echo "G1 — auto-loop: despacho, igual, mudou"
mk_env; mk_repo
OUT="$(decide_loop)"; RC=$?
assert_eq 0 "$RC" "1º: DISPATCH (exit 0)"
assert_contains "$OUT" "reason=first_dispatch" "motivo first_dispatch"
assert_eq 10 "$(rc_of decide_loop)" "estado igual: SKIP_NO_CHANGE (10)"
( cd "$REPO" && echo "novo" > DOING.md && git add -A && git commit -q -m c )
OUT="$(decide_loop)"; RC=$?
assert_eq 0 "$RC" "mudou: DISPATCH"
assert_contains "$OUT" "head_changed" "motivo cita head_changed"
assert_contains "$OUT" "doing_changed" "motivo cita doing_changed"

echo "G2 — run produtivo continua, run vazio para"
mk_env; mk_repo
decide_loop >/dev/null                                   # despacho 1 (fingerprint pré-run F0)
assert_eq 10 "$(rc_of decide_loop)" "run que não mudou nada: tick seguinte grátis (T6)"
( cd "$REPO" && echo "run produtivo" > DOING.md && git add -A && git commit -q -m produtivo )
assert_eq 0 "$(rc_of decide_loop)" "run produtivo mudou o estado: continua (T5)"
assert_eq 10 "$(rc_of decide_loop)" "e o seguinte, idêntico, é grátis de novo"

echo "G3 — exit codes distintos"
mk_env; set_issue 10 "t" ""
decide_watch >/dev/null                                  # baseline
assert_eq 10 "$(rc_of decide_watch)" "SKIP_NO_CHANGE = 10"
set_issue 11 "nova" ""
assert_eq 0 "$(rc_of decide_watch)" "DISPATCH = 0"
bash "$GATE" record issue-watcher --session s1 --rc 0 >/dev/null
close_issue_fixture 11
assert_eq 11 "$(rc_of decide_watch)" "SKIP_NO_ACTIONABLE_WORK = 11"
export FAKE_GH_FAIL=1
assert_eq 20 "$(rc_of decide_watch)" "RETRY_TRANSIENT_SOURCE_FAILURE = 20"
unset FAKE_GH_FAIL

echo "G4 — watcher: issue nova sem comentário"
mk_env; set_issue 10 "t" ""
decide_watch >/dev/null
set_issue 11 "nova sem comentario" ""
OUT="$(decide_watch)"; RC=$?
assert_eq 0 "$RC" "issue nova detectada"
assert_contains "$OUT" "event: new_issue #11" "evento new_issue #11"
assert_contains "$(bash "$GATE" events issue-watcher --session s1)" "new_issue #11" "gate events lista o evento"
assert_eq 0 "$(rc_of decide_watch)" "sem record: o evento NÃO foi consumido (retenta)"
bash "$GATE" record issue-watcher --session s1 --rc 0 >/dev/null
assert_eq 10 "$(rc_of decide_watch)" "após record --rc 0: consumido"

echo "G5 — bot não reativa; humano reativa"
mk_env; set_issue 10 "t" ""; add_comment 10 100 alice
decide_watch >/dev/null
add_comment 10 101 'kof-agent-worker[bot]'
assert_eq 10 "$(rc_of decide_watch)" "T3: comentário do bot = SKIP"
add_comment 10 102 melmonfre
OUT="$(decide_watch)"; RC=$?
assert_eq 0 "$RC" "T4: comentário humano despacha"
assert_contains "$OUT" "external_comment #10 id=102" "evento external_comment com o id"

echo "G6 — falha de API não avança o snapshot (T7)"
mk_env; set_issue 10 "t" ""
decide_watch >/dev/null
set_issue 12 "nasceu durante a falha" ""
export FAKE_GH_FAIL=1
assert_eq 20 "$(rc_of decide_watch)" "gh fora: 20"
unset FAKE_GH_FAIL
assert_eq 0 "$(rc_of decide_watch)" "gh de volta: o evento ainda existe (snapshot intacto)"

echo "G7 — falhas do agente: retenta, cooldown, sucesso zera"
mk_env; mk_repo
export AGENT_COOLDOWN_2_S=600
decide_loop >/dev/null
bash "$GATE" record auto-loop --session s1 --rc 1 --duration 5 >/dev/null
OUT="$(decide_loop)"; RC=$?
assert_eq 0 "$RC" "1ª falha: retenta no tick seguinte"
assert_contains "$OUT" "retry_after_failure" "motivo retry_after_failure"
bash "$GATE" record auto-loop --session s1 --rc 1 --duration 5 >/dev/null
assert_eq 12 "$(rc_of decide_loop)" "2ª falha consecutiva: cooldown = SKIP_AGENT_BUSY (12)"
rm -f "$XDG_STATE_HOME/kof-agent/gate/auto-loop/s1/cooldown_until"
bash "$GATE" record auto-loop --session s1 --rc 0 --duration 5 >/dev/null
assert_eq 10 "$(rc_of decide_loop)" "sucesso zerou falhas: estado igual = 10"
unset AGENT_COOLDOWN_2_S

echo "G8 — só remoção de issue = 11"
mk_env; set_issue 10 "a" ""; set_issue 11 "b" ""
decide_watch >/dev/null
close_issue_fixture 11
assert_eq 11 "$(rc_of decide_watch)" "remoção não é trabalho acionável"
assert_eq 10 "$(rc_of decide_watch)" "e o snapshot avançou (próximo tick igual)"

echo "G9 — --dry-run não persiste nem grava telemetria"
mk_env; mk_repo
OUT="$(decide_loop --dry-run)"
assert_contains "$OUT" "decision=DISPATCH" "dry-run mostra a decisão"
assert_eq 0 "$(rc_of decide_loop --dry-run)" "dry-run repetido: continua DISPATCH"
[ -s "$(telemetry_file)" ] && fail "dry-run gravou telemetria" || pass "sem telemetria no dry-run"
[ -f "$XDG_STATE_HOME/kof-agent/gate/auto-loop/s1/last" ] && fail "dry-run persistiu estado" || pass "sem estado persistido"

echo "G10 — stats"
mk_env; mk_repo
decide_loop >/dev/null
bash "$GATE" record auto-loop --session s1 --rc 0 --duration 100 >/dev/null
decide_loop >/dev/null; decide_loop >/dev/null; decide_loop >/dev/null
S="$(bash "$GATE" stats --since 24h)"
assert_contains "$S" "Ticks:                 4" "4 ticks"
assert_contains "$S" "OpenCode dispatches:   1" "1 despacho"
assert_contains "$S" "Skipped:               3" "3 evitados"
assert_contains "$S" "Dispatch avoidance:    75.0%" "75.0% de evitação"
assert_contains "$S" "Median run duration:   100" "duração mediana"

echo "G11 — shadow"
mk_env; mk_repo
decide_loop --shadow >/dev/null; decide_loop --shadow >/dev/null
assert_contains "$(cat "$(telemetry_file)")" '"legacy_would_call":true' "legacy_would_call"
assert_contains "$(cat "$(telemetry_file)")" '"new_gate":"skip"' "new_gate=skip no tick repetido"

echo "G12 — seed"
mk_env; set_issue 10 "t" ""
bash "$GATE" seed issue-watcher --session s1 >/dev/null
assert_eq 10 "$(rc_of decide_watch)" "após seed: nada a despachar"

finish
