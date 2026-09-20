#!/usr/bin/env bash
#
# agent-state-fingerprint.sh — representação ESTÁVEL do estado que justifica um
# re-despacho ao agente. Custo de modelo = 0 (git, hash, `gh api`).
#
# Uso:
#   agent-state-fingerprint.sh auto-loop     --repo <dir>
#   agent-state-fingerprint.sh issue-watcher
#
# Saída (stdout): as linhas de COMPONENTES seguidas de `fingerprint=<sha256>`.
# Os componentes deixam o gate dizer POR QUE mudou (head_changed, new_issue…).
# Nunca entra timestamp volátil.
#
# Exit: 0 ok · 2 uso · 20 falha TRANSITÓRIA da fonte (git/GitHub) — o chamador
# NÃO pode tratar isso como "estado estável".
set -uo pipefail
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
# shellcheck source=agent-common.sh
. "$HERE/agent-common.sh"

mode="${1:-}"; shift || true
repo=""
while [ $# -gt 0 ]; do
    case "$1" in
        --repo) repo="${2:-}"; shift 2;;
        *) echo "argumento desconhecido: $1" >&2; exit 2;;
    esac
done

emit() { # linhas de componente (stdin) -> componentes + fingerprint
    local body; body="$(cat)"
    printf '%s\n' "$body"
    printf 'fingerprint=%s\n' "$(printf '%s\n' "$body" | sha)"
}

fp_auto_loop() {
    [ -n "$repo" ] || { echo "auto-loop exige --repo" >&2; exit 2; }
    local head ci=na runs
    head="$(git -C "$repo" rev-parse HEAD 2>/dev/null)" || { echo "git falhou em $repo" >&2; exit 20; }
    if [ "${AGENT_FP_CI:-1}" != "0" ] && command -v gh >/dev/null 2>&1; then
        # estado da CI do HEAD, sem LLM; indisponível = "na" (não derruba o gate)
        runs="$(timeout 15 gh run list --repo "$GH_REPO" --commit "$head" \
                --json name,status,conclusion \
                --jq '.[] | "\(.name):\(.status):\(.conclusion)"' 2>/dev/null | sort || true)"
        [ -n "$runs" ] && ci="$(printf '%s\n' "$runs" | sha | cut -c1-16)"
    fi
    {
        echo "head=$head"
        echo "doing=$(hash_file "$repo/DOING.md")"
        echo "known_bugs=$(hash_file "$repo/docs/bugs-and-gaps/known-bugs.md")"
        echo "docs_dev=$(git -C "$repo" ls-files docs/development 2>/dev/null | sort | sha)"
        echo "worktree=$(git -C "$repo" status --porcelain 2>/dev/null | sha)"
        echo "ci=$ci"
    } | emit
}

fp_issue_watcher() {
    local issues cm lines="" n title body ext cid login
    issues="$(gh api --paginate "repos/$GH_REPO/issues?state=open&per_page=100" \
        --jq '.[] | select(.pull_request | not) | [.number, .title, (.body // "")] | @tsv' 2>/dev/null)" \
        || { echo "gh api issues falhou" >&2; exit 20; }
    while IFS=$'\t' read -r n title body; do
        [ -n "$n" ] || continue
        cm="$(gh api --paginate "repos/$GH_REPO/issues/$n/comments?per_page=100" \
            --jq '.[] | [.id, .user.login] | @tsv' 2>/dev/null)" \
            || { echo "gh api comments #$n falhou" >&2; exit 20; }
        ext=0
        while IFS=$'\t' read -r cid login; do
            [ -n "$cid" ] || continue
            is_self_login "$login" && continue          # autoevento do worker
            [ "$cid" -gt "$ext" ] 2>/dev/null && ext="$cid"
        done <<< "$cm"
        lines="${lines}issue:$n t=$(printf '%s' "$title" | sha | cut -c1-16) b=$(printf '%s' "$body" | sha | cut -c1-16) c=$ext"$'\n'
    done <<< "$issues"
    printf '%s' "$lines" | sort -t: -k2,2n | emit
}

case "$mode" in
    auto-loop)     fp_auto_loop;;
    issue-watcher) fp_issue_watcher;;
    *) echo "uso: $0 {auto-loop --repo <dir>|issue-watcher}" >&2; exit 2;;
esac
