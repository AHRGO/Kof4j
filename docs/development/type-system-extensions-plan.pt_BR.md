[English](type-system-extensions-plan.md) | [Português](type-system-extensions-plan.pt_BR.md)

# Extensões do sistema de tipos — plano incremental (X5 variance + sealed · X6 reflexão de interop)

> **Estado: APROVADO 21/09 — implementando em fatias com prova** (exec = lane
> compiler). A mantenedora votou X5 = opção C e X6 (incremental) e respondeu as
> perguntas de superfície do X5 (`DECISIONS.md` §D-TYPE-VARIANCE,
> §D-INTEROP-REFLECT, §D-X5-SURFACE); o texto abaixo fica como a spec medida.
> Fila: `roadmap.md` §2.8.4/§2.8.5. Regras regentes: regra 6 (a mantenedora
> decide), regra 11 (Lei da Simplicidade em qualquer superfície), `D-KOF-FIRST`.

## Por que spec-first

As duas frentes tocam **núcleo congelado** (o sistema de tipos) ou abrem um
**novo caminho de acesso à estrutura do programa**. A intenção fica registrada e
o fatiamento é proposto aqui; nada é implementado até a mantenedora revisar este
documento. Cada fatia abaixo é aditiva e carrega prova própria (teste/golden por
alvo, regra 5 do freeze). Type-classes seguem **não-objetivo permanente**.

## X5 — variance + sealed types

### Objetivo

- **Sealed**: uma classe/record cujo conjunto de subtipos é **fechado** e
  **conhecido em compile-time**, permitindo ao typer provar que um `switch` é
  **exaustivo** (sem `default`).
- **Variance**: declarar como um parâmetro genérico varia (`out`/`in`) para que
  `List<Dog>` seja atribuível a `List<Animal>` com o compilador provando a
  segurança.

### Não-objetivos

- Sem type-classes, sem tipos de ordem superior, sem sistema de efeitos.
- Sem mudança de representação em runtime: variance é **apagada**; sealed é
  propriedade de **compile-time** (tem de valer em JVM/Native/JS com saída
  idêntica).

### Superfície proposta (PARA REVISÃO — não decidida)

```kof
sealed class Shape
class Circle(Float r) : Shape
class Square(Float s) : Shape

String describe(Shape s) {
    return switch (s) {
        case Circle c -> "circle"
        case Square q -> "square"
    }   // sem default: exaustivo porque Shape é sealed
}

class Box<out T>(T value)   // variance no sítio de declaração (1 char, sem cerimônia)
```

Perguntas abertas para a mantenedora: (a) keyword exata da variance
(`out`/`in` vs nenhuma) — precisa passar a regra 11; (b) `sealed` vale só para
`class`/`record` ou também para interfaces; (c) **projeção no sítio de uso**
(`List<out T>`) entra na v1 ou fica adiada; (d) família de código de diagnóstico
para `switch` não-exaustivo e violação de variance.

### Fatias (cada uma = uma unidade commitável com prova)

| # | Fatia | Escopo | Prova |
|---|-------|--------|-------|
| X5.0 | **spec + células** | superfície **congelada 21/09** (`D-X5-SURFACE` respondeu a–d); escreve células de conformidade `sealed`/`variance` + rascunho em `training/idioms` | ✅ congelada; sem código |
| X5.1 | **declaração `sealed`** | parser + typer: conjunto de subtipos fechado; subtipo fora dele = diagnóstico | ✅ **FEITO 21/09** — keyword contextual (`sealed` antes de `class`/`record`/`interface`) + `SEM080` (subtipo direto fora da unidade de compilação do tipo selado); `SealedTypeE2ETest` 6 testes (JVM/Script rodando, JS/Native compilando, SEM080 red-first, retrocompat de identificador) |
| X5.2 | **`switch` exaustivo** | typer prova que todos os casos de um sujeito sealed estão cobertos; caso faltando = diagnóstico | ✅ **FEITO 21/09** — `SEM081` (subtipo direto faltando, sem `default`); `SealedTypeE2ETest` verde JVM/Script/JS + SEM081 red-first + controle com `default` |
| X5.3 | **variance no sítio de declaração** | `out`/`in` em params genéricos; checagem de compatibilidade de atribuição | ✅ **FEITO 21/09** — parser (`TypeParser`) + `TypeParams.variance` + registro por tipo (classe/record/interface); `TypeChecker.genericArgsCompatible` aplica `out` (covariante)/`in` (contravariante)/invariante (§270) sobre args do MESMO raw; **guarda de solidez SEM082** (posição errada de `out`/`in`) em `VarianceChecks`; `SemExpressionTyper` alinhado ao emit (args do `new` em tipo genérico do módulo); erasure intacta (só compile-time). `TypeVarianceE2ETest` 9 testes (covariante JVM/Script/JS, contravariante, invariante rejeitado, SEM082 ×3, `out` como identificador) |
| X5.3b | **variância em herança** | type-args de `extends`/`implements` com variância divergente | ✅ **FEITO 21/09** — `VarianceChecks.checkHeritage` (chamado de classe/record/interface) + **`SEM083`**: um type-param `out`/`in` passado a um parâmetro de supertipo com variância incompatível (ou invariante) é rejeitado; variância correspondente é permitida. v1 conservadora: só nome simples de type-param como argumento. `TypeVarianceE2ETest` 13 testes (4 de herança: out→in, in→out, out→invariante, correspondente OK) |
| X5.4 | **projeção no sítio de uso** | **na v1** (`List<out T>`) — `D-X5-SURFACE` sobrepôs o padrão "adiada" | ✅ **FEITO 21/09** — parser (`TypeParser.parseTypeRef`/`consumeGenericTypeArgs`) preserva `out`/`in` como palavra num type-arg; `Type.of` → `Type.WildcardType` (out→upper/in→lower); `MemberResolver.declaredTypeUnresolved` valida o BOUND; `CompilerTypes.qualifyDeep` qualifica o bound; `TypeChecker.genericArgsCompatible` aplica a projeção por USO (`List<out Animal>` aceita `List<Dog>`; `List<in Dog>` aceita `List<Animal>`), invariante §270 preservado sem projeção; erasure reusa o `WildcardType` que os 4 alvos já apagam. `UseSiteVarianceE2ETest` 5 testes |
| X5.5 | **paridade + docs** | células de conformidade, matriz de paridade, `training/` + `learn/` | ✅ **FEITO 21/09** — matriz de conformidade lote 4 (`sealedswitch`/`variance`/`useproj`, 4 alvos, `ConformanceMatrixDocTest` verde); linhas EN+PT do `backend-parity`; `training/idioms/classes` + `training/anti-patterns/fake-idioms` (só `permits` segue fake) + drift de `learn/10-inheritance`/`15-pattern-matching` corrigido (`sealed` não é mais "adiado"); nota SG-002 do `lexical-structure` atualizada; roadmap 2.5 supersedido/2.8.4 atualizado; docs-lang 100% |

### Riscos / perguntas abertas

- Solidez da variance com coleções mutáveis (`List<T>.add`) — o ponto de
  `out`/`in` é justamente proibir a atribuição insound; o typer tem de rejeitar.
- Exaustividade interage com `when`/`else` e sujeitos nulos — precisa de regras
  explícitas antes de X5.2.
- A erasure tem de manter a ABI atual byte-idêntica (sem boxing acidental).

## X6 — reflexão de interop

### Objetivo

- Uma visão **read-only** da estrutura de um tipo (nomes/tipos de campo)
  disponível **só na fronteira de interop**, para que dados externos
  (schemas Arrow/Parquet/ML) bindem a records Kof sem mappers manuais.

### Não-objetivos

- **Nunca** fundação da linguagem: sem metaprogramação em runtime, sem despacho
  dinâmico, sem annotations-como-framework, sem reflexão no fluxo de usuário.
- Sem caminho de escrita; sem carregamento dinâmico tipo `Class.forName` na
  linguagem.

### Superfície (RESOLVIDA 21/09 — `D-INTEROP-REFLECT`)

- **`interop.schema(R)`** — **intrínseco de compile-time** no namespace
  `interop`, `R` um tipo `record`. Resolve para uma **`List<Field>` imutável**,
  com **`record Field(String name, String type)`** fornecido pelo compilador;
  entradas na ordem dos componentes do record.
- **Zero reflexão em runtime** (o compilador conhece a estrutura) → mesma saída
  em todos os alvos, então **não há gap `REF001`**. Só na fronteira, nunca
  fundação da linguagem.

### Fatias

| # | Fatia | Escopo | Prova |
|---|-------|--------|-------|
| X6.0 | **spec** | escopo, superfície, postura por alvo; confirma "só fronteira de interop" | ✅ **FEITO 21/09** — superfície congelada em `D-INTEROP-REFLECT`: `interop.schema(R)` intrínseco de compile-time → `List<Field>` (`record Field(String, String)`), zero reflexão em runtime, todos os alvos, só na fronteira |
| X6.1 | **intrínseco + dobra** | reconhecer `interop.schema(R)` no typer; dobrar para um literal `List<Field>` imutável em compile-time (frontend, logo todos os alvos) | ✅ **FEITO 22/09** — `import kof.interop` injeta o host `record Field`; typer → `List<Field>`; lowerer dobra para as ops de `listOf(Field("n","t"),…)` (sem reflexão em runtime, sem código por backend). `InteropSchemaE2ETest` 7/7 (ordem JVM/Script/JS, lista imutável, 1 campo, genérico, compila NATIVE, `INTEROP001` x2) |
| X6.2 | **alvos** | a dobra é no frontend → sem `REF001`; em vez disso, diagnóstico honesto para `R` não-record / desconhecido (R6) | diagnóstico pinado para argumento inválido |
| X6.3 | **paridade + docs** | E2E de binding (forma Arrow/Parquet), matriz de paridade, `training/`/`learn/` | suíte verde; docs-lang 100% |

### Notas de implementação (X6.1, desenho)

- O compilador conhece os componentes de `R`, então `interop.schema(R)` dobra
  no **lowerer** para exatamente as ops que o `listOf(Field("n","t"),…)`
  equivalente emitiria (`kof_list_new` + `kof_list_add`; o ctor do record pelo
  caminho normal) — **sem código por backend**, logo mesma saída em todos os
  alvos.
- `record Field(String name, String type)` é um record de host injection
  clássico (mesmo mecanismo de `kof.supervisor`/`kof.workflow`), então acesso a
  membro (`f.name`/`f.type`) e codegen funcionam sem mudança; a tipagem devolve
  `List<Field>`.
- **Sub-escolha RESOLVIDA (mantenedora 21/09):** `import kof.interop`
  explícito (injeta no import — o mecanismo de host estabelecido, sem scanner
  frágil). Implementada no X6.1.

### Riscos / perguntas abertas

- Tentação de virar reflexão geral — a cerca "só fronteira de interop" tem de
  ser imposta e documentada.
- Performance/ABI: a reflexão não pode vazar para caminhos quentes nem mudar o
  layout do record.

## Sequenciamento / dependências

`X5.0 e X6.0 (specs) → revisão da mantenedora → X5.1–X5.5 e X6.1–X6.3`.
Ambas não dependem de nada do caminho crítico atual e **não** podem preemptar o
Estágio 1 (SYSTEMS) nem o trabalho R3/R4; são fila, não trabalho atual.

## Evidência

- Decisões: `DECISIONS.md` §D-TYPE-VARIANCE, §D-INTEROP-REFLECT (21/09/2026),
  §D-X5-SURFACE (congelamento da superfície v1 da X5: `out`/`in`, `sealed`
  class/record + interface, projeção no sítio de uso na v1, `SEM0xx`, 21/09/2026).
- Fila: `roadmap.md` §2.8.4/§2.8.5; `IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  linhas X5/X6.
- Não-objetivos: `docs/philosophy.md`, `training/anti-patterns/fake-idioms.md`.

## Decisões de superfície (RESOLVIDAS 21/09/2026)

O `DECISIONS.md` §D-X5-SURFACE fixa as perguntas acima: (a) `out`/`in`;
(b) `class`/`record` + `interface`; (c) projeção use-site **no v1** (a X5.4 não é
mais adiada); (d) `SEM0xx`. O cabeçalho "FOR REVIEW — not decided" acima é
histórico; a superfície está congelada.
