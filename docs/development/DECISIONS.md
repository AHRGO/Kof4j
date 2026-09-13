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

---

## Como atualizar este doc

Decidiu mais alguma coisa no chat → trava aqui (data + opção + evidência de
código quando houver). Item decidido que vira código: sai daqui para a fila
do `roadmap.md` §23/DOING; a linha fica marcando `✅ decidido <data>` (o
registro é permanente — a pasta `decision-pending/` não existe mais).
