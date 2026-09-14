[English](EDITOR_SUPPORT.md) | [Português](EDITOR_SUPPORT.pt_BR.md)

# Editor Support

Editor support for Kof is distributed with the language itself:

- **TextMate Grammar** — `editor/kof.tmLanguage.json` (scope `source.kof`)
- **Language Server** — `kof lsp` (LSP 3.x over stdio)
- **Diagnostics** — the same as the compiler's, via LSP or `kof check`

No editor needs its own parser. The editor consumes Kof's tooling.

> **Automatic installation:** `kof editor setup` detects your editors and
> installs the recommended integrations (with consent). Documentation per
> editor in [`docs/editors/`](../editors/overview.md). Infra and plan:
> `docs/development/plan-editor-integration.md` (EDI001).

---

## VS Code

Create a local extension pointing to the official grammar:

```json
// .vscode/extensions.json (or extension/package.json)
{
  "contributes": {
    "languages": [{
      "id": "kof",
      "aliases": ["Kof", "kof"],
      "extensions": [".kf"],
      "configuration": "./language-configuration.json"
    }],
    "grammars": [{
      "language": "kof",
      "scopeName": "source.kof",
      "path": "./syntaxes/kof.tmLanguage.json"
    }]
  }
}
```

For edit-time diagnostics, configure `kof lsp` as the language server (e.g.:
via a generic LSP client extension or `vscode-languageserver-node`):

```json
{
  "command": ["kof", "lsp"]
}
```

## IntelliJ

- IntelliJ consumes TextMate grammars in `Settings → Editor → TextMate Bundles`.
- For the full experience, use an LSP plugin (e.g.: LSP4IJ) pointing to
  `kof lsp`.

## Neovim

```lua
-- grammar via vim/helix-style TextMate is supported by treesitter? No —
-- for syntax highlighting use the nvim-treesitter plugin with a dedicated
-- parser OR the LSP for semantics.
vim.lsp.start({
  name = "kof",
  cmd = { "kof", "lsp" },
  root_dir = vim.fs.root(0, { "VERSION", ".git" }),
})
```

The recommended path for Neovim is the LSP: semantic highlights and
diagnostics come from the official frontend, without duplicating the parser.

## Generic LSP editors (Helix, Kakoune, Emacs Eglot, etc.)

Configure the `kof lsp` command as the language server for `source.kof`.

---

## Why grammar + LSP and not a parser per editor?

Because duplicating the parser in each editor guarantees divergence: the
editor would "accept" code that the compiler rejects and vice versa. With the
LSP consuming the real frontend, the editor sees exactly what the compiler
sees.

---

## What ships in the distribution

```text
kof/
├── tooling/           # this document + conventions
├── editor/
│   └── kof.tmLanguage.json
└── bin/kof            # includes the `lsp` command
```
