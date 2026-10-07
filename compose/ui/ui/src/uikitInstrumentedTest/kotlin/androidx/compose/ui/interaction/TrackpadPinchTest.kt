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

package androidx.compose.ui.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toDpOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TrackpadPinchTest {

    @Test
    fun testTransformableReceivesTrackpadPinchIn() = runUIKitInstrumentedTest {
        var totalZoom = 1f
        var transformEventCount = 0
        var lastCentroid: Offset = Offset.Unspecified

        setContent {
            val state = rememberTransformableState { centroid, zoomChange, _, _ ->
                totalZoom *= zoomChange
                transformEventCount++
                lastCentroid = centroid
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .transformable(state)
            )
        }

        val anchor = screenSize.center
        trackpadPinch(anchor, finalScale = 2f)
        waitForIdle()

        assertTrue(
            transformEventCount >= 1,
            "Expected at least one transformation event, received $transformEventCount"
        )
        assertEquals(2f, totalZoom, absoluteTolerance = 0.05f)
        assertCentroidMatchesAnchor(lastCentroid, anchor)
    }

    @Test
    fun testTransformableReceivesTrackpadPinchOut() = runUIKitInstrumentedTest {
        var totalZoom = 1f
        var transformEventCount = 0
        var lastCentroid: Offset = Offset.Unspecified

        setContent {
            val state = rememberTransformableState { centroid, zoomChange, _, _ ->
                totalZoom *= zoomChange
                transformEventCount++
                lastCentroid = centroid
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .transformable(state)
            )
        }

        val anchor = screenSize.center
        trackpadPinch(anchor, finalScale = 0.5f)
        waitForIdle()

        assertTrue(
            transformEventCount >= 1,
            "Expected at least one transformation event, received $transformEventCount"
        )
        assertEquals(0.5f, totalZoom, absoluteTolerance = 0.05f)
        assertCentroidMatchesAnchor(lastCentroid, anchor)
    }

    @Test
    fun testTransformableReceivesMultipleTrackpadPinches() = runUIKitInstrumentedTest {
        var totalZoom = 1f
        var transformEventCount = 0
        var lastCentroid: Offset = Offset.Unspecified

        setContent {
            val state = rememberTransformableState { centroid, zoomChange, _, _ ->
                totalZoom *= zoomChange
                transformEventCount++
                lastCentroid = centroid
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .transformable(state)
            )
        }

        val anchor = screenSize.center
        val finalScales = listOf(2f, 0.5f, 1.5f)
        for (finalScale in finalScales) {
            trackpadPinch(anchor, finalScale)
        }
        waitForIdle()

        val expectedTotal = finalScales.fold(1f) { acc, s -> acc * s }
        assertTrue(
            transformEventCount >= finalScales.size,
            "Expected at least ${finalScales.size} transformation events, received $transformEventCount"
        )
        assertTrue(
            abs(totalZoom - expectedTotal) < 0.05f,
            "Expected accumulated zoom ≈ $expectedTotal, got $totalZoom"
        )
        assertCentroidMatchesAnchor(lastCentroid, anchor)
    }
}

private fun UIKitInstrumentedTest.assertCentroidMatchesAnchor(
    centroid: Offset,
    anchor: DpOffset,
) {
    assertTrue(centroid.isSpecified, "Transform centroid was never reported")
    val centroidDp = centroid.toDpOffset(density)
    assertEquals(anchor.x.value, centroidDp.x.value, absoluteTolerance = 1f)
    assertEquals(anchor.y.value, centroidDp.y.value, absoluteTolerance = 1f)
}
