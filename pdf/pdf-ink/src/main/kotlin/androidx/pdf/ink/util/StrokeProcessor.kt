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

import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.ink.geometry.PartitionedMesh
import androidx.ink.geometry.outlinesToPath
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.annotation.models.StampAnnotation
import androidx.pdf.ink.EditablePdfViewerFragment.PageBoundsProvider
import androidx.pdf.utils.getPathInputsFromPath
import kotlin.math.max
import kotlin.math.min

/** Processes raw [Stroke] objects and converts them to [StampAnnotation]s. */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal class StrokeProcessor(private val pageBoundsProvider: PageBoundsProvider) {

    fun process(stroke: Stroke, zoom: Float): StampAnnotation? {
        if (stroke.inputs.isEmpty()) {
            return null
        }

        val firstInput = stroke.inputs[0]
        val pageInfo =
            pageBoundsProvider.getCurrentPageBounds(firstInput.x, firstInput.y)
                ?: return null // Stroke doesn't start on any page

        val transformedStroke = transformToPdfCoordinates(stroke, pageInfo.bounds, zoom)
        return transformedStroke.toStampAnnotation(pageInfo.pageNum)
    }

    /** Transforms a [Stroke]'s coordinates from view to content coordinates. */
    private fun transformToPdfCoordinates(
        stroke: Stroke,
        pageRectInView: RectF,
        zoom: Float,
    ): Stroke {
        return stroke.transformStroke(pageRectInView, zoom)
    }

    /**
     * Adjusts [Stroke] inputs based on view bounds and zoom.
     *
     * @param viewBounds The bounding box of the view.
     * @param zoom The current zoom level.
     * @return A new [Stroke] with transformed inputs.
     */
    private fun Stroke.transformStroke(viewBounds: RectF, zoom: Float): Stroke {
        val transformedBrushSize = brush.size / zoom
        val transformedInputs = mutableListOf<StrokeInput>()
        for (i in 0 until this.inputs.size) {
            val input = this.inputs[i]
            if (viewBounds.contains(input.x, input.y)) {
                transformedInputs.add(
                    StrokeInput().apply {
                        update(
                            x = (input.x - viewBounds.left) / zoom,
                            y = (input.y - viewBounds.top) / zoom,
                            elapsedTimeMillis = input.elapsedTimeMillis,
                            toolType = input.toolType,
                        )
                    }
                )
            }
        }
        return Stroke(
            brush = brush.copy(size = transformedBrushSize),
            inputs = MutableStrokeInputBatch().apply { add(transformedInputs) },
        )
    }

    /**
     * Converts this [Stroke] into a [StampAnnotation].
     *
     * The outline of the stroke is extracted from its shape's mesh and used to create
     * [PathPdfObject]s.
     *
     * @param pageNum The page number where this annotation will be placed.
     * @return A [StampAnnotation] representing this stroke.
     */
    private fun Stroke.toStampAnnotation(pageNum: Int): StampAnnotation {
        val strokeMesh: PartitionedMesh = shape
        val renderGroupPaths: List<Path> =
            (0 until strokeMesh.getRenderGroupCount()).map { groupIndex ->
                strokeMesh.outlinesToPath(groupIndex)
            }
        val pathInputs: List<PathPdfObject.PathInput> =
            renderGroupPaths.flatMap { it.getPathInputsFromPath() }

        val pathPdfObject =
            PathPdfObject(
                brushColor = brush.colorIntArgb,
                brushWidth = brush.size,
                inputs = pathInputs,
            )
        val bounds = getBounds()
        return StampAnnotation(
            pageNum = pageNum,
            bounds = bounds,
            pdfObjects = listOf(pathPdfObject),
        )
    }

    /**
     * Calculates the bounding box of this [Stroke] based on the minimum and maximum x and y
     * coordinates of its inputs.
     *
     * @return A [RectF] representing the bounding box of the stroke.
     */
    private fun Stroke.getBounds(): RectF {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in 0 until this.inputs.size) {
            val strokeInput = this.inputs[i]
            minX = min(minX, strokeInput.x)
            maxX = max(maxX, strokeInput.x)
            minY = min(minY, strokeInput.y)
            maxY = max(maxY, strokeInput.y)
        }
        return RectF(minX, minY, maxX, maxY)
    }
}
