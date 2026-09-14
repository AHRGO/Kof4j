[English](STRING_MODEL.md) | [Português](STRING_MODEL.pt_BR.md)

# STRING_MODEL.md — Modelo de Strings do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.1

---

## 1. Visão Geral

String é um tipo builtin do Kof com representação independente para cada backend:

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

O core do compilador NÃO depende de java.lang.String.
O tipo String é representado como BuiltinTypes.STRING em todo o compiler.

---

## 2. Tipo Centralizado

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

Todos os arquivos do compiler referenciam BuiltinTypes.STRING.

---

## 3. Layout KofString (Native)

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

| Decisão | Escolha | Motivação |
|---------|---------|-----------|
| Encoding | UTF-8 | Compatibilidade C/POSIX |
| Imutabilidade | Sim | Segurança, hash consistency |
| Length | Byte length | Simples, consistente com strlen |
| Null terminator | Sim | Compatibilidade C |
| Header size | 16 bytes | Alinhamento 16 bytes |

---

## 4. Runtime Functions (Native)

| Função | Entrada | Retorno | Descrição |
|--------|---------|---------|-----------|
| `kof_string_from_literal` | data_ptr, byte_length | str_ptr | Cria KofString de literal |
| `kof_string_length` | str_ptr | int | Retorna byte length |
| `kof_string_concat` | str1, str2 | str3 | Concatena duas strings |
| `kof_string_equals` | str1, str2 | bool | Compara byte a byte |
| `kof_print_string` | str_ptr | void | Imprime usando length |
| `kof_println_string` | str_ptr | void | Imprime + newline |
| `kof_memcpy` | dest, src, n | void | Copia n bytes |

---

## 5. print / println Dispatch

| Tipo | Função Nativa |
|------|---------------|
| int | kof_print_int |
| String | kof_print_string |
| outro | kof_print (strlen-based) |

---

## 6. JVM vs Native

| Operação | JVM | Native |
|----------|-----|--------|
| Literal | ldc | kof_string_from_literal |
| length() | String.length() | kof_string_length |
| concat() | String.concat() | kof_string_concat |
| equals() | String.equals() | kof_string_equals |
| print | PrintStream.print() | kof_print_string |
| println | PrintStream.println() | kof_println_string |

---

## 7. Null

| Valor | Representação |
|-------|---------------|
| null | Ponteiro 0x0 |
| "" | KofString com length=0 |

kof_null_error() disponível para detecção futura.

---

## 8. Concatenação e Igualdade

> **Atualizado (0.0.5):** a sintaxe está integrada — `+` concatena
> (`kof_string_concat`) e `==`/`!=` comparam conteúdo (`kof_string_equals`)
> em JVM e Native. A API completa (charAt, substring, contains, startsWith,
> endsWith, indexOf, trim, toUpperCase, toLowerCase, replace, split) está
> disponível (detecção de tipo de `+` já resolvida no CompilerDriver).
>
> **Atualizado (0.2.6-beta, 31/08):** `kof_print_string` passou a **guardar
> `null`** (antes: segfault em `println(null)`); a conversão de ponto
> flutuante para string (dtoa) usa `snprintf` alinhado, com FP real em XMM
> no Native — parte do fechamento do JSN001/FLT001.

---

## 9. Arquivos

| Arquivo | Papel |
|---------|-------|
| BuiltinTypes.java | Referência centralizada do tipo String |
| Type.java | Type.of("string") usa BuiltinTypes.STRING |
| IRNodes.java | KofLoadLiteral.ofString usa BuiltinTypes.STRING |
| SemanticAnalyzer.java | Literal typing usa BuiltinTypes.STRING |
| CompilerDriver.java | print/println com BuiltinTypes.isString() |
| NativeBackend.java | KofString creation + print dispatch |
| NativeRuntime.java | 7 funções de runtime para strings |
| JvmBackend.java | Delega para java.lang.String |
