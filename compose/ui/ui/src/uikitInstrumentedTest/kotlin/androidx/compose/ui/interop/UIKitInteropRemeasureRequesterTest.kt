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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpSize
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropRemeasureRequester
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.measureFittingSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UILabel
import platform.UIKit.UIView

class UIKitInteropRemeasureRequesterTest {

    @Test
    @OptIn(ExperimentalForeignApi::class)
    fun testInternalConstraintChangeTriggersRemeasure() = runUIKitInstrumentedTestWithInterop { overlay ->
        var composeSize = DpSize.Zero
        val boxSize = DpSize(300.dp, 300.dp)
        val measureRequester = UIKitInteropRemeasureRequester()

        // A view whose intrinsic size depends on an internal width/height constraint.
        class BoxUIView : UIView(frame = CGRectZero.readValue()) {
            val w = widthAnchor.constraintEqualToConstant(50.0)
            val h = heightAnchor.constraintEqualToConstant(40.0)
            init {
                translatesAutoresizingMaskIntoConstraints = false
                backgroundColor = UIColor.blueColor
                NSLayoutConstraint.activateConstraints(listOf(w, h))
            }
        }

        val v = BoxUIView()

        setContent {
            Box(Modifier
                .background(Color.Red)
                .size(boxSize), contentAlignment = Alignment.Center
            ) {
                UIKitView(
                    factory = { v },
                    properties = UIKitInteropProperties(
                        placedAsOverlay = overlay,
                        remeasureRequester = measureRequester
                    ),
                    modifier = Modifier
                        .background(Color.Green)
                        .onGloballyPositioned {
                            composeSize = it.boundsInRoot().size.toDpSize(density)
                        }
                )
            }
        }

        assertEquals(DpSize(50.dp, 40.dp), composeSize)

        v.w.constant = 120.0
        v.h.constant = 90.0
        measureRequester.requestRemeasure()

        waitUntil {
            composeSize ==  DpSize(120.dp, 90.dp)
        }
    }

    @Test
    fun testTextChangeTriggersRemeasure() = runUIKitInstrumentedTestWithInterop { overlay ->
        var composeSize = DpSize.Zero
        val boxSize = DpSize(300.dp, 300.dp)
        val fixedWidth = 120.dp
        val measureRequester = UIKitInteropRemeasureRequester()
        val initialText = "TEXT"
        val changedText = "TEXT 2"

        fun factory(initialText: String): UILabel {
            return UILabel().apply {
                text = initialText
                numberOfLines = 0
                preferredMaxLayoutWidth = fixedWidth.value.toDouble()
                backgroundColor = UIColor.blueColor
            }
        }

        val label = factory(initialText)

        setContent {
            Box(Modifier
                .background(Color.Red)
                .size(boxSize),
                contentAlignment = Alignment.Center
            ) {
                UIKitView(
                    factory = { label },
                    properties = UIKitInteropProperties(
                        placedAsOverlay = overlay,
                        remeasureRequester = measureRequester
                    ),
                    modifier = Modifier
                        .background(Color.Green)
                        .width(fixedWidth)
                        .onGloballyPositioned {
                            composeSize = it.boundsInRoot().size.toDpSize(density)
                        }
                )
            }
        }

        val expectedShort = factory(initialText)
            .also { it.translatesAutoresizingMaskIntoConstraints = false }
            .measureFittingSize(
                fixedWidth = fixedWidth,
                maxWidth = boxSize.width,
                maxHeight = boxSize.height,
            )

        assertEquals(expectedShort, composeSize)

        label.text = changedText

        waitForIdle()

        // size did not change
        assertEquals(expectedShort, composeSize)

        measureRequester.requestRemeasure()

        waitForIdle()

        val expectedLong = factory(changedText)
            .also { it.translatesAutoresizingMaskIntoConstraints = false }
            .measureFittingSize(
                fixedWidth = fixedWidth,
                maxWidth = boxSize.width,
                maxHeight = boxSize.height
            )

        waitUntil {
            composeSize == expectedLong
        }
    }

    @Test
    fun testMeasureRequesterNotInvalidated() = runUIKitInstrumentedTestWithInterop { overlay ->
        val measureRequester = UIKitInteropRemeasureRequester()

        setContent {
            UIKitView(
                factory = { UILabel() },
                properties = UIKitInteropProperties(
                    placedAsOverlay = overlay,
                    remeasureRequester = measureRequester
                )
            )
        }

        assertTrue(measureRequester.isBound())
    }

    @Test
    fun testMeasureRequesterInvalidatedAfterInteropRemoved() = runUIKitInstrumentedTestWithInterop { overlay ->
        val measureRequester = UIKitInteropRemeasureRequester()
        val showUIKitView = mutableStateOf(true)

        setContent {
            if (showUIKitView.value) {
                UIKitView(
                    factory = { UILabel() },
                    properties = UIKitInteropProperties(
                        placedAsOverlay = overlay,
                        remeasureRequester = measureRequester
                    )
                )
            } else {
                Box(Modifier.size(100.dp))
            }
        }

        assertTrue(measureRequester.isBound())

        showUIKitView.value = false
        waitForIdle()

        assertFalse(measureRequester.isBound())
    }

    @Test
    fun testMeasureRequesterNotInvalidatedAfterInteropAdded() = runUIKitInstrumentedTestWithInterop { overlay ->
        val measureRequester = UIKitInteropRemeasureRequester()
        val showUIKitView = mutableStateOf(false)

        setContent {
            if (showUIKitView.value) {
                UIKitView(
                    factory = { UILabel() },
                    properties = UIKitInteropProperties(
                        placedAsOverlay = overlay,
                        remeasureRequester = measureRequester
                    )
                )
            } else {
                Box(Modifier.size(100.dp))
            }
        }

        assertFalse(measureRequester.isBound())

        showUIKitView.value = true
        waitForIdle()

        assertTrue(measureRequester.isBound())
    }
}

private fun UIKitInteropRemeasureRequester.isBound() = requestImpl != null