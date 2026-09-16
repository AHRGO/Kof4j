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
   servidor web ✅ (`HttpServer` + `KofJsWebQueue`); residual
   ws/sse/TLS/path-params = **WEB001**.
6. **kof.web no Native** — residual **WEB002**: TLS, path params,
   keep-alive, ws/sse.
7. **kof.db/orm no JS** — **DB001 FECHADO 16/09** (nao-tipado
   `connect/execute/query/close/transaction` na ponte do host GraalJS —
   `3e55df51`+`eb9140cb`); residual: `db.query<T>` tipado = `DB002`
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
