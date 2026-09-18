[English](interop.md) | [Português](interop.pt_BR.md)

# Idioms — Interop (JVM types and C FFI)

**Status:** partial (whitelist) · **Introduced:** 0.3.x (TIER 2.1) · **Updated:** 17/09

## What it is

Two surfaces, one rule: the platform already exists — do not rebuild it.
**(a)** JVM: any Java type on the classpath by qualified name. **(b)** C FFI:
`extern "<lib>" f(T): R` binds a native function (JVM via `java.lang.foreign`).

## Real API (measured in the compiler — 0.4.0-beta)

```kof
// (a) JVM interop — qualified name, no wrapper
var now = java.time.Instant.now()
println(now.toString())

// (b) C FFI — the whitelist binds 1-arg ONLY (JvmFfiRuntime):
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double   // ok
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int    // ok (String->Int)
// f(Int): Int ok. Everything else is a compile-time diagnostic:
extern "/lib/x86_64-linux-gnu/libc.so.6" strcmp2(String a, String b): Int
//  -> FFI001 (multi-arg; the grammar parses it, the whitelist rejects it)
// JS target  -> FFI002 (FFI not available on the JS target)
// Native     -> FFI001 until §61 (libc not initialized)
```

## BAD → GOOD

| ❌ BAD | ✅ GOOD | Why |
|---|---|---|
| `extern ... drawText(String t, Int x, Int y): void` | today: a 1-arg C bridge you compile (`int kof_draw(char*...)` behind one bound symbol), or JVM interop to an existing binding | multi-arg = `FFI001`; the widening is the R3 slice (`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`, issue #431) — do NOT hand-emit bytecode to bypass the compiler |
| assuming the lib path is checked at compile time | treat missing lib/symbol as a **runtime** `kof_ffi_*` failure | the path resolves at runtime (`SymbolLookup`), not compile time |
| reimplementing sin/cos/strcmp in Kof | bind the system lib (1-arg shapes) | complexity belongs to the platform (iron rule 2) |

## See also

`docs/language-reference/syntax.md` (§FFI to C), `grammar.md`
(`extern-declaration`), `modules.md` §6; gaps `FFI001`/`FFI002`;
queue: R3 first slice (arity → void → String-return → `char*`).
