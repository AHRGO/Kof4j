[English](PLAN-BAREMETAL-BOOT.md) | [Português](PLAN-BAREMETAL-BOOT.pt_BR.md)

# Kof bare-metal / bootável — costura HAL + freestanding, BIOS legado, UEFI, MCU e anéis de privilégio (ring0/ring1)

**Status:** **EM DESENVOLVIMENTO — promovido de `future/` 22/09/2026** (ordem da mantenedora; `DECISIONS.md` §D-BAREMETAL-BOOT, fila 1.7) — face habilitadora **B-0** ainda não pousou (zero código)
**Tipo:** plano de implementação (promovido; ordem de execução da frente bare-metal)
**Data:** 15 de setembro de 2026 · **promoção:** 22 de setembro de 2026
**Fonte:** `../../architecture/UNIVERSAL-PLATFORM-VISION.md` §8.2 (Native: "deploy/edge/sistemas") ·
`PLAN-TREE-SHAKING.md` §T3 (rota embedded/MCU) · `docs/native-multiarch.md`
· diretiva da mantenedora (15/09): *"todo código nativo deve se comunicar direto
com barebones também — código bootável para microcontroladores, legado e UEFI com Kof"*.

> **Promoção (ordem da mantenedora, 22/09/2026 — `DECISIONS.md` §D-BAREMETAL-BOOT):**
> o plano **saiu de `future/` para `docs/development/`** e a frente está **aberta**;
> o portão R12 (SYSTEMS primeiro) é **sobreposto para esta frente pela ordem da
> mantenedora** (mesmo padrão do §D-UNIVERSAL). Escopo ordenado: **bare-metal com
> suporte a ring0/ring1** (níveis de privilégio x86_64) — a face habilitadora
> **B-0** (costura HAL) é o primeiro passo executável; a superfície Kof para mirar
> o ring1 é decisão rule 6 e **não** é inventada aqui. `PLAN-TREE-SHAKING.md` §T3
> ("embedded real = um backend RTOS/bare-metal em si") segue a fronteira honesta
> que este plano paga.

---

## 1. O pedido (uma frase)

Todo backend **nativo** deve poder mirar uma máquina **sem sistema operacional**:
o mesmo frontend/IR/stdlib do Kof, mas emitindo artefatos **bootáveis** para
(a) **microcontroladores**, (b) **BIOS legado (MBR/real mode)** e (c) **UEFI**,
com a fronteira de runtime (`write`/`exit`/alocação/tempo) fornecida por um
**back-end de plataforma por alvo** em vez de syscalls Linux. No x86_64 o modelo
de privilégio é explícito: o runtime boota em **ring0** e a plataforma suporta
domínios **ring1** (costura de privilégio B-6) — nunca uma troca silenciosa.

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
   **C** por `../../architecture/UNIVERSAL-PLATFORM-VISION.md` §8.2(e) ("RISC/ARM codegen").
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
domínio" (`../../architecture/UNIVERSAL-PLATFORM-VISION.md` §16) **não** é violada se isto for um
**perfil de Native** (`native --profile freestanding|uefi|bios|mcu`) em vez de
quatro valores novos no enum `Target` — análogo a como `native.risc`/`native.arm`
são variantes de arch, não novas línguas. Este documento **não** toma posição;
regista as duas opções.

## 4. Decomposição (faces B-0…B-6), cada uma provável independentemente

Seguindo o estilo G-0…G-5 do `native-multiarch.md`: cada face tem uma
**prova falseável**, e faces posteriores dependem das anteriores.

### B-0 — Costura de plataforma (HAL) sobre o runtime · **habilitadora**
Roteia **toda** operação ambiental nos três runtimes nativos pelos símbolos
`kof_plat_*` do §3. A implementação Linux é o código atual, renomeado
(comportamento byte-idêntico onde a ABI permite).
**Aceitação:** a suíte nativa/cross completa fica verde **sem** mudar saídas
esperadas; uma sabotagem (fazer `kof_plat_write` no-op) faz os testes de saída
falhar — provando que a costura é realmente usada, não decorativa.
**Fatia 1 (LANDADA 22/09, lane `baremetal` 9092):** a costura existe e está
roteada no **runtime riscv64** (o aarch64 a herda linha-a-linha via tradutor)
para a superfície core de programas single-thread — `kof_plat_write`,
`kof_plat_writev`, `kof_plat_exit`, `kof_plat_exit_group`, `kof_plat_time`,
`kof_plat_time_mono`, `kof_plat_sleep`, `kof_plat_random`, `kof_plat_thread_id`
(call sites: print/panic da Rt0, exit_group do `_start` nos dois emissores,
log/time da B0, entropia+relógio da Obs, gettid da B4/B48, entropia da
B25/B25b/B27, escritas de gc-print da B42).
Prova: E2E riscv64 54/54 + E2E aarch64 53/53 sob qemu (saídas byte-idênticas),
modelo de fatias verde e `PlatformSeamSabotageTest` — sabotar o corpo de
`kof_plat_writev` para `ret` faz o programa **não imprimir nada** nos dois
alvos (a costura é load-bearing, não decorativa). **Próximas fatias:** runtime
x86_64 (os sítios `call`/libc dele), spawn/futex
(`kof_plat_thread`/`kof_plat_sync`) e sockets de rede (`kof_plat_net_*`);
`NATIVE003` fica reservado aos perfis bare (B-1+).

**Fatia 2 (LANDADA 22/09, lane `baremetal` 9092):** o runtime x86_64 cruza a
mesma costura — fatia nova `RuntimePlat` implementa `kof_plat_write`,
`kof_plat_writev`, `kof_plat_exit`, `kof_plat_exit_group` (Linux = syscall crua
1/20/60/231) e os sítios são roteados: `kof_print`/`kof_print_string` (write),
`kof_panic`/panic do `kof_throw_string`/`kof_process_exit`/fatal JSN004 (exit) e
o epílogo do `_start` (`exit_group`). Prova: teste de sabotagem no host x86
(`as`/`ld` dinâmico, mesmas flags da produção — sabotar `kof_plat_write` faz a
saída sumir), `NativeE2ETest` 67/67, `KofConcurrency2Test` 48/48, `JsonE2ETest`
18/18, `JsonCompleteE2ETest` 10/10, `KofDbE2ETest` 27/27 (4 skips),
`NullSafetyE2ETest` 14/14, `ProcessResultContentE2ETest` 4/4,
`NativeNullablePrimitiveContractE2ETest` 42/42, `ArtifactSizeTest` 6/6 com o
baseline x86 re-medido (39.232→39.304 B, 84→88 syms). **Próximas fatias:**
 superfície de ambiente x86_64 (time/sleep/mono, entropia, gettid), spawn/futex
 (`kof_plat_thread`/`kof_plat_sync`), sockets de rede (`kof_plat_net_*`) e
 então B-1.
 **Depende de:** nada. **Lacuna:** `NATIVE003` (proposta).

**Fatia 3a (LANDADA 22/09, lane `baremetal` 9092):** a **superfície de ambiente**
x86_64 cruza a costura — `RuntimePlat` ganha `kof_plat_time`/`time_mono`
(clock_gettime 228, `rdi`=ts), `kof_plat_sleep` (nanosleep 35), `kof_plat_random`
(getrandom 318) e `kof_plat_thread_id` (gettid 186); os sítios são roteados:
`kof_now`/`kof_time_sleep`, o timestamp do log, `kof_obs_mono_nanos`/
`kof_obs_epoch_micros`, `kof_random_bool`/`kof_random_double`,
`kof_sec_random_hex`/`_int`/`kof_sec_random_bytes`, o relógio da migração ORM
(`RuntimeOrm1`/`RuntimeOrmMysqlDdl`) e o gettid do `_start`. **Bug pego na caça
(Q4), não enviado:** a 1ª impl trocou os args da syscall crua `clock_gettime`
(no x86_64 `rdi`=clockid, `rsi`=ts — não o inverso); `NativeLogE2ETest`,
`KofSecurityTest.jwtNative` e `KofTimeE2ETest` o denunciaram (JWT lido como
"expirado", log datado de 1970) e o fix está provado. Bônus: o timestamp do log
lia `tv_usec` como se fosse nsec (ms sempre 0) — o **timespec** real da costura
fecha esse bug latente. Prova: 371/0F (`KofTimeE2ETest` 42, `KofLogE2ETest` 11,
`NativeLogE2ETest` 7, `KofObservabilityTest` 12, `KofRandomTest` 15,
`KofRngTest` 11, `KofUuidTest` 14, `KofSecurityTest` 41, `KofSecurityG9Test` 3,
`KofConcurrency2Test` 48, `NativeE2ETest` 67, `KofOrmE2ETest` 63/18 skips,
`KofDbE2ETest` 27/4 skips, `PlatformSeamSabotageTest` 4/4), baseline x86
re-medido (39.304→39.512 B, 88→93 syms). **Próximas fatias:** spawn/futex x86
(`kof_plat_thread`/`kof_plat_sync`), sockets de rede (`kof_plat_net_*`) e
então B-1.
**Depende de:** nada. **Lacuna:** `NATIVE003` (proposta).

**Fatia 3b-i (LANDADA 22/09, lane `baremetal` 9092):** os sítios de **futex** do
x86_64 cruzam a costura — `kof_plat_sync` (futex 202) entra no `RuntimePlat` e os
19 sítios `SYS_futex` de `RuntimeScheduler`/`RuntimeChannel`/`RuntimeMemory` são
roteados para ele (o lock de alocação espera/acorda pela costura, então todo
binário nativo a exercita). Prova: `KofConcurrency2Test` 48/48, `SpawnE2ETest`
10/10, `SpawnAwaitBlockE2ETest` 5/5, `ProcessSpawnE2ETest` 4/4, `NativeE2ETest`
67/67, `PlatformSeamSabotageTest` 4/4; baseline x86 re-medido (39.512→39.544 B,
93→94 syms). **Próximas:** `kof_plat_sync` no seam riscv + `kof_plat_thread`
(create) nos dois ISAs, depois sockets de rede (`kof_plat_net_*`) e B-1.

**Fatia 3b-ii (LANDADA 22/09, lane `baremetal` 9092):** sincronização e criação
de thread cruzam a costura. **riscv64:** `kof_plat_sync` (futex 98) entra no seam
e os 3 sítios `SYS_futex` de `NativeRiscvSpawn` são roteados (WAKE, WAIT do await
e WAIT do join) — o aarch64 herda pelo tradutor. **x86_64:**
`kof_plat_thread_create` entra no `RuntimePlat` como `jmp pthread_create` (Linux+
libc) e os 2 sítios (`RuntimeConcurrency`, `RuntimeScheduler`) são roteados.
**Adiamento CADUCADO — risco era conservador (LANDADO 22/09, sessão 9092):** o
`clone` (220) riscv cruza a costura com segurança: no riscv o `call` **não**
empilha (o endereço de retorno é registrador), então o filho volta do seam
direto ao `kof_spawn_result`, que troca o `sp` *antes* de chamar o trampoline —
o frame do pai nunca é tocado. `NativeRiscvSpawn` passa a setar os args e
`call kof_plat_thread_create` (seam no `NativeRiscvAsmRt0`, `li a7,220`; aarch64
herda pelo tradutor); nenhum `li a7, 220` cru sobra no spawn. Guarda:
`PlatformSeamSabotageTest#riscvThreadCreateRoutesThroughSeam`. Prova:
`PlatformSeamSabotageTest` 5/5, `NativeRiscv64E2ETest` 54/1 skip,
`NativeAarch64E2ETest` 53/1 skip, `KofConcurrency2Test` 48, `SpawnE2ETest` 10,
`ArtifactSizeTest` 6, slice-registry 7 → 182/0F/2 skip (baselines riscv/aarch
inalterados em 136.824 B/45 syms e 202.168 B/45 syms; `kof_plat_sync` é podado do
hello). **Próxima:** sockets de rede (`kof_plat_net_*`), depois B-1.

**Fatia 4a (LANDADA 22/09, lane `baremetal` 9092):** a superfície de **rede** do
x86_64 cruza a costura — `RuntimePlat` ganha `kof_plat_read`/`kof_plat_close`
(0/3) e `kof_plat_net_socket`/`connect`/`bind`/`listen`/`accept`/`send` (41/42/
49/50/43/44); roteados: `kof_net_socket`/`bind`/`listen`/`accept`/`read`/`write`/
`close` (`RuntimeNet`) e o connect MySQL (`RuntimeDb3`). **Correção de poda:** a
fatia é *classe+método*, então o `emitPlatSeam` único linkava **todos** os
símbolos da costura em qualquer binário; o `RuntimePlat` foi dividido por família
(write/time/random/thread-id/sync/io/net) e o hello **encolhe** — 39.544→39.432 B,
94→91 syms. Prova: `KofNetTest` 4/4, `KofHttpE2ETest` 8/8,
`KofHttpNativeCircuitE2ETest` 2/2, `KofHttpNativeRetryE2ETest` 3/3,
`KofHttpNativeTimeoutE2ETest` 3/3, `KofHttpResilienceE2ETest` 3/3,
`KofHttpServerTest` 8/8, `KofHttpNativeResilienceCrossTest` 4/4,
`KofDbE2ETest` 27/4 skips, `NativeE2ETest` 67/67,
`NativeRuntimeSliceRegistryTest` 7/7, `RuntimeSourceLoaderTest` 6/6,
`PlatformSeamSabotageTest` 4/4, `ArtifactSizeTest` 6/6, `KofConcurrency2Test`
48/48, `KofTimeE2ETest` 42/42. **Próxima:** a costura de rede riscv64
(`NativeRiscvHttpCore` socket 198 / connect 203 + os sítios do http-support),
depois B-1.

**Fatia 4b (LANDADA 22/09, lane `baremetal` 9092):** a superfície de **rede**
riscv64 entra na costura — `kof_plat_read` (63), `kof_plat_close` (57),
`kof_plat_net_socket` (198) e `kof_plat_net_connect` (203) no
`NativeRiscvAsmRt0`, e os 9 sítios do `NativeRiscvHttpCore` roteados (socket,
connect, reúso de `write`, `read`, 5×`close`) — todos em funções que já salvam o
`ra`, então a guarda de `ra` segue verde; o aarch64 herda pelo tradutor. Só os
membros referenciados foram adicionados: `bind`/`listen`/`accept`/`send` ainda
não têm chamador riscv, então suas entradas na costura ficam **deliberadamente
ausentes** (entram quando existir caminho de servidor riscv — nunca silencioso).
Prova: `NativeRiscv64E2ETest` 54/1 skip, `NativeAarch64E2ETest` 53/1 skip,
`KofHttpNativeResilienceCrossTest` 4/4, `KofHttpNativeTimeoutE2ETest` 3/3,
`KofHttpNativeCircuitE2ETest` 2/2, `KofHttpNativeRetryE2ETest` 3/3,
`KofHttpE2ETest` 8/8, `KofNetTest` 4/4, `CrossRuntimePortsE2ETest` 2/2,
`PlatformSeamSabotageTest` 4/4, `NativeRuntimeSliceRegistryTest` 7/7,
`ArtifactSizeTest` 6/6 (baselines riscv/aarch inalterados: os novos membros da
costura são podados do hello). **Próxima:** B-1 (perfil de link freestanding).

### B-1 — Perfil de link freestanding · **depende de B-0**
`native --profile freestanding`: sem `-lc`/`-dynamic-linker`, `_start`/`_end`
próprios, linker script (heap e stack configuráveis), sem libc. No x86_64 remove
o `-dynamic-linker … -lc -lm` fixo (`NativeAssembler.java:36-49`); o caminho
riscv/aarch já é estático.
**Aceitação:** o parser ELF/`readelf` reporta **sem `PT_INTERP`**, sem `DT_NEEDED`;
o binário ainda imprime um hello Kof sob qemu-user; uma sabotagem (re-adicionar
`-lc`) é pega pela asserção de `DT_NEEDED`.
**Pré-passo de B-1 (LANDADO 22/09, lane `baremetal` 9092):** o
`kof_plat_thread_create` saiu da fatia `sync` para fatia própria — um programa que
toca o futex do lock de alocação (`kof_plat_sync`) não arrasta mais
`pthread_create` para o link, que é exatamente o que o perfil freestanding precisa
(e um ganho de poda para todo binário que só usa sync). Prova:
`NativeRuntimeSliceRegistryTest` 7/7, `ArtifactSizeTest` 6/6 (hello inalterado:
39.432 B/91 syms), `KofConcurrency2Test` 48/48, `SpawnE2ETest` 10/10,
`PlatformSeamSabotageTest` 4/4.

**B-1a (LANDADA 22/09, lane `baremetal` 9092): perfil de link freestanding
ponta a ponta.** Novo `NativeProfile {HOST, FREESTANDING}`
(`nat/NativeProfile.java`) passado por `CompilerDriver.nativeProfile` →
`CompilerDriverState.compile(src, out, target, profile)` →
`CompilerPipeline.selectBackend` → `NativeBackend.profile(p)` →
`NativeAssembler.assemble(..., freestanding)`. Em freestanding o x86_64 liga
`ld -o bin obj --unresolved-symbols=ignore-all` (sem `-dynamic-linker`, sem
`-lc`/libs). Capacidades que precisam de libc por uso (db/orm/mysql/concurrency/
pow/ffi) são **recusadas** em compile-time com o diagnóstico `NATIVE003` — nunca
um fallback dinâmico silencioso. **Por que `--unresolved-symbols=ignore-all`:**
as fatias do runtime x86 são grossas e carregam chamadas libc de funções *não
alcançadas* no mesmo objeto (`snprintf`/`strtod` do dtoa, `pthread_*`, `usleep`);
no host elas resolvem pela libc, aqui ficam sem resolução e só são fatais se o
programa alcançar o caminho libc — coberto pelas capacidades recusadas. Remover
as refs na origem (seções por função + `gc-sections`) é o próximo passo **B-1b**.
> **Face (i) LANDED (22/09, lane 9093 — reivindicado após coordenação com a 9092):** o caminho de panic não alcança mais o dispatcher genérico — o `kof_panic` imprime via `kof_println_string` (toda mensagem de panic é um `.asciz` estático). Medido: programas hello/plain/numéricos sem float não carregam mais `snprintf`/`strtod` (`nm -u` do binário linkado com gc: presente no código antigo, ausente com o fix). Prova: novo `FreestandingLinkE2ETest.freestandingHelloCarriesNoLibcFormatRefs` (RED no código antigo) + classe 4/4 + bateria nativa 101/0F (`NativeE2ETest` 67, `NullSafety` 14, `ArrayBounds` 10, catches/prints 10). Face (ii) — dtoa libc-free pela costura — ainda ABERTA, dona lane 9092.

**B-1b (LANDADA 22/09, lane `baremetal` 9092): o link freestanding x86_64 fica
livre de libc e fecha SEM `--unresolved-symbols=ignore-all`.** Com a face (i)
landada, as funções que carregavam libc eram inalcançáveis no objeto; o caminho
freestanding agora passa o texto do runtime por
`NativeCrossSections.sectionizeTextFunctions` (o passe do cross, lição §445) e
linka `ld -o bin obj --gc-sections -e _start`. O `nm -u` do resultado não mostra
`snprintf`/`strtod`/`pthread_*`/`usleep`. **Aceitação cumprida:** sem
`PT_INTERP`, sem `DT_NEEDED`, o binário ainda imprime o valor do oráculo JVM, o
controle `HOST` mantém ambos, e freestanding+`spawn` é recusado com `NATIVE003`
— tudo sem nenhum escape de símbolo não resolvido. Prova:
`FreestandingLinkE2ETest` 4/4 + bateria 116/0F (`LinkByUseTest` 3,
`PlatformSeamSabotageTest` 5, `NativeRuntimeSliceRegistryTest` 7,
`ArtifactSizeTest` 6, `NativeE2ETest` 67, `NullSafetyE2ETest` 14,
`ArrayBoundsSafetyE2ETest` 10). **Recusa de float-print (LANDADA 22/09, lane
9092):** um `println(Double)`/`Float` freestanding agora falha com o diagnóstico
nomeado `NATIVE003` (o link freestanding converte refs libc remanescentes —
`snprintf`/`strtod` do dtoa — na recusa codificada) em vez de um "undefined
reference" cru do `ld`; prova `FreestandingLinkE2ETest` 5/5 (novo
`freestandingRefusesFloatPrintWithDiagnostic`). **Superfície CLI (`--profile`) LANDADA 23/09 (lane `baremetal` 9092):** `kof build <dir|file.kf> --target native --profile host|freestanding` faz o parse da flag, valida cedo (só `--target native`; um valor fora de `host|freestanding` é recusado — R6, nunca no-op silencioso) e a encadeia pelo novo `CompilerDriver.setNativeProfile` público até a plumbing `NativeProfile` existente; `--profile freestanding` reproduz o link libc-free (sem `PT_INTERP`). Prova: `CmdBuildProfileTest` 4/4 (unidade parse/validate + E2E freestanding sem `PT_INTERP` imprimindo `42` + `--profile host` ainda builda). **Restante de B-1:** o linker script com heap/stack configuráveis e `_end`.

**B-1c — conversão decimal (dtoa) libc-free para `Float`/`Double` (aberta
23/09, decisão da mantenedora: "precisamos dele baremetal").** A mantenedora
ordenou o caminho *completo* (não o desbloqueio incremental): o bare-metal deve
imprimir `Bool`/`Troolean`/`Int`/`Long`/`String` **e** `Float`/`Double` com
paridade JVM, removendo a recusa `NATIVE003` de float-print. **Medido 23/09:**
`Bool` (`true`/`false`) e `Int`/`String` já imprimem no bare-metal (strings
estáticas); `Troolean` (`true`/`false`/`null`) só está bloqueado porque
`kof_box_to_string` referencia estaticamente `kof_double_to_string`/
`kof_float_to_string` (tags 4/5) — `gc-sections` não poda referência de *código*,
então o box printer mantém o dtoa vivo; um programa multi-função expôs um segundo
bug (corrigido: o passe de sectionize movia as funções do programa e quebrava as
expressões de range do DWARF). `Float`/`Double` exigem substituir o
`snprintf("%.*e")`/`strtod` do `RuntimeDtoa`.

- **Decisão (mantenedora, 23/09): espelhar o JDK EXATAMENTE.** Um dtoa
  matematicamente "mais curto" (Ryū/Steele-White) **não basta**: medido no
  Temurin **JDK 25**, `Double.parseDouble("5e-324")` faz round-trip para bits
  `0x1`, mas `Double.toString(0x1)` = `4.9E-324` (2 dígitos) — o comportamento
  de compatibilidade especificado do JDK *não* é o mais curto. O `5.0E-324` do
  host atual já é o mais curto; logo um conversor só-shortest não corrigiria o
  §448 nem casaria com o oráculo dos testes. Portanto o B-1c porta **o algoritmo
  do JDK (`DoubleToDecimal`, Schubfach) + a formatação do `Double.toString`**
  libc-free, reproduzindo a saída exata (incluindo o comportamento subnormal).
- **Restrição de paridade (o ponto difícil).** O host x86_64 escolhe a precisão
  mais curta iterando `snprintf("%.*e", p)` + `strtod` (glibc, round-half-even) e
  fica com o primeiro `p` que faz round-trip. O conversor bare-metal PRECISA
  reproduzir a escolha de dígitos do JDK (mais curto **e** mais próximo, com as
  fronteiras exatas do JDK) — senão os testes de paridade divergem.
- **Resultado do recon (§448, medido 23/09):** a escolha de dígitos do loop do
  host já está **errada vs o JVM** nos menores subnormais (`println(5E-324)`:
  JVM `4.9E-324`, Nativo `5.0E-324`; `println(1E-323)`: JVM `9.9E-324`, Nativo
  `1.0E-323`) — o loop da glibc pega o *mais curto*, o JDK pega o *mais próximo
  entre os mais curtos*. Logo o alvo do B-1c é **paridade JVM (mais curto E mais
  próximo)**, o que também corrige o bug latente host/cross `known-bugs.md §448`.
- **Recon primeiro (barato, sem asm):** fixar o algoritmo e *provar* a paridade
  em Java contra o oráculo JVM (e contra a saída glibc-host) num corpus grande
  (bits aleatórios + bordas: subnormais, `±0.0`, `1e308`, `5e-324`, os limiares
  JDK `1e-3`/`1e7`, empates exatos) **antes** de escrever qualquer assembly.
- **Fatias (cada uma com prova):** (1) recon — FEITA 23/09 (§448 + a decisão de
  espelhar o JDK); (2) portar o core do `DoubleToDecimal` do JDK (Schubfach:
  decode, multiplicação 128-bit + tabela de potências de 10, laço
  shortest/closest) para o runtime x86, com teste de corpus Nativo
  `== Double.toString` (bordas incl. subnormais) — a prova decisiva; (3) adaptar
  o `kof_dtoa_format`/formatação às regras do `Double.toString` (limiares
  `1e-3`/`1e7`, sempre um dígito fracionário); (4) ligar em
  `kof_double_to_string`/`kof_float_to_string`, apagar a recusa `NATIVE003` e
  provar `FreestandingLinkE2ETest` verde com corpus `println(Double)` == oráculo
  JDK e sem `snprintf`/`strtod` no `nm -u`.
- **Nota de escopo:** o mesmo débito do `kof_dtoa_format` no cross (riscv/aarch
  usam `snprintf`/`strtod` da libc) fica fora desta face; a unidade x86 é a
  referência.

- **LANDADO (x86_64, lane `baremetal` 9092, 23/09):** fatias (2)–(4) fechadas.
  `RuntimeDtoaSchubfach` (3 classes, todas <500) porta o `DoubleToDecimal` **e** o
  `FloatToDecimal` do JDK (Schubfach, H=17/9, tabelas `g`/`pow10` == `MathUtils.g`
  por fórmula fechada) para asm x86 libc-free; o `RuntimeDtoa` (snprintf/strtod)
  foi **deletado** — o runtime x86 não carrega nenhuma ref de formatação libc. A
  recusa NATIVE003 de float-print sumiu. **Prova:** `DtoaParityE2ETest` 3/3 — o
  corpus Double, o corpus Float e um corpus escalares+box imprimindo
  Bool/Int/Long/String/**Troolean** e Double/Float boxados via `Object`, tudo
  freestanding, sem `snprintf`/`strtod` no `nm -u`, byte-a-byte == oráculo JVM
  medido; `FreestandingLinkE2ETest` 6/6; `ConformanceMatrixTest` 14/0F; suíte do
  compilador 3111/2F, sendo os 2 red o WIP da lane UEFI irmã, não esta face.
  §448 x86 `Double`+`Float` **FECHADOS**; residual = dtoa **cross** (rv/aa,
  `NativeRiscvAsmRtB45`), fora desta face pela nota de escopo abaixo.

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

### B-6 — Anéis de privilégio x86_64: kernel ring0 + domínios ring1 · **depende de B-1 (+ caminho de boot B-2/B-3)**
Entrar em long mode com **GDT** própria do Kof (código/dados ring0 + ring1, TSS)
e **IDT**; o runtime executa em **CPL0**. Uma primitiva mínima e documentada de
transição deixa uma função Kof rodar em **CPL1** e retornar (`iretq`
inter-privilégio + `rsp0` do TSS para o trap de volta ao ring0), de modo que
instruções privilegiadas (`cli`/`hlt`/`lgdt`) são **recusadas pela CPU** no
domínio ring1 — a prova falseável de que o nível é real, não um rótulo.
**Aceitação:** sob `qemu-system-x86_64`, (a) o boot chega a CPL0 e imprime;
(b) uma entrada controlada executa função Kof em CPL1 e retorna com o estado
intacto; (c) instrução privilegiada tentada no domínio ring1 gera `#GP` (pega
pelo handler ring0 e reportada, nunca um travamento silencioso); (d) sabotagem:
remover o descritor ring1 da GDT faz a entrada CPL1 falhar — provando que o
nível é imposto, não decorativo.
**Depende de:** B-1 + um caminho de boot x86 (B-2 ou B-3). **Classificação:** H (alta).
**Superfície:** a API Kof para *mirar* um domínio ring1 é decisão **rule 6**
(mantenedora); esta face pousa a maquinaria habilitadora primeiro.

## 5. Dependências honestas, bloqueios e classificação

| Face | Depende de | Custo | Lacuna em falha |
|------|-----------|-------|-----------------|
| B-0 costura HAL | — | M | `NATIVE003` |
| B-1 freestanding | B-0 | M | `NATIVE003` / erro de link |
| B-2 UEFI | B-1 | H | `NATIVE003` |
| B-3 BIOS legado | B-1 (+entrada 16-bit) | H | `NATIVE003` |
| B-4 MCU (32 bits) | B-0, B-1, G-4/G-5 | R/H | `NATIVE002` (codegen) / `NATIVE003` |
| B-5 corpos de plataforma | por face | M | `CONC003` para concorrência |
| B-6 anéis de privilégio (x86_64) | B-1 + B-2/B-3 | H | `NATIVE003` / `#GP` tratado |

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
- **Não** é um microkernel/hypervisor: os anéis do B-6 são uma **costura mínima
  de privilégio** (kernel em ring0, um domínio ring1), não escalonador, IPC ou
  camada de VM.
- **Não** está mais sem agendamento: **promovido 22/09/2026** (ordem da
  mantenedora, `D-BAREMETAL-BOOT`) — a execução segue o §7; o R12 é sobreposto
  para esta frente.

## 7. Como terminar (ordem, uma vez autorizada)

1. **B-0** (costura HAL) — seguro, reversível, suíte verde; o princípio "nativo fala
   com barebones" vira real. Commit com a suíte cross completa verde.
2. **B-1** (ELF freestanding) — primeiro artefato quase-bootável, prova qemu-user.
3. **B-2** (UEFI/OVMF) **ou B-3** (BIOS/MBR) — o que a mantenedora priorizar; ambos
   são H e independentes entre si.
4. **B-6** (ring0/ring1, x86_64) — sobre o caminho de boot escolhido em 3 (escopo
   ordenado do `D-BAREMETAL-BOOT`; a superfície Kof dos anéis é decidida com a
   mantenedora antes de qualquer sintaxe/API pousar — rule 6 + Lei da Simplicidade).
5. **G-4/G-5** (coletor) — pré-requisito do **B-4** (MCU).
6. **B-4** (MCU 32 bits) — o maior, classe-resquisa.
7. **B-5** — os corpos de plataforma, um por face conforme cada uma pousa.
