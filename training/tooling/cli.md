[English](cli.md) | [Português](cli.pt_BR.md)

# CLI and Tooling

Facts about the official Kof CLI. Use it to answer questions about
commands, tooling and editor support.

**Version:** 0.4.0-beta (Sep 2026) — 810 tests

## Official commands (18)

| Command | Behavior |
|---------|----------|
| `kof build <dir> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [--output <dir>] [--release] [--apk]` | Compiles |
| `kof run <file.kf\|dir> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [args...]` | Compiles and runs |
| `kof serve <file.kf> [--port <port>] [--host <host>]` | Basic HTTP web server. `--port`/`--host` apply only in legacy mode (`handle`); a kof-native app (`app.listen`) sets its own port and the CLI warns (#35.3) |
| `kof check <file.kf\|dir>` | Type-check without emitting code |
| `kof test <file.kf\|dir> [--target jvm\|native\|js]` | Structured suite `test "name" { }`: PASS/FAIL per test; files without tests run whole (PASS = exit 0) |
| `kof script <file.ks> [--target jvm\|native\|js] [--watch] [--inspect] [args...]` | KofScript: JIT with top-level `let` → KofScriptGlobals, repl, 64 LRU cache |
| `kof repl` | Alias for interactive `kof script` |
| `kof c <file.c> [-o outDir]` | KofCcompiler: native-only C subset → x86_64 ELF |
| `kof fmt <file.kf\|dir>` | Formatter via real parser (`KofFormatter`), idempotent |
| `kof init <name>` | Initializes a project (`main.kf` + `tests/`) |
| `kof config gen <file.kf\|dir> [--target jvm\|native\|js] [--output <file>]` | Generates a `kof.config` template from the `config.*` keys in the code |
| `kof info [--json]` | Environment report (includes native.risc/arm, kofc) |
| `kof lsp` | Language Server (stdio, LSP 3.x) — hover/completion + .ks preprocess |
| `kof editor <list\|detect\|status\|setup\|install\|uninstall\|update>` | Editor integration (EDI001): detects VS Code/Vim/Neovim/IntelliJ/Geany/Nano/Emacs and installs the official integration (grammar + `kof lsp`), with consent. `install <editor>` writes only to HOME; `uninstall` removes only what Kof wrote. Docs: `docs/editors/` |
| `kof version` | Platform version (0.3.22-beta) |
| `kof bench [...]` | Benchmark harness with baselines |
| `kof debug <file.kf>` | DAP MVP on the JVM |

`kof fmt` (real parser, idempotent) and `kof config gen` are implemented
(0.3.22-beta). There is no `kof doctor` command — the official diagnostic is
`kof info`.

## KofScript

```bash
kof script app.ks --target jvm --watch --inspect
let x = 5
// top-level let/const → KofScriptGlobals static fields
```

## KofCcompiler

```bash
kof c app.c
# int globals, void funcs, if/while, *(int*), & → ELF x86_64 via as/ld
```

## Tooling API Level

- The tooling Java API baseline is 21.
- Kof does not require a Java earlier than 21 for its tooling.
- Later versions (e.g. 25, Virtual Threads) may be used
  internally without becoming a requirement.
- The official package ships its own JVM (Temurin 21).

## Editor support

- Editor support travels with the distribution.
- Official grammar: `editor/kof.tmLanguage.json` (scope `source.kof`),
  consumable by VS Code, IntelliJ (TextMate) and compatible highlighters.
- Semantics and diagnostics: `kof lsp` — any LSP editor (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot).
- **Rule: never duplicate the parser in an editor.** The editor consumes
  Kof's tooling; the LSP consumes the compiler's real frontend.
- LSP now supports `.ks` (KofScript) with `let` → `var` preprocess and `main()` wrap.

## LSP

- `kof lsp` implements LSP 3.x over stdio (Content-Length framing, JSON-RPC 2.0).
- Messages: initialize, initialized, shutdown, exit, didOpen, didChange,
  publishDiagnostics, hover, completion.
- Diagnostics are produced by the real CompilerDriver — the same codes and
  messages as `kof check`/`kof build` (includes `a.b.C` import fix 27/08).
- Document sync: full (change: 1).
- No parallel parser: editor and compiler always agree.

## `kof info`

Reports: Kof version (0.3.22-beta), compiler/runtime/stdlib version, tooling API level, target/architecture, OS, embedded JVM, JVM
version, available targets (jvm, native, native.risc, native.arm, js, kofc) and installation location.
Human-readable; `--json` for structured format.

## Important rules

- Preserve `kof build`, `kof install` (compatibility), `kof run`, `kof serve`, `kof script`, `kof c`.
- Do not create `kof doctor` — the diagnostic command is `kof info`.
- Formatter, config gen and test runner are consumed from the official frontend, with no
  parallel implementations.
