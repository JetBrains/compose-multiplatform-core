/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.platform.a11y

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.createEagerAccessibilityWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

class A11yTraversalGroupTest : OnCanvasTests {

    @Test
    fun nestedTraversalGroupIndexOverridesGeometryAndChildrenSortWithinGroup() = runApplicationTest {
        createEagerAccessibilityWindow {
            Box(Modifier.fillMaxSize().semantics { isTraversalGroup = true }.testTag("parent")) {
                Box(
                    Modifier.absoluteOffset(0.dp, 0.dp)
                        .size(140.dp, 120.dp)
                        .semantics { isTraversalGroup = true }
                        .testTag("outerGroup")
                ) {
                    Box(
                        Modifier.absoluteOffset(0.dp, 0.dp)
                            .size(20.dp)
                            .testTag("outerByGeometry")
                    )
                    Box(
                        Modifier.absoluteOffset(80.dp, 0.dp)
                            .size(60.dp, 40.dp)
                            .semantics {
                                isTraversalGroup = true
                                traversalIndex = -1f
                            }
                            .testTag("nestedGroup")
                    ) {
                        Box(
                            Modifier.absoluteOffset(40.dp, 0.dp)
                                .size(20.dp)
                                .semantics { traversalIndex = -1f }
                                .testTag("nestedByTraversalIndex")
                        )
                        Box(Modifier.absoluteOffset(0.dp, 0.dp).size(20.dp).testTag("nestedByGeometry"))
                    }
                    Box(
                        Modifier.absoluteOffset(0.dp, 80.dp)
                            .size(20.dp)
                            .testTag("outerBottom")
                    )
                }
                Box(Modifier.absoluteOffset(200.dp, 0.dp).size(20.dp).testTag("sibling"))
            }
        }
        awaitA11YChanges()

        assertDirectChildOrder("parent", "outerGroup", "sibling")
        assertTrue(
            element("outerByGeometry").getBoundingClientRect().left <
                element("nestedGroup").getBoundingClientRect().left,
            "The nested group must be geometrically after its sibling to verify traversalIndex ordering",
        )
        assertDirectChildOrder("outerGroup", "nestedGroup", "outerByGeometry", "outerBottom")
        assertDirectChildOrder("nestedGroup", "nestedByTraversalIndex", "nestedByGeometry")
    }

    @Test
    fun rtlTraversalGroupsSortRightToLeftAtEachLevel() = runApplicationTest {
        createEagerAccessibilityWindow {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(
                    Modifier.fillMaxSize().semantics { isTraversalGroup = true }.testTag("parent"),
                    contentAlignment = AbsoluteAlignment.TopLeft,
                ) {
                    listOf("leftGroup" to 0.dp, "rightGroup" to 160.dp).forEach { (tag, x) ->
                        Box(
                            Modifier.absoluteOffset(x, 0.dp)
                                .size(100.dp, 60.dp)
                                .semantics { isTraversalGroup = true }
                                .testTag(tag),
                            contentAlignment = AbsoluteAlignment.TopLeft,
                        ) {
                            Box(Modifier.absoluteOffset(0.dp, 0.dp).size(20.dp).testTag("${tag}Left"))
                            Box(Modifier.absoluteOffset(40.dp, 0.dp).size(20.dp).testTag("${tag}Right"))
                        }
                    }
                }
            }
        }
        awaitA11YChanges()

        val leftGroup = element("leftGroup").getBoundingClientRect()
        val rightGroup = element("rightGroup").getBoundingClientRect()
        assertTrue(leftGroup.right <= rightGroup.left, "The right group must be physically right of the left group")
        assertDirectChildOrder("parent", "rightGroup", "leftGroup")
        for (groupTag in listOf("leftGroup", "rightGroup")) {
            val group = element(groupTag).getBoundingClientRect()
            val left = element("${groupTag}Left").getBoundingClientRect()
            val right = element("${groupTag}Right").getBoundingClientRect()
            assertTrue(
                left.left >= group.left && left.right <= group.right &&
                    left.top >= group.top && left.bottom <= group.bottom,
                "$groupTag left child must be inside the group",
            )
            assertTrue(
                right.left >= group.left && right.right <= group.right &&
                    right.top >= group.top && right.bottom <= group.bottom,
                "$groupTag right child must be inside the group",
            )
            assertFalse(element("${groupTag}Left").hasAttribute("inert"), "$groupTag left child must be reachable")
            assertFalse(element("${groupTag}Right").hasAttribute("inert"), "$groupTag right child must be reachable")
            assertEquals(left.top, right.top, "$groupTag children must occupy the same row")
            assertTrue(left.right <= right.left, "$groupTag children must be physically left-to-right")
        }
        assertDirectChildOrder("leftGroup", "leftGroupRight", "leftGroupLeft")
        assertDirectChildOrder("rightGroup", "rightGroupRight", "rightGroupLeft")
    }

    @Test
    fun nonTraversalScrollContainerKeepsNestedGroupReachableWhenScrolled() = runApplicationTest {
        val scrollState = ScrollState(0)
        var syncMarker by mutableStateOf("initial")
        var scrollContainerSemantics: SemanticsNode? = null
        createEagerAccessibilityWindow {
            Column(
                Modifier.size(100.dp, 80.dp)
                    // Keep this outside verticalScroll so it overrides the scroll modifier's
                    // default isTraversalGroup=true on the same layout node.
                    .semantics {
                        isTraversalGroup = false
                        contentDescription = syncMarker
                    }
                    .verticalScroll(scrollState)
                    .testTag("scrollContainer")
                    .onGloballyPositioned {
                        scrollContainerSemantics = SemanticsNode((it as NodeCoordinator).layoutNode, false)
                    }
            ) {
                Box(Modifier.size(100.dp, 40.dp).testTag("scrollItem0"))
                Column(
                    Modifier.width(100.dp)
                        .semantics { isTraversalGroup = true }
                        .testTag("nestedGroup")
                ) {
                    repeat(2) { index ->
                        Box(Modifier.size(100.dp, 40.dp).testTag("nestedItem$index"))
                    }
                }
                repeat(3) { index ->
                    Box(Modifier.size(100.dp, 40.dp).testTag("scrollItem${index + 1}"))
                }
            }
        }
        awaitCondition("scroll container and nested children must be exposed") {
            getShadowRoot().getElementById("scrollContainer") != null &&
                getShadowRoot().getElementById("nestedGroup") != null &&
                (0 until 2).all { getShadowRoot().getElementById("nestedItem$it") != null } &&
                (0 until 4).all { getShadowRoot().getElementById("scrollItem$it") != null }
        }

        val scrollContainer = element("scrollContainer")
        val semantics = assertNotNull(scrollContainerSemantics)
        assertFalse(
            semantics.config.getOrElse(SemanticsProperties.IsTraversalGroup) { false },
            "The fixture must expose the scroll container with isTraversalGroup=false",
        )
        assertEquals("scroll", scrollContainer.style.overflowY)
        assertDirectChildOrder("scrollContainer", "scrollItem0", "nestedGroup", "scrollItem1", "scrollItem2", "scrollItem3")
        assertSame(scrollContainer, element("nestedGroup").parentElement)
        assertSame(element("nestedGroup"), element("nestedItem0").parentElement)
        assertSame(element("nestedGroup"), element("nestedItem1").parentElement)

        val positionedTags = listOf("scrollItem0", "nestedGroup", "nestedItem0", "scrollItem3")
        val positionsBeforeScroll = positionedTags.associateWith { element(it).getBoundingClientRect().top }
        scrollState.scrollTo(scrollState.maxValue)
        awaitCondition("Compose and accessibility DOM scroll offsets and positions must synchronize") {
            val scrollOffset = scrollContainer.scrollTop
            !scrollState.isScrollInProgress &&
                scrollState.value == scrollState.maxValue &&
                scrollOffset > 0.0 &&
                positionedTags.all { tag ->
                    abs(positionsBeforeScroll.getValue(tag) - element(tag).getBoundingClientRect().top - scrollOffset) <= 1.0
                }
        }
        syncMarker = "scrolled"
        awaitCondition("the scrolled semantics update must reach the accessibility DOM") {
            scrollContainer.getAttribute("aria-label") == "scrolled"
        }
        repeat(4) { index ->
            assertFalse(element("scrollItem$index").hasAttribute("inert"), "Scrollable child $index must remain reachable")
        }
        repeat(2) { index ->
            assertFalse(element("nestedItem$index").hasAttribute("inert"), "Nested group child $index must remain reachable")
        }
    }

    @Test
    fun togglingTraversalGroupUpdatesOrderWithoutReplacingElements() = runApplicationTest {
        var grouped by mutableStateOf(true)
        createEagerAccessibilityWindow {
            Box(
                Modifier.fillMaxSize()
                    .semantics { contentDescription = if (grouped) "grouped" else "ungrouped" }
                    .testTag("parent")
            ) {
                Row {
                    Column(Modifier.width(160.dp).semantics { isTraversalGroup = grouped }) {
                        Text("Left top", Modifier.size(160.dp, 40.dp).testTag("dynamicLeftTop"), fontSize = 12.sp)
                        Text("Left bottom", Modifier.size(160.dp, 40.dp).testTag("dynamicLeftBottom"), fontSize = 12.sp)
                    }
                    Column(Modifier.width(160.dp).semantics { isTraversalGroup = grouped }) {
                        Text("Right top", Modifier.size(160.dp, 40.dp).testTag("dynamicRightTop"), fontSize = 12.sp)
                        Text("Right bottom", Modifier.size(160.dp, 40.dp).testTag("dynamicRightBottom"), fontSize = 12.sp)
                    }
                }
            }
        }
        awaitA11YChanges()

        val expectedGrouped = listOf(
            "dynamicLeftTop", "dynamicLeftBottom", "dynamicRightTop", "dynamicRightBottom",
        )
        val expectedUngrouped = listOf(
            "dynamicLeftTop", "dynamicRightTop", "dynamicLeftBottom", "dynamicRightBottom",
        )
        val originalElements = expectedGrouped.associateWith(::element)
        assertEquals(expectedGrouped, descendantIds(element("parent"), "dynamic"))

        grouped = false
        awaitCondition("the semantics update must reach the accessibility DOM") {
            element("parent").getAttribute("aria-label") == "ungrouped"
        }
        originalElements.forEach { (tag, htmlElement) -> assertSame(htmlElement, element(tag)) }
        assertEquals(expectedUngrouped, descendantIds(element("parent"), "dynamic"))

        grouped = true
        awaitCondition("the semantics update must restore the grouped accessibility tree") {
            element("parent").getAttribute("aria-label") == "grouped"
        }
        originalElements.forEach { (tag, htmlElement) -> assertSame(htmlElement, element(tag)) }
        assertEquals(expectedGrouped, descendantIds(element("parent"), "dynamic"))
    }

    @Test
    fun traversalGroupsKeepTwoColumnsTogether() = checkTwoColumnOrder(
        groupColumns = true,
        expectedOrder = listOf("textLeftTop", "textLeftBottom", "textRightTop", "textRightBottom"),
    )

    // Reproduces https://youtrack.jetbrains.com/issue/CMP-8630
    @Test
    fun nonTraversalGroupsInterleaveTwoColumnsByRow() = checkTwoColumnOrder(
        groupColumns = false,
        expectedOrder = listOf("textLeftTop", "textRightTop", "textLeftBottom", "textRightBottom"),
    )

    @Test
    fun defaultNonTraversalGroupsInterleaveTwoColumnsByRow() = checkTwoColumnOrder(
        groupColumns = null,
        expectedOrder = listOf("textLeftTop", "textRightTop", "textLeftBottom", "textRightBottom"),
    )

    @Test
    fun testTagsDoNotCreateTraversalGroups() = checkTwoColumnOrder(
        groupColumns = false,
        expectedOrder = listOf("textLeftTop", "textRightTop", "textLeftBottom", "textRightBottom"),
        tagColumns = true,
    )

    private fun assertDirectChildOrder(parentTag: String, vararg childTags: String) {
        val parent = element(parentTag)
        assertEquals(
            childTags.toList(),
            (0 until parent.children.length).mapNotNull { parent.children[it]?.id?.takeIf(String::isNotEmpty) },
        )
    }

    private fun element(tag: String): HTMLElement = assertNotNull(
        getShadowRoot().getElementById(tag) as? HTMLElement,
        "No A11Y element for '$tag'",
    )

    private fun descendantIds(parent: HTMLElement, idPrefix: String): List<String> {
        val elements = parent.querySelectorAll("[id^='$idPrefix']")
        return (0 until elements.length).mapNotNull { (elements[it] as? HTMLElement)?.id }
    }

    private fun checkTwoColumnOrder(
        groupColumns: Boolean?,
        expectedOrder: List<String>,
        tagColumns: Boolean = false,
    ) = runApplicationTest {
        fun columnModifier(tag: String) = Modifier.width(160.dp)
            .semantics { if (groupColumns != null) isTraversalGroup = groupColumns }
            .then(if (tagColumns) Modifier.testTag(tag) else Modifier)
        createEagerAccessibilityWindow {
            Row(Modifier.semantics { isTraversalGroup = true }.testTag("parent")) {
                Column(columnModifier("leftColumn")) {
                    Text("This sentence is in ", Modifier.size(160.dp, 40.dp).testTag("textLeftTop"), fontSize = 12.sp)
                    Text("the left column.", Modifier.size(160.dp, 40.dp).testTag("textLeftBottom"), fontSize = 12.sp)
                }
                Column(columnModifier("rightColumn")) {
                    Text("This sentence is", Modifier.size(160.dp, 40.dp).testTag("textRightTop"), fontSize = 12.sp)
                    Text("on the right.", Modifier.size(160.dp, 40.dp).testTag("textRightBottom"), fontSize = 12.sp)
                }
            }
        }
        awaitA11YChanges()

        val parent = assertNotNull(getShadowRoot().getElementById("parent"))
        if (tagColumns) {
            assertNull(getShadowRoot().getElementById("leftColumn"), "Layout wrappers must be omitted, not emptied")
            assertNull(getShadowRoot().getElementById("rightColumn"), "Layout wrappers must be omitted, not emptied")
        }
        // querySelectorAll returns descendants in DOM reading order, independent of how the
        // accessibility implementation nests or flattens the column containers.
        val textNodes = parent.querySelectorAll("[id^='text']")
        val elements = (0 until textNodes.length).map { textNodes[it] as HTMLElement }
        assertEquals(4, elements.size, "All four text nodes must be exposed")
        elements.forEach {
            assertFalse(it.hasAttribute("inert"), "${it.id} must be accessibility reachable")
            val bounds = it.getBoundingClientRect()
            assertTrue(bounds.width > 0 && bounds.height > 0, "${it.id} must have nonempty bounds")
        }
        val bounds = elements.associate { it.id to it.getBoundingClientRect() }
        val leftTop = bounds.getValue("textLeftTop")
        val leftBottom = bounds.getValue("textLeftBottom")
        val rightTop = bounds.getValue("textRightTop")
        val rightBottom = bounds.getValue("textRightBottom")
        assertEquals(leftTop.top, rightTop.top, "Top texts must occupy the same row")
        assertEquals(leftBottom.top, rightBottom.top, "Bottom texts must occupy the same row")
        assertTrue(leftTop.right <= rightTop.left, "Columns must not overlap")
        assertTrue(leftTop.bottom <= leftBottom.top, "Rows must not overlap")

        assertEquals(
            expectedOrder,
            elements.map { it.id },
            "Accessibility DOM reading order with isTraversalGroup = $groupColumns",
        )
    }
}
