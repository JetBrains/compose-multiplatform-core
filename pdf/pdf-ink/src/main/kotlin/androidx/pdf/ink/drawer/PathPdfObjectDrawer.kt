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

import android.graphics.Canvas
import android.graphics.Matrix
import androidx.core.graphics.withMatrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.pdf.annotation.drawer.PdfObjectDrawer
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.ink.util.createStroke

/** Draws [PathPdfObject] instances onto a [Canvas] using the `androidx.ink` library. */
internal object PathPdfObjectDrawer : PdfObjectDrawer<PathPdfObject> {
    private val canvasStrokeRenderer = CanvasStrokeRenderer.create()

    /**
     * Draws the given [PathPdfObject] onto the provided [Canvas] with the specified [transform].
     *
     * @param pdfObject The [PathPdfObject] to be drawn.
     * @param canvas The [Canvas] on which to draw the object.
     * @param transform The [Matrix] to apply to the [PathPdfObject] before it is drawn.
     */
    override fun draw(pdfObject: PathPdfObject, canvas: Canvas, transform: Matrix) {
        val stroke = pdfObject.createStroke()
        canvas.withMatrix(transform) { canvasStrokeRenderer.draw(canvas, stroke, transform) }
    }
}
