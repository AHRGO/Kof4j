[English](21-java-interoperability.md) | [Português](21-java-interoperability.pt_BR.md)

# 21 — Java Interoperability

> **Status: parcial — bytecode JVM compatível; chamada Java direta funciona
> para o que está no classpath (verificado 02/09)**
>
> O compilador gera bytecode JVM padrão (V21). **Antes de assumir que uma API
> Java funciona, compile e rode.** Verificado em 02/09: `java.util` collections
> ✅; `java.time`/`java.util.stream` ❌ (tipos não resolvem sem classpath
> externo); `java.io.FileWriter.write` ❌ (resolução de overload errada →
> `NoSuchMethodError`).

## A premissa

Kof gera bytecode JVM padrão — V21, com exception table real e virtual
threads. Bibliotecas Java podem funcionar, mas **o caminho idiomático é a
stdlib Kof** (`listOf`/`mapOf`/`kof.io`/`json.*`).

## Usando Java Collections (verificado ✅)

```kf
import java.util.ArrayList;
import java.util.HashMap;

main() {
    var lista = new ArrayList<String>()
    lista.add("Kof")
    lista.add("legal")
    println(lista.size())    // 2
    println(lista.get(0))    // Kof

    var mapa = new HashMap<String, Integer>()
    mapa.put("kof", 1)
}
```

## O idiomático: use as collections do Kof

Para o caso comum, `List<T>`/`Map<K,V>` da linguagem já resolvem — sem
`import java.util.*`:

```kf
var lista = listOf("Kof", "legal")
println(lista.size)
var mapa = mapOf("kof", 1)
```

## Transformação de dados — use `map/filter`, não Java Streams

```kf
// ✅ Kof idiomático — sem Stream, sem Collectors
var numeros = listOf(1, 2, 3, 4, 5)
var pares = numeros.filter((n: Int) -> n % 2 == 0)
println(pares.size)          // 2

// ❌ Java Streams NÃO compila sem classpath externo:
//   var pares = numeros.stream().filter(...).collect(Collectors.toList())
```

## Arquivos — use `kof.io`

```kf
// ✅ kof.io idiomático
File("/tmp/x.txt").writeText("olá")
println(File("/tmp/x.txt").readText())

// ⚠️ java.io.FileWriter.write(String) → NoSuchMethodError (02/09, não usar)
```

## O que requer classpath externo (parcial)

Tipos fora de `java.lang`/`java.util` (ex.: `java.time.*`, JDBC, Spring)
precisam do classpath externo configurado (`setExternalClasspath` /
`--classpath`) e ainda não têm paridade completa:

```kf
// Requer classpath externo + pode não resolver overloads
var hoje = LocalDate.now()          // ❌ SEM011 sem classpath
var conn = DriverManager.getConnection(url, user, pass)   // ❌ idem
```

## Regras de interoperabilidade

1. **Tipos Kof → Java**: mapeados diretamente (`Int` → `int`, `String` → `String`)
2. **Generics**: funcionam entre as linguagens (collections ✅)
3. **Annotations**: chegam ao bytecode corretamente (ver cap. 20)
4. **Antes de usar API Java**: compile e rode — o suporte é parcial e a
   resolução de overloads ainda tem falhas (02/09)

## Próximo passo

[JVM →](22-jvm.md)