[English](11-generics.md) | [Português](11-generics.pt_BR.md)

# 11 — Generics

> **Status: implemented (JVM / Native / JS) — 0.4.0-beta — erasure + `Box<T>` with primitive `T`**
>
> Generics by erasure work on the three targets; `Box<Int>` with `substituteTypeVariable` + native `kof_int_to_string` is already in 0.2.0.

## The problem

Without generics, you need casts:

```java
List lista = new ArrayList();
lista.add("texto");
String texto = (String) lista.get(0);  // manual cast
```

With generics, the compiler knows the type:

```java
List<String> lista = new ArrayList<String>();
lista.add("texto");
String texto = lista.get(0);  // no cast
```

## Generics in Kof

### Generic classes

```kf
class Box<T> {
    T value

    set(T v) {
        value = v
    }

    get(): T {
        return value
    }
}
```

Usage:

```kf
var caixaTexto = new Box<String>()
caixaTexto.set("olá")
var caixaNumero = new Box<Int>()
caixaNumero.set(42)
println(caixaNumero.get())   // 42 — Box<T> with primitive T
```

`Box<T>` with primitive `T` (`Box<Int>`) works on the three targets — on Native
the `get()` returning `T` has its type substituted at compile-time
(`substituteTypeVariable`), so `println(b.get())` prints the value and does not
become a segfault.

### Generic methods

The type parameters come **after** the function name:

```kf
identity<T>(T x): T {
    return x
}

main() {
    println(identity(42))     // 42
    println(identity("hi"))   // hi
}
```

### Bounds (planned)

`extends` on type parameters is not yet resolved at compile-time.

## Variance (planned)

```kf
void copiar(List<? extends Animal> origem, List<? super Animal> destino) {
    for (var animal : origem) {
        destino.add(animal);
    }
}
```

## Interoperability with Java generics

```kf
// Kof using Java generics
var lista = new java.util.ArrayList<String>();
lista.add("hello");
String item = lista.get(0);
```

## Next step

[Collections →](12-collections.md)
