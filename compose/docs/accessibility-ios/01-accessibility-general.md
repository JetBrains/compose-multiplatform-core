# iOS Accessibility — General Architecture

Compose Multiplatform renders to a single Metal-backed `UIView` with no native subviews. iOS accessibility cannot discover UI elements through the view hierarchy, so the bridge constructs a **virtual accessibility element tree** from the Compose semantics tree and exposes it via `UIAccessibilityContainer`.
Currently, `OverlayInputView` is used to be the host for the accessibility tree as it the most top and interactive view.

## Class Structure

**`AccessibilityMediator`** — one instance per Compose scene. Owns the element cache, the sync loop, and all focus state.

**`AccessibilityRoot`** — single entry point for the virtual tree. Loads the tree lazily — it is not built until iOS first queries the accessibility elements.

**`AccessibilityElement`** — wraps one `AccessibilityNode` and delegates all UIAccessibility property methods to it.

**`AccessibilityNode`** — interface that defines behavior for the `AccessibilityElement`. There are two types:
- **`Semantics`** — provides the semantics-based properties that define the specific accessibility element.
- **`Container`** — never focusable. iOS cannot make an element both focusable and a container simultaneously (see Tree Topology: Flattening below), so Container exists solely to provide grouping for VoiceOver and scroll metrics for FKA.

**`CMPAccessibilityElement`** (ObjC) — UIKit dispatches accessibility queries via ObjC message dispatch. Kotlin/Native cannot dynamically override ObjC methods defined in ObjC extensions, which is where the majority of accessibility-related methods are declared. The ObjC base class is used to bypass this restriction. Kotlin subclasses override via interop.

## Accessibility Tree Synchronization

The accessibility tree structure is designed to replicate the combined behavior of Android's `AndroidComposeViewAccessibilityDelegateCompat` and the TalkBack screen reader. iOS has no equivalent of TalkBack's tree interpretation layer — the bridge must produce the final, ready-to-consume tree that iOS reads directly. This means tree topology decisions (flattening, grouping, merging) that TalkBack handles implicitly on Android must be explicitly computed here.

### Lifecycle: When the Tree Exists

The tree is **not built until iOS first requests it** — triggered by the first `accessibilityElements` query or by the corresponding methods used by Full Keyboard Access. If no queries arrive for 2 seconds after the accessibility tree was last updated, the tree is disposed. Every time the accessibility service updates the tree, it posts a notification about the changes (see Notifications section). The accessibility service will re-read the tree in response unless it has been deactivated.

The `isEnabled` flag controls whether the mediator is allowed to build the tree at all. It can be disabled when the corresponding Compose Scene is either not visible or not accessible in the user interface (e.g., covered by a Dialog).

### Invalidation: What Triggers a Sync

Any semantics or layout change calls `invalidateSemanticsTree()`, which will eventually trigger a sync. The update is throttled by the main loop to debounce rapid changes. In addition, the sync mechanism uses an extra delay after consuming an invalidation:
- **VoiceOver active (no keyboard focus):** 100ms delay. iOS's accessibility engine polls the element tree no faster than this interval. Syncing faster wastes CPU — VoiceOver ignores intermediate updates within its polling window.
- **Full Keyboard Access active (has a focused element):** no delay. FKA can quickly update focused element boundaries, and removing the delay increases visual feedback and improves keyboard navigation stability.

### Tree Topology: Flattening

**The core iOS constraint:** When `isAccessibilityElement` returns `true`, iOS completely ignores that element's container methods. A node **cannot simultaneously be focusable and contain children.** On Android, `AccessibilityNodeInfo` can be both — iOS has no equivalent. This forces the tree to be flattened.

The initial implementation used a hierarchical tree. iOS silently ignored children whenever the parent was focusable. Users reported VoiceOver skipping entire subtrees. The only solution: container nodes must be non-focusable, with their focusable descendants hoisted into their element list.

`flattenAccessibilityChildren` recursively collects descendants of a container into three groups:
- **Visible** — nodes present in `getAllUncoveredSemanticsNodesToIntObjectMap()` (on-screen, not occluded)
- **Before-bounds and After-bounds** — off-screen nodes that can be focused when the accessibility engine navigates to them. See [Beyond-Bounds Focusability](#beyond-bounds-focusability) for details.

### Node Focusability: `canBeAccessibilityElement()`

`canBeAccessibilityElement()` is the central predicate that determines whether a `SemanticsNode` should become an independent accessibility element or be merged into its parent (or as a non-accessible element, located inside its focusable parent).
It returns `true` for in the following cases (if the node is valid and visible):
- The node is actionable node.
- The node is mering semantics of its descendants.
- The node is a simple speaking node and does not have a parent that merged its semantics.
See semantics tests for mode details.

### Containers vs Semantics Nodes

`traverseChildren` decides how each node maps to an `AccessibilityElement`:

**Leaf nodes** (not a traversal group, flattened into parent) → a `Semantics` node with no children. These can be focused by VoiceOver, Full Keyboard Access, or found by UI testing frameworks.

**Container boundaries** (traversal groups, root node, or interactive nodes where flattening stops) → the node and its flattened descendants are traversed. After collecting and sorting the three groups (before-bounds, visible, after-bounds), the combined list becomes the element's children. This addresses the iOS limitation where an element with `isAccessibilityElement = true` cannot contain other elements with `isAccessibilityElement = true`. In this situation, a `Container` is created, and the node itself (as a `Semantics` child) along with all its accessible children are placed at the same level inside the container.

### Beyond-Bounds Focusability

Beyond-bounds elements exist to let the accessibility engine navigate to the next and previous elements within a long scrollable list, even when those elements are not currently visible on screen. However, off-screen elements should only be focusable when another element inside the same scrollable container is already focused. This prevents VoiceOver from unexpectedly selecting an off-screen element when focus shifts from outside the container to inside it.

The rule: an off-screen element is focusable only if it shares a scrollable ancestor with the currently focused element (tracked via `focusedNodesScrollableParentsIds`). This ensures that off-screen elements in one `LazyColumn` are not focusable when the user is navigating a different `LazyColumn`.

### Merged Semantics

`isMergingSemanticsOfDescendants` nodes get their own `Semantics` element to match Android's `mergeDescendants` behavior.
When `flattenAccessibilityChildren` encounters a node with `isMergingSemanticsOfDescendants`, it sets `collectOnlyAccessibilityElements = true` for the recursive traversal of that node's descendants. This changes the collection behavior: only children that pass `canBeAccessibilityElement()` are added to the flat list. Non-accessible descendants (plain Text, layout nodes without actions) are skipped because their content is already merged into the parent's `config`.

## Property Caching

Computing merged `SemanticsConfiguration` traverses the subtree on every `config` access. iOS may query the same property multiple times per frame, so properties are cached to avoid redundant subtree merges.

> **HACK — `isAccessibilityElement` is NOT cached:** Visibility (opacity, clipping) can change between sync cycles (e.g., during fade-out animations) without triggering `onSemanticsChange`.

## Semantics → UIAccessibility Property Mapping

### Traits (`SemanticConfigurationUtils.ios.kt`)

| Compose Property | UIAccessibilityTrait | Why |
|---|---|---|
| `LiveRegion` | `UpdatesFrequently` | Closest UIKit equivalent; tells VoiceOver to periodically re-read |
| `ToggleableState.On` | `Selected` | iOS has no "checked" trait. `Selected` is the closest equivalent. `Off` and `Indeterminate` add no trait — absence of `Selected` implies off |
| `ProgressBarRangeInfo` + `SetProgress` | `Adjustable` | Both required: read-only progress shouldn't offer swipe-up/down adjustment |
| `EditableText` | `CMPAccessibilityTraitTextView` | Undocumented trait — see hack below |
| `EditableText` + `Focused` | above + `CMPAccessibilityTraitIsEditing` | Undocumented trait — see hack below |
| `OnClick` (without `EditableText`) | `Button` | Text fields have `OnClick` (for focus) but should be announced as "text field", not "button", so `EditableText` is excluded from the button trait |
| `Role.DropdownList` | `Adjustable` | No iOS dropdown trait. `Adjustable` lets VoiceOver offer swipe-up/down to cycle options |
| `Role.Switch` (iOS 17+) | `ToggleButton` | `UIAccessibilityTraitToggleButton` was added in iOS 17. On iOS 16, Switch falls through to `Button` from `OnClick` |
| `Text` + `GetTextLayoutResult` + `ShowTextSubstitution` | `StaticText` | Triple condition identifies "real" static text vs. elements that happen to have text content |

Roles not listed (`Checkbox`, `RadioButton`, `Tab`, `ValuePicker`, `Carousel`) receive no additional trait — no specific iOS traits exist for these. They inherit `Button` from `OnClick`.

> **HACK — Undocumented text field traits:** `CMPAccessibilityTraitTextView` is used to make accessibility elment treated as a text view. `CMPAccessibilityTraitIsEditing` is used to indicated that text field is currently editing. 

> **`LiveRegionMode`:** Currently not implemented.

### Value (`accessibilityValue()`)

Designed to provide additional information about the dynamic state of the accessibility element. The content of this field should not be duplicated inside the `accessibilityLabel()` or `contentDescription`.

### Label (`accessibilityLabel()`)

Primary content of the accessibility element. Can merge content if its descendants if they are not independent accessibility elements. To do so, it uses the `NodeDescriptionCollector` - its behavior should be aligned with the corresponding algorhithm in the TalkBack app. 

### Custom Actions

`UIAccessibilityCustomAction` has no "disabled" concept, so the handler returns `false` when the node is disabled. This tells VoiceOver the action failed (error sound) — the closest approximation to a disabled custom action.

### Child Sorting (`sortFlattenChildren`)

The common Compose sort (`sortByGeometryGroupings()`) is defined by the common code and shared between all platforms.

## Notifications

| Notification | When | Why |
|---|---|---|
| `ScreenChanged` | Escape; accessibility enabled; major focus change | Tells VoiceOver "screen changed significantly" |
| `LayoutChanged` | Tree synced; focused element lost | "Content changed but screen is the same" |
| `PageScrolled` | Scroll completed; scrollable parents changed | Announces scroll position and forces Speak Screen re-read |

## Coordinate System

All `SemanticsNode` bounds are in **pixels**. UIKit uses **points** (scale-independent). The conversion goes through Dp as an intermediate step: `Rect (px) → toDpRect(density) → asCGRect() → view.convertRect(toView: nil) → screen CGRect`.

## Native UIView Interop

Native views (MapKit, WebView, video players) have their own UIAccessibility implementations. The bridge delegates hit testing and focus to the native view rather than overriding them. The interop view shares the same `isBeyondBoundsOrFocusable` rule as Compose elements, ensuring consistent beyond-bounds logic in mixed layouts.
