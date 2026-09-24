[English](PARITY-GAPS.md) | [Português](PARITY-GAPS.pt_BR.md)

# Full-parity ledger — 0.5.0 blocker (maintainer 24/09)

> **MANDATE (maintainer, 24/09): full platform parity is INDISPENSABLE for
> the 0.5.0 release.** An honest gap code (`PROC001`, `MEDIA001`, ...) is the
> sanctioned WAY to track a missing face — never the sanctioned END state.
> Every row below is a feature that today works on SOME targets and is a
> named/diagnosed gap on others. **`0.5.0` does not cut while this ledger
> has ANY open row.**
>
> Rule of the ledger (three states, machine-checked by
> `check_release_050_gate.sh` → `full_parity`):
> - a row leaves ONLY when the feature compiles AND runs with byte/golden
>   parity on ALL targets (proof test named, runner recorded);
> - partial closes move the row's target cells (never mark a row DONE
>   partially);
> - the ledger EMPTY (no rows) is the GREEN state — `docs/development/parity/`
>   itself STAYS (the contract + history), the file body shrinks to "0 open rows".
>
> "⏳ golden" = the face exists but the cross golden (riscv64/aarch64 byte
> diff vs JVM oracle) was never measured — unmeasured is NOT green (Q5: no
> false green).

## Open rows (measured 24/09 from `DomainGapCodesTest`, `Kof*.java` gap codes, `training/idioms/stdlib.md` parity table, `docs/bugs-and-gaps/known-bugs.md`)

| # | Surface | JVM/Script | Native x86-64 | Native riscv64/aarch64 | JS | Gap code | Tracker / owner lane |
|---|---------|------------|----------------|--------------------------|----|----------|----------------------|
| 1 | `process.run`/`spawn`/`exit` | ✅ | ❌ | ❌ | ✅ (KofJsRunner) | `PROC001` | native lane |
| 2 | `shell.cmd`/`run`/`runWith`/`pipeline`/`ok` | ✅ | ❌ | ❌ | ✅ (host runner) | `PROC001` | native lane |
| 3 | `ssh.cmd`/`run`/`ok` | ✅ | ❌ (no dispatch) | ❌ | ❌ | (no gap code yet — catalog) | native/js lane |
| 4 | media: `Image.open`/`Audio.openWav`/`Video.open`/`Mic.record`/`list` | ✅ | ❌ | ❌ | ❌ | `MEDIA001`/`MEDIA003` | media front |
| 5 | `mq.*` | ✅ | partial (`MQ001` faces) | ⏳ golden | ⏳ | `MQ001` | infra lane |
| 6 | `gpu.*` (JS face) + cross golden | ✅ | ✅ | ⏳ golden | ❌ `GPU001` | `GPU001` | gpu/native lanes |
| 7 | `observability.*` cross golden + `OBS003` | ✅ | ✅ x86 | ⏳ golden | ⏳ (spans ✅, OBS003 pinned) | `OBS003` | obs lane |
| 8 | `time.*` new faces cross golden + `addDays`/`diffDays` cross | ✅ | ✅ x86 | ❌ `TIME002` | ⏳ (`collect` = `TIME004`) | `TIME002`/`TIME004` | stdlib lane |
| 9 | `cache.*`/`config.*`/`log.*` cross golden; `log` interpreter | ✅ (log ⏳ interp) | ✅ x86 | ⏳ golden + `CONF001` | ✅ | `CONF001` | stdlib lane |
| 10 | `math.pow` cross (static, no libc) | ✅ | ✅ (libm `-lm`) | ❌ `MATH001` | ✅ | `MATH001` | native cross lane |
| 11 | `strings.reverse` non-ASCII (UTF-16 vs byte) + five `String` methods | ✅ | ❌ `NAT-STR01`/`STR003` | ❌ `STR003` | ❌ `STR003` | `NAT-STR01`/`STR003` | native/js lanes |
| 12 | web T1 (`kof.http.server` faces) on native/cross | ✅ | ⏳ | ❌ `WEB002`–`WEB006` | ✅ | `WEB00x` | web lane |
| 13 | `kof.io` file faces on cross | ✅ | ✅ x86 | ❌ `NAT006`/`NAT007` | ✅ | `NAT006`/`NAT007` | native cross lane |
| 14 | security family on cross/native (bcrypt/argon2/keystore faces) | ✅ | partial | ❌ `SECN001`/`003`/`004`/`005` | ⏳ | `SECN00x` | security lane |
| 15 | `orm.*` native (`kof_orm_*`) | ✅ | ❌ | ❌ | ❌ | `ORM001` (D-DB-GAPS queue) | gaps-db lane |
| 16 | `db.*` native query/prepared parity | ✅ | partial (MySQL wire ✅; query/prepared pending) | ❌ `DB001` | ❌ | `DB001` (D-DB-GAPS) | gaps-db lane |

## Already at full parity (verified — the review of what is DONE, 24/09)

Measured 4/4 (JVM/Script ≡ Native x86-64 ≡ Native riscv64/aarch64 ≡ JS),
proof = `ConformanceMatrixTest` std* cases + the E2E named per row:

- **Language core:** operators/precedence, content `==`, exceptions as
  String, null-safety narrowing, `if`/`switch` expressions, pattern matching,
  closures, `spawn`/`await`/`poll`/`done`/`cancel`/`selectAny`/`awaitTimeout`,
  channels incl. riscv64/aarch64 (§423, §485), interface default methods
  (§248), generic interfaces + covariant/primitive bridges (§355–357, §486),
  record equality/hashCode by content everywhere (§104b-ii/§114), SEM diagnostics.
- **stdlib core (rows of the stdlib parity table with ✅ on 4 columns):**
  `math` (abs/sign/clamp/min/max/is*/sqrt/lerp/percentage/roundTo/parse*) —
  EXCEPT `pow` (row 10); `strings` (is*, count, capitalize/uncapitalize,
  reverse ASCII, toCamel/Pascal/Snake/Kebab, slugify, escapeHtml/Json,
  whitespace family, dedent, repeat, truncate, indent, pad*) — EXCEPT
  non-ASCII `reverse` (row 11); `encoding` (hex, base64, base64Url, url);
  `net` (all 8); `uuid` (v4, v7, isUuid); `random` (all faces);
  `time` civil-calendar core (isLeapYear, daysInMonth, dayOfWeek,
  daysBetween, isWeekend, isToday) — EXCEPT the new faces' cross golden
  (rows 8/9); `validation` (BR docs, network, card — all faces).
- **Collections** (`List`/`Set`/`Map` full API incl. `getOrDefault`,
  `putIfAbsent`, sort/indexOf/subList) — 4 targets.
- **`json.encode`/`decode<T>`** — 4 targets (Native composes at compile time).
- **`kof.ui`** — colors/widgets/windows on the 4 targets (rule: platform renders).

An entry here only MOVES when its proof is named; the `ConformanceMatrixTest`
runner is re-run at every release-gate execution (condition 1), so a
regression re-opens the row (zero regression, freeze rule 1).

## Closed (proof recorded here when a row empties)

- (none yet — the ledger was created with the full measured state)

## Definition of done for EVERY row

1. The face compiles on the target (no gap code emitted).
2. Golden/E2E proof: byte-identical output vs the JVM oracle (cross under
   qemu where applicable), test named in the commit.
3. Parity table in `learn/39-stdlib.md`, `training/idioms/stdlib.md` and the
   namespace chapter under `learn/stdlib/` updated in the SAME commit.
4. Row removed from this ledger in the SAME commit + `full_parity` gate re-run.
