[English](modules.md) | [Português](modules.pt_BR.md)

# Modules, Packages and Imports

**Status:** Stable (except where labeled) · **Evidence:** `Parser.parsePackage`/`parseImports`, `CompilerImports.java`, `MemberResolver.qualifyViaImports`, `CompilerTypes.java:48-138`

---

## 1. Compilation unit

The compilation unit is **one `.kf` file** (or `.ks` in KofScript). The
parser produces a `CompilationUnitNode(packageName, imports, declarations)`.

- **`package` is optional**; absent → package `""` (the "default" package, the class goes
  to `Default/Main`).
- **`import` comes after `package`, before the declarations.**
- **There is no** `module` keyword, nor `namespace`, nor a separate module file.
  Kof's "module" is the **root directory** passed to the compiler (module root),
  used to expand directory imports.

---

## 2. Packages

`ebnf
package-declaration = "package" , identifier , { "." , identifier } , [ ";" ]
`

`kof
package com.dev.app
`

- Dotted name, namespace semantics (maps to a JVM package).
- **There are no** package visibility directives besides `public`/`private`/
  `protected` per member.

---

## 3. Imports

`ebnf
import-declaration = "import" , ( "*" | import-path ) , [ ";" ]
import-path = identifier , { "." , identifier } , [ ".*" ]
`

`kof
import com.dev.NodeUI          // specific class
import com.dev.*               // package wildcard
import kof.json                // stdlib module
`

### 3.1 What an import does

1. **Simple name qualification**: `qualifyViaImports` resolves a simple name
   (without `.`/`<`/`[]`) through the **first** non-wildcard import that ends in
   `.<name>` (`MemberResolver.qualifyViaImports`).
   - **`import a.b.*` wildcards do NOT qualify simple names** (:57) — they only
     bring the declarations into scope (item 3.2).
   - An **ambiguous** import (two imports with the same simple name) → **does not guess**:
     the type is preserved without qualification (`simpleNamePackage` returns `null`,
     `CompilerTypes.java:102-122`). **Stable** (the anti-guess rule of bug 32).
2. **Type-arguments are qualified recursively** (`qualifyDeep`,
   `CompilerTypes.java:48-94`): `List<NodeUI>` with `import com.dev.NodeUI`
   resolves to `List<com.dev.NodeUI>` (bug 32). The simple name of the arg resolved
   by imports → classes of the module.
3. **Directory expansion** (`CompilerImports.expandKofImports`,
   called in `CompilerDriver.java`, method `compileSources`): `import a.b` where `a/b/` is a directory in the module
   root **pulls all the `.kf` of that directory** into the unit (fixpoint ≤256
   rounds). This is how separate files of the same package see each other.

### 3.2 Transitive imports and collisions

- Importing a package that imports another **re-exposes** the declarations (a
  transitive import is not a collision — PKG005 fixed).
- Two `main()` in files of the same module → **`PKG002`** (*probe*: "module
  has 2 main() functions; expected exactly one").

---

## 4. Name resolution (order)

For an identifier `x` (see [type-system.md](type-system.md) §6):

`text
local scope (parent chain)
  → args in main
  → unqualified enum constant
  → member of the current class (resolveInHierarchy BFS)
  → types/classes of the module (knownClasses, preDeclareType phase)
  → imports (qualifyViaImports)
  → builtin namespaces (json, process, KofWeb, …)
  → otherwise SEM011
`

- **There is no** `import static`, nor renaming (`import a.b as C`), nor
  `export`/re-export.
- **There is no** package-wildcard resolution for a simple name (item 3.1).

---

## 5. Standard library (`kof.*`)

The stdlib is a set of **namespaces** accessible via `import kof.<area>` and
used through a global object (`json.encode`, `http.get`, …). The namespaces
recognized by the analyzer (`SemExpressionTyper`/`MemberResolver`, builtin namespace list):

`text
json  process  KofWeb  KofConfig  KofCache  KofGpu  KofDb  KofOrm
KofLog  KofSecurity  KofValidation  KofObservability  KofHttp  KofMq
KofTime  KofScheduler  KofTetris  KofMedia  KofUi  Theme
`

Each area has its own document in `docs/stdlib*.md` (not duplicated here). The
**language** defines that these names exist and how they resolve; the
**library** defines the signatures. **Experimental** as a surface (changes between versions).

---

## 6. Interop with the target

- **JVM**: Java types are accessible by qualified name (`java.util.Date`)
  when on the classpath (`ExternalClasspath.resolveMethod`, `:1535-1549`).
  **Target-specific.**
- **C FFI (`extern "<lib>" f(T): R`)** — direct binding to native libraries
  (JVM, `java.lang.foreign`). **Measured surface 18/09 (0.4.0-beta)**: the JVM
  binds **any signature composed of the scalar set** `{Int, Long, Float, Double,
  Boolean, String}` in **every parameter position (arbitrary arity, ≥0)** and any
  of those as the **return**, plus **`void` return** (via `kof_ffi_void`, result
  discarded as a statement); a `String` return reads back the native `char*`
  (`MemorySegment.getString`). One runtime helper `kof_ffi(lib, name, sig,
  Object[])` (FFM downcall; `sig` encodes the layout) replaced the old
  `kof_ffi_i`/`_si`/`_dd` trio; gate `CompilerPipeline.isExternBound`. Still NOT
  bound — honest `FFI001` at compile time, never a silent stub (R6): struct/array/pointer
  ABI (design D6, ⛔ maintainer), callbacks/upcalls (3.4), variadics (3.5, ⛔) and
  opaque handles/out-buffers (3.3, ⛔). JS emits
  `FFI002` ("FFI not available on the JS target"); Native emits `FFI001`
  (`<target>` not supported yet). A missing lib/symbol fails at **runtime** with
  a `kof_ffi` exception naming `lib::symbol` (stack trace, not a surgical
  message). Remaining R3 slices (void, structs/D6, JS/Native parity) in
  `docs/development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (use-case #431);
  `extern "c"` on Native depends on §61.

- **Native/JS**: there is no interop with host types the same way. **Unspecified.**
- **Annotations** (`@Name`, `@JsonFormat`) are interop metadata emitted in the
  JVM bytecode. **Target-specific** (only JVM preserves them).

---

## 7. Files and extension

- **`.kf`** — Kof (compilable to all targets).
- **`.ks`** — KofScript: **pure Kof executed directly** (no `let`/`const`/
  `async`/`fn` — it is not JavaScript). The wrapper only adds the script model
  (top-level `var`/`val` → `KofScriptGlobals`; statements → `main()`).
- **There is no** separate header/source, nor `.kfi`, nor preprocessor.
