# IntelliJ IDEA

> **Estado da integração automática: INSTALA CONTEÚDO HONESTO (degrau 10,
> 13/09).** `kof editor install intellij` escreve sob `$HOME`: filetype XML
> (`Kof.xml`, `*.kf`/`*.kof`), External Tools (`Kof.xml`: `kof build/run/test/
> fmt/check/lsp` — delegam à CLI, §15) e `README.txt` com o passo LSP4IJ
> manual abaixo. O plugin oficial continua sendo um **subprojeto próprio**
> (IntelliJ Platform / Gradle), rastreado na issue **#1** e no plano
> `docs/development/plan-editor-integration.md` (§21).

## O que já funciona hoje (manual)

O IntelliJ consome o tooling oficial do Kof sem plugin:

1. **TextMate grammar** — `Settings → Editor → TextMate Bundles → +` e
   selecione `editor/kof.tmLanguage.json` da distribuição. Isso dá
   highlighting de `*.kf`/`*.kof`.
2. **LSP** — instale o plugin **LSP4IJ** (JetBrains Marketplace) e registre o
   servidor:

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

   Diagnostics, completion, hover, rename e references vêm do `kof lsp`.
3. **Run/Build** — configure External Tools apontando para `kof build`,
   `kof run`, `kof test`, `kof fmt`, `kof check`.

## O plugin oficial (quando #1 fechar)

Prioridade do plugin (delegando à CLI, nunca duplicando o compilador):
language support → file recognition → LSP → diagnostics → completion →
formatting → navigation → run/build integration.

## Troubleshooting

- LSP4IJ sem conexão: teste `kof lsp` no terminal (deve aguardar em stdio).
- Grammar não aplicada: confirme a extensão do arquivo (`.kf` ou `.kof`).
