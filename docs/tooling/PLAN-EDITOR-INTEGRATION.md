[English](PLAN-EDITOR-INTEGRATION.md) | [Português](PLAN-EDITOR-INTEGRATION.pt_BR.md)

# PLAN — Editor Integration (EDI001)

> **Status:** `CONCLUÍDO (13/09)` (degraus 0-13 implementados e provados; movido para docs/tooling/PLAN-EDITOR-INTEGRATION.md) · **Gap:** `EDI001` · **Criado:** 07/09/2026
> installed 13/09 — filetype XML + External Tools + README LSP4IJ via
> `KofEditorContent.intellij`, `EditorIntegrationTest` 17/17; official plugin
> Gradle/Platform continues in issue #1 + step 13 final gate) · **Gap:** `EDI001` · **Created:** 07/09/2026
> **Proof step 12 (measured 13/09):** `docs/editors/` 8 docs (overview+7
> editors) + `training/tooling/cli.md:25` (table `kof editor …`) +
> `learn/38-editors.md` (detect/setup/install). Remaining: IntelliJ plugin
> (own Gradle subproject, §21) + step 13 (final gate).
> **Proof steps 6-9 (measured 13/09, owner = 192.168.100.22):** the providers
> vim/emacs/geany/nano existed since the steps, but **only** vscode/neovim/
> intellij had an installation test (Q1: feature without proof). Now
> `EditorIntegrationTest` covers the generated config of the 4 (ftdetect+syntax+compiler
> in vim; `kof-mode.el` with auto-mode-alist in emacs; `filetypes.kof` with
> build/run in geany; `kof.nanorc` syntax only in nano) — **21/21**. No
> provider touches the real environment (§24, fake DetectContext).
> **Contract fix §6 (13/09):** `kof editor` without a subcommand was **usage**,
> diverging from §6 ("alias of `detect`"); fixed + test
> (`bareEditorIsDetectAliasAndHelpShowsUsage`). `update` gained a
> re-synchronization test (`updateResyncsInstalledIntegrations`) — **23/23**.
> **`workspace/executeCommand` (optional step 0, P2) remains deferred:**
> VS Code delegates `Kof: Build/Run/Test` to the CLI in a terminal (§15/§20), so
> the capability is not needed for the release gate; adding it requires splitting
> `LspServer` (501 lines, in the ratchet). Register as a separate gap.
> **Origin:** briefing "KOF EDITOR INTEGRATION" (official infrastructure for
> editor/IDE integration). **Scope of this doc:** specification + implementation order.
> **The implementation is LATER** — this document is the contract.
> **Rule:** nothing here is an *action* on the current work (R12); it does not start before
> being claimed in `DOING.md`. It reuses what already exists; **never** a
> second LSP/formatter/build (R9 interop-first, R6 never silent).

---

## 0. Audited real state (what ALREADY exists — do not recreate)

| Capability | Where today | State |
|---|---|---|
| Grammar TextMate | `editor/kof.tmLanguage.json` (`source.kof`, `.kf`/`.kof`) | present |
| Language Server | `kof-cli/.../LspServer.java` (LSP 3.x, stdio) | completion, hover, rename, references, publishDiagnostics |
| Diagnostics | identical to the compiler, via LSP or `kof check` | present |
| Formatter | `kof-cli/.../Fmt.java` (`kof fmt`) | present |
| Build/Run/Test | `kof build` / `run` / `test` / `serve` | present |
| Debugger (DAP) | `KofDebug.java` + `JdwpClient` (`kof debug`, JDWP) | PARTIAL |
| Workspace detection | `ProjectLocator` (walks up to `kof.toml`) | present |
| Support doc | `docs/tooling/EDITOR_SUPPORT.md` (grammar + LSP per editor) | present, light |
| Installer | copies the `editor/` folder to the prefix | present (passive) |
| `kof editor` command | **Does NOT exist** (no `CmdEditor`) | absent — it is the core of this plan |

**Audit conclusion:** the plan does NOT start from zero. What is missing is (a) the
**EditorIntegration infra** (detector/registry/installer), (b) the **`kof editor`
command**, (c) **per-editor integrations** that package grammar+LSP+fmt
idiomatically, and (d) **docs**. The LSP is already the central layer (section 15 of
the briefing) — this plan only **exposes** it, never duplicates it.

---

## 1. Objective

After installing Kof, `kof editor detect` lists installed editors + available
integrations; `kof editor setup` installs the recommended ones (with consent);
opening a `.kof` gives LSP + autocomplete + diagnostics + formatter without the user
having to search "how to configure Kof in my editor".

---

## 2. Do NOT couple the core to editors

Forbidden `if (vscode)`/`if (vim)` spread across the CLI. Provider
abstraction:

```
EditorIntegration          (interface — the contract)
├── id() / displayName()
├── detect(): EditorInfo?     // null = not installed
├── integrationAvailable()    // does an official package/config exist?
├── integrationInstalled()    // already configured on this machine?
├── install(ctx) / uninstall(ctx)
├── configure(ctx)            // LSP/formatter/filetype
└── status(): IntegrationStatus
```

Proposed structure (adapted to the real architecture — see §3, the provider lives in
`kof-cli`, not in a new module):

```
kof-cli/.../cli/editor/
├── EditorIntegration.java     (interface)
├── EditorInfo.java            (record: id, version, path, available, installed)
├── EditorRegistry.java        (List<EditorIntegration> — registry, no if-by-id)
├── EditorDetector.java        (PATH + per-platform dirs; no /usr/bin hardcoded)
├── EditorConfig.java          (what to write: LSP cmd, root, filetype, formatter)
├── EditorInstaller.java       (copies/configures; idempotent; consent)
├── EditorStatus.java
└── providers/
    ├── VscodeProvider.java
    ├── IntelliJProvider.java
    ├── VimProvider.java
    ├── NeovimProvider.java
    ├── GeanyProvider.java
    ├── NanoProvider.java
    └── EmacsProvider.java
```

`CmdEditor.java` (root of `cli/`) dispatches `kof editor <sub>` to
list/detect/status/setup/install/uninstall/update.

---

## 3. Dependency architecture (where each thing lives)

- **Provider logic** → `kof-cli` (it knows PATH, fs, installers).
- **Integration content** (grammar, config snippets) → `editor/` folder
  (already travels in the distribution; §14: no network when possible).
- **LSP/formatter/debug** → **already exist** and are called via CLI; the provider
  only points the editor to `kof lsp`/`kof fmt`/`kof debug`. **No parser per
  editor.**
- **Tests** → `kof-cli/src/test/.../editor/` (mocks/fakes, §23-24).

---

## 4. Priority editors

| Editor | Package/config | Minimum scope | Notes |
|---|---|---|---|
| VS Code | local extension | grammar, LSP, diagnostics, completion, hover, go-to-def, references, rename, formatting, code actions, commands, debug (when DAP is ready) | consumes `kof lsp`; snippets+commands via `package.json` |
| IntelliJ | plugin (Platform) | `.kof` recognition, highlighting, LSP (LSP4IJ), diagnostics, completion, formatting, navigation, run/build | delegates to `kof check/build/run/test/fmt/lsp` |
| Vim | `ftplugin`+`syntax` | filetype, syntax, indent, compiler, LSP, formatting | idiomatic config |
| Neovim | lua plugin | filetype, syntax, indent, LSP, diagnostics, completion, formatting, code actions, navigation | `vim.lsp.start({cmd={"kof","lsp"}})` |
| Geany | `.conf` | filetype, syntax, indent, build/run, compiler; LSP if supported | |
| Nano | `syntaxes/kof.nanorc` | syntax, filetype, official config | proportional to the editor — **not** an IDE in Nano |
| Emacs | `kof-mode` | `kof-mode`, syntax, indent, LSP (eglot), diagnostics, formatting, commands | reuses eglot |

All recognize `*.kof` (§16) and point to the **same** LSP (§15).

---

## 5. Editor detection

Cross-platform (Linux/macOS/Windows). **No** hardcoded `/usr/bin`. Sources:
PATH, known executables, known config dirs, native mechanisms.
Report `editor / version / path / integration available / installed`.
**Do not invent versions** (if unknown, `unknown`).

Probes per editor (to consolidate in `EditorDetector`):

| Editor | Probe |
|---|---|
| VS Code | `code --version`; config in `~/.vscode`, `%APPDATA%\Code`, `~/Library/Application Support/Code` |
| Vim | `vim --version` |
| Neovim | `nvim --version` |
| IntelliJ | dirs `~/Library/Application Support/JetBrains`, `~/.config/JetBrains`, `%APPDATA%\JetBrains` |
| Geany | `geany --version` |
| Nano | `nano --version` |
| Emacs | `emacs --version` / `emacsclient --version` |

---

## 6. `kof editor` commands

```
kof editor                # = detect (alias)
kof editor list           # available integrations (independent of detected)
kof editor detect         # detected editors + integrations
kof editor status         # detail: version, path, installed, LSP (see §12)
kof editor setup          # main flow (see §7)
kof editor install <id>   # vscode|intellij|vim|neovim|geany|nano|emacs
kof editor uninstall <id>
kof editor update         # re-synchronizes installed integrations
```

`setup`/`install`/`uninstall` **never** change the environment without consent
when the operation is visible to the user (§12, §14).

---

## 7. `kof editor setup` — flow

```
1. detect editors
2. detect versions
3. detect existing integrations
4. check compatibility
5. show recommendations
6. ask for confirmation
7. install integrations
8. configure LSP/formatter
9. validate installation
10. show result
```

Idempotent: running again does not duplicate config. Declined → show
`kof editor setup` for later. **Never** blocks the Kof installation.

---

## 8. Official installer

During the Kof install, detect editors and **offer** (with `Y/n`) the
recommended integrations. Declined → `kof editor setup` later. It does not block
the installation nor fail if no editor exists.

---

## 9. No network when possible

Integration files **travel in the distribution** (`editor/` folder). It only uses the
editor's official mechanism when a marketplace requires an external download;
**never** arbitrary code from an unknown URL.

---

## 10. LSP as the central layer

```
                 Kof LSP (kof lsp — ALREADY EXISTS)
        ┌───────────────┼───────────────┐
      VSCode         Neovim          IntelliJ
        │              │              │
       Vim           Emacs          others
```

All share what the LSP already exposes: diagnostics, hover, completion,
references, rename, definition, formatting, code actions. **No** semantic
rule specific per editor (R6/R9).

**LSP — capabilities today vs. target of this plan** (the LSP is the
semantics bottleneck; the provider only consumes):

| Capability | `LspServer.java` today | Action |
|---|---|---|
| `textDocument/completion` | present | — |
| `textDocument/hover` | present | — |
| `textDocument/rename` | present | — |
| `textDocument/references` | present | — |
| `textDocument/publishDiagnostics` | present | — |
| `textDocument/definition` | **absent** | add (gap in the LSP, not in the provider) |
| `textDocument/documentSymbol` | absent | add (optional, P2) |
| `textDocument/codeAction` | absent | add (optional, P2) |
| `textDocument/formatting` | absent (fmt is CLI) | expose via LSP (P2) |
| `workspace/executeCommand` | absent | add for `Kof: Build/Run/...` (§19) |

> Rule: if a capability is missing, it is a **gap in the LSP** (claim separately), not
> something for the provider to "solve" with its own parser.

---

## 11. File association + workspace

`*.kof` → Kof in all editors. Workspace detected by `kof.toml`
(`ProjectLocator` already walks up to it); the editor uses source roots/dependencies/
targets/LSP/formatter/compiler/test runner from there.

---

## 12. Status / diagnostics

`kof editor status` prints:

```
Kof Editor Environment
Kof:
  version / compiler: OK / LSP: OK / formatter: OK / debugger: PARTIAL
Editors:
  VS Code      integration: installed   LSP: connected
  Neovim       integration: installed   LSP: configured
  IntelliJ     integration: available   not installed
  Vim          integration: installed
```

`debugger` always `PARTIAL` until the DAP closes (R6, never hide).

---

## 13. Target selection + editor commands

The architecture **allows** (does not require complex UI now) choosing a target
(`kof build --backend=jvm --frontend=kofjs`). Equivalent commands, via the
idiomatic mechanism of each editor:

```
Kof: Build / Run / Test / Check / Format / Serve / Start LSP / Select Target / Open Docs
```

**Terminal is sovereign** (§20): the CLI always works; the integration is convenience.

---

## 14. Debugger (DAP)

```
Editor → DAP → Kof Debug Adapter → runtime/JVM
```

No debugger per editor. It uses the existing `kof debug`/JDWP; **PARTIAL** until the
DAP closes.

---

## 15. Tests

Cover: detection (present/absent/multi-version/custom PATH/Linux/macOS/
Windows), installation (install/already-installed/incompatible/uninstall/update/
failure), configuration (LSP/file association/formatter/project root/exec path),
CLI (list/detect/status/setup/install/uninstall).

**Hard rule (§24):** tests use a temporary fs, mocks and fake editor
installations. They **never** install real plugins on the suite's machine.

---

## 16. Platforms (architecture)

Linux x86_64/ARM64, macOS x86_64/ARM64, Windows x86_64. The detector is the only
end that knows paths — isolate it in `EditorDetector` for the 3 OSes.

---

## 17. Extensibility

Adding an editor = adding 1 `Provider` + 1 entry in `EditorRegistry` +
test. **Without** touching the CLI core. Future: Sublime, Helix, Zed, Kate,
Eclipse, Fleet, Android Studio, Cursor, Windsurf.

---

## 18. Documentation

```
docs/editors/
├── overview.md
├── vscode.md  intellij.md  vim.md  neovim.md
├── geany.md   nano.md      emacs.md
```

Each doc: manual + automatic installation, configuration, LSP, formatter,
debugging, troubleshooting. **And** update `training/` (teach agents/LLMs to
configure a Kof environment) + `learn/`.

---

## 19. Release gate (not "closed" with only VS Code)

**Infra:** cross-platform detector, registry, `kof editor` CLI, install,
uninstall, status, setup.
**Integrations:** VS Code, IntelliJ, Vim, Neovim, Geany, Nano, Emacs.
**Tooling:** `.kof` recognition, LSP, diagnostics, formatter, build, run, test.
**Quality:** idempotent installation, without improperly changing the environment,
automated tests, documentation, cross-platform.

---

## 20. Implementation order (committable steps)

> Each step = 1 cohesive unit with proof (green test). Step 0 is a
> **precondition** (gap in the LSP, claimable separately).

| # | Step | Delivery | Proof |
|---|---|---|---|
| 0 | LSP `definition` (+ optional: documentSymbol, codeAction, formatting, workspace/executeCommand) | `LspServer.java` | LSP test per capability |
| 1 | Abstraction `EditorIntegration`/`EditorInfo`/`EditorRegistry`/`EditorDetector`/`EditorConfig` (no concrete provider yet) | infra in `cli/editor/` | detector test with fake PATH |
| 2 | `CmdEditor` + `list`/`detect`/`status` (read-only) | CLI | output test by mock |
| 3 | `setup`/`install`/`uninstall`/`update` (idempotent + consent) | CLI + `EditorInstaller` | test in temp fs (no network) |
| 4 | VS Code Provider (grammar+LSP+snippets+commands) | `editor/` + provider | generated config test |
| 5 | Neovim Provider (lua LSP) | provider | generated config test |
| 6 | Vim Provider (ftplugin+syntax+indent+compiler) | provider | generated config test |
| 7 | Emacs Provider (`kof-mode`+eglot) | provider | generated config test |
| 8 | Geany Provider (`.conf`) | provider | generated config test |
| 9 | Nano Provider (`.nanorc`) | provider | generated config test |
| 10 | IntelliJ Provider (LSP4IJ plugin + language) | provider | generated config test |
| 11 | Installer hook (offer at install, §8) | installer | flow test |
| 12 | Docs `docs/editors/*` + `training/` + `learn/` | docs | review + CI lint |
| 13 | Final gate: complete suite + release gate §19 | — | green `mvn test` |

> **Step 13 — gate run 13/09 15:22 (owner = 192.168.100.22):** 4-module suite
> green — `kof-compiler` 1485/0 (156 skip), `kof-script` 31/0,
> `kof-c-compiler` 5/0, `kof-cli` 181/0; `grep -rl FAILURE` empty; BUILD
> SUCCESS (10m37s). The skips are qemu/external DB. `check_500` OK.
> **Residual of release gate §19:** official IntelliJ plugin (issue #1, §21) —
> it is a scope decision, not code; the local distribution via `kof editor` is
> complete for the 7 editors.

**Dependencies:** 0 (LSP) is independent and can go first/in parallel. 1→2→3 in
sequence. 4-10 (providers) independent among themselves after 3 (parallelizable,
one per agent). 11,12,13 at the end.

---

## 21. Limitations / design decisions (rule 6 — do NOT decide here)

- **IntelliJ plugin** requires a Gradle/IntelliJ Platform build — it is its own
  subproject; the "provider" only orchestrates/packages. Decide scope (P2?) separately.
- **DAP** still PARTIAL — editor debug stays `PARTIAL` until it closes.
- **`definition`/`codeAction` in the LSP** = capability change (additive);
  validate with the LSP suite first.
- **Marketplace** (VS Code/IntelliJ) involves external publication — outside the
  repo scope; the local distribution (`editor/`) is the network-free path.

---

## 22. Answers required at the end (briefing contract)

On completion, report: (1) changed files, (2) architecture created,
(3) detected editors, (4) implemented integrations, (5) features
per editor, (6) added tests, (7) suite result, (8) remaining
limitations.
