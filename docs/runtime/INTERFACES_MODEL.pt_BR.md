[English](INTERFACES_MODEL.md) | [Português](INTERFACES_MODEL.pt_BR.md)

# INTERFACE_MODEL.md — Modelo de Interfaces do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.5

---

## 1. Visão Geral

Kof suporta interfaces com métodos abstratos. Uma classe pode implementar uma ou mais interfaces.

```kof
interface Speaker {
    speak(): String
}

class Dog implements Speaker {
    speak(): String = "woof"
}
```

---

## 2. Sintaxe

### Declaração de interface

```kof
interface Nome {
    metodo(): TipoRetorno
}
```

### Implementação por classe

```kof
class Classe implements Interface1, Interface2 {
    // implementar métodos obrigatórios
}
```

### Herança de interface

```kof
interface Base {
    metodo(): String
}
interface Derivada extends Base {
    outroMetodo(): String
}
```

---

## 3. Semântica

### Regras

1. Interfaces definem contratos (métodos abstratos)
2. Classes devem implementar todos os métodos da interface
3. Uma classe pode implementar múltiplas interfaces
4. Interfaces podem estender outras interfaces
5. Interfaces NÃO possuem campos (apenas métodos)
6. Interfaces NÃO possuem construtores
7. Métodos de interface são sempre públicos

### Dispatch

Chamadas através de tipo de interface usam dispatch via vtable:

```
Speaker s = new Dog()
s.speak()
    ↓
Dog.speak()  // resolvido pelo tipo real do objeto
```

---

## 4. Representação na IR

### KofCallKind

```java
enum KofCallKind { INSTANCE, STATIC, CONSTRUCTOR, FUNCTION, INTERFACE }
```

Chamadas via tipo de interface usam `KofCallKind.INTERFACE`.

### JvmBackend

Chamadas via interface usam `INVOKEINTERFACE` em vez de `INVOKEVIRTUAL`.

### NativeBackend

Dispatch via vtable, mesmo mecanismo que virtual dispatch. O índice do método é determinado pela ordem dos métodos na interface.

---

## 5. Method Tables

Interfaces contribuem para a vtable das classes que as implementam:

```
Speaker_vtable: [Speaker_speak]
Dog_vtable:     [Dog_speak]  // herda slot da interface
```

Métodos herdados de interfaces são incluídos na vtable da classe implementadora.

---

## 6. Arquivos

| Arquivo | Papel |
|---------|-------|
| IRNodes.java | `KofCallKind.INTERFACE` |
| SemanticAnalyzer.java | `isInterfaceType()`, `resolveInHierarchy()` caminha interfaces |
| CompilerDriver.java | Define `KofCallKind.INTERFACE` para chamadas via interface |
| JvmBackend.java | `INVOKEINTERFACE` para chamadas via interface |
| NativeBackend.java | `collectVirtualMethods()` inclui interfaces, dispatch via vtable |

---

> **Atualizado (0.2.6-beta, 31/08):** dispatch via vtable para interfaces é
> thread-safe com o `spawn` em threads (pthread, 31/08). As limitações de
> default/static methods continuam válidas.

## 7. Limitações

1. Sem default methods (métodos com corpo na interface)
2. Sem static methods em interfaces
3. Sem campos em interfaces
4. Sem validação de implementação completa (compile-time)
5. Sem generics em interfaces
