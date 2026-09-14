[English](specification-status.md) | [Português](specification-status.pt_BR.md)

# Specification Status

**Version:** 0.3.0-beta · **Date:** 06/09/2026

Classification of each language feature. **Nothing here is "stable" out of
courtesy** — Stable requires   (rule 0.2.6-beta) **and** a test
that proves it. Categories: **Stable · Experimental · Implementation-defined ·
Target-specific · Unspecified · Planned**.

---

## 1. Classification by feature

### Syntactic core
| Feature | Status | Test evidence |
|---|---|---|
| Lexer (tokens, literals, comments) | Stable | `Lexer` exercised by the whole suite |
| Recursive descent parser | Stable | `FunctionSyntaxTest`, `Parser` via E2E |
| Optional semicolon | Stable | probes + suite |
| Keywords (list) | Stable | `Lexer.java:13-74` |
| `sealed`/`permits` | **Unspecified** (dead tokens) | none (SG-002) |
| `fn`/`fun`/`func` prefix | **Stable** (rejected with `PARSE085`, SG-001 resolved 06/09) | `FunctionSyntaxTest` (5 cases) |

### Type system
| Feature | Status | Test evidence |
|---|---|---|
| 9 primitives | Stable | `Type.java`, suite |
| `string` as reference | Stable | `BuiltinTypes.java:11` |
| Implicit numeric widening | Stable | probes + `emitWideningIfNeeded` |
| Narrowing only via `as` | Stable | probe SEM021 |
| `bool→numeric` (=1/0) | **Implementation-defined** | probe (representation leaked) |
| Nullability `T?` | Stable | `KofPatternMatchingTest`, probes |
| Narrowing `if (x != null)` | Stable | probes |
| Deref `T?` without narrowing | **Unspecified** (compiles) | probe (SG-005) |
| Subtyping by inheritance | **Unspecified** (not checked) | probe (SG-009) |
| Generics (erasure) | Stable | `KofMapSetTest`, `PackagesE2ETest` |
| Variance (`? extends`) | **Unspecified** (breaks at runtime) | probe (SG-007) |
| Type-var bounds | **Planned/absent** | none |
| Ctor type-args inference | **Absent** | none |
| Elem check in `list.add` | **Unspecified** (does not check) | probe (SG-009) |
| `==` by type (content/identity) | Stable | probes + `CoreRegressionE2ETest` |
| Constructor overload (arity) | Stable | `SymbolTable.java:47` |
| Method overload | **Absent** | none |
| Default parameters | Stable | `lowerFunctionDefaults` |

### Functions and closures
| Feature | Status | Test evidence |
|---|---|---|
| 3 return forms | Stable | `FunctionSyntaxTest` |
| Expression body (`= expr`) | Stable | `FunctionSyntaxTest` |
| `main` (forms) | Stable | probes + `JvmE2ETest` |
| Direct recursion | Stable | probe `fact(5)` |
| TCO | **Absent** (not guaranteed) | none |
| Generic function | Stable | probe `idf<Int>` |
| Lambda (forms) | Stable | `LambdaE2ETest` |
| Snapshot capture | Stable | probe |
| Mutable capture (Box) | Stable | probe `n=2` |
| First-class function types | Stable | `KofHigherOrderTest` |
| Lambda param inference | **Unspecified** (requires annotation) | probe SEM001 |
| Nested function | **Unspecified** | none (SG-011) |
| Trailing lambda | Stable | `LambdaE2ETest` |

### Classes and data types
| Feature | Status | Test evidence |
|---|---|---|
| Mutable class + constructor | Stable | `ClassFileE2ETest` |
| `class X(...)` = record | Stable | `AGENTS.md`, probes |
| Record (equals/hashCode/toString) | Stable | `KofPatternMatchingTest` |
| Enum (constants only, value=String) | Stable | `KofEnumTest`, `KofEnumSwitchTest` |
| Interface (default methods) | Stable | probe |
| Interface without coverage check | **Unspecified** | probe (SG-015) |
| Inheritance + virtual override | Stable | probes |
| `private` at compile-time | **Unspecified** (runtime only) | probe (SG-013) |
| `abstract` non-instantiable | **Unspecified** (runtime only) | probe (SG-017) |
| Pattern matching (binding+destructuring) | Stable | `KofPatternMatchingTest` |
| Pattern with guard/nested | **Absent** | none (SG-014) |
| Entity (ORM) | **Experimental** | `KofOrmE2ETest` |
| Nested classes | **Unspecified** | none (SG-016) |
| Operator overload | **Absent** | none |

### Control flow
| Feature | Status | Test evidence |
|---|---|---|
| if/else (stmt + expr) | Stable | suite |
| while/do-while/for/for-in | Stable | suite |
| switch statement (no fallthrough) | Stable | `KofEnumSwitchTest` |
| switch expression (SYN001) | Stable | `KofSwitchExprE2ETest` 23/23 |
| break/continue (no label) | Stable | probes |
| Labeled break | **Absent** | probe (SG-002) |

### Modules and names
| Feature | Status | Test evidence |
|---|---|---|
| package | Stable | `PackagesE2ETest` |
| import (class) | Stable | `PackagesE2ETest` |
| import wildcard (brings decls) | Stable | `CompilerImports` |
| import wildcard (qualifies name) | **Absent** (does not qualify) | `:57` |
| qualifyDeep (type-args) | Stable | `PackagesE2ETest` (bug 32) |
| Ambiguous import (does not guess) | Stable | `CompilerTypes:102` |
| PKG002 (1 main) | Stable | probe |
| JVM interop (Java types) | **Target-specific** | `AndroidInteropE2ETest` |

### Concurrency
| Feature | Status | Test evidence |
|---|---|---|
| spawn (statement) | Stable | `KofConcurrency2Test` |
| spawn (expression → Handle) | Stable | probe |
| await | Stable | `KofAwaitTest` |
| awaitTimeout | Stable | probe |
| Channel | Stable | `KofConcurrency2Test` |
| Memory model | **Unspecified** | none (SG-020) |
| `spawn { lambda }` with handle | **Bug #29** | `known-bugs.md` |

### Exceptions
| Feature | Status | Test evidence |
|---|---|---|
| throw String | Stable | `ExceptionsE2ETest` |
| try/catch/finally | Stable | `ExceptionsE2ETest` |
| `throws` validated | **Absent** | probe (SG-019) |
| Per-target representation | **Target-specific** | `ExceptionsE2ETest` |

### Stdlib (`kof.*`)
| Feature | Status | Test evidence |
|---|---|---|
| json | Stable (3 targets) | `JsonCompleteE2ETest` |
| collections (List/Map/Set) | Stable | `KofMapSetTest` |
| string methods | Stable | `StringMethodRegistry` |
| http / web / db / orm / cache / mq / time / scheduler / log / config / security / validation / observability / ui / media / process | **Experimental** | E2E per area |
| Map/Set with class type-arg | **Bug #33** | `known-bugs.md` |

---

## 2. Conformance — what prevents a rigorous definition today

A conformance definition (accept valid, reject invalid, preserve
meaning) **cannot be rigorous** as long as the following exist:

1. **Unspecified rules** listed above (subtyping, null-deref, memory
   model, nested classes, top-level overloading, main exit code).
2. **Implementation-defined rules** that leak into observable behavior
   (`bool→int`=1/0, non-immutable `val`, evaluation order of `x++` in an
   expression, slot layout).
3. **Target-specific divergences** not formalized (JS short-circuit,
   exception representation, GC, extreme FP, Map order).
4. **Open bugs** that make the real behavior diverge from what is expected
   (#29 spawn-handle, #33 Map/Set emit).
5. **Absence of a "valid program" oracle**: without the *normative* formal
   grammar (the one here is *extractive*), there is no way to say whether a program that the
   parser accepts *should* be accepted.

**Path to conformance** (recommendation, not implemented):
- Close the SG-00x (decide each Unspecified).
- Freeze the EBNF grammar as normative (not just extractive).
- Extract the E2E tests per target into a *conformance suite* with expected
  outputs per rule (not per file).
- Define a minimal conformance profile (stable core) vs experimental.

---

## 3. Count summary

- **Stable**: core (syntax, primitive types, widening, basic nullability,
  generics erasure, `==`, functions, closures, classes/records/enums/interfaces,
  control flow, packages/imports, basic concurrency, exceptions, json,
  collections).
- **Experimental**: domain stdlib (http/web/db/orm/ui/media/…).
- **Implementation-defined**: `bool→numeric`, `val`, frame layout,
  spawn mechanism.
- **Target-specific**: GC, extreme FP, exception (representation), JS
  short-circuit, interop (JVM only), Map order.
- **Unspecified**: ~20 points (SG-002 to SG-020).
- **Planned/Absent**: type-var bounds, operator overloading, labeled
  break, `~`, ternary, range, macros, traits, type alias.
