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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureScreenshot
import androidx.compose.ui.test.utils.countPixels
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.UIColor
import platform.UIKit.UIView

class InteropRenderingTest {
    @Test
    fun testInteropUpdateIsRenderedWithoutComposeInvalidation() =
        runUIKitInstrumentedTestWithInterop { overlay ->
            val view = UIView()
            val backgroundColor = mutableStateOf(UIColor.redColor)

            setContent {
                UIKitView(
                    factory = { view },
                    modifier = Modifier.fillMaxSize(),
                    properties = UIKitInteropProperties(placedAsOverlay = overlay),
                    update = { it.backgroundColor = backgroundColor.value }
                )
            }

            waitUntil("Initial UIKitView update was not applied") {
                view.backgroundColor == UIColor.redColor
            }

            backgroundColor.value = UIColor.blueColor

            waitUntil("UIKitView update was not applied without a Compose draw invalidation") {
                view.backgroundColor == UIColor.blueColor
            }

            assertEquals(
                expected = 100,
                actual = captureScreenshot()!!.countPixels(
                    color = Color.Blue,
                    step = 4,
                    maxCount = 100,
                ),
                message = "Updated UIKitView was not visible in the rendered hierarchy"
            )
        }

    @Test
    fun testInteropUpdateDoesNotTriggerRecomposition() = runUIKitInstrumentedTestWithInterop { overlay ->
        val view = UIView()
        val backgroundColor = mutableStateOf(UIColor.redColor)
        var recompositions = 0

        setContent {
            SideEffect { recompositions++ }

            UIKitView(
                factory = { view },
                modifier = Modifier.fillMaxSize(),
                properties = UIKitInteropProperties(placedAsOverlay = overlay),
                update = { it.backgroundColor = backgroundColor.value }
            )
        }

        waitUntil("Initial UIKitView update was not applied") {
            view.backgroundColor == UIColor.redColor
        }
        waitForIdle()
        val recompositionsBeforeUpdate = recompositions

        backgroundColor.value = UIColor.blueColor

        waitUntil("UIKitView update was not applied") {
            view.backgroundColor == UIColor.blueColor
        }

        assertEquals(
            expected = recompositionsBeforeUpdate,
            actual = recompositions,
            message = "Interop-only state update triggered recomposition"
        )
    }
}
