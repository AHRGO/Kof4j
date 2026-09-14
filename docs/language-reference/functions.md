[English](functions.md) | [Português](functions.pt_BR.md)

# Functions

**Status:** Stable (except where labeled) · **Evidence:** Parser.parseFunctionDeclaration, `SemanticAnalyzer.analyzeFunction`, `CompilerDriver.java` (`lowerFunction`/`lowerFunctionDefaults`)

---

## 1. Declaration

`ebnf
function-declaration = [ type-ref ] , identifier , [ type-parameters ] ,
                       "(" , [ parameter-list ] , ")" , [ ":" , type-ref ] , function-body
`

The canonical forms (all valid and equivalent):

`kof
main() { println("entry point") }            // no type → void
String saudacao() { return "oi" }            // type before the name
despedida(): String { return "tchau" }       // type after the parentheses
void fazIsso() { println("x") }              // explicit void
Int dobro(Int x) { return x * 2 }
Bool positivo(Int x) = x > 0                 // expression body
`

- **Body**: block `{ … }`, or `= expression` (expression body — becomes
  `return expression`), or `;` (abstract).
- **There is no declaration keyword** (SG-001 resolved 06/09): `fn`/`fun`/
  `func` are **reserved words** (tokens `FUN`/`FN`/`FUNC` in the lexer) and
  **do not exist** in Kof — neither as a declaration prefix (`PARSE085`), nor
  as a function, variable, parameter, or field name. KofScript (`.ks`) keeps
  KofScript (`.ks`) **does not** have its own `fn` — it is pure Kof; `fn` there also gives
  `PARSE085`.
- **There is no** `fun` as a keyword, nor `def`, nor `lambda` keyword.

---

## 2. Parameters

`ebnf
parameter = ( type-ref , identifier | identifier , ":" , type-ref ) , [ "=" , expression ]
`

Two valid orders:

`kof
f(Int x, String s) { }      // type-first
f(x: Int, s: String) { }    // annotated (idiomatic for main)
`

- **Default values**: `f(Int x = 10)` — generate **synthetic overloads by
  decreasing arity** in the lowering (`lowerFunctionDefaults`,
  `CompilerDriver.java`, method `lowerFunctionDefaults`). A call with reduced arity is accepted.
- **Parameters are passed by value** (references: the value is the reference).
- **There are no** by-reference parameters (`ref`/`out`), nor varargs (`T...`),
  nor spread (`f(*args)`).

---

## 3. Return

- Return type **before the name** or **after `:`** after the parentheses.
- **No declared type → `void`** (default).
- **Return inference**: a function declared `void` whose body has
  `return <value>` has its return **inferred** in the fixpoint (≤4 passes,
  `analyzeMethodBody:470-477`). `Int f() { return 1 }` and `f() { return 1 }`
  (declared void, inferred Int) — the second is **Implementation-defined**.
- `return` without a value in a non-void function → the type's default value (see
  [statements.md](statements.md) §3).

---

## 4. Entry point (`main`)

A Kof program needs **exactly one** `main` (PKG002 if 0 or >1).
Accepted forms (*probe*, all compile):

`kof
main() { }                       // no args, no type
void main() { }                  // explicit void
Int main() { return 0 }          // returns Int (the value does NOT become the exit code automatically — Unspecified)
main(args: List<String>) { }     // args as List
main(args: String[]) { }         // args as array
`

- `main` is **recognized by name** (`"main".equals(func.name())` + arity 0
  or 1-arg-`args`, `CompilerDriver.java`, method `lowerFunctionInner` (`isMain`)). It is not a keyword.
- The compiler **rewrites the signature** to `main(String[])` at emit
  (it injects `String[]`); `List<String>` is converted in the prologue (JVM) or
  becomes an empty list (Native/JS).
- **There is no** `@main` annotation, nor a mandatory `Main` class, nor a visibility
  restriction.

---

## 5. Recursion

- **Direct recursion is supported**: `Int fact(Int n) { … return n * fact(n-1) }`
  → `120` (*probe*).
- **Mutual recursion between top-level functions** is supported (call
  resolution scans the unit's declarations).
- **There is no guaranteed TCO** (tail-call optimization) — deep recursion can
  blow the target's stack. **Implementation-defined / Target-specific.**
- Recursion in **methods** works (normal virtual dispatch).

---

## 6. Generic functions

`ebnf
function-declaration = … , identifier , type-parameters , …
type-parameters = "<" , identifier , { "," , identifier } , ">"
`

`kof
T idf<T>(T x) { return x }
main() { println(idf<Int>(7)) }     // → 7 (probe)
`

- Type-params of a **function** are declared before the parentheses.
- **There is no function type-arg inference**: `idf(7)` without `<Int>` —
  **Unspecified** (positional substitution works for classes; for
  generic top-level functions the `TypeVariable` return is inferred from the
  corresponding argument, `MethodCallTyper.java:416-432`).
- Type-var bounds **do not exist**.

---

## 7. Visibility and modifiers

- `public` (default if none), `private`, `protected` — applied as JVM flags
  (`computeAccess`, `CompilerDriver.java`).
- `static` — a top-level function is always `PUBLIC|STATIC` at emit; `static` on a
  method makes it callable by class name.
- `abstract` — method without a body (`isAbstractMethod` = `body == null`,
  `:3393`).
- `final`, `override` — accepted as modifiers; `override` is **not**
  validated (there is no check that the method exists in the super).
- **There is no** `internal`, `module`, `open`, `sealed` (function).

---

## 8. Where functions live

- **Top-level**: compiled into the `Main` class (or `<pkg>/Main`) as `static`
  methods (`CompilerDriver.java`, method `lowerToIR`).
- **Class members**: normal methods.
- **There are no** nested functions (function inside a function) — `main() { f() {} }`
  is not parsed as a nested function declaration. **Unspecified** (SG-011).
- **There are no** named local functions; for local named behavior, use a
  lambda in a `val`.

---

## 9. Function overloading

- **There is no top-level function overloading** — two functions with the same name in
  the same unit collide (the `define` overwrites; `resolveInHierarchy` returns
  one). **Unspecified** whether it is an error or last-wins.
- **Constructors** overload by arity (see [classes.md](classes.md)).
- **Methods** of a class overload by signature (§131 closed 13/09 —
  see §11 of type-system.md).
