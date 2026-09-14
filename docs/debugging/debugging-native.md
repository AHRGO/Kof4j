[English](debugging-native.md) | [Português](debugging-native.pt_BR.md)

# DEBUGGING_NATIVE.md — Debugging on the Native target

**Status:** Partial (02/09) — real DWARF line table in the x86-64 ELF (partial Phase 5): `NativeBackend`
emits `.file 1 "<source.kf>"` + `.loc 1 <line> 0` when debug enabled; `objdump
--dwarf=decodedline` shows the Kof file and the line of each instruction
(`NativeDwarfLineInfoTest`). Local variables, DAP on native and stepping pending.
**Date:** September 2, 2026
**Version:** 0.4.0-beta (7 targets)

---

## 1. Flow

```text
Kof Debug Info (IR)
    ↓
symbols + line tables (DWARF future)
    ↓
ELF x86-64
    ↓
debug adapter (Kof frame info)
    ↓
DAP
    ↓
Editor
```

## 2. Initial phase

- function symbols (already exist: `ClassName_methodName`);
- source locations per instruction (IR Phase 1);
- line tables ✅ (`.file`/`.loc` GAS → `.debug_line`; verified with `objdump --dwarf=decodedline`);
- locals with Kof types;
- scopes;
- stack frames.

## 3. Later

- complete DWARF;
- optimized variable locations;
- native memory inspection.

## 4. Rule

Never show assembly as the primary experience — the `assembly → Kof line`
mapping is internal.
