#!/usr/bin/env bash
#
# install-test.sh — prova local (sem rede) do scripts/install.sh usando um
# `curl` FAKE no PATH + um tar.gz de fixture REAL (checksum de verdade).
#
# Cenarios:
#   1. install feliz (binario presente, symlink current, checksum verificado)
#   2. checksum errado  => falha (exit != 0) com mensagem de checksum
#   3. API fora         => falha (exit != 0) sugerindo --version
#   4. idempotencia     => 2a execucao nao duplica a linha do shell config
#   5. --uninstall      => remove o prefix e as linhas do shell config
#   6. plataforma desconhecida (uname fake) => exit 2
#   7. resolve_version filtra pela plataforma (decoy windows ignorado)
#
# Uso: scripts/tests/install-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
INSTALL="scripts/install.sh"
FAILED=0
VERSION="0.4.4-beta"

detect_platform() {
    local os arch
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    case "$os" in linux*) os=linux ;; darwin*) os=macos ;; esac
    arch="$(uname -m)"
    case "$arch" in x86_64|amd64) arch=x86_64 ;; aarch64|arm64) arch=arm64 ;; esac
    PLATFORM="$os-$arch"
}
detect_platform

pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

write_checksum() { # $1=archive $2=outfile ; usa a mesma fallback do install.sh
    if command -v sha256sum >/dev/null 2>&1; then
        ( cd "$(dirname "$1")" && sha256sum "$(basename "$1")" ) > "$2"
    else
        ( cd "$(dirname "$1")" && shasum -a 256 "$(basename "$1")" ) > "$2"
    fi
}

build_fixture() { # $1=dir (recebe src/, tar.gz e SHA256SUMS)
    local dir="$1" root="$1/src" dist
    dist="$root/kof-$VERSION-$PLATFORM"
    mkdir -p "$dist/bin"
    cat > "$dist/bin/kof" <<EOF
#!/usr/bin/env bash
printf 'kof $VERSION\\n'
EOF
    chmod +x "$dist/bin/kof"
    printf '%s\n' "$VERSION" > "$dist/VERSION"
    ( cd "$root" && tar -czf "$dir/kof-$VERSION-$PLATFORM.tar.gz" "kof-$VERSION-$PLATFORM" )
    write_checksum "$dir/kof-$VERSION-$PLATFORM.tar.gz" "$dir/SHA256SUMS"
}

make_fake_curl() { # $1=dir $2=archive $3=sums $4=json $5=mode(normal|api_down)
    local dir="$1" archive="$2" sums="$3" json="$4" mode="$5"
    cat > "$dir/curl" <<EOF
#!/usr/bin/env bash
out=""
prev=""
for a in "\$@"; do
    [ "\$prev" = "-o" ] && out="\$a"
    prev="\$a"
done
url="\${@: -1}"
case "\$url" in
    *releases/download/*.tar.gz) cp '$archive' "\$out" ;;
    *releases/download/*SHA256SUMS) cp '$sums' "\$out" ;;
    *releases*)
        if [ "$mode" = "api_down" ]; then echo "API rate limit exceeded" >&2; exit 1; fi
        printf '%s\\n' '$json'
        ;;
    *) echo "fake curl: unexpected url: \$url" >&2; exit 1 ;;
esac
EOF
    chmod +x "$dir/curl"
}

# JSON releases list (newest first), with a decoy tag of ANOTHER platform
# FIRST — proves resolve_version filters by $PLATFORM instead of taking
# the first tag (mirrors the real repo: one release per platform).
JSON="[{\"tag_name\": \"kof-$VERSION-windows-x86_64\"}, {\"tag_name\": \"kof-$VERSION-$PLATFORM\", \"name\": \"Kof $VERSION ($PLATFORM)\"}]"

echo "== cenario 1: install feliz =="
TMP="$(mktemp -d)"; FIX="$(mktemp -d)"
build_fixture "$FIX"
BIN="$TMP/bin"; mkdir -p "$BIN"
make_fake_curl "$BIN" "$FIX/kof-$VERSION-$PLATFORM.tar.gz" "$FIX/SHA256SUMS" "$JSON" normal
out1="$(PATH="$BIN:$PATH" bash "$INSTALL" --version "$VERSION" --prefix "$TMP/prefix" --yes --no-modify-shell 2>&1)"; rc1=$?
[ -x "$TMP/prefix/kof-$VERSION-$PLATFORM/bin/kof" ] && pass "binario presente" || fail "binario ausente em $TMP/prefix"
[ "$(readlink "$TMP/prefix/current")" = "$(cd "$TMP/prefix" && pwd -P)/kof-$VERSION-$PLATFORM" ] && pass "symlink current ok" || fail "symlink current errado"
printf '%s' "$out1" | grep -q "verifying SHA-256 checksum" && pass "checksum verificado" || fail "checksum nao verificado"
printf '%s' "$out1" | grep -q "kof $VERSION" && pass "kof version executado" || fail "kof version nao executado"
[ "$rc1" = 0 ] && pass "exit 0" || fail "exit=$rc1 (esperado 0)"
rm -rf "$TMP" "$FIX"

echo "== cenario 2: checksum errado =="
TMP="$(mktemp -d)"; FIX="$(mktemp -d)"
build_fixture "$FIX"
printf '0000000000000000000000000000000000000000000000000000000000000000  kof-%s-%s.tar.gz\n' "$VERSION" "$PLATFORM" > "$FIX/SHA256SUMS"
BIN="$TMP/bin"; mkdir -p "$BIN"
make_fake_curl "$BIN" "$FIX/kof-$VERSION-$PLATFORM.tar.gz" "$FIX/SHA256SUMS" "$JSON" normal
out2="$(PATH="$BIN:$PATH" bash "$INSTALL" --version "$VERSION" --prefix "$TMP/prefix" --yes --no-modify-shell 2>&1)"; rc2=$?
[ "$rc2" != 0 ] && pass "falhou (exit $rc2)" || fail "aceitou checksum errado (exit 0)"
printf '%s' "$out2" | grep -q "checksum" && pass "mensagem de checksum" || fail "sem mensagem de checksum"
rm -rf "$TMP" "$FIX"

echo "== cenario 3: API fora =="
TMP="$(mktemp -d)"; FIX="$(mktemp -d)"
build_fixture "$FIX"
BIN="$TMP/bin"; mkdir -p "$BIN"
make_fake_curl "$BIN" "$FIX/kof-$VERSION-$PLATFORM.tar.gz" "$FIX/SHA256SUMS" "$JSON" api_down
out3="$(PATH="$BIN:$PATH" bash "$INSTALL" --prefix "$TMP/prefix" --yes --no-modify-shell 2>&1)"; rc3=$?
[ "$rc3" != 0 ] && pass "falhou (exit $rc3)" || fail "API fora nao falhou (exit 0)"
printf '%s' "$out3" | grep -q -- "--version" && pass "sugere --version" || fail "nao sugere --version"
rm -rf "$TMP" "$FIX"

echo "== cenario 4: idempotencia do shell config =="
TMP="$(mktemp -d)"; HOME_D="$TMP/home"; PREFIX_D="$TMP/prefix"; mkdir -p "$HOME_D"
FIX="$(mktemp -d)"; build_fixture "$FIX"
BIN="$TMP/bin"; mkdir -p "$BIN"
make_fake_curl "$BIN" "$FIX/kof-$VERSION-$PLATFORM.tar.gz" "$FIX/SHA256SUMS" "$JSON" normal
for i in 1 2; do
    PATH="$BIN:$PATH" SHELL=/bin/zsh HOME="$HOME_D" \
        bash "$INSTALL" --version "$VERSION" --prefix "$PREFIX_D" --yes >/dev/null 2>&1
done
count="$(grep -c "current/bin" "$HOME_D/.zshrc" 2>/dev/null || true)"
[ "$count" = 1 ] && pass "linha PATH unica ($count)" || fail "linha PATH duplicada ($count)"
rm -rf "$TMP" "$FIX"

echo "== cenario 5: uninstall =="
TMP="$(mktemp -d)"; HOME_D="$TMP/home"; PREFIX_D="$TMP/prefix"; mkdir -p "$HOME_D"
FIX="$(mktemp -d)"; build_fixture "$FIX"
BIN="$TMP/bin"; mkdir -p "$BIN"
make_fake_curl "$BIN" "$FIX/kof-$VERSION-$PLATFORM.tar.gz" "$FIX/SHA256SUMS" "$JSON" normal
PATH="$BIN:$PATH" SHELL=/bin/zsh HOME="$HOME_D" \
    bash "$INSTALL" --version "$VERSION" --prefix "$PREFIX_D" --yes >/dev/null 2>&1
[ -d "$PREFIX_D" ] && pass "instalado antes do uninstall" || fail "instalacao previa ausente"
SHELL=/bin/zsh HOME="$HOME_D" bash "$INSTALL" --prefix "$PREFIX_D" --uninstall >/dev/null 2>&1
[ ! -e "$PREFIX_D" ] && pass "prefix removido" || fail "prefix ainda existe"
if grep -qs "current/bin" "$HOME_D/.zshrc" 2>/dev/null; then fail "PATH ainda contem kof"; else pass "PATH limpo"; fi
rm -rf "$TMP" "$FIX"

echo "== cenario 6: plataforma desconhecida =="
TMP="$(mktemp -d)"; BIN="$TMP/bin"; mkdir -p "$BIN"
cat > "$BIN/uname" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = "-s" ]; then printf 'plan9\n'; else printf 'x86_64\n'; fi
EOF
chmod +x "$BIN/uname"
out6="$(PATH="$BIN:$PATH" bash "$INSTALL" --version "$VERSION" --prefix "$TMP/prefix" --yes --no-modify-shell 2>&1)"; rc6=$?
[ "$rc6" = 2 ] && pass "exit 2" || fail "exit=$rc6 (esperado 2)"
printf '%s' "$out6" | grep -qi "unsupported" && pass "mensagem unsupported" || fail "sem mensagem unsupported"
rm -rf "$TMP"

echo "== cenario 7: resolve_version filtra pela plataforma =="
TMP="$(mktemp -d)"; FIX="$(mktemp -d)"
build_fixture "$FIX"
BIN="$TMP/bin"; mkdir -p "$BIN"
make_fake_curl "$BIN" "$FIX/kof-$VERSION-$PLATFORM.tar.gz" "$FIX/SHA256SUMS" "$JSON" normal
out7="$(PATH="$BIN:$PATH" bash "$INSTALL" --prefix "$TMP/prefix" --yes --no-modify-shell 2>&1)"; rc7=$?
[ "$rc7" = 0 ] && pass "exit 0 sem --version" || fail "exit=$rc7 (esperado 0)"
printf '%s' "$out7" | grep -q "kof $VERSION" && pass "resolveu kof-$VERSION (decoy windows ignorado)" || fail "nao resolveu a release da plataforma"
rm -rf "$TMP" "$FIX"

if [ "$FAILED" = 1 ]; then
    echo "== RESULTADO: FALHOU =="
    exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
