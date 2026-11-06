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

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

class LazyListA11yOrderTest : OnCanvasTests {
    @Test
    fun lazyTextItemsKeepDataOrderAfterIncrementalScroll() = runApplicationTest {
        val state = LazyListState()
        var marker by mutableStateOf("initial")
        var listNode: SemanticsNode? = null
        createComposeWindow {
            LazyColumn(
                state = state,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
                    .testTag("list")
                    .semantics { contentDescription = marker }
                    .onGloballyPositioned {
                        listNode = SemanticsNode((it as NodeCoordinator).layoutNode, false)
                    },
            ) {
                items(25) { index ->
                    Text(
                        "Item ${index + 1}",
                        Modifier.padding(16.dp).width(300.dp).testTag("item$index"),
                    )
                }
            }
        }
        awaitCondition("the list must be exposed") {
            getShadowRoot().getElementById("list") != null
        }
        val list = assertNotNull(getShadowRoot().getElementById("list") as? HTMLElement)
        val semantics = assertNotNull(listNode)
        fun itemNode(index: Int) = semantics.replacedChildren.firstOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag) == "item$index"
        }

        // Large scrollToItem jumps miss this case. Small forward scrolls allow the default
        // cache-window prefetch to reuse slots from rows leaving the beyond-bounds region.
        // Three extra rows are placed for screen-reader traversal; prefetch measures the next row.
        var prefetchedIndex = -1
        val itemSize = state.layoutInfo.visibleItemsInfo.first().size.toFloat()
        repeat(6) { step ->
            state.scrollBy(if (step == 0) itemSize / 2 else itemSize - 1)
            prefetchedIndex = state.layoutInfo.visibleItemsInfo.last().index + 4
            awaitCondition("item ${prefetchedIndex + 1} must be prefetched and measured") {
                itemNode(prefetchedIndex)?.let { it.size.width > 0 && it.size.height > 0 } == true
            }
            marker = "step$step"
            awaitCondition("accessibility must sync $marker") {
                list.getAttribute("aria-label") == marker
            }
        }

        // Item 3 is still placed beyond the viewport, while a later item is measured
        // by prefetch but has not been placed yet.
        val thirdItem = assertNotNull(itemNode(2), "Item 3 must participate in the scenario")
        val lateItem = assertNotNull(itemNode(prefetchedIndex), "The prefetched item must participate in the scenario")
        assertTrue(thirdItem.layoutNode.isPlaced, "Item 3 must have a list position")
        assertTrue(!lateItem.layoutNode.isPlaced, "The prefetched item must remain unplaced")

        val elements = list.querySelectorAll("[id^='item']")
        val items = (0 until elements.length).map { elements[it] as HTMLElement }
        val actualOrder = items.map { it.id.removePrefix("item").toInt() }
        val context = "exposed=$actualOrder, firstVisible=${state.firstVisibleItemIndex}, " +
            "offset=${state.firstVisibleItemScrollOffset}"
        assertTrue(2 in actualOrder, "The placed third item must be exposed: $context")
        assertTrue(prefetchedIndex !in actualOrder, "The unplaced prefetched item must not be exposed: $context")
        assertEquals(actualOrder.sorted(), actualOrder, "Exposed lazy items must follow data order: $context")
    }
}
