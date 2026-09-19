[English](workflow.md) | [Português](workflow.pt_BR.md)

# Workflows — `kof.workflow`

**Data:** 19 de setembro de 2026
**Status:** MVP implementado (plano universal Estágio 2, linha 2.1, fatia 2.1.2) — `VERSION` 0.4.0-beta

> **Escopo do MVP (Q2, enquete da mantenedora 19/09):** somente `job` / `dag` /
> `after` / `run` / `Report`. `retry`, `checkpoint`, `deadLetter` e `schedule` são o
> bundle de add-ons 2.1.3 — **ainda não** estão nesta superfície.

---

## 1. O que é

`kof.workflow` é uma **camada de composição sobre Kof puro**, escrita em Kof
(resource `dev/kof/workflow-host.kf`) e injetada **flat** pelo compilador quando
você faz `import kof.workflow` — o mesmo mecanismo e a mesma DD-OTP-01 opção A do
`kof.supervisor`. Como o host é um pacote virtual, a superfície é
`job(...)`/`dag(...)` pelados; **não existe prefixo `workflow.` nem identificador
`workflow`** depois do import.

O MVP executa o DAG **sequencial e determinístico** (um laço fixpoint). A camada não
cruza fronteira de runtime — sem threads, sem `await`, sem `process` — então
**não há gate de target**: o mesmo código roda byte-idêntico em JVM e JS e compila
em Native. Os gaps honestos (`PROC001`, `CRON001`, `ORM001`) aparecem apenas nos
*corpos* de job que você escreve, nunca na camada.

## 2. Superfície

```
import kof.workflow

job(String nome, () -> Bool corpo) -> KofWfJob
KofWfJob.after(KofWfJob dep) -> KofWfJob        // encadeável; guarda nulo/auto-dependência
dag(List<KofWfJob> jobs) -> KofWfDag            // guarda dag vazia / nomes duplicados / job nulo
KofWfDag.run() -> KofWfReport                   // fixpoint topológico sequencial

Campos do Report: succeeded failed skipped errors  // List<String> cada
Report.allOk() -> Bool                          // sem falhas, sem skips
Report.summary() -> String                      // "ok=... failed=... skipped=..."
```

Regras:

- O corpo retorna `Bool` (`true` = sucesso). `false` **ou um string lançado**
  falha o job; o motivo lançado entra em `errors` como `"nome: motivo"` (um
  `false` entra como `"nome: false"`).
- Um job falho ou pulado **envenena seus dependentes transitivos** — eles viram
  `skipped` e nunca rodam. Ramos independentes continuam.
- Um **ciclo é rejeitado no run time** com
  `workflow: ciclo detectado entre: ...` (mensagem acionável — nunca hang
  silencioso, invariante do §2 do plano).

## 3. Idiomática

```
import kof.workflow

main() {
    var acc = listOf("start")
    var build = job("build", () -> { acc.add("built"); return true })
    var image = job("image", () -> { acc.add("imaged"); return true }).after(build)
    var ship  = job("ship",  () -> true).after(image)
    var lint  = job("lint",  () -> true)

    var rep = dag(listOf(ship, image, build, lint)).run()   // ordem = deps, não entrada
    println(rep.summary())   // ok=build,lint,image,ship failed= skipped=
    println(rep.allOk())     // true
    println(acc.get(1))      // built — estado de closure prova a sequenciação real
}
```

Um job que chama o shell usa o Result de `kof.process` (a tabela de gaps dele se
aplica):

```
var compile = job("compile", () -> process.run("make", listOf("-j4")).exitCode == 0)
```

`listOf(...)` em todo lugar — Kof não tem literal de lista `[]` nem de mapa `{}`
(medido; travado por negativo em `WorkflowPrimitivesE2ETest`).

## 4. Semântica de falha num relance

| resultado do corpo | Report | dependentes |
|---|---|---|
| `true` | `succeeded` | rodam quando todos os deps succeederem |
| `false` | `failed` + `errors` `"nome: false"` | `skipped` (transitivamente) |
| `throw "why"` | `failed` + `errors` `"nome: why"` | `skipped` (transitivamente) |
| ciclo nos deps | `run()` lança `workflow: ciclo detectado entre: ...` | — |

Guardas de construção (`dag`/`job`/`after` lançam na hora): nome vazio, corpo
nulo, dependência nula, auto-dependência, nomes duplicados na mesma dag, dag
vazia.

## 5. Bordas conhecidas do Kof (achadas ao construir o host)

- Um campo **sem inicializador** logo acima de um campo-lambda quebra o lookahead
  do parser no corpo de classe — inicialize (`String nome = null`). Gramática é
  terreno de regra 6; contornado no host.
- Um lambda-bloco cuja **única saída é `throw`** tipa `Void` — mantenha um
  `return` final para o corpo tipar como `() -> Bool`.
- Uma local de tipo-função **inferida com `var`** a partir de campo perde o tipo —
  anote: `var f: () -> Bool = job.corpo`.

## 6. Prova

`WorkflowE2ETest` 7/7 (goldens exatos de stdout, paridade byte JVM==JS): ordem
linear, cascata de falha, throw com motivo, mensagem de ciclo, conjunto de
guardas, corpos reais via lista capturada, compilação Native. A camada de formas é
travada por `WorkflowPrimitivesE2ETest` (6/6, incl. os pins negativos de sintaxe).
Plano: `docs/development/workflow-plan.pt_BR.md` §5.
