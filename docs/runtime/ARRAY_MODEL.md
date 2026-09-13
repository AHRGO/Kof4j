# ARRAY_MODEL.md — Modelo de Arrays do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.2

---

## 1. Visão Geral

Array é um tipo de coleção builtin do Kof com representação independente para cada backend:

```
Kof Array
    ↓
Kof IR / Runtime ABI
   ↙       ↘
JVM         Native
  ↓           ↓
JVM arrays   KofArray
 nativos     (heap object)
```

O core do compilador NÃO depende de java.lang.reflect.Array ou qualquer API JVM.

---

## 2. Tipo no Type System

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

Exemplos:
- `Int[]` → `ArrayType(PrimitiveType.INT)`
- `String[]` → `ArrayType(ClassType("java.lang", "String"))`
- `Int[][]` → `ArrayType(ArrayType(PrimitiveType.INT))`

---

## 3. Sintaxe

### Criação

```kof
var a = new Int[10]      // array de 10 inteiros
var b = new String[5]    // array de 5 strings
var c = new Long[3]      // array de 3 longs
```

### Acesso

```kof
a[0] = 42        // escrita
println(a[0])    // leitura
```

### Length

```kof
println(a.length)    // retorna Int
```

### Como parâmetro

```kof
sum(Int[] arr): Int {
    var total = 0
    for (var i = 0; i < arr.length; i++) {
        total = total + arr[i]
    }
    return total
}
```

### Como retorno

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

## 4. Layout KofArray (Native)

```
+---------------------+
| type_id (4 bytes)   |  = 2
+---------------------+
| flags (4 bytes)     |  = 0
+---------------------+
| length (4 bytes)    |  = número de elementos
+---------------------+
| elem_size (4 bytes) |  = tamanho de cada elemento
+---------------------+
| elements[]          |  = dados contíguos
+---------------------+
```

| Campo | Offset | Tamanho | Descrição |
|-------|--------|---------|-----------|
| type_id | 0 | 4 bytes | Sempre 2 para Array |
| flags | 4 | 4 bytes | Reservado para GC futuro |
| length | 8 | 4 bytes | Número de elementos |
| elem_size | 12 | 4 bytes | Tamanho de cada elemento em bytes |
| elements | 16 | variável | Dados contíguos |

### Tamanhos de Elementos

| Tipo | elem_size |
|------|-----------|
| byte, bool | 1 |
| short | 2 |
| int, char | 4 |
| long | 8 |
| float | 4 |
| double | 8 |
| referência | 8 (ponteiro) |

---

## 5. Runtime Functions (Native)

| Função | Entrada | Retorno | Descrição |
|--------|---------|---------|-----------|
| `kof_array_alloc` | length, elem_size | array_ptr | Aloca array no heap |
| `kof_array_length` | array_ptr | int | Retorna length |
| `kof_array_get` | array_ptr, index | element | Lê com bounds check |
| `kof_array_set` | array_ptr, index, value | void | Escreve com bounds check |

### Contrato

- `kof_array_alloc` retorna ponteiro alinhado em 16 bytes
- `kof_array_alloc` inicializa header (type_id=2, flags=0)
- `kof_array_get` em index inválido → `kof_bounds_error`
- `kof_array_set` em index inválido → `kof_bounds_error`
- `kof_array_get` em array NULL → `kof_null_error`
- `kof_array_set` em array NULL → `kof_null_error`

---

## 6. IR Operations

| Operação | JVM | Native | KofJS | Descrição |
|----------|-----|--------|-------|-----------|
| `KofNewArray(elemType)` | `NEWARRAY` | `kof_array_alloc` | `new Array(n).fill(...)` | Cria array |
| `KofArrayLoad(elemType)` | `IALOAD`/`LALOAD`/etc | `kof_array_get` | `kofArrayGet(array, index)` | Lê elemento (bounds check) |
| `KofArrayStore(elemType)` | `IASTORE`/`LASTORE`/etc | `kof_array_set` | `kofArraySet(array, index, value)` | Escreve elemento (bounds check) |
| `KofArrayLength()` | `ARRAYLENGTH` | `kof_array_length` | `array.length` | Retorna length |

> **KOF-SBD-001 (Array Bounds Safety):** até a correção do gap KofJS, o
> lowering desse target baixava `KofArrayLoad`/`KofArrayStore` para
> `array[index]`/`array[index] = value` diretos (semântica JS crua: leitura
> fora dos limites retornava `undefined`, escrita em `index >= length`
> ampliava o array silenciosamente). Os helpers `kofArrayGet`/`kofArraySet`
> (`JsRuntimeCore`) fecham essa divergência: todo acesso indexado a array
> Kof no target JS agora rejeita `index < 0 || index >= length` antes de
> tocar o array, equivalente à checagem que a JVM já faz em
> `IALOAD`/`AALOAD`/etc (JVMS §6.5) e que o Native faz em `kof_array_get`/
> `kof_array_set`. A classe do erro não é idêntica entre os 3 targets — a
> propriedade de segurança (bounds safety) é.

---

## 7. Type Checking (SemanticAnalyzer)

| Regra | Validação |
|-------|-----------|
| Índice é Int | `a[b]` — `b` deve ser `Int` |
| Leitura retorna elementType | `a[i]` retorna tipo do elemento |
| Escrita exige tipo compatível | `a[i] = v` — `v` deve ser compatível com elementType |
| length retorna Int | `a.length` retorna `Int` |
| Criação valida tipo | `new Int[10]` — tipo deve ser válido |
| Array<Int> não aceita String | Type mismatch em runtime |
| Array<String> não aceita Int | Type mismatch em runtime |

---

## 8. JVM vs Native

| Operação | JVM | Native |
|----------|-----|--------|
| Criação | `NEWARRAY` | `kof_array_alloc` |
| Acesso (primitivo) | `IALOAD`/etc | `kof_array_get` |
| Acesso (referência) | `AALOAD` | `kof_array_get` |
| Escrita (primitivo) | `IASTORE`/etc | `kof_array_set` |
| Escrita (referência) | `AASTORE` | `kof_array_set` |
| Length | `ARRAYLENGTH` | `kof_array_length` |
| Bounds check | JVM automático | `kof_bounds_error` |
| Null check | JVM automático | `kof_null_error` |

---

## 9. Null

| Valor | Representação |
|-------|---------------|
| null | Ponteiro 0x0 |
| new Int[0] | KofArray com length=0 |

kof_null_error() disponível para detecção.

---

## 10. Arquivos

| Arquivo | Papel |
|---------|-------|
| Type.java | `ArrayType` record + `isArray()` + `arrayElementType()` |
| AstNodes.java | `NewArrayExpr` + `ArrayAccessExpr` |
| Parser.java | Parsing de `new Type[size]` + `expr[expr]` |
| SemanticAnalyzer.java | Type checking de arrays |
| CompilerDriver.java | Lowering para `KofNewArray`/`KofArrayLoad`/`KofArrayStore`/`KofArrayLength` |
| IRNodes.java | `KofNewArray`/`KofArrayLoad`/`KofArrayStore`/`KofArrayLength` |
| NativeRuntime.java | 4 funções de runtime para arrays |
| NativeBackend.java | Lowering das operações de array |
| JvmBackend.java | `NEWARRAY`/`IALOAD`/`IASTORE`/`ARRAYLENGTH` |

---

> **Atualizado (0.2.6-beta, 31/08):** arrays de `Double`/`Float` entram no
> fluxo do JSON nativo (`Double[]`/`Float[]` no decode — JSN001), com FP em
> XMM (`vcvtsi2sd`/`mulsd`); a alocação de arrays segue na free-list
> `kof_free_head` (thread-safe com o `spawn` em pthreads).

## 11. Limitações Conhecidas

1. Sem inicialização de array com literais (`[1, 2, 3]`) — apenas `new Type[size]`
2. Sem arrays multidimensionais sintáticos (`new Int[3][4]`)
3. Sem `instanceof` para arrays
4. Sem conversão entre tipos de array
5. Sem `System.arraycopy` equivalente
6. Sem anonymous arrays
