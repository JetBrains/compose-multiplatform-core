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

package androidx.pdf.ink.util

import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.color.Color
import androidx.ink.brush.color.toArgb
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.ink.EditablePdfViewerFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
class StrokeProcessorTest {

    private class TestPageBoundsProvider(
        private val pageNum: Int? = null,
        private val viewBounds: RectF? = null,
    ) : EditablePdfViewerFragment.PageBoundsProvider {
        override fun getCurrentPageBounds(
            viewX: Float,
            viewY: Float,
        ): EditablePdfViewerFragment.PageBoundsProvider.PageBounds? {
            if (pageNum == null || viewBounds == null) return null
            return EditablePdfViewerFragment.PageBoundsProvider.PageBounds(pageNum, viewBounds)
        }
    }

    @Test
    fun process_whenStrokeIsEmpty_returnsNull() {
        // Arrange
        val processor = StrokeProcessor(TestPageBoundsProvider())
        val emptyStroke = createStroke(inputs = emptyList())

        // Act
        val annotation = processor.process(emptyStroke, zoom = 1.0f)

        // Assert
        assertThat(annotation).isNull()
    }

    @Test
    fun process_whenStrokeDoesNotStartOnPage_returnsNull() {
        // Arrange
        val processor = StrokeProcessor(TestPageBoundsProvider())
        val stroke = createStroke(inputs = listOf(createStrokeInput(x = 10f, y = 10f)))

        // Act
        val annotation = processor.process(stroke, zoom = 1.0f)

        // Assert
        assertThat(annotation).isNull()
    }

    @Test
    fun process_validStrokeOnPage_returnsTransformedAnnotation() {
        // Arrange
        val pageNum = 0
        val pageBoundsInView = RectF(100f, 100f, 200f, 200f)
        val currentZoom = 2.0f

        val processor = StrokeProcessor(TestPageBoundsProvider(pageNum, pageBoundsInView))

        val viewCoordinatesPoint1 = PointF(pageBoundsInView.left + 20f, pageBoundsInView.top + 40f)
        val viewCoordinatesPoint2 = PointF(pageBoundsInView.left + 60f, pageBoundsInView.top + 20f)

        val stroke =
            createStroke(
                inputs =
                    listOf(
                        createStrokeInput(x = viewCoordinatesPoint1.x, y = viewCoordinatesPoint1.y),
                        createStrokeInput(x = viewCoordinatesPoint2.x, y = viewCoordinatesPoint2.y),
                    )
            )

        // Act
        val annotation = processor.process(stroke, currentZoom)

        // Assert
        val expectedPdfPoint1 = PointF(10f, 20f)
        val expectedPdfPoint2 = PointF(30f, 10f)
        val expectedBrushSize = DEFAULT_BRUSH_SIZE / currentZoom

        assertThat(annotation).isNotNull()
        requireNotNull(annotation)

        with(annotation) {
            assertThat(this.pageNum).isEqualTo(pageNum)
            assertThat(bounds).isEqualTo(RectF(10f, 10f, 30f, 20f))

            val pathObject = pdfObjects.firstOrNull() as? PathPdfObject
            assertThat(pathObject).isNotNull()
            requireNotNull(pathObject)

            with(pathObject) {
                assertThat(brushColor).isEqualTo(DEFAULT_BRUSH_COLOR)
                assertThat(brushWidth).isEqualTo(expectedBrushSize)
                assertThat(inputs).hasSize(2)
                assertThat(inputs[0].x).isEqualTo(expectedPdfPoint1.x)
                assertThat(inputs[0].y).isEqualTo(expectedPdfPoint1.y)
                assertThat(inputs[1].x).isEqualTo(expectedPdfPoint2.x)
                assertThat(inputs[1].y).isEqualTo(expectedPdfPoint2.y)
            }
        }
    }

    private fun createStrokeInput(x: Float, y: Float): StrokeInput {
        return StrokeInput().apply { update(x, y, elapsedTimeMillis, toolType) }
    }

    private fun createStroke(
        inputs: List<StrokeInput>,
        brushSize: Float = DEFAULT_BRUSH_SIZE,
        color: Int = DEFAULT_BRUSH_COLOR,
    ): Stroke {
        val brush =
            Brush.Companion.createWithColorIntArgb(
                family = StockBrushes.pressurePenLatest,
                colorIntArgb = color,
                size = brushSize,
                epsilon = 0.1F,
            )
        val inputBatch = MutableStrokeInputBatch().add(inputs)
        return Stroke(brush, inputBatch)
    }

    private companion object {
        private const val DEFAULT_BRUSH_SIZE = 10f
        private val DEFAULT_BRUSH_COLOR = Color.Black.toArgb()
    }
}
