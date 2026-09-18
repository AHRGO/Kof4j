[English](interop.md) | [Português](interop.pt_BR.md)

# Idioms — Interop (JVM types and C FFI)

**Status:** partial (whitelist) · **Introduced:** 0.3.x (TIER 2.1) · **Updated:** 18/09 (R3 JVM generalized — scalar ABI + void + String return; see `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` 3.6) · **Updated:** 17/09

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
// Native     -> FFI001 until §61 (libc not initialized)
```

## BAD → GOOD

| ❌ BAD | ✅ GOOD | Why |
|---|---|---|
| binding a symbol under a different Kof name (`kof_fmod`) | the NAME is the C symbol (no alias syntax, measured 18/09) — bind `fmod`, wrap in a Kof fn for friendly names | multi-arg/`void`/`String`-return already bind since R3 18/09 — do NOT hand-emit bytecode to bypass the compiler |
| assuming the lib path is checked at compile time | treat missing lib/symbol as a **runtime** `kof_ffi_*` failure | the path resolves at runtime (`SymbolLookup`), not compile time |
| reimplementing sin/cos/strcmp in Kof | bind the system lib (any scalar shape since 18/09) | complexity belongs to the platform (iron rule 2) |

## See also

`docs/language-reference/syntax.md` (§FFI to C), `grammar.md`
(`extern-declaration`), `modules.md` §6; gaps `FFI001`/`FFI002`;
R3 landed: JVM arbitrary scalar (arity/void/String-return, 18/09) + JS host parity (3.6.F2/F3 ✅ 18/09) + callbacks design with host-level mechanism PROVEN (3.4-C1, `JvmFfiCallbackTest`, gate still CLOSED); remaining: opaque handles (3.3), callbacks C2→surface (3.4), variadics (3.5, ⛔ surface decision), struct/array ABI (D6 ⛔), Native §61.
