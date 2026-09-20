[English](debug-adapter.md) | [Português](debug-adapter.pt_BR.md)

# DEBUG-ADAPTER.md — kof-debug (Debug Adapter DAP)

**Status:** JVM (JDWP cru, sem jdk.jdi) + NATIVE (console gdb + DAP↔GDB/MI, X7-3/X7-4) implementados e validados — `kof debug --dap --target native` faz a ponte DAP↔gdb/MI2 real (`KofGdbMi` + `KofDebugNativeDap`); `stackTrace`/breakpoints sempre mostram o `.kf`; sem gdb = erro DAP honesto nomeando a ferramenta (R6); JS = recusa honesta (o alvo roda no engine EMBUTIDO — não há node/inspector para anexar)
**Data:** 27 de agosto de 2026 (atualizada 20/09 com as faces Native)
**Versão:** 0.4.0-beta (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Objetivo

Componente `kof-debug` que expõe a execução Kof via **DAP** (Debug Adapter
Protocol) — o mesmo protocolo dos editores modernos (VS Code, Neovim,
IntelliJ, Kof Editor).

Não criar protocolo proprietário.

## 2. Responsabilidades

- iniciar programas (`launch`);
- anexar a processos (`attach` — futuro);
- controle de execução: continue, pause, step over/into/out, restart, terminate;
- breakpoints (source; depois conditional, hit count, exception);
- stack traces, scopes, locals, arguments, campos;
- eventos de exceção;
- inspeção de variáveis com tipos Kof;
- avaliação de expressões (futuro — com type system, nunca Java/JS cru).

## 3. Interface

```text
kof-debug (DAP over stdio — Content-Length framing)
    ↓
JVM: launch java -agentlib:jdwp + JDWP client (raw wire protocol)
Native: build do ELF com DWARF + gdb (console: `--target native` com `--break`/`--output`;
        editor: `--dap --target native` traduz DAP -> GDB/MI 2, `KofGdbMi`/`KofDebugNativeDap`)  [✅ 20/09]
JS: recusa honesta — o alvo JS roda no engine Graal embutido; não há
    node/inspector para lançar ou anexar (roadmap §19.5 face 7 segue aberta para um futuro
    inspector do engine, nunca uma ponte falsa)
```

O CLI (`kof debug`) é apenas uma interface — a lógica vive no adaptador.
O cliente JDWP é implementado sobre o **wire protocol cru** (sem
dependência do módulo `jdk.jdi`) para manter o tooling autocontido.

## 3.1 Fluxo DAP implementado (JVM)

```text
initialize            → capabilities (configurationDone, terminate)
launch                → compila (JVM + debug info), porta livre,
                        java -agentlib:jdwp=transport=dt_socket,server=y,
                        suspend=y,address=<porta>, conecta e registra o
                        ClassPrepare de Default.Main (suspend ALL)
setBreakpoints        → registra as linhas Kof (aplicadas no ClassPrepare)
configurationDone     → VM.Resume
[evento] stopped      → breakpoint atingido (thread + motivo)
stackTrace            → frames Kof: nome da função, arquivo, linha
continue              → VM.Resume
disconnect/terminate  → VM.Dispose + kill do processo + limpeza
```

## 3.1b Fluxo DAP implementado (Native, via GDB/MI — `--dap --target native`)

```text
initialize            → capacidades (configurationDone, terminate)
launch                → compila NATIVE com debug info (DWARF: line table +
                        DIEs — X7-1/X7-2), sobe `gdb -q --interp mi2` com
                        `directory <dir da fonte>`; gdb ausente = success:false
                        nomeando a ferramenta (nunca stack no meio do stream)
setBreakpoints        → -break-insert -f -- <file.kf>:<line> (verified a partir
                        da linha real que o DWARF resolveu)
configurationDone     → -exec-run --all
[evento] *stopped     → DAP stopped (breakpoint-hit/entry/end-stepping mapeados);
                        exit-code = exited + terminated
stackTrace            → -stack-list-frames; source.path é SEMPRE o .kf — o
                        ponto inteiro da frente (o editor nunca vê asm)
variables             → -stack-list-variables --simple-values sobre os DIEs
                        DW_TAG_variable reais (nome + DW_OP_fbreg + DW_AT_type)
                        que o compilador já emite nas 3 arquiteturas nativas —
                        medido 20/09 com objdump no ELF/.s; temporários do
                        lowering (tmp/cap/lambda$) ficam FORA das DIEs, o
                        editor só vê nome Kof
evaluate              → -data-evaluate-expression; símbolo inexistente = o erro
                        do gdb repassado, nunca valor inventado (R6)
disconnect/terminate  → -gdb-exit + kill + limpeza do build dir
```

Detalhe de transporte MI que custou uma sessão de debug (20/09): os registros
vêm com TOKEN como PREFIXO (`2^done,...`) — o TIPO do registro é o primeiro
caractere NÃO-dígito; classificar pelo char 0 descarta silenciosamente toda
resposta síncrona (elas só aparecem como timeouts de 5s).

## 3.2 Particularidades do JDWP (JDK 25) descobertas na implementação

- event kinds do JDK 25: `VMStart=90`, `VMDeath=99`, `ClassPrepare=8`
  (os valores clássicos do spec — 0, 15, 6 — não são usados pelo HotSpot);
- `ClassMatch` é o modifier **5** (o modifier 1 é `Count` — um erro aqui
  faz o request ser aceito mas o evento nunca disparar);
- `LocationOnly` é o modifier **7**, com o location `tag(1) + typeID +
  methodID + codeIndex` (o tag é obrigatório — sem ele o JVM responde
  `INVALID_OBJECT`);
- `Method.LineTable` retorna `[codeIndex(long), lineCode(int)]` por
  entrada (ordem long/line, não line/codeIndex);
- `ReferenceType.Methods` retorna `methodID + name + signature +
  modifiers` (4 campos);
- `ThreadReference.Frames` é o command set **11** (o 10 é StackFrame) e
  o HotSpot rejeita `length > 5` com `INVALID_LENGTH` (504);
- o handler de eventos roda fora do event loop (dispatch em thread) —
  comandos JDWP emitidos pelo handler precisam do loop para receber
  replies (sem isso: deadlock de timeout);
- eventos `Composite` têm `suspendPolicy + eventCount` antes dos kinds.

## 3.3 Limitações do MVP

- `stackTrace` retorna até 5 frames (limite do JDK 25) e o frame atual
  mostra a função/linha Kof;
- `scopes`/`variables` são placeholders (locals por frame ficam na
  Fase 7, via `StackFrame.GetValues`);
- breakpoints são reportados como `verified: false` (a verificação
  efetiva via LineTable fica na Fase 7);
- sem stepping, pause, attach, exception breakpoints nem avaliação.

## 4. Tipos de runtime

O adaptador traduz representações de backend para tipos Kof:

| Kof | JVM | Native | JS |
|-----|-----|--------|-----|
| `List<User>` | ArrayList | kof list | Array |
| `User` | User.class | struct | object |
| `String` | java.lang.String | KofString | string |

O usuário sempre vê o tipo Kof.

## 5. Fases

- Fase 3 (MVP): launch JVM + breakpoints por linha Kof + stack — ✅
- Fase 7: locals por frame (`StackFrame.GetValues`), stepping, breakpoints
  verificados, exception breakpoints, avaliação com o type system
- ✅ 20/09: Native (DWARF) — console + DAP<->GDB/MI (X7-3/X7-4)
- Depois: attach; JS fica gap honesto (engine embutido, sem inspector)
- ✅ locals no DWARF: DW_TAG_variable + DW_OP_fbreg + DW_AT_type nos 3
  arquétipos nativos (já existiam desde o trabalho da fatia-2; RE-MEDIDO
  20/09 com objdump depois que esta doc afirmou o contrário por um turno —
  shapes measured, never assumed, vale até contra a própria lane)