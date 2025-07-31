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

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.pdf.annotation.models.PathPdfObject

/** Converts this [PathPdfObject] to an [Stroke] using its inputs and brush properties. */
internal fun PathPdfObject.createStroke(): Stroke {
    val strokeInputs = MutableStrokeInputBatch()

    inputs.forEachIndexed { i: Int, input ->
        strokeInputs.add(
            StrokeInput().apply {
                update(x = input.x, y = input.y, elapsedTimeMillis = i.toLong() * 15)
            }
        )
    }

    return Stroke(
        brush =
            Brush.createWithColorIntArgb(
                family = StockBrushes.pressurePenLatest,
                colorIntArgb = brushColor,
                size = brushWidth,
                epsilon = 0.05F,
            ),
        inputs = strokeInputs,
    )
}
