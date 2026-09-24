#!/usr/bin/env bash
# debt-scout-cluster-test.sh — structural test do clustering (Wave 2) e do
# vetor principal/interest/lock-in. Prova: dois achados idênticos se
# fundem, dois diferentes nunca se fundem sem prova de causa comum,
# nenhum candidato se perde, e o vetor nunca vira score único.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/cluster.py --selftest 2>&1)"; then
    echo "FALHOU: cluster --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do cluster.py verdes (inclui varredura ao vivo do repo real)"
fi

if ! OUT2="$(python3 scripts/debt-scout/priority.py --selftest 2>&1)"; then
    echo "FALHOU: priority --selftest:"; echo "$OUT2"; rc=1
else
    echo "ok  — asserções do priority.py verdes (vetor, nunca score único)"
fi

# pipeline real: satd -> cluster -> priority, ponta-a-ponta via stdin/stdout
if ! python3 scripts/debt-scout/detectors/satd.py \
    | python3 scripts/debt-scout/cluster.py \
    | python3 -c "
import json, sys
clusters = json.load(sys.stdin)
sys.path.insert(0, 'scripts/debt-scout')
import priority
for cl in clusters:
    cl['priority_vector'] = priority.compute_priority_vector(cl)
total = sum(cl['member_count'] for cl in clusters)
assert all('priority_vector' in cl for cl in clusters)
assert all('priority' not in cl for cl in clusters)
print(f'{len(clusters)} clusters, {total} membros, todos com priority_vector')
" 2>&1; then
    echo "FALHOU: pipeline satd -> cluster -> priority quebrou"
    rc=1
else
    echo "ok  — pipeline satd -> cluster -> priority ponta-a-ponta no repo real"
fi

exit $rc
