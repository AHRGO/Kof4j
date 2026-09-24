#!/usr/bin/env bash
# debt-scout-confidence-test.sh — structural test do classificador C2/C3
# (Wave 2). Prova que nada promove sem evidencia real e que o pipeline
# completo satd -> cluster -> kof_first -> priority -> confidence roda
# no repo real sem nenhum cluster chegando a C2/C3 (esperado: dedup real
# via rede nao roda no selftest, entao nada satisfaz o requisito).
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/confidence.py --selftest 2>&1)"; then
    echo "FALHOU: confidence --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do confidence.py verdes (inclui o caminho feliz C2->C3)"
fi

if ! python3 -c "
import sys
sys.path.insert(0, 'scripts/debt-scout')
import cluster, kof_first, priority, confidence
from detectors import satd

candidates = satd.scan('.')
clusters = cluster.cluster_candidates(candidates)
decisions = kof_first._read_decisions_md('.')
for cl in clusters:
    cl['kof_triage'] = kof_first.build_context(cl, decisions)
    cl['priority_vector'] = priority.compute_priority_vector(cl)
    confidence.apply_classification(cl)

assert all(cl['confidence'] == 'C0' for cl in clusters), (
    'algum cluster promoveu sem checagem real de duplicata via rede — '
    'nao deveria acontecer no selftest')
print(f'{len(clusters)} clusters processados pelo pipeline completo, todos C0 (esperado sem gh)')
" 2>&1; then
    echo "FALHOU: pipeline completo satd->cluster->kof_first->priority->confidence quebrou"
    rc=1
else
    echo "ok  — pipeline completo no repo real: nenhuma promocao sem dedup checado de verdade"
fi

exit $rc
