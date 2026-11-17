/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.ui.text.platform

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.util.fastForEach

internal actual fun MultiParagraph.drawMultiParagraph(
    canvas: Canvas,
    brush: Brush,
    alpha: Float,
    shadow: Shadow?,
    decoration: TextDecoration?,
    drawStyle: DrawStyle?,
    blendMode: BlendMode
) {
    canvas.save()
    if ((paragraphInfoList.size <= 1) || (brush !is ShaderBrush)) {
        paragraphInfoList.fastForEach {
            it.paragraph.paint(canvas, brush, alpha, shadow, decoration, drawStyle, blendMode)
            canvas.translate(0f, it.paragraph.height)
        }
    } else {
        // Apply the brush to the text as a whole, rather than to each paragraph separately
        var width = 0f
        var height = 0f
        paragraphInfoList.fastForEach {
            width = maxOf(width, it.paragraph.width)
            height += it.paragraph.height
        }
        val size = Size(width, height)
        var offset = 0f
        paragraphInfoList.fastForEach {
            it.paragraph.paint(
                canvas = canvas,
                brush = ParagraphShaderBrush(brush, size, offset),
                alpha = alpha,
                shadow = shadow,
                textDecoration = decoration,
                drawStyle = drawStyle,
                blendMode = blendMode
            )
            canvas.translate(0f, it.paragraph.height)
            offset += it.paragraph.height
        }
    }
    canvas.restore()
}

/**
 * A [ShaderBrush] for drawing one paragraph of a [MultiParagraph] with a part of [brush] created
 * for the whole text.
 *
 * @param brush The brush for the whole text.
 * @param textSize The size of the whole text.
 * @param offsetY The vertical offset of the paragraph in the text.
 */
private class ParagraphShaderBrush(
    private val brush: ShaderBrush,
    private val textSize: Size,
    private val offsetY: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        // The paragraph's own size is ignored; the shader is created for the whole text
        val shader = brush.createShader(textSize)
        if (offsetY == 0f) return shader

        // Shift the shader up by the paragraph's offset, using ShaderBrush.transform
        val shiftedBrush = ShaderBrush(shader).apply {
            transform = Matrix().apply { translate(y = -offsetY) }
        }
        val paint = Paint()
        shiftedBrush.applyTo(textSize, paint, alpha = 1f)
        // applyTo doesn't set a shader if textSize is empty, but then nothing is drawn anyway
        return paint.shader ?: shader
    }

    // Implement [equals], so that paragraphs don't need to recreate their shader when drawn again
    // with an equal brush.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParagraphShaderBrush) return false
        return (brush == other.brush) && (textSize == other.textSize) && (offsetY == other.offsetY)
    }

    override fun hashCode(): Int {
        var result = brush.hashCode()
        result = 31 * result + textSize.hashCode()
        result = 31 * result + offsetY.hashCode()
        return result
    }
}