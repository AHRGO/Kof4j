[English](registry-live-roundtrip-2026-09-20.md) | [Português](registry-live-roundtrip-2026-09-20.pt_BR.md)

# Registry 1.5.3 — smoke real de round-trip no GitHub (20/09/2026) — **VERMELHO → VERDE após a #564**

> **ATUALIZAÇÃO — VERDE após o fix da #564** (ver "Re-run após o fix da #564" abaixo): publish, pull por
> versão, pin do `latest`, 2º resolve idempotente e erro honesto passam contra o GitHub real.
> Um passo segue aberto e **não** é defeito do Registry: código KOF não consegue `import` o pacote
> baixado (#566, pergunta de contrato para a mantenedora). O texto abaixo é o registro RED original.
>
> **Resultado da 1ª rodada: VERMELHO.** O publish funciona contra o GitHub real. O pull **não**: dois bugs
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

> **Atualização 20/09 — #565 CORRIGIDA** (entregue em `d1a12dd9`): self-inclusion removida de `kof build --fat` e `kof deploy --target jvm` (jar em staging fora do `classesDir` + exclusão exata do path final + substituição só após fechar; um rebuild que falha mantém o jar anterior e não deixa `.kof-app-*`). Travada por `CmdBuildFatTest` (build 1, rebuild no mesmo `classesDir`, rebuild que falha) e pela inspeção estrutural do jar distribuído no `CmdDeployTest`. (Naquele momento o smoke seguia VERMELHO por causa da #564 — superado pelo re-run abaixo.)

Ambos triados KOF-first (D-KOF-FIRST): bugs de tooling, sem sintaxe KOF; contrato = `DECISIONS.md` D2-A;
busca de duplicatas (abertas+fechadas) não achou nenhuma.

## Observações (sem issue)

- O jar construído da árvore mostra `compiler: unknown` no `RELEASE.md` porque o build local não tem `dev/kof/version.properties`; o jar oficial imprime `kof 0.4.9-beta`. Artefato de ambiente, não bug.
- O `kof deploy` empacota só classes alcançáveis a partir do `main`. Um pacote só-biblioteca não leva nada se o entry point não o usa — questão de contrato para publicar *bibliotecas* (regra 6: da mantenedora), não um defeito alegado aqui.
- Meu próprio script chamou `kof run .` (COMP001: exige um arquivo `.kf`); foi mau uso do harness, não bug, e por isso o passo `run` de C/D/E não é evidência.

## Re-run após o fix da #564 — **VERDE** (20/09/2026)

Mesma release pública, mesmas condições limpas: jar construído da árvore (`kof-cli-0.5.0-beta.jar`, verificado como contendo o fix), `-Duser.home` novo por fase, diretório de consumidor novo, `GH_TOKEN`/`GITHUB_TOKEN` **removidos**, `api.github.com` real. Nada sensível é registrado (caminhos como `$HOME`).

| Fase | Resultado (medido) |
|---|---|
| C — por versão explícita | **VERDE** — `resolve` exit 0; jar instalado em `$HOME/.kof/deps/kof/<owner>/<repo>/<ver>/<repo>-<ver>.jar`; sha256 do jar instalado == sha256 do jar publicado (`6099e28f…`); o `SHA256SUMS` do próprio pacote publicado confere (`OK`) |
| E — idempotência | **VERDE** — 2º `resolve` exit 0, **sem download**; mtime/tamanho do jar, `kofdeps` e estado do lock byte-idênticos |
| D — `latest` (sem `@version`), HOME e workspace separados | **VERDE** — `kofdeps` `owner/repo` vira `owner/repo@0.1.0-smoke.5d8a2b98`, jar instalado |
| F — referência inexistente | **VERDE** — `REG001` (exit 1), 0 arquivos no cache |
| rodar o pacote instalado | `java -cp <jar> Default.Main` → `hello, producer` |

Prova no código: o `DepsRegistryTest` agora serve o **shape real do GitHub** (`uploader{…}` aninhado com `url` próprio antes de `browser_download_url`, sem `download_url`, `author{…}` antes de `tag_name`, delimitadores e aspas escapadas dentro de string, ordem de campos invertida) e afirma o contrato HTTP (asset baixado pelo `url` da API do asset com `Accept: application/octet-stream`; `User-Agent: kof-cli`; `X-GitHub-Api-Version: 2022-11-28`; um 302 para outro host é seguido e o token `Authorization` **não** é enviado a ele). Antes do fix 11 dos 13 testes falhavam com `REG002: … has no .tar.gz asset`; depois: 13/13 (+ `DepsTest` 4, `DepsTransitiveTest` 10).

Decisão do fix (KOF-first): o JSON da release é lido com o `Json.parse` estrutural do próprio CLI (sem dependência nova, diferente do `jackson-core` proposto no plano); a exigência de `SHA256SUMS` e a seleção exato → `-jvm` → primeiro `.tar.gz` não mudaram.

## Observações do re-run

- O `kofdeps.lock` não é gravado para deps do registry: ele é o fecho transitivo Maven (roadmap 1.5.2). Deps do registry são pinadas **no próprio `kofdeps`** (`latest` → versão concreta) — comportamento existente, afirmado por `latestResolvesAndPinsConcreteVersion`.
- Um programa KOF não consegue `import` o pacote baixado: `PKG006` mesmo com `--classpath` (o gate de import procura módulo-fonte). É uma pergunta de contrato, registrada como #566 — fora do defeito de pull/publish do Registry.
