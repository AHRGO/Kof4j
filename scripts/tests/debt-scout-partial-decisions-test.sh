#!/usr/bin/env bash
# debt-scout-partial-decisions-test.sh — structural test do detector de
# decisoes parciais (V2 §17). Prova: so PARTIAL/BLOCKED/IN_PROGRESS do CORPO
# de DECISIONS.md viram sinal, sempre C1 (decisao parcial nao e divida por
# si so), contrato carregado por construcao, nunca eligible.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/detectors/partial_decisions.py --selftest 2>&1)"; then
    echo "FALHOU: partial_decisions --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do partial_decisions verdes (inclui DECISIONS.md real)"
fi

if ! N="$(python3 scripts/debt-scout/detectors/partial_decisions.py | python3 -c "
import json, sys, re
c = json.load(sys.stdin)
assert all(x['confidence'] == 'C1' for x in c)
assert all(x['publication']['eligible'] is False for x in c)
assert all(len(x['contract_ids']) == 1 and x['contract_ids'][0].startswith('D-') for x in c)
text = open('docs/development/DECISIONS.md', encoding='utf-8').read()
for x in c:
    line = text.splitlines()[x['locations'][0]['line'] - 1]
    assert line.startswith('## ' + x['contract_ids'][0]), line
print(len(c))
")"; then
    echo "FALHOU: CLI do partial_decisions emitiu candidato fora do contrato"; rc=1
else
    echo "ok  — CLI: $N decisoes incompletas, todas C1, cada location aponta pro header real"
fi
exit $rc
