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

import androidx.collection.intObjectMapOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.createEagerAccessibilityWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.accessibility.sortByGeometryGroupingsForWeb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.sortByGeometryGroupings
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WebSemanticsSortTest : OnCanvasTests {

    @Test
    fun customBoundsAreUsedForRowsAndWithinRowsAndGroupExpansion() = runApplicationTest {
        val nodes = mutableMapOf<String, SemanticsNode>()
        fun Modifier.record(tag: String) = testTag(tag).onGloballyPositioned {
            nodes[tag] = SemanticsNode((it as NodeCoordinator).layoutNode, false)
        }
        var direction by mutableStateOf(LayoutDirection.Ltr)
        createEagerAccessibilityWindow {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().record("parent"), contentAlignment = AbsoluteAlignment.TopLeft) {
                    Box(Modifier.size(20.dp).record("a"))
                    Box(Modifier.absoluteOffset(40.dp, 0.dp).size(20.dp).record("b"))
                    Box(Modifier.absoluteOffset(0.dp, 40.dp).size(20.dp).record("c"))
                    Box(Modifier.absoluteOffset(40.dp, 40.dp).size(20.dp).record("d"))
                }
            }
        }
        awaitA11YChanges()

        val parent = nodes.getValue("parent")
        val children = listOf("a", "b", "c", "d").map { nodes.getValue(it) }
        assertEquals(children.map { it.id }, parent.sortByGeometryGroupings(children).map { it.id })
        // Reverse both rows and columns relative to actual layout. Using custom bounds only in
        // the comparator, or only for row grouping, cannot produce this order.
        val bounds = listOf(
            Rect(40f, 40f, 60f, 60f), Rect(0f, 40f, 20f, 60f),
            Rect(40f, 0f, 60f, 20f), Rect(0f, 0f, 20f, 20f),
        )
        val boundsById = children.mapIndexed { i, node -> node.id to bounds[i] }.toMap()
        fun sort() = nodes.getValue("parent").sortByGeometryGroupingsForWeb(
            children, boundsOf = { boundsById.getValue(it.id) }
        ).map { it.id }
        assertEquals(children.reversed().map { it.id }, sort())

        val mapping = intObjectMapOf(children[1].id, children.drop(2))
        for (retainGroup in listOf(false, true)) {
            val sorted = parent.sortByGeometryGroupingsForWeb(
                children.take(2),
                isFocusableContainer = { retainGroup },
                containerChildrenMapping = mapping,
                boundsOf = { boundsById.getValue(it.id) },
            )
            val expected = if (retainGroup) listOf("b", "c", "d", "a") else listOf("c", "d", "a")
            assertEquals(expected.map { nodes.getValue(it).id }, sorted.map { it.id })
        }

        direction = LayoutDirection.Rtl
        awaitIdle()
        awaitAnimationFrame()
        assertEquals(LayoutDirection.Rtl, nodes.getValue("parent").layoutInfo.layoutDirection)
        assertEquals(listOf("c", "d", "a", "b").map { nodes.getValue(it).id }, sort())
    }
}
