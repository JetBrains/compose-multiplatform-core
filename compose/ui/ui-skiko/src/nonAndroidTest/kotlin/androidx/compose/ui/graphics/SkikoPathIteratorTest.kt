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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.clearSkikoComposeImplementation
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class SkikoPathIteratorTest {

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

    private fun Path.segments(
        conicEvaluation: PathIterator.ConicEvaluation,
        tolerance: Float = 0.25f
    ) = iterator(conicEvaluation, tolerance).asSequence().toList()

    private fun Path.segmentTypesFromPointsArray(
        conicEvaluation: PathIterator.ConicEvaluation,
        tolerance: Float = 0.25f
    ): List<PathSegment.Type> {
        val iterator = iterator(conicEvaluation, tolerance)
        val points = FloatArray(8)
        return buildList {
            while (iterator.hasNext()) {
                add(iterator.next(points))
            }
        }
    }

    @Test
    fun conicsAreReturnedAsConics() {
        val segments = circle.segments(PathIterator.ConicEvaluation.AsConic)
        val conics = segments.filter { it.type == PathSegment.Type.Conic }

        assertEquals(4, conics.size)
        for (conic in conics) {
            assertEquals(6, conic.points.size)
            assertEquals(0.70710677f, conic.weight, absoluteTolerance = 0.0001f)
        }
        assertEquals(
            segments.map { it.type },
            circle.segmentTypesFromPointsArray(PathIterator.ConicEvaluation.AsConic)
        )
    }

    @Test
    fun conicsAreConvertedToQuadratics() {
        val segments = circle.segments(PathIterator.ConicEvaluation.AsQuadratics)

        assertTrue(segments.none { it.type == PathSegment.Type.Conic })
        val quadratics = segments.filter { it.type == PathSegment.Type.Quadratic }
        // More than one quadratic for each of the 4 conics
        assertTrue(quadratics.size > 4, "Only ${quadratics.size} quadratics")
        assertEquals(
            segments.map { it.type },
            circle.segmentTypesFromPointsArray(PathIterator.ConicEvaluation.AsQuadratics)
        )
    }

    @Test
    fun quadraticsAreConnected() {
        val quadratics = circle.segments(PathIterator.ConicEvaluation.AsQuadratics)
            .filter { it.type == PathSegment.Type.Quadratic }

        for ((previous, next) in quadratics.zipWithNext()) {
            assertEquals(previous.points[4], next.points[0])
            assertEquals(previous.points[5], next.points[1])
        }
        val first = quadratics.first()
        val last = quadratics.last()
        assertEquals(first.points[0], last.points[4], absoluteTolerance = 0.001f)
        assertEquals(first.points[1], last.points[5], absoluteTolerance = 0.001f)
    }

    @Test
    fun quadraticsApproximateTheConicsWithinTolerance() {
        val tolerance = 0.1f
        val quadratics = circle.segments(PathIterator.ConicEvaluation.AsQuadratics, tolerance)
            .filter { it.type == PathSegment.Type.Quadratic }

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
    }

    @Test
    fun smallerToleranceProducesMoreQuadratics() {
        val coarse = circle.segments(PathIterator.ConicEvaluation.AsQuadratics, 1f)
        val fine = circle.segments(PathIterator.ConicEvaluation.AsQuadratics, 0.01f)
        assertTrue(fine.size > coarse.size, "${fine.size} segments isn't more than ${coarse.size}")
    }

    @Test
    fun calculateSizeIncludesConvertedConics() {
        for (conicEvaluation in PathIterator.ConicEvaluation.entries) {
            val iterator = circle.iterator(conicEvaluation)
            val size = iterator.calculateSize()
            assertEquals(iterator.asSequence().count(), size, "$conicEvaluation")
        }
    }

    @Test
    fun calculateSizeWithoutConvertedConics() {
        val iterator = circle.iterator(PathIterator.ConicEvaluation.AsQuadratics)
        assertEquals(
            circle.iterator(PathIterator.ConicEvaluation.AsConic).asSequence().count(),
            iterator.calculateSize(includeConvertedConics = false)
        )
    }

    @Test
    fun hitTestingAnOval() {
        // Used to throw, because conics were returned as quadratics with a weight
        val hitTester = PathHitTester(circle)

        assertTrue(center in hitTester)
        // The corners of the bounds are outside the circle
        val bounds = circle.getBounds().deflate(1f)
        assertFalse(bounds.topLeft in hitTester)
        assertFalse(bounds.topRight in hitTester)
        assertFalse(bounds.bottomLeft in hitTester)
        assertFalse(bounds.bottomRight in hitTester)
    }
}
