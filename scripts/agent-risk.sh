#!/usr/bin/env bash
#
# agent-risk.sh — classifica o RISCO de uma entrega (low|medium|high) para não
# pagar um verifier de modelo em docs simples e, ao mesmo tempo, não tratar
# ABI/nullability/generics como rename. Determinístico: só caminhos + palavras.
#
# Uso:
#   agent-risk.sh --range BASE..HEAD [--repo DIR] [--text "msg/issue"] [--declared high]
#   agent-risk.sh --file a/b.java --file docs/x.md [--text ...] [--declared ...]
#   git diff --name-only | agent-risk.sh --stdin
#
# Saída (stdout): `risk=<nível>`, uma linha `reason: <nível> <arquivo|texto> (<regra>)`
# por sinal e `independent_verifier_required=true|false` (true só para high).
#
# Regra conservadora: vale o MAIOR sinal; `--declared` só SOBE o nível, nunca
# reduz um high (nem um medium calculado) — o worker não se auto-rebaixa.
set -uo pipefail

files=(); text=""; declared=""; range=""; repo="."; use_stdin=0
while [ $# -gt 0 ]; do
    case "$1" in
        --file)     files+=("${2:-}"); shift 2;;
        --range)    range="${2:-}"; shift 2;;
        --repo)     repo="${2:-.}"; shift 2;;
        --text)     text="${2:-}"; shift 2;;
        --declared) declared="${2:-}"; shift 2;;
        --stdin)    use_stdin=1; shift;;
        *) echo "argumento desconhecido: $1" >&2; exit 2;;
    esac
done
case "$declared" in ''|low|medium|high) ;; *) echo "--declared: low|medium|high" >&2; exit 2;; esac
[ "$use_stdin" -eq 1 ] && while IFS= read -r l; do [ -n "$l" ] && files+=("$l"); done
if [ -n "$range" ]; then
    while IFS= read -r l; do [ -n "$l" ] && files+=("$l"); done < <(git -C "$repo" diff --name-only "$range" 2>/dev/null)
fi

rank() { case "$1" in high) echo 3;; medium) echo 2;; low) echo 1;; *) echo 0;; esac; }
level=""; max=0; reasons=()
bump() { # nível alvo motivo
    reasons+=("reason: $1 $2 ($3)")
    if [ "$(rank "$1")" -gt "$max" ]; then max="$(rank "$1")"; level="$1"; fi
}

# Nome que denuncia domínio de alto risco (wrong-code silencioso mora aqui).
high_name() { # $1 = caminho em minúsculas -> regra ou vazio
    case "$1" in
        */compiler/nat/*|*/nat/*)                echo "native-backend";;
        *ffi*|*extern*)                          echo "ffi-abi";;
        *nullab*|*nullable*)                     echo "nullability";;
        *erasure*|*generic*|*typevar*)           echo "generics-erasure";;
        *concurren*|*spawn*|*scheduler*|*await*) echo "concurrency";;
        *gc*|*memory*|*alloc*)                   echo "gc-memory";;
        *riscv*|*aarch*|*x86*)                   echo "cross-target-backend";;
        *optimiz*|*descriptor*|*jvmtypemapper*)  echo "optimizer-descriptors";;
        *security*|*/sec/*|*crypto*)             echo "security-sensitive";;
    esac
}

classify_path() { # devolve "nível|regra"
    local f="$1" lc hn
    lc="$(printf '%s' "$f" | tr '[:upper:]' '[:lower:]')"
    # governança e decisões de linguagem: sobem mesmo sendo .md
    case "$lc" in
        agents.md|agents.pt_br.md)               echo "high|agent-governance"; return;;
        docs/development/decisions.md|docs/development/decisions.pt_br.md) echo "high|language-decisions"; return;;
        *.md|docs/*|training/*|learn/*|changelog*) echo "low|docs"; return;;
    esac
    hn="$(high_name "$lc")"
    case "$lc" in
        */src/test/*)   # teste de domínio HIGH: relaxar assert é risco de falso verde
            if [ -n "$hn" ]; then echo "medium|test-of-$hn"; else echo "low|test-only"; fi; return;;
    esac
    [ -n "$hn" ] && { echo "high|$hn"; return; }
    case "$lc" in
        tests/golden/*)                          echo "medium|golden-expectations";;
        kof-*/src/main/*|scripts/*|*.sh|pom.xml|*/pom.xml|.github/*|tests/*) echo "medium|production-or-automation";;
        *)                                       echo "medium|unclassified-default";;
    esac
}

for f in "${files[@]+"${files[@]}"}"; do
    r="$(classify_path "$f")"; bump "${r%%|*}" "$f" "${r##*|}"
done

if [ -n "$text" ]; then
    if printf '%s' "$text" | grep -qiE '\b(ffi|abi|nullab[a-z]*|erasure|generics?|concurren[a-z]*|spawn|gc|garbage|cross-target|riscv64?|aarch64|frozen semantics|security)\b'; then
        bump high "text" "high-risk-keyword"
    fi
fi
[ -n "$declared" ] && bump "$declared" "declared" "manual"
[ -n "$level" ] || { level="low"; reasons+=("reason: low (no-files) (empty-change)"); }

echo "risk=$level"
printf '%s\n' "${reasons[@]}"
echo "independent_verifier_required=$([ "$level" = high ] && echo true || echo false)"
