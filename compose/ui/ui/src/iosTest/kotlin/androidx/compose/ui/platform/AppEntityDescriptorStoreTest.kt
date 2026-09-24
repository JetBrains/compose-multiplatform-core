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

package androidx.compose.ui.platform

import androidx.compose.ui.AppEntityReference
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.uikit.utils.CMPAppEntityDescriptor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.UIKit.UIView

class AppEntityDescriptorStoreTest {
    @Test
    fun attachingAnotherNodeAddsItsDescriptor() {
        val view = UIView()
        val store = AppEntityDescriptorStore.forView(view)
        val firstNode = placedNode(id = "miso-ramen")
        val secondNode = placedNode(id = "lemon-orzo")

        store.onAttach(firstNode)
        assertEquals(listOf("miso-ramen"), descriptorIds(view))

        store.onAttach(secondNode)
        assertEquals(listOf("miso-ramen", "lemon-orzo"), descriptorIds(view))

        store.onDetach(firstNode)
        store.onDetach(secondNode)
    }

    @Test
    fun detachingOneOfTwoNodesKeepsTheOtherDescriptor() {
        val view = UIView()
        val store = AppEntityDescriptorStore.forView(view)
        val firstNode = placedNode(id = "miso-ramen")
        val secondNode = placedNode(id = "lemon-orzo")

        store.onAttach(firstNode)
        store.onAttach(secondNode)
        store.onDetach(firstNode)

        assertEquals(listOf("lemon-orzo"), descriptorIds(view))

        store.onDetach(secondNode)
    }

    @Test
    fun descriptorsAreReadOnDemandFromCurrentBounds() {
        val view = UIView()
        val store = AppEntityDescriptorStore.forView(view)
        var isPlaced = false
        var bounds = Rect(left = 8f, top = 12f, right = 168f, bottom = 84f)

        val node = TestAppEntityNode(
            entity = AppEntityReference(typeName = "RecipeEntity", id = "miso-ramen", selected = false),
            isPlacedProvider = { isPlaced },
            boundsProvider = { bounds },
        )
        store.onAttach(node)

        assertTrue(AppEntityDescriptorStore.descriptorsForTest(view).isEmpty())

        isPlaced = true
        assertDescriptor(
            AppEntityDescriptorStore.descriptorsForTest(view).single(),
            typeName = "RecipeEntity",
            rawIdentifier = "miso-ramen",
            y = 12.0,
            selected = false,
        )

        bounds = Rect(left = 8f, top = 52f, right = 168f, bottom = 124f)
        assertEquals(
            52.0,
            AppEntityDescriptorStore.descriptorsForTest(view).single().bounds().useContents { origin.y },
        )

        node.entity = AppEntityReference(typeName = "RecipeEntity", id = "miso-ramen", selected = true)
        assertDescriptor(
            AppEntityDescriptorStore.descriptorsForTest(view).single(),
            typeName = "RecipeEntity",
            rawIdentifier = "miso-ramen",
            y = 52.0,
            selected = true,
        )

        node.entity = AppEntityReference(typeName = "RecipeEntity", id = "lemon-orzo", selected = true)
        assertDescriptor(
            AppEntityDescriptorStore.descriptorsForTest(view).single(),
            typeName = "RecipeEntity",
            rawIdentifier = "lemon-orzo",
            y = 52.0,
            selected = true,
        )

        store.onDetach(node)
        assertTrue(AppEntityDescriptorStore.descriptorsForTest(view).isEmpty())
    }

    private fun placedNode(id: String) = TestAppEntityNode(
        entity = AppEntityReference(typeName = "RecipeEntity", id = id, selected = false),
        isPlacedProvider = { true },
        boundsProvider = { Rect(left = 8f, top = 12f, right = 168f, bottom = 84f) },
    )

    private fun descriptorIds(view: UIView): List<String> =
        AppEntityDescriptorStore.descriptorsForTest(view).map { it.rawIdentifier() }

    private class TestAppEntityNode(
        override var entity: AppEntityReference,
        private val isPlacedProvider: () -> Boolean,
        private val boundsProvider: () -> Rect,
    ) : AppEntityDescriptorNode {
        override fun boundsInRootOrNull(): Rect? = boundsProvider().takeIf { isPlacedProvider() }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun assertDescriptor(
        descriptor: CMPAppEntityDescriptor,
        typeName: String,
        rawIdentifier: String,
        y: Double,
        selected: Boolean,
    ) {
        val bounds = descriptor.bounds()
        assertEquals(typeName, descriptor.typeName())
        assertEquals(rawIdentifier, descriptor.rawIdentifier())
        assertEquals(8.0, bounds.useContents { origin.x })
        assertEquals(y, bounds.useContents { origin.y })
        assertEquals(160.0, bounds.useContents { size.width })
        assertEquals(72.0, bounds.useContents { size.height })
        assertEquals(selected, descriptor.selected())
    }
}
