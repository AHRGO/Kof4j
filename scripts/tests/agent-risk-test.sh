#!/usr/bin/env bash
#
# agent-risk-test.sh — o classificador de risco decide QUEM verifica o quê:
# docs simples não pagam verifier de modelo; FFI/ABI/nullability/generics/
# concorrência/GC/cross-target nunca são tratados como rename.
#
#   R1  só docs → low, sem verifier independente
#   R2  typer/lowering comum → medium
#   R3  FFI / backend Native / nullability / generics / concorrência / GC → high
#   R4  o MAIOR sinal domina (docs + FFI = high)
#   R5  --declared só sobe: nunca reduz high nem o medium calculado
#   R6  palavras de alto risco no texto (issue/commit) sobem para high
#   R7  teste puro = low; teste de domínio HIGH = medium (relaxar assert = falso verde)
#   R8  AGENTS.md / DECISIONS.md sobem mesmo sendo .md
#   R9  desconhecido = medium (conservador); mudança vazia = low
#   R10 --range lê o diff de um repo git real
#
# Uso: scripts/tests/agent-risk-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

RISK="$REPO_ROOT/scripts/agent-risk.sh"
risk_of() { bash "$RISK" "$@" | sed -n 's/^risk=//p'; }
ver_of() { bash "$RISK" "$@" | sed -n 's/^independent_verifier_required=//p'; }

echo "R1 — só docs = low"
assert_eq low "$(risk_of --file docs/a.md --file training/idioms/x.md --file CHANGELOG.md)" "docs isolados"
assert_eq false "$(ver_of --file docs/a.md)" "low não exige verifier independente (não paga 2º modelo)"

echo "R2 — produção comum = medium"
assert_eq medium "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java)" "typer localizado"
assert_eq medium "$(risk_of --file kof-cli/src/main/java/dev/kof/cli/Profile.java)" "CLI"
assert_eq medium "$(risk_of --file scripts/auto-loop.sh)" "script de automação"
assert_eq medium "$(risk_of --file .github/workflows/ci.yml)" "workflow"
assert_eq false "$(ver_of --file scripts/auto-loop.sh)" "medium não exige verifier independente"

echo "R3 — domínios de alto risco"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/nat/NativeFfiCall.java)" "FFI Native"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/ExternArgumentCoercion.java)" "extern/ABI"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/NullableFieldWriter.java)" "nullability"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/GenericFieldEraser.java)" "generics/erasure"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/SpawnLowerer.java)" "concorrência"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/nat/NativeGcSweep.java)" "GC"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/RiscvEmitter.java)" "cross-target"
assert_eq true "$(ver_of --file kof-compiler/src/main/java/dev/kof/compiler/nat/NativeFfiCall.java)" "high exige verifier independente"

echo "R4 — o maior sinal domina"
assert_eq high "$(risk_of --file docs/a.md --file kof-compiler/src/main/java/dev/kof/compiler/FfiSignature.java)" "docs + FFI = high"
assert_eq medium "$(risk_of --file docs/a.md --file scripts/x.sh)" "docs + script = medium"

echo "R5 — --declared só sobe"
assert_eq high "$(risk_of --file docs/a.md --declared high)" "declarado high sobe um low"
assert_eq medium "$(risk_of --file scripts/x.sh --declared low)" "declarado low NÃO reduz o medium calculado"
assert_eq high "$(risk_of --file kof-compiler/src/main/java/dev/kof/compiler/nat/NativeFfiCall.java --declared low)" "declarado low NÃO reduz high"

echo "R6 — palavras de alto risco no texto"
assert_eq high "$(risk_of --file docs/a.md --text 'fix FFI Float slot conversion')" "FFI no texto"
assert_eq high "$(risk_of --file docs/a.md --text 'nullability of Int? field')" "nullability no texto"
assert_eq low "$(risk_of --file docs/a.md --text 'typo no README')" "texto inócuo não sobe"

echo "R7 — teste puro x teste de domínio HIGH"
assert_eq low "$(risk_of --file kof-compiler/src/test/java/dev/kof/compiler/StringTest.java)" "teste puro = low"
assert_eq medium "$(risk_of --file kof-compiler/src/test/java/dev/kof/compiler/FfiE2ETest.java)" "teste de FFI = medium (falso verde)"

echo "R8 — governança e decisões sobem mesmo sendo .md"
assert_eq high "$(risk_of --file AGENTS.md)" "AGENTS.md"
assert_eq high "$(risk_of --file AGENTS.pt_BR.md)" "AGENTS.pt_BR.md"
assert_eq high "$(risk_of --file docs/development/DECISIONS.md)" "DECISIONS.md"
assert_eq low "$(risk_of --file docs/development/roadmap.md)" "outro doc de development = low"

echo "R9 — desconhecido e vazio"
assert_eq medium "$(risk_of --file algo/desconhecido.xyz)" "arquivo desconhecido = medium (conservador)"
assert_eq low "$(risk_of)" "mudança vazia = low"

echo "R10 — --range em repo git real"
mk_env; mk_repo
( cd "$REPO" && mkdir -p kof-compiler/src/main/java/dev/kof/compiler/nat && echo x > kof-compiler/src/main/java/dev/kof/compiler/nat/NativeFfiCall.java \
    && git add -A && git commit -q -m ffi )
assert_eq high "$(risk_of --repo "$REPO" --range HEAD~1..HEAD)" "diff do commit toca FFI Native"
( cd "$REPO" && echo y > docs/development/plan.md && git add -A && git commit -q -m doc )
assert_eq low "$(risk_of --repo "$REPO" --range HEAD~1..HEAD)" "diff só de docs = low"

finish
