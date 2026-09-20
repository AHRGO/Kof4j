#!/usr/bin/env bash
# agent-common.sh — helpers COMPARTILHADOS da automação de agentes (Onda 1).
# Só é `source`ado (nunca executado): diretório de estado, hash, JSON de
# telemetria e a lista de logins do próprio worker.
#
# Estado e telemetria vivem FORA do repositório e fora de /tmp (a regra 9 do
# AGENTS.md: /tmp evapora com queda de energia): ~/.local/state/kof-agent/.

AGENT_STATE_ROOT="${KOF_AGENT_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/kof-agent}"
GH_REPO="${GH_REPO:-KofLang/Kof4j}"

# Logins do PRÓPRIO worker — comentários deles não são evento externo. NÃO inclui
# `melmonfre` nem qualquer humano: mantenedor comentando é sempre evento.
AGENT_SELF_LOGINS="${AGENT_SELF_LOGINS:-kof-agent-worker[bot],Kof-agent-worker,kof-agent-worker}"

is_self_login() {
    case ",$AGENT_SELF_LOGINS," in *",$1,"*) return 0;; esac
    return 1
}

# sha256 do stdin (Linux: sha256sum; macOS: shasum -a 256)
sha() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum | cut -d' ' -f1
    else shasum -a 256 | cut -d' ' -f1; fi
}

hash_file() { # $1=arquivo -> sha do conteúdo ou "absent"
    if [ -f "$1" ]; then sha < "$1"; else echo absent; fi
}

json_str() { # escapa para string JSON (controle -> espaço)
    printf '%s' "$1" | tr '\n\t\r' '   ' | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

# tel k v [k v ...] — 1 linha JSON em dispatch.jsonl. Valor iniciado por `@` é
# literal JSON cru (true/false/número); o resto vira string.
tel() {
    local out k v
    out="{\"ts\":\"$(date -Is)\""
    while [ $# -ge 2 ]; do
        k="$1"; v="$2"; shift 2
        case "$v" in
            @*) out="$out,\"$k\":${v#@}";;
            *)  out="$out,\"$k\":\"$(json_str "$v")\"";;
        esac
    done
    mkdir -p "$AGENT_STATE_ROOT"
    printf '%s}\n' "$out" >> "$AGENT_STATE_ROOT/dispatch.jsonl"
}
