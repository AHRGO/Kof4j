[English](overview.md) | [Português](overview.pt_BR.md)

# Editor Integration — Overview

> Kof is not just compiler + runtime. Installing Kof should leave your
> development environment ready to program. This is the official editor
> integration infrastructure (EDI001).

The desired experience:

```
install Kof → kof detects your editor → offers the integration → confirm →
install → open a .kof → LSP + autocomplete + diagnostics + formatter →
start programming.
```

---

## Central layer: the LSP

All editors share the **same** official Language Server (`kof lsp`).
No editor implements its own parser or semantic rule — that would guarantee
divergence (the editor would "accept" what the compiler rejects). The editor
consumes Kof's tooling:

```
                    kof lsp  (LSP 3.x, stdio)
        ┌───────────────┼───────────────┐
      VS Code        Neovim         IntelliJ
        │              │              │
       Vim           Emacs          others
```

| Feature | Source |
|---|---|
| Syntax highlighting | TextMate grammar (`editor/kof.tmLanguage.json`) or the editor's native syntax |
| Diagnostics / completion / hover / rename / references | `kof lsp` |
| Formatting | `kof fmt` |
| Build / Run / Test / Check / Serve | `kof build` / `run` / `test` / `check` / `serve` |
| Debugging | `kof debug` (DAP — **PARTIAL**) |

---

## CLI `kof editor`

```bash
kof editor list       # official integrations available
kof editor detect     # installed editors + integrations
kof editor status     # editing environment (version, path, LSP, installed)
kof editor setup      # detects and installs the recommended ones (with consent)
kof editor install <editor>     # vscode|vim|neovim|intellij|geany|nano|emacs
kof editor uninstall <editor>
kof editor update     # re-syncs installed integrations
```

`setup` and the post-`kof install` hook **never** change the environment without
consent; in a console-less environment (CI/headless) they only point to the command.
Installation is **idempotent** and `uninstall` removes only what Kof wrote.

---

## File and workspace recognition

- Extensions: `*.kf` and `*.kof` (Kof source).
- Workspace: a project is recognized by the presence of `kof.toml` at the root
  (the LSP and the integrations go up until they find it).

---

## By editor

- [VS Code](vscode.md)
- [Neovim](neovim.md)
- [Vim](vim.md)
- [Emacs](emacs.md)
- [Geany](geany.md)
- [Nano](nano.md)
- [IntelliJ IDEA](intellij.md)

---

## Terminal is sovereign

The integrations are a convenience layer over the CLI. You can always
run `kof build` / `kof test` / `kof run` / `kof serve` manually — nothing is
hidden behind the editor.
