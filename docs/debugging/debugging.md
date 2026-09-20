[English](debugging.md) | [Português](debugging.pt_BR.md)

# DEBUGGING.md — Kof Debugging (usage view)

**Status:** Functional MVP on the JVM target (`kof debug app.kf`)
**Date:** August 27, 2026
**Version:** 0.4.0-beta (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Experience

Debugging Kof is debugging Kof — on any target:

```text
  40 | User find(Int id) {
  41 |     var user = repository.find(id)
● 42 |     return user
  43 | }
```

When it stops:

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

The user never needs to know JVM bytecode, assembly or JavaScript.

## 2. Commands

```bash
kof debug app.kf                 # ✅ JVM (DAP server over stdio)
kof debug --target native app.kf # ✅ X7-3 (`cfa67238`): builds the ELF with Kof DWARF and\                                 #    delegates to the target's gdb (`-x` command file, `-iex set
                                 #    directories` to the Kof source dir) — breakpoints on
                                 #    `Main.kf:2`, never on the mangle
kof debug --target js app.kf     # honest gap: the JS target runs on the EMBEDDED engine
                                 #    (no devtools protocol yet) — diagnostic, not silence
kof debug --attach <pid>         # future
kof build app.kf --debug         # extra metadata (default: debug info on)
kof build app.kf --release
```

The session compiles with debug metadata, launches the JVM with
`-agentlib:jdwp` (suspend=y) and responds to the DAP protocol.

## 3. Capabilities

**Implemented MVP (JVM target — Phase 3):**

- launch (compiles with debug metadata + launches the JVM with JDWP)
- breakpoints by Kof line (`UserService.kf:42`)
- `stopped` event when a breakpoint is hit
- stack traces with Kof names and lines (via LineNumberTable)
- `continue` and `disconnect`

**Planned (Phases 4-7 — see `debug-adapter.md`):**

- step over/into/out, pause, restart
- scopes/locals per frame (`StackFrame.GetValues`)
- exceptions (break on throw / uncaught) with Kof stack
- expression evaluation (respecting the type system)
- ~~Native (DWARF — Phase 5)~~ ✅ **X7-3 landed 20/09** (`cfa67238`, `KofDebugNativeTest`);
  JS (source maps — Phase 6) = honest diagnostic today (embedded engine)

## 4. Integration

```text
Kof Editor
    ├── LSP ────► Kof Language Server (diagnostics, symbols, hover)
    └── DAP ────► kof-debug
                      ├── JVM (JDWP)
                      ├── Native (DWARF)
                      └── JS (Node Inspector)
```

LSP and DAP do not mix: LSP = code; DAP = execution.

## 5. State

- Phase 1 (DebugInfo in the IR) — ✅
- Phase 2 (JVM: SourceFile, LineNumberTable, LocalVariableTable) — ✅
- Phase 3 (`kof-debug` MVP: raw DAP + JDWP) — ✅
  - DAP requests: `initialize`, `launch`, `setBreakpoints`,
    `configurationDone`, `continue`, `threads`, `stackTrace`, `disconnect`
  - `stopped` event when a Kof breakpoint is hit
  - call stack with Kof functions, file and line (via LineNumberTable)
- Phases 4-7 — planned; see `debugger-architecture.md`
