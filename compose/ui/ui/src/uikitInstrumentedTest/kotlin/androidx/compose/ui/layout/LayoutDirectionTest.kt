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

package androidx.compose.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.DpRectZero
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpRect
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.UITraitEnvironmentLayoutDirectionLeftToRight
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft

internal class LayoutDirectionInHostingViewTest : LayoutDirectionTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class LayoutDirectionInHostingViewControllerTest : LayoutDirectionTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class LayoutDirectionTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun testLayoutChangesFromLtrToRtl() = runUIKitInstrumentedTest {
        val markerSize = DpSize(80.dp, 50.dp)
        var markerRect = DpRectZero()

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight) {
            TestContent(
                alignment = Alignment.TopStart,
                markerSize = markerSize,
                onMarkerRectChanged = { markerRect = it }
            )
        }

        val expectedLtrRect = DpRect(
            origin = DpOffset.Zero,
            size = markerSize
        )
        waitUntil("Marker should be laid out for LTR") {
            markerRect == expectedLtrRect
        }
        assertEquals(expectedLtrRect, markerRect)

        setLayoutDirection(UITraitEnvironmentLayoutDirectionRightToLeft)

        val expectedRtlRect = DpRect(
            left = screenSize.width - markerSize.width,
            top = 0.dp,
            right = screenSize.width,
            bottom = markerSize.height
        )
        waitUntil("Marker should move to the right for RTL") {
            markerRect == expectedRtlRect
        }
        assertEquals(expectedRtlRect, markerRect)
    }

    @Test
    fun testLayoutChangesFromRtlToLtr() = runUIKitInstrumentedTest {
        val markerSize = DpSize(80.dp, 50.dp)
        var markerRect = DpRectZero()

        setContent(layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft) {
            TestContent(
                alignment = Alignment.TopStart,
                markerSize = markerSize,
                onMarkerRectChanged = { markerRect = it }
            )
        }

        val expectedRtlRect = DpRect(
            left = screenSize.width - markerSize.width,
            top = 0.dp,
            right = screenSize.width,
            bottom = markerSize.height
        )
        waitUntil("Marker should be laid out for RTL") {
            markerRect == expectedRtlRect
        }
        assertEquals(expectedRtlRect, markerRect)

        setLayoutDirection(UITraitEnvironmentLayoutDirectionLeftToRight)

        val expectedLtrRect = DpRect(
            origin = DpOffset.Zero,
            size = markerSize
        )
        waitUntil("Marker should move to the left for LTR") {
            markerRect == expectedLtrRect
        }
        assertEquals(expectedLtrRect, markerRect)
    }
}

@Composable
private fun TestContent(
    alignment: Alignment = Alignment.TopStart,
    markerSize: DpSize,
    onMarkerRectChanged: (DpRect) -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .align(alignment)
                .size(markerSize)
                .background(Color.Red)
                .onGloballyPositioned {
                    onMarkerRectChanged(it.boundsInWindow().toDpRect(density))
                }
        )
    }
}