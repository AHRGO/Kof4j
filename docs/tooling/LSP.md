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
| `textDocument/hover` | keywords, builtin types and buffer vars/vals (`LspHover`); **— and the DOMAIN**: a stdlib namespace shows its `kof.<ns>` members (real list from `StdCatalog`, the same source as completion), a member in the exact `ns.` context names its namespace (8.3, 19/09) and, for the tabled namespaces (`db`/`http` — LSP-A fatia 1), also its recorded signature(s), one overload per line (`StdCatalog.signaturesOf`, behavior-locked against the real dispatcher); members without a table keep the simple line — no invented shapes (R6); a loose name without `.` never guesses (R6); **fallback = the declaration line of the symbol in the project** — buffer first, then sibling `.kf` files (X10 fatia 7, `LspProject.declarationLine`) |
| `textDocument/definition` | go-to-definition in the buffer **and across the project** — unknown name falls back to sibling `.kf` files (tree walk ≤6, first hit; same `LspSymbols` convention; X10 fatia 4) |
| `textDocument/completion` | trigger `.`: **domain-aware stdlib members** from `StdCatalog` — the 31 real typer namespaces (math, strings, rng, json, log, db, http, Image/Audio/Video/Mic, ...), locked against the typer sources (X10 fatias 1–3); non-stdlib prefix = zero invention |
| `textDocument/references` | word-boundary, in the buffer **and in the project's sibling `.kf` files** (read-only; X10 fatia 5) |
| `textDocument/rename` | **cross-file rename (LSP-A ✅ 19/09, `LspRename`)**: word-boundary edits over the open buffer + every project `.kf` (same textual convention as `references`); keywords and stdlib namespaces refuse with null (never rewrite the language) |
| `textDocument/formatting` | formats via the `kof fmt` formatter (same engine, no parallel writer) |
| `textDocument/documentSymbol` | outline of the buffer (types + functions, textual scan) |
| `workspace/symbol` | symbols of the **whole project**: open buffers (source of truth) + unopened `.kf` siblings; substring filter, prefix→substring→name→uri order (X10 fatia 6) |
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

- The cross-file project scan (`definition`/`references`/`hover`/`workspace`
  symbol) is a **textual convention** (`LspSymbols` — the same one as the
  single-file navigation), not a typed/semantic index: it never lies about a
  position it did not read, but it does not disambiguate same-name symbols
  across files (first hit, deterministic order);
- `rename` is textual, not typed: like `references` it renames EVERY word-boundary occurrence of the name in the project — same-name identifiers in unrelated files are aliased by the same edit (the client previews before applying; no typed index exists on purpose). Renaming a keyword or a stdlib namespace returns null (R6).


The evolution path is always the same: **new LSP capabilities feed on the
official frontend**, never on a parallel parser.
