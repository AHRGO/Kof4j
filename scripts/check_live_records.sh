#!/usr/bin/env bash
#
# check_live_records.sh — toda declaracao de contagem viva nos READMEs da lane
# (docs/development) tem de bater com o classificador canonico. A autoridade e
# sempre o script, nunca a prosa: numero cravado em dois lugares e promessa de
# drift. A classe ja ocorreu duas vezes so em 21/09 — a frase foi ressincronizada
# 17->18 mas uma linha de tabela ficou velha, e depois 18->19 com a mesma linha
# esquecida de novo (achada pela lane irma, corrigida em `5a80625c`).
#
# AUSENCIA de declaracao e FALHA, nao passe livre: se a prosa mudar e o regex
# deixar de casar, este gate tem de ser atualizado JUNTO (regra anti-neutering
# da lane — um gate que emudece para ficar verde nao guarda nada).
#
# Uso: scripts/check_live_records.sh            # reporta + rc!=0 em drift
#      scripts/check_live_records.sh --selftest # 19 ok, 18 pego, ausencia pega
# Env (para teste): LR_EN, LR_PT, LR_COUNT
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

EN="${LR_EN:-docs/development/README.md}"
PT="${LR_PT:-docs/development/README.pt_BR.md}"

if [ "${LR_COUNT:-}" != "" ]; then
    COUNT="$LR_COUNT"
else
    COUNT="$(bash scripts/check_known_bugs_status.sh \
        | sed -nE 's/^EN open[^(]*\(([0-9]+)\).*/\1/p' | head -1)"
fi

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    printf 'x **19 items in the open queue** y\n' > "$T/en.md"
    printf 'x **19 itens na fila aberta** y\n' > "$T/pt.md"
    if ! LR_EN="$T/en.md" LR_PT="$T/pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: caso bom devia passar"; exit 1
    fi
    printf 'x **18 items in the open queue** y\n' > "$T/en.md"
    if LR_EN="$T/en.md" LR_PT="$T/pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: contagem errada passou"; exit 1
    fi
    printf 'sem contagem nenhuma aqui\n' > "$T/en.md"
    if LR_EN="$T/en.md" LR_PT="$T/pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: ausencia de declaracao passou (neutering)"; exit 1
    fi
    echo "SELFTEST OK: 19 ok, 18 pego, ausencia pega"
    exit 0
fi

[ -n "${COUNT:-}" ] || { echo "FALHA: nao extrai a contagem da autoridade (formato mudou?)"; exit 1; }

python3 - "$EN" "$PT" "$COUNT" << 'PYEOF'
import re, sys
files, count = (sys.argv[1], sys.argv[2]), sys.argv[3]
pat = re.compile(r"\*\*([0-9]+) (?:items?|live|itens?|vivos?)\b[^*]*\*\*")
bad, seen = 0, 0
for path in files:
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
            print(f"DRIFT: {path} declara '{n}' mas a autoridade conta '{count}'")
            bad = 1
if not bad:
    print(f"OK: contagem viva {count} consistente nas {seen} declaracoes ({files[0]}, {files[1]})")
sys.exit(1 if bad else 0)
PYEOF
