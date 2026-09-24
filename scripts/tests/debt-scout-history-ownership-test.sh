#!/usr/bin/env bash
# debt-scout-history-ownership-test.sh — structural test de history.py
# (origem via git blame, nunca inventada; clone raso = NOT_CHECKED) e de
# ownership.py (claims ativas do DOING.md; ledger compartilhado nunca e
# unidade de ownership). Ambos os selftests incluem rodada ao vivo no repo.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
for mod in history ownership; do
    if ! OUT="$(python3 "scripts/debt-scout/$mod.py" --selftest 2>&1)"; then
        echo "FALHOU: $mod --selftest:"; echo "$OUT"; rc=1
    else
        echo "ok  — asserções do $mod.py verdes (inclui rodada ao vivo no repo real)"
    fi
done

# CLI: um path nao rastreado nunca vira FOUND (nenhuma origem inventada)
if ! python3 scripts/debt-scout/history.py --path no/such/file.txt --line 1 \
    | python3 -c "
import json, sys
r = json.load(sys.stdin)
assert r['status'] in ('UNKNOWN', 'NOT_CHECKED'), r
assert r['intent'] == 'UNKNOWN'
"; then
    echo "FALHOU: history CLI reportou origem para um path inexistente"; rc=1
else
    echo "ok  — history CLI: path inexistente nunca vira FOUND"
fi

# CLI: o proprio DOING.md (ledger) nunca e 'dono' de nada por path
if ! python3 scripts/debt-scout/ownership.py --symbol no/such/unit.sh \
    | python3 -c "
import json, sys
assert json.load(sys.stdin)['status'] == 'NOT_OWNED'
"; then
    echo "FALHOU: ownership CLI marcou uma unidade inexistente como OWNED"; rc=1
else
    echo "ok  — ownership CLI: unidade que nenhuma claim nomeia e NOT_OWNED"
fi

exit $rc
