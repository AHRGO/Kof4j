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
- Candidate alternative (same intent, expression instead of block):
  `run onFrame { dt -> ... }` — **which form is a rule-6 decision**
  (§9 Q2); the *concept* (platform-owned loop, user-owned step) is not up
  for renegotiation because every game library in every language forces the
  user to wire the loop — Kof absorbs the mechanism (primary guideline).

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

## 6. ERRADICATION — JavaFX was never Kof and never will be

## 7. NON-GOALS

## 8. Slice queue (3.x) with proof criteria

## 9. OPEN QUESTIONS for the maintainer (rule 6)
