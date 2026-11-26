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

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.clearSkikoComposeImplementation
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class SkikoPathSvgTest {

    @BeforeTest
    fun setup() {
        registerSkikoComposeImplementation()
    }

    @AfterTest
    fun tearDown() {
        clearSkikoComposeImplementation()
    }

    private val center = Offset(100f, 100f)
    private val radius = 50f

    // On Skia, a circle is made of conics.
    // Lazy, because a Path can only be created after registerSkikoComposeImplementation
    private val circle by lazy {
        Path().apply {
            addOval(Rect(center = center, radius = radius))
        }
    }

    private fun pathFromSvg(pathData: String) = Path().apply { addSvg(pathData) }

    private fun Path.segmentTypes() =
        iterator(PathIterator.ConicEvaluation.AsConic).asSequence().map { it.type }.toList()

    private fun String.commandCount(command: Char) = count { it == command }

    @Test
    fun circleToSvgContainsTheCircle() {
        // Used to return just "M150.0 100.0Z", because conics were dropped
        val svg = circle.toSvg()

        // Consecutive quadratics share a single "Q" command
        assertEquals(1, svg.commandCount('Q'), svg)
        assertEquals(1, svg.commandCount('M'), svg)
        assertEquals(1, svg.commandCount('Z'), svg)
    }

    @Test
    fun circleSvgRoundTripsToQuadratics() {
        val parsed = pathFromSvg(circle.toSvg())
        val types = parsed.segmentTypes()

        assertEquals(PathSegment.Type.Move, types.first())
        assertEquals(PathSegment.Type.Close, types.last())
        val quadratics = types.count { it == PathSegment.Type.Quadratic }
        assertTrue(quadratics > 4, "Only $quadratics quadratics")
        assertEquals(types.size - 2, quadratics, "Unexpected segments: $types")
    }

    @Test
    fun circleSvgRoundTripsToTheSameShape() {
        val parsed = pathFromSvg(circle.toSvg())

        // The default tolerance of Path.iterator() is 0.25
        val tolerance = 0.25f
        val quadratics = parsed.iterator(PathIterator.ConicEvaluation.AsConic)
            .asSequence()
            .filter { it.type == PathSegment.Type.Quadratic }
            .toList()
        for (quadratic in quadratics) {
            val p = quadratic.points
            for (i in 0..10) {
                val t = i / 10f
                val x = (1 - t) * (1 - t) * p[0] + 2 * t * (1 - t) * p[2] + t * t * p[4]
                val y = (1 - t) * (1 - t) * p[1] + 2 * t * (1 - t) * p[3] + t * t * p[5]
                val distance = abs(hypot(x - center.x, y - center.y) - radius)
                assertTrue(distance <= tolerance, "($x, $y) is $distance away from the circle")
            }
        }

        val bounds = parsed.getBounds()
        val expectedBounds = circle.getBounds()
        assertEquals(expectedBounds.left, bounds.left, absoluteTolerance = tolerance)
        assertEquals(expectedBounds.top, bounds.top, absoluteTolerance = tolerance)
        assertEquals(expectedBounds.right, bounds.right, absoluteTolerance = tolerance)
        assertEquals(expectedBounds.bottom, bounds.bottom, absoluteTolerance = tolerance)
    }

    @Test
    fun roundRectToSvgContainsTheCorners() {
        val roundRect = Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, 200f, 100f), CornerRadius(20f)))
        }
        val types = pathFromSvg(roundRect.toSvg()).segmentTypes()

        // The last side may be drawn by the close
        assertTrue(types.count { it == PathSegment.Type.Line } >= 3, "Segments: $types")
        // At least one quadratic for each corner
        assertTrue(
            types.count { it == PathSegment.Type.Quadratic } >= 4,
            "Segments: $types"
        )
    }

    @Test
    fun circleToSvgDocumentContainsTheCircle() {
        val svg = circle.toSvg(asDocument = true)

        val pathData = svg.substringAfter("d=\"").substringBefore("\"")
        assertEquals(circle.toSvg(), pathData)
        assertTrue('Q' in pathData, pathData)
    }
}
