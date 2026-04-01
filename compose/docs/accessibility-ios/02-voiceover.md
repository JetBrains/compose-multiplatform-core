# iOS Accessibility — VoiceOver

VoiceOver is iOS's built-in screen reader. Users navigate by swiping left/right (move between elements), double-tapping (activate), and three-finger swipes (scroll). The accessibility bridge provides VoiceOver with the virtual element tree described in [01-general.md](Accessibility%20General.md).

This document covers VoiceOver-specific behavior: focus management, scrolling, notifications, and the "Speak Screen" workaround.

---

## Focus Management

### The Three Focus Modes

Focus is tracked by `focusMode: AccessibilityElementFocusMode`:

**`None`** — post `UIAccessibilityLayoutChangedNotification` without a specific element. Used for normal content updates where no specific focus target is needed. VoiceOver decides where to place focus (typically stays on the current element if it still exists).

**`KeepFocus(key)`** — after tree rebuild, restore focus to the element with `key`; search nearest child on failure. When VoiceOver is focused on element X and the UI updates (e.g., a counter value changes), the tree is rebuilt. Without `KeepFocus`, VoiceOver would lose its position and jump to the first element — extremely disorienting. `KeepFocus` finds X in the new tree by key match and posts a notification pointing to it. If X was removed (e.g., a list item deleted), it searches the nearest child — better than jumping to the beginning.

**`Focus(key)`** — one-time forced focus change; then transitions to `KeepFocus(key)`. Programmatic focus (e.g., dialog opening, popup appearing) needs to move VoiceOver to a specific element once. After that initial placement, normal `KeepFocus` behavior takes over. Without the transition to `KeepFocus`, repeated forced focus would fight with user navigation — every tree rebuild would force focus back to the dialog title even as the user swiped away from it.

### Focus Observation

`AccessibilityFocusedElementObserver` listens to `UIAccessibilityElementFocusedNotification` via `NSNotificationCenter`. VoiceOver focus changes don't call any method on the focused or defocused element — the only reliable way to detect which element VoiceOver selected is via this global notification. On each focus change, the observer:
1. Updates `focusMode` to `KeepFocus(newKey)` — so the next tree rebuild preserves this position
2. Schedules `focusedNodesScrollableParentsIds` update (10ms throttle)

### Scrollable Parent Tracking

`focusedNodesScrollableParentsIds` stores all scrollable ancestor IDs of the focused node. This set serves two purposes:

1. **Beyond-bounds focusability**: Off-screen elements are only focusable if they share a scrollable ancestor with the focused element (see Tree Flattening in general doc)
2. **Speak Screen re-read** (see hack below)

The 10ms throttle prevents recomputation on every rapid focus change (e.g., fast swiping).

> **HACK — Speak Screen re-read:** When `focusedNodesScrollableParentsIds` changes, the bridge posts `UIAccessibilityPageScrolledNotification`. When "Speak Screen" is active (two-finger swipe from top), iOS captures a snapshot of the element list at the moment the command starts and reads through that snapshot sequentially. As VoiceOver reads and scrolls, new elements come on-screen but iOS uses its frozen snapshot. `UIAccessibilityPageScrolledNotification` forces iOS to refresh its snapshot. This is posted whenever the set of scrollable parents changes, which naturally happens as the focus point moves through scrollable content. Without this hack, "Speak Screen" skips all content that wasn't visible when the command started. This is undocumented iOS behavior — the notification was designed for scroll position announcements, not snapshot refreshes.

---

## Scrolling

### VoiceOver Scroll Gestures (`accessibilityScroll(direction)`)

When the user performs a three-finger swipe, iOS calls `accessibilityScroll(direction)` on the focused element. The bridge converts this to Compose scroll actions via `SemanticsNode.scrollIfPossible()`.

**Direction normalization** handles two independent inversions:

**Reverse scrolling:** Some Compose scroll containers have `reverseScrolling = true` (e.g., bottom-to-top chat lists). A VoiceOver "scroll down" gesture should scroll toward newer messages, which in a reversed container means scrolling in the opposite physical direction. The `isReverse` flag XORs with the direction.

**RTL layout:** In right-to-left layouts, a VoiceOver "scroll right" gesture should move to the next page of content, which is physically to the left. The `isRTL` flag XORs with horizontal directions.

If a container is both reversed AND RTL, the two inversions cancel out (XOR of two `true` values is `false`), restoring the original direction. This is mathematically correct for all four combinations of (reversed, RTL).

Page actions (`PageUp/Down/Left/Right`) are tried first, with `ScrollBy` as fallback. Page actions provide semantically meaningful scroll amounts (one page), while `ScrollBy` uses the viewport size as delta, which is a coarser approximation. If neither action exists on the current node, the scroll is delegated to the parent — enabling nested scroll containers.

### Scroll Announcements

After a scroll completes (350ms delay), the bridge posts `UIAccessibilityPageScrolledNotification` with a localized string: "First Page", "Last Page", "Next Page", or "Previous Page". The 350ms delay approximates a standard Compose scroll animation duration. The announcement must come after the animation completes — announcing while content is still moving would confuse users who rely on the announcement to understand their new position.

### Scroll to Focus

When VoiceOver focuses an element, `scrollToAccessibilityElement()` finds the nearest scrollable ancestor and scrolls to center the element in the viewport:
1. **Predictable**: users always know where the focused element will appear
2. **Safe area**: centering keeps content away from notches/home indicators — "just visible" might place the element under the notch (CMP-7291 added safe area awareness)
3. **Multi-level scrolling**: unused scroll delta is passed to the parent scrollable, enabling nested scroll containers to cooperate

---

## Hit Testing

Before CMP-9720, there was no hit test implementation. iOS chose the accessibility element by proximity, which could focus elements hidden behind overlapping views. The hit test ensures VoiceOver focus follows the visual hierarchy — the topmost visible element at the touch point wins.

For interop views (`InteropWrappingView`), the hit test is delegated to the native view's own `accessibilityHitTest()` — native views handle their own hit testing.

---

## Press Handling

`AccessibilityMediator` previously intercepted `UIPress` events for keyboard navigation. (CMP-9444) When VoiceOver was active, it sent both a UIPress and an accessibility activation for the same key event, causing duplicate events and crashes during popup dismissal. All press handling was removed from the accessibility layer. FKA uses `UIFocusItem.didBecomeFocused()` instead; VoiceOver uses `accessibilityActivate()`.
