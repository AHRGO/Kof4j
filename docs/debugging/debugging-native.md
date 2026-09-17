[English](debugging-native.md) | [Português](debugging-native.pt_BR.md)

# DEBUGGING_NATIVE.md — Debugging on the Native target

**Status:** Partial (17/09) — real DWARF line table in the x86-64 ELF (partial Phase 5): `NativeBackend`
emits `.file 1 "<source.kf>"` + `.loc 1 <line> 0` when debug enabled; `objdump
--dwarf=decodedline` shows the Kof file and the line of each instruction
(`NativeDwarfLineInfoTest`). **Front 4 slice 1 (17/09): a Kof DWARF
`.debug_info`/`.debug_abbrev` CU with one `DW_TAG_subprogram` per Kof function
(`DW_AT_name` = source name, `low_pc`/`high_pc`, `decl_file`/`decl_line`) —
`NativeDwarf.java` + `NativeDwarfSubprogramTest`; the `as` assembler suppresses
its own auto-CU when the program emits `.debug_info` explicitly (verified on
the linked ELF). **Slice 2 (17/09): each subprogram now carries
`DW_AT_frame_base` (`DW_OP_reg6`/rbp) + child `DW_TAG_formal_parameter`/
`DW_TAG_variable` DIEs with `DW_AT_location = DW_OP_fbreg` at the exact slot
the prologue uses (`-(index+1)*8`). Two DWARF4 lessons measured against real
gdb: (1) `DW_AT_high_pc` is an offset only in v4+ (v3 read it as an absolute
address and gdb discarded the CU as "non-debugging"); (2) the v4 header order
is version→abbrev_offset→address_size. End-to-end proof: `gdb -batch -ex "b
Box_twice" -ex run -ex "info locals"` prints `y = 20` (a Kof `var`) and names
the args `this`/`w` — reading values as typed needs `DW_AT_type` (slice 3).**
**Slice 3 (17/09): DW_AT_type on params/locals/return via child
`DW_TAG_base_type` DIEs (Int=signed4, Long=signed8, Double=float8,
Float=float4, Bool=boolean1, Char=unsigned2, Void=size-0/no-encoding,
class/array/String = opaque 8-byte handle for now). Codes taken from
GCC's own `.debug_abbrev` bytes, not guessed. gdb end-to-end now reads
typed values: `print w` -> `5`, `ptype Box_twice` -> `Int (Opaque, Int)`.**
DAP on native and stepping pending.**
**Date:** September 17, 2026
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
