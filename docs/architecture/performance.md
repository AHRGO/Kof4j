[English](performance.md) | [Português](performance.pt_BR.md)

# KOF — PERFORMANCE, BENCHMARKS, RESOURCE SAFETY AND ARCHITECTURAL GUIDELINES

**Last updated:** September 12, 2026
**Version:** 0.5.0-beta (37 benchmarks; `kof bench` + `benchmark.yml` threshold 1.20)

> This document defines permanent architectural principles of Kof.
>
> They are not suggestions.
> They are not optional features.
> They are not cosmetic marketing goals.
>
> Type system, IR, backends, runtime, standard library and tooling must respect these rules.

---

# 1. FUNDAMENTAL PRINCIPLE

Kof was designed to remove complexity from code without transferring that complexity to runtime.

The language must simultaneously pursue:

```text
high expressiveness
+
strong type safety
+
low overhead
+
low resource consumption
+
high performance
+
excellent interoperability
```

Abstraction exists to benefit the programmer.

When an abstraction is not needed at runtime, it must disappear during compilation.

The fundamental rule is:

> **Kof must try to generate the most semantically efficient representation possible for each construct.**

---

# 2. JAVA IS NOT THE PERFORMANCE CEILING

Java is a reference for:

* ecosystem;
* interoperability;
* platform semantics;
* libraries;
* JVM;
* compatibility.

Java **is not the performance limit of Kof**.

The conceptual comparison is:

```text
Java
 ↓
javac
 ↓
JVM bytecode
 ↓
HotSpot/JIT
```

versus:

```text
Kof
 ↓
Lexer
 ↓
Parser
 ↓
AST
 ↓
Semantic Analysis
 ↓
Type System
 ↓
Kof IR
 ↓
JVM Backend
 ↓
JVM bytecode
 ↓
HotSpot/JIT
```

Kof has semantic knowledge of the program before bytecode generation.

This knowledge must be used.

---

# 3. PERFORMANCE SUPERIORITY RULE

When there is semantically equivalent Java code and Kof code:

```text
Idiomatic Java
vs
Idiomatic Kof
```

Kof must seek to generate a representation:

```text
equal or better
```

relative to Java.

More importantly:

> **Never reproduce Java's overhead simply because a traditional Java implementation does it that way.**

If the compiler can prove that a given abstraction can be eliminated without changing semantics, it must be eliminated.

Examples:

```text
boxing
allocation
iterator
temporary object
reflection
indirection
dispatch
wrapper
```

must not exist simply for the convenience of the compiler implementation.

---

# 4. KOF/JVM

The JVM must not be treated as an excuse to generate mediocre bytecode.

The JVM backend must seek excellent bytecode for HotSpot.

Prioritize:

* concrete types;
* direct access;
* simple methods;
* predictable dispatch;
* absence of unnecessary boxing;
* absence of unnecessary allocations;
* absence of unnecessary reflection;
* efficient loops;
* simple control flow;
* correct metadata;
* correct StackMapTable;
* JIT-friendly structures.

Example:

```kof
var total = 0

for (x in values) {
    total = total + x
}
```

If `values` allows it, the compiler must seek a representation equivalent to a direct loop.

It must not automatically generate:

```text
Iterator allocation
↓
hasNext()
↓
next()
↓
boxing
↓
temporary objects
```

just because that would be an easy path to implement.

---

# 5. KOF/NATIVE

In Native, Kof has even greater control over execution.

Therefore, the efficiency expectation must be even more aggressive.

> **State (0.5.0-beta, re-synced 17/09; base 31/08):** allocation via free-list `kof_free_head`
> (reuse `mmap` — reduces the mmap cost per allocation); FP in XMM
> (`vcvtsi2sd`/`mulsd`) instead of fallback to int; dtoa via `snprintf`;
> `spawn` on real threads (`pthread`) — context overhead documented
> in the concurrency benchmarks.

Prioritize:

* low startup;
* low memory consumption;
* low number of allocations;
* low call overhead;
* low abstraction overhead;
* lean binaries when possible;
* efficient syscalls;
* efficient cache use;
* efficient loops;
* predictable stack usage;
* efficient memory management;
* absence of an unnecessarily large runtime.

The architectural goal is for Kof/Native to be capable of surpassing equivalent implementations whenever the backend's additional control allows it.

---

# 6. KOF/JS

The JS backend must generate natural and efficient JavaScript.

Avoid:

* unnecessary wrappers;
* temporary objects;
* artificial closures;
* boxing;
* indirect dispatch;
* gigantic runtime;
* abstractions that could be eliminated.

When a Kof construct can be translated directly into an efficient ECMAScript construct, prefer the direct representation.

---

# 7. KOF/SCRIPT

Kof/Script must continue to be based on the compiler's existing infrastructure.

Do not create a second compiler just for script.

Script mode must prioritize:

* fast startup;
* low latency;
* low consumption;
* predictable execution;
* simple integration;
* minimal runtime;
* reuse of the frontend;
* reuse of the type system;
* reuse of the IR.

Script does not mean slow.

---

# 8. ZERO UNNECESSARY OVERHEAD

Every new feature must answer:

```text
What is the runtime cost of this abstraction?
```

If the answer is:

```text
none
```

because it disappears during compilation:

great.

If there is a cost:

```text
which one?
why?
is it really necessary?
can it be eliminated?
```

The implementation must not hide overhead.

---

# 9. ZERO UNNECESSARY BOXING

Primitive values must remain primitive when semantically possible.

Example:

```kof
Int
Long
Bool
Char
```

must not be automatically transformed into objects.

Prefer:

```text
ILOAD
ISTORE
IADD
```

over:

```text
allocate Integer
↓
unbox
↓
operation
↓
box
```

when boxing is not necessary.

The type system and the IR must preserve enough information for the backend to make this decision.

---

# 10. ZERO UNNECESSARY REFLECTION

Reflection is necessary for:

* interoperability;
* frameworks;
* metadata;
* explicitly reflective APIs.

But it must not be used for normal operations that can be resolved statically.

Example:

```kof
user.name
```

must prefer:

```text
GETFIELD
```

or equivalent.

Not:

```text
reflection
↓
field lookup
↓
invoke
```

without need.

---

# 11. ZERO UNNECESSARY DISPATCH

If the compiler knows the target of a call, it must use the most direct form possible.

For example:

```text
INVOKESTATIC
INVOKESPECIAL
INVOKEVIRTUAL
INVOKEINTERFACE
```

must be chosen according to the real semantics.

Do not create artificial dynamic dispatch.

The same principle applies to Native and JS.

---

# 12. ABSTRACTIONS MUST DISAPPEAR

High-level constructs must not necessarily exist at runtime.

This applies to:

* loops;
* ranges;
* lambdas;
* closures;
* pipelines;
* pattern matching;
* properties;
* interpolation;
* collections;
* generics;
* extension-like syntax;
* syntactic sugar;
* compact constructors;
* other future abstractions.

Mandatory question:

> Does this abstraction still need to exist after compilation?

If not:

```text
eliminate.
```

---

# 13. TYPE SYSTEM AS A PERFORMANCE TOOL

The type system does not exist only to detect errors.

It also provides information for optimization.

The compiler must know:

```text
type
subtype
mutability
escape
dispatch
future nullability
future generic specialization
```

when that information is available.

The more semantically safe information exists at compile-time, the less work needs to be done at runtime.

---

# 14. OPTIMIZATION-ORIENTED IR

The Kof IR must allow:

* constant folding;
* dead code elimination;
* unreachable code elimination;
* control-flow simplification;
* type propagation;
* branch simplification;
* allocation analysis;
* future escape analysis;
* future inlining;
* future specialization;
* future scalar replacement;
* loop optimization;
* dispatch optimization.

It is not necessary to implement all these optimizations immediately.

But the IR architecture **must not prevent their future implementation**.

---

# 15. ESCAPE ANALYSIS

The compiler must be architected to identify objects that do not escape the scope.

Example:

```kof
class Point(
    Int x,
    Int y
)

distance(Point(10, 20))
```

If the object does not escape and its materialization is not semantically necessary, the compiler must in the future be able to represent its values more efficiently.

On the JVM:

```text
Kof analysis
↓
optimized bytecode
↓
HotSpot
↓
further optimization
```

In Native:

```text
Kof analysis
↓
direct machine representation
```

---

# 16. MEMORY SAFETY

Performance without memory safety is not success.

Kof must seek to prevent:

* memory leaks;
* double free;
* use-after-free;
* invalid access;
* memory corruption;
* unlimited growth of temporary structures.

Especially in Native.

Memory management must have sufficiently clear ownership/lifetime to allow future evolution without turning the runtime into a collection of forgotten `malloc()` calls.

---

# 17. STACK SAFETY

The compiler must never introduce artificial recursion.

Loops must remain loops.

```kof
while (...) {
    ...
}
```

must be compiled as iterative control.

Do not turn this into recursive calls.

For legitimate recursion:

* analyze tail position;
* allow tail-call optimization when possible;
* avoid artificial frames;
* detect dangerous patterns when possible.

Objective:

> **No stack overflow caused artificially by the compiler or runtime.**

---

# 18. RESOURCE SAFETY

The same principles must apply to:

```text
files
file descriptors
sockets
threads
locks
native handles
buffers
processes
```

Resources must have a predictable lifecycle.

The language's evolution must allow structures that guarantee cleanup on:

```text
normal completion
return
exception
break
continue
```

when semantically applicable.

---

# 19. BENCHMARKS ARE PART OF THE ARCHITECTURE

Performance cannot be evaluated by feeling.

Create:

```text
benchmarks/
├── micro/
├── algorithms/
├── collections/
├── strings/
├── math/
├── objects/
├── inheritance/
├── interfaces/
├── generics/
├── json/
├── io/
├── concurrency/
├── startup/
├── memory/
├── stress/
└── applications/
```

Each benchmark must have:

```text
input
expected output
implementation
harness
metrics
baseline
```

---

# 20. MICROPERFORMANCE BENCHMARKS

Measure:

* integer arithmetic;
* long arithmetic;
* floating point;
* bitwise;
* comparisons;
* branches;
* loops;
* function calls;
* static calls;
* virtual calls;
* interface calls;
* field access;
* array access;
* allocation;
* generics;
* boxing/unboxing;
* strings;
* exceptions;
* lambdas;
* closures;
* collections.

---

# 21. ALGORITHM BENCHMARKS

Create real cases for:

* sorting;
* binary search;
* hash lookup;
* graph traversal;
* tree traversal;
* matrix multiplication;
* parsing;
* serialization;
* hashing;
* compression;
* JSON;
* IO.

The programs must be semantically equivalent across implementations.

---

# 22. MEMORY BENCHMARKS

Measure:

```text
heap
peak RSS
allocation rate
object count
GC activity
temporary allocations
file descriptors
threads
```

Measuring time alone is not enough.

A program that finishes 10% faster while consuming 5x more memory is not automatically better.

---

# 23. STRESS TESTS

Create:

```text
benchmarks/stress/
```

Test:

### CPU

Prolonged executions.

### Memory

Millions of allocations.

### Collections

Large volumes of:

```text
insert
lookup
remove
iteration
```

### Strings

Large volumes of:

```text
concat
split
replace
search
parse
```

### Concurrency

Large quantities of:

```kof
spawn
```

and in the future:

```kof
await
```

### IO

Large volumes of:

```text
open
read
write
close
```

### Exceptions

Large volumes of:

```text
throw
catch
finally
```

### HTTP

High volume of requests.

Measure:

```text
requests/sec
p50
p95
p99
CPU
memory
```

---

# 24. LONG-RUN TESTS

Create tests that keep applications running for long periods.

The objective is to verify:

```text
bounded memory growth
bounded resource usage
stable throughput
stable latency
```

Detect:

* memory leaks;
* descriptor leaks;
* thread leaks;
* unexpected heap growth;
* progressive degradation;
* latency growth.

---

# 25. PERFORMANCE REGRESSION

Each version must have a baseline.

Example (historical baseline 0.2.6-beta, 08/27/2026 — `mvn test` 810, golden 16/16):

```text
Kof 0.2.6-beta

sort:       42 ms
json:       17 ms
startup:    38 ms
memory:     12 MB
benchmarks: 37 in 17 categories (kof bench PASS, baseline jvm/native/js)
```

If a change produces:

```text
sort: 61 ms
```

the CI must flag:

```text
PERFORMANCE REGRESSION
```

Use thresholds and statistical analysis to avoid false positives.

Significant regressions must be investigated.

---

# 26. BENCHMARK CI

Create:

```text
.github/workflows/benchmark.yml
```

Pipeline:

```text
compile
 ↓
run
 ↓
validate output
 ↓
collect metrics
 ↓
compare baseline
 ↓
report regression
```

Benchmarks may be informative on PRs and blocking when a regression exceeds a significant limit.

---

# 27. MULTI-TARGET PARITY

The targets:

```text
Kof/JVM
Kof/Native
Kof/JS
Kof/Script
```

must share:

```text
frontend
type system
semantic analysis
IR
```

when possible.

What must change are the specific needs of each backend/runtime.

---

# 28. MULTI-TARGET BENCHMARK

Relevant benchmarks must compare:

```text
Java
Kof/JVM
Kof/Native
Kof/JS
Kof/Script
```

The objective is not to produce marketing.

The objective is to answer:

```text
where is Kof faster?
where is it slower?
why?
which component is responsible?
```

Every relevant regression must become a technical investigation.

---

# 29. JAVA → KOF

One of Kof's strategic objectives is to allow migration of Java systems.

When porting:

```text
Java
 ↓
Kof
```

the result must not simply preserve the incidental overhead of the original Java code.

The compiler must take advantage of:

```text
type information
control-flow information
ownership/lifetime information
semantic information
```

to produce:

```text
same semantics
+
less code
+
fewer runtime abstractions
+
fewer allocations
+
better representation
```

---

# 30. PORTABILITY DOES NOT MEAN PRESERVING INEFFICIENCY

If the original Java has:

```java
new Iterator(...)
```

but Kof semantics allow a direct loop:

```kof
for (x in values) {
    process(x)
}
```

the compiler must not preserve the iterator just to maintain a structural equivalence.

The necessary equivalence is:

```text
semantics
```

not:

```text
internal implementation
```

---

# 31. DEBUG AND PERFORMANCE

There must be distinct profiles:

```text
debug
release
```

Debug must have:

* source mapping;
* line information;
* local variables;
* metadata;
* stack traces;
* observability.

Release must have:

* optimizations;
* lower overhead;
* metadata only when necessary.

The existence of debugging cannot force the final program to carry unnecessary overhead.

---

# 32. RUNTIME DEBUGGING

Plan a Kof-native runtime debugging infrastructure.

The runtime must in the future allow:

```text
breakpoint
step into
step over
step out
continue
pause
stack trace
locals
fields
threads/tasks
exceptions
watch expressions
```

Without turning the program into an interpreter.

The program remains compiled.

The debugger talks to the running process.

Conceptual architecture:

```text
Kof Editor
    │
    │ Debug Protocol
    ▼
Kof Debug Adapter
    │
    ▼
Kof Runtime Debug Interface
    │
    ├── JVM
    │
    ├── Native
    │
    └── JS
```

On the JVM, integrate with existing mechanisms when possible.

In Native, create its own layer of debug metadata/protocol.

In JS, integrate with DevTools/Node when possible.

---

# 33. DEBUGGING IN THE KOF EDITOR

The Kof Editor must in the future be able to:

```text
open project
 ↓
build
 ↓
run
 ↓
debug
```

with:

* breakpoints in Kof code;
* line-by-line execution;
* variable inspection;
* stack trace;
* expression evaluation;
* threads/tasks;
* exception breakpoints;
* console;
* restart;
* attach.

The editor must not know the backend's internal details.

It must speak to a common interface:

```text
Kof Debug Protocol
```

and the backend/runtime provides the specific implementation.

---

# 34. PROFILING

Plan in the future:

```text
kof bench
kof profile
kof inspect
```

Example:

```text
kof bench app.kf
```

must present:

```text
startup
throughput
latency
CPU
memory
allocations
GC
```

`kof profile` integrates appropriate tools per target:

### JVM

* ✅ **JFR — implemented (`kof profile --methods`)** — the JVM's own flight recorder
  samples `jdk.ExecutionSample` stack traces; `kof profile --methods app.kf` prints the
  hot methods with the **Kof source line** (the LineNumberTable maps the bytecode back
  to the `.kf`), with no external tool. Honest note when the recording is too short for
  a sample (never a silent empty list).
* async-profiler / JVM tooling — external, for allocation/lock/flamegraph depth.

### Native

* ✅ **honest gap (`kof profile --methods --target native`)** — method-level sampling on
  native needs `perf record`, whose kernel `perf_event` access is gated by
  `/proc/sys/kernel/perf_event_paranoid`; where the sysctl forbids it (measured `=4` on
  the maintainer's host) there is no in-house substitute, so the CLI refuses naming perf
  **and the measured sysctl** (R6) instead of pretending to sample.
* perf / sampling profiler / native tools (external).

### JS

* ✅ **Node CPU profiler — implemented (`kof profile --methods --target js`)** — the
  emitted module runs under Node's own `--cpu-prof` (part of Node, no external tool) and
  the emitted `.mjs.map` maps the sampled JavaScript line back to the **Kof source line**
  (the JS counterpart of the JVM LineNumberTable). Node internals are filtered; a host
  without Node is an honest failure, never a fake profile.
* V8/DevTools — external, for allocation/flamegraph depth.

The objective is to make it possible to discover **why** Kof is slow, and not just to know that it is slow.

---

# 35. THE STANDARD LIBRARY ALSO NEEDS TO BE FAST

It is no use for the compiler to be fast and the stdlib to be an anchor.

APIs such as:

```text
kof.core
kof.collections
kof.io
kof.time
kof.json
kof.concurrent
```

must be designed considering:

* allocations;
* cache locality;
* syscall count;
* boxing;
* dispatch;
* memory usage;
* throughput;
* latency.

The public API may be simple.

The internal implementation must be efficient.

---

# 36. IO

IO must have cross-platform abstractions without hiding important costs.

Targets:

```text
Kof/JVM
Kof/Native
Kof/JS
Kof/Script
```

must have consistent APIs.

The backend chooses the appropriate implementation.

Do not create a different API just because each platform internally uses a different technology.

---

# 37. CONCURRENCY

Concurrency must prioritize:

* low overhead;
* safety;
* predictability;
* absence of leaks;
* correct lifecycle;
* absence of abandoned threads.

The language must not directly expose implementation details when they are not necessary.

Example:

```kof
spawn processarFila()
```

is preferable to forcing the programmer to handle:

```text
Thread
Runnable
ExecutorService
Future
```

The abstraction must hide the ceremony, not hide an absurd cost.

---

# 38. EXCEPTIONS

Exceptions must be tested under load.

Measure:

```text
throw/catch latency
allocation
stack usage
nested exceptions
finally
propagation
```

Exceptions must not produce state corruption.

In Native, the evolution of the unwinding mechanism must preserve:

```text
correctness
cleanup
stack integrity
resource safety
```

---

# 39. THE COMPILER MUST NOT INTRODUCE RESOURCE BUGS

No backend may introduce:

```text
memory leak
double free
use-after-free
descriptor leak
thread leak
stack corruption
```

as a consequence of a normal language construct.

If a feature cannot yet guarantee this:

```text
document
test
limit
```

but do not hide the limitation.

---

# 40. DEFINITION OF DONE

A feature is not ready just because:

```text
it compiles
```

When applicable, it must have:

```text
Parser
Semantic Analysis
Type System
IR
JVM Backend
Native Backend
JS Backend
Script support
Unit Tests
E2E Tests
Documentation
Benchmark
Stress Test
Memory Test
Resource Test
Debug metadata
```

And it must preserve:

```text
correctness
+
performance
+
resource safety
+
stack safety
+
debuggability
```

---

# 41. RULE FOR NEW FEATURES

Every new feature must answer:

### 1. Does the code become simpler for the programmer?

### 2. Is the semantics still statically verifiable?

### 3. Can the abstraction disappear at compile-time?

### 4. What is the runtime cost?

### 5. How many allocations are introduced?

### 6. Is there boxing?

### 7. Is there reflection?

### 8. Is there indirect dispatch?

### 9. Is there memory overhead?

### 10. Is there a risk of memory leak?

### 11. Is there a risk of stack overflow?

### 12. Is there a benchmark?

### 13. Is there a stress test?

### 14. Does it work on the applicable targets?

### 15. Can the debugger correctly represent the execution?

If the answer is bad:

```text
review the architecture.
```

---

# 42. OPTIMIZATION RULE

The compiler must always seek the following transformation:

```text
programmer intent
        ↓
semantics
        ↓
static analysis
        ↓
elimination of abstractions
        ↓
minimum necessary representation
        ↓
efficient code
```

Not:

```text
intent
 ↓
hidden boilerplate
 ↓
objects
 ↓
wrappers
 ↓
reflection
 ↓
gigantic runtime
 ↓
result
```

---

# 43. FINAL RULE

Kof must remove complexity from both sides.

For the programmer:

```text
high intent
↓
little code
↓
high productivity
```

For the machine:

```text
high abstraction
↓
static analysis
↓
optimization
↓
low representation
↓
low overhead
```

The objective is:

```text
                    KOF
                     │
          ┌──────────┴──────────┐
          │                     │
     PROGRAMMER             MACHINE
          │                     │
    less code             less overhead
    less ceremony         less allocation
    type safety           less boxing
    intent                less dispatch
    simplicity            less memory
          │                     │
          └──────────┬──────────┘
                     │
                     ▼
             HIGH PERFORMANCE
```

Kof's philosophy must not be:

> "It's fast enough."

It must be:

> **"If we can do better, we do better."**

Java remains a reference for interoperability and an extremely powerful platform.

But Kof must not copy its accidental limitations.

Kof must use the knowledge it has at compile-time to produce a better representation.

On the JVM:

> **seek to be more efficient than the equivalent Java whenever technically possible.**

In Native:

> **seek to exploit all of the compiler's control to achieve even greater efficiency.**

In JS:

> **generate efficient and idiomatic JavaScript.**

In Script:

> **keep startup and overhead minimal without creating a second compiler.**

On all targets:

> **correctness first, but never accept unnecessary overhead as an architectural requirement.**

Performance, memory safety, resource safety, stack safety, observability and debuggability are part of the language's definition of quality.

They are not finishing touches.

They are Kof.
