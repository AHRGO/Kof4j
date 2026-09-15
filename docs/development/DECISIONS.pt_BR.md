[English](DECISIONS.md) | [Português](DECISIONS.pt_BR.md)

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

> **✅ EXECUTADO (14/09, degrau 3 parcial — dono 192.168.100.18):**
> `security.cookieSet(name,value[,opts])` e `security.cookieGet(header,name)`
> implementados em **JVM + JS** (`kof_sec_cookie_set/set_opts/get`), com
> defaults seguros (`Path=/; SameSite=Lax; Secure; HttpOnly`) e opts-map
> (`path/domain/maxAge/expires/sameSite/secure/httpOnly`). **Native segue gap
> honesto SECN006** (mesmo precedente SECN000/002). Testes `KofSecurityTest`
> 39/39 (cookieSetDefaults/opts/get Jvm+Js, roundtrip JVM→JS, SECN006 cross).
> **✅ EXECUTADO (14/09, degrau 4 — dono 192.168.100.22):**
> `app.security([opts])` (C18) implementado no runtime JVM (`WebApp` fields +
> `kof_web_security` + `kof_web_security_pipeline` em `JvmRuntimeWebDispatch`).
> Ordem fixa ratificada: rate-limit → cors → security headers (CSP/HSTS/nosniff/frame/referrer)
> → session (authHeader + publicPaths) → csrf. Prova: `KofWebE2ETest#appSecurityPipelineE2E`
> + validação E2E completa no `KofBlogE2ETest`.

> **✅ MESCLADO (14/09, dono 192.168.100.18): as duas implementações de C18 foram
> unificadas como superconjunto** (pacto de agregação — nenhum lado descartado).
> A API da `.22` (`rateLimit` Number, `corsOrigin`, `sessionHeader`,
> `publicPaths`) e a da `.18` (`headers`, `cors`, `rateLimit` String, `csrf`,
> `auth`, `roles`) agora vivem num único `kof_web_security`/`kof_web_security_opts`
> + um pipeline (rate-limit → cors → headers → session → csrf → auth → RBAC)
> gravando headers de resposta em `KOF_SEC_RESPONSE_HEADERS`. Sessão é exigida em
> mutações (leituras públicas; header de sessão inválido nunca passa);
> `auth`/`roles` exigem Bearer JWT válido. Prova: `KofWebE2ETest` 22/22 +
> `KofBlogE2ETest` 1/1 + `KofOAuthResourceServerTest` 4/4 + `KofSecurityTest`
> 41/41 = 68/0/0.

> **✅ EXECUTADO (14/09, degrau 3 completo — dono 192.168.100.18):**
> `app.security()` (C18) implementado em **JVM** (`kof_web_security` /
> `kof_web_security_opts`, `SecurityMiddleware` registrado em `app.middlewares`).
> Aplica a **ordem fixa** rate-limit → CORS → headers → cookies/session → csrf →
> auth → RBAC → rota. Sem args = defaults seguros (headers de hardening:
> CSP/nosniff/frame/referrer; HSTS só sob TLS). Opts-map (tudo documentado em
> `docs/stdlib/stdlib-web.md`): `headers` (Bool), `cors` (String origem/CSV/`*`,
> origem não listada → 403, preflight → 204), `rateLimit`
> (`"limite/janelaSeg"` por IP remoto → 429 + `Retry-After`), `csrf`
> (double-submit cookie), `auth` (exige Bearer JWT válido), `roles` (String CSV
> ou List). **Auth-if-present:** request com token inválido nunca passa, mesmo
> sem `auth:true`. **Security by default:** `listen`/`listenSecure` com
> `KOF_ENV=production` sem `app.security()` avisa em `stderr`. **Native/JS
> reportam `WEB006`** honesto (mesmo precedente WEB002/WEB005). Headers de
> resposta do middleware sobrevivem ao clear do dispatch via
> `KOF_SEC_RESPONSE_HEADERS`. Refactor: novo fragmento
> `JvmWebSecurityRuntime.java` mantém o ratchet §140 verde (`JvmWebCoreRuntime`
> 699→495). Testes: `KofWebE2ETest` 22/22 (headers, auth 401/200,
> auth-if-present, roles 403, CORS deny/preflight, CSRF, rate-limit 429, WEB006
> Native+JS). Também corrigido bug de descriptor pré-existente:
> `kof_sec_auth_user` estava declarado `(Ljava/lang/String;)` mas não recebe
> args.

**OAuth2/OIDC (D cam. 16) — sequência travada:** (1) **resource server**
primeiro (validação de JWT de terceiro: JWKS + issuer/aud — barato, fecha
"quem é usuário Google?"), (2) client authorization-code + PKCE depois;
**provider nunca** (non-goal, fora de qualquer plano).

> **✅ EXECUTADO (14/09, dono 192.168.100.18):** passo 1 — **resource server
> OAuth2** no JVM. `auth.resourceServer(jwksUrl, issuer, audience)` configura a
> validação de JWT de terceiro (busca as chaves públicas na URL do JWKS;
> issuer/audience vazio = não exige) e `auth.resourceServerVerify(token)`
> devolve o JSON de claims ou `null`. Allowlist fixa **RS256/384/512 +
> ES256/384/512** — nunca `none` nem HS* (confusão de algoritmo rejeitada);
> chaves JWK RSA (`n`/`e`) e EC (`crv` P-256/384/521, `x`/`y`); `exp` + `iss` +
> `aud` (String ou lista). Em `kid` desconhecido, re-busca o JWKS uma vez
> (rotação de chave). O resource server pluga em
> `auth.authenticated()`/`app.security({auth:true})`: configurado, um token que
> falha no HS256 cai para a validação via JWKS. JWKS é cacheado em memória.
> **Native/JS reportam `SECN007`** honesto. Testes: `KofOAuthResourceServerTest`
> 4/4 (token RS256 real + JWKS local via `com.sun.net.httpserver`; rejeição de
> iss/aud; alg=none/tamper; integração com `app.security` 401/200; SECN007
> Native+JS). API documentada em `docs/stdlib/security.md`.

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
| Q6 fat jar | **flag `--fat` opcional** (I3); default = classpath explícito. ✅ **EXECUTADO 14/09**: `kof build --fat` (JVM) gera `kof-app.jar` (classes do app + runtime `dev.kof.runtime` + deps externas, `Main-Class` no manifesto, first-wins do app, assinaturas deps descartadas); prova `CmdBuildFatTest` 4/4 (`java -jar` roda o programa; sem a flag não há jar; `--fat` fora do JVM recusa honesto R6). |
| Q7 Wasm | coluna ✅ frontend na tabela quando o target abrir; **modelo não muda**. |
| Q8 `kof.proxy` | fora desta RFC; convenção hoje, stdlib só com 3+ apps pedindo. |
| Q9 rebuild frontend | **sob demanda por hash** (I2); watcher = futuro. |
| Q10 Android | **declarar ✅** na tabela (WebView + KofJS já é full-stack de fato; sem código novo). |

**Plano I1–I3 da RFC:** I1 (manifesto+`kof new`) — **manifesto já existe**;
resta `CmdNew` (esqueletos backend/full-stack/frontend + validação `APP003`),
escopo pequeno. I2 (full-stack serve/build) ✅ e I3 (fat/deploy) ✅ (`--fat`,
14/09) executados com os incrementos da RFC §23. A matriz APP001–003 vai para
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

## D-ENGINEERING — não reinventar a roda (14/09, princípio da mantenedora)

**Regra da mantenedora (14/09):** *"sobre a lógica interna do compilador, sempre
se inspirar na forma que **Java e C** resolvem os problemas, desde que o
frontend e a forma de escrever continuem idiomáticos e a saída continue
determinística; pode se inspirar na forma como outras linguagens resolvem o
backend. Não queremos reinventar a roda."*

- **Escopo:** lógica interna do compilador/backend (lowering, helpers de
  runtime, codegen, semântica). **Não** é o frontend Kof: sintaxe e a forma de
  escrever continuam Kof idiomático (regra 6 — superfície congelada não muda
  com isto).
- **Ordem de referência:** (1) **Java** (JLS/JVMS + comportamento de
  `java.lang`/`java.math` — âncora mais forte, pois o backend JVM já mira nele),
  (2) **C** (ISO C / libm para o backend nativo), (3) outras linguagens só para
  *técnica de backend* (nunca para a superfície Kof).
- **Invariantes mantidos:** saída determinística (mesma entrada → mesmos
  bytes/resultado em todo alvo) e a regra do gap honesto (R6: nunca resposta
  errada silenciosa; face não implementada reporta código, não adivinha).
- **Primeiras aplicações (mesmo dia):** §D-BACKEND-SEMANTICS abaixo.

---

## D-BACKEND-SEMANTICS — 6 decisões do chat de 14/09 (dono 192.168.100.18)

A mantenedora respondeu a lista aberta. Opções escolhidas e a execução:

### 1. §101 — operadores relacionais com NaN → **opção A (IEEE 754 puro)**
Todos os alvos concordam com IEEE 754: **toda comparação relacional com NaN é
`false`** (e `!=` é `true`). O comportamento riscv/aarch é a referência (já é
IEEE); **JVM/x86/JS são alinhados a ele**. Concretamente: o lowering JVM não
pode depender do quirk `dcmpl`/`dcmpg` que devolve `true` para `1.0 < NaN` /
`1.0 <= NaN`; o resultado é computado IEEE-correto (resultado unordered força
`false` para `<`, `<=`, `>`, `>=` e `true` para `!=`). JS segue IEEE por
construção (`<` com NaN é `false`). É o próprio contrato do Java (JLS 15.20.1:
comparações com NaN são todas `false`), então o JVM era o outlier, não a
referência.

**Feito (14/09, dono 192.168.100.18):** o JVM usa `FCMPG`/`DCMPG` para `<`/`<=`
e `FCMPL`/`DCMPL` para `>`/`>=` (`JvmOpEmitter` via
`JvmLiteralEmitter.floatCmpIsG`/`condCmpIsG`); o Native x86 foi corrigido em
`NativeX86Arith` (caminho de valor) e `NativeOpHelpers` (caminho de salto) — o
`setb`/`jb` do `LT` não tinha o guard de unordered que `LE`/`GE` já tinham.
riscv/aarch já eram IEEE. Prova: `BackendParityTest.parityNanRelationalIeee`
(JVM×JS) e `ComponentCoreE2ETest.nanRelationalIsIeeeOnAllTargets`
(JVM+Native+JS, caminhos de valor e de salto, Double e Float).

### 2. §129 — unwind cross-thread no Native → **opção B (frame por thread)**
Dar ao unwinder do Native um **frame de exceção por thread** (não um
`kof_exc_chain` global, não uma chain compartilhada por TID): cada thread é dona
da sua cadeia de handlers/frames, então um `throw` sem handler dentro de um
worker `spawn` marca o handle do worker como excepcionalmente-completo em vez de
`longjmp` para fora da thread. Inspirado no Java (estado de exceção por thread)
e no C (frames `setjmp`/`longjmp` são locais à pilha). Afeta o runtime
compartilhado (`RuntimeDb4`/GC); a chain vira thread-scoped. Destrava o
**OTP S2-Native** (§129), cujo gate é `OTP001` até isto fechar.

### 3. `roundTo` — **implementar, racional (inspirado em Java + C)**
Aprovado para implementação (a ratificação do `pow` de 13/09 o deixou aberto).
Design racional, inspirado em Java+C:
- **Família `round()` do C** = arredonda-meio-para-longe-do-zero
  (`round(2.5)=3`, `round(-2.5)=-3`) — a âncora para o *modo de arredondamento*.
- **`BigDecimal.setScale(n, RoundingMode.HALF_UP)` do Java** = a âncora para a
  *escala decimal* (arredondar `Double`/`Float` para N casas decimais).
- **Superfície (Kof idiomático):** `math.roundTo(value, decimals)` devolve o
  mesmo tipo numérico de `value`, `decimals` um `Int` (0 = arredonda inteiro).
  Determinístico: escala decimal pura, sem locale, sem pattern DSL (mesmo
  precedente de `time.format`, §D-STDLIB). Célula golden cross-target.

**Feito (14/09, dono 192.168.100.18):** S1b.3 — `kof_math_roundTo(Double,Int)`
nos 5 alvos. Contrato **aritmético** (não decimal-string): `p=10^|d|` por
multiplicação REPETIDA (cada passo é 1 op IEEE corretamente arredondada →
byte-idêntico); `d>=0`: `roundHalfAway(v*p)/p`, `d<0`: `roundHalfAway(v/p)*p`
(decimals negativo arredonda p/ dezenas/centenas); `|d|` satura em 308; overflow
de `v*p` → devolve `v` (no-op). `roundHalfAway` = trunc + correção do resto
(`|f|>=0.5` → ±1; evita o double-rounding do `floor(x+0.5)`). Consequência
travada: `roundTo(2.675,2)==2.68` (o double `2.675*100` arredonda a `267.5`).
Sem libm. Backends: JVM (`JvmStringMathRuntime`), SCRIPT (reflexão), JS
(`kofMathRoundTo`), x86 (`RuntimeMath`), riscv (fatia B32, aarch via tradutor).
Prova: `KofMathTest.roundTo{Jvm,Native,Js,CrossArch}` + guard SEM025 +
`ConformanceMatrixTest.stdmathround` (4 targets) + `KofScriptStdlibParityTest.
mathRoundToParity`.

### 4. §179 — tipo `kof.ui`/`kof.media` declarado → **opção A (mapear o builtin)**
`MemberResolver.resolveType`, após `qualifyDeep`, mapeia `ClassType("", name)`
para `KofUi.constructorType(name)`/`KofMedia` quando `name` é builtin UI/media
**e** não foi resolvido por import/classe do módulo — **shadowing do usuário é
preservado** (classe de usuário chamada `Label` ainda vence). Corrige o
`VerifyError` do JVM (descritor `LLabel;` vs handle `int`) para
var/param/campo/retorno declarado de tipos UI/media.

**Feito (14/09, dono 192.168.100.18):** o mapeamento vive em
`CompilerTypes.qualifyDeep` (passo 2b: após `simpleNamePackage` devolver null e
nem o módulo nem o `SymbolTable` declararem o nome), via `builtinDeclaredType`
(`KofUi.typeByName` para todos os tipos UI + `KofMedia.IMAGE_DATA`) e o guard de
shadowing `unitDeclaresType`; `MemberResolver.resolveType` passa por
`qualifyDeep`, e o `VarDeclStmt` do `StatementLowerer` agora resolve com o
analisador semântico (o `toType` de 2 args pulava `qualifyDeep`, então um local
declarado mantinha o pacote vazio). Prova:
`ComponentCoreE2ETest.declaredUiAndMediaTypesCompileAndRun` +
`userClassShadowsBuiltinUiTypeName` (JVM+Native+JS).

### 5. `app.security()` → **inspirado no Spring Security**
Refinar o middleware composto ao **modelo mental do Spring Security**, mantendo
a superfície Kof idiomática e a saída determinística:
- **Chain estilo `HttpSecurity`:** a ordem do middleware é fixa e do framework
  (não composta à mão pelo usuário) — a ordem fixa atual (rate-limit → CORS →
  headers → session → CSRF → auth → RBAC) é exatamente a ideia de filter-chain
  do Spring.
- **`authorizeHttpRequests`:** os caminhos públicos são uma **allow-list** de
  matchers; tudo que não casa exige autenticação. Leituras **não** são
  implicitamente públicas: **o default é autenticado** (o
  `anyRequest().authenticated()` do Spring), com matchers `permitAll`
  explícitos. Isto reverte a escolha interina "reads públicas" do merge — o
  blog E2E manda o token de sessão nos GETs.
- **CSRF:** ligado por default para métodos que mudam estado (o Spring liga por
  default); métodos seguros emitem o cookie. **Session:** o modo header-token
  fica (Kof não tem sessão de servlet), mesma regra "autenticado por default".
- **Native/JS:** seguem gap honesto (`WEB006`).

> **✅ EXECUTADO (14/09, dono 192.168.100.18):** `app.security()` adotou o modelo
> Spring acima. **CSRF LIGADO por default** quando `app.security()` é
> configurado (`kof_web_security_opts` seta `securityCsrf = true`; `csrf:false`
> desliga explicitamente) — métodos seguros emitem o cookie double-submit,
> métodos que mudam estado o exigem. **Leituras são autenticadas por default**:
> a guarda de sessão já exige auth em toda request fora de `publicPaths`
> (GET incluído), então a nota "reads públicas" do merge estava desatualizada.
> **`permitAll`** aceito como alias de `publicPaths` (allow-list de matchers).
> `app.security()` sem args segue só-headers (GET `/hello` → 200), e apps que
> nunca chamam `app.security()` não são afetados. Prova: `KofWebE2ETest` 25/25
> (novos `securityCsrfIsOnByDefault`, `securityPermitAllAliasIsPublicPaths`) +
> `KofBlogE2ETest` (double-submit nos POSTs) + `KofOAuthResourceServerTest` 4/4.

### 6. §180 — `println(double/float)` no Native x86 → **inspirado no Java**
Alinhar o Native ao **`Double.toString`/`Float.toString` (Java)**: decimal
shortest round-trip, `Float` impresso na sua própria forma mais curta (não a
expansão double), o limiar de notação científica do Java (`1e7`→`1.0E7`,
`1e-3`→`0.001`). Inspirado no Java (representação mais curta estilo
Ryu/Grisu; um loop limitado `%.{1..17}g`+`strtod` é implementação
determinística aceitável) — sem reinventar o algoritmo além do que o JDK já
define. Célula golden cross-target (`floatprint`).

> **✅ EXECUTADO (15/09, dono 192.168.100.18):** o Native x86_64 agora usa o
> loop limitado `%.*e`+`strtod` (a opção sancionada acima). Nova fatia de
> runtime `RuntimeDtoa` emite `kof_dtoa_format`, `kof_double_to_string` e
> `kof_float_to_string`: para cada precisão `0..16` (double) / `0..8` (float)
> formata com `snprintf("%.*e")` e re-parseia com `strtod`, guardando a
> **menor** precisão que faz round-trip bit-exato; depois reformata no estilo
> Java (científica só quando `|x|>=1e7` ou `<1e-3`, `E` maiúsculo, mantissa
> sempre com parte fracionária) e `Float` mantém a **própria** forma mais curta
> (não a expansão double). `RuntimePrintNum` (o `print` sem box),
> `RuntimeJsonEncode` (NaN/±Inf → `null`) e os ramos `.Lce_double`/`.Lce_float`
> do `RuntimeCollectionToString` delegam a ela; o `RuntimeStringConv` não
> carrega mais a conversão de float/double (só int/char/long/bool).
> **Restrição libc:** `snprintf`/`strtod` são necessários, então isto é **só
> x86_64**; riscv/aarch seguem `FLT001`. O `_start` do runtime Kof **não**
> garante alinhamento de pilha de 16 bytes, então cada entry point do dtoa faz
> `andq $-16, %rsp` antes das chamadas libc (o `movaps` da glibc precisa de
> 16B — o desalinhamento era a causa-raiz do SIGSEGV). Prova:
> `ConformanceMatrixTest.doubleprint` (exclusão do Native removida, estendida
> com `0.001`/`1.0E-4`/`3.4028235E38`/`-0.0`) — Native == JVM byte a byte;
> `KofMathTest` 29/29, `JsonE2ETest` 18/18, `JsonCompleteE2ETest` 9/9,
> `NativeRuntimeSliceRegistryTest` 7/7, `ConformanceMatrixDocTest` verde.

---

## D-BASELINE — baseline da toolchain 21 → 25 (✅ decidido 14/09, mantenedora)

- **Decisão:** o baseline de build da toolchain do repo sobe de **Java 21**
  para **Java 25** (LTS), pedida pela mantenedora na sessão da lane CodeQL
  (14/09) para destravar o codemod 100% preservador de comportamento dos
  findings `java/local-variable-is-never-read` (×82) e parte de
  `java/unused-parameter` (×81): **unnamed patterns/variables, JEP 443,
  finalizado no Java 22** (medido: `javac --release 21` recusa
  `case WhileStmt _ -> false;`).
- **O que muda (toolchain do repo, NÃO a linguagem):** `pom.xml` `release=25`;
  `setup-java` 21→25 em ci/codeql/release/benchmark/android; Temurin embutido
  do `package.sh --jdk` 21→25; README canônico + PT "JDK 25+"; CHANGELOG EN/PT
  (seção Build de 0.4.0-beta); `learn/31-distribution` EN/PT com a nota das
  três camadas.
- **O que NÃO muda (regra 6 — alvo de runtime de programa Kof, congelado):**
  `JvmBackend` continua emitindo `V21`; template Android continua
  `release="21"`; `KofVersion.TOOLING_API=21` (piso do programa emitido,
  reportado por `kof info`); a guarda `--release 21 --enable-preview` do
  `JvmRuntime` (caminho vk/extern em JDK <22) segue correta em 21..25.
  **Programa Kof compilado hoje roda em JVM 21+** — subir a toolchain do repo
  não sobe o runtime mínimo da linguagem.
- **Evidência:** `mvn -o -pl kof-compiler -am compile` verde com
  `Compiling ... with javac [debug release 25]`; suíte do kof-compiler em JDK
  25: 1464/0 (162 skip) antes do codemod, e worktree b3ab9858+codemod
  61-bindings: 1574/1 (a única fail é o flake SSE documentado da família §90,
  verde 6/6 isolado).

---

## Como atualizar este doc

Decidiu mais alguma coisa no chat → trava aqui (data + opção + evidência de
código quando houver). Item decidido que vira código: sai daqui para a fila
do `roadmap.md` §23/DOING; a linha fica marcando `✅ decidido <data>` (o
registro é permanente — a pasta `decision-pending/` não existe mais).
