[English](db-parity-plan.md) | [Português](db-parity-plan.pt_BR.md)

# DB Parity Plan — every target accepts every scheme (mariadb, mysql, sqlite, mongodb, …)

> **IN DEVELOPMENT** — opened 21/09/2026 from the maintainer's directive
> (`D-DB-GAPS` addendum, `DECISIONS.md`). The plan is the queue; each slice
> lands with proof (Q0–Q7) and this doc is **moved to `docs/stdlib/`** only
> when parity is complete.

**Owner:** DB/ORM front (owner to be named) · **Records/plan:** docs/plataforma lane
**Branch:** `beta-0.5.0` · **Status:** queue opened, S0 not started

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
| `mariadb://` | ✅ JDBC (driver) | ✅ JVM | ✅ host JDBC | ❌ not parsed (mysql-wire compatible) |
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

- **S0 — interim honest diagnostic (clears §421).** On Native, `kof_db_connect`
  must **reject** a scheme it does not implement with the documented code, instead
  of registering a type-0 handle. Transient: removed per scheme as S1–S4 land.
  *Proof:* `MakealiveDbStateE2ETest.stateSurfaceNativeNeverSilent` green + a pin
  that an unsupported scheme yields the diagnostic, not a late `.Lorm_conn` crash.
- **S1 — `mariadb://` = mysql-wire alias (Native, 3 arches).** Parse `mariadb://`
  into the same path as `mysql://`; `kof_db_type` reports the mysql family.
  *Proof:* native E2E connect + query on x86-64 (cross guarded by toolchain).
- **S2 — JDBC scheme parity JVM/JS/Android.** Per-driver measurement (h2, sqlite,
  mysql, mariadb, postgres) + honest missing-driver diagnostic with the code.
  *Proof:* E2E per scheme on JVM; JS/Android delegate proof.
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
