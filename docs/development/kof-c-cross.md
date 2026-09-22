# kof-c-compiler cross targets — sliced plan (C1–C4)

[English](kof-c-cross.md) | [Português](kof-c-cross.pt_BR.md)

**Status:** IN PROGRESS — **C1 + C2 + C3 LANDED** (22/09). Owner: FFI/kof-c
front (development/tooling lane).

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

`int g;` globals and functions with `int` parameters/return and locals —
`signed int` only, no floats. Control flow `if`/`while`, integer /
bitwise / comparison / shift binary ops (one operator per expression without
parentheses), `&ident` and `*(int*)ident` deref/addr, calls with up to six
register arguments, `print`/`print_arg`, raw `asm` bytes. `struct` types with
`int` fields, member access and struct-by-value parameters (≤ 8 B). Free-standing:
raw syscalls only, no libc.

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
- **C2 — LANDED (22/09):** parameters, return values, locals and calls
  (`int f(int a, int b) { int t; ... return t; }`) on the three ISAs with the
  C ABI. Frame: saved frame/return pair + one 8-byte slot per parameter/local
  (`rbp`/`s0`/`x29` base). Arguments follow SysV (`rdi,rsi,rdx,rcx,r8,r9`),
  LP64 (`a0..a5`) and AAPCS64 (`x0..x5`) — up to six register arguments; the
  return lands in the accumulator (`rax`/`a0`/`x0`), which is the ABI return
  register on every target. Calls evaluate arguments onto the stack and pop
  them into the argument registers, so a later argument can reuse the
  accumulator without clobbering an earlier one. Honest diagnostics:
  unknown call, arity mismatch, `print()` with arguments and more than six
  parameters/arguments are rejected before any binary is emitted (R6/Q7).
  Proof: `KofCParamsCompilerTest` 7/7 on x86_64/riscv64/aarch64 (two-parameter
  return, locals + loop, void mutation of a global, nested calls, early
  return, pointer parameter dereferenced in the callee, all six argument
  registers) plus the four rejection cases.
- **C3 — LANDED (22/09, first cut):** `struct` types with **4-byte C `int`
  fields** (matching `AbiLayout.Scalar.INT`) and **by-value struct parameters**
  up to **8 bytes** (one eightbyte). A struct local/global is a single 8-byte
  slot; field access loads/stores 32-bit (`movsxd`/`lw`/`ldursw`) sign-extended;
  an ≤ 8 B struct argument travels in one integer register — the exact path the
  libc `div_t` uses. Grammar: `struct S { int a; int b; };`, `struct S v;`
  (global/local/param) and `v.field`. Honest diagnostics: a struct larger than
  8 bytes, an unknown struct, an unknown field or a field on a non-struct are
  rejected before any binary is emitted (R6/Q7). Proof: `KofCStructCompilerTest`
  7/7 on x86_64/riscv64/aarch64 (field round trip, by-value parameter, negative
  field sign-extension, struct + scalar args mixed, global struct) plus the four
  rejection cases. **Remaining:** struct **return** by value, structs larger
  than 8 bytes (memory/pair register path) and the full SysV/AAPCS64/RISCV64
  multi-eightbyte classification — the fixture's current need (a ≤ 8 B
  struct parameter) is met.
- **C4 — TODO:** reusable object output (`.o`) plus linking so a cross test
  can consume the fixture.
- **Scope boundary on `int` width:** scalar `int` variables keep the toy's
  8-byte slot model (pointer-holding `int`s like `int p; p = &x;` rely on it);
  the 32-bit C width is implemented where the C ABI observes memory layout —
  struct fields and struct-by-value packing. Switching plain scalar `int` to 32
  bits needs a real pointer type (`int*`) and is a separate slice (rule 6).

## Notes

- **riscv64 `gp` relaxation:** the linker relaxes `la` of nearby globals to
  gp-relative (`addi t0, gp, off`); a raw `_start` does not initialise `gp`,
  so the access faults. `_start` must do
  `la gp, __global_pointer$` under `.option norelax`.
- **Honest gaps:** an unsupported construct must fail with a diagnostic,
  never emit a wrong binary (R6/Q7).
