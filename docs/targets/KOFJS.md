[English](KOFJS.md) | [Português](KOFJS.pt_BR.md)

# KofJS — the Kof JavaScript backend

> **Status: alpha.** The pipeline `.kf → Kof IR → KofJS → .js → execution` works
> and runs real programs. The JS target does not depend on Node.js: Kof itself
> runs the generated JavaScript with the embedded engine.

## What it is

KofJS is the Kof backend that generates **modern JavaScript** (ECMAScript 2022+,
ES Modules) from the **same Kof IR** used by the JVM and Native backends.

**It is not a second language.** There is no alternative parser, AST, type
checker or semantics. The frontend is one; the backend changes:

```text
                    Kof Source
                         │
                         ▼
                 ┌──────────────┐
                 │ Kof Frontend │
                 └──────┬───────┘
                        │
                        ▼
                    Kof IR
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
        JVM           Native        KofJS
          │             │             │
       .class          ELF           .mjs
```

## Backend architecture

```text
Kof IR
   ↓
JS Lowering (JsBackend)
   ↓
JsIr  (JsModule / JsClass / JsFunction / JsStatement / JsExpression)
   ↓
JsEmitter
   ↓
.mjs  (ESM, ES2022+)
```

- **JsBackend** — converts the stack-based IR into a JavaScript AST (JsIr).
  The control-flow patterns emitted by the frontend (if/while/for/
  do-while/for-in/switch/try) are reconstructed as native JavaScript
  control flow.
- **JsIr** — the backend's own AST (never a JavaScript parse).
- **JsEmitter** — prints the AST as ESM text.

## Execution — no Node

KofJS **does not depend on Node.js nor on any external runtime**. Kof embeds
a JavaScript engine (GraalJS) and runs the generated module in its own
process:

- `kof run main.kf --target=js` — compiles and runs with the embedded engine.
- The E2E suite also runs this way (without external processes).

The generated JavaScript, however, is **standard ESM** — the same `.mjs` runs
in any engine (browser, Node, Deno, Bun) when the program does not use
platform operations.

## Runtime layers

```text
Default.mjs (generated program — pure JS)
   ├── kof-runtime.mjs     → core platform-neutral (print, List, String, JSON, time)
   └── kof-runtime-io.mjs  → platform operations (filesystem, stdin, stdout)
                               delegates to `kof_platform`, implemented in Java
                               (dev.kof.runtime.KofJsRunner)
```

The generated code never calls `console.*`/`process.*` directly; everything
goes through the runtime. When run by another engine, the core
(`kof-runtime.mjs`) works; IO operations need a corresponding `kof_platform`
(in the browser: fallback with console for `print` and a clear error for IO).

## kof.ui in KofJS

The UI platform (`Color`/`Theme`/`Palette`, `Window`/`Label`/`Button`/
`Input`, `Column`/`Row`, `View`+`Style`) is rendered by KofJS:

1. `kof run --target=js` runs the program in the embedded engine;
2. writes the interactive app (`index.html` + `Default.mjs` + runtimes);
3. the native webview (`bin/kof-webview`, WebKitGTK) runs the page — the DOM
   shim is browser-safe (same code in GraalJS and in the real browser);
4. clicks/editing execute inside the page; closing the window ends the
   program.

The DOM shim in `kof-runtime.mjs` uses the real `document` when it exists
(browser/webview) and a minimal in-memory DOM in the embedded engine — the
serialization (`kofUiFlush`) feeds tests and the static snapshot.

## CLI

```bash
kof build src/ --target=js --output=build/js
kof run main.kf --target=js
```

The JS target is under development and the CLI reports this in `--help`.

## Types and semantics

| Kof | JS | Notes |
|---|---|---|
| `Int` | `number` | arithmetic with 32-bit wrap (`\| 0`), division truncates |
| `Long` | `number` | precision up to 2^53; document larger values |
| `Float` / `Double` | `number` | integral literal prints without `.0` (differs from JVM) |
| `Bool` | `boolean` / `0\|1` | comparisons/equals produce `true/false`; bitwise operations coerce |
| `Char` | `number` | code unit; `charAt` → `charCodeAt` |
| `String` | `string` | direct mapping in the API |
| `List<T>` | `Array` + runtime | bounds check on get/set/remove |
| `Array` | `Array` + runtime | `new Int[n]` → `new Array(n).fill(0)`; read/write via `kofArrayGet`/`kofArraySet` (bounds check — KOF-SBD-001) |
| classes | `class` | native inheritance, super, override |
| records | `class` + accessors | internal fields `_name` to avoid colliding with accessor |
| interfaces | — (type-level) | structural calls `recv.method(...)` |
| generics | erasure | type information stays in the compiler |

### Documented semantic differences

- **Long beyond 2^53** loses precision (JS `number` is double).
- **Bitwise on Long** truncates to 32 bits (JS operators).
- **Float/Double integral literal**: `println(1.0)` → `1` (JVM: `1.0`).
- **Runtime interface** does not exist in JS; the semantics are resolved at
  compile-time (structural calls).
- **`hashCode`/`getClass`** of `Object` have no direct equivalent.

## JSON

```text
json.encode   → JSON.stringify
json.decode   → JSON.parse (with binding to classes/records via helper)
```

Type information remains in the compiler: `json.decode<User>` generates a
helper `__kof_decode_User` that instantiates the class and assigns the fields.

## Source maps

Each module generates `<name>.mjs.map` (v3) with `sources` and
`sourcesContent`. Line-by-line accuracy depends on positions in the Kof IR
(future work); the skeleton is already emitted since the first functional
backend.

## Exceptions

`throw "message"` becomes `throw <string>`; `try/catch/finally` is translated
to JS's native form (the catch-all + rethrow emulated in the IR is eliminated
because JS's `finally` already covers the semantics).

## Tests

`KofJsE2ETest` compiles `.kf` → `.mjs` → runs in the embedded engine and
compares stdout/exit code. Covers: hello world, arithmetic, variables, if/else,
loops, functions, lambdas, classes, constructors, inheritance, interfaces,
generics, List, String API, arrays, JSON, exceptions, ESM, multiple files,
kof.time/kof.io.

## Current state (alpha)

**Works:**
- Full pipeline `.kf → IR → JS → execution` in the embedded engine
- Classes, inheritance, constructors, records, interfaces (type-level)
- List, String API, arrays, JSON (encode/decode with binding)
- Exceptions (try/catch/finally), lambdas **with captures**, if-expressions
- kof.time (now/sleep; scheduler via `setInterval` — 27/08), kof.io (via `kof_platform`), `kof run --target=js`
- **kof.http** via `Java HttpClient` interop in `KofJsRunner` (+ fetch
  fallback); **retry/circuit breaker in parity with the JVM** (30/08)
- `spawn`/`await`/`channel<T>()` with real concurrency via GraalJS's
  `async`/`await`/`Promise` (`CONC003` closed, 03/09) — `KofJsRunner` drains the
  microtask queue (`kofActiveTasks`) until all spawned tasks finish, even
  fire-and-forget never awaited; channel with truly blocking `receive()` on an
  empty channel; `selectAny` via `Promise.race`; `awaitTimeout` really fires
  against a slower task (cooperative polling, no real timer available in the
  embedded GraalJS). Restriction: only lambdas created directly at a `spawn`
  site can become `async` (`CONC003-JS-01` — a common lambda passed to
  `list.map`/`filter`/`reduce` cannot use `await`, becoming a compilation error
  instead of silently corrupting data via `Array<Promise<T>>`). `cancelled()`
  always `0` (known limitation — no thread-local for "current task" context in
  interleaved async functions). See `docs/language-reference/concurrency.md`
  section 4.
- **kof.ui**: widgets, layout, style, events — rendering in native webview
  (WebKitGTK) and browser (static `index.html`); **Phase 7 Router**
  (`go/replace/back/forward/param/current/depth` — 31/08)

**In progress / future:**
- Declarative UI (components) and advanced layout
- Cross-platform embedded WebKit (Windows WebView2 / macOS WKWebView)
- Real `cancelled()` in JS (would need per-task context, with no native
  equivalent in interleaved async functions in the embedded GraalJS)
- Interoperability (`js.import(...)` — future syntax)
- Accurate source maps (positions in the IR) — JS debugging (Phase 6)

## Debugging

Compilation errors point to the `.kf` file (line/column), never to the
generated `.mjs`. Execution errors appear as engine messages with the name of
the corresponding JS function; the source map will allow mapping to the `.kf`
in the future.
