# TRANSLATOR.md — Kof Translator (plano → EM DESENVOLVIMENTO)

> **Dono:** 192.168.100.22 (reivindicado 13/09 ~10:05 — órfão: sem dono com IP
> no header; último código há 6 dias `84c48041`; regra dono-sem-IP=órfão da
> mantenedora).

**Status:** EM DESENVOLVIMENTO (caiu de `future/` em 12/09 — Fase F
implementada: `Translate.java` + `TranslateLexer`/`TranslateExpr`;
prova: `TranslateTest` 9/9 (output compila e roda). Subconjunto Java
ampliado ainda pendente)
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