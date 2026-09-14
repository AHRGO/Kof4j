[English](README.md) | [Português](README.pt_BR.md)

# Kof Tooling

**Tooling API Level: 21**

Kof's tooling is an official part of the distribution. The user does not need
to discover external projects to get syntax highlighting, diagnostics or
formatting — everything ships inside the Kof package.

---

## 1. Architecture

```text
Kof installation
        │
        └── tooling
              ├── syntax definition      (editor/kof.tmLanguage.json)
              ├── language server        (kof lsp)
              ├── formatter              (planned — kof fmt)
              └── diagnostics            (kof check / LSP publishDiagnostics)
```

**Fundamental rule:** there is no parallel parser for the editor. The editor
consumes Kof's tooling, and the tooling consumes the **same frontend** as the
compiler:

```text
Editor
   │
   ▼
Kof Language Server  (kof lsp)
   │
   ▼
Kof Compiler Frontend
   ├── Lexer
   ├── Parser
   ├── Symbol Table
   ├── Type System
   └── Diagnostics
```

This prevents the divergence between "the compiler accepts" and "the editor
thinks it is wrong".

---

## 2. Tooling API Level 21

The Java API baseline for all tooling is **Java 21**:

- tooling APIs are compatible with Java 21;
- Kof does not require Java older than 21;
- later OpenJDK versions (e.g.: 25, for Virtual Threads) may be used
  internally when appropriate, without becoming a requirement;
- the official package carries its own JVM (Temurin 21).

---

## 3. Components

| Component | Status | Command/File |
|------------|--------|------------------|
| Official grammar | ✅ | `editor/kof.tmLanguage.json` (scope `source.kof`) |
| Language Server | ✅ (minimal) | `kof lsp` (stdio, LSP 3.x) |
| Type-check | ✅ | `kof check <file.kf\|dir>` |
| Test runner | ✅ | `kof test <file.kf\|dir>` (PASS/FAIL by exit code) |
| Environment diagnostics | ✅ | `kof info [--json]` |
| Formatter | 🔜 planned | `kof fmt` |


---

## 4. Consumption by editors

See [EDITOR_SUPPORT.md](EDITOR_SUPPORT.md) for the step-by-step for VS Code,
IntelliJ, Neovim and LSP editors.

---

## 5. LSP

`kof lsp` implements the Language Server Protocol over stdio. Capabilities:

- `initialize` / `shutdown` / `exit`
- `textDocument/didOpen` / `didChange` (full sync)
- `textDocument/publishDiagnostics` with the compiler's real frontend

See [LSP.md](LSP.md).

---

## 6. Formatter (planned)

`kof fmt` will use the same frontend AST to rewrite the file with the
canonical formatting. With no parsing implementation of its own — the
formatter consumes the official parser's output, ensuring that `kof fmt`
never changes the program's semantics.

---

## 7. Diagnostics

`kof check` runs the full pipeline (Lexer → Parser → Semantic Analysis)
without emitting code, reporting all errors. The LSP publishes the same set
of diagnostics, with the same codes, at edit time. With the `--json` flag,
`kof check` produces structured output for analysis and CI/CD tools.
