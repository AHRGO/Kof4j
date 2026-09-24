#!/usr/bin/env bash
# debt-scout-kof-first-test.sh — structural test do context builder
# KOF-first (Wave 2). Prova que ele le o DECISIONS.md real e encontra
# a decisao de branch certa pra um cluster de drift real, e que
# check_duplicates nunca finge "sem duplicata" quando a checagem falha.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/kof_first.py --selftest 2>&1)"; then
    echo "FALHOU: kof_first --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do kof_first.py verdes (inclui parsing real de DECISIONS.md)"
fi

# pipeline real: branch_discovery -> cluster -> kof_first no repo de verdade
if ! python3 -c "
import json, sys
sys.path.insert(0, 'scripts/debt-scout')
import branch_discovery, cluster, kof_first
state = branch_discovery.discover('.')
candidates = state['candidates']
clusters = cluster.cluster_candidates(candidates) if candidates else []
decisions = kof_first._read_decisions_md('.')
for cl in clusters:
    cl['kof_triage'] = kof_first.build_context(cl, decisions)
    assert cl['kof_triage']['classification'] in (
        'GAP REAL', 'N/A-PROCESS', 'UNKNOWN', 'BUG REAL', 'TARGET DIVERGENCE',
        'DESIGN REQUEST', 'NOT-VALID', 'CONTRACT CONFLICT', 'CONTRACT AMBIGUITY')
print(f'{len(clusters)} branch-drift cluster(s) on this checkout, all kof_triage-valid')
" 2>&1; then
    echo "FALHOU: pipeline branch_discovery -> cluster -> kof_first quebrou"
    rc=1
else
    echo "ok  — pipeline branch_discovery -> cluster -> kof_first no repo real"
fi

exit $rc
