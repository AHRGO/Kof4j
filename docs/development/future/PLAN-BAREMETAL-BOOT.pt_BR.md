[English](PLAN-BAREMETAL-BOOT.md) | [Português](PLAN-BAREMETAL-BOOT.pt_BR.md)

# Kof bare-metal / bootável — costura HAL + freestanding, BIOS legado, UEFI e MCU

**Status:** Plano (arquitetura futura) — **zero código**, sem passo agendado
**Tipo:** arquitetura futura / registo de dependência (NÃO ordem de implementação)
**Data:** 15 de setembro de 2026
**Fonte:** `../IMPLEMENTATION-UNIVERSAL-PLATFORM.md` §8.2 (Native: "deploy/edge/sistemas") ·
`PLAN-TREE-SHAKING.md` §T3 (rota embedded/MCU) · `docs/development/native-multiarch.md`
· diretiva da mantenedora (15/09): *"todo código nativo deve se comunicar direto
com barebones também — código bootável para microcontroladores, legado e UEFI com Kof"*.

> **Regra deste documento** (mesma do `../IMPLEMENTATION-UNIVERSAL-PLATFORM.md`): é um registo
> estratégico/de arquitetura. Não implementa nada, não abre frente, não muda
> roadmap, não move arquivo, não adiciona dependência. O estado atual do Kof fica
> 100% intacto. Qualquer coisa que exija mudança profunda no core é gravada como
> **dependência arquitetural futura**, nunca como ação.
>
> Este item fica em `future/` porque **não há código**: é a fronteira honesta já
> medida por `PLAN-TREE-SHAKING.md` §T3 ("embedded real exige um backend
> RTOS/bare-metal — fica em `future/` sem passo agendado"). Quando a primeira face
> pousar (um link freestanding produzindo ELF sem dinâmica), o item **sai de
> `future/` para `docs/development/`** conforme a regra do `future/README.md`.

---

## 1. O pedido (uma frase)

Todo backend **nativo** deve poder mirar uma máquina **sem sistema operacional**:
o mesmo frontend/IR/stdlib do Kof, mas emitindo artefatos **bootáveis** para
(a) **microcontroladores**, (b) **BIOS legado (MBR/real mode)** e (c) **UEFI**,
com a fronteira de runtime (`write`/`exit`/alocação/tempo) fornecida por um
**back-end de plataforma por alvo** em vez de syscalls Linux.

A diretiva é mais ampla que um novo formato de saída: é o princípio de que o
código nativo fala com uma superfície "barebones", da qual a superfície atual de
syscalls Linux é **uma implementação entre várias**.

## 2. Estado medido — o que bloqueia bare-metal hoje (15/09)

Fatos do código, não estimativas:

1. **O runtime está fixado a syscalls Linux.** O runtime riscv/aarch emite `ecall`
   cru com números de syscall Linux (`write`=64, `exit`/`exit_group`=93/94, `mmap`,
   `futex`, `clone`, `clock_gettime`); o runtime x86 emite `syscall`
   (SYS_futex/SYS_mmap/SYS_write). **Não há camada de indireção** — cada fatia emite
   o syscall inline.
2. **O nativo x86 é dinamicamente ligado e precisa de libc.**
   `NativeAssembler.java:36-49` (medido) fixa
   `-dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc -lm` e, quando usado,
   `-l:libsqlite3.so.0`/`-l:libmariadb.so.3`/`-l:libpthread.so.0`. Uma imagem de
   boot não tem `ld.so` nem libc. O caminho riscv/aarch já liga **estático sem
   libc** (`riscv64-linux-gnu-ld --no-relax`, `aarch64-linux-gnu-ld --gc-sections`)
   — a base existente mais próxima, mas ainda com `ecall` Linux.
3. **O ponto de entrada é `_start` com suposição de ABI Linux.** Os emitters
   produzem `_start` (`NativeArchEmitter`), e a saída riscv usa `exit_group`(94)
   para matar threads do scheduler — sem sentido em bare-metal. BIOS/UEFI/MCU
   precisam de entradas diferentes (`0x7C00` setor de boot real mode, `efi_main`,
   um `Reset_Handler` de vector table).
4. **A alocação é uma arena `.bss` fixa + free-list.** `_kof_heap`
   (262 144 B, `NativeRiscvAsmRtB4.java:386`) com guard OOM em `_kof_heap_end`;
   um MCU tem poucos KB de SRAM, então o pool precisa ser **dimensionado pelo
   linker script**, não fixo, e o coletor (G-4/G-5 de `native-multiarch.md`) tem
   de estar ligado.
5. **O codegen existe só para 64 bits.** `Target.NATIVE` (x86_64),
   `NATIVE_RISCV64`, `NATIVE_AARCH64` (`Target.java`). MCUs são majoritariamente
   **32 bits** (ARM Cortex-M Thumb-2, riscv32) — lacuna de codegen, classificada
   **C** por `../IMPLEMENTATION-UNIVERSAL-PLATFORM.md` §8.2(e) ("RISC/ARM codegen").
6. **O mecanismo de GC está congelado (regra 6).** `PLAN-TREE-SHAKING.md` §7 diz
   que T1b tocando o root-scan do GC "toca o mecanismo de GC (congelado)". A
   costura HAL abaixo, portanto, **não** deve passar pelo GC.

## 3. O princípio arquitetural — uma costura de plataforma (HAL), não outra linguagem

A diretiva é satisfeita por **um** movimento arquitetural, não por três backends
separados: introduzir uma **superfície de plataforma** fina pela qual toda
operação ambiental do runtime passa, com implementações plugáveis.

```
Programa Kof (frontend, IR, dispatch do stdlib)      ← inalterado, uma língua
        │
   runtime nativo (chama só kof_plat_*)              ← a costura
        │
   ┌────────────┬──────────────┬──────────────┬───────────────┐
 Linux        UEFI          BIOS legado    MCU (Cortex-M/riscv32)
 kof_plat_*   kof_plat_*    kof_plat_*     kof_plat_*
 = syscall    = BootSvc     = int 0x10/13  = UART/semihosting
```

Superfície candidata (mínima, honesta — só o que o runtime já precisa):

| Símbolo | Usado hoje | Impl Linux (agora) | Impl bare (por alvo) |
|---------|-----------|--------------------|----------------------|
| `kof_plat_write` | print/log | `write`(64) | UEFI `ConOut->OutputString`; BIOS `int 0x10` tty; UART/`SYS_WRITE0` |
| `kof_plat_exit` | panic/exit | `exit`/`exit_group` | UEFI `BootServices->Exit`; BIOS `hlt`; MCU `bkpt`/loop |
| `kof_plat_alloc` | heap | bump no `.bss` | idem, tamanho do linker script |
| `kof_plat_time` | clock/sched | `clock_gettime` | UEFI `BootServices->GetTime`; BIOS PIT/RTC; MCU SysTick |
| `kof_plat_sync`/`kof_plat_thread` | spawn (pthread/clone) | `futex`/`clone` | **ausente** (single-core) → lacuna `CONC003`, nunca silenciosa |

**Propriedade-chave (testável):** no Linux a costura é um **renome de custo zero,
semântica idêntica** dos syscalls atuais, então toda a suíte nativa existente
(x86 65/0, riscv 44 + aarch 44 sob qemu, `KofGcE2ETest`) precisa continuar verde.
Esse é o critério de aceitação que torna isto um refactor habilitante seguro, e
não uma reescrita.

**Decisão/nomeação necessária (mantenedor, regra 6):** a diretiva "sem alvo por
domínio" (`../IMPLEMENTATION-UNIVERSAL-PLATFORM.md` §16) **não** é violada se isto for um
**perfil de Native** (`native --profile freestanding|uefi|bios|mcu`) em vez de
quatro valores novos no enum `Target` — análogo a como `native.risc`/`native.arm`
são variantes de arch, não novas línguas. Este documento **não** toma posição;
regista as duas opções.

## 4. Decomposição (faces B-0…B-5), cada uma provável independentemente

Seguindo o estilo G-0…G-5 do `native-multiarch.md`: cada face tem uma
**prova falseável**, e faces posteriores dependem das anteriores.

### B-0 — Costura de plataforma (HAL) sobre o runtime · **habilitadora**
Roteia **toda** operação ambiental nos três runtimes nativos pelos símbolos
`kof_plat_*` do §3. A implementação Linux é o código atual, renomeado
(comportamento byte-idêntico onde a ABI permite).
**Aceitação:** a suíte nativa/cross completa fica verde **sem** mudar saídas
esperadas; uma sabotagem (fazer `kof_plat_write` no-op) faz os testes de saída
falhar — provando que a costura é realmente usada, não decorativa.
**Depende de:** nada. **Lacuna:** `NATIVE003` (proposta).

### B-1 — Perfil de link freestanding · **depende de B-0**
`native --profile freestanding`: sem `-lc`/`-dynamic-linker`, `_start`/`_end`
próprios, linker script (heap e stack configuráveis), sem libc. No x86_64 remove
o `-dynamic-linker … -lc -lm` fixo (`NativeAssembler.java:36-49`); o caminho
riscv/aarch já é estático.
**Aceitação:** o parser ELF/`readelf` reporta **sem `PT_INTERP`**, sem `DT_NEEDED`;
o binário ainda imprime um hello Kof sob qemu-user; uma sabotagem (re-adicionar
`-lc`) é pega pela asserção de `DT_NEEDED`.
**Depende de:** B-0. **Classificação:** M (médio).

### B-2 — UEFI (x86_64, e depois aarch64) · **depende de B-1**
Emitir um aplicativo EFI: entrada `efi_main(EFI_HANDLE, EFI_SYSTEM_TABLE)`;
saída via `SystemTable->ConOut->OutputString`; memória via
`BootServices->AllocatePool`/`Exit`. Produzir PE/COFF (`objcopy -O
efi-app-x86_64` ou emitter PE nativo) e colocá-lo na partição EFI FAT como
`\EFI\BOOT\BOOTX64.EFI`.
**Aceitação:** **qemu + OVMF (TianoCore)** boota um "hello" Kof e o imprime via
Console Output; um `main` Kof retornando não-zero mapeia para o status do
`BootServices->Exit`. **Depende de:** B-1. **Classificação:** H (alto).
**Toolchain:** firmware OVMF + `qemu-system-x86_64`.

### B-3 — BIOS legado (MBR / real mode) · **depende de B-1**
Um setor de boot de 512 bytes (magia `0x55AA`) que carrega o payload Kof (loader
de setor customizado ou Multiboot) e imprime via teletipo de BIOS
`int 0x10, ah=0x0E`; memória via `int 0x15, eax=0xE820`. Exige uma entrada
**16-bit real-mode** (ou um stub pequeno de 32-bit protected mode) — uma lacuna
de codegen própria.
**Aceitação:** `qemu-system-x86_64 -drive format=raw,file=kof.img` boota e imprime
o hello Kof do setor de boot; uma sabotagem (quebrar `0x55AA`) → qemu reporta
"no bootable device".
**Depende de:** B-1. **Classificação:** H (alto) — a entrada real-mode é o custo
dominante.

### B-4 — Microcontrolador (ARM Cortex-M Thumb-2 / riscv32) · **depende de B-1**
Codegen novo de 32 bits (variante `Target`/arch), linker script com **vector
table** (`Reset_Handler`), sem OS; saída via UART ou **semihosting** ARM
(`arm-none-eabi` / `probe-rs`). Heap do linker script (escala KB), então o
coletor (G-4/G-5) é pré-requisito duro para qualquer coisa long-running.
**Aceitação:** sob `qemu-system-arm -M mps2-an385` (Cortex-M3) **ou**
`qemu-system-riscv32 -M virt` com semihosting, um hello Kof imprime pelo canal
semihosting; o caminho de reset da vector table é verificado na imagem.
**Depende de:** B-0, B-1 e do **coletor** `native-multiarch.md` G-4/G-5.
**Classificação:** R/H (pesquisa/alto) — é o §T3 "projeto em si".

### B-5 — Back-ends de plataforma (os corpos `kof_plat_*` por alvo) · **por face**
Implementar a tabela do §3 para UEFI (B-2), BIOS (B-3) e MCU (B-4). Em bare-metal,
`spawn`/`select`/`await` não podem ser fornecidos honestamente → **lacuna
`CONC003`** (nunca um stub silencioso), consistente com o precedente `CONC003` do JS.

## 5. Dependências honestas, bloqueios e classificação

| Face | Depende de | Custo | Lacuna em falha |
|------|-----------|-------|-----------------|
| B-0 costura HAL | — | M | `NATIVE003` |
| B-1 freestanding | B-0 | M | `NATIVE003` / erro de link |
| B-2 UEFI | B-1 | H | `NATIVE003` |
| B-3 BIOS legado | B-1 (+entrada 16-bit) | H | `NATIVE003` |
| B-4 MCU (32 bits) | B-0, B-1, G-4/G-5 | R/H | `NATIVE002` (codegen) / `NATIVE003` |
| B-5 corpos de plataforma | por face | M | `CONC003` para concorrência |

**Bloqueios transversais (reais):**
- **Coletor GC (G-4/G-5)** precisa pousar antes do B-4 (RAM em escala KB). Não é
  necessário para as provas hello-level de B-1/B-2/B-3.
- **Codegen de 32 bits** (B-3 real mode, B-4) é genuinamente novo; não prometer
  via tradutor (riscv64→aarch64 somente).
- **Regra 6 / GC congelado:** B-0 deve renomear a fronteira de syscall **sem**
  tocar internals de `kof_gc_mark`/`sweep`.
- **Toolchains são externas** (OVMF, `arm-none-eabi`, `probe-rs`,
  `qemu-system-*`); o padrão de CI da face (5) do `native-multiarch.md`
  (instalar a toolchain para o teste **rodar**, nunca pular silenciosamente)
  aplica-se literalmente.

## 6. O que isto NÃO é (non-goals)

- **Não** é outra linguagem nem um alvo por domínio: um frontend/IR/stdlib, um
  alvo `Native` com **perfis** (ou variantes de arch) — decisão do mantenedor (§3).
- **Não** é promessa de features de libc/OS em bare-metal: arquivos, rede,
  threads, sinais são **ausentes** e reportados como lacunas (`CONC003`, `NET…`),
  nunca stubados.
- **Não** é um RTOS. Escalonamento, drivers além de serial/framebuffer e
  filesystems são território do usuário/FFI.
- **Não** está agendado: fica em `future/` até B-1 produzir um ELF sem dinâmica,
  quando o item vai para `docs/development/` com estado real.

## 7. Como terminar (ordem, uma vez autorizada)

1. **B-0** (costura HAL) — seguro, reversível, suíte verde; o princípio "nativo fala
   com barebones" vira real. Commit com a suíte cross completa verde.
2. **B-1** (ELF freestanding) — primeiro artefato quase-bootável, prova qemu-user.
3. **B-2** (UEFI/OVMF) **ou B-3** (BIOS/MBR) — o que a mantenedora priorizar; ambos
   são H e independentes entre si.
4. **G-4/G-5** (coletor) — pré-requisito do **B-4** (MCU).
5. **B-4** (MCU 32 bits) — o maior, classe-resquisa.
6. **B-5** — os corpos de plataforma, um por face conforme cada uma pousa.
