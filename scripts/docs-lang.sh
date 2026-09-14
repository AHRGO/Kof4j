#!/usr/bin/env bash
#
# docs-lang.sh — documentacao bilingue do Kof (EN/PT-BR).
#
# Convencao:
#   X.md          -> canonical, INGLES (padrao do GitHub e de qualquer
#                    maquina cujo idioma NAO seja portugues)
#   X.pt_BR.md    -> portugues
#
# Em uma maquina com locale pt_* (LC_ALL/LC_MESSAGES/LANG/LANGUAGE), o hook
# post-checkout sobrepoe o conteudo de X.md com X.pt_BR.md e marca o caminho
# como skip-worktree (a arvore fica limpa; o commit continua sendo o ingles
# canonico). Em qualquer outro locale, o ingles e restaurado do indice.
#
# Subcomandos:
#   lang                 imprime o idioma detectado (en|pt)
#   install              liga core.hooksPath=.githooks e aplica
#   apply [en|pt|auto]   aplica a sobreposicao do idioma na arvore
#   restore              forca o ingles canonico e limpa skip-worktree
#   status               mostra pares, cobertura e drift
#   check [--strict]     gate: todo doc tem par + switcher; --strict falha
#   list [--missing]     lista docs canonicos (para lotes de traducao)
#   show <X.md>          imprime o conteudo no idioma da maquina
#   drift                lista pares possivelmente desatualizados
#
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

PT_SUFFIX=".pt_BR.md"
EN_SUFFIX=".md"

die() { printf 'docs-lang: %s\n' "$*" >&2; exit 1; }

detect_lang() {
    local loc="${LC_ALL:-}"
    [ -n "$loc" ] || loc="${LC_MESSAGES:-}"
    [ -n "$loc" ] || loc="${LANG:-}"
    case "$loc" in
        pt*) printf 'pt\n' ;;
        *)   printf 'en\n' ;;
    esac
}

# caminhos canonicos (X.md) rastreados que possuem par X.pt_BR.md
pairs() {
    local canon
    while IFS= read -r canon; do
        if [ -f "${canon%$EN_SUFFIX}$PT_SUFFIX" ]; then printf '%s\n' "$canon"; fi
    done < <(all_canon)
    return 0
}

# todos os canonicos rastreados, com ou sem par
all_canon() {
    git ls-files '*.md' | grep -v -- "$PT_SUFFIX"'$' || true
}

all_pt() {
    git ls-files '*'$PT_SUFFIX || true
}

is_clean() {
    git diff --quiet -- "$1" 2>/dev/null && git diff --cached --quiet -- "$1" 2>/dev/null
}

overlay_one() {
    local canon="$1" pt="${1%$EN_SUFFIX}$PT_SUFFIX"
    [ -f "$pt" ] || return 0
    if is_clean "$canon"; then
        cp -- "$pt" "$canon"
        git update-index --skip-worktree -- "$canon" 2>/dev/null || true
    fi
}

is_skip_worktree() {
    git ls-files -v -- "$1" 2>/dev/null | grep -q '^S'
}

restore_one() {
    local canon="$1"
    git ls-files --error-unmatch -- "$canon" >/dev/null 2>&1 || return 0
    # So mexe em arquivos que ESTAO com a sobreposicao PT (skip-worktree);
    # edicoes reais de qualquer agente sao preservadas (regra 8 do DOING).
    is_skip_worktree "$canon" || return 0
    git update-index --no-skip-worktree -- "$canon" 2>/dev/null || true
    git checkout-index -f -- "$canon" 2>/dev/null || true
}

cmd_apply() {
    local want="${1:-auto}"
    [ "$want" = auto ] && want="$(detect_lang)"
    if [ "$want" = pt ]; then
        while IFS= read -r canon; do overlay_one "$canon"; done < <(pairs)
    else
        cmd_restore
    fi
}

cmd_restore() {
    while IFS= read -r canon; do restore_one "$canon"; done < <(all_canon)
}

cmd_status() {
    local lang total with_pt
    lang="$(detect_lang)"
    total="$(all_canon | wc -l | tr -d ' ')"
    with_pt="$(pairs | wc -l | tr -d ' ')"
    printf 'idioma da maquina : %s\n' "$lang"
    printf 'docs canonicos    : %s\n' "$total"
    printf 'com par PT        : %s\n' "$with_pt"
    if [ "$total" -gt 0 ]; then
        printf 'cobertura         : %d%%\n' "$((with_pt * 100 / total))"
    fi
}

switcher_ok() {
    head -3 "$1" 2>/dev/null | grep -q "$PT_SUFFIX"
}

cmd_check() {
    local strict=0
    [ "${1:-}" = --strict ] && strict=1
    local missing=0 nopair_pt=0 noswitcher=0 canon pt
    while IFS= read -r canon; do
        pt="${canon%$EN_SUFFIX}$PT_SUFFIX"
        if [ ! -f "$pt" ]; then
            printf 'SEM PAR   : %s\n' "$canon"
            missing=$((missing + 1))
            continue
        fi
        switcher_ok "$canon" || { printf 'SEM SWITCH: %s\n' "$canon"; noswitcher=$((noswitcher + 1)); }
        switcher_ok "$pt"    || { printf 'SEM SWITCH: %s\n' "$pt";    noswitcher=$((noswitcher + 1)); }
    done < <(all_canon)
    while IFS= read -r pt; do
        canon="${pt%$PT_SUFFIX}$EN_SUFFIX"
        if ! git ls-files --error-unmatch -- "$canon" >/dev/null 2>&1; then
            printf 'ORFAO PT  : %s\n' "$pt"
            nopair_pt=$((nopair_pt + 1))
        fi
    done < <(all_pt)
    printf -- '---\nsem par: %d | sem switcher: %d | pt orfao: %d\n' \
        "$missing" "$noswitcher" "$nopair_pt"
    if [ "$strict" -eq 1 ] && { [ "$missing" -gt 0 ] || [ "$nopair_pt" -gt 0 ] || [ "$noswitcher" -gt 0 ]; }; then
        return 1
    fi
}

cmd_list() {
    if [ "${1:-}" = --missing ]; then
        local canon
        while IFS= read -r canon; do
            if [ ! -f "${canon%$EN_SUFFIX}$PT_SUFFIX" ]; then printf '%s\n' "$canon"; fi
        done < <(all_canon)
    else
        all_canon
    fi
    return 0
}

cmd_show() {
    local canon="$1" lang pt
    [ -n "$canon" ] || die "uso: docs-lang.sh show <X.md>"
    lang="$(detect_lang)"
    pt="${canon%$EN_SUFFIX}$PT_SUFFIX"
    if [ "$lang" = pt ] && [ -f "$pt" ]; then
        cat -- "$pt"
    else
        git show ":$canon" 2>/dev/null || cat -- "$canon"
    fi
}

cmd_drift() {
    # heuristica: par PT mais novo que o canonico (possivel traducao atrasada)
    local canon pt
    while IFS= read -r canon; do
        pt="${canon%$EN_SUFFIX}$PT_SUFFIX"
        if [ "$pt" -nt "$canon" ]; then
            printf 'PT mais novo: %s\n' "$canon"
        fi
    done < <(pairs)
}

cmd_install() {
    git config core.hooksPath .githooks
    chmod +x .githooks/* scripts/docs-lang.sh 2>/dev/null || true
    cmd_apply auto
    printf 'hooks instalados (core.hooksPath=.githooks); idioma=%s\n' "$(detect_lang)"
}

case "${1:-status}" in
    lang)     detect_lang ;;
    install)  cmd_install ;;
    apply)    shift; cmd_apply "${1:-auto}" ;;
    restore)  cmd_restore ;;
    status)   cmd_status ;;
    check)    shift; cmd_check "${1:-}" ;;
    list)     shift; cmd_list "${1:-}" ;;
    show)     shift; cmd_show "${1:-}" ;;
    drift)    cmd_drift ;;
    *)        die "subcomando desconhecido: ${1:-} (use: lang|install|apply|restore|status|check|list|show|drift)" ;;
esac
