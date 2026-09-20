[English](registry-live-roundtrip-2026-09-20.md) | [Português](registry-live-roundtrip-2026-09-20.pt_BR.md)

# Registry 1.5.3 — live GitHub round-trip smoke (20/09/2026) — **RED**

> **Result: RED.** Publish works against real GitHub. Pull does **not**: two real bugs
> found, both filed (#564, #565). The tracker note `live GitHub round-trip = smoke
> manual pendente` (`IMPLEMENTATION-UNIVERSAL-PLATFORM`, item 1.5.3) **stays pending**.
> No production code was changed by this smoke. Only sanitized data below: no token
> was ever printed, logged or committed (presence is recorded, never the value).

## Setup (measured)

| Item | Value |
|---|---|
| Repo tip under test | `5d8a2b98` (`beta-0.5.0`), jar built from the tree (`kof-cli-0.4.7-beta.jar`) |
| Official CLI used to cross-check | `kof 0.4.9-beta` (released jar) |
| Host | WSL Ubuntu-24.04, JDK 25 |
| Smoke repo | public `jonasrochasilva-prog/kof-registry-smoke` (README only; **not** `KofLang/Kof4j`) |
| Release / tag published | `kof-registry-smoke-0.1.0-smoke.5d8a2b98` |
| Asset | `kof-registry-smoke-0.1.0-smoke.5d8a2b98.tar.gz`, 1920 bytes (jar + `RELEASE.md` + `SHA256SUMS`, tar uid/gid 0) |
| Cache isolation | `-Duser.home=<fresh dir>` per phase; consumer directory fresh; `GH_TOKEN`/`GITHUB_TOKEN` **unset** in every pull phase |
| Token handling | producer only, via environment (never on a command line); output redacted; a **post-publish scan of the uploaded asset** found no token, user path or host name |

The release and tag are kept as evidence (no automatic cleanup).

## Phases

| Phase | What | Result |
|---|---|---|
| A | build the package from a clean tree (`kof deploy src --target jvm ...`), pre-publish scan of the artifact | OK — no sensitive data |
| B | `kof deploy --publish` to the smoke repo → real GitHub Release + `.tar.gz` asset | **OK** — release and asset visible, asset downloadable **without a token** (HTTP 200), inner `SHA256SUMS` verifies |
| C | consumer, fresh HOME/cache, `kof deps add owner/repo@<ver>` + `kof deps resolve` | **RED** — `REG002: release <tag> has no .tar.gz asset` (exit 1); no lock, no cache written |
| D | same with `latest` (no `@version`) | **RED** — identical `REG002` (exit 1) |
| E | idempotence (2nd `resolve`) | **BLOCKED** by C (nothing installed to compare) |
| F | nonexistent reference `@9.9.9-inexistente` | **OK** — `REG001: release not found on registry ...`, exit 1, 0 files in cache, valid release untouched |

C/D were reproduced with the **official** `kof 0.4.9-beta` jar as well, so it is not a local-build
artifact. Phase F proves the failure path is honest (R6): only the asset parsing of an *existing* release is broken.

## Findings

| # | Issue | Class | Summary |
|---|---|---|---|
| A | [#564](https://github.com/KofLang/Kof4j/issues/564) | BUG REAL | `DepsRegistry.pickTarball` reads `"download_url"` (real key: `"browser_download_url"`) and cuts each asset object at the first `}` (real asset has a nested `"uploader": {…}` before the URL). Pull can never succeed on real GitHub; `DepsRegistryTest` passed only against a minimal fake server. |
| B | [#565](https://github.com/KofLang/Kof4j/issues/565) | BUG REAL (low) | `CmdBuild.buildFatJar` writes `classesDir/kof-app.jar` inside the directory it walks, so every fat jar embeds a truncated, invalid `kof-app.jar` entry (561 bytes). Not a runtime failure. |

> **Update 20/09 — #565 FIXED** (in the commit that lands this change; the delivered SHA is recorded right after the push): self-inclusion removed from `kof build --fat` and `kof deploy --target jvm` (staging jar outside `classesDir` + exact exclusion of the final path + replace only after close; a failed rebuild keeps the previous jar and leaves no `.kof-app-*` file). Pinned by `CmdBuildFatTest` (build 1, rebuild in the same `classesDir`, failed rebuild) and by the structural inspection of the distributed jar in `CmdDeployTest`. **The smoke stays RED**: #564 still blocks the real pull (phases C–E); the producer side is now clean, but the round-trip is not GREEN until #564 is fixed and phases C–E re-run.

Both were triaged KOF-first (D-KOF-FIRST): tooling bugs, no KOF syntax involved; contract = `DECISIONS.md` D2-A;
duplicate search (open+closed) found none.

## Observations (no issue filed)

- The tree-built jar reports `compiler: unknown` in `RELEASE.md` because the local build lacks `dev/kof/version.properties`; the official jar prints `kof 0.4.9-beta`. Environment artifact, not a bug.
- `kof deploy` packages only classes reachable from `main`. A library-only package therefore ships nothing unless the entry point uses it — a contract question for *library* publishing (rule 6: maintainer's), not a defect claimed here.
- My own script called `kof run .` (COMP001: needs a `.kf` file); that was harness misuse, not a bug, and is why the `run` step of C/D/E is not evidence.

## To reach GREEN (after #564 is fixed — not done here)

Re-run phases C–E with the rebuilt jar, fresh HOME, no token: add by version → resolve (sha256 verified before install, jar in `~/.kof/deps/kof/...`, `kofdeps.lock` written) → consumer program imports the package and runs; `latest` pins the version in `kofdeps`; 2nd `resolve` leaves cache and lock byte-identical. Only then flip the tracker note (EN+PT) to done and link the evidence.
