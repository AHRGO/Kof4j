[English](debug-adapter.md) | [Português](debug-adapter.pt_BR.md)

# DEBUG-ADAPTER.md — kof-debug (DAP Debug Adapter)

**Status:** MVP implemented and validated (JVM; raw JDWP, without jdk.jdi)
**Date:** August 27, 2026
**Version:** 0.4.0-beta (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Objective

The `kof-debug` component exposes Kof execution via **DAP** (Debug Adapter
Protocol) — the same protocol used by modern editors (VS Code, Neovim,
IntelliJ, Kof Editor).

Do not create a proprietary protocol.

## 2. Responsibilities

- launch programs (`launch`);
- attach to processes (`attach` — future);
- execution control: continue, pause, step over/into/out, restart, terminate;
- breakpoints (source; later conditional, hit count, exception);
- stack traces, scopes, locals, arguments, fields;
- exception events;
- variable inspection with Kof types;
- expression evaluation (future — with the type system, never raw Java/JS).

## 3. Interface

```text
kof-debug (DAP over stdio — Content-Length framing)
    ↓
JVM: launch java -agentlib:jdwp + JDWP client (raw wire protocol)
Native: launch binary + DWARF/frame info   (future)
JS: launch node --inspect + Inspector protocol  (future)
```

The CLI (`kof debug`) is only an interface — the logic lives in the adapter.
The JDWP client is implemented over the **raw wire protocol** (without
depending on the `jdk.jdi` module) to keep the tooling self-contained.

## 3.1 Implemented DAP flow (JVM)

```text
initialize            → capabilities (configurationDone, terminate)
launch                → compiles (JVM + debug info), free port,
                        java -agentlib:jdwp=transport=dt_socket,server=y,
                        suspend=y,address=<port>, connects and registers the
                        ClassPrepare of Default.Main (suspend ALL)
setBreakpoints        → registers the Kof lines (applied on ClassPrepare)
configurationDone     → VM.Resume
[event] stopped       → breakpoint hit (thread + reason)
stackTrace            → Kof frames: function name, file, line
continue              → VM.Resume
disconnect/terminate  → VM.Dispose + process kill + cleanup
```

## 3.2 JDWP (JDK 25) particularities discovered during implementation

- JDK 25 event kinds: `VMStart=90`, `VMDeath=99`, `ClassPrepare=8`
  (the classic spec values — 0, 15, 6 — are not used by HotSpot);
- `ClassMatch` is modifier **5** (modifier 1 is `Count` — a mistake here
  makes the request be accepted but the event never fires);
- `LocationOnly` is modifier **7**, with the location `tag(1) + typeID +
  methodID + codeIndex` (the tag is mandatory — without it the JVM responds
  `INVALID_OBJECT`);
- `Method.LineTable` returns `[codeIndex(long), lineCode(int)]` per
  entry (order long/line, not line/codeIndex);
- `ReferenceType.Methods` returns `methodID + name + signature +
  modifiers` (4 fields);
- `ThreadReference.Frames` is command set **11** (10 is StackFrame) and
  HotSpot rejects `length > 5` with `INVALID_LENGTH` (504);
- the event handler runs outside the event loop (dispatch on a thread) —
  JDWP commands emitted by the handler need the loop to receive
  replies (without it: timeout deadlock);
- `Composite` events have `suspendPolicy + eventCount` before the kinds.

## 3.3 MVP limitations

- `stackTrace` returns up to 5 frames (JDK 25 limit) and the current frame
  shows the Kof function/line;
- `scopes`/`variables` are placeholders (per-frame locals are left to
  Phase 7, via `StackFrame.GetValues`);
- breakpoints are reported as `verified: false` (the effective
  verification via LineTable is left to Phase 7);
- no stepping, pause, attach, exception breakpoints or evaluation.

## 4. Runtime types

The adapter translates backend representations to Kof types:

| Kof | JVM | Native | JS |
|-----|-----|--------|-----|
| `List<User>` | ArrayList | kof list | Array |
| `User` | User.class | struct | object |
| `String` | java.lang.String | KofString | string |

The user always sees the Kof type.

## 5. Phases

- Phase 3 (MVP): JVM launch + breakpoints by Kof line + stack — ✅
- Phase 7: per-frame locals (`StackFrame.GetValues`), stepping, verified
  breakpoints, exception breakpoints, evaluation with the type system
- Later: attach, Native (DWARF), JS (source maps)
