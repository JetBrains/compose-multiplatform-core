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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

class TraversalOrderA11YTest : OnCanvasTests {
    @Test
    fun traversalGroupsChangeReadingOrder() = runApplicationTest {
        var groupColumns by mutableStateOf(false)
        createComposeWindow {
            Row {
                TraversalColumn("left", groupColumns)
                TraversalColumn("right", groupColumns)
            }
        }

        awaitA11YChanges()
        assertEquals(listOf("left1", "right1", "left2", "right2"), readingOrder())
        val left2 = assertNotNull(getShadowRoot().getElementById("cell-left2"))
        val originalBounds = left2.getBoundingClientRect()

        groupColumns = true
        awaitA11YChanges()
        assertEquals(listOf("left1", "left2", "right1", "right2"), readingOrder())
        assertSame(left2, getShadowRoot().getElementById("cell-left2"))
        assertEquals(originalBounds.left, left2.getBoundingClientRect().left)
        assertEquals(originalBounds.top, left2.getBoundingClientRect().top)

        groupColumns = false
        awaitA11YChanges()
        assertEquals(listOf("left1", "right1", "left2", "right2"), readingOrder())
        assertSame(left2, getShadowRoot().getElementById("cell-left2"))
    }

    @Test
    fun nestedTraversalGroupStaysTogether() = runApplicationTest {
        createComposeWindow {
            Row {
                Column(Modifier.semantics { isTraversalGroup = false }) {
                    Cell("left1")
                    Column(Modifier.semantics { isTraversalGroup = true }) {
                        Cell("left2")
                        Cell("left3")
                    }
                }
                Column(Modifier.semantics { isTraversalGroup = false }) {
                    Cell("right1")
                    Cell("right2")
                    Cell("right3")
                }
            }
        }

        awaitA11YChanges()
        assertEquals(
            listOf("left1", "right1", "left2", "left3", "right2", "right3"),
            readingOrder(),
        )
    }

    @Test
    fun traversalIndexOrdersWholeGroups() = runApplicationTest {
        createComposeWindow {
            Row {
                TraversalColumn("left", group = true, index = 1f)
                TraversalColumn("right", group = true, index = -1f)
            }
        }

        awaitA11YChanges()
        assertEquals(listOf("right1", "right2", "left1", "left2"), readingOrder())
    }

    @Test
    fun materialCardsRemainGroupedByDefault() = runApplicationTest {
        createComposeWindow {
            Row {
                listOf("left", "right").forEach { label ->
                    Card {
                        Column {
                            Cell("${label}1")
                            Cell("${label}2")
                        }
                    }
                }
            }
        }

        awaitA11YChanges()
        assertEquals(listOf("left1", "left2", "right1", "right2"), readingOrder())
    }

    @Test
    fun decorativeNodesDoNotChangeReadingOrder() = runApplicationTest {
        createComposeWindow {
            Box(Modifier.size(200.dp, 30.dp)) {
                Box(Modifier.offset(y = 15.dp).size(10.dp).background(Color.Red))
                Text("left1", Modifier.testTag("cell-left1").size(100.dp, 20.dp))
                Text(
                    "right1",
                    Modifier.offset(x = 100.dp).testTag("cell-right1").size(100.dp, 10.dp),
                )
            }
        }

        awaitA11YChanges()
        assertEquals(listOf("left1", "right1"), readingOrder())
    }

    @Test
    fun ungroupedColumnsFollowRightToLeftLayout() = runApplicationTest {
        createComposeWindow {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(Modifier.semantics { isTraversalGroup = true }) {
                    TraversalColumn("right", group = false)
                    TraversalColumn("left", group = false)
                }
            }
        }

        awaitA11YChanges()
        assertEquals(listOf("right1", "left1", "right2", "left2"), readingOrder())
    }

    @Test
    fun offscreenItemsKeepTheirContentOrderAndScrollContainer() = runApplicationTest {
        val scrollState = ScrollState(0)
        createComposeWindow {
            Column(Modifier.size(200.dp, 60.dp).verticalScroll(scrollState).testTag("scroller")) {
                Row {
                    listOf("left", "right").forEach { label ->
                        Column(Modifier.semantics { isTraversalGroup = false }) {
                            repeat(3) { Cell("$label${it + 1}") }
                        }
                    }
                }
            }
        }

        awaitA11YChanges()
        val order = listOf("left1", "right1", "left2", "right2", "left3", "right3")
        assertEquals(order, readingOrder())
        val scroller = assertNotNull(getShadowRoot().getElementById("scroller"))
        order.forEach { label ->
            assertTrue(scroller.contains(getShadowRoot().getElementById("cell-$label")))
        }

        scrollState.scrollTo(30)
        val startTime = currentTimeMillis()
        while (abs(scroller.scrollTop - 30 / window.devicePixelRatio) > 1) {
            assertTrue(currentTimeMillis() - startTime < 5000, "DOM scroll offset did not update")
            awaitAnimationFrame()
        }
        assertEquals(order, readingOrder())
    }

    @Composable
    private fun TraversalColumn(label: String, group: Boolean, index: Float = 0f) {
        Card(Modifier.semantics {
            isTraversalGroup = group
            traversalIndex = index
        }) {
            Column {
                Cell("${label}1")
                Cell("${label}2")
            }
        }
    }

    @Composable
    private fun Cell(label: String) {
        Text(label, Modifier.testTag("cell-$label").size(100.dp, 30.dp))
    }

    private fun readingOrder(): List<String> {
        val cells = assertNotNull(getA11YContainer()).querySelectorAll("[id^='cell-']")
        return (0 until cells.length).map {
            (cells.item(it) as HTMLElement).id.removePrefix("cell-")
        }
    }
}
