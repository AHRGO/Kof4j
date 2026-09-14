[English](12-collections.md) | [Português](12-collections.pt_BR.md)

# 12 — Collections

> **Status: implemented (JVM / Native / JS) — 0.3.22-beta**
>
> `List<T>`, `Map<K,V>` and `Set<T>` are native Kof collections. `List` now has idiomatic `map/filter/reduce` (0.2.0) in addition to the base operations. The element
> type is preserved by the entire pipeline (inference, for-in, `get`,
> method resolution). On Native, Map and Set run in their own assembly
> over the same allocation layout as List.

## List — the idiomatic form

```kf
var tokens = listOf(
    Token("identifier", "hello"),
    Token("string", "world")
)

for (var token in tokens) {
    println(token.kind())     // the element type never degrades to Object
}

var nomes = listOf<String>()            // typed empty list
nomes.add("Ana")
nomes.add("Bob")
println(nomes.get(0))
```

Inference is maintained throughout the pipeline:

```kf
var users: List<User> = listOf()        // explicit annotation
users.add(User("Mel", 26))
println(users.get(0).name)
```

`json.decode<List<User>>(...)` also preserves the element type —
each element is bound to the record, on JVM and KofJS.

## List operations

```kf
var l = listOf(1, 2, 3, 4)

l.size()          // 4
l.get(0)          // 1
l.contains(3)     // true
l.isEmpty()       // false
l.remove(0)       // removes by index, returns the element
l.set(0, 9)       // replaces in-place
l.clear()         // empties
```

### map / filter / reduce (0.2.0)

`List<T>` exposes direct functional transformations — without exposing `Stream` — via `intention->Kof->frontend->IR->backend->runtime`:

```kf
main() {
    var nums = listOf(1, 2, 3, 4, 5)

    var dobrados = nums.map((x: Int) -> x * 2)          // List<Int> [2,4,6,8,10]
    println(dobrados.get(1))                       // 4

    var pares = nums.filter((x: Int) -> x % 2 == 0)     // [2,4]
    println(pares.size())                          // 2

    var soma = nums.reduce((acc: Int, x: Int) -> acc + x, 0) // 15
    println(soma)

    // chaining:
    var r = listOf(1, 2, 3, 4)
        .filter((x: Int) -> x > 1)
        .map((x: Int) -> x * 10)
    println(r.get(0))   // 20

    // with records:
    var users = listOf(User("Ana", 26), User("Bob", 31))
    var nomes = users.map((u: User) -> u.name)
    println(nomes.contains("Ana"))   // true
}
```

All three methods run on JVM, Native and JS with the same semantics.

## Map — key/value pairs

`Map<K,V>` stores pairs with a unique key. The API mirrors the intention, not the
mechanism — with no `HashMap` exposed on the surface:

```kf
var idades = mapOf()
idades.put("Ana", 26)
idades.put("Bob", 31)

idades.get("Ana")         // 26
idades.containsKey("Bob") // true
idades.size()             // 2

idades.put("Ana", 27)     // overwrites; returns the previous value
idades.remove("Bob")      // returns the removed value

idades.keys()             // List<String> of the keys
idades.values()           // List<Int> of the values
idades.clear()
idades.isEmpty()
```

The value type is pinned on the first `put` — after that `get`, `remove`
and comparisons have concrete typing:

```kf
var estoque = mapOf()
estoque.put("parafuso", 500)
assert(estoque.get("parafuso") == 500)   // direct numeric comparison
```

> **Careful (02/09):** `get`/`remove`/`put` of a `Map<K, primitive>` return
> `V` (non-nullable), but the **absence** of the key is `null` at runtime → NPE when
> unboxing. For reference values (`Map<String, String>`) `get`
> returns `V?` (use `if (v != null)`). For primitives, check with
> `contains`/`containsKey` first.

## Set — unique values

`Set<T>` rejects duplicates: `add` returns `true` only when the element is
new.

```kf
var vistos = setOf(1, 2, 2, 3)
vistos.size()          // 3 — the second 2 was ignored

vistos.contains(2)     // true
vistos.add(2)          // false (already exists)
vistos.remove(1)       // true
vistos.clear()
vistos.isEmpty()       // true
```

Strings work the same:

```kf
var tags = setOf("kof", "lang")
tags.add("kof")        // false
println(tags.size())   // 1
```

## kof.http — example with collections (JVM+JS)

`kof.http` works on JVM and JS (Native reports `HTTP002`):

```kf
main() {
    var url = "https://api.example.com/users"
    var resp = http.get(url)                 // resp is the body (String)
    if (http.status(url) == 200) {
        var users = json.decode<List<User>>(resp)
        var ativos = users.filter((u: User) -> u.age > 18)
        println(ativos.map((u: User) -> u.name).get(0))
    }
}
```

## Parity between targets

| Operation | JVM | Native | JS |
|----------|-----|--------|----|
| Complete List | ✅ | ✅ asm | ✅ |
| List map/filter/reduce | ✅ | ✅ | ✅ |
| Map (all operations) | ✅ HashMap | ✅ own asm | ✅ JS Map |
| Set (all operations) | ✅ HashSet | ✅ asm over List | ✅ JS Set |
| kof.http (JVM+JS) | ✅ | HTTP002 | ✅ |

Equality in Map/Set uses `equals` on the JVM, native comparison (with a type
tag for strings) on Native and `===`/`Map`/`Set` on JS.

## Next step

**[13 — Nullability](13-nullability.md)**
