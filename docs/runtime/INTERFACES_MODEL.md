[English](INTERFACES_MODEL.md) | [Português](INTERFACES_MODEL.pt_BR.md)

# INTERFACE_MODEL.md — Kof Interface Model

**Date:** August 21, 2026
**Status:** Implemented — Phase F.5

---

## 1. Overview

Kof supports interfaces with abstract methods. A class can implement one or more interfaces.

```kof
interface Speaker {
    speak(): String
}

class Dog implements Speaker {
    speak(): String = "woof"
}
```

---

## 2. Syntax

### Interface declaration

```kof
interface Nome {
    metodo(): TipoRetorno
}
```

### Implementation by class

```kof
class Classe implements Interface1, Interface2 {
    // implement required methods
}
```

### Interface inheritance

```kof
interface Base {
    metodo(): String
}
interface Derivada extends Base {
    outroMetodo(): String
}
```

---

## 3. Semantics

### Rules

1. Interfaces define contracts (abstract methods)
2. Classes must implement all interface methods
3. A class can implement multiple interfaces
4. Interfaces can extend other interfaces
5. Interfaces do NOT have fields (only methods)
6. Interfaces do NOT have constructors
7. Interface methods are always public

### Dispatch

Calls through an interface type use vtable dispatch:

```
Speaker s = new Dog()
s.speak()
    ↓
Dog.speak()  // resolved by the object's real type
```

---

## 4. Representation in the IR

### KofCallKind

```java
enum KofCallKind { INSTANCE, STATIC, CONSTRUCTOR, FUNCTION, INTERFACE }
```

Calls through an interface type use `KofCallKind.INTERFACE`.

### JvmBackend

Interface calls use `INVOKEINTERFACE` instead of `INVOKEVIRTUAL`.

### NativeBackend

Dispatch via vtable, the same mechanism as virtual dispatch. The method index is determined by the order of the methods in the interface.

---

## 5. Method Tables

Interfaces contribute to the vtable of the classes that implement them:

```
Speaker_vtable: [Speaker_speak]
Dog_vtable:     [Dog_speak]  // inherits the interface slot
```

Methods inherited from interfaces are included in the implementing class's vtable.

---

## 6. Files

| File | Role |
|---------|-------|
| IRNodes.java | `KofCallKind.INTERFACE` |
| SemanticAnalyzer.java | `isInterfaceType()`, `resolveInHierarchy()` walks interfaces |
| CompilerDriver.java | Defines `KofCallKind.INTERFACE` for interface calls |
| JvmBackend.java | `INVOKEINTERFACE` for interface calls |
| NativeBackend.java | `collectVirtualMethods()` includes interfaces, dispatch via vtable |

---

> **Updated (0.2.6-beta, 31/08):** vtable dispatch for interfaces is
> thread-safe with `spawn` on threads (pthread, 31/08). The limitations on
> default/static methods still hold.

## 7. Limitations

1. No default methods (methods with a body in the interface)
2. No static methods in interfaces
3. No fields in interfaces
4. No validation of complete implementation (compile-time)
5. No generics in interfaces
