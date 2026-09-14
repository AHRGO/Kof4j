[English](PHILOSOPHY.md) | [Português](PHILOSOPHY.pt_BR.md)

# Kof Philosophy Manifesto

## Koffie voor iedereen.

Software should begin with a human intention and end with a machine doing exactly what was asked.

Between those two points lies complexity.

Part of it is inevitable.

Much of it, however, is created by the very tools we use to build software.

Kof is born to question that complexity.

Not to pretend that complex systems are simple.
Not to hide how the machine works.
Not to turn programming into magic.

Kof is born to remove what does not need to be in the way.

**Less accidental complexity. More intention.**

---

## I. Intention comes first

Programmers should describe the problem they are solving, not fight the tool to express it.

The language should bring code closer to intention.

When someone reads a Kof program, they should be able to understand not only how it works, but above all **what it intends to do**.

Boilerplate is not depth.

Configuration is not architecture.

Ceremony is not engineering.

Abstraction is not automatically quality.

If a piece of code exists only because the tool requires it to exist, we should question its necessity.

---

## II. Simplicity does not mean limitation

Kof follows the spirit of KISS:

**Keep It Simple.**

But simplicity does not mean removing capability.

Real systems are complex.

Networks are complex.
Distributed systems are complex.
Concurrency is complex.
Hardware is complex.
Memory is complex.
Security is complex.

Kof does not intend to hide that reality.

It intends to prevent the language from adding complexity where the problem added none.

**Complexity should exist because the problem demands it, not because the tool demands it.**

---

## III. The machine matters

Abstraction should not mean ignorance.

Kof can let someone write high-level software without constantly thinking about registers, pointers, syscalls or the details of a CPU.

But those details continue to exist.

And they should remain accessible when they matter.

The programmer should be able to rise in abstraction without losing the ability to descend to the machine.

We want a language that is comfortable for building a web application and close enough to the system to build low-level software.

**High-level does not have to mean alienated from the hardware.**

---

## IV. Small things, well done

Kof follows the spirit of Unix:

**do one thing and do it well.**

Tools should have clear responsibilities.

Components should be composable.

Systems should be built from parts that can exist independently.

We do not want a single gigantic abstraction that solves everything.

We want components that can work together.

A compiler should compile.

A debugger should debug.

A server should serve.

A library should solve its problem.

And when several tools need to work together, they should do so without requiring the developer to rebuild the world to connect them.

**Composition is a form of simplicity.**

---

## V. There is no sacred architecture

Kof should not decide for the developer whether their system needs to be monolithic, distributed, modular, service-oriented or any other architecture.

A small system can be a monolith.

A large system can be divided.

An application can have frontend and backend in the same project.

Another can separate everything into independent services.

Architecture should respond to the problem.

Not to fashion.

Kof provides tools for building systems.

Not an architectural religion.

---

## VI. The platform should work for the programmer

The computer is excellent at mechanical work.

We should use it for that.

The programmer should not have to manually manage every detail that can be safely determined by the compiler, runtime or tool.

Trivial imports.
Boilerplate.
Wiring.
Repetitive configuration.
Mechanical generation.
Administrative details.

When the machine can resolve something without compromising clarity, safety or control, **let the machine do the work.**

Automation should remove bureaucracy.

Not autonomy.

---

## VII. Power without ceremony

Kof does not seek to be powerful by adding thousands of concepts.

It seeks to be powerful because its fundamental concepts can compose.

A language does not need a different solution for each problem when it has sufficiently good foundations.

We want expressiveness without verbosity.

Flexibility without chaos.

Abstraction without imprisonment.

Performance without requiring every program to be written as machine code.

**Power should not require ceremony.**

---

## VIII. The stdlib should be big. The program should not.

A useful language inevitably accumulates tools.

Kof should have a standard library capable of handling real applications: networking, data, concurrency, systems, automation, security, interfaces and other areas that arise.

But a big stdlib should not mean big programs.

The compiler should know what is being used and produce only what is necessary.

The developer should not have to manually manage every internal dependency.

**The platform can be enormous. The executable should be as small as the problem allows.**

This holds from a server to a microcontroller.

---

## IX. One ecosystem, different machines

The intention of a program should not be tied to a single platform when that is not necessary.

JVM.

Native.

JavaScript.

WASM.

ARM.

RISC-V.

Other platforms that do not even exist yet.

Kof should seek to preserve the semantics of the language while adapting its execution to the characteristics of each environment.

We do not pretend that all machines are the same.

We respect their differences.

But we also do not accept that each platform difference forces the developer to relearn how to express the same intention.

**One intention. Different forms of execution.**

---

## X. Interoperability is freedom

No language exists alone.

Kof should converse with the existing world.

Java.

C.

Operating systems.

Native libraries.

JavaScript.

Other languages.

Other platforms.

FFI and interoperability are not concessions.

They are freedom.

A language that requires abandoning everything that came before in order to be used is imposing its ecosystem on the developer.

Kof should let the developer choose when to start something new and when to take advantage of what already exists.

---

## XI. Code should be noble

Noble code is not sophisticated code.

It is code that has a reason to exist.

It is code that expresses intention.

It is code that does not create abstractions just to look architectural.

It is code that does not hide important complexity behind magic.

It is code that can be read.

Questioned.

Tested.

Optimized.

Debugged.

Replaced.

Noble code is not the smallest possible code.

It is the **smallest code necessary to correctly represent the problem.**

---

## XII. Humans first

Kof is a tool for people.

The language should be readable by humans before anything else.

Documentation should be understandable.

Errors should explain the problem.

Tools should help, not hinder.

The language should be consistent enough that a person can form a reliable mental model of the system.

And precisely because it is well structured for humans, Kof can also be understood by automated tools.

LLMs, agents, IDEs, compilers, analyzers and other tools can work better with a language whose intention is explicit.

**LLM-friendly should be a consequence of being human-friendly.**

Never the other way around.

---

## XIII. AI is a tool, not an authority

Kof can be developed with artificial intelligence.

Kof can have agents.

Kof can be used by agents.

But no AI has authority over the philosophy of the language.

AI can implement.

It can test.

It can review.

It can document.

It can suggest.

It can accelerate.

The intention, the architecture and the principles remain a human responsibility.

**Automating the implementation does not mean outsourcing the thinking.**

---

## XIV. Do not hide what matters

Kof should hide mechanical work.

It should never hide important decisions.

If an operation has relevant cost, relevant behavior or relevant consequence, the developer should be able to understand it.

If something can cause a serious failure, it should be observable.

If something affects performance, it should be analyzable.

If something affects security, it should be auditable.

Good abstraction removes noise.

Bad abstraction removes understanding.

**Kof should hide details. Never hide the truth.**

---

## XV. Evolution without dogma

Kof does not have to get everything right in the first version.

A living language needs to be able to change.

We need to experiment.

Measure.

Break.

Fix.

Remove.

Simplify.

An old decision does not become correct just because it was made earlier.

Legacy code is not a justification for perpetuating complexity.

Compatibility matters.

Stability matters.

But when a fundamentally bad decision blocks the evolution of the language, we should have the courage to correct it.

**The past informs the project. It does not govern the project.**

---

## XVI. The language belongs to those who use it

Kof should not be built around an elite of specialists.

It should be powerful enough for specialists and coherent enough for those just starting out.

We do not want a language that infantilizes beginners.

Nor do we want a language that demands suffering as proof of competence.

Technical knowledge should be earned because the problem is difficult.

Not because the tool decided to create artificial obstacles.

**Programming should be hard when the problem is hard. Not when the language is bad.**

---

## XVII. Build real software

Kof does not exist to win a language benchmark war.

It does not exist to replace all languages.

It does not exist to create yet another pretty syntax for the same problems.

It exists to build software.

Applications.

Services.

Tools.

Systems.

Interfaces.

Infrastructure.

Embedded software.

Experiments.

Small things.

Enormous things.

Things we do not yet know we will need to build.

The best proof of a language is not how many features it has.

It is **what people can build with it.**

---

# The central principle

When in doubt about a decision in Kof, we should return to the fundamental question:

> **Does this reduce the distance between the programmer's intention and the software executed by the machine, or does it increase that distance?**

If it reduces it, we are probably on the right path.

If it increases it, we need a good reason.

Kof does not seek to eliminate the complexity of software.

It seeks to place each complexity where it truly belongs.

In the problem, when it is necessary.

In the tool, when it can be automated.

And never in the code just because nobody stopped to question it.

---

# Kof

KISS reminds us to keep things simple.

Unix reminds us to build systems through small, clear and composable parts.

Engineering reminds us to respect the machine.

Experience reminds us that abstractions have costs.

And Kof adds a question:

> **What was the intention?**

From it, we build a language.

A platform.

An ecosystem.

A community.

Not to write more code.

But to spend less thought fighting the code that should not exist.

**Less accidental complexity.**
**More intention.**
**More control.**
**More software.**

**Koffie voor iedereen.**
