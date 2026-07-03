# CMP-8359: Real per-canvas ComposeSceneLayer on web

## Context

Web is currently the only skiko target where `ComposeSceneLayer` (used by `Popup`/`Dialog`) is implemented as a "compatibility mode": `AttachedComposeSceneLayer` in
[`CanvasLayersComposeScene.skiko.kt:552-696`](../../../../../../../skikoMain/kotlin/androidx/compose/ui/scene/CanvasLayersComposeScene.skiko.kt)
composites every layer's `RootNodeOwner` into the *same* `<canvas>`/`SkiaLayer` as the main window, with hit-testing, z-order and focus handled entirely in Kotlin. This conflicts with common API expectations (`Owner`, coordinate conversions) and is explicitly flagged as temporary by a TODO naming this exact issue in
[`ComposeWindowInternal.web.kt:246-253`](../window/ComposeWindowInternal.web.kt).

Desktop and iOS already solved this properly: `ComposeSceneContext.createLayer` (the actual extension point `rememberComposeSceneLayer` calls — see
[`ComposeSceneLayer.skiko.kt:184-199`](../../../../../../../skikoMain/kotlin/androidx/compose/ui/scene/ComposeSceneLayer.skiko.kt))
returns a platform-specific `ComposeSceneLayer` that wraps a **fully independent** [`PlatformLayersComposeScene`](../../../../../../../skikoMain/kotlin/androidx/compose/ui/scene/PlatformLayersComposeScene.skiko.kt) bound to its own native surface (`WindowComposeSceneLayer.desktop.kt`, `UIKitComposeSceneLayer.ios.kt`). Notably, even desktop treats this as an incremental, flagged rollout: `ComposeFeatureFlags.layerType` in
[`ComposeFeatureFlags.desktop.kt:24-45`](../../../../../../../desktopMain/kotlin/androidx/compose/ui/ComposeFeatureFlags.desktop.kt)
still **defaults to `OnSameCanvas`** (the compatibility mode) because the "real surface" mode (`OnWindow`) has known issues (platform rendering bugs, blinking, clipping). This is directly relevant precedent for how to roll out the web equivalent safely.

Goal: give web the same architecture — each layer gets its own `<canvas>`, `SkiaLayer`, and `PlatformLayersComposeScene` — delivered incrementally behind a feature flag, following the desktop precedent.

## Design

### 1. Extract a reusable "scene binding" (no behavior change)

`ComposeWindow`/`ComposeWindowInternal.web.kt` (932 lines) currently hard-wires one canvas + one `SkiaLayer` + one `FrameRecomposer` + one `SingleComposeSceneRenderingScope` + one `EventTargetListener` + all pointer/keyboard/wheel/touch handling. This bundle is exactly what a layer also needs, so extract it into a new internal class (the web analogue of desktop's `ComposeSceneMediator`), e.g. `WebComposeSceneMediator` in this package, owning:

- canvas creation/attachment (today: `ComposeWindow.web.kt:159-164`, `ComposeWindowInternal.web.kt:534-549`)
- `SkiaLayer` + `SkikoRenderDelegate` (lines 341-347)
- its own `FrameRecomposer` + `SingleComposeSceneRenderingScope` (lines 224-232) — matches desktop, where each `ComposeSceneMediator` owns an independent `frameRecomposer`, not a shared one
- `EventTargetListener(canvas)` + the pointer/keyboard/wheel/touch handling in lines 361-741 (`initEvents`, `onPointerEvent`, `onWheelEvent`, `processKeyboardEvent`)

Refactor `ComposeWindow` to be the first (and only, for now) caller of this class. This is behavior-preserving groundwork — land and verify it separately before adding layers, since `ComposeWindowInternal.web.kt` has a lot of subtlety (coalesced touch events, clipboard-focus-stealing via `clipTarget`, drag-and-drop wiring) that's easy to regress silently.

### 2. `WebComposeSceneLayer`

New `ComposeSceneLayer` implementation (mirrors `WindowComposeSceneLayer.desktop.kt`), created via a web-specific `ComposeSceneContext.createLayer` override (mirrors `ComposeContainer.createPlatformLayer` in `ComposeContainer.desktop.kt:487-513`). Each instance owns:

- a new `<canvas>` appended into `layerRoot` (the existing `appContainer` — see `ComposeWindow.web.kt:190-193`), absolutely positioned via CSS to `boundsInWindow`
- a `WebComposeSceneMediator` instance from step 1, bound to that canvas
- a `PlatformLayersComposeScene` (no changes needed — already platform-agnostic)
- its own a11y container + `ComposeWebSemanticsListener` (confirmed to support an arbitrary root, not a singleton)
- reused from the parent window: `archComponentsOwner` (lifecycle/saved-state must stay shared, matching how desktop shares it via `ComposeContainer` rather than per-mediator), current `density`, and the outside-click layer registry (below)

**Z-order** falls out of DOM stacking (append order / `z-index`) instead of the current software back-to-front draw loop — no changes needed to replicate desktop's manual z-order logic.

**Scrim**: a plain absolutely-positioned `<div>` sibling drawn just below the layer's canvas in DOM order, sized to the viewport, background from `scrimColor` — simpler than desktop's render-callback (`OverlayRenderDecorator`) approach since CSS compositing does this for free.

**Outside-click / dismiss**: because pointer events naturally target only the topmost DOM element, web does *not* need desktop's `BlockingInputLayerEventFilter` pass-through blocking. It only needs outside-*detection*: one capture-phase `pointerdown`/`pointerup` listener on `window` (via the existing global `EventTargetListener`) that walks an ordered registry of active layers top-down and invokes `onOutsidePointerEvent` for any layer whose canvas doesn't contain `event.target`, stopping at the first layer with `consumePointerInputOutside = true` — same semantics as desktop's `DetectEventOutsideLayer`/`BlockingInputLayerEventFilter` in `DesktopComposeSceneLayer.desktop.kt:250-270`, simplified because DOM stacking already prevents click pass-through. The attach/detach/focus bookkeeping in `CanvasLayersComposeScene.skiko.kt:510-550` (`attachLayer`/`detachLayer`/`requestFocus`/`releaseFocus`) is largely DOM-agnostic and can be reused almost as-is for this registry.

**Coordinate conversion**: fix the two TODOs at `ComposeWindowInternal.web.kt:246-262` (`convertLocalToWindowPosition`/`convertWindowToLocalPosition`) so each canvas's own `getBoundingClientRect()` offset is accounted for — no longer safe to assume Window Rect == Canvas Rect.

**IME/text input**: give each layer's `WebTextInputService` a `backingDomInputContainer` scoped to the layer (not the shared `layerRoot`), offsetting the geometry rect by the layer canvas's position — mechanical change since that's the only per-window dependency in `WebTextInputService`.

### 3. Feature flag (mirrors desktop's `ComposeFeatureFlags.layerType`)

Ship default-off. Web has no JVM system-property mechanism, so expose it as an internal flag on `ComposeViewportConfiguration` (or a small `ComposeFeatureFlags.web.kt` reading a JS global, whichever fits the existing config surface better — decide during implementation by checking how other experimental web-only behaviors are toggled today). This lets the change land and be exercised in tests/samples without affecting existing apps, exactly as desktop still defaults to `OnSameCanvas` today.

## Delivery slices

1. **Extract `WebComposeSceneMediator`**, `ComposeWindow` as sole caller — behavior-preserving, verified against existing web tests. **Done.** `WebComposeSceneMediator.web.kt` now owns the canvas's `SkiaLayer`/`FrameRecomposer` (absorbing the former `WebComposeSceneRenderLoop`) *and* all pointer/keyboard/wheel/touch DOM handling, clipboard-focus-stealing, and the Safari `dragend` workaround. `ComposeWindowInternal.web.kt` is now a thin composition root that constructs one `WebComposeSceneMediator` and wires `PlatformContext`/`CanvasLayersComposeScene` to it via two small callbacks (`isBackingInputFocused`, `requestTouchInputMode`) — no DOM event handling left directly in `ComposeWindow`. Verified via `compileKotlinJs`/`compileKotlinWasmJs` and a full `jsBrowserTest`/`wasmJsBrowserTest` run for `compose:ui:ui` (0 failures across all 72 suites, including `CanvasLayersComposeSceneTest`, `DialogTest`, `ComposeWindowLifecycleTest`, `KeyEventTests`, `MouseEventsTest`, `PopupTest`, `PreventDefaultTest`, `WheelEventTests`, `GesturesTest`, `ScrollTests`, `TextFieldFocusTest`, `WebInteropTest`, `WebClipboardIntegrationTest`, `CfWA11YTest`).
2. **`WebComposeSceneLayer` behind the flag** (next up): own canvas/scene, pointer + keyboard input, focus, scrim, outside-click dismiss, resize/DPI. Popup/Dialog functional end-to-end under the flag. `WebComposeSceneMediator` from slice 1 should be directly reusable here — construct one per layer, bound to the layer's own `<canvas>`.
3. **Parity**: per-layer IME/text input, per-layer accessibility root. Drag-and-drop across layer boundaries may remain a documented limitation initially (browser drag events don't automatically coordinate across separate canvases — `WebDragAndDropManager`'s `globalEventsListener` is window-wide but ghost-image/per-canvas listeners are not).
4. **Consider flipping the default** once stable — expect this to take time, per the multi-year desktop precedent where `OnWindow` still isn't default.

## Verification

- Existing tests must keep passing with the flag off (default): `CanvasLayersComposeSceneTest.kt`, `DialogTest.kt`, and web-specific tests under `compose/ui/ui/src/webTest` (or equivalent, e.g. `OnCanvasTests.kt`, `ComposeWindowLifecycleTest.kt`).
- New wasmJs/js browser tests (slice 2+): assert two distinct `<canvas>` elements exist in the DOM when a `Popup`/`Dialog` is shown with the flag on, verify z-order via DOM position, outside-click dismiss fires, scrim renders, and resize/DPI-change propagates to layer canvases independently.
- Manual smoke test: run a web sample app (check `compose/ui/ui/samples` or the repo's web demo target) showing a `Popup`/`Dialog` with the flag toggled on; confirm via browser devtools that two canvases exist and behave independently (drag/resize the popup, outside-click dismiss, IME field inside a dialog).
