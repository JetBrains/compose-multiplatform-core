@file:OptIn(InternalComposeUiApi::class)

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

import androidx.compose.ui.InternalComposeUiApi
import kotlin.math.sqrt
import org.jetbrains.skia.Path as SkPath
import org.jetbrains.skia.PathSegment as SkPathSegment
import org.jetbrains.skia.PathVerb
import org.jetbrains.skia.Point

// Skiko's Path.convertConicToQuads() accepts a "power of 2" number of quadratics, but our APIs
// expose a tolerance/error value. This code computes that "power of 2" number (from 0 to 5) given
// a specified tolerance value.

private const val MaxConicToQuadraticSubdivisions = 5

private fun Point.isFinite() = x.isFinite() && y.isFinite()

private fun toleranceToSubdivisions(
    p0: Point,
    p1: Point,
    p2: Point,
    weight: Float,
    tolerance: Float
): Int {
    if (
        tolerance <= 0.0f ||
        !tolerance.isFinite() ||
        !p0.isFinite() ||
        !p1.isFinite() ||
        !p2.isFinite()
    ) {
        return 0
    }

    val a = weight - 1.0f
    val k = a / (4.0f * (2.0f + a))
    val x = k * (p0.x - 2.0f * p1.x + p2.x)
    val y = k * (p0.y - 2.0f * p1.y + p2.y)

    var error = sqrt(x * x + y * y)
    var subdivisions = 0
    while (subdivisions < MaxConicToQuadraticSubdivisions) {
        if (error <= tolerance) break
        error *= 0.25f
        subdivisions++
    }
    return subdivisions
}

internal class SkiaPathIterator(
    override val path: Path,
    override val conicEvaluation: PathIterator.ConicEvaluation,
    override val tolerance: Float
) : PathIterator {
    private val skiaPath = path.materializeSkiaPath()
    private val iterator = skiaPath.iterator()

    // The points of the quadratics the last conic was converted to: the start point, followed by
    // the control and end points of each quadratic
    private var quadraticPoints: Array<Point> = emptyArray()
    // The index of the next quadratic in quadraticPoints to return
    private var nextQuadraticIndex = 0

    private val hasPendingQuadratics: Boolean
        get() = nextQuadraticIndex < quadraticPoints.quadraticCount

    override fun calculateSize(includeConvertedConics: Boolean): Int {
        var size = skiaPath.verbsCount
        if (includeConvertedConics && conicEvaluation == PathIterator.ConicEvaluation.AsQuadratics) {
            val sizeIterator = skiaPath.iterator()
            while (sizeIterator.hasNext()) {
                val segment = sizeIterator.next() ?: break
                if (segment.verb == PathVerb.CONIC) {
                    // The conic is replaced by its quadratics
                    size += convertConicToQuadratics(segment).quadraticCount - 1
                }
            }
        }
        return size
    }

    override fun hasNext(): Boolean = hasPendingQuadratics || iterator.hasNext()

    override fun next(outPoints: FloatArray, offset: Int): PathSegment.Type {
        check(outPoints.size - offset >= 8) { "The points array must contain at least 8 floats" }

        if (hasPendingQuadratics) return nextQuadratic(outPoints, offset)

        if (!hasNext()) return PathSegment.Type.Done

        val segment = iterator.next()
        requireNotNull(segment)

        return when (segment.verb) {
            PathVerb.MOVE -> {
                outPoints[offset] = segment.p0!!.x
                outPoints[offset + 1] = segment.p0!!.y
                PathSegment.Type.Move
            }
            PathVerb.LINE -> {
                outPoints[offset] = segment.p0!!.x
                outPoints[offset + 1] = segment.p0!!.y
                outPoints[offset + 2] = segment.p1!!.x
                outPoints[offset + 3] = segment.p1!!.y
                PathSegment.Type.Line
            }
            PathVerb.QUAD -> {
                outPoints[offset] = segment.p0!!.x
                outPoints[offset + 1] = segment.p0!!.y
                outPoints[offset + 2] = segment.p1!!.x
                outPoints[offset + 3] = segment.p1!!.y
                outPoints[offset + 4] = segment.p2!!.x
                outPoints[offset + 5] = segment.p2!!.y
                PathSegment.Type.Quadratic
            }
            PathVerb.CONIC -> {
                if (conicEvaluation == PathIterator.ConicEvaluation.AsQuadratics) {
                    quadraticPoints = convertConicToQuadratics(segment)
                    nextQuadraticIndex = 0
                    return nextQuadratic(outPoints, offset)
                }
                outPoints[offset] = segment.p0!!.x
                outPoints[offset + 1] = segment.p0!!.y
                outPoints[offset + 2] = segment.p1!!.x
                outPoints[offset + 3] = segment.p1!!.y
                outPoints[offset + 4] = segment.p2!!.x
                outPoints[offset + 5] = segment.p2!!.y
                outPoints[offset + 6] = segment.conicWeight
                outPoints[offset + 7] = segment.conicWeight
                PathSegment.Type.Conic
            }
            PathVerb.CUBIC -> {
                outPoints[offset] = segment.p0!!.x
                outPoints[offset + 1] = segment.p0!!.y
                outPoints[offset + 2] = segment.p1!!.x
                outPoints[offset + 3] = segment.p1!!.y
                outPoints[offset + 4] = segment.p2!!.x
                outPoints[offset + 5] = segment.p2!!.y
                outPoints[offset + 6] = segment.p3!!.x
                outPoints[offset + 7] = segment.p3!!.y
                PathSegment.Type.Cubic
            }
            PathVerb.CLOSE -> PathSegment.Type.Close
            PathVerb.DONE -> PathSegment.Type.Done
        }
    }

    override fun next(): PathSegment {
        val points = FloatArray(8)
        return when (val type = next(points, 0)) {
            PathSegment.Type.Move -> PathSegment(type, points.copyOf(2), 0.0f)
            PathSegment.Type.Line -> PathSegment(type, points.copyOf(4), 0.0f)
            PathSegment.Type.Quadratic -> PathSegment(type, points.copyOf(6), 0.0f)
            PathSegment.Type.Conic -> PathSegment(type, points.copyOf(6), points[6])
            PathSegment.Type.Cubic -> PathSegment(type, points, 0.0f)
            PathSegment.Type.Close -> CloseSegment
            PathSegment.Type.Done -> DoneSegment
        }
    }

    private fun convertConicToQuadratics(segment: SkPathSegment): Array<Point> {
        val p0 = segment.p0!!
        val p1 = segment.p1!!
        val p2 = segment.p2!!
        val weight = segment.conicWeight
        val subdivisions = toleranceToSubdivisions(p0, p1, p2, weight, tolerance)
        return SkPath.convertConicToQuads(p0, p1, p2, weight, subdivisions)
    }

    private fun nextQuadratic(outPoints: FloatArray, offset: Int): PathSegment.Type {
        val index = 2 * nextQuadraticIndex
        val p0 = quadraticPoints[index]
        val p1 = quadraticPoints[index + 1]
        val p2 = quadraticPoints[index + 2]
        outPoints[offset] = p0.x
        outPoints[offset + 1] = p0.y
        outPoints[offset + 2] = p1.x
        outPoints[offset + 3] = p1.y
        outPoints[offset + 4] = p2.x
        outPoints[offset + 5] = p2.y
        nextQuadraticIndex++
        return PathSegment.Type.Quadratic
    }
}

// The number of quadratics in points returned by Path.convertConicToQuads()
private val Array<Point>.quadraticCount: Int
    get() = (size - 1) / 2
