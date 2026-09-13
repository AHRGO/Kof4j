# TRANSLATOR.md — Kof Translator (plano → EM DESENVOLVIMENTO)

> **Dono:** 192.168.100.22 (reivindicado 13/09 ~10:05 — órfão: sem dono com IP
> no header; último código há 6 dias `84c48041`; regra dono-sem-IP=órfão da
> mantenedora).

**Status:** EM DESENVOLVIMENTO (caiu de `future/` em 12/09 — Fase F
implementada: `Translate.java` + `TranslateLexer`/`TranslateExpr`;
prova: `TranslateTest` 12/12 (output compila e roda; +do-while +switch
+try/catch/throw 13/09). Subconjunto Java ampliado ainda pendente)
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