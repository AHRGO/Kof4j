[English](INHERITANCE_MODEL.md) | [Português](INHERITANCE_MODEL.pt_BR.md)

# INHERITANCE_MODEL.md — Modelo de Herança do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.3

---

## 1. Visão Geral

Kof suporta herança simples de classes. Uma classe pode estender uma única superclass.

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

## 2. Sintaxe

### Declaração de classe com herança

```kof
class SubClasse extends SuperClasse {
    // ...
}
```

### Chamada de super construtor

```kof
class Dog extends Animal {
    public constructor(String name) {
        super(name)  // chamada explícita ao construtor da superclass
    }
}
```

### Acesso a members herdados

```kof
var dog = new Dog("Rex")
println(dog.name)    // field herdados de Animal
println(dog.speak()) // método herdado de Animal
println(dog.bark())  // método próprio de Dog
```

---

## 3. Type System

### Representação

Classes são representadas como `ClassType(packageName, name, typeArguments)`.

A relação de herança NÃO é armazenada no `ClassType`. Ela é armazenada no `ClassSymbol`:

```java
record ClassSymbol(String name, String packageName, String superClass,
                   List<String> interfaces, SymbolTable members)
```

### Subtipificação

`Dog` é subtipo de `Animal` se `Dog.superClass == "Animal"`.

A verificação de subtipificação é feita pelo `SemanticAnalyzer.resolveInHierarchy()`.

---

## 4. Symbol Resolution

O `SemanticAnalyzer` resolve members (fields, methods) caminhando a cadeia de superclasses:

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

### Ordem de resolução

1. Members da classe atual
2. Members da superclass
3. Members da super-superclass
4. ... até `Object`

---

## 5. Object Layout (Native)

### Layout com herança

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
| name (8 bytes)    |  → offset 8 (herdado de Animal)
+-------------------+
| weight (8 bytes)  |  → offset 16 (próprio de Dog)
+-------------------+
Total: 24 bytes
```

### Regras

1. **Campos da superclass vêm primeiro** — na ordem de declaração
2. **Campos da subclass vêm depois** — na ordem de declaração
3. **Não duplicar campos herdados** — cada campo aparece apenas uma vez
4. **Offset é determinístico** — calculado em compile-time pelo `ClassLayout`

### ClassLayout.buildWithSuper

```java
public static ClassLayout buildWithSuper(IRClass clazz,
        Function<String, IRClass> superclassResolver) {
    // 1. Caminhar a cadeia de superclasses
    // 2. Adicionar fields da superclass (na ordem)
    // 3. Adicionar fields da classe atual
    // 4. Calcular offsets e tamanho total
}
```

---

## 6. Constructor Chaining

### Ordem de execução

```kof
var dog = new Dog("Rex")
```

Resultado:

1. `Dog.<init>("Rex")` é chamado
2. Dentro de `Dog.<init>`, `super("Rex")` chama `Animal.<init>("Rex")`
3. `Animal.<init>` inicializa `this.name = "Rex"`
4. `Dog.<init>` continua (corpo do construtor)
5. Objeto Dog está pronto

### Regras

1. `super(args)` DEVE ser a primeira instrução do construtor
2. Se não houver `super(args)` explícito, um `super()` implícito é emitido
3. Apenas uma chamada `super()` por construtor

### IR

```java
// super(name) é lowerado como:
KofLoadLocal(ownerType, 0)           // this
[emit args]                          // name
KofCall(superType, "<init>", args, VOID, CONSTRUCTOR)
```

---

## 7. JVM vs Native

| Aspecto | JVM | Native |
|---------|-----|--------|
| Herança | `extends` bytecode | Field layout herdados |
| Super constructor | `INVOKESPECIAL super.<init>` | `call SuperClass_init` |
| Field access | `GETFIELD` com offset da hierarchy | `movq offset(%rax)` com offset do ClassLayout |
| Method access | `INVOKEVIRTUAL` | `call Class_method` (direct dispatch) |
| Object size | JVM gerencia | ClassLayout calcula (header + fields herdados + próprios) |

---

> **Atualizado (0.2.6-beta, 31/08):** os itens 1 e 3 abaixo foram
> superados — virtual dispatch (F.4) e interfaces (F.5) estão implementados
> em JVM e Native; herança de 3 níveis segue validada por E2E. Com o
> `spawn` em threads (pthread), o layout de objeto e os offsets calculados
> em compile-time (ClassLayout) são inalterados e thread-safe.

## 8. Limitações Conhecidas

1. ~~**Sem virtual dispatch**~~ — ✅ F.4 (vtable)
2. **Sem abstract classes** — todas as classes são concretas
3. ~~**Sem interfaces**~~ — ✅ F.5 (dispatch via vtable)
4. **Sem sealed classes** — não suportado ainda
5. **Sem múltipla herança** — apenas herança simples
6. **Sem diamond problem** — não aplicável com herança simples

---

## 9. Arquivos

| Arquivo | Papel |
|---------|-------|
| Type.java | ClassType (sem campo de herança) |
| SymbolTable.java | ClassSymbol.armazena superClass |
| SemanticAnalyzer.java | resolveInHierarchy() caminha a cadeia |
| ClassLayout.java | buildWithSuper() inclui fields herdados |
| NativeBackend.java | allClassesMap para resolver superclasses |
| CompilerDriver.java | lowerConstructor com super(args), findSuperClass() |
| IRNodes.java | IRClass.superName |

---

## 10. Testes

| Teste | JVM | Native |
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
