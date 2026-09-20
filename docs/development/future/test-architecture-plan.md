[English](test-architecture-plan.md) | [Português](test-architecture-plan.pt_BR.md)

# 🧪 Refactoring Plan — Kof Test Architecture and Modularization

> **State (19/09): FUTURE — plan only, zero code.** Registered at the
> maintainer's request; not an execution queue (three-states rule + R12).
> Promote to `docs/development/` only with her explicit decision.

## 📌 Overview

The repository currently has **thousands of tests**, but they are not
organized as a test architecture. They grew along with the compiler.

The problem is not the quantity.

The problem is that, over time, these emerged:

- repeated tests;
- equivalent scenarios written in different ways;
- giant tests trying to validate many things;
- test classes heavily coupled to the implementation;
- syntax tests mixed with lowering tests;
- lowering tests mixed with execution;
- E2E execution mixed with conformance;
- stress tests living next to fast tests;
- suites whose feedback is slow.

This compromises three things:

1. development speed;
2. compiler reliability;
3. the project's engineering quality.

## 🎯 Objective

Turn the tests into an organized, modular, fast and deterministic system.

The suite must stop being just a large volume of `*Test.java` files and
acquire clear validation layers.

## 🧠 Kof's Testing Philosophy

Proposed as the project's official philosophy:

> A test does not exist to prove the code works.
>
> A test exists to prevent an engineering decision from being lost in the
> future.

Consequently:

- every fixed bug stays protected;
- every design decision stays documented;
- every observable behavior stays validated;
- no test exists merely to inflate a number.

## 🏗️ Layered Architecture

Proposed formal architecture:

```
L0 - Unit Tests
    Parser
    Lexer
    AST
    Typer
    Semantic Analysis

L1 - Component Tests
    Lowering
    Codegen
    IR
    Optimizer
    Backend
    ABI

L2 - Target Execution
    JVM
    Native
    JavaScript
    Script
    Android

L3 - E2E
    Compile
    Run
    Compare stdout
    Check exit code

L4 - Conformance
    syntax
    semantics
    stdlib
    operators
    runtime

L5 - Stress
    concurrency
    memory
    fuzzing
    load
    stability
```

## 🔥 Main Identified Problems

### 1. Massive structural repetition

Currently many tests:

- create a compiler;
- load code;
- compile;
- execute;
- check a string.

This repeats across practically the whole suite.

Proposed: create an official **Kof Test Harness**, centralizing:

```
compile()
run()
expect()
expectOutput()
expectDiagnostic()
```

## 2. Lack of isolation between targets

Today the tests:

```
JVM
Native
JS
Script
```

end up coexisting in the same test repository without explicit boundaries.

Proposed formal separation:

```
compiler/
native/
jvm/
js/
script/
shared/
conformance/
```

## 3. Giant tests

There are files with hundreds of scenarios.

This makes it hard to:

- debug;
- run in isolation;
- measure time;
- discover regressions.

Proposed: split by responsibility.

## 4. Absence of execution profiles

Currently the developer practically runs everything.

There should be profiles:

### Fast

```
mvn test -Pfast
```

Goal: feedback under ~30 seconds.

Contains:

- Parser
- Lexer
- Typer
- Lowering
- Unit
- Component

### Integration

```
mvn test -Pintegration
```

Contains:

- targets
- execution
- golden
- ABI

### Full

```
mvn test
```

Everything.

### Stress

```
mvn test -Pstress
```

Contains:

- concurrency
- memory
- fuzzing
- stability

This separation prevents 10-minute tests from dictating the daily pace.

## 5. Lack of traceability

Today there is no easy map between:

- feature
- test
- bug
- decision

Proposed: each test family declares:

```
Feature:
Records
Pattern Matching
Generics
FFI
```

and:

```
Coverage:
Parser
Typer
Lowering
JVM
Native
JS
```

## 6. Golden tests

Proposed: an official **Golden Suite**.

It should contain:

- real examples;
- compiled code;
- expected output;
- exit code;
- hash.

Goal:

```
same code
↓
same output
↓
on every target
```

## 7. Regression tests

Today many bugs become a single testcase.

Proposed official policy:

> Every fixed bug must generate:
>
> 1. Minimal reproduction
> 2. Regression test
> 3. Permanent reference

The test must never be removed.

## 8. Compiler-crash tests

Today many tests try to reproduce errors.

What's missing is a dedicated Stability suite.

It should validate:

- the parser never hangs;
- lowering never throws an unexpected exception;
- the typer never loops;
- invalid code always produces a diagnostic;
- the AST never ends up inconsistent.

## 9. Deterministic tests

No test may depend on:

- the current time;
- the network;
- a specific operating system;
- an external tool's availability without an explicit guard.

Every external dependency must be protected by honest environment guards
(`assumeTrue` + documented gap — R6, never a silent skip).

## 10. Feedback cost

Proposed: continuous measurement.

Generate a report:

```
test
time
failures
stability
```

The slowest tests must be permanently monitored.

## 🧪 Refactoring Strategy

The refactoring must NOT touch the compiler.

It changes only the test infrastructure.

### Phase 1 — Profiling

Instrument the whole suite.

Discover:

- time per class
- time per target
- duplicated tests
- redundant tests
- unstable tests

### Phase 2 — Quick Wins

Remove:

- repetition;
- sleeps;
- unnecessary loops;
- redundant setup.

### Phase 3 — Modularization

Separate layers:

```
compiler tests
backend tests
target tests
conformance tests
stress tests
```

### Phase 4 — Harness

Build the official infrastructure.

### Phase 5 — Targets

Separate execution:

```
JVM
Native
JS
Script
```

### Phase 6 — Conformance

Build the official equivalence suite.

### Phase 7 — Integration

Deploy:

```
mvn verify
```

or equivalent.

## 📊 Goal

After the refactoring:

- fast feedback;
- less redundancy;
- a testable architecture;
- greater release confidence;
- regressions easier to investigate;
- tests that explain decisions;
- a suite that keeps up with Kof's growth.

## Architecture Diagram

```
Kof Test Suite
        │
        ├── Unit
        │
        ├── Component
        │
        ├── Backend
        │
        ├── Target
        │      │
        │      ├── JVM
        │      ├── Native
        │      ├── JavaScript
        │      ├── Script
        │      └── Android
        │
        ├── E2E
        │
        ├── Conformance
        │
        ├── Golden
        │
        ├── Fuzzing
        │
        └── Stress
```

## Golden rule

> "The compiler may change architecture.
>
> The test suite does not."

That phrase exactly captures the direction we are following.

## Conclusion

Kof's test suite must not be treated as secondary code.

It is one of the project's main engineering assets.

After consolidating the compiler, this is one of the biggest opportunities to
evolve Kof's quality.

Additional proposal: at the end of the refactoring, generate a permanent
document:

```
docs/testing/TEST-PERFORMANCE.md
```

tracking metrics such as:

- total time;
- time per layer;
- slowest tests;
- most unstable tests;
- suite runtime evolution.

## Next Step

Before any deep refactoring, the path would be:

1. measure the whole suite;
2. identify the 20 slowest tests;
3. look for duplication;
4. propose the modularization.

**Important:** this refactoring must not interfere with anything we are
currently doing in the compiler. It is purely test infrastructure — and that
is exactly why it belongs to the future until the maintainer decides (R12: no
future plan item is an action on current work).
