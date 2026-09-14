[English](language-state.md) | [Português](language-state.pt_BR.md)

# Current State of the Kof Language

> ⚠️ **HISTORICAL SNAPSHOT (02/09, 0.2.6-beta).** This doc was one version
> behind the implementation; it was kept as a record of the state on that day.
> For the CURRENT state: `docs/status.md`, `docs/language-reference/`,
> `docs/bugs-and-gaps/specification-gaps.md` and the suite (`mvn test`).

**Date:** September 2, 2026
**Version:** 0.2.6-beta
**Tests:** 810 JUnit (793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli, 0 failures) +1 conditional skip; `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35, `KofCCompilerTest` 5/5, `KofHttpE2ETest` 4/4, `KofCacheE2ETest` 5/5 (x3 targets), `KofWebWsE2ETest` 11/11, `KofWebSseE2ETest` 7/7; includes JSON (complete on the 3 targets, 31/08), exceptions, web (ws/sse 30/08), db/orm, UI, security G9, generics `Box<T>` fix, pattern matching and null safety (JVM fix 02/09)
**Status:** Functional compiler with JVM, Native (x86_64 stable + `native.risc`/`native.arm` placeholder via qemu), KofJS (alpha, GraalJS), KofScript, KofC and Android (Phase 1) backends; web server (ws/sse), official distribution and tooling (0.2.6-beta, 31/08)

---

## What's new 0.1.0 → 0.2.6-beta (31/08)

### 0.2.6-beta — platform (30-31/08)

- **spawn/await on Native** (CONC001 closed): `pthread_create` + trampoline +
  `pthread_join` + thread-safe allocator (futex) — implicit join
- **Real FP on Native** (FLT001): XMM arithmetic (`vcvtsi2sd`/`mulsd`),
  dtoa via `snprintf`, full parse (fraction+exponent)
- **Complete JSON on Native** (JSN001/JSN002/JSN003): objects/records by
  compile-time composition + `Int/Long/Bool/String/Double` arrays
- **Native SQLite** via direct `.so` link; MySQL wire protocol in progress
  (SHA-1 auth scramble + `user:pass@` parse)
- **JVM**: WebSocket (`app.ws`, handshake RFC 6455 + frame codec with mask)
  and SSE (`sse.send/event/close`) via `kof.web`; `kof.http` retry/circuit
  breaker (`KOF_HTTP_RETRIES`/`TRIPS`/`FAILURES`/`OPEN_UNTIL`, 30s window,
  fail-fast); `kof.cache` fixed (register clobber);
  `KofRuntime.close` + ws descriptors
- **JVM+JS**: `kof.http` retry/circuit in parity (30/08)
- **JS**: `kof.time` scheduler via `setInterval`; `kof.http` retry/circuit
- **UI Phase 7**: Router (`go/replace/back/forward/param/current/depth`) — real
  on JS, no-op on JVM
- **CLI**: `kof fmt` (real parser, idempotent) and `kof config gen` implemented
- **Android Phase 1**: `kof build --target android` → Maven project + APK with
  host Activity in Kof
- **Release pipeline**: 2 jobs (`test-and-bump` exports `bump_sha` →
  `package-and-release` checks the bump commit + version sanity) ×
  3 platforms (linux-x86_64/macos-arm64/windows-x86_64)

### 0.2.6-beta — language and platform (27/08)

- **Pattern matching** `switch (x) { case String s: ... }` + record destructuring `Point(x,y)` on JVM/Native/JS (`Parser.java:1`, `SemanticAnalyzer.java:1`, `CompilerDriver.java:1`)
- **Null safety** basic `String?` (`Type?` nullable, compile-time `?`-check)
- **List `map/filter/reduce`** + `Box<T>` generics stable (erasure, `substituteTypeVariable` `CompilerDriver.java:3972`)
- **KofScript** top-level `let` → `KofScriptGlobals` (REPL, `--watch`, Windows SIGPIPE fix)
- **KofCcompiler** (`kof c`) native-only C subset: `while`/`if`/deref `&`/`*(int*)` → ELF x86_64 via `kof_c`
- **Native** free-list (`kof_free_head`) + `kof_gc_collect`; MySQL handshake `kof_db_mysql_scramble`; target separation `native.riscv64`/`native.aarch64` (`Target.java:1`, `NativeBackend.java:1`, riscv64 via `riscv64-linux-gnu-as`, `.option arch,rv64g`, `li a7 214/64/93`)
- **kof.http** JVM+JS (JS via `Java HttpClient` interop in `KofJsRunner`)
- **Bugs**: large-project `import a.b.C` file handling (`CompilerDriver.java:243` `import a.b.C` + `a.b` dir, `largeproj` `a/b/C.kf` OK), `List.get`/`listOf`, `release.yml` single job + JDK 21, `kof_free_head` reuse

### 0.1.0 final (P1 — language, 25/08)

- **Enums** with exhaustive switch (`SEM031`), `values/valueOf/name`,
  content comparison and String mapping in descriptors
- **Map<K,V> / Set<T>** complete on the 3 targets (Native in its own asm)
- **spawn/await** with typed handle `Handle<T>` and primitive unboxing;
  real concurrency on the 3 targets (CONC001 Native + CONC003 JS closed);
  explicit `AND001` gap (Android); non-void single-expression lambda
  becomes a return (VerifyError fix)

- **Android/JVM interop**: `super.metodo()` with INVOKESPECIAL (owner is the
  direct superclass; external signatures resolved via `.jar`/`.aar` classpath
  — `CompilerDriver.setExternalClasspath`) and annotations
  `@Name`/`@Name(valor | key = valor, ...)` emitted in the bytecode
  (RuntimeVisible/Invisible) on classes, fields, methods and parameters.
  `super.metodo()` on Native reports `SUP001`.
- `entity Name { field: Type constraint }` — declarative schema (compile-time)
  for `kof.orm` (`generated`, `unique`, non-numeric PK).
- stdlib namespaces: `kof.db`, `kof.orm`, `kof.process`, `kof.ui`
  (Window/Label/Button/Input/Column/Row/View/Style), plus `kof.web`,
  `kof.io`, `kof.time`, `kof.config`, `kof.log`, `kof.security`, `kof.validation`, `kof.observability`, `kof.http`, `kof.mq`.
- `String.toInt()/toLong()/toDouble()/toFloat()` conversions (runtime).
- ARITH001: division/remainder by **constant** zero rejected at compile-time
  (integers only — float/double produce Infinity/NaN).
- Lexer tolerates a leading UTF-8 BOM.
- Lambdas with captures on all targets (`BoxN` box); multiple windows in kof.ui.
- **25/08:** generics `Box<T>` with primitive `T` (`Box<Int>`) fix — `substituteTypeVariable` + native `kof_int_to_string`; `SEM025` with no false positive in `hashCode/equals/toString`.

---

## Syntax

### Basic structure

```kof
package com.example

import java.util.List

class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    public speak(): String {
        return name
    }
}

main() {
    var a = new Animal("Rex")
    println(a.speak())
}
```

### What the language currently supports

| Construct | Syntax | Example |
|-----------|---------|---------|
| Package | `package a.b.c` | `package com.example` |
| Import | `import a.b.c` | `import java.util.List` |
| Function | `name(args): RetType` | `add(Int a, Int b): Int` |
| Class | `class Name extends Super implements Iface` | `class Dog extends Animal` |
| Record | `record Name(Type field, ...)` | `record Point(Int x, Int y)` |
| Interface | `interface Name extends Iface` | `interface Speaker` |
| Constructor | `constructor(args)` | `constructor(String name)` |
| Field | `Type name = value` | `String name = "default"` |
| Method | `name(args): RetType` | `speak(): String` |
| Variable | `var name = value` or `Type name = value` | `var x = 10` |
| If | `if (cond) { } else { }` | `if (x > 0) { ... }` |
| While | `while (cond) { }` | `while (i < 10) { ... }` |
| Do-while | `do { } while (cond)` | `do { ... } while (i < 10)` |
| For | `for (init; cond; update) { }` | `for (var i = 0; i < 10; i++) { ... }` |
| Try/catch | `try { } catch (Type e) { }` | `try { ... } catch (String e) { ... }` |
| Finally | `finally { }` | `finally { ... }` |
| Throw | `throw expr` | `throw "error"` |
| Return | `return expr` | `return x + 1` |
| New | `new Type(args)` or `new Type[size]` | `new Dog("Rex")`, `new Int[10]` |
| Array access | `arr[index]` | `a[0]` |
| Array length | `arr.length` | `a.length` |
| String length | `str.length` | `s.length` |
| String concat | `str1 + str2` | `"Hello" + " World"` |
| Inheritance | `class Sub extends Super` | `class Dog extends Animal` |
| Implementation | `class Name implements Iface` | `class Dog implements Speaker` |
| Super | `super(args)` | `super(name)` |
| Override | implicit (same name) | `speak()` overrides |

### Supported modifiers

`public`, `private`, `protected`, `static`, `final`, `abstract`, `override`

> `override` is **accepted** as a modifier (backward-compatible) but **not required**:
> override is implicit — the same method name overrides. `training/idioms/classes.md`.

### Primitive types

`bool`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `string`, `void`

### Literals

- Integer: `42`, `0xFF`
- Long: `42l`
- Float: `3.14f`
- Double: `3.14`
- String: `"texto"`
- Char: `'c'`
- Boolean: `true`, `false`
- Null: `null`

### Operators

Arithmetic: `+`, `-`, `*`, `/`, `%`
Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=`
Logical: `&&`, `||`, `!`
Assignment: `=`, `+=`, `-=`, `*=`, `/=`
Bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`

---

## Types

### Primitive types

| Type | Size | Description |
|------|---------|-----------|
| `bool` | 4 bytes | Boolean |
| `byte` | 1 byte | Signed byte |
| `short` | 2 bytes | Signed short |
| `int` | 4 bytes | Signed integer |
| `long` | 8 bytes | Signed long |
| `float` | 4 bytes | IEEE 754 floating point |
| `double` | 8 bytes | IEEE 754 floating point |
| `char` | 4 bytes | UTF-32 codepoint |
| `string` | reference | Kof String (UTF-8) |
| `void` | — | No return |

### Reference types

| Type | Description |
|------|-----------|
| `ClassType` | Class or record |
| `ArrayType` | Array of type |
| `InterfaceType` | Interface |

### Compound types

- **Records**: `record Point(Int x, Int y)` — immutable, user-defined fields
- **Classes**: `class User { ... }` — mutable, fields + methods
- **Interfaces**: `interface Speaker { ... }` — contracts

---

## Object Orientation

### Classes

```kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    public getName(): String {
        return name
    }
}
```

### Inheritance

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
}
```

### Interfaces

```kof
interface Speaker {
    speak(): String
}
class Dog implements Speaker {
    public speak(): String {
        return "woof"
    }
}
```

### Virtual Dispatch

- Methods are resolved by the object's real type at runtime
- `Animal a = new Dog()` → `a.speak()` calls `Dog.speak()`
- Implemented via vtable on the Native backend
- JVM uses native `INVOKEVIRTUAL`

### Records

```kof
record Point(Int x, Int y)
// Generates: class, constructor, accessors x(), y(), toString()
```

---

## Runtime

### JVM

- Delegates to JVM facilities
- GC: uses the JVM GC
- Memory: managed by the JVM
- Strings: `java.lang.String`
- Arrays: native JVM arrays

### Native

- Assembly x86-64 System V AMD64 ABI
- No libc dependency
- Allocation via mmap (free-list `kof_free_head` with reuse, 27/08)
- GC: mark-sweep pending; auto-GC disabled after hang — memory
  returned only on the `munmap` fallback (reclaimed by the OS on exit)
- Concurrency: `spawn`/`await` via `pthread_create` + trampoline +
  `pthread_join` + thread-safe allocator (futex) — 31/08 (CONC001)
- Real FP in XMM (`vcvtsi2sd`/`mulsd`), dtoa via `snprintf` — 31/08 (FLT001)
- Strings: KofString (header + UTF-8)
- Arrays: KofArray (header + elements)
- Objects, inheritance, virtual dispatch and instanceof with hierarchy:
  real execution validated by E2E tests (compile → assemble → link → run)
- Native string methods: length, charAt, substring, contains, startsWith,
  endsWith, concat
- valueOf (int/char/bool → KofString) implemented in the runtime
- JSON: objects/records + `Int/Long/Bool/String/Double` arrays (31/08)

### Object Model

```
Header (16 bytes):
  offset 0:  type_id (4 bytes)
  offset 4:  flags (4 bytes)
  offset 8:  method_table_ptr (8 bytes)

Fields:
  offset 16: field_0
  offset 24: field_1
  ...
```

---

## Backends (0.2.6-beta, 31/08)

| Feature | JVM | Native x86_64 | native.risc (riscv64) | native.arm (aarch64) | JS (GraalJS) | KofC | Android (Phase 1) |
|---------|-----|---------------|----------------|----------------|--------------|------|-----------|
| Target | .class / .jar | ELF x86_64 | ELF riscv64 via qemu (codegen x86_64 placeholder) | ELF aarch64 via qemu (codegen x86_64 placeholder) | ES Modules (.mjs) | ELF x86_64 (C subset) | Maven project + APK (JVM bytecode) |
| Runtime | JVM (virtual threads, web ws/sse, cache, http retry/circuit) | Assembly x86-64 (free-list, pthread spawn, FP XMM) | toolchain + qemu | toolchain + qemu | GraalJS embedded + `Java HttpClient` interop | Native only (`kof_c`) | ART (dex via d8) |
| GC | JVM GC | free-list `kof_free_head` (mark-sweep pending; auto-GC disabled — `munmap` fallback) | same | placeholder | GC JS | none | ART GC |
| Strings | java.lang.String | KofString | via qemu (x86_64) | via qemu (x86_64) | JS string | C char* | KofString (dex) |
| Arrays | native arrays | KofArray | via qemu (x86_64) | via qemu (x86_64) | JS Array | C array | native arrays |
| Virtual dispatch | INVOKEVIRTUAL | vtable | via qemu (x86_64) | via qemu (x86_64) | prototype | — | INVOKEVIRTUAL |
| Interfaces | INVOKEINTERFACE | vtable | via qemu (x86_64) | via qemu (x86_64) | — | — | INVOKEINTERFACE |
| Exceptions | JVM exceptions | own unwinding | via qemu (x86_64) | via qemu (x86_64) | JS throw | — | JVM exceptions |
| print/println | System.out | Linux syscalls (`write` 1) | riscv64 syscalls (`li a7 64`) | aarch64 syscalls | `kof_platform` | `write` | System.out |
| Pattern matching | ✅ `case String s` + `Point(x,y)` | ✅ | via qemu (x86_64) | placeholder | ✅ (`typeof`) | — | ✅ |

---

## Type Safety

- Static and strong typing
- Compile-time checking
- Limited implicit coercion (primitive widening)
- String + anything → String (concatenation)
- Invalid operations rejected by the compiler

---

## Errors

### Compile-time

- Nonexistent variable
- Nonexistent method
- Incompatible type
- Incompatible argument
- Wrong number of arguments

### Runtime

- Null pointer → `kof_null_error` (fatal)
- Array bounds → `kof_bounds_error` (fatal)
- Allocation failure → returns null
- Panic → `kof_panic` (fatal)

---

## Performance

### Known architectural bottlenecks

1. **kof_alloc** uses mmap (slow for small allocations; free-list with `mmap` reuse mitigated it — 27/08)
2. **kof_string_concat** copies byte by byte
3. **kof_memcpy** copies byte by byte
4. **kof_print_int** uses division in a loop
5. **Mark-sweep GC pending** — free-list reuses memory; return to the OS only on the `munmap` fallback (auto-GC disabled after hang)
6. **Active IR optimizer** (constant folding, branch simplification, DCE, dead stack effects) — but without escape analysis/loop optimization

---

## What does NOT exist (residual 0.2.6-beta, 31/08)

- Reflection, Macros; enum/Class annotations on values (`ANNOT001`) — planned
- `kof init` (P5); LSP rename + Native Debugger DWARF/JS source maps (P5)
- Database level 3 (typed query DSL `User.query { where ... }`) — `kof.db` level 0 and `kof.orm` level 2/4 already DONE; MySQL wire protocol WIP (SHA-1 scramble + `user:pass@` parse, 31/08)
- Complete Native riscv64/aarch64 codegen (toolchain + qemu ready; codegen still x86_64 placeholder)
- Complete mark-sweep GC (free-list `kof_free_head` done; auto-GC disabled after hang; memory returned only on the `munmap` fallback)
- Scheduler on Native (SCHED001); `kof.http` on Native (HTTP002); web on Native/JS (WEB002/WEB001)

## What exists since 0.0.5 → 0.2.6-beta

- Generics (erasure) — 25/08 `Box<T>` fixed primitive `T` (`Box<Int>` + native `println` `kof_int_to_string` `CompilerDriver.java:2257`)
- `List<T>` (JVM + Native + JS), `listOf` + `map/filter/reduce` (0.2.0), for-in
- Pattern matching `switch case String s` + record destructuring `Point(x,y)` (JVM/Native/JS, 27/08)
- Basic `String?` null safety (`Type?` nullable, 27/08)
- Lambdas `(x: Int) -> expr` + if-expr + captures (`BoxN` box) on 3 targets
- Complete JSON encode/decode (JVM + Native + JS; objects/records + `Int/Long/Bool/String/Double` arrays on the 3 targets — 31/08)
- Real exceptions (JVM table + Native unwinding)
- `assert` + structured `kof test` (`test "name" {}`) + `process.exit`
- `spawn` (real concurrency on the 3 targets — JVM virtual threads, Native pthread 31/08, JS async/await/Promise 03/09)
- kof.io (File/Path/Directory, readFile/writeFile), kof.time (`now()`, `sleep`; `interval`/`every` JVM+JS)
- HTTP (`kof serve` — native web stack with WebSocket/SSE JVM 30/08), `kof.http` client (JVM+JS via `Java HttpClient`, retry/circuit 30/08), `kof.cache` (3 targets, 30/08), `kof.mq`
- `kof.validation`, `kof.observability` (health/metrics), `kof.security` (PBKDF2/SHA/JWT/AES-GCM + G9 rateLimit/session/apiKey on 3 targets), `kof.db` (JVM + native SQLite `.so` + MySQL WIP) / `kof.orm` + `kof.config`/`kof.log` (Native asm)
- KofJS (`js` target — embedded GraalJS, `kof.http` JS), TLS `web.listenSecure` (JVM), `KofScript` (`let` → `KofScriptGlobals`), `KofCcompiler` (`kof c`)
- `native.risc`/`native.arm` targets (`Target.NATIVE_RISCV64/AARCH64` — toolchain + qemu; codegen x86_64 placeholder)
- Android Phase 1 (`Target.ANDROID` — `kof build --target android` → Maven project + APK, host Activity in Kof)
- Language Server (`kof lsp` — real compiler frontend, hover/completion)
- `kof check`, `kof info`, `kof install`, `kof bench`/`profile`/`inspect`/`debug`, `kof script`/`repl`/`c`, `kof fmt` (31/08), `kof config gen` (31/08)
- Official distribution with embedded JDK 21, `VERSION` 0.2.6-beta versioning and releases via 2 jobs (`test-and-bump` → `package-and-release`) × 3 platforms (`release.yml`) — `scripts/package.sh` PASS
