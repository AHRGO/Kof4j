[English](shell-plan.md) | [Português](shell-plan.pt_BR.md)

# `kof.shell` — shell idiomático sobre `kof.process` (plano de design · Estágio 2 · TIER 2.2)

> **Status: v1 LANDED (18/09, `34e4344f`, lane `.18`)** — `cmd`/`run`/`ok` reais em JVM+JS
> (5 casos byte-parity no `ShellE2ETest` 11/11); `pipeline` real no JVM (helper `kof_shell_pipeline`),
> JS/Native = `PROC001` honesto em tempo de compilação (nunca `ReferenceError` cru — disciplina §235);
> `KofShell.java` dispatch + lowerer + `StdCatalog` + ledger R1 registrados. Q1–Q3 da pesquisa da
> mantenedora respondidos: forma-função ✓, builtin `KofShell.java` ✓, glob/`~`/redirecionamento **FORA do v1**
> (faces futuras por decisão, não dívida). Este arquivo morava em `future/` sob a regra "zero código";
> o MVP landou — a regra dos três estados obriga a movê-lo para `docs/development/` (19/09).
> Restam como desenvolvimento: pipeline JS/Native (libera `PROC001`) e as faces v2 (glob/`~`/redirecionamento).

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

var r = shell.run("git", listOf("status", "--short"))   // Result reaproveitado de kof.process
if (shell.ok(r)) println(r.stdout)                // ok() é função do namespace: o Result de process não tem métodos

var n = shell.run(shell.cmd("wc", listOf("-l"))).stdout.trim()   // cmd() monta o argv; o overload run() recebe

// stdout de A -> stdin de B; argv mantido como listas (nunca reanalisado por um shell)
var out = shell.pipeline(listOf(shell.cmd("ls", listOf("-1")), shell.cmd("wc", listOf("-l")))).stdout

// cwd/env é overload do 2.2.3 (Kof não tem argumentos nomeados — sintaxe corrigida na aprovação 18/09):
var x = shell.runWith(shell.cmd("make", listOf("-j4")), "/src", mapOf("CC", "clang"))
```

Tipos da superfície (v1): `cmd(String program, List<String> args) -> List<String>` (montador
de argv, `[program] + args`, parsing zero), `run(String, List<String>) -> Result`,
`run(List<String>) -> Result` (overload argv), `pipeline(List<List<String>>) -> Result` (o
`Result` do último estágio carrega o resultado da cadeia), `ok(Result) -> Bool`
(`exitCode == 0`).

Invariantes de design (emprestados de dívidas/precedentes do repo, não inventados):
- **argv é sempre `List<String>`** — o comando **nunca** é concatenado numa string entregue a
  um shell (essa é a classe de segurança `concatenated-command-line` que o repo re-limpa o
  tempo todo; `shell` deve ser o bom caminho que torna difícil se autossabotar).
- `Result` **é** o `Result` de `kof.process` (`stdout`, `stderr`, `exitCode`) — um tipo só, sem
  segunda forma para manter em sincronia.
- `shell.ok(r)` == `exitCode == 0`; qualquer outro portão é explícito.
- Sem glob / expansão de `~` / redirecionamento implícitos a menos que um slice futuro,
  separado e aprovado, os desenhe — v1 fica em `run` + `pipeline` + `cmd` + `ok()`, com
  `cwd/env` no 2.2.3.

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

### 4.1 Mapa de fiação de um namespace builtin novo (medido 18/09)
Toda célula da tabela acima já está travada por testes, então nenhum teste de recon foi
adicionado (`DomainGapCodesTest` trava native-run, native-spawn e js-spawn em `PROC001` +
spawn-JVM-sem-gap; `CoreRegressionE2ETest.processRun` (F4) roda `process.run` via `runBoth`
no JVM **e** no JS). O que `KofShell.java` precisa registrar, medido de como `process` está
ligado:

| ponto de contato | arquivo:linha | o que entra |
|------------|-----------|--------------|
| typer de call de membro | `MemberCallNamespaces.java:90` | receiver `shell` → tipar a chamada (como `process`) |
| typer de call de método (sem membro) | `MethodCallNamespaces.java:145` | mesmos tipos de resultado no caminho sem receiver |
| whitelist de identificador solto | `SemExpressionTyper.java:90,152` | somar `"shell"` para `shell` não ser "identificador desconhecido" |
| dispatch de lowering | `ExpressionMethodCallLowerer.java:242` | `shell.*` → novo `ExpressionShellCallLowerer` |
| ligação de runtime JVM | `jvm/JvmRuntimeCallDescriptors.java`, `JvmRuntimeReturnDescriptors.java`, `JvmRuntime.java` (lista de nomes) | `kof_shell_pipeline` (a única ligação *nova*; `run`/`cmd`/`ok` baixam sobre `kof_process_run` + helpers de lista/bool já existentes) |
| catálogo LSP | `StdCatalog.java:45` + `StdCatalogTest.java:89,194,276` | `m.put("shell", KofShell.functions())` + atualização da guarda |

`pipeline` não pode reusar handles `kof_process_spawn` pela IR (precisaria de loops
read/write/exit por estágio no IR); ele baixa para um helper JVM novo `kof_shell_pipeline
(List<List<String>>) -> Result` (cadeia ProcessBuilder, stdout→stdin no runtime, o resultado
do último estágio). JS/Native batem no gap herdado de spawn em **tempo de compilação** — o
lowerer de shell deve portar `pipeline` para `PROC001` nesses alvos exatamente como o gate de
`process.spawn` faz, sem jamais emitir chamada que daria `ReferenceError` (a lição do §235).

## 5. Fila de passos (o todo executável que este doc existe para produzir)
Dono: **lane `.18`** (atribuído pelo greenlight da mantenedora em 18/09; dono padrão
proposto = lane de desenvolvimento, confirmado).

- **2.2.0 [recon — 0 código]** ✅ FEITO 18/09 — tabela de paridade §4 medida do lowerer +
  das ligações de runtime, e **cada célula já estava travada por testes existentes** (ver
  §4.1), então nenhum pin duplicado foi escrito.
- **2.2.1 [aprovação de design — ⛔ regra 6]** ✅ FEITA 18/09 — enquete da mantenedora: Q1
  forma de função ✓, Q2 builtin `KofShell.java` ✓, Q3 glob/`~`/redir **fora** ✓. Superfície
  concreta do §2 adotada (forma posicional/overload; `shell.ok(r)` como função do namespace
  porque o `Result` de process não carrega métodos — os exemplos anteriores `r.ok()`/`cwd:`
  foram reescritos, não analisam hoje).
- **2.2.2 [MVP — JVM + JS no `run`, só JVM no `pipeline`]** — `cmd` + `run` (os dois
  overloads, baixados pela `kof_process_run` existente) + `ok` + `pipeline` (helper novo
  `kof_shell_pipeline`); `ShellE2ETest` golden contra comandos reais sem dependências
  (`wc`/`tr`), assertando **argv-como-lista** (sem `sh -c`); pins js-pipeline/native-pipeline
  `PROC001`; paridade JS do run via `runBoth`. `cwd/env` (`runWith`) mudou para o 2.2.3 —
  precisa de ligações *novas* no runtime JVM e no host JS, o que alargaria o raio do MVP sem
  ganho.
- **2.2.3 [paridade + extras]** — ✅ LANDADO 19/09: `runWith(argv, cwd, env)` no JVM +
  ligação no host JS (ambiente **aditivo**, `cwd` `""` herda, `Result` **honesto** com
  `exitCode -1` p/ erro de spawn e argv vazio; `ShellE2ETest` 15/15 com paridade byte +
  pin `PROC001` no Native). `pipeline`
  no JS quando (e somente quando) uma ligação JS de pipes vivos (`process.spawn`) landar
  (item de plataforma à parte); Native segue `PROC001` até o `process.run` da lane nativa
  fechar.
- **2.2.4 [docs]** — doc de idiomato `docs/stdlib/shell.md` (+PT), linha em `backend-parity`,
  virar `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.2 de `🟡 → ✅` (a linha anda com os testes do
  2.2.2), e só quando entregue promover este arquivo para fora da `future/` conforme a regra
  da pasta.

## 6. Questões abertas (decisões da mantenedora — NÃO resolver em código)
**As três RESPONDIDAS em 18/09 pela enquete da mantenedora** (recriar via a mesma decisão
multi-escolha se algum dia forem revisitadas — regra 6):

- **Q1 — RESPONDIDA: forma de função** (esta proposta) — não gramática infixo de shell
  (backtick/`|`), que seria mudança de gramática regra 6.
- **Q2 — RESPONDIDA: namespace builtin do compilador `KofShell.java`**, como `KofProcess` —
  não pacote de stdlib em nível Kof.
- **Q3 — RESPONDIDA: glob / `~` / redirecionamento ficam FORA do v1** (o default deste doc);
  um slice aprovado posterior pode revisitá-los.

## 7. O que NÃO fazer
- Nenhuma execução de string `sh -c` nem concatenação de comando (classe de injeção).
- Nenhuma mudança de gramática (backtick/pipe/redir) sem decisão regra 6 aprovada.
- Nenhum runtime de processo paralelo — reusa `kof.process` e seus gap codes ipsis litteris.
- Nenhum código antes da aprovação do **2.2.1**; nenhuma alegação de suporte Native que `process` não tem.
