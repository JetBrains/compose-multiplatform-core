# iOS Accessibility — Full Keyboard Access

Full Keyboard Access (FKA) is iOS's keyboard navigation system. Users navigate with Tab/Shift-Tab (move between focusable elements), arrow keys (within containers), and Enter/Space (activate). iOS draws a visible **focus ring** around the focused element. FKA uses the `UIFocusItem` protocol family, which is entirely separate from VoiceOver's `UIAccessibility` APIs.

---

## Separate Focus System

VoiceOver focus and FKA focus are fundamentally different iOS systems:
- **VoiceOver** uses `UIAccessibilityElementFocusedNotification` (global notification, no visible indicator in the app)
- **FKA** uses `UIFocusItem` protocol family (visible focus ring, `UIFocusEnvironment` chain)

The bridge implements both independently on the same `AccessibilityElement` class. VoiceOver focus state lives in `focusMode`, FKA focus state lives in `keyboardFocusedElementKey`.

---

## UIFocusItem Protocol

In Compose, `SemanticsProperties.Focused` indicates the node can receive input focus (text fields, buttons with focus modifiers). This maps directly to FKA's concept of "this element can be tab-selected." Not all VoiceOver-focusable elements are FKA-focusable — a static Text label is read by VoiceOver but cannot be Tab-selected.

When FKA moves to an element, the bridge also invokes `SemanticsActions.RequestFocus` to move Compose's internal focus. Without this, FKA focus and Compose focus would desync — the focus ring on one element, keyboard input going to another.

---

## Immediate Sync for FKA

When `keyboardFocusedElementKey != null`, the accessibility sync loop skips the 100ms VoiceOver delay and syncs immediately. The focus ring is a visible UI element that must track the focused element in real-time. With 100ms delay, the ring visually lags behind keyboard navigation — users see it "jump" instead of smoothly following focus.

---

## Focus Frame Coordinates

**iOS 18+:** Uses hierarchical coordinates relative to the parent container.

**iOS < 18:** Uses global screen coordinates.

iOS 18 introduced native support for nested focus containers with hierarchical coordinate spaces. Before iOS 18, the focus system expected all coordinates in screen space. Using hierarchical coordinates on iOS < 18 caused the focus ring to appear at wrong positions (offset by the parent container's origin). The version check ensures correct focus ring placement on all supported iOS versions.

---

## Scrollable Container Protocol

The `UIFocusItemScrollableContainerProtocol` implementation tells the FKA system the scroll bounds so it can automatically scroll when focus moves to an off-screen element.

> **HACK — Conditional protocol conformance:** `conformsToProtocol()` is overridden to dynamically report scroll protocol conformance only when the node can actually scroll. ObjC protocols cannot be conditionally conformed at the class level. Declaring unconditional conformance caused iOS to call scroll metric methods on every `AccessibilityElement` — buttons, text labels, everything. This was expensive and incorrect (returning zero values confused the focus system, causing erratic focus ring behavior).

> **HACK — Instant scroll:** FKA-driven scrolls disable animation (`motionDurationScale.scaleFactor = 0f`). UIKit's focus system reads scroll metrics continuously while focus is active. During an animated scroll, UIKit reads stale mid-animation values, causing the focus ring to flicker or appear at wrong positions.

---

## Focus on Initial Load

When the accessibility tree is first built and a node has `SemanticsProperties.Focused == true`, `setNeedsFocusUpdate()` is posted on the next main dispatch cycle. UIKit's focus system discovers focus targets through its normal update cycle — calling it synchronously during tree build doesn't work because UIKit hasn't yet associated the new elements with the focus environment. Deferring gives UIKit time to integrate. Added to fix CMP-9339 where dialogs/popups with FKA enabled wouldn't receive initial focus.

---

## Focus Cleanup on Disable

When accessibility is disabled or the mediator is disposed, a focus update is posted if a keyboard-focused element exists. Without this, the focus ring would remain on a disposed element (visual ghost ring) or UIKit would hold a stale reference, causing crashes on the next focus update.
