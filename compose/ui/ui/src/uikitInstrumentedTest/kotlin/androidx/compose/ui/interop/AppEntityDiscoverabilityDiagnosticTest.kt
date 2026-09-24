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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.appEntity
import androidx.compose.ui.node.WeakReference
import androidx.compose.ui.platform.AppEntityDescriptorStore
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.uikit.LocalUIView
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.UIKit.UIView

class AppEntityDiscoverabilityDiagnosticTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun appEntityModifierRegistersPlacedNodesAndRemovesDetachedNodes() = runUIKitInstrumentedTest {
        val view = UIView()

        setContent {
            CompositionLocalProvider(LocalUIView provides view) {
                Box(
                    Modifier
                        .size(80.dp)
                        .appEntity(typeName = "RecipeEntity", id = "miso-ramen")
                )
            }
        }
        waitForIdle()

        assertEquals(1, AppEntityDescriptorStore.descriptorsForTest(view).size)

        setContent {}
        waitForIdle()

        assertTrue(AppEntityDescriptorStore.descriptorsForTest(view).isEmpty())
    }

    @OptIn(ExperimentalComposeUiApi::class, NativeRuntimeApi::class)
    @Test
    fun appEntityModifierDoesNotRetainViewAfterLastNodeIsRemoved() = runUIKitInstrumentedTest {
        lateinit var viewReference: WeakReference<UIView>

        run {
            val view = UIView()
            viewReference = WeakReference(view)

            setContent {
                CompositionLocalProvider(LocalUIView provides view) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .appEntity(typeName = "RecipeEntity", id = "miso-ramen")
                    )
                }
            }
            waitForIdle()

            setContent {}
            waitForIdle()

            assertTrue(AppEntityDescriptorStore.descriptorsForTest(view).isEmpty())
        }

        waitUntil("App Entity provider retained its UIView after the last node was removed") {
            GC.collect()
            viewReference.get() == null
        }
    }
}
