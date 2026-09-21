#!/usr/bin/env bash
#
# check_live_records.sh — integridade dos registros vivos da lane (docs/development):
#   A) contagem viva nos READMEs == classificador canonico (autoridade e o script,
#      nunca a prosa: numero cravado em dois lugares e promessa de drift);
#   B) paridade EN<->PT dos IDs de decisao em DECISIONS.md (o registro de governanca
#      nao pode ter secao so num idioma) e zero heading duplicado no mesmo arquivo.
#
# A classe (A) ja driftou duas vezes em 21/09; a classe (B) apareceu quando a lane
# irma adicionou 3 decisoes so no EN e um merge deixou um heading orfao + duplicado
# no PT (achados 21/09 e corrigidos na mesma rodada).
#
# AUSENCIA e FALHA, nao passe livre: se a prosa/regex mudar de forma, este gate tem
# de ser atualizado JUNTO (regra anti-neutering da lane).
#
# Uso: scripts/check_live_records.sh            # rc!=0 em drift
#      scripts/check_live_records.sh --selftest # casos bons e ruins plantados
# Env (teste): LR_EN, LR_PT, LR_COUNT, DEC_EN, DEC_PT
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

EN="${LR_EN:-docs/development/README.md}"
PT="${LR_PT:-docs/development/README.pt_BR.md}"
DEN="${DEC_EN:-docs/development/DECISIONS.md}"
DPT="${DEC_PT:-docs/development/DECISIONS.pt_BR.md}"

if [ "${LR_COUNT:-}" != "" ]; then
    COUNT="$LR_COUNT"
else
    COUNT="$(bash scripts/check_known_bugs_status.sh \
        | sed -nE 's/^EN open[^(]*\(([0-9]+)\).*/\1/p' | head -1)"
fi

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mk() { printf '%s\n' "$2" > "$T/$1"; }
    # A) contagem: bom, errado, ausente
    mk r_en.md 'x **19 items in the open queue** y'; mk r_pt.md 'x **19 itens na fila aberta** y'
    mk d_en.md '## D-PROPERTY — x';               mk d_pt.md '## D-PROPERTY — x'
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: caso bom devia passar"; exit 1; fi
    mk r_en.md 'x **18 items in the open queue** y'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: contagem errada passou"; exit 1; fi
    mk r_en.md 'sem contagem aqui'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: ausencia de contagem passou (neutering)"; exit 1; fi
    # B) paridade/id duplicado
    mk r_en.md 'x **19 items in the open queue** y'
    mk d_en.md '## D-ONLY-EN — x'; mk d_pt.md '## D-OUTRA — x'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: decisao so no EN passou"; exit 1; fi
    mk d_en.md '## D-DUP — a
## D-DUP — a'; mk d_pt.md '## D-DUP — a'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: heading duplicado passou"; exit 1; fi
    echo "SELFTEST OK: contagem (ok/errada/ausente) + paridade + duplicata"
    exit 0
fi

[ -n "${COUNT:-}" ] || { echo "FALHA: nao extrai a contagem da autoridade (formato mudou?)"; exit 1; }

python3 - "$EN" "$PT" "$COUNT" "$DEN" "$DPT" << 'PYEOF'
import re, sys
en, pt, count, den, dpt = sys.argv[1:6]
bad = 0

# ---- A) contagem viva x autoridade -----------------------------------------
pat = re.compile(r"\*\*([0-9]+) (?:items?|live|itens?|vivos?)\b[^*]*\*\*")
seen = 0
for path in (en, pt):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
    hits = pat.findall(text)
    if not hits:
        print(f"FALHA: nenhuma declaracao de contagem viva em {path} "
              "(regex nao casa mais — atualize o gate junto com a prosa)")
        bad = 1; continue
    seen += len(hits)
    for n in hits:
        if n != count:
            print(f"DRIFT: {path} declara '{n}' mas a autoridade conta '{count}'"); bad = 1

# ---- B) DECISIONS: paridade EN<->PT + heading duplicado --------------------
IDPAT = re.compile(r"(?m)^#{2,3} (D-[A-Z0-9][A-Z0-9.-]*|R[0-9][A-Z0-9-]*)")
TEMPLATE = {"D-XXXX"}
sets = {}
for lang, path in (("EN", den), ("PT", dpt)):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
    ids = {m for m in IDPAT.findall(text) if m not in TEMPLATE}
    sets[lang] = ids
    heads = re.findall(r"(?m)^## .*$", text)  # so nivel 2: ### Context/Decision repetem de proposito
    dups = sorted({h for h in heads if heads.count(h) > 1})
    for h in dups:
        print(f"DUPLICATA: heading de secao repetido em {path}: {h[:70]}"); bad = 1
if sets.get("EN") is not None and sets.get("PT") is not None:
    only_en = sorted(sets["EN"] - sets["PT"])
    only_pt = sorted(sets["PT"] - sets["EN"])
    for d in only_en:
        print(f"PARIDADE: decisao so no EN (sem espelho PT): {d}"); bad = 1
    for d in only_pt:
        print(f"PARIDADE: decisao so no PT (sem espelho EN): {d}"); bad = 1

if not bad:
    print(f"OK: contagem viva {count} consistente ({seen} declaracoes); "
          f"DECISIONS EN<->PT com {len(sets.get('EN', ()))} IDs em paridade, 0 duplicatas")
sys.exit(1 if bad else 0)
PYEOF
