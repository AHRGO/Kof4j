#!/usr/bin/env bash
#
# target-matrix.sh — PROPOSAL-1.0-EXIT-GATE §13/§14 / fila §23 item 10
# ("final target matrix"), a frente EG-5 do D-RELEASE-1.0.
#
# Um comando, no dia do RC, roda o MESMO programa Kof nos 8 alvos da
# superfície Stable 1.0 e prova a paridade byte-a-byte onde o contrato exige:
#
#   core (paridade obrigatória, oráculo = JVM):
#     jvm · native (x86-64) · native.riscv64 · native.aarch64 · js · script
#   gates próprios (delegados, nunca silenciosos — R6):
#     kofc    → EG-9  (gate próprio)
#     android → EG-10 (gate próprio, CI roda o APK)
#
# Honestidade (R6/R7): ferramenta ausente NUNCA vira verde falso.
#   - toolchain de build ausente num alvo core  → FAIL (o RC exige a matriz);
#   - qemu ausente para EXECUTAR o cross        → SKIP honesto → INCOMPLETE (rc=2);
#   - KofC/Android                              → linha DELEGATED nomeando o gate.
# Só o veredito `PASS` (rc=0) satisfaz a §8 para este item.
#
# Uso:
#   scripts/target-matrix.sh                 # usa o bin/kof da árvore
#   scripts/target-matrix.sh --dist DIR      # usa o pacote (o objeto do RC)
#   scripts/target-matrix.sh --keep          # não apaga a sandbox
#   scripts/target-matrix.sh --selftest      # RED-first offline (sem compilar)
#
# rc: 0 PASS · 1 FAIL · 2 INCOMPLETE (ferramenta de execução ausente) · 3 ambiente.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELFTEST=false; KEEP=false; DIST_DIR=""
WORK_ROOT="${KOF_MATRIX_HOME:-$HOME}"
while [ $# -gt 0 ]; do
    case "$1" in
        --dist) DIST_DIR="$2"; shift ;;
        --work) WORK_ROOT="$2"; shift ;;
        --keep) KEEP=true ;;
        --selftest) SELFTEST=true ;;
        *) echo "uso: $0 [--dist DIR] [--work DIR] [--keep] [--selftest]" >&2; exit 2 ;;
    esac
    shift
done

# ── utilidades de veredito ─────────────────────────────────────────────────
FAILURES=""; SKIPS=""
note() { echo "matrix: $*"; }
fail() { FAILURES="$FAILURES
  - $*"; note "FAIL: $*"; }
skip() { SKIPS="$SKIPS
  - $*"; note "SKIP: $*"; }

# paridade byte-a-byte contra o oráculo (comparador testável, RED-first no
# selftest): iguais → 0; divergente → 1 com as duas saídas nomeadas.
parity() { # rotulo oraculo alvo
    local label="$1" oracle="$2" target="$3"
    if cmp -s "$oracle" "$target"; then note "parity ok: $label"; return 0; fi
    fail "$label: stdout divergente do oraculo JVM — $(diff <(cat "$oracle") <(cat "$target") | head -6 | tr '\n' ' ')"
    return 1
}

# qemu_ld_prefix <arch> -> imprime o prefixo do loader ou nada.
qemu_ld_prefix() { # riscv64|aarch64
    local arch="$1" loader root
    case "$arch" in
        riscv64) loader=ld-linux-riscv64-lp64d.so.1 ;;
        aarch64) loader=ld-linux-aarch64.so.1 ;;
        *) return 0 ;;
    esac
    for root in "${KOF_CROSS_SYSROOT:-}" /usr /; do
        [ -n "$root" ] || continue
        if [ -e "$root/$arch-linux-gnu/lib/$loader" ]; then echo "$root/$arch-linux-gnu"; return 0; fi
        if [ -e "$root/usr/$arch-linux-gnu/lib/$loader" ]; then echo "$root/usr/$arch-linux-gnu"; return 0; fi
    done
    return 0
}

# ── selftest RED-first (offline: sem compilar, sem tocar a árvore) ─────────
if [ "$SELFTEST" = true ]; then
    ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
    printf 'a\nb\n' > "$ST/oracle"; printf 'a\nb\n' > "$ST/same"; printf 'a\nX\n' > "$ST/diff"
    FAILURES=""
    if ! parity "controle-igual" "$ST/oracle" "$ST/same" >/dev/null 2>&1; then
        echo "SELFTEST FAIL: comparador reprovou saidas iguais (falso vermelho)"; exit 1
    fi
    FAILURES=""
    if parity "controle-divergente" "$ST/oracle" "$ST/diff" >/dev/null 2>&1; then
        echo "SELFTEST FAIL: comparador aceitou saida divergente (falso verde)"; exit 1
    fi
    [ -n "$FAILURES" ] || { echo "SELFTEST FAIL: divergencia nao registrou FAIL"; exit 1; }
    # prefixo de qemu inexistente nao pode inventar caminho
    if p="$(qemu_ld_prefix archnenhuma)" && [ -n "$p" ]; then
        echo "SELFTEST FAIL: qemu_ld_prefix inventou prefixo '$p'"; exit 1
    fi
    echo "SELFTEST: ok — comparador reprova divergencia, aceita igualdade, sem prefixo falso"
    exit 0
fi

# ── pré-condições honestas ─────────────────────────────────────────────────
command -v java >/dev/null 2>&1 || { echo "matrix: SEM java no PATH (o kof exige JDK 25) — ambiente, nao bug" >&2; exit 3; }
JMAJOR="$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d. -f1)"
[ "${JMAJOR:-0}" -ge 25 ] 2>/dev/null || { echo "matrix: java $JMAJOR no PATH (o kof exige 25) — ambiente" >&2; exit 3; }

if [ -n "$DIST_DIR" ]; then
    KOF="$DIST_DIR/bin/kof"
else
    KOF="$ROOT/bin/kof"
fi
[ -x "$KOF" ] || { echo "matrix: kof nao executavel em $KOF" >&2; exit 3; }

# ── sandbox FORA do repo (regra 9: nunca /tmp) ─────────────────────────────
SANDBOX="$(mktemp -d "$WORK_ROOT/.kof-matrix.XXXXXX")"
cleanup() { [ "$KEEP" = false ] && rm -rf "$SANDBOX"; return 0; }
trap cleanup EXIT
cd "$SANDBOX" || exit 3
case "$PWD" in "$ROOT"*) echo "matrix: IMPOSSIVEL — sandbox dentro do repo" >&2; exit 3;; esac

cat > G.kf <<'KF'
main() {
    println("matrix:start")
    var total = 0
    for (var i in listOf(1, 2, 3, 4)) { total = total + i }
    println("sum=" + total)
    println("Hello, Kof!")
    println("matrix:end")
}
KF
GOLDEN="G.kf"

# roda um alvo core e grava o stdout em $2; rc do kof em $?
run_core() { # target outfile
    local t="$1" out="$2"
    "$KOF" run "$GOLDEN" --target "$t" >"$out" 2>"$out.err"
}

# ── core: JVM é o oráculo; os demais têm de bater byte-a-byte ──────────────
ORACLE="jvm.out"
if ! run_core jvm "$ORACLE"; then fail "jvm (oraculo): $(tail -2 "$ORACLE.err" | tr '\n' ' ')"; fi
if ! grep -q "matrix:end" "$ORACLE"; then fail "jvm: oraculo sem o marcador final (saida: $(tr '\n' ' ' <"$ORACLE"))"; fi

for t in script js native; do
    case "$t" in
        js)     command -v node >/dev/null 2>&1 || { fail "js: sem node no PATH"; continue; } ;;
        native) { command -v as >/dev/null 2>&1 && command -v ld >/dev/null 2>&1; } || { fail "native(x86_64): sem as/ld no PATH"; continue; } ;;
    esac
    if run_core "$t" "$t.out"; then
        parity "$t" "$ORACLE" "$t.out"
    else
        fail "$t: run falhou — $(tail -2 "$t.out.err" | tr '\n' ' ')"
    fi
done

# cross: build com a toolchain; exec sob qemu (paridade) ou skip honesto.
for arch in riscv64 aarch64; do
    t="native.$arch"; tc="${arch}-linux-gnu-as"
    if ! command -v "$tc" >/dev/null 2>&1; then fail "$t: sem $tc (toolchain de build obrigatoria)"; continue; fi
    if ! "$KOF" build "$GOLDEN" --target "$t" >"$t.build" 2>&1; then
        fail "$t: build falhou — $(tail -2 "$t.build" | tr '\n' ' ')"; continue
    fi
    elf="build/classes/Default/Main"
    [ -f "$elf" ] || { fail "$t: build nao produziu $elf"; continue; }
    cp "$elf" "$arch.elf"
    if ! command -v "qemu-$arch" >/dev/null 2>&1; then
        skip "$t: build OK, sem qemu-$arch para EXECUTAR (matrix INCOMPLETE no RC)"
        continue
    fi
    pfx="$(qemu_ld_prefix "$arch")"
    if [ -n "$pfx" ]; then
        QEMU_LD_PREFIX="$pfx" "qemu-$arch" "$arch.elf" >"$t.out" 2>"$t.out.err"
    else
        "qemu-$arch" "$arch.elf" >"$t.out" 2>"$t.out.err"
    fi
    if [ $? -ne 0 ]; then fail "$t: exec sob qemu falhou — $(tail -2 "$t.out.err" | tr '\n' ' ')"; continue; fi
    parity "$t" "$ORACLE" "$t.out"
done

# ── gates próprios (EG-9/EG-10): nunca silenciosos ─────────────────────────
note "DELEGATED kofc    → EG-9 (gate próprio; ver scripts/ e CI kof-c)"
note "DELEGATED android → EG-10 (gate próprio; CI android.yml roda o APK)"

# ── veredito ───────────────────────────────────────────────────────────────
if [ -n "$FAILURES" ]; then
    echo "TARGET-MATRIX: FAIL — alvos fora do contrato:" >&2
    echo "$FAILURES" >&2
    exit 1
fi
if [ -n "$SKIPS" ]; then
    echo "TARGET-MATRIX: INCOMPLETE — execucao ausente (nao certifica o RC):" >&2
    echo "$SKIPS" >&2
    exit 2
fi
echo "TARGET-MATRIX: PASS — jvm/x86_64/riscv64/aarch64/js/script com paridade byte-a-byte (oraculo JVM); kofc=EG-9, android=EG-10"
exit 0
