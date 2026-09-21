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

Real measurement on `beta-0.5.0` tip — not memory:

1. **Zero JavaFX in code.** `import javafx` / qualified `javafx.` usage across
   `kof-*/src`: **0**; `javafx`/`openjfx` in any `pom.xml`: **0**. The 6
   `src/main` files that match the word contain **comments about the JavaFX
   rule** (the launcher swallowing a `VerifyError`) — correct usage, kept.
   Ratified by the corpus: `training/language/ui.md` — *"There is no JavaFX,
   AWT or GUI dependency in any backend."* → addendum 3 confirmed by
   measurement: Kof never used JavaFX (§6).
2. **`kof.ui` on JVM/Native is no-op handles.** The generated JVM runtime
   surface (`jvm/JvmRuntimeUi.java`: `kof_ui_window_new` returns `1`,
   setters/show are empty bodies). Widget rendering is **KofJS-only**
   (DOM + native webview `bin/kof-webview`, WebKitGTK) —
   `training/language/ui.md` §"Semantics across targets".
3. **`kof.media` exists TODAY, JVM face only.** `KofMedia.java` +
   `jvm/JvmMediaCoreRuntime.java` / `JvmMediaWebRuntime.java`: Bitmap
   open/save, video **metadata** (no frame decode — the app does not decode,
   honest gap noted in the source), audio **WAV** sample ops, mic
   list/record. Gap codes in use: `MEDIA001` (default) and `MEDIA003`
   (`mic_record`) — `KofMedia.gapCode`. There is **no playback pipeline**
   (no `play()`), no mixing, no JS/Native face.
4. **Hard dependency on R3 (FFI/ABI).** A portable graphics/audio stack on
   JVM and Native arrives through the interop front
   (`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` R3; handles/out-buffers = roadmap
   step 2.8.2, `D-R3-3.3` DECIDED 21/09). Until R3 lands, the plan has no
   carrier — it stays in `future/` (R12: no new front before SYSTEMS
   closes).

## 1. Intent surface per domain

**Every item below is the shape of the intention** — short, declarative, what
the user *means*; the platform lowers it per target (§3). None is committed
grammar (rule 11 gate: would a human write exactly this in Kof?). None is a
transcribed foreign API (rules 8/10): there is no `SDL_CreateWindow`, no
`<canvas>`, no toolkit `MediaView` in Kof code — only what a Kof reviewer
would not be embarrassed by.

### 1.1 Window + game loop (`scene`/`frame`)

Today `spawn`/`await` express concurrency; a game is a **frame loop**: the
platform owns the clock (vsync/tick), the user owns `update`+`draw`. The
intention:

```kof
scene "Pong" {
    frame { dt ->
        ball.move(speed * dt)
        clear(black)
        draw(ball)
    }
}
```

- `scene` declares *"this program is an interactive window with a loop"* —
  title, window creation, main-loop wiring and termination belong to the
  platform, never to user code.
- `dt` is the seconds since the previous frame — the deterministic
  game-time idiom; frame rate is a platform detail.
- Candidate alternative (same intent, **zero new syntax** — the existing
  HOF/lambda idiom): `Scene("Pong") { dt -> ... }` — **which form enters the
  language is a rule-6 decision** (§9 Q2); the *concept* (platform-owned
  loop, user-owned step) is not up for renegotiation, because every game
  library in every language forces the user to wire the loop — Kof absorbs
  the mechanism (primary guideline).

### 1.2 2D — sprites and tiles

```kof
var ball = sprite("ball.png")
ball.at(120, 80)
ball.draw()

var level = tilemap("level.png", 16)
level.draw()
```

- `sprite(path)` loads through the platform (codec/format support is the
  platform's problem, same as `image` in `kof.media`); `at`/`draw` are
  intent verbs. Animation, rotation and scale grow from the same verb
  family (`frames(...)`, `turn(...)`, `scale(...)`) — no "SpriteBatch",
  no "renderer.begin()", no atlas bookkeeping in user code (the platform
  batches).

### 1.3 3D — mesh, camera, material (honest scope)

```kof
var cam = camera3d().at(0, 2, 5).lookAt(hero.pos)
draw(scene3d {
    mesh("hero.glb").with(material.stone)
    light.sun()
})
```

- `mesh`/`material`/`camera3d` declare **what** the scene is; GPU upload,
  shaders, batching and draw ordering are **how**, owned by the platform.
- **Honest scope:** 3D is the last slice (§8 3.5) and may legitimately
  **not be promoted** at all if any target cannot reach parity (§2 beats
  R7 here by maintainer order). Loading formats (`glb`/`obj`) goes through
  mature libs — never a home-grown parser of binary scene formats.

### 1.4 Input per frame

Game input is a **snapshot read inside the frame**, not UI event wiring:

```kof
frame { dt ->
    if (keys.down("left")) ship.turn(-speed * dt)
    if (keys.pressed("space")) ship.fire()
    aim.aimAt(mouse.pos)
}
```

- `keys.down` (held) vs `keys.pressed` (this frame) — the two questions a
  game asks; repeat/autorepeat/double-fire debouncing is platform noise
  the idiom removes. Mouse/pointer `pos`; gamepad by intent
  (`pad.stick()`, `pad.pressed("a")`).
- The existing `kof.ui` lambda-events stay for forms; they are **not** the
  game idiom (§9 Q6 fixes the exact surface).

### 1.5 Sound — playback, streams, mix, latency, devices

Canon example (`sound.play("x.ogg")`) grown into the honest game-audio
surface:

```kof
var boom = sound("boom.ogg")        // preloaded → low-latency SFX
boom.play()
var bgm = music("theme.ogg")        // streamed — never loads whole file
bgm.loop()
bgm.stop()
sound("ok.ogg").volume(0.3)
```

- One verb family (`play`/`stop`/`pause`/`volume`) over two platform
  behaviors: preloaded sample vs stream — **chosen by the platform from
  usage, not by ceremony in user code** (no `AudioClip` vs `AudioStream`
  types).
- **Latency contract (measured, not promised):** SFX `play()` from inside a
  frame must be audible without perceptible delay on every target — the
  number is set by measurement per target during slice 3.3 (§9 Q4).
  Mixing/voice budget is the platform's; "channels" only as a limit
  diagnostic, never as user wiring.
- Devices: `audio.devices()` + `audio.device(...)` selection — enumeration
  only where the target legitimately has devices (web = output selection
  where the browser allows; honest `SND00x` where the concept does not
  exist — §5).
- Formats: the **platform** picks the decoder (`ogg`/`mp3`/`wav`...) via
  audited libs (§3) — Kof code names a file, never a codec.

### 1.6 Video — playback component, platform chrome

```kof
Window("Trailer") {
    video("intro.mp4").autoplay()
}
```

- `video` is a `kof.ui` panel-family component — same intent level as
  `Label`/`Button`; the player chrome (controls, seek bar, fullscreen)
  belongs to the platform, never to user code.
- Demux/decode come from mature libs (§3); the existing JVM
  `kof_media_video_*` metadata face grows to playback in slice 3.4. No
  `<video>` tag, no toolkit `MediaView` shape crosses into Kof (rules
  8/9/10).

## 2. Acceptance = FULL multi-target parity

**Maintainer order (addendum 2): for this surface R7's "honest scope per
target" does NOT apply.** A graphics/media feature enters the language
surface only when **every** target — JVM, KofScript, Native (x86-64,
riscv64, aarch64), JS-Web — runs the **same program with the same
behavior**; otherwise the feature is **not promoted at all** (it stays a
gap with a diagnostic, §5 — never a partial surface).

- **"Same behavior" is measured the way the house already proves parity**:
  golden E2E run on every target, byte-for-byte on observable output
  (the `runAll3`/`runAll4` E2ETest family, the cross-arch E2E gates, the
  8-target matrix harness of EG-5). Timing-sensitive surfaces (frame
  loop, audio) are tested under a **virtual clock** (fixed `dt` sequence,
  offline audio mix) so the golden is deterministic.
- **Pixel/audio observability:** the surface must expose a deterministic
  readback for tests (render-to-buffer hash; offline mix-to-buffer) — the
  golden compares the **observable contract**, not a GPU/driver artifact.
  The exact mechanism is measured in slice 3.1 before anything promotes
  (§9 Q4).
- Until parity holds, the target answers with the honest gap code and a
  clear message (R6) — e.g. a JS build of a 3D program says `GFX00x` and
  fails with diagnostics, never a silent black screen.

## 3. Stack per target (interop-first, measure before promising)

R9: the first question is always "does it already exist outside and is it
better?" — and the answer here is yes everywhere. **No home-grown renderer,
no home-grown codec, no home-grown mixer** (permanent non-goal; addendum:
codecs/crypto never homemade). The plan **evaluates and measures** (spike
3.0 below) — it does not marry a lib by filename:

| Layer | Candidates to MEASURE (license × coverage × headless-testability) | Notes |
|---|---|---|
| Portable window/GL/input | **SDL3/SDL2, raylib, GLFW+GL** class | the shape addendum 2 names: one portable layer, per-target bindings — not platform chrome |
| Audio | **miniaudio** (zlib, single-file — fits Native directly), OpenAL-soft, SDL3 audio | mixing/voices owned by the platform |
| Video | **ffmpeg / Libav** (demux+decode) | GPL/LGPL vs the GPLv3 output question — measured, never assumed |
| JS-Web | the browser **is** the platform: WebGL, WebAudio, `<video>` | the platform renders; tags never leak to user code (§7) |

Per target (all via the same Kof verbs of §1):

| Target | Carrier | Measured status today |
|---|---|---|
| JVM | FFI (R3 `foreign`/Panama) to the portable stack — **not** JavaFX/Swing/AWT | 0 JavaFX in code (§0); `kof.ui` JVM = no-op; `kof.media` JVM = data ops only |
| KofScript | same carrier as JVM (in-process, shared runtime) | same |
| Native x86-64 / riscv64 / aarch64 | direct C link of the portable stack (the backend already links/`cc`/cross-as) | no audio/video face today → honest gap until parity |
| JS-Web | lowering to browser APIs (the existing DOM pattern of `kof.ui` KofJS) | widgets render here; no sound/video pipeline in stdlib surface yet |

**Rejected as backends (with reason, not by taste):** JavaFX/Swing/AWT
(addendum 3 + measured §0); `javax.sound` and any JDK-only media API —
correct on JVM but it is the *one-target chrome*, and addendum 2 orders
that JVM reach the idiom through **the same portable stack** as the others;
canvas/HTML/CSS (rules 8/9/10 — §7). **KofC/wasm targets are future:** when
they land they join the same parity gate as additional rows, with
`XXX00x` honesty in the interval.

## 4. The R1 boundary — `kof.sound`/`kof.media` vs official packages

The R1 gate (`scripts/check_stdlib_boundary.sh` + ledger
`scripts/stdlib_boundary.txt`) decides the **namespace layer**, not the
backend weight: base stdlib = *"essential to the platform and small"*;
heavy **domains** go to official packages (born `experimental`).

- **`kof.sound` (+ growing `kof.media` with video playback): core stdlib —
  recommendation.** Media is a platform service in the same class as
  JSON/DB/HTTP/crypto: the *surface* is small (verbs of §1.5/§1.6), the
  heavy lifting lives behind it in the platform, and `kof.media` is
  **already core today** (§0.3). Sound without a game is normal; a
  package install for `sound.play("x.ogg")` would be ceremony.
- **`scene`/`sprite`/`tilemap`/`camera3d` (the gaming surface): official
  package — recommendation** (working name `kof.game`, `experimental`).
  Games are a *domain* (R1's own example list), and the 3D scope is exactly
  the kind of heavy capability the package layer exists for. Parity (§2)
  applies to the package the same way — experimental ≠ excused.
- **The `scene { frame { ... } }` block, if chosen as syntax, is a language
  decision regardless of the R1 answer** — keywords never live in a
  package. A zero-syntax shape (builtins/functions only:
  `Scene("Pong") { dt -> ... }`) is the simplicity-law favorite and stays
  open with §9 Q2.
- Registration order is fixed by R1: the layer line goes to the ledger
  **before** any namespace exists (the gate fails the build otherwise).
  Final call: §9 Q1.

## 5. Honest gap codes + the parity matrix

Every face not served answers with a code and a message (R6 — never
silent, never a fake value). Proposed families, same `XXX00x` style as
`MEDIA001`/`WEB005`/`WASM001`:

| Family | Covers | First codes (examples of the honest face) |
|---|---|---|
| `GFX00x` | `scene`/window, `sprite`, `tilemap`, `draw`, 3D | `GFX001` target without graphics carrier (until R3/stack lands) |
| `INP00x` | per-frame `keys`/`mouse`/`pad` | `INP001` input snapshot not served on this target |
| `SND00x` | sound playback/stream/mix/devices | `SND001` no audio carrier; `SND002` format not decodable on this target (list it, never guess) |
| `VID00x` | `video` playback component | `VID001` no video carrier; `VID002` codec absent — platform problem, user sees the code, not the flag |

The matrix the plan commits to (today's column = §0 measurement; a cell
only turns ✅ when the golden E2E of §2 runs on it; **all four ✅ together
or the feature does not promote**):

| Surface | JVM | Script | Native | JS-Web |
|---|---|---|---|---|
| `scene`/frame loop | `GFX001` (no-op today, §0.2) | `GFX001` | `GFX001` | `GFX001` (DOM has rAF carrier — nearest to green) |
| 2D sprite/tile | `GFX001` | `GFX001` | `GFX001` | `GFX001` |
| 3D mesh/camera/material | `GFX001` | `GFX001` | `GFX001` | `GFX001` |
| input per frame | `INP001` | `INP001` | `INP001` | `INP001` |
| sound play/stream/mix | `SND001` (WAV data ops exist, no playback, §0.3) | `SND001` | `SND001` | `SND001` |
| `video` playback | `VID001` (metadata only, §0.3) | `VID001` | `VID001` | `VID001` |

## 6. ERRADICATION — JavaFX was never Kof and never will be

Addendum 3 converts "migration" into **eradication**: measured inventory of
every `javafx` occurrence (09/21, `grep -rni`), each classified — the only
ones that would ever be "bugs to remove" are real bindings, and there are
none:

| Where | Count | Classification | Action |
|---|---|---|---|
| `kof-*/src/main` code (`import javafx`, `javafx.`) | **0** | — | nothing to eradicate; keeps being true by §2/§3 rejection |
| `kof-*/src/main` comments (6 files) | 6 | comment **about the rule** (VerifyError disguised as the launcher message) | keep — correct usage |
| `kof-*/src/test` comments (12 files) | 12 | same (symptom of §149-family bugs) | keep |
| `pom.xml` dependencies | **0** | — | — |
| `DECISIONS.md` §D-GRAPHICS-GAMING body "JVM=JavaFX" | 1 | **wrong-doc claim in the original decision text** — already corrected **in the same file** by addendum 3 (decision record stands; the correction is the addendum) | no edit — addendum governs |
| `docs/philosophy.md` "WebView/JavaFX in UI code → rejected" | 1 | correct: a **rejection**, matching reality | keep |
| `docs/status.md`, `known-bugs.md` "disguised as JavaFX launcher error" | 10+ | correct: names the **symptom** the 12/09 rule covers | keep |
| `training/language/ui.md` "no JavaFX, AWT or GUI dependency" | 1 | correct statement of fact (matches this measurement) | keep |

Rules that make eradication permanent:

1. **The 12/09 JavaFX rule is not relaxed by anything in this plan:** the
   runtime message `componentes de runtime do JavaFX não foram encontrados`
   is **never benign** — it is the launcher swallowing a real
   `VerifyError`/`ExceptionInInitializerError`; always root-cause and fix
   (a JavaFX path is never "accommodated" — that is a bug in disguise).
2. **Machine guard (proposed, slice 3.0):** one deterministic check that
   fails the build on any `import javafx` / `javafx.` usage / javafx
   dependency under `kof-*/src` (same shape as the R1 stdlib-boundary
   gate) — so "never again" is a gate, not a hope.
3. **Backward compatibility does not protect a JavaFX path** (addendum 3
   (c)): user Kof code never named JavaFX; removing any hypothetical
   binding cannot break a valid Kof program.

## 7. NON-GOALS

- **No HTML, `<canvas>`, `<audio>`, `<video>`, CSS or DOM leakage into user
  code** — the browser is a *backend*, not a language. Precedent: RawView
  #449 (rule 9): user code declares intent; the platform renders.
- **No foreign API transcription:** no `SDL_CreateWindow`, `glfwSwapBuffers`,
  OpenGL enums, toolkit `MediaView`/`MediaPlayer` shapes reaching the Kof
  surface (rules 8/10). The lowering owns them; the user owns verbs (§1).
- **No home-grown renderer, mixer, demuxer or codec** (R9/R10; crypto and
  codecs are never homemade — permanent non-goal).
- **No "Kali in Kof"** (permanent non-goal of the platform invariants).
- **No JavaFX/Swing/AWT as a backend on any target** (§3, addenda 2+3).
- **No partial-parity promotion** (§2 beats R7 here by maintainer order).
- **No front opens** from this document: it stays `future/` until the
  maintainer promotes a slice (three-states rule + R12 + rule 6).

## 8. Slice queue (3.x) with proof criteria

Promotion order; **no slice opens without the maintainer promoting it**
(R12 + rule 6). Each slice ships only with its proof — the house parity
standard: **E2E on the 4 targets, byte-for-byte on the observable
(§2), plus the neighboring full suite green**. A slice that cannot turn the
last target green leaves the feature behind its `XXX00x` gap and does not
promote — "the queue advances, the surface only grows in parity".

| # | Slice | Scope (one line) | Depends on | Proof |
|---|---|---|---|---|
| 3.0 | **measure + guard** | spike SDL-class/raylib/GL + miniaudio/OpenAL + ffmpeg/Libav (license×targets×headless); javafx-binding machine gate; `import javafx` fails build | R3 2.8.2 (handles) | written measurement report (this doc §3 grows the verdict); guard test RED-then-GREEN |
| 3.1 | **window + frame loop + input** | `scene` form (per Q2), vsync-driven loop, per-frame snapshot (`keys`/`mouse`), virtual clock + readback mechanism | 3.0 | 4-target byte-for-byte golden; gap diagnostic honest where not |
| 3.2 | **2D** | `sprite`/`tilemap`/`draw` + batching owned by platform | 3.1 | same, + edges (empty frame, 1×1, off-screen) |
| 3.3 | **sound** | play/stream/mix/volume/devices; latency contract number SET from measurement (Q4) | 3.0 (audio carrier; parallel to 3.1) | offline-mix golden 4 targets; device enumeration honest per target |
| 3.4 | **video** | `video` component playback over `kof.ui`; platform chrome; existing metadata face absorbed | 3.3 + R3 | decode-readback golden 4 targets |
| 3.5 | **3D (scoped)** | `mesh`/`camera3d`/`material` minimal honest set — promoted ONLY if 3.0–3.4 prove the carrier holds parity | 3.1–3.4 | 4-target golden or stays `GFX00x` forever (Q3) |
| 3.6 | **corpus + promotion** | `training/idioms/graphics.md`+`audio.md`, `learn/`, `backend-parity.md`, gap table; doc leaves `future/` | 3.1–3.5 | docs-lang 0/0/0 + conformance cells |

## 9. OPEN QUESTIONS for the maintainer (rule 6)

Not decided here — each is a design decision (frozen semantics / language
surface / R1 registration):

1. **Q1 — R1 boundary:** `kof.sound`+`kof.media` core stdlib and the gaming
   surface as official package `kof.game` (recommendation §4) — or another
   split? Ledger line must be registered before any namespace exists.
2. **Q2 — the `scene`/`frame` form:** block syntax (new grammar) vs.
   zero-syntax builtins (`Scene("Pong") { dt -> ... }` — the
   Simplicity-Law favorite: no keyword, no ceremony). Which reaches the
   surface.
3. **Q3 — 3D promotion:** does 3D ever join the surface (0.5.x/later), or
   stays `GFX00x`-gapped until full 4-target parity is demonstrated?
4. **Q4 — measurable contracts:** the sound latency number and the
   byte-for-byte golden mechanism for pixels/audio (render-to-buffer
   hash, offline mix, virtual clock) — ratified from the 3.0/3.1
   measurements before the first promotion.
5. **Q5 — stack choice after 3.0:** which portable layer (SDL-class vs.
   raylib vs. raw GL), which audio lib, ffmpeg vs. Libav — including the
   **license question** (GPL/LGPL stack under the GPLv3 output).
6. **Q6 — input surface:** per-frame snapshot (`keys.down`/`pressed`) vs.
   events vs. both; exact interaction with the existing `kof.ui`
   lambda-events (which stays the form idiom, which is the game idiom).
7. **Q7 — promotion timing:** when (if ever) the first slice leaves
   `future/` — R12 holds this behind SYSTEMS/1.0 unless she orders
   otherwise (as `D-UNIVERSAL` did once before).
8. **Q8 — the existing JVM-only `kof.media` data face** (image/WAV/video
   metadata, §0.3): keep additive on top of the parity stack, or rebase it
   onto the stack during 3.3/3.4 — user Kof programs must not break either
   way (compat promise is to programs, addendum 3 (c)).
