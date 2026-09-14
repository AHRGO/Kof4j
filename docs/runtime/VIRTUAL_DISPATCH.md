[English](VIRTUAL_DISPATCH.md) | [Português](VIRTUAL_DISPATCH.pt_BR.md)

# VIRTUAL_DISPATCH.md — Kof Dynamic Dispatch

**Date:** August 21, 2026
**Status:** Implemented — Phase F.4

---

## 1. Overview

Kof supports dynamic dispatch (virtual dispatch) for instance methods. When a method is called through a superclass reference, the correct implementation is resolved at runtime by the object's real type.

```kof
class Animal {
    speak(): String = "animal"
}
class Dog extends Animal {
    speak(): String = "dog"
}
main() {
    Animal a = new Dog()
    println(a.speak())  // prints "dog", not "animal"
}
```

---

## 2. Mechanism

### Object Header (extended)

```
offset 0:  type_id (4 bytes)
offset 4:  flags (4 bytes)
offset 8:  method_table_ptr (8 bytes) — pointer to vtable
```

HEADER_SIZE = 16 bytes (it was 8 before F.4).

### Method Table (vtable)

Each class has a method table in the `.data` section:

```asm
Dog_vtable:
    .quad Dog_speak    # slot 0: speak (override of Animal)
    .quad 0            # sentinel
```

The vtable contains pointers to the implementations of virtual methods.

### Slot Order

1. Superclass methods first (in declaration order)
2. Subclass methods after
3. Override replaces the pointer in the same slot
4. New methods receive new slots

Example:
```
Animal_vtable: [Animal_speak]
Dog_vtable:    [Dog_speak]           # override in slot 0
```

### Native Dispatch

```asm
# animal.speak()
popq %rax              # loads the object pointer
movq 8(%rax), %rbx     # loads method_table_ptr from the header
addq $0, %rbx          # slot offset (index * 8)
movq (%rbx), %rbx      # loads the function pointer
call *%rbx             # calls via pointer
```

### JVM Dispatch

The JVM uses native `INVOKEVIRTUAL`, which already resolves virtual dispatch correctly.

---

## 3. Rules

1. **Every class** receives a vtable (even without overrides)
2. **Inherited methods** keep the same slot in the hierarchy
3. **Override** replaces the pointer in the existing slot
4. **New methods** receive new slots after the inherited ones
5. **super.method()** remains a static call (direct dispatch)
6. **Static methods** and **constructors** do not use the vtable

---

## 4. Initialization

After `kof_alloc`, the object is initialized with:
1. type_id = 0
2. flags = 0
3. method_table_ptr = pointer to the concrete class's vtable

The constructor then initializes the fields.

---

## 5. Files

| File | Role |
|---------|-------|
| ClassLayout.java | HEADER_SIZE = 16, METHOD_TABLE_OFFSET = 8 |
| NativeRuntime.java | generateMethodTable(), emitInitObject() |
| NativeBackend.java | collectVirtualMethods(), findVirtualMethodIndex(), emitCall() |
| CompilerDriver.java | inferExprType() with case NewExpr |

---

> **Updated (0.2.6-beta, 31/08):** item 1 below was superseded —
> interface dispatch (F.5) uses the same vtable. Dispatch is
> thread-safe with `spawn` on threads (pthread, 31/08): the vtable is
> read-only after compilation.

## 6. Known Limitations

1. ~~No virtual dispatch for interfaces~~ — ✅ F.5 (same vtable)
2. No vtable for records, strings, arrays (they use a fixed header)
3. No runtime vtable cache
4. No vtable invalidation (future: sealed classes)

---

## 7. Tests

| Test | JVM | Native |
|-------|-----|--------|
| simpleOverride | ✅ | ✅ |
| polymorphism | ✅ | ✅ |
| threeLevelOverride | ✅ | ✅ |
| superMethod | ✅ | ✅ |
| methodNotOverridden | ✅ | ✅ |
| vtableContainsMethods | — | ✅ |
