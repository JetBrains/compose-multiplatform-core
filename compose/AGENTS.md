# Project: Compose Multiplatform Core

This directory contains the libraries that make up Compose Multiplatform Core.

**Refer to [AGENTS.md](../AGENTS.md) for general instructions.**

## Project Structure Map
- **Runtime (`compose:runtime`):** The core engine for state management and the composition tree.
- **UI (`compose:ui`):** Orchestration layer for layout, input, graphics, and text primitives.
- **Foundation (`compose:foundation`):** Design-system-agnostic building blocks (e.g., `LazyColumn`, gestures).
- **Material/Material3 (`compose:material`, `compose:material3`):** Design-system-specific components implementing Material Design.
- **Skiko (`compose:ui:ui-skiko`, fork only)** provides the Skia implementation of the `ui-graphics` and `ui-text`
  platform interfaces for desktop, iOS, macOS, and web. `registerSkikoComposeImplementation()` installs it.
  - `SkikoParagraph` uses `emptyLineMetrics` in `text/platform/SkikoParagraphBuilder.nonAndroid.kt` for empty text
    or when Skia returns no line metrics, which can happen with placeholder-only text. Check this path when
    changing line height or font metrics. See `SkikoParagraphTest` (`nonAndroidTest`) and the
    `DesktopParagraph*Test` classes (`desktopTest`).

## Non-Android Code Map
- In `ui`, `foundation`, and `material3`, `desktopMain` and `nonJvmMain` depend on `skikoMain`, which shares code
  across desktop, iOS, macOS, and web. `runtime`, `ui-text`, and `ui-skiko` use `nonAndroidMain` for shared
  non-Android code. Fork-only UI flags are extension properties on `ComposeUiFlags` in `ComposeUiFlags.skiko.kt`
  and `DesktopComposeUiFlags.skiko.kt`.
- `RootNodeOwner.skiko.kt` (`OwnerImpl`) is the skiko `Owner`, the counterpart of Android's `AndroidComposeView`. When
  a commonMain service such as `RectManager` misbehaves only outside Android, compare the calls the two make on it.
- `ComposeWindow` and `ComposeDialog` wrap a `ComposeWindowPanel`. `ComposeWindowPanel` and `ComposePanel` each
  create a `ComposeContainer`, whose `ComposeSceneMediator` holds the `ComposeScene` and `SkiaLayerComponent` and
  installs the AWT input, IME, and focus listeners. Check host-level changes to `exceptionHandler`, `saveState()`,
  or `renderApi` in both panels.
- Desktop popups and dialogs follow `compose.layers.type` (`ComposeFeatureFlags.desktop.kt`). The default
  `OnSameCanvas` uses `CanvasLayersComposeScene`, as web and macOS do. `COMPONENT` (`SwingComposeSceneLayer`) and
  `WINDOW` (`WindowComposeSceneLayer`) use `PlatformLayersComposeScene`, as iOS does. Tests switch the type with
  `ComposeFeatureFlags.layerType.withOverride(LayerType.OnComponent) { … }`.
- In foundation's `skikoMain/.../text/input/internal/`, `TextInputSession.skiko.kt` handles `TextFieldState` and is
  covered by `TextInputSessionTest`. `LegacyPlatformTextInputServiceAdapter.skiko.kt` handles `TextFieldValue`.
  Text-field shortcuts are in `KeyMapping.skiko.kt`, which falls back to commonMain `KeyMapping.kt`.
- In `compose/ui/ui/src/desktopTest`, `WindowTypeTest` replays the key and input-method events each OS sends.
  `AccessibilityTest` checks individual nodes, and `ApplicationAccessibilityTest` covers windows and popups.
  - Window, dialog and popup tests use `runApplicationTest { launchTestApplication { Window { … } } }` from
    `window/TestUtils.kt`. These tests skip without a display. On Linux, run them under Xvfb as CI does.
