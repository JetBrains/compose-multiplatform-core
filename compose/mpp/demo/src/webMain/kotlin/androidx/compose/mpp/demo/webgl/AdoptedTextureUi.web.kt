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

package androidx.compose.mpp.demo.webgl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

/**
 * Draws an adopted [Image] — nothing else. All GL work already happened in this frame's
 * `withFrameNanos` callback, which is what lets this composable live inside graphics layers (`clip`,
 * `blur`, `graphicsLayer`): the draw is merely recorded here and replayed into the GPU surface later,
 * which is fine for an image that already belongs to Skia's context.
 *
 * [invalidation] is read inside the draw scope, which is what schedules the next redraw without
 * recomposing anything.
 */
@Composable
internal fun AdoptedTextureSurface(
    modifier: Modifier,
    invalidation: State<Any?>,
    image: () -> Image?,
) {
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            invalidation.value
            val skiaCanvas = canvas.skiaCanvas
            val adopted = image() ?: return@drawIntoCanvas

            // Center-crop so that square tiles do not squash a wide texture.
            val scale = min(adopted.width / size.width, adopted.height / size.height)
            val cropWidth = size.width * scale
            val cropHeight = size.height * scale
            val source = Rect.makeXYWH(
                (adopted.width - cropWidth) / 2f,
                (adopted.height - cropHeight) / 2f,
                cropWidth,
                cropHeight,
            )
            skiaCanvas.drawImageRect(adopted, source, Rect.makeWH(size.width, size.height))
        }
    }
}

@Composable
internal fun LabelledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String = ((value * 100).roundToInt() / 100f).toString(),
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.body2)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
        Text(
            valueText,
            Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.caption,
        )
    }
}

@Composable
internal fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.body2)
    }
}

@Composable
internal fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(170.dp), style = MaterialTheme.typography.caption)
        Text(
            value,
            style = MaterialTheme.typography.caption,
            fontFamily = FontFamily.Monospace,
        )
    }
}
