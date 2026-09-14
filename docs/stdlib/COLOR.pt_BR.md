[English](COLOR.md) | [Português](COLOR.pt_BR.md)

# Color — paleta de 32 bits

> Um `Color` é um `Int` de 32 bits com semântica ARGB (`0xAARRGGBB`).
> Nada de converter hex ou ANSI na mão: a paleta nomeada já vem pronta
> e o tipo sabe se apresentar.

## A classe

```kof
class Color {
    Int value

    constructor(Int value) {
        this.value = value
    }

    Int red()   { return (this.value >> 16) & 0xFF }
    Int green() { return (this.value >> 8) & 0xFF }
    Int blue()  { return this.value & 0xFF }
    Int alpha() { return (this.value >> 24) & 0xFF }

    String ansi() {
        return "\u001b[38;2;" + this.red() + ";" + this.green() + ";" + this.blue() + "m"
    }
}
```

## A paleta

```kof
class Colors {
    static Int primary   = 0xFF6750A4
    static Int secondary = 0xFF03DAC6
    static Int background = 0xFF121212
    static Int surface   = 0xFF1E1E1E
    static Int text      = 0xFFE0E0E0
    static Int error     = 0xFFCF6679
    static Int success   = 0xFF4CAF50
    static Int warning   = 0xFFFFB74D
}
```

## Uso

```kof
main() {
    var c = Color(Colors.primary)
    println(c.red())    // 103
    println(c.green())  // 80
    println(c.blue())   // 164
    println(c.alpha())  // 255
    println(c.ansi())   // \u001b[38;2;103;80;164m
}
```

## Semântica

- O valor é um `Int` **signed de 32 bits**: `0xFF6750A4` armazena
  `-10006364`. Os componentes usam shift + máscara, que funcionam
  identicamente nos três backends (JVM, Native, KofJS).
- Literais hex (`0xFF...`) são suportados pela linguagem desde a
  implementação do Color.
- A impressão direta do valor mostra o `Int` signed (`-10006364`);
  use os componentes ou `ansi()` para apresentação.

## Estilização orientada a objetos

A cor é um objeto entre objetos: o visual inteiro é uma árvore de objetos
compostos — sem strings mágicas, sem conversão manual:

```kof
class Style {
    Color background
    Color foreground
    Int padding

    constructor(Color background = Colors.surface,
                Color foreground = Colors.text,
                Int padding = 12) {
        this.background = background
        this.foreground = foreground
        this.padding = padding
    }
}
```

Ver também: `training/idioms/composition.md` (composição visual por objetos)
e `learn/35-ui-and-styling.md`.