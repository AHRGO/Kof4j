[English](automation.md) | [Português](automation.pt_BR.md)

# Idiomas — Automação (scripts, jobs, pipelines, CI/CD)

**Status:** disponível (`kof.workflow` 2.1.2 + bundle 2.1.3: retry/deadLetter/checkpoint/schedule) · **Introduzido:** 0.4.0-beta (set 2026) · **Visão:** `docs/architecture/UNIVERSAL-PLATFORM-VISION.md` §4.3

## O que é

O anti-fragmento do `Bash + Python + YAML + jq + sed`: um pipeline é **código
Kof** — tipado, testável, revisável — e o *runner* é tooling. Não existe nem
vai existir "jobs YAML" no Kof (VISION §4.3, "O que NÃO fazer"). Os passos
chamam `kof.process`; o DAG, retry, dead-letter, checkpoint e schedule vêm do
`kof.workflow`; o arquivo inteiro compila e roda na mesma ferramenta do resto
do produto.

## RUIM → BOM

```bash
# ❌ NUNCA — a pilha fragmentada, colada por exit codes que ninguém tipa
build() { mvn package || exit 1; }
deploy() { curl -f ... ; }
on_failure() { jq -r .reason dead.json; }
```

```kof
// ✅ IDIOMÁTICO — o mesmo pipeline como código Kof tipado
import kof.workflow

main() {
    val compile = job("compile", () -> {
        val r = process.run("mvn", "-o", "-q", "package")
        return r.exitCode == 0
    })
    val test = job("test", () -> {
        val r = process.run("mvn", "-o", "-q", "test")
        return r.exitCode == 0
    }).after(compile)
    val ship = job("ship", () -> {
        return process.run("kof", "deploy", "dist").exitCode == 0
    }).after(test)
    val rep = dag(listOf(compile, test, ship)).run()
    println(rep.summary())
    if (!rep.allOk()) { throw "pipeline red: " + rep.summary() }
}
```

Um `throw` sem captura sai do processo com exit **diferente de zero** —
medido: pipeline verde sai 0, vermelho sai 1 com `pipeline red: ...`
nomeando os jobs que falharam
(`StdlibIdiomsCompileTest#greenPipelineExitsZeroRedPipelineExitsNonZero`).
Esse *é* o contrato de CI hoje: `kof run pipeline.kf` em qualquer runner
(GitHub Actions, cron, terminal de humano).

## As faces de confiabilidade (puro Kof, tudo no `Report` tipado)

```kof
val d = dag(listOf(build, flaky))

// retry com backoff tipado ((tentativa:Int)->ms): exponential(100, 2) ou o seu
d.retry(flaky, 3, exponential(100, 2))
d.retryFixed(build, 2)                       // sem espera

// dead-letter: Report.dead SEMPRE guarda "job: motivo" de todo job que
// esgotou retry; o sink durável é opt-in por job:
d.deadLetter(flaky, (nome: String, motivo: String) -> {
    println("dlq: " + nome + " -> " + motivo)
    return true
})

// checkpoint em kof.db/kof.orm (NUNCA um subsistema novo): job concluído
// restaura como succeeded e NÃO re-executa na próxima corrida
val ck = checkpoint(d, "jdbc:h2:mem:ci;DB_CLOSE_DELAY=-1", "release")

// agendamento estilo cron, delegando ao scheduler.at; devolve o id
val id = schedule(ck, "0 3 * * *")
```

Campos do `Report`: `succeeded`, `failed`, `skipped` (cascata), `retries`
(`"job: tentativas=N"`), `dead` (`"job: motivo"`), `allOk()`, `summary()`.
Job falho cascateia `skipped` nos dependentes transitivos; ciclo é rejeitado
pelo `run()` com mensagem acionável.

## Semântica real (verificado — medido 19/09)

| Face | JVM | SCRIPT | JS | NATIVE |
|---|---|---|---|---|
| `job`/`dag`/`after`/`run`/`retry`/`deadLetter` | ✅ | `COMP003` (alvo de compilação; o interpretador roda o host puro-Kof à parte) | ✅ | ✅ compila (golden `WorkflowE2ETest`) |
| `schedule(dag, expr)` | ✅ (delega `scheduler.at`) | — | ✅ | compila; **`CRON001` em runtime** (stub honesto — nunca silencioso) |
| `checkpoint(dag, db, nome)` | ✅ (H2 via `kof.db`) | — | ✅ | compila; **`ORM001` em runtime** (stub honesto) |

Guardas: `WorkflowE2ETest` (goldens paridade byte JVM==JS),
`WorkflowPrimitivesE2ETest`,
`StdlibIdiomsCompileTest#automationWorkflowFormsCompile` (toda forma acima
compila nos 4 alvos; gates impressos, não escondidos).

## O que NÃO fazer

- **Não inventar YAML/JSON de jobs** — o pipeline é o código; um formato de
  config seria um segundo compilador não-tipado.
- **Não reimplementar shell** — chame as ferramentas reais via `kof.process`
  (`run`/`spawn`/pipes/stdin; é a regra interop-first R9).
- **Não farejar o `Report` à mão** para decidir CI — o idioma throw →
  exit-code é o contrato; `allOk()` decide.
- **Não chamar fila de mensagens** onde você tem um DAG: cascata de falha +
  lista `dead` é a resposta da linguagem (`kof.mq` é pub/sub, ferramenta
  diferente).

## Nota do runner (linha 2.6 — design aberto)

`kof workflow run <file.kf>` como comando *dedicado* (saída vermelha mais
bonita, flags de relatório estruturado) está na fila 2.6 do
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`; o contrato de comando/exit é decisão
da mantenedora (regra 6). Nada bloqueia CI hoje: `kof run pipeline.kf` já dá
verde=0 / vermelho≠0.
