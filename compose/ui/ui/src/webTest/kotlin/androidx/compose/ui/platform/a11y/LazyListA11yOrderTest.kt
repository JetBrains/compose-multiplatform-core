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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.platform.accessibility.unclippedBoundsInWindowForWeb
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
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

class LazyListA11yOrderTest : OnCanvasTests {
    @Test
    fun lazyTextItemsKeepDataOrderAfterIncrementalScroll() = runApplicationTest {
        val container = getContainer() as HTMLElement
        val originalStyle = container.getAttribute("style")
        container.style.width = "570.8px"
        container.style.height = "666px"
        val state = LazyListState()
        var marker by mutableStateOf("initial")
        var listNode: SemanticsNode? = null
        val names =
            listOf(
                "Fiona Apple",
                "Charlie Brown",
                "George Clooney",
                "Penelope Cruz",
                "Laura Dern",
                "Nancy Drew",
                "Rachel Green",
                "Yara Greyjoy",
                "Kevin Hart",
                "Samuel Jackson",
                "Alice Johnson",
                "Michael Jordan",
                "Ian McKellen",
                "Hannah Montana",
                "Edward Norton",
                "Diana Prince",
                "Julia Roberts",
                "Bob Smith",
                "Taylor Swift",
                "Quentin Tarantino",
                "Uma Thurman",
                "Vincent Vega",
                "Walter White",
                "Oscar Wilde",
                "Xavier Woods",
            )
        try {
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
                    itemsIndexed(names) { index, name ->
                        Text(
                            "${index + 1}. $name",
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
            // With the demo's rows and this viewport, the prefetched indices are 18 through 23.
            val itemSize = state.layoutInfo.visibleItemsInfo.first().size.toFloat()
            var previousRowBounds = itemNode(1)?.unclippedBoundsInWindowForWeb()
            repeat(6) { step ->
                state.scrollBy(if (step == 0) itemSize / 2 else itemSize - 1)
                val prefetchedIndex = 18 + step
                awaitCondition("item ${prefetchedIndex + 1} must be prefetched and measured") {
                    itemNode(prefetchedIndex)?.let { it.size.width > 0 && it.size.height > 0 } == true
                }
                marker = "step$step"
                awaitCondition("accessibility must sync $marker") {
                    list.getAttribute("aria-label") == marker
                }
                if (step == 4) {
                    previousRowBounds = itemNode(1)?.unclippedBoundsInWindowForWeb()
                }
            }

            // Both reported items participate in this layout: item 3 is placed beyond the
            // viewport, while item 24 is measured by prefetch but has not been placed yet.
            val thirdItem = assertNotNull(itemNode(2), "Item 3 must participate in the scenario")
            val lateItem = assertNotNull(itemNode(23), "Item 24 must participate in the scenario")
            assertTrue(thirdItem.layoutNode.isPlaced, "Item 3 must have a list position")
            assertTrue(!lateItem.layoutNode.isPlaced, "Item 24 must still be prefetched")

            val elements = list.querySelectorAll("[id^='item']")
            val items = (0 until elements.length).map { elements[it] as HTMLElement }
            val actualOrder = items.map { it.id.removePrefix("item").toInt() }
            assertTrue(2 in actualOrder, "The placed third item must be exposed")
            val domGeometry = items.joinToString { "${it.id}(left=${it.style.left},top=${it.style.top})" }
            val semanticGeometry = semantics.replacedChildren.joinToString { node ->
                "${node.config.getOrNull(SemanticsProperties.TestTag)}(" +
                    "placed=${node.layoutNode.isPlaced}," +
                    "sortBounds=${node.unclippedBoundsInWindowForWeb()})"
            }
            assertEquals(
                actualOrder.sorted(),
                actualOrder,
                "Exposed lazy items must follow data order. " +
                    "firstVisible=${state.firstVisibleItemIndex}, " +
                    "offset=${state.firstVisibleItemScrollOffset}, " +
                    "item 2 bounds before slot reuse=$previousRowBounds; " +
                    "semantics=[$semanticGeometry]; DOM=[$domGeometry]",
            )
        } finally {
            if (originalStyle == null) {
                container.removeAttribute("style")
            } else {
                container.setAttribute("style", originalStyle)
            }
        }
    }

    private suspend fun awaitCondition(message: String, condition: () -> Boolean) {
        val start = currentTimeMillis()
        suspendCancellableCoroutine<Unit> { continuation ->
            fun check() {
                when {
                    condition() -> continuation.resumeWith(Result.success(Unit))
                    currentTimeMillis() - start > 5000 ->
                        continuation.resumeWith(Result.failure(AssertionError("Timed out waiting for: $message")))
                    else -> window.requestAnimationFrame { check() }
                }
            }
            check()
        }
    }
}
