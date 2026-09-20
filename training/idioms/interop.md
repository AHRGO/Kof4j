[English](interop.md) | [Português](interop.pt_BR.md)

# Idioms — Interop (JVM types and C FFI)

**Status:** partial (whitelist) · **Introduced:** 0.3.x (TIER 2.1) · **Updated:** 18/09 (R3 JVM generalized — scalar ABI + void + String return; **callbacks C2 — JVM gate OPEN**; see `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` 3.6/3.4) · **Updated:** 17/09

## What it is

Two surfaces, one rule: the platform already exists — do not rebuild it.
**(a)** JVM: any Java type on the classpath by qualified name. **(b)** C FFI:
`extern "<lib>" f(T): R` binds a native function (JVM via `java.lang.foreign`).

## Real API (measured in the compiler — 0.4.0-beta)

```kof
// (a) JVM interop — qualified name, no wrapper
var now = java.time.Instant.now()
println(now.toString())

// (b) C FFI — the JVM binds any SCALAR signature (R3 generalized 18/09):
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double    // ok (1-arg)
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int      // ok
extern "/lib/x86_64-linux-gnu/libm.so.6" fmod(Double a, Double b): Double  // ok — 1.5 measured
extern "/lib/x86_64-linux-gnu/libc.so.6" puts(String s): void     // ok — void binds
extern "/lib/x86_64-linux-gnu/libc.so.6" getenv(String n): String // ok — String return, "mel" measured
// The Kof function NAME is the C symbol (no alias syntax) — kof_fmod failed lookup, fmod works.
// Non-scalar types (objects, generics) -> FFI001 compile-time diagnostic.
// JS runner  -> SAME scalar ABI via KofJsFfiBridge (F2/F3 ✅ 18/09; FfiE2ETest 16/16); browser -> honest runtime error (R7, no host); non-scalar -> FFI002
// Native (x86-64/riscv64/aarch64) -> SAME scalar ABI binds DIRECT since #431 20/09 (§61 CLOSED, §369):
//   no dlopen — link-by-use of library() + call sym@PLT; String<->char* = UTF-8 payload at offset 24
//   (NULL->NULL); >=9 same-class args spill; String return = boundary copy (C buffer never freed);
//   riscv64/aarch64 glibc passes AND returns FP in fa0..fa7 (MEASURED under qemu — NOT ft0);
//   C stdio is flushed at exit; struct/array/callback/missing-library -> FFI001 at the decl line

// (c) CALLBACKS (C2 ✅ + JS parity C3.2/C3.3 ✅, 18/09): a Kof function handed to C as a
// function pointer. Function-typed parameter + lambda at the call site;
// PRIMITIVE + String-arg callback ABI (synchronous, non-escaping):
extern "libcallback.so" kof_cb_add(Int a, Int b, (Int, Int) -> Int cb): Int
// call site — the lambda becomes the C function pointer (Linker.upcallStub):
kof_cb_add(20, 22, (x: Int, y: Int) -> x + y)   // 42 measured
kof_cb_mixed(3, 2.5, (i: Int, d: Double) -> i * d)  // mixed scalar ABI ok
kof_cb_slen("hello", (x: String) -> x.length())  // char* -> String arg (C3.4)
// JS (host runner) binds callbacks too (C3.2/C3.4 ✅ 18/09, byte-for-byte JVM~JS;
// browser = honest runtime degrade R7); struct/pointer-in-callback, a String
// RETURN, and callback-as-return -> FFI001 (JVM) — never a silent stub.
```

## BAD → GOOD

| ❌ BAD | ✅ GOOD | Why |
|---|---|---|
| binding a symbol under a different Kof name (`kof_fmod`) | the NAME is the C symbol (no alias syntax, measured 18/09) — bind `fmod`, wrap in a Kof fn for friendly names | multi-arg/`void`/`String`-return already bind since R3 18/09 — do NOT hand-emit bytecode to bypass the compiler |
| assuming the lib path is checked at compile time | treat missing lib/symbol as a **runtime** `kof_ffi_*` failure | the path resolves at runtime (`SymbolLookup`), not compile time |
| storing the callback pointer to call LATER (atexit/signal/async) | keep callbacks synchronous and non-escaping | escapantes exigem política de vida/GC-rooting (R12) — ficam `FFI001`, nunca stub pendurado |
| reimplementing sin/cos/strcmp in Kof | bind the system lib (any scalar shape since 18/09) | complexity belongs to the platform (iron rule 2) |
| assuming `library()` means the same thing on every target | on JVM/JS it is the dlopen path; on **Native** it is resolved **by basename at LINK time through the sysroot** (`libc.so.6` → `-l:libc.so.6`; an absolute HOST path is wrong-arch cross) | Native has no FFM: a bound `extern` is a `call sym@PLT` + link-by-use (#431 20/09, §369) |

## See also

`docs/language-reference/syntax.md` (§FFI to C), `grammar.md`
(`extern-declaration`), `modules.md` §6; gaps `FFI001`/`FFI002`;
R3 landed: JVM arbitrary scalar (arity/void/String-return, 18/09) + JS host parity (3.6.F2/F3 ✅ 18/09) + **callbacks bind on JVM AND the JS host runner, byte-for-byte parity (C2 ✅ + C3.2/C3.3/C3.4 ✅ 18/09 — primitive + `String`-arg callbacks; `JvmFfiCallbackE2ETest` incl. `jvmAndJsCallbacksMatchByteForByte` and `stringCallbackArgsBindAndMatchJvmJs`)**; remaining: opaque handles (3.3), variadics (3.5, ⛔ surface decision), struct/array ABI (D6 ⛔), callbacks/upcalls on Native (no mechanism — `FFI001`); **the Native scalar ABI BINDS on all 3 archs (fatias 1–2 ✅ 20/09 — §369, §61 CLOSED: `FfiNativeE2ETest` 16/16 x86-64 + `FfiNativeCrossE2ETest` 6/6 riscv64×aarch64 byte-identical under qemu)**.
