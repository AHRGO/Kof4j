#!/usr/bin/env bash
#
# codeql-gate.sh — simula os DOIS gates da aba "Security and quality" do
# GitHub ANTES do commit/push (diretiva da mantenedora, 15/09; registro em
# docs/development/DECISIONS.md §D-GATE).
#
#   GATE 1 (security/code-scanning): nenhum alerta CodeQL "open".
#     Armadilha descoberta 15/09: o filtro `?state=open` da API engana —
#     alertas recém-criados retornam `state: null` (nem open nem dismissed)
#     e SO aparecem na UI. O gate conta como open: state=="open" OU
#     (state==null E sem fixed_at E sem dismissed_at).
#   GATE 2 (security/quality): build dos modulos Java verde SEM stubs ECJ
#     (o exit 0 mentiroso: ECJ gera classes que throws "Unresolved
#     compilation problem") + check_500.sh. A suíte completa continua sendo
#     porta de merge (AGENTS.md Q1/Q2) — o gate local roda o compile+stub-check.
#
# Uso: scripts/codeql-gate.sh [--fast]   (--fast: pula o build, só API+hooks)
# Exit: 0 = dois gates verdes; 1 = algo vermelho (NAO COMMITAR).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

REPO="${CODEQL_GATE_REPO:-KofLang/Kof4j}"
BRANCHES=("main" "beta-0.4.0")
FAILED=0
GATE1_OK=1

echo "== GATE 1: CodeQL alerts (security/code-scanning) =="
# VERDADE NO NIVEL DO ALERTA (licao 15/09 ~22:40, corrige o falso RED de 412):
# /alerts/{n}/instances devolve state="open" para TODO alerta ja detectado —
# inclusive DISMISSADOS (o #743 "won't fix" tinha instance.state="open"). Logo
# contar instancias gera falso positivo. O estado vigente e o `state` do
# /alerts/{n}: "open" (com dismissed_at/fixed_at nulos) OU `null` recem-criado
# SEM dismissed_at/fixed_at = aberto; "dismissed"/"fixed" = fechado. O list
# pagina com atraso (consistencia eventual) — por isso unimos os numeros do
# list com os de `?state=open&ref=` das branches ativas. Custo: 1 GET/alerta.
# CUSTO (licao 15/09 ~18:40): 1 GET por alerta x 731 alertas = 8min de hang no
# pre-push. O LIST paginado vem COM state/dismissed_at/fixed_at/
# most_recent_instance — 1 chamada resolve o gate inteiro. O `?state=open`
# sozinho esconde o `state:null` recem-criado; o list SEM filtro pega todos.
ROWS=$(gh api "/repos/$REPO/code-scanning/alerts?per_page=100" --paginate \
  --jq '.[] | [(.number|tostring), (.state // "null"), (.dismissed_at // "-"), (.fixed_at // "-"), (.most_recent_instance.ref // "-"), (.rule.id), ((.most_recent_instance.location.path // "-") + ":" + ((.most_recent_instance.location.start_line // "-")|tostring))] | @tsv' 2>/dev/null) \
  || { echo "  [aviso] API indisponivel (rate limit?) — GATE 1 NAO verificado; o CI (codeql.yml) continua sendo a porta real"; ROWS=""; }

# A LISTA omite os alertas recem-criados com `state:null` (consistencia eventual:
# o #746 nao veio no list, so no `/alerts/{n}` e no `?state=open&ref=`). Sem esta
# uniao o gate dava FALSO-VERDE. 1 chamada/branch + 1 GET individual so para os
# que faltam = barato (nao os 731 do v1, que travava o pre-push ~8min).
if [ -n "$ROWS" ]; then
  for br in "${BRANCHES[@]}"; do
    for n in $(gh api "/repos/$REPO/code-scanning/alerts?ref=refs/heads/$br&state=open&per_page=100" --paginate --jq '.[].number' 2>/dev/null); do
      printf '%s\n' "$ROWS" | cut -f1 | grep -qx "$n" && continue
      extra=$(gh api "/repos/$REPO/code-scanning/alerts/$n" \
        --jq '[(.number|tostring), (.state // "null"), (.dismissed_at // "-"), (.fixed_at // "-"), (.most_recent_instance.ref // "-"), .rule.id, ((.most_recent_instance.location.path // "-") + ":" + ((.most_recent_instance.location.start_line // "-")|tostring))] | @tsv' 2>/dev/null)
      [ -n "$extra" ] && ROWS="$ROWS
$extra"
    done
  done
fi

for br in "${BRANCHES[@]}"; do
  if [ -z "$ROWS" ]; then
    echo "  NAO-AVALIADO — $br (API fora; nada foi verificado)"
    GATE1_OK=0
    continue
  fi
  open_n=0; open_list=""
  while IFS=$'\t' read -r n st dis fix ref rule loc; do
    [ "$ref" = "refs/heads/$br" ] || continue
    isopen=0
    [ "$st" = "open" ] && isopen=1
    [ "$st" = "null" ] && [ "$dis" = "-" ] && [ "$fix" = "-" ] && isopen=1
    if [ "$isopen" = 1 ]; then
      open_n=$((open_n+1))
      [ "$open_n" -le 25 ] && open_list="$open_list  #$n [$rule] $loc
"
    fi
  done <<< "$ROWS"
  if [ "$open_n" -gt 0 ]; then
    echo "  RED — $open_n alerta(s) open na branch $br (top 25):"
    printf '%s' "$open_list" | sed 's/^/  /'
    FAILED=1
  else
    echo "  green — $br: 0 open"
  fi
done

if [ "${1:-}" != "--fast" ]; then
  echo "== GATE 2: build sem stubs ECJ (security/quality) =="
  if timeout 900 mvn -o -q -pl kof-compiler,kof-cli,kof-script,kof-c-compiler -am compile > /tmp/codeql-gate-build.log 2>&1; then
    stubs=$(for c in $(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep 'src/main.*\.java$' | sed 's|src/main/java/||; s|\.java$||'); do
      find . -path "*/target/classes/${c}.class" 2>/dev/null
    done | head -50)
    bad=0
    for cls in $stubs; do
      if javap -c -p "$cls" 2>/dev/null | grep -q "Unresolved compilation"; then
        echo "  STUB ECJ: $cls"; bad=1
      fi
    done
    [ "$bad" = 1 ] && FAILED=1
    [ "$bad" = 0 ] && echo "  green — compile 4 modulos, 0 stubs nas classes tocadas"
  else
    echo "  RED — build falhou (ver /tmp/codeql-gate-build.log)"; FAILED=1
  fi
  echo "== GATE 2b: check_500 =="
  if ! scripts/check_500.sh > /tmp/codeql-gate-500.log 2>&1; then
    echo "  RED — check_500 falhou (tail abaixo)"; tail -5 /tmp/codeql-gate-500.log | sed 's/^/    /'; FAILED=1
  else
    echo "  green — check_500"
  fi
fi

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: PORTAO VERMELHO — nao commitar sem fechar o achado =="
  exit 1
fi
if [ "$GATE1_OK" = 0 ]; then
  echo "== RESULTADO: INCONCLUSIVO — GATE 1 nao avaliado (API fora); os demais gates passaram =="
  exit 2
fi
echo "== RESULTADO: os dois gates verdes =="
