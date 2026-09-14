[English](LSP.md) | [Português](LSP.pt_BR.md)

# Kof Language Server

`kof lsp` é o Language Server oficial do Kof, distribuído com a CLI.

---

## Arquitetura

```text
Editor
   │  (LSP sobre stdio)
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

O servidor não possui parser próprio. Cada documento aberto é compilado com
o `CompilerDriver` real (pipeline Lexer → Parser → Análise Semântica) e os
diagnósticos produzidos são publicados ao editor via
`textDocument/publishDiagnostics`, com os mesmos códigos (ex.: `PARSE041`,
`JSN001`) e mensagens que `kof check`/`kof build` reportam.

---

## Protocolo

- Transporte: stdio, framing `Content-Length`.
- Mensagens: JSON-RPC 2.0.
- Sync de documentos: completa (`change: 1`).

### Mensagens suportadas

| Mensagem | Comportamento |
|----------|---------------|
| `initialize` | Capacidades: textDocumentSync (full), serverInfo `kof-lsp` |
| `initialized` | no-op |
| `textDocument/didOpen` | compila e publica diagnostics |
| `textDocument/didChange` | recompila e publica diagnostics |
| `shutdown` | responde `null` |
| `exit` | encerra o processo |

### Diagnósticos

Cada `Diagnostic` do compilador é mapeado para o formato LSP:

- `line`/`column` (1-based) → posição LSP (0-based);
- severidade ERROR → 1, demais → 2;
- `source: "kof"`, `code` preservado;
- mensagem igual à do compilador.

---

## Uso

```bash
kof lsp
```

O servidor lê de `stdin` e escreve em `stdout` — integra-se a qualquer
cliente LSP (`cmd: ["kof", "lsp"]`).

---

## Limitações atuais (Alpha)

- Sem autocomplete, hover ou go-to-definition (planejado);
- sync completa de documentos (incremental planejado);
- sem formatação via LSP (o formatter `kof fmt` é planejado separadamente).

O caminho de evolução é sempre o mesmo: **novas capacidades do LSP
alimentam-se do frontend oficial**, nunca de um parser paralelo.