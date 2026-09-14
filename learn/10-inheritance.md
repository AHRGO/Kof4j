[English](10-inheritance.md) | [Português](10-inheritance.pt_BR.md)

# 10 — Inheritance

> **Status: implemented (JVM / JS — Native SUP001) — 0.3.22-beta — Target separation `native.risc/arm` preserves dispatch**
>
> `extends`, virtual dispatch, overriding, `super(...)` constructor and
> `super.metodo()` work on the JVM and KofJS targets; on Native, inheritance and
> `super(...)` work, but `super.metodo()` reports the gap `SUP001`
> (the compiler does not yet emit the non-virtual call over the vtable).

## Extends

```kf
class Animal {
    String nome
    constructor(String n) {
        nome = n
    }
    String falar() {
        return nome + " faz um barulho"
    }
}

class Cachorro extends Animal {
    constructor() {
        super("Rex")
    }
    String falar() {
        return nome + " late"
    }
}
```

## super(...) — superclass constructor

The subclass constructor calls `super(args)` as the **first statement** of the body. Without an explicit call, the compiler inserts `super()` automatically (when the superclass is not `Object`).

```kf
constructor(String n) {
    super(n)          // explicit: forwards the argument
}
```

## super.metodo() — superclass implementation

To invoke the overridden implementation (not its own), use `super.metodo(args)`:

```kf
class Cachorro extends Animal {
    String falar() {
        return super.falar() + " (latindo)"
    }
}
```

On the JVM backend this becomes an `invokespecial` with owner at the direct superclass — non-virtual dispatch, just like `javac`. It also works against external superclasses coming from the classpath (`android.view.View` etc.): the compiler reads the real signature from the `.jar`/`.aar` to emit the exact descriptor.

## Hierarchy

```
Object
  └── Animal
        ├── Cachorro
        └── Gato
```

## Abstract classes

```kf
abstract class Forma {
    abstract Double area()
}

class Circulo(Double raio) extends Forma {
    Double area() {
        return 3.14159 * raio * raio
    }
}

class Retangulo(Double largura, Double altura) extends Forma {
    Double area() {
        return largura * altura
    }
}
```

## sealed classes (planned — not yet implemented)

The keyword exists in the lexer, but the parser does not yet consume `sealed ...
permits` in a class declaration. Illustrative example of what is intended:

```kf
sealed class Resultado<T> permits Sucesso<T>, Erro<T> {}

class Sucesso<T>(T valor) extends Resultado<T> {}
class Erro<T>(String mensagem) extends Resultado<T> {}
```

This guarantees that `Resultado` can only be implemented by `Sucesso` and `Erro`. The compiler can verify the completeness of the `switch`.

## Polymorphism

```kf
void imprimirArea(Forma forma) {
    print(forma.area())
}

var c = new Circulo(5.0)
var r = new Retangulo(3.0, 4.0)

imprimirArea(c)   // 78.53975
imprimirArea(r)   // 12.0
```

## Next step

[Generics →](11-generics.md)
