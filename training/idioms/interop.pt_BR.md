[English](interop.md) | [Português](interop.pt_BR.md)

# Idiomas — Interop (tipos JVM e FFI C)

**Status:** parcial (whitelist) · **Introduzido:** 0.3.x (TIER 2.1) · **Atualizado:** 17/09

## O que é

Duas superfícies, uma regra: a plataforma já existe — não a reconstrua.
**(a)** JVM: qualquer tipo Java no classpath por nome qualificado. **(b)** FFI C:
`extern "<lib>" f(T): R` prende uma função nativa (JVM via `java.lang.foreign`).

## API real (medida no compilador — 0.4.0-beta)

```kof
// (a) interop JVM — nome qualificado, sem wrapper
var now = java.time.Instant.now()
println(now.toString())

// (b) FFI C — a whitelist prende 1-arg SOMENTE (JvmFfiRuntime):
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double   // ok
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int    // ok (String->Int)
// f(Int): Int ok. Todo o resto é diagnóstico em tempo de compilação:
extern "/lib/x86_64-linux-gnu/libc.so.6" strcmp2(String a, String b): Int
//  -> FFI001 (multi-arg; a gramática parseia, a whitelist rejeita)
// target JS  -> FFI002 (FFI não disponível no target JS)
// Native     -> FFI001 até o §61 (libc não inicializada)
```

## RUIM → BOM

| ❌ RUIM | ✅ BOM | Por quê |
|---|---|---|
| `extern ... drawText(String t, Int x, Int y): void` | hoje: uma bridge C 1-arg compilada por você (`int kof_draw(char*...)` atrás de um símbolo preso), ou interop JVM para binding existente | multi-arg = `FFI001`; o alargamento é a fatia R3 (`PLAN-UNIVERSAL-PLATFORM.md`, issue #431) — NÃO emita bytecode na mão para furar o compilador |
| assumir que o caminho da lib é checado em compile | trate lib/símbolo ausente como falha `kof_ffi_*` de **runtime** | o caminho resolve em runtime (`SymbolLookup`), não em compile |
| reimplementar sin/cos/strcmp em Kof | prenda a lib do sistema (formas 1-arg) | complexidade é da plataforma (regra de ferro 2) |

## Veja também

`docs/language-reference/syntax.md` (§FFI com C), `grammar.md`
(`extern-declaration`), `modules.md` §6; gaps `FFI001`/`FFI002`;
fila: primeira fatia R3 (aridade → void → retorno String → `char*`).
