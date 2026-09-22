# kof-c-compiler cross targets — sliced plan (C1–C4)

[English](kof-c-cross.md) | [Português](kof-c-cross.pt_BR.md)

**Status:** IN PROGRESS — **C1 LANDED** (22/09). Owner: FFI/kof-c front
(development/tooling lane).

## Why

The in-repo C compiler (`kof-c-compiler`, CLI `kof c`) only emitted x86-64
freestanding executables. The Native cross FFI tests need a C fixture with
**struct-by-value parameters**, and this host has no cross C compiler (no
`gcc`/`clang`/`zig` for riscv64/aarch64 — only binutils + qemu, mounted by
`scripts/setup-cross-toolchain.sh` in `/tmp/kof-cross`). Instead of depending
on an external cross cc, the maintainer approved growing the in-repo compiler
to emit the cross targets.

This is a multi-session effort; the end goal is a C fixture the Native cross
FFI tests can link (unblocking the struct-param slice).

## Baseline subset

`int g;` globals and `void f() { }` functions — no parameters, no return
values, no locals, no structs, no floats. Tests: `if`/`while`, integer /
bitwise / comparison / shift binary ops, `&ident` and `*(int*)ident`
deref/addr, `print`/`print_arg`, raw `asm` bytes. Freestanding: raw syscalls
only, no libc.

## Slices

- **C1 — LANDED (22/09):** target-aware emission.
  `KofCTarget` (`x86_64`/`riscv64`/`aarch64`), `KofCEmitter` interface,
  `KofCEmitterBase` (shared AST walk) and per-ISA emitters
  `KofCEmitterX86` / `KofCEmitterRiscv` / `KofCEmitterAarch`;
  `KofCCompiler.compile(path, out, target)` picks the target assembler and
  linker; `kof c --target` exposes it. Proof: the whole subset is
  byte-identical on riscv64/aarch64 under qemu and matches the x86_64 oracle
  (`KofCCrossCompilerTest` 7/7; `KofCCompilerTest` 7/7 unchanged). Missing
  cross toolchain → honest skip (`assumeTrue`).
- **C2 — TODO:** function parameters, return values and locals
  (`int f(int a, int b)`) emitted on the three ISAs with the C ABI.
- **C3 — TODO:** `struct` types and by-value struct parameters
  (SysV / AAPCS64 / RISCV64 classification) — the piece the FFI fixture
  needs.
- **C4 — TODO:** reusable object output (`.o`) plus linking so a cross test
  can consume the fixture.

## Notes

- **riscv64 `gp` relaxation:** the linker relaxes `la` of nearby globals to
  gp-relative (`addi t0, gp, off`); a raw `_start` does not initialise `gp`,
  so the access faults. `_start` must do
  `la gp, __global_pointer$` under `.option norelax`.
- **Honest gaps:** an unsupported construct must fail with a diagnostic,
  never emit a wrong binary (R6/Q7).
