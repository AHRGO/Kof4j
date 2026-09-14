[English](roadmap-gap-2026-09-03.md) | [Português](roadmap-gap-2026-09-03.pt_BR.md)

# Roadmap 01 — Gap Report (03/09/2026, 0.2.6-beta)

> **HISTORICAL SNAPSHOT — moved from `docs/development/` to `docs/history/` on
> 12/09**: dated gap report; the pending items it pointed out are now tracked
> live in `roadmap-audit.md`/`known-bugs.md`/`backend-parity.md` (several already
> closed — e.g.: binary MySQL prepared 03/09, `MQ001` Native 02/09). Kept
> as a record of the 0.2.6-beta state.

> Generated at the end of the NATIVE002 core todo (riscv64 02/09 + aarch64 03/09, 13/13 each). Base: `docs/status.md:1`, `docs/native-multiarch.md:1`, `docs/backend-parity.md:1`, `kof-compiler/src/main/java/dev/kof/compiler/nat/NativeBackend.java:1851`, `NativeRiscv64E2ETest.java:1`, `NativeAarch64E2ETest.java:1`.

## Executive summary
- **Tests:** `814` (797 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli) — `docs/status.md:11`
- **NATIVE002 core:** ✅ riscv64 13/13 (`NativeRiscv64E2ETest.java:70`) + aarch64 13/13 (`NativeAarch64E2ETest.java:70`) via `qemu-*`, pure asm runtime without C (`NativeBackend.java:2495` RISCV_RUNTIME_ASM + `NativeBackend.java:3533` emitAarch64 via `translateRiscvToAarch64:3716`)
- **NATIVE002 advanced parity:** ❌ `NATIVE002` remains open — JSON/DB/HTTP/concurrency/UI/net still only x86_64 (`docs/native-multiarch.md:5`, `docs/backend-parity.md:86`)
- **Roadmap vs status discrepancies:** 3 items listed as "In development" in the roadmap are already closed in status/docs (FLT001, JSN002, part of CONC001/G8).

---

## 1. Completed (roadmap) — audit

All 38 "Completed" items match `docs/status.md:664` and `docs/backend-parity.md:15`. Honest gaps within them:

| Roadmap item | Real status | Where | Note |
|---|---|---|---|
| Compiler foundation … Native backend | ✅ | `CompilerDriver.java`, `NativeBackend.java:232` | Complete Native x86_64 (18 emit*); riscv64/aarch64 only **core** (not full) — `docs/native-multiarch.md:61` |
| classes/records/inheritance/interfaces/constructors/exceptions/generics/collections/string operations/control flow | ✅ 3 targets | `BackendParityTest:10`, `KofPatternMatchingTest:10` | Inheritance `super.metodo()` = SUP001 on Native |
| kof build/run/serve/test/debug/bench | ✅ | `docs/status.md:13` | `kof debug` JVM MVP only; Native DWARF only `.debug_line` (`NativeDwarfLineInfoTest:1`) |
| kof.web routes/middleware + WebSocket/SSE | ✅ JVM only | `KofWebE2ETest:10`, `KofWebWsE2ETest:11` | Native/JS gap `WEB002`/`WEB001` (`docs/backend-parity.md:78`) |
| kof.db JDBC + native SQLite | ✅ JVM + Native SQLite | `KofDbE2ETest:11` | Native via direct `.so`; `transaction{}` ✅ 01/09 (`NativeBackend.java:docs/status.md:132`) |
| kof.orm entity/CRUD/migrate/MongoDB | ✅ JVM | `KofOrmE2ETest:22` | Native/JS `ORM001` |
| native kof.log | ✅ | `NativeLogE2ETest:7` | x86_64 asm, UTC, no JSON; riscv/aarch64 not ported |
| kof.config (3 targets) | ✅ | `KofConfigE2ETest:11`, `NativeConfigE2ETest:8` | riscv/aarch64 not ported (x86_64 asm only) |
| kof.mq pub/sub (JVM) | ✅ 3 targets | `KofMqE2ETest:4` | Native 01/09 closed, but x86_64 asm only; riscv/aarch64 pending |
| HTTP client (JVM + JS) | ✅ JVM+JS | `KofHttpE2ETest:4` | Native `HTTP002` |
| kof.security v1 (3 targets) + G9 rateLimit/sessions/API keys (3 targets) | ✅ | `KofSecurityTest:25` |  |
| TLS/HTTPS web.listenSecure (JVM) | ✅ JVM | `KofWebTlsTest:5` | Native/JS `WEB002`/`WEB001` |
| kof.validation 13 preds + kof.observability health/metrics/request IDs (3 targets) | ✅ | `KofValidationTest:3`, `KofObservabilityTest:4` | Native observability 01/09 (`OBS002`) x86_64 only |
| kof.ui widgets | ✅ | `UiE2ETest:14` |  |
| spawn JVM + await Handle<T> | ✅ | `KofAwaitTest:7` |  |
| enum 3 targets + exhaustive switch | ✅ | `KofEnumTest:4` |  |
| Map/Set 3 targets (COL001) | ✅ | `KofMapSetTest:4` |  |
| IR optimizer + bench 37 benchmarks | ✅ | `OptimizerTest:21`, `benchmarks/` |  |
| KofScript top-level let/const | ✅ | `kof-script` 8 |  |
| KofCcompiler C subset → ELF x86_64 | ✅ | `kof-c-compiler` 5 | x86_64 only |
| kof.process + process.spawn stdin/stdout | ✅ | F10 |  |
| kof fmt | ✅ | `Fmt.java` |  |
| constructor overloading, widening, LSP, Native GC free-list, Pattern matching, Null safety, Higher-order map/filter/reduce, Multi-file modules, cross-platform releases | ✅ | `docs/status.md:682` | mark-sweep GC pending (memory only `munmap` — `docs/status.md:605`) |

## 2. In development (roadmap) — what is actually missing

| Roadmap item | Real status (03/09) | Missing |
|---|---|---|
| **Standard Library (contracts being stabilized)** | 🟡 Partial | Many modules with per-target gaps (web, db/orm, http, cache, media, etc.). Contracts not frozen. |
| **Async** | 🟡 | `Async` as a concept has no spec yet; `spawn`/`await` already covers part. `async`/`await` JS-style is missing (does not exist in Kof — `fake-idioms.md`) |
| **Concurrency — spawn on Native (CONC001), spawn-expr/await on JS (CONC003), AND001 on Android** | ✅ CONC001 closed 31/08 (`docs/status.md:606`), AND001 31/08, CONC003 sequential JS (real event-loop pending) — `docs/backend-parity.md:78` | Real async JS over Promises/event-loop (`CONC003` future) |
| **KofAndroid — Phase 1: kof build --target android** | 🟡 Phase 1 generates Maven project + APK (host in Kof) — `docs/status.md:679` Phase 2 31/08 already with standalone `--apk`, but `AND00x` gaps at compile-time | Complete Phase 2 + parity (no virtual threads on ART → platform threads) |
| **Native MySQL/MariaDB — handshake (27/08; query/prepared pending)** | 🟡 Wire protocol ✅ 31/08 (`kof_db_mysql_scramble` + COM_QUERY + `?` binds client-side — `KofDbE2ETest.java:nativeMysqlWireProtocol`) — `docs/status.md:137` | Binary **Prepared statements** COM_STMT_PREPARE/EXECUTE (attempt 01/09 reverted — malformed packet, hang). Pending. |
| **SSE floating point on Native (FLT001)** | ✅ **Closed 31/08** — real XMM (`vcvtsi2sd`, `mulsd`, `snprintf` dtoa) — `docs/status.md:620` vs roadmap says "In development" → **discrepancy: already done** | Nothing (only riscv/aarch64 FP parity — today only int in the core) |
| **Object JSON on Native (JSN002)** | ✅ **Closed 31/08** — compile-time composition — `docs/status.md:608` vs roadmap "In development" → **already done** (x86_64 only) | Port to riscv/aarch64 |
| **native.risc toolchain stable + native.arm placeholder** | 🟡 **Updated 03/09** — riscv64 + aarch64 **complete core 13/13** (`docs/native-multiarch.md:3`), ELF via `cross-as/ld` + qemu, **without C** (bump allocator, raw syscalls) — `NativeBackend.java:1851/3533` | Advanced parity (JSON/DB/HTTP/concurrency/scheduler/mq/UI/net/cache/log/config/time) on both. `NATIVE002` remains as a diagnostic `unknown ops → comment`. |
| **Debugger — beyond the JVM MVP** | 🟡 JVM DAP MVP over stdio ✅ (`docs/status.md:583`) + JS source maps V3 line ✅ (`KofJsSourceMapTest:1`) + Native DWARF `.debug_line` ✅ partial (`NativeDwarfLineInfoTest:1`) | Locals per frame, stepping, exception breakpoints, evaluation; Native variables/expressions; DAP breakpoints on Native; `debugger-architecture.md` |
| **KofJS — web platform in the browser (ES Modules via GraalJS already in alpha)** | 🟡 Alpha + **real browser 01/09** (`KofJsBrowserE2ETest:1` — Chrome headless + HTTP + DOM) — `KofJsRunner` serves `appDir` | Modules via `file://` (ESM requires HTTP), persistence, performance, full API |
| **0.2.x concurrency residual: await with timeout, cancellation, select, typed channels, scheduler/cron (G8)** | ✅ **Closed** — `awaitTimeout`, `cancel`/`cancelled`, `selectAny`, `channel<T>` send/receive, `scheduler.every/at/cancel` on JVM+Native+JS (`KofConcurrency2Test:18`, `SpawnE2ETest:8`, `docs/status.md:677`) vs roadmap "In development" → **already done (except real async JS)** | Real async JS (event-loop) |

## 3. Pending items not listed in roadmap 01 (but in docs)

- **Native GC** — free-list `kof_free_head` ✅, mark-sweep `kof_gc_collect` emitted but **auto-GC off** (hang) — `docs/status.md:605`
- **HTTP/2, gRPC** (`app.grpc`) — planned (`docs/status.md:706`)
- **Package manager** (`kofdeps`, registry), complete **language spec**, **conformance suite**, **self-hosting** — planned (`docs/status.md:701`)
- **Complete Web** (declarative frontend, SSR) — planned
- **Media** — camera `MEDIA002`, Native/JS parity `MEDIA001`, mic without hardware `MEDIA003`
- **CI cross** — `aarch64`/`riscv64` do not enter the pipeline (`docs/native-multiarch.md:68`)
- **Toolchains** — `qemu-user` + `binutils-*-linux-gnu` installed locally, not in CI

## 4. Next steps to fully close NATIVE002

1. Port MySQL `?` bindings + prepared statements (when re-attempted) to riscv/aarch64 (same translation).
2. Port `kof.http`/`kof.db`/`kof.mq`/`kof.cache`/`kof.log`/`kof.config`/`kof.time`/`kof.observability`/`scheduler` to riscv/aarch64 (today x86_64 asm only).
3. Extract `NativeBase` (common layout/`kof_alloc`/mangle) to avoid divergence (`docs/native-multiarch.md:66`).
4. Add CI with `assume` (do not break when the toolchain is absent) + `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` in `ci.yml`.

---
*On 03/09, the NATIVE002 core "todo" is 100% green (26 cross tests, static qemu binaries). Roadmap 01 has 3 discrepant items (already closed in status) and 7 with real gaps (advanced parity + debugger + KofJS + Android).*
