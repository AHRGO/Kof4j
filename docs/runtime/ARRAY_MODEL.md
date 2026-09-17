[English](ARRAY_MODEL.md) | [Português](ARRAY_MODEL.pt_BR.md)

# ARRAY_MODEL.md — Kof Array Model

**Date:** August 21, 2026
**Status:** Implemented — Phase F.2

---

## 1. Overview

An array is a builtin collection type of Kof with an independent representation for each backend:

```
Kof Array
    ↓
Kof IR / Runtime ABI
   ↙       ↘
JVM         Native
  ↓           ↓
JVM arrays   KofArray
 natives     (heap object)
```

The compiler core does NOT depend on java.lang.reflect.Array or any JVM API.

---

## 2. Type in the Type System

```java
// Type.java
record ArrayType(Type componentType) implements Type {
}

static Type of(String name) {
    if (name.endsWith("[]")) {
        Type component = of(name.substring(0, name.length() - 2));
        return new ArrayType(component);
    }
    // ...
}
```

Examples:
- `Int[]` → `ArrayType(PrimitiveType.INT)`
- `String[]` → `ArrayType(ClassType("java.lang", "String"))`
- `Int[][]` → `ArrayType(ArrayType(PrimitiveType.INT))`

---

## 3. Syntax

### Creation

```kof
var a = new Int[10]      // array of 10 integers
var b = new String[5]    // array of 5 strings
var c = new Long[3]      // array of 3 longs
```

### Access

```kof
a[0] = 42        // write
println(a[0])    // read
```

### Length

```kof
println(a.length)    // returns Int
```

### As a parameter

```kof
sum(Int[] arr): Int {
    var total = 0
    for (var i = 0; i < arr.length; i++) {
        total = total + arr[i]
    }
    return total
}
```

### As a return

```kof
createArray(): Int[] {
    var a = new Int[3]
    a[0] = 10
    a[1] = 20
    a[2] = 30
    return a
}
```

---

## 4. KofArray Layout (Native)

```
+---------------------+
| type_id (4 bytes)   |  = 2
+---------------------+
| flags (4 bytes)     |  = 0
+---------------------+
| length (4 bytes)    |  = number of elements
+---------------------+
| elem_size (4 bytes) |  = size of each element
+---------------------+
| elements[]          |  = contiguous data
+---------------------+
```

| Field | Offset | Size | Description |
|-------|--------|------|-------------|
| type_id | 0 | 4 bytes | Always 2 for Array |
| flags | 4 | 4 bytes | Reserved (GC mark-sweep tracks the allocator block prefix) |
| length | 8 | 4 bytes | Number of elements |
| elem_size | 12 | 4 bytes | Size of each element in bytes |
| elements | 16 | variable | Contiguous data |

### Element Sizes

| Type | elem_size |
|------|-----------|
| byte, bool | 1 |
| short | 2 |
| int, char | 4 |
| long | 8 |
| float | 4 |
| double | 8 |
| reference | 8 (pointer) |

---

## 5. Runtime Functions (Native)

| Function | Input | Return | Description |
|----------|-------|--------|-------------|
| `kof_array_alloc` | length, elem_size | array_ptr | Allocates array on the heap |
| `kof_array_length` | array_ptr | int | Returns length |
| `kof_array_get` | array_ptr, index | element | Reads with bounds check |
| `kof_array_set` | array_ptr, index, value | void | Writes with bounds check |

### Contract

- `kof_array_alloc` returns a pointer aligned to 16 bytes
- `kof_array_alloc` initializes the header (type_id=2, flags=0)
- `kof_array_get` on an invalid index → `kof_bounds_error`
- `kof_array_set` on an invalid index → `kof_bounds_error`
- `kof_array_get` on a NULL array → `kof_null_error`
- `kof_array_set` on a NULL array → `kof_null_error`

---

## 6. IR Operations

| Operation | JVM | Native | KofJS | Description |
|----------|-----|--------|-------|-------------|
| `KofNewArray(elemType)` | `NEWARRAY` | `kof_array_alloc` | `new Array(n).fill(...)` | Creates array |
| `KofArrayLoad(elemType)` | `IALOAD`/`LALOAD`/etc | `kof_array_get` | `kofArrayGet(array, index)` | Reads element (bounds check) |
| `KofArrayStore(elemType)` | `IASTORE`/`LASTORE`/etc | `kof_array_set` | `kofArraySet(array, index, value)` | Writes element (bounds check) |
| `KofArrayLength()` | `ARRAYLENGTH` | `kof_array_length` | `array.length` | Returns length |

> **KOF-SBD-001 (Array Bounds Safety):** until the KofJS gap was fixed, the
> lowering of that target lowered `KofArrayLoad`/`KofArrayStore` to
> `array[index]`/`array[index] = value` directly (raw JS semantics: an out-of-bounds
> read returned `undefined`, a write at `index >= length`
> silently expanded the array). The helpers `kofArrayGet`/`kofArraySet`
> (`JsRuntimeCore`) close that divergence: every indexed access to a Kof
> array in the JS target now rejects `index < 0 || index >= length` before
> touching the array, equivalent to the check that the JVM already performs in
> `IALOAD`/`AALOAD`/etc (JVMS §6.5) and that Native performs in `kof_array_get`/
> `kof_array_set`. The error class is not identical across the 3 targets — the
> safety property (bounds safety) is.

---

## 7. Type Checking (SemanticAnalyzer)

| Rule | Validation |
|-------|-----------|
| Index is Int | `a[b]` — `b` must be `Int` |
| Read returns elementType | `a[i]` returns the element type |
| Write requires a compatible type | `a[i] = v` — `v` must be compatible with elementType |
| length returns Int | `a.length` returns `Int` |
| Creation validates the type | `new Int[10]` — type must be valid |
| Array<Int> does not accept String | Type mismatch at runtime |
| Array<String> does not accept Int | Type mismatch at runtime |

---

## 8. JVM vs Native

| Operation | JVM | Native |
|----------|-----|--------|
| Creation | `NEWARRAY` | `kof_array_alloc` |
| Access (primitive) | `IALOAD`/etc | `kof_array_get` |
| Access (reference) | `AALOAD` | `kof_array_get` |
| Write (primitive) | `IASTORE`/etc | `kof_array_set` |
| Write (reference) | `AASTORE` | `kof_array_set` |
| Length | `ARRAYLENGTH` | `kof_array_length` |
| Bounds check | JVM automatic | `kof_bounds_error` |
| Null check | JVM automatic | `kof_null_error` |

---

## 9. Null

| Value | Representation |
|-------|---------------|
| null | Pointer 0x0 |
| new Int[0] | KofArray with length=0 |

kof_null_error() available for detection.

---

## 10. Files

| File | Role |
|---------|-------|
| Type.java | `ArrayType` record + `isArray()` + `arrayElementType()` |
| AstNodes.java | `NewArrayExpr` + `ArrayAccessExpr` |
| Parser.java | Parsing of `new Type[size]` + `expr[expr]` |
| SemanticAnalyzer.java | Array type checking |
| CompilerDriver.java | Lowering to `KofNewArray`/`KofArrayLoad`/`KofArrayStore`/`KofArrayLength` |
| `KofNewArray.java`/`KofArrayLoad.java`/`KofArrayStore.java`/`KofArrayLength.java` | array ops (one record per op) |
| NativeRuntime.java | 4 runtime functions for arrays |
| NativeBackend.java | Lowering of the array operations |
| JvmBackend.java | `NEWARRAY`/`IALOAD`/`IASTORE`/`ARRAYLENGTH` |

---

> **Updated (0.2.6-beta, 31/08):** `Double`/`Float` arrays enter the
> native JSON flow (`Double[]`/`Float[]` in decode — JSN001), with FP in
> XMM (`vcvtsi2sd`/`mulsd`); array allocation stays on the free-list
> `kof_free_head` (thread-safe with `spawn` on pthreads).

## 11. Known Limitations

1. No array initialization with literals (`[1, 2, 3]`) — only `new Type[size]`
2. No syntactic multidimensional arrays (`new Int[3][4]`)
3. No `instanceof` for arrays
4. No conversion between array types
5. No `System.arraycopy` equivalent
6. No anonymous arrays
