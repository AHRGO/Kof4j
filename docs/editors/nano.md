[English](nano.md) | [Português](nano.pt_BR.md)

# Nano

Integration **proportional to the editor**: only syntax highlighting and
file recognition. Nano is not an IDE — semantics (diagnostics/LSP) do not
apply here; for that, use an LSP editor.

## Automatic installation

```bash
kof editor install nano
```

Writes `~/.nano/kof.nanorc` with colors for keywords, types, constants,
strings and comments.

## Configuration

Make sure nano reads the syntax directory (Linux: `nano` already looks for
`~/.nano/*.nanorc`; on other platforms, add it to your `~/.nanorc`):

```
include "~/.nano/kof.nanorc"
```

## Usage

```bash
nano Arquivo.kf
```

Compile/run via the CLI (`kof build` / `kof run`) — nano is editing only.
