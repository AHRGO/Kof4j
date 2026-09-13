#!/usr/bin/env bash
# Gate REFACTOR-500: alvo ≤500 linhas/classe, CRÍTICO ≥600 (decisão da
# mantenedora, 13/09).
#
# MODO RATCHET (12/9, §140; tolerância atualizada 13/09): um gate que falha e
# não está no CI é decorativo. A política agora é:
#   - ≤500 linhas = em conformidade;
#   - 500–599 linhas = TOLERADO (dívida viva; o gate AVISA, não falha — o
#     split continua sendo o caminho; dívida travada no baseline nunca pode
#     crescer, pois crescer em direção a 600 é refactor adiado);
#   - ≥600 linhas = CRÍTICO: falha o build, exige refactor/split antes
#     (inclusive arquivo novo — dívida nova ≥600 nunca entra);
#   - PASSA quando a dívida diminui, mas AVISA (o split feito deve ser
#     removido do baseline: ./scripts/check_500.sh --update-baseline).
# Uso: scripts/check_500.sh [--update-baseline]
set -uo pipefail

LIMIT=500    # alvo da regra — acima disto é dívida (warn ratchet)
CRITICAL=600 # decisão 13/09: 500–599 tolerado; 600+ = crítico, refactor obrigatório
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASELINE="$ROOT/scripts/check_500-baseline.txt"

current=$(find "$ROOT"/kof-*/src/main/java -name '*.java' -exec wc -l {} + \
    | awk -v lim="$LIMIT" -v root="$ROOT/" '$1 > lim && $2 != "total" {gsub(root, "", $2); print $1"\t"$2}' \
    | sort -k2)

if [ "${1:-}" = "--update-baseline" ]; then
    printf '%s\n' "$current" > "$BASELINE"
    echo "check_500: baseline atualizado ($(printf '%s\n' "$current" | grep -c . || true) dívidas)."
    exit 0
fi

if [ ! -f "$BASELINE" ]; then
    echo "check_500: FALHOU — baseline de dívida ausente ($BASELINE)."
    exit 1
fi

fail=0
note=""
while IFS=$'\t' read -r count file; do
    [ -z "$file" ] && continue
    base=$(awk -F'\t' -v f="$file" '$2==f {print $1}' "$BASELINE")
    if [ "$count" -ge "$CRITICAL" ]; then
        echo "check_500: CRÍTICO — $file tem $count linhas (>= $CRITICAL): refactor/split OBRIGATÓRIO."
        fail=1
    elif [ -z "$base" ]; then
        echo "check_500: díVIDA TOLERADA (nova) — $file tem $count linhas (limite $LIMIT; tolerado até $((CRITICAL-1)), crítico em $CRITICAL); não está no baseline."
    elif [ "$count" -gt "$base" ]; then
        echo "check_500: CRÍTICO-crescente — $file tinha $base no baseline, agora $count (dívida travada nunca cresce)."
        fail=1
    elif [ "$count" -lt "$base" ]; then
        note="$note\n    $file: $base -> $count (remova do baseline com --update-baseline)"
    fi
done <<< "$current"

while IFS=$'\t' read -r count file; do
    [ -z "$file" ] && continue
    if ! grep -qF "	$file" <<< "$current"; then
        note="$note\n    $file: $count -> 0 (split concluído! remova do baseline)"
    fi
done < <(grep -v '^#' "$BASELINE")

if [ "$fail" -ne 0 ]; then
    echo "check_500: FALHOU — classe crítica (>= $CRITICAL) ou dívida crescente acima do baseline."
    exit 1
fi

if [ -n "$note" ]; then
    echo "check_500: OK (dívida diminuiu — atualize o baseline):"
    printf "$note\n"
else
    echo "check_500: OK — nenhuma classe crítica (>= $CRITICAL) nem dívida crescente acima do baseline."
fi
