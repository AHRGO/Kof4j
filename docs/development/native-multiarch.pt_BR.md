[English](native-multiarch.md) | [Português](native-multiarch.pt_BR.md)

# Kof Native — Multi-Arch (RISC-V 64 e ARM64/AArch64)

> **🔄 RE-AUDITORIA 12/09 (medido sob qemu REAL neste host — NÃO memória):**
> os cabeçalhos 03/09 abaixo estão DESATUALIZADOS e este bloco é a fonte do
> estado REAL (regra AGENTS "auditar doc contra o código/testes, não contra a
> memória"; estado-4 "doc contradiz o código" corrigido). As 13 faces "core"
> de 03/09 são hoje ~30 testes cross (`NativeRiscv64E2ETest` 39/39 +
> `NativeAarch64E2ETest` 39/39, **executados**, 0 skip neste host). **FECHADO
> e EXECUTANDO sob qemu (byte-idêntico ao JVM medido), para além do "core":**
> Map/Set, `println(<coleção>)` (§107 — x86 `f3b3821c` + cross B39 12/09),
> higher-order `map/filter/reduce` (probe 12/09: `[2,4,6]`/`[2,3]` idêntico
> JVM), HTTP client (`riscv64HttpGetPostStatus`), `kof.net`, JSON
> encode/decode int/list/string, spawn/await (`clone`+`futex`), `time` ISO
> (add/diff), `math` Double (MATH001) + **`math.pow` S1b.2 (decisão 7a 13/09:
> x86 via libm `pow@PLT` + `-lm`; riscv/aarch recusam com MATH001 — link
> cross estático sem libc, decisão de arquitetura regra 6; `KofMath.supportedOn`),
> `random`, `uuid`, multi-dim array
> (§113), busca String UTF-16 (§43/§102/§111). **ABERTO — recusa HONESTA em
> compile-time (NUNCA binário mudo; regra R6 — o stub "exit 0 sem efeito" de
> 03/09 já NÃO descreve mais o estado, ops desconhecidos dão código de gap):**
> `kof.db` → **DB001** (`KofDbE2ETest` prova `assertFalse(success)` + diags
> DB001 nos 6 alvos), `kof.security` crypto-heavy → **SECN000**, os 6
> construtos de concorrência de mais alta ordem (supervisor/`selectAny`
> multi/cancel cross …) → **CONC001** (`spawn`/`await`/`sleep`/`interval`
> VERDES no cross — o gate #91 não os toca), UI (`kof.ui`) **sem port cross
> algum** (nenhum teste riscv/aarch), `json.decode<List<Record>>` → **JSN004**
> (asm puro não tem reflection p/ materializar record). **Consequência honesta
> HOJE:** programa com coleção/HTTP/net/JSON-escalar/spawn/time/math **roda de
> verdade** no riscv/aarch (a frase 03/09 "não executa a lógica — sai 0 sem
> efeito" está SUPERADA); o que ainda não roda (DB/segurança/UI/record-decode)
> é diagnosticado com código de gap em compilação, não silenciosamente.
> **Gap real `NATIVE002` que sobra:** (1) GC mark-sweep cross (riscv é
> bump-pointer sem coletor — vazamento em heap longo, não-crash) —
> **G-0 bloco-header + G-1 free-list/memstats + G-2 gc-list/dump + G-3 mark
> conservative FEITOS 15/09** (ver a decomposição abaixo); G-4..G-5 pendentes,
> o coletor (G-4) é quem de fato recupera; (2) as
> recusas DB001/SECN000/CONC001/JSN004 acima; (3) FP-coleção no cross
> (FLT001 em compilação §107); (4) `backend-parity.md` colunas por-arch
> ainda por separar; (5) CI cross não existe (toolchain host-dependente) —
> **face (5) FECHADA 12/09**: job `cross-native` em `.github/workflows/ci.yml`
> instala `binutils-riscv64/aarch64-linux-gnu` + `qemu-user-static` e roda
> `NativeRiscv64E2ETest,NativeAarch64E2ETest` (executam sob qemu, não skipam —
> o job EXISTE para provar; nomes dos binários batem com `NativeArchEmitter:151
> -282`; local: riscv 39/39 + aarch 39/39 verdes neste host com qemu).
> **➕ Link dinâmico SOB DEMANDA FEITO 15/09** (diretriz "liga dinamicamente"):
> `NativeCrossLink` liga libc (`-dynamic-linker … -lc`) só quando o runtime
> podado chama libc; os 84 binários cross atuais seguem estáticos/portáveis.
> Ver §2.3; falta o 1º consumidor de produção (port do `RuntimeDtoa`, FLT001).
> Este doc continua em `development/` (NATIVE002 não fecha enquanto restam
> (1)–(5)); quando (1)–(5) zerarem → mover para `docs/`.
>
> **🪜 DECOMPOSIÇÃO DA FACE (1) — GC mark-sweep cross (12/09, fila para
> execução por degrau — cada degrau cabe numa sessão e tem prova própria):**
> o riscv é bump puro (`amoadd.d` em `kof_alloc_ptr`, sem flags/mark/free-list);
> o port NÃO é copiar o RuntimeGc x86 — o scan conservative exige stack-walk
> riscv + roots no intervalo de seções. **⚠️ CORRIGIDO 12/09 (lido o código,
> não memória) — a decomposição original estava ERRADA no G-1:** o riscv não
> tem NENHUM header de bloco: `kof_alloc` riscv (`NativeRiscvAsmRt0.java:17-22`)
> retorna o bump cru alinhado a 16 e os usuários escrevem o header do OBJETO
> (typeId @0, vtable @8, …) no OFFSET 0 do ponteiro retornado (ex.:
> `string_from_literal`: `sw t0, 0(s3)`), enquanto no x86 o GC vive num bloco
> de 32B ANTERIOR ao ponteiro retornado (`RuntimeMemory.java:145-150` — size@0,
> free-next@8, gc-next@16, flags@24; `kof_free:205` lê `-32(%rdi)`). Portar a
> free-list sem o bloco-header = o coletor ler o typeId como tamanho →
> corrupção. Por isso entra o **G-0** na frente. Degraus na ordem:
> **G-0 bloco-header riscv (FEITO 12/09, sessão dev):** `kof_alloc` riscv reserva
> 32B ANTES do ponteiro (total = 32+align16, retorno base+32; preenchimento
> size/free_next/gc_next/flags) + guard OOM honesto (`_kof_heap_end`, panic
> `out of memory` exit 1 — R6: o bump NÃO tinha bounds-check e o header
> triplica o consumo/bloco, então o estouro ficou mais provável). Prova:
> suíte riscv 40/40 + aarch 40/40 sob qemu (inclui teste de pressão
> `riscvHeapExhaustionPanicsHonest`/`aarch64HeapExhaustionPanicsHonest`,
> sabotagem-sem-guard = zero output FAIL) + GC x86 3/3 + Artifact 6/6 +
> ratchet ≤500 OK (Rt0 com 500 exatas; prosa de design vive aqui).
> **G-1 free-list riscv (FEITO 15/09, sessão dev):** port da lista de blocos
> livres do x86 SOBRE o layout do G-0 (header 32B:
> size/flags/gc-list/free-next). Fatia nova `NativeRiscvAsmRtB42` com
> `kof_alloc` (SAIU de `Rt0`, que caiu 500→478 — a folga do ratchet),
> `kof_free` e `kof_memstats`; o alloc agora faz first-fit na free list (LIFO,
> re-enfileira no alloc, `flags=0`) e só usa o bump no miss; um spin-lock
> `amoswap.w` protege a free list + contadores (main × workers do spawn).
> `kof_free` é port 1:1 do `RuntimeMemory.emitFree` (flags bit1 = na free
> list). `kof_memstats` imprime `allocs`/`frees`/`live bytes` — a alavanca de
> observação dos degraus seguintes. Prova (qemu riscv64 **e** aarch64,
> toolchain presente, testes não skipam): `NativeRiscvGcFreeListTest` monta o
> runtime de PRODUÇÃO (`RiscvSlices.renderRuntime()`) com um `_start` cru que
> faz alloc(64)→free→alloc(64) e exige p2==p1 (reuso) + `allocs: 2`/`frees: 1`
> — 2/2 verde; `NativeRiscvRuntimeSliceRegistryTest` 8/8 (concat ainda
> byte-idêntica); suítes cross riscv 44 + aarch 44, só o `CastSaturation`
> pré-existente vermelho; `ArtifactSizeTest` 6/6 (o lock/head internos são
> `.L`-locais e NÃO incham o `.symtab` do hello — foi a única regressão real,
> achada e corrigida aqui); `KofGcE2ETest` 3/3 x86 intocado; `check_500` OK.
> ⚠️ **Correção da prova do G-1 (15/09, doc-vs-realidade — a prova do plano
> não tinha caminho alcançável):** a proposta de 12/09 dizia "ligar o free nos
> nós do log (RtB0, espelhando `RuntimeLog2:98`)" e "ciclo alloc/free/alloc
> reusa o slot". Contar no fonte refuta as DUAS metades: (a) o nó de log riscv
> (`NativeRiscvAsmRtB0:242`) escreve label+msg **direto por `write()` e nunca
> aloca**, e a observabilidade usa slots fixos de `.bss` — logo não há nó para
> liberar; (b) no compilador inteiro **nenhuma fatia riscv chama `kof_free`**
> (o x86 tem 4 callers reais: `RuntimeChannel:132`, `RuntimeLog2:98`,
> `RuntimeObservability1:426`/`2:247`) e **não existe API Kof de free/GC**, então
> um E2E Kof não consegue exercitá-lo. Conclusão: a free-list riscv é a
> infraestrutura correta de paridade x86, mas no riscv ela fica **latente até o
> G-4** (o sweep a alimenta) — a prova do G-1 é o harness asm cru, não um
> programa Kof. Ligar o free nos 57 sítios de alloc existentes é **assunto do
> G-4** (eles precisam liberar objetos mortos, que só o coletor identifica),
> NÃO do G-1. Isso também significa que o vazamento do `.bss` de ~260KB é
> fechado pelo G-4, não pelo G-1.
> **G-2 header flags/mark bits + lista GC (FEITO 15/09, sessão dev):** cada
> bloco RESERVADO do bump agora entra na gc-list global
> (`.Lkof_gc_head`, LIFO, `gc_next`@16, flags=0) na mesma fatia
> `NativeRiscvAsmRtB42`; um free+realloc NÃO re-entra (o bloco nunca saiu da
> lista). Novo `kof_gc_dump` imprime uma linha `gc <size> <flags>` por bloco —
> o "dump `KOF_GC_DEBUG`" do plano (asm puro não tem gatilho por env, então a
> alavanca é uma chamada explícita; honesto e testável). Prova (qemu riscv64
> **e** aarch64, nunca skip): `NativeRiscvGcListTest` 4/4 — alloc(16/32/64) ⇒
> dump `gc 96 0`/`gc 64 0`/`gc 48 0` (total = align16+32, LIFO); free+realloc ⇒
> um único `gc 96 0`; sabotagem (remover o link) = 4/4 vermelho com saída
> vazia. Slice-registry 8/8 (concat ainda byte-idêntica), cross riscv 44 +
> aarch 44 (só o `CastSaturation` pré-existente vermelho), `ArtifactSizeTest`
> 6/6, `KofGcE2ETest` 3/3 x86 intocado, `check_500` OK.
> **G-3 mark conservative riscv (FEITO 15/09, sessão dev):** port do
> `kof_gc_mark`/`kof_gc_try_mark`/`kof_gc_mark_transitive` na fatia nova
> `NativeRiscvAsmRtB43` sobre o header de 32B do G-0 e a gc-list do G-2.
> Raízes de pilha = `sp..s11` (fp riscv; fallback 4KB como o x86) com `s0-s11`
> derramados para que ponteiros em registrador apareçam; raízes estáticas = o
> intervalo entre os rótulos LOCAIS riscv-only `.Lkof_heap_root_start`/
> `.Lkof_heap_root_end` emitidos pelo `NativeArchEmitter` em volta do `.data`
> do programa (sentinel `.quad 0` na abertura). **Correção ao plano:** o
> `kof_heap_root_end` x86 (fila bugfix S-5) NÃO foi necessário — o riscv agora
> emite os próprios marcadores `.L`-locais (fora do `.symtab`, a lição do G-1
> no ArtifactSizeTest) e o intervalo EXCLUI de propósito a arena do bump
> (`_kof_heap`, em `.bss`), ao contrário do x86 que varre até `_end` porque lá
> o heap é mmap. Logo o G-3 avançou SEM o pré-requisito compartilhado; os
> rótulos são declarados program-side em `RiscvSlices.programSideLocals()`.
> Prova (qemu riscv64 **e** aarch64, nunca skip): `NativeRiscvGcMarkTest` 2/2 —
> `_start` aloca A(raiz estática)→B(pilha)→C(inalcançável)→D(via campo0 de A),
> chama `kof_gc_mark`, despeja: `gc 96 1`/`gc 96 0`/`gc 96 1`/`gc 96 1` (D/A
> alcançáveis, C não); sabotagem (remover a varredura transitiva de campos) =
> 2/2 vermelho com D=0. Harnesses G-1/G-2 e slice-registry 8/8 ainda verdes;
> `ArtifactSizeTest` 6/6 (rótulos `.L`-locais, sem inchar o symtab);
> `KofGcE2ETest` 3/3 x86 intocado; `check_500` OK.
> **G-4 sweep + collect no alloc** — free-list recebe mortos; `kof_gc_collect`
> portado (tick 4096 como o x86); prova: teste de VASAMENTO que hoje é
> impossível (loop de alloc que estouraria o bump de 260KB roda e a memória
> não cresce monotonicamente — medir via memstats do G-1).
> **G-5 aarch64** — herda tudo via tradutor (as diretivas/labels riscv passam
> ilesas — mesmo caminho da poda S-4; `amoadd.d`→`ldadd`, `amoswap.w`→`swpal` já
> traduzidos, `NativeAarch64Translator.java:299`); gate: suíte aarch sob qemu +
> o teste de vazamento G-4 também no aarch. **O G-1 JÁ provou a herança da
> free-list** (`NativeRiscvGcFreeListTest.freeListReusesSlotAndMemstatsCountsAarch64`).
> Cada degrau: commit com suíte cross completa verde + DOING.md na linha.
> G-0/G-1/G-2 adiantam sem root_end; **o G-3 também adiantou** (emite os
> próprios marcadores `.L`-locais riscv — NÃO precisou do `kof_heap_root_end`
> x86 da S-5); G-4 (sweep+collect) é o próximo e não depende dele.
>
> **Além do GC (futuro, sem degrau agendado):** a diretiva da mantenedora de
> 15/09 ("todo código nativo deve se comunicar direto com barebones — código
> bootável para microcontroladores, legado e UEFI com Kof") está registada,
> decomposta e mantida só-plano em
> `docs/development/future/PLAN-BAREMETAL-BOOT.md` (faces B-0…B-5: costura HAL
> `kof_plat_*` + perfil freestanding + UEFI/BIOS/MCU). A face MCU depende do
> coletor (G-4/G-5) acima.
>
> **Status:** `EM DESENVOLVIMENTO (parcial)` — **riscv64 + aarch64 com core completo (03/09)**: classes/arrays/List/strings/instanceof/switch/try-catch/FP/recursão em asm puro nos dois; paridade avançada pendente *(ver re-auditoria 12/09 acima — muito do que estava "pendente" já roda sob qemu; o que falta tem código de gap honesto)*.
> **Versão:** 0.2.6-beta · **Data:** 2026-09-03
> **Gap:** `NATIVE002` (riscv64 core ✅ 02/09; aarch64 core ✅ 03/09 via tradução riscv→aarch64; paridade total x86 — JSON/DB/HTTP/concorrência/UI/net — pendente nos dois).
> **Progresso 03/09:** toolchain cruzada + qemu + **codegen riscv64 + aarch64** (stack machine,
> `sp`=operandos/`s11`/`x29`=frame pointer, modelo idêntico ao x86_64) + **runtime asm puro** —
> `NativeRiscv64E2ETest 13/13` (`qemu-riscv64`) + `NativeAarch64E2ETest 13/13` (`qemu-aarch64`): println(String/Int), `var`, `if/else`,
> aritmética/comparações, **classes (virtual dispatch/fields/métodos), arrays, List,
> switch, try/catch/throw, pattern matching (`switch String s`/`instanceof`/`as`),
> String methods, recursão**. Ver §2.3.
> **Decisão (02/09):** runtime por arch **em assembly puro**, no mesmo estilo do x86_64
> (`NativeRuntime.generateRuntimeAssembly`) — **sem C** ("Kof é Kof"; o `kof-c-compiler`
> é outra ferramenta, não um runtime). O C compilado com gcc cruzado que foi usado em
> 02/09 como validação de ABI foi descartado: riscv64/aarch64 passam a emitir runtime asm
> puro (bump allocator + raw syscalls `write`/`exit`, sem PLT/libc) e linkam estático via `ld`, idêntico ao modelo x86_64 (sem dependência de libc).
> **Escopo:** expandir o `NativeBackend` (hoje `x86_64` em asm puro) para
> `riscv64` e `aarch64` Linux, preservando `frontend → Kof IR → backend` e
> paridade `JVM/Native/JS`. Este doc vive em `docs/` (não em `docs/future/`)
> porque **já há código em desenvolvimento** — ele documenta o estado real e
> como finalizar.

## 1. Objetivo

Levar o `NativeBackend` de `x86_64` único para multi-arch Linux sem quebrar
`KofPatternMatchingTest` 10/10 (`switch String s` / `instanceof` / `as` /
`checkcast`) e a suíte `NativeE2ETest`.

Não inclui macOS/Windows, GC avançado ou `kof.web` nativo completo (ver
"fora de escopo").

## 2. Estado Real (auditoria 01/09)

> **Regra desta pasta:** `docs/` documenta o que **está em desenvolvimento**;
> `docs/future/` só o que **é plano futuro** (zero código). Este item já tem
> código, por isso está aqui.

### 2.1 O que JÁ ESTÁ FEITO (plumbing)

| Peça | Estado | Onde |
|------|--------|------|
| Enum `Target.NATIVE_RISCV64` / `NATIVE_AARCH64` | ✅ | `Target.java` (valores distintos de `NATIVE`; `NATIVE` continua = `x86_64`) |
| `Target.isNative()` cobre os 3 nativos | ✅ | `Target.java` |
| `Target.nativeArch()` → `x86_64`/`riscv64`/`aarch64` | ✅ | `Target.java` |
| CLI `native.risc`/`native.riscv64`/`native.riscv` → `NATIVE_RISCV64` | ✅ | `Main.java:364` |
| CLI `native.arm`/`native.aarch64`/`native.aarch` → `NATIVE_AARCH64` | ✅ | `Main.java:365` |
| `kof build`/`run` aceitam `native.risc`/`native.arm` | ✅ | `status.md:13-14` |
| Dispatch `emit()` → `emitRiscv`/`emitAarch64` | ✅ | `NativeBackend.java:210-215` |
| Cross toolchain invocado (as/ld + dynamic-linker + `-lc`) | ✅ | `NativeBackend.emitRiscv`/`emitAarch64` |
| Fallback gracioso sem toolchain (`keeping asm`) | ✅ | idem (try/catch `IOException`) |

**Consequência prática:** `kof build --target native.risc` **compila e gera um
binário** (um stub que sai com `0`) — o pipeline de toolchain/cross-as/ld já
funciona de ponta a ponta.

### 2.2 O que AINDA NÃO ESTÁ FEITO (codegen — o gap real `NATIVE002`)

| Peça | Estado | Detalhe |
|------|--------|---------|
| **Lowering real riscv64 (core)** | ✅ completo 02/09 | `emitRiscv` emite o IR em asm: stack machine (`sp`=pilha de operandos, `s11`=frame pointer, `ra`/`s11` salvos no frame — modelo idêntico ao x86_64) + `.macro pop`; todos os ops do core: literal/local/field/binary (int+FP+bitwise)/unary/condjump/jump/label/call (println/print/valueOf/String methods/coleções/construtor/vtable virtual/FUNCTION/STATIC)/new_object/dup/pop/checkcast/instanceof/arrays/throw/try/catch/return. `NativeRiscv64E2ETest 13/13` |
| **Lowering real aarch64 (core)** | ✅ completo 03/09 | `emitAarch64` = **tradução linha-a-linha do riscv64** (mesmo modelo/lowering, ISA ARMv8-A: `sp`=pilha/`x29`=frame pointer, `x30`/`x29` salvos, `.macro pop` → `ldr`/`add`, `sp` já 16-alinhado no `_start`, `str sp` via temp `x17`). `translateRiscvToAarch64` cobre int+FP (`slt`/`sle`/`seqz`/`snez`/`sext.w`/`fcvt`/`fmv`/`fadd`/`feq`…), `andi`/`ori` via `movk x17`, `sd sp` via `mov x17,sp`. `NativeAarch64E2ETest 13/13` (`qemu-aarch64`) |
| Ops fora do core riscv64/aarch64 (JSON/DB/HTTP/concorrência/UI/net) | ❌ diagnóstico `NATIVE002` | ops desconhecidos emitem comentário `# NATIVE002: op fora do caminho feliz` (nunca binário mudo) |
| Os 18 métodos `emit*` reais (x86_64) | ✅ | `emitBinary`/`emitOperation`/`emitMethod`/`emitConditionalJump`/vcall… — o caminho completo continua só em x86_64 |
| Extração de `NativeBase` (layout/`kof_alloc`/mangle comum) | ❌ não existe | `NativeBackend` ainda é monolítico x86_64 (riscv/aarch64 reusam o mesmo lowering via tradução) |
| Runtime por arch (asm) | ✅ riscv64 + aarch64 core | `kof_alloc`(bump)/`kof_memcpy`/strings (literal/concat/equals/charAt/substring/contains/startsWith/endsWith/indexOf/toInt/length)/int-long-bool→string/print/objects (`init_object`/`instanceof`/super_table/vtables)/arrays (alloc/get/set/length+bounds)/List (new/add/get/set/size/contains/grow)/exceções (`throw`/exc_chain/`null_error`/`bounds_error`) em **asm puro** riscv64 **e** aarch64 (raw syscalls, sem libc; aarch64 via `translateRiscvToAarch64` — `adrp`+`add :lo12:`, `svc #0`, `and sp` skip, `str sp` via `x17`); `qemu-riscv64`/`qemu-aarch64` (ver §2.3). |
| Testes E2E `qemu` (aarch64/riscv64) | ✅ | `NativeRiscv64E2ETest` 42/42 + `NativeAarch64E2ETest` 42/42 (84 testes cross, medidos por @Test + surefire 13/09) |
| CI com cross toolchains | ✅ existe (13/09) | job `cross-native` em `.github/workflows/ci.yml` (instala binutils-riscv64/aarch64 + qemu-user-static e roda as 2 suites; provado `success` no run 34732932745) |
| `backend-parity.md` colunas por arch | ⚠️ parcial | delta citado, colunas `NATIVE_X86_64/AARCH64/RISCV64` separadas pendentes |

**Consequência prática (SUPERADA — foto de 01/09):** valia p/ o stub
original de plumbing; hoje (re-auditoria 12/09 no topo) riscv/aarch **executam
a lógica** sob qemu — 42+42 testes E2E cross, inclusive programas reais com
`println`/`instanceof`/`switch`/Map/Set/higher-order byte-idênticos ao JVM.
O que restou de honesto nesta tabela: `NativeBase` não extraído, colunas por
arch em `backend-parity.md` não separadas, e as faces de ops fora do core
(JSON/DB/UI por arch específico).

### 2.3 Runtime em assembly puro por arch (decisão 02/09)

**Não há runtime em C no Kof.** O nativo x86_64 é asm puro de ponta a ponta: o
runtime (`kof_alloc`, `kof_string_*`, `kof_instanceof`, …) é emitido em
assembly por `NativeRuntime.generateRuntimeAssembly()` e linkado com
`ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` — a libc entra via PLT
(`printf`/`snprintf`), sem C compilado. (O módulo `kof-c-compiler` é outra
ferramenta — reimplementação do sectorC — e **não** é um runtime.)

Decisão para riscv64/aarch64: **mesmo caminho** — runtime emitido em asm
puro por arch + `ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc`. Um C
compilado com gcc cruzado foi usado brevemente (02/09) apenas para validar a
ABI/estática no qemu; ele foi descartado da arquitetura.

Toolchain instalada (02/09, via `sudo apt`):
`binutils-riscv64-linux-gnu`, `binutils-aarch64-linux-gnu`, `qemu-user`,
`gcc-riscv64-linux-gnu`/`gcc-aarch64-linux-gnu` (debug),
`libc6-riscv64-cross`, `libc6-arm64-cross`.

Pipeline alvo (`emitRiscv`/`emitAarch64`):
```
Main.s  (programa: kof_main + seções .data/.rodata)
      + runtime asm riscv64/aarch64 (emitido pelo NativeBackend)
   └─ <arch>-as → <arch>-ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc
   └─ qemu-<arch> → saída esperada (exit 0)
```

**🔗 Link dinâmico SOB DEMANDA (link-by-use) — FEITO 15/09 (diretriz da
mantenedora "liga dinamicamente"):** o link cross *era* estático (asm puro, sem
libc) embora a decisão de 02/09 acima sempre dissesse dinâmico. O
`NativeCrossLink` implementa o realinhamento: o binário continua **estático**
enquanto o runtime *podado* não chamar símbolo de libc; no instante em que uma
capacidade libc-dependente entra (double→string/FLT001 via `snprintf`/`strtod`,
`kof.db` via `.so`, …) o `NativeArchEmitter` troca para `<arch>-ld
--allow-shlib-undefined [--no-relax riscv] --sysroot=<s> -dynamic-linker
/lib/ld-linux-<arch>.so.1 -o <bin> <obj> -lc`. Isso preserva a portabilidade dos
84 binários cross atuais (nada muda sem consumidor libc) e destrava os gaps de
libc sob demanda. O `--allow-shlib-undefined` é obrigatório (a `libc.so.6` do
sysroot referencia símbolos `GLIBC_PRIVATE` do loader; o runtime os resolve).
Resolução do sysroot: env `KOF_CROSS_SYSROOT` → instalação de sistema
(`/usr/<arch>-linux-gnu`, sem `--sysroot`) → `/tmp/opencode/x` (este host) →
nenhum (segue estático + stderr, R6). Provado por `NativeCrossDynamicLinkTest`
(5/5, riscv64 **e** aarch64 sob qemu: `snprintf`+`write` resolvidos em runtime; a
sabotagem de ligar o mesmo harness estático falha com `snprintf` indefinido). O
primeiro consumidor de produção (port do `RuntimeDtoa` para o runtime cross,
fechando FLT001) é a próxima unidade — a infra está, o port não.

Detalhes do runtime riscv64/aarch64 (inc-0 02/09 + 03/09):
- alocação: **bump allocator + free-list** em `.bss` (sem `mmap` — evita
  problemas de qemu estático; o x86_64 usa `mmap`+free-list, e riscv64/aarch64
  seguem o modelo). O G-1 (15/09) somou a free-list no estilo x86 +
  `kof_free` + `kof_memstats`; o bump segue como fallback e o coletor (G-4) é
  quem alimenta a free list.
- strings: layout **idêntico ao x86_64** — `[typeId@0 i32][super@4 i32]
  [vtable@8 ptr][len@16 i32][data@24 …]` (`KOF_STRING_TYPE_ID=1`).
- saída: raw syscall `write(1, …)` (`a7=64` riscv / `x8=64` arm) + `exit` (`a7/x8=93`) — binário **estático**, sem libc/PLT.
- aarch64: **tradução mecânica** do runtime riscv64 (`riscv2arm.py` validado + `translateRiscvToAarch64` em `NativeBackend.java:3650`): `la`→`adrp`+`add :lo12:`, `ecall`→`svc #0`, `and sp` skip (sp já 16-alinhado), `str sp` via `mov x17,sp`, `andi -16` via `movk x17`+`and`, `rem`→`sdiv`+`msub`, `slt/sle`→`cmp`+`cset`, FP `fcvt`→`scvtf`/`fmv`→`fmov`/`fadd`→`fadd`/`feq`→`fcmp`+`cset`.
- validação: `NativeRiscv64E2ETest 13/13` via `qemu-riscv64` + `NativeAarch64E2ETest 13/13` via `qemu-aarch64` (core completo).

O que **restou** para os próximos incrementos:
- riscv64 + aarch64: Map/Set, higher-order (map/filter/reduce), JSON/DB/HTTP/concorrência/
  UI/net — paridade total com o x86_64 (mesmo gap nos dois; hoje diagnóstico `NATIVE002`).

## 3. Arquitetura (alvo)

> **Nota:** a implementação real divergiu do esboço original (que propunha
> renomear para `NATIVE_X86_64` + flag `--arch`). A decisão adotada foi **valores
> de enum distintos** (`NATIVE` = x86_64, `NATIVE_RISCV64`, `NATIVE_AARCH64`) +
> **nome de target no CLI** (`native.risc`/`native.arm`) — sem flag `--arch` e
> sem renomear `NATIVE` (mantém compat). Segue a decisão real.

```
Target enum:
  JVM, NATIVE (=x86_64), NATIVE_RISCV64, NATIVE_AARCH64, JS, ANDROID

IRModule → NativeBackend.emit (select por target):
  NATIVE          → lowering x86_64 (completo, 18 emit*)   [FEITO]
  NATIVE_RISCV64  → emitRiscv   (core completo 02/09, 13/13) [FEITO]
  NATIVE_AARCH64  → emitAarch64 (core completo 03/09, 13/13 via tradução) [FEITO]
  → (meta) extrair NativeBase: ClassLayout, kof_alloc, mangle, resolveFieldOffset
```

`kof build --target native.risc|native.arm` (já funciona no dispatch).

## 4. Mapeamento por Arch (referência para o lowering)

| Aspecto | x86_64 (atual) | AArch64 | RISC-V 64 |
|---------|----------------|---------|-----------|
| **Assembler** | `as` GNU | `aarch64-linux-gnu-as` | `riscv64-linux-gnu-as` |
| **Linker** | `ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` (x86_64 usa PLT/libc) | `aarch64-linux-gnu-ld` **estático** (raw syscalls, sem `-lc`) | `riscv64-linux-gnu-ld` **estático** (raw syscalls, sem `-lc`) |
| **Regs args** | `rdi rsi rdx rcx r8 r9` | `x0 x1 x2 x3 x4 x5` | `a0 a1 a2 a3 a4 a5` |
| **Regs temp** | `rax rcx rbx r10` | `x9 x10 x11 x12` | `t0 t1 t2 t3` |
| **Ret** | `rax` | `x0` | `a0` |
| **Stack** | `pushq %rax` | `str x0,[sp,#-16]!` | `addi sp,-16; sd a0,0(sp)` |
| **Call** | `call sym` | `bl sym` | `call sym`/`jal` |
| **Vcall** | `mov 8(%rax),%rbx; add $idx*8,%rbx; mov (%rbx),%rbx; call *%rbx` | `ldr x9,[x0,#8]; add x9,x9,#idx*8; ldr x9,[x9]; blr x9` | `ld t0,8(a0); addi t0,idx*8; ld t0,0(t0); jalr t0` |
| **Cmp/Jmp** | `cmpq %rax,%rcx; je L; jmp M` | `cmp x1,x0; b.eq L; b M` | `sub t0,a0,a1; beqz t0,L; j M` |
| **String header** | `24B [typeId@0][vtable@8][len@16]` | idem | idem |
| **Syscall exit** | `mov $60,%rax; xor %rdi,%rdi; syscall` | `mov x8,#93; mov x0,#0; svc #0` | `li a7,93; li a0,0; ecall` |

`kof_alloc`/`kof_instanceof`/`kof_string_*` por arch com `KOF_STRING_TYPE_ID=1`
constante.

## 5. Como Finalizar (passo a passo — reflete o plumbing que já existe)

> O encanamento (enum + CLI + dispatch + toolchain) **já está pronto**. O que
> falta é a codegen. Ordem incremental, sem quebrar `x86_64`:

1. **Extrair `NativeBase`** — tirar para uma classe/interface comum:
   `getLayoutForType`/`sanitize`/`mangle`/`resolveFieldOffset`/`collectStrings`
   (hoje em `NativeBackend`). `NativeBackend` (x86_64) herda e continua igual.
   → validar `mvn test -Dtest=CompilerDriverTest` (nenhum `emit` muda).
   *Depende de: nada. Não muda binário x86_64.*

2. **Runtime por arch** — mover `kof_alloc`/`kof_instanceof`/`kof_string_*`
   para asm por arch (hoje inline em `NativeRuntime` x86_64); o `emit` de cada
   target inclui a seção `.s` correta. *Depende de 1.*

3. **`Riscv64Backend` mínimo** — substituir o stub `emitRiscv` por lowering real
   do caminho feliz: `String`/`println`/`instanceof String` + `switch String s`
   + `checkcast` no-op, usando os mapeamentos da tabela §4.
   → `qemu-riscv64` rodando `hello` (teste `assume` se `qemu`/`riscv64-as`
   ausentes, como `NativeE2ETest`). *Depende de 1,2.*

4. **`Aarch64Backend` mínimo** — idem para aarch64 (`qemu-aarch64`). *Depende de 1,2.*

5. **Coleções + classes** — `kof_list_*`/`kof_map_*`/`kof_set_*` e
   `kof_instanceof` para classes de usuário (o `Dummy` usado em
   `KofPatternMatchingTest`). *Depende de 3,4.*

6. **Testes E2E multi-arch** — `NativeRiscv64E2ETest` + `NativeAarch64E2ETest`
   (`@Tag("slow")`, `assume` p/ `qemu`+cross-as ausentes) rodando a mesma fonte
   que `KofPatternMatchingTest` roda em x86_64/JS. *Depende de 5.*

7. **CI** — toolchains `x86_64` sempre; `aarch64`/`riscv64` com
   `if: cross-available` (não quebrar o pipeline quando a toolchain falta).
   *Depende de 6.*

8. **Docs** — `backend-parity.md`: colunas separadas
   `NATIVE_X86_64`/`AARCH64`/`RISCV64`; remover `NATIVE002` quando 6 verde.
   *Depende de 6.*

**Critério de pronto:** `var x:Object="hello"; switch(x){case String s: println(s)}`
compila e roda **idêntico** em `x86_64`, `aarch64 (qemu)`, `riscv64 (qemu)` e
`JS` (`typeof==="string"`); `KofPatternMatchingTest` 10/10 por arch.

## 6. Riscos e Mitigação

- **Stack ABI 16-byte** (ARM/RISC-V exigem `sp` alinhado) → usar pares
  `str/ld` de 16.
- **Reloc RIP vs PC-relative**: x64 `leaq sym(%rip)` → ARM `adrp`+`add` /
  RISC-V `auipc`+`ld`.
- **Cross toolchain ausente** → `assume` skip, não falhar `mvn test`.
- **QEMU lento** → `NativeE2ETest` só x86_64 rápido; aarch64/riscv64 em
  `@Tag("slow")`.
- **Divergência silenciosa**: stub atual "passa" gerando binário → garantir que
  o gap `NATIVE002` seja **diagnóstico claro** (não binário que silencia a
  lógica) até o lowering existir.

## 7. Fora de Escopo (ficam em `docs/future/` / outros docs)

- `GC` mark-sweep avançado, `float/double` no Native (`F2D`), `kof.web`
  `listen` nativo, `macOS` Mach-O / `Windows` PE.
- **Bare-metal / bootável** (microcontrolador, BIOS legado, UEFI) — diretiva da
  mantenedora 15/09 ("todo código nativo deve se comunicar direto com barebones").
  O runtime riscv64/aarch64 em asm puro (sem libc) é a base natural, mas os
  emitters estão fixados a `ecall`/`syscall` Linux e a uma ABI `_start`, e não há
  perfil de link freestanding. Só plano, sem agendamento:
  `docs/development/future/PLAN-BAREMETAL-BOOT.md` (faces B-0…B-5).
