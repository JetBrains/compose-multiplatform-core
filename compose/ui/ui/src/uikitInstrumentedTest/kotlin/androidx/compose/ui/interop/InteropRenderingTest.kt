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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureScreenshot
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.forEachSampledPixel
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.UIKit.UIColor
import platform.UIKit.UIView

class InteropRenderingTest {
    @Test
    fun testUIKitViewUpdateInPopupIsRenderedWithoutComposeDraw() = runUIKitInstrumentedTest {
        val view = UIView()
        val backgroundColor = mutableStateOf(UIColor.redColor)
        var popupDraws = 0

        setContent {
            Popup(properties = PopupProperties(focusable = true)) {
                Box(Modifier.fillMaxSize().drawBehind { popupDraws++ }) {
                    UIKitView(
                        factory = { view },
                        modifier = Modifier.fillMaxSize(),
                        update = { it.backgroundColor = backgroundColor.value }
                    )
                }
            }
        }

        waitUntil("Initial UIKitView update in popup was not applied") {
            view.backgroundColor == UIColor.redColor
        }

        waitForIdle()

        val popupDrawsBeforeUpdate = popupDraws

        backgroundColor.value = UIColor.blueColor

        waitUntil("UIKitView update in popup was not applied") {
            view.backgroundColor == UIColor.blueColor
        }

        assertEquals(
            expected = popupDrawsBeforeUpdate,
            actual = popupDraws,
            message = "UIKitView update in popup triggered a Compose draw"
        )
    }

    @Test
    fun testUIKitViewUpdateIsRenderedWithoutComposeDraw() =
        runUIKitInstrumentedTestWithInterop { overlay ->
            val view = UIView()
            val backgroundColor = mutableStateOf(UIColor.redColor)
            var composeDraws = 0

            setContent {
                Box(Modifier.fillMaxSize().drawBehind { composeDraws++ }) {
                    UIKitView(
                        factory = { view },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(placedAsOverlay = overlay),
                        update = { it.backgroundColor = backgroundColor.value }
                    )
                }
            }

            waitUntil("Initial UIKitView update was not applied") {
                view.backgroundColor == UIColor.redColor
            }

            waitForIdle()

            val composeDrawsBeforeUpdate = composeDraws

            backgroundColor.value = UIColor.blueColor

            waitUntil("UIKitView update was not applied") {
                view.backgroundColor == UIColor.blueColor
            }

            assertEquals(
                expected = composeDrawsBeforeUpdate,
                actual = composeDraws,
                message = "UIKitView update triggered a Compose draw"
            )

            var sampledPixels = 0
            var bluePixels = 0
            captureScreenshot()!!.forEachSampledPixel(maxSamples = 100) { _, _, color ->
                sampledPixels++
                if (color == Color.Blue) {
                    bluePixels++
                }
            }

            assertTrue(sampledPixels > 0, "Screenshot did not contain any pixels")
            assertEquals(
                expected = sampledPixels,
                actual = bluePixels,
                message = "Updated UIKitView was not visible across the rendered hierarchy"
            )
        }

    @Test
    fun testUIKitViewUpdateDoesNotTriggerRecomposition() = runUIKitInstrumentedTestWithInterop { overlay ->
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

    @Test
    fun testUIKitViewUpdatesApplyLatestValueOnce() =
        runUIKitInstrumentedTestWithInterop { overlay ->
            val view = UIView()
            val backgroundColor = mutableStateOf(UIColor.redColor)
            var updateCalls = 0

            setContent {
                UIKitView(
                    factory = { view },
                    modifier = Modifier.fillMaxSize(),
                    properties = UIKitInteropProperties(placedAsOverlay = overlay),
                    update = {
                        updateCalls++
                        it.backgroundColor = backgroundColor.value
                    }
                )
            }

            waitUntil("Initial UIKitView update was not applied") {
                view.backgroundColor == UIColor.redColor
            }
            waitForIdle()
            val updateCallsBeforeChange = updateCalls

            backgroundColor.value = UIColor.blueColor
            backgroundColor.value = UIColor.greenColor
            backgroundColor.value = UIColor.yellowColor

            waitUntil("Latest UIKitView update was not applied") {
                view.backgroundColor == UIColor.yellowColor
            }

            assertEquals(
                expected = updateCallsBeforeChange + 1,
                actual = updateCalls,
                message = "Pending UIKitView updates were not coalesced"
            )
        }

    @Test
    fun testUIKitViewUpdateLambdaChange() = runUIKitInstrumentedTestWithInterop { overlay ->
        val view = UIView()
        val useNewUpdate = mutableStateOf(false)
        val initialColor = mutableStateOf(UIColor.redColor)
        val newUpdateColor = mutableStateOf(UIColor.greenColor)
        var recompositions = 0

        setContent {
            SideEffect { recompositions++ }

            UIKitView(
                factory = { view },
                modifier = Modifier.fillMaxSize(),
                properties = UIKitInteropProperties(placedAsOverlay = overlay),
                update = if (useNewUpdate.value) {
                    { it.backgroundColor = newUpdateColor.value }
                } else {
                    { it.backgroundColor = initialColor.value }
                }
            )
        }

        waitUntil("Initial UIKitView update was not applied") {
            view.backgroundColor == UIColor.redColor
        }

        useNewUpdate.value = true

        waitUntil("New UIKitView update lambda was not applied") {
            view.backgroundColor == UIColor.greenColor
        }

        waitForIdle()

        val recompositionsBeforeObservedStateChange = recompositions

        newUpdateColor.value = UIColor.blueColor

        waitUntil("State read by new UIKitView update lambda was not observed") {
            view.backgroundColor == UIColor.blueColor
        }

        assertEquals(
            expected = recompositionsBeforeObservedStateChange,
            actual = recompositions,
            message = "State read only by UIKitView update triggered recomposition"
        )
    }

}
