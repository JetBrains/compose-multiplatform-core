/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.runtime.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mock.Text
import androidx.compose.runtime.mock.View
import androidx.compose.runtime.mock.ViewApplier
import androidx.compose.runtime.mock.compositionTest
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompositionInstanceTests {
    @Test
    fun canFindACompositionInstance() = compositionTest {
        val table = mutableSetOf<CompositionData>()
        compose {
            CompositionLocalProvider(LocalInspectionTables provides table) {
                TestSubcomposition { Text("Some value") }
            }
        }

        assertEquals(1, table.size)
        val data = table.first()
        val instance = data.findCompositionInstance()
        assertNotNull(instance)
    }

    @Test
    fun canFindContextGroup() = compositionTest {
        val table = mutableSetOf<CompositionData>()
        compose {
            CompositionLocalProvider(LocalInspectionTables provides table) {
                TestSubcomposition { Text("Some value") }
            }
        }

        val data = table.first()
        val instance = data.findCompositionInstance()
        assertNotNull(instance)
        val contextGroup = instance.findContextGroup()
        assertNotNull(contextGroup)
    }

    @Test
    fun canFindParentInstance() = compositionTest {
        val table = mutableSetOf<CompositionData>()
        compose {
            CompositionLocalProvider(LocalInspectionTables provides table) {
                TestSubcomposition {
                    TestSubcomposition { TestSubcomposition { Text("Some value") } }
                }
            }
        }

        assertEquals(3, table.size)

        // Find the root (which will not be in the table)
        fun findRootOf(data: CompositionData): CompositionData {
            val parentData = data.findCompositionInstance()?.parent?.data
            return if (parentData == null) data else findRootOf(parentData)
        }

        val root = findRootOf(table.first())

        // Verify that the instance and its parents (not the root) are in the table
        fun verify(data: CompositionData) {
            if (data != root) {
                assertTrue(data in table)
                data.findCompositionInstance()?.parent?.let { verify(it.data) }
            }
        }

        for (instance in table) {
            assertEquals(root, findRootOf(instance))
            verify(instance)
        }
    }

    @Test
    fun canFindParentNotInFirstPosition() = compositionTest {
        val table = mutableSetOf<CompositionData>()
        compose {
            CompositionLocalProvider(LocalInspectionTables provides table) {
                Text("Some value")
                Text("Some value")
                Text("Some value")
                TestSubcomposition { Text("Some value") }
            }
        }
        val instance = table.first().findCompositionInstance()
        assertNotNull(instance)
        val contextGroup = instance.findContextGroup()
        assertNotNull(contextGroup)
    }

    @Test
    fun contextGroupIsInParent() = compositionTest {
        val table = mutableSetOf<CompositionData>()
        compose {
            CompositionLocalProvider(LocalInspectionTables provides table) {
                TestSubcomposition { Text("Some value") }
            }
        }
        val instance = table.first().findCompositionInstance()
        assertNotNull(instance)
        val contextGroup = instance.findContextGroup()
        assertNotNull(contextGroup)
        val parentData = instance.parent?.data
        assertNotNull(parentData)
        val identity = contextGroup.identity
        assertNotNull(identity)
        val foundGroup = parentData.find(identity)
        assertNotNull(foundGroup)
        assertEquals(identity, foundGroup.identity)

        fun identityMap(data: CompositionData): Map<Any, CompositionGroup> {
            val result = mutableMapOf<Any, CompositionGroup>()
            fun addToMap(group: CompositionGroup) {
                val groupIdentity = group.identity
                if (groupIdentity != null) result[groupIdentity] = group
                group.compositionGroups.forEach(::addToMap)
            }
            data.compositionGroups.forEach(::addToMap)
            return result
        }

        val map = identityMap(parentData)
        val mapFoundGroup = map[contextGroup.identity]
        assertNotNull(mapFoundGroup)
    }
}

@Composable
internal fun TestSubcomposition(content: @Composable () -> Unit) {
    val parentRef = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    DisposableEffect(parentRef) {
        val subComposeRoot = View().apply { name = "subComposeRoot" }
        val subcomposition = Composition(ViewApplier(subComposeRoot), parentRef)
        // TODO: work around for b/179701728
        callSetContent(subcomposition) {
            // Note: This is in a lambda invocation to keep the currentContent state read
            // in the sub-composition's content composable. Changing this to be
            // subcomposition.setContent(currentContent) would snapshot read only on initial set.
            currentContent()
        }
        onDispose { subcomposition.dispose() }
    }
}

private fun callSetContent(composition: Composition, content: @Composable () -> Unit) {
    composition.setContent(content)
}
