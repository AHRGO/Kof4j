[English](weak-green-proof.md) | [Português](weak-green-proof.pt_BR.md)

# Um "GREEN" que prova *não-crash* não é prova — tem que asserir a SEMÂNTICA da issue

## Problema

Ao fazer triagem de uma issue (ou escrever teste de regressão para um fix), a
"prova" mais barata é rodar o snippet reportado e ver que ele não lança mais /
sai com 0. Isso só refuta o **sintoma de crash** — não diz nada sobre o
**contrato que o título da issue afirma**. Um bug cujo sintoma era "X quebra"
pode ser "consertado" num estado em que "X retorna silenciosamente o valor
ERRADO" — e uma prova de não-crash chama isso de GREEN.

Caso real neste repo (14/09, issue #207, valores de enum): um agente comentou
"GREEN — `for (var d in Dir.values()) { println(d.name()) }` imprime N S E W,
ec=0". A issue foi fechada. Foi REABERTA depois pela mantenedora: o lowering
ainda emite cada `Dir.N` como `ldc "N"` (constante String), nenhum `Dir.class`
é gerado, `Dir.N.getClass()` retorna `java.lang.String`, e `Dir.N == "N"` é
`true`. O snippet só rodou bem porque `.name()` sobre a constante dobra em
compile-time — a face exata nomeada no título da issue (*"compilados como
constantes String em vez de instâncias getstatic de enum"*) nunca foi testada.
Um run de não-crash é cego a um bug de valor-errado.

É a mesma armadilha de um `assertTrue(run(...).contains("N"))` fraco: a String
`"N"` satisfaz mesmo que o valor nunca tenha sido um enum.

## Ruim

```text
# "prova" da #207:
$ kof run repro.kf
N
S
E
W
-> "GREEN, funciona"   # só prova que não quebra
```

## Bom

```text
# asserir a SEMÂNTICA do título, não a ausência do crash:
$ kof run sem.kf
class java.lang.String     # <- esperado: class Dir
true                       # <- Dir.N == "N" esperado: false / type error
-> "AINDA QUEBRADO"        # o run de não-crash escondia isso
```

Antes de declarar uma issue consertada (ou comentar GREEN nela), traduza a
**afirmação do título** numa asserção executável:
- o título diz *tipo X vira Y* → asserir `getClass()`/kind, não só a saída;
- o título diz *valor errado* → asserir o valor esperado exato;
- o título diz *não compila mas deveria* → asserir os dois sentidos (compila +
  se comporta).

```kof
main() {
    println(Dir.N.getClass())    // tem que ser a classe do enum, não String
    println(Dir.N == "N")        // tem que ser false (ou type error)
}
```

## Porquê

A regra Q5 do portão de qualidade: *um teste que passa por acaso é um bug
disfarçado*. O run de não-crash é o caso extremo — passa para toda
implementação silenciosamente errada. Achar o bug é parte do trabalho (Q4): a
pergunta a responder é **"o que o título assevera, e o programa faz isso?"** —
não "o programa morreu?".
