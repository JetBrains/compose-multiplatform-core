# Pure-skiko reproducer for CMP-8615

Reproduces `WebGL: INVALID_OPERATION: drawElements: no valid shader program in use`
(and the page permanently stops rendering) **without any Compose code** — skiko 0.150.1 only.

## Root cause (as demonstrated by this reproducer)

Two Skia `DirectContext`s end up bound to the **same underlying `WebGLRenderingContext`**:

1. `ComposeWindow.resize()` (compose: `ComposeWindowInternal.web.kt`) calls
   `skiaLayer.attachTo(canvas)` **on every resize**.
2. skiko's `SkiaLayer.attachTo` (web) creates a **new** `CanvasRenderer` →
   `GL.createContext(canvas)` — emscripten registers a *new handle*, but
   `canvas.getContext()` returns the *existing* WebGL context — → a **new**
   `DirectContext.makeGL()`. The previous `DirectContext` is orphaned and never closed.
3. The orphaned `DirectContext` is destroyed later, at an arbitrary time, by skiko's
   `FinalizationRegistry` (GC). Skia's `GrGLGpu` destructor "cleans up" GL state on the
   shared context: `glUseProgram(0)`, unbinding buffers, etc.
4. The live `DirectContext`'s state cache still believes its program/buffers are bound,
   so while the frame content is unchanged it **skips** `glUseProgram`/`glBindBuffer` and
   calls `drawElements` with **no program in use** → `INVALID_OPERATION`, blank canvas.
   Any content change that forces a program switch "heals" it — matching the symptom
   reported in the issue (any event re-shows the content).

GC timing is why the bug looks random and hard to reproduce in real apps.

## Variants (pick via URL hash)

- `#close` (default) — fully deterministic, no GC needed: at frame 60 an "intruder"
  `DirectContext` is created on the same WebGL context, draws once, and is `close()`d.
  The destructor's `glUseProgram(0)` kills the main renderer from that exact frame on.
- `#reattach` — faithful simulation of the Compose resize path: every 500 ms the layer is
  re-`attachTo`'d (exactly what `ComposeWindow.resize()` does) and GC is forced
  (`--js-flags=--expose-gc`). Error fires on nearly every cycle.

## Run

```
./gradlew wasmJsBrowserDevelopmentRun          # then open /#close or /#reattach
node run-repro.js close 15000                  # headless verification (puppeteer-core + @sparticuz/chromium)
```

## Evidence (evidence/)

- `before-intruder-blue-circle.png` — canvas renders fine before the second context dies (0 GL errors).
- `after-intruder-blank.png` — same page seconds later: blank, console flooded with
  `drawElements: no valid shader program in use` + `bufferSubData: no buffer` until
  Chrome stops reporting ("too many errors").
- `console-close.log`, `console-reattach.log` — full console captures (headless Chromium 149, SwiftShader).

## Fix directions

- skiko `CanvasRenderer`/`SkiaLayer`: don't create a new `DirectContext` per `attachTo` on the
  same canvas (reuse it and just recreate the render target at the new size), and/or properly
  `close()` the previous renderer's context *synchronously* on re-attach.
- Defensive: `directContext.resetGLAll()` before drawing a frame (Skia's documented remedy when
  external code touched GL state), though that only masks the shared-context aliasing.
- Compose side: stop re-attaching on every resize (`// TODO: Align with Container/Mediator architecture`).
