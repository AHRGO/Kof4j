English | Português

# Graphics, Games and Media — Kof's Intent Surface

**Status:** Future plan — design only, **zero code**
**Location:** `docs/development/future/`
**Nature:** architecture, contracts, dependencies, implementation strategy, and promotion criteria
**Normative source:** `DECISIONS.md` §D-GRAPHICS-GAMING + addenda recorded by the maintainer
**Main dependencies:** R3 / FFI-ABI, runtime, capability matrix, stdlib boundary, conformance suite
**Implementation status:** not started

> **Fundamental rule:** this document describes a future architectural direction. It does not change the language, add keywords, create namespaces, or open an implementation track.
>
> Every syntax presented in this document is an **intent form**. It exists to demonstrate how an API could express human intent. The definitive form of the language remains subject to the maintainer's decision.

---

# 0. Objective

The objective of this plan is to define how Kof may provide a native surface for:

* 2D graphics;
* 3D graphics;
* windows;
* game loops;
* input;
* sprites;
* tilemaps;
* audio;
* video playback;
* media;
* KofUI integration;
* games;
* interactive graphical applications.

The central characteristic of this surface will be:

> **Kof code declares what it intends to do; the backend decides how to accomplish it.**

A Kof program should not need to know:

* SDL;
* OpenGL;
* Vulkan;
* DirectX;
* WebGL;
* WebAudio;
* WASM;
* JavaFX;
* Swing;
* AWT;
* proprietary APIs for each system;
* device details;
* swapchain;
* framebuffer;
* audio buffer;
* codec;
* demuxer;
* platform event loop.

These mechanisms belong to the backend.

The goal is not to create a new graphics language inside Kof.

The goal is to create an **intent surface**.

---

# 1. Architectural principles

## 1.1 Intent before mechanism

Code should answer:

> What do I want to happen?

and not:

> Which graphics API do I need to call to make this happen?

Example:

```kof
sprite("player.png").at(100, 80).draw()

```

is intent.

Whereas:

```text
createTexture(...)
bindTexture(...)
beginBatch(...)
drawQuad(...)
swapBuffers(...)

```

is mechanism.

The second model should remain hidden from the application.

---

## 1.2 The platform owns the loop

The user should not need to implement:

```text
while (running) {
    pollEvents()
    update()
    render()
    swapBuffers()
}

```

The loop belongs to the platform.

The user provides the logic:

```kof
frame { dt ->
    update(dt)
    draw()
}

```

The backend transforms this into the equivalent model for the target.

---

## 1.3 Foreign APIs do not cross the boundary

The Kof surface should not reproduce foreign APIs.

Not:

```kof
SDL_CreateWindow(...)

```

Not:

```kof
glClear(...)

```

Not:

```kof
canvas.getContext(...)

```

Not:

```kof
MediaPlayer(...)

```

Not:

```kof
javafx.scene...

```

The existence of these technologies in the backend does not mean they are part of the language.

---

# 2. Current measured state

Future implementation must start from the actual state of the repository at `beta-0.5.0`, not from an assumed architecture.

## 2.1 JavaFX

The 09/21 measurement establishes:

```text
import javafx
javafx.
pom.xml → javafx/openjfx

```

as zero occurrences of actual usage.

The occurrences found in code are comments related to launcher error symptoms.

Therefore:

> **JavaFX is not a previous Kof graphics implementation.**

There is no JavaFX → new stack migration.

There is a decision to never introduce JavaFX.

---

## 2.2 `kof.ui`

Currently:

* JVM/Native have a runtime handle without actual rendering;
* JVM has `kof_ui_window_new`;
* setters/show have empty implementations;
* KofJS has a functional DOM/webview-based surface.

This means the future graphics surface needs to replace the current no-op state with a real architecture, without turning the current no-op into false compatibility.

Until there is an implementation:

```text
GFX00x

```

must represent the gap.

---

## 2.3 `kof.media`

A JVM base already exists.

Currently there is support for:

* opening/saving bitmaps;
* video metadata;
* WAV sampling;
* microphone enumeration/recording.

There is currently no:

* audio playback;
* music streaming;
* mixer;
* complete video pipeline;
* video playback;
* Native surface;
* equivalent JS surface.

Existing gaps include:

```text
MEDIA001
MEDIA003

```

Future implementation must evolve this surface without breaking existing programs.

---

# 3. Structural dependency: R3

The future graphics stack directly depends on the FFI/ABI infrastructure defined by R3.

The graphics backend will need to cross the boundary:

```text
Kof
 ↓
Kof IR
 ↓
backend
 ↓
ABI
 ↓
runtime/platform layer
 ↓
graphics/audio/video library
 ↓
OS/device

```

The interop layer must be able to represent, when necessary:

* handles;
* pointers;
* buffers;
* structs;
* callbacks;
* arrays;
* strings;
* lifecycle;
* ownership;
* error codes;
* native resources.

The graphics implementation **must not create a parallel FFI**.

If R3 does not provide a required mechanism, that must become an R3 extension before creating a graphics-specific solution.

---

# 4. General architecture model

The future architecture should have five levels:

```text
┌─────────────────────────────┐
│          Kof App             │
│   graphics/media intent      │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│     Kof Graphics API         │
│   target-independent intent  │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│       Kof Runtime ABI        │
│ handles / buffers / events   │
└──────────────┬──────────────┘
               │
      ┌────────┼─────────┐
      │        │         │
     JVM     Native      JS
      │        │         │
      ▼        ▼         ▼
 platform   platform   browser

```

The same intent must reach the different targets.

---

# 5. Resource model

Graphics and media resources are platform objects.

Examples:

```text
Window
Sprite
Texture
Tilemap
Mesh
Material
Camera
Sound
Music
Video
InputDevice

```

These objects must not expose implementation details.

For example, a `sprite` must not reveal whether internally it is:

* an OpenGL texture;
* a Vulkan texture;
* a WebGL resource;
* an HTML image;
* a native object;
* a GPU buffer.

The application manipulates a Kof abstraction.

---

# 6. Lifecycle

Every graphics/media resource needs a defined lifecycle.

Conceptual example:

```text
create
 ↓
ready
 ↓
use
 ↓
release

```

Future documentation for each resource must answer:

* when it is created;
* whether it is lazy;
* when data is loaded;
* when it becomes available;
* who owns the resource;
* when it can be released;
* whether the platform performs caching;
* what happens when the window/device disappears.

The application should not be required to manually manage GPU details if the backend can handle them.

---

# 7. Window

The window will be the backend's responsibility.

Future intent could resemble:

```kof
Window("Pong") {
    frame { dt ->
        ...
    }
}

```

or:

```kof
Scene("Pong") { dt ->
    ...
}

```

The definitive form has not yet been decided.

The abstraction should cover, where supported:

* title;
* size;
* fullscreen;
* resize;
* focus;
* close;
* DPI;
* orientation;
* visibility;
* input.

None of these requirements should force the application to know operating-system-specific APIs.

---

# 8. Frame loop

The frame loop is a fundamental abstraction.

The backend must control:

```text
clock
vsync
frame scheduling
polling
render submission
present

```

The program receives:

```text
dt

```

where:

```text
dt = time since the previous frame

```

The contract must later define:

* unit;
* precision;
* first-frame behavior;
* behavior after a long frame;
* `dt` limit;
* pause;
* minimized window;
* loss of focus.

---

# 9. Virtual clock

To make tests deterministic, the architecture must allow replacing the real clock with a virtual clock.

Example:

```text
frame 1 → dt = 16ms
frame 2 → dt = 16ms
frame 3 → dt = 16ms
frame 4 → dt = 32ms

```

This allows the same program to produce a deterministic observable.

This is especially important for:

* physics;
* animations;
* input;
* audio;
* playback;
* golden tests.

---

# 10. Input

Game input should be treated as a snapshot.

Example:

```kof
frame { dt ->
    if (keys.down("left")) {
        player.left(dt)
    }

    if (keys.pressed("space")) {
        player.fire()
    }
}

```

## 10.1 States

The abstraction should distinguish:

```text
down
pressed
released

```

when necessary.

Exact semantics remain TBD.

## 10.2 Mouse

Intent:

```kof
mouse.pos
mouse.down("left")
mouse.pressed("left")

```

## 10.3 Gamepad

Intent:

```kof
pad.stick("left")
pad.down("a")
pad.pressed("start")

```

## 10.4 Keyboard

The application should not need to know:

* scan codes;
* virtual key codes;
* X11 keycodes;
* Wayland codes;
* browser KeyboardEvent;
* Windows virtual keys.

The backend performs the translation.

---

# 11. 2D

The first graphics level should be 2D.

## 11.1 Sprite

```kof
var player = sprite("player.png")
player.at(120, 80)
player.draw()

```

## 11.2 Transformations

The family may include:

```text
at
scale
turn
origin
flip

```

The final API is TBD.

## 11.3 Animation

Conceptually:

```kof
player.frames("walk")
player.animate()

```

or equivalent.

The platform should handle:

* atlas;
* batching;
* upload;
* frame selection.

---

# 12. Tilemaps

Tilemaps should represent map intent, rather than manual texture management.

Possible intent:

```kof
var level = tilemap("level.png", 16)
level.at(0, 0)
level.draw()

```

Future questions:

* separate tileset;
* atlas;
* layers;
* collision metadata;
* animated tiles;
* infinite maps;
* external map formats.

Do not include domain features that are not necessary for the first slice.

---

# 13. 2D rendering

The application declares:

```text
what to draw

```

The backend decides:

```text
how to draw

```

The backend may perform:

* batching;
* texture atlas;
* command buffering;
* draw ordering;
* texture caching;
* resource upload.

These details must not appear in the basic API.

---

# 14. 3D

3D is deliberately later.

The planned minimum surface is:

```text
mesh
camera
material
light
transform

```

Example:

```kof
var hero = mesh("hero.glb")
hero.with(material.stone)

var camera = camera3d()
camera.at(0, 2, 5)
camera.lookAt(hero.pos)

draw(scene3d {
    hero
    light.sun()
})

```

The syntax is illustrative only.

---

# 15. 3D formats

Kof should not implement its own parsers for complex formats.

Candidate formats:

```text
glTF / GLB
OBJ

```

The final decision depends on the selected stack.

The backend may delegate parsing to mature libraries.

Criteria:

* license;
* security;
* coverage;
* maintenance;
* testability;
* cross-platform support.

---

# 16. Materials and shaders

The first abstraction level should hide:

* graphics API;
* pipeline state;
* shader compilation;
* descriptor binding;
* uniform buffers;
* vertex buffers.

Custom shaders are a separate concern.

Before exposing shaders to users, a decision must be made regarding:

```text
Kof shader language?
SPIR-V?
WGSL?
GLSL?
HLSL?
cross compilation?

```

This decision is not part of the first slice.

---

# 17. Audio

Audio should have two primary intents:

```text
sound
music

```

Example:

```kof
sound("boom.ogg").play()

var music = music("theme.ogg")
music.loop()
music.play()

```

The distinction is semantic, but the implementation may decide:

* preload;
* streaming;
* cache;
* decoder;
* buffer.

---

# 18. Audio contract

The backend will control:

```text
decoder
buffer
mixer
output
device
latency
voice management

```

The application should not need to manually create:

```text
audio channel
audio buffer
audio callback
audio thread

```

---

# 19. Mixing

The platform should be responsible for the mixer.

The API may eventually provide:

```kof
sound("shot.wav").volume(0.5)
music("theme.ogg").volume(0.3)

```

Possible future capabilities:

```text
volume
pause
resume
stop
loop
fade
pan

```

Each requires a cross-target contract before promotion.

---

# 20. Latency

"Low latency" must not be treated as a vague promise.

Implementation must measure:

```text
request play
      ↓
audio buffer submission
      ↓
audible output

```

per target.

The contract value will only be defined after the 3.0/3.3 spike.

---

# 21. Audio devices

Possible surface:

```kof
audio.devices()
audio.device(...)

```

But behavior depends on the target.

Browsers, for example, have different restrictions from a Native process.

The API should represent the common capability, while target-specific gaps should produce honest diagnostics.

---

# 22. Video

Video will be integrated into the media/UI surface.

Intent:

```kof
Window("Trailer") {
    video("intro.mp4").autoplay()
}

```

The application should not manipulate:

* demuxer;
* decoder;
* codec;
* frame queue;
* hardware decoder.

The backend is responsible for this.

---

# 23. Codec

Codecs will not be implemented by Kof.

The platform will select mature libraries.

Possible sources:

```text
FFmpeg
Libav
browser native codecs
OS media frameworks

```

The decision will depend on analysis of:

* license;
* target;
* security;
* maintenance;
* format support;
* headless capability.

---

# 24. `kof.media`

Future implementation must decide how to evolve the current surface.

Today:

```text
bitmap
WAV
video metadata
microphone

```

Future:

```text
playback
streaming
mixing
video playback

```

Evolution should be additive whenever possible.

Existing Kof programs should not break simply because the internal implementation was replaced.

---

# 25. KofUI

KofUI and graphics must not become two competing languages.

The conceptual division is:

```text
KofUI
→ user interface applications

Graphics/Game
→ interactive applications and games

```

There is overlap in:

* window;
* input;
* video;
* images;
* events.

These parts should share infrastructure when semantically equivalent.

---

# 26. KofJS

In the browser:

```text
Kof
 ↓
KofJS
 ↓
Browser

```

The graphics implementation will use browser capabilities as the backend.

This does not mean the Kof API will be HTML.

For example:

```kof
video("intro.mp4")

```

may internally result in an appropriate HTML element.

That is a lowering detail.

---

# 27. WASM

When the WASM target exists:

```text
Kof
 ↓
WASM
 ↓
Browser/Host

```

The same intent surface should remain valid.

WASM integration should be documented separately in:

```text
WASM/WASI implementation plan

```

and not duplicated here.

---

# 28. Native

The Native backend should use the selected portable stack.

The goal:

```text
Kof
 ↓
Native
 ↓
graphics/audio/video platform layer

```

without requiring users to write bindings manually.

Targets:

```text
x86-64
aarch64
riscv64

```

must enter the matrix individually.

---

# 29. JVM

JVM must not use:

```text
JavaFX
Swing
AWT
javax.sound

```

as the official backend for the surface.

The desired architecture is:

```text
Kof
 ↓
JVM
 ↓
R3 FFI/ABI
 ↓
portable platform stack

```

This keeps semantics aligned with Native and other targets.

---

# 30. KofScript

KofScript should use the same intent semantics.

There should not be a graphics API exclusive to Script.

The Script runtime should delegate to the implementation available in the environment.

If the environment lacks a capability:

```text
GFX001
SND001
VID001

```

or an equivalent code should be produced.

---

# 31. Capability Matrix

Each graphics/media operation must declare its capabilities.

Conceptual example:

```text
Capability        JVM Native JS Script
window             ?     ?    ✓    ?
sprite             ?     ?    ✓    ?
audio              ?     ?    ?    ?
video              ?     ?    ?    ?
3d                 ?     ?    ?    ?

```

A value only becomes `✓` after:

1. implementation;
2. tests;
3. golden;
4. parity;
5. documentation.

---

# 32. Gap codes

Never hide an unavailable capability.

Families:

```text
GFX00x
INP00x
SND00x
VID00x

```

Example:

```text
GFX001 — graphics capability unavailable on target
SND001 — audio playback unavailable on target
VID001 — video playback unavailable on target

```

Final codes must be added to the normative catalog before implementation.

---

# 33. Parity rule

The graphics surface will only be promoted when all mandatory targets exhibit equivalent behavior.

This means:

```text
JVM       ┐
Script    │
Native    ├── same observable contract
JS-Web    ┘

```

It is not enough to:

```text
compile on all targets

```

It is necessary to:

```text
compile
+
execute
+
produce expected behavior
+
pass conformance

```

---

# 34. Graphics observability

Pixels need to be testable.

The future system must have a deterministic way to:

```text
render
 ↓
readback
 ↓
buffer
 ↓
hash

```

The test compares the observable contract.

Do not directly compare:

* driver;
* GPU;
* physical framebuffer;
* hardware-dependent screenshots.

The exact hash format and tolerance, if any, will be defined during implementation.

---

# 35. Audio observability

Audio must have an offline test mode:

```text
program
 ↓
audio mixer
 ↓
PCM buffer
 ↓
hash/reference

```

This allows testing:

* volume;
* mix;
* ordering;
* loop;
* duration;
* channels.

Without depending on physical speakers.

---

# 36. Conformance

Each operation will have cross-target tests.

Example:

```text
graphics/sprite/basic.kof
graphics/input/pressed.kof
audio/play/basic.kof
audio/mix/basic.kof
media/video/basic.kof

```

The harness executes:

```text
JVM
Script
Native
JS

```

and compares observables.

---

# 37. Golden tests

Golden tests should be used for:

* frame sequence;
* input sequence;
* sprite rendering;
* transformations;
* audio mixing;
* media metadata;
* video decoding.

For time:

```text
virtual clock

```

For input:

```text
deterministic input stream

```

For audio:

```text
offline mixer

```

For video:

```text
deterministic frame readback

```

---

# 38. Fuzzing

The backend should have fuzzing specific to:

* transformation;
* coordinates;
* size;
* texture;
* input;
* lifecycle;
* asset loading;
* malformed media;
* audio files;
* video containers;
* resource release.

Special attention must be given to untrusted media files.

---

# 39. Security

Image, audio, and video files are untrusted data.

The backend must consider:

* malformed files;
* integer overflow;
* memory corruption;
* decompression bombs;
* decoder vulnerabilities;
* resource exhaustion;
* sandbox boundaries.

Kof should not implement its own codecs precisely to avoid assuming unnecessary responsibility for this complex code.

---

# 40. Third-party stack

Stack selection must be the result of the 3.0 spike.

Candidates:

### Graphics/window/input

```text
SDL3
SDL2
raylib
GLFW + graphics API

```

### Audio

```text
miniaudio
OpenAL Soft
SDL audio

```

### Video

```text
FFmpeg
Libav
native browser/OS decoder

```

The selection must evaluate:

```text
license
target coverage
maintenance
security
headless support
cross compilation
API stability
binary size
startup
performance

```

Do not choose based on developer familiarity.

---

# 41. Licensing

Dependency licensing must be analyzed before any integration.

Especially:

```text
GPL
LGPL
zlib
MIT
BSD
Apache

```

The analysis must consider:

* Kof distribution;
* runtime;
* final executable;
* linking;
* static linking;
* dynamic linking;
* Native;
* JVM;
* JS;
* official distribution.

No dependency will be approved simply because "it is open source."

---

# 42. Do not create giant wrappers

The Kof layer must remain small.

The goal is:

```text
Kof API
   ↓
thin abstraction
   ↓
backend

```

Not:

```text
Kof API
   ↓
complete SDL reimplementation
   ↓
complete renderer reimplementation

```

The platform is responsible for the complexity.

---

# 43. Performance

Performance will be measured.

Future benchmarks:

```text
startup
window creation
frame scheduling
sprite throughput
texture upload
draw calls
input latency
audio latency
mix throughput
video decode
memory

```

Compare targets only when the benchmark represents the same semantic operation.

Do not make claims such as:

```text
"Native is X times faster"

```

without measurement.

---

# 44. Memory budget

Implementation must monitor:

```text
runtime memory
texture memory
audio buffers
video buffers
temporary allocations

```

Especially in:

```text
Native
mobile
WASM
embedded

```

The API must not require users to manually manage every buffer.

---

# 45. Resource caching

Assets may be cached by the platform.

Possible resources:

```text
texture cache
sound cache
font cache
mesh cache
video cache

```

The policy must be transparent.

The application should be able to request release when necessary, if the final contract determines that such capability is needed.

---

# 46. Assets

The build system should eventually recognize graphics/media assets.

Example:

```text
assets/
    sprites/
    sounds/
    music/
    video/
    models/

```

Documentation must later define:

* copy;
* embed;
* compression;
* hashing;
* cache;
* path resolution;
* packaging.

This should not be implemented as part of the first slice.

---

# 47. Packaging

Future `kof build` must understand that a graphical application contains more than code.

Possible result:

```text
application
├── executable
├── runtime
├── assets
└── metadata

```

In the browser:

```text
application
├── JS/WASM
├── assets
└── bootstrap

```

The final format remains TBD.

---

# 48. Headless development

Every possible component should have a headless path.

This is essential for:

* CI;
* tests;
* fuzzing;
* servers;
* conformance.

Example:

```text
graphics backend
    ↓
headless renderer
    ↓
render buffer

```

Without opening a physical window.

---

# 49. Local development

When normally executed:

```bash
kof run

```

the backend may use a real window/device.

But:

```bash
kof test

```

must not depend on:

* monitor;
* specific GPU;
* speakers;
* microphone;
* camera.

---

# 50. Android

When KofAndroid enters this surface, it must use the same intent.

Do not create:

```text
KofAndroidGraphics

```

as a separate language.

The Android platform must implement the common graphics/media contract.

---

# 51. Mobile future

The same concepts may later support:

```text
Android
iOS

```

if these targets are supported.

The architecture must not block this.

However, iOS is not in the scope of this phase without an explicit decision.

---

# 52. Debugging

The future debugger should be able to relate:

```text
Kof source
 ↓
graphics operation
 ↓
runtime

```

when supported.

Especially important for:

* frame callback;
* resource creation;
* runtime errors;
* asset loading.

There is no need to expose GPU internals to the initial debugger.

---

# 53. Diagnostics

Errors should point to Kof code.

Bad example:

```text
SIGSEGV in libSDL...

```

Desired example:

```text
Kof graphics error GFX002

Resource:
    sprite("player.png")

Reason:
    asset could not be loaded

Source:
    game.kof:42

```

Whenever possible, the backend should translate external failures into Kof diagnostics.

---

# 54. Asset errors

Asset errors should distinguish:

```text
file missing
unsupported format
decode failure
permission denied
resource exhausted

```

Do not turn everything into:

```text
asset not found

```

---

# 55. Threading

Implementation must clearly define:

```text
main thread
render thread
audio thread
background loading

```

The application should not assume a specific model.

The runtime controls this.

This must be compatible with Kof's:

```text
spawn
async
channels
scheduler

```

---

# 56. Determinism

Games do not need to be deterministically identical in performance.

But tests need to be deterministic.

Distinguish:

```text
runtime behavior

```

from:

```text
test behavior

```

Implementation must avoid introducing nondeterminism into the conformance harness.

---

# 57. Implementation phases

## 3.0 — Spike and infrastructure

Objective:

* evaluate stack;
* validate R3;
* validate FFI;
* evaluate licensing;
* validate headless;
* validate cross-compilation;
* implement guard against JavaFX.

Output:

```text
architecture report

```

No new Kof API yet.

---

## 3.1 — Window, frame and input

Future implementation:

```text
window
frame
clock
keyboard
mouse
basic gamepad

```

Criterion:

```text
JVM ✓
Script ✓
Native ✓
JS ✓

```

with conformance.

---

## 3.2 — 2D

Implement:

```text
sprite
texture
transform
tilemap
draw

```

Criteria:

* golden;
* headless;
* cross-target;
* assets;
* lifecycle.

---

## 3.3 — Audio

Implement:

```text
sound
music
play
pause
stop
loop
volume

```

and infrastructure for:

```text
decoder
mixer
device

```

Criterion:

```text
offline PCM golden

```

---

## 3.4 — Video

Evolve `kof.media`.

Implement:

```text
video
play
pause
seek
volume

```

when the contract is defined.

Criterion:

```text
deterministic frame readback

```

---

## 3.5 — 3D

Only start if:

* stack supports it;
* targets support it;
* R3 supports it;
* runtime supports it;
* conformance can be deterministic.

Otherwise:

```text
GFX00x

```

remains valid.

---

## 3.6 — Corpus and promotion

Update:

```text
training/
learn/
docs/
conformance/
backend-parity/

```

Promote only when the gates are met.

---

# 58. Promotion criteria

A slice only leaves `future/` when:

```text
[ ] implementation completed
[ ] no hidden experimental code
[ ] runtime completed
[ ] all mandatory targets
[ ] conformance
[ ] golden
[ ] headless
[ ] documentation
[ ] gaps catalogued
[ ] performance measured
[ ] security reviewed
[ ] licensing reviewed
[ ] corpus updated

```

"It works on my machine" is not a promotion criterion.

---

# 59. Open questions

## Q1 — R1

Do `kof.sound` and `kof.media` remain core stdlib?

Will the game surface be:

```text
kof.game

```

or another namespace?

---

## Q2 — Scene

Which form will be chosen?

```kof
scene "Pong" {
    frame { dt ->
    }
}

```

or:

```kof
Scene("Pong") { dt ->
}

```

The second preserves the principle of avoiding syntax additions when an existing function/HOF resolves the intent.

---

## Q3 — 3D

Does 3D enter only after full parity?

---

## Q4 — Golden

What will the definitive contract be for:

```text
pixel hash
audio hash
video frame hash

```

---

## Q5 — Stack

Which stack will be chosen after measurement?

```text
SDL
raylib
GLFW
or another

```

---

## Q6 — Input

Will the surface have:

```text
snapshot
events
both

```

---

## Q7 — WASM

When WASM becomes available, does it automatically enter the parity matrix?

The expected architectural answer is yes, but the formal decision belongs to the WASM/WASI plan.

---

## Q8 — Current media

Will the existing JVM surface be:

```text
maintained and expanded

```

or:

```text
rebased

```

onto the new infrastructure?

Compatibility with existing programs must be preserved.

---

# 60. Permanent non-goals

This plan does not intend to:

* create a custom renderer;
* create a custom mixer;
* create a custom codec;
* create a custom demuxer;
* expose SDL;
* expose OpenGL;
* expose WebGL;
* expose DOM;
* expose HTML;
* expose CSS;
* expose JavaFX;
* expose Swing;
* expose AWT;
* create a target-specific API for each target;
* create a shader language before it is needed;
* create abstractions that merely wrap foreign APIs;
* accept partial parity as an official feature;
* hide unavailable capabilities;
* open implementation without maintainer promotion.

---

# 61. Golden rule

The most important test for any proposal in this area is:

> **Does a Kof developer need to think about graphics, audio, and media, or do they need to think about the platform underneath?**

The desired answer is:

```text
think about intent.

```

If, to write:

```kof
sound("shot.ogg").play()

```

the developer needs to know how audio works on Linux, Windows, the browser, or Android, the abstraction has failed.

If drawing a sprite requires knowing which renderer is being used, the abstraction has failed.

If creating a window requires knowing which API the operating system provides, the abstraction has failed.

The platform exists to absorb that complexity.

---

# 62. Expected architectural result

At the end of this plan, Kof's vision is:

```text
                    Kof Application
                           │
            ┌──────────────┼──────────────┐
            │              │              │
           UI           Graphics         Media
            │              │              │
            └──────────────┼──────────────┘
                           │
                  Kof Runtime Contract
                           │
                     Target Backend
                           │
       ┌──────────┬────────┼────────┬──────────┐
       │          │        │        │          │
      JVM       Native   Script     JS       WASM
       │          │        │        │          │
       ▼          ▼        ▼        ▼          ▼
   Platform    Platform  Runtime  Browser    Host

```

The application remains Kof.

The backend absorbs the platform.

The language remains intent-oriented.

Parity remains a promotion requirement.

And the complexity required to make graphics, games, and media work stays where it belongs:

> **in the platform implementation, not in the code written by the Kof developer.**
