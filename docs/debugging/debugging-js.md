[English](debugging-js.md) | [Português](debugging-js.pt_BR.md)

# DEBUGGING_JS.md — Debugging on the KofJS target

**Status:** Source maps ✅ partial 01/09 (line-level V3 `Kof → JS` map, `KofJsSourceMapTest`; columns/expressions pending) · **debug execution path: planned** (see `debug-adapter.md`)
**Date:** August 27, 2026
**Version:** 0.4.0-beta (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Flow

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

The JsBackend generates `.mjs` + a **line-level V3 source map** that maps each
JS line to the Kof line (`KofJsSourceMapTest`). A breakpoint at `main.kf:15`
stops at the corresponding JS line. Column-level mapping and expression
evaluation are pending.

The user never looks for the `.mjs` manually.

## 3. Execution

`kof debug --target js app.kf` launches the runtime with `--inspect` and the
adapter talks over the Inspector protocol.
