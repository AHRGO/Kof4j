[English](shell-plan.md) | [Português](shell-plan.pt_BR.md)

# `kof.shell` — shell idiomático sobre `kof.process` (plano de design · Estágio 2 · TIER 2.2)

> **Status: PROPOSTO (18/09) — aguardando decisão de escopo da mantenedora (regra 6). Zero código.**
> Este arquivo existe porque a linha **2.2** de `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` é uma
> linha `🔵` solta com dono `—`, enquanto toda outra frente verde da `future/`
> (`value-records`, `scoped-resources`, `PLAN-BAREMETAL-BOOT`, …) tem um plano concreto.
> Ele **propõe** transformar essa linha num todo executável; **não** autoriza abrir a
> frente — essa decisão é da mantenedora. Também **não** implementa nada: `kof.shell` não
> está no lexer, no parser, em backend algum nem na stdlib hoje (medido 18/09). Baseado na
> superfície **real e medida** de `kof.process` (§4).

## 1. Objetivo
Um único idiomato tipado e componível para conduzir comandos do SO — rodar, capturar,
encadear em pipeline, decidir por código de saída — **sem** jamais stringificar um comando
para `sh -c`. Hoje `kof.process` já dá os primitivos (ver §4); o que falta é ergonomia:
fiação de pipeline, modelagem de `cwd`/`env` e uma história honesta por alvo. `kof.shell`
é **açúcar sobre `kof.process`** — não adiciona primitivo de runtime novo onde `process`
já cobre a chamada, herdando o comportamento de `process` em vez de bifurcar um paralelo.

## 2. Proposta — forma de função, NÃO infixo de shell (a decisão-chave de design)
Fato medido: o `Lexer`/`Parser` do Kof **não** têm backtick, `$()`, pipe `|`, `&&`, `||` nem
redirecionamento `>`, e adicioná-los é **mudança de gramática (regra 6)**. Então `kof.shell`
é proposto como uma **API de função/builder** sobre os namespaces existentes, ex.:

```
import kof.shell

var r = shell.run("git", ["status", "--short"])      // Result reaproveitado de kof.process
if (r.ok()) println(r.stdout)

var n = shell.run("wc", ["-l"], cwd: "/src", env: {"CC": "clang"}).stdout.trim()

// stdout de A -> stdin de B, argv mantido como listas (nunca reanalisado por um shell)
var out = shell.pipeline(shell.cmd("ls", ["-1"]), shell.cmd("wc", ["-l"])).stdout
```

Invariantes de design (emprestados de dívidas/precedentes do repo, não inventados):
- **argv é sempre `List<String>`** — o comando **nunca** é concatenado numa string entregue a
  um shell (essa é a classe de segurança `concatenated-command-line` que o repo re-limpa o
  tempo todo; `shell` deve ser o bom caminho que torna difícil se autossabotar).
- `Result` **é** o `Result` de `kof.process` (`stdout`, `stderr`, `exitCode`) — um tipo só, sem
  segunda forma para manter em sincronia.
- `ok()` == `exitCode == 0`; qualquer outro portão é explícito.
- Sem glob / expansão de `~` / redirecionamento implícitos a menos que um slice futuro,
  separado e aprovado, os desenhe — v1 fica em `run` + `pipeline` + `cmd` + `cwd/env` + `ok()`.

## 3. Contrato
- Lowering puro sobre `kof.process` quando possível → mesma semântica, mesma ligação, nenhuma
  superfície de runtime `Kof*` nova em alvos que `process` já cobre.
- Superfície de linguagem apenas aditiva (um namespace `kof.shell` novo); nenhuma mudança no
  comportamento existente de `process` (regra 6: mínimo, reversível).
- Determinístico e honesto por plataforma: um comando que `shell` não pode rodar num alvo levanta
  o gap (`PROC001`) **existente** daquele alvo; nunca degrada em silêncio.

## 4. ABI por alvo (R7 escopo honesto) — MEDIDO em 18/09, não presumido
De `KofProcess.java` + `ExpressionProcessCallLowerer.java` (lido, não inferido):

| alvo | `process.run` | `process.spawn` | `process.exit` | fonte da verdade |
|--------|:---:|:---:|:---:|-----------------|
| **JVM** | ✅ | ✅ | ✅ (`System.exit`) | `KofProcess.RESULT/HANDLE`, ProcessBuilder |
| **JS** | ✅ (`kof_platform.processRun`, host node) | ❌ `PROC001` — o JS não liga nenhum `kof_process_spawn`/*handle* (`ReferenceError` medido; gate honesto 18/09) | ✅ (sentinela) | `js/JsRuntimeOps.java`, `isRuntimeOp` só lista run/exit |
| **Native** | ❌ `PROC001` (tempo de compilação) | ❌ `PROC001` (tempo de compilação) | ✅ (syscall) | gates `PROC001` de spawn/run em `ExpressionProcessCallLowerer` |

Consequência para `kof.shell`: `run`/`exit` são reais em JVM+JS desde o primeiro dia; mas
**`spawn` é só JVM** — um `PROC001` herdado no **Native e no JS** até a plataforma landar uma
ligação de pipes vivos lá (item separado, **fora** do escopo deste plano). `shell` não pode
disfarçar isso — reporta o mesmo gap honesto. *(Corrigido 18/09: um rascunho anterior confiou no
comentário do `ExpressionProcessCallLowerer` "JVM/JS support it"; medir o backend JS mostra que ele
emite uma chamada crua `kof_process_spawn(...)` sem ligação, e o fix porta o spawn do JS para um
`PROC001` honesto — ver `DomainGapCodesTest.processSpawnOnJsIsProc001`.)*

## 5. Fila de passos (o todo executável que este doc existe para produzir)
Dono é `—` até a mantenedora atribuir; dono padrão proposto = **lane de desenvolvimento**.

- **2.2.0 [recon — 0 código]** *(a parte JS-`spawn` FEITA 18/09: estava sem ligação → agora em
  gate `PROC001`)* — enumerar exatamente quais chamadas de `process` cada alvo alcança hoje e
  produzir a tabela de paridade acima como nota ancorada em teste. *Prova:* pins
  `processSpawn*` do `DomainGapCodesTest` + commit de recon; nenhuma superfície entregue.
- **2.2.1 [aprovação de design — ⛔ regra 6]** — a mantenedora aprova a **forma de função** (§2)
  ou redireciona. **Portão de toda a frente.** Nenhum slice adiante sem isso fechar.
- **2.2.2 [MVP — JVM, um alvo]** — `run` + `pipeline` + `cmd` + `cwd`/`env` + `ok()`,
  baixados sobre `kof.process` existente; `ShellE2ETest` golden contra um comando real e sem
  dependências (ex. `tr`/`wc`), assertando **argv-como-lista** (sem `sh -c`). Só JVM, gap
  honesto nos demais.
- **2.2.3 [paridade]** — estende o golden ao JS conforme `process` for confirmado lá; Native fica
  `PROC001` até o `process.run` da lane nativa fechar.
- **2.2.4 [docs]** — doc de idiomato `docs/stdlib/shell.md` (+PT), linha em `backend-parity`,
  virar `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.2 de `🔵 → 🟡`, e só quando entregue promover este
  arquivo para fora da `future/` conforme a regra da pasta.

## 6. Questões abertas (decisões da mantenedora — NÃO resolver em código)
- **Q1** — forma de função (esta proposta) vs gramática infixo de shell (backtick/`|`) — esta é
  mudança de gramática regra 6; este doc recomenda a forma de função.
- **Q2** — casa da superfície: namespace builtin do compilador (`KofShell.java`, como
  `KofProcess`) vs pacote de stdlib em nível Kof. Afeta todo backend, não só o açúcar.
- **Q3** — glob / `~` / redirecionamento entram no v1 ou ficam explicitamente fora (default deste doc)?

## 7. O que NÃO fazer
- Nenhuma execução de string `sh -c` nem concatenação de comando (classe de injeção).
- Nenhuma mudança de gramática (backtick/pipe/redir) sem decisão regra 6 aprovada.
- Nenhum runtime de processo paralelo — reusa `kof.process` e seus gap codes ipsis litteris.
- Nenhum código antes da aprovação do **2.2.1**; nenhuma alegação de suporte Native que `process` não tem.
