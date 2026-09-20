[English](unwrap-predicate-dead-gate.md) | [Português](unwrap-predicate-dead-gate.pt_BR.md)

# Um gate empilhado atras de um predicado que DESEMBRECA e codigo morto — verifique
# o que o predicado faz com o caso que voce quer pegar, nao so com os que ele mantém

## Problema

Codebases acumulam predicados *permissivos por design* — eles respondem por uma
familia inteira de tipos. Adicionar um ramo especializado **depois** de um desses
predicados (`if (A) ... else if (B_especial) ...`) faz o ramo nunca rodar quando `A`
ja casa com o caso especial. O branch vira codigo morto que *parece* correto em
revisao, o teste do caso especial e escrito e passa — mas pelo motivo errado ou na
trilha errada.

## Caso real neste repo (20/09, §361, fix `e293c4a5`)

O fix do escritor de campo nullable-primitivo boxado gateia a emissao do box de
erasure em `TypeMetrics.isNullablePrimitive(fld.type())`. A primeira tentativa
adicionou o gate como `else if` depois do branch existente de widening
primitivo→primitivo:

```java
// ❌ Errado — pareceu correto, e mesmo assim shipou RED 7/9
if (isPrimitiveType(a) && isPrimitiveType(b)) { emitWideningIfNeeded(a, b); }
else if (isNullablePrimitive(t)) { emitErasureBox(v); }
```

`TypeMetrics.isPrimitiveType` **desembreca** `NullableType`:
`isPrimitiveType(Int?) == true` (TypeMetrics.java:18). Toda store `Int?` era
engolida pelo primeiro branch antes de o novo gate ser alcancado — o predicado
preciso estava certo, a *posicao* o tornava codigo morto. O fix que pousou tambem
teve de excluir nullable do branch de widening puro (`&& !isNullablePrimitive(...)`).

## Preferido

```java
// ✅ BOM — o branch que desembreca exclui o caso especial que ele nao deve comer
if (isPrimitiveType(a) && isPrimitiveType(b) && !isNullablePrimitive(t)) { emitWideningIfNeeded(a, b); }
else if (isNullablePrimitive(t)) { emitErasureBox(v); }
```

Forma geral: antes de adicionar `else if (especial)`, leia o predicado de cada
branch **anterior** e responda "ele ja casa com `especial`?" — se sim, aperte-o
(`&& !especial`) ou trate `especial` **primeiro**. E quando o fix alega cobrir uma
familia (`Int?/Long?/Double?/Char?`), escreva a matriz de teste para CADA membro —
o 9/9 do §361 era falso-verde exatamente na face que este anti-pattern deixou de
fora (`Char?`, ainda aberta como §368).

## Por que

Um ramo morto e pior que nenhum ramo: registra uma intencao que a maquina nunca
executa, e o proximo leitor (ou o proximo agente) acredita que o caso esta tratado.
E o gêmeo do lado do compilador de `weak-green-proof.md` (uma prova que asserir
"nao crashou" em vez da contrat) e da familia de armadilhas §294-2a/§295(b) —
`erasesToReference` retornando FALSE para `NullableType` era o mesmo erro espelhado
na direcao oposta (um gate que NAO disparava para o caso especial, em vez de
disparar e ser pulado). Relacionado: `docs/bugs-and-gaps/known-bugs.md` adendo do
§361 + §368, `DECISIONS.md` D-NULL-INTENT.
