# FFI struct/array ABI — spec D6-A (design-first, no code)

[English](ffi-abi-structs.md) | [Português](ffi-abi-structs.pt_BR.md)

**Status:** DRAFT for maintainer review (D6-A, `docs/development/DECISIONS.md`
D-POLL-19: "written spec first, review, then code").
**Execution after approval:** compiler lane (tracker line 3.8) + native lane (3.7).
This document is DESIGN ONLY — it changes no semantics and binds nothing.


## 1. What exists today (measured 19/09, not remembered)

`extern name[("lib")] (params): Ret` lowers to a signature token
(`FfiSignature.java`): `i`=Int, `j`=Long, `f`=Float, `d`=Double, `b`=Boolean,
`S`=String (`char*`), `v`=void return; a callback param is the nested token
`(<ret><params>)`. Anything the map does not cover is a **compile-time honest
gap**: `FFI001` (JVM/Native not bound) / `FFI002` (JS) —
`CompilerPipeline.java:225-236`, R6 (never a silent stub).

| Surface | JVM | Native | JS |
|---|---|---|---|
| scalar downcall/upcall | ✅ `kof_ffi` FFM (`JvmFfiRuntime.java:142+`) | ❌ `FFI001` (line 3.7) | ✅ host bridge `KofJsFfiBridge` (browser degrades honestly, R7) |
| callbacks (3.4) | ✅ `Linker.upcallStub` | ❌ | ✅ host |
| String = `char*` | ✅ in + out | — | ✅ |
| **struct / array / out-buffer / opaque** | ❌ FFI001 | ❌ FFI001 | ❌ FFI002 |

JVM scalar→FFM mapping (measured): `i→JAVA_INT, j→JAVA_LONG, f→JAVA_FLOAT,
d→JAVA_DOUBLE, b→JAVA_BOOLEAN, S→ADDRESS`, non-scalar token falls back to
`ADDRESS` only inside the callback path (`JvmFfiRuntime.java:88-106,144-145`).

**Measured wart (fix candidate, not a new gap):** downcall `String`
parameters are allocated with `Arena.global()`
(`JvmFfiRuntime.java:24,63`) — a global arena never frees; in a
long-running process every FFI string argument leaks. The spec should
decide the arena policy (§4 D6-5), not leave it to code drift.

## 2. Why "struct" is harder than it looks (the real cost)

The JVM side is nearly free: FFM already understands
`MemorySegment`/`StructLayout` and the JDK's own SysV ABI implementation
does classification. **The cost concentrates in the Native asm backend**,
which must implement struct classification per target ABI by hand
(§3) — that is why the tracker splits 3.8 (layout+JVM) from 3.7 (native),
and why this spec is written before any code.

## 3. Layout and calling rules per ABI (normative references)

Natural alignment (`alignof` field), size rounded up to `alignof` struct,
trailing padding included; no `#pragma pack` in v1.

| ABI | Pass-by-value rule (summary) |
|---|---|
| x86-64 SysV | classify each *eightbyte*: INTEGER / SSE / SSEUP / NO_CLASS ≤ 8 fields total; ≤ 16 B of INTEGER-class → two int regs (`rdi…`), ≤ 16 B SSE → XMM; anything bigger → **memory** (stack), caller-allocated copy |
| aarch64 AAPCS64 | HFA check (≤ 4 homogeneous float); otherwise ≤ 16 B → core regs `x0…` (by eightword class), > 16 B → stack; `w` register for the upper half when mixed |
| riscv64 LP64 | fields ≤ 8 B packed into *doublewords* `a0…a7`; alignment may force a doubleword skip; struct > 2 doublewords or with unaligned-class → **reference** (pointer to caller copy), `Byref` class |

Three worked examples the implementation tests must reproduce bit-exactly:

| Kof shape | C shape | size | align | SysV classes |
|---|---|---|---|---|
| `Point2(Int x, Int y)` | `struct{int,int}` | 8 | 4 | INTEGER (1 eightbyte) |
| `Mixed(Bool b, Int n, Float f)` | `struct{_Bool,int,float}` | 12 | 4 | padding after `b`; INTEGER (8B: b+n) + INTEGER (4B: f) |
| `Time(Int64 s, Double d)` | `struct{int64_t,double}` | 16 | 8 | INTEGER + SSE (SysV), 2 eightwords (aarch64) |

## 4. Design decisions for the maintainer (rule 6 — this lane proposes, never decides)

- **D6-1 · which Kof value maps to a C struct?**
  A) `record` (structural, immutable, already zero-ceremony — recommended default);
  B) a new mutable `struct` declaration (needed for *in/out* buffers);
  C) both, with records = by-value read-only and `struct` = by-ref.
  A+B is the composition we lean toward; a decision must be written to
  `DECISIONS.md` before 3.8 starts.
- **D6-2 · array mapping.** `List<Int>` is boxed (JVM `ArrayList`) — binding
  it means copying to native memory per call. Proposal: primitive arrays
  (`new Int[n]`, which already exist) bind to `ptr` (no implicit length
  param — the C API decides), `List<T>` stays FFI001 until a boxed-unboxing
  benchmark proves otherwise.
- **D6-3 · out-parameters.** No new syntax in v1: out-buffer = `new Byte[n]`
  passed as `S`→`ADDRESS` and read back after the call. Pointer-in-struct
  fields = out of scope (opaque handles are 3.3, separate decision).
- **D6-4 · return-by-value > 16 B.** SysV hidden-pointer (sret) / AAPCS64
  hidden-x8 / LP64 reference — the *JVM* Linker hides this; the *asm*
  backend must implement sret explicitly. Flag: this is the single biggest
  native-lane cost; slices in §6 isolate it.
- **D6-5 · String/arena ownership (fixes the §1 wart).** Proposal:
  confined arena per downcall, closed after the call; returned `char*`
  is **copied then never owned** (Kof String is immutable — the C pointer
  must not outlive the call unless the C API documents ownership
  transfer, which is the 3.3 `free()` story).

## 5. Non-goals (v1)

bitfields; anonymous unions; `#pragma pack`/`alignas`; `long double`
(x87 80-bit — its own gap code if ever); `wchar_t`/UTF-16; struct-typed
callbacks (nested fn-ptr in struct); variadics (3.5, separate ⛔);
C++ name mangling; COMDAT/section rules. Each stays an honest FFI001/002
until decided — no silent partial binding.

## 6. Division of work (after approval — not this lane)

1. **3.8a** layout engine: `AbiLayout` (size/align/classes per triple) in
   compiler, pure data + golden tests vs the three worked examples (§3).
2. **3.8b** JVM binding: records→`StructLayout` in `kof_ffi` (FFM does
   classification); D6-5 arena policy.
3. **3.7** native asm: classification by hand per target (x86-64 now;
   aarch64/riscv64 follow the same AbiLayout golden) + sret (D6-4).
4. **JS**: decide wasm/ffi boundary (node host already binds scalars;
   struct = host-side pack/unpack) — no browser promise (R7).
5. **DoD (R5)**: per-target golden E2E matrix (same C harness, 3 ABIs),
   FFI00x unchanged for everything not covered, `training/idioms/interop.md`
   updated with the chosen Kof shape from D6-1, this doc promoted to
   `docs/` when 3.8 lands.
