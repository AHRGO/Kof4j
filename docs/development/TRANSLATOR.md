# TRANSLATOR.md — Kof Translator (plano → EM DESENVOLVIMENTO)

> **Dono:** 192.168.100.22 (reivindicado 13/09 ~10:05 — órfão: sem dono com IP
> no header; último código há 6 dias `84c48041`; regra dono-sem-IP=órfão da
> mantenedora).

**Status:** EM DESENVOLVIMENTO (caiu de `future/` em 12/09 — Fase F
implementada: `Translate.java` + `TranslateLexer`/`TranslateExpr`;
prova: `TranslateTest` 26/26 (output compila e roda; +do-while +switch
+try/catch/throw +arrays +cast/instanceof +throws +generics +constructor
+enum-body/multi-decl +interface-extends +bitwise/shift +parênteses
+tipos qualificados +assert 13/09). Subconjunto Java ampliado ainda pendente)
**Data:** 22 de agosto de 2026

---

## 1. Objetivo

Migrar código-fonte para Kof. Primeiro alvo: **Java → Kof**.

```text
Java Source
     ↓
Java Parser
     ↓
Java AST
     ↓
Java Semantic Model
     ↓
Translation IR
     ↓
Kof AST
     ↓
Kof Source
```

## 2. O que NÃO é

O translator **não funciona por substituição textual**:

```text
public → ...
class  → ...
```

NÃO fazer isso. A ferramenta deve compreender a **estrutura semântica** do
programa: tipos, herança, overloads, fluxo, exceções — e produzir Kof
idiomático, não uma transliteração.

## 3. Suporte Progressivo Planejado

- classes;
- interfaces;
- inheritance;
- generics;
- overloads;
- constructors;
- exceptions;
- annotations;
- records;
- enums;
- lambdas;
- nested classes;
- anonymous classes;
- static initialization;
- access modifiers;
- Java standard library;
- chamadas de bibliotecas externas.

## 4. Regras de Tradução Conceituais

| Padrão Java | Tradução Kof |
|---|---|
| Classe com getters/setters | Campo público |
| Classe de dados imutável | `record` |
| Utility class com métodos static | Função top-level |
| `.equals()` em strings | `==` |
| `StringBuilder` | `+` |
| Factory estática trivial | Construtor |
| `Optional` | Exceção (até `Option<T>` existir) |
| Service/Repository/Controller | Função top-level ou classe direta |

Estas regras são **conceituais** — a implementação deve derivá-las da
semântica do programa, nunca aplicá-las cegamente.

## 5. Confiança

O translator registra, por construção traduzida, a origem:

```text
Java constructo → Kof constructo
```

com nível de confiança. Construções Java sem equivalente Kof direto são
marcadas para **revisão manual**, não silenciosamente alteradas.

## 6. Fase de Implementação

A Fase F do roadmap da plataforma (`LEGACY_MIGRATION.md`).

Antes de implementar: protótipo pequeno com um subconjunto de Java
(classes, campos, métodos, if/while, strings) validado contra testes
diferenciais.

> **Estado (13/09, dono = 192.168.100.22): do-while traduzido.** `do { ... }
> while (c)` Java → `do { ... } while (c)` Kof (idiom 1:1,
> `training/idioms/control-flow.md`). Causa: `do` era keyword do
> `TranslateLexer` mas nenhum statement a consumia → `parseExprOrDecl`
> falhava com `expected ';' but found '{'`. Fix: ramo `do` em
> `Translate.parseStatement` (corpo em bloco usa o conteúdo cru, sem chaves
> duplas). Prova: `TranslateTest.doWhileTranslates` (traduz + compila no JVM
> + roda `0/1/2`). `Translate.java` 412 ≤500; `TranslateTest` 10/10.
>
> **Estado (13/09 ~10:45, dono = 192.168.100.22): switch-statement traduzido.**
> `switch (x) { case 1: ...; break; default: ... }` Java → `switch (x) {
> case 1: ... default: ... }` Kof statement (`:`, `training/idioms/control-flow.md`).
> Causa: `switch`/`case`/`break`/`default` eram keywords sem ramo no statement
> parser → `expected ';' but found '('`. Fix: `parseSwitch` (corpo de case =
> statements até próximo `case`/`default`/`}`; `break;` dropado — em Kof é
> opcional/sem fallthrough; labels múltiplos `case "a", "b":` → cases
> separados; arrow `case 3 ->` normalizado p/ `:`). Prova:
> `TranslateTest.switchStatementTranslates` (traduz + compila JVM + roda
> `one/ab`; Int e String). `Translate.java` 476 ≤500; `TranslateTest` 11/11.
>
> **Estado (13/09 ~11:00, dono = 192.168.100.22): try/catch/finally + throw
> + bare-call traduzidos.** Gap em 3 frentes, todas do mesmo cluster "corpo de
> método de verdade":
> 1. `try { ... } catch (RuntimeException e) { ... } finally { ... }` Java →
>    `try { ... } catch (String e) { ... } finally { ... }` Kof
>    (`training/idioms/errors.md`: exceções são Strings). Causa: `try`/`catch`/
>    `finally`/`throw` eram keywords sem ramo no statement parser. Fix:
>    `parseTry` (multi-catch `catch (A | B e)` → catch único; bloco vazio →
>    `{}`).
> 2. `throw new RuntimeException(msg)` → `throw msg` (exceção-String).
> 3. **Bug latente descoberto:** chamada sem receiver (`boom("x");`) não tinha
>    ramo em `parsePostfix` → `expected ';' but found '('` — o parser só
>    tratava `recv.metodo(...)`. Corrigido (bare-call), senão *qualquer* corpo
>    de try real quebrava.
> Prova: `TranslateTest.tryCatchFinallyTranslates` (traduz + compila JVM +
> roda `caught/done/t2`). **Split feito no mesmo dia:** `Translate.java`
> 526 → **255** + `TranslateStatements.java` 287 (statements extraídos p/ o
> gate ≤500 — dívida tolerada zerada, `check_500` sem aviso de Translate);
> `TranslateTest` 12/12.
>
> **Estado (13/09 ~11:45, dono = 192.168.100.22): arrays + declarações com
> `[]`/generics.** Gap: `int[] xs = new int[3]` → `expected ']' but found
> 'xs'` (o decl-parser não pulava `[]` após o tipo). **Bug latente grave
> achado no caminho:** `new int[3]` gerava `new Int[]]` (o `parseNew`
> consumia `[` e o PRIMEIRO token da dimensão antes do `parseExpr`) → Kof
> inválido (`PARSE041`); `new int[]` local nem chegava ao compilador porque
> o decl-parser já falhava. Fix: `parseExprOrDecl` com lookahead
> `isLocalDeclAhead` (generics `List<String> xs`, `Type[] name`, `Type
> name[]`); `parseNew` corrigido (size via `parseExpr`, `new T[]{...}` →
> gap explícito R6). `array initializer {...}` → **gap honesto** (não há
> literal `{...}` em Kof — `new Int[n]` + atribuições ou `listOf`).
> Prova: `TranslateTest.arrayDeclarationTranslates` (traduz + compila JVM +
> roda `10/0/0`; C-style for preservado) +
> `TranslateTest.arrayInitializerIsHonestGap` (diagnóstico explícito, R6).
> `TranslateStatements` 326 ≤500; `TranslateTest` 14/14.
>
> **Estado (13/09 ~12:00, dono = 192.168.100.22): cast + instanceof.**
> `(String) o` → `o as String` (conversão Kof) e `o instanceof String`
> preservado (Kof tem nativo, `training/language/overview.md`). Causa:
> `instanceof` não era operador de `parseRel`; `(Type)` era lido como
> parêntese de agrupamento → `expected ';' but found 'o'` /
> `expected ')' but found 'instanceof'`. Fix: ramo `instanceof` em
> `parseRel` + `isCastAhead`/`parseCast` em `parsePrimary` (lookahead
> `(Type[...]) expr`, inclusive genéricos e arrays). Prova:
> `TranslateTest.castAndInstanceofTranslate` (traduz + compila JVM + roda
> `x/is-str/3`; cast de referência e primitivo). `TranslateExpr` 387 ≤500;
> `TranslateTest` 15/15.
>
> **Estado (13/09 ~12:15, dono = 192.168.100.22): cláusula `throws`
> descartada.** `static void f() throws IOException` → `void f()` (Kof não
> declara `throws`; exceções são Strings, sempre propagáveis). Causa: o
> parser de membro esperava `{` logo após os parâmetros →
> `expected '{' but found 'throws'`. Fix: consumir `throws` + lista até
> `{`/`;` em `parseMember`. Prova: `TranslateTest.throwsClauseIsDropped`
> (traduz + compila JVM + roda `caught`). `TranslateTest` 16/16.
>
> **Estado (13/09 ~12:30, dono = 192.168.100.22): gaps honestos varargs e
> tipo aninhado.** Dois constructos Java **sem** equivalente Kof viram
> diagnóstico explícito (R6: nunca silencioso, nunca parse error confuso):
> `T...` varargs (Kof só tem builtins variádicos — `setOf`/`listOf`; função
> de usuário não) e tipo aninhado (`class`/`interface`/`record`/`enum`
> dentro de classe — SEM042 exige top level). Causa: `parseParams` lia `...`
> como erro de sintaxe; `parseMember` caía em `expected class/...`.
> Prova: `TranslateTest.varargsAndNestedTypeAreHonestGaps`. `TranslateExpr`
> 399 ≤500; `Translate.java` 270 ≤500; `TranslateTest` 17/17.
>
> **Estado (13/09 ~13:00, dono = 192.168.100.22): generics + construtores.**
> `class Box<T>`/`record Pair<A,B>`/`<T> T id(T x)` → `Box<T>`/`Pair<A,B>`/
> `T id<T>(T x)` (Kof tem generics); `new Box<Integer>(5)` → `Box(5)` (Kof
> infere). **Bug latente grave:** construtor Java (`public User(...)`) tinha
> o nome da classe lido como tipo de retorno → o ramo de campo escaneava até
> um `;` inexistente e **travava em loop infinito** (EOF) em `kof translate`;
> e o corpo do construtor era descartado (`constructor(...) {}`). Fix:
> detectar `ClassName(` antes de `parseType` → `emitConstructor` com corpo.
> Bounds `<T extends X>` → gap honesto (R6). Prova:
> `TranslateTest.constructorTranslatesWithBody` (traduz + compila JVM + roda
> `Hello Mel/26`) + `TranslateTest.genericsTranslate` (roda `5/7`).
> `Translate.java` 316 ≤500; `TranslateTest` 19/19.
>
> **Estado (13/09 ~13:15, dono = 192.168.100.22): gaps honestos
> try-with-resources e tipo qualificado.** `try (R r = ...)` (Kof sem
> AutoCloseable — RAII é plano futuro) e `new pacote.Classe(...)` (translator
> ignora imports; mapear coleções Java→stdlib Kof é decisão de design,
> regra 6) → diagnóstico explícito com sugestão de revisão manual (R6).
> Prova: `TranslateTest.varargsAndNestedTypeAreHonestGaps` estendido.
> `TranslateExpr` 419 ≤500; `TranslateStatements` 335 ≤500;
> `TranslateTest` 19/19.
>
> **Estado (13/09 ~13:30, dono = 192.168.100.22): corpo de enum + multi-decl.**
> Corpo de enum Java (`enum Color { RED; int code(){...} }`) era pulado com
> `skipBlock` num `;` sem `{` → `expected '{' but found 'int'`; agora o corpo
> é pulado token a token até `}` (Kof enum é só constantes). Multi-declaração
> `int x = 1, y = 2;` (e sem init) → statements Kof separados
> (`var x = 1 var y = 2`); antes `expected ';' but found ','`. Prova:
> `TranslateTest.enumBodyAndMultiDeclTranslate` (traduz + compila JVM + roda
> `3`). `TranslateStatements` 354 ≤500; `TranslateTest` 20/20.
>
> **Estado (13/09 ~13:45, dono = 192.168.100.22): interface `extends` +
> gaps honestos labeled/anon.** `interface B extends A` → Kof (verificado no
> compilador); antes `expected '{' but found 'extends'`. Labeled statement
> (`outer: for ...`) e classe anônima (`new Runnable(){...}`) não têm
> equivalente Kof → diagnóstico explícito (R6). Prova:
> `TranslateTest.interfaceExtendsTranslates` (traduz + compila JVM + roda
> `g/f`) + `varargsAndNestedTypeAreHonestGaps` estendido. `Translate.java`
> 335 ≤500; `TranslateTest` 21/21.
>
> **Estado (13/09 ~14:15, dono = 192.168.100.22): 3 bugs de correção
> latentes (Q4).**
> 1. **Parênteses eram descartados** — `(1+2)*3` → `1+2*3` (=7, não 9):
>    Kof gerado compilava com **semântica errada** (o pior bug). Fix:
>    `parsePrimary` preserva `( ... )` (Kof aceita parênteses redundantes).
> 2. **`&`/`|`/`^`/`<<`/`>>`/`>>>` dropados silenciosamente** pelo lexer
>    (só `&&`/`||` emitiam token) → operandos colavam e o parser quebrava.
>    Fix: tokens `AMP`/`CARET`; shifts combinados de `LT`/`GT` no parser
>    (evita conflito com generics `List<String>`); `parseBitAnd`/
>    `parseBitOr`/`parseShift` com a precedência do parser Kof
>    (`KofFormatter.precOf`).
> 3. **Tipo qualificado** `java.util.Map<...>` → `Map<...>` (stripa pacote;
>    `Map`/`List`/`Set` são builtins Kof). `new java.util.ArrayList()` segue
>    gap honesto (mapear coleção = decisão de design).
> Prova: `TranslateTest.parenthesesPreservePrecedence` (roda `9/-3`),
> `bitwiseAndShiftTranslate` (roda `2/7/5/24/3/3/2147483644`),
> `qualifiedTypeNamesAreStripped`. `TranslateExpr` 471 ≤500;
> `TranslateTest` 24/24.
>
> **Estado (13/09 ~14:30, dono = 192.168.100.22): assert + gap de `for` com
> vírgula.** `assert cond;` / `assert cond : msg;` Java → `assert(cond)` /
> `assert(cond, msg)` Kof (primitive de teste, é função — `AssertE2ETest`);
> antes `expected ';' but found 'x'`. `for` C-style com init/incr múltiplos
> (`for (int i=0, j=3; ...; i++, j--)`) não tem equivalente Kof (for não
> aceita vírgula — PARSE041; desugar p/ while muda o fluxo do `continue`) →
> gap honesto (R6). Prova: `TranslateTest.assertTranslates` (roda `1`) +
> `forMultipleInitIncrIsHonestGap`. `TranslateStatements` 399 ≤500;
> `TranslateTest` 26/26.