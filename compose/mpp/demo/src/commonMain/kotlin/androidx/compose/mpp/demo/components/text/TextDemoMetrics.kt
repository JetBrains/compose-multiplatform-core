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

package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.noriaComposed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import noria.NoriaContext

@Composable
internal fun NoriaContext.TextWithMetrics(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    colors: TextMetricColors? = null
) {
    val textLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = text,
        modifier = modifier.drawTextMetrics(textLayout.value, colors),
        style = style,
        onTextLayout = { textLayout.value = it },
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
internal fun NoriaContext.TextFieldWithMetrics(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    style: TextStyle,
    maxLines: Int,
    softWrap: Boolean = true,
    colors: TextMetricColors? = null
) {
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.drawTextMetrics(textLayout, colors).background(Color.White),
        textStyle = style,
        singleLine = !softWrap,
        maxLines = maxLines,
        onTextLayout = {
            textLayout = it
        }
    )
}

internal class TextMetricColors(
    val background: Color = WinterDoldrums,
    val text: Color = BlackInk,
    val top: Color = MadMagenta,
    val bottom: Color = MandarinOrange,
    val ascent: Color = BlueBlue,
    val descent: Color = YellowYellow,
    val baseline: Color = RedRed,
    val border: Color = Silver,
    val leftRight: Color = CherryTomato
) {
    companion object {
        private val WinterDoldrums = Color(0xfff5f2eb)
        private val BlackInk = Color(0xff44413c)
        private val MadMagenta = Color(0xffce5ec9)
        private val CherryTomato = Color(0xffba2710)
        private val MandarinOrange = Color(0xffff7800)
        private val Silver = Color(0xffbdbdbd)
        private val RedRed = Color(0xffff1744)
        private val YellowYellow = Color(0xffffeb3b)
        private val BlueBlue = Color(0xff2962ff)

        val Default = TextMetricColors()
    }
}

internal fun Modifier.drawTextMetrics(
    textLayoutResult: TextLayoutResult?,
    colors: TextMetricColors?
) = noriaComposed { noriaContext ->
    noriaContext.run {
        val thickness = with(LocalDensity.current) { 1.dp.toPx() }
        val textSize = with(LocalDensity.current) { 12.sp.toPx() }
        val localColors = colors ?: TextMetricColors.Default
        drawWithContent {
            drawContent()
            TextMetricHelper(
                thickness,
                textSize,
                localColors,
                this
            ).drawTextLayout(textLayoutResult)
        }
    }
}

private class TextMetricHelper(
    val thickness: Float,
    val labelSize: Float,
    val colors: TextMetricColors = TextMetricColors.Default,
    drawScope: DrawScope
) : DrawScope by drawScope {

    private enum class Alignment { Left, Right, Center }

    private val pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
    private val overflow = 3 * thickness

    fun drawTextLayout(textLayout: TextLayoutResult?) {
        if (textLayout == null) return
        val size = textLayout.size.toSize()
        val layoutStart = 0f
        val layoutEnd = size.width
        val x1 = layoutStart
        val x2 = layoutEnd
        val textOffset = labelSize
        drawRect(colors.border, topLeft = Offset.Zero, size = size, style = Stroke(thickness))
        for (lineIndex in 0 until textLayout.lineCount) {
            val lineTop = textLayout.getLineTop(lineIndex)
            val lineBottom = textLayout.getLineBottom(lineIndex)
            val lineBaseline = textLayout.getLineBaseline(lineIndex)
            horizontal(colors.top, x1, x2, lineTop, Alignment.Center, -textOffset)
            horizontal(colors.bottom, x1, x2, lineBottom, Alignment.Center, textOffset)
            horizontal(colors.baseline, x1, x2, lineBaseline, Alignment.Center, textOffset)
            vertical(colors.leftRight, textLayout.getLineLeft(lineIndex), lineTop, lineBottom)
            vertical(colors.leftRight, textLayout.getLineRight(lineIndex), lineTop, lineBottom)
        }
    }

    private fun horizontal(
        color: Color,
        startX: Float,
        endX: Float,
        y: Float,
        alignment: Alignment = Alignment.Left,
        textOffset: Float = 0f
    ) {
        drawLine(
            color = color,
            start = Offset(startX - overflow, y),
            end = Offset(endX + overflow, y),
            strokeWidth = thickness,
            pathEffect = pathEffect
        )
        val x = when (alignment) {
            Alignment.Left -> startX + textOffset
            Alignment.Right -> endX - labelSize - textOffset
            Alignment.Center -> startX + (endX - startX) / 2f + textOffset
        }
    }

    private fun vertical(color: Color, x: Float, startY: Float, endY: Float) {
        drawLine(
            color = color,
            start = Offset(x, startY - overflow),
            end = Offset(x, endY + overflow),
            strokeWidth = thickness,
            pathEffect = pathEffect
        )
    }
}
