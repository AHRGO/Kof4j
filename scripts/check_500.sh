#!/usr/bin/env bash
# Gate REFACTOR-500: nenhuma classe de produção acima de 500 linhas.
#
# MODO RATCHET (12/9, §140): um gate que falha e não está no CI é decorativo.
# A dívida EXISTENTE (17 classes >500 no inventário da Fase 8 + regressões
# posteriores — SemanticAnalyzer 396->535, Parser 456->513) está congelada em
# scripts/check_500-baseline.txt. O gate:
#   - FALHA se um arquivo NOVO passar de 500 (dívida nova é proibida);
#   - FALHA se uma dívida cresçer acima do travado no baseline;
#   - PASSA quando a dívida diminui, mas AVISA (o split feito deve ser
#     removido do baseline: ./scripts/check_500.sh --update-baseline).
# Uso: scripts/check_500.sh [--update-baseline]
set -uo pipefail

LIMIT=500
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
    if [ -z "$base" ]; then
        echo "check_500: NOVA DÍVIDA — $file tem $count linhas (limite $LIMIT); não está no baseline."
        fail=1
    elif [ "$count" -gt "$base" ]; then
        echo "check_500: DÍVIDA CRESCEU — $file tinha $base no baseline, agora $count."
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
    echo "check_500: FALHOU — dívida nova ou crescente acima do baseline."
    exit 1
fi

if [ -n "$note" ]; then
    echo "check_500: OK (dívida diminuiu — atualize o baseline):"
    printf "$note\n"
else
    echo "check_500: OK — nenhuma classe nova/crescente acima de $LIMIT linhas."
fi
