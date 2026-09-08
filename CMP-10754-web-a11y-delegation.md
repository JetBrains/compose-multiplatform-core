# CMP-10754: Correct Web A11Y exposure for clipped and scroll-reachable nodes

## Task

Investigate, test, and fix [CMP-10754](https://youtrack.jetbrains.com/issue/CMP-10754) for Compose for Web accessibility.

The fix must be Web-only. Do not modify Material3 or any `commonMain` source.

## Regression

The regression was introduced by [PR #3366](https://github.com/JetBrains/compose-multiplatform-core/pull/3366), commit `06845cfe0bc320c2a90c817e90c518915322621d`.

Before that change, Web A11Y geometry used `SemanticsNode.boundsInRoot`, which is clipped. A fully closed modal navigation drawer therefore produced a `0 x 0` A11Y DOM node.

PR #3366 changed Web A11Y positioning to use `positionInRoot` and the measured `size`. This was necessary to retain content-space geometry for offscreen descendants of DOM-backed scroll containers, but it also gives a fully clipped, closed drawer its measured dimensions (for example, `360 x 776`).

Non-zero geometry is not inherently incorrect. Geometry and accessibility exposure should be modeled separately:

```text
positionInRoot + size  -> DOM geometry
boundsInRoot          -> one input to accessibility exposure
```

A zero-sized DOM node is not guaranteed to be absent from the browser accessibility tree. The desired outcome is therefore not to restore zero geometry, but to prevent assistive technology from discovering or interacting with nodes that are hidden for non-scroll reasons.

## Constraints

- Do not change `NavigationDrawer.kt`.
- Do not change any `commonMain` code.
- Preserve full `positionInRoot + size` geometry.
- Preserve browser/AT-driven scrolling added by PR #3366.
- Preserve DOM element identity across visibility transitions where the semantics node ID is unchanged.
- Support both JS and Wasm browser targets.
- Keep the implementation generic; do not detect drawers through `paneTitle`, `dismiss`, labels, roles, or other component-specific semantics.

## Required exposure model

Classify each Web A11Y semantic node as one of:

```text
Visible
    The clipped bounds are non-empty.

BeyondBounds
    The measured bounds are non-empty.
    The clipped bounds are empty.
    The clipping is attributable to a reachable DOM-backed scroll context.

Hidden
    The measured bounds are non-empty.
    The clipped bounds are empty.
    No reachable scroll context explains the clipping.
    OR an ancestor is Hidden.
```

Do not automatically classify genuinely zero-sized semantic nodes as hidden merely because their bounds are empty. Preserve their existing exposure behavior unless another rule hides them.

Map exposure to the DOM as follows:

```text
Visible       -> exposed
BeyondBounds  -> exposed, so AT/browser scrolling can reach it
Hidden        -> inert
```

Prefer the standard `inert` attribute over relying only on `aria-hidden`, because inertness also prevents focus and interaction. Reuse the existing Web A11Y inert helper where appropriate. If browser behavior requires both attributes, document and test that decision.

## Meaning of “active scroll context” on Web

iOS gates beyond-bounds nodes using native accessibility focus and the focused node's scrollable ancestors. Browsers do not provide a reliable standard event for screen-reader virtual-cursor focus.

Do not make initial discovery of offscreen items depend solely on DOM `focus`, `focusin`, or a prior `scroll` event. That would create a bootstrap problem: an item must be exposed before AT can navigate to it.

For Web, treat a scroll context as active/reachable when its DOM-backed A11Y scroll container is itself exposed or is reachable through an exposed outer scroll context.

## Implementation direction

Primary files:

- `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/accessibility/ComposeWebSemanticsListener.kt`
- `compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/accessibility/A11YScrollUtils.kt`
- Web tests under `compose/ui/ui/src/webTest/kotlin/androidx/compose/ui/platform/a11y/`

Suggested approach:

1. Extract or share the predicate that determines whether a semantics node becomes a DOM-backed scroll container. The exposure classifier and `A11YScrollController` must not disagree about scrollability.
2. Carry exposure context alongside the existing semantics/DOM-parent DFS traversal. Avoid an O(depth) ancestor walk for every node if the state can be propagated.
3. Track whether an ancestor is hidden and which reachable scroll contexts and supported axes apply.
4. Continue writing full geometry with `positionInRoot + size`.
5. Use clipped geometry (`boundsInRoot`) to detect full or partial clipping.
6. For a fully clipped, non-zero node, decide whether a reachable scroll context explains the clipping:
   - the node is beyond the viewport along an axis supported by that scroller;
   - it overlaps appropriately along non-scrollable axes;
   - no hidden ancestor blocks the subtree;
   - nested scroll contexts remain reachable through an exposed outer context.
7. Propagate hidden state. A scrollable list inside a closed drawer must not reactivate itself or its descendants.
8. Apply/remove node-level inertness without detaching or replacing the DOM element.
9. Ensure node-level inertness composes safely with the existing owner-root inertness used for modal layers.
10. Ensure delegated click/keyboard handlers cannot invoke semantic actions for a node under an inert ancestor, even if a programmatic DOM event is dispatched.

An intermediate non-scroll clipping layer inside a scroll container is an important ambiguity. A simple “has a scrollable ancestor” check is insufficient. Validate that the clipping can actually be explained by the candidate scroll viewport and its supported axes.

## Tests to add first

Use generic Compose UI primitives rather than depending on Material3. Reproduce the drawer behavior with a measured non-zero subtree translated fully outside a clipping parent.

### Required regression tests

1. **Offscreen non-scroll panel**
   - The node retains its measured CSS width and height.
   - It is inert when fully clipped.

2. **Visibility transition and DOM identity**
   - Start visible, move fully offscreen, then move visible again.
   - Inertness toggles in both directions.
   - The same `HTMLElement` instance remains attached throughout.

3. **Offscreen descendant of a visible scroll container**
   - The descendant retains content-space geometry.
   - Neither it nor an ancestor introduced by this classifier is inert.
   - `scrollIntoView` or the existing test approximation still updates Compose scroll state.

4. **Scrollable subtree inside a hidden panel**
   - The outer panel is inert.
   - The scroll container and all descendants remain inaccessible through inherited inertness.
   - A nested scroller must not override a hidden ancestor.

5. **Partially clipped node**
   - It remains exposed.
   - It retains full geometry as required by the new model.

6. **Nested scroll containers**
   - An offscreen inner container can remain reachable through an exposed outer scroller.
   - Its descendants use the correct scroll context.

7. **Axis mismatch**
   - A vertical-only scroller must not make a node exposed when its clipping is exclusively horizontal.
   - Add the corresponding horizontal case if useful.

8. **Intermediate non-scroll clip inside a scroll container**
   - A node hidden by that clip must not be classified as scroll-reachable merely because it has a scrollable ancestor.

9. **Focus safety**
   - If a focusable mirrored A11Y element becomes hidden/inert, focus must not remain active inside the inert subtree.
   - Re-exposure must not unexpectedly restore stale focus.

10. **Action safety**
    - Programmatic click or keyboard dispatch against an inert A11Y node must not invoke its Compose semantic action.

Extend existing scroll tests where that is clearer than duplicating their setup. Keep the new non-scroll exposure tests focused in a dedicated Web A11Y test class if it improves readability.

## Relevant platform precedent

The iOS accessibility implementation is the closest precedent:

- it builds visible/adjusted bounds separately;
- retains unclipped bounds for beyond-bounds elements;
- makes beyond-bounds elements accessibility-focusable only when reachable through an applicable scroll context;
- scrolls Compose through semantic scroll actions when such an element becomes focused.

Relevant sources:

- `compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/platform/Accessibility.ios.kt`
- `compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/platform/accessibility/SemanticsNodeUtils.ios.kt`
- `compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsOwner.kt` for reference only; do not modify it.

Desktop is not the model to copy here. Its current AWT implementation exposes clipped `boundsInRoot`, raw size, and reports `isShowing = true` with a TODO for actual visibility.

## Verification

Run the focused Web A11Y tests for both JS and Wasm, then the broader Compose UI Web test suite. Discover the exact available Gradle task names from the module rather than assuming them if necessary.

Also run all existing tests covering:

- Web A11Y scrolling;
- nested A11Y node positioning;
- modal/layer root inertness;
- incremental DOM synchronization and node reuse.

Review the resulting DOM manually or through assertions for these states:

```html
<!-- Closed/fully clipped, still measured -->
<div inert style="position: absolute; width: 360px; height: 776px">

<!-- Open/visible -->
<div style="position: absolute; width: 360px; height: 776px">
```

## Acceptance criteria

- A closed drawer-like subtree retains full geometry but is absent from accessibility interaction through inertness.
- Opening removes inertness; closing restores it.
- Visibility transitions reuse the existing DOM node.
- Offscreen scroll items remain exposed and browser/AT scrolling continues to work.
- Hidden ancestors always dominate nested scroll reachability.
- Scroll-axis mismatches and intermediate non-scroll clipping are not falsely exposed.
- Existing modal owner-root inertness continues to work.
- No Material3 or `commonMain` files are changed.
- Relevant JS and Wasm tests pass.
- The final change includes a concise explanation of the Web-specific definition of an active/reachable scroll context and why DOM focus cannot be used as a direct substitute for iOS accessibility focus.
