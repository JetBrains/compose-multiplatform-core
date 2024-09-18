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

package androidx.compose.ui.graphics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalTestApi
class GraphicsLayerTest {

    @Test // Bug: https://youtrack.jetbrains.com/issue/CMP-6660
    fun recordInvalidation() = runSkikoComposeUiTest {
        var backgroundCompositions = 0
        var foregroundCompositions = 0
        setContent {
            val graphicsContext = LocalGraphicsContext.current
            val graphicsLayer = remember { graphicsContext.createGraphicsLayer() }
            Box(Modifier.size(100.dp).drawWithContent {
                backgroundCompositions++
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
            })
            Box(Modifier.size(50.dp).drawBehind {
                foregroundCompositions++
                drawLayer(graphicsLayer)
            })
            DisposableEffect(graphicsLayer) {
                onDispose { graphicsContext.releaseGraphicsLayer(graphicsLayer) }
            }
        }
        waitForIdle()

        // UnconfinedTestDispatcher makes some difference here: instead of possible infinite
        // invalidation cycle, it resolves invalidations during the same render.
        // But even in this case, with incorrect behaviour composition counters won't be equal 1

        assertEquals(1, backgroundCompositions)
        assertEquals(1, foregroundCompositions)
    }
}
