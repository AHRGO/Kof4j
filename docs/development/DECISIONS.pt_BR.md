[English](DECISIONS.md) | [Português](DECISIONS.pt_BR.md)

# DECISIONS — registro de decisões da linguagem

**Última atualização:** 15/09/2026
**Mantenedora:** Mel Santos
**Natureza:** registro normativo e histórico de decisões de arquitetura, semântica e evolução da linguagem.

> Este arquivo registra decisões que já foram tomadas. Ele não é um backlog, um diário de implementação nem uma coleção de propostas abertas.
>
> Uma decisão registrada aqui continua sendo a fonte de verdade até ser formalmente substituída por outra decisão. O código, os testes e o roadmap devem convergir para este contrato.

---

## 1. Autoridade e regras de alteração

### 1.1 Quem decide

A mantenedora decide questões de contrato, semântica, arquitetura e direção da linguagem.

Agentes e contribuidores podem:

* investigar alternativas;
* propor decisões;
* implementar decisões aprovadas;
* corrigir bugs e divergências em relação ao contrato;
* atualizar evidências e estado de execução.

Agentes e contribuidores **não podem alterar o contrato de uma decisão por interpretação própria**.

### 1.2 Como uma decisão entra neste arquivo

Uma decisão só é considerada vigente quando registrada com:

* identificador estável;
* data;
* escopo;
* contrato;
* decisão tomada;
* relação com decisões anteriores, quando aplicável;
* estado de implementação, se houver.

A decisão não mora no chat. O chat pode conter a discussão; este arquivo contém o resultado normativo.

### 1.3 Como uma decisão é revisada

Uma decisão vigente só pode ser alterada por uma nova entrada que:

1. identifique a decisão anterior;
2. explique o que muda;
3. registre a nova decisão;
4. preserve o histórico;
5. atualize o roadmap e a documentação afetada.

Uma decisão substituída não é apagada.

### 1.4 Estados permitidos

| Estado        | Significado                                                |
| ------------- | ---------------------------------------------------------- |
| `DECIDED`     | Contrato aprovado, ainda sem implementação completa        |
| `IN_PROGRESS` | Implementação em andamento                                 |
| `IMPLEMENTED` | Implementação concluída e validada                         |
| `PARTIAL`     | Parte do contrato implementada; gaps explícitos permanecem |
| `BLOCKED`     | Implementação depende de outra decisão ou capacidade       |
| `SUPERSEDED`  | Substituída por outra decisão                              |
| `REJECTED`    | Alternativa analisada e rejeitada                          |
| `CLOSED`      | Registro encerrado sem backlog restante                    |

O estado de implementação **não altera o contrato**.

---

## 2. Invariantes globais

Estas regras continuam válidas independentemente das decisões abaixo.

### G-01 — Freeze de superfície

O congelamento 0.2.6-beta permanece vigente para os itens explicitamente congelados:

* operadores;
* `==`;
* `spawn`;
* coleções;
* demais itens cobertos pela regra 6.

Uma decisão posterior pode alterar um contrato congelado somente quando registrar explicitamente a revisão correspondente.

### G-02 — Visão universal

R1–R12 permanecem como invariantes da visão universal da linguagem.

### G-03 — Limite de complexidade

A regra ≤500 permanece vigente.

### G-04 — Suíte como gate

A suíte de conformidade permanece como gate de integração. Código não pode ser considerado concluído apenas porque compila localmente.

### G-05 — Gap honesto

Quando uma capacidade não existe em determinado target, o sistema deve:

* reportar o gap documentado;
* usar o código de diagnóstico correspondente;
* nunca produzir um resultado silenciosamente incorreto;
* nunca simular suporte inexistente como se fosse suporte real.

### G-06 — Paridade

Quando uma decisão define comportamento observável, a implementação deve buscar o mesmo contrato em todos os targets suportados.

Uma diferença entre targets só é aceitável quando:

1. estiver explicitamente documentada;
2. tiver diagnóstico ou comportamento definido;
3. estiver representada na matriz de conformidade.

### G-07 — Determinismo

Mesma entrada, mesmo contrato e mesmo target devem produzir resultado determinístico.

Quando a decisão exigir paridade cross-target, o resultado observável deve ser equivalente, salvo gaps explicitamente registrados.

### G-08 — Não reinventar a roda

A lógica interna do compilador e do runtime deve se inspirar prioritariamente em:

1. Java — JLS, JVMS e comportamento de `java.lang`/`java.math`;
2. C — ISO C e `libm`, especialmente para o backend nativo;
3. outras linguagens, quando a referência for uma técnica de backend.

Essa regra não autoriza copiar a superfície de outra linguagem. Sintaxe, ergonomia e modelo de escrita continuam sendo decisões próprias do Kof.

---

# 3. Decisões vigentes

## D-STDLIB — tempo e calendário

**Data:** 13/09/2026
**Estado:** `IMPLEMENTED`
**Escopo:** semântica de data, hora e calendário da stdlib.

### Contrato

**D-STDLIB.1 — UTC como referência padrão**

`today()` e `isToday` derivam de `now()` em UTC em todos os targets.

O fuso local só pode ser obtido por API explícita:

```kof
time.tzOffsetSeconds()
```

Native sem suporte de timezone reporta `TIME003`.

Não existe paridade acidental de timezone entre targets.

**D-STDLIB.2 — Calendário escalar**

A API base de calendário usa valores escalares sobre ISO.

Exemplo:

```kof
time.addDays("YYYY-MM-DD", n) -> String
```

A stdlib base não introduz retornos compostos para operações de calendário.

**D-STDLIB.3 — Diferença de horas**

`hoursBetween` conta horas inteiras completas, com truncamento em direção a zero, consistente com `daysBetween`.

Não usa float nem assinatura de 12 argumentos.

**D-STDLIB.4 — Formatação ISO**

```kof
time.formatDateIso(y, m, d) -> String
```

Data inválida retorna `""`.

**D-STDLIB.5 — Parsing ISO**

```kof
time.parseDateIso(value) -> Int
```

Retorna o serial `daysFromEpoch`.

Entrada inválida retorna `0`.

Patterns arbitrários como `dd/MM/yyyy` não fazem parte da stdlib base.

**D-STDLIB.6 — isToday**

```kof
time.isToday(y, m, d) -> Bool
```

Compara com a data UTC derivada de `now()`.

### API ratificada

| Função                      | Contrato                  | Targets                   |
| --------------------------- | ------------------------- | ------------------------- |
| `time.todayIso()`           | `() -> String`            | 5                         |
| `time.formatDateIso(y,m,d)` | `(Int,Int,Int) -> String` | 5                         |
| `time.isToday(y,m,d)`       | `(Int,Int,Int) -> Bool`   | 5                         |
| `time.hoursBetween(...)`    | `(Int × 8) -> Int`        | 5                         |
| `time.parseDateIso(String)` | `String -> Int`           | 5                         |
| `time.tzOffsetSeconds()`    | `() -> Int`               | JVM/JS/SCRIPT; Native gap |

### Evidência

Implementação S7e–S7h concluída em 13/09/2026.

* `KofTimeE2ETest`: 30/30
* Matrizes `stdtime3`–`stdtime6`
* Paridade Script
* Suíte: 1772/0/0

**Referência de implementação:** `docs/stdlib/time.md`
**Fila:** encerrada.

---

## D-SEC — segurança

**Data:** 13–14/09/2026
**Estado:** `PARTIAL`
**Escopo:** criptografia, cookies, middleware de segurança e OAuth2/OIDC.

### Invariantes

* Criptografia não é implementada de forma caseira.
* JVM utiliza JCA quando aplicável.
* JS utiliza WebCrypto quando aplicável.
* Native utiliza implementação auditada ou reporta gap.
* Algoritmos, formatos de token e regras de validação são contratos observáveis.
* Gaps são reportados por `SECN00x`.

### D-SEC.1 — ChaCha20-Poly1305

**Decisão:** adicionar suporte a ChaCha20-Poly1305 conforme RFC 8439.

Formato:

```text
chacha20$<nonceB64(12B)>$<ct+tagB64(16B tag)>
```

API:

```kof
security.chacha20Encrypt(text, keyHex) -> String
security.chacha20Decrypt(token, keyHex) -> String
```

A chave deve ter 32 bytes representados em hexadecimal.

Nonce nunca pode ser reutilizado com a mesma chave.

Native sem implementação reporta `SECN002`.

**Estado:** JVM + JS implementados. Native permanece em gap.

**Evidência:** vetor RFC 8439 + `node:crypto` + `KofSecurityTest`.

### D-SEC.2 — Cookies

API:

```kof
security.cookieSet(name, value, opts)
security.cookieGet(header, name)
```

Defaults:

* `HttpOnly`;
* `Secure`;
* `SameSite=Lax`;
* `Path=/`.

Native sem implementação reporta `SECN006`.

**Estado:** JVM + JS implementados.

### D-SEC.3 — Middleware `app.security()`

A ordem do pipeline é fixa:

```text
rate-limit
→ CORS
→ security headers
→ cookies/session
→ CSRF
→ authentication
→ RBAC
→ route
```

O usuário configura políticas, mas não recompõe a ordem interna.

Sem argumentos, `app.security()` aplica os defaults de hardening.

Em produção, `listen`/`listenSecure` sem `app.security()` emite warning.

### D-SEC.4 — Autenticação padrão

Quando `app.security()` está configurado:

* o default é autenticação obrigatória;
* métodos de leitura não são implicitamente públicos;
* caminhos públicos devem ser declarados por allow-list;
* `permitAll` é alias de `publicPaths`;
* token inválido nunca passa silenciosamente;
* CSRF é ligado por default para métodos que alteram estado;
* `csrf:false` desliga explicitamente essa proteção.

### D-SEC.5 — OAuth2/OIDC

A implementação segue esta ordem:

1. resource server;
2. client authorization-code + PKCE;
3. provider: fora do escopo.

O resource server valida JWT de terceiros por JWKS, issuer e audience.

Algoritmos permitidos:

* RS256/384/512;
* ES256/384/512.

São rejeitados:

* `none`;
* HS*;
* algoritmos fora da allow-list.

Native/JS sem implementação reportam `SECN007`.

### D-SEC.6 — TLS

API:

```kof
app.listenSecure(port, certPem, keyPem)
```

A chave deve estar em PKCS#8 PEM.

JVM é o primeiro target.

Self-signed permanece conveniência de desenvolvimento, não configuração de produção.

### Evidência

* `KofSecurityTest`
* `KofWebE2ETest`
* `KofBlogE2ETest`
* `KofOAuthResourceServerTest`

**Referência:** `docs/stdlib/security.md`
**Implementação:** `docs/stdlib/stdlib-web.md`
**Fila restante:** Native crypto/TLS e gaps documentados.

---

## D-APP — modelo de aplicação

**Data:** 13/09/2026
**Estado:** `PARTIAL`
**Escopo:** manifesto, composição de componentes e execução de aplicações.

### Contrato

**D-APP.1 — Manifesto**

O manifesto da aplicação é:

```text
kof.toml
```

Ele é opcional.

Sem manifesto, o comportamento atual deve permanecer 1:1.

**D-APP.2 — Unidade de aplicação**

Uma aplicação é um diretório contendo um módulo Kof e um `main()`.

Componentes possíveis:

* backend;
* frontend;
* static.

Uma aplicação é a menor unidade de `kof serve` e deploy.

**D-APP.3 — Frontend**

Frontend é outro módulo Kof compilado para JS.

O backend não chama funções do frontend diretamente.

A comunicação front→back ocorre por HTTP/JSON.

**D-APP.4 — System**

System é composição de deploy, não de compilação.

`kof serve --system` permanece rejeitado.

A alternativa aprovada é:

```text
kof serve --list
```

**D-APP.5 — Fat jar**

A flag:

```text
kof build --fat
```

é opcional.

Default continua sendo classpath explícito.

**D-APP.6 — Rebuild**

O frontend é reconstruído sob demanda por hash.

Watcher permanece futuro.

**D-APP.7 — Targets**

Wasm entra na matriz quando o target estiver aberto.

Android é declarado como suportado pelo modelo WebView + KofJS, sem alterar o modelo de aplicação.

### Manifesto

Seções:

```toml
[app]
[serve]
[frontend]
[static]
```

O manifesto é lido pela CLI, não pelo compilador.

Erro de manifesto deve produzir diagnóstico claro.

### Topologias permitidas

* monolith;
* modular monolith;
* microservices;
* microfrontends;
* full-stack;
* backend-only;
* frontend-only;
* full-stack distribuído;
* gateway.

O modelo não muda entre essas topologias.

### Evidência

* `KofProjectConfig`
* `CmdBuildFatTest`
* `KofBlogE2ETest`
* `KofWebE2ETest`

**Referência:** `docs/architecture/application-model.md`
**Fila:** `CmdNew`, integração completa de manifesto/deps e gaps de target.

---

## D-SPRING — independência de framework

**Data:** 13/09/2026
**Estado:** `IN_PROGRESS`

### Contrato

* Nenhum componente da stdlib depende de Spring.
* Nenhum backend gera Java-source como etapa obrigatória.
* Capacidades fundamentais possuem API Kof-native.
* Spring pode ser consumido como alternativa de interoperabilidade.
* O teste de independência tem o mesmo peso do teste de interoperabilidade.

### Fases

| Fase                | Estado        |
| ------------------- | ------------- |
| 1–9                 | `IMPLEMENTED` |
| 10 — testing nativo | `DECIDED`     |
| 11 — CLI completa   | `IN_PROGRESS` |
| 12 — blog E2E       | `IN_PROGRESS` |

### Fase 10

`kof test` deve executar testes do projeto com asserts Kof.

JUnit não é obrigatório.

Escopo inicial:

* unit;
* HTTP com `web.app` em porta efêmera.

Property testing, stress e mocks são incrementos separados.

### Fase 11

Consolidar:

```text
run
build
test
serve
fmt
deps
init
check
new
```

`kofdeps add/remove/list/resolve` já existe.

Falta integrar dependências ao manifesto.

### Fase 12

O blog E2E é o aplicativo canônico de validação da plataforma:

* backend;
* frontend;
* banco;
* autenticação;
* validação;
* manifesto.

A validação deve ser feita por target, com gaps honestos.

---

## D-RELEASE — critério de avaliação de patch

**Data:** 14/09/2026
**Estado:** `DECIDED`

### Contrato

A linha 0.4.0 já foi liberada no #138.

O desenvolvimento continua normalmente após a release.

Quando a beta estiver entre 100 e 150 commits à frente da main, deve-se avaliar uma release de patch.

### Regra

```text
git rev-list --count origin/main..origin/beta-0.4.0
```

Ao cruzar a faixa:

1. abrir issue de release;
2. executar suíte completa;
3. fechar issues conhecidas da lane de bugs/paridade;
4. avaliar o conteúdo acumulado;
5. atualizar versão somente após a avaliação.

### SemVer

Se houver capability nova material que altere contrato, operador ou superfície de API, a versão avaliada deixa de ser patch e passa a ser minor.

O contador não congela features.

Features e fixes concluídos com suíte verde entram no pacote.

**Estado registrado em 14/09:** `main..beta = 1`.

---

## D-ASM-GATE — gate de ASM riscv/aarch

**Data:** 14/09/2026
**Estado:** `DECIDED`

### Contrato

O gate específico de inspeção de ASM para riscv/aarch é opcional enquanto o desenvolvimento nativo não estiver completo.

A main e a beta nunca podem permanecer com testes quebrados.

### Regra

O gate pode ser reativado com:

```text
KOF_ASM_GATE=1
```

O padrão é skip explícito.

O corpo do teste continua portável:

* se `.s` existe, inspeciona o texto;
* se não existe, exige o binário linkado.

### Proteção mantida

A regressão de labels duplicadas continua coberta pelos E2Es sob qemu.

### Reativação

Quando a lane Native estiver completa e a matriz 5/5 estiver verde, o `assumeTrue` deve ser removido e o gate volta a ser obrigatório.

---

## D-BACKEND-SEMANTICS — semântica de backend

**Data:** 14–15/09/2026
**Estado:** `IMPLEMENTED`

Esta seção registra decisões de semântica já ratificadas e implementadas.

### §101 — NaN em comparações relacionais

**Decisão:** IEEE 754 puro.

Comparações relacionais com NaN retornam `false`.

`!=` retorna `true`.

Todos os targets devem concordar.

### §129 — unwind cross-thread

**Decisão:** frame de exceção por thread.

A cadeia de exceções é thread-scoped.

Worker sem handler interno publica a exceção no handle.

O consumidor relança em `await`, `await_timeout` e `select_any`.

### `roundTo`

**Decisão:** arredondamento decimal aritmético.

```kof
math.roundTo(value, decimals)
```

* mesmo tipo numérico do valor;
* `decimals = 0` arredonda para inteiro;
* negativos arredondam dezenas, centenas etc.;
* modo half-away-from-zero;
* sem locale;
* sem pattern DSL;
* determinístico.

### §179 — tipos builtin UI/media

Tipos builtin declarados são mapeados pelo resolvedor sem quebrar shadowing do usuário.

Uma classe de usuário com o mesmo nome continua vencendo.

### `app.security()`

O modelo mental é inspirado no Spring Security:

* filter chain fixa;
* autenticação por default;
* allow-list explícita para rotas públicas;
* CSRF por default;
* superfície Kof própria.

### §180 — impressão de float/double

Native deve alinhar-se ao comportamento observável de `Double.toString` e `Float.toString` do Java:

* shortest round-trip;
* `Float` mantém sua própria representação;
* notação científica conforme o limiar definido;
* `E` maiúsculo;
* mantissa com parte fracionária.

---

## D-BASELINE — baseline da toolchain

**Data:** 14/09/2026
**Estado:** `IMPLEMENTED`

O baseline de build do repositório sobe de Java 21 para Java 25.

Isso altera a toolchain do repositório, não o runtime mínimo dos programas Kof.

### O que muda

* `pom.xml`: `release=25`;
* CI;
* CodeQL;
* release;
* benchmark;
* Android tooling;
* JDK embutido do `package.sh`;
* documentação de build.

### O que não muda

* `JvmBackend` continua emitindo bytecode V21;
* Android continua com `release="21"`;
* `KofVersion.TOOLING_API=21`;
* programas Kof continuam com runtime mínimo JVM 21+.

---

## D-NULL — nullabilidade e primitivos

**Data:** 15/09/2026
**Estado:** `DECIDED`
**Revisão de:** §125 / SEM048

### Correção histórica

A decisão anterior foi interpretada incorretamente.

O contrato nunca proibiu que `T?` boxed carregasse `null`.

O que é proibido é fabricar `null` em um ponto sem null-safety.

### Contrato

* `Int?`, `Boolean?`, `Double?` e demais `T?` podem carregar `null`;
* `Int`, `Boolean`, `Double` e demais tipos não-nullable não carregam `null`;
* `null` literal em ponto não-nullable é erro de compile-time;
* `T?` boxed não está congelado pela regra 6;
* o comportamento deve ser completado nos targets em lockstep.

### Estado

O trabalho de boxed nullable segue na fila §241/#252/#259/#266.

O catálogo anterior que tratava isso como proibido está corrigido.

---

## D-NULL-INTENT — intenção explícita de nullabilidade

**Data:** 15/09/2026
**Estado:** `DECIDED`
**Revisão de:** opção A do §125

### Contrato

Null não é esperado por default.

Uma declaração sem intenção explícita não pode produzir ou carregar `null`.

A intenção de nullabilidade é expressa pela comparação:

```kof
if (x == null) { ... }
if (x != null) { ... }
```

Isso vale para todos os tipos.

Quando a intenção existe:

* `T?` pode carregar null real;
* `x == null` deve responder corretamente;
* `println(x)` deve imprimir `null`;
* o comportamento deve ser equivalente em todos os targets.

O `null` literal continua proibido em pontos não-nullable.

### Regras de implementação

* `Int?` e demais primitivos nullable devem usar representação boxed real;
* não pode haver dobra silenciosa `null → 0`;
* não pode haver dobra silenciosa `null → false`;
* unbox de null deve ter comportamento definido;
* campos não inicializados devem ter comportamento definido;
* map-miss não pode inventar valor.

### Fila

1. JVM + Script + JS;
2. Native;
3. intenção em declarações não-nullable;
4. auditoria e eliminação dos caminhos silenciosos.

### Proteção de escopo

A implementação do núcleo de intenção está sob responsabilidade da mantenedora.

As lanes não devem atacar o núcleo boxed-nullable de #266/#259 sem nova autorização.

**Autorização (16/09, mantenedora via chat, regra de hierarquia):** a
mantenedora delegou explicitamente a frente D-NULL-INTENT à lane de agentes
(`192.168.100.22`, issue-watcher/compiler) — o requisito de "nova autorização"
acima está cumprido. O núcleo boxed-nullable de #266/#259 está DESTRAVADO para
implementação por esta lane, seguindo a fila do D-NULL-INTENT (1. JVM + Script
+ JS; 2. Native; 3. intenção em declarações não-nullable; 4. auditoria e
eliminação dos caminhos silenciosos). O contrato em si (intenção explícita via
`== null`, `T?` boxed real, sem dobra silenciosa) permanece inalterado.
Registrado aqui pela lane antes/com a implementação, conforme a regra de
hierarquia (diretriz posterior explícita da mantenedora supera restrição
documentada anterior).

As lanes podem atuar em:

* auditoria SEM048/SEM049;
* catálogo de caminhos silenciosos;
* correções que não sobreponham a implementação protegida.

---

## D-PRINT — conversão implícita de Char

**Data:** 15/09/2026
**Estado:** `DECIDED`

### Contrato

`println` imprime `Char` como caractere.

```kof
println('A') // A
```

Concatenação também preserva o caractere:

```kof
"char: " + 'A' // char: A
```

Conversão numérica exige API explícita.

O armazenamento de `Char` em coleções continua sendo contrato separado.

---

## D-NARROW-WHILE — narrowing de fluxo

**Data:** 15/09/2026
**Estado:** `DECIDED`

O narrowing existente em `if` deve ser estendido para:

* condição de `while`;
* receptores de campo de classe;
* escopos estreitados por comparação de null.

Reatribuição dentro do escopo estreitado não altera a nullabilidade da declaração.

O falso-positivo SEM012 do caso #159 deve ser eliminado.

---

## D-ENUM207 — identidade de enum

**Data:** 15/09/2026
**Estado:** `IN_PROGRESS`

A implementação de identidade de enum foi reatribuída à lane bugs-and-gaps.

A mudança semântica:

```kof
Dir.N == "N"
```

deve ser tratada como decisão de contrato, não como simples correção local.

A decisão final sobre a semântica permanece registrada nesta seção antes da alteração do comportamento.

---

## D-VALUE-RECORD — value records / tipos de valor de primeira classe

**Data:** 16/09/2026

**Estado:** `DECIDED`

**Origem:** issue #275 (proposta de feature).

### Contexto

Kof já tem `record` imutável conciso (ex. `record Vec2(Float x, Float y)`),
mas não tem como declarar explicitamente que um agregado definido pelo usuário
tem **semântica de valor e sem identidade de objeto**. Para tipos pequenos
orientados a dados (vetores, coordenadas, cores, intervalos, tokens de parser,
estado de iterador), exigir uma alocação de objeto separada adiciona pressão
de alocação, trabalho de GC, indireção e pior localidade de cache. Depender de
escape analysis do JVM não expressa intenção e não vale nos backends Native/JS.

### Decisão

A sugestão foi **aceita**: adicionar uma entrada na fila de implementação em
`docs/development/future/` para engenharia e desenvolvimento futuros de uma
forma de valor de `record` (`value record`).

### Contrato

* Um `value record` tem o mesmo modelo de dados imutável conciso de um `record`
  Kof existente, mas explicitamente sem identidade de objeto observável.
* Igualdade/hash por campos (já o contrato de `record`).
* Aditivo e retrocompatível: o `record` comum mantém sua semântica existente;
  o código existente continua compilando e rodando (regra 2).
* ABI por alvo é uma decisão de escopo explícita antes do código (R7 honest
  scope): JVM → value/inline class; Native → passagem por valor (struct por
  valor/registradores); JS → objeto congelado comum.
* Fronteira: stdlib do núcleo, não pacote oficial (R1).

### Status

Apenas planejado — **não é desenvolvimento atual**. Sem implementação em
andamento. Lanes não devem abrir esta frente sem nova autorização (regra 6 /
R12: frentes novas não abrem antes de o estágio SYSTEMS fechar).

### Implementação

Entrada de fila adicionada em `docs/development/roadmap.md` §23 (TIER 2) e
esta decisão registrada; engenharia agendada para a fila futura.

### Relações

* `Relacionada:` #275 (issue)

---

## D-DEV-PRIORITY — "Em desenvolvimento" é a prioridade absoluta de toda lane

**Data:** 2026-09-16

**Estado:** `ATIVO` (sobrepõe qualquer ordenação de preferência por lane)

A regra da mantenedora (16/09, chat): **a prioridade total é completar o
roadmap `Em desenvolvimento`** — todo agente, toda lane, todo re-trigger
autônomo escolhe a próxima tarefa desta lista, em ordem, antes de qualquer
outra coisa (outros gaps, outras filas, frentes novas). Catalogar e corrigir
bugs seguem como sempre (o gate de qualidade nunca relaxa), mas a SELEÇÃO DE
TAREFA segue as frentes abaixo.

**As frentes (conforme declaradas pela mantenedora 16/09):**

1. **Standard Library** — contratos em estabilização (a série S:
   `PLAN-STDLIB-EXPANSION`; faces ainda abertas andam na fila por item).
2. **GC auto-collect** — safe-points + mapa de raízes por frame.
3. **Package manager além do MVP** — registry.
4. **Debugger além do MVP JVM** — DAP via stdio já está no JVM; JS
   source maps linha ✅; DWARF Native linha ✅ parcial — variáveis/expressões
   e breakpoints nativos pendentes + ext. VS Code.
5. **KofJS — a plataforma web no browser** — ES Modules via GraalJS; base do
   servidor web ✅ (`HttpServer` + `KofJsWebQueue`); SSE handler-scoped ✅
   16/09 (`7cd69a7b`); residual por feature: ws = **WEB004**, TLS = **WEB002**,
   sse push pós-return/multi-cliente = **WEB003**, path params/keep-alive =
   **WEB001** (linha canônica: `backend-parity.pt_BR.md` "web no Native/JS").
6. **kof.web no Native** — residual por feature: TLS = **WEB002**, path
   params/keep-alive = **WEB001**, ws = **WEB004**, sse = **WEB003**.
7. **kof.db/orm no JS** — **DB001 FECHADO 16/09** (nao-tipado
   `connect/execute/query/close/transaction` na ponte do host GraalJS —
   `3e55df51`+`eb9140cb`); residual `db.query<T>` tipado = `DB002` FECHADO 18/09: bind no guest via `__kof_decode_<T>`
   (arquitetural: JS não emite bytecode JVM no classpath do host) +
   `kof.orm` = `ORM001` (mesma parede; WASM planejado).

**Relação com as outras regras:** esta decisão decide a **ordem**, não o que
é **aceitável** — Q0–Q7, o freeze, a regra 6 e a regra dos três estados
mantêm toda a sua força. Uma frente bloqueada (regra 6, outro dono
`EM CURSO`, ou gate no estilo `§258`) é registrada e o agente pega a PRÓXIMA
frente desta lista — a lista é a fila, não uma sugestão. A regra de prioridade
do `AGENTS.md` (".md solto primeiro") e as camadas do roadmap §23 ficam
subordinadas a esta decisão enquanto a lista de `Em desenvolvimento` tiver
itens abertos.

---

## D-DIAG-EN — tooling e diagnósticos em inglês; docs continuam EN+PT

**Data:** 16/09/2026

**Estado:** `DECIDIDO`

**Origem:** issue #324 (decisão da mantenedora no chat: "aprovo a tradução
completa para inglês"; escopo confirmado 16/09: "tooling da linguagem 100%
em ingles mas as documentações precisam de ingles + pt. porem toda mensagem
de erro da linguagem precisa ser em ingles pro usuario. até pq kof é uma
plataforma universal").

### Contexto

O compiler emite ~144 fragmentos de mensagem visíveis ao usuário em
português (parser, typers, lowerers e runtimes JVM/JS/Native), e ~37
arquivos de teste asserem esses fragmentos. A metade internal-repr do #324
foi corrigida por `130aa213` (`Type.display`); a metade de língua estava
travada aqui como regra 6 até esta decisão.

### Contrato

1. **Toda mensagem visível ao usuário emitida pelo tooling da linguagem é
   em inglês**: diagnósticos do compiler (códigos PARSE/SEM/…), strings de
   erro lançadas pelos runtimes da stdlib (JVM/JS/Native/interpretador),
   saída de CLI/REPL/LSP, logs de build/decompilação e templates de config
   gerados.
2. **Documentação é eixo diferente e continua bilíngue** — cada doc mantém
   o par EN + PT (invariantes do `docs-lang.sh` inalterados).
3. **Códigos nunca mudam** — só o texto da mensagem (SEM048 continua
   SEM048). Testes que casam o *texto* da mensagem migram junto com sua
   unidade; testes que casam o *código* não são tocados.
4. Razão: Kof é plataforma universal — a língua da ferramenta viaja com a
   ferramenta, não com o idioma do usuário.

### Implementação

Fila aberta no DOING (lane issues: tradução em unidades por pacote;
arquivos staged/EM ANDAMENTO de outros agentes ficam de fora de cada unidade
até landarem). Docs que citam textos PT de mensagens (learn/training)
sincronizam como unidade-doc de acompanhamento por pacote traduzido.

### Relacionamentos

- Relacionado: #324 (metade internal-repr fixada por `130aa213`), R6
  (diagnóstico cirúrgico, nunca silencioso), G-01 (freeze da superfície —
  *códigos* congelados; o *texto* deste eixo é definido por esta decisão).

---

## D-DECL-RETURN — o tipo de retorno declarado é lei (#333)

**Data:** 16/09/2026

**Estado:** `DECIDIDO`

**Origem:** issue #333 (decisão da mantenedora no chat, 16/09: "classe
definida como int deve obrigatoriamente retornar int"; "função definida como
int deve obrigatoriamente retornar int e o mesmo vale pras outras tipagem.
função de um tipo declarado deve retornar aquele tipo").

### Contrato

1. **O tipo de retorno declarado é a lei** em todo backend: função/método
   declarado `T` deve retornar valor atribuível a `T`; função declarada
   `void` NÃO pode `return <valor>` — isso é diagnóstico em compile-time
   (nunca re-typing silencioso).
2. **A re-inferência silenciosa `void→T` sai das duas rotas** que hoje
   discordam (raiz medida no thread do #333):
   `SemanticAnalyzer.analyzeMethodBody` (re-tipa o símbolo da classe) e
   `CompilerFunctionLowering.lowerFunctionInner` (re-tipa só o descritor da
   definição — call-sites continuam resolvendo `()V` → o
   `NoSuchMethodError`). Todo call-site resolve contra o tipo **declarado**.
3. **Inferência só onde não há tipo declarado** (`main()`, e formas
   top-level sem anotação como hoje).
4. É **mudança deliberada de contrato** (freeze regra 1): código que hoje
   compila re-tipando um `void` declarado passa a falhar com o diagnóstico
   acima — aprovado pela mantenedora com este registro; entrada no
   CHANGELOG vai junto do commit de implementação.

### Implementação

Dono: lane compiler (issue sweep reivindicado no DOING, 17/09). O texto do
diagnóstico segue D-DIAG-EN (inglês).

### Relacionamentos

- Fecha o bloqueio por regra 6 do #333 (o fix estava catalogado, esperando
  exatamente esta decisão).
- Relacionado: D-NULL-INTENT (família declarado-vs-inferido), bug 62.

---

## D-NOT-JAVA — Kof não é Java/Kotlin: pedido de feature de outra língua NÃO é bug do Kof

**Data:** 2026-09-18

**Estado:** `DECIDIDO`

**Origem:** diretriz da mantenedora, 18/09 (varredura de issues): "ele ta
abrindo issue de java no kof. kof não é java. não tem string builder no kof.
responde todas e as que não forem relativas a kof ou que ele usou treinamento
errado devem ser ignoradas e fechadas. adiciona isso como regra absoluta."

### Contrato

1. Pedido de construto que não existe no Kof porque é **Java/Kotlin/C#
   traduzido** NÃO é bug: o compilador rejeitar é comportamento correto.
   Exemplos medidos na varredura: `StringBuilder`, `val`/`var` top-level,
   keywords `fun`/`val`, `v is Car`, `"""três aspas"""`, `Pair`, `it` implícito
   de lambda, `mutableListOf`, Elvis `?:`, `?.`, intervalo `0..n`, `!!`,
   argumento nomeado, construtor primário estilo Kotlin COM corpo,
   `catch (e: Type)`, `object`, `open`/`override`.
2. Tratamento: **responder uma vez com o idiom do Kof que substitui** (a
   tabela de idioms de `AGENTS.md`) e **FECHAR a issue** como não-procedente.
   Não implementar a feature estrangeira nem "melhorar o diagnóstico" de uma
   rejeição correta.
3. Exceção (trabalho real): o Kof *promete* o construto em
   `training/`/`learn/`/docs e o compilador discorda da própria documentação —
   aí é bug (regra 4 do freeze); e *como* a feature existiria é decisão de
   design reservada à mantenedora (regra 6).
4. A regra fica registrada como **§8 de AGENTS.md** (EN+PT) — absoluta.

### Evidência

- Varredura 18/09, fechadas sob esta regra: #407, #417, #418, #422, #424,
  #425, #406, #410, #414, #416, #411, #412, #419, #420, #421, #404, #364,
  #367, #350, #386, #427 (cada fechamento traz o idiom Kof correto).
- Autoridade no corpus: `AGENTS.md` §"Fake idioms — DO NOT EXIST in Kof",
  `training/anti-patterns/fake-idioms.md`, `learn/15` (`is`/binding não tem
  suporte → `switch`/`as`).

### Relações

- Generaliza o precedente do cluster de açúcar `let/const` (§263) e
  `Int.MAX_VALUE` (bug 99 / SEM050).
- NÃO cobre: bugs cujo reproducer é Kof válido (#403, #336, #313 — ficam e
  foram corrigidos), nem o cluster nullable-primitive (D-NULL-INTENT), nem a
  resolução de tipo JDK sem qualificar (§268).
## D-UI-STYLE — `style` declarativo (UI007)

**Data:** 17/09/2026

**Estado:** `DECIDED`

**Origem:** decisão da mantenedora no chat, respondendo às cinco perguntas
abertas do `KOFUI-AUDIT.md` §UI007 ("UI007 — design proposal"). O item estava
`BLOQUEADO` pela regra 6 (superfície de API); este registro o desbloqueia.

### Contexto

O UI007 pede "declarative `style` (idiomatic CSS), own parser". A superfície
exata é congelamento de API, então não podia ser implementada por julgamento
do agente. O `Style(Int, Int, Int, Int)` existente (background, foreground,
padding, radius — `kof_ui_style_new`) já está entregue nos quatro alvos
(real só no KofJS; no-op documentado nos demais).

### Contrato

1. **Superfície.** `Style("<declarações>")` — um argumento String literal,
   pares `prop: value;` separados por `;`. Produz um valor `kof.ui.Style`,
   consumido por `View(style)` exatamente como a forma de 4 Ints. O
   `Style(4 Ints)` existente fica **intocado** (aditivo, retrocompatível).
2. **Parse no compilador (Q4).** As declarações são parseadas e validadas em
   compile-time; o lowering carrega o texto CSS **normalizado**. Argumento
   não-literal é diagnóstico (nada de parse em runtime).
3. **Cores (Q1).** Aceita hex CSS (`#rgb`, `#rrggbb`, `#rrggbbaa`), nomes de
   cor CSS e os nomes de `Palette` (`red`, `cyan`, …) — a mesma tabela do
   `Palette`. O compilador valida o valor e o mantém no CSS normalizado (o
   browser resolve o nome). A forma de 4 Ints segue sendo o caminho para
   passar um `Color` calculado (a forma String aceita só literal).
4. **Unidades (Q2).** Inteiro nu significa `px`; os sufixos `px`, `%`, `em`
   e `rem` são aceitos.
5. **Propriedades (Q3).** Uma **whitelist tipada**. Propriedade fora da
   whitelist é diagnóstico em compile-time (`SEM075`) — nunca repassada em
   silêncio para `node.style` (R6). Declaração malformada é `SEM076`; valor
   inválido para propriedade conhecida é `SEM077`.
6. **Escopo (Q5).** `setStyle(style)` — recebendo o valor `Style`, exatamente
   como `View(style)` — fica disponível em **todo widget DOM**
   (`KofUi.isDomWidget`), não só `View`, pela família compartilhada
   `kof_ui_widget_set_style` (padrão do UI005, mesma forma de
   `setFont(font)`).

### Invariantes

- **Zero regressão na forma de 4 Ints**: `Style(Int, Int, Int, Int)` mantém
  a semântica exata em todos os alvos.
- **Paridade honesta por alvo**: o style declarativo é real no KofJS e
  **no-op** no JVM/Native/Script, igual ao gap já existente do `Style`
  (UI001). O no-op segue documentado; não vira fallback silencioso.
- **Diagnósticos seguem D-DIAG-EN** (texto da mensagem em inglês; códigos
  estáveis).
- Os códigos novos são **aditivos** e não renumeram nada.

### Alternativas rejeitadas

- **Parse em runtime** (opção do Q4): rejeitada — "own parser" mais validação
  em compile-time (Q3) exigem o compilador; um parse em runtime também
  transformaria o erro de propriedade desconhecida em falha de execução, e
  não em diagnóstico.
- **`setStyle` só em `View`** (opção do Q5): rejeitada pela mantenedora em
  favor da superfície mais ampla (todo widget DOM).

### Implementação

Reivindicado no `DOING.md` (UI007, lane UI/style); roadmap §8 Frontend.
Fatia A = parser no compilador + `Style(String)` + lowering + runtime JS +
testes; fatia B = `setStyle` em todo widget DOM.

### Evidências

`UiStyleCssE2ETest` 10/10 (JVM + Native + Script + JS: happy path, nomes CSS,
unidades, `setStyle` num Label, `SEM075`/`SEM076`/`SEM077`, não-literal,
não-regressão da forma de 4 Ints) + `KofJsBrowserE2ETest` (Chrome headless,
DOM real: `declarativeStyleRendersInRealBrowserDom`,
`setStyleRendersOnAnyDomWidgetInRealBrowser`); suíte completa dos 4 módulos
verde fora do flake pré-existente §252 e dos reds cross §181/§256;
`docs-lang.sh check` 0/0/0.

### Relacionamentos

- Fecha o bloqueio por regra 6 do UI007 (`KOFUI-AUDIT.md` §UI007).
- Relacionado: UI001 (família do no-op silencioso), UI005 (família
  compartilhada `kof_ui_widget_*`), D-DIAG-EN.

---

## D-UNIVERSAL — promoção do `PLAN-UNIVERSAL-PLATFORM` a trabalho corrente (R12 sobreposto)

**Data:** 2026-09-17

**Estado:** `DECIDED`

**Origem:** diretriz da mantenedora no chat, 17/09/2026: "se acabaram os docs
preciso que voce assuma a frente
docs/development/future/PLAN-UNIVERSAL-PLATFORM.pt_BR.md" → respondido
"Promover p/ development/ e implementar".

### Contrato

1. `PLAN-UNIVERSAL-PLATFORM.md` + `.pt_BR.md` **saem de `future/`** e passam a
   ser trabalho corrente em `docs/development/`, estado **EM
   DESENVOLVIMENTO**.
2. O portão de promoção de `docs/development/README.md` §4.3 ("decisão +
   SYSTEMS fechado (R12)") é **sobreposto por esta decisão**: a mantenedora
   autoriza abrir a frente com o SYSTEMS ainda em andamento.
3. O documento deixa de ser "só visão": a regra do próprio cabeçalho
   ("não implementa nada, não move arquivos, não abre frente nova") é
   **revogada e reescrita** no mesmo commit da movimentação.
4. O **primeiro ponto de entrada é o Estágio 1 (consolidação SYSTEMS, §10
   Estágio 1)** e as recomendações executáveis **R1–R12 (§15)** — não o Tier
   6+ (AUTOMATION/INFRA/DATA/…), que mantém sua ordem em `roadmap.md` §23.
5. A visão/design do documento **não** é editada por agentes: só as claims de
   estado são sincronizadas com o código real (regra dos três estados), e cada
   unidade de implementação segue Q0–Q7 como qualquer outra mudança.
6. A semântica congelada do core permanece congelada; toda mudança é aditiva e
   por alvo (R6/R7 valem).

### Invariantes

- O plano **não** vira licença para quebrar o freeze (a regra 6 do `AGENTS.md`
  continua valendo para operadores/precedência/ordem de avaliação).
- `roadmap.md` §23 continua sendo o **plano único ordenado**; este documento
  fornece a arquitetura dos Tiers 6–12.
- Nenhum domínio pesado (`ml`/`bio`/`hpc`) entra na stdlib base (R1).

### Alternativas rejeitadas

- **Respeitar o R12 e fechar o SYSTEMS primeiro** (fazer os itens do TIER 1
  antes de promover) — rejeitada pela mantenedora, que escolheu promover
  agora.
- **Promover só o documento sem abrir implementação** — rejeitada: a diretriz
  é "promover **e implementar**".

### Implementação

- Arquivos movidos: `docs/development/future/PLAN-UNIVERSAL-PLATFORM.md` →
  `docs/development/PLAN-UNIVERSAL-PLATFORM.md` (e o par `.pt_BR.md`).
- Fila: `roadmap.md` §23 TIER 6–12 agora aponta para o novo caminho e registra
  a sobreposição do R12; a primeira unidade executável sai do Estágio 1 /
  R1–R12.
- Acompanhamento: `DOING.md` + `DOING.pt_BR.md`.

### Evidência

- Movimentação + reescrita do cabeçalho + sincronização de referências no
  mesmo commit; `docs-lang.sh check` 0/0/0; `check_500.sh` rc=0.

### Relações

- `Overrides: R12` (a meta-regra "não interromper o presente").
- `Related:` `roadmap.md` §23 (Tiers 0–12), §22 (Plataforma Universal),
  `AGENTS.md` §"Invariantes da plataforma".

---


## D-UI-TOKENS — tokens do design system (Fase 10, pilar 9)

**Data:** 2026-09-18

**Estado:** `DECIDIDO`

**Origem:** autorização de escopo da mantenedora (chat, 18/09 — a resposta
"todas" para as Fases 8–11 do Component Core). A superfície exata (nomes
dos namespaces, nomes dos membros, valores em px) é um congelamento de API
(regra 6); fica travada aqui, seguindo a convenção **D-UI-STYLE Q2** (Int nu
é pixels) e a grade de 8px (consenso Material/Tailwind) para que os tokens
sejam previsíveis e idiomáticos. A *forma* (cinco namespaces de constantes)
é o contrato; os valores específicos da escala podem ser ajustados pela
mantenedora sem mudar a forma da API.

### Contexto

`KOFUI-AUDIT.md`/`architecture.md` §2.1 pilar 9 ("Design system — Theme +
tokens") lista os tokens `Color/Type/Spacing/Border/Radius/Elevation`. Hoje
só existem `Color` (via `Palette`/`Color`) e `Theme`; não há tokens de
Spacing/Border/Radius/Elevation/Typography, então os layouts usam literais
hard-coded (`padding: 16`, `border-radius: 4`) em vez de nomear a intenção
de design.

### Contrato

1. **Superfície.** Cinco namespaces de constantes, cada um um fold em
   compile-time para um `Int` nu (px):

   | Namespace | Membros (→ px) |
   |-----------|----------------|
   | `Spacing` | `xs`=4 `sm`=8 `md`=16 `lg`=24 `xl`=32 |
   | `Radius` | `none`=0 `sm`=2 `md`=4 `lg`=8 `full`=9999 |
   | `Border` | `hairline`=1 `thin`=2 `medium`=4 `thick`=8 |
   | `Elevation` | `none`=0 `sm`=1 `md`=2 `lg`=3 `xl`=4 |
   | `Typography` | `xs`=12 `sm`=14 `md`=16 `lg`=20 `xl`=24 `hero`=32 |

2. **Fold no compilador (frontend compartilhado).** Um `FieldAccessExpr`
   `Namespace.membro` é folding para `KofLoadLiteral(Int)` pelo mesmo
   idiom que o `Palette`. Como o fold vive no frontend compartilhado, os
   quatro targets (JVM/Native/Script/JS) carregam a mesma constante —
   **paridade cross-target por construção**.

3. **R6 — sem 0 silencioso.** Um membro inexistente (`Spacing.huge`) e uma
   chamada de método num namespace (`Spacing.of(4)`) são um diagnóstico de
   compile-time **`SEM078`** (mensagem em inglês, lista os membros válidos).
   O buraco silencioso pré-existente `Palette.nope` é um gap separado e
   catalogado (é superfície da lane compiler; os tokens não o replicam).

4. **Aditivo e retrocompatível.** Nenhum identificador existente é
   sombreado (`Spacing`/`Radius`/`Border`/`Elevation`/`Typography` eram
   não usados). Os tokens compõem com os primitivos existentes
   (`Label.setFontSize(Typography.lg)`, `Style("padding: " + Spacing.md + …)`
   é a forma *literal* do style — os tokens carregam os mesmos valores px).

### Invariantes

- Os cinco namespaces são **apenas constantes** (sem métodos, sem forma
  `var`).
- Valores são `Int` px puros (D-UI-STYLE Q2); `full`=9999 é o idiom CSS de
  "pílula" (totalmente arredondado).
- Diagnósticos seguem D-DIAG-EN. **Emenda (18/09, colisão entre lanes):** os
  códigos desta decisão e da D-UI-STYLE foram re-numerados — a lane compiler
  já havia publicado `SEM073` (aridade do `reduce`, `MemberCallTyper`) e `SEM074`
  (método em primitivo, `SemMethodCallTyper`) no `beta-0.4.0`. Style:
  `SEM073/074/075` → **`SEM075/076/077`**; tokens: `SEM076` → **`SEM078`**.
  Só os rótulos mudaram; nenhuma semântica mudou (as decisões valem como decididas).
- Sem superfície de runtime: o fold é em compile-time, então não há no-op
  por target a documentar (diferente do UI001) — o valor está na IR.

### Alternativas rejeitadas

- **Um namespace `Tokens` com membros aninhados** (`Tokens.Spacing.md`):
  rejeitado — um nível extra de cerimônia sem ganho; o `Spacing.md` plano
  casa com `Palette.red` e a tabela de idiom.
- **Objetos de runtime / tokens `var`**: rejeitado — tokens são constantes
  em compile-time; uma forma de runtime adicionaria um no-op por target
  (família UI001) sem benefício.

### Implementação

Reivindicado no `DOING.md` (Fases 8–11, lane UI/style). `KofUiTokens.java`
(tabela de fold + mensagens) + os seis pontos de toque do `Palette`
(`SemExpressionTyper`×2, `ExpressionTyper`, `ExpressionLowerer`,
`ExpressionMethodCallLowerer`, `MemberCallTyper`).

### Evidência

`UiTokensE2ETest` 7/7 — tabela golden em JVM + Native + Script (mesmo fold
compartilhado → saída idêntica), DOM JS (o valor chega no texto renderizado),
`SEM078` membro inexistente, `SEM078` chamada de método, composição com
`Style`/widget; suíte 4-módulos verde fora do flake pré-existente §252 e dos
reds cross §181/§256; `docs-lang.sh check` 0/0/0.

### Relacionamentos

- Entrega o pilar 9 de `architecture.md` §2.1 (Fase 10).
- Relacionado: D-UI-STYLE (convenção px da Q2), `Palette` (idiom de fold),
  UI001, D-DIAG-EN.

---

## D-UI-APPSTATE — Fase 8: `AppState(initial)` é o store-raiz da aplicação

**Data:** 2026-09-18

**Estado:** `DECIDIDA`

**Contexto:** a `docs/ui/architecture.md` §2.6 define três escopos de
estado. O local (`state`/`text`/`flag` no `Component`) e o `Store`
compartilhado (get/set/subscribe/unsubscribe) já funcionavam; faltava o
escopo **raiz da aplicação** (Fase 8). Ao ligá-lo, o §274 foi medido e
corrigido primeiro: o `Store.unsubscribe` do JS era no-op silencioso
(identidade wrapper-vs-raw), então a perna "cleanup" do §2.6 não tinha
primitivo funcional — ver `known-bugs.md` §274.

**Decisão (contrato mínimo):**
- `AppState(initial)` — um argumento, devolve o store do **escopo da
  aplicação**: um **singleton create-or-get** sobre a máquina do Store. A
  primeira chamada cria com `initial`; as seguintes devolvem o MESMO handle
  e **ignoram** o `initial` (documentado; o valor vive no runtime, um slot
  por processo).
- O handle devolvido é um `Store` — os métodos são exatamente
  `get`/`set`/`subscribe`/`unsubscribe`; nenhuma superfície nova, nenhuma
  máquina `State`/`Signal`.
- O ponto é a alcançabilidade: components chamam `AppState(0)` em qualquer
  lugar em vez de prop-drilling de handle.
- `storesLive()` conta o slot do app-state (probe de leak inalterado).
- O cleanup de inscrições no unmount segue **manual** (`unsubscribe(h)` —
  agora real pelo §274): atribuir inscrições automaticamente a components é
  contrato maior (qual component é o "current" durante um subscribe?) —
  regra 6, não decidido aqui.
- JVM/Native mantêm os no-ops documentados do Store (UI é KofJS —
  backend-parity); o singleton JVM ainda conta uma vez em `storesLive()`.

**Amendável sem quebrar código:** a semântica de ignorar o `initial`
posterior e extensões futuras (p.ex. auto-unsub) são registradas aqui
primeiro; a forma da chamada é congelada.

**Evidência:** `ComponentCoreE2ETest.appStateIsCreateOrGetSingleton` +
`appStateDrivesComponentsWithoutPropDrilling` (VERMELHO pré-feature — SEM015
"Undefined function: 'AppState'"; verde pós-wiring); golden medido por
target (JS `10,10,x=10,x=42,,1`; JVM `0,0,"",1`; Native `0,0,"",0`);
ComponentCore 24/24 + UiE2E 29 + browser 28 + Router 4 + style/tokens 17 +
CoreRegression 102 + CompilerDriver 256 verdes.

- Relacionado: D-UI-STYLE, D-UI-TOKENS, §274, D-BACKEND-SEMANTICS (no-op stores).

---


# 4. Decisões rejeitadas ou substituídas

Esta seção é histórica. Ela não define o comportamento atual.

| ID                  | Decisão anterior                        | Estado       | Substituída por                  |
| ------------------- | --------------------------------------- | ------------ | -------------------------------- |
| §125                | `null` para primitivo dobra em zero     | `SUPERSEDED` | D-NULL                           |
| §125                | `T?` boxed seria proibido               | `SUPERSEDED` | D-NULL                           |
| §125                | `null` silencioso em retorno/atribuição | `SUPERSEDED` | D-NULL-INTENT                    |
| C18 interino        | leituras públicas por default           | `SUPERSEDED` | D-SEC.4                          |
| D-PLATFORM          | plano separado de plataforma            | `CLOSED`     | D-APP + roadmap §23              |
| D-PLAT              | plano separado de conclusão             | `CLOSED`     | roadmap §23 + Definition of Done |
| D-ASM-GATE anterior | inspeção ASM obrigatória sempre         | `SUPERSEDED` | D-ASM-GATE atual                 |

---

# 5. Relação com outros documentos

Este arquivo define **o contrato**.

Os demais documentos definem:

| Documento                | Responsabilidade                        |
| ------------------------ | --------------------------------------- |
| `roadmap.md`             | O que será implementado e em qual ordem |
| `DOING.md`               | O que está sendo executado agora        |
| `AGENTS.md`              | Regras operacionais para agentes        |
| `docs/architecture/`     | Arquitetura detalhada                   |
| `docs/stdlib/`           | Contratos e APIs da stdlib              |
| `docs/backend-parity.md` | Matriz de paridade e gaps               |
| `docs/bugs-and-gaps/`    | Bugs, gaps e regressões                 |
| `training/`              | Material de aprendizagem e corpus       |
| `CHANGELOG`              | Histórico de releases                   |

### Regra de precedência

Em caso de conflito:

1. decisão vigente neste arquivo;
2. documentação normativa específica;
3. testes de conformidade;
4. implementação atual;
5. histórico de chat.

Código e teste que contradizem uma decisão vigente indicam divergência a corrigir, não uma nova decisão automática.

---

# 6. Como registrar uma nova decisão

Use este formato:

```markdown
## D-XXXX — título

**Data:** YYYY-MM-DD
**Estado:** DECIDED | IN_PROGRESS | IMPLEMENTED | PARTIAL |
BLOCKED | SUPERSEDED | REJECTED | CLOSED
**Escopo:** ...

### Contexto

Por que a decisão foi necessária.

### Decisão

Contrato aprovado, sem detalhes de implementação desnecessários.

### Invariantes

O que não pode ser quebrado.

### Alternativas rejeitadas

Somente quando necessário para preservar o raciocínio.

### Implementação

Referência ao roadmap ou DOING.

### Evidência

Testes, commits, matriz ou documentação.

### Relações

- `Supersedes: ...`
- `Depends on: ...`
- `Related: ...`
```

---

## 7. Regra final

**Decisões são permanentes até serem substituídas. Implementações são revisáveis.**

O código pode mudar.

O teste pode mudar.

O roadmap pode mudar.

O contrato só muda por decisão registrada.
