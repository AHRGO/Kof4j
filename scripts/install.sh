#!/usr/bin/env bash
#
# install.sh — install the official Kof distribution from GitHub Releases.
#
# Downloads kof-<version>-<platform>.tar.gz for this system, verifies the
# SHA-256 checksum, extracts it under <prefix>/kof-<version>-<platform> and
# keeps a stable <prefix>/current symlink. Optionally appends the bin dir to
# the shell config, idempotently. Linux and macOS only.
#
# Usage:
#   scripts/install.sh [--version <ver>] [--prefix <dir>]
#                      [--yes] [--no-modify-shell] [--uninstall]
#
#   --version <ver>        install a specific version (e.g. 0.4.4-beta);
#                          default: latest release for this platform
#   --prefix <dir>         install directory (default: ~/.local/share/kof)
#   --yes                  do not ask for confirmation
#   --no-modify-shell      do not touch the shell config
#   --uninstall            remove the installation and the PATH lines
#   -h, --help             show this help
#
# Env:
#   KOF_INSTALL_VERSION    same as --version
#
# Exit codes: 0 ok, 1 error, 2 unsupported platform.
set -euo pipefail

REPO="KofLang/Kof4j"
BASE_URL="https://github.com/$REPO/releases/download"

VERSION=""
PREFIX=""
ASSUME_YES=false
MODIFY_SHELL=true
UNINSTALL=false
PLATFORM=""

usage() {
    cat <<'EOF'
Usage: scripts/install.sh [--version <ver>] [--prefix <dir>]
                         [--yes] [--no-modify-shell] [--uninstall]

Install the official Kof distribution from GitHub Releases (Linux/macOS).

  --version <ver>        install a specific version (e.g. 0.4.4-beta);
                         default: latest release for this platform
  --prefix <dir>         install directory (default: ~/.local/share/kof)
  --yes                  do not ask for confirmation
  --no-modify-shell      do not touch the shell config
  --uninstall            remove the installation and the PATH lines
  -h, --help             show this help

Env:
  KOF_INSTALL_VERSION    same as --version

Exit codes: 0 ok, 1 error, 2 unsupported platform.
EOF
}

log() { printf 'kof-install: %s\n' "$*" >&2; }
die() { printf 'kof-install: error: %s\n' "$*" >&2; exit 1; }

detect_platform() {
    local os arch
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    case "$os" in
        linux*) os=linux ;;
        darwin*) os=macos ;;
        *) printf 'kof-install: unsupported OS "%s" (install.sh covers Linux and macOS)\n' "$os" >&2
           exit 2 ;;
    esac
    arch="$(uname -m)"
    case "$arch" in
        x86_64|amd64) arch=x86_64 ;;
        aarch64|arm64) arch=arm64 ;;
        *) printf 'kof-install: unsupported architecture "%s"\n' "$arch" >&2
           exit 2 ;;
    esac
    PLATFORM="$os-$arch"
    log "platform: $PLATFORM"
}

resolve_version() {
    if [ -n "$VERSION" ]; then
        printf '%s' "$VERSION"
        return
    fi
    log "querying GitHub API for the latest $PLATFORM release..."
    local body
    # NOTE: KofLang/Kof4j publishes ONE release PER PLATFORM
    # (kof-<ver>-linux-x86_64, kof-<ver>-macos-arm64, kof-<ver>-windows-x86_64),
    # so /releases/latest (a single tag) is NOT guaranteed to match $PLATFORM.
    # List releases (newest first) and pick the newest tag for this platform.
    body="$(curl -fsSL "https://api.github.com/repos/$REPO/releases?per_page=30")" || {
        printf 'kof-install: failed to query the GitHub API (rate limit or no network)\n' >&2
        printf 'kof-install: retry later or pin the version: scripts/install.sh --version <ver>\n' >&2
        exit 1
    }
    local tag ver
    tag="$(printf '%s\n' "$body" \
        | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
        | grep "^kof-.*-$PLATFORM\$" | head -1)" || true
    if [ -z "$tag" ]; then
        printf 'kof-install: no release found for platform "%s" (latest 30 releases)\n' "$PLATFORM" >&2
        printf 'kof-install: pin the version explicitly: scripts/install.sh --version <ver>\n' >&2
        exit 1
    fi
    ver="${tag#kof-}"
    ver="${ver%-$PLATFORM}"
    printf '%s' "$ver"
}

download_archive() { # $1=version $2=tmpdir ; echoes the archive path
    local ver="$1" tmp="$2"
    local base="$BASE_URL/kof-$ver-$PLATFORM"
    local archive="kof-$ver-$PLATFORM.tar.gz"
    log "downloading $archive ..."
    curl -fL --retry 3 -o "$tmp/$archive" "$base/$archive" \
        || die "download failed: $base/$archive"
    log "downloading SHA256SUMS ..."
    curl -fL --retry 3 -o "$tmp/SHA256SUMS" "$base/SHA256SUMS" \
        || die "download failed: $base/SHA256SUMS"
    printf '%s' "$tmp/$archive"
}

verify_checksum() { # $1=archive path
    local dir name
    dir="$(dirname "$1")"
    name="$(basename "$1")"
    log "verifying SHA-256 checksum ..."
    if command -v sha256sum >/dev/null 2>&1; then
        ( cd "$dir" && sha256sum -c SHA256SUMS ) || die "checksum verification failed for $name"
    else
        ( cd "$dir" && shasum -a 256 -c SHA256SUMS ) || die "checksum verification failed for $name"
    fi
}

install_dist() { # $1=version $2=archive $3=prefix
    local ver="$1" archive="$2" prefix="$3"
    local target="$prefix/kof-$ver-$PLATFORM"
    mkdir -p "$prefix"
    log "extracting to $target ..."
    tar -xzf "$archive" -C "$prefix"
    [ -x "$target/bin/kof" ] || die "extraction did not produce $target/bin/kof"
    ln -sfn "$target" "$prefix/current"
    log "linked $prefix/current -> $target"
}

detect_shell_config() {
    local shell_name
    shell_name="$(basename "${SHELL:-bash}")"
    case "$shell_name" in
        zsh)  printf '%s' "$HOME/.zshrc" ;;
        bash) printf '%s' "$HOME/.bashrc" ;;
        *)    printf '' ;;
    esac
}

update_shell_config() { # $1=prefix
    local prefix="$1" rc line marker ans
    rc="$(detect_shell_config)"
    [ -n "$rc" ] || { log "shell not recognized — add to PATH manually"; return 0; }
    line="export PATH=\"$prefix/current/bin:\$PATH\""
    marker="# kof — managed by scripts/install.sh"
    if [ -f "$rc" ] && grep -qsF "$prefix/current/bin" "$rc"; then
        log "PATH already configured in $rc"
        return 0
    fi
    if [ "$ASSUME_YES" = false ]; then
        read -r -p "Add '$prefix/current/bin' to PATH in $rc? [Y/n] " ans
        case "${ans:-y}" in
            n|N) log "shell config skipped"; return 0 ;;
        esac
    fi
    printf '\n%s\n%s\n' "$marker" "$line" >> "$rc"
    log "PATH line added to $rc (run 'source $rc' to apply)"
}

verify_install() { # $1=prefix
    local prefix="$1"
    log "verifying installation ..."
    "$prefix/current/bin/kof" version
}

uninstall() {
    local prefix="$PREFIX" rc marker tmp
    # Safety guard: refuse to rm -rf dangerous prefixes ($HOME, /, shallow paths).
    case "$prefix" in
        ""|"/"|"$HOME") die "refusing to uninstall: unsafe prefix '$prefix'" ;;
    esac
    local depth IFS=/
    depth=0
    for _ in $prefix; do depth=$((depth + 1)); done
    [ "$depth" -ge 3 ] || die "refusing to uninstall: prefix '$prefix' is too shallow"
    rc="$(detect_shell_config)"
    marker="# kof — managed by scripts/install.sh"
    tmp="$rc.kof-tmp"
    if [ -n "$rc" ] && [ -f "$rc" ]; then
        if grep -vF "$marker" "$rc" > "$tmp"; then mv "$tmp" "$rc"; else rm -f "$tmp"; fi
        if grep -vF "$prefix/current/bin" "$rc" > "$tmp"; then mv "$tmp" "$rc"; else rm -f "$tmp"; fi
        log "removed PATH lines from $rc"
    fi
    if [ -e "$prefix" ]; then
        rm -rf "$prefix"
        log "removed $prefix"
    fi
}

main() {
    detect_platform
    [ -n "$VERSION" ] || VERSION="$(resolve_version)"
    local archive
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    mkdir -p "$PREFIX"
    PREFIX="$(cd "$PREFIX" && pwd -P)"
    archive="$(download_archive "$VERSION" "$tmp")"
    verify_checksum "$archive"
    install_dist "$VERSION" "$archive" "$PREFIX"
    if [ "$MODIFY_SHELL" = true ]; then
        update_shell_config "$PREFIX"
    else
        log "shell config not modified (--no-modify-shell)"
        printf '  export PATH="%s/current/bin:$PATH"\n' "$PREFIX"
    fi
    verify_install "$PREFIX"
    log "Kof $VERSION installed at $PREFIX/current"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)         [ -n "${2:-}" ] || die "option $1 requires a value"; VERSION="$2"; shift 2 ;;
        --prefix)          [ -n "${2:-}" ] || die "option $1 requires a value"; PREFIX="$2"; shift 2 ;;
        --yes)             ASSUME_YES=true; shift ;;
        --no-modify-shell) MODIFY_SHELL=false; shift ;;
        --uninstall)       UNINSTALL=true; shift ;;
        -h|--help)         usage; exit 0 ;;
        *) printf 'kof-install: unknown option: %s\n\n' "$1" >&2; usage >&2; exit 1 ;;
    esac
done

[ -n "$VERSION" ] || VERSION="${KOF_INSTALL_VERSION:-}"
PREFIX="${PREFIX:-$HOME/.local/share/kof}"

if [ "$UNINSTALL" = true ]; then
    uninstall
    exit 0
fi

main
