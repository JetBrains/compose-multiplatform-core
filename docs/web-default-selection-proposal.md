# Proposal: Default Text Selection for Compose Web

## Problem

In Compose, text is not selectable unless a developer explicitly wraps content in
`SelectionContainer`. On the web, users expect plain text to be selectable by default — this is
a fundamental browser behavior.

The naive solution — wrapping the entire app root in `SelectionContainer` — makes *all* text
selectable, including labels inside `Button`, `Checkbox`, `Tab`, and other interactive
components. That breaks the expected UX (browsers do not let users select button labels either).

### Constraints

- No changes to Material3 components.
- No per-component `expect`/`actual` wrappers.
- No manual developer action required (e.g. wrapping every button with `DisableSelection`).
- Must work for custom components using raw `Modifier.clickable`, not just Material3.
- Must cover all selection entry points: mouse drag, long-press, **and Select All**.

---

## Proposed Solution

Two pieces work together:

1. **A new internal `InteractiveAreaRegistry`** — a composition local that tracks the layout
   bounds of every `Modifier.clickable` node in the tree.
2. **`SelectionManager` consults the registry** before starting selection or including a
   selectable in Select All, skipping anything that falls inside an interactive component.

This requires changes only in `foundation`. Material3 and all other interactive components are
covered automatically because everything interactive in Compose funnels through
`AbstractClickableNode`.

---

## Design

### The `isInsideInteractiveArea` check

The registry answers one question: *"does this selectable's bounding box fall inside any
registered clickable?"*

```
┌─────────────────────────────────────────────────────┐
│  WebDefaultSelectionContainer (root, web only)       │
│                                                     │
│  Text("Page title")      ← selectable, included    │
│                                                     │
│  ┌─ Button ────────────────────────────────────┐   │
│  │  AbstractClickableNode registered in registry│   │
│  │                                             │   │
│  │  Text("Submit")  ← selectable, but EXCLUDED │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  Text("Footer note")     ← selectable, included    │
└─────────────────────────────────────────────────────┘
```

The exclusion applies to:
- **Gesture start** — selection cannot be initiated by clicking/dragging inside a button.
- **Select All (Ctrl+A)** — button labels are skipped when building the full selection.

### Nested explicit `SelectionContainer` restores old behavior

If a developer wraps content in the public `SelectionContainer`, the registry is cleared for
that subtree. This preserves the existing contract: explicit opt-in means everything inside
is selectable, including button labels.

```
WebDefaultSelectionContainer (root)       registry active
    │
    ├── Text("page text")                 ← excluded from interactive areas
    │
    └── SelectionContainer (user)         registry = null (cleared)
            │
            ├── Button { Text("click") }  ← selectable (user explicitly opted in)
            └── Text("caption")           ← selectable
```

---

## Required Changes (5 touch points, 4 files)

### 1. New file — `InteractiveAreaRegistry.kt`

Location: `compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/`

```kotlin
internal interface InteractiveAreaRegistry {
    /**
     * Register a clickable node's layout coordinates.
     * Returns an unregister lambda.
     */
    fun register(getCoords: () -> LayoutCoordinates?): () -> Unit

    /**
     * Returns true if the given selectable's layout coordinates fall
     * entirely or partially inside any registered interactive area.
     */
    fun isInsideInteractiveArea(selectableCoords: LayoutCoordinates): Boolean
}

internal class InteractiveAreaRegistryImpl : InteractiveAreaRegistry {
    private val entries = mutableListOf<() -> LayoutCoordinates?>()

    override fun register(getCoords: () -> LayoutCoordinates?): () -> Unit {
        entries.add(getCoords)
        return { entries.remove(getCoords) }
    }

    override fun isInsideInteractiveArea(selectableCoords: LayoutCoordinates): Boolean {
        return entries.any { getCoords ->
            val interactiveCoords = getCoords() ?: return@any false
            // Check whether the selectable's origin falls within the interactive area
            val origin = interactiveCoords.localPositionOf(selectableCoords, Offset.Zero)
            origin.x >= 0f && origin.y >= 0f &&
                origin.x <= interactiveCoords.size.width &&
                origin.y <= interactiveCoords.size.height
        }
    }
}

/**
 * CompositionLocal providing an [InteractiveAreaRegistry].
 * Null by default — no exclusion logic runs on Android/Desktop.
 * Provided by [WebDefaultSelectionContainer] on web.
 */
internal val LocalInteractiveAreaRegistry = compositionLocalOf<InteractiveAreaRegistry?> { null }
```

---

### 2. `Clickable.kt` — `AbstractClickableNode`

**File:** `compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Clickable.kt`

Add `GlobalPositionAwareModifierNode` to `AbstractClickableNode`'s interface list and register
with the registry on attach, unregister on detach.

```kotlin
internal abstract class AbstractClickableNode(...) :
    DelegatingNode(),
    PointerInputModifierNode,
    // ... existing interfaces ...
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode,
    GlobalPositionAwareModifierNode,  // ← ADD
    ...
{
    private var interactiveCoords: LayoutCoordinates? = null
    private var unregisterInteractive: (() -> Unit)? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        interactiveCoords = coordinates
    }

    override fun onAttach() {
        super.onAttach()
        updateInteractiveRegistration()
    }

    override fun onDetach() {
        unregisterInteractive?.invoke()
        unregisterInteractive = null
        super.onDetach()
    }

    // Call this from the existing onObservedReadsChanged() so re-parenting is handled
    private fun updateInteractiveRegistration() {
        unregisterInteractive?.invoke()
        unregisterInteractive =
            currentValueOf(LocalInteractiveAreaRegistry)?.register { interactiveCoords }
    }
}
```

**Cost on Android/Desktop:** `currentValueOf(LocalInteractiveAreaRegistry)` returns `null`
(the default). The register call is never made. Zero overhead.

---

### 3. `SelectionManager.kt` — two additions

**File:** `compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/SelectionManager.kt`

**Add a nullable property:**

```kotlin
internal var interactiveAreaRegistry: InteractiveAreaRegistry? = null
```

**In `onSelectionUpdateStartCallback`** (currently around line 326), add an early return before
`startSelection` is called:

```kotlin
selectionRegistrar.onSelectionUpdateStartCallback =
    { isInTouchMode, layoutCoordinates, rawPosition, selectionMode ->
        ...
        val positionInContainer = convertToContainerCoordinates(layoutCoordinates, position)

        if (positionInContainer.isSpecified) {
            // Don't start selection when the gesture originates inside an interactive area
            if (interactiveAreaRegistry?.isInsideInteractiveArea(layoutCoordinates) == true) {
                return@onSelectionUpdateStartCallback
            }

            this.isInTouchMode = isInTouchMode
            startSelection(...)
            ...
        }
    }
```

**In `selectAll()`** (currently around line 619), filter selectables:

```kotlin
selectables.fastForEach { selectable ->
    // Skip text that lives inside a button/clickable
    val selectableCoords = selectable.getLayoutCoordinates()
    if (selectableCoords != null &&
        interactiveAreaRegistry?.isInsideInteractiveArea(selectableCoords) == true
    ) {
        return@fastForEach
    }

    val subSelection = selectable.getSelectAllSelection() ?: return@fastForEach
    ...
}
```

---

### 4. `SelectionContainer.kt` (public overload) — clear the registry

**File:** `compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/SelectionContainer.kt`

When a developer explicitly uses `SelectionContainer`, clear `LocalInteractiveAreaRegistry` so
the old "everything is selectable" behavior is restored inside that subtree:

```kotlin
@Composable
fun SelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var selection by remember { mutableStateOf<Selection?>(null) }
    CompositionLocalProvider(LocalInteractiveAreaRegistry provides null) {  // ← ADD
        SelectionContainer(
            modifier = modifier,
            selection = selection,
            onSelectionChange = { selection = it },
            children = content,
        )
    }
}
```

---

### 5. New file — `WebDefaultSelectionContainer.web.kt`

Location: `compose/foundation/foundation/src/webMain/kotlin/androidx/compose/foundation/text/selection/`

```kotlin
/**
 * Root selection container for web. Wraps the entire application content so that plain text
 * is selectable by default, while text inside interactive components (buttons, checkboxes, etc.)
 * is automatically excluded.
 *
 * This is applied automatically at the web platform entry point and is not part of the public API.
 */
@Composable
internal fun WebDefaultSelectionContainer(content: @Composable () -> Unit) {
    var selection by remember { mutableStateOf<Selection?>(null) }
    val registry = remember { InteractiveAreaRegistryImpl() }
    CompositionLocalProvider(LocalInteractiveAreaRegistry provides registry) {
        // Use the internal SelectionContainer overload directly so that
        // LocalInteractiveAreaRegistry is NOT cleared (unlike the public overload).
        SelectionContainer(
            selection = selection,
            onSelectionChange = { selection = it },
            interactiveAreaRegistry = registry,
            children = content,
        )
    }
}
```

Wire this into the web canvas/scene host (wherever `setContent` is called for web targets).

---

## Behavior Matrix

| Scenario | Button text selectable? | Plain text selectable? |
|---|---|---|
| Default (no container) — current | No | No |
| Default (no container) — proposed web | No | **Yes** |
| Inside explicit `SelectionContainer` — current | Yes | Yes |
| Inside explicit `SelectionContainer` — proposed | Yes | Yes |
| Inside `DisableSelection` | No | No |
| Custom `Box(Modifier.clickable{}) { Text(...) }` — proposed web | No | — |

---

## Coverage

All interactive components in Compose inherit from `AbstractClickableNode`:

| Component | Why covered |
|---|---|
| Material3 `Button` | Uses `Surface(onClick=...)` → `Modifier.clickable` |
| Material3 `Checkbox`, `Switch`, `RadioButton` | Use `Modifier.toggleable` → same base class |
| Material3 `Tab`, `DropdownMenuItem`, `NavigationBarItem` | Use `Modifier.clickable` |
| Material3 `Surface(onClick=...)` | Directly uses `Modifier.clickable` |
| Custom `Box(Modifier.clickable { })` | Directly uses `AbstractClickableNode` |
| Custom `Modifier.combinedClickable` | `CombinedClickableNode` extends `AbstractClickableNode` |

No changes required to any of the above.

---

## What Is NOT Covered

- **`Modifier.pointerInput` with custom gesture detection** — components that implement their
  own click handling without going through `Modifier.clickable` won't register. This is
  considered an acceptable gap: such patterns are rare and the developer is explicitly managing
  low-level input.

---

## Files Changed

| File | Type | Change |
|---|---|---|
| `InteractiveAreaRegistry.kt` | New | Interface, impl, and `CompositionLocal` |
| `Clickable.kt` | Modified | `AbstractClickableNode` adds `GlobalPositionAwareModifierNode` and registry registration |
| `SelectionManager.kt` | Modified | Guard in gesture start callback + filter in `selectAll()` |
| `SelectionContainer.kt` | Modified | Public overload clears registry via `CompositionLocalProvider` |
| `WebDefaultSelectionContainer.web.kt` | New | Root web wrapper; wired into platform entry point |
