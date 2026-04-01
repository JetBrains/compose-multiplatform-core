# iOS Accessibility — Testing

This document covers how iOS accessibility is tested and how the bridge supports UI testing frameworks (XCTest, Compose UI Test).

---

## TestTag → accessibilityIdentifier

Compose's `Modifier.testTag("my_tag")` maps directly to `UIAccessibilityElement.accessibilityIdentifier`. This is the standard iOS mechanism for UI test frameworks (XCTest, Appium, etc.) to locate elements. `accessibilityIdentifier` is not announced by VoiceOver — it's purely a testing hook.

For link nodes inside annotated text, `linkTag()` extracts the tag from `LinkAnnotation.Clickable` and uses it as the identifier.

---

## XCTest / UI Automation Support

iOS 17+ introduced `automationElements` — a separate element list specifically for UI Automation frameworks (distinct from VoiceOver's `accessibilityElements`).

VoiceOver's `accessibilityElements` is a flattened tree optimized for screen reader navigation (see tree flattening in [01-general.md](Accessibility%20General.md)). UI Automation frameworks need access to the full semantic structure, including elements that are merged or hidden from VoiceOver. `automationElements` provides this unfiltered view, allowing XCTest to discover all semantic nodes for assertions — even those inside merged accessibility elements that VoiceOver treats as a single unit.

On iOS < 17, `automationElements` is not available. UI Automation falls back to `accessibilityElements`, which has the flattened VoiceOver structure — some semantic detail is lost.

---

## Instrumented Test Infrastructure

Test files are located at: `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/test/`

### `AccessibilityTestNode`

A data class representing an accessibility tree node for test assertions:

```kotlin
data class AccessibilityTestNode(
    var isAccessibilityElement: Boolean?,
    var identifier: String?,        // TestTag → accessibilityIdentifier
    var label: String?,             // accessibilityLabel
    var value: String?,             // accessibilityValue
    var frame: DpRect?,
    var children: List<AccessibilityTestNode>?,
    var traits: List<UIAccessibilityTraits>?,
    var element: NSObject?,
    var parent: AccessibilityTestNode?,
)
```

**Key test methods:**
- `getAccessibilityTree()` — builds a snapshot of the current accessibility tree
- `assertAccessibilityTree { ... }` — validates tree structure against expectations
- `findNodeWithTag(tag)` — finds element by `accessibilityIdentifier` (TestTag)
- `findNodeWithLabel(label)` — finds element by `accessibilityLabel`
- `findAllNodes(predicate)` — query all nodes matching a condition
- `normalized()` — flattens tree by removing non-accessibility-element containers, making assertions simpler when container structure is irrelevant
- `assertVisibleInContainer()` — validates element is within visible bounds

The tree builder supports both VoiceOver's `accessibilityElements` traversal and iOS 17+ `automationElements` traversal.

### `UIKitInstrumentedTest`

Base class for iOS instrumented tests. Provides a Compose view hosted in a UIKit window, accessibility tree access, and helper methods for finding and asserting accessibility elements.

### Example Test

`LayersAccessibilityTest` demonstrates testing accessibility for dialogs, popups, and layered content — verifying that accessibility focus moves correctly between layers.

---

## Debug Logging

An opt-in `AccessibilityDebugLogger` interface enables runtime debugging. Debug logging produces significant output on every accessibility sync (potentially 10+ times per second), so it is compiled-out by default to avoid performance impact in production. Uncomment the logger instance to enable it.

**What is logged:**
- `AccessibilityMediator` creation and sync performance timing
- Focus changes (which element, which mode)
- Layout/semantics invalidation events
- Element removal during cleanup
- Full tree traversal via `debugTraverse()` — prints tree structure with indentation, element properties (label, identifier, traits, frame), and containment chains

---

## Testing Traits

`AccessibilityTestNode` can assert on UIAccessibilityTraits. The test infrastructure maps 20+ traits including:
- Standard: `Button`, `Header`, `Image`, `Link`, `Selected`, `Adjustable`, `NotEnabled`, `StaticText`, `UpdatesFrequently`
- Custom CMP traits: `CMPAccessibilityTraitTextView`, `CMPAccessibilityTraitIsEditing`
- iOS 17+: `ToggleButton`, `SupportsZoom`

This allows tests to verify that the trait mapping (documented in [01-general.md](Accessibility%20General.md)) produces correct results for each Compose semantic configuration.

---

## Common Testing Patterns

**Verify element exists with correct label:**
```kotlin
findNodeWithTag("submit_button").also {
    assertEquals("Submit", it.label)
    assertTrue(it.traits?.contains(UIAccessibilityTraitButton) == true)
}
```

**Verify merged content:**
```kotlin
// A Row with mergeDescendants containing Icon + Text
findNodeWithTag("menu_item").also {
    assertEquals("Settings, Configure app preferences", it.label)
    assertTrue(it.isAccessibilityElement == true)
    assertTrue(it.children.isNullOrEmpty()) // Children merged, not separate
}
```

**Verify flattened structure:**
```kotlin
val tree = getAccessibilityTree().normalized()
// After normalization, non-focusable containers are removed
// Only actual accessibility elements remain in the flat list
```
