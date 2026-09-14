[English](COLOR.md) | [Português](COLOR.pt_BR.md)

# Color — 32-bit palette

> A `Color` is a 32-bit `Int` with ARGB semantics (`0xAARRGGBB`).
> No converting hex or ANSI by hand: the named palette comes ready-made
> and the type knows how to present itself.

## The class

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

## The palette

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

## Usage

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

## Semantics

- The value is a **signed 32-bit** `Int`: `0xFF6750A4` stores
  `-10006364`. The components use shift + mask, which work
  identically across the three backends (JVM, Native, KofJS).
- Hex literals (`0xFF...`) are supported by the language since the
  Color implementation.
- Printing the value directly shows the signed `Int` (`-10006364`);
  use the components or `ansi()` for presentation.

## Object-oriented styling

Color is an object among objects: the entire visual is a tree of
composed objects — no magic strings, no manual conversion:

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

See also: `training/idioms/composition.md` (visual composition through objects)
and `learn/35-ui-and-styling.md`.
