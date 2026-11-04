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

package androidx.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.runUIKitInstrumentedTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import platform.UIKit.UIView

class ComposeContainerViewTest {
    @Test
    fun testDrawDoesNotAllocateLayerBackingStore() = runUIKitInstrumentedTest {
        var frame by mutableIntStateOf(0)
        var drawnFrame = -1

        setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawnFrame = frame
                        drawRect(if (frame % 2 == 0) Color.Red else Color.Blue)
                    }
            )
        }

        repeat(3) {
            frame++
            waitUntil("Compose draws frame $frame") { drawnFrame == frame }
        }

        val containerView = assertNotNull(viewController.view.findComposeContainerView())
        // Core Animation sets the layer contents when it draws into a backing store for -drawRect:.
        assertNull(containerView.layer.contents)
    }
}

private fun UIView.findComposeContainerView(): ComposeContainerView? =
    this as? ComposeContainerView
        ?: subviews.firstNotNullOfOrNull { (it as UIView).findComposeContainerView() }
