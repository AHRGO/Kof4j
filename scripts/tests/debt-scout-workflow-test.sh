#!/usr/bin/env bash
# debt-scout-workflow-test.sh — structural test do workflow
# kof-debt-scout.yml (Wave 1+2, só-descoberta). Prova o invariante mais
# importante deste pouso: NENHUMA permissao de escrita em Issues existe
# em lugar nenhum do arquivo, o único trigger é manual, e a única
# permissão de escrita nova da Wave 2 (security-events, upload de
# SARIF) está justificada no ledger.
set -u
cd "$(git rev-parse --show-toplevel)"

WF=".github/workflows/kof-debt-scout.yml"
rc=0

[ -f "$WF" ] || { echo "FALHOU: $WF nao existe"; exit 1; }

# exclui linhas de comentario (# ...) — o proprio cabecalho do arquivo
# MENCIONA "issues: write" em prosa pra explicar que ele nao existe; um
# grep ingenuo bateria no proprio comentario (achado real ao escrever
# este teste).
if grep -vE '^\s*#' "$WF" | grep -qE 'issues:[[:space:]]*write'; then
    echo "FALHOU: $WF concede issues: write em algum lugar (nao deveria existir na Wave 1/2)"
    rc=1
else
    echo "ok  — nenhuma permissao issues: write em $WF (fora de comentarios)"
fi

if grep -qE '^permissions:' "$WF" && grep -A2 '^permissions:' "$WF" | grep -q 'contents: read'; then
    echo "ok  — permissions de topo declara contents: read"
else
    echo "FALHOU: permissions de topo nao declara contents: read explicitamente"
    rc=1
fi

if grep -q 'workflow_dispatch' "$WF" && ! grep -qE '^\s*(push|schedule):' "$WF"; then
    echo "ok  — unico trigger e workflow_dispatch (sem push/schedule neste pouso)"
else
    echo "FALHOU: trigger inesperado (esperado so workflow_dispatch)"
    rc=1
fi

if grep -qE 'uses:\s*[^@[:space:]]+@[0-9a-f]{40}\b' "$WF"; then
    echo "ok  — pelo menos uma Action referenciada por SHA completo"
else
    echo "FALHOU: nenhuma Action pinada por SHA encontrada"
    rc=1
fi

if grep -qE 'security-events:[[:space:]]*write' "$WF" \
    && grep -q 'upload-sarif' "$WF"; then
    echo "ok  — security-events: write presente E upload-sarif e chamado (Wave 2)"
else
    echo "FALHOU: esperava security-events: write + upload-sarif (Wave 2 nao ligada)"
    rc=1
fi

if grep -qE 'pull-requests:[[:space:]]*write' "$WF"; then
    echo "FALHOU: pull-requests: write inesperado (Wave 2 so precisa de issues: read pro dedup)"
    rc=1
else
    echo "ok  — nenhuma permissao pull-requests: write (dedup e so leitura)"
fi

# history.py so prova origem num clone completo: --history sem fetch-depth: 0
# seria um NOT_CHECKED permanente disfarcado de feature ligada.
if grep -q -- '--history' "$WF" && grep -qE 'fetch-depth:[[:space:]]*0' "$WF"; then
    echo "ok  — --history ligado E checkout com fetch-depth: 0 (historia real)"
else
    echo "FALHOU: --history exige checkout com fetch-depth: 0 no mesmo workflow"
    rc=1
fi

# gates mecanicos ja existentes do repo, aplicados ao arquivo novo tambem —
# SKIP honesto (nao FALHA) quando o gate nao existe nesta branch (ex.: main
# congelada, mais antiga que o hardening P2 da beta-0.5.0 que criou esses
# scripts). Skip != falha silenciosa: fica explicito no output.
if [ -f scripts/check_workflow_pins.sh ]; then
    if ! OUT_PINS="$(bash scripts/check_workflow_pins.sh 2>&1)"; then
        echo "FALHOU: check_workflow_pins.sh (ver detalhe):"; echo "$OUT_PINS"; rc=1
    else
        echo "ok  — check_workflow_pins.sh (repo inteiro, incl. $WF)"
    fi
else
    echo "SKIP — scripts/check_workflow_pins.sh nao existe nesta branch (ambiente sem o gate)"
fi

if [ -f scripts/check_workflow_permissions.py ]; then
    if ! OUT_PERMS="$(python3 scripts/check_workflow_permissions.py 2>&1)"; then
        echo "FALHOU: check_workflow_permissions.py (ver detalhe):"; echo "$OUT_PERMS"; rc=1
    else
        echo "ok  — check_workflow_permissions.py (repo inteiro, incl. $WF)"
    fi
else
    echo "SKIP — scripts/check_workflow_permissions.py nao existe nesta branch (ambiente sem o gate)"
fi

exit $rc
