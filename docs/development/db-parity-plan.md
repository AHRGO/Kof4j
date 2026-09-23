[English](db-parity-plan.md) | [Português](db-parity-plan.pt_BR.md)

# DB Parity Plan — every target accepts every scheme (mariadb, mysql, sqlite, mongodb, …)

> **IN DEVELOPMENT** — opened 21/09/2026 from the maintainer's directive
> (`D-DB-GAPS` addendum, `DECISIONS.md`). The plan is the queue; each slice
> lands with proof (Q0–Q7) and this doc is **moved to `docs/stdlib/`** only
> when parity is complete.

**Owner:** `gaps-db` lane (handed over 21/09 by order of the maintainer, under `D-DB-PARITY-OWNER`; S0/S1 authorized) · **Records/plan:** docs/plataforma lane
**Branch:** `beta-0.5.0` · **Status:** S0 ✅ DONE (21/09, session 9092) — honest native refusal of an unsupported scheme with the named `DB001` code, plus real link-by-use (no `libmariadb` link for non-mysql literals); **S1 ✅ DONE on Native x86-64 (23/09, gaps-db lane)** — `mariadb://` is a `mysql://` wire alias; cross stays honest `DB001` until the mysql wire is ported (R7); **S2 ✅ DONE on JVM/JS/Android (23/09, gaps-db lane)** — a missing JDBC driver is now a named `DB001` diagnostic (real connection failures untouched); S3–S4 follow

---

## Why

The maintainer was asked which gap-code to use for the **silent acceptance** of
unsupported schemes on Native (§421: `kof_db_connect` returns a type-0 handle and
the failure surfaces late at `.Lorm_conn`). The answer was **full parity**: every
target must **accept** `mariadb`, `mysql`, `sqlite`, `mongodb`, … — no gap-code
endpoint. This generalizes `D-DB-GAPS` DB-3 (extend MySQL to riscv64/aarch64) into
a **cross-target scheme-parity** goal.

This is **not** a frozen-surface change: the `kof.db`/`kof.orm` API is unchanged —
the plan only **widens the set of accepted URLs**, each scheme **real** (R6). An
unsupported scheme is an **honest interim gap** while its slice lands (R6: a
diagnostic, never silence) — never a permanent refusal, never a silent accept.

## Measured state (21/09/2026 — measurement, not memory)

| Scheme | JVM | Android | JS | Native (x86/riscv/aarch) |
|---|---|---|---|---|
| `sqlite:` | ✅ JDBC (driver) | ✅ JVM | ✅ host JDBC | ✅ `sqlite3` link-by-use |
| `mysql://` | ✅ JDBC | ✅ JVM | ✅ host JDBC | ⚠️ wire in progress (`RuntimeDb2` — auth scramble/lenenc done; full handshake/prepared pending) |
| `mariadb://` | ✅ JDBC (driver) | ✅ JVM | ✅ host JDBC | ⚠️ x86-64 ✅ alias of the `mysql://` wire (23/09); riscv/aarch still `DB001` (mysql wire not ported → R7) |
| `mongodb://` | ✅ JDBC (driver) | ✅ JVM | ✅ host JDBC | ❌ not parsed |
| `oracle://` | ✅ JDBC (driver) | ✅ JVM | ✅ host JDBC | ❌ not parsed |
| `postgres://` | ✅ JDBC (driver) | ✅ JVM | ✅ host JDBC | ❌ not parsed |

- **JVM/Android/JS** accept any JDBC URL whose driver is on the classpath; the JS
  delegate **is** the host's JDBC. A missing driver is a runtime `DB001` today.
- **Native** parses only `sqlite:` and `mysql://`; any other scheme registers a
  type-0 handle (the §421 silent accept). `kof_db_type` already reserves
  **1=sqlite 2=mysql 3=oracle 4=mongo**.
- Reference: `docs/stdlib/DATABASE_VISION.md` (Levels 0–4, JVM Mongo face, native
  SQLite real, native MySQL/MariaDB in progress).

## Acceptance (parity definition)

A scheme is **at parity** when the **same Kof program** (connect → execute/query →
typed roundtrip) produces the **same observable result** on all four targets, or a
**declared `XXX00x`/`DB00x` diagnostic** where a target genuinely cannot run it
(never a silent divergence). Cross-target E2E per scheme is the proof.

## Slices

- **S0 — interim honest diagnostic (clears §421). ✅ DONE 21/09 (session 9092).**
  On Native, `kof_db_connect`/`kof_db_connect2` now **reject** a scheme outside
  `sqlite:`/`mysql://` with the named code (`DB001: unsupported db scheme …`)
  thrown at connect time, instead of a silent null handle that only died later at
  `.Lorm_conn`. Kept real connection failures (auth / slot limit / `sqlite3_open`)
  in `.Ldb_connect_bad`. x86-64 + riscv/aarch (`NativeRiscvAsmRtB47`, aarch via
  translator). Also made `NativeBackend.connectsToMysql` real link-by-use (only a
  literal `mysql://`/`mariadb://`/`jdbc:mysql://` links `libmariadb`), so the
  probe can link+run on a host without the lib. Transient: removed per scheme as
  S1–S4 land.
  *Proof (measured, skipped=0):* `MakealiveDbStateE2ETest.stateSurfaceNativeNeverSilent`
  green + `KofDbE2ETest.nativeUnsupportedSchemeNamesGapNotSilent` pin (rc≠0,
  `DB001`, no `unknown db connection`) + `NativeDbSchemeRefusalAsmTest`
  (deterministic codegen) + `LinkByUseTest` 3/3.
- **S1 — `mariadb://` = mysql-wire alias (Native, 3 arches). ✅ DONE on x86-64
  23/09 (gaps-db lane).** `kof_db_connect_inner` (`RuntimeDb2`) matches
  `mariadb://` and reuses the `mysql://` path (`r12 = schemeStart+2`, so the
  shared `leaq 8(%r12)` host offset lands after the 10-char scheme);
  `kof_db_type` reports the mysql family (2), so `execute`/`query`/ORM take the
  wire. The `DB001` message now lists `mariadb://`. On riscv64/aarch64 both
  `mysql://` and `mariadb://` still refuse with `DB001` (the cross mysql wire is
  not ported — honest R7). *Proof:* `KofDbE2ETest#nativeMariadbAliasWireProtocol`
  — real MariaDB (KOF_MYSQL_PORT), both the `user:pass@host` and the host-only
  form, byte-identical `{"id":7,"name":"Alias"}`; `KofDbE2ETest` 28/0F +
  `NativeDbSchemeRefusalAsmTest` 2/2.
- **S2 — JDBC scheme parity JVM/JS/Android. ✅ DONE 23/09 (gaps-db lane).**
  Per-driver measurement is already proven by the E2E corpus — `h2`
  (`KofDbE2ETest` execute/query/typed), `sqlite` (`KofOrmE2ETest` `jdbc:sqlite:`,
  JVM find/save/page), `mariadb` (`KofOrmE2ETest#mariadbCrud` + the F2d JVM
  oracle, real server), `postgres` (`KofOrmE2ETest#postgresCrud`, skipped without
  a server) — all through the same `DriverManager` path that JS/Android delegate
  to. What was missing was the **diagnostic**: a JDBC URL whose driver is absent
  leaked a raw `SQLException: No suitable driver`. Now
  `JvmConfigRuntime.kof_db_connect/connect2` (JVM, and Android through the same
  runtime) and `KofJsDbBridge.connect/connect2` (JS delegate) map it to a
  **named** `DB001: no JDBC driver for this URL (add the driver to the
  classpath): <url>`, while a **real** connection failure (server down / bad
  credentials) passes through **untouched** — never masked as `DB001`. *Proof:*
  `KofDbE2ETest#jvmMissingJdbcDriverNamesGapNotSilent` +
  `#jvmRealConnectionFailureIsNotRelabeledDb001` +
  `#jsMissingJdbcDriverNamesGapNotSilent` +
  `#jsRealConnectionFailureIsNotRelabeledDb001` (the JVM one RED on the old code
  — `No suitable driver`); `KofDbE2ETest` 32/0F.
- **S3 — `mongodb://` interop-first (R9).** Driver/wire behind the Kof API —
  **never** a home-grown server. Native closure per R7.
  *Proof:* E2E roundtrip, or a declared gap with the code until the driver lands.
- **S4 — `oracle://` (same route as S3).**

## Non-goals / invariants

- No new syntax, no API change; no frozen-semantics edit (behavior freeze).
- Interop-first (R9): drivers/protocols come from audited external libs, never
  reimplemented. Native closes last (R7), each slice honest meanwhile (R6).
- Connection pooling stays the separate PLANNED item of `DATABASE_VISION.md`
  (not part of scheme parity).
