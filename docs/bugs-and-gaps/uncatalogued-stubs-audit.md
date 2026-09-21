[English](uncatalogued-stubs-audit.md) | [Português](uncatalogued-stubs-audit.pt_BR.md)

# Uncatalogued stubs / incomplete development — review ledger

> **Living record of the REVIEW front** (session 9094, branch `beta-0.5.0`).
> It is **not** a bug ledger: a *confirmed* finding graduates to
> `known-bugs.md` (`§NNN`, EN+PT) with repro; a *candidate* stays here until it
> is measured. The machine inventory is reproducible:
> `scripts/audit-stubs.sh` (read-only).

**Started:** 21/09/2026 · **Base measured:** `16340f62` · **Scope:**
`*/src/main` (761 `.java`) + the 12 `.kf` host files under
`kof-compiler/src/main/resources/dev/kof/`.

## Why this front exists

The maintainer asked for a sweep of the **whole codebase** for stubs / partial
development that is **not** documented. The risk this front addresses is
precisely the silent one (R6): a path that *pretends* to work — a facade
`return null`/`return 0`, a `default` that swallows, a swallowed exception —
that no ledger entry points to.

## Method (reproducible)

```bash
bash scripts/audit-stubs.sh /home/mel/Kof4j > /tmp/slice1-report.txt
```

**Noise warning (measured, not assumed):** Kof comments are in Portuguese,
where `todo` = *every* and `stub honesto` = a **deliberate R6 refusal**
(documented in the code itself, e.g. `NativeMethodEmitter:387`,
`KofJsDbBridge:103`). A raw match is therefore a **candidate**, never a
finding — every candidate is triaged by hand and compared against
`docs/bugs-and-gaps/*` before being catalogued. This front explicitly **does
not** re-catalogue the legitimate honest refusals.

## Slice 1 — measured results (21/09, base `16340f62`)

| Signal | Raw | Triaged |
|---|---|---|
| Case-sensitive `TODO/FIXME/XXX/HACK` (`.java`) | 12 | **0 real** — all 12 are Portuguese "todo" (every) or `§`-referenced fix notes |
| `UnsupportedOperationException` | 3 | **0 stubs** — 1 is a string in a list; 2 are honest hard-fails (`NativeMethodEmitter:387` "R6: never silent"; `KofJsDbBridge:103` "DB002") |
| Empty `catch` (`.java`) | 31 | **30 benign** (file cleanup / env probe / reflective fallback) + **1 candidate** (below) |
| `@Disabled` / `@Ignore` (tests) | 0 | 0 — no disabled tests hiding pending work |
| `assumeTrue` / `Assumptions.` | 216 | all with an **honest environment reason** (toolchain C, MySQL, node, qemu, H2) — a contract of the house, not a mask |

**Conclusion of slice 1:** the code has **no abandoned `TODO` marker and no
disabled test**. Its "stubs" are the designed R6 refusal (an honest
diagnostic), which is correct behaviour, not debt. The remaining audit value
is therefore **semantic** (parity / partial handling), not greppable.

## Candidates (unverified — NOT catalogued as bugs yet)

### UI-JS-1 — JS UI event handlers swallow exceptions silently

- `kof-compiler/src/main/java/dev/kof/compiler/js/JsRuntimeUiEvents.java:62`
  (and `:78`, `:153`, `:173`): `try { … fn(kofEv) } catch (e) {}` — a throwing
  listener is **silently dropped**, with no `console.error`.
- Contrast: `JsRuntimeUiComponents.java:226` explicitly does
  `console.error("[kof] " + where + ": " + detail)` and re-throws — the JS UI
  runtime has two different policies for the same "user callback failed" case.
- **Status: CLOSED (slice 4) — by design, not a bug.** Measurement: on JVM the
  `kof.ui` runtime is a documented **no-op** (`JvmRuntimeUi.kof_ui_widget_on:146`
  and `kof_ui_component_on:180` have empty bodies), and `kof.ui`
  rendering/state rules are the **single named exception** to parity
  (`D-UI-SCOPE`, 18/09 — `docs/backend-parity.md:225`): authored/effective on
  KofJS, never on JVM/Native. With no JVM/Script policy to diverge from, the JS
  `catch (e) {}` is the UI engine's handler-error isolation, not an
  undocumented stub. **No `§NNN` filed.**

## Slice 2 — parity invariant + one doc-drift finding (21/09)

**Invariant checked (grep + read of all call-sites):** for every
`Kof*.supportedOn(...) == false` there is a `gapCode(...)` **emitted at the
same lowering site** — e.g. `ExpressionStaticCallLowerer:139/146` (db),
`ExpressionMethodCallLowerer:411/413` (std/buffer/rng/math), `:429/432`
(observability), `ExpressionTimeCallLowerer:19/27` and `:53/60`,
`ExpressionDbCallLowerer:20/28`, `ExpressionLogCallLowerer:19/27`,
`ExpressionOrmCallLowerer:35` + `CompilerOrmSupport:77/83`,
`ExpressionSchedulerCallLowerer:19/21`. `KofGpu` has no `gapCode()` method but
emits `GPU001` inline at `ExpressionMethodCallLowerer:356-359`. **No silent
parity gap found** in the `Kof*` surface.

**DRIFT-NET-1 (found, fixed, comment-only):** `KofNet.java` advertised the
opposite of reality — the class comment said *"riscv/aarch ainda gated em
compile-time"* and `supportedOn` said *"byte-scan nativo pendente"*, while
`conformance-matrix.md` §net records **NET001 CLOSED 09/09** and
`NativeRiscvAsmRtB24` implements `kof_net_queryEncode`/`queryDecode` (slice
B24, aarch via translator; proof `KofNetTest.netOnCrossArch`). The
`supportedOn` returns `true` for **all** targets (correct), making
`gapCode()="NET001"` **vestigial**. Fixed the two stale comments and annotated
the vestigial one — **no behaviour change**, so Q1's test does not apply (a
comment cannot regress); proof = `mvn -o -pl kof-compiler -am compile` rc=0.

## Slice 3 — two more stale "gated/pending" comments (21/09)

Grep for `ainda não|pendente|gated|not yet|por ora` in the `Kof*` stdlib and
cross-check against the matrix/code. Both findings are the same "append-only
comment" drift (a stale claim left above its own correction) — comment-only
fixes, no behaviour:

- **DRIFT-UUID-1** `KofUuid.supportedOn` said *"Os 3 nativos ainda não têm
  fatia asm — UUID001 os bloqueia"*, contradicting the very next lines
  (*"S3b.2 FEITO nos 5 alvos 10/09 … Gate removido"*) and the matrix
  (**UUID001 closed**, merge beta→main 10/09; riscv B25b + aarch tradutor).
  Stale sentence removed.
- **DRIFT-STRN-1** `KofStrings` javadoc said the word converters (joinWords)
  are *"gated … com o bug 59 aberto"*, but **STRN001 CLOSED 09/09** (riscv
  B15 + aarch tradutor) and `supportedOn` returns `true`. Rewritten.

Proof for both: `mvn -o -pl kof-compiler -am compile` rc=0 (comment-only ⇒ Q1
does not apply).

## Slice 2b — invariant locked as a test (21/09)

New `kof-compiler/src/test/java/dev/kof/compiler/StdParityGapAuditTest.java`
(**15/15 green**) turns the audited support matrix into a ratchet: for each
gated namespace it asserts the exact `unsupported` target set and the exact gap
code (buffer FFI001/FFI002, db DB001, log LOG001, orm ORM001, rng RNG001, gpu,
tetris EGG001, scheduler SCHED001/CRON001, math.pow MATH001, observability
OBS003, time.tzOffsetSeconds TIME003, security.sha512 SECN003), plus the
always-true namespaces stay gate-free. A new gate in an always-true namespace
now breaks the test on purpose — the matrix is law and must be updated with it.

The test **corrected a guess of mine**: `security.sha512` is gated not only on
riscv/aarch but also on **ANDROID and SCRIPT** (`JVM || JS || isNative`), so its
gap code `SECN003` fires on four targets. Measured, not remembered (Q3).

Proof: `mvn -o -pl kof-compiler -am -Dtest=StdParityGapAuditTest test` →
**15/15** (13 core + 2 per-function: `KofSecurity`
chacha/cookie/auth/resource-server and `KofTime.addDays/diffDays` ungated).

## Slice 4 — UI-JS-1 closed + two negative sweeps (21/09)

- **UI-JS-1 closed by measurement** (see the candidate above): JVM `kof.ui` is a
  documented no-op and `D-UI-SCOPE` makes UI the single named parity exception —
  there is no undocumented stub and no divergence to fix.
- **`.kf` host files (12) — all declared:** `makealive-db-host.native.kf`,
  `workflow-ckpt-host.native.kf` and `workflow-sched-host.native.kf` carry
  explicit **high-fail stubs citing the gap** (ORM001/CRON001, R6), and the rest
  have no marker (`todo` = Portuguese *every*). **0 undocumented stubs.**
- **Q7 facade sweep:** 133 `default -> null` in the `staticMethod`-style
  dispatchers are the house idiom for *"not a member of this namespace"* — the
  typer/lowerer turns the null into a diagnostic (the R6 path), not a silent
  facade; the 4 `catch { return null }` are reflective/parse fallbacks
  (`JvmRuntimeCore:196`, `JvmTimeRuntime:270`, `CmdEditor:276`,
  `KofScriptExecutor:162`). **0 silent facades found.**

## Slice 5 — sweep conclusion (21/09)

The whole-codebase sweep **converged to a negative**: across the marker
inventory, the parity invariant, the Q7 facade sweep, the 12 `.kf` host files
and the UI candidate, **no undocumented stub / silent incomplete development
was found**. What the front *did* produce is real and committed:

- **3 documentation drifts fixed** (comment-only): DRIFT-NET-1, DRIFT-UUID-1,
  DRIFT-STRN-1 — all were stale "gated/pending" claims contradicting the matrix
  and the code.
- **1 invariant locked**: `StdParityGapAuditTest` **15/15** (unsupported target
  set + gap code per gated namespace; a new gate in an always-true namespace
  breaks it on purpose).
- **1 candidate closed by measurement**: UI-JS-1 — by design (`D-UI-SCOPE`).
- **2 negative sweeps recorded**: `.kf` hosts and Q7 facades.

Remaining (optional, not a queue): the broad doc/code drift pass across every
`docs/` claim (item 3 of next passes); until then the front is **converged**.

## Pass 3 — phantom test references (21/09)

Method: every backticked `*Test` token in `docs/` checked against the tree
(grep in every `*/src/test` + filename match). **Absent = phantom reference.**
232 tokens in the three parity ledgers, 11 across all docs.

| Token | Where | Verdict |
|---|---|---|
| `KofChannelTest`, `ChannelStdlibE2ETest` | `known-bugs.md` §374 neighbor proof | **broken evidence** — never existed; annotated inline, real channel neighbor = `KofConcurrency2Test` 48/48 |
| `AarchSchedSmokeTest`, `GenericFieldChainE2ETest`, `NullableReceiverFieldWriteE2ETest`, `KofCharCrossE2ETest`, `ProcessRunE2ETest` | open pointers / fix sketches | proposed name (test to be written) — not drift |
| `NullablePrimitiveFieldsE2ETest` | §243 revert note | historical (existed when the half-landed face was pinned) — not drift |
| `SequenceE2ETest`, `ChannelE2ETest` | `future/PLAN-MULTIPARADIGMA.md` | future plan — not drift |
| `KofSemanticTest` | `decisions/planning-mutability.md` | proposed — not drift |

Result: **2 phantom references used as proof** (both already flagged
transiently by the q553 session, `6ce55b28`); now durably catalogued and
annotated inline. The other 9 are legitimate plan/historical names.

### Pass 3 (cont.) — stale live counts in the tracker (21/09)

Sampling the dated deltas against the code: the live tracker rows carried
counts frozen at their landing and now grown:
- X10 row `StdCatalog` = 31 → **34** (`StdCatalogTest:91` locks 34;
  buffer/shell/ssh landed after the X10 close) — annotated inline, dated.
- R1 row `stdlib_boundary.txt` = 31 → **35 registered + 15 `excluded`** (50
  namespace lines, measured 21/09) — annotated inline.
The historical snapshot is preserved and the current reading corrected. The
dated **deltas** themselves are snapshots by design (not rewritten).

### Pass 3 (cont. II) — gap codes cited without a Java occurrence (21/09)

Extracted every `[A-Z]{2,7}\d{3}` token from the five live ledgers (174 codes)
and checked each against every `.java` in the tree. 11 absent, **all accounted
for**:

| Code | Where | Verdict |
|---|---|---|
| `HTTP003` | §259 | already catalogued **phantom** (compiler never emits it) |
| `UUID002` | conformance-matrix | matrix explicitly states it does not exist |
| `SEM094` | known-bugs | **reserved** to the switch-return gate (DECISIONS §D-TROOL) |
| `MEDIA002` | status.md | documented **camera gap label**, never an emitted code (`KofMedia` emits MEDIA001/MEDIA003) — caveat added |
| `COL001`, `STR002`, `CANVAS001`, `SEM063` | parity/ledger | **historical/closed** (explicitly "was"/"renamed"/"closed") |
| `APP002` | matrix APP001–003 | documented **residual** (`kof.toml` `[server] port` not consumed) |
| `AND003` | training reference | documented **caveat, not a compile-time gate** |
| `UIW008` | roadmap | roadmap **item ID**, not a gap code |

Result: **no undocumented phantom code** — the gap-code discipline holds (the
§259 drift already has a machine guard, `DomainGapCodesTest`). Only `MEDIA002`
lacked the "label, not an emitted code" caveat; added inline.

## Pass 4 — front closure (21/09)

Final spot-check of the mandate: `ConformanceMatrixTest` — the class the matrix
says locks every ✅ cell — ran **12/12 green** on the tip
(`mvn -o -pl kof-compiler -am -Dtest=ConformanceMatrixTest test`, 111.5s), so
the doc → cited test → golden chain holds.

**Front concluded.** Across five slices and four passes the sweep found **no
undocumented stub / silent incomplete development**: 0 abandoned `TODO`, 0
disabled tests, the parity invariant is machine-locked, 2 phantom *proof*
references were catalogued and annotated, stale counts/codes were corrected,
and the two candidate items (UI-JS-1, MEDIA002) are by-design/documented. The
durable artifacts are this ledger, the `StdParityGapAuditTest` ratchet and
`scripts/audit-stubs.sh`. Further re-triggers of this front should be refused
(AGENTS stability); new work waits for a regression or a maintainer decision.

## Pass 5 — semantic audit, batch 1 (21/09, the deep front)

The lexical scans are exhausted; the real undocumented incomplete development
is **semantic**. Four parallel reads (codegen emit-sites, runtime no-op ops,
CLI/LSP/tooling, weak tests) produced candidates; each was verified in the code
before cataloguing. Batch 1 (code/parity, catalogued as §424–§426, EN+PT):

- **§424** — five accepted `String` methods (`matches`/`replaceAll`/`replaceFirst`/
  `toCharArray`/`compareToIgnoreCase`) silently incomplete on JS and link-fail on
  Native with no gap code.
- **§425** — riscv64/aarch64 `kof.config` silent-default stub while `supportedOn`
  returns true (stale `CONF001` javadoc).
- **§426** — JS `time.collect()` compiles with no runtime and no gate.

Batch 2 (verified, cataloguing next): cross `kof.io`/web-T1 runtime absent with a
wrong gate; DAP `default -> respond(success:true, {})` façade (JVM+Native) +
`debug-adapter.md` `restart` claim; LSP `default -> {}` (no JSON-RPC reply);
false-green tests (`RouterE2ETest#debugConc001` zero-assert, 5 `NativeDebugTest*`
print-only, `KofWebNativeE2ETest#nativeServerAcceptsAndResponds200` `assertTrue(true)`,
`BareCollectionPrimitiveArgE2ETest#bareListAddPrimitiveNativeRuns`
`assumeTrue(compileSuccess)` masking native regressions).

## Next passes (planned — not yet executed)

1. **Parity asymmetry check** — **DONE (slice 2b, `StdParityGapAuditTest`
   15/15**, including the per-function gates of `KofSecurity` and
   `KofTime`).
2. **Q7 facade sweep** — **DONE (slice 4, negative): 0 silent facades.**
3. **Doc/code drift:** features marked done in `docs/` whose code is partial
   (cross-check the parity matrices and the tracker against the code). —
   **partially done** (slices 2 and 3 found 3 drifts); continue over the
   per-function gates.
4. **`.kf` host files** — **DONE (slice 4, negative): 0 undocumented stubs.**
5. **Close UI-JS-1** — **DONE (slice 4): closed by measurement, by design.**

## Provenance

- Inventory script: `scripts/audit-stubs.sh` (read-only, idempotent) — now guarded
  by a RED-first fixture test `scripts/tests/audit-stubs-test.sh` (a planted
  `TODO`/empty-`catch`/`@Disabled` must be found; a clean fixture must count 0;
  the tree must not change; a root without `*/src/main` refuses), wired into
  `scripts/tests/run-agent-tests.sh` (lane docs/.18, 21/09).
- Base: `16340f62`; counts re-measured on this base.
- Prior recon artifacts: `/tmp/opencode/audit/{markers,candidates,hard}.txt`.
