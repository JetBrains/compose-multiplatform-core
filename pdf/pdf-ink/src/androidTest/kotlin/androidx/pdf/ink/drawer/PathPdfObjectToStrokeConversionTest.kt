/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.pdf.ink.drawer

import android.graphics.Color
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.StrokeInput
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.ink.util.createStroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PathPdfObjectToStrokeConversionTest {

    @Test
    fun createStrokeFromPath_emptyPathObject_returnsStrokeWithCorrectBrushAndNoInputs() {
        val pdfObject =
            PathPdfObject(
                inputs = emptyList(),
                brushColor = DEFAULT_BRUSH_COLOR,
                brushWidth = DEFAULT_BRUSH_WIDTH,
            )

        val stroke = pdfObject.createStroke()

        assertStrokeBrush(stroke.brush)
        assertThat(stroke.inputs.isEmpty()).isTrue()
    }

    @Test
    fun createStrokeFromPath_singleInput_returnsStrokeWithCorrectInputAndTiming() {
        val pathInputs = listOf(PathPdfObject.PathInput(x = 10f, y = 20f))
        val pdfObject =
            PathPdfObject(
                inputs = pathInputs,
                brushColor = DEFAULT_BRUSH_COLOR,
                brushWidth = DEFAULT_BRUSH_WIDTH,
            )

        val stroke = pdfObject.createStroke()

        assertStrokeBrush(stroke.brush)
        assertThat(stroke.inputs.size).isEqualTo(1)
        assertStrokeInput(stroke.inputs[0], 10f, 20f, 0L)
    }

    @Test
    fun createStrokeFromPath_multipleInputs_returnsStrokeWithCorrectInputsAndTimings() {
        val pathInputs =
            listOf(
                PathPdfObject.PathInput(x = 10f, y = 20f),
                PathPdfObject.PathInput(x = 15f, y = 25f),
                PathPdfObject.PathInput(x = 30f, y = 40f),
            )
        val pdfObject =
            PathPdfObject(
                inputs = pathInputs,
                brushColor = DEFAULT_BRUSH_COLOR,
                brushWidth = DEFAULT_BRUSH_WIDTH,
            )

        val stroke = pdfObject.createStroke()

        assertStrokeBrush(stroke.brush)
        assertThat(stroke.inputs.size).isEqualTo(3)
        assertStrokeInput(stroke.inputs[0], 10f, 20f, 0L)
        assertStrokeInput(stroke.inputs[1], 15f, 25f, 15L)
        assertStrokeInput(stroke.inputs[2], 30f, 40f, 30L)
    }

    private fun assertStrokeBrush(brush: Brush) {
        with(brush) {
            assertThat(family).isEqualTo(StockBrushes.pressurePenLatest)
            assertThat(colorIntArgb).isEqualTo(DEFAULT_BRUSH_COLOR)
            assertThat(size).isEqualTo(DEFAULT_BRUSH_WIDTH)
            assertThat(epsilon).isEqualTo(EXPECTED_BRUSH_EPSILON)
        }
    }

    private fun assertStrokeInput(
        input: StrokeInput,
        expectedX: Float,
        expectedY: Float,
        expectedTime: Long,
    ) {
        with(input) {
            assertThat(x).isEqualTo(expectedX)
            assertThat(y).isEqualTo(expectedY)
            assertThat(elapsedTimeMillis).isEqualTo(expectedTime)
        }
    }

    private companion object {
        private const val DEFAULT_BRUSH_COLOR = Color.RED
        private const val DEFAULT_BRUSH_WIDTH = 10f
        private const val EXPECTED_BRUSH_EPSILON = 0.05F
    }
}
