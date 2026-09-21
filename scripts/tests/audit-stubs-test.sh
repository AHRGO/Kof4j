#!/usr/bin/env bash
# audit-stubs-test.sh — teste offline (fixture hermética) do inventário de stubs.
#
# RED-first: um TODO/catch-vazio/@Disabled PLANTADO precisa ser detectado; uma
# fixture LIMPA precisa dar 0 (anti-falso-positivo — o ruído "todo"=todos em PT
# é a razão de existir do cuidado); o script é read-only (a árvore não muda) e
# raiz sem */src/main precisa recusar com rc!=0 (nunca um verde mudo).
set -u
cd "$(git rev-parse --show-toplevel)"
S="scripts/audit-stubs.sh"
[ -x "$S" ] || { echo "FALHOU: $S ausente/nao-executavel"; exit 1; }
rc=0
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

# ── fixture suja: 1 TODO (case-sensitive) + 1 catch vazio + 1 @Disabled ──────
R="$T/repo"; mkdir -p "$R/alpha/src/main/java" "$R/alpha/src/test/java" "$R/beta/src/main/java"
cat > "$R/alpha/src/main/java/A.java" <<'J'
package a;
class A {
    void f() {
        // TODO: plantado (deve ser candidato)
        try { g(); } catch (Exception e) {}
    }
    void g() { throw new UnsupportedOperationException("R6 honesto"); }
}
J
cat > "$R/beta/src/main/java/B.java" <<'J'
package b;
class B { int x() { return 0; } }
J
cat > "$R/alpha/src/test/java/ATest.java" <<'J'
package a;
class ATest { @Disabled void t() {} }
J

# (a) raiz sem */src/main => recusa (rc!=0), nunca verde mudo
mkdir -p "$T/empty"
if bash "$S" "$T/empty" >/dev/null 2>&1; then
    echo "FALHOU: raiz sem src/main devia recusar"; rc=1
else
    echo "ok  — raiz sem src/main recusa (rc!=0)"
fi

# (b) read-only: hash da árvore antes == depois
before="$(find "$R" -type f -exec sha256sum {} + | sort | sha256sum)"
OUT="$(bash "$S" "$R" 2>/dev/null)"; orc=$?
after="$(find "$R" -type f -exec sha256sum {} + | sort | sha256sum)"
[ "$orc" -eq 0 ] && echo "ok  — roda rc=0 na fixture" || { echo "FALHOU: rc=$orc"; rc=1; }
[ "$before" = "$after" ] && echo "ok  — read-only (arvore intacta)" \
    || { echo "FALHOU: script escreveu na arvore"; rc=1; }

# (c) TODO plantado detectado
todo="$(printf '%s\n' "$OUT" | sed -n 's/^candidatos TODO\/FIXME:[[:space:]]*//p' | head -1)"
[ "${todo:-0}" -ge 1 ] && echo "ok  — TODO plantado detectado ($todo)" \
    || { echo "FALHOU: TODO nao detectado (=$todo)"; rc=1; }

# (d) catch vazio detectado
ec="$(printf '%s\n' "$OUT" | sed -n 's/^catch vazio:[[:space:]]*//p' | head -1)"
[ "${ec:-0}" -ge 1 ] && echo "ok  — catch vazio detectado ($ec)" \
    || { echo "FALHOU: catch vazio nao detectado (=$ec)"; rc=1; }

# (e) @Disabled em src/test listado
printf '%s\n' "$OUT" | grep -q "ATest.java" && echo "ok  — @Disabled listado" \
    || { echo "FALHOU: @Disabled nao listado"; rc=1; }

# (f) fixture LIMPA => 0 candidatos (anti-falso-positivo)
C="$T/clean"; mkdir -p "$C/x/src/main/java"
cat > "$C/x/src/main/java/C.java" <<'J'
package x;
class C { int y() { return 1; } }
J
OUT2="$(bash "$S" "$C" 2>/dev/null)"
todo2="$(printf '%s\n' "$OUT2" | sed -n 's/^candidatos TODO\/FIXME:[[:space:]]*//p' | head -1)"
ec2="$(printf '%s\n' "$OUT2" | sed -n 's/^catch vazio:[[:space:]]*//p' | head -1)"
if [ "${todo2:-x}" = "0" ] && [ "${ec2:-x}" = "0" ]; then
    echo "ok  — fixture limpa da 0/0 (sem falso-positivo)"
else
    echo "FALHOU: fixture limpa deu todo=$todo2 catch=$ec2"; rc=1
fi

exit "$rc"
