#!/usr/bin/env bash
#
# agent-verify-wiring-test.sh — prova de que os hooks dos gates CHANGELOG/ledger
# no agent-verify DISPARAM de verdade. O suite-test dos gates prova o detector;
# aqui prova o fio: um regex `touches` quebrado = gate que nunca roda = classe
# falso-verde que a lane combate desde a 419 e que o mutation-testing pegou duas
# vezes. Extração literal do agent-verify.sh (sem fixture de manifesto: o
# verifier real executa exatamente `grep -qE <pat>` sobre a lista changed).
#
# Uso: scripts/tests/agent-verify-wiring-test.sh   (exit 0 = fios ligados)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
AV=scripts/agent-verify.sh
fail=0
hook_pat() { # $1 = rotulo do run_gate -> regex do touches imediatamente anterior
    local p
    p="$(grep -B1 "run_gate $1 " "$AV" | grep -oE "touches '[^']+'" | sed "s/touches '//; s/'$//")"
    [ -n "$p" ] || { echo "MISSING: hook '$1' sumiu do agent-verify.sh"; return 1; }
    printf '%s\n' "$p"
}
hit() { printf '%s\n' "$1" | grep -qE "$2"; }
for_each_pat() { # $1=pat $2=tipo(yes|no) $3..=caminhos
    local pat="$1" want="$2"; shift 2
    local c got
    for c in "$@"; do
        if hit "$c" "$pat"; then got=yes; else got=no; fi
        if [ "$got" != "$want" ]; then
            echo "FIO QUEBRADO: '$c' -> $got (esperado $want) em [$pat]"; fail=1
        fi
    done
}
CHG="$(hook_pat changelog_ledger)" || exit 1
ANC="$(hook_pat ledger_anchors)" || exit 1
for_each_pat "$CHG" yes CHANGELOG.md CHANGELOG.pt_BR.md \
    docs/bugs-and-gaps/known-bugs.md docs/bugs-and-gaps/known-bugs.pt_BR.md \
    scripts/changelog-ledger-waivers.txt
for_each_pat "$CHG" no docs/development/README.md kof-runtime/src/main/java/dev/kof/runtime/KofJsRunner.java
for_each_pat "$ANC" yes docs/bugs-and-gaps/known-bugs.md docs/bugs-and-gaps/known-bugs.pt_BR.md
for_each_pat "$ANC" no CHANGELOG.md scripts/changelog-ledger-waivers.txt
# mutacao do proprio teste: trocar o regex real por um impossivel deve derrubar
if printf 'CHANGELOG.md\n' | grep -qE "^CHANGELOG\\.NUNCA$"; then echo "MUTACAO INVALIDA"; exit 1; fi
[ "$fail" -eq 0 ] && echo "ok  — fios changelog_ledger/ledger_anchors batendo"
exit $fail
