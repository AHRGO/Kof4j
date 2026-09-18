[English](interop.md) | [Português](interop.pt_BR.md)

# Idiomas — Interop (tipos JVM e FFI C)

**Status:** parcial (whitelist) · **Introduzido:** 0.3.x (TIER 2.1) · **Atualizado:** 18/09 (R3 JVM generalizado — ABI escalar + void + retorno String; ver `IMPLEMENTATION-UNIVERSAL-PLATFORM.pt_BR.md` 3.6) · **Atualizado:** 17/09

## O que é

Duas superfícies, uma regra: a plataforma já existe — não a reconstrua.
**(a)** JVM: qualquer tipo Java no classpath por nome qualificado. **(b)** FFI C:
`extern "<lib>" f(T): R` prende uma função nativa (JVM via `java.lang.foreign`).

## API real (medida no compilador — 0.4.0-beta)

```kof
// (a) interop JVM — nome qualificado, sem wrapper
var now = java.time.Instant.now()
println(now.toString())

// (b) FFI C — o JVM liga QUALQUER assinatura ESCALAR (R3 generalizado 18/09):
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double   // ok
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int    // ok (String->Int)
extern "/lib/x86_64-linux-gnu/libm.so.6" fmod(Double a, Double b): Double  // ok — 1.5 medido
extern "/lib/x86_64-linux-gnu/libm.so.6" ldexp(Double x, Int e): Double    // ok — 12.0 medido (misto)
extern "/lib/x86_64-linux-gnu/libc.so.6" puts(String s): void     // ok — void liga
extern "/lib/x86_64-linux-gnu/libc.so.6" getenv(String n): String // ok — "mel" medido
// O NOME da funcao Kof e o simbolo C (sem alias) — kof_fmod falhou no lookup, fmod funciona.
// Tipos nao-escalares (objetos, genericos) -> FFI001 em tempo de compilacao:
// runner JS  -> MESMA ABI escalar via KofJsFfiBridge (F2/F3 ✅ 18/09; FfiE2ETest 16/16); browser -> erro honesto de runtime (R7, sem host); nao-escalar -> FFI002
// Native     -> FFI001 até o §61 (libc não inicializada)
```

## RUIM → BOM

| ❌ RUIM | ✅ BOM | Por quê |
|---|---|---|
| ligar um simbolo sob outro nome Kof (`kof_fmod`) | o NOME e o simbolo C (sem alias, medido 18/09) — ligar `fmod`, envolver numa fn Kof para nome amigavel | multi-arg/`void`/retorno `String` ja ligam desde R3 18/09 — NAO emita bytecode na mao para furar o compilador |
| assumir que o caminho da lib é checado em compile | trate lib/símbolo ausente como falha `kof_ffi_*` de **runtime** | o caminho resolve em runtime (`SymbolLookup`), não em compile |
| reimplementar sin/cos/strcmp em Kof | prenda a lib do sistema (qualquer forma escalar desde 18/09) | complexidade é da plataforma (regra de ferro 2) |

## Veja também

`docs/language-reference/syntax.md` (§FFI com C), `grammar.md`
(`extern-declaration`), `modules.md` §6; gaps `FFI001`/`FFI002`;
R3 landado: JVM escalar arbitrario (aridade/void/retorno String, 18/09) + paridade JS host (3.6.F2/F3 ✅ 18/09); restantes: struct/D6, callbacks, variadics (⛔ mantenedora), Native §61.
