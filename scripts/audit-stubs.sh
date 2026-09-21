#!/usr/bin/env bash
# audit-stubs.sh — inventário REPRODUTÍVEL de incompletude NÃO documentada
# (frente de revisão, sessão 9094). Não altera nada: só lê e imprime.
#
# Uso: scripts/audit-stubs.sh [raiz-do-repo]
#
# CUIDADO DE RUÍDO (medido 21/09): os comentários do Kof são em português, onde
# "todo" = "todos" (every) e "stub honesto" = recusa deliberada R6 (documentada
# no próprio código). Portanto um match aqui é CANDIDATO, nunca achado: a
# triagem é manual e compara com docs/bugs-and-gaps/* antes de catalogar.
# Ver docs/bugs-and-gaps/uncatalogued-stubs-audit.md (frente de revisão).
set -uo pipefail

ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT"

mapfile -t ROOTS < <(ls -d */src/main 2>/dev/null || true)
if [ "${#ROOTS[@]}" -eq 0 ]; then
    echo "audit-stubs: nenhum */src/main em $ROOT" >&2
    exit 1
fi

hdr() { printf '\n## %s\n' "$1"; }
n()   { grep -rnE "$1" "${ROOTS[@]}" --include=*.java 2>/dev/null || true; }

printf '# audit-stubs — %s\n' "$(date -Iseconds)"
printf 'raiz: %s\nroots: %s\n' "$ROOT" "${ROOTS[*]}"

hdr "1. TODO/FIXME/XXX/HACK (case-sensitive, .java) — candidatos, triar à mão"
n '\b(TODO|FIXME|XXX|HACK)\b'

hdr "2. UnsupportedOperationException (.java) — distinguir R6 honesto de stub real"
n 'UnsupportedOperationException'

hdr "3. catch vazio (.java) — possível swallow silencioso (R6)"
n 'catch \([^)]*\)[[:space:]]*\{[[:space:]]*\}'

hdr "4. 'not implemented'/'unimplemented' (.java)"
n 'not[ _]?implemented|unimplemented'

hdr "5. testes desabilitados (@Disabled/@Ignore) — esconder trabalho pendente"
grep -rnE '@(Disabled|Ignore)\b' */src/test 2>/dev/null || true

hdr "6. assumeTrue (SKIP honesto de ambiente — só sinaliza volume)"
grep -rc "assumeTrue\|Assumptions\." "${ROOTS[@]}"/*/src/test 2>/dev/null || true

printf '\n## resumo\n'
printf 'candidatos TODO/FIXME:          %s\n' "$(n '\b(TODO|FIXME|XXX|HACK)\b' | wc -l)"
printf 'UnsupportedOperationException:  %s\n' "$(n 'UnsupportedOperationException' | wc -l)"
printf 'catch vazio:                    %s\n' "$(n 'catch \([^)]*\)[[:space:]]*\{[[:space:]]*\}' | wc -l)"
printf '\nLembrete: catálogo confirmado vai para docs/bugs-and-gaps/known-bugs.md (§NNN, EN+PT);\neste script só levanta candidatos.\n'
