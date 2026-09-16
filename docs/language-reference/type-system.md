[English](type-system.md) | [Português](type-system.pt_BR.md)

# Type System and Type Checking

**Status:** Stable (rules) · **Evidence:** `SemanticAnalyzer.java`, `Type.java`, `CompilerTypes.java`, `TypeMetrics.java`, execution probes

> This is the most important document in the reference. It avoids the vague term
> "strong typing" and describes **concrete behavior**: what is accepted, what
> is rejected, when there is inference, what is guaranteed and what is **not**.

---

## 1. System classification

Kof is **statically typed** (types resolved at compile-time), with
**local inference** (from `var`/`val` and `void` return), **nominal** for
classes (subtyping by name/inheritance, not structural) and **with erasure** for
generics (type-args erased at emit, like Java).

The term "strong typing" is **not** used here as praise. The concrete
properties — and the **guarantee failures** — are in the following sections. Where the
type checker does **not** prevent an operation, that is stated explicitly.

> **Update 09/09 (maintainer decisions on gaps B):** the compilation guarantees
> became strict at the points that were missing — instantiation of
> `abstract` (SEM041), nested type (SEM042), `implements` coverage
> (SEM043), `main` signature (SEM044), `throw` clause (SEM045) and
> `private`/`protected` visibility (SEM046) are compile-time errors.
> Lambda in a collection inherits the element type without annotation (SG-012); a nested
> function works with hoisting (SG-011); guards in pattern matching
> (`case T v if cond`) are supported (SG-014). See the error table in §13.

---

## 2. Where type checking happens (real pipeline)

`text
Source ─▶ Lexer ─▶ Tokens ─▶ Parser ─▶ AST(raw)
       ─▶ Desugar (test/application) ─▶ AST(desugared)
       ─▶ SemanticAnalyzer.analyze ─▶ AST + side maps (resolved types)
       ─▶ [aborts if there is an error] ─▶ Lowering AST→IR ─▶ Optimizer ─▶ Backend
`

`CompilerDriver.java`, method `lowerAndEmit`. **Type checking and name resolution are NOT
separate phases**: they happen interleaved inside `inferType`
(`SemanticAnalyzer`/`SemExpressionTyper.inferType`), which resolves the name and checks the type at the same
point, emitting a diagnostic inline.

### 2.1 The analyzer's 4 phases (`SemanticAnalyzer.analyze`)

| Phase | Method | What it does |
|---|---|---|
| 1 | `preDeclareType` | Creates an empty `ClassSymbol` per type; registers `knownClasses`; synthesizes `values()/valueOf()/name()` in enums |
| 2 | `defineMembers` | Fills fields/methods/constructors; record accessors; type-params |
| 3 | `analyzeDeclaration` | Analyzes bodies; **fixpoint ≤4 passes per class** (void→T return inference) |
| 4 | `resolveMethodCalls` | **Effectively a no-op** — the real resolution already happened eagerly in phase 3 (`SemanticAnalyzer.resolveMethodCalls`) |

> **Post-REFACTOR-500 F6 note:** phases 1–2 were extracted into
> `SymbolTableBuilder`; type checking (`isAssignable`, `primitiveWidth`,
> `checkArgTypes`, `inferBinaryResultType`) into `TypeChecker`; statement
> analysis (`IfStmt`/narrowing) into `StatementAnalyzer`; expression
> inference into `SemExpressionTyper`. `SemanticAnalyzer` orchestrates. The
> references below use **method**, not line (the refactor is in progress).

### 2.2 There is no "typed AST"

The AST nodes **do not carry a resolved type** — declaration types are
`String` in the AST. Resolved types live in **side maps by node
identity** (`IdentityHashMap`): `expressionTypes`, `resolvedMethods`,
`resolvedConstructors` (fields of `SemanticAnalyzer`). The lowering **re-infers**
everything via `ExpressionTyper`/`MethodCallTyper` (the analyzer's cache is cleared each
pass/class — `MethodCallTyper.java:27-34`). **Implementation-defined.**

### 2.3 When errors are reported

During analysis, immediately (`diagnostics.error`), and the driver **aborts
before lowering** if there is an error (`CompilerDriver.java`, guard `diagnostics.hasErrors()` post-`analyze`). Exception:
some `SEM0xx` codes are **deferred** to lowering/emit (SEM016/017/029/
030/031/033/034, ARITH001) — they only fire if the analysis passed.

---

## 3. Assignment and compatibility (`TypeChecker.isAssignable`)

An assignment `dest = src` (and arguments, returns) is accepted when:

| Rule | Accepted? | Evidence |
|---|---|---|
| `T → T` (equal) | ✅ | `from.equals(to)` |
| `T → T?` (makes nullable) | ✅ | `NullableType` case in `to` |
| `T? → T?` (recurses on the inner) | ✅ | `inner()` recursion |
| **`T? → T`** (unwrap nullable) | ❌ `SEM021` | only after narrowing (§5) |
| numeric widening (`primitiveWidth(from) ≤ primitiveWidth(to)`) | ✅ | `TypeChecker.primitiveWidth` |
| `double → float` | ✅ (explicit exception, D2F) | `double→float` case |
| numeric narrowing (`long→int`, `double→int`, `int→byte`) | ❌ `SEM021` | *probe*: `Long x; Int y = x` → SEM021 |
| `primitive → Object` (auto-box) | ✅ | primitive→`java.lang.Object` case |
| `FunctionType → ClassType` (SAM) | ✅ (always; real compatibility deferred to emit) | SAM case |
| `TypeVariable` in any position | ✅ | `TypeVariable` case |
| **`ClassType → ClassType` (any pair)** | ✅ **ALWAYS** | final case `to instanceof ClassType` — ⚠️ see §7 |

### 3.1 `TypeChecker.primitiveWidth`

`bool=0, char=1, {int,byte,short}=2, long=3, float=4, double=5`.

Observable consequence: `bool → int` **passes the check** (width 0≤2) and
**produces `1`/`0`** at emit — because `bool` is stored as `int` 1/0 in Kof
(`var i: Int = true; println(i)` → `1`, *probe*). It is not a "void": it is functional
coercion by representation. Assignments `bool → long/float/double` follow the
same widening path. **Implementation-defined** (the 1/0 representation is an implementation
detail that leaked into observable semantics).

### 3.2 Coercions in arithmetic (`commonNumericType`, TypeMetrics.java:57-70)

For `+ - * / %` with two numerics: `double` dominates, else `float`, else
`long`, else `int`. `7 / 2` → `int` = `3` (integer division) (*probe*).
`1 + 1.5` → `double` = `2.5`.

---

## 4. Conversions: `as` and `instanceof`

- **`x as T`** is an **explicit cast**, never implicit. The result has type `T`
  (`TypeChecker.inferBinaryResultType`, case `as`).
  - primitive→primitive: widening + narrowing (`I2C`, `L2I`, `F2I`, `D2I`, …)
    — `Long x; x as Int` works (*probe*).
  - reference: JVM `checkcast` (may throw `ClassCastException` at runtime).
  - **`5 as String` does NOT parse a number**: it emits `checkcast String` over
    a boxed `Integer` → fails at runtime (*probe*). String↔number is only via
    `toInt()/toLong()/toDouble()/toFloat()` (methods of `string`).
- **`x instanceof T`** → `bool` (`SemExpressionTyper`, case `instanceof`). `o instanceof String` (*probe* ✅).

---

## 5. Nullability

- Representation: wrapper `NullableType(inner)`. `T?` = "T or null".
- **Storage is the inner** — nullable is a compile-time constraint only
  (`StatementLowerer.java:47-51`).
- **Narrowing**: the **only** recognized form is `if (x != null)` (or `null !=
  x`) with `x` an identifier of type `T?` → in the **then-branch**, `x` now has
  type `T` (`StatementAnalyzer`, `IfStmt` narrowing). There is **no** narrowing by `&&`,
  `||`, ternary, or `if (x == null)` in the else.
- **Deref of `T?` without narrowing IS an error** (SG-005 fixed 10/09,
  `9436da12`): `var s: String? = "x"; s.length` → `error: receiver is nullable
  (T?); narrow first` [**SEM049**] (*measured 16/09, jar of tip `803eeef4*`).
  The old "advisory, not guaranteed" behavior is gone — narrowing (`if (x != null)`)
  is mandatory.
- **Comparison with null**: primitive `== null` → **constant** (`false`/`true`,
  `ExpressionLowerer.java:256-268`); reference `== null` → `if_acmp` (class/
  String narrow correctly — *measured 16/09*). **Record is the exception**:
  `==`/`!=` on a record lowers to `.equals()` with no null-guard, so a null
  `Point?` compared to `null` **NPEs** (bug §262, open). `Int? == Int?`
  compares value (*probe*: `5 == 5` → true). **`Int? == null` does NOT throw
  — it folds silently**: a null `Int?` (map miss) compares `== null` as
  **false** (`if (n == null)` printed `not-null`, *measured 16/09, jar of tip*)
  because the nullable-primitive storage is the inner (`null`→`0` at the
  boundary). That silence is the open bug **D-NULL-INTENT / #259** (§125
  measures the fold) — NOT a language rule; the boxed contract keeps failing
  its other faces (§241 reverted to the honest gap). `String? == null` →
  `true` correctly (*measured 16/09*).
- **Sources of `T?`**: `Map.get(k)` for a reference value, `readLine()`,
  `readFile()`, a function declared `T?` that `return null`s. There is **no
  `T? = null` literal** — the null-literal is rejected since 10/09 (SG-008 →
  **SEM048**, *measured*: `null cannot be assigned [SEM048]`).

---

## 6. Name resolution and scope

`SymbolTable` is a chain of scopes with `parent` (`SymbolTable.java:9-77`);
`resolve(name)` searches from the innermost to the outermost. Resolution order
of an identifier (`SemExpressionTyper`, case `IdentifierExpr`):

1. local scope in the chain (locals → params → class fields → root)
2. `args` in `main` → `String[]`
3. unqualified enum constant (`Red` when `enum Color{Red}`)
4. member of the current class via `resolveInHierarchy` (BFS: class→super→interfaces)
5. otherwise, if it is not a builtin namespace (`json`, `process`, `KofWeb`, …) nor a
   builtin type → **`SEM011`** (undefined)

**Shadowing**: allowed per scope (innermost-first). Redeclaring in the **same**
scope → `SEM024`. In lambdas, params and inner declarations go into
`shadowed` and do **not** capture the homonymous outer one.

**Imports**: `qualifyViaImports` only resolves a **simple name** (without `.`/`<`/`[]`)
by the **first** non-wildcard import ending in `.<name>`
(`MemberResolver.qualifyViaImports`). **Wildcards `import a.b.*` are not used to
qualify names** (`MemberResolver.qualifyViaImports`). Type-arguments are qualified recursively by
`qualifyDeep` (bug 32, `CompilerTypes.java:48-94`): simple name via imports →
module classes; **ambiguous import → does not guess** (type preserved).

---

## 7. Subtyping (SG-009 — ✅ FIXED 10/09)

`isAssignable` performs **nominal subtyping** for reference→reference of domain
classes: it walks `superClass`/`interfaces` via BFS. An **unrelated** class is a
**compile error** (`SEM021`, *probe*: `class A`/`class B` with `A a = B()`).
Same for `implements` coverage (`SEM043`), abstract instantiation (`SEM041`) and
collection element type (`SEM056`) — all enforced at compile time:

- `B extends A; A a = b` — valid (real subtype).
- `A a = b_from_another_class` (unrelated) → `SEM021` at compile time.
- `class C implements I {}` with an abstract `f()` → `SEM043`; an `abstract
  class` may defer, the obligation is transitive to the concrete subclass
  (`#322`). `default` methods count as satisfied.
- `abstract class A; new A()`/`A()` → `SEM041` at compile time.
- `l.add("x")` on a `List<Int>` → `SEM056`.

**Guarantee of the type checker:** a function/method **that does not exist on a
known type** is an error (`SEM015`/`SEM025`); argument/constructor arity is
checked (`SEM013`/`SEM023`); an incompatible return type is an error (`SEM010`);
`throw` only accepts `String` (`SEM026`); assignment respects `isAssignable`
(`SEM012`/`SEM021`); redeclaration in the same scope is an error (`SEM024`);
switch-expression requires default/exhaustiveness (`SEM032`); exhaustive enum in
switch (`SEM031`).

**Not a guarantee:** `bool→numeric` coercion works by 1/0 representation but is
implementation-defined (§3.1); check the §`not checked` items (`private`/
`protected` **fields**, SG-013).

---

## 8. Generics (erasure-first)

- Type-args live **only** in `ClassType.typeArguments`. **Erasure**: at emit,
  `TypeVariable → Object` (`JvmTypeMapper.java:16`).
- **Positional substitution** (`substituteTypeVariable`, `CompilerTypes.java:255`):
  given `Box<Int>` and type-var `T` (1st type-param of `Box`), returns `Int`. Only
  for **class** type-params, scanning `currentUnit`.
- **No variance** (no `extends`/`super` in type-args — §3.4 of types.md).
- **No bounds** of type-variable (there is no `T extends X`).
- **No constructor type-arg inference**: `new Box(42)` does **not** infer
  `Box<Int>` (`SemExpressionTyper`, case `NewExpr` returns empty type-args).
- **No element checking in collections**: `List<Int>.add("x")` is not detected
  (`MemberCallTyper`/`CollectionMethodTyper`: `add` is typed `Void` without checking). The failure appears **only at runtime,
  in `get` with a concrete type**: `m.put("b","z")` on a `Map<String,Int>` →
  `ClassCastException` when reading (*probe*); `l.add(9)` on a `List<Int>` works
  normally (*probe* — the inferred type was `Int` and 9 is `Int`). **Unspecified**
  as policy.
- `listOf(1,2)` → `List<Int>` (type of the 1st arg); `mapOf(k1,v1,…)` → `Map<K,V>`
  **pinned to the 1st pair**; `setOf(…)` → `Set<T>` (`SemExpressionTyper`, case `setOf`).

---

## 9. Boxing / unboxing

- **Auto-box** from primitive to reference slot at emit (`Integer`, `Long`,
  …; `JvmBackend.java:77-101`).
- **Unbox** in `list.get(i)` according to elemType (`JvmOpCollections.java:95-112`):
  `listOf(1,2).get(0) + 1` → `2` (*probe*).
- **Erasure box** (primitive behind type-var/Object): `kof_box`/`kof_unbox`.
- **Mutable capture** of a closure uses a synthetic `Box<N>` class (see
  [closures.md](closures.md)).

---

## 10. `==` comparison (per-type semantics — decided in lowering)

| Operand | `==` compares | Evidence |
|---|---|---|
| `string` | **content** (`kof_string_equals`) | *probe*: `"ab" == "a"+"b"` → true |
| `record` | **content** (equals generated field by field) | *probe*: `P(1,2)==P(1,2)` → true |
| `enum` | **identity** between two enum values (each constant is a singleton instance) | `CompilerEnumLowering` |
| primitive | **value** | `if_icmp`/`lcmp`/`fcmpl`/`dcmpl` |
| reference (non-string/record/enum) | **identity** (`if_acmp`) | *probe*: `C(1)==C(1)` → false |

An enum value is **not** a String: `Dir.N == "N"` is rejected at compile time
with `SEM062` (D-ENUM207 / issue #207). Compare two enum values, or call
`.name()` explicitly to get the name.

`a.equals(b)` **works** on string (*probe*) but is an anti-pattern — use `==`.

---

## 11. Overload and method resolution

- **Constructors**: overloading **by arity** (`ConstructorSet`,
  `SymbolTable.java:47-58`). Wrong arity → `SEM023`.
- **Methods**: **real overloading by signature** (§131 closed 13/09,
  `18a64d45`): homonyms with different arity/types coexist via
  `MethodSet` (merge in `define`, `select(argCount, argTypes)`); the typer
  (`MemberCallTyper`) picks the candidate and records it in `resolvedMethods()`.
  In the backends: JVM descriptor by signature, own symbol/slot per
  overload in Native, signature mangle in JS. No compatible candidate →
  `SEM013`/`SEM057`.
- **Default parameters** generate synthetic overloads by decreasing arity in the
  lowering (`lowerFunctionDefaults`).
- **Dispatch**: `KofCallKind {INSTANCE, STATIC, CONSTRUCTOR, FUNCTION,
  INTERFACE, SUPER}` → JVM opcode (`INVOKEVIRTUAL`/`STATIC`/`SPECIAL`/
  `INTERFACE`). **Polymorphic virtual dispatch is delegated to the runtime** — the
  compiler only picks the opcode; there is no own vtable. *probe*: `A a = B();
  a.f()` → `2` (real override).

---

## 12. Builtin type methods

There is no `SymbolTable` for `List`/`Map`/`Set`/`String`/`Channel` — they are
**hard-coded signature tables** in three mirrored layers (analysis, typing
lowering, emit lowering). Return examples:

- `List`: `get/remove`→elemType; `size/length/count`→Int; `contains/isEmpty`→
  Bool; `add/push/append/set/clear`→Void; `map/filter/reduce`→higher-order.
- `Map`: `get`→`V?` (reference); `put/remove`→V; `keys`→`List<K>`; `values`→
  `List<V>`.
- `String`: `indexOf/length/compareTo/hashCode`→Int; `isEmpty`→Bool;
  `substring/split/replace/trim/toUpperCase/toLowerCase`→String/String[];
  `toInt/toLong/toDouble/toFloat`→number (functions of the **runtime**, not of
  `java.lang.String`).

`map((x:Int)->…)` → `List<R>`; `filter` → same type as the receiver; `reduce` →
the lambda's return (*probe*: map/filter/reduce correct).

---

## 13. Type error table (SEM0xx)

| Code | Detects | Evidence |
|---|---|---|
| `SEM001` | arithmetic operator on String/non-numeric | `TypeChecker.inferBinaryResultType` |
| `SEM002` | arithmetic on `bool` | `TypeChecker.inferBinaryResultType` |
| `SEM010` | `return` with incompatible type | `StatementAnalyzer` (case `ReturnStmt`) |
| `SEM011` | undefined variable/type | `SemExpressionTyper` (case `IdentifierExpr`) |
| `SEM012` | incompatible assignment (statement) | `StatementAnalyzer` (case `AssignStmt`) |
| `SEM013` | number of arguments ≠ parameters | `TypeChecker.checkArgTypes` |
| `SEM014` | argument with incompatible type | `TypeChecker.checkArgTypes` |
| `SEM015` | undefined function / non-function called | `BuiltinCallTyper` |
| `SEM020` | assignment to a never-declared variable | `SemExpressionTyper` (case `AssignExpr`) |
| `SEM021` | explicit type ≠ initializer type | `StatementAnalyzer` (case `VarDeclStmt`) |
| `SEM023` | constructor with wrong arity | `SemExpressionTyper` (case `NewExpr`) |
| `SEM024` | redeclaration in the same scope | `StatementAnalyzer` (case `VarDeclStmt`) |
| `SEM025` | nonexistent method on a known type | `MemberCallTyper` |
| `SEM026` | `throw` of a non-String value | `StatementAnalyzer` (case `ThrowStmt`) |
| `SEM027` | assignment used as an expression | `SemExpressionTyper` (case `AssignExpr`) |
| `SEM028` | `.get()/.set()` on array | `SemMethodCallTyper` |
| `SEM029` | `toArray()` on List/Set | driver:4052 |
| `SEM030` | enum without the accessed constant | driver:4859 |
| `SEM031` | non-exhaustive switch-statement over enum | SwitchStmtLowerer:32 |
| `SEM032` | switch-expression without default | `SemExpressionTyper` (case `SwitchExpr`) |
| `SEM033` | `void` value used as an expression | driver:2675 |
| `SEM034` | `sublist()`/`subSet()` | driver:4067 |
| `SEM037` | reassignment of `val` | parser (`type="val"`) + `StatementAnalyzer` |
| `SEM038` | write to a record component | `StatementAnalyzer` (DD-02) |
| `SEM041` | instantiation of an `abstract` class (`new A()` and `A()`) | `SemExpressionTyper`/`BuiltinCallTyper` (SG-017) |
| `SEM042` | nested type (class inside class) | `ClassMemberParser.parseClassMember` (SG-016) |
| `SEM043` | `implements` without covering the interface method / wrong arity | `SemanticAnalyzer.checkInterfaceImplementation` (SG-015) |
| `SEM044` | `main()` with a declared return type (`Int main()`) | `SemanticAnalyzer.analyzeFunction` (SG-018) |
| `SEM045` | `throw X` clause with unknown type | `SemanticAnalyzer.checkThrowsClause` (SG-019) |
| `SEM046` | `private`/`protected` access outside what is allowed | `MemberCallTyper.checkMemberAccess` (SG-013) |
| `ARITH001` | division/remainder by a **constant** zero | ExpressionLowerer:198 |

Division by a **non-constant** zero (`7 / z` with `z=0`) → **runtime**
error (`ArithmeticException` on the JVM; *probe*), not compile-time.
