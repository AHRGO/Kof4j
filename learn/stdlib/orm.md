[English](orm.md) | [Português](orm.pt_BR.md)

# kof.orm — records as rows, no SQL ceremony

> **Status: JVM-first (`D-DB-GAPS` queue) — native `kof_orm_*` entries are
> the measured, diagnosed gap slice.**

| Function | Form |
|----------|------|
| `create` | `create(String entity) -> Bool` |
| `save` | `save(String entity, Object row) -> Object` |
| `saveAll` | `saveAll(String entity, List rows) -> Bool` |
| `find` | `find(String entity, Object id) -> Object` |
| `all` | `all(String entity) -> List` |
| `where` | `where(String entity, String cond, Object value) -> List` · `where(entity, col, op, value) -> List` |
| `page` | `page(String entity, Object offset, Object limit) -> List` |
| `count` | `count(String entity) -> Long` · `count(entity, String where, Object value) -> Long` |
| `delete` | `delete(String entity, Object id) -> Bool` |
| `deleteAll` | `deleteAll(String entity) -> Bool` |
| `migrate` | `migrate(String url, String user, String pass) -> Bool` |

```kf
record User(Int id, String name)

var u = orm.save("users", User(1, "Mel"))
var back = orm.find("users", 1) as User
println(back.name())                 // Mel
var grownUps = orm.where("users", "id", ">", 0)
println(orm.count("users"))          // 1
```

- The entity name is the table; rows are records — no DTO, no mapper, no
  annotations.
- `where` has the two measured shapes: `where(entity, cond, value)` and
  `where(entity, col, op, value)`.
- `migrate` creates the schema from the declared entities.

**See also:** [kof.db](db.md) — raw SQL when you need it;
[D-DB-GAPS](https://github.com/aminadojava/Kof4j/blob/beta-0.5.0/docs/development/DECISIONS.md) — parity queue.
