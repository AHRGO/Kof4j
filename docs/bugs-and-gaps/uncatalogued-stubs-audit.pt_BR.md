[English](uncatalogued-stubs-audit.md) | [Português](uncatalogued-stubs-audit.pt_BR.md)

# Stubs não catalogados / desenvolvimento incompleto — ledger de revisão

> **Registro vivo da frente de REVISÃO** (sessão 9094, branch `beta-0.5.0`).
> **Não** é um ledger de bugs: um achado *confirmado* sobe para
> `known-bugs.md` (`§NNN`, EN+PT) com repro; um *candidato* fica aqui até ser
> medido. O inventário mecânico é reprodutível: `scripts/audit-stubs.sh`
> (somente leitura).

**Início:** 21/09/2026 · **Base medida:** `16340f62` · **Escopo:**
`*/src/main` (761 `.java`) + os 12 arquivos `.kf` host em
`kof-compiler/src/main/resources/dev/kof/`.

## Por que esta frente existe

A mantenedora pediu uma varredura do **código inteiro** por stubs /
desenvolvimento parcial que **não** esteja documentado. O risco que esta frente
ataca é exatamente o silencioso (R6): um caminho que *finge* funcionar — um
`return null`/`return 0` de fachada, um `default` que engole, uma exceção
engolida — e para o qual nenhuma entrada de ledger aponta.

## Método (reprodutível)

```bash
bash scripts/audit-stubs.sh /home/mel/Kof4j > /tmp/slice1-report.txt
```

**Aviso de ruído (medido, não presumido):** os comentários do Kof são em
português, onde `todo` = *todos* e `stub honesto` = **recusa deliberada R6**
(documentada no próprio código, ex.: `NativeMethodEmitter:387`,
`KofJsDbBridge:103`). Um match cru é, portanto, um **candidato**, nunca um
achado — todo candidato é triado à mão e comparado com `docs/bugs-and-gaps/*`
antes de ser catalogado. Esta frente explicitamente **não** recataloga as
recusas honestas legítimas.

## Fatia 1 — resultados medidos (21/09, base `16340f62`)

| Sinal | Cru | Triado |
|---|---|---|
| `TODO/FIXME/XXX/HACK` case-sensitive (`.java`) | 12 | **0 real** — os 12 são "todo" português (every) ou notas de fix com `§` |
| `UnsupportedOperationException` | 3 | **0 stubs** — 1 é string numa lista; 2 são falhas duras honestas (`NativeMethodEmitter:387` "R6: never silent"; `KofJsDbBridge:103` "DB002") |
| `catch` vazio (`.java`) | 31 | **30 benignos** (cleanup de arquivo / probe de ambiente / fallback reflexivo) + **1 candidato** (abaixo) |
| `@Disabled` / `@Ignore` (testes) | 0 | 0 — nenhum teste desabilitado escondendo trabalho pendente |
| `assumeTrue` / `Assumptions.` | 216 | todos com **motivo honesto de ambiente** (toolchain C, MySQL, node, qemu, H2) — é contrato da casa, não máscara |

**Conclusão da fatia 1:** o código **não tem `TODO` abandonado nem teste
desabilitado**. Seus "stubs" são a recusa R6 projetada (diagnóstico honesto),
que é comportamento correto, não dívida. O valor restante da auditoria é,
portanto, **semântico** (paridade / tratamento parcial), não greppável.

## Candidatos (não verificados — ainda NÃO catalogados como bug)

### UI-JS-1 — handlers de evento de UI no JS engolem exceção em silêncio

- `kof-compiler/src/main/java/dev/kof/compiler/js/JsRuntimeUiEvents.java:62`
  (e `:78`, `:153`, `:173`): `try { … fn(kofEv) } catch (e) {}` — um listener
  que lança é **descartado em silêncio**, sem `console.error`.
- Contraste: `JsRuntimeUiComponents.java:226` faz explicitamente
  `console.error("[kof] " + where + ": " + detail)` e relança — o runtime de UI
  do JS tem duas políticas diferentes para o mesmo caso "callback do usuário
  falhou".
- **Estado: FECHADO (fatia 4) — por design, não é bug.** Medição: no JVM o
  runtime `kof.ui` é um **no-op** documentado (`JvmRuntimeUi.kof_ui_widget_on:146`
  e `kof_ui_component_on:180` têm corpo vazio), e as regras de
  renderização/estado de `kof.ui` são a **única exceção nomeada** à paridade
  (`D-UI-SCOPE`, 18/09 — `docs/backend-parity.md:225`): autoradas/efetivas no
  KofJS, nunca no JVM/Native. Sem política JVM/Script de que divergir, o
  `catch (e) {}` do JS é o isolamento de erro de handler do motor de UI, não um
  stub não documentado. **Nenhum `§NNN` aberto.**

## Fatia 2 — invariante de paridade + 1 achado de drift de doc (21/09)

**Invariante checada (grep + leitura de todos os call-sites):** para todo
`Kof*.supportedOn(...) == false` há um `gapCode(...)` **emitido no mesmo ponto
de lowering** — ex.: `ExpressionStaticCallLowerer:139/146` (db),
`ExpressionMethodCallLowerer:411/413` (std/buffer/rng/math), `:429/432`
(observability), `ExpressionTimeCallLowerer:19/27` e `:53/60`,
`ExpressionDbCallLowerer:20/28`, `ExpressionLogCallLowerer:19/27`,
`ExpressionOrmCallLowerer:35` + `CompilerOrmSupport:77/83`,
`ExpressionSchedulerCallLowerer:19/21`. `KofGpu` não tem método `gapCode()`,
mas emite `GPU001` inline em `ExpressionMethodCallLowerer:356-359`.
**Nenhum gap silencioso de paridade encontrado** na superfície `Kof*`.

**DRIFT-NET-1 (achado, corrigido, só comentário):** `KofNet.java` anunciava o
oposto da realidade — o comentário de classe dizia *"riscv/aarch ainda gated em
compile-time"* e `supportedOn` dizia *"byte-scan nativo pendente"*, enquanto
`conformance-matrix.md` §net registra **NET001 CLOSED 09/09** e
`NativeRiscvAsmRtB24` implementa `kof_net_queryEncode`/`queryDecode` (fatia
B24, aarch via tradutor; prova `KofNetTest.netOnCrossArch`). O `supportedOn`
devolve `true` para **todos** os alvos (correto), tornando
`gapCode()="NET001"` **vestigial**. Corrigi os dois comentários stale e anotei
o vestigial — **sem mudança de comportamento**, logo o teste da Q1 não se
aplica (um comentário não regride); prova = `mvn -o -pl kof-compiler -am
compile` rc=0.

## Fatia 3 — mais dois comentários stale "gated/pendente" (21/09)

Grep por `ainda não|pendente|gated|not yet|por ora` na stdlib `Kof*` e
cruzamento com a matriz/código. Ambos são o mesmo drift "comentário
append-only" (afirmação stale deixada acima da própria correção) — correções
só de comentário, sem comportamento:

- **DRIFT-UUID-1** `KofUuid.supportedOn` dizia *"Os 3 nativos ainda não têm
  fatia asm — UUID001 os bloqueia"*, contradizendo as linhas seguintes
  (*"S3b.2 FEITO nos 5 alvos 10/09 … Gate removido"*) e a matriz (**UUID001
  fechado**, merge beta→main 10/09; riscv B25b + aarch tradutor). Frase stale
  removida.
- **DRIFT-STRN-1** o javadoc de `KofStrings` dizia que os conversores de
  palavra (joinWords) ficam *"gated … com o bug 59 aberto"*, mas **STRN001
  FECHADO 09/09** (riscv B15 + aarch tradutor) e `supportedOn` devolve `true`.
  Reescrito.

Prova dos dois: `mvn -o -pl kof-compiler -am compile` rc=0 (só comentário ⇒ Q1
não se aplica).

## Fatia 2b — invariante travada como teste (21/09)

Novo `kof-compiler/src/test/java/dev/kof/compiler/StdParityGapAuditTest.java`
(**13/13 verde**) transforma a matriz de suporte auditada num catraca: para
cada namespace com gate afirma o conjunto exato de alvos `unsupported` e o gap
code exato (buffer FFI001/FFI002, db DB001, log LOG001, orm ORM001, rng RNG001,
gpu, tetris EGG001, scheduler SCHED001/CRON001, math.pow MATH001, observability
OBS003, time.tzOffsetSeconds TIME003, security.sha512 SECN003), além dos
namespaces always-true permanecerem sem gate. Um gate novo num namespace
always-true agora quebra o teste de propósito — a matriz é lei e tem de ser
atualizada junto.

O teste **corrigiu um palpite meu**: `security.sha512` é gated não só em
riscv/aarch, mas também em **ANDROID e SCRIPT** (`JVM || JS || isNative`), logo
o gap code `SECN003` dispara em quatro alvos. Medido, não lembrado (Q3).

Prova: `mvn -o -pl kof-compiler -am -Dtest=StdParityGapAuditTest test` → 13/13.

## Fatia 4 — UI-JS-1 fechado + duas varreduras negativas (21/09)

- **UI-JS-1 fechado por medição** (ver o candidato acima): o `kof.ui` no JVM é
  no-op documentado e `D-UI-SCOPE` faz da UI a única exceção nomeada de
  paridade — não há stub não documentado nem divergência a corrigir.
- **Arquivos `.kf` host (12) — todos declarados:** `makealive-db-host.native.kf`,
  `workflow-ckpt-host.native.kf` e `workflow-sched-host.native.kf` carregam
  **stubs de falha alta citando o gap** (ORM001/CRON001, R6), e o resto não tem
  marcador (`todo` = *todos* em português). **0 stubs não documentados.**
- **Varredura de fachada Q7:** 133 `default -> null` nos dispatchers estilo
  `staticMethod` são o idioma da casa para *"não é membro deste namespace"* — o
  typer/lowerer transforma o null num diagnóstico (o caminho R6), não numa
  fachada silenciosa; os 4 `catch { return null }` são fallback reflexivo/parse
  (`JvmRuntimeCore:196`, `JvmTimeRuntime:270`, `CmdEditor:276`,
  `KofScriptExecutor:162`). **0 fachadas silenciosas encontradas.**

## Próximas passadas (planejadas — ainda não executadas)

1. **Checagem de assimetria de paridade** — **FEITA (fatia 2b,
   `StdParityGapAuditTest` 13/13)**. Próximo: estendê-la aos gates por função
   do `KofSecurity` (chacha/auth/cookie) e do `KofTime` além dos casos
   representativos.
2. **Varredura de fachada Q7** — **FEITA (fatia 4, negativa): 0 fachadas silenciosas.**
3. **Deriva doc/código:** features marcadas como prontas em `docs/` cujo código
   é parcial (cruzar as matrizes de paridade e o tracker contra o código). —
   **parcialmente feita** (fatias 2 e 3 acharam 3 drifts); continuar sobre os
   gates por função.
4. **Arquivos `.kf` host** — **FEITA (fatia 4, negativa): 0 stubs não documentados.**
5. **Fechar UI-JS-1** — **FEITA (fatia 4): fechado por medição, por design.**

## Proveniência

- Script de inventário: `scripts/audit-stubs.sh` (somente leitura, idempotente) —
  agora guardado por um teste RED-first de fixture
  `scripts/tests/audit-stubs-test.sh` (um `TODO`/`catch` vazio/`@Disabled`
  plantado precisa ser achado; fixture limpa dá 0; a árvore não pode mudar; raiz
  sem `*/src/main` recusa), ligado em `scripts/tests/run-agent-tests.sh` (lane
  docs/.18, 21/09).
- Base: `16340f62`; contagens re-medidas nesta base.
- Artefatos de recon prévios: `/tmp/opencode/audit/{markers,candidates,hard}.txt`.
