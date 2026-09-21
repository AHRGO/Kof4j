[Português](secrets-plan.pt_BR.md)

# Secrets — `Secret`, `KeyHandle` and enforced redaction (design plan · Stage 5 / tracker 3.6)

**Status:** Plan (design) — ZERO code landed (21/09, docs/development audit lane by maintainer
request). **Promoted 21/09; face 1 (`Secret`) authorized (`D-SECRETS`)** — incremental, each face
with its own proof and a rule-6 vote before anything touches the language or the current
`secrets.*` signatures (frozen semantics 0.2.6-beta); `KeyHandle`/redaction follow face by face.
**Source:** `docs/development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` line 3.6 (`Secret`/`KeyHandle`
pending, Stage 5) · gap analysis `docs/stdlib/security.md` (rows "Secrets (env): NONEXISTENT",
"Secrets in logs: NO PROTECTION") · `docs/bugs-and-gaps/ecosystem-coverage.md` §kof.security.

## 1. Objective

Today a secret is just a `String`. `secrets.get("API_KEY")` returns the raw value and it then
flows — invisible to the compiler — into `println`, interpolation, `kof.json`, logs, exception
messages and entity fields. One leak in one of those five paths is a production incident.
Stage 5 makes the **type-level default** for credentials non-exportable: a value that cannot be
printed, serialized or concatenated without an explicit, greppable, auditable act.

## 2. Proposal (candidate syntax — each item is its own vote)

**P1 — value type `Secret`.**
- `Secret.of(text)`, `Secret.fromBytes(b: Int[])`; `secrets.get(name)` **returns `Secret`**
  (breaking — the vote; the alternative is a new face `secrets.secret(name)` and freezing
  `get` as the legacy raw path).
- `reveal(): String` — the ONLY export; explicit, one-word greppable in every audit.
- `redacted(): String` → `"***"` (never the length, never a prefix — a prefix is a leak
  budget). Fixed format on every target: `Secret(*** )`.
- `equals` constant-time (wraps the existing `security.constantTimeEquals`); no `<`/`>`;
  no `hashCode` on content (identity only) so a map cannot index secrets.
- `println(s)` and `"…\(s)"` print the REDACTED form; they never call `reveal`.

**P2 — enforced redaction (three layers).**
- Compile-time: `Secret` + string ops resolve to the redacted face; a `reveal()` result
  assigned to a logged path is lint-warned (SECN00x family, same honest-code style as
  SEM099).
- Runtime: `kof.json` and the log faces detect `Secret` and emit `Secret(*** )` — a value
  that reaches a serializer by reflection (interop) is redacted at write time, not skipped.
- Corpus: training pages for `secrets` gain the matrix row "how a Secret PRINTS" (the
  §388-B lesson: an undeclared print format IS a divergence — this plan declares its own).

**P3 — `KeyHandle`.** A named key that never exposes bytes to the guest:
- sources: keystore entry / PEM file / vault env (later, separate lane);
- ops only in the crypto algorithms that exist today (`hmacSha256`, `sign`, `verify`,
  `encryptAesGcm`, `decryptAesGcm`, `encryptChacha20`, `decryptChacha20` — all take an
  optional `key: KeyHandle` overload next to the raw-bytes form);
- `rotate()` returns a NEW handle and revokes the old one (revoked handles fail with
  SECN00x at use, not with an exception in Kof semantics);
- `jwt.create(claims, key)` accepts `KeyHandle` directly — the HS256 secret stops being a
  `String` in the caller's hands.

## 3. Per-target semantics (the parity table, R6)

| target | P1 backing | P2 redaction | P3 handles |
|--------|-----------|--------------|------------|
| JVM | immutable `String` backing (honest: no zeroing — see §4) | serializer hook + `toString()` override | keystore/PEM via FFI-light reader in kof-runtime |
| Script | same (interpreter holds the boxed type) | same | same |
| JS | host-side JS string kept OUT of guest reachables (`reveal` returns through the bridge, never stored) | `JSON.stringify` guard at host | host crypto (SubtleCrypto/openssl binding) |
| Native | buffer that CAN be zeroed — API designed as superset: wipe() is a no-op elsewhere | serializer hook | deferred: SECN gate until keystore exists |

## 4. Non-goals (this plan is NOT)

memory zeroing guarantees (impossible on JVM/JS strings — saying so is the point), KMS/vault
client integrations, secret injection into Makealive-managed infra (that stays env/file per
D-MAKEALIVE-CLI; a `Secret` may be the value read from the env), TPM/HSM, automatic rotation
schedulers, ORM entity-column encryption (P3×orm needs its own vote).

## 5. How it plugs into the existing code

`KofSecurity.java` already owns the namespace table (`"secrets", List.of("get", "redact")`) —
P1 adds the `Secret` value type in the typer/lowering (`Type.ClassType` face, no new parser
grammar needed unless interpolation gains a `reveal` form: that is the rule-6 item), P2 hooks
`kof.json` (`KofJson`) and the log emitter, P3 adds overloads without removing the raw
signatures. Docs touched on landing: `stdlib/security.md(+pt_BR)` (replace the two "no
protection" rows), LSP-A signature tables, training `secrets` page, `ecosystem-coverage`.

## 6. Gate and order

P1+P2 ship before the 1.0 EXIT GATE (they close a "NO PROTECTION" row of the gap analysis and
protect the public surface); P3 after 1.0. Each of P1/P2/P3 gets: rule-6 vote in
`DECISIONS.md` (EN+PT), a version bump, and the golden-corpus battery green on all four
targets. This document changes zero behavior; the audit-lane tracker line 3.6 stays 🟡 until
the first vote.
