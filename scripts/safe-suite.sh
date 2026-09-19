#!/usr/bin/env bash
# safe-suite.sh — runner de suíte com proteção de host (regra 6: as duas mortes
# de 19/09 foram MEDIDAS, cada guarda abaixo mata uma causa-raiz real):
#
#   Morte 1 (17:40, global OOM): DOIS agentes rodando `mvn` ao mesmo tempo na
#     árvore compartilhada (heartbeat corrompido anexado no servidor errado) →
#     forks do surefire empilhados. Guarda: flock de instância ÚNICA por host.
#   Morte 2 (18:42, global OOM): `kill -STOP` no JDT LS (mitigação da corrida
#     de stub ECJ) → o health-check do redhat.java (`jcmd <pid> VM.uptime`) não
#     tem timeout nem cleanup: cada ping congela no attach para sempre. 395
#     jcmd pendurados (visto no despejo do kernel), cada um mapeando o espaço
#     do alvo via ptrace → RAM esgotada; as VÍTIMAS do OOM foram `code` e
#     `opencode`, nunca o próprio invasor. Guardas: watchdog de jcmd + recusa
#     rodar com qualquer java em estado T (SIGSTOP).
#
# A alternativa correta ao "pare o JDT" é no HOST:
#   "java.autobuild.enabled": false  (settings.json do VS Code)
# — sem autobuild o JDT não escreve em target/classes e não precisa ser pausado.
#
# Uso:
#   scripts/safe-suite.sh                       # suíte completa (reatores default)
#   scripts/safe-suite.sh -pl kof-compiler -am -Dtest=WorkflowE2ETest
#   SAFE_SUITE_LOG=/tmp/x.log scripts/safe-suite.sh
# O mvn roda em PRIMEIRO PLANO neste script (chame com setsid/nohup você mesmo);
# o log é tee'd para SAFE_SUITE_LOG e o resumo sai no final, sempre.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

LOG="${SAFE_SUITE_LOG:-/tmp/opencode/safe-suite-$(date +%Y%m%d-%H%M%S).log}"
LOCK="${SAFE_SUITE_LOCK:-/tmp/opencode/kof-suite.lock}"
mkdir -p "$(dirname "$LOG")" "$(dirname "$LOCK")"

kill_stale_jcmd() {
  # ping legítimo do redhat.java responde em ms; >120s = congelado no attach
  local victims
  victims=$(ps -o pid=,etimes=,comm= -C jcmd 2>/dev/null | awk '$2 > 120 { print $1 }')
  [ -n "$victims" ] || return 0
  echo "safe-suite: matando jcmd congelado(s): $(echo $victims | tr '\n' ' ')" >&2
  # shellcheck disable=SC2086
  kill -9 $victims 2>/dev/null || true
}

# guarda 3: nenhum java SIGSTOPado nesta máquina (o estado que gerou a morte 2)
STOPPED_JAVA=$(ps -eo stat=,comm= | awk '$2 == "java" && $1 ~ /^T/ { c++ } END { print c + 0 }')
if [ "$STOPPED_JAVA" -gt 0 ]; then
  echo "safe-suite: RECUSADO — $STOPPED_JAVA processo(s) java em estado T (SIGSTOP)." >&2
  echo "  Não pause o JDT LS; use no host \"java.autobuild.enabled\": false e" >&2
  echo "  libere os pausados: kill -CONT <pid>. (causa da morte 19/09 18:42)" >&2
  ps -eo pid=,stat=,comm= | awk '$2 ~ /^T/ && $3 == "java" { print "  pid " $1 " parado" }' >&2
  exit 1
fi

# guarda 1: flock de instância única (herdado pelo mvn; solta quando a suíte acaba)
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "safe-suite: RECUSADO — outra suíte segura a lock $LOCK." >&2
  echo "  (causa da morte 19/09 17:40: dois mvn na mesma árvore)" >&2
  exit 1
fi

# guarda 2: watchdog pontual — jcmd congelado de antes não entra carona na suíte
kill_stale_jcmd

# guarda 4: teto de heap do JVM mestre (forks já levam -Xmx1g do argLine do pom raiz)
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1g}"

export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-25}"
export PATH="$HOME/tools/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$PATH"

if [ "$#" -eq 0 ]; then
  set -- -o test -Dmaven.test.failure.ignore=true
fi
echo "safe-suite: mvn $*  ->  $LOG"
# clean de reator INTEIRO antes de qualquer coisa: a corrida de stub ECJ nao e
# so do kof-compiler — em 19/09 19:1x a suíte limpa pegou KofJsRunner (runtime)
# contaminado no meio da corrida ("Unresolved compilation problems: Arena.
# allocateFrom" = API que o JRE 21 do JDT nao tem). rm pontual nao basta.
CLEAN_ARGS=()
for a in "$@"; do
  case "$a" in
    -o|--offline) CLEAN_ARGS+=("-o") ;;
  esac
done
mvn "${CLEAN_ARGS[@]+"${CLEAN_ARGS[@]}"}" clean -q >"$LOG.clean" 2>&1
mvn "$@" >"$LOG" 2>&1
RC=$?

# watchdog pós-execução: se a extensão congelou pings durante a corrida, limpa
kill_stale_jcmd

echo "----- resumo (rc=$RC) -----"
grep -hE "Tests run:.*Time elapsed" "$LOG" 2>/dev/null \
  | awk -F'[ ,]+' '{ t += $3; f += $5; e += $7; s += $9 }
                   END { printf "TOTAL: tests=%d failures=%d errors=%d skipped=%d\n", t, f, e, s }'
echo "log completo: $LOG"
exit $RC
