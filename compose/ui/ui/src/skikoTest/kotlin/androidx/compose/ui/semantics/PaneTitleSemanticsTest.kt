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

package androidx.compose.ui.semantics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PaneTitleSemanticsTest {
    @Test
    fun clickableAncestorPreservesPaneAndItsChildren() = runComposeUiTest {
        setContent {
            Column(Modifier.testTag("root").clickable {}) {
                Text("Outside")
                Column(Modifier.testTag("pane").semantics { paneTitle = "Menu" }) {
                    Column(Modifier.testTag("group")) {
                        Text("First", Modifier.testTag("first"))
                        Text("Second", Modifier.testTag("second"))
                    }
                    Box(Modifier.testTag("action").clickable {}) { Text("Action") }
                }
            }
        }

        val root = onNodeWithTag("root").fetchSemanticsNode()
        val pane = onNodeWithTag("pane").fetchSemanticsNode()
        val group = onNodeWithTag("group").fetchSemanticsNode()
        val first = onNodeWithTag("first").fetchSemanticsNode()
        val second = onNodeWithTag("second").fetchSemanticsNode()
        val action = onNodeWithTag("action").fetchSemanticsNode()

        assertNull(root.config.getOrNull(SemanticsProperties.PaneTitle))
        assertEquals(listOf("Outside"), root.config[SemanticsProperties.Text].map { it.text })
        assertEquals(listOf(pane.id), root.children.map { it.id })
        assertEquals("Menu", pane.config[SemanticsProperties.PaneTitle])
        assertNull(pane.config.getOrNull(SemanticsProperties.Text))
        assertEquals(listOf(group.id, action.id), pane.children.map { it.id })
        assertEquals(listOf(first.id, second.id), group.children.map { it.id })
        assertEquals(root.id, pane.parent?.id)
        assertEquals(pane.id, group.parent?.id)
        assertEquals(group.id, first.parent?.id)
        assertEquals(pane.id, action.parent?.id)
        assertEquals(listOf("Action"), action.config[SemanticsProperties.Text].map { it.text })
    }

    @Test
    fun siblingPanesKeepTheirOwnTitles() = runComposeUiTest {
        setContent {
            Column(Modifier.testTag("root").clickable {}) {
                Box(Modifier.testTag("firstPane").semantics { paneTitle = "First pane" }) {
                    Text("First content")
                }
                Box(Modifier.testTag("secondPane").semantics { paneTitle = "Second pane" }) {
                    Text("Second content")
                }
            }
        }

        val root = onNodeWithTag("root").fetchSemanticsNode()
        val first = onNodeWithTag("firstPane").fetchSemanticsNode()
        val second = onNodeWithTag("secondPane").fetchSemanticsNode()

        assertNull(root.config.getOrNull(SemanticsProperties.PaneTitle))
        assertNull(root.config.getOrNull(SemanticsProperties.Text))
        assertEquals(listOf(first.id, second.id), root.children.map { it.id })
        assertEquals("First pane", first.config[SemanticsProperties.PaneTitle])
        assertEquals("Second pane", second.config[SemanticsProperties.PaneTitle])
    }

    @Test
    fun nestedPanesKeepTheirParents() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("root").clickable {}) {
                Box(Modifier.testTag("outerPane").semantics { paneTitle = "Outer pane" }) {
                    Box(Modifier.testTag("innerPane").semantics { paneTitle = "Inner pane" }) {
                        Text("Content", Modifier.testTag("content"))
                    }
                }
            }
        }

        val root = onNodeWithTag("root").fetchSemanticsNode()
        val outer = onNodeWithTag("outerPane").fetchSemanticsNode()
        val inner = onNodeWithTag("innerPane").fetchSemanticsNode()
        val content = onNodeWithTag("content").fetchSemanticsNode()

        assertEquals(listOf(outer.id), root.children.map { it.id })
        assertEquals(listOf(inner.id), outer.children.map { it.id })
        assertEquals(root.id, outer.parent?.id)
        assertEquals(outer.id, inner.parent?.id)
        assertEquals(inner.id, content.parent?.id)
        assertEquals("Outer pane", outer.config[SemanticsProperties.PaneTitle])
        assertEquals("Inner pane", inner.config[SemanticsProperties.PaneTitle])
    }

    @Test
    fun explicitlyMergingPaneStillMergesText() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("root").clickable {}) {
                Column(
                    Modifier.testTag("pane").semantics(mergeDescendants = true) {
                        paneTitle = "Menu"
                    }
                ) {
                    Text("First")
                    Text("Second")
                }
            }
        }

        val root = onNodeWithTag("root").fetchSemanticsNode()
        val pane = onNodeWithTag("pane").fetchSemanticsNode()

        assertEquals(listOf(pane.id), root.children.map { it.id })
        assertEquals("Menu", pane.config[SemanticsProperties.PaneTitle])
        assertEquals(listOf("First", "Second"), pane.config[SemanticsProperties.Text].map { it.text })
        assertTrue(pane.children.isEmpty())
    }

    @Test
    fun clearingSemanticsStillHidesDescendantPane() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("root").clickable {}) {
                Box(Modifier.clearAndSetSemantics { contentDescription = "Replacement" }) {
                    Box(Modifier.testTag("pane").semantics { paneTitle = "Hidden pane" }) {
                        Text("Hidden content")
                    }
                }
            }
        }

        val root = onNodeWithTag("root").fetchSemanticsNode()

        assertEquals(listOf("Replacement"), root.config[SemanticsProperties.ContentDescription])
        assertNull(root.config.getOrNull(SemanticsProperties.PaneTitle))
        assertTrue(root.children.isEmpty())
        onNodeWithTag("pane").assertDoesNotExist()
    }

    @Test
    fun unmergedTreeKeepsOriginalHierarchy() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("root").clickable {}) {
                Box(Modifier.testTag("container").semantics {}) {
                    Box(Modifier.testTag("pane").semantics { paneTitle = "Menu" }) {
                        Text("Content", Modifier.testTag("content"))
                    }
                }
            }
        }

        val root = onNodeWithTag("root", useUnmergedTree = true).fetchSemanticsNode()
        val container = onNodeWithTag("container", useUnmergedTree = true).fetchSemanticsNode()
        val pane = onNodeWithTag("pane", useUnmergedTree = true).fetchSemanticsNode()
        val content = onNodeWithTag("content", useUnmergedTree = true).fetchSemanticsNode()

        assertEquals(listOf(container.id), root.children.map { it.id })
        assertEquals(root.id, container.parent?.id)
        assertEquals(container.id, pane.parent?.id)
        assertEquals(pane.id, content.parent?.id)
        assertEquals("Menu", pane.config[SemanticsProperties.PaneTitle])
    }
}
