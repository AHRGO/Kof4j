#!/usr/bin/env bash
# sync-push.sh — a regra de sync-on-push mecanizada (AGENTS.md §Multi-agent,
# diretriz da mantenedora 18/09): NENHUM commit sobe de base dessincronizada e
# NENHUM commit esperado vira "tarefa fantasma" (outro agente reatribui a lane:
# "owner sumiu = tarefa morta").
#
# Faz, na ordem: fetch → pull --rebase --autostash → push → verificação 0/0.
# Conflito de rebase = PARA e manda resolver com edição cirúrgica dos dois
# lados (lição d7dba433: NUNCA `checkout --ours/theirs` às cegas em arquivo de
# registro — DOING.md/known-bugs).
#
# Uso:  scripts/sync-push.sh                 # push normal (hooks/gates rodam)
#       scripts/sync-push.sh --no-verify     # só com a CAUSA registrada no
#                                            # commit e no DOING.md (gate RED
#                                            # por dívida pré-existente alheia
#                                            # — nunca p/ esconder falha própria)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

branch=$(git rev-parse --abbrev-ref HEAD)
[ "$branch" != "HEAD" ] || { echo "fora de branch (detached/rebase em curso): primeiro conclua/abra o rebase" >&2; exit 1; }

if ! git pull --rebase --autostash origin "$branch"; then
    echo "== CONFLITO no rebase de $branch. Resolva os blocos <<<<<<< preservando os DOIS lados," >&2
    echo "   depois: git add <arquivos> && git rebase --continue && $0" >&2
    echo "   (registro truncado = incidente d7dba433; na dúvida: git rebase --abort)" >&2
    exit 1
fi

git push "$@" origin "$branch"
rc=$?
[ $rc -eq 0 ] || { echo "== push falhou (rc=$rc) — rode scripts/codeql-gate.sh --fast p/ ver a causa do hook" >&2; exit $rc; }

git fetch -q origin
behind=$(git rev-list --count "HEAD..origin/$branch")
ahead=$(git rev-list --count "origin/$branch..HEAD")
echo "== SYNCED $branch: ahead=$ahead behind=$behind"
[ "$ahead" = "0" ] && [ "$behind" = "0" ]
