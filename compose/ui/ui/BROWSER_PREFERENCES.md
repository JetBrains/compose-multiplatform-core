# Browser accessibility preferences (CMP-10692)

## Status and scope of this change

This is the first, web-only implementation of automatic reduced motion and browser-default
font scaling. It does **not** complete CMP-10692. Contrast, forced colors, reduced transparency,
and inverted-color compensation still need reviewed consumers, implementation, and validation.
No unused listeners for those features are registered by this change.

Production and test changes are confined to `webMain` and `webTest`. There are no public API,
common production, commonTest, or Android implementation changes. `PlatformContext.mediaScope`
remains untouched: the current `UiMediaScope` does not expose these accessibility preferences.

The issue's public API response contained its title, an empty description, and no comments.
No separate investigation was available with the supplied handoff; the handoff and this checkout
were used as the implementation specification.

### Implemented paths

- `WebPreferencesObserver.web.kt`: each window owns its reduced-motion listener, snapshot-backed
  `MotionDurationScale`, and font-size probe. Initial readings precede density/recomposer creation.
  Font callbacks start only after the scene exists. Disposal is idempotent and late callbacks
  are ignored.
- `ComposeWindowInternal.web.kt`: supplies `Dispatchers.Main + motionDurationScale` to the existing
  recomposer. Both resize and font-only changes use the same scene/window density setter. A
  font-only change invalidates rendering/layout without writing canvas buffer attributes or
  reattaching the Skia layer. DPR and the existing clamped visualViewport raster scale remain
  independent of font scale.
- `ComposeViewportConfiguration.web.kt`: an internal, per-window observer factory allows tests
  to inject preferences without replacing browser globals. This is not application-facing API.
- `MediaQueryListener.kt`: retains modern and legacy listener support, tolerates missing query
  or listener APIs, and rejects callbacks after disposal. A non-matching reduced-motion query is
  neutral, including when that individual feature is unsupported. Availability of matchMedia is
  not treated as evidence that the feature is supported.
- `SystemThemeObserver.web.kt`: retains the current dark/light consumer path. Its query dependency
  is injectable for focused regression tests; initial values, live changes, and disposal remain
  covered.

### Font measurement contract and limits

The probe is attached inside the existing shadow root. It is an empty, absolutely positioned,
`aria-hidden`, non-interactive div with `font: medium sans-serif`, `width: 1em`, and
`visibility: hidden`. A zero-sized, overflow-clipped wrapper prevents it from increasing host
layout or scroll extents, even for very large fonts in a tiny container. The measured element
has no size containment and is not `display: none`.

Its computed CSS-pixel font size is divided by **16**, the compatibility baseline. In particular,
a 20px preference already active at startup must produce 1.25, not 1. Invalid/non-positive
measurements retain the last valid scale, or 1 before the first valid reading. There is no
arbitrary upper cap on font scale. Host html/rem font resets are deliberately not inherited.

ResizeObserver monitors the probe's em-sized width. Focus and visibility restoration refresh the
reading too. Without ResizeObserver, the initial reading and these refreshes remain available;
container sizing also falls back to window resize events. Repeated identical readings do nothing.

**Actual browser default-font preference detection/live updating is still pending manual
validation.** Synthetic sizes demonstrate propagation, not browser preference reporting. Minimum
font settings, text-only zoom, mobile text autosizing, OS text settings, explicit pixel text,
and host rem inheritance are not claimed as supported by this policy. Native browser/OS behavior
may differ; do not close the font-detection acceptance item until real settings are checked.

### Motion coverage and inherited timing limit

The scale is 0 for `prefers-reduced-motion: reduce`, otherwise 1. The same snapshot-backed object
is updated without recreating the scene, composition, or recomposer. Finite animations are not
handled by stopping frames. InfiniteTransition can suspend at zero and resume through its
existing snapshotFlow when the preference returns to normal.

An existing animation-core detail matters for live toggles: `SuspendAnimation.kt` reads the scale
**before** awaiting its next frame. An already queued frame can therefore use the previous scale;
the following delivered frame finishes the finite animation. At 60 Hz that is normally one extra
~17ms frame, not a preference being ignored. A throttled/hidden tab depends on browser frame
delivery. Initial reduced motion is honored by the first animation. The implementation retains
this inherited latency rather than changing shared animation code; the tests explicitly cover it.
Strict completion on the first already-queued frame is not claimed.

MotionDurationScale references were audited in animation-core, foundation common/skiko sources,
and UI skiko/web sources. Explicit overrides are unchanged:

- `foundation/BasicMarquee.kt`: FixedMotionDurationScale remains 1.
- `foundation/gestures/AbstractScrollableNode.kt`: default fling uses
  DefaultScrollMotionDurationScale (1).
- `foundation/gestures/snapping/SnapFlingBehavior.kt`: uses that same default scroll scale.
- `foundation/gestures/cupertino/CupertinoFlingBehavior.kt`: also has an explicit scroll scale.

Application overrides, custom frame loops, CSS animations, and DOM/interop animation are not
suppressed automatically. Dialog and duration-scaled scroll/animation consumers continue to use
their existing scale mechanisms; this is not a claim of complete motion suppression.

## Verification

Both targets compiled before executing the focused suite:

```bash
mkdir -p .cmp10692/tmp
TMPDIR="$PWD/.cmp10692/tmp" ./gradlew \
  :compose:ui:ui:compileTestDevelopmentExecutableKotlinJs \
  :compose:ui:ui:compileTestDevelopmentExecutableKotlinWasmJs \
  --console=plain -Djava.io.tmpdir="$PWD/.cmp10692/tmp"
```

The relevant existing and new browser tests were run with these filters on **each** target:

```bash
filters=(
  --tests 'androidx.compose.ui.window.*Preferences*'
  --tests 'androidx.compose.ui.window.WebMotionDurationScaleTest'
  --tests 'androidx.compose.ui.window.ComposeWindowDisposeOnDomRemovalTest'
  --tests 'androidx.compose.ui.window.ComposeViewportSizeTest'
  --tests 'androidx.compose.ui.platform.a11y.A11yNodePositioningTest'
)
TMPDIR="$PWD/.cmp10692/tmp" ./gradlew \
  :compose:ui:ui:cleanAllTests \
  :compose:ui:ui:jsBrowserTest "${filters[@]}" \
  :compose:ui:ui:wasmJsBrowserTest "${filters[@]}" \
  --no-build-cache --console=plain -Djava.io.tmpdir="$PWD/.cmp10692/tmp"
```

Result: **26 tests passed per target (52 total), Chrome 155 on macOS**. The final successful logs
are `.cmp10692/compile-5.log` and `.cmp10692/web-tests-2.log`. Earlier logs retain the initial
compiler/test failures and their diagnoses; they are not the final results. Temporary files and
logs are in the checkout and can be removed later. No Android tests/checks were run.
IDE diagnostics were unavailable because this checkout was not the project open in the connected
IDE; both Kotlin compilers and browser suites were used instead.

Coverage includes initial/non-default values, changes, unsupported features/APIs, legacy media
listeners, independent observers/windows, double disposal and queued callbacks, semantics
explicitly disabled, DOM-removal cleanup, resize disconnection and visibility/focus refresh,
unchanged/invalid/large font readings, sp relayout with unchanged dp and canvas geometry,
DPR/viewport independence, accessibility bounds, backing text-input placement, dark/light
regressions, and deterministic finite/infinite animation frames through motion changes.
The attached native probe passed isolation/hidden-content/scroll-extent checks in Chrome. This
is **not** evidence of a real 20px browser-profile setting being detected.

### Manual browser validation still required

At the user's request, real-browser preference validation is deferred. Run the following in
supported Chromium, Firefox, and WebKit/Safari configurations and record versions/settings:

1. Set the browser's default proportional/sans-serif font preference to 16px before page load:
   verify fontScale 1. Repeat with 20px already configured: expect 1.25. Use an actual profile
   preference, not DevTools CSS or an injected measurement.
2. Repeat with fixed-pixel and percentage html/root font resets. They must not change this
   measurement policy. Confirm that the hidden probe adds no content, focus target, or scroll area.
3. Change the browser default font with the viewport/DPR unchanged. Check whether ResizeObserver
   fires immediately, whether focus/visibility restoration is needed, or whether reload is needed.
   Verify sp layout, dp CSS geometry, input placement, and the semantics mirror together.
4. Exercise normal page zoom and pinch zoom independently of font preference changes. The existing
   raster-resolution cap must remain, and the font ratio must not incorporate DPR or viewport zoom.
5. Enable reduced motion before page load, toggle during a running animation, then restore motion
   while an infinite transition is waiting at zero. Record actual OS/browser media reporting and
   the inherited queued-frame latency separately from observer propagation.
6. Test minimum-font settings, text-only zoom, and OS text settings separately. Record differences;
   do not silently replace this policy with host root CSS sizing if default-font detection fails.
   If `medium` does not expose a supported engine's actual default preference, block the detection
   claim and review a replacement measurement with evidence before shipping that claim.

## Step 6: proposal for the remaining preferences — NOT implemented or approved

Observation alone is not feature completion. The following is a concrete review proposal, not
permission to modify common code or introduce public API. Start with an internal Material3 web
pilot and tests of resolved preferences/styles. Review the public boundary and shared hooks before
connecting application themes and stock components.

### State and fallback policy

Keep distinct contrast values `More`, `Less`, `Custom`, `NoPreference`, and `Unknown`. Keep
forced colors as a separate Enabled/Disabled/Unknown property; do not infer it from Custom.
Reduced transparency and inverted colors each get their own Enabled/Disabled/Unknown property.

Query all known values of each feature when distinguishing unsupported from neutral:
`prefers-contrast` (more/less/custom/no-preference), `forced-colors` (active/none),
`prefers-reduced-transparency` (reduce/no-preference), and `inverted-colors` (inverted/none).
No known value matching means Unknown, not a fabricated NoPreference. Unknown is neutral and
must not change the application palette/component behavior. These listeners are only added
alongside their reviewed consumers, not in the present implementation.

### Consumer/API boundary proposed for review

A root palette provider cannot override an application's nested MaterialTheme. Also,
`ButtonDefaults`/`SwitchDefaults`/`CheckboxDefaults`/`RadioButtonDefaults`/text-field defaults use
cached ColorScheme colors with alpha-based disabled states: merely replacing ColorScheme does
not map disabled content to GrayText. Both facts need consumer-level work.

Preferred sequence:

1. Prototype internal palette/style resolution in Material3 webMain/webTest, with injected
   preference snapshots. Do not expose UI-module internal observer types across module boundaries.
2. Review a **web-only** public UI snapshot boundary for custom components and a Material3 opt-in
   theme adapter. Avoid extending UiMediaScope or changing MaterialTheme's public parameters in
   this proposal. The exact draft declarations are below; none exist in this patch:

```kotlin
// Proposed ui/webMain, androidx.compose.ui.platform. Review required before adding public API.
enum class BrowserContrastPreference { More, Less, Custom, NoPreference, Unknown }
enum class BrowserPreferenceValue { Enabled, Disabled, Unknown }

data class BrowserSystemColors(
    val canvas: Color,
    val canvasText: Color,
    val buttonFace: Color,
    val buttonText: Color,
    val highlight: Color,
    val highlightText: Color,
    val linkText: Color,
    val grayText: Color
)

interface BrowserAccessibilityPreferences {
    val contrast: BrowserContrastPreference
    val forcedColors: BrowserPreferenceValue
    val reducedTransparency: BrowserPreferenceValue
    val invertedColors: BrowserPreferenceValue
    val systemColors: BrowserSystemColors?
}
val LocalBrowserAccessibilityPreferences:
    ProvidableCompositionLocal<BrowserAccessibilityPreferences>

// Proposed material3/webMain, androidx.compose.material3. Review required.
@Composable
fun BrowserAccessibleMaterialTheme(
    enabled: Boolean = true,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
    shapes: Shapes = MaterialTheme.shapes,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit
)

// Proposed ui/webMain opt-in for known raster assets, not arbitrary scene drawing.
fun Modifier.preserveImageColorsWhenInverted(): Modifier
```

The neutral snapshot must be usable outside a browser host. The API's visibility, stability/GA
intent, naming, and publication are explicit review gates, not assumed approvals. An internal
pilot is not production support available to applications.

3. For stock Material components under the opted-in adapter, review a neutral-by-default internal
   common hook (new `material3/src/commonMain/.../PlatformAccessibilityStyle.kt`):

```kotlin
internal interface PlatformAccessibilityStyle {
    fun colorScheme(base: ColorScheme): ColorScheme
    fun buttonColors(base: ButtonColors): ButtonColors
    fun checkboxColors(base: CheckboxColors): CheckboxColors
    fun radioButtonColors(base: RadioButtonColors): RadioButtonColors
    fun switchColors(base: SwitchColors): SwitchColors
    fun textFieldColors(base: TextFieldColors): TextFieldColors
    fun selectionColors(base: TextSelectionColors): TextSelectionColors
    fun scrimColor(base: Color): Color
    fun surfaceColor(base: Color, tonalElevation: Dp): Color
}
internal val LocalPlatformAccessibilityStyle:
    ProvidableCompositionLocal<PlatformAccessibilityStyle?> // default null
```

Exact shared files proposed for that bounded consumer change:

- `MaterialTheme.kt`: consult the hook at **each** MaterialTheme boundary and resolve text-selection
  colors through it; leave public signatures unchanged.
- `Button.kt`, `Checkbox.kt`, `RadioButton.kt`, `Switch.kt`, `TextFieldDefaults.kt`: adapt default
  colors after retrieving normal caches, without writing preference-specific values into shared
  ColorScheme caches. Explicit per-component color arguments remain application overrides.
- `Surface.kt`: avoid tonal overlays that corrupt forced-color role pairings; do not globally
  replace arbitrary surface alpha values.
- `SheetDefaults.kt`, `ModalBottomSheet.kt`, `NavigationDrawer.kt`: targeted opaque modal scrims
  when reduced transparency is requested, including their visibility/fade behavior.

The existing `LocalRippleThemeConfiguration` inset-ring support and
`LocalRippleConfiguration` ring colors can provide a solid two-color focus ring from webMain;
prefer these existing consumers over inventing another common focus API. Menus and custom
controls using other indications need explicit validation/consumer adaptations before being
claimed covered. Link-colored text is consumed explicitly by the custom component/link layer
using LinkText; a canvas-wide filter is not a substitute.

Inside an opted-in adapter scope, every nested MaterialTheme palette is treated as a base to be
adapted, **including explicitly supplied palettes**. That rule must be documented: opting in
prioritizes the system palette/contrast policy. A nested `enabled = false` adapter with an explicit
application palette opts a subtree out. Outside the adapter, all current behavior is unchanged.
Explicit component colors/custom drawing are not forcibly remapped: those consumers must use
the snapshot's role pairs themselves. This avoids pretending that a root observer controls every
application theme or canvas draw call.

### Rendering policy and measurable outcomes

| Preference | Proposed consumer behavior | Required visible validation |
| --- | --- | --- |
| Forced colors | Map Canvas/CanvasText to surfaces/body text; ButtonFace/ButtonText to unselected controls; Highlight/HighlightText to selected controls and text selection; LinkText to opted-in links; GrayText to disabled text/borders, without disabled-alpha blending. Use solid, distinguishable focus rings and remove role-corrupting tonal/state overlays where necessary. | Buttons, inputs, check/radio/switch selected and disabled states, focus traversal, text selection, links, menus, custom controls and DOM interop retain consistent foreground/background role pairs. Include Windows standard and custom contrast palettes. |
| Contrast More | Increase default text contrast toward 7:1 and boundary/focus visibility (2dp solid outlines); never recolor a forced-colors palette with a generic contrast transform. | Measured text/background ratios and control/focus boundaries; snapshot and pixel/geometry tests, not only a flag assertion. |
| Contrast Less | Reduce non-essential boundary/state emphasis toward the regular 1dp treatment while retaining normal-text 4.5:1 and UI 3:1 floors. | Compare More/Less/default renderings and verify the floors. Explicit component overrides remain the app's responsibility. |
| Contrast Custom | Preserve reported system role pairs when forced colors is independently active. Without a usable forced palette, retain the application colors but strengthen solid component boundaries/focus indication instead of inventing a custom palette. | Exercise both forced and non-forced Custom states, retain the distinct reported value, and verify the chosen boundary response. |
| Reduced transparency | Replace the selected bottom-sheet/drawer modal scrims with an opaque resolved background while visible; do not fade those scrims through intermediate opacity. Other motion and explicit application alpha are separate. | Inspect intermediate as well as final pixels for these scrims. No global alpha rewrite or replacement of every translucent drawing operation. Other translucent components remain tracked separately until mapped. |
| Inverted colors | Only opt-in photographic/raster assets receive targeted RGB compensation, preserving alpha; text, controls and the scene are not inverted again. | Validate actual OS/browser inversion, including whether the browser already compensates images. Compare an opted-in image with a normal image and text/control rendering; disable compensation where it would double-correct. |

Resolve system color keywords with attached hidden DOM probes into actual RGBA Compose colors;
never parse the keyword names as colors or use whole-canvas CSS/filter recoloring. Add further
system roles only for reviewed component pairings, keeping each foreground/background together.

Palette refresh: read at startup, relevant media changes, focus, and visibility restoration.
To catch a palette switch while forced-colors stays active, propose an injectable **1-second
visible/active-only refresh timer**, stopped when hidden, inactive, or disposed. Compare complete
palettes before publishing changes. This provides bounded foreground refresh latency without
continuous frames. Validate an active-to-active Windows palette switch; media-query boolean
changes alone do not satisfy that test. If an engine does not refresh resolved keywords without
a reload, record that concrete engine limitation rather than claim full live switching.

### Essential-motion policy proposed for separate review

Pause autonomous marquee movement when reduced motion is requested, retaining static/full-text
accessibility content. Keep direct gesture tracking and user-driven fling/snapping continuity as
an explicit essential-input exception (including Cupertino physics); duration-scaled programmatic
movement should honor the ambient scale. Autonomous custom/CSS/interop animations need their
own consumer policy. Implementing marquee's change would require prior approval for
`foundation/src/commonMain/.../BasicMarquee.kt`; it is not part of this patch. The existing fling
and snapping overrides remain exclusions until that policy is reviewed.

### Remaining delivery gates

- Supply real default-font profile and reduced-motion reporting evidence in supported engines;
  distinguish font preference settings from zoom/minimum/OS settings.
- Review the exact public UI/web and Material3/web API boundary and the listed internal shared
  consumer hooks, including explicit-override behavior. No listed common change is authorized yet.
- Implement and test each reviewed color/component consumer in step 7. Add web tests, and broaden
  compilation/tests to desktop/iOS as appropriate for any approved shared-code changes; no Android
  checks for fork work.
- Validate Windows contrast themes, a custom palette and active-to-active refresh, browser contrast
  settings, supported transparency settings, and actual inversion on an appropriate browser/OS.
- Keep CMP-10692 open until each preference has a validated rendering/component response or is
  explicitly tracked as remaining work. A set of observed media-query flags is not completion.
