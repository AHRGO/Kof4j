[English](CODE_OF_CONDUCT.md) | [Português](CODE_OF_CONDUCT.pt_BR.md)

# Kof Code of Conduct and Collaboration Philosophy

> **Source of truth:** Mel Santos ([@aminadojava](https://pt.linkedin.com/in/aminadojava))
> — official maintainer of the Kof ecosystem (Koflang, Kof4J, Kof Native, Kof Editor).

## Development philosophy

Kof is **open source (GPLv3)** and its development is actively conducted
with **autonomous AI agents publicly documented** inside the repository
itself. But — and this is essential — **Kof was not made by AI**.

Artificial intelligence does not take the language "out of nowhere" and does not
replace conceptual engineering. AI is a **tool** under the maintainer's reins:
it accelerates and optimizes the build, but the architecture, the semantics and
the direction of the project are decisions of **human engineering** (the maintainer).

For this reason, collaboration on Kof follows clear rules:

1. **Technical skepticism, not hype.** Automation without criteria masks a lack of
   quality and immediacy. "Root programming" holds: rigor in compilation,
   hands-on experience and robust code — never rushed delivery.
2. **No hallucination.** Whoever "thinks" something compiles without testing should stop and
   compile before delivering. Hallucinated syntax is worse than verbose syntax.
3. **Surgical documentation of errors.** The repository keeps a dedicated
   **regression file**: for each bug, the **root cause** and the **smallest piece of
   code that reproduces it** — including regressions that the maintainer herself
   introduced. No hiding what is broken (see `docs/bugs-and-gaps/known-bugs.md`).
4. **Shielding against pollution.** Explicit disclaimers and naming rules
   exist to prevent AIs and generative tools from mixing the language
   with terms foreign to the domain and polluting the project's history.
5. **Contribution through technical discussion.** Contributions do not come in through
   open and disorderly PRs; they come in through **deep technical debates** about the
   real engineering problems (e.g.: floating-point overflows in
   pre-beta-0.3.0), in which experienced developers debate **conceptual
   solutions** before any line of code.
6. **Every PR comes with a related issue.** "Loose" pull requests
   do not enter. Every proposed change must reference an open issue that
   justifies it — traceability is law, not a preference.

---

# Collaborative Development Norms (pre-beta 0.3.0)

> **In effect from the next beta.** The norms below are being tested
> during pre-beta 0.3.0 and will be adjusted in the coming days, as
> experience evolves. Ecosystem site: <https://koflang.github.io>.

## What changed

We have started to **accept contributions assisted by AI agents**, as long as
they meet the project's architecture, philosophy, syntax and quality
requirements. Using AI **is not the problem** — delivering code you do not
understand is.

> Relation to rules 1–6 above: **rule 5** (technical debate before of
> operator change, precedence, API contract. What changes is the flow
> of contributions in general: PRs are now welcome (produced by a person,
> agent or both), **keeping rule 6** — every PR with a related issue.

## The three principles

1. **Accelerate, do not outsource thinking.** Use the agents to speed up the
   work. The agent can write the code; **the one who needs to understand that
   code is you.**
2. **The quality standard does not change.** Every Pull Request continues to be
   carefully reviewed before any merge. It does not matter whether the
   contribution was produced by a person, an agent or both —
   the criterion is the same.
3. **Responsibility before permissiveness.** The idea is not to prevent AI. It is
   to discover how to use AI responsibly in a real software project.
   Quality and reliability remain the main goal.

## The two coexistence files

| File | Role | Content |
|---|---|---|
| **`AGENTS.md`** | long-term memory of the agents | project context, coexistence rules, architecture, philosophy and coordination among multiple agents — documented and updated frequently |
| **`DOING.md`** | agents' log | everything the agents are doing is recorded there — it allows tracking work in progress and reducing conflicts between different agents working on the project |

## The experiment

This is a deliberate test: discovering how far we can evolve the collaborative
development process **without compromising the quality and reliability of
Kof**. If pre-beta 0.3.0 brings positive results, the
norms may become progressively less restrictive regarding the use of
agents in the future.

---

# Contributor Code of Conduct

## Our Pledge

We as members, contributors and leaders pledge to make participation in our
community a harassment-free experience for everyone, regardless of age, body
size, visible or invisible disability, ethnicity, sexual characteristics, gender
identity and expression, level of experience, education, socio-economic status,
nationality, personal appearance, race, religion or sexual identity and
orientation.

We pledge to act and interact in ways that contribute to an open, welcoming,
diverse, inclusive and healthy community.

## Our Standards

Examples of behavior that contributes to a positive environment:

* Demonstrating empathy and kindness toward other people
* Respecting differing opinions, viewpoints and experiences
* Giving and gracefully accepting constructive feedback
* Accepting responsibility and apologizing to those affected by our mistakes,
  and learning from the experience
* Focusing on what is best not just for us as individuals, but for the community as a whole

Examples of unacceptable behavior:

* The use of sexualized language or imagery, and sexual attention or advances of
  any kind
* Trolling, insulting or derogatory comments, and personal or political
  attacks
* Public or private harassment
* Publishing others' private information, such as a physical or email
  address, without their explicit permission
* Other conduct which could reasonably be considered inappropriate in a
  professional setting

## Enforcement Responsibilities

Community leaders are responsible for clarifying and enforcing our standards of
acceptable behavior and will take appropriate and fair corrective action in
response to any behavior that they consider inappropriate, threatening,
offensive or harmful.

Community leaders have the right and responsibility to remove, edit
or reject comments, commits, code, wiki edits, issues and other
contributions that are not aligned with this Code of Conduct, and will communicate
the reasons for moderation decisions when appropriate.

## Scope

This Code of Conduct applies in all community spaces, and also
when an individual is officially representing the community in public spaces.
Examples of representing the community include using an official email
address, posting via an official social media account, or acting as an
appointed representative at online or in-person events.

## Enforcement

Instances of abusive, harassing or otherwise unacceptable behavior — or
any question of technical conduct/centralization — may be reported to the
official maintainer and single source of truth, Mel Santos
([@aminadojava](https://pt.linkedin.com/in/aminadojava)).
All reports will be reviewed and investigated promptly and fairly.

All community leaders are obligated to respect the privacy and security of
those who report any incident.

## Enforcement Guidelines

Community leaders will follow these Community Impact Guidelines in
determining the consequences for any action in violation of this Code of
Conduct:

### 1. Correction

**Community Impact**: Use of inappropriate language or other behavior
considered unprofessional or unwelcome in the community.

**Consequence**: A private, written warning from community
leaders, with clarity about the nature of the violation and an explanation of why
the behavior was inappropriate. A public apology may be requested.

### 2. Warning

**Community Impact**: A violation through a single incident or a
series of actions.

**Consequence**: A warning with consequences for continued behavior. No
interaction with the people involved, including unsolicited interaction with
those enforcing the Code of Conduct, for a specified period of time.
This includes avoiding interactions in community spaces and also in external
channels such as social media. Violating these terms may lead to a temporary
or permanent suspension.

### 3. Temporary Suspension

**Community Impact**: A serious violation of community standards,
including sustained inappropriate behavior.

**Consequence**: A temporary suspension from any kind of public interaction or
communication with the community for a specified period of time. No public or
private interaction with the people involved, including unsolicited interaction
with those enforcing the Code of Conduct, is allowed during that
period. Violating these terms may lead to a permanent suspension.

### 4. Permanent Suspension

**Community Impact**: Demonstrating a pattern of violation of community
standards, including sustained inappropriate behavior, harassment of an
individual, or aggression toward or disparagement of classes of individuals.

**Consequence**: A permanent suspension from any kind of public interaction
within the community.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant][homepage],
version 2.0, available at
https://www.contributor-covenant.org/version/2/0/code_of_conduct.html.

The Community Impact Guidelines were inspired by [Mozilla's code of conduct
enforcement ladder](https://github.com/mozilla/diversity).

[homepage]: https://www.contributor-covenant.org

For answers to common questions about this code of conduct, see the FAQ at
https://www.contributor-covenant.org/faq. Translations are available at
https://www.contributor-covenant.org/translations.
