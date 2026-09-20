[English](registry-live-roundtrip-2026-09-20.md) | [Português](registry-live-roundtrip-2026-09-20.pt_BR.md)

# Registry 1.5.3 — live GitHub round-trip smoke (20/09/2026) — **RED → GREEN after #564**

> **UPDATE — GREEN after the #564 fix** (see "Re-run after the #564 fix" below): publish, pull by
> version, `latest` pin, idempotent 2nd resolve and honest error all pass against real GitHub.
> One step stays open and is **not** a Registry defect: KOF code cannot `import` the pulled
> package (#566, contract question for the maintainer). The text below is the original RED record.
>
> **Result of the first run: RED.** Publish works against real GitHub. Pull does **not**: two real bugs
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

> **Update 20/09 — #565 FIXED** (delivered in `d1a12dd9`): self-inclusion removed from `kof build --fat` and `kof deploy --target jvm` (staging jar outside `classesDir` + exact exclusion of the final path + replace only after close; a failed rebuild keeps the previous jar and leaves no `.kof-app-*` file). Pinned by `CmdBuildFatTest` (build 1, rebuild in the same `classesDir`, failed rebuild) and by the structural inspection of the distributed jar in `CmdDeployTest`. (At that point the smoke stayed RED because of #564 — superseded by the re-run below.)

Both were triaged KOF-first (D-KOF-FIRST): tooling bugs, no KOF syntax involved; contract = `DECISIONS.md` D2-A;
duplicate search (open+closed) found none.

## Observations (no issue filed)

- The tree-built jar reports `compiler: unknown` in `RELEASE.md` because the local build lacks `dev/kof/version.properties`; the official jar prints `kof 0.4.9-beta`. Environment artifact, not a bug.
- `kof deploy` packages only classes reachable from `main`. A library-only package therefore ships nothing unless the entry point uses it — a contract question for *library* publishing (rule 6: maintainer's), not a defect claimed here.
- My own script called `kof run .` (COMP001: needs a `.kf` file); that was harness misuse, not a bug, and is why the `run` step of C/D/E is not evidence.

## Re-run after the #564 fix — **GREEN** (20/09/2026)

Same public release, same clean conditions: jar built from the tree (`kof-cli-0.5.0-beta.jar`, verified to carry the fix), fresh `-Duser.home` per phase, fresh consumer directory, `GH_TOKEN`/`GITHUB_TOKEN` **unset**, real `api.github.com`. Nothing sensitive is recorded (paths shown as `$HOME`).

| Phase | Result (measured) |
|---|---|
| C — by explicit version | **GREEN** — `resolve` exit 0; jar installed at `$HOME/.kof/deps/kof/<owner>/<repo>/<ver>/<repo>-<ver>.jar`; installed jar sha256 == published jar sha256 (`6099e28f…`); the published package's own `SHA256SUMS` verifies (`OK`) |
| E — idempotence | **GREEN** — 2nd `resolve` exit 0, **no download**; jar mtime/size, `kofdeps` and lock state byte-identical |
| D — `latest` (no `@version`), separate HOME and workspace | **GREEN** — `kofdeps` `owner/repo` becomes `owner/repo@0.1.0-smoke.5d8a2b98`, jar installed |
| F — nonexistent reference | **GREEN** — `REG001` (exit 1), 0 files in the cache |
| run the installed package | `java -cp <jar> Default.Main` → `hello, producer` |

Proof in code: `DepsRegistryTest` now serves the **real GitHub shape** (nested `uploader{…}` with its own `url` before `browser_download_url`, no `download_url`, `author{…}` before `tag_name`, delimiters and escaped quotes inside strings, reversed field order) and asserts the HTTP contract (asset downloaded from the asset API `url` with `Accept: application/octet-stream`; `User-Agent: kof-cli`; `X-GitHub-Api-Version: 2022-11-28`; a 302 to another host is followed and the `Authorization` token is **not** sent to it). Before the fix 11 of its 13 tests failed with `REG002: … has no .tar.gz asset`; after: 13/13 (+ `DepsTest` 4, `DepsTransitiveTest` 10).

Fix decision (KOF-first): the release JSON is read with the CLI's own structural `Json.parse` (no new dependency, unlike the `jackson-core` proposed in the plan); the `SHA256SUMS` requirement and the exact → `-jvm` → first `.tar.gz` selection are unchanged.

## Observations from the re-run

- `kofdeps.lock` is not written for registry deps: it is the Maven transitive closure (roadmap 1.5.2). Registry deps are pinned **in `kofdeps`** itself (`latest` → concrete version) — existing behavior, asserted by `latestResolvesAndPinsConcreteVersion`.
- A KOF program cannot `import` the pulled package: `PKG006` even with `--classpath` (the import gate looks for a source module). That is a contract question, filed as #566 — not part of the Registry pull/publish defect.
