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

package androidx.ink.geometry

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.testing.buildStrokeInputBatchFromPoints
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AndroidGraphicsConversionExtensionsTest {
    @Test
    fun populateMatrix_resultingMatrixIsAffine() {
        val affineTransform = ImmutableAffineTransform(A, B, C, D, E, F)
        val matrix = Matrix()
        affineTransform.populateMatrix(matrix)
        assertThat(matrix.isAffine).isTrue()
    }

    @Test
    fun populateMatrix_resultsInEquivalentVecTransformations() {
        val affineTransform = ImmutableAffineTransform(A, B, C, D, E, F)

        // First, apply the affineTransform to an ink Vec for later comparison.
        val inputVec = ImmutableVec(1f, 2f)
        val outputVec = MutableVec()
        affineTransform.applyTransform(inputVec, outputVec)

        // Then, populate an android.graphics.Matrix from the affineTransform and perform the
        // equivalent
        // operation.
        val matrix = Matrix()
        affineTransform.populateMatrix(matrix)
        val vecFloatArray = floatArrayOf(inputVec.x, inputVec.y)
        matrix.mapPoints(vecFloatArray)

        assertThat(outputVec).isEqualTo(ImmutableVec(vecFloatArray[0], vecFloatArray[1]))
    }

    @Test
    fun toMatrix_resultingMatrixIsAffine() {
        val affineTransform = ImmutableAffineTransform(A, B, C, D, E, F)
        val matrix = affineTransform.toMatrix()
        assertThat(matrix.isAffine).isTrue()
    }

    @Test
    fun toMatrix_resultsInEquivalentVecTransformations() {
        val affineTransform = ImmutableAffineTransform(A, B, C, D, E, F)

        // First, apply the affineTransform to an ink Vec for later comparison.
        val inputVec = ImmutableVec(1f, 2f)
        val outputVec = MutableVec()
        affineTransform.applyTransform(inputVec, outputVec)

        // Then, populate an android.graphics.Matrix from the affineTransform and perform the
        // equivalent
        // operation.
        val matrix = affineTransform.toMatrix()
        val vecFloatArray = floatArrayOf(inputVec.x, inputVec.y)
        matrix.mapPoints(vecFloatArray)

        assertThat(outputVec).isEqualTo(ImmutableVec(vecFloatArray[0], vecFloatArray[1]))
    }

    @Test
    fun ImmutableAffineTransform_from_resultsInEquivalentVecTransformations() {
        val matrix = Matrix()
        matrix.setValues(floatArrayOf(A, B, C, D, E, F, 0f, 0f, 1f))
        // First, apply the matrix to the values of an ink Vec for later comparison.
        val inputVec = ImmutableVec(1f, 2f)
        val vecFloatArray = floatArrayOf(inputVec.x, inputVec.y)
        matrix.mapPoints(vecFloatArray)

        // Then, populate an ImmutableAffineTransform from the matrix and perform the equivalent
        // operation.
        val affineTransform = ImmutableAffineTransform.from(matrix)
        val outputVec = MutableVec()
        affineTransform?.applyTransform(inputVec, outputVec)

        assertThat(outputVec).isEqualTo(ImmutableVec(vecFloatArray[0], vecFloatArray[1]))
    }

    @Test
    fun MutableAffineTransform_populateFrom_resultsInEquivalentVecTransformations() {
        val matrix = Matrix()
        matrix.setValues(floatArrayOf(A, B, C, D, E, F, 0f, 0f, 1f))
        // First, apply the matrix to the values of an ink Vec for later comparison.
        val inputVec = ImmutableVec(1f, 2f)
        val vecFloatArray = floatArrayOf(inputVec.x, inputVec.y)
        matrix.mapPoints(vecFloatArray)

        // Then, populate an MutableAffineTransform from the matrix and perform the equivalent
        // operation.
        val affineTransform = MutableAffineTransform()
        affineTransform.populateFrom(matrix)
        val outputVec = MutableVec()
        affineTransform.applyTransform(inputVec, outputVec)

        assertThat(outputVec).isEqualTo(ImmutableVec(vecFloatArray[0], vecFloatArray[1]))
    }

    @Test
    fun MutableAffineTransform_populateFrom_throwsForNonAffineMatrix() {
        val matrix = Matrix()
        matrix.setValues(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f))
        val affineTransform = MutableAffineTransform()
        assertFailsWith<IllegalArgumentException> { affineTransform.populateFrom(matrix) }
    }

    @Test
    fun toPointF_resultingPointIsEquivalent() {
        val point = ImmutableVec(1f, 2f)
        val pointF = point.toPointF()
        assertThat(pointF.x).isEqualTo(point.x)
        assertThat(pointF.y).isEqualTo(point.y)
    }

    @Test
    fun populatePointF_resultingPointIsEquivalent() {
        val point = ImmutableVec(1f, 2f)
        val pointF = PointF()
        point.populatePointF(pointF)

        assertThat(pointF.x).isEqualTo(point.x)
        assertThat(pointF.y).isEqualTo(point.y)
    }

    @Test
    fun from_resultingVecIsEquivalentToPointF() {
        val pointF = PointF(1f, 2f)
        val point = ImmutableVec.from(pointF)

        assertThat(point.x).isEqualTo(pointF.x)
        assertThat(point.y).isEqualTo(pointF.y)
    }

    @Test
    fun populateFrom_resultingVecIsEquivalentToPointF() {
        val pointF = PointF(1f, 2f)
        val point = MutableVec()
        point.populateFrom(pointF)

        assertThat(point.x).isEqualTo(pointF.x)
        assertThat(point.y).isEqualTo(pointF.y)
    }

    @Test
    fun toRectF_resultingRectIsEquivalent() {
        val box = ImmutableBox.fromTwoPoints(ImmutableVec(1f, 2f), ImmutableVec(3f, 4f))
        val rectF = box.toRectF()

        assertThat(rectF.left).isEqualTo(box.xMin)
        assertThat(rectF.top).isEqualTo(box.yMin)
        assertThat(rectF.right).isEqualTo(box.xMax)
        assertThat(rectF.bottom).isEqualTo(box.yMax)
    }

    @Test
    fun populateRectF_resultingRectIsEquivalent() {
        val box = ImmutableBox.fromTwoPoints(ImmutableVec(1f, 2f), ImmutableVec(3f, 4f))
        val rectF = RectF()
        box.populateRectF(rectF)

        assertThat(rectF.left).isEqualTo(box.xMin)
        assertThat(rectF.top).isEqualTo(box.yMin)
        assertThat(rectF.right).isEqualTo(box.xMax)
        assertThat(rectF.bottom).isEqualTo(box.yMax)
    }

    @Test
    fun from_resultingBoxIsEquivalentToRectF() {
        val rectF = RectF(1f, 2f, 3f, 4f)
        val box = ImmutableBox.from(rectF)

        assertThat(box?.xMin).isEqualTo(rectF.left)
        assertThat(box?.yMin).isEqualTo(rectF.top)
        assertThat(box?.xMax).isEqualTo(rectF.right)
        assertThat(box?.yMax).isEqualTo(rectF.bottom)
    }

    @Test
    fun from_resultIsNullForEmptyRectF() {
        val rectF = RectF()
        val box = ImmutableBox.from(rectF)

        assertThat(box).isNull()
    }

    @Test
    fun boxAccumulatorAdd_resultingBoxIsEquivalentToRectF() {
        val rectF = RectF(1f, 2f, 3f, 4f)
        val boxAccumulator = BoxAccumulator()
        boxAccumulator.add(rectF)
        val box = boxAccumulator.box
        assertThat(box).isNotNull()

        assertThat(box?.xMin).isEqualTo(rectF.left)
        assertThat(box?.yMin).isEqualTo(rectF.top)
        assertThat(box?.xMax).isEqualTo(rectF.right)
        assertThat(box?.yMax).isEqualTo(rectF.bottom)
    }

    @Test
    fun boxAccumulatorAdd_resultingBoxIsUnchangedForEmptyRectF() {
        // Malformed rectF
        val rectF = RectF(5f, 8f, 2f, 1f)
        val startingBox = MutableBox().setXBounds(1F, 2F).setYBounds(3F, 4F)
        val boxAccumulator = BoxAccumulator(startingBox)
        boxAccumulator.add(rectF)
        val box = boxAccumulator.box
        assertThat(box).isEqualTo(startingBox)
    }

    @Test
    fun outlinesToPath_returnsCorrectListOfPath() {
        val partitionedMesh = buildTestStrokeShape()
        val path = partitionedMesh.outlinesToPath(0)
        assertThat(path.isEmpty).isFalse()
        // For point-by-point test, see the AndroidGraphicsConversionExtensionsEmulatorTest.
    }

    @Test
    fun outlinesToPath_returnsOneListWithEmptyPathForEmptyShape() {
        val partitionedMesh = buildEmptyTestStrokeShape()

        val path = partitionedMesh.outlinesToPath(0)
        assertThat(path.isEmpty).isTrue()
    }

    @Test
    fun populatePathFromOutlines_returnsCorrectpath() {
        val partitionedMesh = buildTestStrokeShape()
        val path = Path()
        partitionedMesh.populatePathFromOutlines(0, path)
        assertThat(path.isEmpty).isFalse()
        // For point-by-point test, see the AndroidGraphicsConversionExtensionsEmulatorTest.
    }

    @Test
    fun populatePathFromOutlines_returnsWithEmptyPathForEmptyShape() {
        val partitionedMesh = buildEmptyTestStrokeShape()

        val path = Path()
        partitionedMesh.populatePathFromOutlines(0, path)
        assertThat(path.isEmpty).isTrue()
    }

    private fun buildTestStrokeShape(): PartitionedMesh {
        return Stroke(
                TEST_BRUSH,
                buildStrokeInputBatchFromPoints(floatArrayOf(10f, 3f, 20f, 5f)).asImmutable(),
            )
            .shape
    }

    private fun buildEmptyTestStrokeShape(): PartitionedMesh {
        return Stroke(TEST_BRUSH, buildStrokeInputBatchFromPoints(floatArrayOf()).asImmutable())
            .shape
    }

    companion object {
        private const val A = 1f
        private const val B = 2f
        private const val C = -3f
        private const val D = -4f
        private const val E = 5f
        private const val F = 6f

        private val TEST_BRUSH =
            Brush(family = StockBrushes.markerLatest, size = 10f, epsilon = 0.1f)
    }
}
