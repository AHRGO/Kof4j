[English](graphics-gaming-plan.md) | [Português](graphics-gaming-plan.pt_BR.md)

# Graphics, Gaming & Media — the Kof intent surface (future plan)

**Status:** Plan (design only) — **zero code**; lives in `future/` until the
maintainer promotes a slice (three-states rule + R12).
**Source:** `DECISIONS.md` §D-GRAPHICS-GAMING (20/09) + addendum (media
som+vídeo) + addendum 2 (sem JavaFX + paridade total) + addendum 3 (Kof nunca
usou JavaFX — eradicação) · `AGENTS.md` rules 8/9/10/11 · JavaFX rule (12/09).

> **Rule of this document:** this is a **plan for the future** — it changes no
> behavior and opens no front. Every syntax below is the **shape of the
> intention**, not committed grammar; the exact surface is a rule-6 decision
> of the maintainer. No lane may attack it without explicit promotion.

## 0. Ground truth measured (09/21)

_(medição real, não memória)_

## 1. Intent surface per domain

### 1.1 Window + game loop (`scene`/`frame`)
### 1.2 2D — sprites and tiles
### 1.3 3D — mesh, camera, material (honest scope)
### 1.4 Input per frame
### 1.5 Sound — playback, streams, mix, latency, devices
### 1.6 Video — playback component, platform chrome

## 2. Acceptance = FULL multi-target parity

## 3. Stack per target (interop-first, measure before promising)

## 4. The R1 boundary — `kof.sound`/`kof.media` vs official packages

## 5. Honest gap codes + the parity matrix

## 6. ERRADICATION — JavaFX was never Kof and never will be

## 7. NON-GOALS

## 8. Slice queue (3.x) with proof criteria

## 9. OPEN QUESTIONS for the maintainer (rule 6)
