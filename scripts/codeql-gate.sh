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

echo "== GATE 1: CodeQL alerts (security/code-scanning) =="
for br in "${BRANCHES[@]}"; do
  n=1; hits=0; open_list=""
  while :; do
    page=$(gh api "/repos/$REPO/code-scanning/alerts?per_page=100&page=$n" \
      --jq '.[] | select((.state=="open") or (.state==null and .fixed_at==null and .dismissed_at==null)) |
            "#\(.number) \(.rule.id) \(.most_recent_instance.location.path):\(.most_recent_instance.location.start_line)"' 2>/dev/null) || { echo "  [erro] API indisponivel para $br"; FAILED=1; break; }
    [ -z "$page" ] && break
    hits=$((hits + $(printf '%s\n' "$page" | grep -c .)))
    open_list="$open_list$(printf '%s\n' "$page")"
    n=$((n+1))
    [ "$n" -gt 12 ] && break   # teto de seguranca: ~1200 alertas
  done
  if [ "$hits" -gt 0 ]; then
    echo "  RED — $hits alerta(s) open na branch $br:"
    printf '%s\n' "$open_list" | sed 's/^/    /'
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
echo "== RESULTADO: os dois gates verdes =="
