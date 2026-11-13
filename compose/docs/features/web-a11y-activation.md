# Web accessibility: opt-in DOM tree activation

Status: design draft, 2026-11-12. No implementation changes in this branch.

## Objective and scope

Defer the cost of projecting Compose semantics into the browser DOM until a user
requests it. Keep the running composition, application state, keyboard interaction,
and focus indicators intact. Once activated, maintain accessibility through the
viewport's lifetime, including in-app navigation and new dialogs/popups.

This defers the web accessibility bridge, not Compose's internal semantics model.
Internal semantics can still be required for Compose behavior and testing. Do not
promise zero semantics overhead before activation.

The implementation is limited to fork web source sets (`webMain`, `webTest`) and
web test/application fixtures. No common-source changes are planned. Any need for
common-source changes requires a separate discussion under repository guidance.
New configuration API is a proposal and must use the repository's applicable
experimental API conventions; this feature is not assumed to be GA.

## Decisions agreed with the task owner

- Change the default from eager DOM semantics to opt-in.
- Activation lasts for the current viewport lifetime. No cookies, localStorage,
  sessionStorage, URL flags, or browser-level preference are written by the library.
- Each viewport activates independently. This fits current viewport ownership and
  avoids a shared page registry. Activating viewport A does not activate B.
- A full page reload starts again from configuration. An eager viewport starts
  active; an opt-in viewport starts with its activation control.
- Localization uses a configuration string supplied when the viewport is created.
  Live locale updates are outside the initial API.
- Activation hands focus into the Compose accessibility DOM once it is ready,
  synchronously where safe or after the initial synchronization completes. The
  canvas is not the normal destination for this handoff.

## Existing behavior and integration points

Paths below are relative to the repository root.

- `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/window/ComposeViewportConfiguration.web.kt`:
  `isA11YEnabled` defaults to true and currently means maintaining a DOM tree.
- `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/window/ComposeWindow.web.kt`:
  builds the canvas (a tab stop), shadow DOM, and optional accessibility container.
- `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/window/WebWindowPlatformContext.kt`:
  constructs `ComposeWebSemanticsListener` when enabled; reports screen-reader
  activity from configuration. The semantics container has opacity zero and
  pointer events disabled initially. User activation restores pointer hit testing
  for the semantics container. Eager mode keeps pointer events disabled.
- `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/window/ComposeWindowInternal.web.kt`:
  starts the listener after initial composition and stops it on disposal.
- `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/accessibility/ComposeWebSemanticsListener.kt`:
  tracks semantics owners, schedules debounced updates (100 ms, forced by 1000 ms
  of continuing invalidations), and maps semantics nodes to DOM elements.
- `compose/ui/ui/src/webTest/kotlin/androidx/compose/ui/platform/a11y/`:
  existing tests assume eager tree creation and need an explicit eager fixture.

## Configuration contract (proposed names)

```kotlin
ComposeViewport(/* ... */, configure = {
    accessibilityActivation = WebAccessibilityActivation.OnUserRequest(activationLabel = translatedLabel)
}) { /* existing content */ }
```

This is illustrative syntax; verify the exact overload when implementing samples.
Configuration is read during viewport creation, not a reactive runtime controller.

| WebAccessibilityActivation | Initial behavior |
| --- | --- |
| Disabled | No activation control or web semantics bridge |
| OnUserRequest(activationLabel) | Activation control, with DOM projection deferred until activation. This is the default. |
| Eager | Full tree initialized at startup, with no activation control |

The existing `isA11YEnabled` property reads and writes the same mode. Its getter
is true for `OnUserRequest` and `Eager`. Setting it to false selects `Disabled`.
Setting it to true selects `OnUserRequest()` with the default label from `Disabled`
and preserves an existing enabled configuration. Document the changed default
and the eager setting in release notes and examples.

The English label is the default. The application supplies a nonblank translation
for both the accessible name and visible button text, with host language/direction
respected. Fall back to the default for blank input. Multiple viewports can use
contextual labels such as “Enable accessibility for editor”. The label is fixed
for the viewport lifetime; live locale changes are outside the initial API.

## Activation control

Use a native `<button type="button">` before the canvas in logical DOM/tab order,
inside the viewport's shadow DOM but outside the transparent semantics container.
Give it the configured text, an accessible name from that text, and a stable
`data-cmp-a11y-activation` attribute for automation. Do not require an element ID,
English-label selector, or knowledge of generated semantics IDs.

The button is visually clipped at rest but remains a normal tab stop. On keyboard
focus, reveal it within the viewport with readable text, contrast, and a visible
focus indicator. Its focused presentation must not be clipped by viewport overflow
or scroll the surrounding page unexpectedly. Do not use `display:none`, `hidden`,
`aria-hidden`, `inert`, or negative tabindex on this control. Do not put an invisible
overlay over the canvas or intercept ordinary canvas pointer/touch events.

Native activation handles Enter, Space, screen-reader activation, touch exploration
activation, and DOM click. Trigger activation on click, not focus or pointer-down;
Tab past the button must leave the application fully keyboard-operable. Avoid
duplicate key handlers that activate twice. No special user-gesture or screen-reader
detection heuristic is required.

The supplied Flutter placeholder is motivation, not a literal markup requirement.
Its described keyboard-inaccessible behavior would conflict with this task's
explicit requirement for keyboard access. No live region is required for the
button itself; avoid an unnecessary activation announcement or whole-tree chatter.

## Lifecycle and state machine

```text
disabled -------------------------------> disposed
awaiting activation -> activating -> active -> disposed
eager construction --> activating -> active
                       |
                       +-- initialization failure -> awaiting activation
```

1. Disabled viewports create neither the control nor the bridge.
2. Awaiting viewports retain only current owner registrations and minimal state
   needed for later activation. Do not traverse/project semantics, calculate DOM
   geometry, enqueue debounced tree updates, or run bridge jobs while dormant.
3. Activation is idempotent. Keep the control available while initialization runs;
   prevent duplicate work without dropping focus (prefer a busy state over native
   disabling of a focused button).
4. Attach the bridge to the existing scene and build from all currently attached
   owners. Prefer a synchronous initial synchronization in the activation handler
   when the existing scene has current, placed layout and synchronization is safe.
   Otherwise synchronize at the next safe post-layout opportunity. Do not wait for
   a later semantics mutation to discover old nodes or use the ordinary debounce
   delay for this first synchronization. A fixed one-frame delay is not the contract.
5. Reconcile owner additions/removals and changes during activation before declaring
   readiness. An owner removed while dormant must never reappear in the DOM.
6. After successful initial synchronization, perform the focus handoff, remove the
   activation control, and mark the root `data-cmp-a11y-state="active"`. Publish
   readiness only after the initial DOM and handoff are complete. Before readiness,
   use `awaiting`/`activating` on the same viewport-local state marker.
7. Active is irreversible until disposal. Existing debounce/update behavior resumes.
   Recomposition, route changes, and dialogs must not reset activation.
8. Disposal cancels jobs, unregisters owners/listeners and removes DOM. Disposal
   during activation cancels completion; queued callbacks cannot recreate the tree.
9. A recoverable initialization failure cleans up partial bridge state and restores
   a usable activation control. Do not advertise readiness. Report through existing
   error handling rather than inventing a new public error API.

Prefer a stable `SemanticsOwnerListener` with dormant/active behavior over replacing
it after scene creation: scene owners can already be registered before activation.
The existing concrete `webSemanticsListener` testing/lifecycle accessor must continue
to work or be adjusted explicitly. Separate lightweight owner tracking from costly
projection and start/stop jobs. Reuse the existing scene/composition scope.

Audit `PlatformScreenReader.isActive` consumers. Eligibility is no longer equivalent
to an active bridge; report the appropriate activation state without claiming that
the browser detected a screen reader. Eager mode must retain its previous behavior.

## Keyboard and focus contract

Before activation, existing canvas event routing, Compose focus traversal, backing
text inputs, editing, and Compose-drawn focus indicators remain operational. The
activation button adds one deliberate entry tab stop; it must not consume keys
destined for the canvas or create a tab trap. Test both forward and reverse Tab.

Activation must not recreate the scene, reset Compose focus, clear selection, close
menus, reset scroll, restart text input/IME, or blur an unrelated host DOM control.
Compare the deep active element inside shadow DOM, not only `document.activeElement`.

The current DOM projection method (`syncSemanticsWithWebA11Y`) is synchronous and
non-suspending: it traverses registered owners and applies their DOM updates before
returning. Its usual delay comes from the invalidation/debounce pipeline, not an
inherent need for another frame. An internal initial-sync entry point could bypass
that delay. This does not prove that layout is always current in an activation
handler, or that the browser's assistive-technology representation updates at the
same time. Confirm scene/layout ordering during implementation; avoid forcing a
recomposition or reentrant layout just to promise synchronous activation.

Handoff when the activation button itself still owns DOM focus:

- Preserve the current Compose focus target. If a corresponding safe, focusable
  semantics DOM target exists, transfer DOM focus there with scroll prevention.
- Otherwise choose the first exposed element in accessibility traversal order,
  respecting the active modal owner and excluding inert/hidden content. Allow
  programmatic focus with `tabindex="-1"` where necessary without adding a new
  keyboard tab stop. Do not choose the canvas merely because projection is pending.
- If synchronization must be deferred, retain focus on the activation button until
  the initial DOM exists, then perform the handoff. Use an explicit completion
  signal from synchronization rather than a timeout or an assumed frame count.
- If the tree is empty, provide a programmatically focusable entry within the
  accessibility DOM with an appropriate accessible name/role. Its exact markup
  needs validation; do not remove the focused button with no valid destination.
- Do not invoke semantic click actions just to transfer focus. Editable semantics
  elements currently have focus listeners that click; those need special care.
- If focus moved elsewhere during asynchronous initialization, leave it there.
  Remove the button only after arranging a valid destination when necessary.

Browser DOM focus and screen-reader virtual cursors are different. There is no
portable guarantee that restoring DOM focus preserves a virtual cursor. Validate
both immediate and deferred handoffs with actual screen readers; if either strands
users or restarts reading, revise it before shipping. Preserve an existing Compose
focus target when it can serve as the entry point.

## Use cases and edge cases

| Scenario | Expected behavior |
| --- | --- |
| Screen-reader user arrives | Activation control is discoverable before full semantics; activation exposes current content without reload. |
| Keyboard user, no screen reader | Button reveals on focus; Enter/Space activate; Tab can skip it; existing keyboard UI remains usable. |
| Pointer-only user | No invisible hit target over content; no projection until explicitly requested or configured eagerly. |
| State changes while dormant | First projection reflects current state, not an initial snapshot or queued history. |
| Activation while typing | Text, selection, backing input and IME remain intact; unrelated focus is preserved. |
| Dialog open before activation | Initial tree includes current modal ownership/exposure; do not focus behind the dialog. |
| SPA route change or history navigation | Same viewport remains active; project new route content normally. |
| Viewport disposed and recreated on a route | New instance follows its configuration and asks again in opt-in mode; apps needing continuity should retain the viewport or use eager mode. |
| Reload, hard reload, full-document link, new tab | New document/viewport starts from configuration; no preference is restored by the library. |
| Browser back/forward cache | A restored live viewport retains its state; a recreated document starts over. Verify no duplicate handlers on pageshow. |
| Several viewports or embedded apps | Independent controls and state; eager/disabled siblings are unaffected; labels may identify each region. |
| Zero-size, offscreen, or CSS-hidden viewport | Follow host visibility; no global overlay. When revealed, the control and active semantics use current geometry. |
| Rapid repeated activation / immediate unmount | One initialization, no duplicate tree, no delayed DOM after disposal. |
| AT starts after page load | Control remains available indefinitely while awaiting activation; no timeout or input-based removal. |
| Storage disabled / private browsing | Same behavior; no storage dependency. |
| Eager application / accessibility audit | Full tree without activation; await readiness rather than assuming synchronous DOM creation. |

## E2E and regression strategy

Provide two explicit paths; do not auto-enable semantics on webdriver/headless
detection, user agent, arbitrary keypress, or test framework presence.

1. Exercise the product flow: locate the viewport, find the native button by role
   and localized name, activate, then wait for its root's active-state marker and
   assert expected semantic content. Also cover real Tab/Enter and Tab/Space.
2. In test application fixtures for existing semantic-content suites, set
   `accessibilityActivation = WebAccessibilityActivation.Eager`. This avoids repeating opt-in in every unrelated test.

The button and readiness marker live in the existing open shadow DOM.
[Playwright role locators](https://playwright.dev/docs/locators#locate-in-shadow-dom)
can traverse open shadow roots;
[Selenium](https://www.selenium.dev/documentation/webdriver/elements/finders/#evaluating-the-shadow-dom)
should obtain the host's shadow root explicitly and then locate the button/marker.
Do not rely on XPath crossing shadow boundaries. Since the button is clipped at
rest, focus it first to reveal it before using pointer-click automation; do not
assume an ordinary Playwright/WebDriver click passes visibility/hit-test checks.
Use condition-based waits, not fixed sleeps or an assumed 100 ms readiness delay.
Synthetic clicks are allowed; test assistive-technology and real keyboard actions
separately. No public runtime activation/controller API is needed initially.

Add `webTest` coverage for all activation modes, no dormant traversal or
scheduled projection work, current-state initial snapshot, owner churn, idempotence,
disposal/failure cleanup, routing, localization, multiple viewports, keyboard input,
focus preservation, and continued updates after activation. Keep current A11Y tests
explicitly eager; add separate tests that verify the actual new default.
Cover both synchronous initial synchronization and deferred post-layout completion,
an empty tree, and a user moving focus away while activation is pending. Assert
that a delayed completion never steals that user's new focus.

For implementation, compile JS and WasmJS test executables before running the
affected browser tests. Use `--console=plain`, saved logs, and relevant fork tasks:
`compileTestDevelopmentExecutableKotlinJs`,
`compileTestDevelopmentExecutableKotlinWasmJs`, then clean test results and run
`jsBrowserTest` and `wasmJsBrowserTest` for `:compose:ui:ui`. No Android validation.
This design-only change does not require builds.

Manual acceptance: VoiceOver/Safari on macOS and iOS, NVDA with Chrome/Firefox,
TalkBack/Chrome, and keyboard-only use. Include touch discovery, activation, reading
continuation, forms/IME, modal content, mixed HTML/Compose, zoom, forced colors,
and visible focus under clipped/scrollable viewport layouts. Automation cannot
establish screen-reader discoverability or virtual-cursor continuity on its own.

Measure dormant versus eager bridge work with an identical changing scene: projected
DOM node count, traversal/sync count, jobs, allocations/retention, startup time and
frame time. Measure activation latency and active-mode steady-state cost. Dormant
bridge projection/sync count must be zero; do not claim a performance percentage
before collecting measurements.

## Compatibility and acceptance gates

The default change means DOM-based tests, accessibility audits, and tools that read
semantic content before user activation will see only the entry control. Publish
the eager setting and migration guidance together with the change. Applications
must translate the entry label for supported locales.

Ship only after the control is discoverable with supported assistive technology,
keyboard use works before activation, the handoff permits immediate continuation,
and both browser targets pass their relevant tests. Remaining DOM accessibility
limitations are separate issues; this feature does not establish WCAG conformance
or legal compliance.

## Open questions

1. Confirm safe synchronous initial-sync conditions against scene/layout ordering;
   otherwise use explicit completion after the next safe post-layout sync.
2. Validate focus entry for noninteractive/empty content and browser/AT timing.
   Choose the empty-tree entry's accessible name/role during implementation.
3. Confirm configuration names, experimental API annotation, and release-note
   destination during implementation. No additional public runtime API proposed.

## References

- [Flutter web accessibility](https://docs.flutter.dev/ui/accessibility/web-accessibility):
  documents its invisible opt-in button and eager programmatic alternative. It is
  precedent for deferring a DOM projection, not evidence that our keyboard or focus
  requirements are met.
- [W3C: Understanding Conformance, conforming alternate versions](https://www.w3.org/WAI/WCAG21/Understanding/conformance#conforming-alt-versions):
  discusses access through accessibility-supported mechanisms and full-page/process
  conformance. An opt-in switch alone is not proof of conformance.
