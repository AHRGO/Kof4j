#!/usr/bin/env bash
# debt-scout-workflow-test.sh — structural test do workflow
# kof-debt-scout.yml (Wave 1, só-descoberta). Prova o invariante mais
# importante deste pouso: NENHUMA permissao de escrita em Issues existe
# em lugar nenhum do arquivo, e o unico trigger e manual.
set -u
cd "$(git rev-parse --show-toplevel)"

WF=".github/workflows/kof-debt-scout.yml"
rc=0

[ -f "$WF" ] || { echo "FALHOU: $WF nao existe"; exit 1; }

if grep -qE 'issues:[[:space:]]*write' "$WF"; then
    echo "FALHOU: $WF concede issues: write em algum lugar (nao deveria existir na Wave 1)"
    rc=1
else
    echo "ok  — nenhuma permissao issues: write em $WF"
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

# gates mecanicos ja existentes do repo, aplicados ao arquivo novo tambem
if ! bash scripts/check_workflow_pins.sh >/tmp/wf-pins.$$ 2>&1; then
    echo "FALHOU: check_workflow_pins.sh (ver detalhe):"; cat /tmp/wf-pins.$$; rc=1
else
    echo "ok  — check_workflow_pins.sh (repo inteiro, incl. $WF)"
fi
rm -f /tmp/wf-pins.$$

if ! python3 scripts/check_workflow_permissions.py >/tmp/wf-perms.$$ 2>&1; then
    echo "FALHOU: check_workflow_permissions.py (ver detalhe):"; cat /tmp/wf-perms.$$; rc=1
else
    echo "ok  — check_workflow_permissions.py (repo inteiro, incl. $WF)"
fi
rm -f /tmp/wf-perms.$$

exit $rc
