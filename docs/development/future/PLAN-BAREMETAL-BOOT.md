[English](PLAN-BAREMETAL-BOOT.md) | [Português](PLAN-BAREMETAL-BOOT.pt_BR.md)

# Bare-metal / bootable Kof — HAL seam + freestanding, legacy BIOS, UEFI and MCU

**Status:** Plan (future architecture) — **zero code**, no scheduled step
**Type:** future architecture / dependency record (NOT an implementation order)
**Date:** September 15, 2026
**Source:** `../../architecture/UNIVERSAL-PLATFORM-VISION.md` §8.2 (Native: "deploy/edge/systems") ·
`PLAN-TREE-SHAKING.md` §T3 (embedded/MCU route) · `docs/native-multiarch.md`
· maintainer directive (15/09): *"all native code must also talk directly to
barebones — bootable code for microcontrollers, legacy and UEFI with Kof"*.

> **Rule of this document** (same as `../../architecture/UNIVERSAL-PLATFORM-VISION.md`): it is a
> strategic/architecture record. It implements nothing, opens no front, changes no
> roadmap, moves no file, adds no dependency. The current state of Kof remains
> 100% intact. Anything here requiring a deep core change is recorded as a
> **future architectural dependency**, never as an action.
>
> This item stays in `future/` because **there is no code**: it is the honest
> boundary already measured by `PLAN-TREE-SHAKING.md` §T3 ("real embedded requires
> an RTOS/bare-metal backend — it stays in `future/` with no scheduled step").
> When the first face lands (a freestanding link producing a dynamic-free ELF), it
> **leaves `future/` for `docs/development/`** per the `future/README.md` rule.

---

## 1. The request (one sentence)

Every **native** backend must be able to target a machine with **no operating
system**: the same Kof frontend/IR/stdlib, but emitting **bootable** artifacts for
(a) **microcontrollers**, (b) **legacy BIOS (MBR/real mode)** and (c) **UEFI**,
with the runtime boundary (`write`/`exit`/allocation/time) supplied by a
**platform back-end per target** instead of Linux syscalls.

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

## 4. Decomposition (faces B-0…B-5), each independently provable

Following the `native-multiarch.md` G-0…G-5 style: each face has a **falsifiable
proof**, and later faces depend on earlier ones.

### B-0 — Platform seam (HAL) over the runtime · **enabling**
Route **every** environmental operation in the three native runtimes through the
`kof_plat_*` symbols of §3. The Linux implementation is the current code, renamed
(behavior byte-identical where the ABI allows).
**Acceptance:** the full native/cross suite is green **without** changing expected
outputs; a sabotage (make `kof_plat_write` a no-op) makes the output tests fail —
proving the seam is really used, not decorative.
**Depends on:** nothing. **Gap:** `NATIVE003` (proposed).

### B-1 — Freestanding link profile · **depends B-0**
`native --profile freestanding`: no `-lc`/`-dynamic-linker`, own `_start`/`_end`,
linker script (heap size and stack configurable), no libc. For x86_64 this removes
the hardcoded `-dynamic-linker … -lc -lm` (`NativeAssembler.java:36-49`); the
riscv/aarch path is already static.
**Acceptance:** `readelf`/ELF parser reports **no `PT_INTERP`**, no `DT_NEEDED`;
the binary still prints a Kof hello under qemu-user; a sabotage (re-add `-lc`)
is detected by the linker-script/`DT_NEEDED` assertion.
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

## 5. Honest dependencies, blockers and classification

| Face | Depends on | Cost | Gap on failure |
|------|-----------|------|----------------|
| B-0 HAL seam | — | M | `NATIVE003` |
| B-1 freestanding | B-0 | M | `NATIVE003` / link error |
| B-2 UEFI | B-1 | H | `NATIVE003` |
| B-3 legacy BIOS | B-1 (+16-bit entry) | H | `NATIVE003` |
| B-4 MCU (32-bit) | B-0, B-1, G-4/G-5 | R/H | `NATIVE002` (codegen) / `NATIVE003` |
| B-5 platform bodies | per face | M | `CONC003` for concurrency |

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
- **Not** scheduled: this stays in `future/` until B-1 produces a dynamic-free ELF,
  at which point the item moves to `docs/development/` with real state.

## 7. How to finish (order, once authorized)

1. **B-0** (HAL seam) — safe, reversible, suite-green; the "native talks to
   barebones" principle becomes real. Commit with the complete cross suite green.
2. **B-1** (freestanding ELF) — first bootable-adjacent, qemu-user proof.
3. **B-2** (UEFI/OVMF) **or B-3** (BIOS/MBR) — whichever the maintainer prioritises;
   both are H and independent of each other.
4. **G-4/G-5** (collector) — prerequisite for **B-4** (MCU).
5. **B-4** (32-bit MCU) — the largest, research-class step.
6. **B-5** — the platform bodies, one per face as each lands.
