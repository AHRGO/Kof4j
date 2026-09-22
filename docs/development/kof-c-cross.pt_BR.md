# kof-c-compiler em alvos cross — plano em fatias (C1–C4)

[English](kof-c-cross.md) | [Português](kof-c-cross.pt_BR.md)

**Status:** EM CURSO — **C1 LANDADA** (22/09). Dono: frente FFI/kof-c
(lane development/tooling).

## Por quê

O compilador C do repositório (`kof-c-compiler`, CLI `kof c`) só emitia
executáveis freestanding x86-64. Os testes FFI cross precisam de uma fixture
C com **struct por valor em parâmetro**, e este host não tem compilador C
cross (nada de `gcc`/`clang`/`zig` para riscv64/aarch64 — só binutils + qemu,
montados por `scripts/setup-cross-toolchain.sh` em `/tmp/kof-cross`). Em vez
de depender de um cc cross externo, a mantenedora aprovou crescer o
compilador do repositório para emitir os alvos cross.

É um esforço de várias sessões; o objetivo final é uma fixture C que os
testes FFI cross nativos consigam ligar (destravando a fatia de struct-param).

## Subconjunto base

Globais `int g;` e funções `void f() { }` — sem parâmetros, sem retorno, sem
locais, sem structs, sem floats. Testes: `if`/`while`, binárias
inteiras/bitwise/comparação/shift, `&ident` e `*(int*)ident`, `print`/
`print_arg`, bytes `asm` crus. Freestanding: só syscalls cruas, sem libc.

## Fatias

- **C1 — LANDADA (22/09):** emissão alvo-aware.
  `KofCTarget` (`x86_64`/`riscv64`/`aarch64`), interface `KofCEmitter`,
  `KofCEmitterBase` (percurso da AST compartilhado) e emissores por ISA
  `KofCEmitterX86` / `KofCEmitterRiscv` / `KofCEmitterAarch`;
  `KofCCompiler.compile(path, out, target)` escolhe assembler/linker do
  alvo; `kof c --target` expõe isso. Prova: o subconjunto inteiro é
  byte-a-byte idêntico em riscv64/aarch64 sob qemu e igual ao oráculo x86_64
  (`KofCCrossCompilerTest` 7/7; `KofCCompilerTest` 7/7 inalterado). Sem
  toolchain cross → skip honesto (`assumeTrue`).
- **C2 — TODO:** parâmetros de função, retorno e locais
  (`int f(int a, int b)`) emitidos nas três ISAs com a ABI C.
- **C3 — TODO:** tipos `struct` e struct por valor em parâmetro (classificação
  SysV / AAPCS64 / RISCV64) — a peça que a fixture FFI precisa.
- **C4 — TODO:** saída de objeto reutilizável (`.o`) e link, para um teste
  cross consumir a fixture.

## Notas

- **relaxamento de `gp` no riscv64:** o linker relaxa `la` de globais
  próximos para gp-relativo (`addi t0, gp, off`); um `_start` cru não
  inicializa `gp`, então o acesso falha. O `_start` precisa fazer
  `la gp, __global_pointer$` sob `.option norelax`.
- **gaps honestos:** construção não suportada deve falhar com diagnóstico,
  nunca emitir binário errado (R6/Q7).
