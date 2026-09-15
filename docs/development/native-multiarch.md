[English](native-multiarch.md) | [Português](native-multiarch.pt_BR.md)

# Kof Native — Multi-Arch (RISC-V 64 and ARM64/AArch64)

> **🔄 RE-AUDIT 12/09 (measured under REAL qemu on this host — NOT memory):**
> the 03/09 headers below are OUTDATED and this block is the source of the
> REAL state (AGENTS rule "audit doc against the code/tests, not against
> memory"; state-4 "doc contradicts the code" corrected). The 13 "core"
> faces of 03/09 are today ~30 cross tests (`NativeRiscv64E2ETest` 39/39 +
> `NativeAarch64E2ETest` 39/39, **executed**, 0 skip on this host). **CLOSED
> and RUNNING under qemu (byte-identical to the measured JVM), beyond the "core":**
> Map/Set, `println(<collection>)` (§107 — x86 `f3b3821c` + cross B39 12/09),
> higher-order `map/filter/reduce` (probe 12/09: `[2,4,6]`/`[2,3]` identical
> JVM), HTTP client (`riscv64HttpGetPostStatus`), `kof.net`, JSON
> encode/decode int/list/string, spawn/await (`clone`+`futex`), `time` ISO
> (add/diff), `math` Double (MATH001) + **`math.pow` S1b.2 (decision 7a 13/09:
> x86 via libm `pow@PLT` + `-lm`; riscv/aarch refuse with MATH001 — static
> cross link without libc, architecture decision rule 6; `KofMath.supportedOn`),
> `random`, `uuid`, multi-dim array
> (§113), UTF-16 String search (§43/§102/§111). **OPEN — HONEST refusal at
> compile-time (NEVER silent binary; rule R6 — the "exit 0 with no effect" stub
> of 03/09 no longer describes the state, unknown ops give a gap code):**
> `kof.db` → **DB001** (`KofDbE2ETest` proves `assertFalse(success)` + DB001
> diags on the 6 targets), `kof.security` crypto-heavy → **SECN000**, the 6
> higher-order concurrency constructs (supervisor/`selectAny`
> multi/cancel cross …) → **CONC001** (`spawn`/`await`/`sleep`/`interval`
> GREEN on cross — gate #91 does not touch them), UI (`kof.ui`) **without any
> cross port** (no riscv/aarch test), `json.decode<List<Record>>` → **JSN004**
> (pure asm has no reflection to materialize a record). **Honest consequence
> TODAY:** a program with collection/HTTP/net/scalar-JSON/spawn/time/math **truly
> runs** on riscv/aarch (the 03/09 phrase "does not execute the logic — exits 0 with no
> effect" is SUPERSEDED); what still does not run (DB/security/UI/record-decode)
> is diagnosed with a gap code at compile time, not silently.
> **Real remaining gap `NATIVE002`:** (1) cross GC mark-sweep (riscv is
> bump-pointer without collector — leak on long heap, non-crash) —
> **G-0 header-block + G-1 free-list/memstats + G-2 gc-list/dump + G-3
> conservative mark DONE 15/09** (see the decomposition below); G-4..G-5
> pending, the collector (G-4) is what actually reclaims; (2) the
> DB001/SECN000/CONC001/JSN004 refusals above; (3) FP-collection on cross
> (FLT001 at compile §107); (4) `backend-parity.md` per-arch columns
> still to be separated; (5) cross CI does not exist (host-dependent toolchain) —
> **face (5) CLOSED 12/09**: job `cross-native` in `.github/workflows/ci.yml`
> installs `binutils-riscv64/aarch64-linux-gnu` + `qemu-user-static` and runs
> `NativeRiscv64E2ETest,NativeAarch64E2ETest` (they execute under qemu, do not skip —
> the job EXISTS to prove; binary names match `NativeArchEmitter:151
> -282`; local: riscv 39/39 + aarch 39/39 green on this host with qemu).
> **➕ Dynamic link ON DEMAND DONE 15/09** (directive "liga dinamicamente"):
> `NativeCrossLink` links libc (`-dynamic-linker … -lc`) only when the pruned
> runtime calls libc; the current 84 cross binaries stay static/portable.
> See §2.3; the first production consumer (port of `RuntimeDtoa`, FLT001) is
> still pending.
> This doc remains in `development/` (NATIVE002 does not close while (1)–(5)
> remain); when (1)–(5) reach zero → move to `docs/`.
>
> **🪜 FACE (1) DECOMPOSITION — cross GC mark-sweep (12/09, queue for
> step-by-step execution — each step fits in a session and has its own proof):**
> riscv is pure bump (`amoadd.d` in `kof_alloc_ptr`, no flags/mark/free-list);
> the port is NOT copying the x86 RuntimeGc — the conservative scan requires a
> riscv stack-walk + roots in the section range. **⚠️ CORRECTED 12/09 (read the code,
> not memory) — the original decomposition was WRONG at G-1:** riscv has NO
> block header: riscv `kof_alloc` (`NativeRiscvAsmRt0.java:17-22`)
> returns the raw bump aligned to 16 and users write the OBJECT header
> (typeId @0, vtable @8, …) at OFFSET 0 of the returned pointer (e.g.:
> `string_from_literal`: `sw t0, 0(s3)`), whereas on x86 the GC lives in a block
> of 32B BEFORE the returned pointer (`RuntimeMemory.java:145-150` — size@0,
> free-next@8, gc-next@16, flags@24; `kof_free:205` reads `-32(%rdi)`). Porting the
> free-list without the header-block = the collector reads the typeId as size →
> corruption. That is why **G-0** comes first. Steps in order:
> **G-0 riscv header-block (DONE 12/09, dev session):** riscv `kof_alloc` reserves
> 32B BEFORE the pointer (total = 32+align16, return base+32; filling
> size/free_next/gc_next/flags) + honest OOM guard (`_kof_heap_end`, panic
> `out of memory` exit 1 — R6: the bump did NOT have a bounds-check and the header
> triples the consumption/block, so overflow became more likely). Proof:
> riscv suite 40/40 + aarch 40/40 under qemu (includes the pressure test
> `riscvHeapExhaustionPanicsHonest`/`aarch64HeapExhaustionPanicsHonest`,
> sabotage-without-guard = zero output FAIL) + x86 GC 3/3 + Artifact 6/6 +
> ratchet ≤500 OK (Rt0 with exactly 500; design prose lives here).
> **G-1 riscv free-list (DONE 15/09, dev session):** port of the x86
> free-block list ON TOP of the G-0 layout (header 32B:
> size/flags/gc-list/free-next). New slice `NativeRiscvAsmRtB42` holds
> `kof_alloc` (MOVED out of `Rt0`, which dropped 500→478 — the ratchet room),
> `kof_free` and `kof_memstats`; the alloc now first-fits the free list (LIFO,
> re-enqueue on alloc, `flags=0`) and only bumps on a miss; an `amoswap.w`
> spin-lock guards the shared free list + counters (main × spawn workers).
> `kof_free` is a 1:1 port of `RuntimeMemory.emitFree` (flags bit1 = in free
> list). `kof_memstats` prints `allocs`/`frees`/`live bytes` — the observation
> lever for the following steps. Proof (qemu riscv64 **and** aarch64, toolchain
> present, tests never skip): `NativeRiscvGcFreeListTest` assembles the
> PRODUCTION runtime (`RiscvSlices.renderRuntime()`) with a raw `_start` that
> alloc(64)→free→alloc(64) and asserts p2==p1 (reuse) + `allocs: 2`/`frees: 1`
> — 2/2 green; `NativeRiscvRuntimeSliceRegistryTest` 8/8 (concat still
> byte-identical); cross suites riscv 44 + aarch 44, only the pre-existing
> `CastSaturation` red; `ArtifactSizeTest` 6/6 (the internal lock/head are
> `.L`-local so the hello `.symtab` does NOT grow — was the one real
> regression found and fixed here); `KofGcE2ETest` 3/3 x86 untouched;
> `check_500` OK.
> ⚠️ **G-1 proof correction (15/09, doc-vs-reality — the plan's proof had no
> reachable path):** the 12/09 proposal said "wire free into the log nodes
> (RtB0, mirroring `RuntimeLog2:98`)" and "alloc/free/alloc reuses the slot".
> Counting in the source refutes BOTH halves: (a) the riscv log node
> (`NativeRiscvAsmRtB0:242`) writes label+msg **directly via `write()` and never
> allocates**, and observability uses fixed `.bss` slots — so there is NO node
> to free; (b) across the compiler **no riscv slice calls `kof_free`** (x86 has
> 4 real callers: `RuntimeChannel:132`, `RuntimeLog2:98`,
> `RuntimeObservability1:426`/`2:247`) and there is **no Kof-level free/GC API**,
> so a Kof E2E cannot exercise it. Conclusion: the riscv free-list is the
> correct x86-parity infrastructure, but on riscv it is **latent until G-4**
> (sweep feeds it) — the G-1 proof is therefore the raw asm harness, not a Kof
> program. Wiring free into the 57 existing alloc sites is a **G-4 concern**
> (they must free dead objects, which only the collector can identify), NOT
> G-1. This also means the ~260KB `.bss` leak is closed by G-4, not G-1.
> **G-2 header flags/mark bits + GC list (DONE 15/09, dev session):** every
> block RESERVED from the bump now enters the global gc-list
> (`.Lkof_gc_head`, LIFO, `gc_next`@16, flags=0) inside the same slice
> `NativeRiscvAsmRtB42`; a free+realloc does NOT re-enter (the block never left
> the list). New `kof_gc_dump` prints one `gc <size> <flags>` line per block —
> the plan's "`KOF_GC_DEBUG` dump" (pure asm has no env trigger, so the lever is
> an explicit call; honest and testable). Proof (qemu riscv64 **and** aarch64,
> never skip): `NativeRiscvGcListTest` 4/4 — alloc(16/32/64) ⇒ dump
> `gc 96 0`/`gc 64 0`/`gc 48 0` (total = align16+32, LIFO); free+realloc ⇒ a
> single `gc 96 0`; sabotage (drop the link) = 4/4 red with empty output.
> Slice-registry 8/8 (concat still byte-identical), cross riscv 44 + aarch 44
> (only the pre-existing `CastSaturation` red), `ArtifactSizeTest` 6/6,
> `KofGcE2ETest` 3/3 x86 untouched, `check_500` OK.
> **G-3 conservative mark riscv (DONE 15/09, dev session):** port of
> `kof_gc_mark`/`kof_gc_try_mark`/`kof_gc_mark_transitive` in the new slice
> `NativeRiscvAsmRtB43` over the G-0 32B header and the G-2 gc-list. Stack
> roots = `sp..s11` (riscv fp; 4KB fallback like x86) with `s0-s11` spilled so
> register-held pointers are visible; static roots = the interval between the
> NEW riscv-only local labels `.Lkof_heap_root_start`/`.Lkof_heap_root_end`
> emitted by `NativeArchEmitter` around the program `.data` (sentinel `.quad 0`
> at the opening). **Correction to the plan:** the x86 `kof_heap_root_end`
> (S-5 bugfix queue) was NOT needed — riscv now emits its own `.L`-local
> markers (outside `.symtab`, the G-1 ArtifactSizeTest lesson) and the interval
> DELIBERATELY excludes the bump arena (`_kof_heap`, in `.bss`), unlike x86
> which scans to `_end` because there the heap is mmap'd. So G-3 advanced
> WITHOUT the shared prerequisite; the labels are declared program-side in
> `RiscvSlices.programSideLocals()`. Proof (qemu riscv64 **and** aarch64, never
> skip): `NativeRiscvGcMarkTest` 2/2 — `_start` allocs A(static root)→B(stack)
> →C(unreachable)→D(via field0 of A), calls `kof_gc_mark`, dumps: `gc 96 1`/
> `gc 96 0`/`gc 96 1`/`gc 96 1` (D/A reachable, C not); sabotage (drop the
> transitive field walk) = 2/2 red with D=0. G-1/G-2 harnesses and the
> slice-registry 8/8 still green; `ArtifactSizeTest` 6/6 (labels `.L`-local, no
> symtab bloat); `KofGcE2ETest` 3/3 x86 untouched; `check_500` OK.
> **G-4 sweep + collect on alloc** — free-list receives the dead; `kof_gc_collect`
> ported (tick 4096 like x86); proof: LEAK test that today is
> impossible (alloc loop that would overflow the 260KB bump runs and memory
> does not grow monotonically — measure via G-1 memstats).
> **G-5 aarch64** — inherits everything via translator (the riscv directives/labels pass
> unscathed — same path as the S-4 prune; `amoadd.d`→`ldadd`, `amoswap.w`→`swpal` already
> translated, `NativeAarch64Translator.java:299`); gate: aarch suite under qemu +
> the G-4 leak test also on aarch. **G-1 ALREADY proved the inheritance for the
> free-list** (`NativeRiscvGcFreeListTest.freeListReusesSlotAndMemstatsCountsAarch64`).
> Each step: commit with the complete cross suite green + DOING.md on the line.
> Do NOT mix with S-5-x86/root_end (bugfix queue). G-0/G-1/G-2 move ahead
> without root_end; **G-3 also advanced** (emits its own riscv `.L`-local
> markers — did NOT need the x86 `kof_heap_root_end` of S-5); G-4 (sweep+
> collect) is next and does not depend on it either.
>
> **Beyond the GC (future, no scheduled step):** the maintainer directive of
> 15/09 ("all native code must also talk directly to barebones — bootable code
> for microcontrollers, legacy and UEFI with Kof") is recorded, decomposed and
> kept plan-only in `docs/development/future/PLAN-BAREMETAL-BOOT.md` (faces
> B-0…B-5: `kof_plat_*` HAL seam + freestanding profile + UEFI/BIOS/MCU). The
> MCU face depends on the collector (G-4/G-5) above.
>
> **Status:** `IN DEVELOPMENT (partial)` — **riscv64 + aarch64 with complete core (03/09)**: classes/arrays/List/strings/instanceof/switch/try-catch/FP/recursion in pure asm on both; advanced parity pending *(see re-audit 12/09 above — much of what was "pending" already runs under qemu; what remains has an honest gap code)*.
> **Version:** 0.2.6-beta · **Date:** 2026-09-03
> **Gap:** `NATIVE002` (riscv64 core ✅ 02/09; aarch64 core ✅ 03/09 via riscv→aarch64 translation; total x86 parity — JSON/DB/HTTP/concurrency/UI/net — pending on both).
> **Progress 03/09:** cross toolchain + qemu + **riscv64 + aarch64 codegen** (stack machine,
> `sp`=operands/`s11`/`x29`=frame pointer, model identical to x86_64) + **pure asm runtime** —
> `NativeRiscv64E2ETest 13/13` (`qemu-riscv64`) + `NativeAarch64E2ETest 13/13` (`qemu-aarch64`): println(String/Int), `var`, `if/else`,
> arithmetic/comparisons, **classes (virtual dispatch/fields/methods), arrays, List,
> switch, try/catch/throw, pattern matching (`switch String s`/`instanceof`/`as`),
> String methods, recursion**. See §2.3.
> **Decision (02/09):** runtime per arch **in pure assembly**, in the same style as x86_64
> (`NativeRuntime.generateRuntimeAssembly`) — **without C** ("Kof is Kof"; `kof-c-compiler`
> is another tool, not a runtime). The C compiled with cross gcc that was used on
> 02/09 as ABI validation was discarded: riscv64/aarch64 now emit pure asm
> runtime (bump allocator + raw syscalls `write`/`exit`, no PLT/libc) and link statically via `ld`, identical to the x86_64 model (no libc dependency).
> **Scope:** expand the `NativeBackend` (today `x86_64` in pure asm) to
> `riscv64` and `aarch64` Linux, preserving `frontend → Kof IR → backend` and
> `JVM/Native/JS` parity. This doc lives in `docs/` (not in `docs/future/`)
> because **there is already code in development** — it documents the real state and
> how to finish it.

## 1. Objective

Take the `NativeBackend` from single `x86_64` to multi-arch Linux without breaking
`KofPatternMatchingTest` 10/10 (`switch String s` / `instanceof` / `as` /
`checkcast`) and the `NativeE2ETest` suite.

It does not include macOS/Windows, advanced GC or complete native `kof.web` (see
"out of scope").

## 2. Real State (audit 01/09)

> **Rule of this folder:** `docs/` documents what **is in development**;
> `docs/future/` only what **is a future plan** (zero code). This item already has
> code, which is why it is here.

### 2.1 What IS ALREADY DONE (plumbing)

| Piece | State | Where |
|------|--------|------|
| Enum `Target.NATIVE_RISCV64` / `NATIVE_AARCH64` | ✅ | `Target.java` (values distinct from `NATIVE`; `NATIVE` remains = `x86_64`) |
| `Target.isNative()` covers the 3 natives | ✅ | `Target.java` |
| `Target.nativeArch()` → `x86_64`/`riscv64`/`aarch64` | ✅ | `Target.java` |
| CLI `native.risc`/`native.riscv64`/`native.riscv` → `NATIVE_RISCV64` | ✅ | `Main.java:364` |
| CLI `native.arm`/`native.aarch64`/`native.aarch` → `NATIVE_AARCH64` | ✅ | `Main.java:365` |
| `kof build`/`run` accept `native.risc`/`native.arm` | ✅ | `status.md:13-14` |
| Dispatch `emit()` → `emitRiscv`/`emitAarch64` | ✅ | `NativeBackend.java:210-215` |
| Cross toolchain invoked (as/ld + dynamic-linker + `-lc`) | ✅ | `NativeBackend.emitRiscv`/`emitAarch64` |
| Graceful fallback without toolchain (`keeping asm`) | ✅ | idem (try/catch `IOException`) |

**Practical consequence:** `kof build --target native.risc` **compiles and generates a
binary** (a stub that exits with `0`) — the toolchain/cross-as/ld pipeline already
works end to end.

### 2.2 What is NOT DONE YET (codegen — the real gap `NATIVE002`)

| Piece | State | Detail |
|------|--------|---------|
| **Real riscv64 lowering (core)** | ✅ complete 02/09 | `emitRiscv` emits the IR in asm: stack machine (`sp`=operand stack, `s11`=frame pointer, `ra`/`s11` saved in the frame — model identical to x86_64) + `.macro pop`; all core ops: literal/local/field/binary (int+FP+bitwise)/unary/condjump/jump/label/call (println/print/valueOf/String methods/collections/constructor/vtable virtual/FUNCTION/STATIC)/new_object/dup/pop/checkcast/instanceof/arrays/throw/try/catch/return. `NativeRiscv64E2ETest 13/13` |
| **Real aarch64 lowering (core)** | ✅ complete 03/09 | `emitAarch64` = **line-by-line translation of riscv64** (same model/lowering, ARMv8-A ISA: `sp`=stack/`x29`=frame pointer, `x30`/`x29` saved, `.macro pop` → `ldr`/`add`, `sp` already 16-aligned in `_start`, `str sp` via temp `x17`). `translateRiscvToAarch64` covers int+FP (`slt`/`sle`/`seqz`/`snez`/`sext.w`/`fcvt`/`fmv`/`fadd`/`feq`…), `andi`/`ori` via `movk x17`, `sd sp` via `mov x17,sp`. `NativeAarch64E2ETest 13/13` (`qemu-aarch64`) |
| Ops outside the riscv64/aarch64 core (JSON/DB/HTTP/concurrency/UI/net) | ❌ diagnostic `NATIVE002` | unknown ops emit the comment `# NATIVE002: op outside the happy path` (never a silent binary) |
| The 18 real `emit*` methods (x86_64) | ✅ | `emitBinary`/`emitOperation`/`emitMethod`/`emitConditionalJump`/vcall… — the complete path remains only on x86_64 |
| Extraction of `NativeBase` (common layout/`kof_alloc`/mangle) | ❌ does not exist | `NativeBackend` is still monolithic x86_64 (riscv/aarch64 reuse the same lowering via translation) |
| Runtime per arch (asm) | ✅ riscv64 + aarch64 core | `kof_alloc`(bump)/`kof_memcpy`/strings (literal/concat/equals/charAt/substring/contains/startsWith/endsWith/indexOf/toInt/length)/int-long-bool→string/print/objects (`init_object`/`instanceof`/super_table/vtables)/arrays (alloc/get/set/length+bounds)/List (new/add/get/set/size/contains/grow)/exceptions (`throw`/exc_chain/`null_error`/`bounds_error`) in **pure asm** riscv64 **and** aarch64 (raw syscalls, no libc; aarch64 via `translateRiscvToAarch64` — `adrp`+`add :lo12:`, `svc #0`, `and sp` skip, `str sp` via `x17`); `qemu-riscv64`/`qemu-aarch64` (see §2.3). |
| E2E `qemu` tests (aarch64/riscv64) | ✅ | `NativeRiscv64E2ETest` 42/42 + `NativeAarch64E2ETest` 42/42 (84 cross tests, measured by @Test + surefire 13/09) |
| CI with cross toolchains | ✅ exists (13/09) | job `cross-native` in `.github/workflows/ci.yml` (installs binutils-riscv64/aarch64 + qemu-user-static and runs the 2 suites; proved `success` in run 34732932745) |
| `backend-parity.md` columns per arch | ⚠️ partial | delta cited, separate `NATIVE_X86_64/AARCH64/RISCV64` columns pending |

**Practical consequence (SUPERSEDED — snapshot of 01/09):** it was valid for the original
plumbing stub; today (re-audit 12/09 at the top) riscv/aarch **execute
the logic** under qemu — 42+42 cross E2E tests, including real programs with
`println`/`instanceof`/`switch`/Map/Set/higher-order byte-identical to the JVM.
What remains honest in this table: `NativeBase` not extracted, per-arch columns
in `backend-parity.md` not separated, and the faces of ops outside the core
(JSON/DB/UI per specific arch).

### 2.3 Runtime in pure assembly per arch (decision 02/09)

**There is no C runtime in Kof.** The x86_64 native is pure asm end to end: the
runtime (`kof_alloc`, `kof_string_*`, `kof_instanceof`, …) is emitted in
assembly by `NativeRuntime.generateRuntimeAssembly()` and linked with
`ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` — libc enters via PLT
(`printf`/`snprintf`), without compiled C. (The `kof-c-compiler` module is another
tool — reimplementation of sectorC — and is **not** a runtime.)

Decision for riscv64/aarch64: **same path** — runtime emitted in pure
asm per arch + `ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc`. A C
compiled with cross gcc was used briefly (02/09) only to validate the
ABI/static linking on qemu; it was discarded from the architecture.

Installed toolchain (02/09, via `sudo apt`):
`binutils-riscv64-linux-gnu`, `binutils-aarch64-linux-gnu`, `qemu-user`,
`gcc-riscv64-linux-gnu`/`gcc-aarch64-linux-gnu` (debug),
`libc6-riscv64-cross`, `libc6-arm64-cross`.

Target pipeline (`emitRiscv`/`emitAarch64`):
```
Main.s  (program: kof_main + .data/.rodata sections)
      + riscv64/aarch64 asm runtime (emitted by NativeBackend)
   └─ <arch>-as → <arch>-ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc
   └─ qemu-<arch> → expected output (exit 0)
```

**🔗 Dynamic link ON DEMAND (link-by-use) — DONE 15/09 (maintainer directive
"liga dinamicamente"):** the cross link *was* static (pure asm, no libc) even
though the 02/09 decision above always said dynamic. `NativeCrossLink` implements
the realignment: the binary stays **static** while the *pruned* runtime calls no
libc symbol; the moment a libc-dependent capability enters (double→string/FLT001
via `snprintf`/`strtod`, `kof.db` via `.so`, …) `NativeArchEmitter` switches to
`<arch>-ld --allow-shlib-undefined [--no-relax riscv] --sysroot=<s> -dynamic-linker
/lib/ld-linux-<arch>.so.1 -o <bin> <obj> -lc`. This preserves the portability of
the current 84 cross binaries (nothing changes without a libc consumer) and
unblocks the libc gaps on demand. `--allow-shlib-undefined` is required (the
sysroot `libc.so.6` references `GLIBC_PRIVATE` loader symbols; the runtime
resolves them). Sysroot resolution: `KOF_CROSS_SYSROOT` env → system install
(`/usr/<arch>-linux-gnu`, no `--sysroot`) → `/tmp/opencode/x` (this host) →
none (stays static + stderr, R6). Proven by `NativeCrossDynamicLinkTest` (5/5,
riscv64 **and** aarch64 under qemu: `snprintf`+`write` resolved at runtime; the
static-link sabotage of the same harness fails with undefined `snprintf`). The
first production consumer (port of `RuntimeDtoa` to the cross runtime, closing
FLT001) is the next unit — the infra is in, the port is not.

Runtime details for riscv64/aarch64 (inc-0 02/09 + 03/09):
- allocation: **bump allocator + free-list** in `.bss` (no `mmap` — avoids
  problems with static qemu; x86_64 uses `mmap`+free-list, and riscv64/aarch64
  follow the model). G-1 (15/09) added the x86-style free-list + `kof_free` +
  `kof_memstats`; the bump remains the fallback and the collector (G-4) is what
  feeds the free list.
- strings: layout **identical to x86_64** — `[typeId@0 i32][super@4 i32]
  [vtable@8 ptr][len@16 i32][data@24 …]` (`KOF_STRING_TYPE_ID=1`).
- output: raw syscall `write(1, …)` (`a7=64` riscv / `x8=64` arm) + `exit` (`a7/x8=93`) — **static** binary, no libc/PLT.
- aarch64: **mechanical translation** of the riscv64 runtime (`riscv2arm.py` validated + `translateRiscvToAarch64` in `NativeBackend.java:3650`): `la`→`adrp`+`add :lo12:`, `ecall`→`svc #0`, `and sp` skip (sp already 16-aligned), `str sp` via `mov x17,sp`, `andi -16` via `movk x17`+`and`, `rem`→`sdiv`+`msub`, `slt/sle`→`cmp`+`cset`, FP `fcvt`→`scvtf`/`fmv`→`fmov`/`fadd`→`fadd`/`feq`→`fcmp`+`cset`.
- validation: `NativeRiscv64E2ETest 13/13` via `qemu-riscv64` + `NativeAarch64E2ETest 13/13` via `qemu-aarch64` (complete core).

What **remained** for the next increments:
- riscv64 + aarch64: Map/Set, higher-order (map/filter/reduce), JSON/DB/HTTP/concurrency/
  UI/net — total parity with x86_64 (same gap on both; today diagnostic `NATIVE002`).

## 3. Architecture (target)

> **Note:** the real implementation diverged from the original sketch (which proposed
> renaming to `NATIVE_X86_64` + `--arch` flag). The decision adopted was **distinct
> enum values** (`NATIVE` = x86_64, `NATIVE_RISCV64`, `NATIVE_AARCH64`) +
> **target name in the CLI** (`native.risc`/`native.arm`) — without the `--arch` flag and
> without renaming `NATIVE` (keeps compat). It follows the real decision.

```
Target enum:
  JVM, NATIVE (=x86_64), NATIVE_RISCV64, NATIVE_AARCH64, JS, ANDROID

IRModule → NativeBackend.emit (select by target):
  NATIVE          → x86_64 lowering (complete, 18 emit*)   [DONE]
  NATIVE_RISCV64  → emitRiscv   (complete core 02/09, 13/13) [DONE]
  NATIVE_AARCH64  → emitAarch64 (complete core 03/09, 13/13 via translation) [DONE]
  → (goal) extract NativeBase: ClassLayout, kof_alloc, mangle, resolveFieldOffset
```

`kof build --target native.risc|native.arm` (already works in dispatch).

## 4. Per-Arch Mapping (reference for the lowering)

| Aspect | x86_64 (current) | AArch64 | RISC-V 64 |
|---------|----------------|---------|-----------|
| **Assembler** | `as` GNU | `aarch64-linux-gnu-as` | `riscv64-linux-gnu-as` |
| **Linker** | `ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` (x86_64 uses PLT/libc) | `aarch64-linux-gnu-ld` **static** (raw syscalls, no `-lc`) | `riscv64-linux-gnu-ld` **static** (raw syscalls, no `-lc`) |
| **Regs args** | `rdi rsi rdx rcx r8 r9` | `x0 x1 x2 x3 x4 x5` | `a0 a1 a2 a3 a4 a5` |
| **Regs temp** | `rax rcx rbx r10` | `x9 x10 x11 x12` | `t0 t1 t2 t3` |
| **Ret** | `rax` | `x0` | `a0` |
| **Stack** | `pushq %rax` | `str x0,[sp,#-16]!` | `addi sp,-16; sd a0,0(sp)` |
| **Call** | `call sym` | `bl sym` | `call sym`/`jal` |
| **Vcall** | `mov 8(%rax),%rbx; add $idx*8,%rbx; mov (%rbx),%rbx; call *%rbx` | `ldr x9,[x0,#8]; add x9,x9,#idx*8; ldr x9,[x9]; blr x9` | `ld t0,8(a0); addi t0,idx*8; ld t0,0(t0); jalr t0` |
| **Cmp/Jmp** | `cmpq %rax,%rcx; je L; jmp M` | `cmp x1,x0; b.eq L; b M` | `sub t0,a0,a1; beqz t0,L; j M` |
| **String header** | `24B [typeId@0][vtable@8][len@16]` | same | same |
| **Syscall exit** | `mov $60,%rax; xor %rdi,%rdi; syscall` | `mov x8,#93; mov x0,#0; svc #0` | `li a7,93; li a0,0; ecall` |

`kof_alloc`/`kof_instanceof`/`kof_string_*` per arch with `KOF_STRING_TYPE_ID=1`
constant.

## 5. How to Finish (step by step — reflects the plumbing that already exists)

> The plumbing (enum + CLI + dispatch + toolchain) **is already ready**. What is
> missing is the codegen. Incremental order, without breaking `x86_64`:

1. **Extract `NativeBase`** — move to a common class/interface:
   `getLayoutForType`/`sanitize`/`mangle`/`resolveFieldOffset`/`collectStrings`
   (today in `NativeBackend`). `NativeBackend` (x86_64) inherits and stays the same.
   → validate `mvn test -Dtest=CompilerDriverTest` (no `emit` changes).
   *Depends on: nothing. Does not change the x86_64 binary.*

2. **Runtime per arch** — move `kof_alloc`/`kof_instanceof`/`kof_string_*`
   to asm per arch (today inline in x86_64 `NativeRuntime`); the `emit` of each
   target includes the correct `.s` section. *Depends on 1.*

3. **Minimal `Riscv64Backend`** — replace the `emitRiscv` stub with real lowering
   of the happy path: `String`/`println`/`instanceof String` + `switch String s`
   + `checkcast` no-op, using the mappings of table §4.
   → `qemu-riscv64` running `hello` (test `assume` if `qemu`/`riscv64-as`
   are absent, like `NativeE2ETest`). *Depends on 1,2.*

4. **Minimal `Aarch64Backend`** — idem for aarch64 (`qemu-aarch64`). *Depends on 1,2.*

5. **Collections + classes** — `kof_list_*`/`kof_map_*`/`kof_set_*` and
   `kof_instanceof` for user classes (the `Dummy` used in
   `KofPatternMatchingTest`). *Depends on 3,4.*

6. **Multi-arch E2E tests** — `NativeRiscv64E2ETest` + `NativeAarch64E2ETest`
   (`@Tag("slow")`, `assume` for absent `qemu`+cross-as) running the same source
   that `KofPatternMatchingTest` runs on x86_64/JS. *Depends on 5.*

7. **CI** — `x86_64` toolchains always; `aarch64`/`riscv64` with
   `if: cross-available` (do not break the pipeline when the toolchain is missing).
   *Depends on 6.*

8. **Docs** — `backend-parity.md`: separate columns
   `NATIVE_X86_64`/`AARCH64`/`RISCV64`; remove `NATIVE002` when 6 is green.
   *Depends on 6.*

**Definition of done:** `var x:Object="hello"; switch(x){case String s: println(s)}`
compiles and runs **identical** on `x86_64`, `aarch64 (qemu)`, `riscv64 (qemu)` and
`JS` (`typeof==="string"`); `KofPatternMatchingTest` 10/10 per arch.

## 6. Risks and Mitigation

- **16-byte Stack ABI** (ARM/RISC-V require aligned `sp`) → use 16-byte
  `str/ld` pairs.
- **RIP vs PC-relative reloc**: x64 `leaq sym(%rip)` → ARM `adrp`+`add` /
  RISC-V `auipc`+`ld`.
- **Cross toolchain absent** → `assume` skip, do not fail `mvn test`.
- **QEMU slow** → `NativeE2ETest` only fast x86_64; aarch64/riscv64 in
  `@Tag("slow")`.
- **Silent divergence**: the current stub "passes" by generating a binary → ensure
  the `NATIVE002` gap is a **clear diagnostic** (not a binary that silences the
  logic) until the lowering exists.

## 7. Out of Scope (stay in `docs/future/` / other docs)

- Advanced `GC` mark-sweep, `float/double` on Native (`F2D`), native `kof.web`
  `listen`, `macOS` Mach-O / `Windows` PE.
- **Bare-metal / bootable** (microcontroller, legacy BIOS, UEFI) — maintainer
  directive 15/09 ("all native code must also talk directly to barebones"). The
  pure-asm riscv64/aarch64 runtime (no libc) is the natural base, but the emitters
  are hardwired to Linux `ecall`/`syscall` and a `_start` ABI, and there is no
  freestanding link profile. Plan-only, not scheduled:
  `docs/development/future/PLAN-BAREMETAL-BOOT.md` (faces B-0…B-5).
