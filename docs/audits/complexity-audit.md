[English](complexity-audit.md) | [Português](complexity-audit.pt_BR.md)

# Complexity Audit

> **HISTORICAL SNAPSHOT — moved from `docs/development/` to `docs/architecture/` on
> 09/12 and from there to `docs/audits/` in the clarity refactor of 09/13**: the snapshot is from 09/02 (0.2.6-beta, 810 tests) and the line numbers have already
> changed — the megaclasses listed were split by `PLAN-SOLID-500`.
> Kept as a record of the decision "Kof is small"; the live gate is
> `scripts/check_500.sh` + `docs/architecture/PLAN-SOLID-500.md` (CLOSED 09/13).
> The rule "ask before adding a feature" (§Conclusion) remains in force.

**Last updated:** September 2, 2026
**Version:** 0.2.6-beta (810 tests; 7 targets: jvm, native x86_64, native.risc/arm, js, kofc, android; free-list GC; `VERSION` 0.2.6-beta)

---

## Principle

> Kof must be small by design.

We do not want to accumulate features until it becomes another Java.

---

## What Exists Today

### Compiler Code

| File | Lines (08/31) | Function |
|---------|--------|--------|
| Lexer.java | ~480 | Lexical analysis (`case` pattern matching + `String?`) |
| Parser.java | ~1,720 | Syntactic analysis (pattern matching, `String?`, KofScript `let`, trailing lambda) |
| SemanticAnalyzer.java | ~1,870 | Semantic analysis (pattern matching, `String?`, `CompilerDriver.java:243` import fix) |
| CompilerDriver.java | ~7,520 | IR lowering (free-list alloc, `substituteTypeVariable` `Box<T>`, `KofScriptGlobals`, stdlib dispatch) |
| JvmBackend.java | ~1,320 | JVM backend (V21, LineNumberTable, web ws/sse, `Java HttpClient` for JS) |
| NativeBackend.java | ~1,800 | Native backend (x86_64; riscv64/aarch64 toolchain) |
| NativeRuntime.java | ~15,340 | Native runtime — most of it is embedded assembly (free-list `kof_free_head`, spawn pthread 08/31, FP XMM, JSON objects/arrays, config/log/security/cache asm, `kof_db_mysql_scramble`) |
| JsBackend.java | ~5,280 | JS backend (GraalJS, CORE_RUNTIME DOM/UI, `kof.http` interop) |
| KofCCompiler + Lexer/Parser/AST/Emitter | ~710 | C subset (`kof c`) → ELF x86_64 |
| KofScript.java | ~610 | KofScript (`let` → `KofScriptGlobals`, REPL) |
| IRNodes.java | ~250 | Intermediate representation (+ KofDebugInfo) |
| Type.java | ~140 | Type system (`Type?` nullable) |
| SymbolTable.java | ~210 | Symbol table |
| ClassLayout.java | ~110 | Memory layout |
| Optimizer.java | ~610 | IR optimizer (always active) |
| Others | ~10,000 | JVM runtime generation (`JvmRuntime`/`JvmWebRuntime`...), stdlib descriptors (`KofWeb`, `KofSecurity`, `KofUi`, `KofDb`...), utilities |

**Total:** ~46,600 lines in `kof-compiler` (main; ~51,800 counting `kof-cli`/`kof-script`/`kof-c-compiler`) (0.2.6-beta, 08/31) — the weight comes from embedded native assembly and the generated JVM runtime, not from Java logic.

### What is necessarily complex

- Lexer: must recognize the entire syntax
- Parser: must deal with ambiguities
- SemanticAnalyzer: must resolve types and members
- NativeRuntime: must implement low-level functions

### What can be simplified

1. **NativeTypeMapper** — not used by NativeBackend (dead code)
2. **JvmTypeMapper.BUILTIN_TYPES** — map defined but not read
3. **FieldLayout.naturalSize()** — method never called
4. **ClassLayout.clearCache()** — no-op
5. **JvmBackend.computeLocals/computeStack** — values discarded by ASM

---

## Necessary vs Unnecessary Abstractions

### Necessary

- **IR** — boundary between frontend and backends
- **ClassLayout** — centralized offset calculation
- **BuiltinTypes** — centralized type references
- **NativeRuntime** — native runtime functions

### Potentially Unnecessary

- **NativeTypeMapper** — not used
- **JvmTypeMapper.BUILTIN_TYPES** — not used
- **FieldLayout.naturalSize()** — not used

---

## Accidental Complexity

### In the Compiler

1. **Parser**: 1,720 lines — grew with pattern matching, trailing lambda, KofScript `let`
2. **SemanticAnalyzer**: 1,874 lines — reasonable for semantic analysis (includes `supportedOn`/gap diagnostics)
3. **CompilerDriver**: 7,521 lines — lowering + dispatch of the entire stdlib; priority candidate for helper extraction
4. **NativeBackend**: 1,803 lines — complex but necessary (x86_64 + toolchains)

### In the Runtime

1. **NativeRuntime**: 15,341 lines — embedded assembly is verbose but necessary (free-list, spawn pthread, FP XMM, JSON, config/log/security/cache/db in asm)
2. **Runtime functions** — broad subset but each function covers a stdlib module

---

## What Could Be Simplified

1. **Remove NativeTypeMapper** — not used
2. **Remove JvmTypeMapper.BUILTIN_TYPES** — not used
3. **Remove FieldLayout.naturalSize()** — not used
4. **Remove ClassLayout.clearCache()** — no-op
5. **Simplify CompilerDriver** — extract helpers

---

## Architecture rule — 500-line-per-class limit (future)

> **Registered 09/02/2026 — mandatory general refactor in the future.**

**Rule:** no class may have more than **500 lines**. Large classes
are an architectural smell: multiple responsibilities, coupling, painful
diffs and a barrier for agents/humans to understand.

**Current state (violations):**

| File | Lines | What it is |
|---------|--------|---------|
| `NativeRuntime.java` | **~17,300** | Embedded x86-64 assembly (free-list, spawn pthread, FP XMM, JSON, config/log/security/cache/db) + C runtime |
| `CompilerDriver.java` | **~8,200** | IR lowering + dispatch of the entire stdlib |
| `JsBackend.java` | **~5,700** | JS backend (GraalJS) + DOM/UI runtime |
| `Parser.java` | **~1,800** | Syntactic analysis |
| `SemanticAnalyzer.java` | **~2,000** | Semantic analysis |
| `JvmBackend.java` | **~1,400** | JVM backend (ASM) |

**How to get there (future refactor):**

1. **`NativeRuntime.java`** — the embedded assembly (gigantic Java strings) must
   become **separate modules per domain** (e.g.: `native/asm/*.s` included in
   the build, or classes `NativeRuntimeMemory`/`NativeRuntimeJson`/…) with a
   concatenator. It is the largest effort (it is the source of the "tens of
   thousands of lines of assembly").
2. **`CompilerDriver.java`** — extract helpers per area (expression
   lowering, stdlib dispatch, collections, json, web) into dedicated classes.
3. **`JsBackend.java`** — separate the emitter from the embedded runtime.
4. `Parser`/`SemanticAnalyzer`/`JvmBackend` — extract sub-parsers/validators.

**Acceptance criterion:** `find src -name '*.java' | xargs wc -l | sort -n |
tail` must not show any class above 500 lines.

**Note:** `git` does not split by classes — use `grep -n '^class '`/an IDE to
count per declaration, or a metrics tool (e.g.: `cloc` per class) in the
refactor PR.

---

## Conclusion

Current Kof has ~46,600 lines in `kof-compiler` (08/31), of which ~15,300 are
native assembly embedded in `NativeRuntime.java` and ~10,000 are generated JVM
runtime templates — the "pure" Java logic of the frontend/backends remains on
the scale of tens of thousands, comparable to:
- Lua: ~20,000 lines
- Zig: ~150,000 lines
- Go: ~1,500,000 lines

Kof is small. The goal is to keep it that way — and the recent growth came from
the native implementation (asm), not from extra Java layers.

**Rule:** before adding a feature, ask:
1. "Does this solve a real problem?"
2. "Is there a simpler way?"
3. "How much code does this add?"
4. "Can this be solved by the runtime instead of the compiler?"
