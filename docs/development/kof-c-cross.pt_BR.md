# kof-c-compiler em alvos cross — plano em fatias (C1–C4)

[English](kof-c-cross.md) | [Português](kof-c-cross.pt_BR.md)

**Status:** EM CURSO — **C1 + C2 LANDADAS** (22/09). Dono: frente FFI/kof-c
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

Globais `int g;` e funções com parâmetros/retorno `int` e locais — só
`signed int`, sem structs, sem floats. Controle `if`/`while`, binárias
inteiras/bitwise/comparação/shift, `&ident` e `*(int*)ident`, chamadas com
até seis argumentos em registrador, `print`/`print_arg`, bytes `asm` crus.
Freestanding: só syscalls cruas, sem libc.

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
- **C2 — LANDADA (22/09):** parâmetros, retorno, locais e chamadas
  (`int f(int a, int b) { int t; ... return t; }`) nas três ISAs com a ABI C.
  Frame: par frame/retorno salvo + um slot de 8 bytes por parâmetro/local
  (base `rbp`/`s0`/`x29`). Argumentos seguem SysV
  (`rdi,rsi,rdx,rcx,r8,r9`), LP64 (`a0..a5`) e AAPCS64 (`x0..x5`) — até seis
  argumentos em registrador; o retorno sai no acumulador
  (`rax`/`a0`/`x0`), que é o registrador de retorno da ABI em todo alvo. As
  chamadas avaliam os argumentos na pilha e os desempilham nos registradores,
  então um argumento posterior pode reusar o acumulador sem clobberar o
  anterior. Diagnósticos honestos: chamada desconhecida, aridade errada,
  `print()` com argumentos e mais de seis parâmetros/argumentos são rejeitados
  antes de emitir binário (R6/Q7). Prova: `KofCParamsCompilerTest` 7/7 em
  x86_64/riscv64/aarch64 (retorno de dois parâmetros, locais + laço, void
  mutando global, chamadas aninhadas, return antecipado, parâmetro ponteiro
  desreferenciado no callee, os seis registradores de argumento) + os quatro
  casos de rejeição.
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
