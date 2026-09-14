[English](specification-gaps-0.3.0-snapshot.md) | [Português](specification-gaps-0.3.0-snapshot.pt_BR.md)

// Specification Gaps — Kof 0.3.0-beta

This document lists known gaps in the Kof language/Compiler 
(updated on 08/09/2026). Open gaps are tracked on the roadmap 
and may block or limit functionality on some target.

## Gap conventions

- **Codes**: short prefixes such as `R6`, `HW001`, `CONC001`, etc.
- **Status**: `open`, `closed`, `partial`
- **Targets**: `JVM`, `Native`, `JS`
- **Reference**: every gap must have issues/references in the tests and docs

---

## Gap R6 — putfield of `Int` fields

**Status**: open  
**Platform**: JVM, Native, JS  
**Since**: 0.0.4-alpha  
**Last updated**: 08/09/2026  

**Description**: The Kof compiler generates incorrect bytecode when assigning 
an `Int` value to a class field (`putfield`). This causes a `VerifyError` at 
runtime when the code tries to assign an `Int` to a class field 
of an object.

**Problematic example**:
```kof
class Foo {
    Int x = 0
    void setX(Int v) { x = v }  // May generate VerifyError at runtime
}
```

**Impact**: Any Kof code that tries to assign an `Int` to class 
fields at runtime may fail with `VerifyError`. Most safe code 
uses only local variables or `record` (immutable data), which do not 
suffer from this problem.

**Workaround**: Use `record` for immutable data or local variables instead 
of mutable class fields.

**Roadmap**: Fix in the Kof compiler codegen backend to avoid 
`putfield` of `Int` on class fields. Priority: high.

---

## Gap HW001 — Bare-metal kernel

**Status**: documented as a design decision  
**Platform**: Native  
**Description**: The Kof Native backend generates ELF x86-64 that depends on Linux + 
glibc. The entry point `_start` uses `SYS_gettid`/`exit_group`, allocates with 
`mmap`, uses `pthread_create`. It does not configure GDT/IDT/paging/ring0 and does not expose 
hardware primitives (`in/out`, `cli/sti`, `lgdt/lidt`, `int 0x80`, IRQ).

**The Kof IR has 30 high-level ops; there is no inline assembly or hardware access.**

**Decision (design — not silent)**: KofOS is ported as a hosted kernel 
in pure Kof, preserving the architecture and functionality of VibeOS (scheduler, 
processes, IPC, syscalls, microkernel services, VFS, AppFS, desktop, terminal, 
file manager, editor, task manager, games) and the same branding and boot flow. 
The hardware layer (BIOS bootloader, real GDT/IDT, PIT, PIC, I/O ports, 
real ring0/ring3) is abstracted.

When the Kof compiler gains a freestanding mode + hardware primitives, 
the kernel can be retargeted to real x86 without rewriting the logic.

**Impact**: KofOS preserves the VibeOS architecture (boot → scheduler → 
memory → syscalls → IPC → VFS → userland), but runs as a hosted application 
on the Kof runtime, not as a bare-metal kernel.

---

## Gap CONC001 — Concurrency on Native

**Status**: closed (31/08)  
**Platform**: Native  
**Since**: 0.0.5-alpha  
**Closed**: 31/08  

**Description**: Native concurrency with `pthread_create` + trampoline + `await`/`pthread_join` + futex thread-safe allocator + implicit join at the end of `main`.

**State**: Fixed. The Native backend now supports concurrency via `spawn`/`await` 
with native operating system threads.

---

## Gap CONC003 — Async on JS

**Status**: partial  
**Platform**: JS  
**Since**: 0.2.6-beta  

**Description**: Sequential execution — `spawn`/`await` cover statement and expression; 
real event-loop async = CONC003 partial.

**State**: In development. `spawn` and `await` work for independent 
tasks, but real event-loop async is not yet complete.

---

## Gap WEB001 — Web handler on Native/JS

**Status**: partial (JVM: closed 30/08)  
**Platform**: Native, JS  
**Since**: 0.2.6-beta  

**Description**: Web handler on the JVM (`web.app()`) with `get/post/put/delete/patch/options` routes, 
`status(201, body)`, `headerSet`, WebSocket, SSE, `listenSecure` TLS — 30/08. 
Native/JS: WEB001.

**State**: JVM has a complete implementation. Native/JS still in development.

---

## Gap MQ001 — Producer/consumer queues

**Status**: closed (01/09)  
**Platform**: JVM, Native, JS  

**Description**: Producer/consumer queues (`kof.mq`) on the 3 targets.

**State**: Fixed. `kof.mq` works on all targets.

---

## Gap conventions

- **Codes**: short prefixes such as `R6`, `HW001`, `CONC001`, etc.
- **Status**: `open`, `closed`, `partial`
- **Targets**: `JVM`, `Native`, `JS`
- **Reference**: every gap must have issues/references in the tests and docs
- **Workaround**: documented in each specific gap

---

## Roadmap of pending gaps

1. **R6** — Fix putfield of Int in the compiler (Priority: high)
2. **CONC003** — Real async on JS (Priority: medium)
3. **WEB001** — Web handler on Native/JS (Priority: medium)
4. **HW001** — Bare-metal kernel (depends on freestanding in the compiler)

---

**Source**: Analysis of compiled Kof 0.3.0-beta bytecode + runtime verification 
`VerifyError` + KofLang team roadmap.

**Maintained by**: KofLang Team.  
**Updated**: 08/09/2026.

---

Strategy documented according to AGENTS.md — autonomous mode rules, intention not mechanism, 
complexity belongs to the platform, represent the domain, zero ceremony, null hallucination 
avoided, honest multi-target.
