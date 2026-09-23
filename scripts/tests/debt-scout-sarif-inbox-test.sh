#!/usr/bin/env bash
# debt-scout-sarif-inbox-test.sh — structural test dos dois destinos de
# publicacao C0-C2 da Wave 2: SARIF (achados com localizacao) e Debt
# Inbox (C2 sem localizacao). Prova que os dois sao mutuamente
# exclusivos (nunca duplicam o mesmo achado nos dois canais) e que o
# SARIF gerado do repo real e um documento JSON valido.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/sarif.py --selftest 2>&1)"; then
    echo "FALHOU: sarif --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do sarif.py verdes"
fi

if ! OUT2="$(python3 scripts/debt-scout/inbox.py --selftest 2>&1)"; then
    echo "FALHOU: inbox --selftest:"; echo "$OUT2"; rc=1
else
    echo "ok  — asserções do inbox.py verdes"
fi

if ! python3 scripts/debt-scout/detectors/satd.py \
    | python3 -c "
import json, sys
sys.path.insert(0, 'scripts/debt-scout')
import sarif
c = json.load(sys.stdin)
doc = sarif.build_sarif(c, analyzed_sha='live')
json.dumps(doc)
print(f'SARIF real gerado: {len(doc[\"runs\"][0][\"results\"])} resultados, '
      f'{len(doc[\"runs\"][0][\"tool\"][\"driver\"][\"rules\"])} regra(s)')
" 2>&1; then
    echo "FALHOU: gerar SARIF do repo real quebrou"
    rc=1
else
    echo "ok  — SARIF do repo real e valido/serializavel"
fi

exit $rc
