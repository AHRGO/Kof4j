[English](debugging.md) | [Português](debugging.pt_BR.md)

# DEBUGGING.md — Depuração Kof (visão de uso)

**Status:** MVP funcional no target JVM (`kof debug app.kf`)
**Data:** 27 de agosto de 2026
**Versão:** 0.4.0-beta (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Experiência

Depurar Kof é depurar Kof — em qualquer target:

```text
  40 | User find(Int id) {
  41 |     var user = repository.find(id)
● 42 |     return user
  43 | }
```

Ao parar:

```text
CALL STACK

UserService.find       UserService.kf:42
UserController.get     UserController.kf:18
main                    Application.kf:7
```

```text
VARIABLES

id      Int        42
user    User
  name             "Mel"
  active           true
```

O usuário nunca precisa saber JVM bytecode, assembly ou JavaScript.

## 2. Comandos

```bash
kof debug app.kf                 # ✅ JVM (servidor DAP sobre stdio)
kof debug --dap app.kf           # DAP JVM explicito (o padrao e o mesmo servidor)
kof debug --dap --target native app.kf # ✅ X7-4 (`bda631a7`): DAP em ponte com o gdb/MI2 real
kof debug --target native app.kf # ✅ X7-3 (`cfa67238`): builda o ELF com DWARF Kof e
                                 #    delega ao gdb do alvo (`-x` arquivo de comandos, `-iex set
                                 #    directories` ate a fonte Kof) — breakpoints em
                                 #    `Main.kf:2`, nunca no mangle
kof debug --target js app.kf     # gap honesto: o alvo JS roda no motor EMBUTIDO (sem
                                 #    protocolo devtools ainda) — diagnostico, nao silencio
kof debug --attach <pid>         # futuro
kof build app.kf --debug         # metadata extra (padrão: debug info ligado)
kof build app.kf --release
```

A sessão compila com metadata de debug, lança o JVM com
`-agentlib:jdwp` (suspend=y) e responde ao protocolo DAP.

## 3. Capacidades

**MVP implementado (target JVM — Fase 3):**

- launch (compila com metadata de debug + lança o JVM com JDWP)
- breakpoints por linha Kof (`UserService.kf:42`)
- evento `stopped` ao atingir breakpoint
- stack traces com nomes e linhas Kof (via LineNumberTable)
- `continue` e `disconnect`

**Planejadas (Fases 4-7 — ver `debug-adapter.md`):**

- step over/into/out, pause, restart
- scopes/locals por frame (`StackFrame.GetValues`)
- exceções (break on throw / uncaught) com stack Kof
- avaliação de expressões (com respeito ao type system)
- ~~Native (DWARF — Fase 5)~~ ✅ **X7-3 pousou 20/09** (`cfa67238`, `KofDebugNativeTest`);
  JS (source maps — Fase 6) = diagnostico honesto hoje (motor embutido)

## 4. Integração

```text
Kof Editor
    ├── LSP ────► Kof Language Server (diagnostics, symbols, hover)
    └── DAP ────► kof-debug
                      ├── JVM (JDWP)
                      ├── Native (DWARF)
                      └── JS (Node Inspector)
```

LSP e DAP não se misturam: LSP = código; DAP = execução.

## 5. Estado

- Fase 1 (DebugInfo na IR) — ✅
- Fase 2 (JVM: SourceFile, LineNumberTable, LocalVariableTable) — ✅
- Fase 3 (`kof-debug` MVP: DAP + JDWP cru) — ✅
  - requests DAP: `initialize`, `launch`, `setBreakpoints`,
    `configurationDone`, `continue`, `threads`, `stackTrace`, `disconnect`
  - evento `stopped` quando um breakpoint Kof é atingido
  - call stack com funções Kof, arquivo e linha (via LineNumberTable)
- Fase 5 (Native) — ✅ entregue como a ponte DAP↔gdb/MI2
  (`kof debug --dap --target native`, `bda631a7`; garantias medidas em
  `debug-adapter.md`). Fase 6 (JS) — recusa honesta (engine embutido, sem
  node/inspector — nunca uma ponte falsa). Fase 4 (UI do Kof Editor) e os
  refinamentos da Fase 7 — ver `debugger-architecture.md`.

## 6. Medindo um fix pousado — a armadilha do jar obsoleto (lição 20/09)

Ao verificar um fix do compilador pelo jar da CLI, o jar precisa ser
**provavelmente atual**: um `mvn package -pl kof-cli -am` incremental pode
deixar as entradas sombreadas `dev/kof/compiler/*` apontando para um build
antigo, e você mede o **compilador velho e acredita que o fix não existe**
(aconteceu com o §368 — o `FieldAssignabilityPhantomE2ETest` 8/8 estava
certo, o jar era fantasma). Regra: reconstrua com
`mvn clean package -DskipTests` e, na dúvida, compare a classe dentro do jar
com o output do módulo (`unzip -p <cli.jar> dev/kof/compiler/Foo.class |
md5sum` vs `md5sum kof-compiler/target/classes/.../Foo.class`) — **bytes
idênticos ou você não está medindo o tip.**