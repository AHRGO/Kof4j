#!/usr/bin/env bash
# debt-scout-scan-test.sh — structural test do orquestrador scan.py
# (Wave 1+2). O selftest cobre um diretório não-git isolado (degrada
# sem crash), um config inválido (recusa escanear) e uma varredura AO
# VIVO do repo real, incluindo o pipeline de clustering/qualificação.
# Este teste tambem exercita o CLI ponta-a-ponta (--phase state, --out,
# --sarif-out, --inbox-out) sem deixar sujeira.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/scan.py --selftest 2>&1)"; then
    echo "FALHOU: scan --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do scan.py verdes (temp dir isolado + config invalido"
    echo "      recusado + fixture plantada + varredura ao vivo do repo real)"
fi

STATE_JSON="$(python3 scripts/debt-scout/scan.py --phase state)"
# NAO assume "sem drift" (essa e' uma propriedade do estado atual da
# branch, nao um invariante do codigo — `main` tem drift real agora,
# `beta-0.5.0` nao). O invariante e' que a resolucao acontece sem crash.
if ! echo "$STATE_JSON" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['default_branch']
assert d['declared_active_branch_exists'] is not None
" 2>/dev/null; then
    echo "FALHOU: --phase state nao resolveu o estado real do repo"
    rc=1
else
    EXISTS="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['declared_active_branch_exists'])" "$STATE_JSON")"
    echo "ok  — --phase state: resolvido (declared_active_branch_exists=$EXISTS)"
fi

OUT_FILE=".debt-scout/out/candidates-test.json"
SARIF_FILE=".debt-scout/out/results-test.sarif"
INBOX_FILE=".debt-scout/out/inbox-test.md"
rm -rf .debt-scout
python3 scripts/debt-scout/scan.py --out "$OUT_FILE" --sarif-out "$SARIF_FILE" --inbox-out "$INBOX_FILE" >/dev/null
if [ ! -f "$OUT_FILE" ] || [ ! -f "$SARIF_FILE" ] || [ ! -f "$INBOX_FILE" ]; then
    echo "FALHOU: --out/--sarif-out/--inbox-out nao escreveram os 3 arquivos"
    rc=1
else
    if ! python3 -c "
import json
with open('$OUT_FILE', encoding='utf-8') as f:
    d = json.load(f)
assert d['summary']['issues_opened'] == 0
assert all(c['publication']['eligible'] is False for c in d['candidates'])
assert sum(cl['member_count'] for cl in d['clusters']) == d['summary']['total_candidates']
with open('$SARIF_FILE', encoding='utf-8') as f:
    sarif_doc = json.load(f)
assert sarif_doc['version'] == '2.1.0'
" 2>/dev/null; then
        echo "FALHOU: os arquivos escritos tem forma inesperada"
        rc=1
    else
        N="$(python3 -c "
import json
print(json.load(open('$OUT_FILE', encoding='utf-8'))['summary']['total_candidates'])
")"
        echo "ok  — --out/--sarif-out/--inbox-out escrevem relatorios validos ($N candidatos, issues_opened=0, SARIF 2.1.0)"
    fi
fi
rm -rf .debt-scout

exit $rc
