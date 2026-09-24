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

package androidx.compose.ui.interop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.appEntity
import androidx.compose.ui.platform.AppEntityDescriptorStore
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.uikit.LocalUIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.UIKit.UIView

class AppEntityDiscoverabilityDiagnosticTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun appEntityModifierRegistersPlacedNodesAndRemovesDetachedNodes() = runUIKitInstrumentedTest {
        val view = UIView()
        var showEntity by mutableStateOf(true)

        setContent {
            CompositionLocalProvider(LocalUIView provides view) {
                if (showEntity) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .appEntity(typeName = "RecipeEntity", id = "miso-ramen")
                    )
                }
            }
        }
        waitForIdle()

        assertEquals(1, AppEntityDescriptorStore.descriptorsForTest(view).size)
        assertTrue(AppEntityDescriptorStore.isAssociatedWithStoreForTest(view))

        showEntity = false
        waitForIdle()

        assertTrue(AppEntityDescriptorStore.descriptorsForTest(view).isEmpty())
        assertFalse(AppEntityDescriptorStore.isAssociatedWithStoreForTest(view))
    }
}
