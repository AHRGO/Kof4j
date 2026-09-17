[English](LSP.md) | [Português](LSP.pt_BR.md)

# Kof Language Server

`kof lsp` is the official Kof Language Server, distributed with the CLI.

---

## Architecture

```text
Editor
   │  (LSP over stdio)
   ▼
kof lsp
   │
   ▼
Kof Compiler Frontend
   ├── Lexer
   ├── Parser
   ├── Symbol Table
   ├── Type System
   └── Diagnostics
```

The server has no parser of its own. Each open document is compiled with the
real `CompilerDriver` (pipeline Lexer → Parser → Semantic Analysis) and the
diagnostics produced are published to the editor via
`textDocument/publishDiagnostics`, with the same codes (e.g.: `PARSE041`,
`JSN001`) and messages that `kof check`/`kof build` report.

---

## Protocol

- Transport: stdio, `Content-Length` framing.
- Messages: JSON-RPC 2.0.
- Document sync: full (`change: 1`).

### Supported messages

| Message | Behavior |
|----------|---------------|
| `initialize` | Capabilities: textDocumentSync (full), serverInfo `kof-lsp` |
| `initialized` | no-op |
| `textDocument/didOpen` | compiles and publishes diagnostics |
| `textDocument/didChange` | recompiles and publishes diagnostics |
| `textDocument/hover` | hover info for the symbol at the position |
| `textDocument/definition` | go-to-definition (single file) |
| `textDocument/completion` | completion (trigger `.`) |
| `textDocument/references` | references (word-boundary, single file) |
| `textDocument/rename` | rename (word-boundary, single file) |
| `shutdown` | responds `null` |
| `exit` | terminates the process |

### Diagnostics

Each compiler `Diagnostic` is mapped to the LSP format:

- `line`/`column` (1-based) → LSP position (0-based);
- ERROR severity → 1, others → 2;
- `source: "kof"`, `code` preserved;
- message same as the compiler's.

---

## Usage

```bash
kof lsp
```

The server reads from `stdin` and writes to `stdout` — it integrates with any
LSP client (`cmd: ["kof", "lsp"]`).

---

## Current limitations

- Single-file analysis: `definition`/`references`/`rename` are word-boundary
  based within the open document (no cross-file project index);
- full document sync (incremental planned);
- no formatting via LSP (the `kof fmt` formatter is a separate, implemented
  command).

The evolution path is always the same: **new LSP capabilities feed on the
official frontend**, never on a parallel parser.
