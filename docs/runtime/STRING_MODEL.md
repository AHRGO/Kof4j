[English](STRING_MODEL.md) | [Português](STRING_MODEL.pt_BR.md)

# STRING_MODEL.md — Kof String Model

**Date:** August 21, 2026
**Status:** Implemented — Phase F.1

---

## 1. Overview

String is a builtin Kof type with an independent representation for each backend:

```
Kof String
    ↓
Kof IR / Runtime ABI
   ↙       ↘
JVM         Native
  ↓           ↓
java.lang    KofString
String       (heap object)
```

The compiler core does NOT depend on java.lang.String.
The String type is represented as BuiltinTypes.STRING throughout the compiler.

---

## 2. Centralized Type

```java
// BuiltinTypes.java
public static final Type STRING = new Type.ClassType("java.lang", "String", List.of());

public static boolean isString(Type type) {
    if (type instanceof Type.ClassType ct) {
        return "java.lang".equals(ct.packageName()) && "String".equals(ct.name());
    }
    return false;
}
```

All compiler files reference BuiltinTypes.STRING.

---

## 3. KofString Layout (Native)

```
+--------------------+
| type_id (4 bytes)  |  = 1
+--------------------+
| flags (4 bytes)    |  = 0
+--------------------+
| length (4 bytes)   |  = byte length
+--------------------+
| padding (4 bytes)  |
+--------------------+
| UTF-8 data + \0    |
+--------------------+
```

| Decision | Choice | Motivation |
|---------|---------|-----------|
| Encoding | UTF-8 | C/POSIX compatibility |
| Immutability | Yes | Safety, hash consistency |
| Length | Byte length | Simple, consistent with strlen |
| Null terminator | Yes | C compatibility |
| Header size | 16 bytes | 16-byte alignment |

---

## 4. Runtime Functions (Native)

| Function | Input | Return | Description |
|--------|---------|---------|-----------|
| `kof_string_from_literal` | data_ptr, byte_length | str_ptr | Creates a KofString from a literal |
| `kof_string_length` | str_ptr | int | Returns byte length |
| `kof_string_concat` | str1, str2 | str3 | Concatenates two strings |
| `kof_string_equals` | str1, str2 | bool | Compares byte by byte |
| `kof_print_string` | str_ptr | void | Prints using length |
| `kof_println_string` | str_ptr | void | Prints + newline |
| `kof_memcpy` | dest, src, n | void | Copies n bytes |

---

## 5. print / println Dispatch

| Type | Native Function |
|------|---------------|
| int | kof_print_int |
| String | kof_print_string |
| other | kof_print (strlen-based) |

---

## 6. JVM vs Native

| Operation | JVM | Native |
|----------|-----|--------|
| Literal | ldc | kof_string_from_literal |
| length() | String.length() | kof_string_length |
| concat() | String.concat() | kof_string_concat |
| equals() | String.equals() | kof_string_equals |
| print | PrintStream.print() | kof_print_string |
| println | PrintStream.println() | kof_println_string |

---

## 7. Null

| Value | Representation |
|-------|---------------|
| null | Pointer 0x0 |
| "" | KofString with length=0 |

kof_null_error() available for future detection.

---

## 8. Concatenation and Equality

> **Updated (0.0.5):** the syntax is integrated — `+` concatenates
> (`kof_string_concat`) and `==`/`!=` compare content (`kof_string_equals`)
> in JVM and Native. The complete API (charAt, substring, contains, startsWith,
> endsWith, indexOf, trim, toUpperCase, toLowerCase, replace, split) is
> available (type detection of `+` already resolved in CompilerDriver).
>
> **Updated (0.2.6-beta, 31/08):** `kof_print_string` now **guards against
> `null`** (before: segfault on `println(null)`); the floating-point to
> string conversion (dtoa) uses aligned `snprintf`, with real FP in XMM
> in Native — part of closing JSN001/FLT001.

---

## 9. Files

| File | Role |
|---------|-------|
| BuiltinTypes.java | Centralized reference for the String type |
| Type.java | Type.of("string") uses BuiltinTypes.STRING |
| IRNodes.java | KofLoadLiteral.ofString uses BuiltinTypes.STRING |
| SemanticAnalyzer.java | Literal typing uses BuiltinTypes.STRING |
| CompilerDriver.java | print/println with BuiltinTypes.isString() |
| NativeBackend.java | KofString creation + print dispatch |
| NativeRuntime.java | 7 runtime functions for strings |
| JvmBackend.java | Delegates to java.lang.String |
