#!/usr/bin/env bash
# debt-scout-branch-discovery-test.sh — structural test da descoberta de
# branch/ref do Debt Scout (Wave 1). Prova que o parser do AGENTS.md e a
# checagem de existencia de ref funcionam, e que o repo real nao tem
# contract-drift entre o que o AGENTS.md declara e o que existe.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/branch_discovery.py --selftest 2>&1)"; then
    echo "FALHOU: selftest do branch_discovery:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do branch_discovery verdes (inclui checagem AO VIVO"
    echo "      do repo real: AGENTS.md nao esta em drift contra os refs)"
fi

JSON="$(python3 scripts/debt-scout/branch_discovery.py)"
if ! python3 -c "
import json, sys
d = json.loads(sys.argv[1])
assert d['default_branch'], 'default_branch vazio'
assert d['declared_active_branch'], 'AGENTS.md nao declarou branch ativa'
# NAO hardcoda o NOME da branch (muda a cada corte de release, D-BRANCH-*):
# o invariante e que a branch declarada EXISTE e portanto 0 candidatos.
assert d['declared_active_branch_exists'] is True, d
assert d['candidates'] == [], d['candidates']
" "$JSON" 2>/dev/null; then
    echo "FALHOU: CLI sem --selftest acusa drift real entre AGENTS.md e os refs:"
    echo "$JSON"
    rc=1
else
    echo "ok  — CLI sem --selftest: $JSON" | tr -d '\n'; echo
fi

exit $rc
