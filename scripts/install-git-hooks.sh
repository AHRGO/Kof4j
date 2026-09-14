#!/bin/bash
# Instala os git hooks versionados em .githooks/ (PACTO DE AGREGAÇÃO, 14/09).
# Uso: scripts/install-git-hooks.sh
# Storage compartilhado = uma instalação vale para todas as máquinas/lanes.
set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_DIR="$REPO_ROOT/.githooks"
TARGET_DIR="$REPO_ROOT/.git/hooks"
mkdir -p "$TARGET_DIR"
for hook in "$HOOKS_DIR"/*; do
    name="$(basename "$hook")"
    cp "$hook" "$TARGET_DIR/$name"
    chmod +x "$TARGET_DIR/$name"
    echo "instalado: .git/hooks/$name"
done
