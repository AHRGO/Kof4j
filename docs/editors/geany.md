[English](geany.md) | [Português](geany.pt_BR.md)

# Geany

Integration: filetype with syntax, indentation and build/run commands that
delegate to the CLI.

## Automatic installation

```bash
kof editor install geany
```

Writes `~/.config/geany/filedefs/filetypes.kof`:

- comments `//` and `/* */`, extension `.kf`, name "Kof";
- `[build]`: `compiler=kof build`, `execute=kof run`;
- `[error_messages]`: regex that matches `file:line:column: error/warning: msg`
  — the compiler diagnostics appear clickable in the message bar.

## Usage

- `F8` compiles (`kof build`), `F5` runs (`kof run`).
- Errors/warnings navigate to the line in the editor (error parsing).

## Troubleshooting

- If the filetype does not appear: `Document → Set Filetype → Kof`.
- Adjust the `kof` path in the `.conf` file if it is not in the PATH.
