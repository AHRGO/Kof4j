[English](INHERITANCE_MODEL.md) | [Português](INHERITANCE_MODEL.pt_BR.md)

# INHERITANCE_MODEL.md — Kof Inheritance Model

**Date:** August 21, 2026
**Status:** Implemented — Phase F.3

---

## 1. Overview

Kof supports single class inheritance. A class can extend a single superclass.

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    public speak(): String {
        return name
    }
}

class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
    public bark(): String {
        return "woof"
    }
}
```

---

## 2. Syntax

### Class declaration with inheritance

```kof
class SubClasse extends SuperClasse {
    // ...
}
```

### Super constructor call

```kof
class Dog extends Animal {
    public constructor(String name) {
        super(name)  // explicit call to the superclass constructor
    }
}
```

### Access to inherited members

```kof
var dog = new Dog("Rex")
println(dog.name)    // field inherited from Animal
println(dog.speak()) // method inherited from Animal
println(dog.bark())  // method of Dog itself
```

---

## 3. Type System

### Representation

Classes are represented as `ClassType(packageName, name, typeArguments)`.

The inheritance relation is NOT stored in `ClassType`. It is stored in `ClassSymbol`:

```java
record ClassSymbol(String name, String packageName, String superClass,
                   List<String> interfaces, SymbolTable members)
```

### Subtyping

`Dog` is a subtype of `Animal` if `Dog.superClass == "Animal"`.

The subtyping check is performed by `SemanticAnalyzer.resolveInHierarchy()`.

---

## 4. Symbol Resolution

`SemanticAnalyzer` resolves members (fields, methods) by walking the superclass chain:

```java
SymbolTable.Symbol resolveInHierarchy(String className, String memberName) {
    String current = className;
    while (current != null && !current.isEmpty() && !"Object".equals(current)) {
        ClassSymbol cs = knownClasses.get(current);
        if (cs == null) break;
        Symbol s = cs.members().resolve(memberName);
        if (s != null) return s;
        current = cs.superClass();
    }
    return null;
}
```

### Resolution order

1. Members of the current class
2. Members of the superclass
3. Members of the super-superclass
4. ... up to `Object`

---

## 5. Object Layout (Native)

### Layout with inheritance

```
Animal:
+-------------------+
| type_id (4 bytes) |  → Animal
+-------------------+
| flags (4 bytes)   |
+-------------------+
| name (8 bytes)    |  → offset 8
+-------------------+
Total: 16 bytes

Dog (extends Animal):
+-------------------+
| type_id (4 bytes) |  → Dog
+-------------------+
| flags (4 bytes)   |
+-------------------+
| name (8 bytes)    |  → offset 8 (inherited from Animal)
+-------------------+
| weight (8 bytes)  |  → offset 16 (own to Dog)
+-------------------+
Total: 24 bytes
```

### Rules

1. **Superclass fields come first** — in declaration order
2. **Subclass fields come after** — in declaration order
3. **Do not duplicate inherited fields** — each field appears only once
4. **Offset is deterministic** — computed at compile-time by `ClassLayout`

### ClassLayout.buildWithSuper

```java
public static ClassLayout buildWithSuper(IRClass clazz,
        Function<String, IRClass> superclassResolver) {
    // 1. Walk the superclass chain
    // 2. Add the superclass fields (in order)
    // 3. Add the fields of the current class
    // 4. Compute offsets and total size
}
```

---

## 6. Constructor Chaining

### Execution order

```kof
var dog = new Dog("Rex")
```

Result:

1. `Dog.<init>("Rex")` is called
2. Inside `Dog.<init>`, `super("Rex")` calls `Animal.<init>("Rex")`
3. `Animal.<init>` initializes `this.name = "Rex"`
4. `Dog.<init>` continues (constructor body)
5. The Dog object is ready

### Rules

1. `super(args)` MUST be the first statement of the constructor
2. If there is no explicit `super(args)`, an implicit `super()` is emitted
3. Only one `super()` call per constructor

### IR

```java
// super(name) is lowered as:
KofLoadLocal(ownerType, 0)           // this
[emit args]                          // name
KofCall(superType, "<init>", args, VOID, CONSTRUCTOR)
```

---

## 7. JVM vs Native

| Aspect | JVM | Native |
|---------|-----|--------|
| Inheritance | `extends` bytecode | Inherited field layout |
| Super constructor | `INVOKESPECIAL super.<init>` | `call SuperClass_init` |
| Field access | `GETFIELD` with hierarchy offset | `movq offset(%rax)` with ClassLayout offset |
| Method access | `INVOKEVIRTUAL` | `call Class_method` (direct dispatch) |
| Object size | JVM manages | ClassLayout computes (header + inherited + own fields) |

---

> **Updated (0.2.6-beta, 31/08):** items 1 and 3 below were
> superseded — virtual dispatch (F.4) and interfaces (F.5) are implemented
> in JVM and Native; 3-level inheritance remains validated by E2E. With
> `spawn` on threads (pthread), the object layout and the offsets computed
> at compile-time (ClassLayout) are unchanged and thread-safe.

## 8. Known Limitations

1. ~~**No virtual dispatch**~~ — ✅ F.4 (vtable)
2. **No abstract classes** — all classes are concrete
3. ~~**No interfaces**~~ — ✅ F.5 (dispatch via vtable)
4. **No sealed classes** — not supported yet
5. **No multiple inheritance** — single inheritance only
6. **No diamond problem** — not applicable with single inheritance

---

## 9. Files

| File | Role |
|---------|-------|
| Type.java | ClassType (no inheritance field) |
| SymbolTable.java | ClassSymbol stores superClass |
| SemanticAnalyzer.java | resolveInHierarchy() walks the chain |
| ClassLayout.java | buildWithSuper() includes inherited fields |
| NativeBackend.java | allClassesMap to resolve superclasses |
| CompilerDriver.java | lowerConstructor with super(args), findSuperClass() |
| IRNodes.java | IRClass.superName |

---

## 10. Tests

| Test | JVM | Native |
|-------|-----|--------|
| simpleSubclass | ✅ | ✅ |
| superclassField | ✅ | ✅ |
| inheritedFieldAccess | ✅ | ✅ |
| inheritedMethod | ✅ | ✅ |
| constructorChaining | ✅ | ✅ |
| subclassOwnField | ✅ | ✅ |
| fieldLayoutInheritance | — | ✅ |
| superCallWithArgs | ✅ | ✅ |
| threeLevelInheritance | ✅ | ✅ |
| defaultConstructorInheritance | ✅ | ✅ |
| objectSizeInheritance | — | ✅ |
