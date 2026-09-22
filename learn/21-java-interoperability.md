[English](21-java-interoperability.md) | [Português](21-java-interoperability.pt_BR.md)

# 21 — Java Interoperability

> **Status: partial — compatible JVM bytecode; direct Java call works
> for what is on the classpath (verified 02/09)**
>
> The compiler generates standard JVM bytecode (V21). **Before assuming that a Java
> API works, compile and run.** Verified on 02/09: `java.util` collections
> ✅; `java.time`/`java.util.stream` ❌ (types do not resolve without an external
> classpath); `java.io.FileWriter.write` ❌ (wrong overload resolution →
> `NoSuchMethodError`).

## The premise

Kof generates standard JVM bytecode — V21, with a real exception table and virtual
threads. Java libraries can work, but **the idiomatic path is the Kof
stdlib** (`listOf`/`mapOf`/`kof.io`/`json.*`).

## Using Java Collections (verified ✅)

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

## The idiomatic way: use Kof's collections

For the common case, the language's `List<T>`/`Map<K,V>` already solve it — without
`import java.util.*`:

```kf
var lista = listOf("Kof", "legal")
println(lista.size)
var mapa = mapOf("kof", 1)
```

## Data transformation — use `map/filter`, not Java Streams

```kf
// ✅ idiomatic Kof — no Stream, no Collectors
var numeros = listOf(1, 2, 3, 4, 5)
var pares = numeros.filter((n: Int) -> n % 2 == 0)
println(pares.size)          // 2

// ❌ Java Streams do NOT compile without an external classpath:
//   var pares = numeros.stream().filter(...).collect(Collectors.toList())
```

## Files — use `kof.io`

```kf
// ✅ idiomatic kof.io
File("/tmp/x.txt").writeText("olá")
println(File("/tmp/x.txt").readText())

// ⚠️ java.io.FileWriter.write(String) → NoSuchMethodError (02/09, do not use)
```

## What requires an external classpath (partial)

Types outside `java.lang`/`java.util` (e.g.: `java.time.*`, JDBC, Spring)
need the external classpath configured (`setExternalClasspath` /
`--classpath`) and still do not have full parity:

```kf
// Requires external classpath + may not resolve overloads
var hoje = LocalDate.now()          // ❌ SEM011 without classpath
var conn = DriverManager.getConnection(url, user, pass)   // ❌ same
```

## Reflection at the boundary — `interop.schema` (X6)

When the data comes from outside (Arrow/Parquet/ML schemas), you usually need
the record's **structure** (field names + types) to bind columns to fields. Kof
exposes that as a **compile-time intrinsic**, only at the interop boundary — no
runtime reflection, no hand-written mapper:

```kf
import kof.interop

record Order(String id, Double amount, Long qty)

main() {
    for (var f in interop.schema(Order)) {
        println(f.name() + ":" + f.type())   // id:String, amount:Double, qty:Long
    }
}
```

`interop.schema(R)` returns an immutable `List<Field>`, where `Field` is a
compiler-provided `record Field(String name, String type)` in declaration order.
Because the fold happens in the frontend, the output is identical on
JVM/Native/Script/JS. An invalid use is diagnosed (`INTEROP002` unknown member;
`INTEROP001` wrong arity / a value / a class / an enum) — never silent.

## Interoperability rules

1. **Kof types → Java**: mapped directly (`Int` → `int`, `String` → `String`)
2. **Generics**: work between the languages (collections ✅)
3. **Annotations**: reach the bytecode correctly (see ch. 20)
4. **Before using a Java API**: compile and run — support is partial and
   overload resolution still has flaws (02/09)

## Reflection at the boundary — `interop.schema(R)` (X6)

When external data must bind to a Kof `record` (an Arrow/Parquet/ML schema),
you do not hand-write a mapper. The compiler already knows the record structure:
`interop.schema(R)` gives a read-only view of it at compile time — zero runtime
reflection, so the same output on every target. It is enabled explicitly by
`import kof.interop`.

```kf
import kof.interop

record Order(String id, Double amount, Long qty)

main() {
    for (var f in interop.schema(Order)) {
        println(f.name() + ":" + f.type())   // id:String, amount:Double, qty:Long
    }
}
```

`interop.schema(R)` resolves to an immutable `List<Field>`, where `Field` is the
compiler-provided `record Field(String name, String type)` in declaration order.
An invalid use is a diagnostic, never silence (`INTEROP002` for an unknown
member, `INTEROP001` for a wrong arity or a non-record argument).

## Next step

[JVM →](22-jvm.md)
