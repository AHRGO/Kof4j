[English](23-testing.md) | [Português](23-testing.pt_BR.md)

# 23 — Testing

> **Status: implemented — `test "nome" { }`, `kof test` + `assert` — 0.5.0-beta**
>
> Testing Kof is writing Kof. The structured suite declares cases with
> `test "nome" { }`; `kof test` runs each test in isolation and reports
> PASS/FAIL **by name**, with an exit code based on the result. Inside the
> test, `assert(cond[, "msg"])` marks the failure with a clear message.

## test "nome" { } — structured suite

```kf
test "soma simples" {
    assert(2 + 2 == 4)
}

test "string igual" {
    assert("kof" == "kof", "strings iguais")
}

main() {
    // the real program; kof test ignores it (like cargo test)
}
```

```bash
kof test Suite.kf                     # jvm
kof test Suite.kf --target native     # native
kof test Suite.kf --target js         # js
```

Output:

```text
PASS soma simples
PASS string igual
0 failed of 2 tests
```

Each test runs **in isolation** (one failing does not interrupt the others).
The compiler knows the tests at compile-time — the names become literals in
the generated runner, without reflection. Failure = exit code ≠ 0, without a
stack trace.

## assert

```kf
main() {
    assert(2 + 2 == 4)
    assert("kof" == "kof", "strings iguais")
    assert(listOf(1, 2).size == 2)
}
```

## kof test (whole programs)

`.kf` files **without** `test` blocks keep the previous contract: the file is
a program; PASS = exit code 0.

```bash
kof test src/tests/            # directory — one program per file
kof test math.kf               # single file
kof test src/tests --target native
```

Output:

```text
PASS src/tests/math.kf
FAIL src/tests/broken.kf
1 passed, 1 failed
```

The test fails when: the program does not compile, the process exits with a
code ≠ 0 (e.g. a false `assert`), or main is not found.

## process.exit(code)

For scripts and your own harnesses: terminates immediately with the given
code, on all three targets, without a stack trace.

```kf
main() {
    if (!validar()) {
        process.exit(1)
    }
}
```

## Convention

Each `.kf` test file is an independent executable program (it has
`main()`). `assert` is the primitive — there is no framework and no
annotations.

## Writing a suite

```kf
// math.kf — one file per area
Int soma(Int a, Int b) {
    return a + b
}
main() {
    assert(soma(2, 3) == 5)
    assert(soma(-1, 1) == 0, "negativos")
    println("math ok")
}
```

## JUnit (do not use)

The Java/JUnit ecosystem is **not** part of the language — no annotations, no
framework. The Kof test is the language: `test "nome" { assert(...) }` is the
unit of testing on any target.

## Running tests

```bash
kof test src/test/                        # directory — one program per file
kof test math.kf                          # single file
kof test src/test/ --target native        # target
```

## Next step

[Build Tools →](24-build-tools.md)
