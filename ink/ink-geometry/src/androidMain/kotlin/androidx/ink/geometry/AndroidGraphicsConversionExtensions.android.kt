/*
 * Copyright (C) 2024 The Android Open Source Project
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

@file:JvmName("AndroidGraphicsConverter")

package androidx.ink.geometry

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import androidx.ink.geometry.internal.threadLocal

/** Scratch space to be used as the argument to [Matrix.getValues] and [Matrix.setValues]. */
private val matrixValuesScratchArray by threadLocal { FloatArray(9) }

/** Scratch space to be used as the argument to [BoxAccumulator.add]. */
private val boxAccumulatorScratchMutableBox by threadLocal { MutableBox() }

/** Scratch space to be used as the argument to [PartitionedMesh.populateOutlinePosition]. */
private val populateOutlinePositionScratchMutableVec by threadLocal { MutableVec() }

/**
 * Constructs a [Matrix] with the values from the [AffineTransform].
 *
 * Performance-sensitive code should use the [populateMatrix] overload that takes a pre-allocated
 * [Matrix], so that the instance can be reused across multiple calls.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun AffineTransform.toMatrix(): Matrix {
    getValues(matrixValuesScratchArray)
    matrixValuesScratchArray[Matrix.MPERSP_0] = 0f
    matrixValuesScratchArray[Matrix.MPERSP_1] = 0f
    matrixValuesScratchArray[Matrix.MPERSP_2] = 1f
    return Matrix().apply { setValues(matrixValuesScratchArray) }
}

/** Writes the values from this [AffineTransform] to [matrixOut]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun AffineTransform.populateMatrix(matrixOut: Matrix) {
    getValues(matrixValuesScratchArray)
    matrixValuesScratchArray[Matrix.MPERSP_0] = 0f
    matrixValuesScratchArray[Matrix.MPERSP_1] = 0f
    matrixValuesScratchArray[Matrix.MPERSP_2] = 1f
    matrixOut.setValues(matrixValuesScratchArray)
}

/**
 * Constructs an ImmutableAffineTransform with the values from [matrix], if and only if [matrix] is
 * an affine transform. Returns null if [matrix] is not an affine transform.
 *
 * Performance-sensitive code should use the [populateFrom] overload that takes a pre-allocated
 * [MutableAffineTransform], so that the instance can be reused across multiple calls.
 *
 * Java callers should prefer
 * `AndroidGraphicsConverter.createAffineTransform(Matrix)`([createAffineTransform]).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun ImmutableAffineTransform.Companion.from(matrix: Matrix): ImmutableAffineTransform? {
    if (!matrix.isAffine) {
        return null
    }
    matrix.getValues(matrixValuesScratchArray)
    return ImmutableAffineTransform(matrixValuesScratchArray)
}

/**
 * Constructs an ImmutableAffineTransform with the values from [matrix], if and only if [matrix] is
 * an affine transform. Returns null if [matrix] is not an affine transform.
 *
 * Performance-sensitive code should use the [populateFrom] overload that takes a pre-allocated
 * [MutableAffineTransform], so that the instance can be reused across multiple calls.
 *
 * Kotlin callers should prefer [ImmutableAffineTransform.Companion.from].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun createAffineTransform(matrix: Matrix): ImmutableAffineTransform? =
    ImmutableAffineTransform.from(matrix)

/**
 * If [matrix] is an affine transform, copies the values from [matrix] to this
 * [MutableAffineTransform] and returns [this].
 *
 * @Throws [IllegalArgumentException] if [matrix] is not an affine transform.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun MutableAffineTransform.populateFrom(matrix: Matrix): MutableAffineTransform {
    if (!matrix.isAffine) {
        throw IllegalArgumentException("Matrix is not affine")
    }
    matrix.getValues(matrixValuesScratchArray)
    setValues(matrixValuesScratchArray)
    return this
}

/** Constructs a [PointF] with the same coordinates as the [Vec] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun Vec.toPointF(): PointF = PointF(x, y)

/** Writes the values from this [Vec] to [pointFOut]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun Vec.populatePointF(pointFOut: PointF) {
    pointFOut.x = x
    pointFOut.y = y
}

/**
 * Constructs an [ImmutableVec] with the values from [pointF].
 *
 * Java callers should prefer `AndroidGraphicsConverter.createVec(PointF)`([createVec]).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun ImmutableVec.Companion.from(pointF: PointF): ImmutableVec =
    ImmutableVec(pointF.x, pointF.y)

/**
 * Constructs an [ImmutableVec] with the values from [pointF].
 *
 * Kotlin callers should prefer [ImmutableVec.Companion.from].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun createVec(pointF: PointF): ImmutableVec = ImmutableVec(pointF.x, pointF.y)

/** Writes the values from [pointF] to this [MutableVec]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun MutableVec.populateFrom(pointF: PointF) {
    x = pointF.x
    y = pointF.y
}

/** Constructs a [RectF] with the same coordinates as the [Box] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun Box.toRectF(): RectF = RectF(xMin, yMin, xMax, yMax)

/** Writes the values from this [Box] to [rectFOut]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun Box.populateRectF(rectFOut: RectF) {
    rectFOut.left = xMin
    rectFOut.top = yMin
    rectFOut.right = xMax
    rectFOut.bottom = yMax
}

/**
 * Constructs an [ImmutableBox] with the values from [rectF], if and only if [rectF] is not empty.
 * Returns null if [rectF] is empty.
 *
 * Java callers should prefer `AndroidGraphicsConverter.createBox`([createBox]).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun ImmutableBox.Companion.from(rectF: RectF): ImmutableBox? =
    if (rectF.isEmpty) {
        null
    } else {
        fromTwoPoints(ImmutableVec(rectF.left, rectF.bottom), ImmutableVec(rectF.right, rectF.top))
    }

/**
 * Constructs an [ImmutableBox] with the values from [rectF], if and only if [rectF] is not empty.
 * Returns null if [rectF] is empty.
 *
 * Kotlin callers should prefer [ImmutableBox.Companion.from].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun createBox(rectF: RectF): ImmutableBox? = ImmutableBox.from(rectF)

/**
 * Expands the accumulated bounding box (if necessary) such that it also contains [rectf]. If
 * [rectf] is null, this is a no-op.
 *
 * This is functionally equivalent to, but more efficient than: `add(ImmutableBox.from(rectF))`
 *
 * @return `this`
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun BoxAccumulator.add(rectF: RectF): BoxAccumulator {
    if (!rectF.isEmpty) {
        boxAccumulatorScratchMutableBox.setXBounds(rectF.left, rectF.right)
        boxAccumulatorScratchMutableBox.setYBounds(rectF.top, rectF.bottom)
        add(boxAccumulatorScratchMutableBox)
    }
    return this
}

/** Returns a [Path] containing the outlines in the render group at [renderGroupIndex]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun PartitionedMesh.outlinesToPath(@IntRange(from = 0) renderGroupIndex: Int): Path {
    val path = Path()
    populatePathFromOutlines(renderGroupIndex, path)
    return path
}

/** Replaces the contents of [path] with the outline of the render group at [renderGroupIndex]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public fun PartitionedMesh.populatePathFromOutlines(
    @IntRange(from = 0) renderGroupIndex: Int,
    path: Path,
) {
    path.rewind()
    for (outlineIndex in 0 until getOutlineCount(renderGroupIndex)) {
        val outlineVertexCount = getOutlineVertexCount(renderGroupIndex, outlineIndex)
        if (outlineVertexCount == 0) continue

        populateOutlinePosition(
            renderGroupIndex,
            outlineIndex,
            0,
            populateOutlinePositionScratchMutableVec,
        )
        path.moveTo(
            populateOutlinePositionScratchMutableVec.x,
            populateOutlinePositionScratchMutableVec.y,
        )

        for (outlineVertexIndex in 1 until outlineVertexCount) {
            populateOutlinePosition(
                renderGroupIndex,
                outlineIndex,
                outlineVertexIndex,
                populateOutlinePositionScratchMutableVec,
            )
            path.lineTo(
                populateOutlinePositionScratchMutableVec.x,
                populateOutlinePositionScratchMutableVec.y,
            )
        }

        path.close()
    }
}
