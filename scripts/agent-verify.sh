#!/usr/bin/env bash
#
# agent-verify.sh — verifier DETERMINÍSTICO (sem modelo) antes de qualquer
# verifier independente. Não modifica produção: só lê o manifesto, roda gates
# baratos aplicáveis e confere a evidência exigida pelo risco.
#
# Uso:
#   agent-verify.sh deterministic --run-id ID [--risk auto|low|medium|high] [--repo DIR]
#
# Veredito (exit): PASS (0) · BLOCK (1: prova ausente/falha) · INCOMPLETE (3:
# NOT_RUN — toolchain ausente etc.; nunca vira PASS). Grava deterministic.json.
#
#  LOW    gates diretamente afetados (check_500 se Java/scripts; docs-lang se docs).
#  MEDIUM + testes PASS no SHA; package_smoke PASS quando CLI/distribuição é tocada
#           (rodado FORA do diretório do repo — previne a família #550).
#  HIGH   + matriz adversarial do domínio (scripts/agent-matrix.tsv); cross-target
#           x86/riscv64/aarch64 = PASS ou NOT_RUN com motivo; o verifier
#           INDEPENDENTE (commit seguinte) é exigido pelo close guard.
#
# Gates configuráveis por env (os testes usam stubs): AGENT_VERIFY_CHECK500,
# AGENT_VERIFY_DOCSLANG, AGENT_VERIFY_STDLIB (comando shell; default = script do repo).
set -uo pipefail
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
# shellcheck source=agent-common.sh
. "$HERE/agent-common.sh"
EVID="$HERE/agent-evidence.sh"
MATRIX="${AGENT_MATRIX_FILE:-$HERE/agent-matrix.tsv}"

sub="${1:-}"; shift || true
run_id=""; risk_arg="auto"; repo="."
while [ $# -gt 0 ]; do
    case "$1" in
        --run-id) run_id="${2:-}"; shift 2;;
        --risk)   risk_arg="${2:-auto}"; shift 2;;
        --repo)   repo="${2:-.}"; shift 2;;
        *) echo "argumento desconhecido: $1" >&2; exit 2;;
    esac
done
[ "$sub" = "deterministic" ] || { echo "uso: $0 deterministic --run-id ID [--risk ...] [--repo D]" >&2; exit 2; }
D="$AGENT_STATE_ROOT/verifier/$run_id"
EJ="$D/evidence.json"
[ -n "$run_id" ] && [ -f "$EJ" ] || { echo "run-id inexistente: '$run_id'" >&2; exit 2; }

# --- 1) validação do manifesto (STALE/DIRTY/SEM_TESTES/NOT_RUN/FAIL) ---------------------
VAL="$(bash "$EVID" validate --repo "$repo" --run-id "$run_id" 2>&1)"; VAL_RC=$?
printf '%s\n' "$VAL" > "$D/validation.txt"

# --- 2) gates baratos aplicáveis ao que mudou ------------------------------------------------
changed="$(python3 -c "import json;print('\n'.join(json.load(open('$EJ'))['changed_files']))")"
touches() { printf '%s\n' "$changed" | grep -qE "$1"; }
GATES="$D/gates.tsv"; : > "$GATES"
run_gate() { # nome comando-shell
    local name="$1" cmd="$2" out="$D/logs/verify-$1.log"
    mkdir -p "$D/logs"
    if ( cd "$repo" && bash -c "$cmd" ) > "$out" 2>&1; then printf '%s\tPASS\t%s\n' "$name" "$cmd" >> "$GATES"
    else printf '%s\tFAIL\t%s\n' "$name" "$cmd" >> "$GATES"; fi
}
if touches '\.java$|^scripts/|^kof-.*/src/main/'; then
    run_gate check_500 "${AGENT_VERIFY_CHECK500:-bash scripts/check_500.sh}"
fi
if touches '\.md$'; then
    run_gate docs_lang "${AGENT_VERIFY_DOCSLANG:-bash scripts/docs-lang.sh check}"
fi
if touches '(^|/)(kof-runtime|stdlib)/|stdlib_boundary'; then
    run_gate stdlib_boundary "${AGENT_VERIFY_STDLIB:-bash scripts/check_stdlib_boundary.sh}"
fi
DIST=0; touches '^kof-cli/|^scripts/install|^bin/|(^|/)pom\.xml$' && DIST=1

# --- 3) avaliação (risco, smoke, matriz adversarial, cross-target) ----------------------------
python3 - "$EJ" "$D" "$VAL_RC" "$risk_arg" "$MATRIX" "$repo" "$DIST" "$GATES" <<'PYEOF'
import json, sys, os, subprocess
ej, D, val_rc, risk_arg, matrix, repo, dist, gates = sys.argv[1:9]
ev = json.load(open(ej, encoding="utf-8"))
problems, incomplete, checks = [], [], []

def rank(r): return {"low": 1, "medium": 2, "high": 3}.get(r, 0)
risk = ev.get("risk", "medium")
if rank(risk_arg) > rank(risk): risk = risk_arg            # o arg só SOBE o risco

# 1) manifesto
for line in open(os.path.join(D, "validation.txt"), encoding="utf-8").read().splitlines():
    if not line.strip() or line.startswith("OK"): continue
    if line.startswith("SEM_TESTES") and risk == "low": continue   # LOW (docs/teste puro): só os gates aplicáveis
    (incomplete if line.startswith("NOT_RUN") else problems).append(line)
checks.append({"name": "evidence_manifest", "status": "FAIL" if problems else ("INCOMPLETE" if incomplete else "PASS")})

# 2) gates baratos
for line in open(gates, encoding="utf-8").read().splitlines():
    name, st, cmd = line.split("\t", 2)
    checks.append({"name": name, "status": st, "command": cmd})
    if st != "PASS": problems.append(f"GATE_FALHOU: {name}")

# 3) distribuição/CLI: package smoke fora do diretório do repo, PASS, no SHA
if dist == "1" and rank(risk) >= 2:
    sm = ev.get("package_smoke")
    if not sm: problems.append("SEM_PACKAGE_SMOKE: distribuição/CLI tocada sem smoke do artefato empacotado")
    else:
        if sm.get("status") != "PASS": problems.append("PACKAGE_SMOKE_NAO_PASS")
        if sm.get("sha_tested") != ev.get("head_sha"): problems.append("PACKAGE_SMOKE_SHA_DIFERENTE")
        repo_abs = os.path.realpath(repo)
        cwd = os.path.realpath(sm.get("cwd") or "")
        if cwd == repo_abs or cwd.startswith(repo_abs + os.sep):
            problems.append("PACKAGE_SMOKE_DENTRO_DO_REPO: rodar fora do diretório favorável do módulo (família #550)")
    checks.append({"name": "package_smoke", "status": "FAIL" if any(p.startswith(("SEM_PACKAGE", "PACKAGE_SMOKE")) for p in problems) else "PASS"})

# 4) HIGH: matriz adversarial + cross-target
if risk == "high":
    rules = set()
    for r in ev.get("risk_reasons", []):
        if "(" in r and r.rstrip().endswith(")"): rules.add(r[r.rindex("(") + 1:-1])
    domain_of = {"ffi-abi": "ffi-abi", "nullability": "nullability", "generics-erasure": "generics-erasure",
                 "native-backend": "cross-native", "cross-target-backend": "cross-native",
                 "concurrency": "generic-high", "gc-memory": "generic-high",
                 "optimizer-descriptors": "generic-high", "security-sensitive": "generic-high",
                 "high-risk-keyword": "generic-high", "manual": "generic-high"}
    domains = {domain_of[r] for r in rules if r in domain_of}
    # decisões de linguagem/governança: sem código a atacar; o gate é o verifier independente
    if not domains and not (rules & {"language-decisions", "agent-governance"}): domains = {"generic-high"}
    passed = {t["label"] for t in ev.get("tests", []) if t.get("kind") == "adversarial" and t.get("status") == "PASS"}
    failed = {t["label"] for t in ev.get("tests", []) if t.get("kind") == "adversarial" and t.get("status") != "PASS"}
    rows = [l.rstrip("\n").split("\t") for l in open(matrix, encoding="utf-8") if l.strip() and not l.startswith("#")]
    for dom in sorted(domains - {"cross-native"}):
        req = [r[1] for r in rows if r[0] == dom]
        if dom == "generic-high":
            ok = bool(passed)
            if not ok: problems.append("MATRIZ_ADVERSARIAL_AUSENTE: HIGH exige ao menos 1 caso adversarial PASS (--kind adversarial)")
            checks.append({"name": "adversarial:" + dom, "status": "PASS" if ok else "FAIL"})
            continue
        missing = [l for l in req if l not in passed]
        for l in missing:
            problems.append(f"MATRIZ_ADVERSARIAL_FALTA: {dom}/{l}" + (" (FALHOU)" if l in failed else ""))
        checks.append({"name": "adversarial:" + dom, "status": "PASS" if not missing else "FAIL", "missing": missing})
    if "cross-native" in domains:
        ct = ev.get("cross_target", {})
        for t in ("x86", "riscv64", "aarch64"):
            e = ct.get(t)
            if e is None: problems.append(f"CROSS_TARGET_SEM_REGISTRO: {t} (PASS via run --kind cross ou NOT_RUN com motivo)")
            elif e.get("status") == "NOT_RUN": incomplete.append(f"NOT_RUN: cross_target.{t} ({e.get('reason')})")
            elif e.get("status") != "PASS": problems.append(f"CROSS_TARGET_FALHOU: {t}")
        checks.append({"name": "cross_target", "status": "PASS" if not any("CROSS_TARGET" in p for p in problems) and not any("cross_target" in i for i in incomplete) else "INCOMPLETE/FAIL"})

verdict = "BLOCK" if problems else ("INCOMPLETE" if incomplete else "PASS")
out = {"sha": ev.get("head_sha"), "risk": risk, "verdict": verdict, "problems": problems,
       "not_run": incomplete, "checks": checks, "independent_verifier_required": risk == "high"}
json.dump(out, open(os.path.join(D, "deterministic.json"), "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print(f"verdict={verdict} risk={risk} sha={str(ev.get('head_sha'))[:12]}")
for p in problems: print("  BLOCK:", p)
for i in incomplete: print("  INCOMPLETE:", i)
sys.exit({"PASS": 0, "BLOCK": 1, "INCOMPLETE": 3}[verdict])
PYEOF
