# Fix plan: skiko web `SkiaLayer` resize support (CMP-8615)

Target repository: **https://github.com/JetBrains/skiko** (this plan describes changes there;
it lives next to the reproducer in `compose-multiplatform-core` branch
`web_skiko_blank_screen_reproducer` only for convenience).

## Problem being fixed

[CMP-8615](https://youtrack.jetbrains.com/issue/CMP-8615): on Web, the page sometimes
permanently stops rendering with the console error
`WebGL: INVALID_OPERATION: drawElements: no valid shader program in use`.

Root cause (verified by the pure-skiko reproducer in this directory, see `README.md`):

1. The web `SkiaLayer` has **no resize API**: `CanvasRenderer` captures `width`/`height` as
   immutable constructor `val`s and bakes them into `BackendRenderTarget.makeGL(...)`.
2. Consumers (Compose's `ComposeWindow.resize()`) therefore call `skiaLayer.attachTo(canvas)`
   again on **every canvas resize**. Each `attachTo` creates a new `CanvasRenderer` →
   `GL.createContext(canvas)` (a new emscripten handle over the **same**
   `WebGLRenderingContext`, since `canvas.getContext()` returns the existing context) →
   a new `DirectContext.makeGL()`.
3. The previous `DirectContext` is orphaned — `attachTo` doesn't dispose it and
   `SkiaLayer.detach()` is an empty `TODO`. It is destroyed later, at an arbitrary time, by
   the GC `FinalizationRegistry`. Skia's `GrGLGpu` destructor then runs cleanup
   (`glUseProgram(0)`, buffer unbinds) **on the shared WebGL context**, behind the back of
   the live `DirectContext`'s state cache.
4. On the next frame with unchanged content the live context skips the redundant
   `glUseProgram`/`glBindBuffer` and calls `drawElements` with no program bound →
   `INVALID_OPERATION`, blank canvas until some content change forces a program rebind.
   GC timing makes it look random.

## Fix strategy

Never create a second `DirectContext` over the same WebGL context, and never leave one to
the GC finalizer:

- Add `resize(width, height)` that keeps the WebGL context and `DirectContext` and only
  recreates the `BackendRenderTarget`/`Surface` (this is what the AWT redrawers do, and
  standard Skia practice). Setting `canvas.width`/`height` resets the WebGL *drawing
  buffer* but leaves all GL objects and bindings intact, so only the render target is stale.
- Make `attachTo` on the **same** canvas degrade to `resize` — this fixes existing consumers
  (Compose) without any change on their side.
- Implement `detach()` (synchronous disposal), and dispose the old renderer when attaching
  to a **different** canvas.
- Guard the pending `requestAnimationFrame` callback against running after disposal.

## Changes

### 1. `skiko/src/webMain/kotlin/org/jetbrains/skiko/CanvasRenderer.kt`

Current code for reference: constructor is
`CanvasRenderer(private val contextPointer: NativePointer, val width: Int, val height: Int)`;
`initCanvas()` uses those `val`s; the rAF callback does
`clear/resetMatrix/drawFrame/flushAndSubmit/flush` unconditionally.

Make these changes:

a) Turn `width`/`height` into `var ... private set` (constructor parameters remain, they
   just initialize the properties instead of being the properties).

b) Add an `isDisposed` flag and two new functions:

```kotlin
/**
 * Recreates the render target and surface at the new size, reusing the existing
 * WebGL context and [DirectContext].
 *
 * Must be called after the canvas element's `width`/`height` attributes change:
 * that resets the WebGL drawing buffer, while the Skia surface keeps targeting
 * the default framebuffer with the old dimensions.
 */
fun resize(width: Int, height: Int) {
    check(!isDisposed) { "CanvasRenderer is disposed" }
    if (width == this.width && height == this.height) return
    this.width = width
    this.height = height
    GL.makeContextCurrent(contextPointer)
    initCanvas()
}

/**
 * Releases the GPU resources. The renderer can't be used afterwards;
 * a frame already scheduled via [needRedraw] becomes a no-op.
 */
fun dispose() {
    if (isDisposed) return
    isDisposed = true
    GL.makeContextCurrent(contextPointer)
    disposeCanvas()
    canvas = null
    context.close()
}
```

c) Guard the body of `requestAnimationFrameCallback` with `if (!isDisposed) { ... }`.
   Keep `redrawScheduled = false` OUTSIDE the guard (bookkeeping must stay consistent).
   Without this guard, a frame scheduled before `dispose()` would touch closed native
   objects (crash) or interleave draws from two renderers (GL state corruption).

Notes:
- `GL.makeContextCurrent(contextPointer)` before recreating/disposing is required — another
  `SkiaLayer` on a different canvas may be current.
- No `context.resetAll()`/`resetGLAll()` is needed in `resize`: the drawing-buffer reset
  does not touch GL bindings, so Skia's cached state stays truthful; the new surface's first
  flush re-sets viewport/scissor by itself because the cached values no longer match.
- `disposeCanvas()` (existing private fun) already closes surface + render target; `dispose`
  additionally closes the `DirectContext` — synchronously, so the GC finalizer never runs
  GL cleanup at an uncontrolled time. (`Managed.close()` unregisters the finalizer.)

### 2. `skiko/src/webMain/kotlin/org/jetbrains/skiko/SkiaLayer.web.kt`

a) Rework `private fun attachTo(htmlCanvas: HTMLCanvasElement)`:

```kotlin
private fun attachTo(htmlCanvas: HTMLCanvasElement) {
    if (this.htmlCanvas === htmlCanvas && state != null) {
        // Re-attaching to the same canvas: reuse the WebGL context and DirectContext.
        // Creating a second DirectContext over the same WebGLRenderingContext corrupts
        // both contexts' GL state caches once either of them is destroyed (CMP-8615).
        state!!.resize(htmlCanvas.width, htmlCanvas.height)
        return
    }
    detach()
    this.htmlCanvas = htmlCanvas
    state = object : CanvasRenderer(createWebGLContext(htmlCanvas), htmlCanvas.width, htmlCanvas.height) {
        override fun drawFrame(currentTimestamp: Double) {
            // currentTimestamp is in milliseconds.
            val currentNanos = currentTimestamp * 1_000_000
            renderDelegate?.onRender(canvas!!, width, height, currentNanos.toLong())
        }
    }
}
```

b) Add a public `resize` (web-only extra member — `actual` classes may have members beyond
   the `expect` declaration, same as the AWT `SkiaLayer` does):

```kotlin
/**
 * Recreates the drawing surface to match the current size of the attached canvas
 * element. Call this after changing the canvas element's `width`/`height` attributes.
 */
fun resize(width: Int, height: Int) {
    checkNotNull(state) { "SkiaLayer is not attached to a canvas" }.resize(width, height)
}
```

c) Implement `detach()` (currently `// TODO: when switch to the frame dispatcher - stop it here.`):

```kotlin
actual fun detach() {
    state?.dispose()
    state = null
    htmlCanvas = null
}
```

Update its KDoc: detach used to be a no-op; it now synchronously releases the GPU resources,
and re-attaching afterwards performs a full context recreation.

### 3. What NOT to change

- `createWebGLContext` / `patchWebGlContext` / emscripten `GL` plumbing — untouched.
- Other targets (`awtMain`, `uikitMain`, ...) — untouched; this is web-only.
- The `expect class SkiaLayer` declaration — `resize` is an extra member of the web `actual`
  only. Do not add it to `expect` (it would force implementing it on every platform).

## Tests

Add browser tests (they run in `skiko/src/webTest/kotlin`, executed for both `js` and
`wasmJs` — follow the existing test setup there):

1. **resize keeps rendering**: attach a `SkiaLayer` to a canvas, render a frame, change
   `canvas.width`/`height`, call `layer.resize(newW, newH)` (and separately: call
   `attachTo(sameCanvas)` again), render another frame. Assert `state.width/height` updated
   and that rendering completes without exceptions.
2. **re-attach to the same canvas does not create a new renderer context**: expose what's
   needed via `internal` + `@InternalSkikoApi` if necessary, or assert indirectly (e.g. the
   `CanvasRenderer` instance identity in `state` is preserved... note: with this design the
   instance IS preserved on same-canvas attach — assert `layer.state === before`).
3. **detach is safe with a pending frame**: attach, call `needRender()`, immediately
   `detach()`, wait one animation frame — no crash.
4. **detach + attach to a different canvas** renders correctly on the new canvas.

Manual/integration verification:

- Run `samples/SkiaWebSample` (js and wasmJs) — three canvases must still render.
- Build Compose against the new skiko and run the reproducer from
  `compose-multiplatform-core` branch `web_skiko_blank_screen_reproducer`
  (`skiko-blank-screen-reproducer/`, see its README):
  - the `#reattach` variant must produce **zero** `INVALID_OPERATION` errors and keep
    rendering the blue circle through all re-attach + forced-GC cycles;
  - the `#close` variant will STILL fail — it deliberately creates a second `DirectContext`
    at the app level, which this fix does not (and should not) prevent. Do not treat it as
    a regression.
- Full CMP-8615 check: Compose Web app (LazyColumn) — scroll, resize the window, scroll
  again, repeatedly; no blank content, no console errors.

## Acceptance criteria

- [ ] `SkiaLayer.attachTo(sameCanvas)` reuses the existing `DirectContext` (no new WebGL
      context handle, no orphaned Skia context).
- [ ] Public `SkiaLayer.resize(width, height)` exists on web and recreates only the
      render target + surface.
- [ ] `SkiaLayer.detach()` synchronously closes surface, render target and `DirectContext`.
- [ ] A pending `requestAnimationFrame` callback after `dispose()` is a no-op.
- [ ] New webTests pass on js + wasmJs; existing web tests and SkiaWebSample unaffected.
- [ ] Reproducer `#reattach` variant is clean; Compose resize+scroll scenario is clean.

## Risks / edge cases

- **Behavioral change of `detach()`**: it was a no-op; any consumer calling `detach()` and
  then re-attaching now pays a full context recreation (correct, but new). Compose calls
  `detach()` only in `ComposeWindow.dispose()`, where freeing GPU resources is the desired
  behavior (today it leaks until GC).
- **Same-size resize**: `CanvasRenderer.resize` early-returns when dimensions are unchanged.
  Setting `canvas.width` to the same value still resets (clears) the drawing buffer, but
  that's harmless: every frame starts with `canvas.clear(WHITE)` and a full redraw.
- **Context loss** (`webglcontextlost`) is out of scope — same behavior as today.
- Kotlin/JS and Kotlin/Wasm share `webMain` sources here; no per-target code is needed, but
  make sure both `jsTest` and `wasmJsTest` runs are green.

## Suggested follow-up (separate PR, compose-multiplatform-core)

In `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/window/ComposeWindowInternal.web.kt`,
`resize()`: replace the `skiaLayer.attachTo(canvas)` call (marked
`// TODO: Align with Container/Mediator architecture`) with an initial-attach check +
`skiaLayer.resize(sizeInPx.width, sizeInPx.height)`. Not required for the bug fix (the
same-canvas `attachTo` path already degrades to `resize`), but it makes the intent explicit.
