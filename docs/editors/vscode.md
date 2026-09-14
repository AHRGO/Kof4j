[English](vscode.md) | [Português](vscode.pt_BR.md)

# VS Code

Integration: local extension with TextMate grammar + commands; semantics via
`kof lsp`.

## Automatic installation

```bash
kof editor install vscode
```

Writes to `~/.vscode/extensions/kof.kof/`:

- `syntaxes/kof.tmLanguage.json` — the official grammar (read from the distribution;
  embedded fallback if absent — never downloads from the internet).
- `language-configuration.json` — comments, brackets, auto-close.
- `extension.js` — registers the Command Palette commands (`Kof: Build/Run/
  Test/Check/Format/Serve/Start LSP/Select Target/Open Docs`), each
  delegating to the official CLI in an integrated terminal. Kof does **not**
  reimplement build/run/format inside the editor — the CLI is the source (§15/§20).
- `snippets/kof.json` — idiomatic snippets (`main`, `fn`, `rec`, `cls`,
  `ife` (if-expression), `for`, `sw` (switch-expression), `sp` (spawn), `try`).
- `package.json` — registers the language (`*.kf`/`*.kof`, `source.kof`), the
  snippets, the config (`kof.executable`, `kof.target`) and the commands.

Then, reload VS Code. For LSP, configure the client (e.g.: the
"vscode-languageserver-node" extension or similar) with the command `["kof", "lsp"]`.

## Manual installation

Copy the extension folder to `~/.vscode/extensions/` and point the LSP client
to `kof lsp`.

## Troubleshooting

- **No highlight:** confirm that the extension was loaded (`Developer: Show
  Running Extensions`).
- **No diagnostics:** the LSP must be running — test `kof lsp` in the
  terminal (it should wait on stdio, not error).
