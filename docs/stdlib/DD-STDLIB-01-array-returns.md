[English](DD-STDLIB-01-array-returns.md) | [Português](DD-STDLIB-01-array-returns.pt_BR.md)

# DD-STDLIB-01 — Array/object return in the stdlib dispatch layer (CLOSED 13/09 — moved to docs/)

> **✅ DECIDED 13/09 (maintainer, option 6a):** Option B — `randomBytesHex(n)->String` (zero plumbing); `randomChoice` closed as an idiom (`l[random.randomInt(l.size)]` documented in `training/idioms`); the binary `randomBytes` name stays RESERVED (does not enter).
>
> **Status:** `IMPLEMENTED` 13/09 (option 6a) — `random.randomBytesHex(n)->String`
> as an additive alias of `random.hex` (same runtime fn `kof_random_hex`,
> same contract, 5 targets; `KofRandomTest.randomBytesHex{Jvm,Js,Native}`);
> binary `randomBytes` name RESERVED (does not enter); choice = idiom
> `l[random.randomInt(l.size)]` (already documented in learn/39-stdlib.md +
> training/idioms/stdlib.md) · **Gap S10c CLOSED** · **Lane:** STDLIB ·
> **Created:** 09/09/2026 · **Bump:** none (additive — only opens the way)

## The problem

Plan S10 lists `randomBytes(n)` (returns bytes) and `randomChoice(List)`
(returns an element) in the `random` namespace
(`plan-stdlib-expansion.md:45`). Both require a **stdlib** function to
return a composite type through the dispatch layer (`KofStd.StdCall` →
`KofCall` → runtime of the 5 targets). Today **no** stdlib function returns
Array/object — the layer's vocabulary is `Int/Bool/String/Void`:

- `KofMath`: Int-only (`kof_math_*` → `(I)I`); FP stays S1b.
- `KofStrings`: STR→STR; `split` is not stdlib (it is a `String` method, its
  own route with `kof_new_array` embedded in the method lowerer).
- `KofUuid`/`KofNet`/`KofEncoding`/`KofValidation`: STR/BOOL.
- `KofRandom` (S10a/b): INT/BOOL/STR.

NATIVE arrays exist (`kof_array_alloc`/`get`/`set` in x86; riscv
allocator B-slices; `List<T>` interp with JVM reflection) — what does not exist is the
**type plumbing** for `Type.ArrayType` to cross `KofStd → KofCall →
JVM descriptors → JsTypeMapper → ABI asm`. This is a design decision (rule
6): it touches five target contracts, and the shape of the return (byte[]? List?
String hex?) affects the frozen API.

## Options

| Option | randomBytes | randomChoice | Cost |
|---|---|---|---|
| **A. Array type in dispatch** | `Bytes -> ByteArray` | `Choice(List) -> Object` | complete plumbing; 5 ABI; `ArrayType` in the descriptors |
| **B. Hex String** (recommended for bytes) | `randomBytesHex(n) -> String` | — | zero plumbing; reuses the dynamic String machine from S10b; precedent: `security.randomHex` exists! |
| **C. No randomChoice** | — | `l[random.randomInt(l.size)]` in pure Kof | zero — the language already solves it |

## Recommendation

- **`randomChoice` does NOT need a runtime**: `list.get(random.randomInt(list.size))`
  is 1 idiomatic line of Kof (the language rule: complexity belongs to whoever
  uses it, not to the platform — and `get`+`size` already exist in the 5 targets). Close the
  plan item as "covered by idiom", document it in `learn/39` +
  `training/idioms`. If the maintainer wants the sugar, it becomes a `List`
  feature (not a `random` one).
- **`randomBytes`**: the safe version already exists (`security.randomHex(n)` —
  hex string, 5 targets). If the plan keeps the unsafe face, the coherent
  shape is identical: **Option B** (`random.bytes` returns the same hex
  shape? NO — binary bytes ≠ hex; it would open a parity divergence in the return
  type). **Question for the maintainer:** does the value of binary `randomBytes`
  justify the Array-type plumbing (Option A) now, or does the name stay
  reserved and `security.randomHex`/`security.randomBytes` cover the real
  use case (which is ALWAYS cryptographic — token/salt/key)?

Neither enters by directly editing the current layer. `random` closes
v1 with `randomInt/randomBoolean/randomString` (S10a/b) + idiom for choice.

## Impact if Option A is approved (for the record only)

`KofStd.StdCall.returnType` accepts `ArrayType` → `JvmRuntimeCallDescriptors`
(case `"[B"`), `JvmRuntimeReturnDescriptors` (`"[B"`), `KofInterpreter`
dispatch, `JsTypeMapper.runtimeJsName` (invariant — name only), x86 ABI
(rdi/len → rax is already the `kof_array_alloc` shape), riscv/aarch (same B14).
Estimate: 1 unit per target (like S1–S10b) + equality matrix with
element-walk. Does NOT start without a decision.
