# Extensões do sistema de tipos — plano incremental (X5 variance + sealed · X6 reflexão de interop)

> **Estado: RASCUNHO para revisão da mantenedora — spec-first, SEM CÓDIGO**
> (regra 6). Uma mudança de núcleo do sistema de tipos e uma superfície de
> reflexão só pousam depois deste plano revisado. Autoridade: `DECISIONS.md`
> §D-TYPE-VARIANCE (X5 = opção C, aberta) e §D-INTEROP-REFLECT (X6 = aberta,
> plano incremental exigido). Fila: `roadmap.md` §2.8.4/§2.8.5. Regras
> regentes: regra 6 (a mantenedora decide), regra 11 (Lei da Simplicidade em
> qualquer superfície), `D-KOF-FIRST`.

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
| X5.0 | **spec + células** | congela a superfície (perguntas a–d); escreve células de conformidade `sealed`/`variance` + rascunho em `training/idioms` | revisão; sem código |
| X5.1 | **declaração `sealed`** | parser + typer: conjunto de subtipos fechado; subtipo fora dele = diagnóstico | teste de typer red-first; compila em JVM/Native/JS |
| X5.2 | **`switch` exaustivo** | typer prova que todos os casos de um sujeito sealed estão cobertos; caso faltando = diagnóstico | red-first (falta caso → falha), green (completo); E2E cross-target |
| X5.3 | **variance no sítio de declaração** | `out`/`in` em params genéricos; checagem de compatibilidade de atribuição | testes de atribuibilidade; paridade de bytes da erasure entre alvos |
| X5.4 | **projeção no sítio de uso** | decidir na revisão (padrão: **adiada**) | — |
| X5.5 | **paridade + docs** | células de conformidade, matriz de paridade, `training/` + `learn/` | suíte verde; docs-lang 100% |

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

### Superfície proposta (PARA REVISÃO)

- Um membro de um namespace de interop (ex.: `interop.schema(record)`) que
  devolve uma lista **imutável** de descritores de campo, usável só pela camada
  de binding.
- Perguntas abertas: nome/forma exatos; se é exposto como método ou intrínseco
  de compile-time; qual alvo lidera (JVM primeiro, por R7).

### Fatias

| # | Fatia | Escopo | Prova |
|---|-------|--------|-------|
| X6.0 | **spec** | escopo, superfície, postura por alvo; confirma "só fronteira de interop" | revisão; sem código |
| X6.1 | **JVM** | leitura estrutural no host (`java.lang.reflect` por trás da camada FFI) | E2E: schema de um record descoberto e casado a um golden |
| X6.2 | **Native/JS** | gap honesto `REF001` (ou mínimo) — nunca stub silencioso (R6) | diagnóstico pinado em alvos não suportados |
| X6.3 | **paridade + docs** | E2E de binding (forma Arrow/Parquet), matriz de paridade, `training/`/`learn/` | suíte verde; docs-lang 100% |

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

- Decisões: `DECISIONS.md` §D-TYPE-VARIANCE, §D-INTEROP-REFLECT (21/09/2026).
- Fila: `roadmap.md` §2.8.4/§2.8.5; `IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  linhas X5/X6.
- Não-objetivos: `docs/philosophy.md`, `training/anti-patterns/fake-idioms.md`.
