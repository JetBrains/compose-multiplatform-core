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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalTestApi
class GraphicsLayerTest {

    // TODO: Add test timeout once available
    @Test // Bug: https://youtrack.jetbrains.com/issue/CMP-6660
    fun layerDrawingWithRecording() = runSkikoComposeUiTest {
        var drawCount = 0
        setContent {
            val graphicsLayer = rememberGraphicsLayer()
            Box(Modifier.size(100.dp).drawWithContent {
                drawCount++
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            })
        }
        waitForIdle()

        // UnconfinedTestDispatcher might make some difference here: instead of possible infinite
        // invalidation cycle, it resolves invalidations during the same render.
        // But even in this case, with incorrect behaviour composition counters won't be equal 1

        assertEquals(1, drawCount)
    }
}
