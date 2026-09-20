[English](registry-live-roundtrip-2026-09-20.md) | [Português](registry-live-roundtrip-2026-09-20.pt_BR.md)

# Registry 1.5.3 — smoke real de round-trip no GitHub (20/09/2026) — **VERMELHO**

> **Resultado: VERMELHO.** O publish funciona contra o GitHub real. O pull **não**: dois bugs
> reais achados, ambos abertos (#564, #565). A nota do tracker `round-trip live no GitHub =
> smoke manual pendente` (`IMPLEMENTATION-UNIVERSAL-PLATFORM`, item 1.5.3) **continua pendente**.
> Este smoke não alterou código de produção. Abaixo só dados sanitizados: nenhum token foi
> impresso, logado ou commitado (registra-se a presença, nunca o valor).

## Preparo (medido)

| Item | Valor |
|---|---|
| Tip sob teste | `5d8a2b98` (`beta-0.5.0`), jar construído da árvore (`kof-cli-0.4.7-beta.jar`) |
| CLI oficial para contraprova | `kof 0.4.9-beta` (jar released) |
| Host | WSL Ubuntu-24.04, JDK 25 |
| Repo do smoke | público `jonasrochasilva-prog/kof-registry-smoke` (só README; **não** o `KofLang/Kof4j`) |
| Release / tag publicada | `kof-registry-smoke-0.1.0-smoke.5d8a2b98` |
| Asset | `kof-registry-smoke-0.1.0-smoke.5d8a2b98.tar.gz`, 1920 bytes (jar + `RELEASE.md` + `SHA256SUMS`, tar uid/gid 0) |
| Isolamento do cache | `-Duser.home=<dir novo>` por fase; diretório do consumidor novo; `GH_TOKEN`/`GITHUB_TOKEN` **removidos** em toda fase de pull |
| Trato do token | só no produtor, por variável de ambiente (nunca em linha de comando); saída redigida; **varredura pós-publish do asset enviado** sem token, caminho de usuário ou nome de host |

A release e a tag ficam como evidência (sem limpeza automática).

## Fases

| Fase | O quê | Resultado |
|---|---|---|
| A | empacotar de árvore limpa (`kof deploy src --target jvm ...`), varredura pré-publish do artefato | OK — nenhum dado sensível |
| B | `kof deploy --publish` no repo do smoke → Release real + asset `.tar.gz` | **OK** — release e asset visíveis, asset baixável **sem token** (HTTP 200), `SHA256SUMS` interno confere |
| C | consumidor, HOME/cache novos, `kof deps add owner/repo@<ver>` + `kof deps resolve` | **VERMELHO** — `REG002: release <tag> has no .tar.gz asset` (exit 1); sem lock, sem cache gravado |
| D | idem com `latest` (sem `@version`) | **VERMELHO** — mesmo `REG002` (exit 1) |
| E | idempotência (2º `resolve`) | **BLOQUEADA** pela C (nada instalado para comparar) |
| F | referência inexistente `@9.9.9-inexistente` | **OK** — `REG001: release not found on registry ...`, exit 1, 0 arquivos no cache, release válida intacta |

C/D foram reproduzidas também com o jar **oficial** `kof 0.4.9-beta`, então não é artefato do build local.
A fase F prova que o caminho de falha é honesto (R6): só o parsing dos assets de uma release *existente* está quebrado.

## Achados

| # | Issue | Classe | Resumo |
|---|---|---|---|
| A | [#564](https://github.com/KofLang/Kof4j/issues/564) | BUG REAL | `DepsRegistry.pickTarball` lê `"download_url"` (a chave real é `"browser_download_url"`) e corta cada objeto de asset na primeira `}` (o asset real tem `"uploader": {…}` aninhado antes da URL). O pull nunca funciona no GitHub real; o `DepsRegistryTest` só passava contra um servidor falso mínimo. |
| B | [#565](https://github.com/KofLang/Kof4j/issues/565) | BUG REAL (baixo) | `CmdBuild.buildFatJar` grava `classesDir/kof-app.jar` dentro do diretório que percorre, então todo fat jar embute uma entrada `kof-app.jar` truncada e inválida (561 bytes). Não é falha de execução. |

Ambos triados KOF-first (D-KOF-FIRST): bugs de tooling, sem sintaxe KOF; contrato = `DECISIONS.md` D2-A;
busca de duplicatas (abertas+fechadas) não achou nenhuma.

## Observações (sem issue)

- O jar construído da árvore mostra `compiler: unknown` no `RELEASE.md` porque o build local não tem `dev/kof/version.properties`; o jar oficial imprime `kof 0.4.9-beta`. Artefato de ambiente, não bug.
- O `kof deploy` empacota só classes alcançáveis a partir do `main`. Um pacote só-biblioteca não leva nada se o entry point não o usa — questão de contrato para publicar *bibliotecas* (regra 6: da mantenedora), não um defeito alegado aqui.
- Meu próprio script chamou `kof run .` (COMP001: exige um arquivo `.kf`); foi mau uso do harness, não bug, e por isso o passo `run` de C/D/E não é evidência.

## Para chegar ao VERDE (depois de corrigir a #564 — não feito aqui)

Rodar de novo as fases C–E com o jar reconstruído, HOME novo, sem token: add por versão → resolve (sha256 verificado antes de instalar, jar em `~/.kof/deps/kof/...`, `kofdeps.lock` gravado) → programa consumidor importa o pacote e roda; `latest` pinna a versão no `kofdeps`; 2º `resolve` deixa cache e lock byte-idênticos. Só então virar a nota do tracker (EN+PT) para feito e linkar a evidência.
