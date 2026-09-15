#!/usr/bin/env bash
#
# codeql-gate-test.sh — prova local (sem rede) da logica do GATE 1 do
# scripts/codeql-gate.sh via um `gh` FAKE no PATH que devolve JSON/TSV canned.
#
# Dois bugs reais provados aqui (licao 15/09):
#   (1) FALSO-VERDE: a LISTA (/code-scanning/alerts) OMITE o alerta recem-criado
#       com `state:null` (consistencia eventual). Um gate que so le a lista
#       reporta 0 open e deixa passar um alerta aberto. O gate precisa unir a
#       lista com `?state=open&ref=<branch>` (1 chamada/branch).
#   (2) FALSO-RED: `instance.state` de `/alerts/{n}/instances` e "open" para
#       TODO alerta ja detectado, inclusive DISMISSADO. Contar instancias acusa
#       412 "open" na main com 0 aberto de verdade. O estado vigente e o
#       `state` do proprio alerta.
#   (3) INCONCLUSIVO != verde: API fora nao pode imprimir "os dois gates verdes".
#
# Uso: scripts/tests/codeql-gate-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GATE="scripts/codeql-gate.sh"
FAILED=0

pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

make_fake_gh() { # $1 = dir, $2 = modo (normal|down)
  local dir="$1" mode="$2"
  cat > "$dir/gh" <<EOF
#!/usr/bin/env bash
mode="$mode"
args="\$*"
if [ "\$mode" = "down" ]; then echo "API rate limit exceeded" >&2; exit 1; fi
case "\$args" in
  *"/code-scanning/alerts?per_page=100"*)
    # LISTA: traz o #1 DISMISSADO (nunca deve contar) e OMITE o #2 (state:null).
    printf '#1\tdismissed\t2026-09-15T00:00:00Z\t-\trefs/heads/beta-0.4.0\tjava/relative-path-command\tkof-compiler/src/test/SomeTest.java:10\n'
    ;;
  *"alerts?ref=refs/heads/beta-0.4.0&state=open"*)
    # Uniao por branch: revela o #2 que a LISTA omitiu.
    printf '2\n'
    ;;
  *"alerts?ref=refs/heads/main&state=open"*)
    printf ''
    ;;
  *"/code-scanning/alerts/2"*)
    printf '#2\tnull\t-\t-\trefs/heads/beta-0.4.0\tjava/concatenated-command-line\tkof-compiler/src/test/PrimitiveToReferenceCastE2ETest.java:46\n'
    ;;
  *)
    printf ''
    ;;
esac
EOF
  chmod +x "$dir/gh"
}

echo "== cenario 1: null-state omitido da lista vira RED (nao falso-verde) =="
TMP1=$(mktemp -d); make_fake_gh "$TMP1" normal
out1=$(PATH="$TMP1:$PATH" timeout 60 bash "$GATE" --fast 2>&1); rc1=$?
rm -rf "$TMP1"
if printf '%s' "$out1" | grep -q "RED — 1 alerta(s) open na branch beta-0.4.0"; then
  pass "beta acusa 1 open (o #2 null-state foi pego pela uniao por branch)"
else
  fail "beta NAO acusou o #2 null-state (falso-verde persistiu)"; printf '%s\n' "$out1" | sed 's/^/      /'
fi
if printf '%s' "$out1" | grep -q "green — main: 0 open"; then
  pass "main green (dismissed nao conta)"
else
  fail "main nao ficou green (falso-RED de instancia?)"; printf '%s\n' "$out1" | sed 's/^/      /'
fi
if printf '%s' "$out1" | grep -q "#2 \[java/concatenated-command-line\]"; then
  pass "o alerta listado e o #2 (regra e path corretos)"
else
  fail "listagem nao identifica o #2"
fi
[ "$rc1" = 1 ] && pass "exit 1 (portao vermelho)" || fail "exit=$rc1 (esperado 1)"

echo "== cenario 2: API fora => INCONCLUSIVO (exit 2), nunca 'verdes' =="
TMP2=$(mktemp -d); make_fake_gh "$TMP2" down
out2=$(PATH="$TMP2:$PATH" timeout 60 bash "$GATE" --fast 2>&1); rc2=$?
rm -rf "$TMP2"
if printf '%s' "$out2" | grep -q "INCONCLUSIVO"; then
  pass "reporta INCONCLUSIVO"
else
  fail "nao reportou INCONCLUSIVO"; printf '%s\n' "$out2" | sed 's/^/      /'
fi
if printf '%s' "$out2" | grep -q "os dois gates verdes"; then
  fail "imprimiu 'verdes' com a API fora (falso-verde de gate)"
else
  pass "nao imprime 'verdes' quando GATE 1 nao foi avaliado"
fi
[ "$rc2" = 2 ] && pass "exit 2 (inconclusivo)" || fail "exit=$rc2 (esperado 2)"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
