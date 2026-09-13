#!/usr/bin/env bash
# issue-watcher.sh — vigia uma issue do GitHub a cada N minutos (cron) e,
# quando há comentário novo, injeta um turno na SESSÃO ABERTA do opencode
# (--attach, nunca spawn concorrente) para o agente interagir com a issue.
#
# Uso:
#   scripts/issue-watcher.sh start [issue] [intervalo-min] [sessionID]
#   scripts/issue-watcher.sh stop
#   scripts/issue-watcher.sh status
#   scripts/issue-watcher.sh tick            # chamado pelo cron
set -euo pipefail

MARKER="kof-issue-watch"
SCRIPT=$(readlink -f "$0")
REPO_DIR=$(cd "$(dirname "$SCRIPT")/.." && pwd)
GH_REPO="KofLang/Kof4j"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/$MARKER"
STATE="$STATE_DIR/state"
LOG="$STATE_DIR/watch.log"

SERVER="${OPENCODE_SERVER_URL:-http://127.0.0.1:9093}"
OPENCODE="${OPENCODE_BIN:-}"
if [ -z "$OPENCODE" ]; then
    OPENCODE=$(command -v opencode || true)
    [ -n "$OPENCODE" ] || OPENCODE="$HOME/.opencode/bin/opencode"
fi

latest_comment_id() {
    local issue="$1"
    gh api "repos/$GH_REPO/issues/$issue/comments" --jq '.[-1].id // 0' 2>/dev/null || echo ""
}

open_issues() {
    gh issue list --repo "$GH_REPO" --state open --json number --jq '.[].number' 2>/dev/null || echo ""
}

cmd_start() {
    local issue="${1:-all}" interval="${2:-20}" session="${3:-}"
    [ -n "$session" ] || session=$("$OPENCODE" session list -n 1 --format json \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
    case "$interval" in *[!0-9]*|'') echo "intervalo deve ser inteiro (minutos)" >&2; exit 1;; esac
    mkdir -p "$STATE_DIR"
    local seen
    if [ "$issue" = "all" ]; then
        seen=$(for i in $(open_issues); do printf '%s=%s\n' "$i" "$(latest_comment_id "$i")"; done | tr '\n' ' ')
        seen="${seen:-none}"
    else
        seen=$(latest_comment_id "$issue")
        seen="${seen:-0}"
    fi
    {
        echo "issue=$issue"
        echo "interval=$interval"
        echo "session=$session"
        echo "server=$SERVER"
        echo "seen=\"$seen\""
        echo "started=$(date -Is)"
    } > "$STATE"
    local line
    if [ "$interval" -ge 60 ] && [ $((interval % 60)) -eq 0 ] && [ $((interval / 60)) -le 23 ] \
            && [ $((60 % (interval / 60))) -eq 0 ]; then
        line="0 */$((interval / 60)) * * * $SCRIPT tick # $MARKER"
    elif [ "$interval" -le 59 ]; then
        line="*/$interval * * * * $SCRIPT tick # $MARKER"
    else
        echo "intervalo precisa divides 60 (ex.: 5, 30, 60, 120) — recebido $interval" >&2; exit 1
    fi
    ( { crontab -l 2>/dev/null | grep -vF "$MARKER" || true; }; echo "$line" ) | crontab -
    echo "issue-watch ATIVO: #$issue (último comentário visto id=$seen) a cada ${interval}min -> sessão $session (attach $SERVER)"
    echo "parar: $SCRIPT stop"
}

cmd_stop() {
    if crontab -l 2>/dev/null | grep -qF "$MARKER"; then
        { crontab -l 2>/dev/null | grep -vF "$MARKER" || true; } | crontab -
    fi
    rm -f "$STATE"
    echo "issue-watch PARADO (estado em $STATE_DIR)"
}

cmd_status() {
    if [ -f "$STATE" ]; then echo "ATIVO:"; sed 's/^/  /' "$STATE"; else echo "INATIVO"; fi
    echo "cron:"
    crontab -l 2>/dev/null | grep -F "$MARKER" | sed 's/^/  /' || echo "  (nenhuma linha $MARKER)"
    if [ -f "$LOG" ]; then echo "últimos ticks:"; tail -n 5 "$LOG" | sed 's/^/  /'; fi
}

cmd_tick() {
    [ -f "$STATE" ] || exit 0
    # shellcheck disable=SC1090
    . "$STATE"
    # OPENCODE_SERVER_URL no momento do start fixa a porta da sessão-alvo;
    # o tick roda no cron sem o env → usa o server gravado no state.
    SERVER="${OPENCODE_SERVER_URL:-${server:-$SERVER}}"
    if [ "${issue:-}" = "all" ]; then
        tick_all
        return $?
    fi
    local now
    now=$(latest_comment_id "$issue")
    [ -n "$now" ] || { echo "$(date -Is) tick: gh falhou (sem rede?)" >> "$LOG"; return 0; }
    if [ "$now" -le "${seen:-0}" ] 2>/dev/null; then
        return 0
    fi
    if ! curl -s -o /dev/null -m 5 "$SERVER/global/health"; then
        echo "$(date -Is) tick #$issue: comentário novo ($seen->$now) mas servidor $SERVER fora do ar — não injeta" >> "$LOG"
        return 0
    fi
    echo "$(date -Is) tick #$issue: comentário novo ($seen->$now) -> injeta na sessão $session" >> "$LOG"
    local prompt="A issue https://github.com/$GH_REPO/issues/$issue tem comentário(s) novo(s) (último id visto $seen, agora $now). Leia os novos com 'gh issue view $issue --repo $GH_REPO --json comments', interaja (responda tecnicamente na issue se procedente), atualize DOING.md/plano conforme impactar o trabalho em docs/development, e continue a fila PRÓXIMO PASSO. Ao final commite."
    "$OPENCODE" run --session "$session" --dir "$REPO_DIR" --attach "$SERVER" --auto "$prompt" >> "$LOG" 2>&1 \
        || echo "$(date -Is) tick #$issue INJEÇÃO FALHOU (rc=$?)" >> "$LOG"
    # só avança o 'seen' depois de injetar (falha de entrega = re-tenta no próximo tick)
    sed -i "s/^seen=.*/seen=$now/" "$STATE"
}

tick_all() {
    local i now new_seen="" news=""
    for i in $(open_issues); do
        now=$(latest_comment_id "$i")
        [ -n "$now" ] || continue
        new_seen="$new_seen $i=$now"
        old=$(printf '%s' "${seen:-}" | tr ' ' '\n' | grep "^$i=" | cut -d= -f2)
        old="${old:-0}"
        if [ "$now" -gt "$old" ] 2>/dev/null; then
            news="$news #$i($old->$now)"
        fi
    done
    new_seen=$(printf '%s' "$new_seen" | tr -s ' ' | sed 's/^ //')
    if [ -z "$news" ]; then
        # só atualiza o snapshot (issue nova pode ter aparecido); sem ruído no log
        [ -n "$new_seen" ] && sed -i "s/^seen=.*/seen=\"$new_seen\"/" "$STATE"
        return 0
    fi
    if ! curl -s -o /dev/null -m 5 "$SERVER/global/health"; then
        echo "$(date -Is) tick all: comentários novos:$news mas servidor $SERVER fora do ar — não injeta" >> "$LOG"
        return 0
    fi
    echo "$(date -Is) tick all: comentários novos:$news -> injeta na sessão $session" >> "$LOG"
    local prompt="As issues$news têm comentário(s) novo(s) (ver 'gh issue view N --repo $GH_REPO --json comments'). Leia os novos, interaja (responda tecnicamente na issue se procedente), atualize DOING.md/plano conforme impactar o trabalho em docs/development, e continue a fila PRÓXIMO PASSO. Ao final commite."
    "$OPENCODE" run --session "$session" --dir "$REPO_DIR" --attach "$SERVER" --auto "$prompt" >> "$LOG" 2>&1 \
        || echo "$(date -Is) tick all INJEÇÃO FALHOU (rc=$?)" >> "$LOG"
    sed -i "s/^seen=.*/seen=\"$new_seen\"/" "$STATE"
}

case "${1:-}" in
    start)  shift; cmd_start "${1:-}" "${2:-}" "${3:-}";;
    stop)   cmd_stop;;
    status) cmd_status;;
    tick)   cmd_tick;;
    *)      echo "uso: $0 {start [issue] [min] [sessionID]|stop|status|tick}" >&2; exit 1;;
esac
