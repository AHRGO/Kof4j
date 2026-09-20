#!/usr/bin/env bash
#
# stability-report.sh — condicao 4 do gate 0.5.0 (`D-RELEASE-0.5.0-GATE`):
# transforma o log de uma corrida REAL da suite num relatorio machine-readable,
# para o gate consumir `STABILITY: GREEN` a partir de MEDICAO, nunca de memoria.
#
# O que mede (honesto, nada alem): a ultima linha
#   TOTAL: tests=N failures=F errors=E skipped=S
# do log. `STABILITY: GREEN` exige F=0 e E=0. A matriz de conformidade
# (`ConformanceMatrixTest` / `ConformanceMatrixDocTest`) e parte da suite — um
# log 0F/0E ja a inclui; a paridade por alvo e a condicao 1 (EG-5,
# `scripts/target-matrix.sh`). Este script NAO re-executa a suite (caro): ele
# le o log de uma corrida ja feita na candidata.
#
# Uso:
#   scripts/stability-report.sh --suite-log FILE [--sha SHA]
#   KOF_SUITE_LOG=FILE scripts/stability-report.sh
#   scripts/stability-report.sh --selftest
# rc: 0 GREEN · 1 RED · 3 sem log/ambiente.
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

LOG="${KOF_SUITE_LOG:-}"; SHA=""; SELFTEST=false
while [ $# -gt 0 ]; do
  case "$1" in
    --suite-log) LOG="$2"; shift ;;
    --sha) SHA="$2"; shift ;;
    --selftest) SELFTEST=true ;;
    *) echo "uso: $0 --suite-log FILE [--sha SHA] | --selftest" >&2; exit 2 ;;
  esac
  shift
done

sha_of() { [ -n "$SHA" ] && echo "$SHA" || git rev-parse --short HEAD 2>/dev/null || echo "unknown"; }

# verdict_from_log LOG -> imprime STABILITY: ... ; rc 0 GREEN, 1 RED, 3 sem TOTAL
verdict_from_log() {
  local log="$1" line t f e s
  [ -f "$log" ] || { echo "STABILITY: unknown — log ausente: $log"; return 3; }
  line="$(grep -E 'TOTAL: tests=[0-9]+ failures=[0-9]+ errors=[0-9]+' "$log" | tail -1)"
  if [ -z "$line" ]; then
    echo "STABILITY: unknown — sem linha TOTAL no log: $log"; return 3
  fi
  t="$(sed -E 's/.*tests=([0-9]+).*/\1/' <<<"$line")"
  f="$(sed -E 's/.*failures=([0-9]+).*/\1/' <<<"$line")"
  e="$(sed -E 's/.*errors=([0-9]+).*/\1/' <<<"$line")"
  s="$(sed -E 's/.*skipped=([0-9]+).*/\1/' <<<"$line")"
  if [ "$f" -eq 0 ] && [ "$e" -eq 0 ]; then
    echo "STABILITY: GREEN sha=$(sha_of) tests=$t failures=0 errors=0 skipped=$s"
    return 0
  fi
  echo "STABILITY: RED sha=$(sha_of) tests=$t failures=$f errors=$e skipped=$s"
  return 1
}

if [ "$SELFTEST" = true ]; then
  ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
  fail() { echo "SELFTEST FAIL: $*" >&2; exit 2; }
  printf 'x\nTOTAL: tests=100 failures=0 errors=0 skipped=5\n' > "$ST/green"
  printf 'x\nTOTAL: tests=100 failures=1 errors=0 skipped=5\n' > "$ST/redf"
  printf 'x\nTOTAL: tests=100 failures=0 errors=2 skipped=5\n' > "$ST/rede"
  printf 'sem total aqui\n' > "$ST/none"
  out="$(verdict_from_log "$ST/green")" || fail "log verde reprovado (falso vermelho): $out"
  grep -q "STABILITY: GREEN" <<<"$out" || fail "log verde nao deu GREEN"
  verdict_from_log "$ST/redf" >/dev/null && fail "failures=1 aceito (falso verde)"
  verdict_from_log "$ST/rede" >/dev/null && fail "errors=2 aceito (falso verde)"
  verdict_from_log "$ST/none" >/dev/null && fail "log sem TOTAL aceito (falso verde)"
  echo "SELFTEST: ok — GREEN so com 0F/0E; failures/errors/sem-TOTAL reprovados"
  exit 0
fi

verdict_from_log "$LOG"
exit $?
