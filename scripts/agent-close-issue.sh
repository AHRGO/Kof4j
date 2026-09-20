#!/usr/bin/env bash
#
# agent-close-issue.sh — ÚNICO caminho oficial para o worker FECHAR issue.
# Não confia em instrução textual do prompt: valida a prova e só então chama
# `gh issue close`.
#
# Uso:
#   agent-close-issue.sh <issue> --run-id ID [--repo DIR] [--dry-run]
#
# Bloqueia (exit 1, NÃO fecha, explica qual prova falta) quando:
#   1. a evidência não existe ou é de outra issue;
#   2. o manifesto é STALE/DIRTY/sem testes/NOT_RUN/FAIL (agent-evidence validate);
#   3. o verifier determinístico não é PASS para ESTE SHA;
#   4. risk=HIGH sem verifier INDEPENDENTE PASS (verdict.json, mesmo SHA, sessão do
#      verifier diferente da do worker — nunca a mesma conversa);
#   5. a classificação é design/ambiguidade (DESIGN REQUEST, CONTRACT AMBIGUITY,
#      CONTRACT CONFLICT): isso é decisão da mantenedora, não bugfix;
#   6. o SHA do fix não está no remoto (issue não fecha com fix só local);
#   7. a identidade GitHub ativa não é a do worker (kof-agent-worker[bot]); um
#      login humano só passa com AGENT_CLOSE_ALLOW_LOGIN explícito.
set -uo pipefail
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
# shellcheck source=agent-common.sh
. "$HERE/agent-common.sh"

issue="${1:-}"; shift || true
run_id=""; repo="."; dry=0
while [ $# -gt 0 ]; do
    case "$1" in
        --run-id) run_id="${2:-}"; shift 2;;
        --repo)   repo="${2:-.}"; shift 2;;
        --dry-run) dry=1; shift;;
        *) echo "argumento desconhecido: $1" >&2; exit 2;;
    esac
done
case "$issue" in ''|*[!0-9]*) echo "uso: $0 <issue> --run-id ID [--repo D] [--dry-run]" >&2; exit 2;; esac
D="$AGENT_STATE_ROOT/verifier/$run_id"
EJ="$D/evidence.json"

blocks=()
block() { blocks+=("$1"); }
jget() { python3 -c "import json,sys
try:
    d=json.load(open(sys.argv[1]))
    v=eval(sys.argv[2])
    print('' if v is None else v)
except Exception:
    print('')" "$1" "$2"; }

if [ -z "$run_id" ] || [ ! -f "$EJ" ]; then
    block "SEM_EVIDENCIA: run-id '$run_id' inexistente — rode agent-evidence.sh init/run antes"
else
    ev_issue="$(jget "$EJ" "d['issue']")"
    head="$(jget "$EJ" "d['head_sha']")"
    branch="$(jget "$EJ" "d['branch']")"
    classification="$(jget "$EJ" "d['classification']")"
    worker_session="$(jget "$EJ" "d['worker_session']")"
    ev_risk="$(jget "$EJ" "d['risk']")"
    [ "$ev_issue" = "$issue" ] || block "ISSUE_DIFERENTE: a evidência é da issue #$ev_issue, não da #$issue"

    # 2) manifesto
    VAL="$(bash "$HERE/agent-evidence.sh" validate --repo "$repo" --run-id "$run_id" 2>&1)" \
        || block "MANIFESTO_INVALIDO: $(printf '%s' "$VAL" | tr '\n' ';')"

    # 3) verifier determinístico para ESTE sha
    DJ="$D/deterministic.json"
    if [ ! -f "$DJ" ]; then block "SEM_VERIFIER_DETERMINISTICO: rode agent-verify.sh deterministic --run-id $run_id"
    else
        [ "$(jget "$DJ" "d['verdict']")" = "PASS" ] || block "VERIFIER_DETERMINISTICO_NAO_PASS: $(jget "$DJ" "d['verdict']")"
        [ "$(jget "$DJ" "d['sha']")" = "$head" ] || block "VERIFIER_DETERMINISTICO_STALE: sha $(jget "$DJ" "d['sha']" | cut -c1-12) != $(printf '%s' "$head" | cut -c1-12)"
        r="$(jget "$DJ" "d['risk']")"
        case "$r" in high) ev_risk=high;; medium) [ "$ev_risk" = low ] && ev_risk=medium;; esac
    fi

    # 4) HIGH exige verifier independente
    if [ "$ev_risk" = "high" ]; then
        VJ="$D/verdict.json"
        if [ ! -f "$VJ" ]; then block "SEM_VERIFIER_INDEPENDENTE: risk=HIGH exige verdict.json do verifier independente"
        else
            [ "$(jget "$VJ" "d['verdict']")" = "PASS" ] || block "VERIFIER_INDEPENDENTE_NAO_PASS: $(jget "$VJ" "d['verdict']")"
            [ "$(jget "$VJ" "d['sha']")" = "$head" ] || block "VERIFIER_INDEPENDENTE_STALE: verificou outro SHA"
            vs="$(jget "$VJ" "d['verifier_session']")"
            if [ -z "$vs" ]; then block "VERIFIER_SEM_SESSAO: verdict.json sem verifier_session (independência não comprovada)"
            elif [ "$vs" = "$worker_session" ]; then block "VERIFIER_NAO_INDEPENDENTE: mesma sessão do worker ($vs)"; fi
        fi
    fi

    # 5) design/ambiguidade não vira "fixed"
    if printf '%s' "$classification" | grep -qiE 'DESIGN REQUEST|CONTRACT AMBIGUITY|CONTRACT CONFLICT'; then
        block "DECISAO_PENDENTE: classificação '$classification' é decisão da mantenedora (regra 6), não bugfix"
    fi

    # 6) o fix precisa estar no remoto
    if ! git -C "$repo" merge-base --is-ancestor "$head" "origin/$branch" 2>/dev/null; then
        block "FIX_NAO_PUSHADO: $(printf '%s' "$head" | cut -c1-12) não está em origin/$branch"
    fi
fi

# 7) identidade GitHub ativa
identity_cmd="${AGENT_IDENTITY_CMD:-bash $HERE/gh-as-agent.sh whoami}"
who="$(bash -c "$identity_cmd" 2>/dev/null | tail -n1 | tr -d '[:space:]')"
if [ -z "$who" ]; then block "IDENTIDADE_INDISPONIVEL: não foi possível confirmar a identidade GitHub ativa"
elif is_self_login "$who"; then :
elif [ -n "${AGENT_CLOSE_ALLOW_LOGIN:-}" ] && case ",$AGENT_CLOSE_ALLOW_LOGIN," in *",$who,"*) true;; *) false;; esac; then :
else block "IDENTIDADE_NAO_E_DO_WORKER: '$who' (esperado kof-agent-worker[bot]; humano só com AGENT_CLOSE_ALLOW_LOGIN explícito)"; fi

if [ "${#blocks[@]}" -gt 0 ]; then
    echo "BLOCKED: issue #$issue NÃO foi fechada — prova ausente:"
    printf '  - %s\n' "${blocks[@]}"
    exit 1
fi

comment="Corrigido em \`${head:0:12}\` (branch \`$branch\`). Evidência verificada mecanicamente (run \`$run_id\`): risco **$ev_risk**, verifier determinístico PASS para este SHA$( [ "$ev_risk" = high ] && printf ', verifier independente PASS (sessão diferente da do worker)' ). Fechamento pelo caminho oficial scripts/agent-close-issue.sh."
if [ "$dry" -eq 1 ]; then
    echo "DRY-RUN: fecharia a issue #$issue com: $comment"
    exit 0
fi
gh issue close "$issue" --repo "$GH_REPO" --comment "$comment"
