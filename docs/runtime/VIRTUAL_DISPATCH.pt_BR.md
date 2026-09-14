[English](VIRTUAL_DISPATCH.md) | [Português](VIRTUAL_DISPATCH.pt_BR.md)

# VIRTUAL_DISPATCH.md — Dispatch Dinâmico do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.4

---

## 1. Visão Geral

Kof suporta dispatch dinâmico (virtual dispatch) para métodos de instância. Quando um método é chamado via referência de superclass, a implementação correta é resolvida em runtime pelo tipo real do objeto.

```kof
class Animal {
    speak(): String = "animal"
}
class Dog extends Animal {
    speak(): String = "dog"
}
main() {
    Animal a = new Dog()
    println(a.speak())  // imprime "dog", não "animal"
}
```

---

## 2. Mecanismo

### Object Header (estendido)

```
offset 0:  type_id (4 bytes)
offset 4:  flags (4 bytes)
offset 8:  method_table_ptr (8 bytes) — ponteiro para vtable
```

HEADER_SIZE = 16 bytes (era 8 antes do F.4).

### Method Table (vtable)

Cada classe possui uma method table na seção `.data`:

```asm
Dog_vtable:
    .quad Dog_speak    # slot 0: speak (override de Animal)
    .quad 0            # sentinel
```

A vtable contém ponteiros para as implementações de métodos virtuais.

### Ordem dos Slots

1. Métodos da superclass primeiro (na ordem de declaração)
2. Métodos da subclass depois
3. Override substitui o ponteiro no mesmo slot
4. Novos métodos recebem novos slots

Exemplo:
```
Animal_vtable: [Animal_speak]
Dog_vtable:    [Dog_speak]           # override no slot 0
```

### Dispatch Nativo

```asm
# animal.speak()
popq %rax              # carrega ponteiro do objeto
movq 8(%rax), %rbx     # carrega method_table_ptr do header
addq $0, %rbx          # offset do slot (index * 8)
movq (%rbx), %rbx      # carrega ponteiro da função
call *%rbx             # chama via ponteiro
```

### Dispatch JVM

O JVM usa `INVOKEVIRTUAL` nativo, que já resolve virtual dispatch corretamente.

---

## 3. Regras

1. **Toda classe** recebe uma vtable (mesmo sem override)
2. **Métodos herdados** mantêm o mesmo slot na hierarquia
3. **Override** substitui o ponteiro no slot existente
4. **Novos métodos** recebem slots novos após os herdados
5. **super.method()** continua sendo chamada estática (direct dispatch)
6. **Métodos estáticos** e **construtores** não usam vtable

---

## 4. Inicialização

Após `kof_alloc`, o objeto é inicializado com:
1. type_id = 0
2. flags = 0
3. method_table_ptr = ponteiro para a vtable da classe concreta

O construtor depois inicializa os fields.

---

## 5. Arquivos

| Arquivo | Papel |
|---------|-------|
| ClassLayout.java | HEADER_SIZE = 16, METHOD_TABLE_OFFSET = 8 |
| NativeRuntime.java | generateMethodTable(), emitInitObject() |
| NativeBackend.java | collectVirtualMethods(), findVirtualMethodIndex(), emitCall() |
| CompilerDriver.java | inferExprType() com case NewExpr |

---

> **Atualizado (0.2.6-beta, 31/08):** o item 1 abaixo foi superado —
> dispatch para interfaces (F.5) usa a mesma vtable. O dispatch é
> thread-safe com o `spawn` em threads (pthread, 31/08): a vtable é
> somente-leitura após a compilação.

## 6. Limitações Conhecidas

1. ~~Sem virtual dispatch para interfaces~~ — ✅ F.5 (mesma vtable)
2. Sem vtable para records, strings, arrays (usam header fixo)
3. Sem cache de vtable em runtime
4. Sem invalidation de vtable (futuro: sealed classes)

---

## 7. Testes

| Teste | JVM | Native |
|-------|-----|--------|
| simpleOverride | ✅ | ✅ |
| polymorphism | ✅ | ✅ |
| threeLevelOverride | ✅ | ✅ |
| superMethod | ✅ | ✅ |
| methodNotOverridden | ✅ | ✅ |
| vtableContainsMethods | — | ✅ |
