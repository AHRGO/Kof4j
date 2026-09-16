[English](README.md) | [Português](README.pt_BR.md)

# Development — backlog vivo (só trabalho em desenvolvimento)

> **Base:** `0.4.0-beta` · branch `beta-0.4.0` · **atualizado:** 16/09/2026
> **Suíte medida neste HEAD:** `2218` run (1911 kof-compiler + 38 kof-script
> + 7 kof-c-compiler + 262 kof-cli), **0 regressões / 0 erros / 0 falhas nesta corrida**, 192 skip (a única falha que a suíte já mostrou é o flake INTERMITENTE conhecido do §252 nativo `spawnWorkerThrowPropagatesThroughSelectAnyNative`, dona lane nativa `.18`/nat — não é regressão; re-medido 16/09 ~15:54 no tip `9572949f` a partir de um CLONE LIMPO; o flake ficou CALADO pela 3ª vez seguida — disparou 09:44, calou 11:38/15:09/15:54 → ~1/4) (sem
> qemu no host da medição: os 84 cross são pulados, + os 5 DBs externos +
> outros guardas de toolchain; `node` presente — todos os `*Js` verdes) —
> os 262 kof-cli refletem `e5013152` (DepsTransitiveTest, +10; `2a60b426` reescreveu o guard, mesmos 10 @Test). 1911 compiler = 1899 + 3 (`78b733fa` NumericFormatterE2ETest) + 2 (`7b38d0d4` §253-face-A KofTimeE2ETest) + 3 (`7cd69a7b` SSE-JS KofWebJsE2ETest) + 1 (`4ea099b3` §261 window-bind KofJsBrowserE2ETest) + 3 (`92d11a03` G-6b NativeX86GcMarkScopeTest); +1 skip no KofDbE2ETest = o guard de sysroot do §255 (`06e77e94`). O número 2199/1902/252 foi uma contagem no meio do caminho (medida enquanto `555d2afe`/`e5013152` landavam); 16/09 ~15:54 é o nº autoritativo do clone limpo. A leitura de 16/09 ~01:45 deu 297 erros = o trap de stub ECJ velho do §257, limpo com `mvn -pl kof-runtime clean`. O número
> anterior (1662/13-erros, 13/09) era de host sem node. **Nº autoritativo da suíte = a execução no host** (o gate
> `mvn test ... -Dmaven.test.failure.ignore=true`; conferir por módulo com
> `grep -rl FAILURE */target/surefire-reports/*.txt`), não esta linha — ela
> apodrece a cada commit. Refold da concatenação do `NativeRiscvAsm` para
> `<clinit>` (anti-pattern novo `constant-folded-runtime-asm.md`) verde no
> gate `gate1585.log` (HEAD 54da1325).
> **Regra dos 3 estados (`AGENTS.md`):** `docs/` = implementado/decidido ·
> `development/` = **trabalho técnico pendente** · `development/future/` =
> **só plano, zero código**. Concluiu → move p/ submódulo de `docs/` no mesmo
> commit; iniciou → cai p/ cá. A varredura de 12/09 (`655afa6b`) moveu 13 docs
> de `future/` p/ cá (todos com código) e 4 concluídos p/ `docs/`.
> **Refactor de clareza 13/09 (mantenedora):** bugs/gaps/matrizes →
> `docs/bugs-and-gaps/` (linhas 2, 41, §2, §3, §4.2, §5); planos **parados por
> decisão** foram **ratificados 13/09 e consolidados em `DECISIONS.md`** (a
> pasta `decision-pending/` foi extinta — ver §3). Este README lista o que
> **anda**; decisão tomada mora em `DECISIONS.md` (regra 6: frente sem linha
> lá não é atacada).

**Fontes de verdade que NÃO estão aqui (não são backlog):** `docs/status.md`
(o que funciona + gate da suíte), `docs/backend-parity.md` (matriz de
paridade com gaps honestos), `docs/bugs-and-gaps/specification-gaps.md`
(SG-001–020 — fila do maintainer COMPLETA 12/09, virou referência).

---

## 1. Ordem de execução dos planos (fila oficial da lane development)

> Critério: (1) frente designada pela mantenedora > (2) saúde do gate >
> (3) trabalho de código-puro sem decisão > (4) itens bloqueados = NÃO atacar
> (regra 6). Itens de registro vivo (matrizes/auditorias) não têm "fim" —
> atualizam-se a cada gap fechado, não puxam prioridade.

| # | Plano | Estado | Por que nesta posição | Próximo passo concreto |
|---|---|---|---|---|
| 1 | `stdlib/PLAN-TREE-SHAKING.md` (#97) | ✅ **CONCLUÍDO 13/09** — S-1..S-6.1 ✅ (S-6.1 mergeado `0104f6d6` PR #106) + S-7 ✅ (consolidado em `docs/stdlib/stdlib-loading.md`, movido p/ `docs/stdlib/`) | frente designada 11/09, fechada; S-5-x86 segue fila bugfix (`root_end`, fora deste plano) |
| 2 | ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **FEITO 13/09 — F3 fechada** (NativeBackend **498** ≤500 medido: `NativeSymbolMangling` 92 + `NativeStaticData` 116 + `emitMethodTable`→NativeClassMeta; o bloqueio "lane GC em `nat/`" caducou — refs não existem mais no repo, regra do dono-morto) — **PLANO FECHADO e MOVIDO 13/09** (F1–F9 todas ✅; regra dos 3 estados) | gate ≤500 virou **ratchet travado no CI** (2652aa45, §140): dívida não cresce e só encolhe; a contagem autoritativa é `wc -l scripts/check_500-baseline.txt` (atualize APONTANDO p/ o arquivo, não gravando nº que apodrece a cada split) | — (doc em `docs/architecture/`; se resíduo >500 novo aparecer, reabre como item próprio) |
| 3 | `native-multiarch.md` (NATIVE002) | `EM CURSO` — ~30 faces cross fechadas sob qemu (41+41 medidas 12/09) | paridade riscv/aarch = condição de estabilidade do release | GC mark-sweep p/ riscv/aarch (faces restantes da §5; JS é outra frente) |
| 4 | `planning-otp-supervision.md` (#83) | `EM CURSO` — 1ª fatia ✅ 11/09 (núcleo+`restartLimit`+`stop`) + **S2-JVM ✅ 13/09** (`startAll`/`lacoUnico` + wrapper de identidade) + **S2-Native x86 ✅ 15/09** (§129 fechado — chain TLS por thread + handler por worker; `KofSupervisorE2ETest` 15/15 incl. `supervisorNativeParityX86`/`supervisorNativeS2ParityX86`; riscv/aarch=OTP001, JS=OTP002 §132) | **DD-OTP RATIFICADAS 13/09** (opção 1a: S2 JVM; riscv/aarch PARTIAL) — ver §3 | JS (OTP002/§132) e riscv/aarch (clone cru, sem TLS) seguem gates honestos; promover OTP001/002 só com essas faces decididas |
| 5 | ~~`plan-editor-integration.md`~~ → `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | ✅ **CONCLUÍDO 14/09** — degraus 0–13 implementados e provados (`EditorIntegrationTest` 23/23; `kof editor` completo nos 7 editores; release gate §19 verde) | movido para `docs/tooling/` (regra dos 3 estados) | — |
| 6 | ~~`plan-stdlib-expansion.md`~~ → `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ **CONCLUÍDO 14/09** — S0–S13 implementados e validados nos 5 alvos; pendências de decisão consolidadas em `DECISIONS.md` §D-STDLIB | movido para `docs/stdlib/` (regra dos 3 estados) | — |
| 7 | fila recém-aberta de `DECISIONS.md` (13/09): ~~`time.todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds` (D-STDLIB)~~ **✅ EXECUTADA 13/09** (S7e-S7h, matriz stdtime3-6, suíte 1772/0/0; TIME003 = fila geral) · ~~`CmdNew` (D-APP I1)~~ **✅ FEITO 14/09** (`kof new --type mono\|backend\|frontend\|full-stack`, esqueletos compiláveis, APP003 honesto, `CmdNewTest` 8/8, matriz APP em `backend-parity.md`) · ~~`chacha20Encrypt/Decrypt` (D-SEC)~~ **✅ FEITO 14/09** · ~~`security.cookies`~~ **✅ FEITO 14/09** · ~~`app.security()` (C18)~~ **✅ FEITO 14/09** (middleware composto, ordem fixa, JVM; `KofWebE2ETest` 22/22 + `appSecurityPipelineE2E`; Native/JS `WEB006`; superconjunto unificado .18×.22) · ~~`--fat` (D-APP I3)~~ **✅ FEITO 14/09** (`kof build --fat` → `kof-app.jar` executável com classes+runtime+deps; `CmdBuildFatTest` 4/4, prova `java -jar`; não-JVM recusa honesto R6) · ~~blog E2E (D-SPRING F12)~~ **✅ FEITO 14/09** (`KofBlogE2ETest` verde; expôs+corrigiu 2 bugs JVM: `readRequest` contava body em chars vs `Content-Length` em bytes — travava conexão UTF-8 multibyte; CLOB cru do JDBC no read path) | `RATIFICADO` (decisão travada 13/09) | — | ~~OAuth resource-server (D-SEC camada 16)~~ **✅ FEITO 14/09** (`auth.resourceServer(jwksUrl,issuer,aud)` + `resourceServerVerify`; RS/ES via JWKS, sem confusão de algoritmo; integra com `auth.authenticated`/`app.security`; `KofOAuthResourceServerTest` 4/4; Native/JS `SECN007`) — **FILA §7 VAZIA**; cada linha = unidade-teste-commit |
| — | registros vivos: `conformance-matrix.md`, `ecosystem-coverage.md`, `KOFUI-AUDIT.md`, `known-bugs.md` (em `docs/bugs-and-gaps/`); `roadmap.md` (aqui); `roadmap-audit.md`/`complexity-audit.md` (em `docs/audits/`) | `VIVA` | **não são backlog** — matriz/auditoria/fila que se atualizam junto com cada fechamento | atualizar célula/seção no MESMO commit que fecha o gap |

**Regra R12 (AGENTS.md):** nada de `future/` (plataforma universal, RAII,
package-compiler) abre antes de SYSTEMS fechar (paridade + GC + estabilidade).

---

## 2. Bugs abertos (fila em `docs/bugs-and-gaps/known-bugs.md`) — triagem
13/09, ressincronizada 14/09 ~22:15 (lane docs — registro vivo, regra §1 da
tabela três-estados)

**32 itens na fila aberta** (contados do arquivo em 14/09; a lista de 13/09
abaixo foi tirada ANTES da onda §220–§239). A conclusão permanece COM
correção: os itens ainda abertos têm dono/bloqueio/regra-6 — mas o "ZERO item
código-puro" foi REFUTADO pela própria onda de 14/09: §236 (comparisonReturn
Bool×Int) e §238 (hoist de local escapante + sipush) eram itens código-puro do
decompiler e **foram consertados na lane de desenvolvimento** (unidades 2c,
`8719e304`+`f2371212`), enquanto §233/§234 (migração de teste
`split()->String[]` — lane compiler) e §237 (`computeStack` — lane .22) foram
catalogados com dono. O resto da onda de 14/09 (§220–§232, §235, §239) pertence
às lanes .15/.18/.22 ou regra 6. Itens da lista de 13/09 que mudaram desde
então: §129-[coleção] ✅ 11/09 (`3645` — o §129 ABERTO é o do OTP, colisão de
número), os demais seguem como descrito. Fechados 13/09: §89, §106 (+JS `ab85cfae`), §117, §131 (+residual
`73ca2d58`), §127-JVM, §155, §94, §156, §81 (BigInt), §163 (interpretador
2º parâmetro largo); §157-160 e §65 fechados/NÃO-REPRODUZ.
Todos pendurados em:

| Grupo | Bugs | Quem destrava |
|---|---|---|
| Decisão ratificada 13/09 — implementação pendente | §161/NAT-STR01 (§89 ✅ `e33425b5`, §106 ✅ `5b939106`+JS `ab85cfae`, §117 ✅ `3734f2aa`, §131 ✅ `18a64d45`, §81 ✅ `839bd73f`, §163 ✅ `d2a8a618`; §45/DD-01 FECHADO 13/09 — ver `docs/decisions/DD-01-finally-return.md`) | fila ratificada / lanes executoras |
| Congelado regra-6 | ~~§101~~ ✅ CORRIGIDO 14/09 (DECISIONS §1 opção A — IEEE 754 puro em todos os alvos) | ninguém (contrato) |
| Lane alheia | §104b-ii + §107 restante + §114 (bugfixer — storage-box de record), §132 (OTP-JS), §165 (js-slices — re-verificado 13/09: NÃO reproduz em clean build, provável não-bug) — §129 ✅ CORRIGIDO 15/09 (lane development `192.168.100.18`) | donos das lanes |

Corrigidos 13/09: **§89** (conversão numérica em primitivo = alias do `as` +
warning SEM090; 4 alvos — `CoreRegressionE2ETest.numericConvertMethodAliasOfAs`),
**§106** (`json.encode(Map)` chaves SORTED nos 4 alvos — `JsonCompleteE2ETest`
+ célula `jsonenc-map` da matriz; residual JS `ab85cfae`),
**§117** (cancel por TID real + probe linear no Native x86 — `KofConcurrency2Test`
34/0), **§131** (sobrecarga de método por assinatura nos 4 backends —
`CoreRegressionE2ETest.methodOverloadByArity` + harness 4/4), **§94** (EQ/NE de Double/Float no interpretador agora IEEE —
célula `stdsqrt` 4/4 sem exclusão), **§127-JVM** (cast p/ tipo-função →
interface SAM sintética; `LambdaE2ETest.castToFunctionTypeJvm/Native`),
**§155** (tipo-função como type-arg → parser preserva os espaços do type-ref;
`LambdaE2ETest.declaredFunctionTypeListJvm/Native`), **§156** (lista
heterogênea de lambdas mesma assinatura → elemento sem className, dispatch
SAM; `LambdaE2ETest.heterogeneousLambdaListJvm/Native`), **§81** (Long=BigInt
no JS, paridade 64-bit real — `839bd73f`) e **§163** (interpretador: 2º
parâmetro largo `Long`/`Double` lido como `null` — `KofInterpreterParityTest.
wideParametersOccupyTwoSlots` + célula `wideparams` 4/4; `d2a8a618`).
Corrigidos 12/09: §90 (web, #98), §125,
§139, §140 (gate→ratchet), §107-face
escalar, §108, §138, MATH001, TIME002, **§145/§146/§147 (issue #101,
`440730c8` — prova qemu 42+42)**.

---

## 3. Decisões da mantenedora (registro: `DECISIONS.md`)

> Nada aqui está "parado esperando" — as frentes que esperavam decisão foram
> **ratificadas 13/09** e vivem em `DECISIONS.md` (D-STDLIB/D-SEC/D-APP/
> D-SPRING/D-PLAT/D-PLATFORM) com a fila de execução aberta. A regra
> permanece: **frente sem linha em `DECISIONS.md` não é atacada** (regra 6);
> decisão do chat trava lá no mesmo commit. `known-bugs.md` =
> `docs/bugs-and-gaps/known-bugs.md`.

| Item | Onde | O que espera |
|---|---|---|
| DD-STDLIB-01 — `randomBytes`/`randomChoice` (S10c) | `docs/stdlib/DD-STDLIB-01-array-returns.md` (FECHADO 13/09, movido p/ docs/) | ✅ IMPLEMENTADO 13/09 (opção 6a: `randomBytesHex` alias de `hex` + choice=idiom; S10c FECHADO) |
| DD-STDLIB-02 — `time.format`/`boundaries` | `DECISIONS.md` §D-STDLIB | ✅ RATIFICADO 13/09 (UTC-only, escalares ISO, zero pattern-DSL) — **fila liberada** (todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds) |
| DD-01 — `finally` no caminho de `return` | `docs/decisions/DD-01-finally-return.md` (FECHADO 13/09, movido p/ docs/) | ✅ IMPLEMENTADO 13/09 (opção 4a: FinallyFrame na IR + gates finallyReturnJvm/Js; suíte 1627/0; bug 45 FECHADO) |
| DD-OTP (restante) | `planning-otp-supervision.md` | ✅ RATIFICADAS 13/09 (opção 1a: wrapper `(id, resultado)`; riscv/aarch PARTIAL) — **S2-JVM ✅ IMPLEMENTADO 13/09** (`Supervisor.startAll`/laço selectAny único) e **S2-Native x86 ✅ IMPLEMENTADO 15/09** (§129 fechado, DECISIONS §2 opção B); resta OTP002-JS (§132) e riscv/aarch (`OTP001`, clone cru) |
| `pow`/`-lm`, `roundTo`-mode | `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ `pow` **FEITO 13/09** (7a: `-lm`; 5 alvos MATH001 cross; `stdmathpow` matriz + `powCrossArchRefused` `d736e36e`) · `roundTo` **NÃO aprovado pela 7a** (ratificação = só pow; "+roundTo" era nota de agente no plano — superfície/assinatura indefinida = regra 6, aguarda decisão da mantenedora) |
| NAT-STR01 (case-map astral) | `known-bugs.md` §161 / conformance-matrix | ✅ ABERTO POR DECISÃO 13/09 — implementar UTF-8 astral nos nativos |
| §129 (unwind cross-thread via TLS) | `known-bugs.md` | ✅ CORRIGIDO 15/09 (DECISIONS §2 opção B: chain TLS por thread + handler por worker no trampolim; x86_64; riscv/aarch seguem `OTP001`) |
| json §106 | `known-bugs.md` | ✅ CORRIGIDO 13/09 (opção 2b: chaves sorted) — JVM/x86/Script/JS (`5b939106` + residual JS `ab85cfae`); gap de porte riscv/aarch rastreado à parte |

---

## 4. Índice do que está EM DESENVOLVIMENTO aqui

### 4.1 Plataformas & migração (caíram de `future/` 12/09 — código iniciado; os `~~riscados~~` foram **ratificados 13/09 e consolidados em `DECISIONS.md`** — os 6 arquivos de `decision-pending/` foram apagados)

| Arquivo | Estado real | O que falta p/ fechar |
|---|---|---|
| ~~`PLATFORM-PLAN.md`~~ → `DECISIONS.md` §D-PLATFORM (morto) | F1–3/8/9 com código (`ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/007, conformance 11 testes) | F1 resolvido pelo manifesto; F4/F5→KOFUI-AUDIT/stdlib-web; F6 wasm/F7 android→tabela D-APP Q7/Q10; F9→conformance-matrix |
| ~~`APPLICATION_MODEL.md`~~ → `DECISIONS.md` §D-APP (Q1–Q10 travados) | `application { onStart/onShutdown }` ✅ E2E 3 targets; I2 (full-stack) ✅ `FullStackE2ETest` | `CmdNew` (I1), I3 (`--fat`), System/distribuído — fila |
| ~~`LEGACY_MIGRATION.md` + `DECOMPILER.md` + `TRANSLATOR.md`~~ → **`future/` (DESPRIORIZADO pela mantenedora 15/09)** — umbrella §4 IR/Confidence, §8 diff-testing; ~~+ `DIFFERENTIAL_TESTING.md` + `LEGACY_IR.md`~~ (FUNDIDAS no umbrella 13/09) | código fica no repo: `inspect/decompile/translate/compare/migrate` (`Main.java:25-29`) + `Confidence`/`Type.fromJvmSignature`; **NÃO é trabalho atual — promoção exige decisão explícita dela**; **contagem viva = `roadmap.md` §23 TIER 3–5** (não duplicar número aqui) | cobertura: switch/athrow opacos, `inspect --java` (R5 do audit), IR non-JVM |
| ~~`IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md`~~ → `roadmap.md` §23 | **FUNDIDOS 13/09** (redundância ~85% entre si; status sobre-claimed vs código — ex.: `CodegenStep` ✅ inexistente, FFI Native era FFI001) | §23 é o plano único; tiers 6–12 = `future/` (R12) |
| ~~`PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md`~~ → `docs/audits/` | comparação branch `planning-future`×beta **encerrada 13/09** — nada de código aberto próprio mora nelas: R2 vive em `DECISIONS.md` §D-APP/§D-PLATFORM; R5 no cluster migração (`DECOMPILER.md`/`LEGACY_MIGRATION.md` §4 Fase C) | — (fora de `development/`) |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | FECHADO 13/09 (FinallyFrame IR + gates; bug 45 CORRIGIDO, suíte 1627/0) | — (fora de `development/`) |
| ~~`planning-stdlib-time-design.md`~~ → `DECISIONS.md` §D-STDLIB | `addDays`/`diffDays` nos 5 alvos | ✅ RATIFICADO 13/09 — fila liberada |

### 4.2 Plans & auditorias vivas

| Arquivo | Estado real | Nota |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUÍDO 13/09 (S-1..S-6.1 + S-7; consolidado em `docs/stdlib/stdlib-loading.md`) | S-5-x86 = fila bugfix (`root_end`), fora do plano |
| `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | S0–S6, S8–S12 ✅ (MATH001/TIME002 fechados 11/09) | só decisões pendentes (§3) |
| `planning-otp-supervision.md` | 1ª fatia ✅ JVM+Script; **S2-JVM ✅ 13/09** (`startAll`/`lacoUnico`); **S2-Native x86 ✅ 15/09** (§129 fechado — chain de handler por thread) | riscv/aarch (`OTP001`) + JS (`OTP002`) seguem gates honestos |
| `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | CLI/DAP/LSP/stdout-json ✅ | plugin IntelliJ |
| `native-multiarch.md` | re-auditoria 12/09 sob qemu: ~30 faces cross fechadas | GC riscv/aarch + faces §5 |
| ~~`security-plan.md`~~ → `DECISIONS.md` §D-SEC | A ✅; B/C ✅; C11 cookies + C18 middleware + D16 OAuth + D17 TLS-cert **ratificados 13/09** (executa com I2 do app model) | ChaCha20 = fila; OAuth: resource-server→client, provider=NUNCA |
| ~~`plan-platform-completion.md`~~ → `DECISIONS.md` §D-PLAT (morto) | P0–P3 ✅; P4 (health/tracing/metrics) ❌; P5: `kof fmt` ✅ 31/08, LSP/VS Code ❌ | P4/P5 já têm casa (§23/backend-parity); blog E2E = D-SPRING F12 |
| ~~`plan-spring-independence.md`~~ → `DECISIONS.md` §D-SPRING | F1–9 ✅/parciais; F10–F12 **ratificadas 13/09** (escopo travado) | F12 blog E2E **AGORA** (validação da plataforma); starter só depois |
| ~~`conformance-matrix.md`~~ → `docs/bugs-and-gaps/` | matriz Feature×4 targets travada por `ConformanceMatrixTest` (11) + doc-gate | viva: atualiza com cada gap |
| ~~`ecosystem-coverage.md`~~ → `docs/bugs-and-gaps/` | G1–G12 com `PARTIAL`/`PLANNED` (events, batch, AI) | referência de cobertura |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monólito→micro) | longo prazo |
| ~~`roadmap-audit.md`~~ → `docs/audits/roadmap-audit.md` | matriz 06/09 + fila P0→P5 (P0 FECHADO 09/09) | re-audit quando algo fecha |
| ~~`KOFUI-AUDIT.md`~~ → `docs/bugs-and-gaps/` | UI001-Native (face R6: no-op silencioso) ABERTO | lane UI |
| ~~`known-bugs.md`~~ → `docs/bugs-and-gaps/` | 8 abertos (triagem §2 acima; §81/§163/§127-JVM, §155, §94, §157-160 e §65 fechados/NÃO-REPRODUZ 13/09) | fila viva |
| ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **FEITO + MOVIDO 13/09** (F1–F9 todas fechadas — F3: NativeBackend 498 ≤500 medido, bloqueio da lane GC caducou/regra do dono-morto); ratchet `check_500-baseline.txt` (dívidas travadas — nº autoritativo = `wc -l` do arquivo) no CI | plano FECHADO (regra dos 3 estados) |

### 4.3 `future/` — só plano, zero código (não é trabalho atual)

| Arquivo | Gatilho p/ cair p/ cá |
|---|---|
| `PLAN-UNIVERSAL-PLATFORM.md` | decisão + SYSTEMS fechado (R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump com `using`/`resource_scope` decidido |
| `PLAN-BAREMETAL-BOOT.md` (nativo → bare-metal/bootável; diretiva da mantenedora 15/09) | SYSTEMS fechado (R12) + primeira face (costura HAL) autorizada |

*(DD-STDLIB-01 `planning-stdlib-array-returns.md` **saiu de `future/` 13/09** — decisão 6a ratificada, implementado e movido p/ `docs/stdlib/DD-STDLIB-01-array-returns.md`.)*

*(movimentos históricos de 12/09: 13 docs caíram de `future/` p/ cá —
evidência em cada linha de §4.1; snapshot SG 08/09 → `docs/history/`)*

---

## 5. O que NÃO está mais aqui (consolidado 12/09, com prova)

| Saiu p/ | Doc | Prova |
|---|---|---|
| `docs/bugs-and-gaps/specification-gaps.md` | SG-001–020 + E1–E3 | fila do maintainer COMPLETA (resumo do próprio doc); snapshot antigo → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | níveis 0–4 | query DSL 01/09 (`KofOrmE2ETest` 22), MySQL prepared (`nativeMysqlPreparedBinary`), pooling ✅; DB001/ORM001 vivem na matriz de paridade |
| `docs/audits/complexity-audit.md` | snapshot 02/09 | números pré-SOLID-500; gate vivo = `scripts/check_500.sh` (ratchet) |
| `docs/history/roadmap-gap-2026-09-03.md` | gap report datado | pendências vivem em roadmap-audit/known-bugs |
| `docs/decisions/` | `planning-switch-expr`, `planning-mutability` | SYN001, DD-02/SEM037/SEM038 aplicados |
| `docs/ui/PLAN-CANVAS-WIDGET.md` | CANVAS001 | `UiE2ETest` 29/29 sem exclusões |

---

## 6. Como usar (agente autônomo)

```
1. LEIA docs/status.md + docs/backend-parity.md            → o que funciona (gate)
2. LEIA a fila §1 deste README + DOING.md (donos)          → o que falta, sem colisão
3. BUGS: known-bugs.md §Aberto só com dono na mesa; decisão → §3, não editar
4. EXECUTE um escopo → teste (suíte com -Dmaven.test.failure.ignore=true)
   → commit com DOING.md atualizado → mova doc p/ docs/ se FECHOU
5. RE-DISPARO: sem item na fila §1 sem dono E suíte verde → RECUSE
   (condição de estabilidade AGENTS.md)
```

**Sincronização:** `git fetch && git pull --rebase --autostash` antes de TODO
commit; releia este README depois do pull (outro agente pode ter fechado um
item da fila). `DOING.md` marca dono/estado; este README é a **fila**.

**Não confundir:** `training/` + `learn/` + `docs/` = corpus estável.
`development/` = trabalho que ainda não é comportamento previsto. Mudança de
contrato congelado nunca passa por aqui sem bump + decisão (regra 6).
