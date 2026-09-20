#!/usr/bin/env bash
# issue-watcher.sh — vigia issues do GitHub a cada N minutos (cron) e injeta um
# turno na SESSÃO ABERTA do opencode (--attach, nunca spawn concorrente) SÓ
# quando há EVENTO EXTERNO: issue nova (mesmo sem comentário), título/corpo
# editado ou comentário externo novo. Polling é barato (gh/hash, custo de modelo
# zero); o modelo só é chamado quando o gate (agent-dispatch-gate.sh) acha
# evento. Comentário do PRÓPRIO worker (kof-agent-worker[bot]) nunca retriga;
# comentário humano (inclusive da mantenedora) sempre conta. `all` = issues
# abertas, prompt focalizado nos eventos; a varredura completa não é mais a
# cada tick.
#
# Uso:
#   scripts/issue-watcher.sh start [issue|all] [intervalo-min] [sessionID]
#   scripts/issue-watcher.sh stop
#   scripts/issue-watcher.sh status
#   scripts/issue-watcher.sh tick            # chamado pelo cron
#   scripts/issue-watcher.sh set-mode active|shadow   # rollout do gate (sem reiniciar)
#   scripts/issue-watcher.sh stats [--since 24h]      # ticks x chamadas ao modelo
#
# gate_mode=shadow (estado legado, sem a chave): comportamento ANTIGO (varredura
# completa a cada tick) + registro do que o gate faria; `active` = só eventos.
set -euo pipefail

MARKER="kof-issue-watch"
SCRIPT=$(readlink -f "$0")
REPO_DIR=$(cd "$(dirname "$SCRIPT")/.." && pwd)
GH_REPO="KofLang/Kof4j"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/$MARKER"
STATE="$STATE_DIR/state"
LOG="$STATE_DIR/watch.log"
LOCK="$STATE_DIR/lock"
# Gate de despacho (Onda 1): o watcher só chama o modelo com EVENTO EXTERNO.
GATE="$(dirname "$SCRIPT")/agent-dispatch-gate.sh"
# shellcheck source=agent-common.sh
. "$(dirname "$SCRIPT")/agent-common.sh"

SERVER="${OPENCODE_SERVER_URL:-http://127.0.0.1:9093}"
OPENCODE="${OPENCODE_BIN:-}"
if [ -z "$OPENCODE" ]; then
    OPENCODE=$(command -v opencode || true)
    [ -n "$OPENCODE" ] || OPENCODE="$HOME/.opencode/bin/opencode"
fi

# Último comentário EXTERNO (ignora o próprio worker) de TODAS as páginas —
# `.[-1]` da 1ª página (30) errava em issues longas. Vazio = falha do gh.
latest_comment_id() {
    local issue="$1" cm cid login max=0
    cm=$(gh api --paginate "repos/$GH_REPO/issues/$issue/comments?per_page=100" \
        --jq '.[] | [.id, .user.login] | @tsv' 2>/dev/null) || { echo ""; return 0; }
    while IFS=$'\t' read -r cid login; do
        [ -n "$cid" ] || continue
        is_self_login "$login" && continue
        [ "$cid" -gt "$max" ] 2>/dev/null && max="$cid"
    done <<< "$cm"
    echo "$max"
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
        echo "gate_mode=${AGENT_GATE_MODE:-active}"
        echo "started=$(date -Is)"
    } > "$STATE"
    # baseline do gate (custo zero): eventos só a partir de agora
    [ "$issue" = "all" ] && { "$GATE" seed issue-watcher --session "$session" >/dev/null 2>&1 || true; }
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
    # GUARDA (16/09): sem lock, cada tick empilhava um 'opencode run' na sessao
    # viva (16 runs acumulados 15/09 21:54->22:24; 'seen' so avanca apos o run
    # terminar -> o mesmo tick re-injetava a mesma varredura infinitamente,
    # competindo com o turno injetado). Espelho do flock+watchdog de
    # auto-loop.sh:139-175: run anterior ativo = tick pulado; stale >=
    # WATCHER_MAX_MIN (default 240) = mata o holder e segue.
    mkdir -p "$STATE_DIR"
    exec 9>"$LOCK"
    if ! flock -n 9; then
        local age_min max holder held_since
        age_min=0
        if [ -f "$LOCK.held" ]; then
            held_since=$(cat "$LOCK.held" 2>/dev/null || echo 0)
            case "$held_since" in (*[!0-9]*|'') held_since=0;; esac
            age_min=$(( ( $(date +%s) - held_since ) / 60 ))
        fi
        max="${WATCHER_MAX_MIN:-240}"
        if [ "$age_min" -ge "$max" ]; then
            holder=$(fuser "$LOCK" 2>/dev/null | tr -s ' \t' '\n' | grep -E '^[0-9]+$' | grep -vx "$$" | tr '\n' ' ' || true)
            echo "$(date -Is) lock STALE (${age_min}min >= ${max}min) — matando holder(s): ${holder:-nenhum}" >> "$LOG"
            if [ -n "$holder" ]; then
                # shellcheck disable=SC2086
                kill $holder 2>/dev/null || true
                sleep 2
            fi
            exec 9>"$LOCK"
            if ! flock -n 9; then
                echo "$(date -Is) tick pulado: lock ainda ocupada apos kill do holder stale" >> "$LOG"
                return 0
            fi
        else
            echo "$(date -Is) tick pulado: run anterior ainda ativo (${age_min}min < ${max}min)" >> "$LOG"
            "$GATE" note issue-watcher --session "$session" --decision skip --reason agent_busy || true
            return 0
        fi
    fi
    date +%s > "$LOCK.held"
    if [ "${issue:-}" = "all" ]; then
        tick_all
        return $?
    fi
    local now
    now=$(latest_comment_id "$issue")
    if [ -z "$now" ]; then
        echo "$(date -Is) tick: gh falhou (sem rede?)" >> "$LOG"
        "$GATE" note issue-watcher --session "$session" --decision retry --reason source_failure || true
        return 0
    fi
    if [ "$now" -le "${seen:-0}" ] 2>/dev/null; then
        "$GATE" note issue-watcher --session "$session" --decision skip --reason no_external_change || true
        return 0
    fi
    if ! curl -s -o /dev/null -m 5 "$SERVER/global/health"; then
        echo "$(date -Is) tick #$issue: comentário novo ($seen->$now) mas servidor $SERVER fora do ar — não injeta" >> "$LOG"
        "$GATE" note issue-watcher --session "$session" --decision skip --reason server_down || true
        return 0
    fi
    echo "$(date -Is) tick #$issue: comentário novo ($seen->$now) -> injeta na sessão $session" >> "$LOG"
    "$GATE" note issue-watcher --session "$session" --decision dispatch --reason external_comment || true
    local prompt="A issue https://github.com/$GH_REPO/issues/$issue tem comentário(s) novo(s) (último id visto $seen, agora $now). Leia os novos com 'gh issue view $issue --repo $GH_REPO --json comments', interaja (responda tecnicamente na issue se procedente), atualize DOING.md/plano conforme impactar o trabalho em docs/development, e continue a fila PRÓXIMO PASSO. Ao final commite."
    local t0 run_rc=0
    t0=$(date +%s)
    "$OPENCODE" run --session "$session" --dir "$REPO_DIR" --attach "$SERVER" --auto "$prompt" >> "$LOG" 2>&1 \
        || run_rc=$?
    [ "$run_rc" -eq 0 ] || echo "$(date -Is) tick #$issue INJEÇÃO FALHOU (rc=$run_rc)" >> "$LOG"
    "$GATE" record issue-watcher --session "$session" --rc "$run_rc" --duration $(( $(date +%s) - t0 )) || true
    # só avança o 'seen' depois de injetar COM SUCESSO (falha de entrega = re-tenta no próximo tick)
    [ "$run_rc" -eq 0 ] && sed -i "s/^seen=.*/seen=$now/" "$STATE"
    return 0
}

# Prompt legado (SHADOW): varredura completa a cada tick — o comportamento
# ANTIGO, mantido só para comparar com o gate durante o rollout.
legacy_full_sweep_prompt() { # $1 = novidades (texto)
    local news="$1"
    printf '%s' "Varredura completa das issues abertas (NÃO só a #97$( [ -n "$news" ] && printf ' — novidades desde o último tick:%s' "$news")). Passos obrigatórios, NESTA ordem: (1) liste TODAS as issues abertas com 'gh issue list --state open' e leia o corpo de cada uma; (2) leia os comentários novos de cada uma ('gh issue view N --repo $GH_REPO --json comments') e responda tecnicamente na issue quando procedente; (3) TRIAGEM: para cada issue aberta decida — corrigir agora (se está na sua lane e sem dono EM CURSO no DOING.md), registrar gap/plano em docs/development (se é decisão de design — regra 6 do AGENTS.md), ou declarar NÃO-procedente com motivo técnico; (4) CORRIJA o que for da sua lane (compile + teste + commit, regra do DOING.md no mesmo commit); (5) FECHE a issue com 'gh issue close' (ou peça review do parceiro/dono quando a frente é de outra lane, ex. S-6/ViniAguiar1, §149/bugfix-101) somente após a prova existir; (6) atualize DOING.md (PRÓXIMO PASSO) e o plano afetado em docs/development. NUNCA: tocar frente com dono EM CURSO de outra lane; fechar issue sem prova (teste verde/suíte). Ao final commite e pushe."
}

# Prompt FOCALIZADO (ACTIVE): só os eventos externos detectados pelo gate.
focused_prompt() { # $1 = eventos (uma linha por evento)
    local ev
    ev=$(printf '%s\n' "$1" | sed -e 's/^new_issue #\([0-9]*\)$/issue #\1 criada/' \
        -e 's/^edited_issue #\([0-9]*\)$/issue #\1 com título\/corpo editado/' \
        -e 's/^external_comment #\([0-9]*\) id=\([0-9]*\)$/issue #\1 com comentário externo id=\2/' \
        | paste -sd';' - | sed 's/;/; /g')
    printf '%s' "Eventos externos detectados desde o último snapshot: $ev. Leia SOMENTE os eventos listados primeiro ('gh issue view N --repo $GH_REPO --json title,body,comments'); depois consulte o DOING.md para detectar impacto na lane e amplie a busca só se houver relação técnica comprovada. Responda tecnicamente na issue quando procedente. TRIAGEM por issue: corrigir agora (na sua lane e sem dono EM CURSO no DOING.md), registrar gap/plano em docs/development (decisão de design — regra 6 do AGENTS.md) ou declarar NÃO-procedente com motivo técnico. Feche issue SOMENTE com prova (teste verde/suíte; scripts/agent-close-issue.sh quando disponível). NUNCA toque frente com dono EM CURSO de outra lane. Ao final commite e pushe."
}

tick_all() {
    local gmode="${gate_mode:-shadow}" gout grc=0 prompt t0 run_rc=0 i now new_seen="" news="" old
    if ! curl -s -o /dev/null -m 5 "$SERVER/global/health"; then
        echo "$(date -Is) tick all: servidor $SERVER fora do ar — não injeta" >> "$LOG"
        "$GATE" note issue-watcher --session "$session" --decision skip --reason server_down || true
        return 0
    fi

    if [ "$gmode" = "shadow" ]; then
        # SHADOW: o gate calcula e registra o que FARIA (legacy_would_call/new_gate),
        # mas a varredura completa antiga continua acontecendo.
        gout=$("$GATE" decide issue-watcher --session "$session" --shadow) || true
        for i in $(open_issues); do
            now=$(latest_comment_id "$i")
            [ -n "$now" ] || continue
            new_seen="$new_seen $i=$now"
            old=$(printf '%s' "${seen:-}" | tr ' ' '\n' | { grep "^$i=" || true; } | cut -d= -f2)
            old="${old:-0}"
            if [ "$now" -gt "$old" ] 2>/dev/null; then news="$news #$i($old->$now)"; fi
        done
        new_seen=$(printf '%s' "$new_seen" | tr -s ' ' | sed 's/^ //')
        echo "$(date -Is) tick all [shadow; gate: $gout]: injeta a varredura completa -> sessão $session" >> "$LOG"
        prompt=$(legacy_full_sweep_prompt "$news")
    else
        gout=$("$GATE" decide issue-watcher --session "$session") || grc=$?
        if [ "$grc" -ne 0 ]; then
            echo "$(date -Is) tick all: gate rc=$grc ($gout) — sem chamada ao modelo" >> "$LOG"
            return 0
        fi
        echo "$(date -Is) tick all: gate DISPATCH ($gout) -> sessão $session" >> "$LOG"
        prompt=$(focused_prompt "$("$GATE" events issue-watcher --session "$session")")
    fi

    t0=$(date +%s)
    "$OPENCODE" run --session "$session" --dir "$REPO_DIR" --attach "$SERVER" --auto "$prompt" >> "$LOG" 2>&1 \
        || run_rc=$?
    [ "$run_rc" -eq 0 ] || echo "$(date -Is) tick all INJEÇÃO FALHOU (rc=$run_rc)" >> "$LOG"
    # o gate só avança o snapshot após rc=0 (falha de entrega = re-tenta no próximo tick)
    "$GATE" record issue-watcher --session "$session" --rc "$run_rc" --duration $(( $(date +%s) - t0 )) || true
    if [ "$gmode" = "shadow" ] && [ "$run_rc" -eq 0 ] && [ -n "$new_seen" ]; then
        sed -i "s/^seen=.*/seen=\"$new_seen\"/" "$STATE"
    fi
    return 0
}

case "${1:-}" in
    start)  shift; cmd_start "${1:-}" "${2:-}" "${3:-}";;
    stop)   cmd_stop;;
    status) cmd_status;;
    tick)   cmd_tick;;
    set-mode)
        m="${2:-}"
        case "$m" in active|shadow) ;; *) echo "uso: $0 set-mode {active|shadow}" >&2; exit 1;; esac
        [ -f "$STATE" ] || { echo "sem state em $STATE (rode start)" >&2; exit 1; }
        if grep -q '^gate_mode=' "$STATE"; then sed -i "s/^gate_mode=.*/gate_mode=$m/" "$STATE"
        else echo "gate_mode=$m" >> "$STATE"; fi
        echo "gate_mode=$m";;
    stats)  shift; exec "$GATE" stats "$@";;
    *)      echo "uso: $0 {start [issue] [min] [sessionID]|stop|status|tick|set-mode {active|shadow}|stats [--since 24h]}" >&2; exit 1;;
esac
