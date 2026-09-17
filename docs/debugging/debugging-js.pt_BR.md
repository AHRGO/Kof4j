[English](debugging-js.md) | [Português](debugging-js.pt_BR.md)

# DEBUGGING_JS.md — Debug no target KofJS

**Status:** Source maps ✅ parcial 01/09 (mapa V3 por linha `Kof → JS`, `KofJsSourceMapTest`; colunas/expressões pendentes) · **caminho de execução de debug: planejado** (ver `debug-adapter.md`)
**Data:** 27 de agosto de 2026
**Versão:** 0.4.0-beta (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Fluxo

```text
Kof Source
    ↓
KofJS (ES Modules)
    ↓
Source Map (Kof → JS)
    ↓
Node Inspector / Chrome DevTools
    ↓
kof-debug (DAP)
    ↓
Editor
```

## 2. Source Maps

O JsBackend gera `.mjs` + **source map V3 por linha** que mapeia cada linha JS
para a linha Kof (`KofJsSourceMapTest`). Breakpoint em `main.kf:15` para na
linha JS correspondente. Mapeamento por coluna e avaliação de expressões estão
pendentes.

O usuário nunca procura o `.mjs` manualmente.

## 3. Execução

`kof debug --target js app.kf` lança o runtime com `--inspect` e o
adaptador conversa pelo protocolo do Inspector.