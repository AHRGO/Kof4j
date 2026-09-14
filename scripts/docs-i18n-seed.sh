#!/usr/bin/env bash
#
# docs-i18n-seed.sh — cria a frente PT-BR (X.pt_BR.md) de todo doc canonico
# (X.md) que ainda nao tem par, preservando o conteudo original em portugues e
# adicionando o seletor de idioma na primeira linha.
#
# Idempotente: nao sobrescreve um X.pt_BR.md existente. Nao toca em X.md — a
# traducao do canonico para o ingles e feita em seguida (lotes de traducao).
#
# Uso:
#   scripts/docs-i18n-seed.sh [--dry-run] [X.md ...]
#
# Sem caminhos, processa todos os canonicos rastreados. Com caminhos, processa
# apenas os informados (lotes de traducao).
#
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

DRY=0
if [ "${1:-}" = --dry-run ]; then
    DRY=1
    shift
fi

if [ "$#" -gt 0 ]; then
    CANON_LIST="$(printf '%s\n' "$@")"
else
    CANON_LIST="$(git ls-files '*.md' | grep -v -- '\.pt_BR\.md$' || true)"
fi

switcher_for() {
    local canon="$1" base ptbase
    base="$(basename "$canon")"
    ptbase="${base%.md}.pt_BR.md"
    printf '[English](%s) | [Português](%s)' "$base" "$ptbase"
}

created=0
skipped=0
while IFS= read -r canon; do
    [ -n "$canon" ] || continue
    case "$canon" in
        *.pt_BR.md) skipped=$((skipped + 1)); continue ;;
        *.md) ;;
        *) printf 'ignorado (nao .md): %s\n' "$canon" >&2; continue ;;
    esac
    pt="${canon%.md}.pt_BR.md"
    if [ -f "$pt" ]; then
        skipped=$((skipped + 1))
        continue
    fi
    if [ "$DRY" -eq 1 ]; then
        printf 'criaria %s\n' "$pt"
        created=$((created + 1))
        continue
    fi
    { printf '%s\n\n' "$(switcher_for "$canon")"; cat -- "$canon"; } > "$pt"
    created=$((created + 1))
done <<< "$CANON_LIST"

printf 'pares criados: %d | ja existentes: %d\n' "$created" "$skipped"
