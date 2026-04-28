# SelectionContainer — Architecture & Internals

## Table of Contents

1. [Overview](#overview)
2. [Key Components](#key-components)
3. [Composition Local Chain](#composition-local-chain)
4. [Selectable Lifecycle](#selectable-lifecycle)
5. [Data Model](#data-model)
6. [Gesture Flow](#gesture-flow)
7. [Multi-Widget Selection](#multi-widget-selection)
8. [Handle Dragging](#handle-dragging)
9. [Select All](#select-all)
10. [Copy to Clipboard](#copy-to-clipboard)
11. [DisableSelection](#disableselection)
12. [Selection Adjustment](#selection-adjustment)
13. [Platform Specifics](#platform-specifics)

---

## Overview

`SelectionContainer` enables text selection across one or more `Text` composables in a subtree.
By default, nothing in Compose is selectable — text only becomes selectable when wrapped in a
`SelectionContainer`.

```
┌─────────────────────────────────────────────────────┐
│ SelectionContainer                                  │
│                                                     │
│   "Hello world"          ← selectable               │
│                                                     │
│   ┌──────────────────┐                              │
│   │ DisableSelection │                              │
│   │   "Submit"       │  ← NOT selectable            │
│   └──────────────────┘                              │
│                                                     │
│   "Another paragraph"    ← selectable               │
└─────────────────────────────────────────────────────┘
```

A single `SelectionContainer` manages selection across **all** registered text composables in its
subtree, including multi-widget selection that spans across separate `Text` nodes.

---

## Key Components

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                          SelectionContainer (Composable)                        │
│                                                                                │
│  ┌──────────────────────────┐    ┌─────────────────────────────────────────┐  │
│  │  SelectionRegistrarImpl  │───▶│  SelectionManager                       │  │
│  │                          │    │                                         │  │
│  │  - selectables: List     │    │  - selection: Selection?                │  │
│  │  - subselections: Map    │    │  - containerCoords: LayoutCoordinates   │  │
│  │  - callbacks (→ Manager) │    │  - startHandlePosition: Offset?         │  │
│  └──────────────────────────┘    │  - endHandlePosition: Offset?           │  │
│             ▲                    │  - modifier (gestures, focus, copy)     │  │
│             │ subscribe          └─────────────────────────────────────────┘  │
│             │                                                                  │
│  ┌──────────┴──────────────────────────────────────────────────────────────┐  │
│  │  LocalSelectionRegistrar (CompositionLocal)                              │  │
│  │  default: null                                                           │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│             ▲                                                                  │
│             │ provided to subtree                                              │
│   ┌─────────┴──────────────┐   ┌────────────────────────┐                    │
│   │  SelectionController   │   │  SelectionController   │  ...               │
│   │  (inside Text #1)      │   │  (inside Text #2)      │                    │
│   │                        │   │                        │                    │
│   │  - selectableId: Long  │   │  - selectableId: Long  │                    │
│   │  - selectable          │   │  - selectable          │                    │
│   └────────────────────────┘   └────────────────────────┘                    │
└────────────────────────────────────────────────────────────────────────────────┘
```

| Component | Role |
|---|---|
| `SelectionContainer` | Public composable. Owns `Selection?` state and provides the registrar to the subtree. |
| `SelectionRegistrarImpl` | Maintains the ordered list of all subscribed `Selectable`s and routes callbacks to `SelectionManager`. |
| `SelectionManager` | Stateful object that owns gesture handling, handle positions, toolbar, and calls `onSelectionChange`. |
| `SelectionController` | A `RememberObserver` created inside each `BasicText`. Subscribes/unsubscribes with the registrar and draws the selection highlight. |
| `Selectable` | Interface implemented by `MultiWidgetSelectionDelegate`. Provides per-text selection queries (offset, bounds, text content). |
| `LocalSelectionRegistrar` | `CompositionLocal<SelectionRegistrar?>`. `null` by default — text is only selectable when a non-null value is present. |

---

## Composition Local Chain

`LocalSelectionRegistrar` is the spine of the system. Its value determines whether any `Text`
composable in the subtree participates in selection.

```
null (default)
    │
    │   SelectionContainer {
    ▼       CompositionLocalProvider(LocalSelectionRegistrar provides registrarImpl)
registrarImpl ──────────────────────────────────────────────────────────────────▶
    │                                                                            │
    │   Text("hello")         Text("world")         Button { Text("click") }    │
    │   reads non-null        reads non-null         reads non-null              │
    │   → subscribes          → subscribes           → subscribes                │
    │                                                                            │
    │   DisableSelection {                                                       │
    │       CompositionLocalProvider(LocalSelectionRegistrar provides null)      │
    │       ──────────────────────────────────────────────────────────▶         │
    │           Text("no select")                                                │
    │           reads null → does NOT subscribe                                  │
    │       }                                                                    │
    └────────────────────────────────────────────────────────────────────────────┘
```

---

## Selectable Lifecycle

Each `BasicText` creates a `SelectionController` when a non-null `LocalSelectionRegistrar` is
present. The controller acts as a `RememberObserver` — its subscribe/unsubscribe is tied to
composition.

```
┌────────────────────────────────────────────────────────────────────┐
│  COMPOSITION                                                        │
│                                                                     │
│  BasicText enters composition                                       │
│      │                                                              │
│      ├─ localSelectionRegistrar = LocalSelectionRegistrar.current  │
│      │                                                              │
│      ├─ [null?] ──▶ no SelectionController created                 │
│      │                                                              │
│      └─ [non-null] ──▶ selectableId = registrar.nextSelectableId() │
│                        controller = remember { SelectionController }│
│                                                                     │
│  SelectionController.onRemembered()                                 │
│      └─ selectable = registrar.subscribe(MultiWidgetSelectionDelegate)
│                                                                     │
│  SelectionController.onForgotten() / onAbandoned()                  │
│      └─ registrar.unsubscribe(selectable)                           │
└────────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────┐
│  LAYOUT                                                              │
│                                                                     │
│  Text is laid out                                                   │
│      └─ SelectionController.updateGlobalPosition(coords)            │
│          └─ registrar.notifyPositionChange(selectableId)            │
│              └─ SelectionManager.updateHandleOffsets()              │
│                  └─ re-sorts selectables by geometric position      │
└────────────────────────────────────────────────────────────────────┘
```

### Sorting

`SelectionRegistrarImpl.sort()` orders selectables geometrically before any selection computation:
- Primary sort: **top-to-bottom** (y-coordinate)
- Secondary sort: **left-to-right** (x-coordinate)
- `inARow` heuristic: if two text blocks overlap vertically by ≥ 50%, they are sorted
  left-to-right regardless of y-position (handles inline spans in a paragraph).

---

## Data Model

```
Selection
├── start: AnchorInfo
│   ├── selectableId: Long   ← which Text node
│   ├── offset: Int          ← character index within that text
│   └── direction: ResolvedTextDirection
│
├── end: AnchorInfo
│   ├── selectableId: Long
│   ├── offset: Int
│   └── direction: ResolvedTextDirection
│
└── handlesCrossed: Boolean  ← true when end handle dragged before start

SelectionRegistrarImpl.subselections: LongObjectMap<Selection>
    ← per-selectable sub-range derived from the global Selection
    ← backed by MutableState, so each Text recomposes only when its sub-range changes
```

A single `Selection` spans the whole container. Each `Text` renders only the portion that
overlaps with its own content, stored in `subselections[selectableId]`.

---

## Gesture Flow

### Touch (long-press → drag)

```
User long-presses on Text
         │
         ▼
SelectionController.pointerInput
    awaitSelectionGestures()
        longPressDragObserver.onLongPress(offset)
         │
         ▼
registrar.notifySelectionUpdateStart(
    layoutCoordinates,
    startPosition = offset,
    adjustment = Word,          ← initial word snap
    isInTouchMode = true
)
         │
         ▼
SelectionManager.onSelectionUpdateStartCallback
    position = convertToContainerCoordinates(layoutCoords, rawOffset)
    startSelection(position, adjustment = Word)
         │
         ▼
SelectionManager.updateSelection(...)
    layout = getSelectionLayout(position, prevPosition, isStartHandle)
    selection = adjustment.adjust(layout)   ← snap to word
    selectionChanged(selection)
         │
         ├─▶ registrarImpl.subselections updated (MutableState)
         │       └─▶ each affected Text recomposes, draws highlight
         │
         └─▶ onSelectionChange(selection)
                 └─▶ SelectionContainer updates its selection state
                         └─▶ selection handles rendered
```

### Mouse (press → drag)

```
User presses mouse on Text
         │
         ▼
SelectionController.pointerInput
    awaitSelectionGestures()
        mouseSelectionObserver.onStart(offset)
         │
         ├─ single click → adjustment = Character
         ├─ double click → adjustment = Word
         └─ triple click → adjustment = Paragraph
         │
         ▼
registrar.notifySelectionUpdateStart(adjustment = <above>)
    └─▶ same path as touch from here
```

### Gesture lifecycle callbacks

```
notifySelectionUpdateStart  →  dragging begins, previousSelectionLayout = null
notifySelectionUpdate       →  repeated during drag, returns Boolean (consumed?)
notifySelectionUpdateEnd    →  drag released, shows text toolbar
```

---

## Multi-Widget Selection

When a selection spans multiple `Text` composables, `SelectionManager` computes a sub-selection
for each registered `Selectable`.

```
SelectionContainer
├── Text A: "Hello world"        (selectableId = 1)
├── Text B: "Foo bar"            (selectableId = 2)
└── Text C: "Baz qux"            (selectableId = 3)

User selects from "world" to "bar":

Selection {
    start = AnchorInfo(selectableId=1, offset=6)   ← "world" in A
    end   = AnchorInfo(selectableId=2, offset=3)   ← "bar" in B
}

subselections = {
    1 → Selection(start=offset6, end=endOfA)   ← "world"
    2 → Selection(start=0,       end=offset3)  ← "Foo bar"
    3 → (no entry)                             ← not selected
}
```

`SelectionManager.updateSelection()` builds a `SelectionLayout` from all sorted selectables,
then calls `SelectionAdjustment.adjust()` which returns the final `Selection` along with
per-selectable sub-ranges.

---

## Handle Dragging

When the user drags a selection handle, the `SelectionContainer` renders `SelectionHandle`
composables and attaches `pointerInput` observers to them.

```
User drags start handle
         │
         ▼
SelectionManager.handleDragObserver(isStartHandle = true)
    .onStart(startPoint)
         │
         ▼
registrar.notifySelectionUpdateStart(
    layoutCoordinates = containerCoords,
    startPosition = startPoint,
    adjustment = CharacterWithWordAccelerate
)
         │  (same update path as gestures above)
         ▼
On release:
    notifySelectionUpdateEnd()
        └─▶ toolbar shown, drag state cleared
```

Handles are only shown in touch mode (`manager.isInTouchMode == true`). On desktop/web,
selection uses mouse drag and has no visible handles.

```
isInTouchMode = true:
  ┌─────────────────────────────────┐
  │ |Hello world|                  │
  │  ▲         ▲                   │
  │  start     end                 │  ← handles rendered
  └─────────────────────────────────┘

isInTouchMode = false (desktop/web):
  ┌─────────────────────────────────┐
  │ |Hello world|                  │
  │                                │  ← no handles, mouse cursor sufficient
  └─────────────────────────────────┘
```

---

## Select All

`SelectionManager.selectAll()` is triggered by Ctrl/Cmd+A or the context menu.

```
selectAll()
    │
    ├─▶ sort selectables by position
    │
    └─▶ for each selectable:
            subSelection = selectable.getSelectAllSelection()
            if non-null → add to subselections map
         │
         ▼
    Merge all sub-selections into one global Selection
    (start = first selectable's start, end = last selectable's end)
         │
         ▼
    onSelectionChange(mergedSelection)
```

`notifySelectionUpdateSelectAll(selectableId)` is also called from `SelectionController` when
the gesture system detects a "select all in this widget" event (e.g. triple-click in a single
text), not to be confused with the container-level Ctrl+A.

---

## Copy to Clipboard

Copy happens via two paths:

```
Path 1 — Keyboard shortcut (Ctrl/Cmd+C):
    SelectionManager.modifier
        └─ onKeyEvent { if isCopyKeyEvent → manager.copy() }

Path 2 — Context menu / toolbar "Copy" button:
    SelectionManager.addSelectionContainerTextContextMenuComponents()
        └─ Copy item → manager.copy()

manager.copy():
    selectedText = getSelectedText()
        └─ iterate subselections, call selectable.getText() for each range
        └─ concatenate into AnnotatedString
    onCopyHandler(selectedText)
        └─ clipboard.setClipEntry(text.toClipEntry())
```

`shouldIgnoreCopyKeyEvent` is wired to a `rememberClipboardEventsHandler` to handle the browser
clipboard permission model on web — the browser may intercept the copy event before Compose does.

---

## DisableSelection

`DisableSelection` is a composable that overrides `LocalSelectionRegistrar` to `null` for its
entire subtree. Any `Text` inside sees a null registrar and skips creating a `SelectionController`.

```kotlin
@Composable
fun DisableSelection(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSelectionRegistrar provides null, content = content)
}
```

```
SelectionContainer          LocalSelectionRegistrar = registrarImpl
    │
    ├── Text("selectable")  reads registrarImpl → subscribes ✓
    │
    └── DisableSelection    LocalSelectionRegistrar = null
            │
            └── Text("not selectable")  reads null → no subscribe ✗
            └── Button { Text("label") } reads null → no subscribe ✗
```

This is also how nested `SelectionContainer`s work: the inner one overrides the outer registrar
with its own, so selectables inside belong to the inner container only.

---

## Selection Adjustment

`SelectionAdjustment` is applied after every raw position update to snap the selection boundary
to a semantic unit.

| Value | When used | Behavior |
|---|---|---|
| `None` | Raw handle drag computations | No snapping |
| `Character` | Mouse single-click drag, handle drag (shrink) | Minimum one character selected |
| `Word` | Touch long-press initial selection, mouse double-click | Snaps to word boundaries |
| `Paragraph` | Mouse triple-click | Snaps to paragraph boundaries |
| `CharacterWithWordAccelerate` | Handle drag after initial selection | Expands by word, shrinks by character; jumps to next line's word boundary when crossing lines |

```
Raw offset lands mid-word:

  "Hello world"
         ^── raw offset at 'o' in 'world'

  After Word adjustment:
  "Hello |world|"
          ▲────▲  ← snapped to full word
```

`CharacterWithWordAccelerate` provides the platform-native "smart" handle behavior:
- Dragging outward: expands by full words
- Dragging inward: shrinks by individual characters
- Crossing a line boundary: jumps to the nearest word on that line

---

## Platform Specifics

### Touch vs. Mouse/Keyboard

The selection system distinguishes two input modes via `isInTouchMode`:

- **Touch**: long-press to start, drag handles to adjust, floating toolbar shown
- **Mouse/Keyboard**: click-drag to select, no handles, text toolbar shown in-place

### Web

| File | What it customizes |
|---|---|
| `SelectionGestures.web.kt` | `FirstLongPressSelectionAdjustment = Word` |
| `SelectionManager.web.kt` | `skipCopyKeyEvent = true` (browser clipboard API), no magnifier |
| `SelectionHandles.web.kt` | Delegates to `SkikoSelectionHandle` |
| `SelectionController.web.kt` | Uses `makeSkikoSelectionModifier()` for canvas-based selection rendering |

On web, `skipCopyKeyEvent` returns `true` because the browser intercepts Ctrl+C before the
canvas. Compose registers a `rememberClipboardEventsHandler` to receive the browser's copy event
and respond with the selected text.

### Desktop

| File | What it customizes |
|---|---|
| `SelectionGestures.desktop.kt` | `FirstLongPressSelectionAdjustment = Word` |
| `SelectionManager.desktop.kt` | `isCopyKeyEvent`: Ctrl+C on Linux/Windows, Cmd+C on macOS |
| `SelectionController.desktop.kt` | Uses `makeSkikoSelectionModifier()` |

---

## Complete Call Graph (summary)

```
                    ┌──────────────────────────────────────────────────────────┐
                    │ User gesture / keyboard / context menu                   │
                    └───────────────────────────┬──────────────────────────────┘
                                                │
                    ┌───────────────────────────▼──────────────────────────────┐
                    │ SelectionController (Modifier.Node on Text)              │
                    │   pointerInput → awaitSelectionGestures()                │
                    └───────────────────────────┬──────────────────────────────┘
                                                │ notifySelectionUpdateStart/Update/End
                    ┌───────────────────────────▼──────────────────────────────┐
                    │ SelectionRegistrarImpl                                   │
                    │   invokes stored callback lambdas                        │
                    └───────────────────────────┬──────────────────────────────┘
                                                │ onSelectionUpdateStartCallback
                    ┌───────────────────────────▼──────────────────────────────┐
                    │ SelectionManager                                         │
                    │   startSelection() / updateSelection()                   │
                    │   → getSelectionLayout()                                 │
                    │   → SelectionAdjustment.adjust()                         │
                    │   → selectionChanged()                                   │
                    └──────┬──────────────────────────┬───────────────────────┘
                           │                          │
           ┌───────────────▼──────┐      ┌────────────▼──────────────────────┐
           │ subselections updated │      │ onSelectionChange(selection)      │
           │ (MutableState)        │      │  └─ SelectionContainer state      │
           │  └─ Text recomposes   │      │      └─ handles + toolbar shown   │
           │     draws highlight   │      └───────────────────────────────────┘
           └──────────────────────┘
```
