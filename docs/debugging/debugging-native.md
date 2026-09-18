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

## 5. Validating the Native target from a Windows host

The x86-64 Native backend is not just an in-memory code transformation:
it invokes an external toolchain to assemble and link the generated
binary.

In the current code, `NativeAssembler` uses `as` and `ld` and, on the
dynamic Linux path, references the ELF loader and system libraries,
including `libc` and `libm`.

Because of that, a run on plain Windows may not be able to validate the
same Native path exercised on Linux. A message such as:

```text
as not available: ...
```

or:

```text
ld not available: ...
```

first means the process could not find the required external tool. Do
not treat that condition, by itself, as proof of a Kof compiler
regression.

### 5.1 Before running the suite

Check the Java version the project requires in `pom.xml`:

```xml
<maven.compiler.release>...</maven.compiler.release>
```

Use a compatible JDK inside WSL. Do not copy a fixed version from this
document — `pom.xml` is the source of truth.

Also confirm the Linux toolchain:

```bash
java -version
command -v as
command -v ld
as --version
ld --version
```

When the test depends on the dynamic x86-64 ELF path, it's also worth
checking the environment the linker expects, for example:

```bash
test -e /lib64/ld-linux-x86-64.so.2 && echo "dynamic linker: ok"
```

The exact presence of tools and libraries depends on the WSL
distribution. Don't assume every distro already ships `as`, `ld`, `gcc`,
or the required JDK — verify first.

### 5.2 Running from Windows

For simple commands, `wsl.exe` can run commands through the configured
shell. For the Kof flow, where we frequently already invoke `bash -lc`
explicitly to set `JAVA_HOME`, `PATH`, and then run Maven, prefer the
`--exec`/`-e` mode:

```powershell
wsl -d Ubuntu-24.04 -e bash -lc 'export JAVA_HOME=...; export PATH="$JAVA_HOME/bin:$PATH"; mvn ...'
```

Avoid building this kind of nested script as:

```powershell
wsl -d Ubuntu-24.04 -- bash -lc 'export JAVA_HOME=...; export PATH="$JAVA_HOME/bin:$PATH"; mvn ...'
```

when the intent is for the provided `bash -lc` to be the only layer
responsible for interpreting the script's variables and metacharacters.

#### Why does Kof recommend `-e` here?

This behavior was investigated in the official WSL project in
[microsoft/WSL#41598](https://github.com/microsoft/WSL/issues/41598) and
discussed in PR
[microsoft/WSL#41599](https://github.com/microsoft/WSL/pull/41599).

The WSL project decided to keep the current behavior and labeled the
issue `bydesign`. The team considers `--` the separator that ends
`wsl.exe`'s own option parsing, while the shell type used for execution
is controlled by the dedicated shell/exec options.

So this Kof guidance **does not describe a WSL bug**. It is an
operational choice to make nested build scripts predictable: since we are
already providing `bash -lc`, `-e` avoids depending on the default
shell's prior interpretation.

In practical terms:

```text
wsl.exe -e bash -lc '<script>'
        │
        └── explicitly run bash with the given arguments
```

is preferable, for our build scripts, to depending on:

```text
wsl.exe <command line>
        │
        └── default shell interprets the command line
                │
                └── bash -lc interprets the inner script
```

when the text contains `$VAR`, quotes, `;`, and other constructs meant
for the inner shell.

### 5.3 Git Bash/MSYS

If `wsl.exe` is being invoked from Git Bash/MSYS, remember that MSYS
itself can convert arguments that look like Unix paths before calling a
native Windows executable.

If that conversion interferes with `/mnt/...` paths, run the call from
PowerShell/cmd, or disable path conversion for that invocation according
to the environment in use.

Do not treat MSYS path-conversion issues and WSL shell-parsing issues as
the same cause — they are different layers.

### 5.4 Where to generate and run Native binaries

Prefer generating and running Native artifacts inside the distro's own
Linux filesystem during validation. This avoids mixing Linux filesystem
permission semantics with Windows mounts under `/mnt/...`.

If a binary created on a Windows mount cannot be executed, check
permissions and the mount type before concluding that Kof produced an
invalid executable.

### 5.5 Diagnosis order

When a Native test fails on a Windows host, diagnose in this order:

```text
1. Does the JDK match the current maven.compiler.release?
        ↓
2. Is WSL/the distro available?
        ↓
3. Do as and ld exist inside the distro?
        ↓
4. Do the loader/libraries required by the tested path exist?
        ↓
5. Is the command running with the expected shell layer?
        ↓
6. Does the isolated Native test fail?
        ↓
7. Does the same case fail in the conformance suite/gate?
        ↓
8. Only then treat it as a possible compiler regression
```

The rule is simple:

> missing toolchain does not prove a defect in the generated code; a
> regression must remain reproducible once the environment the target
> needs is available.
