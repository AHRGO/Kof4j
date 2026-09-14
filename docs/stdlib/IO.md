[English](IO.md) | [Português](IO.pt_BR.md)

# kof.io — Filesystem API

`kof.io` is the official Kof filesystem API: files, directories and
paths with a single semantics on the JVM and Native targets.

## Types

`File`, `Path` and `Directory` represent a path (the path string).
All `kof.io` operations work on the three types — the type only
guides the intent.

## Path

| Operation | Example | Result (Linux/macOS) |
|----------|---------|--------------------------|
| `resolve` | `Path("data").resolve("users.txt")` | `data/users.txt` |
| `parent` | `Path("data/users.txt").parent()` | `data` |
| `fileName` | `Path("data/users.txt").fileName()` | `users.txt` |
| `extension` | `Path("data/users.txt").extension()` | `txt` |
| `normalize` | `Path("a/./b/../c").normalize()` | `a/c` |
| `isAbsolute` | `Path("/x").isAbsolute()` | `true` |
| `toAbsolute` | `Path("x").toAbsolute()` | absolute path |

On Windows the separator is `\`; Kof code never concatenates separators.

## File

| Operation | Description |
|----------|-----------|
| `exists()` | Bool |
| `isFile()` / `isDirectory()` | Bool |
| `readText()` | `String?` — `null` on failure (JVM and Native) |
| `writeText(s)` / `appendText(s)` | Bool, UTF-8 |
| `readBytes()` | `Int[]` (0-255), `null` on failure |
| `writeBytes(b)` / `appendBytes(b)` | Bool |
| `size()` | Long; throws an exception if the file does not exist (02/09 — no `-1` sentinel) |
| `delete()` | Bool (file or empty directory) |
| `name()` / `path()` | String |

Static forms: `File.exists(p)`, `File.readText(p)`,
`File.writeText(p, s)`, `File.appendText(p, s)`, `File.delete(p)`,
`File.size(p)`, `File.name(p)`.

## Directory

| Operation | Description |
|----------|-----------|
| `exists()` | Bool |
| `create()` | creates; fails if it already exists |
| `createDirectories()` | creates recursively |
| `list()` | `List<String>` of names, sorted |
| `delete()` | removes an empty directory |

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

`entry.name` and `entry.path` return the entry itself.

## Complete example

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
var text = path.readText()
println(text)
println(path.size())
```

## Errors and encoding

- Text: UTF-8 always.
- Absence as a value (02/09): `readText()`/`readFile()` return `String?`
  (`null` for a nonexistent file) on JVM and Native; `size()` throws a
  recoverable exception (`catch (String e)`) — the `-1` sentinel was removed.
- Booleans: `true`/`false`. `size()` throws an exception when the file does not exist (without `-1`).

## Reference

- [learn/34-file-system.md](../../learn/34-file-system.md)
- Tests: `kof-compiler/src/test/java/dev/kof/compiler/IoE2ETest.java`
