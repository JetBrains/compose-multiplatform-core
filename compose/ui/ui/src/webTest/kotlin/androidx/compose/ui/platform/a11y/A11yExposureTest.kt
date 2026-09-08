/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package androidx.compose.ui.platform.a11y

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.WebApplicationScope
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.events.keyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/** Regression coverage for Web A11Y exposure independently from DOM geometry. */
class A11yExposureTest : OnCanvasTests {
    private fun element(tag: String): HTMLElement =
        assertNotNull(
            getShadowRoot().getElementById(tag) as? HTMLElement,
            "No A11Y element for '$tag'",
        )

    private suspend fun WebApplicationScope.awaitCondition(
        message: String,
        condition: () -> Boolean,
    ) {
        val start = currentTimeMillis()
        while (!condition()) {
            assertTrue(currentTimeMillis() - start < 5_000, "Timed out waiting for: $message")
            awaitAnimationFrame()
        }
    }

    @Test
    fun fullyClippedNonScrollNodeIsInertButRetainsMeasuredSize() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(Modifier.offset(200.dp, 0.dp).size(40.dp).testTag("panel"))
            }
        }
        awaitA11YChanges()

        val panel = element("panel")
        assertTrue(panel.hasAttribute("inert"))
        assertTrue(panel.getBoundingClientRect().width > 0.0)
        assertTrue(panel.getBoundingClientRect().height > 0.0)
    }

    @Test
    fun clippingTransitionTogglesInertnessWithoutReplacingElement() = runApplicationTest {
        var hidden by mutableStateOf(true)
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(
                    Modifier.offset(if (hidden) 200.dp else 0.dp, 0.dp).size(40.dp).testTag("panel")
                )
            }
        }
        awaitA11YChanges()
        val panel = element("panel")
        assertTrue(panel.hasAttribute("inert"))

        hidden = false
        awaitA11YChanges()
        assertSame(panel, element("panel"))
        assertFalse(panel.hasAttribute("inert"))

        hidden = true
        awaitA11YChanges()
        assertSame(panel, element("panel"))
        assertTrue(panel.hasAttribute("inert"))
    }

    @Test
    fun descendantOfVisibleScrollContainerRemainsExposed() = runApplicationTest {
        val scrollState = ScrollState(0)
        createComposeWindow {
            Column(Modifier.size(100.dp).verticalScroll(scrollState).testTag("scroller")) {
                Box(Modifier.size(100.dp))
                Box(Modifier.size(40.dp).testTag("item"))
            }
        }
        awaitA11YChanges()

        val scroller = element("scroller")
        val item = element("item")
        assertFalse(scroller.hasAttribute("inert"))
        assertFalse(item.hasAttribute("inert"))
        assertTrue(item.getBoundingClientRect().height > 0.0)

        scrollIntoView(item)
        awaitCondition("Browser scrolling must reach the exposed offscreen item") {
            scrollState.value > 0
        }
        assertSame(item, element("item"))
    }

    @Test
    fun hiddenScrollableSubtreeCannotReactivateDescendants() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Column(
                    Modifier.offset(200.dp, 0.dp)
                        .size(100.dp)
                        .verticalScroll(ScrollState(0))
                        .testTag("hiddenScroller")
                ) {
                    Box(Modifier.size(40.dp).testTag("hiddenItem"))
                }
            }
        }
        awaitA11YChanges()

        assertTrue(element("hiddenScroller").hasAttribute("inert"))
        assertTrue(element("hiddenItem").hasAttribute("inert"))
    }

    @Test
    fun intermediateNonScrollClipMustHideDescendant() = runApplicationTest {
        val scrollState = ScrollState(0)
        createComposeWindow {
            Column(Modifier.size(100.dp).verticalScroll(scrollState).testTag("scroller")) {
                // Deliberately omit semantics on the clip: it must still block reachability.
                Box(Modifier.size(20.dp).clipToBounds()) {
                    Box(Modifier.offset(40.dp, 0.dp).size(10.dp).testTag("clipped"))
                }
                Spacer(Modifier.height(200.dp))
            }
        }
        awaitA11YChanges()

        assertNull(element("scroller").closest("[inert]"))
        // The child lies inside the scroll viewport, but scrolling cannot reveal it through
        // the intermediate clip, which moves together with the child.
        assertNotNull(
            element("clipped").closest("[inert]"),
            "Intermediate non-scroll clipping must make the node inert",
        )
    }

    @Test
    fun partialCrossAxisOverlapMustRemainScrollReachable() = runApplicationTest {
        val scrollState = ScrollState(0)
        createComposeWindow {
            Column(Modifier.size(100.dp).verticalScroll(scrollState).testTag("scroller")) {
                Spacer(Modifier.height(120.dp))
                Box(Modifier.offset((-10).dp, 0.dp).size(40.dp).testTag("reachable"))
            }
        }
        awaitA11YChanges()

        val item = element("reachable")
        val viewportBounds = element("scroller").getBoundingClientRect()
        val itemBounds = item.getBoundingClientRect()
        assertTrue(
            itemBounds.top >= viewportBounds.bottom,
            "The item must start below the viewport",
        )
        assertTrue(itemBounds.left < viewportBounds.left, "The item must overflow horizontally")
        assertTrue(
            itemBounds.right > viewportBounds.left,
            "The item must still overlap horizontally",
        )
        assertNull(
            item.closest("[inert]"),
            "Vertical scrolling can reveal the horizontally overlapping part of the item",
        )
    }

    @Test
    fun fullyClippedLinkInPartiallyVisibleTextMustBeHidden() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds().testTag("clip")) {
                BasicText(
                    text =
                        buildAnnotatedString {
                            repeat(8) { append("line\n") }
                            withLink(
                                LinkAnnotation.Clickable(
                                    "link",
                                    linkInteractionListener = LinkInteractionListener {},
                                )
                            ) {
                                append("link")
                            }
                        },
                    modifier = Modifier.width(100.dp).requiredHeight(200.dp).testTag("text"),
                    style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
                )
            }
        }
        awaitA11YChanges()

        val text = element("text")
        val link = assertNotNull(text.querySelector("[role=link]") as? HTMLElement)
        assertNull(text.closest("[inert]"), "The partially visible text must remain exposed")
        assertTrue(
            link.getBoundingClientRect().top >= element("clip").getBoundingClientRect().bottom,
            "The link must lie entirely below the non-scroll clipping boundary",
        )
        assertNotNull(
            link.closest("[inert]"),
            "A fully clipped link must be hidden separately from its visible text parent",
        )
    }

    @Test
    fun inlineContentMustInheritTextScrollContext() = runApplicationTest {
        val scrollState = ScrollState(0)
        createComposeWindow {
            Column(Modifier.size(100.dp).verticalScroll(scrollState)) {
                Spacer(Modifier.height(120.dp))
                BasicText(
                    text = buildAnnotatedString { appendInlineContent("inline") },
                    modifier = Modifier.width(100.dp).testTag("text"),
                    inlineContent =
                        mapOf(
                            "inline" to
                                InlineTextContent(
                                    Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.Top)
                                ) {
                                    Box(Modifier.size(20.dp).testTag("inline").clickable {})
                                }
                        ),
                )
            }
        }
        awaitA11YChanges()

        assertNull(
            element("text").closest("[inert]"),
            "The offscreen text must be scroll-reachable",
        )
        assertNull(
            element("inline").closest("[inert]"),
            "Inline content must inherit the reachable scroll context from its text parent",
        )
    }

    @Test
    fun partiallyClippedAndZeroSizedNodesRemainExposed() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(Modifier.offset(80.dp, 0.dp).size(40.dp).testTag("partial"))
                Box(Modifier.size(0.dp).testTag("zero"))
                Box(Modifier.offset(200.dp, 0.dp).size(40.dp).testTag("hidden")) {
                    Box(Modifier.size(0.dp).testTag("hiddenZero"))
                }
            }
        }
        awaitA11YChanges()

        assertNull(element("partial").closest("[inert]"))
        assertEquals("40px", element("partial").style.width)
        assertEquals("40px", element("partial").style.height)
        assertNull(element("zero").closest("[inert]"))
        assertNotNull(element("hiddenZero").closest("[inert]"))
    }

    @Test
    fun offscreenNestedScrollContainerAndItsContentRemainReachable() = runApplicationTest {
        val outerState = ScrollState(0)
        val innerState = ScrollState(0)
        createComposeWindow {
            Column(Modifier.size(100.dp).verticalScroll(outerState).testTag("outer")) {
                Spacer(Modifier.height(120.dp))
                Row(Modifier.size(100.dp).horizontalScroll(innerState).testTag("inner")) {
                    Spacer(Modifier.width(120.dp))
                    Box(Modifier.size(40.dp).testTag("nestedItem"))
                }
            }
        }
        awaitA11YChanges()

        val item = element("nestedItem")
        assertNull(element("inner").closest("[inert]"))
        assertNull(item.closest("[inert]"))
        scrollIntoView(item)
        awaitCondition("Both scroll containers must reveal the nested item") {
            outerState.value > 0 && innerState.value > 0
        }
        assertSame(item, element("nestedItem"))
    }

    @Test
    fun retainedLazyItemsRemainDiscoverableInBothDirections() = runApplicationTest {
        val state = LazyListState()
        createComposeWindow {
            LazyColumn(Modifier.size(100.dp), state = state) {
                items(100) { index -> Box(Modifier.size(60.dp).testTag("item$index")) }
            }
        }
        awaitA11YChanges()
        assertNull(element("item2").closest("[inert]"))

        state.scrollToItem(10)
        awaitA11YChanges()
        val precedingItem = element("item9")
        assertNull(precedingItem.closest("[inert]"))
        assertNull(element("item12").closest("[inert]"))
        scrollIntoView(precedingItem)
        awaitCondition("AT must be able to navigate to a retained item above the viewport") {
            state.firstVisibleItemIndex < 10
        }
    }

    @Test
    fun reverseScrollingRetainsOffscreenItemsInBothDirections() = runApplicationTest {
        val state = ScrollState(0)
        createComposeWindow {
            Column(
                Modifier.size(100.dp)
                    .verticalScroll(state, reverseScrolling = true)
                    .testTag("scroller")
            ) {
                repeat(5) { index -> Box(Modifier.size(40.dp).testTag("item$index")) }
            }
        }
        awaitA11YChanges()
        val firstItem = element("item0")
        assertNull(firstItem.closest("[inert]"))
        scrollIntoView(firstItem)
        awaitCondition("Reverse scrolling must reach the first item") { state.value > 0 }

        state.scrollTo(state.maxValue)
        awaitCondition("The reversed DOM offset must reach the start") {
            element("scroller").scrollTop == 0.0
        }
        assertNull(element("item4").closest("[inert]"))
    }

    @Test
    fun unsupportedScrollAxisDoesNotExposeClippedNodes() = runApplicationTest {
        val verticalState = ScrollState(0)
        val horizontalState = ScrollState(0)
        createComposeWindow {
            Column {
                Column(Modifier.size(100.dp).verticalScroll(verticalState)) {
                    Box(Modifier.offset(200.dp, 0.dp).size(40.dp).testTag("horizontalClip"))
                    Spacer(Modifier.height(200.dp))
                }
                Row(Modifier.size(100.dp).horizontalScroll(horizontalState)) {
                    Box(Modifier.offset(0.dp, 200.dp).size(40.dp).testTag("verticalClip"))
                    Spacer(Modifier.width(200.dp))
                }
            }
        }
        awaitA11YChanges()

        assertNotNull(element("horizontalClip").closest("[inert]"))
        assertNotNull(element("verticalClip").closest("[inert]"))
    }

    @Test
    fun contentClipOnScrollerLayoutNodeStillHidesDescendants() = runApplicationTest {
        val state = ScrollState(0)
        createComposeWindow {
            Column(
                Modifier.size(100.dp)
                    .verticalScroll(state)
                    .height(200.dp)
                    .clipToBounds()
                    .testTag("scroller")
            ) {
                Spacer(Modifier.height(120.dp))
                Box(Modifier.offset(0.dp, 200.dp).size(20.dp).testTag("clipped"))
            }
        }
        awaitA11YChanges()

        assertTrue(state.maxValue > 0)
        assertNull(element("scroller").closest("[inert]"))
        assertNotNull(
            element("clipped").closest("[inert]"),
            "Only the viewport's scroll clip may be relaxed, not its content's clip",
        )
    }

    @Test
    fun partiallyClippedScrollerRetainsNonScrollableAxisRestriction() = runApplicationTest {
        val state = ScrollState(0)
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Column(
                    Modifier.offset(50.dp, 0.dp)
                        .size(100.dp)
                        .verticalScroll(state)
                        .testTag("scroller")
                ) {
                    Box(Modifier.offset(60.dp, 0.dp).size(20.dp).testTag("clipped"))
                    Spacer(Modifier.height(200.dp))
                }
            }
        }
        awaitA11YChanges()

        assertNull(element("scroller").closest("[inert]"))
        assertNotNull(
            element("clipped").closest("[inert]"),
            "Vertical scrolling cannot reveal content beyond the outer horizontal clip",
        )
    }

    @Test
    fun hidingFocusedNodeClearsFocusWithoutRestoringItOnReexposure() = runApplicationTest {
        var hidden by mutableStateOf(false)
        var clicks = 0
        var progressChanges = 0
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(
                    Modifier.offset(if (hidden) 200.dp else 0.dp, 0.dp)
                        .size(40.dp)
                        .testTag("slider")
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(0.5f, 0f..1f)
                            setProgress {
                                progressChanges++
                                true
                            }
                            onClick {
                                clicks++
                                true
                            }
                        }
                )
            }
        }
        awaitA11YChanges()

        val slider = element("slider")
        slider.focus()
        assertSame(slider, getShadowRoot().activeElement)
        hidden = true
        awaitA11YChanges()
        awaitAnimationFrame()
        assertNotNull(slider.closest("[inert]"))
        assertFalse(slider === getShadowRoot().activeElement)
        dispatchClick(slider)
        slider.dispatchEvent(keyEvent("ArrowRight", code = "ArrowRight"))
        assertEquals(0, clicks)
        assertEquals(0, progressChanges)

        hidden = false
        awaitA11YChanges()
        assertSame(slider, element("slider"))
        assertNull(slider.closest("[inert]"))
        assertFalse(
            slider === getShadowRoot().activeElement,
            "Re-exposure must not restore stale focus",
        )
        dispatchClick(slider)
        slider.dispatchEvent(keyEvent("ArrowRight", code = "ArrowRight"))
        assertEquals(1, clicks)
        assertEquals(1, progressChanges)
    }

    @Test
    fun modalInertnessBlocksActionsAndPreservesHiddenNodesAfterDismissal() = runApplicationTest {
        var showDialog by mutableStateOf(true)
        var clicks = 0
        var progressChanges = 0
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(
                    Modifier.size(40.dp).testTag("slider").semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(0.5f, 0f..1f)
                        setProgress {
                            progressChanges++
                            true
                        }
                        onClick {
                            clicks++
                            true
                        }
                    }
                )
                Box(Modifier.offset(200.dp, 0.dp).size(40.dp).testTag("hidden"))
            }
            if (showDialog) Dialog(onDismissRequest = {}) { Box(Modifier.size(40.dp)) }
        }
        awaitA11YChanges()

        val slider = element("slider")
        assertFalse(slider.hasAttribute("inert"), "Inertness must come from the modal owner root")
        assertNotNull(slider.closest("[inert]"))
        dispatchClick(slider)
        slider.dispatchEvent(keyEvent("ArrowRight", code = "ArrowRight"))
        assertEquals(0, clicks)
        assertEquals(0, progressChanges)

        showDialog = false
        awaitA11YChanges()
        assertSame(slider, element("slider"))
        assertNull(slider.closest("[inert]"))
        assertNotNull(element("hidden").closest("[inert]"))
        dispatchClick(slider)
        slider.dispatchEvent(keyEvent("ArrowRight", code = "ArrowRight"))
        assertEquals(1, clicks)
        assertEquals(1, progressChanges)
    }

    @Test
    fun actionsAreNotDispatchedForInertNodes() = runApplicationTest {
        var clicks = 0
        createComposeWindow {
            Box(Modifier.size(100.dp).clipToBounds()) {
                Box(
                    Modifier.offset(200.dp, 0.dp).size(40.dp).testTag("action").clickable {
                        clicks++
                    }
                )
            }
        }
        awaitA11YChanges()

        element("action").click()
        assertEquals(0, clicks)
    }
}

private fun dispatchClick(element: HTMLElement) {
    val event = Event("click")
    event.initEvent("click", bubbles = true, cancelable = true)
    element.dispatchEvent(event)
}

private fun scrollIntoView(element: HTMLElement) {
    js("element.scrollIntoView({ block: 'nearest', inline: 'nearest' })")
}
