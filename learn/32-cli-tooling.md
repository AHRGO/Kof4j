[English](32-cli-tooling.md) | [Português](32-cli-tooling.pt_BR.md)

# 32 — CLI and Tooling

> **Kof 0.4.0-beta — Sep 2026 — targets jvm/native/native.risc/native.arm/js/android + kofc**

The CLI is the central tool of the Kof platform.

## Commands

| Command | What it does |
|---------|--------------|
| `kof build <dir>` | Compiles to JVM (default) |
| `kof build <dir> --target=native` | Compiles to x86-64 ELF |
| `kof build <dir> --target=native.risc` | Compiles to riscv64 ELF |
| `kof build <dir> --target=native.arm` | Compiles to aarch64 ELF |
| `kof build <dir> --target=js` | Compiles to ES Modules |
| `kof build <dir> --target=android` | Generates a Maven project + APK (Phase 1: host Activity in Kof; `mvn verify` / `--apk` with the SDK) |
| `kof run <file.kf> [--target jvm|native|native.risc|native.arm|js]` | Compiles and runs |
| `kof script <file.ks|kf> [--watch] [--target ...]` | Direct KofScript (pure Kof; top-level `var`/`val` → `KofScriptGlobals`) + diagnostics with file:line |
| `kof repl` | Incremental KofScript REPL (type `exit` to quit) |
| `kof c <file.c> [--run] [--output <bin>]` | KofC C subset → native-only x86-64 ELF |
| `kof serve <file.kf>` | HTTP web server (native `web.app()` + legacy `handle()` API) |
| `kof check <file.kf\|dir> [--json]` | Type-check without emitting code |
| `kof test <file.kf\|dir> [--target jvm|native|js]` | Structured suite `test "nome" { assert(...) }` on the 3 targets + whole programs by exit code |
| `kof bench [paths...] [--target ...] [--iterations N] [--baseline <file>] [--threshold <ratio>] [--json] [--fail-on-regression]` | Benchmark harness (compile, run, validate, metrics, baseline) |
| `kof profile <file.kf> [--target ...]` | Execution + metrics (CPU, RSS, GC) |
| `kof inspect <file.kf> [--json]` | IR statistics: ops before/after optimization |
| `kof decompile <file.class> [--output <file.kf>]` | Structural Kof skeleton from a `.class` |
| `kof translate <file.java> [--output <file.kf>]` | Java subset → Kof source |
| `kof compare <legacy.class\|jar> <file.kf> [--json]` | Differential test legacy vs Kof |
| `kof migrate <file.class\|java> [--output <file.kf>] [--json]` | Migration + traceable report |
| `kof config gen <file.kf\|dir> [--output <arquivo>]` | Generates a `kof.config` template from the `config.*` keys in the code |
| `kof fmt <file.kf\|dir> [-w]` | Real formatter via parser (`KofFormatter`), idempotent — implemented on 31/08 |
| `kof debug <file.kf> [--target jvm]` | DAP MVP (breakpoints by Kof line, stack trace) |
| `kof new <name>` | Project skeletons by type |
| `kof init` | Initialize a project in the current directory |
| `kof deps <init\|add\|remove\|list\|resolve>` | Package manager (`kofdeps`, Maven Central) |
| `kof editor <list\|detect\|status\|setup\|install\|uninstall\|update>` | Editor integration (EDI001) |
| `kof info [--json]` | Environment report |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof install <dir>` | Installs this build as a distribution (launcher + `kof.jar`) |
| `kof version` | Platform version (`<revision>`) |

All commands follow `intention->Kof->frontend->IR->backend->runtime`.

## `kof info`

Official environment diagnostics — for users and support:

```text
Kof 0.4.0-beta
Release channel: beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.0.4 (embedded)
Compiler: 0.3.22-beta
Runtime: 0.3.22-beta
Stdlib: 0.3.22-beta
Targets: jvm, native, js (alpha)
LSP: available
Editor support: available
Install: /opt/kof
```

(`parseTarget` also accepts `native.risc`/`native.arm`/`android` — the
report summarizes the main runtime targets.)

Structured format: `kof info --json`.

## `kof check`

Runs the complete pipeline (Lexer → Parser → Semantic Analysis) and reports
all errors, without emitting code. It is the same check that the LSP publishes.
With the `--json` flag (`kof check <file.kf|dir> --json`), it emits the
diagnostics in structured JSON format for automation and continuous
integration (CI/CD).

## `kof script` and `kof c` (0.2.0)

```bash
kof script demo.ks                 # top-level var/val → KofScriptGlobals
kof script demo.ks --watch         # re-executes on save
kof script --repl                  # incremental REPL (exit to quit)
kof c hello.c --run                # native-only C subset (GAS+LD)
kof c hello.c --output ./bin
```

`KofScript` reuses the real frontend (`lexer→parser→AST→IR`) and the chosen backend. **KofScript is pure Kof executed directly — it is not JavaScript**: there is no `let`/`const`/`async`/`fn`. The wrapper's only service is the script model: `var x=5` at the top becomes `class KofScriptGlobals { static Int x=5 }` and loose statements become `main(){…}`.

## `kof fmt` and `kof config gen` (31/08)

```bash
kof fmt src/                  # formats and prints (dry-run)
kof fmt src/ -w               # rewrites the files in-place
kof config gen src/           # generates a kof.config template from the config.* keys
```

- `kof fmt` formats via the real parser (`KofFormatter`) — the result is
  idempotent (running it twice changes nothing).
- `kof config gen` extracts the `config.*` keys from the code and generates a
  `kof.config` template ready for editing (precedence: `KOF_CONFIG` >
  env `KOF_<KEY>` > profile > `kof.config`).

## `kof bench`, `kof profile` and `kof inspect`

- `kof bench [paths...] [--iterations N] [--baseline <file>]
  [--update-baseline <file>] [--threshold <ratio>] [--json]
  [--fail-on-regression]` — compiles, runs, validates the stdout against
  `expected.txt`, measures time (median) and RSS and compares with the baseline
  (`PERFORMANCE REGRESSION` above the threshold; CI uses `--threshold 1.20`).
- `kof profile <file.kf>` — execution + metrics (CPU, RSS, GC).
- `kof inspect <file.kf> [--json]` — IR statistics: ops before/after
  optimization.

## `kof lsp`

Language Server that consumes the **real compiler frontend**. The editor's
diagnostics are exactly the compiler's — there is no parallel parser.

```bash
kof lsp   # reads stdin, writes stdout (LSP)
```

## Editor support

Editor tooling ships with the distribution:

- `editor/kof.tmLanguage.json` — official TextMate grammar (scope `source.kof`);
- `kof lsp` — semantics and diagnostics in any LSP editor (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot, etc.).

Never duplicate the parser in an editor: consume Kof's tooling. Target separation (`native.risc`/`native.arm`) already appears in `kof info` and in `parseTarget`.

## References

- [docs/tooling/README.md](../docs/tooling/README.md)
- [docs/tooling/EDITOR_SUPPORT.md](../docs/tooling/EDITOR_SUPPORT.md)
- [docs/tooling/LSP.md](../docs/tooling/LSP.md)
