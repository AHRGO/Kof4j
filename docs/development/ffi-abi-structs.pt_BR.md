# ABI de struct/array na FFI — spec D6-A (design primeiro, sem código)

[English](ffi-abi-structs.md) | [Português](ffi-abi-structs.pt_BR.md)

**Status:** **D6 DECIDIDO (mantenedora 20/09/2026)** — `docs/development/DECISIONS.md`
§D-FFI-STRUCT. D6-1 = A+B (`record` por valor + novo `struct` mutável por
referência) · D6-2 = só `new T[n]` · D6-3 = `Buffer(U8, INOUT)`, sem sintaxe
nova · D6-4 = implementar o sret completo · D6-5 = arena confinada por downcall.
**D6-1 B aprovado spec-first 21/09 (`D-FFI-STRUCT-B`)**: a superfície `struct`
mutável é desenhada aqui (§4/§6) e revisada antes de qualquer diff de
parser/typer (regra 11). O texto-proposta da §4 fica pelo raciocínio medido.
Este documento segue SOMENTE DESIGN.
**Execução após aprovação:** compiler lane (linha 3.8 do tracker) + native
lane (3.7).
**Pousou 20/09 (fatia sem decisão):** 3.8a `AbiLayout` — o substrato de
layout/classificação, com golden medido nas três ABIs (§6.1). O binding
(3.8b/3.7) procede sob as decisões D6 acima.
**Pousou 20/09 (3.8b fatia 1):** `record` Kof→struct C **por valor como
argumento** no JVM (token `@`; `FfiStructE2ETest` 6/6).
**Pousou 21/09 (3.8b fatia 2):** `record` Kof **devolvido por valor** no JVM
(registrador e sret; nome binário codificado em `@`+`:`, reconstrução pelo
construtor canônico; `FfiStructE2ETest` 10/10). Struct no Native = 3.7.
**Pousou 21/09 (3.8b bridge JS · D6-1):** o runner JS agora binda um `record`
**por valor como ARGUMENTO** — token `@<n><chars>` carrega o layout dos campos
no fio (o host não reflete `RecordComponent` de um objeto GraalJS), o record
expõe `__kof_ffi_fields()` na ordem de declaração e o `KofJsFfiMarshal` empacota
o `StructLayout` (mesmos offsets/padding de cauda do JVM) na arena da chamada
(D6-5). Prova: `structParamByValueJsParity` — `sumpoint(Point(3,4))=7`,
`scale(Point(2,3),2.0)=10.0` e `parammix(ParamMix(3,2.5,4))=9.5` (layout j/d/i)
byte-a-byte JVM==JS. **Retorno** de struct no JS e array/buffer no JS seguem
`FFI002`.
**Pousou 21/09 (3.8b fatia 3 · D6-2):** array escalar **`T[]`→`ptr` C** no JVM,
**copy-in por chamada** (token `p`+char do elemento; `new Int[n]` atravessa como
`int*`). O array Java não é pinado nem aliasado — o callee não escreve de volta
(isso é o out-buffer da D6-3, `Buffer(U8, INOUT)`). `String[]` (array de
ponteiros) segue FFI001; Native/JS mantêm seus gap codes. `FfiArrayE2ETest` 5/5.

## 1. O que existe hoje (medido 19/09, não lembrado)

`extern name[("lib")] (params): Ret` vira um token de assinatura
(`FfiSignature.java`): `i`=Int, `j`=Long, `f`=Float, `d`=Double, `b`=Boolean,
`S`=String (`char*`), `v`=retorno void; `@`=record por valor (fatias 1–2),
`p<elem>`=array escalar `T[]`→`ptr` com copy-in (fatia 3); parâmetro callback é
o token aninhado `(<ret><params>)`. O que o mapa não cobre é **gap honesto em
tempo de compilação**: `FFI001` (JVM/Native não bindável) / `FFI002` (JS) —
`CompilerPipeline.java:225-236`, R6 (nunca stub silencioso).

| Superfície | JVM | Native | JS |
|---|---|---|---|
| downcall escalar | ✅ `kof_ffi` FFM (`JvmFfiRuntime.java:142+`) | ✅ **`call sym@PLT` direto em x86-64/riscv64/aarch64** (#431 fatias 1–2, 20/09, §369 — link-by-use, sem `dlopen`) | ✅ bridge do host `KofJsFfiBridge` (browser degrada honesto, R7) |
| callbacks/upcalls (3.4) | ✅ `Linker.upcallStub` | ❌ `FFI001` (sem mecanismo) | ✅ host |
| String = `char*` | ✅ entrada + saída | ✅ entrada (payload off 24) + saída (cópia na fronteira) | ✅ |
| **struct (record, campos escalares)** | ✅ **por valor entrada + retorno** (token `@`, 3.8b fatias 1–2, 20–21/09) | ❌ FFI001 (3.7) | ✅ **por valor ENTRADA** (token `@<n><chars>` + `__kof_ffi_fields`, bridge 21/09); ❌ FFI002 retorno |
| **array escalar `T[]`→`ptr`** | ✅ **copy-in por chamada** (token `p<elem>`, 3.8b fatia 3, 21/09; sem write-back) | ❌ FFI001 | ❌ FFI002 |
| **out-buffer `Buffer(U8)` INOUT** | ✅ **copy-in / chamada / copy-back** (token `B` + `buffer.alloc`/`Buffer.bytes()`, D6-3, 21/09) | ❌ FFI001 | ❌ FFI002 |
| array não-escalar / opaco (ex. `String[]`/`List<T>`/`Handle`) | ❌ FFI001 | ❌ FFI001 | ❌ FFI002 |

Mapeamento escalar JVM→FFM (medido): `i→JAVA_INT, j→JAVA_LONG, f→JAVA_FLOAT,
d→JAVA_DOUBLE, b→JAVA_BOOLEAN, S→ADDRESS`; token não-escalar cai em
`ADDRESS` só no caminho de callback (`JvmFfiRuntime.java:88-106,144-145`).

**Verruga medida (CORRIGIDA 21/09 — 3.8b fatia 2, D6-5):** a política de arena
do downcall agora é **confinada por chamada, fechada no `finally`** nos helpers
escalares (`kof_ffi_i`/`kof_ffi_si`/`kof_ffi_dd`) e no `kof_ffi`. Antes, `i`/`dd`
usavam `Arena.global()` (o handle do `libraryLookup` nunca era liberado →
vazamento por chamada) e `si` abria arena confinada sem fechar. Prova:
`FfiE2ETest` 17/17 (`scalarHelpersRepeatStableUnderConfinedArena`: 300× cada
helper, idempotente).

## 2. Por que "struct" é mais duro do que parece (o custo real)

O lado JVM é quase grátis: o FFM já entende
`MemorySegment`/`StructLayout` e a classificação é feita pela própria
implementação SysV do JDK. **O custo se concentra no backend asm Native**,
que precisa implementar a classificação de struct por ABI manualmente
(§3) — por isso o tracker separa 3.8 (layout+JVM) de 3.7 (native), e por
isso esta spec vem antes de qualquer código.

## 3. Regras de layout e chamada por ABI (referências normativas)

Alinhamento natural (`alignof` do campo), tamanho arredondado para cima até
`alignof` do struct, padding final incluído; sem `#pragma pack` na v1.

| ABI | Regra de passo por valor (resumo) |
|---|---|
| x86-64 SysV | classifica cada *eightbyte*: INTEGER / SSE / SSEUP / NO_CLASS (≤ 8 campos no total); ≤ 16 B classe INTEGER → duas int regs (`rdi…`), ≤ 16 B SSE → XMM; maior que isso → **memória** (stack), cópia alocada pelo chamador |
| aarch64 AAPCS64 | teste HFA (≤ 4 floats homogêneos); senão ≤ 16 B → core regs `x0…` (classe eightword), > 16 B → stack; registro `w` para a metade alta quando misto |
| riscv64 LP64D | **MEDIDO 20/09 (corrige a prosa do rascunho "empacotados em doublewords a0…a7"):** struct ≤ 16 B com **≤ 2 campos** é *flattened* — campos de ponto flutuante em `fa0/fa1`, campos inteiros empacotados em `a0/a1` (`Time(Long,Double)`→`a0`+`fa0`; `{Float,Int}`→`fa0`+`a0`); com **3+ campos** empacota em doublewords inteiros (`{Int,Int,Int}`→`a0,a1`); > 16 B → **referência** (ponteiro para cópia do chamador), classe `Byref`. O `riscv64-linux-gnu-gcc` 13.3 do host é LP64D (hard-float), por isso o empacotamento soft-float do rascunho não batia |

Três exemplos resolvidos que os testes de implementação devem reproduzir bit a bit:

| Forma Kof | Forma C | size | align | classes SysV |
|---|---|---|---|---|
| `Point2(Int x, Int y)` | `struct{int,int}` | 8 | 4 | INTEGER (1 eightbyte) |
| `Mixed(Bool b, Int n, Float f)` | `struct{_Bool,int,float}` | 12 | 4 | padding após `b`; eightbyte 0 (b+n) = INTEGER, eightbyte 1 (f) = **SSE** — MEDIDO (GCC 13.3, x86-64 `-O0 -S`): primeiro eightbyte em `%rdi`, `f` em `%xmm0`; offsets n=4, f=8 (corrigido 20/09: o rascunho dizia INTEGER+INTEGER) |
| `Time(Long s, Double d)` | `struct{int64_t,double}` | 16 | 8 | INTEGER + SSE (SysV; MEDIDO: `s`→`%rdi`, `d`→`%xmm0`), 2 eightwords (aarch64). O Kof não tem `Int64` — o inteiro de 64 bits é `Long` (corrigido 20/09) |

## 4. Decisões de design — **DECIDIDO** (D-FFI-STRUCT, mantenedora 20/09/2026; rule 6)

> **Decidido:** D6-1 = **A+B** (`record` por valor read-only + novo `struct`
> mutável por referência) · D6-2 = **só `new T[n]`** binda a `ptr` · D6-3 =
> **`Buffer(U8, INOUT)` sem sintaxe nova** · D6-4 = **implementar o sret
> completo** · D6-5 = **arena confinada por downcall**. Autoridade:
> `docs/development/DECISIONS.md` §D-FFI-STRUCT. O texto-proposta abaixo fica
> pelo raciocínio medido.

- **D6-1 · qual valor Kof mapeia para struct C?**
  A) `record` (estrutural, imutável, zero-ceremonia — default recomendado);
  B) um novo `struct` mutável (necessário para buffers *in/out*);
  C) ambos: records = by-value read-only, `struct` = by-ref.
  A composição é A+B; **B está aprovado spec-first 21/09**
  (`D-FFI-STRUCT-B`) — o desenho é revisado antes de qualquer diff de
  parser/typer (regra 11).
- **D6-2 · mapeamento de array.** `List<Int>` é boxed (`ArrayList` no JVM) —
  bindar significa copiar para memória nativa a cada chamada. Proposta:
  arrays primitivos (`new Int[n]`, que já existem) bindam como `ptr` (sem
  parâmetro de comprimento implícito — quem decide é a API C); `List<T>`
  permanece FFI001 até um benchmark de unboxing provar o contrário.
  **✅ fatia 3 POUSOU 21/09 (JVM, copy-in por chamada, token `p<elem>`) — a
  proposta acima, exatamente; `List<T>` segue não bindado.**
- **D6-3 · parâmetros out.** Sem sintaxe nova na v1: out-buffer =
  `new Byte[n]` cruzando como tipo ABI PRÓPRIO — `Buffer(U8, INOUT)`, copia-para-dentro /
  chamada / copia-de-volta — **nunca o token `S`** (corrigido 20/09: `S` = `String` = `char*`
  UTF-8 terminado em NUL, somente-leitura; um buffer difere em mutabilidade, comprimento,
  direção e tempo de vida, então não pode reusar `S`; `CString`, `Buffer`, `Pointer`,
  `OpaqueHandle` e `Struct` são tipos ABI distintos mesmo quando todos viram um endereço
  num registrador). O comprimento segue argumento explícito do C.
  Campos ponteiro-em-struct ficam fora (handles opacos são 3.3, decisão
  separada).
  **Pousou 21/09 (D-R3-BUFFER/D-R3-HANDLE-LIFETIME):** a grafia nominal é
  **`Buffer(U8)`** (não um reuso de `Byte[]`), criado com
  **`buffer.alloc(Int) : Buffer(U8)`** — o programador nunca aloca/libera
  (vida gerenciada pela linguagem; `Handle` segue a mesma regra automática).
  Fatias
  (JVM): `buffer.alloc` + `Buffer.bytes() : Byte[]` (`BufferE2ETest`
  4/4) e `Buffer(U8)` como parâmetro INOUT de `extern` — **copy-in / chamada /
  copy-back** (`BufferFfiE2ETest` 4/4 com shim C real: as escritas acumulam
  entre chamadas, provando que o copy-in lê e o copy-back escreve). Native/JS
  seguem `FFI001`/`FFI002` (R6-SCOPE: incremental, gaps declarados).
- **D6-4 · retorno by-value > 16 B.** SysV hidden-pointer (sret) /
  AAPCS64 hidden-x8 / LP64 referência — o Linker do *JVM* esconde isso; o
  backend *asm* precisa implementar sret explicitamente. Alerta: é o maior
  custo single da native lane; as fatias em §6 o isolam.
- **D6-5 · ownership de String/arena (conserta a verruga do §1).**
  Proposta: arena confined por downcall, fechada após a chamada; `char*`
  retornado é **copiado e nunca possuído** (String Kof é imutável — o
  ponteiro C não pode sobreviver à chamada, salvo API C que documenta
  transferência de posse, que é a história do `free()` de 3.3).

## 5. Não-objetivos (v1)

bitfields; unions anônimas; `#pragma pack`/`alignas`; `long double`
(x87 80-bit — código de gap próprio se um dia); `wchar_t`/UTF-16;
callbacks struct-typed (fn-ptr aninhado em struct); variadics (3.5 — `D-R3-3.5` ✅ decidido 21/09: **sem variadics gerais**, gap documentado); name mangling C++; regras de COMDAT/seção. Cada item permanece
FFI001/002 honesto até decidido — nada de binding parcial silencioso.

## 6. Divisão de trabalho (após aprovação — não é esta lane)

1. **3.8a** engine de layout: `AbiLayout` (size/align/classes por triple) no
   compiler, dados puros + golden tests contra os três exemplos resolvidos
   (§3). **✅ POUSOU 20/09** — `AbiLayout.java` + `AbiLayoutTest` (14 shapes ×
   3 ABIs, golden medido com GCC 13.3 em x86-64/aarch64/riscv64 e reprovado ao
   vivo com `_Static_assert` contra os compiladores reais). Não binda nada e
   não decide nada de D6-1..D6-5; é o substrato compartilhado que 3.8b/3.7
   consomem.
2. **3.8b** binding JVM: records→`StructLayout` no `kof_ffi` (FFM faz a
   classificação); política de arena D6-5. **✅ fatia 1 (param por valor,
   20/09) + fatia 2 (retorno por valor: registrador + sret, 21/09) + fatia 3
   (D6-2: `T[]` escalar→`ptr`, copy-in por chamada, 21/09) + fatia 4 (D6-3:
   out-buffer `Buffer(U8)` — `buffer.alloc`/`Buffer.bytes()` + `extern` INOUT
   copy-in/copy-back, 21/09) POUSARAM** —
   só o subconjunto de campos escalares; `struct` mutável (D6-1 B) é superfície
   nova da linguagem sob a Lei da Simplicidade (regra 11), decisão separada.
   Restam: o **retorno** de struct no JS e array/buffer no JS (D6-2/D6-3 no
   JS) — o bridge de struct **param** no JS (pack no host, D6-5) pousou 21/09.
3. **3.7** asm native: classificação manual por target (x86-64 agora;
   aarch64/riscv64 seguem o mesmo golden de AbiLayout) + sret (D6-4).
4. **JS**: decidir a fronteira wasm/ffi (o host node já binda escalares;
   struct = pack/unpack no host) — nenhuma promessa para browser (R7).
5. **DoD (R5)**: matriz E2E golden por target (mesmo harness C, 3 ABIs),
   FFI00x inalterado para tudo que não for coberto,
   `training/idioms/interop.md` atualizado com a forma Kof escolhida em
   D6-1, e este doc promovido a `docs/` quando 3.8 pousar.
