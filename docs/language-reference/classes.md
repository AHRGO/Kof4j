[English](classes.md) | [Português](classes.pt_BR.md)

# Classes, Records, Enums, Interfaces, Entities

**Status:** Stable (except where labeled) · **Evidence:** Parser.parseTypeDeclaration, `SymbolTableBuilder`/`SemanticAnalyzer` (defineMembers), `SymbolTable.java`

---

## 1. Classes (mutable state)

`ebnf
class-declaration = modifiers , "class" , identifier , [ type-parameters ] ,
                    [ "extends" , type-ref ] , [ implements-clause ] , class-body
class-body = "{" , { field | method | constructor | nested-type } , "}"
`

`kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    String greeting() { return "Hello " + name }
}
var u = User("Mel", 26)     // without `new` (new is also accepted)
u.age = 27                  // direct field — mutable
`

- **Fields are public by default** (no `private`); direct write.
- **Constructor**: `constructor(...)` or `{ ... }` block (`parseConstructor`).
  If no constructor is declared, a **default 0-args** one is synthesized
  (`defineClassMembers:145-147`).
- **`new` is optional**: `User(...)` and `new User(...)` are both valid.
- **Instantiation without `new` of a class with an args constructor**:
  `User("Mel",26)` resolves via *implicit construction* (`:1083-1084`).
- **No getters/setters** — direct field (language idiom).
- **No `val` on a field** (`val x = 1` in a class body → `PARSE016`,
  *probe*); use `final`.

### 1.1 `class X(...)` is a record, not a class

`class User(String name, Int age) { }` **is not** a class with a primary
constructor — the parser routes to `parseRecordBody` (Parser.parseRecordBody)
and produces a **record** (immutable, accessors `u.name()`). Writing
`u.name = "x"` **does not** work. For immutable data, the canonical form is
`record`. **Stable** (documented in `AGENTS.md`, verified).

---

## 2. Inheritance

`ebnf
implements-clause = "implements" , type-ref , { "," , type-ref }
`

- `extends` = **single class** (no multiple class inheritance).
- `implements` = list of interfaces.
- **Default superclass**: `Object` (classes), synthetic `"Record"` (records/
  entities).
- **Member resolution in the hierarchy**: BFS class→super→interfaces→
  super-super (`resolveInHierarchy:243-266`), first found wins.
- **`super.method()`** and **`super(args)`** (constructor) work (*probe*:
  `B.g()` calling `super.f()` → 1).
- **Override**: a method in the subclass with the same name **replaces** it
  (real virtual dispatch at runtime: `A a = B(); a.f()` → 2, *probe*).
- **`override` modifier** is accepted but **not validated** (there is no check
  that the method exists in the super).
- **Subtyping is not checked in the type checker** (SG-009) — see
  [type-system.md](type-system.md) §7.

---

## 3. Visibility

| Modifier | Effect |
|---|---|
| `public` (default) | visible everywhere |
| `private` | visible only in the class |
| `protected` | visible in the package/subclass (JVM semantics) |

- **`private` is NOT checked at compile-time**: accessing `p.x` from outside →
  `IllegalAccessError` at **runtime** (*probe*). Visibility is emitted as a
  JVM flag; the Kof compiler does not enforce it. **Implementation-defined**
  (SG-013).
- No modifier → `public` (`accessFlagsFor:3388`).
- `static` field/method: access by class name (`S.k`, `S.k()` — *probe*).

---

## 4. Records (immutable data)

`ebnf
record-declaration = modifiers , "record" , identifier , [ type-parameters ] ,
                     [ "extends" , type-ref ] , [ implements-clause ] ,
                     record-header , [ record-body ]
record-header = "(" , [ record-component , { "," , record-component } ] , ")"
record-component = [ modifiers ] , type-ref , identifier , [ "=" , expression ]
`

`kof
record Point(Int x, Int y)
var p = Point(10, 20)
println(p.x())          // accessor by method (probe)
println(p.x)            // direct read also works (probe)
println(p)              // JVM: Point[x=10, y=20]
`

- Each component generates: **private field**, **accessor `name()`** (0-arg
  method), and the **canonical constructor** (`defineRecordMembers:150-182`).
- **`equals`/`hashCode`/`toString` are generated** — `equals` compares field
  by field (primitives by value, refs by `Objects.equals`), `JvmBackend:303-397`.
- **Records are immutable**: there is no setter; assignment to `p.x` is **not
  supported** (the accessor is a method).
- **Record with methods**: `record P(Int x, Int y) { Int sum() { return x+y } }`
  works (*probe*).
- **Generic record**: `record Box<T>(T v)` works (*probe*).
- **Default in a component**: `record C(Int x = 0)` generates overloads by arity.

---

## 5. Enums

`ebnf
enum-declaration = modifiers , "enum" , identifier ,
                   "{" , [ identifier , { "," , identifier } ] , "}"
`

`kof
enum Color { Red, Blue }
println(Color.Red)        // "Red" (probe)
var c = Color.Red
println(c.name())         // "Red" (probe)
`

- **Constants only** — no methods, fields, constructors, body (`enum E { A
  String f(){…} }` → `PARSE032`, *probe*).
- **An enum value is a real instance (D-ENUM207, issue #207)** — the compiler
  emits a real enum class (`Dir.class`) with the constants as `static final`
  instances created in `<clinit>`; `Dir.N` compiles to
  `getstatic Dir.N : LDir;` (not `ldc "N"`). `getClass()` returns `Dir` and
  `instanceof Dir` is a real check. `==`/`!=` between two enum values compares
  **identity** (`if_acmp`) — correct because the constants are singletons.
  An enum value is **not** a String: `Color.Red == "Red"` is rejected at
  compile time with `SEM062` — compare two enum values or call `.name()`.
- Synthetic methods: `values() → List<Dir>` (static), `valueOf(String) →
  Dir` (static, null on miss), `name() → String`, `ordinal() → Int`,
  `toString() → String` (the name), `compareTo(Dir) → Int` (instance).
- **Switch over enum**: without `default` it requires full coverage → otherwise `SEM031`.
- Unqualified constant (`Red` within the enum context) resolves
  (`:869-876`).

---

## 6. Pattern matching (in switch)

`ebnf
pattern = type-name , identifier                          (* binding *)
        | type-name , "(" , { ( "var" | "val" )? , identifier } , ")"   (* destructuring *)
`

`kof
switch (obj) {
    case String s: println(s); break
    case Point(var x, var y): println(x + "," + y); break
    default: println("outro")
}
`

- **Binding**: `case Type var` — tests `instanceof` and binds `var`.
- **Destructuring**: `case Point(var x, var y)` — tests type + extracts fields
  (records). `var`/`val` in the sub-bindings are optional (ExpressionParser (pattern)).
- Implemented by `KofInstanceOf` + `KofCheckCast` + `KofLoadField` per
  component (`SwitchStmtLowerer.java:48-133`).
- **Works on all 3 targets** (JVM/Native/JS — tested in
  `KofPatternMatchingTest`, `KofSwitchExprE2ETest`).
- **There is no** pattern in `if`/`while`, nor `when`, nor guards (`case P(x) if
  x>0`), nor nested patterns (`case List(P(a,b))`). **Unspecified** (SG-014).

---

## 7. Interfaces

`ebnf
interface-declaration = modifiers , "interface" , identifier ,
                        [ "extends" , type-ref , { "," , type-ref } ] ,
                        "{" , { method } , "}"
`

`kof
interface I { Int f() }
class C implements I { Int f() { return 1 } }
`

- Methods without a body → **abstract** (`isAbstractMethod` = body null).
- **`default Int f() { … }`** → a method with a body in an interface works
  (*probe*).
- **Does not accept type-parameters** (`interface F<T>` → `PARSE007`, *probe*).
- **There is no check for complete implementation**: `class C implements I {}` without
  `f()` **compiles** (*probe*) — it fails only at runtime if `f()` is called
  (`AbstractMethodError`). **Unspecified** (SG-015).
- **There is no** trait, nor interface with state (fields), nor companion object.

---

## 8. Entities (ORM)

`ebnf
entity-declaration = modifiers , "entity" , identifier ,
                     "{" , { entity-field } , "}"
entity-field = identifier , ":" , type-ref , { "generated" | "unique" }
`

`kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}
`

- **It is a generated record + schema for `kof.orm`** (`AstNodes.java:147-158`).
- Constraints `generated`/`unique` are schema metadata (compile-time, without
  reflection).
- Enables the **Query DSL**: `User.query(db) { where age > 18; … }`.
- **Experimental** (ORM domain). See [../stdlib-database.md](../stdlib/stdlib-database.md).

---

## 9. Nested classes

`class A { class B { } }` — the parser accepts `type-declaration` as a member
(`parseClassMember:740-742`). **The naming/scoping semantics of nesting
is Unspecified** (SG-016) — there is no dedicated test that fixes `A.B` vs `B`.

---

## 10. What does NOT exist in Kof classes

| Missing | Note |
|---|---|
| `sealed`/`permits` | lexer keywords, not parsed (SG-002) |
| `abstract class` non-instantiable at compile-time | `new A()` compiles, fails at runtime (SG-017) |
| `companion object` | does not exist |
| `object` (singleton) | there is no `object` keyword |
| `data class` | use `record` |
| `value class`/`inline class` | does not exist |
| `operator fun` (operator overloading) | **there is no** custom operator overloading |
| `init` block | initialization via constructor |
| custom `get()/set()` | direct field |
| `lateinit` | does not exist |
| `open` (inheritance) | classes are open by default (no implicit `final`) |
| Kotlin-style primary constructor | `class X(...)` = record |
| implicit `super()` without args | does the default constructor call `super()`? **Unspecified** |
