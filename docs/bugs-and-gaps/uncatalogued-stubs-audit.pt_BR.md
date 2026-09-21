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
- **Estado:** não verificado. Para subir a `§`, medir a política do JVM/Script
  para um callback de UI que lança: se eles propagam (ou logam) e o JS
  descarta em silêncio, é divergência real R6/paridade e ganha `§NNN` + repro.
  **Próxima passada:** localizar o dispatch de callback de UI no JVM/Script,
  montar um handler que lança em cada alvo e comparar a saída observável.

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

## Próximas passadas (planejadas — ainda não executadas)

1. **Checagem de assimetria de paridade** (maior rendimento): para cada
   `Kof*.supportedOn(function, target)` × `Target`, afirmar que o caminho não
   suportado carrega um `gapCode()` não-nulo. Um caminho suportado num alvo e
   ausente noutro **sem gap code é a incompletude silenciosa** que esta frente
   caça. Assinaturas mistas (`supportedOn(Target)` de namespace inteiro vs
   `supportedOn(String, Target)` por função) ⇒ implementada como teste JUnit
   (`StdParityGapAuditTest`), para que a prova seja verde/vermelho, não grep.
2. **Varredura de fachada Q7:** métodos que devolvem `null`/`0`/`""` num
   caminho que deveria computar, ramos `default:` que escondem caso não
   tratado, emissores que devolvem vazio sem diagnóstico.
3. **Deriva doc/código:** features marcadas como prontas em `docs/` cujo código
   é parcial (cruzar as matrizes de paridade e o tracker contra o código).
4. **Arquivos `.kf` host:** os 12 sob `kof-compiler/src/main/resources/dev/kof/`
   (hosts supervisor/workflow/makealive/android) — triar seus marcadores
   explícitos de "STUB" (alguns são declarados por design).
5. **Fechar UI-JS-1** por medição.

## Proveniência

- Script de inventário: `scripts/audit-stubs.sh` (somente leitura, idempotente).
- Base: `16340f62`; contagens re-medidas nesta base.
- Artefatos de recon prévios: `/tmp/opencode/audit/{markers,candidates,hard}.txt`.
