/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.mpp.demo.graphics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.DropShadow
import androidx.compose.ui.graphics.shadow.InnerShadow
import androidx.compose.ui.unit.dp

@Composable
fun BrushAndShadows() {
    val scrollState = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Create a linear gradient that shows red in the top left corner
        // and blue in the bottom right corner
        val linear = Brush.linearGradient(listOf(Color.Red, Color.Blue))

        Box(modifier = Modifier.size(120.dp).background(linear))

        // Create a radial gradient centered about the drawing area that is green on
        // the outer
        // edge of the circle and magenta towards the center of the circle
        val radial = Brush.radialGradient(listOf(Color.Green, Color.Magenta))
        Box(modifier = Modifier.size(120.dp).background(radial))

        // Create a sweep gradient centered about the drawing area that is cyan at
        // the start angle and magenta towards the end angle.
        val sweep = Brush.sweepGradient(listOf(Color.Cyan, Color.Magenta))
        Box(modifier = Modifier.size(120.dp).background(sweep))

        Box(
            modifier =
                Modifier.size(120.dp).drawWithCache {
                    val bitmap = ImageBitmap(size.width.toInt(), size.height.toInt())
                    CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), size) {
                        drawRect(Color.Black, style = Stroke(20.dp.toPx()))
                    }
                    val bitmapBrush = ShaderBrush(ImageShader(bitmap))
                    val sweepBrush =
                        Brush.sweepGradient(listOf(Color.Red, Color.Blue, Color.Cyan, Color.Green))
                    val compositeBrush =
                        Brush.compositeShaderBrush(bitmapBrush, sweepBrush, BlendMode.SrcIn)
                    onDrawBehind { drawRect(brush = compositeBrush) }
                }
        )

        Box(Modifier.size(120.dp).shadow(12.dp, RectangleShape).background(Color.White))

        Box(Modifier.size(120.dp).dropShadow(RectangleShape, DropShadow(12.dp)).background(Color.White))

        Box(Modifier.size(120.dp).innerShadow(RectangleShape, InnerShadow(12.dp)))
    }
}
