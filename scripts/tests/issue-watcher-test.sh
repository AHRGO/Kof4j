#!/usr/bin/env bash
#
# issue-watcher-test.sh — o watcher `all` só pode chamar o OpenCode quando há
# EVENTO EXTERNO relevante (Onda 1, custo de modelo = 0 sem novidade).
#
# Cenários (todos com gh/opencode/curl FAKES; contam chamadas ao opencode):
#   W1  sem mudança                       → 0 chamadas (varredura vazia é o desperdício)
#   W2  issue NOVA sem comentário         → 1 chamada, e só 1
#   W3  comentário do PRÓPRIO bot         → 0 chamadas (sem auto-retrigger)
#   W4  comentário HUMANO (inclui melmonfre) → 1 chamada
#   W5  título/corpo editado              → 1 chamada
#   W6  falha da API do GitHub            → 0 chamadas e o snapshot NÃO avança
#   W7  servidor TUI fora do ar           → 0 chamadas e o evento NÃO se perde
#   W8  injeção falhou (rc!=0)            → o mesmo evento é retentado
#   W9  telemetria de skip/dispatch       → dispatch.jsonl com motivo e fingerprint
#   W10 modo shadow (estado legado)       → comportamento antigo + registro do que o gate faria
#   W11 issue fechada (só remoção)        → 0 chamadas
#   W12 >30 comentários (paginação)       → comentário externo novo na pág. 2 é visto
#
# Uso: scripts/tests/issue-watcher-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

WATCHER="$REPO_ROOT/scripts/issue-watcher.sh"

mk_watcher_state() { # gate_mode
    local st="$XDG_STATE_HOME/kof-issue-watch"
    mkdir -p "$st"
    {
        echo "issue=all"
        echo "interval=5"
        echo "session=ses_test"
        echo "server=http://127.0.0.1:9"
        echo 'seen="10=100"'
        echo "started=2026-09-20T00:00:00-03:00"
        [ -n "${1:-}" ] && echo "gate_mode=$1"
    } > "$st/state"
}
tick() { bash "$WATCHER" tick >> "$TMP/tick.out" 2>&1; }

# baseline comum: issue #10 com um comentário humano (id 100) já visto
baseline() {
    mk_env
    mk_watcher_state active
    set_issue 10 "issue antiga" "corpo antigo"
    add_comment 10 100 alice
    tick    # 1º tick: baseline (nunca pode gastar modelo)
}

echo "W1 — sem mudança → 0 chamadas"
baseline
tick; tick
assert_eq 0 "$(opencode_calls)" "3 ticks sem novidade: nenhuma chamada ao OpenCode"

echo "W2 — issue nova SEM comentário → exatamente 1 chamada"
baseline
set_issue 11 "issue nova" "sem comentários"
tick
assert_eq 1 "$(opencode_calls)" "issue nova sem comentário despacha"
assert_contains "$(last_opencode_call)" "#11" "prompt focalizado cita a issue nova #11"
tick
assert_eq 1 "$(opencode_calls)" "mesmo evento não despacha de novo no tick seguinte"

echo "W3 — comentário do próprio worker não reativa"
baseline
add_comment 10 101 'kof-agent-worker[bot]'
tick
assert_eq 0 "$(opencode_calls)" "comentário do bot não é evento externo"
add_comment 10 102 'Kof-agent-worker'
add_comment 10 103 'kof-agent-worker'
tick
assert_eq 0 "$(opencode_calls)" "variantes de login do worker também são ignoradas"

echo "W4 — comentário humano reativa (melmonfre NÃO é bot)"
baseline
add_comment 10 102 melmonfre
tick
assert_eq 1 "$(opencode_calls)" "comentário de mantenedor humano despacha"
assert_contains "$(last_opencode_call)" "#10" "prompt focalizado cita a issue #10"

echo "W5 — título/corpo editado despacha"
baseline
set_issue 10 "issue antiga" "corpo EDITADO"
tick
assert_eq 1 "$(opencode_calls)" "edição de corpo despacha"

echo "W6 — falha do GitHub não é 'estado estável'"
baseline
export FAKE_GH_FAIL=1
tick
assert_eq 0 "$(opencode_calls)" "gh falhando: nenhuma chamada"
unset FAKE_GH_FAIL
set_issue 12 "nasceu durante a falha" ""
tick
assert_eq 1 "$(opencode_calls)" "snapshot não avançou na falha: o evento ainda é detectado"

echo "W7 — servidor fora do ar não perde o evento"
baseline
set_issue 13 "nova com servidor fora" ""
export FAKE_SERVER_DOWN=1
tick
assert_eq 0 "$(opencode_calls)" "servidor fora do ar: não injeta"
unset FAKE_SERVER_DOWN
tick
assert_eq 1 "$(opencode_calls)" "servidor de volta: o evento pendente é despachado"

echo "W8 — injeção falhou → retenta o mesmo evento"
baseline
set_issue 14 "nova, run falha" ""
export FAKE_OPENCODE_RC=1
tick
assert_eq 1 "$(opencode_calls)" "1ª tentativa chamou o OpenCode (rc=1)"
unset FAKE_OPENCODE_RC
tick
assert_eq 2 "$(opencode_calls)" "falha de entrega: o próximo tick retenta"
tick
assert_eq 2 "$(opencode_calls)" "sucesso consumiu o evento: sem 3ª chamada"

echo "W9 — telemetria de decisão"
baseline
tick
set_issue 15 "para telemetria" ""
tick
T="$(telemetry_file)"
if [ -s "$T" ]; then pass "dispatch.jsonl existe"; else fail "dispatch.jsonl ausente"; fi
assert_contains "$(cat "$T" 2>/dev/null)" '"decision":"skip"' "registra skip"
assert_contains "$(cat "$T" 2>/dev/null)" '"decision":"dispatch"' "registra dispatch"
assert_contains "$(cat "$T" 2>/dev/null)" '"source":"issue-watcher"' "identifica a origem"
assert_contains "$(cat "$T" 2>/dev/null)" '"fingerprint":"' "registra o fingerprint"

echo "W10 — estado legado (sem gate_mode) = shadow: mantém o comportamento antigo e registra o que o gate faria"
mk_env
mk_watcher_state ""
set_issue 10 "issue antiga" "corpo antigo"
add_comment 10 100 alice
tick
assert_eq 1 "$(opencode_calls)" "shadow preserva a varredura legada (1 chamada)"
assert_contains "$(cat "$(telemetry_file)" 2>/dev/null)" '"legacy_would_call":true' "registra legacy_would_call"
assert_contains "$(cat "$(telemetry_file)" 2>/dev/null)" '"new_gate":"' "registra a decisão do gate novo"

echo "W11 — issue fechada (só remoção) não despacha"
baseline
set_issue 16 "vai fechar" ""
tick
N="$(opencode_calls)"
close_issue_fixture 16
tick
assert_eq "$N" "$(opencode_calls)" "remoção de issue aberta não é trabalho acionável"

echo "W12 — >30 comentários: paginação enxerga o externo novo"
mk_env
mk_watcher_state active
set_issue 10 "issue longa" ""
for i in $(seq 1 35); do add_comment 10 "$((200 + i))" alice; done
tick
add_comment 10 300 alice
tick
assert_eq 1 "$(opencode_calls)" "comentário novo além da 1ª página foi detectado"
assert_contains "$(cat "$FAKE_GH_DIR/gh.calls")" "--paginate" "usa --paginate na API de comentários"

finish
