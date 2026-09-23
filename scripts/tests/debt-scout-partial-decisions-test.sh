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

if ! OUT="$(python3 scripts/debt-scout/detectors/decision_evidence.py --selftest 2>&1)"; then
    echo "FALHOU: decision_evidence --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do decision_evidence verdes (PARTIAL_PROVED/STALE_STATE/UNPROVED)"
fi

# invariantes da qualificacao no repo real (o estado de cada decisao muda com
# o tempo; o que nao pode mudar e a coerencia entre kind, prova e saida)
if ! python3 scripts/debt-scout/detectors/partial_decisions.py | python3 -c "
import json, os, sys
for c in json.load(sys.stdin):
    q = c['qualification']
    assert q['kind'] in ('PARTIAL_PROVED', 'STALE_STATE', 'UNPROVED'), q['kind']
    if q['kind'] == 'PARTIAL_PROVED':
        assert q['complete_items'] and q['incomplete_items'], c['contract_ids']
    if q['kind'] == 'STALE_STATE':
        assert q['complete_items'] and not q['incomplete_items'], c['contract_ids']
        assert c['taxonomy']['mechanism'] == 'DOC_CODE_DRIFT'
    if q['kind'] == 'UNPROVED':
        assert q['exit_condition'] is None and 'exit_condition' not in c
    else:
        assert c['exit_condition'] == q['exit_condition']
    assert not q['tracked_gap_refs'] or q['kind'] == 'PARTIAL_PROVED'
    for p in q['implementation_refs'] + q['test_refs']:
        assert os.path.isfile(p), ('implementation ref does not exist', p)
"; then
    echo "FALHOU: qualificacao incoerente no repo real"; rc=1
else
    echo "ok  — qualificacao coerente no repo real (toda ref de implementacao existe no disco)"
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
