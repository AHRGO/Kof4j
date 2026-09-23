[English](PLAN-BAREMETAL-BOOT.md) | [Português](PLAN-BAREMETAL-BOOT.pt_BR.md)

# Bare-metal / bootable Kof — HAL seam + freestanding, legacy BIOS, UEFI, MCU and privilege rings (ring0/ring1)

**Status:** **IN DEVELOPMENT — promoted from `future/` 22/09/2026** (maintainer order; `DECISIONS.md` §D-BAREMETAL-BOOT, queue 1.7) — enabling face **B-0** not landed yet (zero code)
**Type:** implementation plan (promoted; execution order of the bare-metal front)
**Date:** September 15, 2026 · **promoted:** September 22, 2026
**Source:** `../../architecture/UNIVERSAL-PLATFORM-VISION.md` §8.2 (Native: "deploy/edge/systems") ·
`PLAN-TREE-SHAKING.md` §T3 (embedded/MCU route) · `docs/native-multiarch.md`
· maintainer directive (15/09): *"all native code must also talk directly to
barebones — bootable code for microcontrollers, legacy and UEFI with Kof"*.

> **Promotion (maintainer order, 22/09/2026 — `DECISIONS.md` §D-BAREMETAL-BOOT):**
> the plan **left `future/` for `docs/development/`** and the front is **open**;
> the R12 gate (SYSTEMS first) is **overridden for this front by the maintainer's
> order** (same pattern as §D-UNIVERSAL). Ordered scope: **bare-metal with
> ring0/ring1 support** (x86_64 privilege levels) — the enabling face **B-0**
> (HAL seam) is the first executable step; the Kof-level surface to target ring1
> is a rule-6 decision and is **not** invented here. `PLAN-TREE-SHAKING.md` §T3
> ("real embedded = an RTOS/bare-metal backend of its own") remains the honest
> boundary this plan pays for.

---

## 1. The request (one sentence)

Every **native** backend must be able to target a machine with **no operating
system**: the same Kof frontend/IR/stdlib, but emitting **bootable** artifacts for
(a) **microcontrollers**, (b) **legacy BIOS (MBR/real mode)** and (c) **UEFI**,
with the runtime boundary (`write`/`exit`/allocation/time) supplied by a
**platform back-end per target** instead of Linux syscalls. On x86_64 the
privilege model is explicit: the runtime boots at **ring0** and the platform
supports **ring1** domains (privilege seam B-6) — never a silent switch.

The directive is wider than a new output format: it is the principle that *native
code talks to a "barebones" surface*, of which the current Linux syscall surface
is **one implementation among several**.

## 2. Measured state — what blocks bare-metal today (15/09)

These are facts from the code, not estimates:

1. **The runtime is hard-wired to Linux syscalls.** The riscv/aarch runtime issues
   raw `ecall` with Linux syscall numbers (`write`=64, `exit`/`exit_group`=93/94,
   `mmap`, `futex`, `clone`, `clock_gettime`); the x86 runtime issues `syscall`
   (SYS_futex/SYS_mmap/SYS_write). There is **no indirection layer** — every slice
   emits the syscall inline.
2. **x86 native is dynamically linked and needs libc.**
   `NativeAssembler.java:36-49` (measured) hardcodes
   `-dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc -lm` and, when used,
   `-l:libsqlite3.so.0`/`-l:libmariadb.so.3`/`-l:libpthread.so.0`. A boot image has
   no `ld.so` and no libc. The riscv/aarch path already links **static without
   libc** (`riscv64-linux-gnu-ld --no-relax`, `aarch64-linux-gnu-ld --gc-sections`)
   — the closest existing base, but still with Linux `ecall`.
3. **The entry point is `_start` with a Linux ABI assumption.** The emitters
   produce `_start` (`NativeArchEmitter`), and riscv exit uses `exit_group`(94) to
   kill scheduler threads — meaningless on bare-metal. BIOS/UEFI/MCU need
   different entries (`0x7C00` real-mode boot sector, `efi_main`, a vector-table
   `Reset_Handler`).
4. **Allocation is a fixed `.bss` bump arena + free-list.** `_kof_heap`
   (262 144 B, `NativeRiscvAsmRtB4.java:386`) with the OOM guard at `_kof_heap_end`;
   an MCU has a few KB of SRAM, so the pool must be **sized by the linker script**,
   not hardcoded, and the collector (G-4/G-5 of `native-multiarch.md`) must be on.
5. **Codegen exists only for 64-bit targets.** `Target.NATIVE` (x86_64),
   `NATIVE_RISCV64`, `NATIVE_AARCH64` (`Target.java`). MCUs are predominantly
   **32-bit** (ARM Cortex-M Thumb-2, riscv32) — a codegen gap, classified **C** by
   `../../architecture/UNIVERSAL-PLATFORM-VISION.md` §8.2(e) ("RISC/ARM codegen").
6. **The GC mechanism is frozen (rule 6).** `PLAN-TREE-SHAKING.md` §7 states T1b
   touching the GC root-scan "touches the GC mechanism (frozen)". The HAL seam
   below must therefore **not** go through the GC.

## 3. The architectural principle — a platform seam (HAL), not a new language

The directive is satisfied by **one** architectural move, not by three separate
backends: introduce a thin **platform surface** that every runtime environmental
operation crosses, with pluggable implementations.

```
Kof program (frontend, IR, stdlib dispatch)      ← unchanged, one language
        │
   native runtime (calls kof_plat_* only)        ← the seam
        │
   ┌────────────┬──────────────┬──────────────┬───────────────┐
 Linux        UEFI          legacy BIOS      MCU (Cortex-M/riscv32)
 kof_plat_*   kof_plat_*    kof_plat_*       kof_plat_*
 = syscalls   = BootSvc     = int 0x10/13    = UART/semihosting
```

Candidate surface (minimal, honest — only what the runtime already needs):

| Symbol | Used today | Linux impl (now) | Bare impl (per target) |
|--------|-----------|------------------|------------------------|
| `kof_plat_write` | print/log | `write`(64) | UEFI `ConOut->OutputString`; BIOS `int 0x10` tty; UART/`SYS_WRITE0` |
| `kof_plat_exit` | panic/exit | `exit`/`exit_group` | UEFI `BootServices->Exit`; BIOS `hlt`; MCU `bkpt`/loop |
| `kof_plat_alloc` | heap | bump in `.bss` | same, with size from linker script |
| `kof_plat_time` | clock/sched | `clock_gettime` | UEFI `BootServices->GetTime`; BIOS PIT/RTC; MCU SysTick |
| `kof_plat_sync` / `kof_plat_thread` | spawn (pthread/clone) | `futex`/`clone` | **absent** (single-core) → `CONC003` gap, never silent |

**Key property (testable):** on Linux the seam is a **zero-cost, semantics-identical
rename** of the current syscalls, so the entire existing native suite (x86 65/0,
riscv 44 + aarch 44 under qemu, `KofGcE2ETest`) must stay green. That is the
acceptance criterion that makes this a safe enabling refactor rather than a
rewrite.

**Naming/decision needed (maintainer, rule 6):** the directive "no per-domain
target" (`../../architecture/UNIVERSAL-PLATFORM-VISION.md` §16) is **not** violated if this is a
**profile of Native** (`native --profile freestanding|uefi|bios|mcu`) rather than
four new `Target` enum values — analogous to how `native.risc`/`native.arm` are
arch variants, not new languages. This document takes **no** position; it records
both options.

## 4. Decomposition (faces B-0…B-6), each independently provable

Following the `native-multiarch.md` G-0…G-5 style: each face has a **falsifiable
proof**, and later faces depend on earlier ones.

### B-0 — Platform seam (HAL) over the runtime · **enabling**
Route **every** environmental operation in the three native runtimes through the
`kof_plat_*` symbols of §3. The Linux implementation is the current code, renamed
(behavior byte-identical where the ABI allows).
**Acceptance:** the full native/cross suite is green **without** changing expected
outputs; a sabotage (make `kof_plat_write` a no-op) makes the output tests fail —
proving the seam is really used, not decorative.
**Slice 1 (LANDED 22/09, lane `baremetal` 9092):** the seam exists and is routed
on the **riscv64 runtime** (aarch64 inherits it line-by-line via the translator)
for the core surface of single-threaded programs — `kof_plat_write`,
`kof_plat_writev`, `kof_plat_exit`, `kof_plat_exit_group`, `kof_plat_time`,
`kof_plat_time_mono`, `kof_plat_sleep`, `kof_plat_random`, `kof_plat_thread_id`
(call sites: Rt0 print/panic, `_start` exit_group in both emitters, B0 log/time,
Obs entropy+clock, B4/B48 gettid, B25/B25b/B27 entropy, B42 gc-print writes).
Proof: riscv64 E2E 54/54 + aarch64 E2E 53/53 under qemu (byte-identical outputs),
slice-registry model green, and `PlatformSeamSabotageTest` — sabotaging the body
of `kof_plat_writev` to `ret` makes the program print **nothing** on both
targets (the seam is load-bearing, not decorative). **Next slices:** x86_64
runtime (its `call`/libc sites), spawn/futex (`kof_plat_thread`/`kof_plat_sync`)
and net sockets (`kof_plat_net_*`); `NATIVE003` stays reserved for the bare
profiles (B-1+).

**Slice 2 (LANDED 22/09, lane `baremetal` 9092):** the x86_64 runtime crosses
the same seam — new slice `RuntimePlat` implements `kof_plat_write`,
`kof_plat_writev`, `kof_plat_exit`, `kof_plat_exit_group` (Linux = raw syscall
1/20/60/231) and the call sites are routed: `kof_print`/`kof_print_string`
(write), `kof_panic`/`kof_throw_string`-panic/`kof_process_exit`/JSN004 fatal
(exit) and the `_start` epilogue (`exit_group`). Proof: x86 host sabotage test
(`as`/`ld` dynamic, same production flags — sabotage of `kof_plat_write` makes
the output vanish), `NativeE2ETest` 67/67, `KofConcurrency2Test` 48/48,
`JsonE2ETest` 18/18, `JsonCompleteE2ETest` 10/10, `KofDbE2ETest` 27/27 (4
skips), `NullSafetyE2ETest` 14/14, `ProcessResultContentE2ETest` 4/4,
`NativeNullablePrimitiveContractE2ETest` 42/42, `ArtifactSizeTest` 6/6 with the
 x86 baseline re-measured (39.232→39.304 B, 84→88 syms). **Next slices:**
 x86_64 env surface (time/sleep/mono, entropy, gettid), spawn/futex
 (`kof_plat_thread`/`kof_plat_sync`), net sockets (`kof_plat_net_*`), then B-1.
 **Depends on:** nothing. **Gap:** `NATIVE003` (proposed).

**Slice 3a (LANDED 22/09, lane `baremetal` 9092):** the x86_64 **environment
surface** crosses the seam — `RuntimePlat` gains `kof_plat_time`/`time_mono`
(clock_gettime 228, `rdi`=ts), `kof_plat_sleep` (nanosleep 35), `kof_plat_random`
(getrandom 318) and `kof_plat_thread_id` (gettid 186); the sites are routed:
`kof_now`/`kof_time_sleep`, the log timestamp, `kof_obs_mono_nanos`/
`kof_obs_epoch_micros`, `kof_random_bool`/`kof_random_double`,
`kof_sec_random_hex`/`_int`/`kof_sec_random_bytes`, the ORM migration clock
(`RuntimeOrm1`/`RuntimeOrmMysqlDdl`) and the `_start` gettid. **Bug caught while
hunting (Q4), not shipped:** the first impl swapped the `clock_gettime` raw-syscall
args (on x86_64 `rdi`=clockid, `rsi`=ts — not the reverse); `NativeLogE2ETest`,
`KofSecurityTest.jwtNative` and `KofTimeE2ETest` red-flagged it (JWT read
"expired", log dated 1970) and the fix is proven. Bonus: the log timestamp read
`tv_usec` as if it were nsec (ms always 0) — the seam's real **timespec** closes
that latent bug. Proof: 371/0F (`KofTimeE2ETest` 42, `KofLogE2ETest` 11,
`NativeLogE2ETest` 7, `KofObservabilityTest` 12, `KofRandomTest` 15,
`KofRngTest` 11, `KofUuidTest` 14, `KofSecurityTest` 41, `KofSecurityG9Test` 3,
`KofConcurrency2Test` 48, `NativeE2ETest` 67, `KofOrmE2ETest` 63/18 skips,
`KofDbE2ETest` 27/4 skips, `PlatformSeamSabotageTest` 4/4), x86 baseline
re-measured (39.304→39.512 B, 88→93 syms). **Next slices:** spawn/futex x86
(`kof_plat_thread`/`kof_plat_sync`), net sockets (`kof_plat_net_*`), then B-1.
**Depends on:** nothing. **Gap:** `NATIVE003` (proposed).

**Slice 3b-i (LANDED 22/09, lane `baremetal` 9092):** the x86_64 **futex** sites
cross the seam — `kof_plat_sync` (futex 202) added to `RuntimePlat` and the 19
`SYS_futex` sites in `RuntimeScheduler`/`RuntimeChannel`/`RuntimeMemory` routed to
it (the allocation lock waits/wakes through the seam, so every native binary
exercises it). Proof: `KofConcurrency2Test` 48/48, `SpawnE2ETest` 10/10,
`SpawnAwaitBlockE2ETest` 5/5, `ProcessSpawnE2ETest` 4/4, `NativeE2ETest` 67/67,
`PlatformSeamSabotageTest` 4/4; x86 baseline re-measured (39.512→39.544 B,
93→94 syms). **Next:** `kof_plat_sync` on the riscv seam + `kof_plat_thread`
(create) on both ISAs, then net sockets (`kof_plat_net_*`) and B-1.

**Slice 3b-ii (LANDED 22/09, lane `baremetal` 9092):** synchronization and thread
creation cross the seam. **riscv64:** `kof_plat_sync` (futex 98) added to the seam
and the 3 `SYS_futex` sites of `NativeRiscvSpawn` routed (WAKE, await-WAIT and
join-WAIT) — aarch64 inherits through the translator. **x86_64:**
`kof_plat_thread_create` added to `RuntimePlat` as a tail `jmp pthread_create`
(Linux+libc) and the 2 call sites (`RuntimeConcurrency`, `RuntimeScheduler`)
routed to it. **riscv `clone` (220) — LANDED 22/09 (sessão 9092), deferral
caducado:** the earlier note that the split-flow "cannot cross a call-based
seam" was too conservative. On riscv `call` does **not** push (the return
address is a register), so the child returns from the seam straight to
`kof_spawn_result`, which swaps `sp` *before* calling the trampoline — the
parent's frame is never touched. `NativeRiscvSpawn` now sets the args and
`call kof_plat_thread_create` (seam in `NativeRiscvAsmRt0`, `li a7,220`; aarch64
inherits through the translator); no raw `li a7, 220` is left in the spawn.
Guard: `PlatformSeamSabotageTest#riscvThreadCreateRoutesThroughSeam`. Proof:
`PlatformSeamSabotageTest` 5/5, `NativeRiscv64E2ETest` 54/1 skip,
`NativeAarch64E2ETest` 53/1 skip, `KofConcurrency2Test` 48, `SpawnE2ETest` 10,
`ArtifactSizeTest` 6, slice-registry 7 → 182/0F/2 skip (hello baselines unchanged:
riscv/aarch still 136,824 B/45 syms and 202,168 B/45 syms; `kof_plat_sync` is
pruned from the hello). **Next:** net sockets (`kof_plat_net_*`), then B-1.

**Slice 4a (LANDED 22/09, lane `baremetal` 9092):** the x86_64 **net** surface
crosses the seam — `RuntimePlat` gains `kof_plat_read`/`kof_plat_close` (0/3) and
`kof_plat_net_socket`/`connect`/`bind`/`listen`/`accept`/`send` (41/42/49/50/43/
44); routed: `kof_net_socket`/`bind`/`listen`/`accept`/`read`/`write`/`close`
(`RuntimeNet`) and the MySQL connect (`RuntimeDb3`). **Pruning fix:** a slice is
*class+method*, so the single `emitPlatSeam` linked **all** seam symbols into
every binary; `RuntimePlat` was split by family (write/time/random/thread-id/
sync/io/net) and the hello **shrinks** — 39.544→39.432 B, 94→91 syms. Proof:
`KofNetTest` 4/4, `KofHttpE2ETest` 8/8, `KofHttpNativeCircuitE2ETest` 2/2,
`KofHttpNativeRetryE2ETest` 3/3, `KofHttpNativeTimeoutE2ETest` 3/3,
`KofHttpResilienceE2ETest` 3/3, `KofHttpServerTest` 8/8,
`KofHttpNativeResilienceCrossTest` 4/4, `KofDbE2ETest` 27/4 skips,
`NativeE2ETest` 67/67, `NativeRuntimeSliceRegistryTest` 7/7,
`RuntimeSourceLoaderTest` 6/6, `PlatformSeamSabotageTest` 4/4, `ArtifactSizeTest`
6/6, `KofConcurrency2Test` 48/48, `KofTimeE2ETest` 42/42. **Next:** the riscv64
net seam (`NativeRiscvHttpCore` socket 198 / connect 203 + the http-support
sites), then B-1.

**Slice 4b (LANDED 22/09, lane `baremetal` 9092):** the riscv64 **net** surface
joins the seam — `kof_plat_read` (63), `kof_plat_close` (57),
`kof_plat_net_socket` (198) and `kof_plat_net_connect` (203) added to
`NativeRiscvAsmRt0`, and the 9 sites of `NativeRiscvHttpCore` routed (socket,
connect, `write` reuse, `read`, 5×`close`) — all inside functions that already
save `ra`, so the RA guard stays green; aarch64 inherits through the translator.
Only the referenced members were added: `bind`/`listen`/`accept`/`send` have no
riscv caller yet, so their seam entries are **deliberately absent** (added when a
riscv server path exists — never silent). Proof: `NativeRiscv64E2ETest` 54/1
skip, `NativeAarch64E2ETest` 53/1 skip, `KofHttpNativeResilienceCrossTest` 4/4,
`KofHttpNativeTimeoutE2ETest` 3/3, `KofHttpNativeCircuitE2ETest` 2/2,
`KofHttpNativeRetryE2ETest` 3/3, `KofHttpE2ETest` 8/8, `KofNetTest` 4/4,
`CrossRuntimePortsE2ETest` 2/2, `PlatformSeamSabotageTest` 4/4,
`NativeRuntimeSliceRegistryTest` 7/7, `ArtifactSizeTest` 6/6 (riscv/aarch
baselines unchanged: the new seam members are pruned from the hello). **Next:**
B-1 (freestanding link profile).

### B-1 — Freestanding link profile · **depends B-0**
`native --profile freestanding`: no `-lc`/`-dynamic-linker`, own `_start`/`_end`,
linker script (heap size and stack configurable), no libc. For x86_64 this removes
the hardcoded `-dynamic-linker … -lc -lm` (`NativeAssembler.java:36-49`); the
riscv/aarch path is already static.
**Acceptance:** `readelf`/ELF parser reports **no `PT_INTERP`**, no `DT_NEEDED`;
the binary still prints a Kof hello under qemu-user; a sabotage (re-add `-lc`)
is detected by the linker-script/`DT_NEEDED` assertion.
**B-1 pre-step (LANDED 22/09, lane `baremetal` 9092):** `kof_plat_thread_create`
was moved out of the `sync` slice into its own slice — a program that touches the
allocation-lock futex (`kof_plat_sync`) no longer drags `pthread_create` into the
link, which is exactly what the freestanding profile needs (and a pruning win for
every sync-only binary). Proof: `NativeRuntimeSliceRegistryTest` 7/7,
`ArtifactSizeTest` 6/6 (hello unchanged: 39,432 B/91 syms), `KofConcurrency2Test`
48/48, `SpawnE2ETest` 10/10, `PlatformSeamSabotageTest` 4/4.

**B-1a (LANDED 22/09, lane `baremetal` 9092): freestanding link profile
end-to-end.** New `NativeProfile {HOST, FREESTANDING}` (`nat/NativeProfile.java`)
threaded through `CompilerDriver.nativeProfile` → `CompilerDriverState.compile(src,
out, target, profile)` → `CompilerPipeline.selectBackend` → `NativeBackend.profile(p)`
→ `NativeAssembler.assemble(..., freestanding)`. In freestanding mode x86_64 links
`ld -o bin obj --unresolved-symbols=ignore-all` (no `-dynamic-linker`, no `-lc`/libs).
Capabilities that need libc by use (db/orm/mysql/concurrency/pow/ffi) are **refused**
at compile time with the `NATIVE003` diagnostic — never a silent dynamic fallback.
**Why `--unresolved-symbols=ignore-all`:** the x86 runtime slices are coarse and
carry libc calls from functions *not reached* in the same object
(`snprintf`/`strtod` from dtoa, `pthread_*`, `usleep`); on the host they resolve via
libc, here they stay unresolved and are only fatal if the program reaches the libc
path — which the refused capabilities cover. Removing the refs at the source
(per-function sections + `gc-sections`) is the **B-1b** follow-up.
> **Face (i) LANDED (22/09, lane 9093 — claimed after coordination with 9092):** the panic path no longer reaches the generic dispatcher — `kof_panic` now prints via `kof_println_string` (every panic message is a static `.asciz`). Measured: hello/plain/numeric programs without floats carry no `snprintf`/`strtod` anymore (`nm -u` of the gc-linked binary: present on the old code, gone on the fix). Proof: new `FreestandingLinkE2ETest.freestandingHelloCarriesNoLibcFormatRefs` (RED on the old code) + class 4/4 + native battery 101/0F (`NativeE2ETest` 67, `NullSafety` 14, `ArrayBounds` 10, catches/prints 10). Face (ii) — libc-free dtoa through the seam — still OPEN, owner lane 9092.

**B-1b (LANDED 22/09, lane `baremetal` 9092): the freestanding x86_64 link is
libc-free and closes without `--unresolved-symbols=ignore-all`.** With face (i)
landed, the only remaining libc-carrying functions were unreachable in the
object; the freestanding path now runs the runtime text through
`NativeCrossSections.sectionizeTextFunctions` (the cross pass, §445 lesson) and
links `ld -o bin obj --gc-sections -e _start`. `nm -u` of the result shows no
`snprintf`/`strtod`/`pthread_*`/`usleep`. **Acceptance met:** no `PT_INTERP`, no
`DT_NEEDED`, the binary still prints the JVM-oracle value, `HOST` control keeps
both, and freestanding+`spawn` is refused with `NATIVE003` — all without any
unresolved-symbol escape hatch. Proof: `FreestandingLinkE2ETest` 4/4 + battery
116/0F (`LinkByUseTest` 3, `PlatformSeamSabotageTest` 5,
`NativeRuntimeSliceRegistryTest` 7, `ArtifactSizeTest` 6, `NativeE2ETest` 67,
`NullSafetyE2ETest` 14, `ArrayBoundsSafetyE2ETest` 10). **Float-print refusal
(LANDED 22/09, lane 9092):** a freestanding `println(Double)`/`Float` now fails
with the named `NATIVE003` diagnostic (the freestanding link converts surviving
libc refs — `snprintf`/`strtod` of dtoa — into the coded refusal) instead of a
raw `ld` "undefined reference"; proof `FreestandingLinkE2ETest` 5/5 (new
`freestandingRefusesFloatPrintWithDiagnostic`). **Remaining for B-1:** the CLI
surface (`--profile`), then the linker script with configurable heap/stack and
`_end`.

**B-1c — libc-free decimal conversion (dtoa) for `Float`/`Double` (opened
23/09, maintainer's decision: "precisamos dele baremetal").** The maintainer
ordered the *complete* path (not the incremental unblock): bare-metal must print
`Bool`/`Troolean`/`Int`/`Long`/`String` **and** `Float`/`Double` with JVM parity,
removing the `NATIVE003` float-print refusal. **Measured 23/09:** `Bool`
(`true`/`false`) and `Int`/`String` already print bare-metal (static strings);
`Troolean` (`true`/`false`/`null`) is blocked only because `kof_box_to_string`
statically references `kof_double_to_string`/`kof_float_to_string` (tags 4/5) —
`gc-sections` cannot prune a *code* reference, so the box printer keeps the dtoa
alive; a multi-function program exposed a second bug (fixed: the sectionize pass
moved program functions and broke DWARF range expressions). `Float`/`Double`
need `RuntimeDtoa`'s `snprintf("%.*e")`/`strtod` replaced.

- **Parity constraint (the hard part).** The host x86_64 picks the shortest
  precision by looping `snprintf("%.*e", p)` + `strtod` (glibc, round-half-even)
  and takes the first `p` that round-trips. The bare-metal converter MUST
  reproduce that digit selection, or the host golden changes — a silent
  regression (Freeze rule 3). So the libc-free unit is a **correctly-rounded
  `%.*e` formatter + a correctly-rounded `strtod`**, not an arbitrary
  shortest-dtoa (e.g. Ryū) whose tie-breaking may differ from glibc.
- **Recon first (cheap, no asm):** fix the algorithm and *prove* parity in Java
  against the JVM oracle (and against the glibc-host output) over a large corpus
  (random bits + edges: subnormals, `±0.0`, `1e308`, `5e-324`, the `1e-3`/`1e7`
  JDK thresholds, exact ties) **before** writing any assembly. No asm is written
  until the digit selection is pinned.
- **Suggested slices (each with proof):** (1) recon/parity harness in Java;
  (2) exact big-integer core (add/sub/mul-small/shl/cmp/divmod-10) in the x86
  runtime + end-to-end corpus test; (3) `kof_fmt_sci` (correctly-rounded `%.*e`)
  replacing `snprintf`; (4) `kof_parse_double` (correctly-rounded) replacing
  `strtod`; (5) wire `kof_double_to_string`/`kof_float_to_string`, delete the
  `NATIVE003` float-print refusal, and prove `FreestandingLinkE2ETest` green with
  a `println(Double)`/`Math.PI` corpus == JVM oracle and no `snprintf`/`strtod`
  in `nm -u`.
- **Scope note:** the same `kof_dtoa_format` cross-debt (riscv/aarch use libc
  `snprintf`/`strtod`) is out of this face; the x86 unit is the reference.

**Depends on:** B-0. **Classification:** M (medium).

### B-2 — UEFI (x86_64, and later aarch64) · **depends B-1**
Emit an EFI application: entry `efi_main(EFI_HANDLE, EFI_SYSTEM_TABLE)`; output via
`SystemTable->ConOut->OutputString`; memory via `BootServices->AllocatePool`/`Exit`.
Produce PE/COFF (`objcopy -O efi-app-x86_64` or a native PE emitter) and place it
on a FAT EFI System Partition as `\EFI\BOOT\BOOTX64.EFI`.
**Acceptance:** **qemu + OVMF (TianoCore)** boots a Kof "hello" and prints it via
Console Output; a Kof `main` returning non-zero maps to
`BootServices->Exit` status. **Depends on:** B-1. **Classification:** H (high).
**Toolchain:** OVMF firmware + `qemu-system-x86_64`.

### B-3 — Legacy BIOS (MBR / real mode) · **depends B-1**
A 512-byte boot sector (magic `0x55AA`) that loads the Kof payload (custom sector
loader or Multiboot) and prints via BIOS teletype `int 0x10, ah=0x0E`; memory via
`int 0x15, eax=0xE820`. Requires a **16-bit real-mode** (or a tiny 32-bit
protected-mode stub) entry — a codegen gap of its own.
**Acceptance:** `qemu-system-x86_64 -drive format=raw,file=kof.img` boots and prints
the Kof hello from the boot sector; a sabotage (break `0x55AA`) → qemu reports
"no bootable device".
**Depends on:** B-1. **Classification:** H (high) — the real-mode entry is the
dominant cost.

### B-4 — Microcontroller (ARM Cortex-M Thumb-2 / riscv32) · **depends B-1**
New 32-bit codegen (`Target`/arch variant), a linker script with a **vector
table** (`Reset_Handler`), no OS; output via UART or ARM **semihosting**
(`arm-none-eabi` / `probe-rs`). Heap from the linker script (KB-scale), so the
collector (G-4/G-5) is a hard prerequisite for anything long-running.
**Acceptance:** under `qemu-system-arm -M mps2-an385` (Cortex-M3) **or**
`qemu-system-riscv32 -M virt` with semihosting, a Kof hello prints over the
semihosting channel; the vector-table reset path is asserted in the image.
**Depends on:** B-0, B-1, and the **collector** `native-multiarch.md` G-4/G-5.
**Classification:** R/H (research/high) — this is the §T3 "project of its own".

### B-5 — Platform back-ends (the per-target `kof_plat_*` bodies) · **per face**
Implement the §3 table for UEFI (B-2), BIOS (B-3) and MCU (B-4). On bare-metal,
`spawn`/`select`/`await` cannot be provided honestly → **`CONC003` gap** (never a
silent stub), consistent with the JS `CONC003` precedent.

### B-6 — x86_64 privilege rings: ring0 kernel + ring1 domains · **depends B-1 (+ boot path B-2/B-3)**
Enter long mode with a Kof-owned **GDT** (ring0 + ring1 code/data, TSS) and
**IDT**; the runtime executes at **CPL0**. A minimal, documented transition
primitive lets a Kof function run at **CPL1** and return (inter-privilege
`iretq` + TSS `rsp0` for the trap back to ring0), so privileged instructions
(`cli`/`hlt`/`lgdt`) are **refused by the CPU** in the ring1 domain — the
falsifiable proof that the level is real, not a label.
**Acceptance:** under `qemu-system-x86_64`, (a) boot reaches CPL0 and prints;
(b) a controlled entry executes a Kof function at CPL1 and returns with state
intact; (c) a privileged instruction attempted in the ring1 domain raises `#GP`
(caught by the ring0 handler and reported, never a silent hang); (d) sabotage:
removing the ring1 GDT descriptor makes the CPL1 entry fault — proving the
level is enforced, not decorative.
**Depends on:** B-1 + one x86 boot path (B-2 or B-3). **Classification:** H (high).
**Surface:** the Kof-level API to *target* a ring1 domain is a **rule-6 decision**
(maintainer); this face lands the enabling machinery first.

## 5. Honest dependencies, blockers and classification

| Face | Depends on | Cost | Gap on failure |
|------|-----------|------|----------------|
| B-0 HAL seam | — | M | `NATIVE003` |
| B-1 freestanding | B-0 | M | `NATIVE003` / link error |
| B-2 UEFI | B-1 | H | `NATIVE003` |
| B-3 legacy BIOS | B-1 (+16-bit entry) | H | `NATIVE003` |
| B-4 MCU (32-bit) | B-0, B-1, G-4/G-5 | R/H | `NATIVE002` (codegen) / `NATIVE003` |
| B-5 platform bodies | per face | M | `CONC003` for concurrency |
| B-6 privilege rings (x86_64) | B-1 + B-2/B-3 | H | `NATIVE003` / `#GP` handled |

**Cross-cutting blockers (real):**
- **GC collector (G-4/G-5)** must land before B-4 (KB-scale RAM). Not needed for
  B-1/B-2/B-3 hello-level proofs.
- **32-bit codegen** (B-3 real mode, B-4) is genuinely new; do not promise it via
  the translator (riscv64→aarch64 only).
- **Rule 6 / frozen GC:** B-0 must rename the syscall boundary **without** touching
  `kof_gc_mark`/`sweep` internals.
- **Toolchains are external** (OVMF, `arm-none-eabi`, `probe-rs`, `qemu-system-*`);
  the CI pattern of `native-multiarch.md` face (5) (install the toolchain so the
  test **runs**, never silently skips) applies verbatim.

## 6. What this is NOT (non-goals)

- **Not** a new language or a per-domain target: one frontend/IR/stdlib, one
  `Native` target with **profiles** (or arch variants) — decision left to the
  maintainer (§3).
- **Not** a promise of libc/OS features on bare-metal: files, network, threads,
  signals are **absent** and reported as gaps (`CONC003`, `NET…`), never stubbed.
- **Not** an RTOS. Scheduling, drivers beyond serial/framebuffer, and filesystems
  are user/FFI territory.
- **Not** a microkernel/hypervisor: the B-6 rings are a **minimal privilege
  seam** (kernel at ring0, one ring1 domain), not a scheduler, IPC or VM layer.
- **Not** scheduled as before: **promoted 22/09/2026** (maintainer order,
  `D-BAREMETAL-BOOT`) — execution follows §7; R12 is overridden for this front.

## 7. How to finish (order, once authorized)

1. **B-0** (HAL seam) — safe, reversible, suite-green; the "native talks to
   barebones" principle becomes real. Commit with the complete cross suite green.
2. **B-1** (freestanding ELF) — first bootable-adjacent, qemu-user proof.
3. **B-2** (UEFI/OVMF) **or B-3** (BIOS/MBR) — whichever the maintainer prioritises;
   both are H and independent of each other.
4. **B-6** (ring0/ring1, x86_64) — on top of the boot path chosen in 3 (ordered
   scope of `D-BAREMETAL-BOOT`; the Kof-level ring surface is decided with the
   maintainer before any syntax/API lands — rule 6 + Simplicity Law).
5. **G-4/G-5** (collector) — prerequisite for **B-4** (MCU).
6. **B-4** (32-bit MCU) — the largest, research-class step.
7. **B-5** — the platform bodies, one per face as each lands.
