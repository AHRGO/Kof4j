# ABI de struct/array na FFI — spec D6-A (design primeiro, sem código)

[English](ffi-abi-structs.md) | [Português](ffi-abi-structs.pt_BR.md)

**Status:** RASCUNHO para revisão da mantenedora (D6-A,
`docs/development/DECISIONS.md` D-POLL-19: "especificação escrita primeiro,
revisão, depois código").
**Execução após aprovação:** compiler lane (linha 3.8 do tracker) + native
lane (3.7). Este documento é SOMENTE DESIGN — não muda semântica nem binda
nada.

## 1. O que existe hoje (medido 19/09, não lembrado)

`extern name[("lib")] (params): Ret` vira um token de assinatura
(`FfiSignature.java`): `i`=Int, `j`=Long, `f`=Float, `d`=Double, `b`=Boolean,
`S`=String (`char*`), `v`=retorno void; parâmetro callback é o token aninhado
`(<ret><params>)`. O que o mapa não cobre é **gap honesto em tempo de
compilação**: `FFI001` (JVM/Native não bound) / `FFI002` (JS) —
`CompilerPipeline.java:225-236`, R6 (nunca stub silencioso).

| Superfície | JVM | Native | JS |
|---|---|---|---|
| downcall/upcall escalar | ✅ `kof_ffi` FFM (`JvmFfiRuntime.java:142+`) | ❌ `FFI001` (linha 3.7) | ✅ bridge do host `KofJsFfiBridge` (browser degrada honesto, R7) |
| callbacks (3.4) | ✅ `Linker.upcallStub` | ❌ | ✅ host |
| String = `char*` | ✅ entrada + saída | — | ✅ |
| **struct / array / out-buffer / opaco** | ❌ FFI001 | ❌ FFI001 | ❌ FFI002 |

Mapeamento escalar JVM→FFM (medido): `i→JAVA_INT, j→JAVA_LONG, f→JAVA_FLOAT,
d→JAVA_DOUBLE, b→JAVA_BOOLEAN, S→ADDRESS`; token não-escalar cai em
`ADDRESS` só no caminho de callback (`JvmFfiRuntime.java:88-106,144-145`).

**Verruga medida (candidata a fix, não é gap novo):** parâmetros `String` do
downcall são alocados com `Arena.global()` (`JvmFfiRuntime.java:24,63`) —
arena global nunca libera; num processo longo, todo argumento FFI vira
vazamento. A spec deve decidir a política de arena (§4 D6-5), não deixar o
código derivando.

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
| riscv64 LP64 | campos ≤ 8 B empacotados em *doublewords* `a0…a7`; alinhamento pode forçar pulo de doubleword; struct > 2 doublewords ou com classe não-alinhada → **referência** (ponteiro para cópia do chamador), classe `Byref` |

Três exemplos resolvidos que os testes de implementação devem reproduzir bit a bit:

| Forma Kof | Forma C | size | align | classes SysV |
|---|---|---|---|---|
| `Point2(Int x, Int y)` | `struct{int,int}` | 8 | 4 | INTEGER (1 eightbyte) |
| `Mixed(Bool b, Int n, Float f)` | `struct{_Bool,int,float}` | 12 | 4 | padding após `b`; INTEGER (8B: b+n) + INTEGER (4B: f) |
| `Time(Int64 s, Double d)` | `struct{int64_t,double}` | 16 | 8 | INTEGER + SSE (SysV), 2 eightwords (aarch64) |

## 4. Decisões de design para a mantenedora (rule 6 — esta lane propõe, nunca decide)

- **D6-1 · qual valor Kof mapeia para struct C?**
  A) `record` (estrutural, imutável, zero-ceremonia — default recomendado);
  B) um novo `struct` mutável (necessário para buffers *in/out*);
  C) ambos: records = by-value read-only, `struct` = by-ref.
  A composição que pendemos é A+B; a decisão precisa ser escrita em
  `DECISIONS.md` antes de 3.8 começar.
- **D6-2 · mapeamento de array.** `List<Int>` é boxed (`ArrayList` no JVM) —
  bindar significa copiar para memória nativa a cada chamada. Proposta:
  arrays primitivos (`new Int[n]`, que já existem) bindam como `ptr` (sem
  parâmetro de comprimento implícito — quem decide é a API C); `List<T>`
  permanece FFI001 até um benchmark de unboxing provar o contrário.
- **D6-3 · parâmetros out.** Sem sintaxe nova na v1: out-buffer =
  `new Byte[n]` passado como `S`→`ADDRESS` e lido de volta após a chamada.
  Campos ponteiro-em-struct ficam fora (handles opacos são 3.3, decisão
  separada).
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
callbacks struct-typed (fn-ptr aninhado em struct); variadics (3.5, ⛔
separado); name mangling C++; regras de COMDAT/seção. Cada item permanece
FFI001/002 honesto até decidido — nada de binding parcial silencioso.

## 6. Divisão de trabalho (após aprovação — não é esta lane)

1. **3.8a** engine de layout: `AbiLayout` (size/align/classes por triple) no
   compiler, dados puros + golden tests contra os três exemplos resolvidos
   (§3).
2. **3.8b** binding JVM: records→`StructLayout` no `kof_ffi` (FFM faz a
   classificação); política de arena D6-5.
3. **3.7** asm native: classificação manual por target (x86-64 agora;
   aarch64/riscv64 seguem o mesmo golden de AbiLayout) + sret (D6-4).
4. **JS**: decidir a fronteira wasm/ffi (o host node já binda escalares;
   struct = pack/unpack no host) — nenhuma promessa para browser (R7).
5. **DoD (R5)**: matriz E2E golden por target (mesmo harness C, 3 ABIs),
   FFI00x inalterado para tudo que não for coberto,
   `training/idioms/interop.md` atualizado com a forma Kof escolhida em
   D6-1, e este doc promovido a `docs/` quando 3.8 pousar.
