# DECISIONS — registro de decisões da mantenedora + planos ratificados

**Última atualização:** 13/09/2026 · **Quem decide:** Mel Santos (mantenedora)
**Como decidir este tipo de item:** a mantenedora responde no chat ("pode
seguir com a recomendada", ou escolhe outra opção); o agente trava a resposta
aqui com data + opção escolhida — **a decisão não mora no chat, mora aqui**.

> Este documento **substitui** os 6 arquivos que viviam em
> `docs/development/decision-pending/` (decisão da mantenedora 13/09:
> "transformar eles num doc só pra development"). As decisões foram
> inventariadas, auditadas contra o código (nunca memória) e **ratificadas
> 13/09** com a recomendação técnica aceita. O que cada arquivo virou:
>
> | Arquivo apagado | Virou |
> |---|---|
> | `planning-stdlib-time-design.md` | §D-STDLIB — execução na fila STDLIB |
> | `security-plan.md` | §D-SEC — camadas B/C/D viraram fila; arquitetura invariante em `docs/stdlib/security.md` |
> | `plan-spring-independence.md` | §D-SPRING — fases 1–9 ✅ (histórico), 10–12 ratificadas aqui |
> | `plan-platform-completion.md` | §D-PLAT — P0–P2 ✅ históricos; a fila real vive em `roadmap.md` §23 |
> | `APPLICATION_MODEL.md` | §D-APP (Q1–Q10 travados) |
> | `PLATFORM-PLAN.md` | §D-PLATFORM — **morto**: F1 absorvido pelo manifesto (já implementado, `KofProjectConfig`); F2–F9 já vivem em `roadmap.md` §23 |
>
> Invariantes que nada aqui altera: congelamento 0.2.6-beta (operadores, `==`,
> `spawn`, coleções), R1–R12 da visão universal, regra ≤500, suíte como gate.

---

## D-STDLIB — semântica de tempo/calendário (ratificado 13/09)

**D1 (fuso) — UTC-only.** `today()`/`isToday` derivam de `now()` em UTC em
TODOS os 5 alvos. Quem quiser fuso usa getter explícito `tzOffsetSeconds()`
(JVM: host; JS: `Date.getTimezoneOffset`; Native: gap DIAG `TIME003` até
haver `TZ`/`/etc/localtime` no asm). Sem paridade acidental de fuso — a
divergência silenciosa cross-target está proibida por construção.

**D2 (compostos) — sem retorno composto.** A trava DD-STDLIB-01 já fechou
13/09, mas **não** é para reabri-la: o calendário entra **escalar puro sobre
ISO** — a convenção `addDays("YYYY-MM-DD", n) -> String` já é a forma viva
desde S7a (auditado em `KofTime.java:133`). Nada de `startOf` retornando
tupla.

**D3 (`hoursBetween`) — completo, floor simétrico.** Consistente com
`daysBetween` (truncado em direção a zero): conta dias-inteiros-completos +
delta de horas. Sem float (FLT001), sem assinatura de 12 args.

**D4 (formato) — zero pattern-DSL.** A stdlib expõe `formatDateIso(y,m,d) ->
STR` (invalidez ⇒ `""`, face leniente documentada) e `parseDateIso(STR) ->
Int` (serial `daysFromEpoch`; inválido ⇒ 0). Patterns `dd/MM/yyyy` **não
entram na stdlib base** (R1/R12 — motor de parsing é pacote, não base).

**D5 (`isToday`) — entra**, dependência de D1 resolvida:
`isToday(y,m,d) -> Bool` = igualdade com a data UTC de `now()`.

### Fila STDLIB liberada (uma unidade-teste-commit cada)

| Item | Assinatura | Alvos |
|---|---|---|
| `time.todayIso()` | `() -> STR` ("YYYY-MM-DD" UTC) | 5 |
| `time.formatDateIso(y,m,d)` | `(I,I,I) -> STR` (inválido ⇒ `""`) | 5 |
| `time.isToday(y,m,d)` | `(I,I,I) -> Bool` (UTC) | 5 |
| `time.hoursBetween(y,m,d,H, y,m,d,H)` | `(I×8) -> Int`, floor simétrico | 5 |
| `time.parseDateIso(STR)` | `(STR) -> Int` (serial; inválido ⇒ 0) | 5 |
| `time.tzOffsetSeconds()` | `() -> Int` (Native = `TIME003` DIAG) | JVM/JS/SCRIPT; Native gap |

Cada linha: golden do oracle JVM (medição real), dispatch em `KofTime`,
`training/idioms`, matriz de conformidade, gap honesto onde houver.

> **✅ EXECUTADO 13/09 (lane development, dono 192.168.100.18):** as 6
> linhas acima implementadas e validadas — todayIso/formatDateIso/isToday
> (S7e), hoursBetween (S7f + fix emit x86 7+ args), parseDateIso (S7g),
> tzOffsetSeconds (S7h, Native gap honesto TIME003 — fila geral). Prova:
> `KofTimeE2ETest` S7e-S7h (30/30) + matriz `stdtime3`/`stdtime4`/
> `stdtime5`/`stdtime6` + parity Script. Suíte 1772/0/0. **Fila
> D-STDLIB TIME FECHADA.**

---

## D-SEC — segurança (ratificado 13/09)

**Arquitetura invariante (não muda — já documentada em
`docs/stdlib/security.md`):** 18 camadas; cripto nunca caseira (JCA/WebCrypto/
asm auditado); `SECN00x` para gap por target; estado atual verificado no
código 13/09: camada A e B ✅ (password/sha512/AES-GCM/JWT nos 3+alvos com
`SECN001/2/3/4` honestos), C10/12/13/14/15 ✅, D17 parcial ✅.

**ChaCha20-Poly1305 — entra (RFC 8439), espelhando o envelope AES-GCM real**
(auditado em `JvmStringSecurityRuntime.java:152-154` —
`aesgcm$<ivB64>$<ct+tagB64>`, `(plaintext, keyHex)` com chave 32B em hex):

```
chacha20$<nonceB64(12B)>$<ct+tagB64(16B tag)>
security.chacha20Encrypt(String text, String keyHex) -> String
security.chacha20Decrypt(String token, String keyHex) -> String
```

Mesmo padrão `SecCall` de `kof_sec_aesgcm_*` (gap SECN002 nos alvos sem
implementação). JS via WebCrypto `ChaCha20-Poly1305` (onde existir; restante
= SECN002 honesto). Constante de tempo, nonce nunca reusado (documentado).

> **✅ EXECUTADO (14/09, degrau 2 — dono 192.168.100.18):** chacha20 JVM+JS
> (`kof_sec_chacha20_encrypt/decrypt`, SecCall idêntico ao aesgcm). JS é
> implementação pura (WebCrypto **não** expõe ChaCha20 em nenhum engine
> principal — a premissa "onde existir" caiu; prova: MDN
> SubtleCrypto.algorithms). Validado byte a byte contra node:crypto e contra
> o vetor RFC 8439 §2.8.2 (Poly1305 AEAD: r/s LE, mac_data
> pad16(ct)||le64(0)||le64(ctLen)). **Native (x86/riscv/aarch64) segue gap
> SECN002 honesto em compile-time** — asm puro de Poly1305 (aritmética
> 130-bit) fica na fila, mesmo precedente do SECN000. Constante de tempo:
> tag comparada com `MessageDigest.isEqual` (JVM) / XOR acumulado (JS).
> Testes: KofSecurityTest 32/32 (`chacha20*`), suíte 4 módulos 0 falhas.

**Cookies (C11) + middleware de security (C18) — entram, EXECUTAM JUNTO com
o Application Model** (a ordem só faz sentido com `app.use`):

- `security.cookies`: parse/set com defaults seguros (`HttpOnly`, `Secure`,
  `SameSite=Lax`, `Path=/`); API `cookieSet(name, value, opts-map)` e
  `cookieGet(request, name)`.
- `app.security()` — middleware composto aplicando a **ordem fixa**:
  rate-limit → cors → headers → cookies/session → csrf → auth → RBAC → rota.
  Security by default: `listen` em produção exige `app.security()` explícito
  ou warning.

**OAuth2/OIDC (D cam. 16) — sequência travada:** (1) **resource server**
primeiro (validação de JWT de terceiro: JWKS + issuer/aud — barato, fecha
"quem é usuário Google?"), (2) client authorization-code + PKCE depois;
**provider nunca** (non-goal, fora de qualquer plano).

**TLS com certificado próprio — entra:** `app.listenSecure(port, certPem,
keyPem)` (PKCS#8 PEM; JVM primeiro; Native/JS continuam `WEB002` honesto).
Self-signed atual permanece como conveniência de dev, não como produção.

---

## D-APP — Application Model (ratificado 13/09)

Os 10 open questions da RFC viram decisão:

| Q | Decisão |
|---|---|
| Q1 manifesto | **`kof.toml`** — ✅ já implementado (`KofProjectConfig` subconjunto INI; PKG006 usa como raiz de projeto). Decisão formalizada, sem código novo. |
| Q2 bump | **0.4.0-beta** (capability nova; a linha 0.4.0 já está em curso). |
| Q3 shared types | package local importado por front+backend, **depois** do package manager; não bloqueia nada. |
| Q4 `kof serve --system` | **rejeitado** (confirmado) — alternativa: `kof serve --list` (apps+portas, sem subir). |
| Q5 `[frontend].api` | **convenção documentada** (não feature de rewrite). |
| Q6 fat jar | **flag `--fat` opcional** (I3); default = classpath explícito. |
| Q7 Wasm | coluna ✅ frontend na tabela quando o target abrir; **modelo não muda**. |
| Q8 `kof.proxy` | fora desta RFC; convenção hoje, stdlib só com 3+ apps pedindo. |
| Q9 rebuild frontend | **sob demanda por hash** (I2); watcher = futuro. |
| Q10 Android | **declarar ✅** na tabela (WebView + KofJS já é full-stack de fato; sem código novo). |

**Plano I1–I3 da RFC:** I1 (manifesto+`kof new`) — **manifesto já existe**;
resta `CmdNew` (esqueletos backend/full-stack/frontend + validação `APP003`),
escopo pequeno. I2 (full-stack serve/build) e I3 (fat/deploy) seguem na fila
com os incrementos da RFC §23. A matriz APP001–003 vai para
`docs/backend-parity.md` (gap codes R6).

### D-APP.REF — o modelo em uma página (conteúdo de referência da RFC)

**Definição.** Uma Kof Application é um diretório contendo um módulo Kof
(conjunto de `.kf` com um `main()`) + manifesto opcional `kof.toml`
descrevendo componentes (frontend, static) e execução (porta/host, target).

```text
my-app/
├── kof.toml            # manifesto (OPCIONAL — sem ele, convenção atual 1:1)
├── kofdeps             # dependências (existe hoje)
├── src/main.kf         # entrypoint (main() único = backend)
│   └── web/main.kf     # componente FRONTEND (outro módulo → bundle js)
└── src/static/         # componente STATIC (css/img — copy puro)
```

Regras: (1) uma aplicação = um módulo = um `main()`; frontend é OUTRO módulo
compilado para `js`; (2) componentes = zero/um/mais (backend, frontend,
static); (3) menor unidade de `kof serve`/deploy (1 processo, 1 porta);
(4) System = composição de deploy, não de compilação; (5) **sem `kof.toml`
= comportamento exatamente atual**.

Manifesto (subconjunto INI já lido por `KofProjectConfig`): `[app]`
(name/version/entry/target), `[serve]` (port/host; flag sempre vence),
`[frontend]` (path/entry/out/base/api/cors), `[static]` (path). Lido pela
CLI, nunca pelo compilador; erro de manifesto = diagnóstico claro (R6).

**Full-stack:** backend monta o bundle via `app.serveDir` (existe no JVM) e
o frontend chama a API **por HTTP** (`kof.http` no js) — nunca chamada
direta. Contrato front→back é sempre HTTP/JSON ⇒ "mesmo processo" e
"serviço remoto" são intercambiáveis (base da portabilidade
monólito↔distribuído).

Topologias (o que muda é só o número de diretórios e quem orquestra —
linguagem/compilador/CLI não mudam): monolith (default de hoje), modular
monolith (organização de pacotes, nada do modelo), microservices (N apps
backend-only), microfrontends (N bundles + shell), full-stack (meta I2),
backend-only, frontend-only, full-stack distribuído (+ gateway = app que
roteia).

**Regras estruturais permanentes (ex-spring, D-SPRING):** nenhum componente
da stdlib depende de Spring; nenhum backend gera Java-source como passo
obrigatório; capacidades fundamentais têm API Kof-native (Spring é
alternativa consumida); o teste de independência (app Kof sem Spring) vale
tanto quanto o de interoperabilidade.

---

## D-SPRING — fases 10/11/12 (ratificado 13/09)

- **Fase 10 (testing nativo):** escopo travado = `kof test` roda os testes do
  projeto com **asserts Kof** (sem JUnit obrigatório; interoperável quando
  JVM); unit + HTTP (via `web.app` em porta efêmera) primeiro; property/
  stress/mocks são incrementos separados, nunca gate.
- **Fase 11 (CLI completa):** consolidar o que existe (`run/build/test/serve/
  fmt/deps/init/check` ✅ no `Main.java`) + `kof new` (I1 do D-APP); `kofdeps
  add/remove/list/resolve` já landed (Deps.java) — falta só o elo com o
  manifesto (deps no `kof.toml`).
- **Fase 12 (blog E2E):** **AGORA** — é a validação da plataforma, e é o app
  model canônico (backend + frontend + db + auth + validation num único
  `kof.toml`); roda em JVM e Native sem mudar uma linha (gaps diagnosticados
  contam como honestos, não como falha).

## D-PLAT — platform-completion (ratificado 13/09)

P0–P2 ✅ fechados (histórico 0.1.0→0.2.6-beta). P3–P5 não são fila própria:
**cada linha restante já tem casa** — DB/orm no roadmap §23 e
`docs/stdlib/DATABASE_VISION.md`, observabilidade (OTLP) em
`docs/backend-parity.md`, DX (`kof new`) no D-APP/I1. O documento morto.
O **Definition of Done** do plano ("compila nos 3 targets ou gap com
`supportedOn` + E2E por target + benchmark quando plausível + docs
sincronizadas no mesmo commit + suíte verde") não se perdeu: mora em
`docs/architecture/performance.md` §40–§41 (a fonte que o próprio plano
citava) e ecoa no portão Q0–Q6 do `AGENTS.md`.

## D-PLATFORM — PLATFORM-PLAN (morto 13/09)

F1 (raiz de projeto) **resolvido pelo manifesto** (`KofProjectConfig` +
PKG006); F2 targets = `docs/targets` + §23; F3 full-stack = D-APP I2; F4/F5 =
`KOFUI-AUDIT`/`docs/stdlib/stdlib-web.md`; F6 wasm/F7 android = tabela do
D-APP Q7/Q10; F8 script = `kof-cli CmdScript` ✅; F9 conformance =
`docs/bugs-and-gaps/conformance-matrix.md`. **Nada restava de único — o arquivo foi absorvido, não descartado.**

## D-RELEASE — gatilho de patch (0.4.1) por volume de fixes (14/09)

**Regra da mantenedora (14/09, após fechar a minor 0.4.0):** desenvolvimento
agora é **estabilização de patch**, não feature. O critério objetivo de subir
um patch:

- **Gatilho:** quando a `beta` estiver **entre 100 e 150 commits à frente da
  `main`**, avaliar o bump para **`0.4.1`** (patch — só fixes, zero capability
  nova; a linha `0.4.0` já foi liberada no #138).
- **Features NÃO param nem são descartadas na janela (adição da mantenedora
  14/09):** entre 0.4.0 e o gatilho, **evolução real (features/melhorias)
  concorre com os bugfixes** — o contador de 100–150 mistura os dois. A janela
  de patch **não congela desenvolvimento** e o bump **não pode descartar /
  reverter / segurar em branch** o trabalho de feature feito no período: tudo
  que está na `beta` com suíte verde e prova entra no pacote. Se o volume de
  **capability nova** acumulado na janela for material (mudou contrato/operador
  — regra 6, ou superfície de API visível), o bump avaliado deixa de ser
  patch: vira **0.5.0-minor** (semver decide pelo conteúdo, não pelo calendário
  nem pelo gatilho). A nota de release lista fixes E features.
- **Média de pacote estável:** 100–150 commits de fix acumulados = um pacote
  estável o bastante para valer um release. Abaixo disso, é ruído; acima, o
  backlog de correções já justifica o número de versão.
- **Antes de bumpar:** **corrigir as issues abertas** (`gh issue list --state
  open`) que forem da lane de bugs/paridade — o patch sai com as issues
  conhecidas fechadas, não em cima delas. Issues de regra 6 (contrato) ficam
  abertas com nota, não bloqueiam o patch.
- **Depois do bump:** voltar ao desenvolvimento normal (a `beta` reabre para a
  próxima minor/feature; o contador reinicia contra a nova `main`).

**Estado do contador (medição 14/09, post-#138):** `main..beta = 1` (só
`a5eedbe2`), `beta..main = 1` (o merge do PR). **Longe do gatilho** — o loop
segue acumulando fixes na beta; nenhum agente bumpa versão enquanto não
chegar perto de 100. Quem medir deve anotar aqui a contagem e a data.

**Como medir:** `git rev-list --count origin/main..origin/beta-0.4.0`. Ao
cruzar a faixa, abrir a issue "Release 0.4.1" (regra: toda PR vem com issue),
rodar a suíte completa verde, fechar as issues da lane bugs, e só então bumpar
`pom.xml` + `version.properties`.

---

## D-ASM-GATE — gate de asm riscv/aarch OPCIONAL até o dev nativo fechar (14/09)

**Decisão da mantenedora (14/09):** *"deixa o teste do riscv e arm opcional
até o desenvolvimento estar completo"* — e o chão inegociável que a acompanha:
*"não pode ter teste quebrado na main nem na beta"*.

- **Contexto (causa raiz medida):** os testes
  `*CastSaturationLabelsAreUniquePerEmission` (trava de regressão do §181,
  `67db6c50`) assertavam "o backend sempre mantém o `.s`". **Falso:** em host
  COM toolchain, `as`+`ld` linkam com sucesso e o `NativeArchEmitter` APAGA o
  `.s` (só mantém com `KOF_KEEP_ASM`). Resultado: verde no host de dev (sem
  toolchain, ramo `ToolchainMissing` preserva o asm), **vermelho no CI**
  (toolchain presente) — gate da beta quebrado desde `67db6c50`/`fdf0dd92`.
- **Opção escolhida:** skip **honesto e explícito** (regra Q5, nunca "passa por
  acidente"): `Assumptions.assumeTrue(KOF_ASM_GATE)` no início dos dois testes.
  Padrão = skip (CI verde); reativa com `KOF_ASM_GATE=1` quando o gate cross
  for exigido de novo. Corpo tornado **portável** (if `.s` existe → inspeção de
  texto; senão → exige o binário linkado, prova mecânica).
- **A regressão do §181 continua provada nos 42 E2Es riscv + 42 aarch sob qemu:**
  label `.Lsat181` duplicada = `as` falha = `success()` false = teste E2E
  vermelho. O gate de texto é redundante com qemu; só acrescenta em host SEM
  toolchain — por isso pode ser opcional sem perder proteção real.
- **Evidência:** `NativeRiscv64E2ETest.java`/`NativeAarch64E2ETest.java`
  (`KOF_ASM_GATE`); prova local dupla — sem flag: `Skipped: 2`; com
  `KOF_ASM_GATE=1`: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- **Condição de reativação:** quando o desenvolvimento nativo estiver completo
  (bugs da lane Native — §184/§187/§181-adjacentes — fechados com matriz
  5/5), o `assumeTrue` é removido e o gate volta a ser obrigatório no CI.

---

## Como atualizar este doc

Decidiu mais alguma coisa no chat → trava aqui (data + opção + evidência de
código quando houver). Item decidido que vira código: sai daqui para a fila
do `roadmap.md` §23/DOING; a linha fica marcando `✅ decidido <data>` (o
registro é permanente — a pasta `decision-pending/` não existe mais).
