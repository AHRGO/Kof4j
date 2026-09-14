[English](intellij.md) | [Português](intellij.pt_BR.md)

# IntelliJ IDEA

> **State of automatic integration: INSTALLS HONEST CONTENT (step 10,
> 09/13).** `kof editor install intellij` writes under `$HOME`: filetype XML
> (`Kof.xml`, `*.kf`/`*.kof`), External Tools (`Kof.xml`: `kof build/run/test/
> fmt/check/lsp` — delegate to the CLI, §15) and `README.txt` with the manual
> LSP4IJ step below. The official plugin remains a **separate subproject**
> (IntelliJ Platform / Gradle), tracked in issue **#1** and in the plan
> `docs/development/plan-editor-integration.md` (§21).

## What already works today (manual)

IntelliJ consumes Kof's official tooling without a plugin:

1. **TextMate grammar** — `Settings → Editor → TextMate Bundles → +` and
   select `editor/kof.tmLanguage.json` from the distribution. This gives
   highlighting for `*.kf`/`*.kof`.
2. **LSP** — install the **LSP4IJ** plugin (JetBrains Marketplace) and register
   the server:

   ```json
   // Settings → Languages & Frameworks → LSP → Server Mapping
   {
     "kof": {
       "command": ["kof", "lsp"],
       "languageId": "Kof",
       "extensions": ["kf", "kof"]
     }
   }
   ```

   Diagnostics, completion, hover, rename and references come from `kof lsp`.
3. **Run/Build** — configure External Tools pointing to `kof build`,
   `kof run`, `kof test`, `kof fmt`, `kof check`.

## The official plugin (when #1 closes)

Plugin priority (delegating to the CLI, never duplicating the compiler):
language support → file recognition → LSP → diagnostics → completion →
formatting → navigation → run/build integration.

## Troubleshooting

- LSP4IJ with no connection: test `kof lsp` in the terminal (it should wait on stdio).
- Grammar not applied: confirm the file extension (`.kf` or `.kof`).
