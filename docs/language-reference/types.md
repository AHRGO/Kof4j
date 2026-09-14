[English](types.md) | [Português](types.pt_BR.md)

# Types — Catalog and Syntax

**Status:** Stable (except where labeled) · **Evidence:** `Type.java`, `BuiltinTypes.java`, TypeParser

This document lists **which types exist** and **how they are written**. The
*validity* rules (what can be assigned to what, when there is an error) are in
[type-system.md](type-system.md).

---

## 1. Primitive types (9)

`Type.java:8-16` defines exactly nine `PrimitiveType`:

| Type | `sort` | Notes |
|---|---|---|
| `void` | 0 | return/absence type only |
| `bool` | 1 | `true`/`false` |
| `char` | 2 | 16 bits, UTF-16 code unit |
| `byte` | 5 | 8 bits signed |
| `short` | 9 | 16 bits signed |
| `int` | 10 | 32 bits signed |
| `long` | 11 | 64 bits signed |
| `float` | 6 | IEEE-754 32 bits |
| `double` | 7 | IEEE-754 64 bits |

- **There is no `boolean`** as a type name — `bool` is the name. `Type.of`
  normalizes `"bool"|"boolean"|"Bool"|"Boolean"` → `bool` (`Type.java:82`),
  so `Boolean` *works* as a spelling alias, but the canonical name is
  `bool`.
- **There is no `uint`/`ulong`/unsigned types.**
- **There is no `unit`/`never`/`nothing`.**
- `void` is not a value type: using it as an expression → `SEM033`.
- **Arithmetic overflow is silent and wrap-around** (JVM semantics):
  `2147483647 + 1` → `-2147483648` (*probe*). There is no overflow check by
  default. **Implementation-defined** (inherited from the target).

### 1.1 `string` is a reference, not a primitive

`string` is a type keyword (`TokenType.STRING_TYPE`), but **not** a
`PrimitiveType`: it resolves to `ClassType("java.lang","String")`
(`BuiltinTypes.java:11`). It is the only "spelling primitive type" that is a
reference. `String` (capitalized) also resolves to the same (`Type.java:82`).

---

## 2. Named reference types

### 2.1 Program classes

`class`, `record`, `enum`, `entity`, `interface` defined in the module. Written
by name, with package when qualified (`com.dev.NodeUI`).

### 2.2 Builtin collections (`kof.*`)

| Type | Spelling | Note |
|---|---|---|
| `List<T>` | `List<Int>`, `listOf(…)` | `ClassType("kof","List")` |
| `Map<K,V>` | `Map<String,Int>`, `mapOf(k,v,…)` | `ClassType("kof","Map")` |
| `Set<T>` | `Set<Int>`, `setOf(…)` | `ClassType("kof","Set")` |
| `Channel<T>` | `channel<Int>()` | `ClassType("kof.concurrent","Channel")` |

`ArrayList`/`HashMap`/`HashSet` are **spelling aliases** that resolve to
`List`/`Map`/`Set` (`Type.java:69-80`). They are not separate types.

### 2.3 Concurrency

`Handle<T>` — the type of the value returned by `spawn` in expression position
(`kof.concurrent.Handle`). Recognized by predicate, not by a constant in
`BuiltinTypes` (`TypeChecker.isConcurrentHandle`).

### 2.4 `Object`

`ClassType("java.lang","Object")` (`Type.java:83`). Every reference type is
assignable to `Object`; primitives are auto-boxed to `Object`
(`TypeChecker.isAssignable`, primitive→`Object` case).

---

## 3. Composite types

### 3.1 Array

`ebnf
array-type = type-ref , "[]" ;        (* Int[], String[][], List<Int>[] *)
`

`ArrayType(componentType)` (`Type.java:35`). **One-dimensional by suffix**;
multidimensional is array-of-array (`Int[][]`). There is no size in the type.
Allocation: `new Int[n]`. Access: `a[i]` (method `.get()` on array → `SEM028`).

### 3.2 Nullable

`ebnf
nullable-type = type-ref , "?" ;      (* String?, Int?, List<Int>? *)
`

`NullableType(inner)` — a **wrapper**, not an atomic suffix (`Type.java:45`).
`T?` means "T or null". See rules in [type-system.md](type-system.md) §5.

### 3.3 Function type

`ebnf
function-type = "(" , [ type-ref , { "," , type-ref } ] , ")" , "->" , type-ref ;
`

`(Int) -> Int`, `(Int, String) -> Bool`, `() -> void`
(`FunctionType(parameterTypes, returnType, className)`, `Type.java:29`).
`className` is `null` for an anonymous lambda and filled with the synthetic class
in the lowering — **Implementation-defined**, not observable in the language.

### 3.4 Generic

`ebnf
generic-type = qualified-name , "<" , type-ref , { "," , type-ref } , ">" ;
`

`List<List<Int>>`, `Map<String, List<Int>>`. Nesting by depth
counting with `>>`/`>>>` splitting. There is **no** wildcard `? extends T` /
`? super T` in the language: `List<? extends Int>` *compiles* (the `?` is read as a
nullable suffix and `extends`/`Int` become garbage in the name) but **breaks at
runtime** with `NoClassDefFoundError: ?extendsInt` (*probe*) — **Unspecified /
not supported** (SG-007).

---

## 4. Literals and their types

| Literal | Type | Example |
|---|---|---|
| integer without suffix | `int` (or `long` if it does not fit) | `42` |
| suffix `l`/`L` | `long` | `9000000000L` |
| suffix `f`/`F` | `float` | `1.5f` |
| suffix `d`/`D` or decimal | `double` | `1.5`, `1.5d` |
| hex | `int` | `0xFF` |
| `"…"` | `string` | `"oi"` |
| `'…'` | `char` | `'a'` |
| `true`/`false` | `bool` | |
| `null` | null type (adapts to the context) | |

---

## 5. What is NOT a type in Kof (SG-003)

There are no: **type alias** (`typealias X = Int` → `PARSE011`, *probe*),
**traits** (`trait` is not a keyword; it becomes a function → `PARSE011`, *probe*),
**macros** (`macro m(){}` is parsed as a function called `macro`, not as a
macro — *probe*; there is no macro system), **intersection/union types**,
**literal/singleton types**, **`Optional<T>`/`Result<T>`** (use `T?` +
`throw`), **custom value types** (only the 9 primitives).

---

## 6. Type recursion

A type can refer to itself (by name): `class Node { Node next … }`,
`List<List<Int>>`. There is no stratification restriction — resolution is by
name, not by expansion. **Recursive types are allowed.**
